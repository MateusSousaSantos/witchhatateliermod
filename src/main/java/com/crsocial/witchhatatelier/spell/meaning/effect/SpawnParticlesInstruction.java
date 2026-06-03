package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * {@code "type": "spawn_particles"} effect instruction. Emits a cloud of a
 * vanilla (or mod) particle at the spell origin — the reusable "particle
 * generator" building block any {@link EffectKind} can carry. {@link ParticleEffect}
 * is the kind that consumes it, but the record is deliberately generic so other
 * effects can layer particles onto their own behaviour later.
 *
 * <p>Only particles whose {@code ParticleType} is itself a {@code ParticleOptions}
 * (i.e. {@code SimpleParticleType}s like {@code minecraft:flame}, {@code cloud},
 * {@code crit}) are supported right now; parameterised particles (dust, block)
 * need extra fields and are skipped with a warning at emit time.</p>
 *
 * @param particleId      particle type id to emit, or {@code null} when the JSON
 *                        id was absent/malformed — the effect skips in that case
 * @param count           baseline particle count per emission, before magnitude scaling
 * @param spread          gaussian offset radius handed to {@code sendParticles}
 *                        (dx/dy/dz); larger = a looser cloud
 * @param speed           particle speed argument to {@code sendParticles}
 * @param verticalOffset  blocks to raise the emission point above the origin
 */
public record SpawnParticlesInstruction(@Nullable ResourceLocation particleId,
                                        int count,
                                        float spread,
                                        float speed,
                                        float verticalOffset) implements EffectInstruction {

    public static final String TYPE = "spawn_particles";

    @Override
    public String type() {
        return TYPE;
    }

    /** Parses a {@code spawn_particles} entry; missing fields fall back to gentle defaults. */
    static SpawnParticlesInstruction fromJson(JsonObject o) {
        ResourceLocation id = null;
        if (o.has("particle") && !o.get("particle").getAsString().isBlank()) {
            id = ResourceLocation.tryParse(o.get("particle").getAsString());
        }
        int count = o.has("count") ? o.get("count").getAsInt() : 8;
        float spread = o.has("spread") ? o.get("spread").getAsFloat() : 0.25f;
        float speed = o.has("speed") ? o.get("speed").getAsFloat() : 0.0f;
        float vOff = o.has("vertical_offset") ? o.get("vertical_offset").getAsFloat() : 0.0f;
        return new SpawnParticlesInstruction(id, Math.max(0, count), Math.max(0f, spread), speed, vOff);
    }
}
