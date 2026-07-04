package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Shared particle resolution for effect kinds that emit {@link SpawnParticlesInstruction}s
 * ({@code particles}, {@code light}, {@code wind_pillar}, …). Vanilla {@code SimpleParticleType}s
 * are themselves {@link ParticleOptions}; {@code minecraft:dust} is built from the instruction's
 * {@code color}/{@code scale} fields. Particles that need other config (block, item) aren't
 * supported yet and resolve to {@code null} with a warning.
 */
public final class ParticleSupport {

    /** Fallback tint for an uncoloured {@code minecraft:dust} entry (plain white). */
    private static final Vector3f DEFAULT_DUST_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);

    private ParticleSupport() {}

    /**
     * Resolves a full {@link SpawnParticlesInstruction} to {@link ParticleOptions}, honouring
     * the optional {@code color}/{@code scale} fields for parameterised particles like
     * {@code minecraft:dust}. Prefer this over {@link #resolve(ResourceLocation, String)} so
     * data authors can drive coloured particles from JSON. Returns {@code null} if unusable.
     */
    @Nullable
    public static ParticleOptions resolve(SpawnParticlesInstruction sp, String kindKey) {
        ResourceLocation id = sp.particleId();
        if (id == null) return null;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (type == ParticleTypes.DUST) {
            Vector3f color = sp.color() != null ? sp.color() : DEFAULT_DUST_COLOR;
            return new DustParticleOptions(color, sp.scale());
        }
        return resolve(id, kindKey);
    }

    /** Resolves a particle id to {@link ParticleOptions}, or {@code null} if unusable. */
    @Nullable
    public static ParticleOptions resolve(@Nullable ResourceLocation id, String kindKey) {
        if (id == null) return null;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (type instanceof ParticleOptions opts) return opts;
        if (type != null) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[{}] Particle '{}' needs extra options not supported yet; skipping.", kindKey, id);
        } else {
            WitchHatAtelierMod.LOGGER.warn("[{}] Unknown particle id '{}'; skipping.", kindKey, id);
        }
        return null;
    }
}
