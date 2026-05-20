package com.crsocial.witchhatatelier.client.gesture;

import com.mojang.blaze3d.platform.NativeImage;
import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.spell.trigger.TriggerEvaluator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Gesture canvas screen with zoom + pan viewport.
 *
 * <h2>Coordinate spaces</h2>
 * <ul>
 *   <li><b>Canvas space</b> — logical grid {@code [0, canvasSize.width] × [0, canvasSize.height]}.
 *       All stroke data, dead-zone checks, and smoothing operate here. Never changes with zoom/pan.</li>
 *   <li><b>Screen space</b> — monitor pixels. Derived via {@link #scrX}/{@link #scrY} or
 *       their inverses {@link #logX}/{@link #logY}.</li>
 * </ul>
 *
 * <h2>Viewport model</h2>
 * <ul>
 *   <li>{@code displayX/Y/W/H} — on-screen rectangle allocated for drawing (set in {@link #init}).</li>
 *   <li>{@code displayScale} — screen pixels per canvas pixel at zoom 1.0.</li>
 *   <li>{@code zoom} ≥ 1.0; 1.0 = whole canvas fills display area.</li>
 *   <li>{@code panX/Y} — canvas-space coordinate at the top-left corner of the viewport.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class CanvasScreen extends Screen {

    // ════════════════════════════════════════════════════════════════════════════
    // Constants
    // ════════════════════════════════════════════════════════════════════════════

    // Display sizing: smaller canvases get a smaller portion of the screen so you
    // can feel the size difference. Base fraction for a 512px canvas = 0.75;
    // scales down linearly toward 0.30 for tiny canvases.
    private static final float DISPLAY_FRACTION_MIN  = 0.30f;
    private static final float DISPLAY_FRACTION_RANGE = 0.45f; // added on top of MIN at canvas=512px
    private static final int   DISPLAY_REF_PX        = 512;    // reference canvas size for max fraction

    private static final float ZOOM_MIN = 1.0f;
    private static final float ZOOM_MAX = 8.0f;

    /** Minimum screen pixels per canvas pixel before the pixel grid is shown. */
    private static final int GRID_THRESHOLD_PX = 4;

    private static final float SNAP_MIN_SEGMENT     = 2f;
    private static final int   SNAP_MIN_CONSISTENCY = 8;

    // ════════════════════════════════════════════════════════════════════════════
    // Fields
    // ════════════════════════════════════════════════════════════════════════════

    // ── Profile & mode ──────────────────────────────────────────────────────────
    private final CanvasProfile profile;
    private final boolean readOnly;
    private final BiConsumer<List<GesturePoint>, List<Integer>> saveHandler;

    /** True after the trigger phase fires an activation ring. Input is rejected but save still runs. */
    private boolean inputLocked = false;
    private List<Integer> activationRingStrokeIds = List.of();

    // ── Stroke data ─────────────────────────────────────────────────────────────
    private final CanvasPointStore pointStore = new CanvasPointStore();
    private final List<GesturePoint> preloadedPoints;

    // ── Canvas logical dimensions (set in init) ──────────────────────────────────
    private CanvasSize canvasSize;

    // ── On-screen drawing rectangle (set in init) ────────────────────────────────
    protected int displayX, displayY, displayW, displayH;
    /** Screen pixels per canvas pixel at zoom 1.0. */
    private float displayScale;

    // ── Viewport state ────────────────────────────────────────────────────────────
    private float zoom = 1.0f;
    /** Top-left corner of the viewport in canvas coordinates. */
    private float panX = 0f, panY = 0f;
    private boolean panning = false;
    private double lastPanMouseX, lastPanMouseY;

    // ── Lazy-Mouse state (canvas space) ──────────────────────────────────────────
    private float smoothedX, smoothedY;

    // ── Angle-Snap state (canvas space) ──────────────────────────────────────────
    private boolean snapActive      = false;
    private float   snapOriginX     = 0f, snapOriginY = 0f;
    private int     snapConsistency = 0;
    private double  snapAngleDeg    = -1.0;

    // ── Animation ────────────────────────────────────────────────────────────────
    private int clearAnimTimer = 0;

    // ── GLFW cursor cache ─────────────────────────────────────────────────────────
    private static final Map<String, Long> CURSOR_CACHE = new HashMap<>();
    private static final int CURSOR_SCALE = 3;
    private String activeCursorKey = null;

    // ════════════════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════════════════

    public CanvasScreen(CanvasProfile profile,
                        List<GesturePoint> preloadedPoints,
                        boolean editable,
                        BiConsumer<List<GesturePoint>, List<Integer>> saveHandler,
                        @SuppressWarnings("unused") BlockPos sourceBlock) {
        super(Component.translatable(profile.titleKey()));
        this.profile         = profile;
        this.readOnly        = !editable;
        this.saveHandler     = saveHandler;
        this.preloadedPoints = preloadedPoints;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Screen lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        canvasSize = profile.canvasSize();

        // Allocate display area: fraction of screen grows with canvas size so that
        // smaller papers feel smaller and larger papers feel more spacious.
        int shortDim = Math.min(canvasSize.width(), canvasSize.height());
        float sizeFrac = Math.min(1f, (float) shortDim / DISPLAY_REF_PX);
        float budgetFrac = DISPLAY_FRACTION_MIN + sizeFrac * DISPLAY_FRACTION_RANGE;
        float budgetPx = Math.min(width, height) * budgetFrac;

        float aspect = (float) canvasSize.width() / canvasSize.height();
        if (aspect >= 1f) {
            displayW = (int) budgetPx;
            displayH = (int) (budgetPx / aspect);
        } else {
            displayH = (int) budgetPx;
            displayW = (int) (budgetPx * aspect);
        }
        displayX = (width  - displayW) / 2;
        displayY = (height - displayH) / 2;
        displayScale = (float) displayW / canvasSize.width();

        zoom = 1.0f;
        panX = 0f;
        panY = 0f;

        loadPoints(preloadedPoints);
    }

    @Override
    public void tick() {
        super.tick();
        if (clearAnimTimer > 0) clearAnimTimer--;
    }

    @Override
    public void onClose() {
        if (!readOnly && !pointStore.isEmpty()) {
            saveHandler.accept(savePoints(), activationRingStrokeIds);
        }
        cleanupCursors();
        super.onClose();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Read / Write API  (always canvas space — zoom-independent)
    // ════════════════════════════════════════════════════════════════════════════

    public void loadPoints(List<GesturePoint> points) {
        pointStore.denormalize(points, canvasSize.width(), canvasSize.height());
    }

    public List<GesturePoint> savePoints() {
        return pointStore.normalize(canvasSize.width(), canvasSize.height());
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Viewport transform helpers
    // ════════════════════════════════════════════════════════════════════════════

    /** Canvas → screen X. */
    private float scrX(float cx) { return displayX + (cx - panX) * displayScale * zoom; }
    /** Canvas → screen Y. */
    private float scrY(float cy) { return displayY + (cy - panY) * displayScale * zoom; }

    /** Screen → canvas X. */
    private float logX(double sx) { return (float)((sx - displayX) / (displayScale * zoom) + panX); }
    /** Screen → canvas Y. */
    private float logY(double sy) { return (float)((sy - displayY) / (displayScale * zoom) + panY); }

    private void clampPan() {
        float visW = canvasSize.width()  / zoom;
        float visH = canvasSize.height() / zoom;
        panX = Math.clamp(panX, 0f, Math.max(0f, canvasSize.width()  - visW));
        panY = Math.clamp(panY, 0f, Math.max(0f, canvasSize.height() - visH));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Rendering
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        renderCanvas(gui);

        int titleX = displayX + displayW / 2;
        gui.drawCenteredString(font,
                Component.translatable(profile.titleKey()),
                titleX, displayY - font.lineHeight - 4, 0xFFFFFFFF);

        if (readOnly) {
            gui.drawCenteredString(font,
                    Component.translatable(profile.readOnlyKey()),
                    titleX, displayY + displayH + 4, 0xFFAAAAAA);
        }

        if (zoom > 1.0f) {
            String label = String.format("%d×", (int) zoom);
            gui.drawString(font, label,
                    displayX + displayW - 4 - font.width(label),
                    displayY + 4, 0xAAFFFFFF);
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0x00000000);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Canvas composite ────────────────────────────────────────────────────────

    private void renderCanvas(GuiGraphics gui) {
        drawScreenSprite(gui);

        int bg = readOnly ? profile.canvasBgReadOnlyColor() : profile.canvasBgColor();
        drawCanvasBackground(gui, bg);
        drawPixelGrid(gui);
        drawBorder(gui);

        int sw = profile.strokeWidth();
        for (List<Vector2f> stroke : pointStore.strokes()) {
            drawStroke(gui, stroke, profile.strokeColor(), sw);
        }

        List<Vector2f> active = pointStore.activeStroke();
        if (!readOnly && active != null && !active.isEmpty()) {
            drawStroke(gui, active, profile.activeStrokeColor(), sw);
            int inkTipColor = snapActive ? 0xFFFFFFFF : profile.activeStrokeColor();
            drawInkTipIndicator(gui, smoothedX, smoothedY, inkTipColor, sw);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Input handling
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2) {
            panning = true;
            lastPanMouseX = mouseX;
            lastPanMouseY = mouseY;
            return true;
        }

        if (readOnly || inputLocked) return super.mouseClicked(mouseX, mouseY, button);

        if (button == 0 && isInsideCanvas(mouseX, mouseY)) {
            Vector2f start = clampToShape(logX(mouseX), logY(mouseY));
            smoothedX = start.x;
            smoothedY = start.y;
            resetSnapState(start.x, start.y);
            pointStore.beginStroke(start);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 2 && panning) {
            panX -= (float)(mouseX - lastPanMouseX) / (displayScale * zoom);
            panY -= (float)(mouseY - lastPanMouseY) / (displayScale * zoom);
            lastPanMouseX = mouseX;
            lastPanMouseY = mouseY;
            clampPan();
            return true;
        }

        if (readOnly || inputLocked) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        if (button == 0 && pointStore.isDrawing()) {
            Vector2f raw = clampToShape(logX(mouseX), logY(mouseY));

            // ── Lazy-Mouse smoothing (canvas space) ─────────────────────────
            float factor = profile.strokeSmoothingFactor();
            smoothedX += (raw.x - smoothedX) * factor;
            smoothedY += (raw.y - smoothedY) * factor;
            Vector2f pt = new Vector2f(smoothedX, smoothedY);

            // ── Angle-Snap ──────────────────────────────────────────────────
            List<Vector2f> active = pointStore.activeStroke();
            if (isAngleSnapEnabled() && active != null && !active.isEmpty()) {
                pt = applyAngleSnap(active.getLast(), pt);
            }

            // ── Dead Zone (canvas pixels) ───────────────────────────────────
            if (active != null && !active.isEmpty()) {
                Vector2f last = active.getLast();
                float ddx = pt.x - last.x;
                float ddy = pt.y - last.y;
                float dz  = Config.POINT_DEAD_ZONE_PIXELS.get().floatValue();
                if (ddx * ddx + ddy * ddy >= dz * dz) {
                    pointStore.addPoint(pt);
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) {
            panning = false;
            return true;
        }
        if (button == 0 && pointStore.isDrawing()) {
            Vector2f release = clampToShape(logX(mouseX), logY(mouseY));
            smoothedX = release.x;
            smoothedY = release.y;
            pointStore.addPoint(release);
            List<Vector2f> committed = pointStore.finishStroke();
            resetSnapState(release.x, release.y);
            snapActive = false;

            // ── Trigger phase: closure + encapsulation gate ─────────────────
            // Only activate if the stroke the player JUST released is part of the
            // detected ring chain — otherwise a paper that already contained a
            // closed ring would fire on every unrelated stroke.
            if (!readOnly && !inputLocked && isTriggerPhaseEnabled() && committed != null) {
                int justReleasedStrokeId = pointStore.strokes().size() - 1;
                Optional<TriggerEvaluator.TriggerResult> trig = TriggerEvaluator.evaluate(
                        pointStore.strokes(),
                        Config.SNAP_EPSILON_PIXELS.get().floatValue(),
                        Config.CLOSURE_EPSILON_PIXELS.get().floatValue(),
                        canvasSize.width(),
                        canvasSize.height());
                if (trig.isPresent() && trig.get().ringStrokeIds().contains(justReleasedStrokeId)) {
                    activationRingStrokeIds = List.copyOf(trig.get().ringStrokeIds());
                    inputLocked = true;       // execution lock: canvas rejects further input
                    this.onClose();           // commit + save via existing save path
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideDisplay(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        // Record the canvas point under the cursor before zooming.
        float cx = logX(mouseX);
        float cy = logY(mouseY);
        zoom = Math.clamp(zoom + (scrollY > 0 ? 1f : -1f), ZOOM_MIN, ZOOM_MAX);
        // Adjust pan so the canvas point under the cursor stays fixed on screen.
        panX = cx - (float)(mouseX - displayX) / (displayScale * zoom);
        panY = cy - (float)(mouseY - displayY) / (displayScale * zoom);
        clampPan();
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (isInsideCanvas(mouseX, mouseY) && !readOnly && !inputLocked) {
            ResourceLocation customCursor = profile.cursorSprite();
            if (customCursor != null) {
                setCursorFromSprite(customCursor, profile.cursorHotspotX(), profile.cursorHotspotY());
            } else {
                setSystemCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
            }
        } else {
            setSystemCursor(GLFW.GLFW_ARROW_CURSOR);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Subclass hooks
    // ════════════════════════════════════════════════════════════════════════════

    /** Override to {@code false} to suppress spell-trigger detection (e.g. debug screens). */
    protected boolean isTriggerPhaseEnabled() { return true; }

    /** Clears all committed strokes and any in-progress stroke. */
    public void clearStrokes() { pointStore.clear(); }

    // ════════════════════════════════════════════════════════════════════════════
    // Angle-Snap  (all coords are canvas space)
    // ════════════════════════════════════════════════════════════════════════════

    private boolean isAngleSnapEnabled() {
        return profile.angleSnapEnabled() && Config.ANGLE_SNAP_ENABLED.get();
    }

    private void resetSnapState(float x, float y) {
        snapActive      = false;
        snapConsistency = 0;
        snapAngleDeg    = -1.0;
        snapOriginX     = x;
        snapOriginY     = y;
    }

    private Vector2f applyAngleSnap(Vector2f prev, Vector2f pt) {
        float dx = pt.x - prev.x;
        float dy = pt.y - prev.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < SNAP_MIN_SEGMENT) {
            return snapActive ? projectOnSnapAxis(pt) : pt;
        }

        double headingDeg = Math.toDegrees(Math.atan2(dy, dx));
        if (headingDeg < 0) headingDeg += 360.0;

        double nearest45  = Math.round(headingDeg / 45.0) * 45.0;
        double deviation  = Math.abs(headingDeg - nearest45);
        if (deviation > 180.0) deviation = 360.0 - deviation;

        double threshold = Config.ANGLE_SNAP_THRESHOLD_DEGREES.get();

        if (deviation <= threshold) {
            if (nearest45 == snapAngleDeg) {
                snapConsistency++;
                if (snapConsistency >= SNAP_MIN_CONSISTENCY) {
                    snapActive = true;
                    return projectOnSnapAxis(pt);
                }
            } else {
                snapAngleDeg    = nearest45;
                snapConsistency = 1;
                snapOriginX     = prev.x;
                snapOriginY     = prev.y;
                snapActive      = false;
            }
        } else {
            snapConsistency = 0;
            snapAngleDeg    = -1.0;
            snapActive      = false;
            snapOriginX     = pt.x;
            snapOriginY     = pt.y;
        }

        return snapActive ? projectOnSnapAxis(pt) : pt;
    }

    private Vector2f projectOnSnapAxis(Vector2f pt) {
        double snapRad = Math.toRadians(snapAngleDeg);
        float  axisX   = (float) Math.cos(snapRad);
        float  axisY   = (float) Math.sin(snapRad);
        float  dot     = (pt.x - snapOriginX) * axisX + (pt.y - snapOriginY) * axisY;
        return clampToShape(snapOriginX + dot * axisX, snapOriginY + dot * axisY);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Coordinate helpers
    // ════════════════════════════════════════════════════════════════════════════

    /** Returns {@code true} if the screen-space point is inside the display rectangle. */
    private boolean isInsideDisplay(double sx, double sy) {
        return !(sx >= displayX) || !(sx < displayX + displayW)
                || !(sy >= displayY) || !(sy < displayY + displayH);
    }

    /** Returns {@code true} if the screen-space point maps to a valid canvas coordinate (shape-aware). */
    private boolean isInsideCanvas(double sx, double sy) {
        if (isInsideDisplay(sx, sy)) return false;
        float cx = logX(sx), cy = logY(sy);
        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            float dcx = cx - canvasSize.width()  / 2f;
            float dcy = cy - canvasSize.height() / 2f;
            float r   = Math.min(canvasSize.width(), canvasSize.height()) / 2f;
            return dcx * dcx + dcy * dcy <= r * r;
        }
        return cx >= 0 && cx <= canvasSize.width() && cy >= 0 && cy <= canvasSize.height();
    }

    /** Clamps a canvas-space point to the drawable region (rect or circle). */
    private Vector2f clampToShape(float cx, float cy) {
        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            float centerX = canvasSize.width()  / 2f;
            float centerY = canvasSize.height() / 2f;
            float radius  = Math.min(canvasSize.width(), canvasSize.height()) / 2f;
            float dx = cx - centerX, dy = cy - centerY;
            float lenSq = dx * dx + dy * dy;
            if (lenSq > radius * radius) {
                if (lenSq == 0f) return new Vector2f(centerX, centerY);
                float inv = 1f / (float) Math.sqrt(lenSq);
                return new Vector2f(centerX + dx * inv * radius, centerY + dy * inv * radius);
            }
            return new Vector2f(cx, cy);
        }
        return new Vector2f(
                Math.clamp(cx, 0f, (float) canvasSize.width()),
                Math.clamp(cy, 0f, (float) canvasSize.height()));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Drawing primitives  (take canvas-space inputs; transform to screen internally)
    // ════════════════════════════════════════════════════════════════════════════

    private void drawScreenSprite(GuiGraphics gui) {
        ResourceLocation sprite = profile.screenSprite();
        if (sprite == null) return;
        int pad = 12;
        int sx = (int) scrX(0) - pad;
        int sy = (int) scrY(0) - pad;
        int sw = (int)(canvasSize.width()  * displayScale * zoom) + pad * 2;
        int sh = (int)(canvasSize.height() * displayScale * zoom) + pad * 2;
        gui.blit(sprite, sx, sy, 0, 0, sw, sh,
                Math.max(1, profile.screenSpriteWidth()),
                Math.max(1, profile.screenSpriteHeight()));
    }

    private void drawCanvasBackground(GuiGraphics gui, int color) {
        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            int cx = (int) scrX(canvasSize.width()  / 2f);
            int cy = (int) scrY(canvasSize.height() / 2f);
            int r  = (int)(Math.min(canvasSize.width(), canvasSize.height()) / 2.0f * displayScale * zoom);
            for (int dy = -r; dy <= r; dy++) {
                int span = (int) Math.sqrt((double) r * r - (double) dy * dy);
                gui.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
            }
            return;
        }
        int sx = (int) scrX(0),                  sy = (int) scrY(0);
        int ex = (int) scrX(canvasSize.width()),  ey = (int) scrY(canvasSize.height());
        gui.fill(sx, sy, ex, ey, color);
    }

    private void drawBorder(GuiGraphics gui) {
        int thickness = Math.max(1, profile.borderThickness());
        int color     = profile.canvasBorderColor();

        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            int cx = (int) scrX(canvasSize.width()  / 2f);
            int cy = (int) scrY(canvasSize.height() / 2f);
            int r  = (int)(Math.min(canvasSize.width(), canvasSize.height()) / 2.0f * displayScale * zoom);
            for (int ring = 0; ring < thickness; ring++) {
                int rr = r - ring;
                if (rr < 0) break;
                for (int i = 0; i < 360; i++) {
                    double rad = Math.toRadians(i);
                    int px = cx + (int) Math.round(rr * Math.cos(rad));
                    int py = cy + (int) Math.round(rr * Math.sin(rad));
                    gui.fill(px, py, px + 1, py + 1, color);
                }
            }
            return;
        }
        int x1 = (int) scrX(0),                  y1 = (int) scrY(0);
        int x2 = (int) scrX(canvasSize.width()),  y2 = (int) scrY(canvasSize.height());
        gui.fill(x1, y1 - thickness, x2, y1,              color);
        gui.fill(x1, y2,             x2, y2 + thickness,  color);
        gui.fill(x1 - thickness, y1, x1, y2,              color);
        gui.fill(x2, y1, x2 + thickness, y2,              color);
    }

    private void drawStroke(GuiGraphics gui, List<Vector2f> pts, int color, int strokeWidth) {
        if (pts.size() < 2) {
            if (!pts.isEmpty()) {
                Vector2f p = pts.getFirst();
                int px = Math.max(1, Math.round(strokeWidth * displayScale * zoom));
                int sx0 = displayX + (int) Math.round((p.x - panX) * displayScale * zoom);
                int sy0 = displayY + (int) Math.round((p.y - panY) * displayScale * zoom);
                gui.fill(sx0, sy0, sx0 + px, sy0 + px, color);
            }
            return;
        }
        for (int i = 1; i < pts.size(); i++) {
            Vector2f a = pts.get(i - 1), b = pts.get(i);
            drawPixelLine(gui, (int) a.x, (int) a.y, (int) b.x, (int) b.y, color, strokeWidth);
        }
    }

    /** Draws the ink-tip square at the current smoothed canvas position. */
    private void drawInkTipIndicator(GuiGraphics gui, float cx, float cy, int color, int strokeWidth) {
        int px = Math.max(1, Math.round(strokeWidth * displayScale * zoom));
        int sx0 = displayX + (int) Math.round((cx - panX) * displayScale * zoom);
        int sy0 = displayY + (int) Math.round((cy - panY) * displayScale * zoom);
        gui.fill(sx0, sy0, sx0 + px, sy0 + px, color);
    }

    /**
     * Bresenham line rasteriser in canvas space.
     * Each Bresenham step fills a {@code px × px} screen square where
     * {@code px = max(1, round(strokeWidth × scale))} — this prevents gaps at low zoom
     * where a single canvas pixel would otherwise round to zero screen pixels.
     */
    private void drawPixelLine(GuiGraphics gui, int cx0, int cy0, int cx1, int cy1, int color, int strokeWidth) {
        int dx  = Math.abs(cx1 - cx0), dy = -Math.abs(cy1 - cy0);
        int sx  = cx0 < cx1 ? 1 : -1,  sy = cy0 < cy1 ? 1 : -1;
        int err = dx + dy;
        float scale = displayScale * zoom;
        int px = Math.max(1, Math.round(strokeWidth * scale));
        while (true) {
            int sx0 = displayX + (int) Math.round((cx0 - panX) * scale);
            int sy0 = displayY + (int) Math.round((cy0 - panY) * scale);
            gui.fill(sx0, sy0, sx0 + px, sy0 + px, color);
            if (cx0 == cx1 && cy0 == cy1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; cx0 += sx; }
            if (e2 <= dx) { err += dx; cy0 += sy; }
        }
    }

    /**
     * Draws a faint pixel grid overlay when canvas pixels are large enough to distinguish.
     * Mimics Aseprite's grid: helps the user see individual canvas pixels when zoomed in.
     */
    private void drawPixelGrid(GuiGraphics gui) {
        int px = (int) Math.round(displayScale * zoom);
        if (px < GRID_THRESHOLD_PX) return;

        int gridColor = 0x22000000;

        // Visible canvas range (canvas-space ints)
        int startCX = Math.max(0, (int) panX);
        int endCX   = Math.min(canvasSize.width(),  (int) Math.ceil(panX + canvasSize.width()  / zoom));
        int startCY = Math.max(0, (int) panY);
        int endCY   = Math.min(canvasSize.height(), (int) Math.ceil(panY + canvasSize.height() / zoom));

        for (int cx = startCX; cx <= endCX; cx++) {
            int sx = (int) scrX(cx);
            if (sx >= displayX && sx <= displayX + displayW)
                gui.fill(sx, displayY, sx + 1, displayY + displayH, gridColor);
        }
        for (int cy = startCY; cy <= endCY; cy++) {
            int sy = (int) scrY(cy);
            if (sy >= displayY && sy <= displayY + displayH)
                gui.fill(displayX, sy, displayX + displayW, sy + 1, gridColor);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Cursor management
    // ════════════════════════════════════════════════════════════════════════════

    public enum CursorPattern { CROSSHAIR_WHITE, CROSSHAIR_RED, CROSSHAIR_BLUE, PAINTBRUSH, PENCIL }

    private void setSystemCursor(int glfwShape) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return;
        long cursor = GLFW.glfwCreateStandardCursor(glfwShape);
        if (cursor != 0L) {
            GLFW.glfwSetCursor(window, cursor);
            activeCursorKey = null;
        }
    }

    private void setCursorFromSprite(ResourceLocation spriteLocation, int hotspotX, int hotspotY) {
        String key = spriteLocation + "#scale=" + CURSOR_SCALE;
        if (key.equals(activeCursorKey)) return;

        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return;

        long cursor = CURSOR_CACHE.computeIfAbsent(key,
                k -> loadTextureAsCursor(spriteLocation, hotspotX, hotspotY));

        if (cursor != 0L) {
            GLFW.glfwSetCursor(window, cursor);
            activeCursorKey = key;
        }
    }

    private static long loadTextureAsCursor(ResourceLocation loc, int hotspotX, int hotspotY) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (resource.isEmpty()) {
                System.err.println("[Cursor] Resource not found: " + loc);
                return createCursorFromPattern(CursorPattern.CROSSHAIR_WHITE);
            }

            try (NativeImage img = NativeImage.read(resource.get().open())) {
                int w  = img.getWidth(), h  = img.getHeight();
                int sc = Math.max(1, CURSOR_SCALE);
                int sw = Math.max(1, w * sc), sh = Math.max(1, h * sc);

                ByteBuffer buf = MemoryUtil.memAlloc(sw * sh * 4);
                for (int y = 0; y < sh; y++) {
                    int sy = y / sc;
                    for (int x = 0; x < sw; x++) {
                        int px = img.getPixelRGBA(x / sc, sy);
                        int r = px & 0xFF, g = (px >> 8) & 0xFF,
                            b = (px >> 16) & 0xFF, a = (px >> 24) & 0xFF;
                        buf.putInt((a << 24) | (b << 16) | (g << 8) | r);
                    }
                }
                buf.flip();

                GLFWImage glfwImg = GLFWImage.create();
                glfwImg.width(sw).height(sh).pixels(buf);

                int hx = hotspotX < 0 ? sw / 2 : hotspotX * sc;
                int hy = hotspotY < 0 ? sh / 2 : hotspotY * sc;
                long cursor = GLFW.glfwCreateCursor(glfwImg, hx, hy);
                MemoryUtil.memFree(buf);

                if (cursor == 0L) System.err.println("[Cursor] GLFW failed to create cursor from: " + loc);
                return cursor;
            }
        } catch (IOException e) {
            System.err.println("[Cursor] IO error loading " + loc + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Cursor] Error loading " + loc + ": " + e.getMessage());
        }
        return createCursorFromPattern(CursorPattern.CROSSHAIR_WHITE);
    }

    private static void cleanupCursors() {
        CURSOR_CACHE.values().forEach(c -> { if (c != 0L) GLFW.glfwDestroyCursor(c); });
        CURSOR_CACHE.clear();
    }

    public static long createCursorFromPattern(CursorPattern pattern) {
        return switch (pattern) {
            case CROSSHAIR_WHITE -> createCrosshairCursor(0xFFFFFFFF, 0xFF0000FF);
            case CROSSHAIR_RED   -> createCrosshairCursor(0xFFFF0000, 0xFF00FF00);
            case CROSSHAIR_BLUE  -> createCrosshairCursor(0xFF0000FF, 0xFFFFFF00);
            case PAINTBRUSH      -> createPaintbrushCursor();
            case PENCIL          -> createPencilCursor();
        };
    }

    public static long createCrosshairCursor(int lineColor, int centerColor) {
        int size = 32;
        ByteBuffer px = MemoryUtil.memAlloc(size * size * 4);
        int half = size / 2;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int dx = Math.abs(x - half), dy = Math.abs(y - half);
            if      (dx == 0 && dy == 0)                    px.putInt(centerColor);
            else if ((dx < 2 || dy < 2) && (dx + dy < 10)) px.putInt(lineColor);
            else                                             px.putInt(0);
        }
        px.flip();
        GLFWImage img = GLFWImage.create().width(size).height(size).pixels(px);
        long c = GLFW.glfwCreateCursor(img, half, half);
        MemoryUtil.memFree(px);
        return c;
    }

    public static long createPaintbrushCursor() {
        int size = 32;
        ByteBuffer px = MemoryUtil.memAlloc(size * size * 4);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int edge = Math.min(Math.min(x, y), Math.min(size - x - 1, size - y - 1));
            if      (edge < 3 && y < 24)                      px.putInt(0xFFFFFFFF);
            else if (y >= 24 && y < 28 && x >= 14 && x < 18) px.putInt(0xFF8B4513);
            else                                               px.putInt(0);
        }
        px.flip();
        GLFWImage img = GLFWImage.create().width(size).height(size).pixels(px);
        long c = GLFW.glfwCreateCursor(img, 8, 2);
        MemoryUtil.memFree(px);
        return c;
    }

    public static long createPencilCursor() {
        int size = 32;
        ByteBuffer px = MemoryUtil.memAlloc(size * size * 4);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (x >= 14 && x <= 17 && y >= 2 && y <= 24)
                px.putInt(y <= 20 ? 0xFFFFCC66 : 0xFF000000);
            else
                px.putInt(0);
        }
        px.flip();
        GLFWImage img = GLFWImage.create().width(size).height(size).pixels(px);
        long c = GLFW.glfwCreateCursor(img, 15, 24);
        MemoryUtil.memFree(px);
        return c;
    }
}
