package com.crsocial.witchhatatelier.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class PlacedPaper extends Block {
    public static final MapCodec<PlacedPaper> CODEC = simpleCodec(PlacedPaper::new);
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    // Thickness of the paper on the wall (similar to item frame depth)
    protected static final float DEPTH = 0.0325F;
    protected static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 16.0 - DEPTH * 16, 16.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, DEPTH * 16);
    protected static final VoxelShape WEST_SHAPE = Block.box(16.0 - DEPTH * 16, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 0.0, DEPTH * 16, 16.0, 16.0);
    protected static final VoxelShape UP_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, DEPTH * 16, 16.0);
    protected static final VoxelShape DOWN_SHAPE = Block.box(0.0, 16.0 - DEPTH * 16, 0.0, 16.0, 16.0, 16.0);

    public PlacedPaper(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends PlacedPaper> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
        };
    }

    /**
     * Placement logic similar to ItemFrame - can be placed on any solid surface
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockPos blockpos = context.getClickedPos();
        BlockPos relativePos = blockpos.relative(direction.getOpposite());
        BlockState blockstate = context.getLevel().getBlockState(relativePos);

        // Check if the surface we're placing on is solid (similar to item frame logic)
        if (blockstate.isFaceSturdy(context.getLevel(), relativePos, direction)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }

        return null;
    }

    /**
     * Breaking logic - ensures the paper can survive on the surface it's attached to
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        // If the block behind us is removed, break this block (like item frame)
        Direction attachedDirection = state.getValue(FACING);
        if (direction.getOpposite() == attachedDirection) {
            if (!this.canSurvive(state, level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        BlockState attachedState = level.getBlockState(attachedPos);
        // Check if the surface we're attached to is still solid
        return attachedState.isFaceSturdy(level, attachedPos, direction);
    }

    /**
     * onUse method for future implementation - currently does nothing
     * Returns SUCCESS on client side and CONSUME on server side to prevent further interactions
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        // Placeholder for future implementation
        // Return PASS_TO_DEFAULT_BLOCK_INTERACTION to allow default block interaction
        // or return SUCCESS/CONSUME to handle it here
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Rotation and mirroring support for better placement
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
