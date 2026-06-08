package com.crsocial.witchhatatelier.spell.meaning.effect.earth;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import com.crsocial.witchhatatelier.spell.meaning.Origin;
import com.crsocial.witchhatatelier.spell.meaning.SizeScaling;
import com.crsocial.witchhatatelier.spell.meaning.effect.ColumnPlacer;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffects;
import com.crsocial.witchhatatelier.spell.meaning.effect.SpawnBlocksInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Earth + Column → a stone pillar rising from the glyph surface. With a Crush
 * Force sign in the same ring it inverts to <em>carving</em>: instead of placing
 * stone along the column it breaks the blocks the column would have passed
 * through, drilling a tunnel (the old standalone {@code excavate} behaviour, now a
 * Crush-mode of the pillar). On a <b>surface cast</b> (placed paper / inscribed
 * block) the carve drills <em>into</em> the surface — the inverse of the pillar's
 * outward growth — so it breaks the blocks underneath the placed paper; a
 * <b>hand cast</b> keeps the player's live aim and drills straight along it (see
 * {@link #crushDirection}). Height/depth scale with
 * {@code blocks_per_magnitude × count × size × quality}, capped by
 * {@link ColumnPlacer#MAX_HEIGHT}.
 */
public final class EarthPillarEffect extends PillarEffect {

    public static final String KEY = "earth_pillar";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    protected @Nullable Block defaultBlock() {
        return Blocks.STONE;
    }

    @Override
    protected @Nullable ParticleOptions trailParticle() {
        return null;
    }

    /** Crush-mode: break blocks along the column direction rather than placing them. */
    @Override
    @SuppressWarnings("unchecked")
    protected void crush(ServerLevel level, Player caster, ExecutableSpell spell, BehaviorOp op) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
            WitchHatAtelierMod.LOGGER.debug("[{}] Crush with no spawn_blocks instructions; nothing to carve.", KEY);
            return;
        }
        Magnitude m = spell.magnitude();
        float quality = Math.max(0.1f, m.quality());
        float reachScale = SizeScaling.powerMultiplier(m.sizeNormalized());
        Vector3f origin = spell.originWorld();
        Vector3f dir = crushDirection(spell);
        int count = Math.max(1, op.count());

        for (EffectInstruction ins : (List<EffectInstruction>) raw) {
            if (!(ins instanceof SpawnBlocksInstruction sb)) continue;
            int depth = Math.max(1, Math.round(sb.blocksPerMagnitude() * count * reachScale * quality));
            int broken = carve(level, caster, origin, dir, depth);
            WitchHatAtelierMod.LOGGER.info(
                    "[{}] CRUSH carved {}/{} block(s) from {} along ({}, {}, {}) (count={}, quality={}).",
                    KEY, broken, depth, BlockPos.containing(origin.x, origin.y, origin.z),
                    String.format(java.util.Locale.ROOT, "%.2f", dir.x),
                    String.format(java.util.Locale.ROOT, "%.2f", dir.y),
                    String.format(java.util.Locale.ROOT, "%.2f", dir.z), count,
                    String.format(java.util.Locale.ROOT, "%.2f", quality));
        }
    }

    /**
     * Direction the carve drills. A surface cast (placed paper / inscribed block)
     * grows its pillar <em>out</em> of the surface along the normal, so carving in
     * that direction would chew through empty air above the glyph — useless. We
     * invert it there so Crush bites <em>into</em> the surface, breaking the blocks
     * underneath (or behind) the placed paper. A hand cast re-aims to the crosshair
     * every tick, so its {@link PillarEffects#columnGrow column direction} is already
     * the player's aim — we keep that untouched and drill straight along it.
     */
    private static Vector3f crushDirection(ExecutableSpell spell) {
        Vector3f dir = PillarEffects.columnGrow(spell);
        if (spell.origin() != Origin.HAND) dir.negate();
        return dir;
    }

    /**
     * Breaks up to {@code depth} blocks from {@code origin} along {@code dir}. Air
     * and unbreakable blocks (negative destroy speed, e.g. bedrock) are skipped,
     * not fatal; returns the count actually destroyed.
     */
    private static int carve(ServerLevel level, Player caster, Vector3f origin, Vector3f dir, int depth) {
        int clamped = Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT, depth));
        int broken = 0;
        for (BlockPos pos : ColumnPlacer.traverse(origin, dir, clamped)) {
            if (!level.isInWorldBounds(pos)) break;
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir()) continue;
            if (existing.getDestroySpeed(level, pos) < 0f) continue; // bedrock & friends
            if (level.destroyBlock(pos, false, caster)) broken++;
        }
        return broken;
    }
}
