package com.crsocial.witchhatatelier.spell.compiler;

import java.util.Locale;
import java.util.Optional;

/**
 * The five canonical elements a spell can carry — the "what" axis of the
 * compositional spell model (see {@code docs/spell_pipeline.md}). Successor to
 * {@link SigilType}, which this type will replace once the compiler and
 * meaning layer are cut over to composition (see the composition-engine
 * migration plan); until then both types exist side by side and this one is
 * only consumed by the additive {@code spell/composition/} package.
 */
public enum ElementType {
    EARTH,
    AIR,
    WATER,
    FIRE,
    LIGHT;

    /**
     * Maps a recognizer {@code spell_name} to an element type. Returns empty for
     * names that are not canonical elements (forms, effects, ring templates, or
     * {@code unknown}).
     */
    public static Optional<ElementType> fromSpellName(String spellName) {
        if (spellName == null) return Optional.empty();
        return switch (spellName.toLowerCase(Locale.ROOT)) {
            case "earth" -> Optional.of(EARTH);
            case "air"   -> Optional.of(AIR);
            case "water" -> Optional.of(WATER);
            case "fire"  -> Optional.of(FIRE);
            case "light" -> Optional.of(LIGHT);
            default       -> Optional.empty();
        };
    }
}
