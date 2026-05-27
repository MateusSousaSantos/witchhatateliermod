package com.crsocial.witchhatatelier;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = WitchHatAtelierMod.MODID)
public class ModCommands {

    /** Players who have toggled verbose spell debug output on. Cleared on server restart. */
    public static final Set<UUID> SPELL_DEBUGGERS = Collections.synchronizedSet(new HashSet<>());

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
        );
    }
}
