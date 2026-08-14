package com.crsocial.witchhatatelier.spell.compiler;

import java.util.List;

/**
 * Grouped view of all occurrences of one form type, derived by {@link
 * SpellGraph#formsByType()}. The composition engine reads {@link #count} to
 * fold repeat-occurrence stacking into the resolved magnitude.
 */
public record FormBundle(FormType type, int count, List<FormNode> occurrences) {
}
