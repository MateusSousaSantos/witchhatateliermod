package com.crsocial.witchhatatelier.spell.composition.effect.fire;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.effect.CrushEffect;
import com.crsocial.witchhatatelier.spell.composition.form.ColumnForm;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Fire + Crush override — the generic {@link CrushEffect} un-places whatever
 * the column just placed (via {@code super.modify}), plus a one-shot
 * detonation along the column's line: a wider burn radius, direct damage,
 * and an ignite. Illustrative example of the opt-in override mechanism (see
 * {@code docs/new_spell_engine.md} §7 and the "Crush inverts Column" framing
 * in §6), not an exhaustive per-element pass over Crush.
 */
public final class FireCrushOverride extends CrushEffect {

    private static final double BURN_RADIUS = 1.0;
    private static final double RADIUS_MULT = 1.75;
    private static final float DAMAGE = 9.0f;
    private static final float BURN_SECONDS = 8.0f;

    @Override
    public Manifestation modify(Manifestation in, Material working, CastContext ctx) {
        Manifestation result = super.modify(in, working, ctx); // un-place the column
        detonate(ctx);
        return result;
    }

    private static void detonate(CastContext ctx) {
        ServerLevel level = ctx.level();
        Vector3f o = ctx.origin();
        Vector3f dir = ctx.direction();
        int height = ColumnForm.heightFor(ctx);
        float power = Math.max(0.1f, ctx.magnitude().power());
        float aoe = Math.max(0.1f, ctx.magnitude().aoe());

        Vec3 start = new Vec3(o.x, o.y, o.z);
        Vec3 end = start.add(new Vec3(dir.x, dir.y, dir.z).scale(height));
        double radius = BURN_RADIUS * RADIUS_MULT * aoe;
        float damage = DAMAGE * Mth.clamp(power, 0.5f, 3.0f);

        DamageSource fire = ctx.caster() != null
                ? level.damageSources().source(DamageTypes.ON_FIRE, ctx.caster())
                : level.damageSources().source(DamageTypes.ON_FIRE);

        AABB box = new AABB(start, end).inflate(radius);
        int hit = 0;
        for (Entity e : level.getEntities((Entity) null, box, en -> en.isAlive() && !en.isSpectator())) {
            if (e == ctx.caster()) continue;
            if (distanceToSegment(e.getBoundingBox().getCenter(), start, end) > radius) continue;
            e.igniteForSeconds(BURN_SECONDS);
            e.hurt(fire, damage);
            hit++;
        }
        WitchHatAtelierMod.LOGGER.info(
                "[Composition] Fire+Crush detonation scorched {} entit(ies) along a {}-block line.", hit, height);
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-6) return p.distanceTo(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
