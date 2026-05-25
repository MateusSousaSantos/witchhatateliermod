package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * One sign occurrence. The compiler emits one node per occurrence (not one per
 * type) so the symmetry analyzer sees per-position data; {@link SpellGraph#signsByType()}
 * groups them for the meaning engine.
 *
 * @param type           recognized sign
 * @param position       cluster centroid in canvas-space pixels
 * @param orientationDeg indicative angle in degrees
 * @param quality        recognizer confidence in {@code [0, 1]}
 */
public record SignNode(SignType type, Vector2f position, float orientationDeg, float quality) {}
