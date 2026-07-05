package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * In-memory representation of one {@code spell_matrix/<sigil>/<sign>.json}
 * cell. The matrix key is {@code (sigil, sign)}; {@code contextModifiers} add the
 * third spec axis — Context — as optional data (rain boosts water, night boosts light,
 * underground boosts earth) without any per-spell code. See
 * {@code docs/magic_system/03_meaning_engine.md}.
 *
 * @param sigil            element domain
 * @param sign             behaviour modifier, or {@code null} for the sigil's
 *                         "no-signs" default cell ({@code <sigil>/default.json})
 * @param behaviorKind     identifier the engine maps to an effect implementation
 *                         (e.g. {@code "earth_pillar"} → {@code EarthPillarEffect})
 * @param basePower        baseline power before scaling
 * @param baseAoe          baseline area-of-effect multiplier
 * @param effects          raw effect-list payloads, interpreted by the effect kind
 * @param stackingCurve    how the per-cell magnitude scales with sign count
 * @param costPerTick      fuel drained per server tick during this effect (default 0.0)
 * @param costPerUse       fuel drained once per trigger/event (default 0.0)
 * @param contextModifiers environmental power/aoe multipliers applied when their
 *                         condition holds at cast time; empty when the cell has none
 */
public record MatrixEntry(SigilType sigil,
                          @Nullable SignType sign,
                          String behaviorKind,
                          float basePower,
                          float baseAoe,
                          List<JsonObject> effects,
                          StackingCurve stackingCurve,
                          float costPerTick,
                          float costPerUse,
                          List<ContextModifier> contextModifiers) {}
