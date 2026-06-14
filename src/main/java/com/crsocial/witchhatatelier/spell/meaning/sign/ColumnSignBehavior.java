package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import com.crsocial.witchhatatelier.spell.meaning.SizeScaling;
import org.joml.Vector2f;

/**
 * Custom behaviour for the Column sign — <b>identical for every element</b>.
 * Where {@link LevitationSignBehavior} shifts the spell's <i>origin</i>, Column
 * skews the spell's <i>direction</i> toward the side it was drawn: a Column drawn
 * to the right steers a Column spell to the right, one drawn forward steers it
 * forward, and so on.
 *
 * <p>The skew <i>direction</i> is taken from the Column glyph's own recovered heading
 * ("which way the drawn arrow points") when one was recovered — the player explicitly
 * aimed it, so no off-centre placement is needed. When the glyph yields no heading the
 * behaviour falls back to the sign's <b>placement</b> relative to the sigil centre (mean
 * of the Column occurrences − {@code graph.core().centre()}); the placement magnitude
 * is {@code signQuality × steerFraction(distance) × maxSkew × steer(size)} —
 * this sign's own recognizer confidence (averaged over its occurrences), times how far
 * off-centre it was drawn (zero inside {@link Config#COLUMN_STEER_DEADZONE}, ramping to
 * full at {@link Config#COLUMN_STEER_FULL_DISTANCE} so a Column drawn near the centre goes
 * straight up and only a clearly side-drawn one leans), times {@link Config#COLUMN_STEER_MAX_SKEW},
 * times a size-steer curve ({@code SizeScaling.steerMultiplier}, which weights size more steeply
 * than power so big drawings lean much harder). The canvas-space
 * side is rotated by the paper's in-plane rotation and mapped to world space via
 * {@link com.crsocial.witchhatatelier.spell.meaning.CanvasDirection} ({@code canvas.x → world.x},
 * {@code canvas.y → world.z}), so the skew follows the rendered drawing — the same convention as
 * {@code MeaningEngine.resolveDirection}.</p>
 *
 * <p>The result is emitted as a {@code directionBias}; the meaning engine adds it
 * to the base direction and re-normalizes, so the bias steers rather than
 * replaces. Placement on the centre (no direction) produces no skew.</p>
 */
public final class ColumnSignBehavior implements SignBehavior {

    @Override
    public SpellModification modify(SigilType sigil, SignBundle bundle,
                                    SpellGraph graph, CastingContext ctx,
                                    Magnitude magnitude) {
        // Shared placement read (displacement, quality, paper-rotation-aware direction).
        SignPlacement p = SignPlacement.from(bundle, graph);
        float size = magnitude.sizeNormalized();
        float maxSkew = Config.COLUMN_STEER_MAX_SKEW.get().floatValue();

        // ── Primary: steer along the drawn arrow's own heading ───────────────────────────
        // The player aimed the Column glyph, so it points the pillar directly — no off-centre
        // ramp needed. Magnitude = sign quality × heading confidence × maxSkew × size-steer.
        SignHeading h = SignHeading.from(bundle);
        if (h.hasDirection()) {
            float skew = p.signQuality() * h.confidence() * maxSkew * SizeScaling.steerMultiplier(size);
            Vector2f dir = h.worldDirectionXZ(ctx); // rotated to follow the rendered drawing
            return SpellModification.builder()
                    .directionBias(dir.x * skew, 0f, dir.y * skew)
                    .build();
        }

        // ── Fallback: no heading recovered → steer by where the Column was drawn ──────────
        if (!p.hasDirection()) {
            // Drawn on the centre — no directional intent, no skew.
            return SpellModification.NONE;
        }

        // ── How far off-centre? Inside the deadzone the pillar goes straight up; ──────────
        // between the deadzone and the full-steer distance the lean ramps in linearly, so
        // a near-centred Column barely tilts and only a clearly side-drawn one leans hard.
        // This is what makes "straight up" easy to hit by hand — see Config.COLUMN_STEER_*.
        float steerFraction = steerFraction(p.distance());
        if (steerFraction <= 0f) return SpellModification.NONE;

        // ── Direction skew: side from placement, magnitude = quality × off-centre ramp ───
        // × maxSkew × size-steer curve. Size feeds through SizeScaling.steerMultiplier (the
        // size→power curve raised to DIRECTION_SIZE_EXPONENT) so a big off-centre Column leans
        // hard while a small one barely steers — size carries more weight here than on power.
        float skew = p.signQuality() * steerFraction * maxSkew * SizeScaling.steerMultiplier(size);
        Vector2f dir = p.worldDirectionXZ(ctx);   // rotated to follow the rendered drawing

        return SpellModification.builder()
                .directionBias(dir.x * skew, 0f, dir.y * skew)
                .build();
    }

    /**
     * Off-centre ramp: {@code 0} inside {@link Config#COLUMN_STEER_DEADZONE}, rising linearly
     * to {@code 1} at {@link Config#COLUMN_STEER_FULL_DISTANCE} and clamped there. {@code distance}
     * is the Column's displacement from the sigil centre in canvas units.
     */
    private static float steerFraction(float distance) {
        float deadzone = Config.COLUMN_STEER_DEADZONE.get().floatValue();
        float full = Config.COLUMN_STEER_FULL_DISTANCE.get().floatValue();
        if (distance <= deadzone) return 0f;
        if (full <= deadzone) return 1f;   // guard against a mis-set full < deadzone
        return Math.min(1f, (distance - deadzone) / (full - deadzone));
    }
}
