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
            .defineInRange("resampleN", 64, 16, 128);
    public static final ModConfigSpec.DoubleValue RECOGNITION_MIN_SCORE = BUILDER
            .comment("Minimum confidence score for a recognized spell.",
                    "Below this, the result is reported as 'unknown'.",
                    "Range: 0.0 – 1.0. Default: 0.75.")
            .defineInRange("recognitionMinScore", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RECOGNITION_AMBIGUITY_MARGIN = BUILDER
            .comment("Minimum score gap between the best template and the runner-up of a",
                    "DIFFERENT spell. If the winner beats the second-best by less than this",
                    "margin, the result is reported as 'unknown' rather than risking a",
                    "confident misclassification. Variants of the same spell never trigger",
                    "this gate against each other.",
                    "Lower values are permissive; higher values demand a clear winner.",
                    "Range: 0.00 – 0.30. Default: 0.10.")
            .defineInRange("recognitionAmbiguityMargin", 0.10, 0.0, 0.30);
    public static final ModConfigSpec.DoubleValue SCORE_CURVE_EXPONENT = BUILDER
            .comment("Exponent applied to the linear chamfer-derived score before thresholding.",
                    "1.0 = linear (legacy behavior). 2.0 = quadratic — pushes mid scores down",
                    "(0.80 → 0.64) while top scores barely move (0.95 → 0.90), giving a much",
                    "cleaner separation between correct sigils and partial matches.",
                    "Pairs with RECOGNITION_MIN_SCORE; you may need to lower the threshold",
                    "when raising the exponent because the curve compresses the entire range.",
                    "Range: 1.0 – 4.0. Default: 2.0.")
            .defineInRange("scoreCurveExponent", 2.0, 1.0, 4.0);
    public static final ModConfigSpec.DoubleValue COVERAGE_WEIGHT = BUILDER
            .comment("Asymmetric chamfer weight: how heavily template-not-covered-by-input",
                    "distances count versus input-to-template distances. Higher = more",
                    "sensitive to drawings that fail to cover the template (e.g., a simple",
                    "T-shape against a complex multi-stroke template).",
                    "1.0 = symmetric (legacy behavior). 2.0 = recommended starting point.",
                    "Range: 0.0 – 10.0. Default: 2.0.")
            .defineInRange("coverageWeight", 4.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue COVERAGE_TOUCH_THRESHOLD = BUILDER
            .comment("Maximum point distance (in $P+'s normalized 3-channel space) within",
                    "which an input point counts as 'covering' a template point. Template",
                    "points whose nearest input lies beyond this threshold are treated as",
                    "uncovered and contribute to Phase B (coverage failure) of the chamfer.",
                    "Without a gate, Phase A's NN lookup marks template points as 'touched'",
                    "even when the input is far away, vacuously satisfying coverage. The",
                    "gate is closer to $P+'s coverage spirit.",
                    "Lower values demand tighter coverage; higher values are permissive.",
                    "Range: 0.01 – 0.30. Default: 0.05.")
            .defineInRange("coverageTouchThreshold", 0.10, 0.01, 0.30);
    public static final ModConfigSpec.DoubleValue ARC_LENGTH_RATIO_MIN = BUILDER
            .comment("Minimum allowed ratio of candidate arc length to template arc length.",
                    "A simple shape drawn against a complex template is rejected when their",
                    "total path lengths differ too much, regardless of stroke count.",
                    "This is drawing-style-agnostic: a triangle in one stroke vs three strokes",
                    "has the same arc length, so both pass. But a trapezoid vs a multi-arm star",
                    "will fail because the star traces far more path relative to its bounding box.",
                    "Range: 0.1 – 1.0. Default: 0.4.")
            .defineInRange("arcLengthRatioMin", 0.4, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue ARC_LENGTH_RATIO_MAX = BUILDER
            .comment("Maximum allowed ratio of candidate arc length to template arc length.",
                    "Rejects candidates that trace far more path than the template expects.",
                    "Range: 1.0 – 10.0. Default: 2.5.")
            .defineInRange("arcLengthRatioMax", 2.5, 1.0, 10.0);

    // Must be declared AFTER all values so the builder has them all registered before building.
    static final ModConfigSpec SPEC = BUILDER.build();
}
