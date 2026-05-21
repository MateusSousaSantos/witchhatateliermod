package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.spell.cluster.SigilCluster;
import com.crsocial.witchhatatelier.spell.cluster.SigilClusterer;
import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.PointCloudPreprocessor;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.recognition.TemplateRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the full recognition pipeline state every time the canvas is closed,
 * so the F9 debug screen can visualize it.
 *
 * <p>Runs client-side; in single-player the {@link TemplateRegistry} singleton
 * is shared with the server, so the snapshot mirrors what the server saw.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LastRecognitionDebug {

    public record ClusterSnapshot(
            int index,
            List<Integer> originalStrokeIds,
            PointCloud rawCloud,
            PointCloud processedCloud,
            float indicativeAngle,
            List<PDollarPlusRecognizer.Scored> ranked,
            RecognitionResult result) {}

    public record Snapshot(
            long timestampMs,
            List<GesturePoint> rawPoints,
            List<Integer> ringStrokeIds,
            List<ClusterSnapshot> clusters,
            int totalStrokes) {}

    private static volatile Snapshot last;

    private LastRecognitionDebug() {}

    public static Snapshot get() { return last; }

    /** Re-runs the full pipeline on the client and stores the result. No side effects. */
    public static void capture(List<GesturePoint> points, List<Integer> ringStrokeIds) {
        List<Integer> ringIds = ringStrokeIds == null ? List.of() : List.copyOf(ringStrokeIds);
        if (points == null || points.isEmpty()) {
            last = new Snapshot(System.currentTimeMillis(), List.of(), ringIds, List.of(), 0);
            return;
        }

        // 1. Group by stroke ID
        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (GesturePoint gp : points) {
            byStroke.computeIfAbsent(gp.strokeID(), k -> new ArrayList<>())
                    .add(new Point(gp.x(), gp.y(), gp.strokeID()));
        }
        int totalStrokes = byStroke.size();

        // 3. Filter out ring strokes for content recognition
        List<List<Point>> contentStrokes = new ArrayList<>();
        List<Integer> contentStrokeIds = new ArrayList<>();
        for (var e : byStroke.entrySet()) {
            if (ringIds.contains(e.getKey())) continue;
            contentStrokes.add(e.getValue());
            contentStrokeIds.add(e.getKey());
        }

        List<ClusterSnapshot> clusterSnaps = new ArrayList<>();
        if (!contentStrokes.isEmpty()) {
            float microR = Config.MICRO_MERGE_RADIUS.get().floatValue();
            float macroR = Config.MACRO_MERGE_RADIUS.get().floatValue();
            int resampleN = Config.RESAMPLE_N.get();
            float minScore = Config.RECOGNITION_MIN_SCORE.get().floatValue();

            List<SigilCluster> clusters = SigilClusterer.cluster(contentStrokes, microR, macroR);
            PDollarPlusRecognizer recognizer = new PDollarPlusRecognizer(TemplateRegistry.get(), minScore);

            for (int i = 0; i < clusters.size(); i++) {
                SigilCluster cluster = clusters.get(i);

                // Recover original stroke IDs by matching stroke contents back to contentStrokes
                List<Integer> originalIds = getIntegers(cluster, contentStrokes, contentStrokeIds);

                PointCloud raw = cluster.toPointCloud("candidate_" + i);
                PointCloudPreprocessor.Processed processed = PointCloudPreprocessor.process(raw, resampleN);
                RecognitionResult result = recognizer.match(processed);
                List<PDollarPlusRecognizer.Scored> ranked =
                        recognizer.matchVerbose(processed, TemplateRegistry.get().all());

                clusterSnaps.add(new ClusterSnapshot(
                        i, originalIds, raw, processed.cloud(),
                        processed.indicativeAngle(), ranked, result));
            }
        }

        last = new Snapshot(
                System.currentTimeMillis(),
                List.copyOf(points),
                ringIds,
                List.copyOf(clusterSnaps),
                totalStrokes);
    }

    private static @NotNull List<Integer> getIntegers(SigilCluster cluster, List<List<Point>> contentStrokes, List<Integer> contentStrokeIds) {
        List<Integer> originalIds = new ArrayList<>();
        for (List<Point> stroke : cluster.strokes()) {
            int matchedIdx = -1;
            for (int j = 0; j < contentStrokes.size(); j++) {
                if (contentStrokes.get(j) == stroke || contentStrokes.get(j).equals(stroke)) {
                    matchedIdx = j;
                    break;
                }
            }
            if (matchedIdx >= 0) originalIds.add(contentStrokeIds.get(matchedIdx));
        }
        return originalIds;
    }
}
