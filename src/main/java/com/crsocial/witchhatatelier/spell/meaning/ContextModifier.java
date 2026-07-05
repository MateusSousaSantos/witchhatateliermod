package com.crsocial.witchhatatelier.spell.meaning;

/**
 * One environmental override from a matrix cell's {@code context_modifiers}: when its
 * {@link #condition} holds at the casting position, its {@link #powerMultiplier} and
 * {@link #aoeMultiplier} fold into that cell's contribution. Multiple matching modifiers
 * on the same cell stack multiplicatively. Parsed by {@link MatrixLoader}, applied in
 * {@link MeaningEngine}.
 *
 * <p>JSON shape (all multipliers default to {@code 1.0}):</p>
 * <pre>
 * "context_modifiers": [
 *   { "when": "raining",     "power": 1.5, "aoe": 1.2 },
 *   { "when": "underground", "power": 1.4 }
 * ]
 * </pre>
 */
public record ContextModifier(ContextCondition condition, float powerMultiplier, float aoeMultiplier) {}
