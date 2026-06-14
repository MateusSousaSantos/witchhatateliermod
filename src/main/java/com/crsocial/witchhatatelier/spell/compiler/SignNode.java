package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * One sign occurrence. The compiler emits one node per occurrence (not one per
 * type) so the symmetry analyzer sees per-position data; {@link SpellGraph#signsByType()}
 * groups them for the meaning engine.
 *
 * @param type              recognized sign
 * @param position          cluster centroid in canvas-space pixels
 * @param orientationDeg    indicative angle in degrees (principal axis, 180°-ambiguous)
 * @param quality           recognizer confidence in {@code [0, 1]}
 * @param heading           recovered glyph heading (unit, canvas space); zero vector when the
 *                          sign is not directional or no heading could be recovered
 * @param headingConfidence {@code [0, 1]} confidence in {@code heading}; {@code 0} when undefined
 */
public record SignNode(SignType type, Vector2f position, float orientationDeg, float quality,
                       Vector2f heading, float headingConfidence) {

    /** True when a usable glyph heading was recovered for this sign occurrence. */
    public boolean hasHeading() {
        return heading.lengthSquared() > 1e-6f;
    }
}
