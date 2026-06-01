package com.crsocial.witchhatatelier.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side record of which players are currently channeling a spell, fed by
 * {@link com.crsocial.witchhatatelier.network.CastingStateHandler}.
 *
 * <p>{@link #onCastingStateChanged} drives the casting player animation layer
 * (see {@link CastingAnimation}) whenever a player's channeling state flips.</p>
 */
public final class ClientCastingState {

    private static final Set<Integer> CASTING = ConcurrentHashMap.newKeySet();

    private ClientCastingState() {}

    /** Updates the casting set for {@code entityId}; fires the hook on change. */
    public static void set(int entityId, boolean active) {
        boolean changed = active ? CASTING.add(entityId) : CASTING.remove(entityId);
        if (changed) {
            onCastingStateChanged(entityId, active);
        }
    }

    /** Whether the given player entity is currently channeling a spell. */
    public static boolean isCasting(int entityId) {
        return CASTING.contains(entityId);
    }

    private static void onCastingStateChanged(int entityId, boolean active) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (entity instanceof AbstractClientPlayer player) {
            CastingAnimation.setCasting(player, active);
        }
    }
}
