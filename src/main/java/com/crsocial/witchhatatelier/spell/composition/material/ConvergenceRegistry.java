package com.crsocial.witchhatatelier.spell.composition.material;

import com.crsocial.witchhatatelier.spell.compiler.ElementType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sparse, code-registered table of which elements have a convergence
 * material-modifier wired. Bootstrapped once at class-load — this is a Java
 * registry, not JSON, because convergence swaps a <em>material</em> (a
 * gameplay/rendering decision belonging to the asset/code half of the opt-in
 * override split), not a tunable number; see {@code docs/new_spell_engine.md}
 * §7. An element with no registered modifier simply has convergence do
 * nothing — a valid, documented outcome the composition engine falls back
 * on, not a gap to fill in later.
 */
public final class ConvergenceRegistry {

    private static final Map<ElementType, MaterialModifier> MODIFIERS = new EnumMap<>(ElementType.class);

    static {
        bootstrap();
    }

    private ConvergenceRegistry() {
    }

    private static void bootstrap() {
        // Fire + Convergence -> magma. The modifier ignores `in` and returns the
        // element's registered dense form outright, since fire's convergence is a
        // flat material swap, not derived from whatever `in` happened to be.
        register(ElementType.FIRE, (element, in) -> element.converged());
    }

    public static void register(ElementType type, MaterialModifier modifier) {
        MODIFIERS.put(type, modifier);
    }

    public static Optional<MaterialModifier> find(ElementType type) {
        return Optional.ofNullable(MODIFIERS.get(type));
    }
}
