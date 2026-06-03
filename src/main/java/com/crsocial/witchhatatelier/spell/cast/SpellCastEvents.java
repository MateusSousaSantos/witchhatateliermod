package com.crsocial.witchhatatelier.spell.cast;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus subscribers that drive {@link SpellCastManager}. Mirrors the
 * {@code @EventBusSubscriber} idiom used by {@code events.ModEvents}.
 */
@EventBusSubscriber(modid = WitchHatAtelierMod.MODID)
public final class SpellCastEvents {

    private SpellCastEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SpellCastManager.get().tickAll(event.getServer());
        PlacedPaperCastManager.get().tickAll(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCastManager.get().cancel(player, CancelReason.LOGOUT);
        }
    }
}
