package com.abyssia.habitat.relay;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
import org.jetbrains.annotations.Nullable;

/**
 * WR01 lower block: the housing (front panel on the FACING side), {@link WirelessPowerRelayBlockEntity}. Placed only by
 * the constructor (or /abyssia relay place) together with {@link WirelessPowerRelayTopBlock} above it; placing it
 * registers it in {@link RelayNetwork}, removing it unregisters it and removes the antenna. LINKED = has a link
 * (light 7 / 3, core texture on / off). Waterlogged so it can stand on the open seabed.
 */
public class WirelessPowerRelayBlock extends BaseEntityBlock implements SimpleWaterloggedBlock
{
    public static final MapCodec<WirelessPowerRelayBlock> CODEC = simpleCodec(WirelessPowerRelayBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(1.5, 2, 1.5, 14.5, 13, 14.5),
            Block.box(4, 13, 4, 12, 16, 12));

    public WirelessPowerRelayBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LINKED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
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
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) RelayNetwork.add(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server)
        {
            RelayNetwork.remove(server, pos);
            BlockState above = level.getBlockState(pos.above());
            if (above.is(RelayContent.RELAY_TOP.get())) clearCell(level, pos.above(), above);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Removes a relay half: water when it was waterlogged, else air. */
    static void clearCell(Level level, BlockPos pos, BlockState state)
    {
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new WirelessPowerRelayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) ->
        {
            if (be instanceof WirelessPowerRelayBlockEntity relay) relay.serverTick();
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        return status(level, pos, player);
    }

    /** Sneak + empty main hand: the relay status on the action bar (lower or upper half). */
    static InteractionResult status(Level level, BlockPos lower, Player player)
    {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) player.displayClientMessage(RelayNetwork.status(server, lower), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
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
