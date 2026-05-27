package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.PlacedPaperBlockEntity;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.items.ModItems;
import com.crsocial.witchhatatelier.items.PaperType;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.spell.cluster.SigilCluster;
import com.crsocial.witchhatatelier.spell.cluster.SigilClusterer;
import com.crsocial.witchhatatelier.ModCommands;
import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.CompileResult;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraphBuilder;
import com.crsocial.witchhatatelier.spell.trigger.TriggerEvaluator;
import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.PointCloudPreprocessor;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.recognition.TemplateRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side handler for {@link SaveGesturePayload}.
 *
 * <h2>Two save paths</h2>
 * <ol>
 *   <li><b>Block-entity path</b> — when the payload carries a non-null
 *       {@code blockOrigin}, the gesture data is written to the
 *       {@link PlacedPaperBlockEntity} at that position.</li>
 *   <li><b>Item path</b> — when {@code blockOrigin} is null, the handler looks
 *       for a paper item in the player's hands:
 *       <ul>
 *         <li>Blank paper ({@link SpellPaperItem#isBlank()}) — consumes one, produces
 *             the corresponding inscribed spell-paper.</li>
 *         <li>Inscribed paper — overwrites the gesture data in place.</li>
 *         <li>Vanilla {@code minecraft:paper} — treated as
 *             {@link PaperType#MEDIUM_SQUARE}.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public final class SaveGestureHandler {

    private SaveGestureHandler() {}

    public static void handle(final SaveGesturePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();

            // ── Block-entity path ──────────────────────────────────────────────
            BlockPos origin = payload.blockOrigin();
            if (origin != null) {
                BlockEntity be = player.level().getBlockEntity(origin);
                if (be instanceof PlacedPaperBlockEntity placed) {
                    placed.setGestureData(buildNbt(payload));
                    WitchHatAtelierMod.LOGGER.info(
                            "[SaveGesture] Saved {} point(s) to placed_paper at {} for player '{}'.",
                            payload.points().size(), origin, player.getScoreboardName());
                } else {
                    WitchHatAtelierMod.LOGGER.warn(
                            "[SaveGesture] No PlacedPaperBlockEntity at {} for player '{}'.",
                            origin, player.getScoreboardName());
                }
                return;
            }

            // ── Item path ──────────────────────────────────────────────────────
            InteractionHand paperHand = findPaperHand(player);
            if (paperHand == null) {
                WitchHatAtelierMod.LOGGER.warn(
                        "[SaveGesture] Ignored – player '{}' had no paper in either hand.",
                        player.getScoreboardName());
                return;
            }

            ItemStack paperStack = player.getItemInHand(paperHand);
            CompoundTag nbt = buildNbt(payload);

            if (paperStack.getItem() instanceof SpellPaperItem paper) {
                if (paper.isBlank()) {
                    // Consume one blank → produce the corresponding inscribed item.
                    PaperType type = paper.getPaperType();
                    paperStack.shrink(1);
                    ItemStack result = new ItemStack(ModItems.inscribedFor(type).get());
                    result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    giveOrDrop(player, paperHand, paperStack, result);
                    WitchHatAtelierMod.LOGGER.info(
                            "[SaveGesture] Created {} with {} point(s) for player '{}'.",
                            type.getId() + "_spell_paper", payload.points().size(),
                            player.getScoreboardName());
                } else {
                    // Overwrite existing inscribed paper in-place.
                    ItemStack result = paperStack.copy();
                    result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    player.setItemInHand(paperHand, result);
                    WitchHatAtelierMod.LOGGER.info(
                            "[SaveGesture] Updated {} with {} point(s) for player '{}'.",
                            paper.getPaperType().getId() + "_spell_paper", payload.points().size(),
                            player.getScoreboardName());
                }
            } else if (paperStack.is(Items.PAPER)) {
                // Vanilla paper → medium-square inscribed paper.
                paperStack.shrink(1);
                ItemStack result = new ItemStack(ModItems.inscribedFor(PaperType.MEDIUM_SQUARE).get());
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                giveOrDrop(player, paperHand, paperStack, result);
                WitchHatAtelierMod.LOGGER.info(
                        "[SaveGesture] Converted vanilla paper → medium_square_spell_paper for player '{}'.",
                        player.getScoreboardName());
            }
        });

        // Recognition runs alongside (and after) any NBT writes. It only logs — no state mutation.
        context.enqueueWork(() -> runSpellPipeline(payload, context.player()));

        // Activation feedback: broadcast a sound + particle burst to everyone nearby
        // whenever the canvas detected a closing ring. Runs on the server thread so all
        // tracking clients see/hear the same effect.
        context.enqueueWork(() -> playActivationEffects(payload, context.player()));
    }

    // ── Activation effects (server-side broadcast) ──────────────────────────────

    private static void playActivationEffects(SaveGesturePayload payload, Player player) {
        if (payload.activationRingStrokeIds().isEmpty()) return;
        if (player == null) return;
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos origin = payload.blockOrigin();
        double x, y, z;
        if (origin != null) {
            x = origin.getX() + 0.5;
            y = origin.getY() + 0.5;
            z = origin.getZ() + 0.5;
        } else {
            x = player.getX();
            y = player.getY() + player.getBbHeight() * 0.5;
            z = player.getZ();
        }

        serverLevel.playSound(null, x, y, z,
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.SCRAPE,
                x, y, z, 40, 0.4, 0.4, 0.4, 0.6);

        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x, y, z, 20, 0.8, 0.2, 0.8, 0.3);
    }

    // ── Spell pipeline (cluster → preprocess → recognize) ───────────────────────

    private static void runSpellPipeline(SaveGesturePayload payload, Player player) {
        if (payload.points().isEmpty()) return;

        Map<Integer, List<Point>> byStroke = new LinkedHashMap<>();
        for (GesturePoint gp : payload.points()) {
            byStroke.computeIfAbsent(gp.strokeID(), k -> new ArrayList<>())
                    .add(new Point(gp.x(), gp.y(), gp.strokeID()));
        }

        List<Integer> ringIds = payload.activationRingStrokeIds();
        if (ringIds.isEmpty()) return; // only run the pipeline when a ring was closed

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

        float microR = Config.MICRO_MERGE_RADIUS.get().floatValue();
        float macroR = Config.MACRO_MERGE_RADIUS.get().floatValue();
        int resampleN = Config.RESAMPLE_N.get();
        float minScore = Config.RECOGNITION_MIN_SCORE.get().floatValue();

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

        // Group recognitions by name for the summary line (e.g. "fire ×1  column ×4")
        Map<String, Integer> recogCounts = new LinkedHashMap<>();
        for (RecognitionResult r : recognitions) {
            if (!RecognitionResult.UNKNOWN.equals(r.spellName())) {
                recogCounts.merge(r.spellName(), 1, Integer::sum);
            }
        }

        // ── Compile the spell graph ───────────────────────────────────────────────
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

        // Surface-normal resolution (placed-paper facing) is Phase 3; default to up.
        Vector3f surfaceNormal = new Vector3f(0f, 1f, 0f);
        return CastingContext.of(medium, originWorld, surfaceNormal);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static InteractionHand findPaperHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.is(Items.PAPER) || s.getItem() instanceof SpellPaperItem) {
                return hand;
            }
        }
        return null;
    }

    private static void giveOrDrop(Player player, InteractionHand hand,
                                   ItemStack handStack, ItemStack result) {
        if (handStack.isEmpty()) {
            player.setItemInHand(hand, result);
        } else if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }

    @NotNull
    static CompoundTag buildNbt(SaveGesturePayload payload) {
        CompoundTag root = new CompoundTag();
        root.putDouble("playerX", payload.playerPos().x);
        root.putDouble("playerY", payload.playerPos().y);
        root.putDouble("playerZ", payload.playerPos().z);

        ListTag pointsList = new ListTag();
        int maxStroke = 0;
        for (GesturePoint p : payload.points()) {
            CompoundTag pt = new CompoundTag();
            pt.putFloat("x", p.x());
            pt.putFloat("y", p.y());
            pt.putInt("strokeID", p.strokeID);
            pointsList.add(pt);
            if (p.strokeID > maxStroke) maxStroke = p.strokeID;
        }
        root.put("points", pointsList);
        root.putInt("strokeCount", payload.points().isEmpty() ? 0 : maxStroke + 1);
        return root;
    }
}
