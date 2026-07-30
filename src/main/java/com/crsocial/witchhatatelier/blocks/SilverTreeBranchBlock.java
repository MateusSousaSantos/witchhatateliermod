package com.crsocial.witchhatatelier.blocks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wooden branch nub that grows off {@link BuddingSilverWoodBlock} through 4 stages
 * (small/medium/large/full), mirroring vanilla's {@code AmethystClusterBlock}. Supports all 6
 * {@link Direction}s because the trunk's own bark faces rotate with its placement axis (a trunk
 * lying on its side grows branches on its top/bottom/side bark faces, not its cut-end faces).
 */
public class SilverTreeBranchBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<SilverTreeBranchBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.FLOAT.fieldOf("height").forGetter(b -> b.height),
                    Codec.FLOAT.fieldOf("aabb_offset").forGetter(b -> b.aabbOffset),
                    propertiesCodec())
            .apply(instance, SilverTreeBranchBlock::new));

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /**
     * Only meaningful when {@link #FACING} is UP or DOWN (i.e. this branch grew off a trunk lying
     * on its side). The flat branch model's roll is otherwise fixed east/west-facing, which reads
     * correctly for the 4 horizontal FACING states but needs an extra 90° roll for UP/DOWN
     * branches on a trunk whose {@code AXIS} runs east-west, so the branch's flat face lines up
     * with the log's length instead of always looking the same regardless of host orientation.
     */
    public static final BooleanProperty ALIGN_NORTH_SOUTH = BooleanProperty.create("align_north_south");

    private final float height;
    private final float aabbOffset;
    private final VoxelShape northAabb;
    private final VoxelShape southAabb;
    private final VoxelShape eastAabb;
    private final VoxelShape westAabb;
    private final VoxelShape upAabb;
    private final VoxelShape downAabb;

    public SilverTreeBranchBlock(float height, float aabbOffset, BlockBehaviour.Properties properties) {
        super(properties);
        this.height = height;
        this.aabbOffset = aabbOffset;
        this.northAabb = Block.box(8 - aabbOffset, 8 - aabbOffset, 16 - height, 8 + aabbOffset, 8 + aabbOffset, 16);
        this.southAabb = Block.box(8 - aabbOffset, 8 - aabbOffset, 0, 8 + aabbOffset, 8 + aabbOffset, height);
        this.eastAabb = Block.box(0, 8 - aabbOffset, 8 - aabbOffset, height, 8 + aabbOffset, 8 + aabbOffset);
        this.westAabb = Block.box(16 - height, 8 - aabbOffset, 8 - aabbOffset, 16, 8 + aabbOffset, 8 + aabbOffset);
        this.upAabb = Block.box(8 - aabbOffset, 0, 8 - aabbOffset, 8 + aabbOffset, height, 8 + aabbOffset);
        this.downAabb = Block.box(8 - aabbOffset, 16 - height, 8 - aabbOffset, 8 + aabbOffset, 16, 8 + aabbOffset);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false)
                .setValue(ALIGN_NORTH_SOUTH, false));
    }

    @Override
    protected MapCodec<? extends SilverTreeBranchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, ALIGN_NORTH_SOUTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> northAabb;
            case SOUTH -> southAabb;
            case EAST -> eastAabb;
            case WEST -> westAabb;
            case UP -> upAabb;
            case DOWN -> downAabb;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                      LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
                .setValue(FACING, face)
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }
}
