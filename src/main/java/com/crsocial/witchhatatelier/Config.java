package com.crsocial.witchhatatelier;


import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Lazy-Mouse stroke smoothing factor for exponential smoothing.
     * <p>
     * Formula applied every drag event: {@code P_new = P_old + (Cursor - P_old) × factor}
     * <ul>
     *   <li>{@code 1.0} – no smoothing, ink follows cursor exactly</li>
     *   <li>{@code 0.5} – moderate drag, good default for sigil drawing</li>
     *   <li>{@code 0.15} – heavy brush feel, ideal for reducing hand tremor</li>
     * </ul>
     */
    public static final ModConfigSpec.DoubleValue STROKE_SMOOTHING_FACTOR = BUILDER
            .comment("Lazy-Mouse exponential smoothing factor (0.0 = frozen ink, 1.0 = no smoothing).",
                    "Lower values create a heavier drag that smooths out hand tremors.",
                    "Recommended range: 0.15 – 0.7")
            .defineInRange("strokeSmoothingFactor", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue POINT_DEAD_ZONE_PIXELS = BUILDER
            .comment("Minimum pixel distance the ink tip must travel before a new stroke point is recorded.",
                    "Prevents point blobs on hesitation and evenly spaces points for better gesture recognition.",
                    "Range: 1 – 20. Default: 4.")
            .defineInRange("pointDeadZonePixels", 4.0, 1.0, 20.0);
    public static final ModConfigSpec.BooleanValue ANGLE_SNAP_ENABLED = BUILDER
            .comment("Enable angle snapping for straight-line assistance.",
                    "When the stroke heading is within the threshold of 0°/45°/90°/135° axes,",
                    "the ink is snapped to that axis, producing a perfectly straight line.")
            .define("angleSnapEnabled", true);
    public static final ModConfigSpec.DoubleValue ANGLE_SNAP_THRESHOLD_DEGREES = BUILDER
            .comment("Angle snap window (degrees). A stroke heading within this many degrees",
                    "of a cardinal/diagonal axis (0, 45, 90, 135…) will be snapped to that axis.",
                    "Range: 1 – 44. Default: 8.")
            .defineInRange("angleSnapThresholdDegrees", 8.0, 1.0, 44.0);

    // ── Trigger phase (client-side) ─────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue MIN_RING_AREA_FRACTION = BUILDER
            .comment("Minimum ring bounding-box area as a fraction of the canvas area.",
                    "Rings whose bbox is smaller than this threshold are rejected.",
                    "Scales naturally with canvas size: a 128px canvas requires a smaller",
                    "absolute ring than a 768px canvas at the same fraction.",
                    "Range: 0.01 – 0.25. Default: 0.04 (4% of canvas area).")
            .defineInRange("minRingAreaFraction", 0.04, 0.01, 0.25);
    public static final ModConfigSpec.DoubleValue SNAP_EPSILON_PIXELS = BUILDER
            .comment("Endpoint stitching radius (canvas pixels). Strokes whose head or tail land",
                    "within this distance of another stroke's endpoint are merged into a chain.",
                    "Range: 1 – 30. Default: 8.")
            .defineInRange("snapEpsilonPixels", 8.0, 1.0, 30.0);
    public static final ModConfigSpec.DoubleValue CLOSURE_EPSILON_PIXELS = BUILDER
            .comment("Closure radius (canvas pixels). NOTE: ring closure is now decided by the",
                    "geometric winding/gap/roundness test below (ringMinWindingDegrees etc.), not",
                    "by this absolute pixel gap. Kept for back-compat / endpoint-stitching reasoning.",
                    "Range: 1 – 30. Default: 10.")
            .defineInRange("closureEpsilonPixels", 10.0, 1.0, 30.0);

    // ── Geometric ring closure (winding-angle test; replaces $P+ ring validation) ────

    public static final ModConfigSpec.DoubleValue RING_MIN_WINDING_DEGREES = BUILDER
            .comment("Total winding angle (degrees) the ring path must sweep around its own centroid",
                    "to count as a closed loop. A full circle sweeps 360°; lowering this accepts",
                    "rings whose ends don't quite meet, while a C-shape/open arc stays below it.",
                    "This is the primary 'did it go all the way around' signal.",
                    "Range: 180 – 360. Default: 300.")
            .defineInRange("ringMinWindingDegrees", 300.0, 180.0, 360.0);
    public static final ModConfigSpec.DoubleValue RING_CLOSURE_GAP_FRACTION = BUILDER
            .comment("Maximum gap between the ring path's first and last point, as a fraction of the",
                    "ring's bounding-box diagonal. Size-independent replacement for the absolute",
                    "closureEpsilonPixels gap: a big ring may leave a proportionally bigger gap.",
                    "Range: 0.05 – 0.6. Default: 0.25.")
            .defineInRange("ringClosureGapFraction", 0.25, 0.05, 0.6);
    public static final ModConfigSpec.DoubleValue RING_MAX_RADIAL_DEVIATION = BUILDER
            .comment("Lenient roundness ceiling: max radial deviation (stdev/mean of point distance",
                    "to the centroid) for a path to count as a ring. Rejects degenerate near-line",
                    "'loops' while still passing ellipses and even square rings. Set high to disable.",
                    "Range: 0.1 – 1.5. Default: 0.6.")
            .defineInRange("ringMaxRadialDeviation", 0.6, 0.1, 1.5);

    // ── Sigil clustering (server-side; normalized [0,1] space) ──────────────────

    public static final ModConfigSpec.DoubleValue MICRO_MERGE_RADIUS = BUILDER
            .comment("Micro-merge radius in normalized [0,1] space. Strokes whose raw points come",
                    "within this distance of each other are merged immediately before clustering.",
                    "Range: 0.001 – 0.05. Default: 0.01 (≈ 5 px on a 512 canvas).")
            .defineInRange("microMergeRadius", 0.01, 0.001, 0.05);
    public static final ModConfigSpec.DoubleValue MACRO_MERGE_RADIUS = BUILDER
            .comment("Macro-merge radius in normalized [0,1] space. Clusters whose convex hulls",
                    "come within this distance of each other are merged into one sigil.",
                    "Range: 0.01 – 0.30. Default: 0.10.")
            .defineInRange("macroMergeRadius", 0.04, 0.01, 0.30);

    // ── Recognition ($P+) ──────────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue RESAMPLE_N = BUILDER
            .comment("Number of equidistant points the $P+ recognizer resamples each sigil to.",
                    "Lower = faster, less accurate; higher = slower, more accurate.",
                    "Range: 16 – 128. Default: 64.")
            .defineInRange("resampleN", 128, 16, 128);
    public static final ModConfigSpec.DoubleValue RECOGNITION_MIN_SCORE = BUILDER
            .comment("Minimum confidence score for a recognized spell.",
                    "Below this, the result is reported as 'unknown'.",
                    "NOTE: this lives on the Phase-1 DE-COMPRESSED scale set by",
                    "RECOGNITION_DIST_AT_FULL/ZERO_SCORE — a typical good draw now scores",
                    "~0.9 and a typical non-match ~0.2, so the crossover is far below the",
                    "old compressed ~0.90 band. Re-tuned (2026-06) on the Tier-2 corpus",
                    "replay (current 75-variant template set, /spell replay-corpus): 0.30 is",
                    "the knee of the curve — ~87% valid recall, and the lowest floor that",
                    "still rejects all garbage (with ambiguityMargin 0.10). Below 0.30 garbage",
                    "leaks in without recovering any valid draws (the rejected ones sit inside",
                    "the valid/garbage distance overlap); higher rejects real casts.",
                    "RECALL-FIRST retune (2026-06): lowered 0.30 → 0.15. This floor lives on",
                    "the wider distAtZero=0.12 scale, so the weak draws that distAtZero now",
                    "rescues need a lower floor to actually pass. Coupled with that ramp —",
                    "do not move one without the other.",
                    "FINAL (2026-06-11, R4 retune on the 123-variant set, 275-sample 2-drawer",
                    "corpus): 0.12. The floor is inert across 0.08–0.14 here — residual errors",
                    "are confusions/near-ties, not weak scores — so it sits mid-plateau.",
                    "Operating point: 96% recall, 17% garbage rejected (accepted trade).",
                    "Range: 0.0 – 1.0. Default: 0.12.")
            .defineInRange("recognitionMinScore", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RECOGNITION_DIST_AT_FULL_SCORE = BUILDER
            .comment("Phase-1 score de-compression — LOWER pin. Effective chamfer distance",
                    "at or below which a match scores a full 1.0. With",
                    "RECOGNITION_DIST_AT_ZERO_SCORE this replaces the old fixed 1−d/√3 map,",
                    "which crushed every score into a ~0.94–0.98 band and left valid and",
                    "garbage inseparable by any threshold. The score is a linear ramp:",
                    "  score = clamp((distAtZero − effDist) / (distAtZero − distAtFull), 0, 1)",
                    "Set near the MEDIAN effective distance of VALID draws, so a typical good",
                    "draw lands ~0.9. Derived offline by analyze_recognition_log.py's Phase-1",
                    "recommendation; re-derive as the corpus grows.",
                    "Tuned (2026-06) on a 119-sample labeled corpus: 0.054.",
                    "FINAL (2026-06-11): lowered 0.054 → 0.03 to DE-SATURATE the ramp's top.",
                    "With dense template coverage (123 variants) several classes' best match",
                    "fell below 0.054, pinning multiple scores at exactly 1.0 — and the",
                    "ambiguity margin cannot separate saturated ties (observed: column 1.000",
                    "vs dispersion 0.999 → false 'unknown'). The lower pin restores ordering",
                    "at the top; recall +2 and garbage rejection improved at the same time.",
                    "Range: 0.0 – 0.30. Default: 0.03.")
            .defineInRange("recognitionDistAtFullScore", 0.03, 0.0, 0.30);
    public static final ModConfigSpec.DoubleValue RECOGNITION_DIST_AT_ZERO_SCORE = BUILDER
            .comment("Phase-1 score de-compression — UPPER pin. Effective chamfer distance",
                    "at or above which a match scores 0.0. Must be greater than",
                    "distAtFullScore. Set near the MEDIAN effective distance of",
                    "NON-MATCH/garbage draws, so a typical non-match lands ~0.2 and falls",
                    "below recognitionMinScore. Derived offline by analyze_recognition_log.py;",
                    "re-derive as the corpus grows.",
                    "Tuned (2026-06) on a 119-sample labeled corpus: 0.085.",
                    "RECALL-FIRST retune (2026-06): widened 0.085 → 0.12. The old 0.085 pin",
                    "scored any draw past 0.085 distance as exactly 0, so ~12 weak but valid",
                    "column/dispersion/levitation draws were unrecoverable by any floor.",
                    "Widening the ramp gives them a nonzero score so a lowered minScore can",
                    "admit them — at the cost of more garbage scoring nonzero too (accepted",
                    "trade). Dial in via /spell replay-corpus.",
                    "FINAL (2026-06-11, R4): 0.12 confirmed — 0.11/0.13 measure the same;",
                    "garbage rejection at the shipped operating point is 17% (4/23), the",
                    "recorded cost of the recall-first objective (96% recall).",
                    "Range: 0.0 – 0.60. Default: 0.12.")
            .defineInRange("recognitionDistAtZeroScore", 0.12, 0.0, 0.60);
    public static final ModConfigSpec.DoubleValue RECOGNITION_AMBIGUITY_MARGIN = BUILDER
            .comment("Minimum score gap between the best template and the runner-up of a",
                    "DIFFERENT spell. If the winner beats the second-best by less than this",
                    "margin, the result is reported as 'unknown' rather than risking a",
                    "confident misclassification. Variants of the same spell never trigger",
                    "this gate against each other.",
                    "Lower values are permissive; higher values demand a clear winner.",
                    "Re-tuned (2026-06) on the Tier-2 corpus replay: 0.10 lifts garbage",
                    "rejection from ~94% to 100% (catches the one ambiguous-garbage draw)",
                    "with no loss of valid recall — correct draws and even the residual",
                    "confident misclassification win by far larger gaps, so they're untouched.",
                    "0.10 is the conservative end of the winning plateau (0.08–0.20 all score",
                    "the same here), leaving headroom before real cross-spell near-ties gate.",
                    "RECALL-FIRST retune (2026-06): lowered 0.10 → 0.04. The 0.10 margin",
                    "rejected near-ties as 'unknown' (recall loss); a lower margin lets the",
                    "top pick win more often. Accepts more cross-spell confusion, which the",
                    "recall-first objective tolerates.",
                    "FINAL (2026-06-11, R4): 0.03, paired with distAtFullScore 0.03 — once",
                    "the ramp top is de-saturated, real winners separate by more than 0.03",
                    "while saturated false ties no longer occur.",
                    "Range: 0.00 – 0.30. Default: 0.03.")
            .defineInRange("recognitionAmbiguityMargin", 0.03, 0.0, 0.30);
    public static final ModConfigSpec.DoubleValue RECOGNITION_CONSENSUS_BONUS = BUILDER
            .comment("Consensus tie-breaker bonus. When the winning sigil fails the",
                    "ambiguity margin against a DIFFERENT spell, the recognizer counts how",
                    "many of the top-N surviving templates share the winner's spell and adds",
                    "this much to the winner's score per match. Several variants of one spell",
                    "clustering at the top (e.g. levitation ×3) is strong evidence, so that",
                    "agreement can break an otherwise-ambiguous near-tie.",
                    "Set to 0.0 to disable the consensus rescue.",
                    "Range: 0.00 – 0.10. Default: 0.01.")
            .defineInRange("recognitionConsensusBonus", 0.01, 0.0, 0.10);
    public static final ModConfigSpec.IntValue RECOGNITION_CONSENSUS_TOP_N = BUILDER
            .comment("How many of the top-ranked surviving templates the consensus",
                    "tie-breaker inspects when counting same-spell agreement.",
                    "Range: 1 – 20. Default: 5.")
            .defineInRange("recognitionConsensusTopN", 5, 1, 20);
    public static final ModConfigSpec.BooleanValue RECOGNITION_LOGGING_ENABLED = BUILDER
            .comment("When true, append a structured JSONL record for every recognized sigil to",
                    "logs/spell_recognition.jsonl (one line per sigil). Each record captures the",
                    "raw strokes, the processed point cloud, the full per-template chamfer distance",
                    "AND score, best-per-spell, the final result, and the active thresholds — the",
                    "data source for calibrating the recognizer (and a future training corpus).",
                    "Off by default; enable during data-collection sessions.")
            .define("recognitionLoggingEnabled", false);
    // ── Filtering pipeline (pre / post around the $P+ chamfer) ──────────────────

    public static final ModConfigSpec.DoubleValue ASPECT_RATIO_TALL_THRESHOLD = BUILDER
            .comment("Aspect ratio (width / height) below which a sigil is classified as",
                    "'tall'. A candidate classified tall will be rejected against any",
                    "template classified wide (and vice versa) before the chamfer runs,",
                    "regardless of magnitude.",
                    "RECALL-FIRST retune (2026-06): lowered 0.85 → 0.75 to widen the",
                    "permissive 'square' band, so fewer valid draws are hard-rejected by",
                    "this prefilter before the chamfer even runs.",
                    "Range: 0.50 – 1.00. Default: 0.75.")
            .defineInRange("aspectRatioTallThreshold", 0.75, 0.50, 1.00);
    public static final ModConfigSpec.DoubleValue ASPECT_RATIO_WIDE_THRESHOLD = BUILDER
            .comment("Aspect ratio (width / height) above which a sigil is classified as",
                    "'wide'. Paired with the tall threshold to form a direction-aware",
                    "pre-filter: tall vs. wide cross-matches are rejected, square↔tall",
                    "and square↔wide are allowed (chamfer decides).",
                    "RECALL-FIRST retune (2026-06): raised 1.18 → 1.30 (upper side of the",
                    "same widened 'square' band as aspectRatioTallThreshold).",
                    "Range: 1.00 – 2.00. Default: 1.30.")
            .defineInRange("aspectRatioWideThreshold", 1.30, 1.00, 2.00);
    public static final ModConfigSpec.IntValue DOT_COUNT_TOLERANCE = BUILDER
            .comment("Allowed |candidate.dotCount − template.dotCount|. Dot count is the",
                    "number of zero-length 'tap' strokes detected before dot injection.",
                    "Templates with dots (Earth, Cross-hair) need at least one dot in",
                    "the candidate; this tolerance permits one missing or spurious dot.",
                    "RECALL-FIRST retune (2026-06): loosened 1 → 2 so an extra/missing tap",
                    "no longer hard-rejects an otherwise-valid draw.",
                    "Range: 0 – 5. Default: 2.")
            .defineInRange("dotCountTolerance", 2, 0, 5);
    public static final ModConfigSpec.IntValue LOOP_COUNT_TOLERANCE = BUILDER
            .comment("Allowed |candidate.closedLoopCount − template.closedLoopCount|.",
                    "Loop count is computed via Euler's formula on the stroke-endpoint",
                    "graph: E − V + C, where endpoints within LOOP_CLOSURE_FRACTION of",
                    "the bounding-box diagonal are stitched into the same vertex.",
                    "Templates with N closed loops (Fire=1, Collection=2, …) need the",
                    "candidate to land within this tolerance.",
                    "RECALL-FIRST retune (2026-06): loosened 1 → 2 so a stray/missed loop",
                    "closure no longer hard-rejects an otherwise-valid draw.",
                    "Range: 0 – 3. Default: 2.")
            .defineInRange("loopCountTolerance", 2, 0, 3);
    public static final ModConfigSpec.DoubleValue LOOP_CLOSURE_FRACTION = BUILDER
            .comment("Endpoint stitching radius for closed-loop counting, expressed as",
                    "a fraction of the bounding-box diagonal. Stroke endpoints within",
                    "this radius are merged into the same graph vertex.",
                    "Range: 0.02 – 0.30. Default: 0.12.")
            .defineInRange("loopClosureFraction", 0.12, 0.02, 0.30);
    public static final ModConfigSpec.IntValue MIN_POINTS_PER_STROKE = BUILDER
            .comment("Minimum points the resampler allocates to any single stroke,",
                    "regardless of its proportional arc length. Without a floor, very",
                    "short strokes (Cross-hair's marks, Earth's dot rings) get 1–2",
                    "points and become invisible to the chamfer. The floor is capped",
                    "at n/numStrokes so it never starves longer strokes when many",
                    "strokes are present.",
                    "Range: 2 – 16. Default: 4.")
            .defineInRange("minPointsPerStroke", 4, 2, 16);
    public static final ModConfigSpec.DoubleValue INK_DENSITY_MAX_REL_DIFF = BUILDER
            .comment("Maximum allowed relative deviation between candidate and template",
                    "ink density. Ink density = totalStrokeLength / bboxDiagonal,",
                    "measuring how much path is drawn per unit of shape extent.",
                    "A simple cross has density ≈ 1.4; a detailed starburst is 7+.",
                    "0.25 means accept candidates whose density is within ±25% of the template's.",
                    "RECALL-FIRST retune (2026-06): loosened 0.50 → 0.80. This is a HARD",
                    "prefilter — a mis-proportioned valid draw is deleted before the chamfer,",
                    "so a wider tolerance directly recovers recall.",
                    "Range: 0.05 – 1.00. Default: 0.80.")
            .defineInRange("inkDensityMaxRelDiff", 0.80, 0.05, 1.0);
    public static final ModConfigSpec.DoubleValue GRID_CHECK_SCORE_THRESHOLD = BUILDER
            .comment("Chamfer-score threshold above which the 3×3 spatial histogram",
                    "post-filter activates. The post-filter is a sanity check on",
                    "otherwise-high scores — it confirms the candidate's spatial mass",
                    "distribution resembles the template's, not just its outline.",
                    "Below this score, no extra check runs.",
                    "Range: 0.5 – 1.0. Default: 0.70.")
            .defineInRange("gridCheckScoreThreshold", 0.70, 0.5, 1.0);
    public static final ModConfigSpec.DoubleValue GRID_MIN_SIMILARITY = BUILDER
            .comment("3×3 histogram similarity (1 − L1/2 over normalized mass) at or above",
                    "which a match keeps its full chamfer score. Below this, the score is",
                    "scaled down proportionally (soft penalty = gridSim / gridMinSimilarity),",
                    "ramping to 0 as the spatial mass distribution diverges — a near-miss is",
                    "demoted, not deleted. Lower = permissive, higher = strict.",
                    "RECALL-FIRST retune (2026-06): lowered 0.70 → 0.55 so a spatially-off",
                    "but valid draw keeps more of its chamfer score (softer demote).",
                    "Range: 0.30 – 0.95. Default: 0.55.")
            .defineInRange("gridMinSimilarity", 0.55, 0.30, 0.95);
    public static final ModConfigSpec.DoubleValue WORST_PAIR_FREE_ALLOWANCE = BUILDER
            .comment("Phase-2 worst-pair SOFT-demote: free allowance. Worst-pair distance",
                    "up to this value adds nothing to the effective chamfer distance — a",
                    "single moderately-far point (a hook, an endpoint) is tolerated. Above",
                    "it, the excess is weighted (WORST_PAIR_WEIGHT) and ADDED to the mean",
                    "distance before scoring, so the match is demoted, not deleted. Set",
                    "near the upper bulk of valid draws' worst-pair (~0.23 median).",
                    "Tuned (2026-06) on a 119-sample labeled corpus: 0.20.",
                    "Range: 0.00 – 0.60. Default: 0.20.")
            .defineInRange("worstPairFreeAllowance", 0.20, 0.00, 0.60);
    public static final ModConfigSpec.DoubleValue WORST_PAIR_WEIGHT = BUILDER
            .comment("Phase-2 worst-pair SOFT-demote: weight on the excess worst-pair",
                    "distance above the free allowance. effectiveDistance = meanDistance +",
                    "WORST_PAIR_WEIGHT * max(0, worstPair - WORST_PAIR_FREE_ALLOWANCE); the",
                    "score map (Phase-1 ramp) then runs on that effective distance.",
                    "DISABLED (0.0) since Phase-1 de-compression (2026-06): on the new scale",
                    "a plain RECOGNITION_MIN_SCORE on the mean distance already rejects ~94%",
                    "of garbage — the demote was compensating for the old COMPRESSED scale.",
                    "With the tight Phase-1 pins any weight>0 pushes effDist past",
                    "distAtZeroScore and over-rejects valid draws (recall collapses). Re-",
                    "enable only alongside a wider RECOGNITION_DIST_AT_ZERO_SCORE if logs show",
                    "garbage with a low mean but a stranded far point.",
                    "Range: 0.00 – 2.00. Default: 0.00.")
            .defineInRange("worstPairWeight", 0.00, 0.00, 2.00);
    public static final ModConfigSpec.DoubleValue DOT_INJECTION_RADIUS = BUILDER
            .comment("Radius (in normalized [0,1] canvas coordinates) used both to",
                    "detect dot-strokes (path length < radius) and to size the",
                    "injected circle of points that replaces each dot. The injection",
                    "ensures the $P+ resampler always has real geometry instead of a",
                    "stack of duplicate coordinates.",
                    "Range: 0.001 – 0.10. Default: 0.01.")
            .defineInRange("dotInjectionRadius", 0.01, 0.001, 0.10);
    public static final ModConfigSpec.IntValue DOT_INJECTION_CIRCLE_POINTS = BUILDER
            .comment("Number of points that form each injected dot ring. Must be at",
                    "least 3 for the resampler to see geometry rather than collinear",
                    "points; 8 gives the resampler a clean circle to bite into.",
                    "Range: 3 – 32. Default: 8.")
            .defineInRange("dotInjectionCirclePoints", 8, 3, 32);

    // ── Spell meaning (sigil stacking) ──────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue SIGIL_STACK_POWER_PER_EXTRA = BUILDER
            .comment("Power bonus per DUPLICATE sigil of the same element drawn in one ring.",
                    "Drawing the same element N times multiplies the spell's power by",
                    "1 + (N - 1) * this value. Example at 0.5: one fire = ×1.0, two fire = ×1.5,",
                    "three fire = ×2.0. Different elements in one ring are still rejected (combine",
                    "them via nested rings); only repeats of the SAME element stack here.",
                    "Range: 0.0 – 4.0. Default: 0.5 (each extra sigil = +50% power).")
            .defineInRange("sigilStackPowerPerExtra", 0.5, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue SIZE_POWER_MAX = BUILDER
            .comment("Power/reach multiplier a sigil reaches at FULL drawn size. Drawn size is",
                    "the content bounding box as a fraction of the ring (0 = a dot, 1 = fills the",
                    "ring). The curve is anchored (see sizePowerReference) so a reference-sized",
                    "sigil = ×1.0 and a full-size one = this value, letting 'draw it big' amplify",
                    "a spell instead of only ever shrinking it. Affects both the abstract power",
                    "(Pyreball, Wind shove) and effect reach (pillar/column height).",
                    "Example at 2.0: one full-size column ≈ two reference-size columns. Set 3.0",
                    "to make one big column ≈ three small ones. Range: 1.0 – 8.0. Default: 2.0.")
            .defineInRange("sizePowerMax", 2.0, 1.0, 8.0);
    public static final ModConfigSpec.DoubleValue SIZE_POWER_REFERENCE = BUILDER
            .comment("The drawn size that maps to ×1.0 power — the anchor of the size curve.",
                    "Sigils drawn at this size are unaffected; larger ones amplify toward",
                    "sizePowerMax, smaller ones fall toward a 0.1 floor. Lower it to make more",
                    "drawings count as 'large', raise it to make amplification harder to reach.",
                    "Range: 0.05 – 0.95. Default: 0.5.")
            .defineInRange("sizePowerReference", 0.5, 0.05, 0.95);
    public static final ModConfigSpec.DoubleValue SIZE_POWER_EXPONENT = BUILDER
            .comment("Shape of the size→power curve on each side of the reference anchor.",
                    "1.0 = linear ramp. >1.0 = big-heavy (size barely matters until near full,",
                    "then ramps hard). <1.0 = front-loaded (small size gains matter most).",
                    "Range: 0.25 – 4.0. Default: 1.0.")
            .defineInRange("sizePowerExponent", 1.0, 0.25, 4.0);
    public static final ModConfigSpec.DoubleValue DIRECTION_SIZE_EXPONENT = BUILDER
            .comment("Extra weight size carries on a DIRECTIONAL cast's steering force (the",
                    "Column sign's lean), over and above its effect on power. The steering skew",
                    "uses the size→power multiplier raised to this exponent, so >1.0 makes a big",
                    "off-centre sign steer much harder while a small one barely leans. 1.0 = the",
                    "steering tracks size exactly like power does. Range: 0.5 – 4.0. Default: 1.5.")
            .defineInRange("directionSizeExponent", 1.5, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue COLUMN_STEER_DEADZONE = BUILDER
            .comment("How far off the sigil centre a Column sign must be drawn before it steers",
                    "the pillar at all, in canvas units (the displacement of the Column from the",
                    "sigil centre; the whole canvas is 1.0 wide). Inside this radius the pillar",
                    "goes STRAIGHT UP — raise it to make 'straight up' easier to hit by hand,",
                    "lower it toward 0 to let even a slightly off-centre Column lean.",
                    "Range: 0.0 – 0.5. Default: 0.10.")
            .defineInRange("columnSteerDeadzone", 0.10, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue COLUMN_STEER_FULL_DISTANCE = BUILDER
            .comment("The off-centre distance (canvas units) at which a Column reaches its FULL",
                    "lean (columnSteerMaxSkew). Between columnSteerDeadzone and this, the lean",
                    "ramps up linearly, so the further you draw the Column to one side the harder",
                    "the pillar tilts. Must be above the deadzone. Range: 0.05 – 0.71. Default: 0.40.")
            .defineInRange("columnSteerFullDistance", 0.40, 0.05, 0.71);
    public static final ModConfigSpec.DoubleValue COLUMN_STEER_MAX_SKEW = BUILDER
            .comment("Strength of a Column sign's lean at full off-centre distance and quality.",
                    "It is the length of the horizontal steering bias added to the pillar's",
                    "(unit-length) upward direction before re-normalizing: 1.0 = a 45-degree lean",
                    "at most, higher = closer to horizontal. Lower this to keep pillars more",
                    "upright even when steered. Range: 0.0 – 8.0. Default: 2.5.")
            .defineInRange("columnSteerMaxSkew", 2.5, 0.0, 8.0);

    public static final ModConfigSpec.DoubleValue SYMMETRY_CANCEL_DEADZONE = BUILDER
            .comment("How close to balanced opposing signs must be before the spell's net",
                    "direction cancels to zero. Each sign is a force vector — its displacement",
                    "from the sigil centre, weighted by recognizer quality. The engine sums",
                    "them and divides the net length by the total force length to get an",
                    "imbalance in [0, 1]: 0 = forces perfectly cancel, 1 = all pull one way.",
                    "When imbalance <= this value the residual is snapped to zero, so",
                    "slightly-imperfect mirror placements still yield a balanced spell —",
                    "making it easier to draw a directionless cast on purpose.",
                    "0.0 = only exact cancellation balances (old behaviour). Higher = more",
                    "forgiving. Range: 0.0 – 1.0. Default: 0.2.")
            .defineInRange("symmetryCancelDeadzone", 0.2, 0.0, 1.0);

    // ── Spell casting & fuel system ─────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue DEFAULT_SPELL_FUEL = BUILDER
            .comment("Default fuel units a spell starts with. Will be replaced by",
                    "ink-type-driven values in a future update, but for now all spells",
                    "start with this much fuel. A spell ends when fuel reaches zero.",
                    "Per-tick costs drain this each tick; per-use costs drain on trigger/event.",
                    "Range: 1.0 – 10000.0. Default: 100.0.")
            .defineInRange("defaultSpellFuel", 1200.0, 1.0, 10000.0);
    public static final ModConfigSpec.DoubleValue COST_POWER_SCALING = BUILDER
            .comment("How strongly fuel cost tracks a spell's power. The matrix cost.per_tick/",
                    "per_use values are the BASE, paid in full by a spell drawn at its reference",
                    "power. Amplifiers (quality, size, sign stacking, sign behaviours, repeated",
                    "sigils) raise power above that baseline; cost is multiplied by",
                    "1 + (powerFactor - 1) * this, where powerFactor = finalPower / basePower.",
                    "0.0 = flat cost (old behaviour). 1.0 = cost rises 1:1 with power",
                    "(double power → double cost). >1.0 = a steeper toll on heavy casts.",
                    "Range: 0.0 – 4.0. Default: 1.0.")
            .defineInRange("costPowerScaling", 1.0, 0.0, 4.0);

    // Must be declared AFTER all values so the builder has them all registered before building.
    static final ModConfigSpec SPEC = BUILDER.build();
}
