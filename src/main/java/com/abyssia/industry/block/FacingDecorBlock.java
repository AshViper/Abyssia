package com.abyssia.industry.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A small decoration with FACING (6 directions, the face it was placed on) + WATERLOGGED and one shape per facing.
 * Used by the industrial valve and the work / warning lights.
 */
public abstract class FacingDecorBlock extends Block implements SimpleWaterloggedBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    protected FacingDecorBlock(Properties properties)
    {
        super(properties);
        for (Direction dir : Direction.values()) shapes.put(dir, shapeFor(dir));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(WATERLOGGED, false));
    }

    protected abstract VoxelShape shapeFor(Direction facing);

    /**
     * A box that is lo..hi (16 units) across the facing axis and from..to along it, with from..to counted from the
     * negative end for positive facings (UP / SOUTH / EAST) and mirrored for negative ones.
     */
    protected static VoxelShape oriented(Direction facing, double lo, double hi, double from, double to)
    {
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double a = positive ? from : 16 - to;
        double b = positive ? to : 16 - from;
        return switch (facing.getAxis())
        {
            case X -> Block.box(a, lo, lo, b, hi, hi);
            case Y -> Block.box(lo, a, lo, hi, b, hi);
            case Z -> Block.box(lo, lo, a, hi, hi, b);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(WATERLOGGED, water);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos)
    {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
