package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Fire + Column → a roaring pillar of flame erupting from the glyph along the
 * casting-surface normal. Mirrors {@link StonePillarEffect} but with fire as
 * the default block.
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
        }
    }
}
