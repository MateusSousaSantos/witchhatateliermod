package com.crsocial.witchhatatelier.network;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.items.ModItems;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side handler for {@link SaveGesturePayload}.
 *
 * <p>Separated from the payload record so that business logic (inventory
 * manipulation, NBT building, item creation) does not live inside the
 * data-transfer object.</p>
 */
public final class SaveGestureHandler {

    private SaveGestureHandler() {}

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
            CompoundTag root = buildNbt(payload);

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

    /**
     * Serialises the payload's gesture data into a {@link CompoundTag} that is
     * stored on the resulting spell-paper item.
     */
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

