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
import com.crsocial.witchhatatelier.spell.cast.PlacedPaperCastManager;
import com.crsocial.witchhatatelier.spell.cast.SpellCastManager;
import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.CompileResult;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraphBuilder;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.MeaningEngine;
import com.crsocial.witchhatatelier.spell.meaning.SpellExecutor;
import com.crsocial.witchhatatelier.spell.trigger.TriggerEvaluator;
import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.PointCloudPreprocessor;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionLog;
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
import net.minecraft.server.level.ServerPlayer;
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

            // Save the gesture first; the item path returns the exact inscribed stack
            // so the channeled cast can be keyed to it regardless of which slot it
            // lands in. Then run the pipeline and broadcast activation feedback — all
            // on the server thread, in order.
            ItemStack inscribed = null;
            if (payload.blockOrigin() != null) {
                saveBlockPath(payload, player, payload.blockOrigin());
            } else {
                inscribed = saveItemPath(payload, player);
            }

            runSpellPipeline(payload, player, inscribed);
            playActivationEffects(payload, player);
        });
    }

    // ── Save paths ──────────────────────────────────────────────────────────────

    private static void saveBlockPath(SaveGesturePayload payload, Player player, BlockPos origin) {
        BlockEntity be = player.level().getBlockEntity(origin);
        if (be instanceof PlacedPaperBlockEntity placed) {
            if (placed.isSpent()) {
                WitchHatAtelierMod.LOGGER.info(
                        "[SaveGesture] Ignored – placed_paper at {} is spent (player '{}').",
                        origin, player.getScoreboardName());
                return;
            }
            placed.setGestureData(buildNbt(payload));
            WitchHatAtelierMod.LOGGER.info(
                    "[SaveGesture] Saved {} point(s) to placed_paper at {} for player '{}'.",
                    payload.points().size(), origin, player.getScoreboardName());
        } else {
            WitchHatAtelierMod.LOGGER.warn(
                    "[SaveGesture] No PlacedPaperBlockEntity at {} for player '{}'.",
                    origin, player.getScoreboardName());
        }
    }

    /**
     * Saves the gesture onto a held paper and returns the resulting inscribed stack
     * (or {@code null} if nothing was inscribed). The returned reference is the exact
     * stack carrying the gesture, even when it lands in the inventory rather than the
     * hand (e.g. drawn on a multi-count stack).
     */
    private static ItemStack saveItemPath(SaveGesturePayload payload, Player player) {
        InteractionHand paperHand = findPaperHand(player);
        if (paperHand == null) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[SaveGesture] Ignored – player '{}' had no paper in either hand.",
                    player.getScoreboardName());
            return null;
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
                return result;
            } else if (SpellPaperItem.isSpent(paperStack)) {
                WitchHatAtelierMod.LOGGER.info(
                        "[SaveGesture] Ignored – held {} is spent (player '{}').",
                        paper.getPaperType().getId() + "_spell_paper",
                        player.getScoreboardName());
                return null;
            } else {
                // Overwrite existing inscribed paper in-place.
                ItemStack result = paperStack.copy();
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                player.setItemInHand(paperHand, result);
                WitchHatAtelierMod.LOGGER.info(
                        "[SaveGesture] Updated {} with {} point(s) for player '{}'.",
                        paper.getPaperType().getId() + "_spell_paper", payload.points().size(),
                        player.getScoreboardName());
                return result;
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
            return result;
        }
        return null;
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

    private static void runSpellPipeline(SaveGesturePayload payload, Player player, ItemStack inscribed) {
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
            // When logging, run the traced variant so the decision trail is captured;
            // otherwise the plain match() (identical result, no trace overhead).
            PDollarPlusRecognizer.MatchTrace trace = null;
            RecognitionResult r;
            if (RecognitionLog.isEnabled()) {
                PDollarPlusRecognizer.Traced traced =
                        recognizer.matchTraced(processed, TemplateRegistry.get().all());
                r = traced.result();
                trace = traced.trace();
            } else {
                r = recognizer.match(processed);
            }
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

            // Phase 0 — persist the full recognition event (raw strokes, processed cloud,
            // every template's chamfer distance + score, final result, live thresholds).
            if (RecognitionLog.isEnabled()) {
                BlockPos bo = payload.blockOrigin();
                RecognitionLog.log(new RecognitionLog.Entry(
                        who,
                        player != null ? player.getStringUUID() : "<none>",
                        player != null ? ModCommands.intendedLabel(player.getUUID()) : null,
                        bo != null ? "PLACED_PAPER" : "PAPER_ITEM",
                        bo != null ? new int[]{bo.getX(), bo.getY(), bo.getZ()} : null,
                        debugMode,
                        i, clusters.size(), TemplateRegistry.get().size(),
                        clusters.get(i).strokes(), processed.cloud(), processed.indicativeAngle(),
                        r, ranked, trace));
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

            java.util.Optional<ExecutableSpell> executable = MeaningEngine.evaluate(graph, ctx);

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
                if (executable.isPresent()) {
                    player.sendSystemMessage(Component.empty()
                            .append(Component.literal("  ✓ ").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(executable.get().toLogString())
                                    .withStyle(ChatFormatting.DARK_GREEN)));
                } else {
                    player.sendSystemMessage(Component.empty()
                            .append(Component.literal("  · ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("Prepared (no matrix cell for this combination)")
                                    .withStyle(ChatFormatting.GRAY)));
                }
                if (debugMode) {
                    for (String line : graph.toDebugString().split("\n")) {
                        player.sendSystemMessage(Component.literal(line)
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }

            if (executable.isPresent()) {
                WitchHatAtelierMod.LOGGER.info(
                        "[MeaningEngine] player='{}' → {}", who, executable.get().toLogString());

                // ── Phase 3: dispatch to runtime ──────────────────────────────────
                if (player != null && player.level() instanceof ServerLevel serverLevel) {
                    if (payload.blockOrigin() == null && player instanceof ServerPlayer sp) {
                        // Hand cast → start a channeled, aim-following cast keyed to the
                        // inscribed paper just produced. The paper is consumed when the
                        // channel finishes or is canceled, so we do NOT consume here.
                        SpellCastManager.get().start(sp, executable.get(), inscribed);
                    } else if (payload.blockOrigin() != null
                            && executable.get().totalCostPerTick() > 0f) {
                        // Surface cast with a per-tick cost → sustained channel anchored to
                        // the placed_paper, active until its fuel drains; the block is marked
                        // spent by the manager when the cast ends (so we do NOT consume here).
                        PlacedPaperCastManager.get().start(
                                serverLevel, player, executable.get(), payload.blockOrigin());
                    } else {
                        // Instantaneous surface cast (no per-tick cost) → fire once and spend.
                        boolean fired = SpellExecutor.run(serverLevel, player, executable.get());
                        if (fired) {
                            consumeMedium(payload, player, serverLevel);
                        }
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

    // ── Medium consumption (Phase 3: Prepared → Activated → Used) ──────────────

    private static void consumeMedium(SaveGesturePayload payload, Player player, ServerLevel level) {
        BlockPos blockOrigin = payload.blockOrigin();
        if (blockOrigin != null) {
            // Surface cast: mark the placed_paper as spent so it stays in the world
            // but rejects any further casts. The inscription is considered consumed.
            if (level.getBlockEntity(blockOrigin) instanceof PlacedPaperBlockEntity placed) {
                placed.setSpent(true);
                WitchHatAtelierMod.LOGGER.info(
                        "[SaveGesture] Marked placed_paper at {} as spent after spell fired.", blockOrigin);
            }
            return;
        }
        // Hand cast: mark the held inscribed paper as spent (no shrink) so it
        // stays in the inventory but can't be re-cast or re-drawn.
        InteractionHand paperHand = findPaperHand(player);
        if (paperHand == null) return;
        ItemStack held = player.getItemInHand(paperHand);
        if (held.getItem() instanceof SpellPaperItem paper && !paper.isBlank()) {
            SpellPaperItem.markSpent(held);
            WitchHatAtelierMod.LOGGER.info(
                    "[SaveGesture] Marked held {} as spent after spell fired.",
                    paper.getPaperType().getId());
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
        Vector3f surfaceNormal;

        if (origin != null) {
            // Surface cast — anchor to the paper's outward face.
            net.minecraft.core.Direction facing = net.minecraft.core.Direction.UP;
            if (player != null
                    && player.level().getBlockEntity(origin) instanceof PlacedPaperBlockEntity be) {
                var state = be.getBlockState();
                if (state.hasProperty(com.crsocial.witchhatatelier.blocks.PlacedPaper.FACING)) {
                    facing = state.getValue(com.crsocial.witchhatatelier.blocks.PlacedPaper.FACING);
                }
            }
            surfaceNormal = new Vector3f(
                    facing.getStepX(), facing.getStepY(), facing.getStepZ());
            originWorld = new Vector3f(
                    origin.getX() + 0.5f + facing.getStepX() * 0.5f,
                    origin.getY() + 0.5f + facing.getStepY() * 0.5f,
                    origin.getZ() + 0.5f + facing.getStepZ() * 0.5f);
        } else if (player != null) {
            // Hand cast — origin is in front of the player along their look vector.
            var look = player.getLookAngle();
            surfaceNormal = new Vector3f((float) look.x, (float) look.y, (float) look.z);
            if (surfaceNormal.lengthSquared() < 1e-6f) surfaceNormal.set(0f, 1f, 0f);
            else surfaceNormal.normalize();
            originWorld = new Vector3f((float) player.getX(),
                    (float) player.getEyeY(),
                    (float) player.getZ())
                    .add(new Vector3f(surfaceNormal).mul(1.5f));
        } else {
            originWorld = new Vector3f();
            surfaceNormal = new Vector3f(0f, 1f, 0f);
        }

        return CastingContext.of(medium, originWorld, surfaceNormal, origin);
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
