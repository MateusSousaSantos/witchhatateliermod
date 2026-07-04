package com.crsocial.witchhatatelier.spell.meaning.effect.air;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.SizeScaling;
import com.crsocial.witchhatatelier.spell.meaning.effect.ColumnPlacer;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.PillarEffects;
import com.crsocial.witchhatatelier.spell.meaning.effect.SpawnParticlesInstruction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Air + Column → a blockless pillar of wind. Renders like the flame pillar — a
 * {@link ParticleTypes#CLOUD} column trail along the casting direction, re-emitted
 * each channel tick by the base — but places no blocks and instead shoves entities
 * caught in the column along that axis (an updraft when cast straight up).
 *
 * <p>The matrix cell's {@code spawn_particles.reach} is the column length in blocks
 * (scaled by sign-count × size); {@code 0}/absent falls back to {@link #DEFAULT_REACH}.
 * A surface cast applies one strong shove ({@link #ONE_SHOT_PUSH}); a channeled hand
 * cast applies a gentler per-tick lift ({@link #PER_TICK_PUSH}) toward a terminal
 * speed.</p>
 *
 * <p>With a Crush Force sign in the same ring it inverts to an <em>implosion</em>: a
 * single burst that yanks entities <em>inward</em> onto the column line rather than
 * lifting them along it — the destructive, compressing counterpart to the updraft (see
 * {@link #crush}). It runs once on {@code begin} and is skipped on channel ticks.</p>
 */
public final class AirPillarEffect extends PillarEffect {

    public static final String KEY = "air_pillar";

    /** Column length (blocks) used when an instruction sets no {@code reach}. */
    private static final double DEFAULT_REACH = 4.0;
    /** Base distance an entity may be from the column line and still be pushed. */
    private static final double PUSH_RADIUS = 1.0;
    /** Velocity (blocks/tick) added by a one-shot (surface) cast, before power scaling. */
    private static final double ONE_SHOT_PUSH = 0.3;
    /** Velocity added per channel tick — small so a sustained channel lifts rather than flings. */
    private static final double PER_TICK_PUSH = 0.10;
    /** Hard cap on the per-application velocity add, so heavy casts can't launch to the void. */
    private static final double MAX_PUSH = 2.0;
    /** Terminal speed along the column axis — sustained channels lift toward this, not past it. */
    private static final double TERMINAL_ALONG_AXIS = 1.5;
    /** Crush implosion reaches further than the create-mode push, to gather entities in. */
    private static final double CRUSH_RADIUS_MULT = 2.0;
    /** Inward velocity (blocks/tick) an implosion adds at reference power, before power scaling. */
    private static final double CRUSH_PULL = 0.6;
    /** Hard cap on the per-entity inward velocity add, so a heavy implosion still can't fling. */
    private static final double CRUSH_MAX_PULL = 2.0;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    protected @Nullable Block defaultBlock() {
        return null; // blockless — wind places nothing
    }

    @Override
    protected @Nullable ParticleOptions trailParticle() {
        return ParticleTypes.CLOUD;
    }

    /** Shove entities along the column axis; stronger for a one-shot surface cast than a channel tick. */
    @Override
    protected void affectEntities(ServerLevel level, @Nullable Player caster,
                                  ExecutableSpell spell, BehaviorOp op, boolean oneShot) {
        push(level, spell, op, oneShot ? ONE_SHOT_PUSH : PER_TICK_PUSH);
    }

    @SuppressWarnings("unchecked")
    private void push(ServerLevel level, ExecutableSpell spell, BehaviorOp op, double baseStrength) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) return;

        Vector3f o = spell.originWorld();
        // Shove along the SAME axis the visual column grows on (Column-sign skew
        // included) so the push and the rendered pillar stay aligned.
        Vector3f dir = PillarEffects.columnGrow(spell);
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());
        float power = Math.max(0.1f, spell.magnitude().power());
        float aoe = Math.max(1f, spell.magnitude().aoe());
        int count = Math.max(1, op.count());

        // The shove uses the longest column among this op's instructions.
        int height = 0;
        for (EffectInstruction ins : (List<EffectInstruction>) raw) {
            if (ins instanceof SpawnParticlesInstruction sp) {
                height = Math.max(height, columnHeight(sp.reach(), count, size));
            }
        }
        if (height <= 0) return;

        Vec3 start = new Vec3(o.x, o.y, o.z);
        Vec3 d = new Vec3(dir.x, dir.y, dir.z); // unit (growDirection normalizes)
        Vec3 end = start.add(d.scale(height));
        double radius = PUSH_RADIUS * aoe;
        double mag = Math.min(MAX_PUSH, baseStrength * Mth.clamp(power, 0.5f, 3.0f));
        AABB box = new AABB(start, end).inflate(radius);

        for (Entity e : level.getEntities((Entity) null, box,
                ent -> ent.isAlive() && !ent.isSpectator())) {
            if (distanceToSegment(e.getBoundingBox().getCenter(), start, end) > radius) continue;

            // Cap velocity *along the column axis* at terminal so a sustained channel
            // lifts toward it rather than accelerating without bound; perpendicular
            // motion is left untouched.
            Vec3 vel = e.getDeltaMovement();
            double along = vel.dot(d);
            double addMag = Math.min(mag, Math.max(0.0, TERMINAL_ALONG_AXIS - along));
            if (addMag <= 0) continue;

            e.setDeltaMovement(vel.add(d.scale(addMag)));
            e.hurtMarked = true; // force the server to resync velocity for non-players
            if (e instanceof ServerPlayer p) {
                // Players own their movement client-side; push them explicitly.
                p.connection.send(new ClientboundSetEntityMotionPacket(p));
            }
        }
    }

    /**
     * Crush-mode: a one-shot implosion that yanks entities inward onto the column
     * line. The create-mode push accelerates entities <em>along</em> the axis (an
     * updraft); this pulls them <em>toward</em> the axis instead — the compressing,
     * destructive counterpart. One-shot: fired on {@code begin}, skipped on ticks.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void crush(ServerLevel level, Player caster, ExecutableSpell spell, BehaviorOp op) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
            WitchHatAtelierMod.LOGGER.debug("[{}] Crush with no spawn_particles instructions; nothing to implode.", KEY);
            return;
        }
        Vector3f o = spell.originWorld();
        Vector3f dir = PillarEffects.columnGrow(spell);
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());
        float power = Math.max(0.1f, spell.magnitude().power());
        float aoe = Math.max(1f, spell.magnitude().aoe());
        int count = Math.max(1, op.count());

        int height = 0;
        for (EffectInstruction ins : (List<EffectInstruction>) raw) {
            if (ins instanceof SpawnParticlesInstruction sp) {
                height = Math.max(height, columnHeight(sp.reach(), count, size));
            }
        }
        if (height <= 0) return;

        Vec3 start = new Vec3(o.x, o.y, o.z);
        Vec3 d = new Vec3(dir.x, dir.y, dir.z); // unit (growDirection normalizes)
        Vec3 end = start.add(d.scale(height));
        double radius = PUSH_RADIUS * CRUSH_RADIUS_MULT * aoe;
        double mag = Math.min(CRUSH_MAX_PULL, CRUSH_PULL * Mth.clamp(power, 0.5f, 3.0f));
        AABB box = new AABB(start, end).inflate(radius);

        int hit = 0;
        for (Entity e : level.getEntities((Entity) null, box,
                ent -> ent.isAlive() && !ent.isSpectator())) {
            if (e == caster) continue; // don't yank the caster into their own column
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 axisPoint = closestPointOnSegment(center, start, end);
            Vec3 inward = axisPoint.subtract(center);
            double dist = inward.length();
            if (dist > radius || dist < 1e-3) continue; // out of range, or already on the line
            Vec3 pullDir = inward.scale(1.0 / dist);
            // Stronger pull the closer to the line's outer reach — a compressing snap inward.
            e.setDeltaMovement(e.getDeltaMovement().add(pullDir.scale(mag)));
            e.hurtMarked = true;
            if (e instanceof ServerPlayer p) {
                p.connection.send(new ClientboundSetEntityMotionPacket(p));
            }
            hit++;
        }
        WitchHatAtelierMod.LOGGER.info(
                "[{}] CRUSH implosion pulled {} entit(ies) toward a {}-block line (power={}, radius={}).",
                KEY, hit, height,
                String.format(java.util.Locale.ROOT, "%.2f", power),
                String.format(java.util.Locale.ROOT, "%.2f", radius));
    }

    private static int columnHeight(float reach, int count, float size) {
        double base = reach > 0f ? reach : DEFAULT_REACH;
        float reachScale = SizeScaling.powerMultiplier(size); // size→reach curve
        return Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT, (int) Math.round(base * count * reachScale)));
    }

    /** Shortest distance from point {@code p} to the segment {@code a→b}. */
    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-6) return p.distanceTo(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Closest point on the segment {@code a→b} to {@code p} — the implosion pull target. */
    private static Vec3 closestPointOnSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-6) return a;
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return a.add(ab.scale(t));
    }
}
