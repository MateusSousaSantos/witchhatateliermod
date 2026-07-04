package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.client.gesture.CanvasProfile;
import com.crsocial.witchhatatelier.client.gesture.GestureCanvasClient;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.items.Wand;
import com.crsocial.witchhatatelier.network.SaveGestureHandler;
import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A pressure plate carrying a drawable {@link CanvasPlateBlockEntity} canvas. Right-click
 * (with a {@link Wand} to edit) opens the gesture canvas; the inscribed spell is then
 * re-cast every time an entity steps on the plate (rising edge), firing upward out of the
 * block face. Unlike a placed paper, the plate is reusable — it is never marked spent.
 *
 * <p>The cast reuses the whole recognize → compile → meaning → execute pipeline via
 * {@link SaveGestureHandler#castFromGesture}.</p>
 */
public class CanvasPressurePlate extends PressurePlateBlock implements EntityBlock {

    public CanvasPressurePlate(BlockSetType blockSetType, Properties properties) {
        super(blockSetType, properties);
    }

    // ── EntityBlock ──────────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CanvasPlateBlockEntity(pos, state);
    }

    /**
     * Bakes the plate's stored canvas (gesture + paper type) into the dropped item's
     * {@code BLOCK_ENTITY_DATA} component, so breaking and re-placing the plate carries
     * the inscribed spell with it. Vanilla {@link net.minecraft.world.item.BlockItem}
     * placement reads that component back into the fresh block entity automatically.
     * Blank plates drop clean (and stackable).
     */
    @Override
    protected @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof CanvasPlateBlockEntity plate && plate.hasGesture()) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    plate.saveToItem(drop, params.getLevel().registryAccess());
                }
            }
        }
        return drops;
    }

    // ── Rising-edge activation ───────────────────────────────────────────────────

    @Override
    protected void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Entity entity) {
        boolean wasPowered = getSignalForState(state) > 0;
        super.entityInside(state, level, pos, entity); // flips POWERED + schedules release
        // Fire only on the unpowered → powered transition so each stomp casts once.
        if (!level.isClientSide && !wasPowered
                && level instanceof ServerLevel serverLevel
                && getSignalForState(level.getBlockState(pos)) > 0) {
            fireStoredSpell(serverLevel, pos, entity);
        }
    }

    private void fireStoredSpell(ServerLevel level, BlockPos pos, @Nullable Entity trigger) {
        if (!(level.getBlockEntity(pos) instanceof CanvasPlateBlockEntity be)) return;

        CompoundTag gestureData = be.getGestureData();
        List<GesturePoint> points = SpellPaperItem.loadPointsFromTag(gestureData);
        if (points.isEmpty()) return;

        List<Integer> ringIds = new ArrayList<>();
        for (int id : gestureData.getIntArray("ringStrokeIds")) ringIds.add(id);
        if (ringIds.isEmpty()) return; // no closed ring stored — nothing compilable

        // Anchor the cast to the plate's top face, firing straight up.
        Vector3f normal = new Vector3f(0f, 1f, 0f);
        Vector3f origin = new Vector3f(pos.getX() + 0.5f, pos.getY() + 1.0f, pos.getZ() + 0.5f);
        CastingContext ctx = CastingContext.of(
                CastingContext.MediumKind.PLACED_PAPER, origin, normal, pos, 0f);

        Player player = trigger instanceof Player p ? p : null;
        SaveGestureHandler.castFromGesture(
                level, player, points, ringIds, ctx, pos,
                SaveGestureHandler.Dispatch.PLATE, null);
    }

    // ── Right-click → open canvas ────────────────────────────────────────────────

    @Override
    protected @NotNull ItemInteractionResult useItemOn(
            @NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
            @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            openCanvasFromBlock(level, pos, stack.getItem() instanceof Wand);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            openCanvasFromBlock(level, pos, false); // empty hand → view only (Wand required to edit)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openCanvasFromBlock(Level level, BlockPos pos, boolean editable) {
        if (!(level.getBlockEntity(pos) instanceof CanvasPlateBlockEntity be)) return;
        List<GesturePoint> points = SpellPaperItem.loadPointsFromTag(be.getGestureData());
        GestureCanvasClient.openCanvas(new CanvasProfile.CanvasPlateProfile(), points, editable, pos);
    }
}
