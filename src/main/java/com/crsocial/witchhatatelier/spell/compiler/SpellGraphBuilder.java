package com.crsocial.witchhatatelier.spell.compiler;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.cluster.SigilCluster;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.trigger.TriggerEvaluator.TriggerResult;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a {@link SpellGraph} from a closed ring's recognized content. Enforces
 * the structural compilation rules from {@code docs/magic_system/02_spell_compiler.md}:
 *
 * <ul>
 *   <li>exactly one Sigil per ring,</li>
 *   <li>at most one Manifestation sign type per ring,</li>
 *   <li>at most one Force sign type per ring.</li>
 * </ul>
 *
 * Multiple occurrences of the <em>same</em> sign type are valid (stacking). On
 * any violation the builder emits no graph and logs the reason; the inscription
 * then falls back to the Prepared state.
 */
public final class SpellGraphBuilder {

    private SpellGraphBuilder() {}

    /**
     * @param trigger      the detected ring (stroke IDs + enclosed content)
     * @param ringStrokes  the ring strokes' point geometry, for {@link RingNode}
     * @param clusters     content clusters, parallel to {@code recognitions}
     * @param recognitions recognizer result per cluster, parallel to {@code clusters}
     * @param ctx          per-cast environment (currently unused by the builder;
     *                     threaded for the meaning engine and future ink/wand systems)
     */
    public static Optional<SpellGraph> build(TriggerResult trigger,
                                             List<List<Point>> ringStrokes,
                                             List<SigilCluster> clusters,
                                             List<RecognitionResult> recognitions,
                                             CastingContext ctx) {
        if (clusters.size() != recognitions.size()) {
            throw new IllegalArgumentException(
                    "clusters/recognitions size mismatch: " + clusters.size() + " vs " + recognitions.size());
        }

        List<SigilNode> sigils = new ArrayList<>();
        List<SignNode> signs = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            RecognitionResult r = recognitions.get(i);
            if (RecognitionResult.UNKNOWN.equals(r.spellName())) continue;

            Vector2f centre = centroid(clusters.get(i));

            Optional<SigilType> sigil = SigilType.fromSpellName(r.spellName());
            if (sigil.isPresent()) {
                sigils.add(new SigilNode(sigil.get(), centre, r.confidenceScore()));
                continue;
            }
            Optional<SignType> sign = SignType.fromSpellName(r.spellName());
            if (sign.isPresent()) {
                signs.add(new SignNode(sign.get(), centre,
                        (float) Math.toDegrees(r.indicativeAngle()), r.confidenceScore()));
                continue;
            }
            WitchHatAtelierMod.LOGGER.info(
                    "[Compiler] Ignoring recognized but non-sigil/non-sign content '{}'.", r.spellName());
        }

        // ── Rule: exactly one Sigil per ring ────────────────────────────────────
        if (sigils.isEmpty()) {
            WitchHatAtelierMod.LOGGER.info(
                    "[Compiler] Rejected: no sigil recognized inside the ring. Falling back to Prepared.");
            return Optional.empty();
        }
        if (sigils.size() > 1) {
            WitchHatAtelierMod.LOGGER.info(
                    "[Compiler] Rejected: {} sigils in one ring (only one allowed; combine via nested rings). "
                            + "Falling back to Prepared.", sigils.size());
            return Optional.empty();
        }
        SigilNode core = sigils.getFirst();

        // ── Rule: at most one Manifestation sign type ───────────────────────────
        if (distinctTypeCount(signs, SignType.Tier.MANIFESTATION) > 1) {
            WitchHatAtelierMod.LOGGER.info(
                    "[Compiler] Rejected: multiple Manifestation sign types in one ring. Falling back to Prepared.");
            return Optional.empty();
        }
        // ── Rule: at most one Force sign type ────────────────────────────────────
        if (distinctTypeCount(signs, SignType.Tier.FORCE) > 1) {
            WitchHatAtelierMod.LOGGER.info(
                    "[Compiler] Rejected: multiple Force sign types in one ring. Falling back to Prepared.");
            return Optional.empty();
        }

        RingNode ring = buildRing(trigger.ringStrokeIds(), ringStrokes);
        SymmetryReport symmetry = SymmetryAnalyzer.analyze(core.centre(), signs);
        SizeReport size = buildSize(clusters, ringStrokes);

        return Optional.of(new SpellGraph(ring, core, List.copyOf(signs),
                Optional.empty(), symmetry, size));
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────────

    private static long distinctTypeCount(List<SignNode> signs, SignType.Tier tier) {
        return signs.stream()
                .map(SignNode::type)
                .filter(t -> t.tier() == tier)
                .distinct()
                .count();
    }

    private static Vector2f centroid(SigilCluster cluster) {
        float sx = 0f, sy = 0f;
        int n = 0;
        for (List<Point> stroke : cluster.strokes()) {
            for (Point p : stroke) {
                sx += p.x();
                sy += p.y();
                n++;
            }
        }
        return n == 0 ? new Vector2f(0f, 0f) : new Vector2f(sx / n, sy / n);
    }

    private static RingNode buildRing(List<Integer> strokeIds, List<List<Point>> ringStrokes) {
        float sx = 0f, sy = 0f;
        int n = 0;
        for (List<Point> stroke : ringStrokes) {
            for (Point p : stroke) {
                sx += p.x();
                sy += p.y();
                n++;
            }
        }
        if (n == 0) {
            return new RingNode(List.copyOf(strokeIds), 360f, 0f);
        }
        float cx = sx / n, cy = sy / n;
        float meanRadius = 0f;
        for (List<Point> stroke : ringStrokes) {
            for (Point p : stroke) {
                float dx = p.x() - cx, dy = p.y() - cy;
                meanRadius += (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        meanRadius /= n;
        // The trigger evaluator already confirmed closure; precise arc-coverage is future work.
        return new RingNode(List.copyOf(strokeIds), 360f, meanRadius);
    }

    private static SizeReport buildSize(List<SigilCluster> clusters, List<List<Point>> ringStrokes) {
        float[] content = emptyBox();
        for (SigilCluster c : clusters) {
            for (List<Point> stroke : c.strokes()) {
                for (Point p : stroke) accumulate(content, p.x(), p.y());
            }
        }
        float contentArea = boxArea(content);

        float[] ring = emptyBox();
        for (List<Point> stroke : ringStrokes) {
            for (Point p : stroke) accumulate(ring, p.x(), p.y());
        }
        float ringArea = boxArea(ring);

        float normalized = ringArea <= 0f ? 0f : Math.min(1f, contentArea / ringArea);
        return new SizeReport(contentArea, normalized);
    }

    private static float[] emptyBox() {
        return new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    }

    private static void accumulate(float[] box, float x, float y) {
        if (x < box[0]) box[0] = x;
        if (y < box[1]) box[1] = y;
        if (x > box[2]) box[2] = x;
        if (y > box[3]) box[3] = y;
    }

    private static float boxArea(float[] box) {
        if (box[2] < box[0] || box[3] < box[1]) return 0f;
        return (box[2] - box[0]) * (box[3] - box[1]);
    }
}
