package com.crsocial.witchhatatelier.spell.cast;

/**
 * Mutable fuel container for channeled spells. Tracks the remaining fuel supply;
 * a spell ends when fuel reaches zero (via {@link #consume(float)}), regardless
 * of any other conditions.
 */
public final class SpellFuel {

    private float fuel;
    private final float capacity;

    public SpellFuel(float initialFuel) {
        this.fuel = this.capacity = Math.max(0f, initialFuel);
    }

    /**
     * Consumes up to {@code amount} fuel. Returns {@code true} if at least some
     * fuel was available and consumed; {@code false} if already empty.
     */
    public boolean consume(float amount) {
        if (fuel <= 0f) return false;
        fuel = Math.max(0f, fuel - amount);
        return true;
    }

    public boolean isEmpty() {
        return fuel <= 0f;
    }

    public float remaining() {
        return fuel;
    }

    public float capacity() {
        return capacity;
    }

    /** Fraction elapsed (consumed / total), in [0, 1]. */
    public float progress() {
        return capacity > 0f ? 1f - fuel / capacity : 0f;
    }
}
