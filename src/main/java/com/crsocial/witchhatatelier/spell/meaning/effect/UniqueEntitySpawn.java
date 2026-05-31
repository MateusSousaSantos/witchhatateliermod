package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code "type": "unique_entity"} effect instruction. Spawns a mod-defined
 * "unique" entity whose behaviour is driven by the spell rather than a vanilla
 * mob template — the {@code specialEffects} tags name what it does (ignite,
 * levitate, …). Entity lifetime is driven by the engine-computed
 * {@code spell.durationTicks()} (from {@code base.duration_ticks}), not by
 * any per-effect scaling block.
 *
 * <p>When no {@code entity} is supplied (or its type is unavailable at runtime),
 * the effect degrades to placing {@code fallbackBlock} instead — this is the
 * "…or be a block, based on the spell" branch. At least one of the two should be
 * present; a cell with neither is a no-op.</p>
 *
 * @param entityId         entity type to spawn, or {@code null} to fall back to a block
 * @param fallbackBlock    block placed when no entity is spawned, or {@code null}
 * @param specialEffects   behaviour tags applied to the spawned entity (never {@code null})
 */
public record UniqueEntitySpawn(@Nullable ResourceLocation entityId,
                                @Nullable ResourceLocation fallbackBlock,
                                List<String> specialEffects) implements EffectInstruction {

    public static final String TYPE = "unique_entity";


    @Override
    public String type() {
        return TYPE;
    }

    /** Parses a {@code unique_entity} effect entry; missing fields fall back to defaults. */
    static UniqueEntitySpawn fromJson(JsonObject o) {
        ResourceLocation entity = optionalId(o, "entity");
        ResourceLocation block = optionalId(o, "fallback_block");

        List<String> effects = new ArrayList<>();
        if (o.has("special_effects") && o.get("special_effects").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("special_effects");
            for (JsonElement el : arr) effects.add(el.getAsString());
        }

        return new UniqueEntitySpawn(entity, block, List.copyOf(effects));
    }

    /** Reads a field as a {@link ResourceLocation}, or {@code null} when absent/blank/malformed. */
    @Nullable
    private static ResourceLocation optionalId(JsonObject o, String field) {
        if (!o.has(field) || o.get(field).getAsString().isBlank()) return null;
        return ResourceLocation.tryParse(o.get(field).getAsString());
    }
}
