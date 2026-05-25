package com.crsocial.witchhatatelier.spell.compiler;

import java.util.Locale;
import java.util.Optional;

/**
 * The seven signs that shape how a sigil's element manifests. Each declares its
 * {@link Tier} (used for co-presence rules) and {@link StackingMode} (read by
 * the meaning engine; the compiler is mode-agnostic). See
 * {@code docs/magic_system/signs.md}.
 */
public enum SignType {
    COLUMN(Tier.MANIFESTATION, StackingMode.MAGNITUDE),
    DISPERSION(Tier.MANIFESTATION, StackingMode.MAGNITUDE),
    BOLT(Tier.MANIFESTATION, StackingMode.REPETITION),
    CRUSH(Tier.FORCE, StackingMode.MAGNITUDE),
    CONVERGENCE(Tier.FORCE, StackingMode.MAGNITUDE),
    COLLECTION(Tier.META, StackingMode.MAGNITUDE),
    LEVITATION(Tier.META, StackingMode.MAGNITUDE),
    CROSSHAIR(Tier.META, StackingMode.MAGNITUDE);

    public enum Tier { MANIFESTATION, FORCE, META }

    public enum StackingMode { MAGNITUDE, REPETITION }

    private final Tier tier;
    private final StackingMode stackingMode;

    SignType(Tier tier, StackingMode stackingMode) {
        this.tier = tier;
        this.stackingMode = stackingMode;
    }

    public Tier tier() {
        return tier;
    }

    public StackingMode stackingMode() {
        return stackingMode;
    }

    /**
     * Maps a recognizer {@code spell_name} to a sign type. Returns empty for
     * names that are not signs (sigils, ring templates, or {@code unknown}).
     */
    public static Optional<SignType> fromSpellName(String spellName) {
        if (spellName == null) return Optional.empty();
        return switch (spellName.toLowerCase(Locale.ROOT)) {
            case "column"      -> Optional.of(COLUMN);
            case "dispersion"  -> Optional.of(DISPERSION);
            case "bolt"        -> Optional.of(BOLT);
            case "crush"       -> Optional.of(CRUSH);
            case "convergence" -> Optional.of(CONVERGENCE);
            case "collection"  -> Optional.of(COLLECTION);
            case "levitation"  -> Optional.of(LEVITATION);
            case "crosshair"   -> Optional.of(CROSSHAIR);
            default             -> Optional.empty();
        };
    }
}
