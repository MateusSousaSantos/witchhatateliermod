package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.client.ClientCastingState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for {@link CastingStatePayload}. Registered via
 * {@code registrar.playToClient}, so {@link #handle} only ever runs on the
 * client — {@link ClientCastingState} (which is client-only) is therefore
 * loaded lazily and safely on first dispatch.
 */
public final class CastingStateHandler {

    private CastingStateHandler() {}

    public static void handle(CastingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                ClientCastingState.set(payload.playerEntityId(), payload.active()));
    }
}
