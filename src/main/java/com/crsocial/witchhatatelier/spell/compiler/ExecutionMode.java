package com.crsocial.witchhatatelier.spell.compiler;

/**
 * How a cast spell lives once it fires — intrinsic, set by whichever {@link
 * EffectType}s are present (see {@link EffectType#modeTag()}), never chosen
 * separately. See {@code docs/spell_pipeline.md} §7.
 *
 * <ul>
 *   <li>{@code CONTINUOUS} (default) — executes every tick while cast, draining
 *       {@code cost.per_tick} until empty or the cast ends.</li>
 *   <li>{@code REACTIVE} — stays armed and idle, watches a trigger condition, and
 *       executes + charges a per-event cost only when the trigger fires. Not to
 *       be confused with the unrelated, compile-time <b>Prepared</b> inert state
 *       (see {@code InscriptionSummary.InscriptionState}) — Reactive is a live
 *       runtime-lifecycle concept, Prepared is "recognized, compiled, but
 *       nothing manifested."</li>
 * </ul>
 */
public enum ExecutionMode {
    CONTINUOUS,
    REACTIVE
}
