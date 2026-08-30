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
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Generic default for {@link FormType#COLUMN} — extrudes a column of the
 * working material from the cast origin along the resolved direction. Any
 * element combines with Column "for free" through this class alone: a block
 * material places a column of that block, a particle material (Air, Water by
 * default) emits a trail instead, since there's no block to place.
 * Per-element overrides (see {@code docs/sigils_and_signs.md}) layer bespoke
 * behaviour on top of this same geometry.
 *
 * <p>{@code non-final} so an override can {@code extends ColumnForm} and
 * reuse {@link #manifest} via {@code super}.</p>
 */
public class ColumnForm implements Form {

    /** Blocks per magnitude at reference size/quality with no stacking. */
    private static final float BLOCKS_PER_MAGNITUDE = 3f;
    private static final float DEFAULT_PARTICLE_REACH = 4f;

    @Override
    public FormType type() {
        return FormType.COLUMN;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MAGNITUDE;
    }

    @Override
    public Manifestation manifest(Material working, CastContext ctx) {
        Vector3f origin = ctx.origin();
        Vector3f dir = ctx.direction();
        int height = heightFor(ctx);

        Optional<Block> block = working.asBlock();
        if (block.isPresent()) {
            List<BlockPos> placed = ColumnGeometry.placeColumn(ctx.level(), origin, dir, height, block.get());
            return new BlocksManifestation(placed, block.get().defaultBlockState());
        }

        ParticleOptions particle = working.asParticle().orElse(null);
        float reach = Math.max(DEFAULT_PARTICLE_REACH, height);
        ColumnGeometry.emitTrail(ctx.level(), origin, dir, Math.round(reach), particle, 2f);
        return new ParticlesManifestation(origin, dir, particle, reach);
    }

    /** Column height for {@code ctx}'s resolved magnitude. Shared with per-element overrides. */
    public static int heightFor(CastContext ctx) {
        float quality = Math.max(0.1f, ctx.magnitude().quality());
        float power = Math.max(0.1f, ctx.magnitude().power());
        return Math.max(1, Math.round(BLOCKS_PER_MAGNITUDE * power * quality));
    }
}
