package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * The central sigil of a ring — exactly one per {@link SpellGraph}.
 *
 * @param type    recognized element
 * @param centre  cluster centroid in canvas-space pixels
 * @param quality recognizer confidence in {@code [0, 1]}
 */
public record SigilNode(SigilType type, Vector2f centre, float quality) {}
