package com.crsocial.witchhatatelier.spell.recognition;

import java.util.List;

/**
 * Per-sigil cached geometry. Computed once when a template is loaded and once
 * per candidate inside {@link PointCloudPreprocessor#process}. Holds everything
 * the {@link SigilFilters} pipeline needs so the hot loop in
 * {@link PDollarPlusRecognizer#match} is O(1) per template for the pre-filter
 * stages — no point iteration at match time.
 *
 * <p><b>Coordinate spaces:</b> raw-frame metrics (width, height, aspect ratio,
 * diagonal, stroke length, ink density) are scale-invariant, so they're computed
 * from the raw cloud and survive the {@code scale → translate → resample} pipeline
 * unchanged. The {@code gridHistogram} uses the <i>processed</i> cloud, which is
 * the only fair frame for comparing spatial distributions (raw clouds may differ
 * in canvas size, normalization, etc.).</p>
 *
 * <p><b>Mutability:</b> {@code gridHistogram} is a primitive {@code int[]} for
 * cache locality. Callers must treat it as read-only.</p>
 */
public record SigilMetrics(
        float width,
        float height,
        float aspectRatio,
        float diagonal,
        float totalStrokeLength,
        float inkDensity,
        int dotCount,
        int closedLoopCount,
        int[] gridHistogram
) {

    private static final float EPS = 1e-6f;

    /** Empty/degenerate sentinel — used when a cloud has zero points. */
    public static final SigilMetrics EMPTY =
            new SigilMetrics(0f, 0f, 1f, 0f, 0f, 0f, 0, 0, new int[9]);

    /**
     * Computes all cached metrics for one sigil.
     *
     * @param rawCloud         cloud in its original coordinate frame (used for
     *                         width/height/length so the values are physically
     *                         meaningful rather than reflecting our internal scale)
     * @param processedCloud   cloud after the full preprocessing pipeline (used
     *                         for the 3×3 grid histogram so two clouds are
     *                         binned in comparable normalized frames)
     * @param dotCount         count of zero-length 'tap' strokes detected
     *                         <i>before</i> dot injection — passed in because
     *                         after injection the strokes are full rings and
     *                         the count would always be zero
     * @param closureFraction  endpoint-stitching radius for closed-loop counting,
     *                         expressed as a fraction of the bounding-box
     *                         diagonal
     */
    public static SigilMetrics compute(PointCloud rawCloud, PointCloud processedCloud,
                                       int dotCount, float closureFraction) {
        List<Point> raw = rawCloud.points();
        if (raw.isEmpty()) return EMPTY;

        // ── Bounding box (single pass) ───────────────────────────────────────────
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (Point p : raw) {
            float x = p.x(), y = p.y();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        float w = Math.max(EPS, maxX - minX);
        float h = Math.max(EPS, maxY - minY);
        float aspect = w / h;
        float diag = (float) Math.sqrt((double) w * w + (double) h * h);

        // ── Total stroke path length (single pass, intra-stroke only) ───────────
        double lengthAcc = 0.0;
        for (int i = 1, n = raw.size(); i < n; i++) {
            Point a = raw.get(i - 1);
            Point b = raw.get(i);
            if (a.strokeID() != b.strokeID()) continue;
            float dx = a.x() - b.x();
            float dy = a.y() - b.y();
            lengthAcc += Math.sqrt((double) dx * dx + (double) dy * dy);
        }
        float length = (float) lengthAcc;
        float density = length / Math.max(EPS, diag);

        // ── Closed-loop count via Euler formula on the endpoint graph ───────────
        float closureEpsilon = closureFraction * diag;
        int loops = SigilFilters.countClosedLoops(raw, closureEpsilon);

        int[] grid = gridHistogram(processedCloud.points());
        return new SigilMetrics(w, h, aspect, diag, length, density,
                                dotCount, loops, grid);
    }

    /**
     * Bins points into a 3×3 grid laid over their own bounding box (each cloud
     * normalized to its own [0,1]² frame). Returns {@code int[9]} in
     * {@code row * 3 + col} order with the origin in the top-left.
     *
     * <p>Using each cloud's own bbox makes the comparison shape-relative rather
     * than canvas-relative, which is what the post-filter wants: "does the
     * spatial mass land in the same regions of the shape?"</p>
     */
    private static int[] gridHistogram(List<Point> pts) {
        int[] grid = new int[9];
        int n = pts.size();
        if (n == 0) return grid;

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (Point p : pts) {
            float x = p.x(), y = p.y();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        float w = Math.max(EPS, maxX - minX);
        float h = Math.max(EPS, maxY - minY);

        for (int i = 0; i < n; i++) {
            Point p = pts.get(i);
            // Map into [0, 3); clamp the right/bottom edge into cell 2
            int cx = (int) ((p.x() - minX) / w * 3f);
            int cy = (int) ((p.y() - minY) / h * 3f);
            if (cx > 2) cx = 2; else if (cx < 0) cx = 0;
            if (cy > 2) cy = 2; else if (cy < 0) cy = 0;
            grid[cy * 3 + cx]++;
        }
        return grid;
    }
}
