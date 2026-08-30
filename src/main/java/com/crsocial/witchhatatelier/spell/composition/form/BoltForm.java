package com.crsocial.witchhatatelier.spell.composition.form;

import com.crsocial.witchhatatelier.spell.compiler.FormType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Generic default for {@link FormType#BOLT} — a {@code RIDER}-role form
 * (combining forms, e.g. {@code column+bolt}, is deferred per {@code
 * docs/new_spell_engine.md} §13) that manifests independently: one fast
 * particle streak per drawn occurrence, fired along the resolved direction.
 * {@link StackingMode#REPETITION} means repeat draws fire more streaks, not
 * one bigger one.
 *
 * <p>A real projectile entity (collision, damage, a model/renderer) is
 * reserved for a future bespoke {@code (BOLT, element)} override — not a gap
 * in this default, the same documented-gap pattern already used for {@code
 * EXTINGUISH}'s missing gesture template.</p>
 */
public final class BoltForm implements Form {

    private static final float STREAK_REACH = 6f;
    private static final float PARTICLES_PER_POINT = 3f;

    @Override
    public FormType type() {
        return FormType.BOLT;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.REPETITION;
    }

    @Override
    public Manifestation manifest(Material working, CastContext ctx) {
        int count = Math.max(1, ctx.magnitude().repetitions());
        float reach = STREAK_REACH * Math.max(0.1f, ctx.magnitude().power());
        int length = Math.round(reach);
        ParticleOptions particle = particleFor(working);

        for (int i = 0; i < count; i++) {
            ColumnGeometry.emitTrail(ctx.level(), ctx.origin(), ctx.direction(), length, particle, PARTICLES_PER_POINT);
        }
        return new ParticlesManifestation(ctx.origin(), ctx.direction(), particle, reach);
    }

    private static ParticleOptions particleFor(Material working) {
        return working.asParticle().<ParticleOptions>map(p -> p)
                .orElseGet(() -> working.asBlock()
                        .<ParticleOptions>map(b -> new BlockParticleOption(ParticleTypes.BLOCK, b.defaultBlockState()))
                        .orElse(ParticleTypes.CRIT));
    }
}
