package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * Axis-agnostic "where was this drawn, how confidently" reading — lets {@link
 * SymmetryAnalyzer} see every drawn glyph (forms and effects alike) without
 * caring which axis produced it. Both {@link FormNode} and {@link EffectNode}
 * convert to this via {@link #of}.
 *
 * @param position canvas-space centroid of the occurrence
 * @param quality  recognizer confidence in {@code [0, 1]}
 */
public record GlyphPlacement(Vector2f position, float quality) {

    public static GlyphPlacement of(FormNode node) {
        return new GlyphPlacement(node.position(), node.quality());
    }

    public static GlyphPlacement of(EffectNode node) {
        return new GlyphPlacement(node.position(), node.quality());
    }
}
