package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * Symmetry of the sign placement around the central sigil.
 *
 * @param radialScore   {@code 1.0} = perfectly balanced placement, {@code 0.0} = all on one side
 * @param bilateralScore mirror symmetry score; not computed in Phase 1 (left {@code 0})
 * @param netDirection  mean sign displacement from the sigil centre; zero = balanced,
 *                      nonzero points toward the sign cluster (canvas-space pixels)
 * @param stable        whether placement is symmetric enough to be a stable spell
 */
public record SymmetryReport(float radialScore,
                             float bilateralScore,
                             Vector2f netDirection,
                             boolean stable) {}
