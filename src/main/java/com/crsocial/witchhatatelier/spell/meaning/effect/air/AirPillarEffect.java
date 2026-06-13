package com.crsocial.witchhatatelier.spell.meaning.effect.air;

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
 * speed. Has no Crush behaviour yet.</p>
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
    protected void affectEntities(ServerLevel level, ExecutableSpell spell, BehaviorOp op, boolean oneShot) {
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
}
