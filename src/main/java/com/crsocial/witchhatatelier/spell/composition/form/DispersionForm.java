package com.crsocial.witchhatatelier.spell.composition.form;

import com.crsocial.witchhatatelier.spell.compiler.FormType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.manifest.BlocksManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic default for {@link FormType#DISPERSION} — scatters the working
 * material in a flat burst around the cast origin. Blocky materials scatter
 * individual blocks across the disc; blockless materials emit a particle
 * burst of the same radius instead.
 */
public class DispersionForm implements Form {

    private static final int BASE_COUNT = 6;
    private static final float BASE_RADIUS = 2.5f;

    @Override
    public FormType type() {
        return FormType.DISPERSION;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MAGNITUDE;
    }

    @Override
    public Manifestation manifest(Material working, CastContext ctx) {
        float power = Math.max(0.1f, ctx.magnitude().power());
        float aoe = Math.max(0.1f, ctx.magnitude().aoe());
        int count = Math.max(1, Math.round(BASE_COUNT * power));
        float radius = BASE_RADIUS * aoe;
        Vector3f origin = ctx.origin();

        Optional<Block> block = working.asBlock();
        if (block.isPresent()) {
            List<BlockPos> placed = scatterBlocks(ctx.level(), origin, radius, count, block.get());
            return new BlocksManifestation(placed, block.get().defaultBlockState());
        }

        ParticleOptions particle = working.asParticle().orElse(null);
        emitBurst(ctx.level(), origin, radius, count, particle);
        return new ParticlesManifestation(origin, ctx.direction(), particle, radius);
    }

    private static List<BlockPos> scatterBlocks(ServerLevel level, Vector3f origin, float radius,
                                                 int count, Block block) {
        RandomSource random = level.random;
        BlockState target = block.defaultBlockState();
        List<BlockPos> placed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = random.nextDouble() * radius;
            BlockPos pos = BlockPos.containing(
                    origin.x + Math.cos(angle) * dist, origin.y, origin.z + Math.sin(angle) * dist);
            if (!level.isInWorldBounds(pos)) continue;
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced()) {
                level.setBlock(pos, target, Block.UPDATE_ALL);
                placed.add(pos);
            }
        }
        return placed;
    }

    private static void emitBurst(ServerLevel level, Vector3f origin, float radius, int count,
                                  ParticleOptions particle) {
        if (particle == null) return;
        level.sendParticles(particle, origin.x, origin.y, origin.z,
                Math.max(1, count * 3), radius * 0.5, 0.4, radius * 0.5, 0.02);
    }
}
