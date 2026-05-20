package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.spell.geometry.SegmentMath;

import java.util.ArrayList;
import java.util.List;

/**
 * $P+ point-cloud recognizer. Matches a candidate {@link PointCloud} against
 * every {@link Template} in the supplied {@link TemplateRegistry} via
 * greedy nearest-neighbor cloud distance.
 *
 * <p>Both the candidate and the templates must already be preprocessed
 * (resampled, scaled to a unit square, centered on the origin).</p>
 */
public final class PDollarPlusRecognizer {

    /** Diagonal of the unit reference square; max possible cloud distance per point. */
    private static final float REFERENCE_SIZE = (float) Math.sqrt(2.0);

    private final TemplateRegistry registry;
    private final float minScore;

    public PDollarPlusRecognizer(TemplateRegistry registry, float minScore) {
        this.registry = registry;
        this.minScore = minScore;
    }

    /**
     * @param candidate fully-preprocessed candidate cloud
     * @param candidateAngle indicative angle of the candidate (radians)
     */
    public RecognitionResult match(PointCloud candidate, float candidateAngle) {
        List<Template> templates = registry.all();
        if (templates.isEmpty() || candidate.points().isEmpty()) {
            return RecognitionResult.unknown(0f, candidateAngle);
        }

        Template best = null;
        float bestScore = 0f;
        for (Template t : templates) {
            float d = cloudDistance(candidate.points(), t.processedCloud().points());
            float score = Math.max(0f, 1f - d / REFERENCE_SIZE);
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }

        if (best == null || bestScore < minScore) {
            return RecognitionResult.unknown(bestScore, candidateAngle);
        }
        return new RecognitionResult(best.spellName(), bestScore, candidateAngle);
    }

    // ── Greedy nearest-neighbor cloud distance ──────────────────────────────────

    /**
     * Average per-candidate-point distance to its nearest still-available
     * template point. Greedy and symmetric: at most {@code min(|a|, |b|)} pairs
     * are matched; the remainder contribute the reference size diagonal, capping
     * mismatches at the worst-case unit-square distance.
     */
    private static float cloudDistance(List<Point> a, List<Point> b) {
        if (a.isEmpty() || b.isEmpty()) return REFERENCE_SIZE;

        int matchable = Math.min(a.size(), b.size());
        boolean[] usedB = new boolean[b.size()];
        List<Float> matched = new ArrayList<>(matchable);

        for (int i = 0; i < a.size() && matched.size() < matchable; i++) {
            Point pa = a.get(i);
            int bestJ = -1;
            float bestD = Float.MAX_VALUE;
            for (int j = 0; j < b.size(); j++) {
                if (usedB[j]) continue;
                float d = SegmentMath.distance(pa, b.get(j));
                if (d < bestD) {
                    bestD = d;
                    bestJ = j;
                }
            }
            if (bestJ < 0) break;
            usedB[bestJ] = true;
            matched.add(bestD);
        }

        double total = 0.0;
        for (float d : matched) total += d;
        // Penalize unmatched points (size mismatch) with the worst-case distance.
        int unmatched = Math.max(a.size(), b.size()) - matched.size();
        total += unmatched * (double) REFERENCE_SIZE;

        return (float) (total / Math.max(a.size(), b.size()));
    }
}
