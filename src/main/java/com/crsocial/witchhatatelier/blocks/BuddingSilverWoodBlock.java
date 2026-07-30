package com.crsocial.witchhatatelier.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.EnumMap;
import java.util.Map;

/**
 * A tree-trunk block that, like vanilla's {@code BuddingAmethystBlock}, randomly sprouts a
 * {@link SilverTreeBranchBlock} chain on a neighboring face. Growth is restricted to the trunk's
 * 4 bark faces — the ones perpendicular to its {@link RotatedPillarBlock#AXIS} — never the two
 * cut-end faces along the axis. Since {@code AXIS} rotates with how the trunk was placed, a trunk
 * lying on its side grows branches on a different set of faces (including up/down) than one
 * standing upright.
 */
public class BuddingSilverWoodBlock extends RotatedPillarBlock {

    public static final MapCodec<BuddingSilverWoodBlock> CODEC = simpleCodec(BuddingSilverWoodBlock::new);

    private static final Map<Direction.Axis, Direction[]> BARK_DIRECTIONS = new EnumMap<>(Direction.Axis.class);

    static {
        BARK_DIRECTIONS.put(Direction.Axis.X, new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH});
        BARK_DIRECTIONS.put(Direction.Axis.Y, new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST});
        BARK_DIRECTIONS.put(Direction.Axis.Z, new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST});
    }

    public BuddingSilverWoodBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BuddingSilverWoodBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }
        Direction[] bark = BARK_DIRECTIONS.get(state.getValue(AXIS));
        Direction direction = bark[random.nextInt(bark.length)];
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        Block grown = null;

        if (canBranchGrowAtState(targetState)) {
            grown = ModBlocks.SILVER_TREE_BRANCH_SMALL.get();
        } else if (targetState.is(ModBlocks.SILVER_TREE_BRANCH_SMALL.get())
                && targetState.getValue(SilverTreeBranchBlock.FACING) == direction) {
            grown = ModBlocks.SILVER_TREE_BRANCH_MEDIUM.get();
        } else if (targetState.is(ModBlocks.SILVER_TREE_BRANCH_MEDIUM.get())
                && targetState.getValue(SilverTreeBranchBlock.FACING) == direction) {
            grown = ModBlocks.SILVER_TREE_BRANCH_LARGE.get();
        } else if (targetState.is(ModBlocks.SILVER_TREE_BRANCH_LARGE.get())
                && targetState.getValue(SilverTreeBranchBlock.FACING) == direction) {
            grown = ModBlocks.SILVER_TREE_BRANCH.get();
        }

        if (grown != null) {
            // Only matters when direction is UP/DOWN (i.e. this trunk is lying on its side): roll
            // the branch's flat face to align with the trunk's own length instead of always
            // defaulting to an east/west-facing roll regardless of how the trunk is oriented.
            boolean alignNorthSouth = state.getValue(AXIS) == Direction.Axis.X;
            level.setBlockAndUpdate(targetPos, grown.defaultBlockState()
                    .setValue(SilverTreeBranchBlock.FACING, direction)
                    .setValue(SilverTreeBranchBlock.WATERLOGGED,
                            targetState.getFluidState().getType() == Fluids.WATER)
                    .setValue(SilverTreeBranchBlock.ALIGN_NORTH_SOUTH, alignNorthSouth));
        }
    }

    private static boolean canBranchGrowAtState(BlockState state) {
        return state.isAir()
                || (state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8);
    }
}
