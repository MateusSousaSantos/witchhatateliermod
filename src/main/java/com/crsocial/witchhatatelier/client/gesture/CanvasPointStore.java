package com.crsocial.witchhatatelier.client.gesture;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns all mutable stroke data for a gesture canvas.
 *
 * <p>Separates data concerns from the screen/UI layer (see {@code CanvasScreen}).
 * The screen calls the lifecycle methods as the user draws; when the session ends
 * it calls {@link #normalize} to convert raw pixel coordinates to the
 * portable [0,1] {@link GesturePoint} list that is sent to the server.</p>
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

    /** All committed strokes in pixel-space coordinates. */
    private final List<List<Vector2f>> strokes = new ArrayList<>();

    /** Stroke currently being drawn, or {@code null} when idle. */
    private List<Vector2f> activeStroke = null;

    // ── Stroke lifecycle ─────────────────────────────────────────────────────────

    /**
     * Starts a new stroke at the given pixel-space starting point.
     * Any previously open stroke is discarded (should not happen in normal flow).
     *
     * @param start the first point of the stroke (already clamped to canvas)
     */
    public void beginStroke(Vector2f start) {
        activeStroke = new ArrayList<>();
        activeStroke.add(start);
    }

    /**
     * Appends a point to the active stroke.
     * Does nothing if no stroke is currently open.
     *
     * @param pt point to add (already smoothed / snapped / dead-zone filtered)
     */
    public void addPoint(Vector2f pt) {
        if (activeStroke != null) {
            activeStroke.add(pt);
        }
    }

    /**
     * Finalizes the active stroke.
     *
     * <p>If the stroke accumulated ≥ 2 points it is moved to the committed list
     * and returned; otherwise it is silently dropped and {@code null} is returned.</p>
     *
     * @return the committed stroke, or {@code null} if it was too short to keep
     */
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

    /** Returns an unmodifiable view of all committed strokes. */
    public List<List<Vector2f>> strokes() {
        return Collections.unmodifiableList(strokes);
    }

    /**
     * Returns the stroke currently being drawn, or {@code null} if idle.
     * The returned list is live — do not modify it.
     */
    public List<Vector2f> activeStroke() {
        return activeStroke;
    }

    /** {@code true} while a stroke is being drawn. */
    public boolean isDrawing() {
        return activeStroke != null;
    }

    /** {@code true} if there are no committed strokes. */
    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    // ── Serialisation helpers ────────────────────────────────────────────────────

    /**
     * Converts all committed strokes from pixel-space to normalized [0,1] coordinates.
     *
     * @param canvasX left edge of the canvas in screen pixels
     * @param canvasY top edge of the canvas in screen pixels
     * @param canvasW canvas width in screen pixels
     * @param canvasH canvas height in screen pixels
     * @return flat list of {@link GesturePoint}s ready for network/NBT serialisation
     */
    public List<GesturePoint> normalize(int canvasX, int canvasY, int canvasW, int canvasH) {
        List<GesturePoint> out = new ArrayList<>();
        for (int s = 0; s < strokes.size(); s++) {
            for (Vector2f v : strokes.get(s)) {
                float nx = (v.x - canvasX) / canvasW;
                float ny = (v.y - canvasY) / canvasH;
                out.add(new GesturePoint(nx, ny, s));
            }
        }
        return out;
    }

    /**
     * Restores committed strokes from a saved normalized point list.
     * Replaces any existing stroke data.
     *
     * @param points   flat list as returned by {@link #normalize} (or loaded from NBT)
     * @param canvasX  left edge of the canvas in screen pixels
     * @param canvasY  top edge of the canvas in screen pixels
     * @param canvasW  canvas width in screen pixels
     * @param canvasH  canvas height in screen pixels
     */
    public void denormalize(List<GesturePoint> points,
                            int canvasX, int canvasY, int canvasW, int canvasH) {
        strokes.clear();
        if (points == null || points.isEmpty()) return;

        int maxStroke = points.stream().mapToInt(GesturePoint::strokeID).max().orElse(0);
        for (int s = 0; s <= maxStroke; s++) {
            strokes.add(new ArrayList<>());
        }
        for (GesturePoint p : points) {
            float px = p.x() * canvasW + canvasX;
            float py = p.y() * canvasH + canvasY;
            strokes.get(p.strokeID()).add(new Vector2f(px, py));
        }
        strokes.removeIf(List::isEmpty);
    }
}


