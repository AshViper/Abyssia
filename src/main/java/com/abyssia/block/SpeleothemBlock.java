package com.abyssia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Deep-sea stalactite / stalagmite segment, stacked like pointed dripstone: {@code tip_direction} down hangs from a
 * ceiling, up grows from a floor, and where the two meet both tips become {@code tip_merge}, forming a column.
 * Mineral-laden water precipitates these instead of dripping: they never tick, drip or grow, so cave ceilings full
 * of them cost nothing at runtime. Waterloggable because they also form inside the gas pockets of underground lakes.
 */
public class SpeleothemBlock extends Block implements SimpleWaterloggedBlock
{
    public static final DirectionProperty TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape TIP_MERGE = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape TIP_UP = Block.box(5, 0, 5, 11, 11, 11);
    private static final VoxelShape TIP_DOWN = Block.box(5, 5, 5, 11, 16, 11);
    private static final VoxelShape FRUSTUM = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape MIDDLE = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape BASE = Block.box(2, 0, 2, 14, 16, 14);

    public SpeleothemBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIP_DIRECTION, Direction.UP).setValue(THICKNESS, DripstoneThickness.TIP)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(TIP_DIRECTION, THICKNESS, WATERLOGGED);
    }

    /** A segment of this speleothem pointing {@code direction}, with the given thickness; used by world generation. */
    public BlockState segment(Direction direction, DripstoneThickness thickness, boolean waterlogged)
    {
        return defaultBlockState().setValue(TIP_DIRECTION, direction).setValue(THICKNESS, thickness).setValue(WATERLOGGED, waterlogged);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return switch (state.getValue(THICKNESS))
        {
            case TIP_MERGE -> TIP_MERGE;
            case TIP -> state.getValue(TIP_DIRECTION) == Direction.DOWN ? TIP_DOWN : TIP_UP;
            case FRUSTUM -> FRUSTUM;
            case MIDDLE -> MIDDLE;
            case BASE -> BASE;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        Direction tip = state.getValue(TIP_DIRECTION);
        BlockPos supportPos = pos.relative(tip.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, tip) || isSegmentPointing(support, tip);
    }

    private boolean isSegmentPointing(BlockState state, Direction direction)
    {
        return state.is(this) && state.getValue(TIP_DIRECTION) == direction;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        LevelAccessor level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        // Clicking the underside of a block hangs a stalactite; anything else grows a stalagmite where it can.
        Direction preferred = context.getClickedFace() == Direction.DOWN ? Direction.DOWN : Direction.UP;
        for (Direction direction : new Direction[] {preferred, preferred.getOpposite()})
        {
            BlockState state = defaultBlockState().setValue(TIP_DIRECTION, direction).setValue(WATERLOGGED, water);
            if (state.canSurvive(level, pos)) return state.setValue(THICKNESS, thickness(level, pos, direction));
        }
        return null;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (direction != Direction.UP && direction != Direction.DOWN) return state;
        Direction tip = state.getValue(TIP_DIRECTION);
        // Losing its support breaks the whole hanging part, one segment per update, leaving the water it held.
        if (direction == tip.getOpposite() && !state.canSurvive(level, pos))
        {
            return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return state.setValue(THICKNESS, thickness(level, pos, tip));
    }

    /** Same rules as pointed dripstone: tip at the end, frustum behind it, base against the support, middle between. */
    private DripstoneThickness thickness(LevelReader level, BlockPos pos, Direction tip)
    {
        BlockState front = level.getBlockState(pos.relative(tip));
        if (isSegmentPointing(front, tip.getOpposite())) return DripstoneThickness.TIP_MERGE;
        if (!isSegmentPointing(front, tip)) return DripstoneThickness.TIP;
        DripstoneThickness frontThickness = front.getValue(THICKNESS);
        if (frontThickness == DripstoneThickness.TIP || frontThickness == DripstoneThickness.TIP_MERGE) return DripstoneThickness.FRUSTUM;
        return isSegmentPointing(level.getBlockState(pos.relative(tip.getOpposite())), tip) ? DripstoneThickness.MIDDLE : DripstoneThickness.BASE;
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }
}
