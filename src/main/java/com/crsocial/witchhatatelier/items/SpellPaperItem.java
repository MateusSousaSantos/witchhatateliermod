package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.client.gesture.GestureCanvasClient;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code spell_paper} item stores a normalized gesture point cloud drawn
 * by the player on the gesture canvas UI.
 *
 * <h2>Opening the canvas</h2>
 * <ul>
 *   <li><strong>Editable</strong> — right-click while holding a {@link Wand} in the
 *       <em>main hand</em> (paper may be in either hand).</li>
 *   <li><strong>Read-only</strong> — right-click without a wand in the main hand.</li>
 * </ul>
 */
public class SpellPaperItem extends Item {

    /** Whether this paper variant uses a circular canvas input shape. */
    private final boolean round;

    public SpellPaperItem(Properties properties, boolean round) {
        super(properties);
        this.round = round;
    }

    /** Returns {@code true} if this paper uses a circular canvas (round variant). */
    public boolean isRound() {
        return round;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Editable only when the player is holding a Wand in the main hand.
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

    /**
     * Reads the stored {@link GesturePoint} list from the item's {@code CustomData} NBT.
     * Returns an empty list if the item has no data yet.
     */
    private static List<GesturePoint> loadPoints(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return List.of();

        CompoundTag root = customData.copyTag();
        if (!root.contains("points", Tag.TAG_LIST)) return List.of();

        ListTag pointsList = root.getList("points", Tag.TAG_COMPOUND);
        List<GesturePoint> result = new ArrayList<>(pointsList.size());
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag pt = pointsList.getCompound(i);
            result.add(new GesturePoint(pt.getFloat("x"), pt.getFloat("y"), pt.getInt("strokeID")));
        }
        return result;
    }
}
