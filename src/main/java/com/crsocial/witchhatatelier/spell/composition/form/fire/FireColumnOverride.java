package com.crsocial.witchhatatelier.spell.composition.form.fire;

import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.form.ColumnForm;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Fire + Column override — same geometry as the generic {@link ColumnForm}
 * (reused via {@code super.manifest}), plus a scorch: anything standing
 * against the freshly-grown column catches fire. Illustrative example of the
 * opt-in override mechanism (see {@code docs/new_spell_engine.md} §7), not
 * an exhaustive per-element pass over Column.
 */
public final class FireColumnOverride extends ColumnForm {

    private static final double SCORCH_RADIUS = 1.0;
    private static final float IGNITE_SECONDS = 4f;

    @Override
    public Manifestation manifest(Material working, CastContext ctx) {
        Manifestation result = super.manifest(working, ctx);
        scorchNearby(ctx, heightFor(ctx));
        return result;
    }

    private static void scorchNearby(CastContext ctx, int height) {
        Vector3f o = ctx.origin();
        Vector3f dir = ctx.direction();
        Vec3 start = new Vec3(o.x, o.y, o.z);
        Vec3 end = start.add(new Vec3(dir.x, dir.y, dir.z).scale(height));
        AABB box = new AABB(start, end).inflate(SCORCH_RADIUS);
        for (Entity e : ctx.level().getEntities((Entity) null, box, Entity::isAlive)) {
            if (e == ctx.caster()) continue;
            e.igniteForSeconds(IGNITE_SECONDS);
        }
    }
}
