package com.abyssia.logistics;

import com.abyssia.industry.block.ValveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Logistics pump (LOG01): the valve's shape and water handling with a block entity. FACING is the clicked face: it
 * takes from the block behind it and pushes into the pipe network in front of it. Pipes connect to it like to a valve.
 */
public class LogisticsPumpBlock extends ValveBlock implements EntityBlock
{
    public LogisticsPumpBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new LogisticsPumpBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide || type != LogisticsContent.PUMP_ENTITY.get()) return null;
        return (lvl, pos, st, be) -> LogisticsPumpBlockEntity.serverTick(lvl, pos, st, (LogisticsPumpBlockEntity) be);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LogisticsPumpBlockEntity pump)
        {
            pump.rescan(level);
            player.displayClientMessage(Component.translatable("message.abyssia.logistics.pump.status", pump.pipeCount(), pump.destCount()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
