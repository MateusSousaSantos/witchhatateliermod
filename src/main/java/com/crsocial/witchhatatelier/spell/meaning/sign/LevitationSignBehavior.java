package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import org.joml.Vector2f;

/**
 * Custom behaviour for the Levitation sign — <b>identical for every element</b>.
 * Levitation shifts the spell's origin point in the direction the sign was drawn
 * <i>relative to the sigil centre</i>: a sign placed to one side pushes the origin
 * that way, and a sign placed further out pushes further than one drawn close in.
 *
 * <p>The horizontal shift's magnitude is {@code placementDistance × signQuality ×
 * size} ("strength"): placement distance is normalized against the ring radius so
 * it is independent of canvas scale, {@code signQuality} is this sign's own
 * recognizer confidence (averaged over its occurrences), and {@code size} is the
 * overall inscription size. The placement direction is rotated by the paper's in-plane rotation
 * and mapped to world space via {@link SignPlacement} ({@code canvas.x → world.x},
 * {@code canvas.y → world.z}), so the shift follows the rendered drawing — the same convention as
 * {@code MeaningEngine.resolveDirection} and {@link ColumnSignBehavior}.</p>
 *
 * <p>A vertical lift (+Y) is always added so the cast still "levitates" even when
 * the sign sits on the sigil centre; the lift scales with {@code signQuality ×
 * size} but not with distance.</p>
 */
public final class LevitationSignBehavior implements SignBehavior {

    // ── Tuning constants ─────────────────────────────────────────────────────

    /** World blocks of horizontal shift at full strength. */
    private static final float MAX_HORIZONTAL_REACH = 1.0f;
    /** World blocks of upward lift at full quality×size. */
    private static final float MAX_LIFT = 1.0f;
    /** Below this displacement length the placement direction is treated as undefined. */
    private static final float EPSILON = 1e-4f;

    @Override
    public SpellModification modify(SigilType sigil, SignBundle bundle,
                                    SpellGraph graph, CastingContext ctx,
                                    Magnitude magnitude) {
        // Shared placement read (displacement, quality, paper-rotation-aware direction).
        SignPlacement p = SignPlacement.from(bundle, graph);

        // ── Distance factor: normalize against the ring radius, bounded to [0,1] ──
        float ringRadius = Math.max(graph.root().radius(), EPSILON);
        float distNorm = clamp01(p.distance() / ringRadius);

        float size = magnitude.sizeNormalized();

        // ── Horizontal shift: direction from placement, magnitude = distance × quality × size ──
        // The placement direction is rotated to follow the placed paper (zero when on-centre).
        float strength = distNorm * p.signQuality() * size;
        float reach = strength * MAX_HORIZONTAL_REACH;
        Vector2f dir = p.worldDirectionXZ(ctx);
        float horizX = dir.x * reach;
        float horizZ = dir.y * reach;

        // ── Vertical lift: always present, scales with quality × size (not distance) ──
        float lift = p.signQuality() * size * MAX_LIFT;

        return SpellModification.builder()
                .originOffset(horizX, lift, horizZ)
                .build();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
