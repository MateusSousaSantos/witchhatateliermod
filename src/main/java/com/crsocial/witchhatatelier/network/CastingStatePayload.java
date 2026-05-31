package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server → Client packet announcing that a player started or stopped channeling
 * a spell. Sent to the caster and everyone tracking them so clients can drive a
 * (future) holding/casting animation. See {@link CastingStateHandler}.
 *
 * @param playerEntityId the casting player's entity id
 * @param active         {@code true} on channel start, {@code false} on end/cancel
 */
public record CastingStatePayload(int playerEntityId, boolean active)
        implements CustomPacketPayload {

    public static final Type<CastingStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "casting_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastingStatePayload> STREAM_CODEC =
            StreamCodec.of(CastingStatePayload::encode, CastingStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, CastingStatePayload payload) {
        buf.writeVarInt(payload.playerEntityId());
        buf.writeBoolean(payload.active());
    }

    private static CastingStatePayload decode(RegistryFriendlyByteBuf buf) {
        return new CastingStatePayload(buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
