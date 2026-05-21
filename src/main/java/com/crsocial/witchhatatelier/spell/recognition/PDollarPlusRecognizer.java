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
 *   <li><b>Asymmetric Phase A / Phase B chamfer</b>. The reference averages
 *       both phases together over the total term count, which dilutes Phase B
 *       (template points the input failed to reach) with Phase A (drawing
 *       noise on points the input did reach). We average each phase separately
 *       and combine with a {@link Config#COVERAGE_WEIGHT} bias toward Phase B.
 *       This targets the "draw a simple subset of a complex template, still
 *       score high" failure without depending on stroke count. Because the
 *       chamfer is now intentionally directional (input → template), we no
 *       longer take {@code min(cd(a,b), cd(b,a))} — that symmetrization
 *       silently undid the directional bias.</li>
 *   <li><b>Non-linear score curve</b>. The linear {@code 1 − d/√3} confidence
 *       compresses real differences into a narrow high band, so partial matches
 *       (e.g., 0.80) sit visually next to correct ones (e.g., 0.95). Raising the
 *       result to {@link Config#SCORE_CURVE_EXPONENT} (default 2.0) spreads the
 *       high range apart — 0.95 → 0.90 (barely moves), 0.80 → 0.64 (clearly
 *       below threshold). Preserves rank ordering so the ambiguity gate is
 *       unaffected.</li>
 * </ol>
 *
 * <p>Internally, {@link #cloudDistance} is the two-phase chamfer-style measure
 * from the reference (every point in {@code input} contributes its NN distance
 * to {@code template}; every {@code template} point not touched in phase A then
 * contributes its NN back to {@code input}), but the two phases are kept
 * separate and combined via {@link Config#COVERAGE_WEIGHT}.</p>
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
     * <p>Each flip evaluates the directional {@link #cloudDistance} (input →
     * template) and the final score uses the better (smaller) distance across
     * the two flips. The chamfer itself is intentionally asymmetric so we don't
     * mirror-symmetrize {@code min(cd(cand, tmpl), cd(tmpl, cand))} the way the
     * reference does — that would silently undo the Phase B coverage weighting.</p>
     */
    private static float scoreWithFlip(PointCloud candidate, PointCloud flipped, PointCloud template) {
        float d0 = cloudDistance(candidate.points(), template.points());
        float d1 = cloudDistance(flipped.points(), template.points());
        float d  = Math.min(d0, d1);
        float linear = Math.max(0f, 1f - d / REFERENCE_SIZE);
        double exp = Config.SCORE_CURVE_EXPONENT.get();
        return (exp == 1.0) ? linear : (float) Math.pow(linear, exp);
    }

    // ── Asymmetric chamfer-style cloud distance ────────────────────────────────

    /**
     * Two-phase chamfer-style cloud distance, intentionally directional
     * ({@code input → template}):
     * <ol>
     *   <li><b>Phase A</b> — for every point in {@code input}, accumulate the
     *       distance to its nearest neighbor in {@code template} (multiple
     *       {@code i} may map to the same {@code j}). Mark each chosen
     *       {@code j} as touched. This is the "drawing noise" channel.</li>
     *   <li><b>Phase B</b> — for every point in {@code template} that no Phase A
     *       lookup mapped to, accumulate the distance to its nearest neighbor
     *       in {@code input}. This is the "coverage failure" channel: how far
     *       the input is from the template regions it didn't reach.</li>
     * </ol>
     *
     * <p>The two phases are averaged <i>independently</i>, then combined as
     * {@code (phaseAAvg + w · phaseBAvg) / (1 + w)} where {@code w} is
     * {@link Config#COVERAGE_WEIGHT}. The reference $P+ averages both phases
     * over the total term count, which dilutes Phase B with Phase A's many
     * cheap terms and lets a simple drawing get a high score against a complex
     * template. Keeping the averages separate exposes the coverage signal.</p>
     *
     * <p>The result is in {@code [0, √3]} assuming preprocessing bounded all
     * three channels to {@code [0, 1]}.</p>
     */
    private static float cloudDistance(List<Point> input, List<Point> template) {
        int n1 = input.size();
        int n2 = template.size();
        if (n1 == 0 || n2 == 0) return REFERENCE_SIZE;
        boolean[] touched = new boolean[n2];
        double phaseATotal = 0.0;
        int phaseATerms = 0;
        double phaseBTotal = 0.0;
        int phaseBTerms = 0;

        // Phase A — every input point contributes its NN distance to template.
        for (Point pa : input) {
            int bestJ = -1;
            float bestD = Float.MAX_VALUE;
            for (int j = 0; j < n2; j++) {
                float d = pointDistance(pa, template.get(j));
                if (d < bestD) {
                    bestD = d;
                    bestJ = j;
                }
            }
            if (bestJ < 0) continue;
            touched[bestJ] = true;
            phaseATotal += bestD;
            phaseATerms++;
        }

        // Phase B — every untouched template point contributes its NN distance to input.
        for (int j = 0; j < n2; j++) {
            if (touched[j]) continue;
            Point pb = template.get(j);
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < n1; i++) {
                float d = pointDistance(input.get(i), pb);
                if (d < bestD) bestD = d;
            }
            if (bestD == Float.MAX_VALUE) continue;
            phaseBTotal += bestD;
            phaseBTerms++;
        }

        // Phase A defaults to REFERENCE_SIZE (a bad match) if input was empty post-loop;
        // Phase B defaults to 0 (no untouched template points = perfect coverage).
        float phaseAAvg = phaseATerms > 0 ? (float) (phaseATotal / phaseATerms) : REFERENCE_SIZE;
        float phaseBAvg = phaseBTerms > 0 ? (float) (phaseBTotal / phaseBTerms) : 0f;
        float w = Config.COVERAGE_WEIGHT.get().floatValue();
        return (phaseAAvg + w * phaseBAvg) / (1f + w);
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
