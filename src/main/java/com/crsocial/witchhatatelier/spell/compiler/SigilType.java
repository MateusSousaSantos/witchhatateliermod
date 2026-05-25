package com.crsocial.witchhatatelier.spell.compiler;

import java.util.Locale;
import java.util.Optional;

/**
 * The five canonical elements a sigil can carry. See
 * {@code docs/magic_system/sigils.md}.
 */
public enum SigilType {
    EARTH,
    AIR,
    WATER,
    FIRE,
    LIGHT;

    /**
     * Maps a recognizer {@code spell_name} to a sigil type. Returns empty for
     * names that are not canonical sigils (signs, ring templates, orphaned
     * content like {@code levitation}, or {@code unknown}).
     */
    public static Optional<SigilType> fromSpellName(String spellName) {
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
