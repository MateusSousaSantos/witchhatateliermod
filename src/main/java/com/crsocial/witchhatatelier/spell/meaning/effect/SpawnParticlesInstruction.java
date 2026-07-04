package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * {@code "type": "spawn_particles"} effect instruction. Emits a cloud of a
 * vanilla (or mod) particle at the spell origin — the reusable "particle
 * generator" building block any {@link EffectKind} can carry. {@link ParticleEffect}
 * is the kind that consumes it, but the record is deliberately generic so other
 * effects can layer particles onto their own behaviour later.
 *
 * <p>Simple {@code SimpleParticleType}s (e.g. {@code minecraft:flame}, {@code cloud},
 * {@code end_rod}) emit as-is. Parameterised particles are supported through the optional
 * {@code color}/{@code scale} fields: {@code minecraft:dust} reads them to build a coloured,
 * sized {@code DustParticleOptions} (see {@link ParticleSupport}). Particles that need other
 * config (block, item) are still skipped with a warning at emit time.</p>
 *
 * @param particleId      particle type id to emit, or {@code null} when the JSON
 *                        id was absent/malformed — the effect skips in that case
 * @param count           baseline particle count per emission, before magnitude scaling
 * @param spread          gaussian offset radius handed to {@code sendParticles}
 *                        (dx/dy/dz); larger = a looser cloud
 * @param speed           particle speed argument to {@code sendParticles}
 * @param verticalOffset  blocks to raise the emission point above the origin
 * @param reach           column length in blocks for line-shaped effects (e.g.
 *                        {@code wind_pillar}); {@code 0} = a single point emission,
 *                        which is all the generic {@code particles} kind uses
 * @param color           RGB tint in {@code [0,1]} for parameterised particles
 *                        ({@code minecraft:dust}), or {@code null} for an uncoloured default
 * @param scale           particle scale for {@code minecraft:dust} (vanilla clamps to {@code [0.01, 4]})
 */
public record SpawnParticlesInstruction(@Nullable ResourceLocation particleId,
                                        int count,
                                        float spread,
                                        float speed,
                                        float verticalOffset,
                                        float reach,
                                        @Nullable Vector3f color,
                                        float scale) implements EffectInstruction {

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
        float reach = o.has("reach") ? o.get("reach").getAsFloat() : 0.0f;
        Vector3f color = parseColor(o.has("color") ? o.get("color") : null);
        float scale = o.has("scale") ? Math.max(0.01f, o.get("scale").getAsFloat()) : 1.0f;
        return new SpawnParticlesInstruction(
                id, Math.max(0, count), Math.max(0f, spread), speed, vOff, Math.max(0f, reach),
                color, scale);
    }

    /** Reads a {@code [r, g, b]} array (each {@code 0..1}) into a colour, or {@code null} if absent/malformed. */
    @Nullable
    private static Vector3f parseColor(@Nullable com.google.gson.JsonElement el) {
        if (el == null || !el.isJsonArray()) return null;
        JsonArray arr = el.getAsJsonArray();
        if (arr.size() != 3) return null;
        return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }
}
