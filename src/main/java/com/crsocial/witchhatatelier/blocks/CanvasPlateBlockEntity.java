package com.crsocial.witchhatatelier.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the {@link CanvasPressurePlate}. It is a thin subclass of
 * {@link PlacedPaperBlockEntity} so the canvas/save/draw plumbing
 * ({@code instanceof PlacedPaperBlockEntity} checks in the canvas screen and
 * {@link com.crsocial.witchhatatelier.network.SaveGestureHandler}) works unchanged
 * and the gesture/paper-type NBT persistence is inherited for free.
 *
 * <p>Unlike a placed paper, the stored spell is re-cast every time the plate is
 * stepped on rather than once at draw time, and the entity is never marked spent.</p>
 */
public class CanvasPlateBlockEntity extends PlacedPaperBlockEntity {

    public CanvasPlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CANVAS_PLATE.get(), pos, state);
    }

    /** Whether a gesture has actually been drawn — empty plates drop blank (and stackable). */
    public boolean hasGesture() {
        return !getGestureData().isEmpty();
    }
}
