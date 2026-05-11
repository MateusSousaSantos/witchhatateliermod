package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Client → Server packet sent when the player closes the gesture canvas.
 *
 * <p>Carries a flat, <em>normalized</em> ({@code [0,1]×[0,1]}) point cloud with
 * stroke IDs and the player's world position at the time of drawing.</p>
 *
 * <p>Server-side handling (inventory manipulation, NBT building, item creation)
 * lives in {@link SaveGestureHandler}.</p>
 */
public record SaveGesturePayload(List<GesturePoint> points, Vec3 playerPos, BlockPos blockOrigin)
        implements CustomPacketPayload {

    public static final Type<SaveGesturePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "save_gesture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveGesturePayload> STREAM_CODEC =
            StreamCodec.of(SaveGesturePayload::encode, SaveGesturePayload::decode);

    // ── Codec ──────────────────────────────────────────────────────────────────

    private static void encode(RegistryFriendlyByteBuf buf, SaveGesturePayload payload) {
        buf.writeInt(payload.points().size());
        for (GesturePoint p : payload.points()) {
            buf.writeFloat(p.x());
            buf.writeFloat(p.y());
            buf.writeInt(p.strokeID);
        }
        buf.writeDouble(payload.playerPos().x);
        buf.writeDouble(payload.playerPos().y);
        buf.writeDouble(payload.playerPos().z);
        if (payload.blockOrigin() != null) {
            buf.writeBoolean(true);
            buf.writeInt(payload.blockOrigin().getX());
            buf.writeInt(payload.blockOrigin().getY());
            buf.writeInt(payload.blockOrigin().getZ());
        } else {
            buf.writeBoolean(false);
        }
    }

    private static SaveGesturePayload decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<GesturePoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            points.add(new GesturePoint(buf.readFloat(), buf.readFloat(), buf.readInt()));
        }
        Vec3 playerPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        BlockPos origin = null;
        boolean hasOrigin = buf.readBoolean();
        if (hasOrigin) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            origin = new BlockPos(x, y, z);
        }
        return new SaveGesturePayload(points, playerPos, origin);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
