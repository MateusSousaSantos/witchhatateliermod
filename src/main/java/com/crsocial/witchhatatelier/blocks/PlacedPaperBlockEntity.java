package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.items.PaperType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the {@link PaperType} and drawn gesture data for a {@link PlacedPaper} block.
 *
 * <p>NBT layout:</p>
 * <ul>
 *   <li>{@code "paperType"} — string id of the PaperType (e.g. {@code "medium_square"})</li>
 *   <li>{@code "gestureData"} — compound tag holding the gesture point list written
 *       by {@link com.crsocial.witchhatatelier.network.SaveGestureHandler}</li>
 * </ul>
 */
public class PlacedPaperBlockEntity extends BlockEntity {

    private PaperType paperType = PaperType.MEDIUM_SQUARE;
    private CompoundTag gestureData = new CompoundTag();
    private boolean spent = false;
    /** In-plane drawing rotation (0–15, {@code RotationSegment} convention). Floor/ceiling only. */
    private int rotationSegment = 0;

    public PlacedPaperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_PAPER.get(), pos, state);
    }

    // ── Accessors ────────────────────────────────────────────────────────────────

    public PaperType getPaperType() { return paperType; }

    public void setPaperType(PaperType paperType) {
        this.paperType = paperType;
        setChanged();
        syncToClient();
    }

    public CompoundTag getGestureData() { return gestureData; }

    public void setGestureData(CompoundTag gestureData) {
        this.gestureData = gestureData;
        setChanged();
        syncToClient();
    }

    public int getRotationSegment() { return rotationSegment; }

    public void setRotationSegment(int rotationSegment) {
        this.rotationSegment = rotationSegment;
        setChanged();
        syncToClient();
    }

    public boolean isSpent() { return spent; }

    public void setSpent(boolean spent) {
        if (this.spent == spent) return;
        this.spent = spent;
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    // ── NBT persistence ──────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("paperType", paperType.getId());
        tag.put("gestureData", gestureData.copy());
        tag.putBoolean("spent", spent);
        tag.putInt("rotation", rotationSegment);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("paperType")) {
            paperType = PaperType.fromId(tag.getString("paperType"))
                    .orElse(PaperType.MEDIUM_SQUARE);
        }
        if (tag.contains("gestureData")) {
            gestureData = tag.getCompound("gestureData");
        }
        spent = tag.getBoolean("spent");
        rotationSegment = tag.getInt("rotation");
    }
}
