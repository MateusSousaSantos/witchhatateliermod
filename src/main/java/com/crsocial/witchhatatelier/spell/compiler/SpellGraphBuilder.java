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
 * Builds a {@link SpellGraph} from a closed ring's recognized content. The only
 * structural rule enforced is <b>exactly one Sigil per ring</b> (multi-element
 * spells use nested rings, per {@code docs/magic_system/02_spell_compiler.md}).
 *
 * <p>Signs combine freely: multiple occurrences of the same type stack, and
 * different types coexist. Manifestation signs follow a carrier/rider hierarchy
 * (Column/Dispersion are carriers, Bolt is a rider) exposed via
 * {@link SpellGraph#carriers()} / {@link SpellGraph#riders()}; Force signs
 * simply coexist. On a missing sigil the builder emits no graph and the
 * inscription falls back to the Prepared state.
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
    public static CompileResult build(TriggerResult trigger,
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

        // ── Rule: one Sigil ELEMENT per ring; repeats of that element stack ──────
        // Drawing the same element twice (e.g. fire + fire) is no longer an error:
        // the copies amplify power. Two *different* elements still need nested rings.
        if (sigils.isEmpty()) {
            return CompileResult.rejected("No sigil recognized inside the ring");
        }
        SigilType element = sigils.getFirst().type();
        SigilNode core = sigils.getFirst();
        for (SigilNode s : sigils) {
            if (s.type() != element) {
                return CompileResult.rejected("Mixed elements in one ring (" + element + " + " + s.type()
                        + "); only one element per ring — combine different elements via nested rings");
            }
            if (s.quality() > core.quality()) core = s; // best-drawn copy represents the stack
        }
        int sigilStack = sigils.size(); // repeats of the same element amplify the spell's power

        RingNode ring = buildRing(trigger.ringStrokeIds(), ringStrokes);
        SymmetryReport symmetry = SymmetryAnalyzer.analyze(core.centre(), signs);
        SizeReport size = buildSize(clusters, ringStrokes);

        return CompileResult.success(new SpellGraph(ring, core, sigilStack, List.copyOf(signs),
                Optional.empty(), symmetry, size));
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────────

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
