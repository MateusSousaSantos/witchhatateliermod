package com.crsocial.witchhatatelier.spell.compiler;

import com.crsocial.witchhatatelier.Config;
import org.joml.Vector2f;

import java.util.List;

/**
 * Computes the {@link SymmetryReport} from sign placement around the central
 * sigil. Each sign is treated as a force vector — its displacement from the
 * sigil centre, weighted by recognizer quality — and the report describes how
 * those forces sum. Opposing placements of similar quality pull against each
 * other and the net direction approaches zero; a configurable deadzone
 * ({@link Config#SYMMETRY_CANCEL_DEADZONE}) snaps near-balanced placements all
 * the way to zero so deliberately-balanced spells are easy to draw.
 */
public final class SymmetryAnalyzer {

    /** Minimum radial score for a spell to be considered stable. */
    private static final float STABILITY_THRESHOLD = 0.6f;

    /** Below this total force magnitude the placement is treated as degenerate/balanced. */
    private static final float MIN_RADIUS_EPSILON = 1e-3f;

    private SymmetryAnalyzer() {}

    public static SymmetryReport analyze(Vector2f sigilCentre, List<SignNode> signs) {
        if (signs.isEmpty()) {
            // No signs around the sigil → trivially balanced.
            return new SymmetryReport(1f, 0f, new Vector2f(0f, 0f), true);
        }

        // Each sign contributes a force: its displacement from the sigil centre,
        // weighted by recognizer quality. The vector sum is the net pull; the scalar
        // sum of the individual lengths is what the net would be if every sign pulled
        // the same way, so net / total is a [0, 1] imbalance measure.
        Vector2f net = new Vector2f();
        float totalMagnitude = 0f;
        for (SignNode s : signs) {
            float q = s.quality();
            float fx = (s.position().x - sigilCentre.x) * q;
            float fy = (s.position().y - sigilCentre.y) * q;
            net.add(fx, fy);
            totalMagnitude += (float) Math.sqrt(fx * fx + fy * fy);
        }

        // Imbalance: 0 = opposing forces perfectly cancel, 1 = all forces aligned.
        float imbalance = totalMagnitude <= MIN_RADIUS_EPSILON
                ? 0f
                : net.length() / totalMagnitude;

        // Deadzone: when opposing forces nearly cancel, snap the residual to zero so a
        // slightly-imperfect mirror placement still yields a balanced (directionless)
        // spell. Two opposite signs of similar quality therefore cancel out even if the
        // drawing isn't pixel-perfect, instead of leaking a small leftover direction.
        float deadzone = Config.SYMMETRY_CANCEL_DEADZONE.get().floatValue();
        if (imbalance <= deadzone) {
            return new SymmetryReport(1f, 0f, new Vector2f(0f, 0f), true);
        }

        // Outside the deadzone → radialScore drops as the placement leans one way.
        float radialScore = clamp01(1f - imbalance);
        boolean stable = radialScore >= STABILITY_THRESHOLD;
        return new SymmetryReport(radialScore, 0f, net, stable);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
