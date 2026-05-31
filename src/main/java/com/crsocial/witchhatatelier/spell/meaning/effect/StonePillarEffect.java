package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Earth + Column → a stone pillar rising from the glyph surface, height scaled
 * by {@code Magnitude.size × quality × blocks_per_magnitude} and capped by
 * {@link ColumnPlacer#MAX_HEIGHT}.
 */
public final class StonePillarEffect extends AbstractPillarEffect {

    public static final String KEY = "stone_pillar";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            PillarEffects.executeColumn(level, spell, op, Blocks.STONE, KEY);
        }
    }
}
