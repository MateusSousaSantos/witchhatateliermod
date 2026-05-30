package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * In-memory representation of one {@code spell_matrix/<sigil>/<sign>.json}
 * cell. The matrix key is {@code (sigil, sign)} only — no environmental
 * overrides. See {@code docs/magic_system/03_meaning_engine.md}.
 *
 * @param sigil          element domain
 * @param sign           behaviour modifier
 * @param behaviorKind   identifier the engine maps to an effect implementation
 *                       (e.g. {@code "stone_pillar"} → {@code StonePillarEffect})
 * @param basePower      baseline power before scaling
 * @param baseDurationTicks baseline duration in server ticks
 * @param baseAoe        baseline area-of-effect multiplier
 * @param effects        raw effect-list payloads, interpreted by the effect kind
 * @param stackingCurve  how the per-cell magnitude scales with sign count
 */
public record MatrixEntry(SigilType sigil,
                          SignType sign,
                          String behaviorKind,
                          float basePower,
                          long baseDurationTicks,
                          float baseAoe,
                          List<JsonObject> effects,
                          StackingCurve stackingCurve) {}
