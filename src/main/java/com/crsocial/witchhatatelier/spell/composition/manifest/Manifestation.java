package com.crsocial.witchhatatelier.spell.composition.manifest;

/**
 * What a {@code Form}/carrying {@code Effect} produced in the world (or
 * intends to) — renderer-agnostic per {@code docs/new_spell_engine.md}
 * invariant 6: blocks, particles, a projectile, or a rigged entity are all
 * equally valid. {@code Effect.modify} consumes and returns one of these;
 * {@link NoneManifestation} is the explicit "nothing (left) here" case, e.g.
 * after {@code CrushEffect} un-places a {@link BlocksManifestation}.
 */
public sealed interface Manifestation
        permits BlocksManifestation, ParticlesManifestation, ProjectileManifestation,
                EntityManifestation, NoneManifestation {
}
