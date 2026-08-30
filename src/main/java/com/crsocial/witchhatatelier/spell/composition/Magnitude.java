package com.crsocial.witchhatatelier.spell.composition;

/**
 * The resolved "how strong/how long" numbers a {@code Form}/{@code Effect}
 * reads off {@link CastContext} — see {@code docs/new_spell_engine.md} §9:
 * <b>size → power/aoe</b>, <b>neatness → duration only</b>, <b>same-element
 * stacking → power</b>. {@link #REFERENCE} is the un-amplified baseline
 * (reference size/quality, no stacking, one occurrence) cost scaling is
 * measured against.
 *
 * @param power       overall strength scalar (blast radius, lift height, pull force, …),
 *                     {@code 1.0} at reference size with no stacking
 * @param aoe         area-of-effect scalar, {@code 1.0} at reference size
 * @param duration    channel/effect duration scalar, driven by neatness only — never by size
 * @param quality     the compiled element's recognizer quality in {@code [0, 1]}, carried
 *                     through for ops that want to fade in/out with how well it was drawn
 * @param repetitions how many times a {@link StackingMode#REPETITION} op should run / how
 *                     many targets it should hit; {@code 1} for anything else
 */
public record Magnitude(float power, float aoe, float duration, float quality, int repetitions) {

    /** Reference magnitude: no size/stacking amplification, one occurrence. */
    public static final Magnitude REFERENCE = new Magnitude(1f, 1f, 1f, 1f, 1);

    public Magnitude withPower(float newPower) {
        return new Magnitude(newPower, aoe, duration, quality, repetitions);
    }

    public Magnitude scaledBy(float multiplier) {
        return new Magnitude(power * multiplier, aoe * multiplier, duration, quality, repetitions);
    }

    public Magnitude withRepetitions(int newRepetitions) {
        return new Magnitude(power, aoe, duration, quality, newRepetitions);
    }
}
