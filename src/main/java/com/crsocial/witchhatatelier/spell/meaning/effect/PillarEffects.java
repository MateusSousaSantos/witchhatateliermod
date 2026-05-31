package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.List;

/**
 * Shared {@link EffectKind#execute} body for column-shaped block effects. The
 * stone-pillar and flame-pillar cells use identical placement semantics — only
 * the block id differs, and that comes from the matrix JSON. Centralizing the
 * math here keeps {@link StonePillarEffect} / {@link FlamePillarEffect} as thin
 * wrappers carrying just a fallback block id and a {@link #key()}.
 */
final class PillarEffects {

    private PillarEffects() {}

    /**
     * Pillar-placement workhorse. Loops over the parsed {@code spawn_blocks}
     * instructions on {@code op} and extrudes a column from the spell's
     * world-space origin along its surface normal for each one.
     */
    @SuppressWarnings("unchecked")
    static int executeColumn(ServerLevel level, ExecutableSpell spell, BehaviorOp op,
                             Block fallbackBlock, String kindKey) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
            WitchHatAtelierMod.LOGGER.debug(
                    "[{}] No spawn_blocks instructions on op; nothing to place.", kindKey);
            return 0;
        }
        List<EffectInstruction> instructions = (List<EffectInstruction>) raw;

        Magnitude m = spell.magnitude();
        float quality = Math.max(0.1f, m.quality());
        float size = Math.max(0.1f, m.sizeNormalized());
        Vector3f normal = spell.surfaceNormal();
        Direction grow = ColumnPlacer.directionFromNormal(normal);
        BlockPos originBlock = BlockPos.containing(
                spell.originWorld().x, spell.originWorld().y, spell.originWorld().z);

        int totalPlaced = 0;
        for (EffectInstruction ins : instructions) {
            if (!(ins instanceof SpawnBlocksInstruction sb)) continue;

            Block block = resolveBlock(sb.block(), fallbackBlock);
            int count = Math.max(1, op.count());
            int height = Math.max(1, Math.round(sb.blocksPerMagnitude() * count * size * quality));
            int placed = ColumnPlacer.placeColumn(level, originBlock, grow, height, block);
            totalPlaced += placed;

            WitchHatAtelierMod.LOGGER.info(
                    "[{}] Placed {}/{} {} block(s) at {} growing {} (count={}, size={}, quality={}).",
                    kindKey, placed, height,
                    BuiltInRegistries.BLOCK.getKey(block), originBlock, grow, count,
                    String.format(java.util.Locale.ROOT, "%.2f", size),
                    String.format(java.util.Locale.ROOT, "%.2f", quality));
        }
        return totalPlaced;
    }

    private static Block resolveBlock(ResourceLocation id, Block fallback) {
        if (id == null) return fallback;
        Block b = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (b == null || b == Blocks.AIR) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[PillarEffects] Unknown block id '{}'; using fallback {}.",
                    id, BuiltInRegistries.BLOCK.getKey(fallback));
            return fallback;
        }
        return b;
    }
}
