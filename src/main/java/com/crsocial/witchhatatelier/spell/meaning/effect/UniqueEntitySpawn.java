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
 * levitate, …) and the runtime scales lifetime via a per-entity
 * {@link LifetimeScaling} strategy.
 *
 * <p>When no {@code entity} is supplied (or its type is unavailable at runtime),
 * the effect degrades to placing {@code fallbackBlock} instead — this is the
 * "…or be a block, based on the spell" branch. At least one of the two should be
 * present; a cell with neither is a no-op.</p>
 *
 * <p>Parsing only; {@link EffectKind#execute} stays a Phase 2 no-op and will
 * consume this record once Phase 3 wires runtime execution.</p>
 *
 * @param entityId         entity type to spawn, or {@code null} to fall back to a block
 * @param fallbackBlock    block placed when no entity is spawned, or {@code null}
 * @param specialEffects   behaviour tags applied to the spawned entity (never {@code null})
 * @param lifetimeScaling  strategy that converts spell magnitude into lifetime ticks
 */
public record UniqueEntitySpawn(@Nullable ResourceLocation entityId,
                                @Nullable ResourceLocation fallbackBlock,
                                List<String> specialEffects,
                                LifetimeScaling lifetimeScaling) implements EffectInstruction {

    public static final String TYPE = "unique_entity";


    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Convenience method: computes the lifetime in ticks for a given spell magnitude.
     * Delegates to the entity's configured {@link LifetimeScaling} strategy.
     */
    public int computeLifetimeTicks(float magnitude) {
        return lifetimeScaling.computeTicks(magnitude);
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

        LifetimeScaling scaling = LifetimeScaling.fromJson(o);

        return new UniqueEntitySpawn(entity, block, List.copyOf(effects), scaling);
    }

    /** Reads a field as a {@link ResourceLocation}, or {@code null} when absent/blank/malformed. */
    @Nullable
    private static ResourceLocation optionalId(JsonObject o, String field) {
        if (!o.has(field) || o.get(field).getAsString().isBlank()) return null;
        return ResourceLocation.tryParse(o.get(field).getAsString());
    }
}
