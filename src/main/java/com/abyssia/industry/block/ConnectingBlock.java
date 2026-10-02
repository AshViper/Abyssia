package com.abyssia.industry.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A core with arms towards connected neighbours: north / south / east / west / up / down booleans + WATERLOGGED.
 * The shape is a box min..max (16 units) per part. Subclasses decide what they connect to.
 */
public abstract class ConnectingBlock extends Block implements SimpleWaterloggedBlock
{
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final VoxelShape[] shapes = new VoxelShape[64];

    protected ConnectingBlock(Properties properties, double min, double max)
    {
        super(properties);
        VoxelShape core = Block.box(min, min, min, max, max, max);
        VoxelShape[] arms = new VoxelShape[6];
        for (Direction dir : Direction.values()) arms[dir.ordinal()] = arm(dir, min, max);
        for (int mask = 0; mask < 64; mask++)
        {
            VoxelShape shape = core;
            for (Direction dir : Direction.values())
                if ((mask & (1 << dir.ordinal())) != 0) shape = Shapes.or(shape, arms[dir.ordinal()]);
            shapes[mask] = shape.optimize();
        }
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    private static VoxelShape arm(Direction dir, double min, double max)
    {
        return switch (dir)
        {
            case DOWN -> Block.box(min, 0, min, max, min, max);
            case UP -> Block.box(min, max, min, max, 16, max);
            case NORTH -> Block.box(min, min, 0, max, max, min);
            case SOUTH -> Block.box(min, min, max, max, max, 16);
            case WEST -> Block.box(0, min, min, min, max, max);
            case EAST -> Block.box(max, min, min, 16, max, max);
        };
    }

    /** Whether this block at pos connects towards its neighbour in direction dir. */
    protected abstract boolean connectsTo(BlockGetter level, BlockPos pos, Direction dir);

    /** Server-side hook when a connection appears or disappears. */
    protected void onConnectionChanged(LevelAccessor level, BlockPos pos)
    {
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST,
                BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(pos).getType() == Fluids.WATER);
        for (Direction dir : Direction.values())
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connectsTo(context.getLevel(), pos, dir));
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
        boolean connected = connectsTo(level, pos, direction);
        if (connected != state.getValue(property) && !level.isClientSide()) onConnectionChanged(level, pos);
        return state.setValue(property, connected);
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        int mask = 0;
        for (Direction dir : Direction.values())
            if (state.getValue(PROPERTY_BY_DIRECTION.get(dir))) mask |= 1 << dir.ordinal();
        return shapes[mask];
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos)
    {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        BlockState out = state;
        for (Direction dir : Direction.Plane.HORIZONTAL)
            out = out.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(dir)), state.getValue(PROPERTY_BY_DIRECTION.get(dir)));
        return out;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        BlockState out = state;
        for (Direction dir : Direction.Plane.HORIZONTAL)
            out = out.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(dir)), state.getValue(PROPERTY_BY_DIRECTION.get(dir)));
        return out;
    }
}
