package com.crsocial.witchhatatelier.spell.meaning;

/**
 * Per-spell magnitude. One overall {@code quality} scales the whole spell
 * uniformly — there is no per-sign quality multiplier. See
 * {@code docs/magic_system/03_meaning_engine.md}.
 *
 * @param power          base × stackingCurve(count) × quality × size
 * @param aoe            area-of-effect multiplier
 * @param quality        overall spell quality in {@code [0, 1]}, taken from the central sigil
 * @param sizeNormalized inscription size in {@code [0, 1]}, from the compiler's {@link com.crsocial.witchhatatelier.spell.compiler.SizeReport}
 */
public record Magnitude(float power, float aoe, float quality, float sizeNormalized) {}
