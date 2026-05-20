package com.crsocial.witchhatatelier.spell.cluster;

import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;

import java.util.ArrayList;
import java.util.List;

/**
 * One logical sigil produced by {@link SigilClusterer}: a group of strokes that
 * are spatially related (touching, intersecting, or close enough to be one
 * symbol — central rune + accents, parallel lines, etc.).
 *
 * <p>Stroke IDs in {@code strokes} are the <em>original</em> IDs from the
 * raw input. Use {@link #toPointCloud} to obtain a {@link PointCloud} with
 * stroke IDs renumbered {@code 0..N-1} for downstream processing.</p>
 */
public record SigilCluster(List<List<Point>> strokes) {

    public PointCloud toPointCloud(String name) {
        List<Point> flat = new ArrayList<>();
        for (int s = 0; s < strokes.size(); s++) {
            for (Point p : strokes.get(s)) {
                flat.add(new Point(p.x(), p.y(), s));
            }
        }
        return new PointCloud(name, flat);
    }
}
