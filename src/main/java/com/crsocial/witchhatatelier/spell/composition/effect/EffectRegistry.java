package com.crsocial.witchhatatelier.spell.composition.effect;

import com.crsocial.witchhatatelier.spell.compiler.EffectType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every {@link EffectType}'s generic default implementation — the "the
 * default always exists" half of the opt-in override split (see {@code
 * docs/new_spell_engine.md} §7). {@code OverrideRegistry} checks a sparse
 * {@code (EffectType, ElementType)} table first; this registry is always the
 * fallback, exhaustively registered for every {@link EffectType} (including
 * {@code EXTINGUISH}, despite it having no gesture template yet).
 */
public final class EffectRegistry {

    private static final Map<EffectType, Effect> DEFAULTS = new EnumMap<>(EffectType.class);

    static {
        bootstrap();
    }

    private EffectRegistry() {
    }

    private static void bootstrap() {
        register(new LevitationEffect());
        register(new CrushEffect());
        register(new PullEffect());
        register(new CollectionEffect());
        register(new ExtinguishEffect());
    }

    private static void register(Effect effect) {
        DEFAULTS.put(effect.type(), effect);
    }

    /** The generic default {@link Effect} for {@code type}. Every {@link EffectType} is always present. */
    public static Effect get(EffectType type) {
        Effect effect = DEFAULTS.get(type);
        if (effect == null) {
            throw new IllegalStateException("No default Effect registered for " + type + " - EffectRegistry.bootstrap() is incomplete");
        }
        return effect;
    }
}
