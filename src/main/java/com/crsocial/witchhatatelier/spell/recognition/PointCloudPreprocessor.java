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
 *   <li>Compute the indicative angle from centroid to the first point.</li>
 * </ol>
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
                Point p = e.getValue().get(0);
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
            Point p = stroke.get(0);
            return List.of(new Point(p.x(), p.y(), strokeID));
        }
        double total = pathLength(stroke);
        if (total == 0.0) {
            // All points identical — emit n copies.
            List<Point> dup = new ArrayList<>(n);
            Point p = stroke.get(0);
            for (int i = 0; i < n; i++) dup.add(new Point(p.x(), p.y(), strokeID));
            return dup;
        }

        double interval = total / (n - 1);
        List<Point> out = new ArrayList<>(n);
        Point first = stroke.get(0);
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
            Point last = stroke.get(stroke.size() - 1);
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

    public static float indicativeAngle(PointCloud in) {
        if (in.points().isEmpty()) return 0f;
        Point first = in.points().get(0);
        return (float) Math.atan2(first.y(), first.x());
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────────

    public static Processed process(PointCloud raw, int n) {
        PointCloud resampled = resample(raw, n);
        PointCloud scaled = scaleToReferenceSquare(resampled);
        PointCloud centered = translateToOrigin(scaled);
        float angle = indicativeAngle(centered);
        return new Processed(centered, angle);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static Map<Integer, List<Point>> groupByStroke(List<Point> pts) {
        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (Point p : pts) byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        return byStroke;
    }
}
