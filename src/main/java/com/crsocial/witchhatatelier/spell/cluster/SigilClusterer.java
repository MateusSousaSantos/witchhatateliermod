package com.crsocial.witchhatatelier.spell.cluster;

import com.crsocial.witchhatatelier.spell.geometry.ConvexHull;
import com.crsocial.witchhatatelier.spell.geometry.SegmentMath;
import com.crsocial.witchhatatelier.spell.recognition.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups raw strokes into spatially-related {@link SigilCluster}s using
 * pure geometric topology (spec: §3 "Refined Sigil Grouping").
 *
 * <ol>
 *   <li><b>Micro-snap</b> — merge strokes whose raw points come within
 *       {@code microMergeRadius} of each other (fixes near-misses).</li>
 *   <li><b>Convex hull per cluster</b>.</li>
 *   <li><b>Macro-merge</b> — merge clusters whose hulls intersect or whose
 *       hull edges come within {@code macroMergeRadius}.</li>
 *   <li><b>Iterate</b> until stable.</li>
 * </ol>
 */
public final class SigilClusterer {

    private SigilClusterer() {}

    public static List<SigilCluster> cluster(List<List<Point>> strokes,
                                             float microMergeRadius,
                                             float macroMergeRadius) {
        if (strokes.isEmpty()) return List.of();

        // Working clusters; each starts as a single stroke.
        List<List<List<Point>>> clusters = new ArrayList<>();
        for (List<Point> s : strokes) {
            if (s.isEmpty()) continue;
            List<List<Point>> single = new ArrayList<>();
            single.add(s);
            clusters.add(single);
        }

        // 1. Micro-snap: merge pairs with any-point proximity ≤ microMergeRadius.
        mergeWhile(clusters, (a, b) -> anyPointWithin(a, b, microMergeRadius));

        // 3+4. Macro-merge: merge by hull intersection or hull-edge proximity, iterate.
        mergeWhile(clusters, (a, b) -> hullsTouchOrClose(a, b, macroMergeRadius));

        List<SigilCluster> out = new ArrayList<>(clusters.size());
        for (var c : clusters) out.add(new SigilCluster(c));
        return out;
    }

    // ── Merge loop ───────────────────────────────────────────────────────────────

    private interface PairTest {
        boolean shouldMerge(List<List<Point>> a, List<List<Point>> b);
    }

    private static void mergeWhile(List<List<List<Point>>> clusters, PairTest test) {
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    if (test.shouldMerge(clusters.get(i), clusters.get(j))) {
                        clusters.get(i).addAll(clusters.get(j));
                        clusters.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
    }

    // ── Predicates ───────────────────────────────────────────────────────────────

    private static boolean anyPointWithin(List<List<Point>> a, List<List<Point>> b, float r) {
        float rSq = r * r;
        for (List<Point> sa : a) {
            for (Point pa : sa) {
                for (List<Point> sb : b) {
                    for (Point pb : sb) {
                        if (SegmentMath.distanceSq(pa, pb) <= rSq) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hullsTouchOrClose(List<List<Point>> a, List<List<Point>> b, float r) {
        List<Point> hullA = ConvexHull.compute(flatten(a));
        List<Point> hullB = ConvexHull.compute(flatten(b));
        if (hullA.size() < 2 || hullB.size() < 2) return false;
        // Degenerate < 3-vertex "hulls" (collinear points) — fall back to polygonDistance which
        // handles the segment case via its edge loop.
        return SegmentMath.polygonDistance(hullA, hullB) <= r;
    }

    private static List<Point> flatten(List<List<Point>> strokes) {
        List<Point> all = new ArrayList<>();
        for (List<Point> s : strokes) all.addAll(s);
        return all;
    }
}
