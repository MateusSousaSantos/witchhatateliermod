package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Serializable mechanical payload produced by {@link MeaningEngine}. Carries
 * everything the runtime needs to execute the spell server-side; the visual
 * pipeline reads from the {@link com.crsocial.witchhatatelier.spell.compiler.SpellGraph}
 * separately. See {@code docs/magic_system/03_meaning_engine.md}.
 *
 * @param element       central sigil's element
 * @param ops           resolved per-sign behaviour operations
 * @param magnitude     per-spell magnitude (power, AoE, quality, size)
 * @param origin        where the spell emerges from
 * @param originWorld   world-space position the spell emerges from
 * @param surfaceNormal world-space normal of the casting surface (1,0,0) etc.
 * @param direction     world-space net direction; zero = radial / undirected
 * @param durationTicks lifetime in server ticks
 * @param inner         composed inner-ring spell when {@code SpellGraph.inner} is present
 */
public record ExecutableSpell(SigilType element,
                              List<BehaviorOp> ops,
                              Magnitude magnitude,
                              Origin origin,
                              Vector3f originWorld,
                              Vector3f surfaceNormal,
                              Vector3f direction,
                              long durationTicks,
                              @Nullable ExecutableSpell inner) {

    /**
     * Returns a copy with the spatial vectors replaced. Used each tick of a
     * channeled cast to re-aim the spell at the caster's live crosshair without
     * mutating the shared base vectors (JOML {@link Vector3f} is mutable).
     */
    public ExecutableSpell withOrigin(Vector3f originWorld, Vector3f surfaceNormal, Vector3f direction) {
        return new ExecutableSpell(element, ops, magnitude, origin,
                originWorld, surfaceNormal, direction, durationTicks, inner);
    }

    /** Human-readable single-line summary for server logging. */
    public String toLogString() {
        StringBuilder ks = new StringBuilder();
        for (int i = 0; i < ops.size(); i++) {
            if (i > 0) ks.append(", ");
            BehaviorOp op = ops.get(i);
            ks.append(op.kind()).append("×").append(op.count());
        }
        return String.format(java.util.Locale.ROOT,
                "ExecutableSpell{element=%s ops=[%s] power=%.2f aoe=%.2f quality=%.2f size=%.2f duration=%dt origin=%s}",
                element, ks, magnitude.power(), magnitude.aoe(),
                magnitude.quality(), magnitude.sizeNormalized(), durationTicks, origin);
    }
}
