package com.crsocial.witchhatatelier.client.magic;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of pen strokes that form a single drawn sigil.
 *
 * <p>Multiple strokes are grouped into a single Sigil when they are drawn
 * in close spatial proximity within a short temporal window.</p>
 */
public class Sigil {

    /**
     * Axis-aligned bounding rectangle in pixel space.
     */
    public record Rect2D(float minX, float minY, float maxX, float maxY) {

        public float width() { return maxX - minX; }

        public float height() { return maxY - minY; }

        public float centerX() { return (minX + maxX) * 0.5f; }

        public float centerY() { return (minY + maxY) * 0.5f; }

        /**
         * Returns the shortest distance from point (px, py) to the edge of this rectangle.
         * Returns 0 if the point is inside or on the boundary.
         */
        public float distanceTo(float px, float py) {
            float dx = Math.max(0, Math.max(minX - px, px - maxX));
            float dy = Math.max(0, Math.max(minY - py, py - maxY));
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    // ── State ───────────────────────────────────────────────────────────────────

    /** The strokes composing this sigil, each stroke is a list of pixel-space points. */
    private final List<List<Vector2f>> strokes = new ArrayList<>();

    // Cached bounding box (invalidated on mutation)
    private Rect2D cachedBounds = null;

    // ── Construction ────────────────────────────────────────────────────────────

    public Sigil() {}

    /**
     * Creates a Sigil pre-populated with the given strokes (used for deserialization).
     */
    public Sigil(List<List<Vector2f>> existingStrokes) {
        for (List<Vector2f> stroke : existingStrokes) {
            strokes.add(new ArrayList<>(stroke));
        }
    }

    // ── Stroke management ───────────────────────────────────────────────────────

    /**
     * Begins a new stroke in this sigil. Returns the stroke list for appending points.
     */
    public List<Vector2f> beginStroke(float startX, float startY) {
        List<Vector2f> stroke = new ArrayList<>();
        stroke.add(new Vector2f(startX, startY));
        strokes.add(stroke);
        cachedBounds = null;
        return stroke;
    }

    /**
     * Adds a point to the currently active (last) stroke.
     */
    public void addPoint(float x, float y) {
        if (strokes.isEmpty()) {
            beginStroke(x, y);
            return;
        }
        List<Vector2f> currentStroke = strokes.getLast();
        currentStroke.add(new Vector2f(x, y));
        cachedBounds = null;
    }

    /**
     * Returns an unmodifiable view of all strokes.
     */
    public List<List<Vector2f>> getStrokes() {
        return strokes;
    }

    /**
     * Returns the total number of strokes in this sigil.
     */
    public int getStrokeCount() {
        return strokes.size();
    }

    /**
     * Returns the total number of points across all strokes.
     */
    public int getTotalPointCount() {
        int count = 0;
        for (List<Vector2f> stroke : strokes) {
            count += stroke.size();
        }
        return count;
    }

    // ── Bounds ──────────────────────────────────────────────────────────────────

    /**
     * Computes (or returns cached) axis-aligned bounding rectangle of all points in this sigil.
     * Returns {@code null} if the sigil has no points.
     */
    public Rect2D getBounds() {
        if (cachedBounds != null) return cachedBounds;
        if (strokes.isEmpty()) return null;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        boolean hasPoints = false;
        for (List<Vector2f> stroke : strokes) {
            for (Vector2f pt : stroke) {
                if (pt.x < minX) minX = pt.x;
                if (pt.y < minY) minY = pt.y;
                if (pt.x > maxX) maxX = pt.x;
                if (pt.y > maxY) maxY = pt.y;
                hasPoints = true;
            }
        }

        if (!hasPoints) return null;
        cachedBounds = new Rect2D(minX, minY, maxX, maxY);
        return cachedBounds;
    }

    /**
     * Returns the distance from point (px, py) to this sigil's bounding box.
     * Returns 0 if inside. Returns {@link Float#MAX_VALUE} if the sigil is empty.
     */
    public float distanceToBounds(float px, float py) {
        Rect2D bounds = getBounds();
        if (bounds == null) return Float.MAX_VALUE;
        return bounds.distanceTo(px, py);
    }
}

