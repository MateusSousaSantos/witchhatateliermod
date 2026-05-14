package com.crsocial.witchhatatelier.client.gesture;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns all mutable stroke data for a gesture canvas.
 *
 * <p>All coordinates stored here are in <b>canvas space</b> — the logical pixel grid
 * defined by {@link CanvasSize}. They are independent of screen resolution, zoom, or pan.</p>
 *
 * <h3>Lifecycle per stroke</h3>
 * <ol>
 *   <li>{@link #beginStroke(Vector2f)} — mouse pressed on canvas</li>
 *   <li>{@link #addPoint(Vector2f)} — (zero or more times) mouse dragged</li>
 *   <li>{@link #finishStroke()} — mouse released; stroke committed if it has ≥ 2 points</li>
 * </ol>
 */
public final class CanvasPointStore {

    // ── Storage ─────────────────────────────────────────────────────────────────

    /** All committed strokes in canvas-space coordinates. */
    private final List<List<Vector2f>> strokes = new ArrayList<>();

    /** Stroke currently being drawn, or {@code null} when idle. */
    private List<Vector2f> activeStroke = null;

    // ── Stroke lifecycle ─────────────────────────────────────────────────────────

    public void beginStroke(Vector2f start) {
        activeStroke = new ArrayList<>();
        activeStroke.add(start);
    }

    public void addPoint(Vector2f pt) {
        if (activeStroke != null) activeStroke.add(pt);
    }

    public List<Vector2f> finishStroke() {
        if (activeStroke == null) return null;
        List<Vector2f> finished = activeStroke;
        activeStroke = null;
        if (finished.size() >= 2) {
            strokes.add(finished);
            return finished;
        }
        return null;
    }

    // ── Accessors ────────────────────────────────────────────────────────────────

    public List<List<Vector2f>> strokes()     { return Collections.unmodifiableList(strokes); }
    public List<Vector2f>       activeStroke(){ return activeStroke; }
    public boolean isDrawing()                { return activeStroke != null; }
    public boolean isEmpty()                  { return strokes.isEmpty(); }

    // ── Serialisation helpers ────────────────────────────────────────────────────

    /**
     * Converts all committed strokes from canvas-space to normalized [0,1] coordinates.
     *
     * @param canvasW canvas width in canvas pixels
     * @param canvasH canvas height in canvas pixels
     * @return flat list of {@link GesturePoint}s ready for network/NBT serialisation
     */
    public List<GesturePoint> normalize(int canvasW, int canvasH) {
        List<GesturePoint> out = new ArrayList<>();
        for (int s = 0; s < strokes.size(); s++) {
            for (Vector2f v : strokes.get(s)) {
                out.add(new GesturePoint(v.x / canvasW, v.y / canvasH, s));
            }
        }
        return out;
    }

    /**
     * Restores committed strokes from a saved normalized point list.
     * Replaces any existing stroke data.
     *
     * @param points  flat list as returned by {@link #normalize} (or loaded from NBT)
     * @param canvasW canvas width in canvas pixels
     * @param canvasH canvas height in canvas pixels
     */
    public void denormalize(List<GesturePoint> points, int canvasW, int canvasH) {
        strokes.clear();
        if (points == null || points.isEmpty()) return;

        int maxStroke = points.stream().mapToInt(GesturePoint::strokeID).max().orElse(0);
        for (int s = 0; s <= maxStroke; s++) strokes.add(new ArrayList<>());
        for (GesturePoint p : points) {
            strokes.get(p.strokeID()).add(new Vector2f(p.x() * canvasW, p.y() * canvasH));
        }
        strokes.removeIf(List::isEmpty);
    }
}
