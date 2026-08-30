package com.crsocial.witchhatatelier.spell.composition.material;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * What an element (or its convergence-transformed form) physically manifests
 * as, once a {@code Form}/{@code Effect} shapes it. Not every element/
 * convergence combination produces a block — Water and Air are blockless by
 * default ({@link ParticleMaterial}), and a bespoke convergence may render as
 * a custom model/entity instead ({@link ModelMaterial}). See {@code
 * docs/new_spell_engine.md} §11.
 */
public sealed interface Material permits BlockMaterial, ModelMaterial, ParticleMaterial {

    /** Convenience accessor; empty unless this is a {@link BlockMaterial}. */
    default Optional<Block> asBlock() {
        return this instanceof BlockMaterial(Block block) ? Optional.of(block) : Optional.empty();
    }

    /** Convenience accessor; empty unless this is a {@link ParticleMaterial}. */
    default Optional<ParticleOptions> asParticle() {
        return this instanceof ParticleMaterial(ParticleOptions particle) ? Optional.of(particle) : Optional.empty();
    }

    /** Convenience accessor; empty unless this is a {@link ModelMaterial}. */
    default Optional<ResourceLocation> asModel() {
        return this instanceof ModelMaterial(ResourceLocation modelId) ? Optional.of(modelId) : Optional.empty();
    }
}
