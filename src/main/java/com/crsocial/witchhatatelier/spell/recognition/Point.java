package com.crsocial.witchhatatelier.spell.recognition;

/**
 * Fundamental unit of the recognition system. Decoupled from the client-side
 * {@code GesturePoint} wire format so the recognizer carries no network or
 * rendering dependencies.
 */
public record Point(float x, float y, int strokeID) {

    public Point withStrokeID(int newStrokeID) {
        return new Point(x, y, newStrokeID);
    }
}
