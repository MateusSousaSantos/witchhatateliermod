package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Defines how a unique entity's lifetime scales with spell magnitude. Each
 * implementation provides a different curve so different entities feel distinct
 * even at the same magnitude values.
 *
 * <p>JSON format inside a {@code unique_entity} effect entry:
 * <pre>{@code
 * "lifetime_scaling": {
 *   "type": "linear",       // or "logarithmic", "stepped", "capped"
 *   "base_ticks": 40,       // minimum lifetime in ticks (always granted)
 *   "per_magnitude": 20,    // type-specific growth factor
 *   ...                     // extra fields depend on type
 * }
 * }</pre>
 *
 * <p>When the JSON omits {@code lifetime_scaling} entirely, a default linear
 * scaling of 20 ticks per magnitude with a 40-tick base is used (preserving
 * legacy behaviour).</p>
 */
public sealed interface LifetimeScaling permits
        LifetimeScaling.Linear,
        LifetimeScaling.Logarithmic,
        LifetimeScaling.Stepped,
        LifetimeScaling.Capped {

    /** Default base lifetime in ticks when not specified. */
    int DEFAULT_BASE_TICKS = 40;
    /** Default per-magnitude grant (legacy behaviour). */
    int DEFAULT_PER_MAGNITUDE = 20;

    /**
     * Computes total lifetime in ticks for the given spell magnitude.
     *
     * @param magnitude spell magnitude (≥ 0); typically derived from sign count × quality × size
     * @return lifetime in ticks (always ≥ 1)
     */
    int computeTicks(float magnitude);

    // ── Implementations ──────────────────────────────────────────────────────

    /**
     * Linear: {@code baseTicks + (int)(perMagnitude × magnitude)}.
     * Simple proportional growth — the classic behaviour.
     */
    record Linear(int baseTicks, int perMagnitude) implements LifetimeScaling {
        @Override
        public int computeTicks(float magnitude) {
            return Math.max(1, baseTicks + (int) (perMagnitude * magnitude));
        }
    }

    /**
     * Logarithmic: {@code baseTicks + (int)(scale × ln(1 + magnitude))}.
     * Grows quickly at low magnitude then flattens — ideal for entities that
     * shouldn't become immortal but reward initial investment.
     */
    record Logarithmic(int baseTicks, float scale) implements LifetimeScaling {
        @Override
        public int computeTicks(float magnitude) {
            return Math.max(1, baseTicks + (int) (scale * Math.log(1 + magnitude)));
        }
    }

    /**
     * Stepped: lifetime jumps at discrete thresholds.
     * {@code baseTicks + step × floor(magnitude / threshold)}.
     * Useful for entities with distinct "power tiers".
     */
    record Stepped(int baseTicks, int stepTicks, float threshold) implements LifetimeScaling {
        @Override
        public int computeTicks(float magnitude) {
            int steps = (int) (magnitude / threshold);
            return Math.max(1, baseTicks + stepTicks * steps);
        }
    }

    /**
     * Capped linear: grows linearly but never exceeds {@code maxTicks}.
     * {@code min(maxTicks, baseTicks + perMagnitude × magnitude)}.
     * Prevents absurdly long lifetimes on high-magnitude spells.
     */
    record Capped(int baseTicks, int perMagnitude, int maxTicks) implements LifetimeScaling {
        @Override
        public int computeTicks(float magnitude) {
            int raw = baseTicks + (int) (perMagnitude * magnitude);
            return Math.clamp(raw, 1, maxTicks);
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    /** Returns the default scaling used when the JSON field is absent. */
    static LifetimeScaling defaultScaling() {
        return new Linear(DEFAULT_BASE_TICKS, DEFAULT_PER_MAGNITUDE);
    }

    /**
     * Parses a {@code lifetime_scaling} JSON object into the appropriate variant.
     * Falls back to {@link #defaultScaling()} on missing or unrecognized types.
     */
    static LifetimeScaling fromJson(JsonObject parent) {
        if (!parent.has("lifetime_scaling") || !parent.get("lifetime_scaling").isJsonObject()) {
            // Legacy support: if old "lifetime_per_magnitude" field exists, honour it
            if (parent.has("lifetime_per_magnitude")) {
                int perMag = parent.get("lifetime_per_magnitude").getAsInt();
                return new Linear(DEFAULT_BASE_TICKS, perMag);
            }
            return defaultScaling();
        }

        JsonObject o = parent.getAsJsonObject("lifetime_scaling");
        String type = o.has("type") ? o.get("type").getAsString().toLowerCase(Locale.ROOT) : "linear";
        int baseTicks = o.has("base_ticks") ? o.get("base_ticks").getAsInt() : DEFAULT_BASE_TICKS;

        return switch (type) {
            case "logarithmic" -> {
                float scale = o.has("scale") ? o.get("scale").getAsFloat() : 60.0f;
                yield new Logarithmic(baseTicks, scale);
            }
            case "stepped" -> {
                int step = o.has("step_ticks") ? o.get("step_ticks").getAsInt() : 40;
                float threshold = o.has("threshold") ? o.get("threshold").getAsFloat() : 1.0f;
                yield new Stepped(baseTicks, step, threshold);
            }
            case "capped" -> {
                int perMag = o.has("per_magnitude") ? o.get("per_magnitude").getAsInt() : DEFAULT_PER_MAGNITUDE;
                int max = o.has("max_ticks") ? o.get("max_ticks").getAsInt() : 600;
                yield new Capped(baseTicks, perMag, max);
            }
            default -> { // "linear" or unknown
                int perMag = o.has("per_magnitude") ? o.get("per_magnitude").getAsInt() : DEFAULT_PER_MAGNITUDE;
                yield new Linear(baseTicks, perMag);
            }
        };
    }
}


