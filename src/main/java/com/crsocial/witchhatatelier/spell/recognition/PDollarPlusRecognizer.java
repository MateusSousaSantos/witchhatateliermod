package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;

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
 * a {@code [0,1]} score by {@link #scoreFromDistance} — a Phase-1 two-pin linear
 * ramp (distAtFull → 1, distAtZero → 0) that replaces the original {@code 1 − d/√3}
 * map, which compressed all scores into a narrow high band.</p>
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

    /**
     * One ranked entry, used by {@link #matchVerbose}. Carries the {@code [0,1]} score,
     * the underlying raw chamfer {@code rawDistance} (the mean matched-pair distance) it
     * was derived from, and the {@code worstPairDistance}/{@code p90PairDistance} of the
     * winning match direction. The worst-pair channels are the Phase-2 signal: garbage
     * tends to match a template on average while stranding a few points far away — the
     * mean hides that sprawl, the worst-pair exposes it.
     */
    public record Scored(String spellName, String variantName, float score,
                         float rawDistance, float worstPairDistance, float p90PairDistance) {}

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
     *   <li><b>Chamfer score</b> ( ) — the expensive O(N²) step.</li>
     *   <li><b>Post-filter</b> ({@link SigilFilters#gridSimilarity}) — runs only
     *       when the score is high enough to be a credible match. Confirms the
     *       point-mass distribution matches; rejects high scores whose spatial
     *       layout disagrees with the template.</li>
     * </ol>
     */
    public RecognitionResult match(PointCloudPreprocessor.Processed candidate,
                                   List<Template> templates) {
        return matchInternal(candidate, templates, null);
    }

    // ── Decision-trail diagnostics (Phase 0) ──────────────────────────────────────

    /**
     * One row of the {@link #match} decision trail: a template's prefilter verdict,
     * its raw chamfer score vs the grid-adjusted final score, and its distance stats.
     * Score/stat fields are {@code NaN} for templates rejected by a prefilter (the
     * chamfer is skipped for those).
     */
    public record TemplateTrace(
            String spellName, String variantName,
            boolean prefilterPassed, String rejectedBy,
            float rawScore, float gridSim, float gridMultiplier, float finalScore,
            float dist, float worstPair, float p90Pair) {}

    /**
     * The full decision trail behind one {@link #match} call: every template's verdict
     * plus the meta-gates (winner, runner-up, worst-pair gate, consensus, ambiguity).
     * {@code rejectionStage} is {@code null} when a spell was accepted, otherwise names
     * the gate that forced {@code unknown}.
     */
    public record MatchTrace(
            List<TemplateTrace> templates,
            String winnerSpell, String winnerVariant,
            float bestScore, float bestWorstPair,
            String runnerUpSpell, float bestOfOtherSpell,
            float margin, float gap,
            int consensusAgree, float consensusBonus, float effectiveScore,
            float worstPairFree, float worstPairWeight,
            String bestRejectionSpell, float bestRejectionScore, float rejectionMargin,
            String rejectionStage) {}

    /** A {@link #match} result paired with its decision trail. */
    public record Traced(RecognitionResult result, MatchTrace trace) {}

    /** Mutable accumulator filled by {@link #matchInternal} only when tracing is on. */
    private static final class TraceCollector {
        final List<TemplateTrace> templates = new ArrayList<>();
        String winnerSpell, winnerVariant, runnerUpSpell, rejectionStage, bestRejectionSpell;
        float bestScore, bestWorstPair, bestOfOtherSpell, margin, gap, consensusBonus, effectiveScore;
        float worstPairFree, worstPairWeight, bestRejectionScore, rejectionMargin;
        int consensusAgree;
    }

    /**
     * Like {@link #match(PointCloudPreprocessor.Processed, List)} but also returns the
     * full decision trail, so a caller can see exactly why a winner beat the raw
     * chamfer leaders — which templates a prefilter removed, which the grid penalty
     * demoted, and whether the worst-pair / consensus / ambiguity gates fired.
     */
    public Traced matchTraced(PointCloudPreprocessor.Processed candidate,
                              List<Template> templates) {
        TraceCollector tc = new TraceCollector();
        RecognitionResult r = matchInternal(candidate, templates, tc);
        MatchTrace mt = new MatchTrace(
                List.copyOf(tc.templates),
                tc.winnerSpell, tc.winnerVariant,
                tc.bestScore, tc.bestWorstPair,
                tc.runnerUpSpell, tc.bestOfOtherSpell,
                tc.margin, tc.gap,
                tc.consensusAgree, tc.consensusBonus, tc.effectiveScore,
                tc.worstPairFree, tc.worstPairWeight,
                tc.bestRejectionSpell, tc.bestRejectionScore, tc.rejectionMargin,
                tc.rejectionStage);
        return new Traced(r, mt);
    }

    private RecognitionResult matchInternal(PointCloudPreprocessor.Processed candidate,
                                            List<Template> templates,
                                            TraceCollector tc) {
        PointCloud cloud = candidate.cloud();

        if (templates.isEmpty() || cloud.points().isEmpty()) {
            if (tc != null) tc.rejectionStage = "empty";
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
        float worstPairFree      = Config.WORST_PAIR_FREE_ALLOWANCE.get().floatValue();
        float worstPairWeight    = Config.WORST_PAIR_WEIGHT.get().floatValue();
        float distAtFull         = Config.RECOGNITION_DIST_AT_FULL_SCORE.get().floatValue();
        float distAtZero         = Config.RECOGNITION_DIST_AT_ZERO_SCORE.get().floatValue();
        float rejectionMargin    = Config.RECOGNITION_REJECTION_MARGIN.get().floatValue();

        // Per-spell best score, overall best template, and the full ranked list of
        // survivors (templates that cleared the pre-filters) for consensus scoring.
        Map<String, Float> bestPerSpell = new HashMap<>();
        List<Scored> survivors = new ArrayList<>();
        Template best = null;
        float bestScore = 0f;
        float bestWorstPair = 0f;
        // Best-scoring NEGATIVE (is_rejection) template. Tracked apart from real spells:
        // it never wins a cast, but if it out-scores (or ties within rejectionMargin) the
        // winning real spell, the draw is rejected as garbage/cut-class below.
        float bestRejectionScore = 0f;
        String bestRejectionSpell = null;
        for (Template t : templates) {
            SigilMetrics tMetrics = t.metrics();

            // Stage 1 — cheap structural pre-filters. All O(1) given cached metrics;
            // any failure short-circuits before the expensive chamfer.
            String rejectedBy = null;
            if (tMetrics != null && candMetrics != null) {
                if (!SigilFilters.aspectRatioPasses(candMetrics, tMetrics, tallThreshold, wideThreshold)) rejectedBy = "aspectRatio";
                else if (!SigilFilters.dotCountPasses(candMetrics, tMetrics, dotTolerance)) rejectedBy = "dotCount";
                else if (!SigilFilters.loopCountPasses(candMetrics, tMetrics, loopTolerance)) rejectedBy = "loopCount";
                else if (!SigilFilters.inkDensityPasses(candMetrics, tMetrics, maxDensityRelDiff)) rejectedBy = "inkDensity";
            }
            if (rejectedBy != null) {
                if (tc != null) tc.templates.add(new TemplateTrace(
                        t.spellName(), t.variantName(), false, rejectedBy,
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN));
                continue;
            }

            // Stage 2 — chamfer + Phase-2 soft-demote. The score is derived not from the
            // raw mean but from an EFFECTIVE distance that folds in the worst-pair excess
            // above the free allowance: garbage that strands points far away is pushed
            // down, while a strong mean match survives one outlier. `d` stays the pure
            // mean (for the log's distributions); only the score sees the penalty.
            ChamferStats cs = chamferStats(cloud, t.processedCloud());
            float d = cs.mean();
            float effDist = d + worstPairWeight * Math.max(0f, cs.worstPair() - worstPairFree);
            float rawScore = scoreFromDistance(effDist, distAtFull, distAtZero);
            float s = rawScore;

            // Stage 3 — spatial agreement as a SOFT penalty (not a hard reject). On
            // otherwise-high scores, fold the 3×3 histogram similarity into the score:
            // full credit at/above gridMinSimilarity, ramping toward 0 as the spatial
            // mass distribution diverges. A near-miss is demoted, not deleted — so a
            // strong chamfer match can no longer be silently dropped by the grid check.
            float gridSim = Float.NaN;
            float gridMult = 1f;
            if (s > gridCheckThreshold && tMetrics != null && candMetrics != null) {
                gridSim = SigilFilters.gridSimilarity(
                        candMetrics.gridHistogram(), tMetrics.gridHistogram());
                gridMult = SigilFilters.gridScoreMultiplier(gridSim, gridMinSimilarity);
                s *= gridMult;
            }

            if (tc != null) tc.templates.add(new TemplateTrace(
                    t.spellName(), t.variantName(), true, null,
                    rawScore, gridSim, gridMult, s, d, cs.worstPair(), cs.p90Pair()));

            // Negative template: a strong match here is evidence to REJECT, not to cast.
            // It never enters the survivor pool (consensus is about real-spell agreement),
            // the per-spell table, or the winning-template race — only its best score is
            // kept, for the rejection gate below.
            if (t.isRejection()) {
                if (s > bestRejectionScore) {
                    bestRejectionScore = s;
                    bestRejectionSpell = t.spellName();
                }
                continue;
            }

            survivors.add(new Scored(t.spellName(), t.variantName(), s, d, cs.worstPair(), cs.p90Pair()));
            Float prev = bestPerSpell.get(t.spellName());
            if (prev == null || s > prev) bestPerSpell.put(t.spellName(), s);
            if (s > bestScore) {
                bestScore = s;
                best = t;
                bestWorstPair = cs.worstPair();
            }
        }

        // Ambiguity runner-up: best score of any different spell.
        float bestOfOtherSpell = 0f;
        String runnerUpSpell = null;
        if (best != null) {
            String winner = best.spellName();
            for (Map.Entry<String, Float> e : bestPerSpell.entrySet()) {
                if (!e.getKey().equals(winner) && e.getValue() > bestOfOtherSpell) {
                    bestOfOtherSpell = e.getValue();
                    runnerUpSpell = e.getKey();
                }
            }
        }

        // Hard rejections first: no winner, or the absolute score is below the floor.
        // Consensus can't rescue a genuinely weak match — only a near-tie.
        float margin = Config.RECOGNITION_AMBIGUITY_MARGIN.get().floatValue();
        if (tc != null) {
            tc.bestScore = bestScore;
            tc.bestWorstPair = bestWorstPair;
            tc.bestOfOtherSpell = bestOfOtherSpell;
            tc.runnerUpSpell = runnerUpSpell;
            tc.margin = margin;
            tc.worstPairFree = worstPairFree;
            tc.worstPairWeight = worstPairWeight;
            tc.effectiveScore = bestScore;
            tc.gap = bestScore - bestOfOtherSpell;
            tc.bestRejectionSpell = bestRejectionSpell;
            tc.bestRejectionScore = bestRejectionScore;
            tc.rejectionMargin = rejectionMargin;
            if (best != null) {
                tc.winnerSpell = best.spellName();
                tc.winnerVariant = best.variantName();
            }
        }
        // Phase 2 is now a soft-demote folded into `bestScore` above (the winner's score
        // already carries its worst-pair penalty), so the floor check below is the only
        // distance gate — a sprawling match simply fails to clear minScore.
        if (best == null || bestScore < minScore) {
            if (tc != null) tc.rejectionStage = (best == null) ? "noWinner" : "belowMinScore";
            return RecognitionResult.unknown(bestScore, candidate.indicativeAngle());
        }

        // Consensus tie-breaker. When the winner fails to clear the runner-up of a
        // DIFFERENT spell by `margin`, count how many of the top-N survivors share
        // the winner's spell and award `consensusBonus` per hit. Several variants of
        // one spell clustering at the top (e.g. levitation ×3) is strong evidence that
        // spell is the answer, so that agreement can push a near-tie over the line.
        float effectiveScore = bestScore;
        float gap = bestScore - bestOfOtherSpell;
        if (gap < margin) {
            survivors.sort((x, y) -> Float.compare(y.score(), x.score()));
            int topN = Math.min(Config.RECOGNITION_CONSENSUS_TOP_N.get(), survivors.size());
            int agree = 0;
            for (int i = 0; i < topN; i++) {
                if (survivors.get(i).spellName().equals(best.spellName())) agree++;
            }
            float bonus = Config.RECOGNITION_CONSENSUS_BONUS.get().floatValue() * agree;
            effectiveScore = Math.min(1f, bestScore + bonus);
            gap = effectiveScore - bestOfOtherSpell;
            if (tc != null) { tc.consensusAgree = agree; tc.consensusBonus = bonus; }
        }
        if (tc != null) { tc.effectiveScore = effectiveScore; tc.gap = gap; }

        // Open-set rejection gate. A negative/tombstone template (is_rejection) matching at
        // least as well as the winning spell — within rejectionMargin — means the draw is a
        // garbage or cut-class shape sitting closest to some real spell only by default.
        // Reject it instead of casting that nearest neighbor. With the default margin of 0
        // this fires only when a negative template strictly out-scores every real spell, so
        // it is inert until negative templates are authored. Consensus (folded into
        // effectiveScore above) is positive evidence and is allowed to clear this gate.
        if (bestRejectionSpell != null && effectiveScore - bestRejectionScore < rejectionMargin) {
            if (tc != null) tc.rejectionStage = "rejectionTemplate";
            WitchHatAtelierMod.LOGGER.warn(
                    "[Recognizer] Rejection gate rejected '{}' (score={}) — negative template '{}' scored {} (margin={})",
                    best.spellName(),
                    String.format(java.util.Locale.ROOT, "%.3f", effectiveScore),
                    bestRejectionSpell,
                    String.format(java.util.Locale.ROOT, "%.3f", bestRejectionScore),
                    String.format(java.util.Locale.ROOT, "%.3f", rejectionMargin));
            return RecognitionResult.unknown(effectiveScore, candidate.indicativeAngle());
        }

        if (gap < margin) {
            if (tc != null) tc.rejectionStage = "ambiguityGate";
            WitchHatAtelierMod.LOGGER.warn(
                    "[Recognizer] Ambiguity gate rejected '{}' (score={}) — runner-up '{}' scored {} (gap={}, margin={})",
                    best.spellName(),
                    String.format(java.util.Locale.ROOT, "%.3f", effectiveScore),
                    runnerUpSpell,
                    String.format(java.util.Locale.ROOT, "%.3f", bestOfOtherSpell),
                    String.format(java.util.Locale.ROOT, "%.3f", gap),
                    String.format(java.util.Locale.ROOT, "%.3f", margin));
            return RecognitionResult.unknown(effectiveScore, candidate.indicativeAngle());
        }
        return new RecognitionResult(best.spellName(), effectiveScore, candidate.indicativeAngle());
    }

    /**
     * Returns every <b>real (castable)</b> template ranked by raw chamfer score, best
     * first. <b>Does not apply the {@link SigilFilters} pipeline</b> — this is the
     * unfiltered diagnostic view, so a caller can compare {@code matchVerbose} output to
     * the result of {@link #match} and identify which templates were rejected by which
     * filter (aspect ratio, ink density, or 3×3 grid).
     *
     * <p>Negative ({@code is_rejection}) templates are skipped here so the survivor
     * ranking — and the per-spell distributions the offline tools derive from it — stay a
     * castable-spell view; a negative label like {@code dispersion} must not read as a
     * recall class. Their scoring is still fully visible in the {@link #matchTraced}
     * decision trail ({@link MatchTrace#bestRejectionSpell} and the per-template list).</p>
     */
    public List<Scored> matchVerbose(PointCloudPreprocessor.Processed candidate,
                                     List<Template> templates) {
        PointCloud cloud = candidate.cloud();
        float distAtFull = Config.RECOGNITION_DIST_AT_FULL_SCORE.get().floatValue();
        float distAtZero = Config.RECOGNITION_DIST_AT_ZERO_SCORE.get().floatValue();
        List<Scored> out = new ArrayList<>(templates.size());
        for (Template t : templates) {
            if (t.isRejection()) continue;
            ChamferStats cs = chamferStats(cloud, t.processedCloud());
            float s = scoreFromDistance(cs.mean(), distAtFull, distAtZero);
            out.add(new Scored(t.spellName(), t.variantName(), s, cs.mean(), cs.worstPair(), cs.p90Pair()));
        }
        out.sort((x, y) -> Float.compare(y.score(), x.score()));
        return out;
    }

    // ── Symmetric score (original $P+ GreedyCloudMatch) ───────────────────────────

    /**
     * Chamfer statistics for one match direction: the mean matched-pair distance (the
     * score's basis) plus the worst (max) and 90th-percentile matched-pair distances.
     */
    private record ChamferStats(float mean, float worstPair, float p90Pair) {}

    /**
     * Symmetric chamfer: compute both match directions and keep the better (smaller
     * mean) — mirrors the reference {@code min(CloudDistance(A→B), CloudDistance(B→A))} —
     * but return that winning direction's full {@link ChamferStats}, not just the mean.
     */
    private static ChamferStats chamferStats(PointCloud candidate, PointCloud template) {
        ChamferStats a = cloudDistanceStats(candidate.points(), template.points());
        ChamferStats b = cloudDistanceStats(template.points(), candidate.points());
        return a.mean() <= b.mean() ? a : b;
    }

    /**
     * Phase-1 de-compression: maps an (effective) chamfer distance to a {@code [0,1]}
     * score via a two-pin linear ramp instead of the old fixed {@code 1 − d/√3}. At or
     * below {@code distAtFull} the score is {@code 1}; at or above {@code distAtZero} it
     * is {@code 0}; in between it ramps linearly:
     * {@code (distAtZero − d) / (distAtZero − distAtFull)}. Pinning the two ends near the
     * valid and non-match distance medians spreads the previously-compressed
     * (~0.94–0.98) band across the full range, so the score-space gates separate again.
     */
    private static float scoreFromDistance(float d, float distAtFull, float distAtZero) {
        float denom = distAtZero - distAtFull;
        if (denom <= 1e-6f) return d <= distAtFull ? 1f : 0f;
        return Math.clamp((distAtZero - d) / denom, 0f, 1f);
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
     * Returns the {@link ChamferStats} over all accumulated terms: the mean (the
     * reference {@code CloudDistance}), the worst (max) matched-pair distance, and the
     * 90th-percentile matched-pair distance.
     */
    private static ChamferStats cloudDistanceStats(List<Point> input, List<Point> template) {
        int n1 = input.size();
        int n2 = template.size();
        if (n1 == 0 || n2 == 0) return new ChamferStats(REFERENCE_SIZE, REFERENCE_SIZE, REFERENCE_SIZE);

        boolean[] matched = new boolean[n2];
        float[] pairs = new float[n1 + n2];
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
                pairs[terms++] = bestD;
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
                pairs[terms++] = bestD;
            }
        }

        if (terms == 0) return new ChamferStats(REFERENCE_SIZE, REFERENCE_SIZE, REFERENCE_SIZE);

        float mean = (float) (sum / terms);

        // Find worst (max) and p90 in single pass instead of full sort.
        float worst = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < terms; i++) worst = Math.max(worst, pairs[i]);

        // p90 via partial selection: partition around p90 index without full sort.
        int p90Idx = (int) Math.ceil(0.9 * terms) - 1;
        p90Idx = Math.clamp(p90Idx, 0, terms - 1);
        float p90 = quickSelect(pairs, 0, terms - 1, p90Idx);

        return new ChamferStats(mean, worst, p90);
    }

    /**
     * Finds the k-th smallest element (0-indexed) in the range [left, right] via
     * Hoare partitioning. Used to compute p90 percentile without full sort.
     */
    private static float quickSelect(float[] arr, int left, int right, int k) {
        if (left == right) return arr[left];
        int pivotIdx = partition(arr, left, right);
        if (k == pivotIdx) return arr[k];
        else if (k < pivotIdx) return quickSelect(arr, left, pivotIdx - 1, k);
        else return quickSelect(arr, pivotIdx + 1, right, k);
    }

    private static int partition(float[] arr, int left, int right) {
        float pivot = arr[(left + right) / 2];
        int i = left, j = right;
        while (i <= j) {
            while (arr[i] < pivot) i++;
            while (arr[j] > pivot) j--;
            if (i <= j) {
                float tmp = arr[i];
                arr[i] = arr[j];
                arr[j] = tmp;
                i++;
                j--;
            }
        }
        return i;
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
