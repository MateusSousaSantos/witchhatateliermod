package com.crsocial.witchhatatelier.spell.meaning;

import org.joml.Vector2f;

/**
 * Shared canvas→world direction mapping for the meaning engine.
 *
 * <p>A spell's direction is authored in canvas space, but the placed paper can be rotated
 * in-plane in the world. This rotates a canvas-space 2-D direction by the paper's in-plane
 * rotation and maps it to the horizontal world plane as {@code canvas.x → world.x},
 * {@code canvas.y → world.z}.</p>
 *
 * <p>The rotation reproduces exactly what {@code PlacedPaperBlockEntityRenderer.rotU/rotV}
 * does to the rendered ink ({@code dx' = dx·cosθ − dy·sinθ}, {@code dy' = dx·sinθ + dy·cosθ}
 * with {@code θ = drawRotationDeg}), so the steered direction lines up with the drawing the
 * player sees. Used by both {@code MeaningEngine.resolveDirection} and
 * {@code ColumnSignBehavior} so they stay consistent.</p>
 */
public final class CanvasDirection {

    private CanvasDirection() {}

    /**
     * Rotates the canvas-space direction {@code (dx, dy)} by {@code drawRotationDeg} and maps it
     * to world XZ. Returns {@code (worldX, worldZ)}; the vertical (Y) component is the caller's
     * concern.
     */
    public static Vector2f toWorldXZ(float dx, float dy, float drawRotationDeg) {
        float r = (float) Math.toRadians(drawRotationDeg);
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new Vector2f(dx * c - dy * s, dx * s + dy * c);
    }
}
