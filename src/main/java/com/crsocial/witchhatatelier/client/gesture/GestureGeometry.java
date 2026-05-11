package com.crsocial.witchhatatelier.client.gesture;

import org.joml.Vector2f;

import java.util.List;

/**
 * Utility methods for 2-D geometry on gesture point clouds.
 *
 * <p>All methods work in normalized [0,1]×[0,1] coordinate space unless otherwise
 * stated.</p>
 */
public final class GestureGeometry {

    private GestureGeometry() {}

    // ── Centroid ────────────────────────────────────────────────────────────────

    /**
     * Computes the centroid (arithmetic mean) of a gesture point cloud.
     *
     * @param points non-empty list of gesture points
     * @return a {@link Vector2f} with the average (x, y)
     * @throws IllegalArgumentException if {@code points} is empty
     */
    public static Vector2f computeCentroid(List<GesturePoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute centroid of an empty point cloud");
        }
        float sumX = 0, sumY = 0;
        for (GesturePoint p : points) {
            sumX += p.x();
            sumY += p.y();
        }
        return new Vector2f(sumX / points.size(), sumY / points.size());
    }

    // ── Point-in-polygon (ray-casting / even-odd rule) ─────────────────────────

    /**
     * Tests whether the point {@code (px, py)} lies inside a polygon defined by an
     * ordered list of vertices using the <em>ray-casting</em> (even-odd) algorithm.
     *
     * <p>The polygon is implicitly closed — an edge connects the last vertex back
     * to the first.</p>
     *
     * @param px         x-coordinate of the test point
     * @param py         y-coordinate of the test point
     * @param polygon    ordered vertices of the polygon
     * @return {@code true} if the point is inside the polygon
     */
    public static boolean isPointInPolygon(float px, float py, List<Vector2f> polygon) {
        if (polygon.size() < 3) return false;

        boolean inside = false;
        int n = polygon.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vector2f vi = polygon.get(i);
            Vector2f vj = polygon.get(j);

            // Does a horizontal ray from (px, py) going right cross edge (vi → vj)?
            if ((vi.y > py) != (vj.y > py)) {
                float xIntersect = vi.x + (py - vi.y) / (vj.y - vi.y) * (vj.x - vi.x);
                if (px < xIntersect) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /**
     * Overload that accepts separate x/y float arrays derived from
     * {@link GesturePoint} clouds (canvas-pixel or normalized coordinates).
     */

    public static boolean isPointInPolygon(float px, float py, float[] polyX, float[] polyY) {
        int n = polyX.length;
        if (n < 3) return false;

        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((polyY[i] > py) != (polyY[j] > py)) {
                float xIntersect = polyX[i] + (py - polyY[i]) / (polyY[j] - polyY[i]) * (polyX[j] - polyX[i]);
                if (px < xIntersect) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    // ── Bounding box size ────────────────────────────────────────────────────────

    /**
     * Computes the bounding-box diagonal length of a gesture point cloud.
     * This is a quick measure of how large the sigil was drawn on the canvas.
     * Returns {@code 0} if fewer than 2 points are provided.
     *
     * @param points the gesture point cloud (in any consistent coordinate space)
     * @return diagonal length (√((maxX−minX)² + (maxY−minY)²))
     */
    public static float computeBoundingBoxSize(List<GesturePoint> points) {
        if (points.size() < 2) return 0f;
        float minX =  Float.MAX_VALUE, minY =  Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (GesturePoint p : points) {
            if (p.x() < minX) minX = p.x();
            if (p.y() < minY) minY = p.y();
            if (p.x() > maxX) maxX = p.x();
            if (p.y() > maxY) maxY = p.y();
        }
        float dx = maxX - minX, dy = maxY - minY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ── PCA orientation ──────────────────────────────────────────────────────────

    /**
     * Computes the orientation angle (principal axis) of a gesture point cloud
     * using 2-D Principal Component Analysis (PCA).
     *
     * <p>Returns the angle of the dominant eigenvector of the covariance matrix,
     * in radians.  The result is approximately in [−π/2, π/2], where 0 means
     * the sigil's major spread is horizontal and π/2 means it is vertical.</p>
     *
     * <p>Returns {@code 0} if fewer than 2 points are provided.</p>
     *
     * @param points the gesture point cloud (in any consistent coordinate space)
     * @return orientation angle in radians
     */
    public static float computeOrientationAngle(List<GesturePoint> points) {
        if (points.size() < 2) return 0f;
        // Compute mean
        float meanX = 0f, meanY = 0f;
        for (GesturePoint p : points) { meanX += p.x(); meanY += p.y(); }
        meanX /= points.size();
        meanY /= points.size();
        // Compute 2×2 covariance matrix entries
        float cxx = 0f, cyy = 0f, cxy = 0f;
        for (GesturePoint p : points) {
            float dx = p.x() - meanX, dy = p.y() - meanY;
            cxx += dx * dx;
            cyy += dy * dy;
            cxy += dx * dy;
        }
        // Angle of principal component: θ = 0.5 * atan2(2·cxy, cxx − cyy)
        return (float) (0.5 * Math.atan2(2.0 * cxy, cxx - cyy));
    }

    /**
     * Convenience: convert a list of {@link GesturePoint} (e.g. a single stroke)
     * into a {@link Vector2f} polygon and run the containment test.
     */
    public static boolean isPointInGesturePolygon(float px, float py, List<GesturePoint> strokePoints) {
        if (strokePoints.size() < 3) return false;
        int n = strokePoints.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            GesturePoint pi = strokePoints.get(i);
            GesturePoint pj = strokePoints.get(j);
            if ((pi.y() > py) != (pj.y() > py)) {
                float xIntersect = pi.x() + (py - pi.y()) / (pj.y() - pi.y()) * (pj.x() - pi.x());
                if (px < xIntersect) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}

