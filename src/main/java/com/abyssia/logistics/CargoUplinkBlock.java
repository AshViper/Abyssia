package com.abyssia.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Cargo uplink (LOG01): horizontal FACING, RECEIVER (false = send, true = receive). Sneak + empty hand switches the
 * mode, a plain right click opens the nine slots. The placer becomes the owner (the channel).
 */
public class CargoUplinkBlock extends BaseEntityBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty RECEIVER = BooleanProperty.create("receiver");

    public CargoUplinkBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(RECEIVER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, RECEIVER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof CargoUplinkBlockEntity be)
            be.setOwner(player.getUUID());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CargoUplinkBlockEntity be)) return InteractionResult.PASS;
        if (player.isShiftKeyDown())
        {
            boolean receiver = !state.getValue(RECEIVER);
            level.setBlock(pos, state.setValue(RECEIVER, receiver), 3);
            player.displayClientMessage(Component.translatable(receiver
                    ? "message.abyssia.logistics.uplink.mode.receive" : "message.abyssia.logistics.uplink.mode.send"), true);
            return InteractionResult.CONSUME;
        }
        if (be.owner() == null)
            player.displayClientMessage(Component.translatable("message.abyssia.logistics.uplink.no_owner"), true);
        else
            player.displayClientMessage(Component.translatable("message.abyssia.logistics.uplink.buffer",
                    CargoNetwork.get(level.getServer()).total(be.owner())), true);
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.openMenu(be);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        // a mode switch keeps the block entity and its slots
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CargoUplinkBlockEntity be)
        {
            Containers.dropContents(level, pos, be);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CargoUplinkBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide || type != LogisticsContent.UPLINK_ENTITY.get()) return null;
        return (lvl, pos, st, be) -> CargoUplinkBlockEntity.serverTick(lvl, pos, st, (CargoUplinkBlockEntity) be);
    }
}
