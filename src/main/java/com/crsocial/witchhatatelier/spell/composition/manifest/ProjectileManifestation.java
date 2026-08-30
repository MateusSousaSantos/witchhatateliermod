package com.crsocial.witchhatatelier.spell.composition.manifest;

import net.minecraft.world.entity.Entity;

import java.util.List;

/** One or more launched projectile entities — {@code BoltForm}'s output. */
public record ProjectileManifestation(List<Entity> projectiles) implements Manifestation {
}
