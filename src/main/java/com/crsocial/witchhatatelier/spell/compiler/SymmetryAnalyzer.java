package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

import java.util.List;

/**
 * Computes the {@link SymmetryReport} from sign placement around the central
 * sigil. Phase 1 implements {@code netDirection} and {@code radialScore};
 * {@code bilateralScore} is left at {@code 0} until a later phase.
 */
public final class SymmetryAnalyzer {

    /** Minimum radial score for a spell to be considered stable. */
    private static final float STABILITY_THRESHOLD = 0.6f;

    /** Below this mean radius (canvas px) the placement is treated as degenerate/balanced. */
    private static final float MIN_RADIUS_EPSILON = 1e-3f;

    private SymmetryAnalyzer() {}

    public static SymmetryReport analyze(Vector2f sigilCentre, List<SignNode> signs) {
        if (signs.isEmpty()) {
            // No signs around the sigil → trivially balanced.
            return new SymmetryReport(1f, 0f, new Vector2f(0f, 0f), true);
        }

        Vector2f net = new Vector2f();
        float meanRadius = 0f;
        for (SignNode s : signs) {
            float dx = s.position().x - sigilCentre.x;
            float dy = s.position().y - sigilCentre.y;
            net.add(dx, dy);
            meanRadius += (float) Math.sqrt(dx * dx + dy * dy);
        }
        net.div(signs.size());
        meanRadius /= signs.size();

        // Balanced placement → net ≈ 0 → radialScore ≈ 1. All signs clustered on
        // one side → net length ≈ meanRadius → radialScore ≈ 0.
        float radialScore = meanRadius <= MIN_RADIUS_EPSILON
                ? 1f
                : clamp01(1f - net.length() / meanRadius);

        boolean stable = radialScore >= STABILITY_THRESHOLD;
        return new SymmetryReport(radialScore, 0f, net, stable);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
