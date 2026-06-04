package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.Config;

/**
 * Maps a sigil's normalized drawn size {@code [0, 1]} to a power/reach multiplier.
 *
 * <p>The raw {@code sizeNormalized} (content bbox area ÷ ring bbox area) used to enter
 * the power formula as a bare linear factor floored at {@code 0.1}, so size could only
 * ever <i>reduce</i> a spell — a full-size sigil was ×1.0 and there was no way to draw
 * "big" for more power. This curve fixes that: it is anchored so a <b>reference</b> size
 * still maps to ×1.0 (existing spells barely move), a <b>full</b> size amplifies up to
 * {@link Config#SIZE_POWER_MAX}, and a vanishing size falls toward the old {@code 0.1}
 * floor. The curve is continuous and monotonic across the anchor.</p>
 *
 * <p>{@link #steerMultiplier} reuses the same curve but raises it to
 * {@link Config#DIRECTION_SIZE_EXPONENT} so size weighs more heavily on a directional
 * cast's steering force than on raw power — see {@code ColumnSignBehavior}.</p>
 */
public final class SizeScaling {

    /** Multiplier as size → 0; matches the legacy {@code Math.max(0.1f, size)} floor. */
    private static final float SIZE_FLOOR = 0.1f;

    private SizeScaling() {}

    /**
     * Power/reach multiplier for a normalized size. {@code ref → ×1.0},
     * {@code 1.0 → SIZE_POWER_MAX}, {@code 0 → SIZE_FLOOR}.
     */
    public static float powerMultiplier(float size) {
        float ref = Config.SIZE_POWER_REFERENCE.get().floatValue();
        float max = Config.SIZE_POWER_MAX.get().floatValue();
        float exp = Config.SIZE_POWER_EXPONENT.get().floatValue();
        float s = clamp01(size);
        if (s >= ref) {
            float t = ref >= 1f ? 0f : (s - ref) / (1f - ref);   // 0 at ref → 1 at full size
            return 1f + (max - 1f) * (float) Math.pow(t, exp);
        }
        float t = ref <= 0f ? 1f : s / ref;                      // 0 at size 0 → 1 at ref
        return SIZE_FLOOR + (1f - SIZE_FLOOR) * (float) Math.pow(t, exp);
    }

    /**
     * Steering-force multiplier — the power curve raised to
     * {@link Config#DIRECTION_SIZE_EXPONENT} so a directional cast's skew scales more
     * steeply with size than its power does.
     */
    public static float steerMultiplier(float size) {
        return (float) Math.pow(powerMultiplier(size),
                Config.DIRECTION_SIZE_EXPONENT.get().floatValue());
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
