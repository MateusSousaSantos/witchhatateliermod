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
            .comment("Minimum score gap between the best template and the runner-up.",
                    "If the winner beats the second-best by less than this margin,",
                    "the result is reported as 'unknown' rather than risking a confident",
                    "misclassification on two visually similar sigils.",
                    "Range: 0.00 – 0.30. Default: 0.08.")
            .defineInRange("recognitionAmbiguityMargin", 0.01, 0.0, 0.30);

    // Must be declared AFTER all values so the builder has them all registered before building.
    static final ModConfigSpec SPEC = BUILDER.build();
}
