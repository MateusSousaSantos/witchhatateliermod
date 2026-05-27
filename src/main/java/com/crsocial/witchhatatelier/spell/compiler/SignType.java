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
    COLUMN(Tier.MANIFESTATION, StackingMode.MAGNITUDE, ManifestationRole.CARRIER),
    DISPERSION(Tier.MANIFESTATION, StackingMode.MAGNITUDE, ManifestationRole.CARRIER),
    BOLT(Tier.MANIFESTATION, StackingMode.REPETITION, ManifestationRole.RIDER),
    CRUSH(Tier.FORCE, StackingMode.MAGNITUDE, ManifestationRole.NONE),
    CONVERGENCE(Tier.FORCE, StackingMode.MAGNITUDE, ManifestationRole.NONE),
    COLLECTION(Tier.META, StackingMode.MAGNITUDE, ManifestationRole.NONE),
    LEVITATION(Tier.META, StackingMode.MAGNITUDE, ManifestationRole.NONE),
    CROSSHAIR(Tier.META, StackingMode.MAGNITUDE, ManifestationRole.NONE);

    public enum Tier { MANIFESTATION, FORCE, META }

    public enum StackingMode { MAGNITUDE, REPETITION }

    /**
     * Role of a Manifestation sign when several combine in one ring.
     * <ul>
     *   <li>{@code CARRIER} — a base output shape (Column, Dispersion). Multiple
     *       carriers coexist (e.g. "a column and dispersion").</li>
     *   <li>{@code RIDER} — attaches onto whatever carrier is present (Bolt → impacts).</li>
     *   <li>{@code NONE} — not a Manifestation sign (Force / Meta).</li>
     * </ul>
     */
    public enum ManifestationRole { CARRIER, RIDER, NONE }

    private final Tier tier;
    private final StackingMode stackingMode;
    private final ManifestationRole manifestationRole;

    SignType(Tier tier, StackingMode stackingMode, ManifestationRole manifestationRole) {
        this.tier = tier;
        this.stackingMode = stackingMode;
        this.manifestationRole = manifestationRole;
    }

    public Tier tier() {
        return tier;
    }

    public StackingMode stackingMode() {
        return stackingMode;
    }

    public ManifestationRole manifestationRole() {
        return manifestationRole;
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
