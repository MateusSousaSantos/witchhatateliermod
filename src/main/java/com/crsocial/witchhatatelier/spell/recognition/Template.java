package com.crsocial.witchhatatelier.spell.recognition;

/**
 * One template entry in the recognizer registry: a single variant of a canonical
 * spell. The {@code rawCloud} preserves the unprocessed input (so future
 * preprocessing changes can re-derive a new {@code processedCloud} without
 * re-authoring templates).
 */
public record Template(String spellName,
                       String variantName,
                       PointCloud rawCloud,
                       PointCloud processedCloud,
                       int resampleN,
                       float indicativeAngle,
                       boolean isRing,
                       boolean directional,
                       float normalizedArcLength,
                       SigilMetrics metrics) {
}
