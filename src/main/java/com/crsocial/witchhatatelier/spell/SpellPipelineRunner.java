package com.crsocial.witchhatatelier.spell;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.ModCommands;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.network.SaveGesturePayload;
import com.crsocial.witchhatatelier.spell.cluster.SigilCluster;
import com.crsocial.witchhatatelier.spell.cluster.SigilClusterer;
import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.CompileResult;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraphBuilder;
import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.PointCloudPreprocessor;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.recognition.TemplateRegistry;
import com.crsocial.witchhatatelier.spell.trigger.TriggerEvaluator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the server-side spell pipeline: cluster → preprocess → recognize → compile.
 * Only triggered when the payload contains a closing ring.
 */
public final class SpellPipelineRunner {

    private SpellPipelineRunner() {}

    public static void run(SaveGesturePayload payload, Player player) {
        if (payload.points().isEmpty()) return;

        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (GesturePoint gp : payload.points()) {
            byStroke.computeIfAbsent(gp.strokeID(), k -> new ArrayList<>())
                    .add(new Point(gp.x(), gp.y(), gp.strokeID()));
        }

        List<Integer> ringIds = payload.activationRingStrokeIds();
        if (ringIds.isEmpty()) return;

        List<List<Point>> contentStrokes = new ArrayList<>();
        for (var e : byStroke.entrySet()) {
            if (ringIds.contains(e.getKey())) continue;
            contentStrokes.add(e.getValue());
        }
        if (contentStrokes.isEmpty()) {
            WitchHatAtelierMod.LOGGER.info(
                    "[SpellPipeline] No content strokes (ring={} of {} stroke(s)) — nothing to recognize.",
                    ringIds.size(), byStroke.size());
            return;
        }

        float microR    = Config.MICRO_MERGE_RADIUS.get().floatValue();
        float macroR    = Config.MACRO_MERGE_RADIUS.get().floatValue();
        int resampleN   = Config.RESAMPLE_N.get();
        float minScore  = Config.RECOGNITION_MIN_SCORE.get().floatValue();

        List<SigilCluster> clusters = SigilClusterer.cluster(contentStrokes, microR, macroR);
        PDollarPlusRecognizer recognizer = new PDollarPlusRecognizer(TemplateRegistry.get(), minScore);

        String who = player != null ? player.getScoreboardName() : "<unknown>";
        boolean debugMode = player != null && ModCommands.SPELL_DEBUGGERS.contains(player.getUUID());

        WitchHatAtelierMod.LOGGER.info(
                "[SpellPipeline] player='{}' ring={} content_strokes={} → {} sigil cluster(s); templates={}.",
                who, ringIds, contentStrokes.size(), clusters.size(), TemplateRegistry.get().size());

        List<RecognitionResult> recognitions = new ArrayList<>(clusters.size());
        for (int i = 0; i < clusters.size(); i++) {
            PointCloud raw = clusters.get(i).toPointCloud("candidate_" + i);
            PointCloudPreprocessor.Processed processed = PointCloudPreprocessor.process(raw, resampleN);
            RecognitionResult r = recognizer.match(processed);
            recognitions.add(r);
            WitchHatAtelierMod.LOGGER.info(
                    "[SpellPipeline] sigil[{}] → {} (score={}, angle={}rad).",
                    i, r.spellName(),
                    String.format(java.util.Locale.ROOT, "%.3f", r.confidenceScore()),
                    String.format(java.util.Locale.ROOT, "%.3f", r.indicativeAngle()));

            List<PDollarPlusRecognizer.Scored> ranked =
                    recognizer.matchVerbose(processed, TemplateRegistry.get().all());
            int topK = Math.min(3, ranked.size());
            for (int k = 0; k < topK; k++) {
                PDollarPlusRecognizer.Scored s = ranked.get(k);
                WitchHatAtelierMod.LOGGER.info(
                        "[SpellPipeline]   #{} {}:{} score={}",
                        k + 1, s.spellName(), s.variantName(),
                        String.format(java.util.Locale.ROOT, "%.3f", s.score()));
            }

            if (debugMode) {
                boolean recognized = !RecognitionResult.UNKNOWN.equals(r.spellName());
                int pct = Math.round(r.confidenceScore() * 100f);
                player.sendSystemMessage(Component.empty()
                        .append(Component.literal("[Sigil " + (i + 1) + "] ")
                                .withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal(recognized ? r.spellName() : "unrecognized")
                                .withStyle(recognized ? ChatFormatting.GOLD : ChatFormatting.GRAY))
                        .append(Component.literal(" (" + pct + "%)")
                                .withStyle(ChatFormatting.DARK_GRAY)));
                for (int k = 0; k < topK; k++) {
                    PDollarPlusRecognizer.Scored s = ranked.get(k);
                    int sPct = Math.round(s.score() * 100f);
                    player.sendSystemMessage(Component.empty()
                            .append(Component.literal("  #" + (k + 1) + " ")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(s.spellName() + ":" + s.variantName())
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" " + sPct + "%")
                                    .withStyle(ChatFormatting.DARK_GRAY)));
                }
            }
        }

        Map<String, Integer> recogCounts = new LinkedHashMap<>();
        for (RecognitionResult r : recognitions) {
            if (!RecognitionResult.UNKNOWN.equals(r.spellName())) {
                recogCounts.merge(r.spellName(), 1, Integer::sum);
            }
        }

        List<List<Point>> ringStrokes = new ArrayList<>();
        List<Integer> contentIds = new ArrayList<>();
        for (var e : byStroke.entrySet()) {
            if (ringIds.contains(e.getKey())) {
                ringStrokes.add(e.getValue());
            } else {
                contentIds.add(e.getKey());
            }
        }
        TriggerEvaluator.TriggerResult trigger =
                new TriggerEvaluator.TriggerResult(ringIds, contentIds);
        CastingContext ctx = buildCastingContext(payload, player);

        CompileResult result = SpellGraphBuilder.build(trigger, ringStrokes, clusters, recognitions, ctx);

        if (result.isSuccess()) {
            var graph = result.graph().get();
            WitchHatAtelierMod.LOGGER.info(
                    "[Compiler] Compiled spell graph for player='{}':\n{}", who, graph.toDebugString());
            if (player != null) {
                player.sendSystemMessage(Component.empty()
                        .append(Component.literal("◆ ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(graph.core().type().toString())
                                .withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(true))));
                player.sendSystemMessage(Component.literal("  ")
                        .append(Component.literal(graph.describeForm())
                                .withStyle(ChatFormatting.AQUA)));
                if (!recogCounts.isEmpty()) {
                    player.sendSystemMessage(Component.literal("  ")
                            .append(buildRecognitionSummary(recogCounts)));
                }
                if (debugMode) {
                    for (String line : graph.toDebugString().split("\n")) {
                        player.sendSystemMessage(Component.literal(line)
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        } else {
            WitchHatAtelierMod.LOGGER.info("[Compiler] Rejected: {}", result.rejectionReason());
            if (player != null) {
                assert result.rejectionReason() != null;
                player.sendSystemMessage(Component.empty()
                        .append(Component.literal("◆ ").withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal("✗ ").withStyle(ChatFormatting.RED))
                        .append(Component.literal(result.rejectionReason())
                                .withStyle(ChatFormatting.RED)));
                if (!recogCounts.isEmpty()) {
                    player.sendSystemMessage(Component.literal("  ")
                            .append(buildRecognitionSummary(recogCounts)));
                }
            }
        }
    }

    private static MutableComponent buildRecognitionSummary(Map<String, Integer> counts) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!first) line.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(e.getKey()).withStyle(ChatFormatting.AQUA));
            line.append(Component.literal(" ×" + e.getValue()).withStyle(ChatFormatting.GRAY));
            first = false;
        }
        return line;
    }

    private static CastingContext buildCastingContext(SaveGesturePayload payload, Player player) {
        BlockPos origin = payload.blockOrigin();
        CastingContext.MediumKind medium = origin != null
                ? CastingContext.MediumKind.PLACED_PAPER
                : CastingContext.MediumKind.PAPER_ITEM;

        Vector3f originWorld;
        if (origin != null) {
            originWorld = new Vector3f(origin.getX() + 0.5f, origin.getY() + 0.5f, origin.getZ() + 0.5f);
        } else if (player != null) {
            originWorld = new Vector3f((float) player.getX(),
                    (float) (player.getY() + player.getBbHeight() * 0.5),
                    (float) player.getZ());
        } else {
            originWorld = new Vector3f();
        }

        Vector3f surfaceNormal = new Vector3f(0f, 1f, 0f);
        return CastingContext.of(medium, originWorld, surfaceNormal);
    }
}
