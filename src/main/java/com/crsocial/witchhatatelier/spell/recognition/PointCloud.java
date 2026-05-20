package com.crsocial.witchhatatelier.spell.recognition;

import java.util.List;

/**
 * A processed collection of points representing either a player-drawn sigil
 * or a stored template variant.
 */
public record PointCloud(String name, List<Point> points) {
}
