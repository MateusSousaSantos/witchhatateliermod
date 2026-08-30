package com.crsocial.witchhatatelier.spell.composition.form;

import com.crsocial.witchhatatelier.spell.compiler.FormType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every {@link FormType}'s generic default implementation — the "the default
 * always exists" half of the opt-in override split (see {@code
 * docs/new_spell_engine.md} §7). {@code OverrideRegistry} checks a sparse
 * {@code (FormType, ElementType)} table first; this registry is always the
 * fallback, exhaustively registered for every {@link FormType}.
 */
public final class FormRegistry {

    private static final Map<FormType, Form> DEFAULTS = new EnumMap<>(FormType.class);

    static {
        bootstrap();
    }

    private FormRegistry() {
    }

    private static void bootstrap() {
        register(new ColumnForm());
        register(new DispersionForm());
        register(new BoltForm());
    }

    private static void register(Form form) {
        DEFAULTS.put(form.type(), form);
    }

    /** The generic default {@link Form} for {@code type}. Every {@link FormType} is always present. */
    public static Form get(FormType type) {
        Form form = DEFAULTS.get(type);
        if (form == null) {
            throw new IllegalStateException("No default Form registered for " + type + " - FormRegistry.bootstrap() is incomplete");
        }
        return form;
    }
}
