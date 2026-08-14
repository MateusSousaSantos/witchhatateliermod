package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * One form occurrence. The compiler emits one node per occurrence (not one per
 * type) so the symmetry analyzer sees per-position data; {@link SpellGraph#formsByType()}
 * groups them for the composition engine. Successor to (half of) the old
 * {@code SignNode}.
 *
 * @param type           recognized form
 * @param position       cluster centroid in canvas-space pixels
 * @param orientationDeg indicative angle in degrees
 * @param quality        recognizer confidence in {@code [0, 1]}
 */
public record FormNode(FormType type, Vector2f position, float orientationDeg, float quality) {
}
