package com.crsocial.witchhatatelier.client.gesture;

import com.mojang.blaze3d.platform.NativeImage;
import com.crsocial.witchhatatelier.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all canvas-based drawing screens.
 *
 * <p>Provides shared stroke capture, rendering helpers, and coordinate utilities.
 * Subclasses override {@link #onStrokeFinished(List)} to react when the user
 * completes a stroke, and can call {@link #buildNormalizedPointCloud()} to get
 * the normalized [0,1] point cloud from all strokes.</p>
 */
public abstract class AbstractCanvasScreen extends Screen {

    // ── State ──────────────────────────────────────────────────────────────────

    /** All completed strokes (raw pixel coordinates). */
    protected final List<List<Vector2f>> strokes = new ArrayList<>();

    /** The stroke currently being drawn (null when idle). */
    protected List<Vector2f> activeStroke = null;

    // ── Lazy-Mouse (exponential smoothing) state ────────────────────────────────

    /**
     * The current "ink" position after smoothing.
     * Reset to the raw cursor on every stroke start; drifts toward the cursor
     * during drag via {@link #getSmoothingFactor()}.
     */
    private float smoothedX = 0f, smoothedY = 0f;

    // ── Angle-Snap state ────────────────────────────────────────────────────────

    /**
     * True while the current drag event is locked to a cardinal/diagonal axis.
     * Drives the white ink-tip tint so the player sees the snap is active.
     */
    private boolean snapActive = false;

    /**
     * Pixel position of the last point <em>before</em> a snap sequence began.
     * All snapped points during the active sequence are projected along the
     * snap axis from this origin, so the "committed straight" segment starts
     * exactly where the user was when the consistency threshold was reached.
     */
    private float snapOriginX = 0f, snapOriginY = 0f;

    /**
     * How many consecutive drag events have been heading toward the same
     * 45° axis.  Snapping only activates once this reaches
     * {@link #ANGLE_SNAP_MIN_CONSISTENCY}.
     */
    private int snapConsistency = 0;

    /**
     * The 45° cardinal/diagonal that is currently being tracked
     * ({@code -1} means no axis tracked yet for this stroke).
     */
    private double snapAngleDeg = -1.0;

    // Canvas bounds (computed in init/resize)
    protected int canvasX, canvasY, canvasW, canvasH;

    /** Item-specific visual profile. */
    protected final GestureCanvasProfile profile;

    /** Optional preloaded points to restore on init. */
    private final List<GesturePoint> preloadedPoints;

    /** Cache of GLFW cursors loaded from custom sprites. */
    private static final Map<String, Long> CURSOR_CACHE = new HashMap<>();

    /** Scale multiplier for custom cursor textures (1 = original size). */
    private static final int CUSTOM_CURSOR_SCALE = 3;

    /** Current active cursor type (null for system cursors, or sprite path for custom). */
    private String activeCursorType = null;

    // ── Construction ───────────────────────────────────────────────────────────

    protected AbstractCanvasScreen(Component title, GestureCanvasProfile profile, List<GesturePoint> preloadedPoints) {
        super(title);
        this.profile = profile;
        this.preloadedPoints = preloadedPoints;
    }

    // ── Screen lifecycle ───────────────────────────────────────────────────────

    @Override
    protected void init() {
        int canvasSize = (int) (Math.min(width, height) * profile.canvasFraction());
        canvasW = canvasSize;
        canvasH = canvasSize;
        canvasX = (width - canvasW) / 2;
        canvasY = (height - canvasH) / 2;

        // Restore previously saved strokes, de-normalizing [0,1] → pixel space.
        if (preloadedPoints != null && !preloadedPoints.isEmpty()) {
            strokes.clear();
            int maxStroke = preloadedPoints.stream().mapToInt(GesturePoint::strokeID).max().orElse(0);
            for (int s = 0; s <= maxStroke; s++) {
                strokes.add(new ArrayList<>());
            }
            for (GesturePoint p : preloadedPoints) {
                float px = p.x() * canvasW + canvasX;
                float py = p.y() * canvasH + canvasY;
                strokes.get(p.strokeID()).add(new Vector2f(px, py));
            }
            strokes.removeIf(List::isEmpty);
        }
    }

    // ── Abstract / overridable hooks ────────────────────────────────────────────

    /** Whether drawing input is disabled. Default: false. */
    protected boolean isReadOnly() {
        return false;
    }

    /**
     * Returns the Lazy-Mouse exponential smoothing factor applied during drag.
     *
     * <p>Formula: {@code P_new = P_old + (Cursor - P_old) × factor}</p>
     * <ul>
     *   <li>{@code 1.0} – no smoothing, ink snaps to cursor</li>
     *   <li>{@code 0.5} – moderate drag (typical default)</li>
     *   <li>{@code 0.15} – heavy brush, strong jitter elimination</li>
     * </ul>
     *
     * <p>Reads {@link GestureCanvasProfile#strokeSmoothingFactor()} for this screen's profile.
     * Subclasses may override to force a fixed value independent of the profile.</p>
     */
    protected float getSmoothingFactor() {
        return profile.strokeSmoothingFactor();
    }

    /**
     * Returns whether angle snapping is active for this screen.
     *
     * <p>Snapping fires only when <em>both</em> this method returns {@code true}
     * <em>and</em> the global config toggle {@link Config#ANGLE_SNAP_ENABLED} is on.
     * Subclasses may override to force snapping on/off independent of the profile.</p>
     */
    protected boolean isAngleSnapEnabled() {
        return profile.angleSnapEnabled() && Config.ANGLE_SNAP_ENABLED.get();
    }


    /**
     * Called when a stroke is finalized (mouse released with >1 point).
     * Subclasses override to add spell-activation logic, auto-test, etc.
     */
    protected void onStrokeFinished(List<Vector2f> finishedStroke) {
        // no-op by default
    }

    // ── Input handling ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isReadOnly()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && isInsideCanvas(mouseX, mouseY)) {
            Vector2f start = clampToCanvasShape(mouseX, mouseY);
            // Reset smoothed position to the exact click point so the stroke
            // always originates at the cursor (no initial lag).
            smoothedX = start.x;
            smoothedY = start.y;
            snapActive = false;
            snapConsistency = 0;
            snapAngleDeg = -1.0;
            snapOriginX = start.x;
            snapOriginY = start.y;
            activeStroke = new ArrayList<>();
            activeStroke.add(start);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isReadOnly()) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (button == 0 && activeStroke != null) {
            Vector2f raw = clampToCanvasShape(mouseX, mouseY);

            // ── Lazy-Mouse: exponential smoothing ──────────────────────────
            // P_new = P_old + (Cursor - P_old) × factor
            float factor = getSmoothingFactor();
            smoothedX += (raw.x - smoothedX) * factor;
            smoothedY += (raw.y - smoothedY) * factor;
            Vector2f pt = new Vector2f(smoothedX, smoothedY);
            // ───────────────────────────────────────────────────────────────

            // ── Angle-Snap ─────────────────────────────────────────────────
            // After smoothing, optionally project pt onto the nearest 45° axis.
            // Consistency is measured per drag-event segment so that curves and
            // complex shapes are never affected — only deliberate straight runs.
            if (isAngleSnapEnabled() && !activeStroke.isEmpty()) {
                pt = applyAngleSnap(activeStroke.getLast(), pt);
            }
            // ───────────────────────────────────────────────────────────────

            // ── Dead Zone ──────────────────────────────────────────────────
            // Only commit a new point when the ink tip has moved far enough
            // from the last recorded point.  The visual smoothedX/Y updates
            // every frame regardless so the ink-tip indicator stays fluid.
            Vector2f last = activeStroke.getLast();
            float ddx = pt.x - last.x;
            float ddy = pt.y - last.y;
            float deadZone = Config.POINT_DEAD_ZONE_PIXELS.get().floatValue();
            if (ddx * ddx + ddy * ddy >= deadZone * deadZone) {
                activeStroke.add(pt);
            }
            // ───────────────────────────────────────────────────────────────
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && activeStroke != null) {
            if (activeStroke.size() > 1) {
                // Snap the smoothed ink position to the exact release point so
                // the stroke always ends where the user lifted the mouse.
                Vector2f releasePoint = clampToCanvasShape(mouseX, mouseY);
                smoothedX = releasePoint.x;
                smoothedY = releasePoint.y;
                activeStroke.add(releasePoint);

                strokes.add(activeStroke);
                onStrokeFinished(activeStroke);
            }
            activeStroke = null;
            snapActive = false;
            snapConsistency = 0;
            snapAngleDeg = -1.0;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Updates the cursor appearance based on mouse position.
     * Shows a crosshair cursor when over the canvas, arrow elsewhere.
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (isInsideCanvas(mouseX, mouseY) && !isReadOnly()) {
            ResourceLocation customCursor = getCanvasCursorSprite();
            if (customCursor != null) {
                setCursorFromSprite(customCursor);
            } else {
                setCursorShape(GLFW.GLFW_CROSSHAIR_CURSOR);
            }
        } else {
            setCursorShape(GLFW.GLFW_ARROW_CURSOR);
        }
    }

    /**
     * Returns the ResourceLocation of a custom cursor sprite to use on the canvas,
     * or null to use the default system cursor. Override this to provide your own cursor.
     *
     * <p>The sprite should be a square image (e.g., 32x32) where the top-left pixel
     * is treated as the hot-spot (click point).</p>
     *
     * @return the ResourceLocation of a cursor sprite, or null for default
     */
    protected ResourceLocation getCanvasCursorSprite() {
        return null;
    }

    /**
     * Sets the cursor shape using standard GLFW cursor shapes.
     * Common shapes: GLFW_ARROW_CURSOR, GLFW_CROSSHAIR_CURSOR, GLFW_HAND_CURSOR, etc.
     *
     * @param cursorShape the GLFW cursor shape constant
     */
    protected void setCursorShape(int cursorShape) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        if (window != 0L) {
            // Create and set a standard cursor shape
            long cursor = GLFW.glfwCreateStandardCursor(cursorShape);
            if (cursor != 0L) {
                GLFW.glfwSetCursor(window, cursor);
                this.activeCursorType = null;
            }
        }
    }

    /**
     * Sets the cursor from a custom sprite/texture.
     * The sprite is cached for performance, so it won't be recreated every frame.
     *
     * @param spriteLocation the ResourceLocation of the cursor sprite
     */
    protected void setCursorFromSprite(ResourceLocation spriteLocation) {
        String spritePath = spriteLocation + "#scale=" + CUSTOM_CURSOR_SCALE;

        // Check if we already have this cursor cached
        if (spritePath.equals(activeCursorType)) {
            return; // Already set
        }

        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        if (window == 0L) return;

        // Try to get cached cursor, or create a new one
        long cursor = CURSOR_CACHE.getOrDefault(spritePath, 0L);
        if (cursor == 0L) {
            cursor = loadCursorFromSprite(spriteLocation);
            if (cursor != 0L) {
                CURSOR_CACHE.put(spritePath, cursor);
            }
        }

        if (cursor != 0L) {
            GLFW.glfwSetCursor(window, cursor);
            this.activeCursorType = spritePath;
        }
    }

    /**
     * Loads a custom cursor. Attempts to load from the texture file at the ResourceLocation.
     * Falls back to a crosshair pattern if loading fails.
     *
     * <p>To create custom cursors, you can:</p>
     * <ul>
     *   <li>Place a PNG texture file at your ResourceLocation path</li>
     *   <li>Override this method to customize loading behavior</li>
     *   <li>Use {@link #createCursorFromPattern(CursorPattern)} for predefined patterns</li>
     * </ul>
     *
     * @param spriteLocation the ResourceLocation of the texture (e.g., "modid:textures/gui/cursor.png")
     * @return the GLFW cursor handle, or 0L if loading failed
     */
    private long loadCursorFromSprite(ResourceLocation spriteLocation) {
        try {
            // Try to load the texture as a PNG
            long cursor = loadTextureAsCursor(spriteLocation);
            if (cursor != 0L) {
                return cursor;
            }
        } catch (Exception e) {
            // Fall through to default
        }

        // Fallback: create a custom crosshair pattern
        return createCursorFromPattern(CursorPattern.CROSSHAIR_WHITE);
    }

    /**
     * Loads a PNG texture file and converts it to a GLFW cursor.
     *
     * @param textureLocation the resource location of the PNG file
     * @return the GLFW cursor handle, or 0L if loading failed
     */
    private long loadTextureAsCursor(ResourceLocation textureLocation) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var resourceManager = minecraft.getResourceManager();

            // Try to get the resource
            var resource = resourceManager.getResource(textureLocation);
            if (resource.isEmpty()) {
                System.err.println("[Cursor] Resource not found: " + textureLocation);
                return 0L;
            }

            try (NativeImage nativeImage = NativeImage.read(resource.get().open())) {
                int width = nativeImage.getWidth();
                int height = nativeImage.getHeight();
                int scale = Math.max(1, CUSTOM_CURSOR_SCALE);
                int scaledWidth = Math.max(1, width * scale);
                int scaledHeight = Math.max(1, height * scale);
                System.out.println("[Cursor] Loaded texture: " + textureLocation + " (" + width + "x" + height + "), scaled to (" + scaledWidth + "x" + scaledHeight + ")");

                // Create RGBA buffer
                ByteBuffer pixelBuffer = MemoryUtil.memAlloc(scaledWidth * scaledHeight * 4);

                // Copy pixel data from the image (ABGR format in Minecraft)
                for (int y = 0; y < scaledHeight; y++) {
                    int srcY = y / scale;
                    for (int x = 0; x < scaledWidth; x++) {
                        int srcX = x / scale;
                        int pixel = nativeImage.getPixelRGBA(srcX, srcY);
                        // Convert from ABGR to RGBA for GLFW
                        int r = pixel & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = (pixel >> 16) & 0xFF;
                        int a = (pixel >> 24) & 0xFF;
                        int rgba = (a << 24) | (b << 16) | (g << 8) | r;
                        pixelBuffer.putInt(rgba);
                    }
                }
                pixelBuffer.flip();

                // Create GLFW image
                GLFWImage glfwImage = GLFWImage.create();
                glfwImage.width(scaledWidth);
                glfwImage.height(scaledHeight);
                glfwImage.pixels(pixelBuffer);

                // Create cursor with hotspot at center
                long cursor = GLFW.glfwCreateCursor(glfwImage, scaledWidth / 2, scaledHeight / 2);
                MemoryUtil.memFree(pixelBuffer);

                if (cursor != 0L) {
                    System.out.println("[Cursor] Successfully created cursor from: " + textureLocation);
                } else {
                    System.err.println("[Cursor] Failed to create GLFW cursor");
                }

                return cursor;
            }

        } catch (IOException e) {
            System.err.println("[Cursor] IO Error loading texture: " + textureLocation);
            System.err.println("[Cursor] " + e.getMessage());
            return 0L;
        } catch (Exception e) {
            System.err.println("[Cursor] Error loading texture: " + textureLocation);
            System.err.println("[Cursor] " + e.getMessage());
            return 0L;
        }
    }

    /**
     * Creates a simple cursor from a custom pattern.
     */
    public enum CursorPattern {
        CROSSHAIR_WHITE,
        CROSSHAIR_RED,
        CROSSHAIR_BLUE,
        PAINTBRUSH,
        PENCIL
    }

    /**
     * Creates a cursor from a predefined pattern.
     *
     * @param pattern the cursor pattern
     * @return the GLFW cursor handle, or 0L if creation failed
     */
    protected static long createCursorFromPattern(CursorPattern pattern) {
        return switch (pattern) {
            case CROSSHAIR_WHITE -> createCrosshairCursor(0xFFFFFFFF, 0xFF0000FF);
            case CROSSHAIR_RED -> createCrosshairCursor(0xFFFF0000, 0xFF00FF00);
            case CROSSHAIR_BLUE -> createCrosshairCursor(0xFF0000FF, 0xFFFFFF00);
            case PAINTBRUSH -> createPaintbrushCursor();
            case PENCIL -> createPencilCursor();
        };
    }

    /**
     * Creates a simple crosshair cursor with custom colors.
     *
     * @param crosshairColor ARGB color for the crosshair lines
     * @param centerColor ARGB color for the center dot
     * @return the GLFW cursor handle
     */
    protected static long createCrosshairCursor(int crosshairColor, int centerColor) {
        int size = 32;
        ByteBuffer pixelData = MemoryUtil.memAlloc(size * size * 4);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int centerX = size / 2;
                int centerY = size / 2;
                int distX = Math.abs(x - centerX);
                int distY = Math.abs(y - centerY);

                // Draw center dot
                if (distX == 0 && distY == 0) {
                    pixelData.putInt(centerColor);
                }
                // Draw horizontal and vertical lines
                else if ((distX < 2 || distY < 2) && (distX + distY < 10)) {
                    pixelData.putInt(crosshairColor);
                } else {
                    pixelData.putInt(0x00000000); // Transparent
                }
            }
        }
        pixelData.flip();

        GLFWImage glfwImage = GLFWImage.create();
        glfwImage.width(size);
        glfwImage.height(size);
        glfwImage.pixels(pixelData);

        long cursor = GLFW.glfwCreateCursor(glfwImage, size / 2, size / 2);
        MemoryUtil.memFree(pixelData);
        return cursor;
    }

    /**
     * Creates a simple paintbrush-like cursor.
     */
    protected static long createPaintbrushCursor() {
        int size = 32;
        ByteBuffer pixelData = MemoryUtil.memAlloc(size * size * 4);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int distFromEdge = Math.min(Math.min(x, y), Math.min(size - x - 1, size - y - 1));

                if (distFromEdge < 3 && y < 24) {
                    // Brush shape
                    pixelData.putInt(0xFFFFFFFF); // White
                } else if (y >= 24 && y < 28 && x >= 14 && x < 18) {
                    // Handle
                    pixelData.putInt(0xFF8B4513); // Brown
                } else {
                    pixelData.putInt(0x00000000); // Transparent
                }
            }
        }
        pixelData.flip();

        GLFWImage glfwImage = GLFWImage.create();
        glfwImage.width(size);
        glfwImage.height(size);
        glfwImage.pixels(pixelData);

        long cursor = GLFW.glfwCreateCursor(glfwImage, 8, 2);
        MemoryUtil.memFree(pixelData);
        return cursor;
    }

    /**
     * Creates a simple pencil-like cursor.
     */
    protected static long createPencilCursor() {
        int size = 32;
        ByteBuffer pixelData = MemoryUtil.memAlloc(size * size * 4);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Simple pencil shape
                if (x >= 14 && x <= 17 && y >= 2 && y <= 24) {
                    if (y <= 20) {
                        pixelData.putInt(0xFFFFCC66); // Light yellow (wood)
                    } else {
                        pixelData.putInt(0xFF000000); // Black (tip)
                    }
                } else {
                    pixelData.putInt(0x00000000); // Transparent
                }
            }
        }
        pixelData.flip();

        GLFWImage glfwImage = GLFWImage.create();
        glfwImage.width(size);
        glfwImage.height(size);
        glfwImage.pixels(pixelData);

        long cursor = GLFW.glfwCreateCursor(glfwImage, 15, 24);
        MemoryUtil.memFree(pixelData);
        return cursor;
    }

    @Override
    public void onClose() {
        // Clean up cached cursors when screen closes
        for (long cursor : CURSOR_CACHE.values()) {
            if (cursor != 0L) {
                GLFW.glfwDestroyCursor(cursor);
            }
        }
        CURSOR_CACHE.clear();
        super.onClose();
    }

    // ── Angle-Snap helper ───────────────────────────────────────────────────────

    /**
     * Minimum per-event segment length (pixels) before we trust the heading.
     * Very slow mouse movements produce near-zero vectors with meaningless angles.
     */
    private static final float ANGLE_SNAP_MIN_SEGMENT = 2f;

    /**
     * Number of consecutive drag events that must all point toward the same 45° axis
     * before snapping activates.  This prevents curves and complex shapes from being
     * accidentally snapped — the player must make a deliberate, sustained straight run.
     */
    private static final int ANGLE_SNAP_MIN_CONSISTENCY = 8;

    /**
     * Consistency-based angle snapper.
     *
     * <p>Unlike a naïve "stroke-start to current-point" approach, this method
     * evaluates <em>per-event segment</em> headings and only locks to an axis after
     * {@link #ANGLE_SNAP_MIN_CONSISTENCY} consecutive drag events have all been heading
     * toward the same cardinal/diagonal.  As soon as the heading drifts more than
     * {@link Config#ANGLE_SNAP_THRESHOLD_DEGREES} away from that axis the snap is
     * released and the consistency counter resets, allowing the next curve segment
     * to flow freely until it in turn becomes consistently straight.</p>
     *
     * <p>Projection uses a dot-product along the snap axis from {@link #snapOriginX}/Y,
     * so the committed straight segment always begins at the point where the consistency
     * threshold was first reached.</p>
     *
     * @param prev the last committed stroke point (pixel space)
     * @param pt   candidate point after Lazy-Mouse smoothing (pixel space)
     * @return the (possibly snapped) point, clamped to the canvas
     */
    private Vector2f applyAngleSnap(Vector2f prev, Vector2f pt) {
        float dx = pt.x - prev.x;
        float dy = pt.y - prev.y;
        float segDist = (float) Math.sqrt(dx * dx + dy * dy);

        // Mouse barely moved — maintain whatever snap state we already have.
        if (segDist < ANGLE_SNAP_MIN_SEGMENT) {
            return snapActive ? projectOnSnapAxis(pt) : pt;
        }

        // Heading of this segment in degrees, normalized to [0, 360).
        double headingDeg = Math.toDegrees(Math.atan2(dy, dx));
        if (headingDeg < 0) headingDeg += 360.0;

        // Nearest multiple of 45°.
        double nearest45 = Math.round(headingDeg / 45.0) * 45.0;
        double deviation = Math.abs(headingDeg - nearest45);
        if (deviation > 180.0) deviation = 360.0 - deviation; // wrap-around fix

        double threshold = Config.ANGLE_SNAP_THRESHOLD_DEGREES.get();

        if (deviation <= threshold) {
            if (nearest45 == snapAngleDeg) {
                // Same cardinal as before — build consistency.
                snapConsistency++;
                if (snapConsistency >= ANGLE_SNAP_MIN_CONSISTENCY) {
                    snapActive = true;
                    return projectOnSnapAxis(pt);
                }
            } else {
                // Switched to a different cardinal — reset and start tracking the new one.
                // Record the anchor as the *previous* point so the straight segment
                // begins exactly where the direction change happened.
                snapAngleDeg = nearest45;
                snapConsistency = 1;
                snapOriginX = prev.x;
                snapOriginY = prev.y;
                snapActive = false;
            }
        } else {
            // Off-axis — release snap and reset tracking.
            snapConsistency = 0;
            snapAngleDeg = -1.0;
            snapActive = false;
            snapOriginX = pt.x;
            snapOriginY = pt.y;
        }

        return snapActive ? projectOnSnapAxis(pt) : pt;
    }

    /**
     * Projects {@code pt} onto the active snap axis via dot-product, then clamps
     * the result back to the canvas.
     *
     * <p>Using a dot-product (not a fixed distance from origin) means subsequent
     * points advance naturally along the axis as the cursor moves forward.</p>
     */
    private Vector2f projectOnSnapAxis(Vector2f pt) {
        double snapRad = Math.toRadians(snapAngleDeg);
        float axisX = (float) Math.cos(snapRad);
        float axisY = (float) Math.sin(snapRad);

        float fromOriginX = pt.x - snapOriginX;
        float fromOriginY = pt.y - snapOriginY;
        float dot = fromOriginX * axisX + fromOriginY * axisY;

        float snappedX = snapOriginX + dot * axisX;
        float snappedY = snapOriginY + dot * axisY;
        return clampToCanvasShape(snappedX, snappedY);
    }


    // ── Normalization utilities ─────────────────────────────────────────────────

    /**
     * Builds a normalized [0,1] point cloud from all current strokes.
     */
    protected List<GesturePoint> buildNormalizedPointCloud() {
        List<GesturePoint> pointCloud = new ArrayList<>();
        for (int s = 0; s < strokes.size(); s++) {
            for (Vector2f v : strokes.get(s)) {
                float nx = (v.x - canvasX) / canvasW;
                float ny = (v.y - canvasY) / canvasH;
                pointCloud.add(new GesturePoint(nx, ny, s));
            }
        }
        return pointCloud;
    }
    // ── Rendering helpers ──────────────────────────────────────────────────────

    protected void renderCanvas(GuiGraphics gui) {
        drawScreenSprite(gui);

        int bg = isReadOnly() ? profile.canvasBgReadOnlyColor() : profile.canvasBgColor();
        drawCanvasBackground(gui, bg);
        drawBorder(gui);

        // Completed strokes
        for (List<Vector2f> stroke : strokes) {
            drawStroke(gui, stroke, profile.strokeColor());
        }

        // Active (in-progress) stroke
        if (!isReadOnly() && activeStroke != null && !activeStroke.isEmpty()) {
            drawStroke(gui, activeStroke, profile.activeStrokeColor());

            // ── Lazy-Mouse indicator ────────────────────────────────────────
            // Draw a small hollow circle at the smoothed ink position so the
            // user can see where the "ink tip" currently is (it lags behind
            // the real cursor, giving visual feedback of the drag).
            // When angle snapping is active the dot turns white so the player
            // knows the stroke is locked to a cardinal/diagonal axis.
            int inkTipColor = snapActive ? 0xFFFFFFFF : profile.activeStrokeColor();
            drawInkTipIndicator(gui, smoothedX, smoothedY, inkTipColor);
            // ────────────────────────────────────────────────────────────────
        }
    }

    /**
     * Draws a small filled dot representing the current smoothed "ink tip" position.
     * This is the Lazy-Mouse visual indicator — distinct from the cursor itself.
     *
     * @param gui   the graphics context
     * @param x     smoothed ink X (pixel space)
     * @param y     smoothed ink Y (pixel space)
     * @param color ARGB color (typically matches the active stroke color)
     */
    protected void drawInkTipIndicator(GuiGraphics gui, float x, float y, int color) {
        int ix = (int) x;
        int iy = (int) y;
        int r = 1; // 3×3 pixel dot

        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.sqrt((r * r - dy * dy));
            gui.fill(ix - span, iy + dy, ix + span + 1, iy + dy + 1, color);
        }
    }

    protected void drawScreenSprite(GuiGraphics gui) {
        ResourceLocation sprite = profile.screenSprite();
        if (sprite == null) return;

        int padding = 12;
        int x = canvasX - padding;
        int y = canvasY - padding;
        int w = canvasW + padding * 2;
        int h = canvasH + padding * 2;
        gui.blit(sprite, x, y, 0, 0, w, h,
                Math.max(1, profile.screenSpriteWidth()),
                Math.max(1, profile.screenSpriteHeight()));
    }

    // ── Coordinate helpers ──────────────────────────────────────────────────────

    protected boolean isInsideCanvas(double x, double y) {
        if (profile.inputShape() == CanvasInputShape.CIRCLE) {
            float cx = canvasX + canvasW / 2.0f;
            float cy = canvasY + canvasH / 2.0f;
            float radius = Math.min(canvasW, canvasH) / 2.0f;
            float dx = (float) x - cx;
            float dy = (float) y - cy;
            return dx * dx + dy * dy <= radius * radius;
        }
        return x >= canvasX && x <= canvasX + canvasW
                && y >= canvasY && y <= canvasY + canvasH;
    }

    protected Vector2f clampToCanvasShape(double x, double y) {
        float cx = (float) x;
        float cy = (float) y;

        if (profile.inputShape() == CanvasInputShape.CIRCLE) {
            float centerX = canvasX + canvasW / 2.0f;
            float centerY = canvasY + canvasH / 2.0f;
            float radius = Math.min(canvasW, canvasH) / 2.0f;

            float dx = cx - centerX;
            float dy = cy - centerY;
            float lenSq = dx * dx + dy * dy;
            float radiusSq = radius * radius;

            if (lenSq > radiusSq) {
                if (lenSq == 0.0f) return new Vector2f(centerX, centerY);
                float invLen = 1.0f / (float) Math.sqrt(lenSq);
                cx = centerX + dx * invLen * radius;
                cy = centerY + dy * invLen * radius;
            }
            return new Vector2f(cx, cy);
        }

        cx = (float) Math.clamp((double) cx, canvasX, canvasX + canvasW);
        cy = (float) Math.clamp((double) cy, canvasY, canvasY + canvasH);
        return new Vector2f(cx, cy);
    }

    // ── Drawing primitives ──────────────────────────────────────────────────────

    protected void drawCanvasBackground(GuiGraphics gui, int color) {
        if (profile.inputShape() == CanvasInputShape.CIRCLE) {
            int centerX = canvasX + canvasW / 2;
            int centerY = canvasY + canvasH / 2;
            int radius = Math.min(canvasW, canvasH) / 2;
            for (int y = -radius; y <= radius; y++) {
                int span = (int) Math.sqrt(radius * radius - y * y);
                gui.fill(centerX - span, centerY + y, centerX + span + 1, centerY + y + 1, color);
            }
            return;
        }
        gui.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, color);
    }

    protected void drawBorder(GuiGraphics gui) {
        int thickness = Math.max(1, profile.borderThickness());
        int color = profile.canvasBorderColor();

        if (profile.inputShape() == CanvasInputShape.CIRCLE) {
            int centerX = canvasX + canvasW / 2;
            int centerY = canvasY + canvasH / 2;
            int radius = Math.min(canvasW, canvasH) / 2;
            for (int ring = 0; ring < thickness; ring++) {
                int ringRadius = radius - ring;
                if (ringRadius < 0) break;
                for (int i = 0; i < 360; i++) {
                    double rad = Math.toRadians(i);
                    int px = centerX + (int) Math.round(ringRadius * Math.cos(rad));
                    int py = centerY + (int) Math.round(ringRadius * Math.sin(rad));
                    gui.fill(px, py, px + 1, py + 1, color);
                }
            }
            return;
        }

        int x1 = canvasX, y1 = canvasY;
        int x2 = canvasX + canvasW, y2 = canvasY + canvasH;
        gui.fill(x1, y1 - thickness, x2, y1, color);
        gui.fill(x1, y2, x2, y2 + thickness, color);
        gui.fill(x1 - thickness, y1, x1, y2, color);
        gui.fill(x2, y1, x2 + thickness, y2, color);
    }

    protected void drawStroke(GuiGraphics gui, List<Vector2f> pts, int color) {
        if (pts.size() < 2) {
            if (!pts.isEmpty()) {
                Vector2f p = pts.getFirst();
                gui.fill((int) p.x - 1, (int) p.y - 1, (int) p.x + 1, (int) p.y + 1, color);
            }
            return;
        }
        for (int i = 1; i < pts.size(); i++) {
            Vector2f a = pts.get(i - 1);
            Vector2f b = pts.get(i);
            drawLine(gui, (int) a.x, (int) a.y, (int) b.x, (int) b.y, color);
        }
    }

    /**
     * Bresenham line rasterizer — draws a 1-pixel-wide line between two points.
     */
    protected static void drawLine(GuiGraphics gui, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            gui.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0x00000000);
    }
}

