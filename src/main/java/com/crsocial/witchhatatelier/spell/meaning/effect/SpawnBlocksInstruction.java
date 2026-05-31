package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code "type": "spawn_blocks"} effect instruction. Places one or more blocks
 * of a given type in a fixed shape rooted at the spell's origin.
 *
 * <p>Phase 3 supports the {@link Shape#VERTICAL_COLUMN} shape, where blocks
 * extrude from the casting surface along the surface normal. Other shapes are
 * defined ahead-of-time so cell authors can target them, but unsupported shapes
 * degrade to {@link Shape#VERTICAL_COLUMN} until they ship.</p>
 *
 * @param block               block type to place; may be {@code null} when the
 *                            JSON id failed to parse — effects should skip in
 *                            that case
 * @param shape               placement pattern
 * @param blocksPerMagnitude  baseline height/count before magnitude scaling
 */
public record SpawnBlocksInstruction(@Nullable ResourceLocation block,
                                     Shape shape,
                                     int blocksPerMagnitude) implements EffectInstruction {

    public static final String TYPE = "spawn_blocks";

    public enum Shape {
        VERTICAL_COLUMN,
        UNKNOWN;

        static Shape parse(@Nullable String raw) {
            if (raw == null) return VERTICAL_COLUMN;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "vertical_column" -> VERTICAL_COLUMN;
                default -> UNKNOWN;
            };
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    static SpawnBlocksInstruction fromJson(JsonObject o) {
        ResourceLocation block = null;
        if (o.has("block") && !o.get("block").getAsString().isBlank()) {
            block = ResourceLocation.tryParse(o.get("block").getAsString());
        }
        Shape shape = Shape.parse(o.has("shape") ? o.get("shape").getAsString() : null);
        int per = o.has("blocks_per_magnitude") ? o.get("blocks_per_magnitude").getAsInt() : 1;
        return new SpawnBlocksInstruction(block, shape, Math.max(1, per));
    }
}
