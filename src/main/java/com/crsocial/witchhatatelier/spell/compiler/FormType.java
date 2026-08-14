package com.crsocial.witchhatatelier.spell.compiler;

import java.util.Locale;
import java.util.Optional;

/**
 * The "how it manifests" axis of the compositional spell model — one of the
 * shapes a working material can be formed into (see {@code
 * docs/spell_pipeline.md}). Successor to the {@code MANIFESTATION}-tier
 * values of {@link SignType} ({@code COLUMN}, {@code DISPERSION}, {@code
 * BOLT}), split out into its own axis alongside {@link EffectType}.
 *
 * <p>{@link FormRole} is descriptive metadata reserved for the not-yet-built
 * "combining forms" feature (a {@code RIDER} attaching to a {@code CARRIER}'s
 * output, e.g. {@code column+bolt}); until that lands every form manifests
 * independently when drawn, {@code RIDER} included — there is no rider-alone
 * penalty today.</p>
 */
public enum FormType {
    COLUMN(FormRole.CARRIER),
    DISPERSION(FormRole.CARRIER),
    BOLT(FormRole.RIDER);

    /** Reserved for the deferred "combining forms" feature; see the class javadoc. */
    public enum FormRole { CARRIER, RIDER }

    private final FormRole role;

    FormType(FormRole role) {
        this.role = role;
    }

    public FormRole role() {
        return role;
    }

    /** Maps a recognizer {@code spell_name} to a form type. Empty for non-form names. */
    public static Optional<FormType> fromSpellName(String spellName) {
        if (spellName == null) return Optional.empty();
        return switch (spellName.toLowerCase(Locale.ROOT)) {
            case "column"     -> Optional.of(COLUMN);
            case "dispersion" -> Optional.of(DISPERSION);
            case "bolt"       -> Optional.of(BOLT);
            default            -> Optional.empty();
        };
    }
}
