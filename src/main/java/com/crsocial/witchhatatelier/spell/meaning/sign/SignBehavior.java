package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;

/**
 * Custom sign implementation that provides unique behaviour beyond what the
 * matrix system alone can express. Only signs that need special mechanics
 * (origin shifting, direction manipulation, etc.) implement this interface —
 * the majority of signs rely solely on the matrix's base power/duration/AoE
 * values and stacking curves.
 *
 * <p>Each implementation receives the sigil type so it can vary its effect
 * per element. For example, {@link LevitationSignBehavior} raises the origin
 * for Fire (heat rises) but sinks it for Earth (weight resists).</p>
 *
 * <p>The {@link SignBehaviorRegistry} maps {@link com.crsocial.witchhatatelier.spell.compiler.SignType}
 * to an optional {@code SignBehavior}. When no implementation is registered,
 * the meaning engine skips custom modifications and uses only matrix data.</p>
 */
public interface SignBehavior {

    /**
     * Produces a modification for this sign's interaction with the given sigil.
     *
     * @param sigil     the central sigil element of the spell
     * @param bundle    the sign bundle (count, occurrences with positions/angles)
     * @param graph     the complete spell graph (for geometry queries)
     * @param ctx       casting context (medium, world origin, surface normal)
     * @param magnitude the pre-computed magnitude of the spell
     * @return modifications to apply; {@link SpellModification#NONE} if this
     *         sign has no unique interaction with the given sigil
     */
    SpellModification modify(SigilType sigil,
                             SignBundle bundle,
                             SpellGraph graph,
                             CastingContext ctx,
                             Magnitude magnitude);
}

