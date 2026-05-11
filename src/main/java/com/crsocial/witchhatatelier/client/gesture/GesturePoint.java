package com.crsocial.witchhatatelier.client.gesture;


/**
 * A single point in the flattened point-cloud fed to the $P gesture recognizer.
 *
 * <p>{@code strokeID} preserves which stroke the point belonged to, which is
 * required by the $P algorithm to differentiate disconnected lines in a sigil.</p>
 */
public final class GesturePoint {

    /**
     * Normalized canvas X coordinate (pixels, then scaled to [0,1] during recognition).
     */
    private final float x;

    /**
     * Normalized canvas Y coordinate (pixels, then scaled to [0,1] during recognition).
     */
    private final float y;

    /**
     * Zero-based index of the stroke this point was captured in.
     * The $P algorithm uses this to group disconnected sub-paths.
     */
    public final int strokeID;

    public GesturePoint(float x, float y, int strokeID) {
        this.x = x;
        this.y = y;
        this.strokeID = strokeID;
    }

    @Override
    public String toString() {
        return "GesturePoint{x=" + x + ", y=" + y + ", strokeID=" + strokeID + "}";
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public int strokeID() {
        return strokeID;
    }
}
