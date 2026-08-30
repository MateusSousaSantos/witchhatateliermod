package com.crsocial.witchhatatelier.spell.composition.material;

import net.minecraft.core.particles.ParticleOptions;

/** A blockless material that manifests as particles only (e.g. Air, Water by default). */
public record ParticleMaterial(ParticleOptions particle) implements Material {
}
