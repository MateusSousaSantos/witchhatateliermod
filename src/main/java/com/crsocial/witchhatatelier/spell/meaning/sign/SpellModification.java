package com.crsocial.witchhatatelier.spell.meaning.sign;

import org.joml.Vector3f;

/**
 * Modifications produced by a {@link SignBehavior} for a particular sign
 * interaction. These deltas are applied to the {@link com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell}
 * after the base parameters are resolved by the meaning engine.
 *
 * <p>All fields are additive offsets or multipliers — a "no change" modification
 * is represented by {@link #NONE}.</p>
 *
 * @param originOffset    world-space offset added to the spell's origin point
 * @param directionBias   world-space vector added to the spell's direction before re-normalization
 * @param powerMultiplier multiplicative factor on final power (1.0 = no change)
 * @param durationMultiplier multiplicative factor on final duration (1.0 = no change)
 * @param aoeMultiplier   multiplicative factor on AoE (1.0 = no change)
 */
public record SpellModification(Vector3f originOffset,
                                Vector3f directionBias,
                                float powerMultiplier,
                                float durationMultiplier,
                                float aoeMultiplier) {

    /** Identity modification — changes nothing. */
    public static final SpellModification NONE = new SpellModification(
            new Vector3f(), new Vector3f(), 1.0f, 1.0f, 1.0f);

    /** Convenience builder starting from no-change defaults. */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Vector3f originOffset = new Vector3f();
        private Vector3f directionBias = new Vector3f();
        private float powerMultiplier = 1.0f;
        private float durationMultiplier = 1.0f;
        private float aoeMultiplier = 1.0f;

        public Builder originOffset(Vector3f offset) { this.originOffset = offset; return this; }
        public Builder originOffset(float x, float y, float z) { this.originOffset = new Vector3f(x, y, z); return this; }
        public Builder directionBias(Vector3f bias) { this.directionBias = bias; return this; }
        public Builder directionBias(float x, float y, float z) { this.directionBias = new Vector3f(x, y, z); return this; }
        public Builder powerMultiplier(float m) { this.powerMultiplier = m; return this; }
        public Builder durationMultiplier(float m) { this.durationMultiplier = m; return this; }
        public Builder aoeMultiplier(float m) { this.aoeMultiplier = m; return this; }

        public SpellModification build() {
            return new SpellModification(originOffset, directionBias, powerMultiplier, durationMultiplier, aoeMultiplier);
        }
    }
}

