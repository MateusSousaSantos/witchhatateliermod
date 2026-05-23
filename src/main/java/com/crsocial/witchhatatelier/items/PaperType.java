package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.client.gesture.CanvasSize;

import java.util.Arrays;
import java.util.Optional;

/**
 * Defines every paper variant in the mod.
 *
 * <p>Each entry encodes the shape of the drawable canvas and its fixed logical canvas size.
 * Adding a new paper type requires only a new enum constant here plus the corresponding
 * item registrations in {@link ModItems}.</p>
 */
public enum PaperType {

    SMALL_SQUARE ("small_square",  false, new CanvasSize(512, 512)),
    MEDIUM_SQUARE("medium_square", false, new CanvasSize(1024, 1024)),
    SMALL_ROUND  ("small_round",   true,  new CanvasSize(512, 512)),
    MEDIUM_ROUND ("medium_round",  true,  new CanvasSize(1024, 1024));

    private final String id;
    private final boolean round;
    private final CanvasSize canvasSize;

    PaperType(String id, boolean round, CanvasSize canvasSize) {
        this.id = id;
        this.round = round;
        this.canvasSize = canvasSize;
    }

    /** Registry-friendly string identifier, e.g. {@code "medium_square"}. */
    public String getId() { return id; }

    /** {@code true} if this paper uses a circular canvas. */
    public boolean isRound() { return round; }

    /** Fixed logical canvas size in canvas pixels. */
    public CanvasSize getCanvasSize() { return canvasSize; }

    /** {@code true} for LARGE_SQUARE and LARGE_ROUND (occupy a 2×2 floor area when placed). */
    public boolean isLarge() { return false; }

    /** Looks up a PaperType by its {@link #getId()} string. */
    public static Optional<PaperType> fromId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }
}
