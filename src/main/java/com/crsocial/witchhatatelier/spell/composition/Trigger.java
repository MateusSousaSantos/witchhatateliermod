package com.crsocial.witchhatatelier.spell.composition;

import net.minecraft.server.level.ServerLevel;

/**
 * A reactive spell's watch condition (see {@code docs/new_spell_engine.md}
 * §8) — armed by {@link com.crsocial.witchhatatelier.spell.compiler.ExecutionMode#REACTIVE},
 * evaluated by whatever runtime eventually watches it. Composed onto {@link
 * ExecutableSpell} by {@link CompositionEngine}, never evaluated there — no
 * runtime exists yet to poll it (see {@code docs/new_spell_engine.md}'s own
 * scope: this engine matches and creates spells, it doesn't execute them).
 */
public interface Trigger {
    boolean test(ServerLevel level, ExecutableSpell spell);
}
