package com.crsocial.witchhatatelier.spell.composition.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.manifest.BlocksManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.NoneManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generic default for {@link EffectType#CRUSH} — "inverts build → destroy":
 * given a {@code Form}'s just-placed {@link BlocksManifestation}, breaks
 * every position it placed rather than leaving them standing. Because the
 * {@code Form} already computed the correct positions for the drawn
 * magnitude (a Column's height, a Dispersion's scatter, …), Crush needs no
 * form-specific geometry of its own — "whatever this spell was about to
 * build, break instead", for every form.
 *
 * <p>{@code CRUSH} cannot carry ({@link EffectType#canCarry()} is {@code
 * false}) — drawn alone with no form it falls to the bare element default or
 * Prepared, per the resolution algorithm's branch 3/4.</p>
 *
 * <p>A {@link com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation}
 * (blockless materials — Air, Water by default) has nothing to un-place; the
 * generic default leaves it untouched. Per-element overrides (see {@code
 * docs/sigils_and_signs.md}) add bespoke destructive flavour on top.</p>
 */
public class CrushEffect implements Effect {

    @Override
    public EffectType type() {
        return EffectType.CRUSH;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MAGNITUDE;
    }

    @Override
    public Manifestation modify(Manifestation in, Material working, CastContext ctx) {
        if (!(in instanceof BlocksManifestation(var positions, BlockState state))) {
            return in;
        }
        ServerLevel level = ctx.level();

        int broken = 0;
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).is(state.getBlock())) continue; // something else changed it since placement
            if (level.destroyBlock(pos, false, ctx.caster())) broken++;
        }
        WitchHatAtelierMod.LOGGER.info("[Composition] Crush broke {}/{} placed block(s).", broken, positions.size());
        return new NoneManifestation();
    }
}
