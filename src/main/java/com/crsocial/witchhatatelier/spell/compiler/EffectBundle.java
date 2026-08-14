package com.crsocial.witchhatatelier.spell.compiler;

import java.util.List;

/**
 * Grouped view of all occurrences of one effect type, derived by {@link
 * SpellGraph#effectsByType()}. The composition engine reads {@link #count} to
 * fold repeat-occurrence stacking into the resolved magnitude.
 */
public record EffectBundle(EffectType type, int count, List<EffectNode> occurrences) {
}
