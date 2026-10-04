package com.abyssia.habitat.relay;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * WR01 relay, upper half: thin antenna mast with the cross antenna and the core (no block entity). LINKED mirrors the
 * lower half (core lit / dim). Removing it removes the lower half.
 */
public class WirelessPowerRelayTopBlock extends Block implements SimpleWaterloggedBlock
{
    public static final MapCodec<WirelessPowerRelayTopBlock> CODEC = simpleCodec(WirelessPowerRelayTopBlock::new);
    public static final DirectionProperty FACING = WirelessPowerRelayBlock.FACING;
    public static final BooleanProperty LINKED = WirelessPowerRelayBlock.LINKED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(6, 0, 6, 10, 2, 10), Block.box(7, 2, 7, 9, 16, 9),
            Block.box(1, 10, 7, 15, 12, 9), Block.box(7, 10, 1, 9, 12, 15), Block.box(6, 9, 6, 10, 14, 10));

    public WirelessPowerRelayTopBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LINKED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, LINKED, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        return WirelessPowerRelayBlock.status(level, pos.below(), player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston)
    {
        if (!newState.is(this))
        {
            BlockState below = level.getBlockState(pos.below());
            if (below.is(RelayContent.RELAY.get())) WirelessPowerRelayBlock.clearCell(level, pos.below(), below);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
