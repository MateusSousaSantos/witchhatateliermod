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

/**
 * Generic default for {@link EffectType#PULL} — draws nearby entities toward
 * the cast origin. Applies whether it's modifying a {@code Form}'s
 * manifestation or carrying on its own (no form drawn).
 */
public class PullEffect implements Effect {

    private static final double BASE_RADIUS = 5.0;
    private static final double BASE_STRENGTH = 0.35;

    @Override
    public EffectType type() {
        return EffectType.PULL;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MAGNITUDE;
    }

    @Override
    public Manifestation carry(Material working, CastContext ctx) {
        pullNearby(ctx);
        ParticleOptions particle = working.asParticle().orElse(ParticleTypes.SWEEP_ATTACK);
        return new ParticlesManifestation(ctx.origin(), ctx.direction(), particle, (float) radiusFor(ctx));
    }

    @Override
    public Manifestation modify(Manifestation in, Material working, CastContext ctx) {
        pullNearby(ctx);
        return in;
    }

    private void pullNearby(CastContext ctx) {
        double radius = radiusFor(ctx);
        double strength = BASE_STRENGTH * Math.max(0.1f, ctx.magnitude().power());
        Vec3 center = new Vec3(ctx.origin().x, ctx.origin().y, ctx.origin().z);
        AABB box = new AABB(center, center).inflate(radius);
        for (Entity e : ctx.level().getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 toward = center.subtract(e.position());
            if (toward.lengthSqr() < 1.0e-4) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(toward.normalize().scale(strength)));
            e.hurtMarked = true; // force a velocity sync to the client
        }
    }

    private double radiusFor(CastContext ctx) {
        return BASE_RADIUS * Math.max(0.1f, ctx.magnitude().aoe());
    }
}
