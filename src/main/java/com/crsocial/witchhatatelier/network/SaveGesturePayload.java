package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.items.ModItems;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Client → Server packet sent when the player closes the gesture canvas.
 *
 * <p>Carries a flat, <em>normalized</em> ({@code [0,1]×[0,1]}) point cloud with
 * stroke IDs and the player's world position at the time of drawing. The server
 * handler writes this data to the player's off-hand {@code paper} item and
 * replaces it with a {@code spell_paper}.</p>
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
            buf.writeFloat(p.x);
            buf.writeFloat(p.y);
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

    // ── Server-side handler ────────────────────────────────────────────────────

    /**
     * Runs on the server thread.
     * <p>Looks for a paper item in the player's <strong>main hand first</strong>, then the
     * <strong>off-hand</strong>.  Accepts vanilla {@code minecraft:paper} (converts it to a
     * new {@code spell_paper}) or an existing {@code spell_paper} / {@code round_spell_paper}
     * (overwrites its data in-place).</p>
     */
    public static void handle(final SaveGesturePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();

            // Search main hand first (vanilla paper opened via right-click), then off-hand.
            InteractionHand paperHand = null;
            ItemStack paperStack = ItemStack.EMPTY;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack s = player.getItemInHand(hand);
                if (s.is(Items.PAPER) || s.is(ModItems.ROUND_PAPER.get())
                        || s.getItem() instanceof SpellPaperItem) {
                    paperHand = hand;
                    paperStack = s;
                    break;
                }
            }

            if (paperHand == null) {
                WitchHatAtelierMod.LOGGER.warn(
                        "[SaveGesture] Ignored – player '{}' had no paper in either hand.",
                        player.getScoreboardName());
                return;
            }

            // ── Build NBT ──────────────────────────────────────────────────────
            CompoundTag root = getCompoundTag(payload);

            // An already-drawn spell paper (stacksTo 1) is overwritten in-place.
            // A blank paper (vanilla paper or round_paper, which can stack) consumes one
            // and produces a new spell_paper / round_spell_paper.
            boolean isAlreadySpellPaper = paperStack.is(ModItems.SPELL_PAPER.get())
                    || paperStack.is(ModItems.ROUND_SPELL_PAPER.get());
            boolean isRoundPaper = paperStack.is(ModItems.ROUND_PAPER.get());

            if (isAlreadySpellPaper) {
                // Overwrite existing spell_paper in-place (single item, no stack concern).
                ItemStack result = paperStack.copy();
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                player.setItemInHand(paperHand, result);
            } else {
                // Blank paper (vanilla or round_paper) → consume exactly one, produce one spell_paper.
                paperStack.shrink(1);
                ItemStack result = new ItemStack(
                        isRoundPaper ? ModItems.ROUND_SPELL_PAPER.get() : ModItems.SPELL_PAPER.get());
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                if (player.getItemInHand(paperHand).isEmpty()) {
                    player.setItemInHand(paperHand, result);
                } else if (!player.getInventory().add(result)) {
                    player.drop(result, false);
                }
            }

            WitchHatAtelierMod.LOGGER.info(
                    "[SaveGesture] {} {} stroke(s), {} point(s) for player '{}'.",
                    isAlreadySpellPaper ? "Updated spell_paper –" : "Created spell_paper with",
                    root.getInt("strokeCount"), payload.points().size(), player.getScoreboardName());
        });
    }

    private static @NotNull CompoundTag getCompoundTag(SaveGesturePayload payload) {
        CompoundTag root = new CompoundTag();
        root.putDouble("playerX", payload.playerPos().x);
        root.putDouble("playerY", payload.playerPos().y);
        root.putDouble("playerZ", payload.playerPos().z);

        ListTag pointsList = new ListTag();
        int maxStroke = 0;
        for (GesturePoint p : payload.points()) {
            CompoundTag pt = new CompoundTag();
            pt.putFloat("x", p.x);
            pt.putFloat("y", p.y);
            pt.putInt("strokeID", p.strokeID);
            pointsList.add(pt);
            if (p.strokeID > maxStroke) maxStroke = p.strokeID;
        }
        root.put("points", pointsList);
        root.putInt("strokeCount", payload.points().isEmpty() ? 0 : maxStroke + 1);
        return root;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

