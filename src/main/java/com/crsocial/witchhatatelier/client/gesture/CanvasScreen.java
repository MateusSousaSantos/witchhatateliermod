package com.crsocial.witchhatatelier.client.gesture;

import com.mojang.blaze3d.platform.NativeImage;
import com.crsocial.witchhatatelier.Config;
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
import java.util.function.Consumer;

/**
 * Single, self-contained gesture canvas screen.
 *
 * <p>Responsibilities (all in one place):</p>
 * <ul>
 *   <li><b>UI / lifecycle</b> — {@link #init}, {@link #render}, {@link #tick}, {@link #onClose}</li>
 *   <li><b>Input handling</b> — mouse click/drag/release/move with Lazy-Mouse and angle-snap</li>
 *   <li><b>Cursor management</b> — GLFW custom-cursor loading, caching, and cleanup</li>
 *   <li><b>Drawing primitives</b> — background, border, strokes, ink-tip indicator</li>
 *   <li><b>Read / write API</b> — {@link #loadPoints} and {@link #savePoints}</li>
 * </ul>
 *
 * <p>All stroke data is delegated to a {@link CanvasPointStore}.
 * All visual and behavioural configuration comes from a {@link CanvasProfile}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CanvasScreen extends Screen {

    // ════════════════════════════════════════════════════════════════════════════
    // Fields
    // ════════════════════════════════════════════════════════════════════════════

    // ── Profile & mode ──────────────────────────────────────────────────────────
    private final CanvasProfile profile;
    private final boolean readOnly;
    private final Consumer<List<GesturePoint>> saveHandler;

    // ── Stroke data ─────────────────────────────────────────────────────────────
    private final CanvasPointStore pointStore = new CanvasPointStore();

    /** Points to preload once canvas bounds are known (set in {@link #init}). */
    private final List<GesturePoint> preloadedPoints;

    // ── Canvas geometry (set in init/resize) ────────────────────────────────────
    private int canvasX, canvasY, canvasW, canvasH;

    // ── Lazy-Mouse state ─────────────────────────────────────────────────────────
    /** Smoothed ink position; lags behind the actual cursor. */
    private float smoothedX, smoothedY;

    // ── Angle-Snap state ─────────────────────────────────────────────────────────
    private boolean snapActive      = false;
    private float   snapOriginX     = 0f, snapOriginY = 0f;
    private int     snapConsistency = 0;
    private double  snapAngleDeg    = -1.0;

    private static final float SNAP_MIN_SEGMENT   = 2f;
    private static final int   SNAP_MIN_CONSISTENCY = 8;

    // ── Animation ────────────────────────────────────────────────────────────────
    private int clearAnimTimer = 0;

    // ── GLFW cursor cache ────────────────────────────────────────────────────────
    /** Screen-lifetime cache: sprite-path → GLFW cursor handle. */
    private static final Map<String, Long> CURSOR_CACHE = new HashMap<>();
    private static final int CURSOR_SCALE = 3;
    private String activeCursorKey = null;

    // ════════════════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════════════════

    public CanvasScreen(CanvasProfile profile,
                        List<GesturePoint> preloadedPoints,
                        boolean editable,
                        Consumer<List<GesturePoint>> saveHandler,
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
        int canvasSize = (int) (Math.min(width, height) * profile.canvasFraction());
        canvasW = canvasSize;
        canvasH = canvasSize;
        canvasX = (width  - canvasW) / 2;
        canvasY = (height - canvasH) / 2;

        // Restore previously saved drawing, de-normalising [0,1] → pixel space.
        loadPoints(preloadedPoints);
    }

    @Override
    public void tick() {
        super.tick();
        if (clearAnimTimer > 0) clearAnimTimer--;
    }

    @Override
    public void onClose() {
        // Persist strokes when closing an editable canvas that has content.
        if (!readOnly && !pointStore.isEmpty()) {
            saveHandler.accept(savePoints());
        }
        cleanupCursors();
        super.onClose();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Read / Write API
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Restores strokes from a saved point list.
     * Canvas bounds must already be set (call after {@link #init}).
     *
     * @param points normalised points (may be {@code null} or empty)
     */
    public void loadPoints(List<GesturePoint> points) {
        pointStore.denormalize(points, canvasX, canvasY, canvasW, canvasH);
    }

    /**
     * Converts all current strokes to a normalised [0,1] point list.
     *
     * @return flat {@link GesturePoint} list ready for network / NBT serialisation
     */
    public List<GesturePoint> savePoints() {
        return pointStore.normalize(canvasX, canvasY, canvasW, canvasH);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Rendering
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        renderCanvas(gui);

        // Title
        gui.drawCenteredString(font,
                Component.translatable(profile.titleKey()),
                canvasX + canvasW / 2,
                canvasY - font.lineHeight - 4,
                0xFFFFFFFF);

        // Read-only badge
        if (readOnly) {
            gui.drawCenteredString(font,
                    Component.translatable(profile.readOnlyKey()),
                    canvasX + canvasW / 2,
                    canvasY + canvasH + 4,
                    0xFFAAAAAA);
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Transparent background — the world stays visible behind the canvas.
        gui.fill(0, 0, width, height, 0x00000000);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Canvas composite ────────────────────────────────────────────────────────

    private void renderCanvas(GuiGraphics gui) {
        drawScreenSprite(gui);

        int bg = readOnly ? profile.canvasBgReadOnlyColor() : profile.canvasBgColor();
        drawCanvasBackground(gui, bg);
        drawBorder(gui);

        // Committed strokes
        for (List<Vector2f> stroke : pointStore.strokes()) {
            drawStroke(gui, stroke, profile.strokeColor());
        }

        // Active stroke + ink-tip indicator
        List<Vector2f> active = pointStore.activeStroke();
        if (!readOnly && active != null && !active.isEmpty()) {
            drawStroke(gui, active, profile.activeStrokeColor());
            int inkTipColor = snapActive ? 0xFFFFFFFF : profile.activeStrokeColor();
            drawInkTipIndicator(gui, smoothedX, smoothedY, inkTipColor);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Input handling
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (readOnly) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && isInsideCanvas(mouseX, mouseY)) {
            Vector2f start = clampToShape(mouseX, mouseY);
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
        if (readOnly) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (button == 0 && pointStore.isDrawing()) {
            Vector2f raw = clampToShape(mouseX, mouseY);

            // ── Lazy-Mouse smoothing ────────────────────────────────────────
            float factor = profile.strokeSmoothingFactor();
            smoothedX += (raw.x - smoothedX) * factor;
            smoothedY += (raw.y - smoothedY) * factor;
            Vector2f pt = new Vector2f(smoothedX, smoothedY);

            // ── Angle-Snap ──────────────────────────────────────────────────
            List<Vector2f> active = pointStore.activeStroke();
            if (isAngleSnapEnabled() && active != null && !active.isEmpty()) {
                pt = applyAngleSnap(active.getLast(), pt);
            }

            // ── Dead Zone ───────────────────────────────────────────────────
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
        if (button == 0 && pointStore.isDrawing()) {
            // Snap ink position to exact release point — stroke ends where mouse lifts.
            Vector2f release = clampToShape(mouseX, mouseY);
            smoothedX = release.x;
            smoothedY = release.y;
            pointStore.addPoint(release);
            pointStore.finishStroke();

            resetSnapState(release.x, release.y);
            snapActive = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (isInsideCanvas(mouseX, mouseY) && !readOnly) {
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
    // Angle-Snap
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

    /**
     * Consistency-based angle snapper.
     * Evaluates per-event segment headings; locks to an axis only after
     * {@link #SNAP_MIN_CONSISTENCY} consecutive segments all point that way.
     */
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

    /** Projects {@code pt} onto the active snap axis via dot-product, then clamps. */
    private Vector2f projectOnSnapAxis(Vector2f pt) {
        double snapRad  = Math.toRadians(snapAngleDeg);
        float  axisX    = (float) Math.cos(snapRad);
        float  axisY    = (float) Math.sin(snapRad);
        float  dot      = (pt.x - snapOriginX) * axisX + (pt.y - snapOriginY) * axisY;
        return clampToShape(snapOriginX + dot * axisX, snapOriginY + dot * axisY);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Coordinate helpers
    // ════════════════════════════════════════════════════════════════════════════

    private boolean isInsideCanvas(double x, double y) {
        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            float cx = canvasX + canvasW / 2.0f;
            float cy = canvasY + canvasH / 2.0f;
            float r  = Math.min(canvasW, canvasH) / 2.0f;
            float dx = (float) x - cx;
            float dy = (float) y - cy;
            return dx * dx + dy * dy <= r * r;
        }
        return x >= canvasX && x <= canvasX + canvasW
                && y >= canvasY && y <= canvasY + canvasH;
    }

    private Vector2f clampToShape(double x, double y) {
        return clampToShape((float) x, (float) y);
    }

    private Vector2f clampToShape(float cx, float cy) {
        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            float centerX = canvasX + canvasW / 2.0f;
            float centerY = canvasY + canvasH / 2.0f;
            float radius  = Math.min(canvasW, canvasH) / 2.0f;
            float dx = cx - centerX;
            float dy = cy - centerY;
            float lenSq = dx * dx + dy * dy;
            if (lenSq > radius * radius) {
                if (lenSq == 0f) return new Vector2f(centerX, centerY);
                float inv = 1.0f / (float) Math.sqrt(lenSq);
                cx = centerX + dx * inv * radius;
                cy = centerY + dy * inv * radius;
            }
            return new Vector2f(cx, cy);
        }
        cx = (float) Math.clamp((double) cx, canvasX, canvasX + canvasW);
        cy = (float) Math.clamp((double) cy, canvasY, canvasY + canvasH);
        return new Vector2f(cx, cy);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Drawing primitives
    // ════════════════════════════════════════════════════════════════════════════

    private void drawScreenSprite(GuiGraphics gui) {
        ResourceLocation sprite = profile.screenSprite();
        if (sprite == null) return;
        int pad = 12;
        gui.blit(sprite,
                canvasX - pad, canvasY - pad, 0, 0,
                canvasW + pad * 2, canvasH + pad * 2,
                Math.max(1, profile.screenSpriteWidth()),
                Math.max(1, profile.screenSpriteHeight()));
    }

    private void drawCanvasBackground(GuiGraphics gui, int color) {
        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            int cx = canvasX + canvasW / 2;
            int cy = canvasY + canvasH / 2;
            int r  = Math.min(canvasW, canvasH) / 2;
            for (int dy = -r; dy <= r; dy++) {
                int span = (int) Math.sqrt((double) r * r - (double) dy * dy);
                gui.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
            }
            return;
        }
        gui.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, color);
    }

    private void drawBorder(GuiGraphics gui) {
        int thickness = Math.max(1, profile.borderThickness());
        int color     = profile.canvasBorderColor();

        if (profile.inputShape() == CanvasProfile.Shape.CIRCLE) {
            int cx = canvasX + canvasW / 2;
            int cy = canvasY + canvasH / 2;
            int r  = Math.min(canvasW, canvasH) / 2;
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
        int x1 = canvasX, y1 = canvasY, x2 = canvasX + canvasW, y2 = canvasY + canvasH;
        gui.fill(x1, y1 - thickness, x2, y1,          color);
        gui.fill(x1, y2,            x2, y2 + thickness, color);
        gui.fill(x1 - thickness, y1, x1, y2,            color);
        gui.fill(x2, y1, x2 + thickness, y2,            color);
    }

    private void drawStroke(GuiGraphics gui, List<Vector2f> pts, int color) {
        if (pts.size() < 2) {
            if (!pts.isEmpty()) {
                Vector2f p = pts.getFirst();
                gui.fill((int) p.x - 1, (int) p.y - 1, (int) p.x + 1, (int) p.y + 1, color);
            }
            return;
        }
        for (int i = 1; i < pts.size(); i++) {
            Vector2f a = pts.get(i - 1), b = pts.get(i);
            drawLine(gui, (int) a.x, (int) a.y, (int) b.x, (int) b.y, color);
        }
    }

    private void drawInkTipIndicator(GuiGraphics gui, float x, float y, int color) {
        int ix = (int) x, iy = (int) y, r = 1;
        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.sqrt((double) r * r - (double) dy * dy);
            gui.fill(ix - span, iy + dy, ix + span + 1, iy + dy + 1, color);
        }
    }

    /** Bresenham line rasteriser — 2-pixel-wide alias-free line. */
    private static void drawLine(GuiGraphics gui, int x0, int y0, int x1, int y1, int color) {
        int dx  = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx  = x0 < x1 ? 1 : -1,  sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            gui.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Cursor management
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Predefined programmatic cursor shapes (fallbacks when no PNG sprite exists).
     * Pass to {@link #createCursorFromPattern}.
     */
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

    /**
     * Sets a cursor from a PNG sprite, caching the GLFW handle for reuse.
     *
     * @param spriteLocation texture resource location
     * @param hotspotX       hotspot X in the unscaled image; use {@code -1} for centre
     * @param hotspotY       hotspot Y in the unscaled image; use {@code -1} for centre
     */
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

    /**
     * Loads a PNG texture as a GLFW cursor, scaling it by {@link #CURSOR_SCALE}.
     * Returns {@code 0L} on any failure; uses {@link CursorPattern#CROSSHAIR_WHITE} as fallback.
     */
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
                        // Minecraft stores ABGR; GLFW expects RGBA.
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

    /** Destroys all cached GLFW cursors. Called from {@link #onClose}. */
    private static void cleanupCursors() {
        CURSOR_CACHE.values().forEach(c -> { if (c != 0L) GLFW.glfwDestroyCursor(c); });
        CURSOR_CACHE.clear();
    }

    // ── Programmatic cursor builders ─────────────────────────────────────────────

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
            if      (dx == 0 && dy == 0)                           px.putInt(centerColor);
            else if ((dx < 2 || dy < 2) && (dx + dy < 10))        px.putInt(lineColor);
            else                                                    px.putInt(0);
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
            if      (edge < 3 && y < 24)                           px.putInt(0xFFFFFFFF);
            else if (y >= 24 && y < 28 && x >= 14 && x < 18)      px.putInt(0xFF8B4513);
            else                                                    px.putInt(0);
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

