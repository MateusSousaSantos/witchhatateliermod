package com.crsocial.witchhatatelier.spell.composition.effect;

import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.material.BlockMaterial;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Marker for {@link EffectType#COLLECTION} — "amplifies the spell for free
 * using nearby matching material, without spending extra mana" (see {@code
 * docs/new_spell_engine.md} §9's cost-exemption rule). Unlike every other
 * effect, Collection does not run through {@code modify}/{@code carry} at
 * all: it's a dedicated <b>post-pass</b> in {@code CompositionEngine} (step
 * 4, after cost is finalized from everything else). {@code modify}/{@code
 * carry} are therefore identity/unused here; {@link #freeBonus} is the real
 * logic, called directly by {@code CompositionEngine}.
 *
 * <p>{@link StackingMode#MODIFIER} — its own draw-count never amplifies;
 * drawing Collection twice doesn't scan a wider radius. The bonus scales
 * only with how much matching material is actually nearby.</p>
 */
public final class CollectionEffect implements Effect {

    private static final int SCAN_RADIUS = 4;
    /** Power/AoE bonus per matching block found, before the hard cap. */
    private static final float BONUS_PER_MATCH = 0.02f;
    /** Hard ceiling on the free bonus, so a dense cluster of matching blocks can't trivialize cost scaling. */
    private static final float MAX_BONUS = 1.5f;

    @Override
    public EffectType type() {
        return EffectType.COLLECTION;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MODIFIER;
    }

    /**
     * Scans a cube of {@link #SCAN_RADIUS} around {@code origin} for blocks
     * matching {@code working} (only meaningful for a {@link BlockMaterial}
     * — blockless elements have nothing to collect and always score zero)
     * and returns the free power/AoE multiplier bonus, already capped at
     * {@link #MAX_BONUS}.
     */
    public static float freeBonus(ServerLevel level, Vec3 origin, Material working) {
        if (!(working instanceof BlockMaterial(var block))) return 0f;

        int matches = 0;
        BlockPos center = BlockPos.containing(origin.x, origin.y, origin.z);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            if (level.getBlockState(pos).is(block)) matches++;
        }
        return Math.min(MAX_BONUS, matches * BONUS_PER_MATCH);
    }
}
