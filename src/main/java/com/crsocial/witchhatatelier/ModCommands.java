package com.crsocial.witchhatatelier;

import com.crsocial.witchhatatelier.spell.recognition.CorpusCrossVal;
import com.crsocial.witchhatatelier.spell.recognition.CorpusReplay;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = WitchHatAtelierMod.MODID)
public class ModCommands {

    /** Players who have toggled verbose spell debug output on. Cleared on server restart. */
    public static final Set<UUID> SPELL_DEBUGGERS = Collections.synchronizedSet(new HashSet<>());

    /**
     * Per-player "what I meant to draw" label, set via {@code /spell label <value>}.
     * Stamped onto every recognition-log record (Phase 0) so playtesting produces
     * ground-truth-labeled data. Sticky until changed or cleared; lost on restart.
     */
    public static final Map<UUID, String> INTENDED_LABELS = Collections.synchronizedMap(new HashMap<>());

    /** The intended label for a player, or {@code null} if none is set. */
    public static String intendedLabel(UUID id) {
        return INTENDED_LABELS.get(id);
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("spell")
                .then(Commands.literal("debug")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal("This command must be run by a player."));
                            return 0;
                        }
                        UUID id = player.getUUID();
                        boolean nowOn;
                        if (SPELL_DEBUGGERS.contains(id)) {
                            SPELL_DEBUGGERS.remove(id);
                            nowOn = false;
                        } else {
                            SPELL_DEBUGGERS.add(id);
                            nowOn = true;
                        }
                        player.sendSystemMessage(Component.empty()
                            .append(Component.literal("[Spell Debug] ")
                                .withStyle(ChatFormatting.DARK_PURPLE))
                            .append(Component.literal(nowOn ? "ON" : "OFF")
                                .withStyle(nowOn ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
                        return 1;
                    }))
                .then(Commands.literal("label")
                    .then(Commands.literal("clear")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("This command must be run by a player."));
                                return 0;
                            }
                            INTENDED_LABELS.remove(player.getUUID());
                            player.sendSystemMessage(Component.empty()
                                .append(Component.literal("[Spell Label] ").withStyle(ChatFormatting.DARK_PURPLE))
                                .append(Component.literal("cleared").withStyle(ChatFormatting.GRAY)));
                            return 1;
                        }))
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("This command must be run by a player."));
                                return 0;
                            }
                            String value = StringArgumentType.getString(ctx, "value");
                            INTENDED_LABELS.put(player.getUUID(), value);
                            player.sendSystemMessage(Component.empty()
                                .append(Component.literal("[Spell Label] ").withStyle(ChatFormatting.DARK_PURPLE))
                                .append(Component.literal("now tagging draws as ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(value).withStyle(ChatFormatting.AQUA)));
                            return 1;
                        })))
                .then(Commands.literal("replay-corpus")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> runReplay(ctx.getSource(), null))
                    .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> runReplay(ctx.getSource(),
                                StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("crossval")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> runCrossval(ctx.getSource(), null))
                    .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> runCrossval(ctx.getSource(),
                                StringArgumentType.getString(ctx, "path")))))
        );
    }

    /**
     * Tier-2 harness entry point: replay the labeled corpus through the live recognition
     * pipeline against the currently-loaded templates and write the result in the
     * recognition-log schema for the offline Python tools to consume. Runnable from a
     * player, the console, or a command block — uses the source for feedback rather than
     * requiring a player, since the pipeline needs no world context.
     */
    private static int runReplay(CommandSourceStack source, String pathArg) {
        // Game dir is run/; the permanent corpus lives at <project>/dataset/.
        Path corpus = (pathArg != null)
                ? Path.of(pathArg)
                : FMLPaths.GAMEDIR.get().getParent().resolve("dataset").resolve("spell_corpus.jsonl");
        Path out = FMLPaths.GAMEDIR.get().resolve("logs").resolve("corpus_replay.jsonl");

        try {
            CorpusReplay.Summary s = CorpusReplay.replay(corpus, out);
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("[CorpusReplay] ").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal(s.total() + " sigils").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" — real " + s.correct() + "/" + s.realTotal() + " correct")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(", " + s.unknown() + " unknown").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(", garbage " + s.garbageRejected() + "/" + s.garbageTotal() + " rejected")
                            .withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("  → wrote " + out
                    + "\n  analyze: python scripts/tune_corpus.py run/logs/corpus_replay.jsonl")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        } catch (IOException | RuntimeException ex) {
            source.sendFailure(Component.literal("[CorpusReplay] failed: " + ex.getMessage()));
            return 0;
        }
    }

    /**
     * Tier-2 cross-validation: split the corpus by drawer (leave-one-drawer-out when ≥2
     * drawers exist, else stratified k-fold), promote each fold's train draws to templates,
     * and report held-out accuracy with base templates vs base + promoted coverage. The Δ is
     * the marginal value of that coverage — the user-independent generalization number the
     * plan's Definition of Done calls for.
     */
    private static int runCrossval(CommandSourceStack source, String pathArg) {
        Path corpus = (pathArg != null)
                ? Path.of(pathArg)
                : FMLPaths.GAMEDIR.get().getParent().resolve("dataset").resolve("spell_corpus.jsonl");
        Path out = FMLPaths.GAMEDIR.get().resolve("logs").resolve("corpus_crossval.jsonl");

        try {
            CorpusCrossVal.Summary s = CorpusCrossVal.run(corpus, out);
            String mode = s.userIndependent()
                    ? ("leave-one-drawer-out over " + s.drawers() + " drawers")
                    : "5-fold (single drawer — NOT user-independent)";
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("[CrossVal] ").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal(mode).withStyle(ChatFormatting.AQUA)), false);
            int basePct = s.realTotal() == 0 ? 0 : Math.round(100f * s.baseCorrect() / s.realTotal());
            int augPct = s.realTotal() == 0 ? 0 : Math.round(100f * s.augCorrect() / s.realTotal());
            int delta = augPct - basePct;
            source.sendSuccess(() -> Component.literal(
                    String.format("  held-out recall: base %d/%d (%d%%)  →  base+coverage %d/%d (%d%%)  [Δ %+d%%]",
                            s.baseCorrect(), s.realTotal(), basePct,
                            s.augCorrect(), s.realTotal(), augPct, delta))
                    .withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal(
                    String.format("  garbage rejected: base %d/%d  →  base+coverage %d/%d",
                            s.baseGarbageRej(), s.garbageTotal(),
                            s.augGarbageRej(), s.garbageTotal()))
                    .withStyle(ChatFormatting.GOLD), false);
            if (!s.userIndependent()) {
                source.sendSuccess(() -> Component.literal(
                        "  (collect a 2nd drawer's draws for a real user-independent split)")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
            source.sendSuccess(() -> Component.literal("  → wrote " + out)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        } catch (IOException | RuntimeException ex) {
            source.sendFailure(Component.literal("[CrossVal] failed: " + ex.getMessage()));
            return 0;
        }
    }
}
