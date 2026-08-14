package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * The central element of a ring — exactly one per {@link SpellGraph}. Renamed
 * from {@code SigilNode} as part of the compositional spell engine migration.
 *
 * @param type    recognized element
 * @param centre  cluster centroid in canvas-space pixels
 * @param quality recognizer confidence in {@code [0, 1]}
 */
public record ElementNode(ElementType type, Vector2f centre, float quality) {
}
