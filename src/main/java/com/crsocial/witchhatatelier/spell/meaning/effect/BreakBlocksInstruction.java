package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;

/**
 * {@code "type": "break_blocks"} effect instruction. Carves a line of blocks out
 * of the world rather than placing them — the destructive counterpart to
 * {@link SpawnBlocksInstruction}. The carve direction and origin come from the
 * spell at cast time; this record only carries the depth/drop knobs.
 *
 * @param depthPerMagnitude baseline carve length (blocks) before magnitude scaling
 * @param dropItems         whether broken blocks drop their normal loot
 */
public record BreakBlocksInstruction(int depthPerMagnitude,
                                     boolean dropItems) implements EffectInstruction {

    public static final String TYPE = "break_blocks";

    @Override
    public String type() {
        return TYPE;
    }

    static BreakBlocksInstruction fromJson(JsonObject o) {
        int depth = o.has("depth_per_magnitude") ? o.get("depth_per_magnitude").getAsInt() : 1;
        boolean drop = o.has("drop_items") && o.get("drop_items").getAsBoolean();
        return new BreakBlocksInstruction(Math.max(1, depth), drop);
    }
}
