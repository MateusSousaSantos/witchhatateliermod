package com.crsocial.witchhatatelier.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side record of which players are currently channeling a spell, fed by
 * {@link com.crsocial.witchhatatelier.network.CastingStateHandler}.
 *
 * <p>This is the attachment point for a future holding/casting player animation:
 * {@link #onCastingStateChanged} is the stub hook a renderer/animation layer will
 * read. No animation assets exist yet.</p>
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
        // TODO: drive the holding/casting player animation here once it exists.
    }
}
