package com.crsocial.witchhatatelier.spell.meaning.effect.water;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import com.crsocial.witchhatatelier.spell.meaning.SizeScaling;
import com.crsocial.witchhatatelier.spell.meaning.effect.ColumnPlacer;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffects;
import com.crsocial.witchhatatelier.spell.meaning.effect.SpawnBlocksInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Water + Column → a blockless column of bubble particles rising from the glyph
 * (it draws a {@link ParticleTypes#BUBBLE} trail rather than placing water). With
 * a Crush Force sign in the same ring it inverts to <em>draining</em>: it removes
 * the water the column passes through — full water blocks become air, waterlogged
 * blocks are un-logged. Column length / drain depth scale with {@code
 * blocks_per_magnitude × count × size × quality}, capped by {@link ColumnPlacer#MAX_HEIGHT}.
 *
 * <p>Note: vanilla {@link ParticleTypes#BUBBLE} only renders <em>inside water</em>
 * (the particle removes itself in air). For a column that's visible in open air,
 * swap {@link #trailParticle()} to {@link ParticleTypes#BUBBLE_POP} or
 * {@link ParticleTypes#SPLASH}.</p>
 */
public final class WaterPillarEffect extends PillarEffect {

    public static final String KEY = "water_pillar";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    protected @Nullable Block defaultBlock() {
        return null;
    }

    @Override
    protected @Nullable ParticleOptions trailParticle() {
        return ParticleTypes.SPLASH;
    }

    /** Crush-mode: drain water along the column direction. */
    @Override
    @SuppressWarnings("unchecked")
    protected void crush(ServerLevel level, Player caster, ExecutableSpell spell, BehaviorOp op) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
            WitchHatAtelierMod.LOGGER.debug("[{}] Crush with no spawn_blocks instructions; nothing to drain.", KEY);
            return;
        }
        Magnitude m = spell.magnitude();
        float quality = Math.max(0.1f, m.quality());
        float reachScale = SizeScaling.powerMultiplier(m.sizeNormalized());
        Vector3f origin = spell.originWorld();
        Vector3f dir = PillarEffects.columnGrow(spell);
        int count = Math.max(1, op.count());

        for (EffectInstruction ins : (List<EffectInstruction>) raw) {
            if (!(ins instanceof SpawnBlocksInstruction sb)) continue;
            int depth = Math.max(1, Math.round(sb.blocksPerMagnitude() * count * reachScale * quality));
            int drained = drain(level, origin, dir, depth);
            WitchHatAtelierMod.LOGGER.info(
                    "[{}] CRUSH drained {}/{} water cell(s) from {} along ({}, {}, {}) (count={}, quality={}).",
                    KEY, drained, depth, BlockPos.containing(origin.x, origin.y, origin.z),
                    String.format(java.util.Locale.ROOT, "%.2f", dir.x),
                    String.format(java.util.Locale.ROOT, "%.2f", dir.y),
                    String.format(java.util.Locale.ROOT, "%.2f", dir.z), count,
                    String.format(java.util.Locale.ROOT, "%.2f", quality));
        }
    }

    /**
     * Removes water from up to {@code depth} blocks along {@code dir}: a full water
     * block becomes air; a waterlogged block is un-logged in place. Returns the
     * number of cells actually drained.
     */
    private static int drain(ServerLevel level, Vector3f origin, Vector3f dir, int depth) {
        int clamped = Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT, depth));
        int drained = 0;
        for (BlockPos pos : ColumnPlacer.traverse(origin, dir, clamped)) {
            if (!level.isInWorldBounds(pos)) break;
            BlockState existing = level.getBlockState(pos);
            if (existing.getBlock() instanceof SimpleWaterloggedBlock
                    && existing.hasProperty(BlockStateProperties.WATERLOGGED)
                    && existing.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, existing.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
                drained++;
                continue;
            }
            FluidState fluid = existing.getFluidState();
            if (!fluid.isEmpty() && fluid.getType() == Fluids.WATER && existing.getBlock() == Blocks.WATER) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                drained++;
            }
        }
        return drained;
    }
}
