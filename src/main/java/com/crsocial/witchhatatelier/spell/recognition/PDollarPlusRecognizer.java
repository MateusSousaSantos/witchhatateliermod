package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference $P+ point-cloud recognizer (Vatavu — CHI '17).
 *
 * <p>This is a faithful Java port of the original C# {@code PointCloudRecognizerPlus}.
 * Preprocessing follows the original {@code Gesture} class pipeline:
 * scale → translate to centroid → resample. No PCA rotation is applied.</p>
 *
 * <p>The core distance is the original symmetric chamfer:
 * {@code min(cd(candidate→template), cd(template→candidate))}, where {@code cd}
 * accumulates the three-channel NN distance {@code √(Δx²+Δy²+Δα²)} for every
 * point in the first cloud, then adds the NN distance for each unmatched point
 * in the second cloud. The result is averaged over total terms and converted to
 * a {@code [0,1]} score via {@code 1 − avg_d / √3}.</p>
 *
 * <p>Meta-level rejection gates (min score, ambiguity margin) are kept from our
 * framework — the reference classifier returns the closest class unconditionally.</p>
 */
public final class PDollarPlusRecognizer {

    /**
     * Per-pair maximum distance with all three channels in [0,1]: √(1²+1²+1²) = √3.
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

    /** Matches against all content templates in the registry. */
    public RecognitionResult match(PointCloudPreprocessor.Processed candidate) {
        return match(candidate, registry.all());
    }

    /**
     * Matches against an explicit template list. The filtering pipeline runs in
     * three stages per template:
     *
     * <ol>
     *   <li><b>Pre-filters</b> ({@link SigilFilters#aspectRatioPasses},
     *       {@link SigilFilters#inkDensityPasses}) — O(1) given cached
     *       {@link SigilMetrics}. Skip the chamfer entirely on structurally
     *       incompatible templates.</li>
     *   <li><b>Chamfer score</b> ({@link #score}) — the expensive O(N²) step.</li>
     *   <li><b>Post-filter</b> ({@link SigilFilters#gridSimilarity}) — runs only
     *       when the score is high enough to be a credible match. Confirms the
     *       point-mass distribution matches; rejects high scores whose spatial
     *       layout disagrees with the template.</li>
     * </ol>
     */
    public RecognitionResult match(PointCloudPreprocessor.Processed candidate,
                                   List<Template> templates) {
        PointCloud cloud = candidate.cloud();

        if (templates.isEmpty() || cloud.points().isEmpty()) {
            return RecognitionResult.unknown(0f, candidate.indicativeAngle());
        }

        SigilMetrics candMetrics = candidate.metrics();
        float tallThreshold      = Config.ASPECT_RATIO_TALL_THRESHOLD.get().floatValue();
        float wideThreshold      = Config.ASPECT_RATIO_WIDE_THRESHOLD.get().floatValue();
        float maxDensityRelDiff  = Config.INK_DENSITY_MAX_REL_DIFF.get().floatValue();
        int   dotTolerance       = Config.DOT_COUNT_TOLERANCE.get();
        int   loopTolerance      = Config.LOOP_COUNT_TOLERANCE.get();
        float gridCheckThreshold = Config.GRID_CHECK_SCORE_THRESHOLD.get().floatValue();
        float gridMinSimilarity  = Config.GRID_MIN_SIMILARITY.get().floatValue();

        // Per-spell best score and overall best template.
        Map<String, Float> bestPerSpell = new HashMap<>();
        Template best = null;
        float bestScore = 0f;
        for (Template t : templates) {
            SigilMetrics tMetrics = t.metrics();

            // Stage 1 — cheap structural pre-filters. All O(1) given cached metrics;
            // any failure short-circuits before the expensive chamfer.
            if (tMetrics != null && candMetrics != null) {
                if (!SigilFilters.aspectRatioPasses(candMetrics, tMetrics, tallThreshold, wideThreshold)) continue;
                if (!SigilFilters.dotCountPasses(candMetrics, tMetrics, dotTolerance)) continue;
                if (!SigilFilters.loopCountPasses(candMetrics, tMetrics, loopTolerance)) continue;
                if (!SigilFilters.inkDensityPasses(candMetrics, tMetrics, maxDensityRelDiff)) continue;
            }

            // Stage 2 — chamfer.
            float s = score(cloud, t.processedCloud());

            // Stage 3 — spatial sanity check on otherwise-high scores.
            if (s > gridCheckThreshold && tMetrics != null && candMetrics != null) {
                float gridSim = SigilFilters.gridSimilarity(
                        candMetrics.gridHistogram(), tMetrics.gridHistogram());
                if (gridSim < gridMinSimilarity) continue;
            }

            Float prev = bestPerSpell.get(t.spellName());
            if (prev == null || s > prev) bestPerSpell.put(t.spellName(), s);
            if (s > bestScore) {
                bestScore = s;
                best = t;
            }
        }

        // Ambiguity runner-up: best score of any different spell.
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
            return RecognitionResult.unknown(bestScore, candidate.indicativeAngle());
        }
        return new RecognitionResult(best.spellName(), bestScore, candidate.indicativeAngle());
    }

    /**
     * Returns every template ranked by raw chamfer score, best first. <b>Does
     * not apply the {@link SigilFilters} pipeline</b> — this is the unfiltered
     * diagnostic view, so a caller can compare {@code matchVerbose} output to
     * the result of {@link #match} and identify which templates were rejected
     * by which filter (aspect ratio, ink density, or 3×3 grid).
     */
    public List<Scored> matchVerbose(PointCloudPreprocessor.Processed candidate,
                                     List<Template> templates) {
        PointCloud cloud = candidate.cloud();
        List<Scored> out = new ArrayList<>(templates.size());
        for (Template t : templates) {
            float s = score(cloud, t.processedCloud());
            out.add(new Scored(t.spellName(), t.variantName(), s));
        }
        out.sort((x, y) -> Float.compare(y.score(), x.score()));
        return out;
    }

    // ── Symmetric score (original $P+ GreedyCloudMatch) ───────────────────────────

    /**
     * Symmetric score: try both match directions and take the better (smaller)
     * distance, then normalize to [0,1]. Mirrors the reference:
     * {@code min(CloudDistance(A→B), CloudDistance(B→A))}.
     */
    private static float score(PointCloud candidate, PointCloud template) {
        float d0 = cloudDistance(candidate.points(), template.points());
        float d1 = cloudDistance(template.points(), candidate.points());
        float d  = Math.min(d0, d1);
        return Math.max(0f, 1f - d / REFERENCE_SIZE);
    }

    // ── Two-phase chamfer (original $P+ CloudDistance) ────────────────────────────

    /**
     * Two-phase chamfer matching (reference $P+ {@code CloudDistance}):
     * <ol>
     *   <li><b>Phase A</b> — for every point in {@code input}, find its nearest
     *       neighbor in {@code template} by three-channel distance
     *       {@code √(Δx²+Δy²+Δα²)}, accumulate that distance, and mark the
     *       template point as matched.</li>
     *   <li><b>Phase B</b> — for every template point not matched in Phase A,
     *       find its nearest neighbor in {@code input} and accumulate that
     *       distance.</li>
     * </ol>
     * Returns the average distance over all accumulated terms.
     */
    private static float cloudDistance(List<Point> input, List<Point> template) {
        int n1 = input.size();
        int n2 = template.size();
        if (n1 == 0 || n2 == 0) return REFERENCE_SIZE;

        boolean[] matched = new boolean[n2];
        double sum = 0.0;
        int terms = 0;

        // Phase A: each input point → nearest template point
        for (Point pa : input) {
            int bestJ = -1;
            float bestD = Float.MAX_VALUE;
            for (int j = 0; j < n2; j++) {
                float d = pointDistance(pa, template.get(j));
                if (d < bestD) { bestD = d; bestJ = j; }
            }
            if (bestJ >= 0) {
                matched[bestJ] = true;
                sum += bestD;
                terms++;
            }
        }

        // Phase B: each unmatched template point → nearest input point
        for (int j = 0; j < n2; j++) {
            if (matched[j]) continue;
            Point pb = template.get(j);
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < n1; i++) {
                float d = pointDistance(input.get(i), pb);
                if (d < bestD) bestD = d;
            }
            if (bestD < Float.MAX_VALUE) {
                sum += bestD;
                terms++;
            }
        }

        return terms > 0 ? (float) (sum / terms) : REFERENCE_SIZE;
    }

    /**
     * Three-channel point distance: spatial (x, y) plus normalized turning angle.
     * This is the $P+ {@code DistanceWithAngle}.
     */
    private static float pointDistance(Point a, Point b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float da = a.turningAngle() - b.turningAngle();
        return (float) Math.sqrt(dx * dx + dy * dy + da * da);
    }
}
