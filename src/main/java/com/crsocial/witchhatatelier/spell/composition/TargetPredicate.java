package com.crsocial.witchhatatelier.spell.composition;

import net.minecraft.world.entity.Entity;

/**
 * What counts as a valid target for a seeking/spreading sign (homing, chain —
 * see {@code docs/new_spell_engine.md} §10). Supplied by the sign itself;
 * consumed by {@link TargetSelector}. No default {@code Form}/{@code Effect}
 * registered today seeks or spreads, so nothing implements this yet — it's
 * infrastructure for a future sign, not a gap in an existing one, same
 * honesty as {@code EXTINGUISH}'s missing gesture template.
 */
public interface TargetPredicate {
    boolean valid(Entity candidate, ExecutableSpell spell);
}
