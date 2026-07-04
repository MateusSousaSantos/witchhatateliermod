package com.crsocial.witchhatatelier.spell.meaning.effect.fire;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.SizeScaling;
import com.crsocial.witchhatatelier.spell.meaning.effect.ColumnPlacer;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffects;
import com.crsocial.witchhatatelier.spell.meaning.effect.SpawnBlocksInstruction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Fire + Column → a roaring pillar of flame erupting from the glyph along the
 * casting-surface normal, with a {@link ParticleTypes#FLAME} trail the base
 * re-emits each channel tick. Beyond placing fire blocks, the column actively
 * <b>ignites and scorches</b> any entity caught along its length (see
 * {@link #affectEntities}). With a Crush Force sign in the same ring it inverts to a
 * <em>detonation</em>: instead of laying a sustained column of fire it delivers one
 * concentrated blast along the column line — a wider burn radius, heavier one-shot
 * damage, and a longer ignite than the create-mode column (see {@link #crush}). It
 * places no fire blocks (Crush is the destructive counterpart), so it won't start a
 * spreading terrain fire the way the create column can. Blast strength scales with
 * {@code power}; reach scales with {@code blocks_per_magnitude × count × size × quality}.
 */
public final class FirePillarEffect extends PillarEffect {

    public static final String KEY = "fire_pillar";

    /** Base distance an entity may be from the column line and still catch fire (× aoe). */
    private static final double BURN_RADIUS = 1.0;
    /** Seconds of burning applied at reference power; scales up with power, refreshed each tick. */
    private static final float BURN_SECONDS_BASE = 3.0f;
    /** Hardest burn a heavy cast can apply, in seconds. */
    private static final float BURN_SECONDS_MAX = 10.0f;
    /** Direct damage of a one-shot (surface) cast at reference power, before power scaling. */
    private static final float ONE_SHOT_DAMAGE = 4.0f;
    /** Direct damage per channel tick; vanilla i-frames throttle this to a steady burn. */
    private static final float PER_TICK_DAMAGE = 1.0f;
    /** Crush detonation widens the burn radius over the create-mode column. */
    private static final double CRUSH_RADIUS_MULT = 1.75;
    /** Crush detonation direct damage at reference power, before power scaling (> one-shot column). */
    private static final float CRUSH_DAMAGE = 9.0f;
    /** Seconds of burning a crush detonation applies at reference power (longer than the column). */
    private static final float CRUSH_BURN_SECONDS = 8.0f;

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

    /** Ignite and scorch entities standing in the column; a one-shot cast hits harder than a channel tick. */
    @Override
    @SuppressWarnings("unchecked")
    protected void affectEntities(ServerLevel level, @Nullable Player caster,
                                  ExecutableSpell spell, BehaviorOp op, boolean oneShot) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) return;

        Vector3f o = spell.originWorld();
        // Burn along the SAME axis the visual column grows on (Column-sign skew
        // included) so the flame and the rendered pillar stay aligned.
        Vector3f dir = PillarEffects.columnGrow(spell);
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());
        float quality = Math.max(0.1f, spell.magnitude().quality());
        float power = Math.max(0.1f, spell.magnitude().power());
        float aoe = Math.max(1f, spell.magnitude().aoe());
        int count = Math.max(1, op.count());
        float reachScale = SizeScaling.powerMultiplier(size); // size→reach curve

        // Reach the longest block-column among this op's instructions — matches the
        // height PillarEffects.executeColumn places fire blocks along.
        int height = 0;
        for (EffectInstruction ins : (List<EffectInstruction>) raw) {
            if (ins instanceof SpawnBlocksInstruction sb) {
                int h = Math.round(sb.blocksPerMagnitude() * count * reachScale * quality);
                height = Math.max(height, Math.min(ColumnPlacer.MAX_HEIGHT, h));
            }
        }
        if (height <= 0) return;

        Vec3 start = new Vec3(o.x, o.y, o.z);
        Vec3 d = new Vec3(dir.x, dir.y, dir.z); // unit (growDirection normalizes)
        Vec3 end = start.add(d.scale(height));
        double radius = BURN_RADIUS * aoe;

        float powerScale = Mth.clamp(power, 0.5f, 3.0f);
        float burnSeconds = Mth.clamp(BURN_SECONDS_BASE + power, BURN_SECONDS_BASE, BURN_SECONDS_MAX);
        float damage = (oneShot ? ONE_SHOT_DAMAGE : PER_TICK_DAMAGE) * powerScale;

        // Attribute the burn to the caster so kills are credited (and PvP rules apply);
        // fall back to an unattributed fire source when there's no live caster.
        DamageSource fire = caster != null
                ? level.damageSources().source(DamageTypes.ON_FIRE, caster)
                : level.damageSources().inFire();

        AABB box = new AABB(start, end).inflate(radius);
        for (Entity e : level.getEntities((Entity) null, box,
                ent -> ent.isAlive() && !ent.isSpectator())) {
            if (e == caster) continue; // don't burn yourself with your own column
            if (PillarEffects.distanceToSegment(e.getBoundingBox().getCenter(), start, end) > radius) continue;
            // Refresh the burn each application so a sustained channel keeps the
            // target alight; the vanilla fire tick carries the damage-over-time.
            e.igniteForSeconds(burnSeconds);
            e.hurt(fire, damage);
        }
    }

    /**
     * Crush-mode: a one-shot fire detonation along the column line. Unlike the
     * create-mode column (which lays fire blocks and burns whatever stands in it every
     * tick), this fires a single concentrated blast — wider radius, heavier damage, a
     * longer ignite — and places no blocks. It runs once on {@code begin} and is skipped
     * on channel ticks (see {@link PillarEffect#tick}).
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void crush(ServerLevel level, Player caster, ExecutableSpell spell, BehaviorOp op) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
            WitchHatAtelierMod.LOGGER.debug("[{}] Crush with no spawn_blocks instructions; nothing to detonate.", KEY);
            return;
        }
        Vector3f o = spell.originWorld();
        Vector3f dir = PillarEffects.columnGrow(spell); // same axis the column would grow on
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());
        float quality = Math.max(0.1f, spell.magnitude().quality());
        float power = Math.max(0.1f, spell.magnitude().power());
        float aoe = Math.max(1f, spell.magnitude().aoe());
        int count = Math.max(1, op.count());
        float reachScale = SizeScaling.powerMultiplier(size);

        // Reach the longest block-column among this op's instructions — matches where the
        // create-mode column would have ended, so the blast covers the same line.
        int height = 0;
        for (EffectInstruction ins : (List<EffectInstruction>) raw) {
            if (ins instanceof SpawnBlocksInstruction sb) {
                int h = Math.round(sb.blocksPerMagnitude() * count * reachScale * quality);
                height = Math.max(height, Math.min(ColumnPlacer.MAX_HEIGHT, h));
            }
        }
        if (height <= 0) return;

        Vec3 start = new Vec3(o.x, o.y, o.z);
        Vec3 d = new Vec3(dir.x, dir.y, dir.z); // unit (growDirection normalizes)
        Vec3 end = start.add(d.scale(height));
        double radius = BURN_RADIUS * CRUSH_RADIUS_MULT * aoe;

        float powerScale = Mth.clamp(power, 0.5f, 3.0f);
        float burnSeconds = Mth.clamp(CRUSH_BURN_SECONDS + power, CRUSH_BURN_SECONDS, BURN_SECONDS_MAX);
        float damage = CRUSH_DAMAGE * powerScale;

        DamageSource fire = caster != null
                ? level.damageSources().source(DamageTypes.ON_FIRE, caster)
                : level.damageSources().inFire();

        AABB box = new AABB(start, end).inflate(radius);
        int hit = 0;
        for (Entity e : level.getEntities((Entity) null, box,
                ent -> ent.isAlive() && !ent.isSpectator())) {
            if (e == caster) continue; // don't detonate on yourself
            if (PillarEffects.distanceToSegment(e.getBoundingBox().getCenter(), start, end) > radius) continue;
            e.igniteForSeconds(burnSeconds);
            e.hurt(fire, damage);
            hit++;
        }
        WitchHatAtelierMod.LOGGER.info(
                "[{}] CRUSH detonation scorched {} entit(ies) along a {}-block line (power={}, radius={}).",
                KEY, hit, height,
                String.format(java.util.Locale.ROOT, "%.2f", power),
                String.format(java.util.Locale.ROOT, "%.2f", radius));
    }
}
