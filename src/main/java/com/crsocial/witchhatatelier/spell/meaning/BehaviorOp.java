package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.spell.compiler.SignType;
import org.jetbrains.annotations.Nullable;

/**
 * One resolved behaviour operation contributed by a sign. Decomposing the work
 * into per-sign ops gives the runtime a uniform interpretation loop (see
 * {@code docs/magic_system/03_meaning_engine.md}). {@code count} reflects how
 * many sign occurrences contributed; the meaning engine applies the sign's
 * {@link com.crsocial.witchhatatelier.spell.compiler.SignType.StackingMode}
 * before emitting the op.
 *
 * @param sign    sign type that produced this op, or {@code null} for the
 *                sigil's "no-signs" default behaviour
 * @param count   sign occurrences contributing to this op (always {@code 1}
 *                for {@link com.crsocial.witchhatatelier.spell.compiler.SignType.StackingMode#REPETITION})
 * @param kind    behaviour kind string from the matrix JSON (e.g. {@code "stone_pillar"})
 * @param payload effect-specific configuration, parsed from the matrix JSON
 */
public record BehaviorOp(@Nullable SignType sign, int count, String kind, Object payload) {}
