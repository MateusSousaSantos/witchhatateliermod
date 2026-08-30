package com.crsocial.witchhatatelier.spell.composition.manifest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** One or more world-placed blocks of a single {@link BlockState} — a Column, a Dispersion scatter, a bare placement. */
public record BlocksManifestation(List<BlockPos> positions, BlockState state) implements Manifestation {
}
