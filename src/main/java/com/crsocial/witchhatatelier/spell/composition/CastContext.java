package com.crsocial.witchhatatelier.spell.composition;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Live per-cast view a {@code Form}/{@code Effect} manifests against — the
 * {@code CastContext} from {@code docs/new_spell_engine.md} §11. Distinct
 * from {@link CastingContext} (compile-time medium/origin/surface-normal,
 * threaded through the compiler): this wraps that plus the live {@link
 * ServerLevel}, the casting entity, and the fully-resolved cast geometry
 * ({@link #origin}/{@link #direction}) and {@link Magnitude} — what
 * {@link com.crsocial.witchhatatelier.spell.composition.CompositionEngine}
 * builds once per {@code (SpellGraph, live world)} pair.
 *
 * @param level     the world the spell manifests in
 * @param caster    the casting entity, or {@code null} for a sourceless cast
 *                   (e.g. a placed-paper trap with no player present)
 * @param casting   the compile-time medium/origin/surface-normal context this was built from
 * @param magnitude this op's resolved magnitude — see {@link #withMagnitude}
 * @param origin    resolved world-space cast origin
 * @param direction resolved world-space direction (see {@link CanvasDirection})
 */
public record CastContext(ServerLevel level,
                          @Nullable LivingEntity caster,
                          CastingContext casting,
                          Magnitude magnitude,
                          Vector3f origin,
                          Vector3f direction) {

    /**
     * A copy of this context scoped to one Form/Effect bundle's own resolved
     * magnitude — see {@code docs/new_spell_engine.md} §5: a {@link
     * StackingMode#MAGNITUDE} sign's own repeat count amplifies only its own
     * invocation, not the whole spell's shared baseline.
     */
    public CastContext withMagnitude(Magnitude bundleMagnitude) {
        return new CastContext(level, caster, casting, bundleMagnitude, origin, direction);
    }
}
