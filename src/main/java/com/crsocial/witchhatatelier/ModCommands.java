package com.crsocial.witchhatatelier;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
        );
    }
}
