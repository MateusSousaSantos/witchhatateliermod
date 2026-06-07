package com.crsocial.witchhatatelier.spell.meaning.effect.earth;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import com.crsocial.witchhatatelier.spell.meaning.SizeScaling;
import com.crsocial.witchhatatelier.spell.meaning.effect.BreakBlocksInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.ColumnPlacer;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.List;

/**
 * Earth + Crush → carves a tunnel <em>into</em> the casting surface, breaking
 * blocks along the direction opposite the casting medium's facing. The medium
 * (placed paper) faces along {@link ExecutableSpell#surfaceNormal()}; we dig the
 * other way, so a paper on the ground drills straight down, one on a wall bores
 * into the wall. Depth scales with {@code depth_per_magnitude × count × size ×
 * quality} and is capped by {@link ColumnPlacer#MAX_HEIGHT}.
 *
 * <p>Unbreakable blocks (negative destroy speed, e.g. bedrock) are skipped, not
 * fatal — the carve continues past them like {@link ColumnPlacer#placeColumn}
 * steps past a one-block obstruction.</p>
 */
public final class ExcavateEffect implements EffectKind {

    public static final String KEY = "excavate";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }

    /**
     * Direction the carve travels: the negated surface normal (into the medium),
     * falling back to straight down for a degenerate normal.
     */
    private static Vector3f carveDirection(ExecutableSpell spell) {
        Vector3f normal = spell.surfaceNormal();
        if (normal == null || normal.lengthSquared() < 1e-6f) return new Vector3f(0f, -1f, 0f);
        return new Vector3f(normal).negate().normalize();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
                WitchHatAtelierMod.LOGGER.debug("[{}] No break_blocks instructions on op; nothing to carve.", KEY);
                continue;
            }
            List<EffectInstruction> instructions = (List<EffectInstruction>) raw;

            Magnitude m = spell.magnitude();
            float quality = Math.max(0.1f, m.quality());
            float reachScale = SizeScaling.powerMultiplier(m.sizeNormalized()); // size→reach curve
            Vector3f origin = spell.originWorld();
            Vector3f dir = carveDirection(spell);
            int count = Math.max(1, op.count());

            for (EffectInstruction ins : instructions) {
                if (!(ins instanceof BreakBlocksInstruction bb)) continue;
                int depth = Math.max(1, Math.round(bb.depthPerMagnitude() * count * reachScale * quality));
                int broken = carve(level, caster, origin, dir, depth, bb.dropItems());

                WitchHatAtelierMod.LOGGER.info(
                        "[{}] Broke {}/{} block(s) from {} carving ({}, {}, {}) (count={}, quality={}, drop={}).",
                        KEY, broken, depth, BlockPos.containing(origin.x, origin.y, origin.z),
                        String.format(java.util.Locale.ROOT, "%.2f", dir.x),
                        String.format(java.util.Locale.ROOT, "%.2f", dir.y),
                        String.format(java.util.Locale.ROOT, "%.2f", dir.z), count,
                        String.format(java.util.Locale.ROOT, "%.2f", quality), bb.dropItems());
            }
        }
    }

    /**
     * Breaks up to {@code depth} blocks from {@code origin} along {@code dir}.
     * Air and unbreakable blocks are skipped (not fatal); returns the count
     * actually destroyed.
     */
    private static int carve(ServerLevel level, Player caster, Vector3f origin, Vector3f dir,
                             int depth, boolean dropItems) {
        int clamped = Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT, depth));
        int broken = 0;
        for (BlockPos pos : ColumnPlacer.traverse(origin, dir, clamped)) {
            if (!level.isInWorldBounds(pos)) break;
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir()) continue;
            if (existing.getDestroySpeed(level, pos) < 0f) continue; // bedrock & friends
            if (level.destroyBlock(pos, dropItems, caster)) broken++;
        }
        return broken;
    }
}
