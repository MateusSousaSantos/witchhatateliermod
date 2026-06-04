package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SignNode;
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
 * <p>The skew direction comes from the sign's placement relative to the sigil
 * centre (mean of the Column occurrences − {@code graph.core().centre()}); its
 * magnitude is {@code signQuality × steer(size)} — this sign's own recognizer confidence
 * (averaged over its occurrences) times a size-steer curve ({@code SizeScaling.steerMultiplier},
 * which weights size more steeply than power so big drawings lean much harder). Canvas axes
 * map to world space as {@code canvas.x → world.x}, {@code canvas.y → world.z}
 * (the same convention as {@code MeaningEngine.resolveDirection}).</p>
 *
 * <p>The result is emitted as a {@code directionBias}; the meaning engine adds it
 * to the base direction and re-normalizes, so the bias steers rather than
 * replaces. Placement on the centre (no direction) produces no skew.</p>
 */
public final class ColumnSignBehavior implements SignBehavior {

    // ── Tuning constants ─────────────────────────────────────────────────────

    /** Direction-bias magnitude at full quality×size (≈ length of the unit base direction). */
    private static final float MAX_SKEW = 5.0f;
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

        float dist = d.length();
        if (dist <= EPSILON) {
            // Drawn on the centre — no directional intent, no skew.
            return SpellModification.NONE;
        }

        // ── Sign quality: average recognizer confidence over this sign's occurrences ──
        float signQuality = 0f;
        for (SignNode occ : bundle.occurrences()) {
            signQuality += occ.quality();
        }
        if (!bundle.occurrences().isEmpty()) {
            signQuality /= bundle.occurrences().size();
        }

        float size = magnitude.sizeNormalized();

        // ── Direction skew: side from placement, magnitude = quality × size-steer curve ──
        // Size feeds through SizeScaling.steerMultiplier (the size→power curve raised to
        // DIRECTION_SIZE_EXPONENT) so a big off-centre Column leans hard while a small one
        // barely steers — size carries more weight here than it does on raw power.
        float invLen = 1f / dist;                 // normalize d
        float skew = signQuality * MAX_SKEW * SizeScaling.steerMultiplier(size);
        float biasX = d.x * invLen * skew;        // canvas.x → world.x
        float biasZ = d.y * invLen * skew;        // canvas.y → world.z

        return SpellModification.builder()
                .directionBias(biasX, 0f, biasZ)
                .build();
    }
}
