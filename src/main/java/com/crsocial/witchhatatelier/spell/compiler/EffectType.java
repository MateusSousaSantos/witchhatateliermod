package com.crsocial.witchhatatelier.spell.compiler;

import java.util.Locale;
import java.util.Optional;

/**
 * The "how its behaviour is modified" axis of the compositional spell model —
 * modifies a {@code Form}'s manifestation, or (if {@link #canCarry()}) can
 * manifest on its own with no form drawn (see {@code docs/spell_pipeline.md}
 * §5). Successor to the {@code FORCE}/{@code META}-tier values of {@link
 * SignType} ({@code CRUSH}, {@code PULL}, {@code COLLECTION}, {@code
 * LEVITATION}), split out into its own axis alongside {@link FormType}.
 * {@code CONVERGENCE} is deliberately absent — it is a separate axis (a
 * material modifier, not an effect), a distinction that only mattered to the
 * since-removed composition engine. {@code CROSSHAIR} is dropped outright
 * (never wired to anything pre-migration, maps to no axis).
 *
 * <p>{@code EXTINGUISH} was the migration's reactive-mode worked example
 * (armed and idle, watches for nearby fire, extinguishes + charges a
 * per-event cost only when it actually triggers) before the composition
 * engine that gave that behaviour meaning was removed.
 * It has no gesture template yet (unlike every other value here, which was
 * ported from an existing, real, hand-drawn-trained recognizer template) —
 * authoring one requires the human-in-the-loop training workflow in {@code
 * docs/recognizer.md}, not something fabricated point-by-point; simply
 * undrawable in-game until that template exists.</p>
 */
public enum EffectType {
    LEVITATION(true, ExecutionMode.CONTINUOUS),
    CRUSH(false, ExecutionMode.CONTINUOUS),
    PULL(true, ExecutionMode.CONTINUOUS),
    COLLECTION(false, ExecutionMode.CONTINUOUS),
    EXTINGUISH(true, ExecutionMode.REACTIVE);

    private final boolean canCarry;
    private final ExecutionMode modeTag;

    EffectType(boolean canCarry, ExecutionMode modeTag) {
        this.canCarry = canCarry;
        this.modeTag = modeTag;
    }

    /** Whether this effect can manifest on its own with no form drawn (the fallback chain's branch 2). */
    public boolean canCarry() {
        return canCarry;
    }

    /** The {@link ExecutionMode} this effect tags a spell with when present. */
    public ExecutionMode modeTag() {
        return modeTag;
    }

    /** Maps a recognizer {@code spell_name} to an effect type. Empty for non-effect names. */
    public static Optional<EffectType> fromSpellName(String spellName) {
        if (spellName == null) return Optional.empty();
        return switch (spellName.toLowerCase(Locale.ROOT)) {
            case "levitation" -> Optional.of(LEVITATION);
            case "crush"      -> Optional.of(CRUSH);
            case "pull"       -> Optional.of(PULL);
            case "collection" -> Optional.of(COLLECTION);
            case "extinguish" -> Optional.of(EXTINGUISH);
            default            -> Optional.empty();
        };
    }
}
