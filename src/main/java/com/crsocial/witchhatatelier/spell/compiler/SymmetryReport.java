package com.crsocial.witchhatatelier.spell.compiler;

import org.joml.Vector2f;

/**
 * Symmetry of the sign placement around the central sigil.
 *
 * @param radialScore   {@code 1.0} = perfectly balanced placement, {@code 0.0} = all on one side
 * @param bilateralScore mirror symmetry score; not computed in Phase 1 (left {@code 0})
 * @param netDirection  quality-weighted net of the sign force vectors (each = displacement
 *                      from the sigil centre × quality); zero = balanced (forces cancel or
 *                      fell inside the cancel deadzone), nonzero points toward the dominant
 *                      side. Only its direction is consumed downstream (it is re-normalized).
 * @param stable        whether placement is symmetric enough to be a stable spell
 */
public record SymmetryReport(float radialScore,
                             float bilateralScore,
                             Vector2f netDirection,
                             boolean stable) {}
