package com.crsocial.witchhatatelier.spell.composition.manifest;

import net.minecraft.world.entity.Entity;

/**
 * A summoned/rigged entity manifestation — reserved for a bespoke Form/Effect
 * override that wants a live entity rather than blocks/particles/a
 * projectile. Unused by any default registration today.
 */
public record EntityManifestation(Entity entity) implements Manifestation {
}
