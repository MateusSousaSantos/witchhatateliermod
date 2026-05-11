package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
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
 * <h3>Sealed permitted types</h3>
 * <ul>
 *   <li>{@link RoundPaperProfile}  — circular canvas for round spell-papers</li>
 *   <li>{@link SpellPaperProfile}  — rectangular canvas for flat spell-papers</li>
 *   <li>{@link FallbackProfile}    — sensible defaults driven by global config</li>
 * </ul>
 * To add a new item type, create a {@code non-sealed record MyItemProfile implements CanvasProfile}
 * in its own file; this interface does not need to be modified.
 */
public sealed interface CanvasProfile
        permits CanvasProfile.RoundPaperProfile,
                CanvasProfile.SpellPaperProfile,
                CanvasProfile.FallbackProfile {

    // ── Input shape ─────────────────────────────────────────────────────────────

    /** The drawable region shape for this canvas. */
    enum Shape { RECTANGLE, CIRCLE }

    // ── Required accessors ──────────────────────────────────────────────────────

    /** I18n key for the screen title shown above the canvas. */
    String titleKey();

    /** I18n key for the "read-only" label shown below the canvas when not editable. */
    String readOnlyKey();

    /** Fraction of min(screenW, screenH) used as the canvas side length. */
    float canvasFraction();

    /** Drawable region shape. */
    Shape inputShape();

    /** ARGB background fill when the canvas is editable. */
    int canvasBgColor();

    /** ARGB background fill when the canvas is read-only. */
    int canvasBgReadOnlyColor();

    /** ARGB colour of the canvas border ring/rect. */
    int canvasBorderColor();

    /** Thickness (pixels) of the canvas border. */
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
     * Background sprite drawn behind the canvas, or {@code null} for none.
     * Typically, the parchment / paper texture.
     */
    @Nullable ResourceLocation screenSprite();

    /** Pixel width of the {@link #screenSprite()} source image. */
    int screenSpriteWidth();

    /** Pixel height of the {@link #screenSprite()} source image. */
    int screenSpriteHeight();

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
     * Circular canvas profile for round spell-papers.
     * Hotspot is centred on the cursor texture.
     */
    record RoundPaperProfile() implements CanvasProfile {
        private static final ResourceLocation SPRITE =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/blank.png");
        private static final ResourceLocation CURSOR =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/cursor.png");

        @Override public String titleKey()            { return "screen.witchhatatelier.gesture_canvas.paper"; }
        @Override public String readOnlyKey()         { return "screen.witchhatatelier.gesture_canvas.paper.read_only"; }
        @Override public float  canvasFraction()      { return 0.55f; }
        @Override public Shape  inputShape()          { return Shape.CIRCLE; }
        @Override public int    canvasBgColor()       { return 0xFFFCFCF2; }
        @Override public int    canvasBgReadOnlyColor(){ return 0xFFF3F3FF; }
        @Override public int    canvasBorderColor()   { return 0xFFE9EAEB; }
        @Override public int    borderThickness()     { return 5; }
        @Override public int    strokeColor()         { return 0xFF000000; }
        @Override public int    activeStrokeColor()   { return 0xFFCF31C2; }
        @Override public float  strokeSmoothingFactor(){ return 0.5f; }
        @Override public boolean angleSnapEnabled()   { return true; }
        @Override public ResourceLocation screenSprite()     { return SPRITE; }
        @Override public int    screenSpriteWidth()   { return 16; }
        @Override public int    screenSpriteHeight()  { return 16; }
        @Override public ResourceLocation cursorSprite() { return CURSOR; }
        /** Hotspot at image centre (cursor texture is assumed square). */
        @Override public int cursorHotspotX()         { return -1; } // -1 = derive from image centre at load time
        @Override public int cursorHotspotY()         { return -1; }
    }

    /**
     * Rectangular canvas profile for flat spell-papers.
     */
    record SpellPaperProfile() implements CanvasProfile {
        private static final ResourceLocation SPRITE =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/blank.png");
        private static final ResourceLocation CURSOR =
                ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/cursor.png");

        @Override public String titleKey()            { return "screen.witchhatatelier.gesture_canvas.spell_paper"; }
        @Override public String readOnlyKey()         { return "screen.witchhatatelier.gesture_canvas.spell_paper.read_only"; }
        @Override public float  canvasFraction()      { return 0.55f; }
        @Override public Shape  inputShape()          { return Shape.RECTANGLE; }
        @Override public int    canvasBgColor()       { return 0xFFFCFCF2; }
        @Override public int    canvasBgReadOnlyColor(){ return 0xFFF3F3FF; }
        @Override public int    canvasBorderColor()   { return 0xFFE9EAEB; }
        @Override public int    borderThickness()     { return 5; }
        @Override public int    strokeColor()         { return 0xFF000000; }
        @Override public int    activeStrokeColor()   { return 0xFFCF31C2; }
        @Override public float  strokeSmoothingFactor(){ return 0.5f; }
        @Override public boolean angleSnapEnabled()   { return true; }
        @Override public ResourceLocation screenSprite()     { return SPRITE; }
        @Override public int    screenSpriteWidth()  { return 16; }
        @Override public int    screenSpriteHeight() { return 16; }
        @Override public ResourceLocation cursorSprite() { return CURSOR; }
        @Override public int cursorHotspotX() { return -1; }
        @Override public int cursorHotspotY() { return -1; }
    }

    /**
     * Fallback profile — reads smoothing and snap toggles from the global config
     * so players can tune them without code changes.
     */
    record FallbackProfile() implements CanvasProfile {
        @Override public String titleKey()            { return "screen.witchhatatelier.gesture_canvas"; }
        @Override public String readOnlyKey()         { return "screen.witchhatatelier.gesture_canvas.read_only"; }
        @Override public float  canvasFraction()      { return 0.65f; }
        @Override public Shape  inputShape()          { return Shape.RECTANGLE; }
        @Override public int    canvasBgColor()       { return 0xFFFFFFF3; }
        @Override public int    canvasBgReadOnlyColor(){ return 0xFFF3F3FF; }
        @Override public int    canvasBorderColor()   { return 0xFFFFFFFF; }
        @Override public int    borderThickness()     { return 1; }
        @Override public int    strokeColor()         { return 0xFF000000; }
        @Override public int    activeStrokeColor()   { return 0xFFCF31C2; }
        @Override public float  strokeSmoothingFactor(){ return Config.STROKE_SMOOTHING_FACTOR.get().floatValue(); }
        @Override public boolean angleSnapEnabled()   { return Config.ANGLE_SNAP_ENABLED.get(); }
        @Override public @Nullable ResourceLocation screenSprite() { return null; }
        @Override public int    screenSpriteWidth()  { return 16; }
        @Override public int    screenSpriteHeight() { return 16; }
    }
}


