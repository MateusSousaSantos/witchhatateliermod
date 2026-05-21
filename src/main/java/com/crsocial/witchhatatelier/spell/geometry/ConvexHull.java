package com.crsocial.witchhatatelier.spell.geometry;

import com.crsocial.witchhatatelier.spell.recognition.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Andrew's monotone chain convex hull. Operates on {@link Point} (strokeID is ignored).
 * Returns the hull vertices in counter-clockwise order (assuming standard math axes;
 * with y-down screen coords the winding is clockwise — that doesn't matter for the
 * containment / intersection helpers as long as winding is consistent).
 */
public final class ConvexHull {

    private ConvexHull() {}

    /**
     * Builds the convex hull of {@code pts}. The returned list does not repeat the first vertex.
     * Returns a copy of the input if fewer than 3 unique points exist.
     */
    public static List<Point> compute(List<Point> pts) {
        if (pts.size() < 3) return new ArrayList<>(pts);

        List<Point> sorted = new ArrayList<>(pts);
        sorted.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));

        int n = sorted.size();
        Point[] hull = new Point[2 * n];
        int k = 0;

        // Lower hull
        for (Point p : sorted) {
            while (k >= 2 && cross(hull[k - 2], hull[k - 1], p) <= 0) k--;
            hull[k++] = p;
        }
        // Upper hull
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            Point p = sorted.get(i);
            while (k >= lower && cross(hull[k - 2], hull[k - 1], p) <= 0) k--;
            hull[k++] = p;
        }

        List<Point> out = new ArrayList<>(k - 1);
        out.addAll(Arrays.asList(hull).subList(0, k - 1));
        return out;
    }

    private static double cross(Point o, Point a, Point b) {
        return (a.x() - o.x()) * (double) (b.y() - o.y())
             - (a.y() - o.y()) * (double) (b.x() - o.x());
    }
}
