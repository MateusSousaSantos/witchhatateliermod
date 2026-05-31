package com.crsocial.witchhatatelier.spell.meaning.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Places a vertical column of blocks from a starting position, extruding along
 * a casting-surface normal. Used by column-shaped effects (stone pillar, flame
 * pillar) so they share identical placement semantics — air / replaceable
 * blocks are overwritten, solid blocks are skipped but do not abort the run.
 */
public final class ColumnPlacer {

    public static final int MAX_HEIGHT = 32;

    private ColumnPlacer() {}

    /** Resolves a surface-normal vector to the nearest cardinal {@link Direction}. */
    public static Direction directionFromNormal(Vector3f normal) {
        if (normal == null || normal.lengthSquared() < 1e-6f) return Direction.UP;
        float ax = Math.abs(normal.x), ay = Math.abs(normal.y), az = Math.abs(normal.z);
        if (ay >= ax && ay >= az) return normal.y >= 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return normal.x >= 0 ? Direction.EAST : Direction.WEST;
        return normal.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Places up to {@code height} blocks of {@code block} starting at {@code start}
     * and stepping along {@code grow}. Non-replaceable blocks are skipped (not
     * fatal) so the pillar still finishes if there's a one-block obstruction.
     *
     * @return number of blocks actually placed
     */
    public static int placeColumn(ServerLevel level, BlockPos start, Direction grow,
                                  int height, Block block) {
        int clamped = Math.max(1, Math.min(MAX_HEIGHT, height));
        int placed = 0;
        BlockState target = block.defaultBlockState();
        for (int i = 0; i < clamped; i++) {
            BlockPos pos = start.relative(grow, i);
            if (!level.isInWorldBounds(pos)) break;
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced() || existing.is(Blocks.AIR)) {
                level.setBlock(pos, target, Block.UPDATE_ALL);
                placed++;
            }
        }
        return placed;
    }
}
