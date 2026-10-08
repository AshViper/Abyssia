package com.abyssia.industry.block;

import com.abyssia.industry.ExcavatorStructure;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.blockentity.ExcavatorPartBlockEntity;
import com.abyssia.industry.blockentity.IndustryBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * ORE01 invisible collision cell of the excavator multiblock (drawn by the master renderer). Right-click opens the
 * master GUI; energy and item capabilities are forwarded to the master (ModIndustry); breaking a part removes the
 * whole machine.
 */
public class ExcavatorPartBlock extends BaseEntityBlock implements SimpleWaterloggedBlock
{
    public static final MapCodec<ExcavatorPartBlock> CODEC = simpleCodec(ExcavatorPartBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public ExcavatorPartBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(WATERLOGGED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ExcavatorPartBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof ExcavatorPartBlockEntity part) || part.controller() == null)
            return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos master = part.controller();
        if (level.getBlockEntity(master) instanceof IndustryBlockEntity be && player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.openMenu(be, buf ->
            {
                buf.writeBlockPos(master);
                buf.writeByte(MachineKind.ABYSSAL_EXCAVATOR.ordinal());
            });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        // read the master before the block entity goes; removing the other cells re-enters here harmlessly
        BlockPos master = null;
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ExcavatorPartBlockEntity part)
            master = part.controller();
        super.onRemove(state, level, pos, newState, moved);
        if (master != null) ExcavatorStructure.remove(level, master);
    }

    @Override
    protected FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }
}
