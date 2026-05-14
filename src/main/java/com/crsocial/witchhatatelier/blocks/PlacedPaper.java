package com.crsocial.witchhatatelier.blocks;

import com.mojang.serialization.MapCodec;
import com.crsocial.witchhatatelier.client.gesture.GestureCanvasClient;
import com.crsocial.witchhatatelier.client.gesture.GesturePoint;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.items.Wand;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PlacedPaper extends Block implements EntityBlock {
    public static final MapCodec<PlacedPaper> CODEC = simpleCodec(PlacedPaper::new);
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    protected static final float DEPTH = 0.0325F;
    protected static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 16.0 - DEPTH * 16, 16.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, DEPTH * 16);
    protected static final VoxelShape WEST_SHAPE  = Block.box(16.0 - DEPTH * 16, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE  = Block.box(0.0, 0.0, 0.0, DEPTH * 16, 16.0, 16.0);
    protected static final VoxelShape UP_SHAPE    = Block.box(0.0, 0.0, 0.0, 16.0, DEPTH * 16, 16.0);
    protected static final VoxelShape DOWN_SHAPE  = Block.box(0.0, 16.0 - DEPTH * 16, 0.0, 16.0, 16.0, 16.0);

    public PlacedPaper(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends PlacedPaper> codec() { return CODEC; }

    // ── EntityBlock ──────────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PlacedPaperBlockEntity(pos, state);
    }

    // ── Block interaction ────────────────────────────────────────────────────────

    @Override
    protected @NotNull ItemInteractionResult useItemOn(
            @NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
            @NotNull BlockHitResult hitResult) {

        if (level.isClientSide) {
            openCanvasFromBlock(level, pos, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openCanvasFromBlock(Level level, BlockPos pos, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof PlacedPaperBlockEntity be)) return;
        boolean editable = stack.getItem() instanceof Wand;
        List<GesturePoint> points = SpellPaperItem.loadPointsFromTag(be.getGestureData());
        GestureCanvasClient.openCanvas(be.getPaperType(), points, editable, pos);
    }

    // ── Block state / shape ──────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST  -> WEST_SHAPE;
            case EAST  -> EAST_SHAPE;
            case UP    -> UP_SHAPE;
            case DOWN  -> DOWN_SHAPE;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockPos blockpos = context.getClickedPos();
        BlockPos relativePos = blockpos.relative(direction.getOpposite());
        BlockState blockstate = context.getLevel().getBlockState(relativePos);
        if (blockstate.isFaceSturdy(context.getLevel(), relativePos, direction)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }
        return null;
    }

    @Override
    protected @NotNull BlockState updateShape(
            @NotNull BlockState state, @NotNull Direction direction,
            @NotNull BlockState neighborState, @NotNull LevelAccessor level,
            @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        Direction attachedDirection = state.getValue(FACING);
        if (direction.getOpposite() == attachedDirection && !this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        BlockState attachedState = level.getBlockState(attachedPos);
        return attachedState.isFaceSturdy(level, attachedPos, direction);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
