package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.spell.geometry.SegmentMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalization pipeline applied identically to both candidate sigils and
 * stored templates before $P+ matching:
 *
 * <ol>
 *   <li>Equidistant resampling to {@code N} points (per-stroke).</li>
 *   <li>Scale to a reference unit square.</li>
 *   <li>Translate so the centroid sits at {@code (0, 0)}.</li>
 *   <li>Compute the principal-axis indicative angle and rotate to canonical orientation.</li>
 *   <li>Compute the per-point normalized turning angle (the $P+ third channel).</li>
 * </ol>
 *
 * <p>The turning-angle step must come <i>after</i> resampling (it depends on
 * neighbor spacing) but is invariant under rotation, translation, and uniform
 * scaling — so its position in the pipeline relative to those steps is a matter
 * of convenience, not correctness.</p>
 */
public final class PointCloudPreprocessor {

    /** Output of {@link #process}: the normalized cloud plus its rotational signature. */
    public record Processed(PointCloud cloud, float indicativeAngle) {}

    private PointCloudPreprocessor() {}

    // ── 1. Equidistant resampling ────────────────────────────────────────────────

    /**
     * Per-stroke equidistant resampling so the cloud ends up with approximately
     * {@code n} points across all strokes, weighted by per-stroke length.
     *
     * <p>The total point count is guaranteed to be ≥ 1 and is exactly {@code n}
     * when every stroke contributes at least one point and rounding lines up.</p>
     */
    public static PointCloud resample(PointCloud in, int n) {
        if (n < 2) throw new IllegalArgumentException("n must be >= 2");
        if (in.points().isEmpty()) return new PointCloud(in.name(), List.of());

        Map<Integer, List<Point>> byStroke = groupByStroke(in.points());

        double totalLength = 0.0;
        Map<Integer, Double> lengths = new LinkedHashMap<>();
        for (var e : byStroke.entrySet()) {
            double len = pathLength(e.getValue());
            lengths.put(e.getKey(), len);
            totalLength += len;
        }

        List<Point> out = new ArrayList<>(n);

        if (totalLength == 0.0) {
            // Degenerate: every stroke is a single repeated point. Emit one point per stroke.
            for (var e : byStroke.entrySet()) {
                Point p = e.getValue().getFirst();
                out.add(new Point(p.x(), p.y(), e.getKey()));
            }
            return new PointCloud(in.name(), out);
        }

        int remaining = n;
        int strokesLeft = byStroke.size();
        for (var e : byStroke.entrySet()) {
            int strokeID = e.getKey();
            List<Point> stroke = e.getValue();
            double strokeLen = lengths.get(strokeID);

            int strokeN;
            if (strokesLeft == 1) {
                strokeN = remaining;
            } else if (totalLength > 0) {
                strokeN = Math.max(2, (int) Math.round(n * (strokeLen / totalLength)));
                strokeN = Math.min(strokeN, remaining - 2 * (strokesLeft - 1));
                strokeN = Math.max(strokeN, 2);
            } else {
                strokeN = 2;
            }
            out.addAll(resampleStroke(stroke, strokeN, strokeID));
            remaining -= strokeN;
            strokesLeft--;
        }
        return new PointCloud(in.name(), out);
    }

    private static List<Point> resampleStroke(List<Point> stroke, int n, int strokeID) {
        if (stroke.size() == 1 || n == 1) {
            Point p = stroke.getFirst();
            return List.of(new Point(p.x(), p.y(), strokeID));
        }
        double total = pathLength(stroke);
        if (total == 0.0) {
            // All points identical — emit n copies.
            List<Point> dup = new ArrayList<>(n);
            Point p = stroke.getFirst();
            for (int i = 0; i < n; i++) dup.add(new Point(p.x(), p.y(), strokeID));
            return dup;
        }

        double interval = total / (n - 1);
        List<Point> out = new ArrayList<>(n);
        Point first = stroke.getFirst();
        out.add(new Point(first.x(), first.y(), strokeID));

        double accumulated = 0.0;
        for (int i = 1; i < stroke.size(); i++) {
            Point a = stroke.get(i - 1);
            Point b = stroke.get(i);
            double segLen = SegmentMath.distance(a, b);
            if (segLen == 0.0) continue;

            while (accumulated + segLen >= interval && out.size() < n - 1) {
                double need = interval - accumulated;
                double t = need / segLen;
                float nx = (float) (a.x() + t * (b.x() - a.x()));
                float ny = (float) (a.y() + t * (b.y() - a.y()));
                out.add(new Point(nx, ny, strokeID));
                // Continue along the same segment from the inserted point.
                a = new Point(nx, ny, strokeID);
                segLen -= need;
                accumulated = 0.0;
            }
            accumulated += segLen;
        }
        // Pad to exactly n with the last input point (handles floating-point drift).
        while (out.size() < n) {
            Point last = stroke.getLast();
            out.add(new Point(last.x(), last.y(), strokeID));
        }
        return out;
    }

    private static double pathLength(List<Point> stroke) {
        double total = 0.0;
        for (int i = 1; i < stroke.size(); i++) {
            total += SegmentMath.distance(stroke.get(i - 1), stroke.get(i));
        }
        return total;
    }

    // ── 2. Scale to reference square ─────────────────────────────────────────────

    public static PointCloud scaleToReferenceSquare(PointCloud in) {
        if (in.points().isEmpty()) return in;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Point p : in.points()) {
            if (p.x() < minX) minX = p.x();
            if (p.y() < minY) minY = p.y();
            if (p.x() > maxX) maxX = p.x();
            if (p.y() > maxY) maxY = p.y();
        }
        float s = Math.max(maxX - minX, maxY - minY);
        if (s == 0f) return in; // single point — nothing to scale
        List<Point> out = new ArrayList<>(in.points().size());
        for (Point p : in.points()) {
            out.add(new Point(p.x() / s, p.y() / s, p.strokeID()));
        }
        return new PointCloud(in.name(), out);
    }

    // ── 3. Translate to origin ───────────────────────────────────────────────────

    public static PointCloud translateToOrigin(PointCloud in) {
        if (in.points().isEmpty()) return in;
        double cx = 0, cy = 0;
        for (Point p : in.points()) { cx += p.x(); cy += p.y(); }
        cx /= in.points().size();
        cy /= in.points().size();
        List<Point> out = new ArrayList<>(in.points().size());
        for (Point p : in.points()) {
            out.add(new Point((float) (p.x() - cx), (float) (p.y() - cy), p.strokeID()));
        }
        return new PointCloud(in.name(), out);
    }

    // ── 4. Indicative angle ──────────────────────────────────────────────────────

    /**
     * Principal-axis angle of the (already centered) cloud, computed via 2×2
     * covariance eigendecomposition. Uses every point, so it is stable under
     * stroke-order changes and resampling noise — unlike a centroid→first-point
     * angle, which collapses for symmetric or multi-stroke shapes.
     *
     * <p>The principal axis is a <i>line</i>, so this returns an angle in
     * (-π/2, π/2); the residual 180° ambiguity is resolved by the recognizer
     * trying both flips at match time.</p>
     */
    public static float indicativeAngle(PointCloud in) {
        if (in.points().size() < 2) return 0f;
        double sxx = 0, syy = 0, sxy = 0;
        for (Point p : in.points()) {
            sxx += p.x() * p.x();
            syy += p.y() * p.y();
            sxy += p.x() * p.y();
        }
        // Closed-form principal-axis angle of the 2D covariance matrix
        // [[sxx, sxy], [sxy, syy]] for the larger eigenvalue:
        //   θ = ½ · atan2(2·sxy, sxx − syy)
        return 0.5f * (float) Math.atan2(2.0 * sxy, sxx - syy);
    }

    // ── 5. Rotation alignment ────────────────────────────────────────────────────

    /**
     * Rotates every point about the origin by {@code radians}.
     * Used to bring a centered cloud to a canonical orientation (indicative point on
     * the positive X-axis) so that $P+ matching becomes rotation-invariant, and
     * called again by the recognizer for the 180° flip retry.
     *
     * <p>Preserves {@link Point#turningAngle()} unchanged — interior turning
     * angles are rotation-invariant.</p>
     */
    public static PointCloud rotateBy(PointCloud in, float radians) {
        if (in.points().isEmpty()) return in;
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        List<Point> out = new ArrayList<>(in.points().size());
        for (Point p : in.points()) {
            float nx = p.x() * cos - p.y() * sin;
            float ny = p.x() * sin + p.y() * cos;
            out.add(new Point(nx, ny, p.strokeID(), p.turningAngle()));
        }
        return new PointCloud(in.name(), out);
    }

    // ── 6. Normalized turning angles ($P+ third channel) ─────────────────────────

    /**
     * Computes the normalized interior turning angle at each interior point and
     * stores it on the point as {@link Point#turningAngle()}. Endpoints — both
     * stroke endpoints and the start/end of the cloud — keep {@code angle = 0}.
     *
     * <p>For interior point {@code p_i} between {@code p_{i-1}} and {@code p_{i+1}}
     * within the <i>same stroke</i>:</p>
     * <pre>
     *   cos θ = ((p_{i+1} − p_i) · (p_i − p_{i-1})) / (|p_{i+1} − p_i| · |p_i − p_{i-1}|)
     *   α     = acos(clamp(cos θ, -1, +1)) / π        ∈ [0, 1]
     * </pre>
     *
     * <p>Discontinuities between strokes are skipped — angle computation only
     * runs over consecutive points sharing the same {@code strokeID}.</p>
     */
    public static PointCloud computeTurningAngles(PointCloud in) {
        List<Point> pts = in.points();
        if (pts.size() < 3) return in;
        List<Point> out = new ArrayList<>(pts.size());
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);
            // Endpoints of the cloud, or of a stroke, keep angle = 0.
            if (i == 0 || i == pts.size() - 1
                    || pts.get(i - 1).strokeID() != p.strokeID()
                    || pts.get(i + 1).strokeID() != p.strokeID()) {
                out.add(p.withTurningAngle(0f));
                continue;
            }
            float angle = getAngle(pts, i, p);
            out.add(p.withTurningAngle(angle));
        }
        return new PointCloud(in.name(), out);
    }

    private static float getAngle(List<Point> pts, int i, Point p) {
        Point prev = pts.get(i - 1);
        Point next = pts.get(i + 1);
        float ax = p.x() - prev.x();
        float ay = p.y() - prev.y();
        float bx = next.x() - p.x();
        float by = next.y() - p.y();
        float dn = (float) (Math.sqrt(ax * ax + ay * ay) * Math.sqrt(bx * bx + by * by));
        float angle;
        if (dn <= 1e-12f) {
            angle = 0f;
        } else {
            float cos = (ax * bx + ay * by) / dn;
            if (cos < -1f) cos = -1f;
            else if (cos > 1f) cos = 1f;
            angle = (float) (Math.acos(cos) / Math.PI);
        }
        return angle;
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────────

    public static Processed process(PointCloud raw, int n) {
        PointCloud resampled = resample(raw, n);
        PointCloud scaled    = scaleToReferenceSquare(resampled);
        PointCloud centered  = translateToOrigin(scaled);
        float angle          = indicativeAngle(centered);
        PointCloud rotated   = rotateBy(centered, -angle);
        PointCloud withAngles = computeTurningAngles(rotated);
        return new Processed(withAngles, angle);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static Map<Integer, List<Point>> groupByStroke(List<Point> pts) {
        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (Point p : pts) byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        return byStroke;
    }
}
