package com.crsocial.witchhatatelier.spell.meaning.effect.air;

import com.crsocial.witchhatatelier.spell.cast.CastContext;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.effect.*;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * {@code behavior_kind: "wind_pillar"} — Air + Column. Renders exactly like the
 * stone/flame pillars — a continuous particle column along the casting direction
 * via {@link PillarEffects#emitColumnTrail}, so it tracks a diagonal aim — but with
 * {@link #PILLAR_PARTICLE} instead of flame and no placed blocks. It also shoves
 * entities caught in the column along that axis (an updraft when cast straight up).
 *
 * <p>The matrix cell's {@code spawn_particles.reach} field is the column length in
 * blocks (scaled by sign-count × size); {@code 0}/absent falls back to
 * {@link #DEFAULT_REACH}. A surface cast fires one burst + shove via
 * {@link #execute}; a channeled hand cast streams the column and a gentler per-tick
 * lift via {@link #tick}.</p>
 */
public final class WindPillarEffect implements EffectKind {

    public static final String KEY = "wind_pillar";

    /** Particle the pillar is drawn from — swap this to restyle the column. */
    private static final ParticleOptions PILLAR_PARTICLE = ParticleTypes.CLOUD;

    /** Column length (blocks) used when an instruction sets no {@code reach}. */
    private static final double DEFAULT_REACH = 4.0;
    /** Base distance an entity may be from the column line and still be pushed. */
    private static final double PUSH_RADIUS = 1.0;
    /** Velocity (blocks/tick) added by a one-shot (surface) cast, before power scaling. */
    private static final double ONE_SHOT_PUSH = 0.7;
    /** Velocity added per channel tick — small so a sustained channel lifts rather than flings. */
    private static final double PER_TICK_PUSH = 0.12;
    /** Hard cap on the per-application velocity add, so heavy casts can't launch to the void. */
    private static final double MAX_PUSH =2.5;
    /** Terminal speed along the column axis — sustained channels lift toward this, not past it. */
    private static final double TERMINAL_ALONG_AXIS = 2.5;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }

    /** Surface (placed-paper) cast: one column burst + a single shove. */
    @Override
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        emit(level, spell);
        push(level, spell, ONE_SHOT_PUSH);
    }

    /** Channeled hand cast: stream the column and apply a gentle per-tick lift. */
    @Override
    public void tick(CastContext ctx) {
        if (ctx.level() == null) return;
        emit(ctx.level(), ctx.spell());
        push(ctx.level(), ctx.spell(), PER_TICK_PUSH);
    }

    // ── particles ────────────────────────────────────────────────────────────────

    /** Draws the pillar as a flame-pillar-style {@link PILLAR_PARTICLE} column trail. */
    private void emit(ServerLevel level, ExecutableSpell spell) {
        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            PillarEffects.emitColumnTrail(level, spell, op, PILLAR_PARTICLE);
        }
    }

    // ── knockback ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void push(ServerLevel level, ExecutableSpell spell, double baseStrength) {
        Vector3f o = spell.originWorld();
        Vector3f dir = ColumnPlacer.growDirection(spell.surfaceNormal());
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());
        float power = Math.max(0.1f, spell.magnitude().power());
        float aoe = Math.max(1f, spell.magnitude().aoe());

        // The shove uses the longest column among this kind's instructions.
        int height = 0;
        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            if (!(op.payload() instanceof List<?> raw)) continue;
            int count = Math.max(1, op.count());
            for (EffectInstruction ins : (List<EffectInstruction>) raw) {
                if (ins instanceof SpawnParticlesInstruction sp) {
                    height = Math.max(height, columnHeight(sp.reach(), count, size));
                }
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
        return Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT, (int) Math.round(base * count * size)));
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
