package com.crsocial.witchhatatelier.spell.cast;

import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;

import java.util.UUID;

/**
 * Mutable per-player state for one in-flight channeled cast. Owned and ticked by
 * {@link SpellCastManager}; never touched off the server thread.
 *
 * <p>The casting paper is tracked by {@link #castId} (stamped into its NBT), not by
 * an {@code ItemStack} reference or slot — that survives inventory moves. If the
 * paper was held in a hand at start ({@link #heldAtStart}), removing it from both
 * hands cancels the cast; if it began in the inventory (e.g. drawn on a stack, so
 * the new inscribed copy landed in a free slot), the channel simply runs to term.</p>
 */
final class ActiveSpellCast {

    final UUID casterId;
    /** The spell as evaluated at start; re-aimed per tick via {@link ExecutableSpell#withOrigin}. */
    final ExecutableSpell baseSpell;
    /** Stamped into the paper's CustomData so it can be relocated after slot moves. */
    final UUID castId;
    final int totalTicks;
    /** Whether the paper was in a hand when the cast began (drives cancel-on-unhold). */
    final boolean heldAtStart;
    final EffectScratch scratch = new EffectScratch();

    int remainingTicks;
    boolean finished;

    ActiveSpellCast(UUID casterId, ExecutableSpell baseSpell, UUID castId,
                    int totalTicks, boolean heldAtStart) {
        this.casterId = casterId;
        this.baseSpell = baseSpell;
        this.castId = castId;
        this.totalTicks = totalTicks;
        this.heldAtStart = heldAtStart;
        this.remainingTicks = totalTicks;
    }
}
