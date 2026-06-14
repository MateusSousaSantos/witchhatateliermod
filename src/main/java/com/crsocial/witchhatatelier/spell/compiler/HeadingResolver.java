package com.crsocial.witchhatatelier.spell.compiler;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import org.joml.Vector2f;

import java.util.List;

/**
 * Recovers a full <b>360° heading</b> ("which way the arrow points") from a drawn
 * glyph's raw cluster strokes, in canvas {@code [0,1]} space.
 *
 * <p>The {@code $P+} recognizer is deliberately rotation-invariant — it identifies a
 * glyph regardless of how it is turned — so the drawn orientation is information it
 * throws away. This resolver re-derives it independently from the <i>raw</i> strokes
 * (before resampling, so raw point density survives) and never touches the recognizer
 * scoring, leaving the tuning corpus untouched.</p>
 *
 * <p>Two stages:</p>
 * <ol>
 *   <li><b>Axis</b> — the principal axis via 2×2 covariance eigendecomposition (the same
 *       math as {@code PointCloudPreprocessor.indicativeAngle}). A principal axis is a
 *       <i>line</i>, so it only fixes the orientation up to 180°. Round/blobby glyphs have
 *       no real axis ({@code elongation} below {@link Config#HEADING_MIN_ELONGATION}) and
 *       yield {@link #NONE}.</li>
 *   <li><b>Flip</b> — which end is "forward" is decided by a weighted vote of two asymmetry
 *       signals (per the design): <i>geometric mass asymmetry</i> (the centroid leans toward
 *       the heavier/denser end of the axial extent — survives resampling) and <i>raw point
 *       density</i> (more drawn points on the forward side). The end the glyph leans toward
 *       is treated as the aim end.</li>
 * </ol>
 *
 * <p>The returned heading is canvas-space; callers map it to world XZ with
 * {@code CanvasDirection.toWorldXZ(dir.x, dir.y, drawRotationDeg)} so it follows the rendered
 * drawing, exactly as the placement-based steering does. When confidence is below
 * {@link Config#HEADING_MIN_CONFIDENCE} the result is {@link #NONE} and callers fall back to
 * placement-derived direction.</p>
 */
public final class HeadingResolver {

    /** No usable heading — callers should fall back to placement. */
    public static final Heading NONE = new Heading(new Vector2f(0f, 0f), 0f);

    private static final float EPSILON = 1e-6f;

    private HeadingResolver() {}

    /**
     * A recovered heading.
     *
     * @param dir        unit direction in canvas space; the zero vector when undefined
     * @param confidence {@code [0,1]} — how clear the axis and its forward end are
     */
    public record Heading(Vector2f dir, float confidence) {
        /** True when a usable (non-zero) heading was recovered. */
        public boolean hasDirection() {
            return dir.lengthSquared() > EPSILON;
        }
    }

    /**
     * Resolves the heading of one glyph from its raw (unresampled) strokes.
     * Returns {@link #NONE} when the shape is too round or too symmetric to point.
     */
    public static Heading resolve(List<List<Point>> strokes) {
        // ── Centroid over all raw points ─────────────────────────────────────────
        float cx = 0f, cy = 0f;
        int n = 0;
        for (List<Point> stroke : strokes) {
            for (Point p : stroke) { cx += p.x(); cy += p.y(); n++; }
        }
        if (n < 2) return NONE;
        cx /= n; cy /= n;

        // ── 2×2 covariance of the centered points ────────────────────────────────
        double sxx = 0, syy = 0, sxy = 0;
        for (List<Point> stroke : strokes) {
            for (Point p : stroke) {
                double dx = p.x() - cx, dy = p.y() - cy;
                sxx += dx * dx; syy += dy * dy; sxy += dx * dy;
            }
        }

        // Eigenvalues of [[sxx, sxy], [sxy, syy]] (symmetric → real).
        double mean = (sxx + syy) / 2.0;
        double diff = (sxx - syy) / 2.0;
        double disc = Math.sqrt(diff * diff + sxy * sxy);
        double lambda1 = mean + disc;   // larger
        double lambda2 = mean - disc;   // smaller
        double sum = lambda1 + lambda2;
        if (sum <= EPSILON) return NONE;

        // ── Elongation gate: round blobs have no axis to point along ──────────────
        float elong = (float) ((lambda1 - lambda2) / sum);
        float minElong = Config.HEADING_MIN_ELONGATION.get().floatValue();
        if (elong < minElong) return NONE;
        float elongConf = clamp01((elong - minElong) / Math.max(EPSILON, 1f - minElong));

        // ── Principal-axis direction (line, 180°-ambiguous) ──────────────────────
        float theta = 0.5f * (float) Math.atan2(2.0 * sxy, sxx - syy);
        float ax = (float) Math.cos(theta), ay = (float) Math.sin(theta);

        // ── Flip vote: project onto the axis and measure forward-end asymmetry ────
        float minT = Float.MAX_VALUE, maxT = -Float.MAX_VALUE;
        int nPos = 0, nNeg = 0;
        for (List<Point> stroke : strokes) {
            for (Point p : stroke) {
                float t = (p.x() - cx) * ax + (p.y() - cy) * ay;
                if (t < minT) minT = t;
                if (t > maxT) maxT = t;
                if (t > 0f) nPos++; else if (t < 0f) nNeg++;
            }
        }
        float extentWidth = maxT - minT;

        // Mass asymmetry: the centroid (projection 0) sits off the extent midpoint,
        // pulled toward the denser/heavier end. A negative midpoint ⇒ mass on +axis.
        float massScore = extentWidth > EPSILON
                ? clampUnit(-2f * ((minT + maxT) / 2f) / extentWidth)
                : 0f;
        // Raw density: more drawn points on the +axis side ⇒ that end is forward.
        float densityScore = (nPos + nNeg) > 0
                ? (float) (nPos - nNeg) / (nPos + nNeg)
                : 0f;

        float wMass = Config.HEADING_MASS_WEIGHT.get().floatValue();
        float wDensity = Config.HEADING_DENSITY_WEIGHT.get().floatValue();
        float wSum = wMass + wDensity;
        if (wSum <= EPSILON) return NONE;
        float vote = (wMass * massScore + wDensity * densityScore) / wSum; // [-1, 1]

        float forwardSign = vote >= 0f ? 1f : -1f;
        float flipConf = Math.abs(vote);
        float confidence = elongConf * flipConf;
        if (confidence < Config.HEADING_MIN_CONFIDENCE.get().floatValue()) return NONE;

        return new Heading(new Vector2f(ax * forwardSign, ay * forwardSign), confidence);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float clampUnit(float v) {
        return v < -1f ? -1f : Math.min(v, 1f);
    }
}
