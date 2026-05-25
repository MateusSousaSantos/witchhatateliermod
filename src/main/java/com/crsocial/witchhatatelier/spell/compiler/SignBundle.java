package com.crsocial.witchhatatelier.spell.compiler;

import java.util.List;

/**
 * Grouped view of all occurrences of one sign type, derived by
 * {@link SpellGraph#signsByType()}. The meaning engine reads {@link #count}
 * and applies it per the sign's {@link SignType#stackingMode()}.
 */
public record SignBundle(SignType type, int count, List<SignNode> occurrences) {}
