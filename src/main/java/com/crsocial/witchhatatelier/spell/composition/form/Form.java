package com.crsocial.witchhatatelier.spell.composition.form;

import com.crsocial.witchhatatelier.spell.compiler.FormType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;

/**
 * Produces a manifestation from the working material. One of the two
 * "producer" roles in the composition engine's fallback chain (the other is
 * a carrying {@code Effect}); {@code Effect.modify} then layers behaviour on
 * top of whatever a {@code Form} returns. See the resolution algorithm in
 * {@code docs/new_spell_engine.md} §6.
 *
 * <p>{@code ctx.magnitude()} already has this form's own {@link
 * #stacking()}-driven repeat count folded in by the time {@link #manifest}
 * is called — a {@code Form} never re-reads an occurrence count itself.</p>
 */
public interface Form {

    FormType type();

    StackingMode stacking();

    Manifestation manifest(Material working, CastContext ctx);
}
