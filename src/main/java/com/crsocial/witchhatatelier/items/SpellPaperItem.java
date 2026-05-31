package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.blocks.PlacedPaper;
import com.crsocial.witchhatatelier.blocks.PlacedPaperBlockEntity;
import com.crsocial.witchhatatelier.client.gesture.GestureCanvasClient;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents any paper item — both blank (stackable, no gesture data) and
 * inscribed (non-stackable, carries a gesture point cloud in NBT).
 *
 * <h2>Interactions</h2>
 * <ul>
 *   <li><b>Right-click in air / on non-solid face</b> — opens the gesture canvas
 *       ({@link #use}). Editable when a {@link Wand} is in the main hand.</li>
 *   <li><b>Right-click on a solid block face (blank paper only)</b> — places the
 *       {@code placed_paper} block at the adjacent position and sets the
 *       {@link PlacedPaperBlockEntity}'s {@link PaperType} ({@link #useOn}).</li>
 * </ul>
 */
public class SpellPaperItem extends Item {

    private final PaperType paperType;
    private final boolean blank;

    public SpellPaperItem(Properties properties, PaperType paperType, boolean blank) {
        super(properties);
        this.paperType = paperType;
        this.blank = blank;
    }

    public PaperType getPaperType() { return paperType; }

    /** {@code true} for blank (undrawn) paper; {@code false} for inscribed spell paper. */
    public boolean isBlank() { return blank; }

    // ── Ground placement ─────────────────────────────────────────────────────────

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        PlacedPaper block = ModBlocks.placedFor(paperType).get();
        BlockPlaceContext blockCtx = new BlockPlaceContext(context);
        BlockState paperState = block.getStateForPlacement(blockCtx);
        if (paperState == null) return InteractionResult.PASS;

        BlockPos placePos = blockCtx.getClickedPos();

        if (!level.isClientSide) {
            level.setBlockAndUpdate(placePos, paperState);

            if (level.getBlockEntity(placePos) instanceof PlacedPaperBlockEntity be) {
                be.setPaperType(paperType);
                if (!blank) {
                    CustomData customData = context.getItemInHand().get(DataComponents.CUSTOM_DATA);
                    if (customData != null) {
                        be.setGestureData(customData.copyTag());
                    }
                    if (isSpent(context.getItemInHand())) {
                        be.setSpent(true);
                    }
                }
            }
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ── Canvas drawing ───────────────────────────────────────────────────────────

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!blank && isSpent(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        boolean editable = player.getMainHandItem().getItem() instanceof Wand;

        if (level.isClientSide) {
            openCanvasClient(stack, editable);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openCanvasClient(ItemStack stack, boolean editable) {
        List<GesturePoint> points = loadPoints(stack);
        GestureCanvasClient.openCanvas(stack, points, editable);
    }

    // ── NBT helpers ──────────────────────────────────────────────────────────────

    /**
     * Reads the gesture point list from the item's {@code CustomData} NBT.
     * Returns an empty list if there is no data.
     */
    public static List<GesturePoint> loadPoints(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return List.of();
        return loadPointsFromTag(customData.copyTag());
    }

    /**
     * Reads gesture points from a raw compound tag (used by {@link PlacedPaperBlockEntity}).
     */
    public static List<GesturePoint> loadPointsFromTag(CompoundTag root) {
        if (root == null || !root.contains("points", Tag.TAG_LIST)) return List.of();
        ListTag pointsList = root.getList("points", Tag.TAG_COMPOUND);
        List<GesturePoint> result = new ArrayList<>(pointsList.size());
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag pt = pointsList.getCompound(i);
            result.add(new GesturePoint(pt.getFloat("x"), pt.getFloat("y"), pt.getInt("strokeID")));
        }
        return result;
    }

    // ── Spent flag ───────────────────────────────────────────────────────────────

    /** {@code true} if the inscribed paper has already been used to cast its spell. */
    public static boolean isSpent(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean("spent");
    }

    /** Flags this inscribed paper as spent so it can no longer cast or be re-drawn. */
    public static void markSpent(ItemStack stack) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.putBoolean("spent", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!blank && isSpent(stack)) {
            tooltip.add(Component.translatable("item.witchhatateliermod.spell_paper.spent")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
