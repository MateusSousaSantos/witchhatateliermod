package com.crsocial.witchhatatelier.spell.composition;

/**
 * The one counting concept in the composition engine (see {@code
 * docs/new_spell_engine.md} §5) — how a sign's drawn-occurrence count feeds
 * the resolved spell. Every {@code Form}/{@code Effect} declares exactly one:
 *
 * <ul>
 *   <li>{@code MAGNITUDE} — the count feeds a curve → a size/reach/strength scalar
 *       ({@link StackingCurve}).</li>
 *   <li>{@code REPETITION} — the count = how many times the op runs, or how many
 *       targets it hits.</li>
 *   <li>{@code MODIFIER} — the count doesn't amplify; the sign is a behavioural
 *       flag (extra copies may only reposition, never strengthen).</li>
 * </ul>
 */
public enum StackingMode {
    MAGNITUDE,
    REPETITION,
    MODIFIER
}
