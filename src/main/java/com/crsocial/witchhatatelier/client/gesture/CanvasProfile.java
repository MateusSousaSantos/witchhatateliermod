package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.PaperType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Describes the visual and behavioral configuration for a gesture canvas screen.
 *
 * <p>Implement this interface (preferably as a {@code record}) for each item type
 * that needs its own canvas look and feel. No shared constants file needs to be
 * modified — just create a new implementation and register it in
 * {@link GestureCanvasClient} (add a branch in its private {@code resolveProfile} method).</p>
 *
 * <h3>Input-shape enum</h3>
 * {@link Shape} is declared here because it is exclusively a profile concern.
 *
 * <h3>Known implementations</h3>
 * <ul>
 *   <li>{@link PaperTypeProfile} — primary profile; derives shape and canvas size from a {@link PaperType}</li>
 *   <li>{@link FallbackProfile}  — sensible defaults driven by global config</li>
 * </ul>
 * To add a new item type, create a record or class that implements this interface.
 */
public interface CanvasProfile {

    // ── Input shape ─────────────────────────────────────────────────────────────

    /** The drawable region shape for this canvas. */
    enum Shape { RECTANGLE, CIRCLE }

    // ── Required accessors ──────────────────────────────────────────────────────

    /** I18n key for the screen title shown above the canvas. */
    String titleKey();

    /** I18n key for the "read-only" label shown below the canvas when not editable. */
    String readOnlyKey();

    /**
     * Fixed logical canvas size in canvas pixels (e.g. {@code 256×256}).
     * Zoom magnifies these pixels on screen but never changes the resolution.
     */
    CanvasSize canvasSize();

    /** Drawable region shape. */
    Shape inputShape();

    /** ARGB background fill when the canvas is editable. */
    int canvasBgColor();

    /** ARGB background fill when the canvas is read-only. */
    int canvasBgReadOnlyColor();

    /** ARGB colour of the canvas border ring/rect. */
    int canvasBorderColor();

    /** Thickness of the canvas border in logical canvas pixels; scaled to screen pixels at render time. */
    int borderThickness();

    /** ARGB colour of completed strokes. */
    int strokeColor();

    /** ARGB colour of the stroke currently being drawn. */
    int activeStrokeColor();

    /**
     * Lazy-Mouse exponential smoothing factor.
     * Formula: {@code P_new = P_old + (Cursor − P_old) × factor}.
     * {@code 1.0} = no smoothing; {@code 0.15} = heavy drag.
     */
    float strokeSmoothingFactor();

    /**
     * Whether angle-snapping (45° locking) is active for this profile.
     * Global config toggle {@link Config#ANGLE_SNAP_ENABLED} is also honoured.
     */
    boolean angleSnapEnabled();

    /**
     * Stroke width in canvas pixels. Rendered as {@code max(1, round(strokeWidth × scale))}
     * screen pixels so strokes never disappear at low zoom.
     * Default: {@code 2}.
     */
    default int strokeWidth() { return 2; }

    /**
     * GUI sprite id for the parchment/paper texture drawn behind the canvas, or {@code null}
     * for none. Resolved against the GUI sprite atlas ({@code assets/<ns>/textures/gui/sprites/});
     * a sibling {@code .mcmeta} controls scaling, so {@code nine_slice} keeps the border crisp as
     * the canvas grows. Drawn with {@code GuiGraphics.blitSprite}, which derives the size from the
     * atlas — no source width/height needed.
     */
    @Nullable ResourceLocation screenSprite();

    /**
     * ResourceLocation of the custom cursor PNG drawn over the canvas.
     * Return {@code null} to use the system crosshair.
     */
    default @Nullable ResourceLocation cursorSprite() { return null; }

    /**
     * Hotspot X within the cursor image (0 = left edge).
     * Used when {@link #cursorSprite()} is non-null.
     */
    default int cursorHotspotX() { return 0; }

    /**
     * Hotspot Y within the cursor image (0 = top edge).
     * Used when {@link #cursorSprite()} is non-null.
     */
    default int cursorHotspotY() { return 0; }

    // ── Named profile records ───────────────────────────────────────────────────

    /**
     * Primary profile for all mod paper items.
     * Canvas shape and size are derived directly from the item's {@link PaperType};
     * colours, stroke settings, and cursor are shared across all paper variants.
     */
    record PaperTypeProfile(PaperType paperType) implements CanvasProfile {
        private static final ResourceLocation SPRITE =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "canvas/paper");
        private static final ResourceLocation CURSOR =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/cursor.png");

        @Override public String     titleKey()            { return "screen.witchhatatelier.gesture_canvas." + paperType.getId(); }
        @Override public String     readOnlyKey()         { return titleKey() + ".read_only"; }
        @Override public CanvasSize canvasSize()          { return paperType.getCanvasSize(); }
        @Override public Shape      inputShape()          { return paperType.isRound() ? Shape.CIRCLE : Shape.RECTANGLE; }
        @Override public int        canvasBgColor()       { return 0xFFFCFCF2; }
        @Override public int        canvasBgReadOnlyColor(){ return 0xFFF3F3FF; }
        @Override public int        canvasBorderColor()   { return 0xFFE9EAEB; }
        @Override public int        borderThickness()     { return 20; }
        @Override public int        strokeColor()         { return 0xFF000000; }
        @Override public int        activeStrokeColor()   { return 0xFFCF31C2; }
        @Override public float      strokeSmoothingFactor(){ return 1f; }
        @Override public boolean    angleSnapEnabled()    { return false; }
        @Override public ResourceLocation screenSprite()  { return SPRITE; }
        @Override public ResourceLocation cursorSprite()  { return CURSOR; }
        @Override public int        cursorHotspotX()      { return -1; }
        @Override public int        cursorHotspotY()      { return -1; }
    }

    /**
     * Profile for the {@link com.crsocial.witchhatatelier.blocks.CanvasPressurePlate}.
     * Stone-etching aesthetic — a grey slab with dark engraved strokes — so the plate's
     * canvas reads as carved stone rather than parchment. Square 1024² canvas, no parchment
     * sprite behind it.
     */
    record CanvasPlateProfile() implements CanvasProfile {
        private static final ResourceLocation CURSOR =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/cursor.png");

        @Override public String     titleKey()            { return "screen.witchhatatelier.gesture_canvas.canvas_plate"; }
        @Override public String     readOnlyKey()         { return titleKey() + ".read_only"; }
        @Override public CanvasSize canvasSize()          { return new CanvasSize(1024, 1024); }
        @Override public Shape      inputShape()          { return Shape.RECTANGLE; }
        @Override public int        canvasBgColor()       { return 0xFFB8B8B8; }
        @Override public int        canvasBgReadOnlyColor(){ return 0xFFA0A0A0; }
        @Override public int        canvasBorderColor()   { return 0xFF6E6E6E; }
        @Override public int        borderThickness()     { return 20; }
        @Override public int        strokeColor()         { return 0xFF1A1A1A; }
        @Override public int        activeStrokeColor()   { return 0xFFCF31C2; }
        @Override public float      strokeSmoothingFactor(){ return 1f; }
        @Override public boolean    angleSnapEnabled()    { return false; }
        @Override public @Nullable ResourceLocation screenSprite() { return null; }
        @Override public ResourceLocation cursorSprite()  { return CURSOR; }
        @Override public int        cursorHotspotX()      { return -1; }
        @Override public int        cursorHotspotY()      { return -1; }
    }

    /**
     * Fallback profile — reads smoothing and snap toggles from the global config.
     * Used when no known item type is in hand (e.g. debug/editor scenarios).
     */
    record FallbackProfile() implements CanvasProfile {
        @Override public String     titleKey()            { return "screen.witchhatatelier.gesture_canvas"; }
        @Override public String     readOnlyKey()         { return "screen.witchhatatelier.gesture_canvas.read_only"; }
        @Override public CanvasSize canvasSize()          { return new CanvasSize(256, 256); }
        @Override public Shape      inputShape()          { return Shape.RECTANGLE; }
        @Override public int        canvasBgColor()       { return 0xFFFFFFF3; }
        @Override public int        canvasBgReadOnlyColor(){ return 0xFFF3F3FF; }
        @Override public int        canvasBorderColor()   { return 0xFFFFFFFF; }
        @Override public int        borderThickness()     { return 1; }
        @Override public int        strokeColor()         { return 0xFF000000; }
        @Override public int        activeStrokeColor()   { return 0xFFCF31C2; }
        @Override public float      strokeSmoothingFactor(){ return Config.STROKE_SMOOTHING_FACTOR.get().floatValue(); }
        @Override public boolean    angleSnapEnabled()    { return Config.ANGLE_SNAP_ENABLED.get(); }
        @Override public @Nullable ResourceLocation screenSprite() { return null; }
    }

    /**
     * Profile for the debug template recorder screen.
     * Dark background with green strokes — clearly signals "dev tool" vs normal paper.
     * No smoothing and no angle-snap so recorded points reflect exact cursor positions.
     */
    record DebugProfile() implements CanvasProfile {
        @Override public String     titleKey()             { return "screen.witchhatatelier.debug_template"; }
        @Override public String     readOnlyKey()          { return "screen.witchhatatelier.debug_template.read_only"; }
        @Override public CanvasSize canvasSize()           { return new CanvasSize(256, 256); }
        @Override public Shape      inputShape()           { return Shape.RECTANGLE; }
        @Override public int        canvasBgColor()        { return 0xFF1A1A2E; }
        @Override public int        canvasBgReadOnlyColor(){ return 0xFF0F0F1A; }
        @Override public int        canvasBorderColor()    { return 0xFF00FF88; }
        @Override public int        borderThickness()      { return 2; }
        @Override public int        strokeColor()          { return 0xFF00FF88; }
        @Override public int        activeStrokeColor()    { return 0xFF88FFCC; }
        @Override public float      strokeSmoothingFactor(){ return 1f; }
        @Override public boolean    angleSnapEnabled()     { return false; }
        @Override public @Nullable ResourceLocation screenSprite() { return null; }
    }
}
