package com.crsocial.witchhatatelier.spell.composition;

import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.compiler.FormType;
import com.crsocial.witchhatatelier.spell.composition.effect.Effect;
import com.crsocial.witchhatatelier.spell.composition.effect.fire.FireCrushOverride;
import com.crsocial.witchhatatelier.spell.composition.form.Form;
import com.crsocial.witchhatatelier.spell.composition.form.fire.FireColumnOverride;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sparse {@code (type, element)} → bespoke implementation table — the opt-in
 * half of §7's override split (see {@code docs/new_spell_engine.md}). A miss
 * means "use the default" ({@code FormRegistry}/{@code EffectRegistry});
 * registering one combination never obligates another. Deliberately sparse —
 * see {@code docs/sigils_and_signs.md} for the (small, illustrative) set
 * registered today.
 */
public final class OverrideRegistry {

    private static final Map<FormType, Map<ElementType, Form>> FORM_OVERRIDES = new EnumMap<>(FormType.class);
    private static final Map<EffectType, Map<ElementType, Effect>> EFFECT_OVERRIDES = new EnumMap<>(EffectType.class);

    static {
        bootstrap();
    }

    private OverrideRegistry() {
    }

    private static void bootstrap() {
        registerForm(FormType.COLUMN, ElementType.FIRE, new FireColumnOverride());
        registerEffect(EffectType.CRUSH, ElementType.FIRE, new FireCrushOverride());
    }

    public static void registerForm(FormType type, ElementType element, Form form) {
        FORM_OVERRIDES.computeIfAbsent(type, t -> new EnumMap<>(ElementType.class)).put(element, form);
    }

    public static void registerEffect(EffectType type, ElementType element, Effect effect) {
        EFFECT_OVERRIDES.computeIfAbsent(type, t -> new EnumMap<>(ElementType.class)).put(element, effect);
    }

    public static Optional<Form> findForm(FormType type, ElementType element) {
        Map<ElementType, Form> byElement = FORM_OVERRIDES.get(type);
        return byElement == null ? Optional.empty() : Optional.ofNullable(byElement.get(element));
    }

    public static Optional<Effect> findEffect(EffectType type, ElementType element) {
        Map<ElementType, Effect> byElement = EFFECT_OVERRIDES.get(type);
        return byElement == null ? Optional.empty() : Optional.ofNullable(byElement.get(element));
    }
}
