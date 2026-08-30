package com.crsocial.witchhatatelier.spell.composition.effect;

import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.compiler.ExecutionMode;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.Trigger;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;

import java.util.Optional;

/**
 * Modifies a {@code Form}'s manifestation, or — if {@link #canCarry()} —
 * manifests on its own when no form was drawn (the fallback chain's branch
 * 2). See the resolution algorithm in {@code docs/new_spell_engine.md} §6.
 *
 * <p>{@link #canCarry()} and {@link #modeTag()} default to delegating to
 * {@link #type()} so a concrete effect need not repeat its {@link
 * EffectType}'s declared metadata; override only if an effect ever needs to
 * diverge from its type's declared defaults (none do today).</p>
 */
public interface Effect {

    EffectType type();

    StackingMode stacking();

    default boolean canCarry() {
        return type().canCarry();
    }

    default ExecutionMode modeTag() {
        return type().modeTag();
    }

    /**
     * Manifests this effect on its own, with no form present. Only called
     * when {@link #canCarry()} is {@code true}; the default throws for
     * effects that can't carry so a non-carrying implementation doesn't need
     * to stub it out.
     */
    default Manifestation carry(Material working, CastContext ctx) {
        throw new UnsupportedOperationException(type() + " cannot carry (canCarry() is false)");
    }

    /** Modifies a {@code Form}'s (or another effect's carried) manifestation. Identity by default. */
    default Manifestation modify(Manifestation in, Material working, CastContext ctx) {
        return in;
    }

    /**
     * The watch condition a reactive runtime would poll once armed (§8) — empty
     * unless this effect's {@link #modeTag()} is ever {@link ExecutionMode#REACTIVE}.
     * {@code CompositionEngine} attaches whichever present effect returns one to
     * the compiled {@code ExecutableSpell}; it never evaluates it itself.
     */
    default Optional<Trigger> trigger() {
        return Optional.empty();
    }
}
