package com.crsocial.witchhatatelier.spell.recognition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cheap pre- and post-filters that bracket the $P+ chamfer to suppress false
 * positives without paying the O(N²) recognizer cost on obviously-wrong templates.
 *
 * <p>The recognizer's chamfer is scale-, density-, and aspect-invariant — useful
 * for matching the <i>shape</i> of a sigil regardless of how it was drawn, but
 * also the reason a "+" matches a multi-arm starburst at high confidence. These
 * filters re-introduce the invariants the chamfer threw away:</p>
 *
 * <ol>
 *   <li><b>Aspect ratio gate (pre):</b> bounding-box {@code W/H} ratios must
 *       agree within a configurable multiplier. Tall lines won't be tested
 *       against squares.</li>
 *   <li><b>Ink density gate (pre):</b> {@code totalStrokeLength / bboxDiagonal}
 *       must agree within a relative tolerance. A simple "+" has density ≈ 1.4;
 *       a detailed starburst is 7+. Same template? No.</li>
 *   <li><b>3×3 spatial histogram (post):</b> only runs when the chamfer score
 *       crosses a high-confidence threshold. Confirms the candidate's <i>point
 *       mass distribution</i> resembles the template's, not just its outline.</li>
 *   <li><b>Dot-stroke injection (input fixup):</b> replaces zero-length "tap"
 *       strokes with a small ring of points so resampling produces real geometry
 *       instead of N copies of the same coordinate.</li>
 * </ol>
 *
 * <p>All checks are O(1) given precomputed {@link SigilMetrics}, so the hot
 * loop pays no point-iteration cost at match time.</p>
 */
public final class SigilFilters {

    private static final float EPS = 1e-6f;

    private SigilFilters() {}

    // ── 1. Aspect ratio gate (direction-aware) ──────────────────────────────────

    /**
     * Direction-aware aspect-ratio gate. Classifies both clouds as
     * <i>tall</i> ({@code AR < tallThreshold}), <i>wide</i>
     * ({@code AR > wideThreshold}), or <i>square</i> (in between), then rejects
     * cross-class pairs (tall ↔ wide). Square is permissive on both sides — the
     * chamfer decides whether a slightly-off-axis candidate matches.
     *
     * <p>Earlier multiplicative {@code max(a/b, b/a)} comparisons treated tall
     * and wide symmetrically, so a vertical "Column" sigil could still pass
     * against a horizontal "Crush" sigil if their AR magnitudes happened to
     * cancel. The direction check fixes that.</p>
     *
     * @param tallThreshold AR below this is "tall" (e.g. 0.85)
     * @param wideThreshold AR above this is "wide" (e.g. 1.18)
     */
    public static boolean aspectRatioPasses(SigilMetrics candidate, SigilMetrics template,
                                            float tallThreshold, float wideThreshold) {
        float ca = candidate.aspectRatio();
        float ta = template.aspectRatio();
        boolean candTall = ca < tallThreshold;
        boolean candWide = ca > wideThreshold;
        boolean tmplTall = ta < tallThreshold;
        boolean tmplWide = ta > wideThreshold;
        if (candTall && tmplWide) return false;
        return !candWide || !tmplTall;
    }

    // ── 1b. Dot count (free discriminator for Earth, Cross-hair) ────────────────

    /**
     * Counts strokes whose path length is below {@code dotRadius} — i.e. the
     * same predicate {@link #injectDotsIfNeeded} uses to detect taps. Must be
     * called <i>before</i> dot injection; after injection, all dot strokes are
     * full rings and the count would always be zero.
     */
    public static int countDots(List<Point> input, float dotRadius) {
        if (input.isEmpty() || dotRadius <= 0f) return 0;
        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (Point p : input) {
            byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        }
        int dots = 0;
        for (List<Point> stroke : byStroke.values()) {
            if (strokeLength(stroke) < dotRadius) dots++;
        }
        return dots;
    }

    /**
     * Tolerance gate: {@code |candidate.dotCount − template.dotCount| ≤ tolerance}.
     * Templates with dots (Earth=2, Cross-hair=4) require the candidate to land
     * within ±tolerance dots; templates without dots reject candidates that
     * accidentally tapped too many times.
     */
    public static boolean dotCountPasses(SigilMetrics candidate, SigilMetrics template, int tolerance) {
        int diff = candidate.dotCount() - template.dotCount();
        if (diff < 0) diff = -diff;
        return diff <= tolerance;
    }

    // ── 1c. Closed-loop count (Euler formula on the endpoint graph) ─────────────

    /**
     * Counts independent closed loops formed by the strokes, treating endpoints
     * within {@code closureEpsilon} as the same graph vertex. Uses Euler's
     * formula for planar graphs: {@code cycles = E − V + C} where {@code E} is
     * the stroke count, {@code V} is the unique endpoint-cluster count, and
     * {@code C} is the number of stroke-graph connected components.
     *
     * <p>Robust against drawing style — a triangle drawn as one continuous
     * stroke (V=1, E=1, C=1 → 1 loop) and the same triangle drawn as three
     * separate edges (V=3, E=3, C=1 → 1 loop) both yield the same count. A
     * cross or zigzag (open structure) yields 0.</p>
     *
     * <p>Dot strokes are excluded from both the V and E counts — they're
     * topologically irrelevant and would otherwise inflate {@code V} (two
     * indistinguishable endpoints at the dot center) without contributing
     * real connectivity.</p>
     *
     * @param closureEpsilon endpoint-stitching radius in the cloud's coordinate
     *                       space (callers typically pass
     *                       {@code closureFraction * bboxDiagonal})
     */
    public static int countClosedLoops(List<Point> input, float closureEpsilon) {
        if (input.isEmpty() || closureEpsilon <= 0f) return 0;

        // Group + filter out dot strokes (they have no topological meaning here).
        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (Point p : input) {
            byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        }
        List<List<Point>> strokes = new ArrayList<>(byStroke.size());
        for (List<Point> s : byStroke.values()) {
            if (strokeLength(s) >= closureEpsilon) strokes.add(s);
        }
        int e = strokes.size();
        if (e == 0) return 0;

        // Union-find over 2·e endpoints. Even index = head, odd index = tail.
        int en = 2 * e;
        int[] parent = new int[en];
        for (int i = 0; i < en; i++) parent[i] = i;

        Point[] eps = new Point[en];
        for (int i = 0; i < e; i++) {
            List<Point> s = strokes.get(i);
            eps[2 * i]     = s.getFirst();
            eps[2 * i + 1] = s.getLast();
        }

        float epsSq = closureEpsilon * closureEpsilon;
        for (int i = 0; i < en; i++) {
            for (int j = i + 1; j < en; j++) {
                float dx = eps[i].x() - eps[j].x();
                float dy = eps[i].y() - eps[j].y();
                if (dx * dx + dy * dy < epsSq) union(parent, i, j);
            }
        }

        // V = distinct endpoint clusters.
        Set<Integer> vSet = new HashSet<>();
        for (int i = 0; i < en; i++) vSet.add(find(parent, i));
        int v = vSet.size();

        // C = connected components of the stroke graph (strokes connected via
        // shared endpoint clusters).
        int[] strokeParent = new int[e];
        for (int i = 0; i < e; i++) strokeParent[i] = i;
        Map<Integer, Integer> firstStrokeInCluster = new HashMap<>();
        for (int i = 0; i < e; i++) {
            int hRoot = find(parent, 2 * i);
            int tRoot = find(parent, 2 * i + 1);
            Integer prev = firstStrokeInCluster.putIfAbsent(hRoot, i);
            if (prev != null) union(strokeParent, i, prev);
            if (tRoot != hRoot) {
                prev = firstStrokeInCluster.putIfAbsent(tRoot, i);
                if (prev != null) union(strokeParent, i, prev);
            }
        }
        Set<Integer> cSet = new HashSet<>();
        for (int i = 0; i < e; i++) cSet.add(find(strokeParent, i));
        int c = cSet.size();

        int cycles = e - v + c;
        return Math.max(0, cycles);
    }

    /**
     * Tolerance gate: {@code |candidate.closedLoopCount − template.closedLoopCount| ≤ tolerance}.
     * Splits the sigil set cleanly — Air/Crush/Column have 0 loops, Fire/Light/Bolt
     * have 1, Collection has 2 — and rejects across-class mismatches.
     */
    public static boolean loopCountPasses(SigilMetrics candidate, SigilMetrics template, int tolerance) {
        int diff = candidate.closedLoopCount() - template.closedLoopCount();
        if (diff < 0) diff = -diff;
        return diff <= tolerance;
    }

    // ── 2. Ink density gate / penalty ───────────────────────────────────────────

    /**
     * Relative deviation of candidate ink density from template ink density:
     * {@code |cand - tmpl| / tmpl}. Use {@link #inkDensityPasses} for a hard gate
     * or {@link #inkDensityScoreMultiplier} for a soft penalty.
     */
    public static float inkDensityDeviation(SigilMetrics candidate, SigilMetrics template) {
        float tmpl = Math.max(template.inkDensity(), EPS);
        float diff = candidate.inkDensity() - tmpl;
        return (diff < 0f ? -diff : diff) / tmpl;
    }

    /**
     * @param maxRelDeviation pass if the deviation is at most this fraction
     *                        (e.g. {@code 0.25} = within ±25% of template density)
     */
    public static boolean inkDensityPasses(SigilMetrics candidate, SigilMetrics template, float maxRelDeviation) {
        return inkDensityDeviation(candidate, template) <= maxRelDeviation;
    }

    /**
     * Soft penalty variant: linearly ramps from {@code 1.0} (perfect density
     * match) to {@code 0.0} at deviation ≥ {@code zeroAt}. Multiply the chamfer
     * score by the return value to fold ink density into the final score.
     */
    public static float inkDensityScoreMultiplier(SigilMetrics candidate, SigilMetrics template, float zeroAt) {
        float dev = inkDensityDeviation(candidate, template);
        if (dev <= 0f) return 1f;
        if (dev >= zeroAt) return 0f;
        return 1f - dev / zeroAt;
    }

    // ── 3. 3×3 grid similarity (post-filter) ────────────────────────────────────

    /**
     * Histogram intersection over total, computed on normalized (per-histogram)
     * mass so the two clouds can have different point counts.
     *
     * <p>Returns a similarity in {@code [0, 1]}: {@code 1.0} = identical spatial
     * distribution, {@code 0.0} = disjoint quadrants. The standard L1 distance
     * between two probability vectors lives in {@code [0, 2]}; we map it to
     * similarity via {@code 1 − L1/2}.</p>
     */
    public static float gridSimilarity(int[] a, int[] b) {
        int totalA = 0, totalB = 0;
        for (int i = 0; i < 9; i++) { totalA += a[i]; totalB += b[i]; }
        if (totalA == 0 || totalB == 0) return 0f;
        float invA = 1f / totalA;
        float invB = 1f / totalB;
        float l1 = 0f;
        for (int i = 0; i < 9; i++) {
            float diff = a[i] * invA - b[i] * invB;
            l1 += (diff < 0f) ? -diff : diff;
        }
        return 1f - l1 * 0.5f;
    }

    // ── 4. Zero-length stroke injection (input fixup) ───────────────────────────

    /**
     * Detects strokes whose path length is below {@code dotRadius} (treated as
     * "taps" / dots) and replaces each with a {@code circlePointCount}-vertex
     * regular polygon centered on the stroke's centroid, with radius
     * {@code dotRadius}. Non-dot strokes pass through unchanged in their
     * original order.
     *
     * <p>Without this, a tap submitted to the $P+ resampler degenerates: the
     * resampler measures zero stroke length, divides by it for the per-stroke
     * interval, and emits N duplicated points at one coordinate — which has a
     * pathological turning angle and a singular point cloud.</p>
     *
     * <p>Choice of {@code dotRadius} depends on the caller's coordinate space.
     * On a 512px canvas, {@code 4f} is reasonable. In normalized [0,1] space,
     * {@code 0.01f} is reasonable. The radius doubles as the dot-detection
     * threshold: any stroke shorter than {@code dotRadius} is treated as a dot.</p>
     *
     * @param input             raw stroke points (any coordinate space)
     * @param dotRadius         strokes shorter than this become dots; injected
     *                          circles use this as their radius
     * @param circlePointCount  ≥ 3; how many points form the injected ring
     * @return a new list with dots expanded, or the input list itself when
     *         nothing needed expanding (zero-copy fast path)
     */
    public static List<Point> injectDotsIfNeeded(List<Point> input, float dotRadius, int circlePointCount) {
        if (input.isEmpty() || dotRadius <= 0f || circlePointCount < 3) return input;

        // Group by strokeID, preserving stroke order.
        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (Point p : input) {
            byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        }

        // Cheap detect: scan once, only allocate the output if there's actually a dot.
        boolean anyDot = false;
        for (List<Point> stroke : byStroke.values()) {
            if (strokeLength(stroke) < dotRadius) { anyDot = true; break; }
        }
        if (!anyDot) return input;

        // Precompute the unit circle once so we don't call sin/cos per stroke.
        float[] unitX = new float[circlePointCount];
        float[] unitY = new float[circlePointCount];
        double twoPiOverN = 2.0 * Math.PI / circlePointCount;
        for (int i = 0; i < circlePointCount; i++) {
            double theta = i * twoPiOverN;
            unitX[i] = (float) Math.cos(theta);
            unitY[i] = (float) Math.sin(theta);
        }

        List<Point> out = new ArrayList<>(input.size() + byStroke.size() * circlePointCount);
        for (Map.Entry<Integer, List<Point>> e : byStroke.entrySet()) {
            int strokeID = e.getKey();
            List<Point> stroke = e.getValue();
            if (strokeLength(stroke) >= dotRadius) {
                out.addAll(stroke);
                continue;
            }
            // Inject a circle centered on the stroke's centroid.
            float cx = 0f, cy = 0f;
            int n = stroke.size();
            for (Point p : stroke) {
                cx += p.x();
                cy += p.y();
            }
            float invN = 1f / n;
            cx *= invN;
            cy *= invN;
            for (int i = 0; i < circlePointCount; i++) {
                out.add(new Point(cx + dotRadius * unitX[i], cy + dotRadius * unitY[i], strokeID));
            }
        }
        return out;
    }

    // ── Union-find (path-compressed) for the loop-counting graph ────────────────

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]; // path compression (one-step splay)
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    /** Sum of segment distances within a single stroke (intra-stroke only). */
    private static float strokeLength(List<Point> stroke) {
        int n = stroke.size();
        if (n < 2) return 0f;
        double total = 0.0;
        for (int i = 1; i < n; i++) {
            Point a = stroke.get(i - 1);
            Point b = stroke.get(i);
            float dx = a.x() - b.x();
            float dy = a.y() - b.y();
            total += Math.sqrt((double) dx * dx + (double) dy * dy);
        }
        return (float) total;
    }
}
