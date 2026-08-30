package com.crsocial.witchhatatelier.spell.composition.form;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Element-agnostic column placement/trail geometry shared by {@link
 * ColumnForm} and any per-element override — every element's column uses
 * identical placement/trail semantics; only the block/particle differ, and
 * those come from the working {@link
 * com.crsocial.witchhatatelier.spell.composition.material.Material}.
 */
public final class ColumnGeometry {

    public static final int MAX_HEIGHT = 32;

    private ColumnGeometry() {
    }

    /**
     * Returns the {@code count} face-connected block positions a ray from
     * {@code origin} along {@code dir} passes through, via a voxel
     * (Amanatides–Woo) traversal — a diagonal aim stays face-connected (a
     * clean staircase, not corner-touching gaps) and a cardinal direction
     * reduces to stepping one block at a time along that axis.
     */
    public static List<BlockPos> traverse(Vector3f origin, Vector3f dir, int count) {
        int n = Math.max(1, count);
        List<BlockPos> out = new ArrayList<>(n);
        Vector3f d = dir == null || dir.lengthSquared() < 1e-6f ? new Vector3f(0f, 1f, 0f) : new Vector3f(dir).normalize();

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
     * Places up to {@code height} blocks of {@code block} from {@code
     * origin} along the continuous {@code dir}. Non-replaceable blocks are
     * skipped (not fatal) so the column still finishes past a one-block
     * obstruction.
     *
     * @return the positions actually placed, in traversal order
     */
    public static List<BlockPos> placeColumn(ServerLevel level, Vector3f origin, Vector3f dir,
                                             int height, Block block) {
        int clamped = Math.max(1, Math.min(MAX_HEIGHT, height));
        List<BlockPos> placed = new ArrayList<>(clamped);
        BlockState target = block.defaultBlockState();
        for (BlockPos pos : traverse(origin, dir, clamped)) {
            if (!level.isInWorldBounds(pos)) break;
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced()) {
                level.setBlock(pos, target, Block.UPDATE_ALL);
                placed.add(pos);
            }
        }
        return placed;
    }

    /**
     * Emits a one-shot trail of {@code particle} along the continuous
     * diagonal from {@code origin} to {@code origin + dir*height}. Composing
     * is a one-shot event today (no channel/tick loop exists yet), so this
     * fires once rather than every tick.
     */
    public static void emitTrail(ServerLevel level, Vector3f origin, Vector3f dir,
                                 int height, ParticleOptions particle, float perPointCount) {
        if (particle == null) return;
        int clamped = Math.max(1, Math.min(MAX_HEIGHT, height));
        int perPoint = Math.max(1, Math.round(perPointCount));
        Vector3f d = dir == null || dir.lengthSquared() < 1e-6f ? new Vector3f(0f, 1f, 0f) : new Vector3f(dir).normalize();
        for (int i = 0; i < clamped; i++) {
            double px = origin.x + d.x * (i + 0.5);
            double py = origin.y + d.y * (i + 0.5);
            double pz = origin.z + d.z * (i + 0.5);
            level.sendParticles(particle, px, py, pz, perPoint, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private static int sign(float v) {
        return v > 0f ? 1 : (v < 0f ? -1 : 0);
    }

    private static double tMax(double originComp, int voxel, float dirComp, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? voxel + 1 : voxel;
        return (boundary - originComp) / dirComp;
    }
}
