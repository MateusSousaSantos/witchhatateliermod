package com.crsocial.witchhatatelier.spell.meaning.effect.fire;

import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffect;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Fire + Column → a roaring pillar of flame erupting from the glyph along the
 * casting-surface normal, with a {@link ParticleTypes#FLAME} trail the base
 * re-emits each channel tick. Has no Crush behaviour yet — under a Crush sign it
 * falls back to the base no-op rather than placing fire.
 */
public final class FirePillarEffect extends PillarEffect {

    public static final String KEY = "fire_pillar";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    protected @Nullable Block defaultBlock() {
        return Blocks.FIRE;
    }

    @Override
    protected @Nullable ParticleOptions trailParticle() {
        return ParticleTypes.FLAME;
    }
}
