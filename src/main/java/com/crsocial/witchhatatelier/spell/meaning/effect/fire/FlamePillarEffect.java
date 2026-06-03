package com.crsocial.witchhatatelier.spell.meaning.effect.fire;

import com.crsocial.witchhatatelier.spell.cast.CastContext;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.effect.AbstractPillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Fire + Column → a roaring pillar of flame erupting from the glyph along the
 * casting-surface normal. Mirrors
 * {@link com.crsocial.witchhatatelier.spell.meaning.effect.earth.StonePillarEffect}
 * but with fire as the default block.
 */
public final class FlamePillarEffect extends AbstractPillarEffect {

    public static final String KEY = "flame_pillar";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            PillarEffects.executeColumn(level, spell, op, Blocks.FIRE, KEY);
            PillarEffects.emitColumnTrail(level, spell, op, ParticleTypes.FLAME);
        }
    }

    /**
     * Re-emits the flame trail each channel tick so the pillar keeps licking
     * upward from the source for the cast's duration (blocks are placed once by
     * {@link #execute}; only the particles need refreshing).
     */
    @Override
    public void tick(CastContext ctx) {
        if (ctx.level() == null) return;
        for (BehaviorOp op : ctx.spell().ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            PillarEffects.emitColumnTrail(ctx.level(), ctx.spell(), op, ParticleTypes.FLAME);
        }
    }
}
