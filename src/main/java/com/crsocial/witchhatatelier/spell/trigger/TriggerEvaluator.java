package com.crsocial.witchhatatelier.spell.trigger;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.PointCloudPreprocessor;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.recognition.Template;
import com.crsocial.witchhatatelier.spell.recognition.TemplateRegistry;
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
 *   <li><b>Closure</b> — the chain's ultimate head/tail must lie within
 *       {@code closureEpsilon}.</li>
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
     * @param closureEpsilon closure gap radius (canvas pixels)
     * @param canvasW      canvas width in canvas-space pixels (used for min-ring scaling)
     * @param canvasH      canvas height in canvas-space pixels (used for min-ring scaling)
     */
    public static Optional<TriggerResult> evaluate(List<List<Vector2f>> strokes,
                                                   float snapEpsilon,
                                                   float closureEpsilon,
                                                   float canvasW,
                                                   float canvasH) {
        int n = strokes.size();
        if (n == 0) return Optional.empty();

        // ── Endpoint connectivity ───────────────────────────────────────────────
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // degree[2k]   = #connections of stroke k's head
        // degree[2k+1] = #connections of stroke k's tail
        int[] degree = new int[n * 2];

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

                boolean connected = false;
                if (within(hi, hj, snapEpsilon)) { degree[2*i]++;   degree[2*j]++;   connected = true; }
                if (within(hi, tj, snapEpsilon)) { degree[2*i]++;   degree[2*j+1]++; connected = true; }
                if (within(ti, hj, snapEpsilon)) { degree[2*i+1]++; degree[2*j]++;   connected = true; }
                if (within(ti, tj, snapEpsilon)) { degree[2*i+1]++; degree[2*j+1]++; connected = true; }
                if (connected) union(parent, i, j);
            }
        }

        // Group strokes by connected component (= stroke chain).
        Map<Integer, List<Integer>> chains = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            chains.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }

        // ── Evaluate every chain for closure + encapsulation ────────────────────
        float minArea = Config.MIN_RING_AREA_FRACTION.get().floatValue() * canvasW * canvasH;

        for (List<Integer> chain : chains.values()) {
            if (!isClosed(strokes, chain, degree, closureEpsilon)) continue;

            float[] bbox = boundingBox(strokes, chain);

            // ── Gate 1: minimum ring size (scales with canvas) ──────────────────
            float ringArea = (bbox[2] - bbox[0]) * (bbox[3] - bbox[1]);
            if (ringArea < minArea) continue;

            // ── Gate 2: encapsulation ────────────────────────────────────────────
            List<Integer> enclosed = findEnclosed(strokes, chain, bbox);
            if (enclosed.isEmpty()) continue;

            // ── Gate 3: ring-shape template validation (optional) ────────────────
            List<Template> ringTemplates = TemplateRegistry.get().allRing();
            if (!ringTemplates.isEmpty() && !matchesRingTemplate(strokes, chain, ringTemplates)) {
                continue;
            }

            return Optional.of(new TriggerResult(chain, enclosed));
        }
        return Optional.empty();
    }

    // ── Ring template validation ─────────────────────────────────────────────────

    private static boolean matchesRingTemplate(List<List<Vector2f>> strokes,
                                               List<Integer> chain,
                                               List<Template> ringTemplates) {
        List<Point> pts = new ArrayList<>();
        int sid = 0;
        for (int idx : chain) {
            for (Vector2f p : strokes.get(idx)) pts.add(new Point(p.x, p.y, sid));
            sid++;
        }
        PointCloudPreprocessor.Processed proc =
                PointCloudPreprocessor.process(new PointCloud("ring_candidate", pts),
                        Config.RESAMPLE_N.get());
        PDollarPlusRecognizer recognizer =
                new PDollarPlusRecognizer(TemplateRegistry.get(),
                        Config.RECOGNITION_MIN_SCORE.get().floatValue());
        RecognitionResult r = recognizer.match(proc.cloud(), proc.indicativeAngle(), ringTemplates);
        return !RecognitionResult.UNKNOWN.equals(r.spellName());
    }

    // ── Closure check ────────────────────────────────────────────────────────────

    private static boolean isClosed(List<List<Vector2f>> strokes,
                                    List<Integer> chain,
                                    int[] degree,
                                    float closureEpsilon) {
        // Collect endpoints that are not stitched to any other stroke ("free" / "ultimate").
        List<Vector2f> free = new ArrayList<>(2);
        for (int idx : chain) {
            List<Vector2f> s = strokes.get(idx);
            if (s.isEmpty()) continue;
            if (degree[2*idx]     == 0) free.add(s.getFirst());
            if (degree[2*idx + 1] == 0) free.add(s.getLast());
            if (free.size() > 2) return false; // T-junction / branching — not a clean loop
        }
        if (free.isEmpty()) return true; // every endpoint stitched — fully closed cycle
        if (free.size() == 2) return within(free.get(0), free.get(1), closureEpsilon);
        return false;
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
