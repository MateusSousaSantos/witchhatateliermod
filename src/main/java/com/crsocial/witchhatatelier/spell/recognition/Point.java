package com.crsocial.witchhatatelier.spell.recognition;

/**
 * Fundamental unit of the recognition system. Decoupled from the client-side
 * {@code GesturePoint} wire format so the recognizer carries no network or
 * rendering dependencies.
 *
 * <p>The {@code turningAngle} channel is the normalized interior turning angle
 * at this point, in {@code [0, 1]} (i.e. {@code acos(cos θ) / π}). It is the
 * third dimension of the $P+ distance metric. Endpoints and points not yet
 * processed by {@link PointCloudPreprocessor} have {@code turningAngle = 0}.</p>
 */
public record Point(float x, float y, int strokeID, float turningAngle) {

    public Point(float x, float y, int strokeID) {
        this(x, y, strokeID, 0f);
    }

    public Point withTurningAngle(float newAngle) {
        return new Point(x, y, strokeID, newAngle);
    }
}
