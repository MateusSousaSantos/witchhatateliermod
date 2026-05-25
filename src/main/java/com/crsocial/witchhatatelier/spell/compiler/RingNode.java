package com.crsocial.witchhatatelier.spell.compiler;

import java.util.List;

/**
 * The enclosing activation ring.
 *
 * @param strokeIds      the ring's stroke IDs (from {@code TriggerResult})
 * @param arcCoverageDeg how much of a full circle the ring covers; {@code 360}
 *                       for a ring the trigger evaluator already confirmed
 *                       closed. Precise arc-coverage measurement is future work.
 * @param radius         mean distance from the ring centroid to its points
 *                       (canvas-space pixels)
 */
public record RingNode(List<Integer> strokeIds, float arcCoverageDeg, float radius) {}
