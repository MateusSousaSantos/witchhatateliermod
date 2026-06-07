package com.crsocial.witchhatatelier.spell.trigger;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements spec §2 "Trigger Phase":
 *
 * <ol>
 *   <li><b>Endpoint stitching</b> — strokes whose head/tail land within
 *       {@code snapEpsilon} merge into a Stroke Chain.</li>
 *   <li><b>Closure</b> — the chain, ordered into one polyline, must form a closed
 *       loop: it sweeps a near-full winding angle around its own centroid, its
 *       endpoints meet within a scale-relative gap, and it is roughly round. This
 *       geometric test replaces both the old absolute endpoint-gap check and the
 *       {@code $P+} ring-template validation, which were fragile for hand-drawn
 *       circles (see {@link #isClosedLoop}).</li>
 *   <li><b>Encapsulation</b> — the chain's bounding box must wholly contain
 *       at least one other stroke.</li>
 * </ol>
 *
 * <p>Inputs are kept generic ({@link Vector2f}) so this class is callable from
 * both the canvas screen and the server pipeline.</p>
 */
public final class TriggerEvaluator {

    /** Identified activation ring. */
    public record TriggerResult(List<Integer> ringStrokeIds,
                                List<Integer> enclosedStrokeIds) {}

    private TriggerEvaluator() {}

    /**
     * @param strokes      all committed canvas strokes in canvas-space pixels
     * @param snapEpsilon  endpoint-stitching radius (canvas pixels)
     * @param canvasW      canvas width in canvas-space pixels (used for min-ring scaling)
     * @param canvasH      canvas height in canvas-space pixels (used for min-ring scaling)
     */
    public static Optional<TriggerResult> evaluate(List<List<Vector2f>> strokes,
                                                   float snapEpsilon,
                                                   float canvasW,
                                                   float canvasH) {
        int n = strokes.size();
        if (n == 0) return Optional.empty();

        // ── Endpoint connectivity ───────────────────────────────────────────────
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // Union strokes whose endpoints stitch together into the same chain.
        for (int i = 0; i < n; i++) {
            List<Vector2f> si = strokes.get(i);
            if (si.isEmpty()) continue;
            Vector2f hi = si.getFirst();
            Vector2f ti = si.getLast();
            for (int j = i + 1; j < n; j++) {
                List<Vector2f> sj = strokes.get(j);
                if (sj.isEmpty()) continue;
                Vector2f hj = sj.getFirst();
                Vector2f tj = sj.getLast();

                if (within(hi, hj, snapEpsilon) || within(hi, tj, snapEpsilon)
                        || within(ti, hj, snapEpsilon) || within(ti, tj, snapEpsilon)) {
                    union(parent, i, j);
                }
            }
        }

        // Group strokes by connected component (= stroke chain).
        Map<Integer, List<Integer>> chains = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            chains.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }

        // ── Evaluate every chain for closure + encapsulation ────────────────────
        float minArea       = Config.MIN_RING_AREA_FRACTION.get().floatValue() * canvasW * canvasH;
        float minWinding    = (float) Math.toRadians(Config.RING_MIN_WINDING_DEGREES.get());
        float gapFraction   = Config.RING_CLOSURE_GAP_FRACTION.get().floatValue();
        float maxRadialDev  = Config.RING_MAX_RADIAL_DEVIATION.get().floatValue();

        for (List<Integer> chain : chains.values()) {
            // ── Gate 0: geometric closure (winding + gap + roundness) ───────────
            List<Vector2f> path = orderedChainPath(strokes, chain, snapEpsilon);
            if (!isClosedLoop(path, minWinding, gapFraction, maxRadialDev)) continue;

            float[] bbox = boundingBox(strokes, chain);

            // ── Gate 1: minimum ring size (scales with canvas) ──────────────────
            float ringArea = (bbox[2] - bbox[0]) * (bbox[3] - bbox[1]);
            if (ringArea < minArea) {
                WitchHatAtelierMod.LOGGER.debug(
                        "[Trigger] Closed ring rejected — too small (area {} < min {}). Draw a bigger circle.",
                        String.format(java.util.Locale.ROOT, "%.0f", ringArea),
                        String.format(java.util.Locale.ROOT, "%.0f", minArea));
                continue;
            }

            // ── Gate 2: encapsulation ────────────────────────────────────────────
            List<Integer> enclosed = findEnclosed(strokes, chain, bbox);
            if (enclosed.isEmpty()) {
                WitchHatAtelierMod.LOGGER.debug(
                        "[Trigger] Closed ring rejected — nothing fully inside it. The ring must "
                                + "enclose the sigil's strokes (every point inside the ring's bounds).");
                continue;
            }

            return Optional.of(new TriggerResult(chain, enclosed));
        }
        return Optional.empty();
    }

    // ── Geometric closure (winding + gap + roundness) ─────────────────────────────

    /**
     * Reconstructs the stroke chain as a single ordered polyline so the winding angle
     * follows the drawn path. A single-stroke chain (the common ring case) is returned
     * as-is. Multi-stroke chains are greedily stitched head-to-tail using {@code snapEpsilon}
     * adjacency, flipping a stroke when its tail (not head) meets the current path end. If
     * stitching can't consume every stroke (branching / dirty chain), falls back to the old
     * free-endpoint cycle test by returning {@code null} — the caller then treats the chain
     * as closed only if every endpoint was stitched.
     */
    private static List<Vector2f> orderedChainPath(List<List<Vector2f>> strokes,
                                                   List<Integer> chain,
                                                   float snapEpsilon) {
        if (chain.size() == 1) {
            return strokes.get(chain.getFirst());
        }

        List<Integer> remaining = new ArrayList<>(chain);
        List<Vector2f> path = new ArrayList<>();
        // Seed with the first non-empty stroke.
        int seed = -1;
        for (int i = 0; i < remaining.size(); i++) {
            if (!strokes.get(remaining.get(i)).isEmpty()) { seed = i; break; }
        }
        if (seed < 0) return List.of();
        path.addAll(strokes.get(remaining.remove(seed)));

        while (!remaining.isEmpty()) {
            Vector2f end = path.getLast();
            int pick = -1;
            boolean flip = false;
            for (int i = 0; i < remaining.size(); i++) {
                List<Vector2f> s = strokes.get(remaining.get(i));
                if (s.isEmpty()) { remaining.remove(i); i--; continue; }
                if (within(end, s.getFirst(), snapEpsilon)) { pick = i; flip = false; break; }
                if (within(end, s.getLast(),  snapEpsilon)) { pick = i; flip = true;  break; }
            }
            if (pick < 0) return null; // can't continue the chain cleanly — fall back
            List<Vector2f> s = strokes.get(remaining.remove(pick));
            if (flip) {
                for (int k = s.size() - 1; k >= 0; k--) path.add(s.get(k));
            } else {
                path.addAll(s);
            }
        }
        return path;
    }

    /**
     * Geometric "is this a closed loop?" test, replacing both the absolute endpoint-gap
     * check and the {@code $P+} ring-template match. A path is a ring when, taken around
     * its own centroid, it:
     * <ol>
     *   <li><b>winds</b> a near-full turn — {@code |Σ Δθ| ≥ minWinding} (a C-shape stays
     *       below this even though its ends may be close);</li>
     *   <li><b>closes</b> — first/last points meet within {@code gapFraction × bbox-diagonal}
     *       (size-independent);</li>
     *   <li><b>is roughly round</b> — radial deviation {@code stdev/mean ≤ maxRadialDev}
     *       (rejects degenerate near-line loops; ellipses and squares still pass).</li>
     * </ol>
     *
     * <p>{@code path == null} signals {@link #orderedChainPath} hit a branching / dirty chain
     * it couldn't order into a single polyline — the old free-endpoint test rejected those
     * too (more than two unstitched endpoints), so we conservatively reject here as well.</p>
     */
    private static boolean isClosedLoop(List<Vector2f> path,
                                        float minWinding,
                                        float gapFraction,
                                        float maxRadialDev) {
        if (path == null || path.size() < 3) return false;

        // Centroid.
        float cx = 0f, cy = 0f;
        for (Vector2f p : path) { cx += p.x; cy += p.y; }
        cx /= path.size();
        cy /= path.size();

        // Winding angle around the centroid + radial mean.
        double winding = 0.0;
        double radSum = 0.0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float prevX = path.getFirst().x - cx, prevY = path.getFirst().y - cy;
        for (Vector2f p : path) {
            float vx = p.x - cx, vy = p.y - cy;
            // signed angle from previous radial vector to this one, in (-π, π]
            float cross = prevX * vy - prevY * vx;
            float dot   = prevX * vx + prevY * vy;
            if (cross != 0f || dot != 0f) winding += Math.atan2(cross, dot);
            prevX = vx; prevY = vy;
            radSum += Math.sqrt((double) vx * vx + (double) vy * vy);
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }
        if (Math.abs(winding) < minWinding) return false;

        // Scale-relative endpoint gap.
        float diag = (float) Math.sqrt((double)(maxX - minX) * (maxX - minX)
                + (double)(maxY - minY) * (maxY - minY));
        Vector2f first = path.getFirst();
        Vector2f last  = path.getLast();
        float gap = (float) Math.sqrt((double)(first.x - last.x) * (first.x - last.x)
                + (double)(first.y - last.y) * (first.y - last.y));
        if (gap > gapFraction * diag) return false;

        // Roundness: radial deviation relative to mean radius.
        double meanRad = radSum / path.size();
        if (meanRad <= 1e-4) return false;
        double varSum = 0.0;
        for (Vector2f p : path) {
            double r = Math.sqrt((double)(p.x - cx) * (p.x - cx) + (double)(p.y - cy) * (p.y - cy));
            double d = r - meanRad;
            varSum += d * d;
        }
        double radialDev = Math.sqrt(varSum / path.size()) / meanRad;
        return radialDev <= maxRadialDev;
    }

    // ── Bounding box + enclosure ─────────────────────────────────────────────────

    private static float[] boundingBox(List<List<Vector2f>> strokes, List<Integer> chain) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int idx : chain) {
            for (Vector2f p : strokes.get(idx)) {
                if (p.x < minX) minX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.x > maxX) maxX = p.x;
                if (p.y > maxY) maxY = p.y;
            }
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private static List<Integer> findEnclosed(List<List<Vector2f>> strokes,
                                              List<Integer> chain,
                                              float[] bbox) {
        List<Integer> out = new ArrayList<>();
        outer:
        for (int k = 0; k < strokes.size(); k++) {
            if (chain.contains(k)) continue;
            List<Vector2f> s = strokes.get(k);
            if (s.isEmpty()) continue;
            for (Vector2f p : s) {
                if (p.x < bbox[0] || p.x > bbox[2] || p.y < bbox[1] || p.y > bbox[3]) continue outer;
            }
            out.add(k);
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static boolean within(Vector2f a, Vector2f b, float eps) {
        float dx = a.x - b.x, dy = a.y - b.y;
        return dx * dx + dy * dy <= eps * eps;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
