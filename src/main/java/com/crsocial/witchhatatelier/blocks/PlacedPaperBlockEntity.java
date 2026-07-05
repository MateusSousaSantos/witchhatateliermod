package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.items.PaperType;
import com.crsocial.witchhatatelier.spell.feedback.InscriptionSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
    /**
     * Stroke IDs whose cluster the recognizer could not read, written by the spell
     * pipeline. The renderer tints these red — but only once {@link #spent} is set (the
     * cast has finished) — as retrospective "these didn't read" feedback.
     */
    private int[] unrecognizedStrokeIds = new int[0];
    /**
     * What the spell pipeline concluded about the current drawing, written after every
     * save (ring-less included). Read client-side by the canvas status header; synced
     * with the rest of the BE state. {@code null} until the first pipeline pass.
     */
    @Nullable
    private InscriptionSummary inscription = null;

    public PlacedPaperBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PLACED_PAPER.get(), pos, state);
    }

    /** Lets subclasses (e.g. the canvas pressure plate) supply their own block-entity type. */
    protected PlacedPaperBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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

    public int[] getUnrecognizedStrokeIds() { return unrecognizedStrokeIds; }

    public void setUnrecognizedStrokeIds(int[] ids) {
        this.unrecognizedStrokeIds = ids == null ? new int[0] : ids;
        setChanged();
        syncToClient();
    }

    @Nullable
    public InscriptionSummary getInscription() { return inscription; }

    public void setInscription(@Nullable InscriptionSummary inscription) {
        this.inscription = inscription;
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
        tag.putIntArray("unrecognizedStrokes", unrecognizedStrokeIds);
        if (inscription != null) {
            tag.put(InscriptionSummary.NBT_KEY, inscription.toNbt());
        }
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
        unrecognizedStrokeIds = tag.getIntArray("unrecognizedStrokes");
        inscription = InscriptionSummary.fromNbt(tag).orElse(null);
    }
}
