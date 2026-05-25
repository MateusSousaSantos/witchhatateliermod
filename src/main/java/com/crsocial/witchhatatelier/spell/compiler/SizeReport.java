package com.crsocial.witchhatatelier.spell.compiler;

/**
 * Size of the inscribed content.
 *
 * @param bboxArea           content bounding-box area in canvas-space pixels²
 * @param normalizedBboxArea content bbox area as a fraction of the ring bbox area, clamped to {@code [0, 1]}
 */
public record SizeReport(float bboxArea, float normalizedBboxArea) {}
