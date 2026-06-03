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
            .comment("Closure radius (canvas pixels). Distance between the ultimate head and tail",
                    "of a stroke chain that counts as 'closed' and may trigger an activation ring.",
                    "Range: 1 – 30. Default: 10.")
            .defineInRange("closureEpsilonPixels", 10.0, 1.0, 30.0);

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
                    "Tuned (2026-06) on a 119-sample labeled corpus together with the",
                    "worst-pair soft-demote: 0.90 gives ~85% valid recall and rejects",
                    "~89% of garbage. Lower re-admits garbage; higher rejects real casts.",
                    "Range: 0.0 – 1.0. Default: 0.90.")
            .defineInRange("recognitionMinScore", 0.90, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RECOGNITION_AMBIGUITY_MARGIN = BUILDER
            .comment("Minimum score gap between the best template and the runner-up of a",
                    "DIFFERENT spell. If the winner beats the second-best by less than this",
                    "margin, the result is reported as 'unknown' rather than risking a",
                    "confident misclassification. Variants of the same spell never trigger",
                    "this gate against each other.",
                    "Lower values are permissive; higher values demand a clear winner.",
                    "Range: 0.00 – 0.30. Default: 0.10.")
            .defineInRange("recognitionAmbiguityMargin", 0.01, 0.0, 0.30);
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
                    "Range: 0.50 – 1.00. Default: 0.85.")
            .defineInRange("aspectRatioTallThreshold", 0.85, 0.50, 1.00);
    public static final ModConfigSpec.DoubleValue ASPECT_RATIO_WIDE_THRESHOLD = BUILDER
            .comment("Aspect ratio (width / height) above which a sigil is classified as",
                    "'wide'. Paired with the tall threshold to form a direction-aware",
                    "pre-filter: tall vs. wide cross-matches are rejected, square↔tall",
                    "and square↔wide are allowed (chamfer decides).",
                    "Range: 1.00 – 2.00. Default: 1.18.")
            .defineInRange("aspectRatioWideThreshold", 1.18, 1.00, 2.00);
    public static final ModConfigSpec.IntValue DOT_COUNT_TOLERANCE = BUILDER
            .comment("Allowed |candidate.dotCount − template.dotCount|. Dot count is the",
                    "number of zero-length 'tap' strokes detected before dot injection.",
                    "Templates with dots (Earth, Cross-hair) need at least one dot in",
                    "the candidate; this tolerance permits one missing or spurious dot.",
                    "Range: 0 – 5. Default: 1.")
            .defineInRange("dotCountTolerance", 1, 0, 5);
    public static final ModConfigSpec.IntValue LOOP_COUNT_TOLERANCE = BUILDER
            .comment("Allowed |candidate.closedLoopCount − template.closedLoopCount|.",
                    "Loop count is computed via Euler's formula on the stroke-endpoint",
                    "graph: E − V + C, where endpoints within LOOP_CLOSURE_FRACTION of",
                    "the bounding-box diagonal are stitched into the same vertex.",
                    "Templates with N closed loops (Fire=1, Collection=2, …) need the",
                    "candidate to land within this tolerance.",
                    "Range: 0 – 3. Default: 1.")
            .defineInRange("loopCountTolerance", 1, 0, 3);
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
                    "Range: 0.05 – 1.00. Default: 0.25.")
            .defineInRange("inkDensityMaxRelDiff", 0.50, 0.05, 1.0);
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
                    "Range: 0.30 – 0.95. Default: 0.70.")
            .defineInRange("gridMinSimilarity", 0.70, 0.30, 0.95);
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
                    "WORST_PAIR_WEIGHT * max(0, worstPair - WORST_PAIR_FREE_ALLOWANCE). The",
                    "score is then 1 - effectiveDistance/sqrt(3) as usual. Garbage that",
                    "strands points far away is pushed below RECOGNITION_MIN_SCORE while a",
                    "strong mean match survives one outlier. 0.0 disables the demote.",
                    "Tuned (2026-06) on a 119-sample labeled corpus: 0.50.",
                    "Range: 0.00 – 2.00. Default: 0.50.")
            .defineInRange("worstPairWeight", 0.50, 0.00, 2.00);
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
