package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * $P+ point-cloud recognizer (Vatavu, Anthony, Wobbrock — CHI '17). Matches a
 * candidate {@link PointCloud} against every {@link Template} in the supplied
 * {@link TemplateRegistry} using the three-channel point distance
 * {@code √(Δx² + Δy² + Δα²)} where {@code α} is the normalized interior
 * turning angle (computed in {@link PointCloudPreprocessor#computeTurningAngles}).
 *
 * <p>Both the candidate and the templates must already be preprocessed via
 * {@link PointCloudPreprocessor#process}.</p>
 *
 * <h3>Departures from the reference $P+ spec</h3>
 * <ol>
 *   <li><b>PCA canonical rotation</b> is applied in the preprocessor. The
 *       original $P+ is intentionally <i>not</i> rotation-invariant; we add
 *       this so spells drawn at arbitrary in-world angles still recognize.
 *       The 180° flip retry below resolves the line-vs-vector ambiguity left
 *       by PCA (the principal axis is a line, defined modulo π).</li>
 *   <li><b>Score normalization</b> uses {@code 1 − avg_d / √3} rather than the
 *       reference's {@code 1 / sum_d}. The linear form is interpretable as a
 *       confidence in {@code [0, 1]} and works directly with
 *       {@link Config#RECOGNITION_MIN_SCORE} / {@link Config#RECOGNITION_AMBIGUITY_MARGIN}.
 *       The {@code √3} ceiling is the per-pair maximum when α ∈ [0, 1] and the
 *       cloud occupies the unit square: {@code √(1² + 1² + 1²) = √3}.</li>
 *   <li><b>Rejection rules</b> on top of raw scoring:
 *     <ul>
 *       <li>Best score must reach {@link Config#RECOGNITION_MIN_SCORE}.</li>
 *       <li>Best score must beat the best score of any <i>different</i> spell
 *           by at least {@link Config#RECOGNITION_AMBIGUITY_MARGIN}. Variants
 *           of the same spell never trigger this gate against each other.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Internally, {@link #cloudDistance} is the two-phase chamfer-style measure
 * from the reference (every point in {@code pts1} contributes its NN distance
 * to {@code pts2}; every {@code pts2} point not touched in phase A then
 * contributes its NN back to {@code pts1}). Symmetry is recovered the same way
 * the reference does it: {@code min(cloudDistance(a, b), cloudDistance(b, a))}.</p>
 */
public final class PDollarPlusRecognizer {

    /**
     * Per-pair maximum distance: {@code √(Δx² + Δy² + Δα²)} with all three
     * channels in {@code [0, 1]} ⇒ {@code √3}. Used to convert the average
     * accumulated distance into a {@code [0, 1]} confidence score.
     */
    private static final float REFERENCE_SIZE = (float) Math.sqrt(3.0);

    private final TemplateRegistry registry;
    private final float minScore;

    public PDollarPlusRecognizer(TemplateRegistry registry, float minScore) {
        this.registry = registry;
        this.minScore = minScore;
    }

    /** One ranked entry, used by {@link #matchVerbose}. */
    public record Scored(String spellName, String variantName, float score) {}

    /**
     * Matches against all content templates in the registry.
     *
     * @param candidate fully-preprocessed candidate cloud
     * @param candidateAngle indicative angle of the candidate (radians)
     */
    public RecognitionResult match(PointCloud candidate, float candidateAngle) {
        return match(candidate, candidateAngle, registry.all());
    }

    /**
     * Matches against an explicit template list. Used by the trigger evaluator
     * for ring-shape validation (passes only ring templates) and by any caller
     * that needs to match against a custom subset.
     */
    public RecognitionResult match(PointCloud candidate, float candidateAngle,
                                   List<Template> templates) {
        if (templates.isEmpty() || candidate.points().isEmpty()) {
            return RecognitionResult.unknown(0f, candidateAngle);
        }

        // Pre-flip the candidate once — PCA gives an axis (line) not a vector,
        // so the canonical-rotated cloud could be 180° off from any template.
        PointCloud flipped = PointCloudPreprocessor.rotateBy(candidate, (float) Math.PI);

        // Pass 1: per-spell best score, plus the overall best template.
        Map<String, Float> bestPerSpell = new HashMap<>();
        Template best = null;
        float bestScore = 0f;
        for (Template t : templates) {
            float score = scoreWithFlip(candidate, flipped, t.processedCloud());
            Float prev = bestPerSpell.get(t.spellName());
            if (prev == null || score > prev) bestPerSpell.put(t.spellName(), score);
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }

        // Pass 2: ambiguity runner-up is the best score of any DIFFERENT spell.
        // This way multiple variants of the winning spell don't trigger the gate
        // against each other (they're the *same* spell, not a confusable rival).
        float bestOfOtherSpell = 0f;
        if (best != null) {
            String winner = best.spellName();
            for (Map.Entry<String, Float> e : bestPerSpell.entrySet()) {
                if (!e.getKey().equals(winner) && e.getValue() > bestOfOtherSpell) {
                    bestOfOtherSpell = e.getValue();
                }
            }
        }

        float margin = Config.RECOGNITION_AMBIGUITY_MARGIN.get().floatValue();
        if (best == null
                || bestScore < minScore
                || (bestScore - bestOfOtherSpell) < margin) {
            return RecognitionResult.unknown(bestScore, candidateAngle);
        }
        return new RecognitionResult(best.spellName(), bestScore, candidateAngle);
    }

    /**
     * Returns every template ranked by score (best first). For diagnostics —
     * lets callers log all candidate scores to understand why a specific
     * template won (or why a match was rejected as ambiguous).
     */
    public List<Scored> matchVerbose(PointCloud candidate, List<Template> templates) {
        PointCloud flipped = candidate.points().isEmpty()
                ? candidate
                : PointCloudPreprocessor.rotateBy(candidate, (float) Math.PI);
        List<Scored> out = new ArrayList<>(templates.size());
        for (Template t : templates) {
            float score = scoreWithFlip(candidate, flipped, t.processedCloud());
            out.add(new Scored(t.spellName(), t.variantName(), score));
        }
        out.sort((x, y) -> Float.compare(y.score(), x.score()));
        return out;
    }

    /**
     * Best of two scores: candidate vs template, and candidate flipped 180° vs template.
     * Resolves the line-vs-vector ambiguity left by PCA-based canonical rotation.
     *
     * <p>Each flip evaluates the symmetric distance
     * {@code min(cloudDistance(cand, tmpl), cloudDistance(tmpl, cand))} — matching
     * the reference $P+ Recognize loop — and the final score uses the better
     * (smaller) distance across the two flips.</p>
     */
    private static float scoreWithFlip(PointCloud candidate, PointCloud flipped, PointCloud template) {
        float d0 = symmetricCloudDistance(candidate.points(), template.points());
        float d1 = symmetricCloudDistance(flipped.points(), template.points());
        float d  = Math.min(d0, d1);
        return Math.max(0f, 1f - d / REFERENCE_SIZE);
    }

    // ── Chamfer-style symmetric cloud distance ($P+ spec) ──────────────────────

    /**
     * {@code min(cloudDistance(a, b), cloudDistance(b, a))} — same symmetrization
     * the reference {@code Recognize} loop uses.
     */
    private static float symmetricCloudDistance(List<Point> a, List<Point> b) {
        if (a.isEmpty() || b.isEmpty()) return REFERENCE_SIZE;
        return Math.min(cloudDistance(a, b), cloudDistance(b, a));
    }

    /**
     * Two-phase chamfer-style cloud distance from $P+:
     * <ol>
     *   <li><b>Phase A</b> — for every point in {@code pts1}, accumulate the
     *       distance to its nearest neighbor in {@code pts2} (multiple {@code i}
     *       may map to the same {@code j}). Mark each chosen {@code j} as touched.</li>
     *   <li><b>Phase B</b> — for every point in {@code pts2} that no Phase A
     *       lookup mapped to, accumulate the distance to its nearest neighbor
     *       in {@code pts1}.</li>
     * </ol>
     *
     * <p>The accumulated total is divided by the number of distance terms
     * actually summed, giving an average per-pair distance in {@code [0, √3]}
     * (assuming preprocessing has bounded all three channels to {@code [0, 1]}).</p>
     */
    private static float cloudDistance(List<Point> pts1, List<Point> pts2) {
        int n1 = pts1.size();
        int n2 = pts2.size();
        boolean[] touched = new boolean[n2];
        double total = 0.0;
        int terms = 0;

        // Phase A — every pts1 point contributes its NN distance to pts2.
        for (int i = 0; i < n1; i++) {
            Point pa = pts1.get(i);
            int bestJ = -1;
            float bestD = Float.MAX_VALUE;
            for (int j = 0; j < n2; j++) {
                float d = pointDistance(pa, pts2.get(j));
                if (d < bestD) {
                    bestD = d;
                    bestJ = j;
                }
            }
            if (bestJ < 0) continue;
            touched[bestJ] = true;
            total += bestD;
            terms++;
        }

        // Phase B — every untouched pts2 point contributes its NN distance to pts1.
        for (int j = 0; j < n2; j++) {
            if (touched[j]) continue;
            Point pb = pts2.get(j);
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < n1; i++) {
                float d = pointDistance(pts1.get(i), pb);
                if (d < bestD) bestD = d;
            }
            if (bestD == Float.MAX_VALUE) continue;
            total += bestD;
            terms++;
        }

        if (terms == 0) return REFERENCE_SIZE;
        return (float) (total / terms);
    }

    /**
     * Three-channel point distance: spatial (x, y) plus normalized turning angle.
     * This is the $P+ {@code DistanceWithAngle} — without the angle term the
     * recognizer degenerates to $P.
     */
    private static float pointDistance(Point a, Point b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float da = a.turningAngle() - b.turningAngle();
        return (float) Math.sqrt(dx * dx + dy * dy + da * da);
    }
}
