package com.crsocial.witchhatatelier.spell.composition.effect;

import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Generic default for {@link EffectType#LEVITATION} — gives nearby entities
 * an upward nudge around the cast origin. Applies whether it's modifying a
 * {@code Form}'s manifestation or carrying on its own (no form drawn): either
 * way, something near the origin gets lifted.
 */
public class LevitationEffect implements Effect {

    private static final double BASE_RADIUS = 3.0;
    private static final double BASE_LIFT = 0.6;

    @Override
    public EffectType type() {
        return EffectType.LEVITATION;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MAGNITUDE;
    }

    @Override
    public Manifestation carry(Material working, CastContext ctx) {
        liftNearby(ctx);
        ParticleOptions particle = working.asParticle().orElse(ParticleTypes.CLOUD);
        return new ParticlesManifestation(ctx.origin(), new Vector3f(0f, 1f, 0f), particle, (float) radiusFor(ctx));
    }

    @Override
    public Manifestation modify(Manifestation in, Material working, CastContext ctx) {
        liftNearby(ctx);
        return in;
    }

    private void liftNearby(CastContext ctx) {
        double radius = radiusFor(ctx);
        double lift = BASE_LIFT * Math.max(0.1f, ctx.magnitude().power());
        Vec3 center = new Vec3(ctx.origin().x, ctx.origin().y, ctx.origin().z);
        AABB box = new AABB(center, center).inflate(radius);
        for (Entity e : ctx.level().getEntities((Entity) null, box, Entity::isAlive)) {
            e.setDeltaMovement(e.getDeltaMovement().x, lift, e.getDeltaMovement().z);
            e.hurtMarked = true; // force a velocity sync to the client
        }
    }

    private double radiusFor(CastContext ctx) {
        return BASE_RADIUS * Math.max(0.1f, ctx.magnitude().aoe());
    }
}
