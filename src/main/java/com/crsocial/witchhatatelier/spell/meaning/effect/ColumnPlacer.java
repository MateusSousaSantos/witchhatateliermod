package com.crsocial.witchhatatelier.spell.meaning.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a column of blocks from a starting position, extruding along a
 * casting-surface normal. Used by column-shaped effects (stone pillar, flame
 * pillar) so they share identical placement semantics — air / replaceable
 * blocks are overwritten, solid blocks are skipped but do not abort the run.
 *
 * <p>The grow direction is the <em>continuous</em> surface normal: a diagonal
 * aim produces a diagonal column. Blocks are laid down with a voxel
 * (Amanatides–Woo) traversal so a diagonal stays face-connected — a clean
 * staircase rather than corner-touching gaps — and a cardinal direction reduces
 * to stepping one block at a time along that axis (the old behaviour).</p>
 */
public final class ColumnPlacer {

    public static final int MAX_HEIGHT = 32;

    private ColumnPlacer() {}

    /**
     * Normalizes a surface normal into a grow direction, defaulting to straight
     * up for a degenerate (near-zero) normal. Unlike a cardinal {@code Direction},
     * this preserves diagonal aim.
     */
    public static Vector3f growDirection(Vector3f normal) {
        if (normal == null || normal.lengthSquared() < 1e-6f) return new Vector3f(0f, 1f, 0f);
        return new Vector3f(normal).normalize();
    }

    /**
     * Returns the {@code count} face-connected block positions a ray from
     * {@code origin} along {@code dir} passes through, via a voxel traversal.
     * Each step advances to the next voxel across the nearest axis boundary, so
     * consecutive blocks always share a face — no diagonal corner gaps.
     */
    public static List<BlockPos> traverse(Vector3f origin, Vector3f dir, int count) {
        int n = Math.max(1, count);
        List<BlockPos> out = new ArrayList<>(n);
        Vector3f d = growDirection(dir);

        int x = Mth.floor(origin.x), y = Mth.floor(origin.y), z = Mth.floor(origin.z);
        int stepX = sign(d.x), stepY = sign(d.y), stepZ = sign(d.z);
        double tMaxX = tMax(origin.x, x, d.x, stepX);
        double tMaxY = tMax(origin.y, y, d.y, stepY);
        double tMaxZ = tMax(origin.z, z, d.z, stepZ);
        double tDeltaX = d.x != 0f ? 1.0 / Math.abs(d.x) : Double.POSITIVE_INFINITY;
        double tDeltaY = d.y != 0f ? 1.0 / Math.abs(d.y) : Double.POSITIVE_INFINITY;
        double tDeltaZ = d.z != 0f ? 1.0 / Math.abs(d.z) : Double.POSITIVE_INFINITY;

        out.add(new BlockPos(x, y, z));
        while (out.size() < n) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { x += stepX; tMaxX += tDeltaX; }
            else if (tMaxY <= tMaxZ) { y += stepY; tMaxY += tDeltaY; }
            else { z += stepZ; tMaxZ += tDeltaZ; }
            out.add(new BlockPos(x, y, z));
        }
        return out;
    }

    /**
     * Places up to {@code height} blocks of {@code block} from {@code origin}
     * along the continuous {@code dir}. Non-replaceable blocks are skipped (not
     * fatal) so the pillar still finishes past a one-block obstruction.
     *
     * @return number of blocks actually placed
     */
    public static int placeColumn(ServerLevel level, Vector3f origin, Vector3f dir,
                                  int height, Block block) {
        int clamped = Math.max(1, Math.min(MAX_HEIGHT, height));
        int placed = 0;
        BlockState target = block.defaultBlockState();
        for (BlockPos pos : traverse(origin, dir, clamped)) {
            if (!level.isInWorldBounds(pos)) break;
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced() || existing.is(Blocks.AIR)) {
                level.setBlock(pos, target, Block.UPDATE_ALL);
                placed++;
            }
        }
        return placed;
    }

    private static int sign(float v) {
        return v > 0f ? 1 : (v < 0f ? -1 : 0);
    }

    /** Parametric distance from {@code originComp} to the first voxel boundary along one axis. */
    private static double tMax(double originComp, int voxel, float dirComp, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? voxel + 1 : voxel;
        return (boundary - originComp) / dirComp;
    }
}
