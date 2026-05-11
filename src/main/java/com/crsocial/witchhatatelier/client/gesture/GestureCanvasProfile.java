package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.Config;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Visual and text configuration for a gesture canvas screen.
 *
 * <p>{@link #strokeSmoothingFactor} controls Lazy-Mouse exponential smoothing for this screen:
 * {@code P_new = P_old + (Cursor - P_old) × factor}.
 * Range [0.0, 1.0] — {@code 1.0} means no smoothing, {@code 0.15} is a heavy brush feel.</p>
 */
public record GestureCanvasProfile(
        String titleKey,
        String readOnlyKey,
        @Nullable ResourceLocation screenSprite,
        int screenSpriteWidth,
        int screenSpriteHeight,
        CanvasInputShape inputShape,
        float canvasFraction,
        int canvasBgColor,
        int canvasBgReadOnlyColor,
        int canvasBorderColor,
        int strokeColor,
        int activeStrokeColor,
        int borderThickness,
        /**
         * Lazy-Mouse smoothing factor for this screen.
         * {@code 1.0f} = no smoothing (raw cursor). {@code 0.5f} = moderate drag.
         */
        float strokeSmoothingFactor,
        /**
         * Whether angle snapping is active for this screen.
         * When {@code true} and the global config toggle is also on, stroke segments
         * whose heading is within {@link net.crsocial.witchhatatelier.Config#ANGLE_SNAP_THRESHOLD_DEGREES}
         * of a cardinal / diagonal axis (every 45°) are projected onto that axis.
         */
        boolean angleSnapEnabled
) {
    public static GestureCanvasProfile fallback() {
        return new GestureCanvasProfile(
                "screen.witchhatatelier.gesture_canvas",
                "screen.witchhatatelier.gesture_canvas.read_only",
                null,
                16,
                16,
                CanvasInputShape.RECTANGLE,
                0.65f,
                0xFFFFFFF3,
                0xFFF3F3FF,
                0xFFFFFFFF,
                0xFF000000,
                0xFFCF31C2,
                1,
                // Fallback reads from the global config so players can tune it without code changes.
                Config.STROKE_SMOOTHING_FACTOR.get().floatValue(),
                Config.ANGLE_SNAP_ENABLED.get()
        );
    }
}
