package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SignNode;
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
 * overall inscription size. Canvas axes map to world space as
 * {@code canvas.x → world.x}, {@code canvas.y → world.z} (the same convention as
 * {@code MeaningEngine.resolveDirection}).</p>
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
        // ── Placement displacement: mean sign position − sigil centre (canvas [0,1]) ──
        Vector2f meanPos = new Vector2f();
        for (SignNode occ : bundle.occurrences()) {
            meanPos.add(occ.position());
        }
        if (!bundle.occurrences().isEmpty()) {
            meanPos.div(bundle.occurrences().size());
        }
        Vector2f sigilCentre = graph.core().centre();
        Vector2f d = new Vector2f(meanPos.x - sigilCentre.x, meanPos.y - sigilCentre.y);

        // ── Distance factor: normalize against the ring radius, bounded to [0,1] ──
        float ringRadius = Math.max(graph.root().radius(), EPSILON);
        float dist = d.length();
        float distNorm = clamp01(dist / ringRadius);

        // ── Sign quality: average recognizer confidence over this sign's occurrences ──
        float signQuality = 0f;
        for (SignNode occ : bundle.occurrences()) {
            signQuality += occ.quality();
        }
        if (!bundle.occurrences().isEmpty()) {
            signQuality /= bundle.occurrences().size();
        }

        float size = magnitude.sizeNormalized();

        // ── Horizontal shift: direction from placement, magnitude = distance × quality × size ──
        float strength = distNorm * signQuality * size;
        float horizX = 0f;
        float horizZ = 0f;
        if (dist > EPSILON) {
            float invLen = 1f / dist;            // normalize d
            float reach = strength * MAX_HORIZONTAL_REACH;
            horizX = d.x * invLen * reach;       // canvas.x → world.x
            horizZ = d.y * invLen * reach;       // canvas.y → world.z
        }

        // ── Vertical lift: always present, scales with quality × size (not distance) ──
        float lift = signQuality * size * MAX_LIFT;

        return SpellModification.builder()
                .originOffset(horizX, lift, horizZ)
                .build();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
