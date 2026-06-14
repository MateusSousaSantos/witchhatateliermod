package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * The central sigil of a ring — exactly one per {@link SpellGraph}.
 *
 * @param type              recognized element
 * @param centre            cluster centroid in canvas-space pixels
 * @param quality           recognizer confidence in {@code [0, 1]}
 * @param heading           recovered glyph heading (unit, canvas space); zero vector when the
 *                          sigil is not directional or no heading could be recovered
 * @param headingConfidence {@code [0, 1]} confidence in {@code heading}; {@code 0} when undefined
 */
public record SigilNode(SigilType type, Vector2f centre, float quality,
                        Vector2f heading, float headingConfidence) {

    /** True when a usable glyph heading was recovered for this sigil. */
    public boolean hasHeading() {
        return heading.lengthSquared() > 1e-6f;
    }
}
