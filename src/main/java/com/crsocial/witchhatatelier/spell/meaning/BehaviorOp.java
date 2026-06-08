package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.spell.compiler.SignType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One resolved behaviour operation contributed by a sign. Decomposing the work
 * into per-sign ops gives the runtime a uniform interpretation loop (see
 * {@code docs/magic_system/03_meaning_engine.md}). {@code count} reflects how
 * many sign occurrences contributed; the meaning engine applies the sign's
 * {@link com.crsocial.witchhatatelier.spell.compiler.SignType.StackingMode}
 * before emitting the op.
 *
 * <p>{@code modifiers} carries the Force-tier signs (e.g. {@code CRUSH}) present
 * in the same ring, mapped to their occurrence count. A Force sign produces no op
 * of its own — instead it <em>retargets</em> the carrier ops: the meaning engine
 * attaches the same modifier map to every carrier op, and the {@link com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind}
 * reads {@link #hasModifier}/{@link #modifierCount} to branch (e.g. a pillar
 * carving instead of placing under {@code CRUSH}). Empty when no Force sign is
 * present, so unmodified casts behave exactly as before.</p>
 *
 * @param sign        sign type that produced this op, or {@code null} for the
 *                    sigil's "no-signs" default behaviour
 * @param count       sign occurrences contributing to this op (always the raw
 *                    bundle count — the {@link com.crsocial.witchhatatelier.spell.compiler.SignType.StackingMode}
 *                    is the effect's concern: REPETITION effects loop {@code count}
 *                    times, MAGNITUDE effects fold {@code count} into a scalar).
 * @param kind        behaviour kind string from the matrix JSON (e.g. {@code "earth_pillar"})
 * @param payload     effect-specific configuration, parsed from the matrix JSON
 * @param costPerTick fuel drained per server tick during this op
 * @param costPerUse  fuel drained once per trigger/event for this op
 * @param modifiers   Force-tier signs co-present in the ring → occurrence count;
 *                    empty when none. Never {@code null}.
 */
public record BehaviorOp(@Nullable SignType sign, int count, String kind, Object payload,
                         float costPerTick, float costPerUse,
                         Map<SignType, Integer> modifiers) {

    public BehaviorOp {
        modifiers = modifiers == null ? Map.of() : Map.copyOf(modifiers);
    }

    /** Convenience overload for ops with no Force modifiers. */
    public BehaviorOp(@Nullable SignType sign, int count, String kind, Object payload,
                      float costPerTick, float costPerUse) {
        this(sign, count, kind, payload, costPerTick, costPerUse, Map.of());
    }

    /** Whether a Force sign of the given type modifies this op. */
    public boolean hasModifier(SignType type) {
        return modifiers.containsKey(type);
    }

    /** Occurrence count of the given Force modifier, or {@code 0} if absent (stackability). */
    public int modifierCount(SignType type) {
        return modifiers.getOrDefault(type, 0);
    }
}
