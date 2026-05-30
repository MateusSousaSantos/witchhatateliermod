package com.crsocial.witchhatatelier.spell.meaning;

import java.util.Locale;

/**
 * How a sign's per-cell magnitude scales with {@code SignBundle.count}.
 * Mirrors the {@code stacking_curve} field of the matrix JSON. See
 * {@code docs/magic_system/03_meaning_engine.md}.
 */
public enum StackingCurve {
    LINEAR,
    LOGARITHMIC,
    CAPPED;

    /** Maximum multiplier applied by {@link #CAPPED} regardless of count. */
    private static final float CAP = 3.0f;

    public float apply(int count) {
        if (count <= 1) return 1.0f;
        return switch (this) {
            case LINEAR      -> count;
            case LOGARITHMIC -> 1.0f + (float) (Math.log(count) / Math.log(2.0));
            case CAPPED      -> Math.min(CAP, count);
        };
    }

    public static StackingCurve parse(String s) {
        if (s == null) return LINEAR;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "logarithmic" -> LOGARITHMIC;
            case "capped"      -> CAPPED;
            default             -> LINEAR;
        };
    }
}
