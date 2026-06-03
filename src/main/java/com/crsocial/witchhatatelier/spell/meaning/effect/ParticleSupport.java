package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Shared particle resolution for effect kinds that emit {@link SpawnParticlesInstruction}s
 * ({@code particles}, {@code wind_pillar}, …). Vanilla {@code SimpleParticleType}s
 * are themselves {@link ParticleOptions}; particles that need extra config (dust,
 * block, item) aren't supported yet and resolve to {@code null} with a warning.
 */
public final class ParticleSupport {

    private ParticleSupport() {}

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
