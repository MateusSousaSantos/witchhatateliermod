package com.crsocial.witchhatatelier.spell.composition.manifest;

/**
 * Explicit "nothing (left) here" manifestation — e.g. what {@code
 * CrushEffect} turns a {@link BlocksManifestation} into after un-placing it.
 * Distinct from the composition engine finding <em>no</em> manifestation at
 * all (the Prepared safety net, {@code docs/new_spell_engine.md} §6 step 3):
 * this is a manifestation that resolved to "nothing", produced deliberately.
 */
public record NoneManifestation() implements Manifestation {
}
