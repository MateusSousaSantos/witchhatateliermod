package com.crsocial.witchhatatelier.spell.geometry;

import com.crsocial.witchhatatelier.spell.recognition.Point;

import java.util.List;

/**
 * Pure-math helpers used by clustering and trigger evaluation.
 *
 * <p>All routines treat {@link Point} as a 2D coordinate; the {@code strokeID}
 * field is ignored.</p>
 */
public final class SegmentMath {

    private SegmentMath() {}

    // ── Point ↔ Point ────────────────────────────────────────────────────────────

    public static float distance(Point a, Point b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static float distanceSq(Point a, Point b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    // ── Point ↔ Segment ──────────────────────────────────────────────────────────

    /** Shortest distance from point {@code p} to the segment {@code (a, b)}. */
    public static float pointToSegment(Point p, Point a, Point b) {
        float vx = b.x() - a.x();
        float vy = b.y() - a.y();
        float wx = p.x() - a.x();
        float wy = p.y() - a.y();

        float c1 = vx * wx + vy * wy;
        if (c1 <= 0) return distance(p, a);

        float c2 = vx * vx + vy * vy;
        if (c2 <= c1) return distance(p, b);

        float t = c1 / c2;
        float projX = a.x() + t * vx;
        float projY = a.y() + t * vy;
        float dx = p.x() - projX;
        float dy = p.y() - projY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ── Segment ↔ Segment ────────────────────────────────────────────────────────

    /** Whether closed segments {@code (a, b)} and {@code (c, d)} intersect (including endpoint touches). */
    public static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        double d1 = ccw(c, d, a);
        double d2 = ccw(c, d, b);
        double d3 = ccw(a, b, c);
        double d4 = ccw(a, b, d);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
         && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;

        if (d1 == 0 && onSegment(c, d, a)) return true;
        if (d2 == 0 && onSegment(c, d, b)) return true;
        if (d3 == 0 && onSegment(a, b, c)) return true;
        return d4 == 0 && onSegment(a, b, d);
    }

    /** Shortest distance between two closed segments. */
    public static float segmentDistance(Point a, Point b, Point c, Point d) {
        if (segmentsIntersect(a, b, c, d)) return 0f;
        return Math.min(
                Math.min(pointToSegment(a, c, d), pointToSegment(b, c, d)),
                Math.min(pointToSegment(c, a, b), pointToSegment(d, a, b)));
    }

    // ── Polygon ──────────────────────────────────────────────────────────────────

    /**
     * Whether the closed polygon {@code poly} contains the point {@code p}.
     * Standard ray casting; works for any simple polygon (does not require convexity).
     */
    public static boolean polygonContains(List<Point> poly, Point p) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = poly.get(i);
            Point b = poly.get(j);
            boolean crosses = ((a.y() > p.y()) != (b.y() > p.y()))
                    && (p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y() + 1e-12f) + a.x());
            if (crosses) inside = !inside;
        }
        return inside;
    }

    /** Whether two polygons (in vertex order, not closed) intersect or one contains the other. */
    public static boolean polygonsIntersect(List<Point> a, List<Point> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        // Edge crossing
        int na = a.size();
        int nb = b.size();
        for (int i = 0; i < na; i++) {
            Point a0 = a.get(i);
            Point a1 = a.get((i + 1) % na);
            for (int j = 0; j < nb; j++) {
                Point b0 = b.get(j);
                Point b1 = b.get((j + 1) % nb);
                if (segmentsIntersect(a0, a1, b0, b1)) return true;
            }
        }
        // One inside the other
        return polygonContains(a, b.getFirst()) || polygonContains(b, a.getFirst());
    }

    /**
     * Minimum distance between the boundaries of two polygons.
     * Returns 0 if they intersect.
     */
    public static float polygonDistance(List<Point> a, List<Point> b) {
        if (polygonsIntersect(a, b)) return 0f;
        float best = Float.MAX_VALUE;
        int na = a.size();
        int nb = b.size();
        for (int i = 0; i < na; i++) {
            Point a0 = a.get(i);
            Point a1 = a.get((i + 1) % na);
            for (int j = 0; j < nb; j++) {
                Point b0 = b.get(j);
                Point b1 = b.get((j + 1) % nb);
                float d = segmentDistance(a0, a1, b0, b1);
                if (d < best) best = d;
            }
        }
        return best;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static double ccw(Point a, Point b, Point c) {
        return (b.x() - a.x()) * (double) (c.y() - a.y())
             - (b.y() - a.y()) * (double) (c.x() - a.x());
    }

    private static boolean onSegment(Point a, Point b, Point p) {
        return Math.min(a.x(), b.x()) <= p.x() && p.x() <= Math.max(a.x(), b.x())
            && Math.min(a.y(), b.y()) <= p.y() && p.y() <= Math.max(a.y(), b.y());
    }
}
