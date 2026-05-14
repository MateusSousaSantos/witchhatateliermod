package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.PlacedPaperBlockEntity;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.items.ModItems;
import com.crsocial.witchhatatelier.items.PaperType;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side handler for {@link SaveGesturePayload}.
 *
 * <h2>Two save paths</h2>
 * <ol>
 *   <li><b>Block-entity path</b> — when the payload carries a non-null
 *       {@code blockOrigin}, the gesture data is written to the
 *       {@link PlacedPaperBlockEntity} at that position.</li>
 *   <li><b>Item path</b> — when {@code blockOrigin} is null, the handler looks
 *       for a paper item in the player's hands:
 *       <ul>
 *         <li>Blank paper ({@link SpellPaperItem#isBlank()}) — consumes one, produces
 *             the corresponding inscribed spell-paper.</li>
 *         <li>Inscribed paper — overwrites the gesture data in place.</li>
 *         <li>Vanilla {@code minecraft:paper} — treated as
 *             {@link PaperType#MEDIUM_SQUARE}.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public final class SaveGestureHandler {

    private SaveGestureHandler() {}

    public static void handle(final SaveGesturePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();

            // ── Block-entity path ──────────────────────────────────────────────
            BlockPos origin = payload.blockOrigin();
            if (origin != null) {
                BlockEntity be = player.level().getBlockEntity(origin);
                if (be instanceof PlacedPaperBlockEntity placed) {
                    placed.setGestureData(buildNbt(payload));
                    WitchHatAtelierMod.LOGGER.info(
                            "[SaveGesture] Saved {} point(s) to placed_paper at {} for player '{}'.",
                            payload.points().size(), origin, player.getScoreboardName());
                } else {
                    WitchHatAtelierMod.LOGGER.warn(
                            "[SaveGesture] No PlacedPaperBlockEntity at {} for player '{}'.",
                            origin, player.getScoreboardName());
                }
                return;
            }

            // ── Item path ──────────────────────────────────────────────────────
            InteractionHand paperHand = findPaperHand(player);
            if (paperHand == null) {
                WitchHatAtelierMod.LOGGER.warn(
                        "[SaveGesture] Ignored – player '{}' had no paper in either hand.",
                        player.getScoreboardName());
                return;
            }

            ItemStack paperStack = player.getItemInHand(paperHand);
            CompoundTag nbt = buildNbt(payload);

            if (paperStack.getItem() instanceof SpellPaperItem paper) {
                if (paper.isBlank()) {
                    // Consume one blank → produce the corresponding inscribed item.
                    PaperType type = paper.getPaperType();
                    paperStack.shrink(1);
                    ItemStack result = new ItemStack(ModItems.inscribedFor(type).get());
                    result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    giveOrDrop(player, paperHand, paperStack, result);
                    WitchHatAtelierMod.LOGGER.info(
                            "[SaveGesture] Created {} with {} point(s) for player '{}'.",
                            type.getId() + "_spell_paper", payload.points().size(),
                            player.getScoreboardName());
                } else {
                    // Overwrite existing inscribed paper in-place.
                    ItemStack result = paperStack.copy();
                    result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    player.setItemInHand(paperHand, result);
                    WitchHatAtelierMod.LOGGER.info(
                            "[SaveGesture] Updated {} with {} point(s) for player '{}'.",
                            paper.getPaperType().getId() + "_spell_paper", payload.points().size(),
                            player.getScoreboardName());
                }
            } else if (paperStack.is(Items.PAPER)) {
                // Vanilla paper → medium-square inscribed paper.
                paperStack.shrink(1);
                ItemStack result = new ItemStack(ModItems.inscribedFor(PaperType.MEDIUM_SQUARE).get());
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                giveOrDrop(player, paperHand, paperStack, result);
                WitchHatAtelierMod.LOGGER.info(
                        "[SaveGesture] Converted vanilla paper → medium_square_spell_paper for player '{}'.",
                        player.getScoreboardName());
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static InteractionHand findPaperHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.is(Items.PAPER) || s.getItem() instanceof SpellPaperItem) {
                return hand;
            }
        }
        return null;
    }

    private static void giveOrDrop(Player player, InteractionHand hand,
                                   ItemStack handStack, ItemStack result) {
        if (handStack.isEmpty()) {
            player.setItemInHand(hand, result);
        } else if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }

    @NotNull
    static CompoundTag buildNbt(SaveGesturePayload payload) {
        CompoundTag root = new CompoundTag();
        root.putDouble("playerX", payload.playerPos().x);
        root.putDouble("playerY", payload.playerPos().y);
        root.putDouble("playerZ", payload.playerPos().z);

        ListTag pointsList = new ListTag();
        int maxStroke = 0;
        for (GesturePoint p : payload.points()) {
            CompoundTag pt = new CompoundTag();
            pt.putFloat("x", p.x());
            pt.putFloat("y", p.y());
            pt.putInt("strokeID", p.strokeID);
            pointsList.add(pt);
            if (p.strokeID > maxStroke) maxStroke = p.strokeID;
        }
        root.put("points", pointsList);
        root.putInt("strokeCount", payload.points().isEmpty() ? 0 : maxStroke + 1);
        return root;
    }
}
