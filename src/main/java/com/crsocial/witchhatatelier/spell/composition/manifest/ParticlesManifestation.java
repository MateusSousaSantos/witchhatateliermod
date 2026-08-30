package com.crsocial.witchhatatelier.spell.composition.manifest;

import net.minecraft.core.particles.ParticleOptions;
import org.joml.Vector3f;

/**
 * A blockless manifestation — a burst or trail of {@code particle} from
 * {@code origin} along {@code direction} for {@code reach} blocks. What a
 * blockless {@link com.crsocial.witchhatatelier.spell.composition.material.ParticleMaterial}
 * (Air, Water by default) produces where a blocky material would place a
 * {@link BlocksManifestation}.
 */
public record ParticlesManifestation(Vector3f origin, Vector3f direction,
                                     ParticleOptions particle, float reach) implements Manifestation {
}
