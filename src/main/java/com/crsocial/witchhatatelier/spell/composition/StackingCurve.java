package com.crsocial.witchhatatelier.spell.composition;

/**
 * The single amplification curve behind {@link StackingMode#MAGNITUDE} —
 * shared by a sign's own repeat count (§5) and the element's same-type
 * repeat count, {@code sigilStack} (§9's "same-element stacking → power").
 * Diminishing returns: the first repeat matters most, later ones taper off,
 * so drawing a sign ten times can't trivially dwarf drawing it once.
 */
public final class StackingCurve {

    /** How much each additional occurrence contributes, before diminishing returns. */
    private static final float GROWTH_PER_EXTRA = 0.35f;

    private StackingCurve() {
    }

    /**
     * @param count occurrences of the sign (or element repeats), {@code >= 1}
     * @return a multiplier {@code >= 1.0} to apply to whatever baseline scalar this
     *         sign amplifies (height, radius, power, …)
     */
    public static float multiplierFor(int count) {
        int extra = Math.max(0, count - 1);
        return 1f + GROWTH_PER_EXTRA * (float) Math.sqrt(extra);
    }
}
