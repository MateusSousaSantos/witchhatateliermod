package com.crsocial.witchhatatelier.spell.recognition;

/**
 * One template entry in the recognizer registry: a single variant of a canonical
 * spell. The {@code rawCloud} preserves the unprocessed input (so future
 * preprocessing changes can re-derive a new {@code processedCloud} without
 * re-authoring templates).
 *
 * <p>{@code isRejection} marks a <b>negative/tombstone</b> template: a shape the
 * recognizer should <i>reject</i> rather than cast (explicit garbage, or a cut
 * glyph like {@code dispersion} kept only so its draws don't fall through to their
 * nearest live spell). Negative templates compete in the chamfer like any other,
 * but a win means {@code unknown} — see {@link PDollarPlusRecognizer}.</p>
 */
public record Template(String spellName,
                       String variantName,
                       PointCloud rawCloud,
                       PointCloud processedCloud,
                       int resampleN,
                       float indicativeAngle,
                       boolean isRing,
                       boolean isRejection,
                       float normalizedArcLength,
                       SigilMetrics metrics) {
}
