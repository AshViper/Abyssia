package com.abyssia.industry.block;

import com.abyssia.industry.ExcavatorStructure;
import com.abyssia.industry.ExcavatorTier;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.blockentity.AbyssalExcavatorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

/**
 * Abyssal Excavator: FACING + WATERLOGGED (from IndustryEntityBlock) + LIT. The tier carries every number of the
 * machine (see {@link ExcavatorTier}); Mk1 and Mk2 are registered. This block is the MASTER of a 3 x 3 x 3 multiblock
 * ({@link ExcavatorStructure}, parts = {@link ExcavatorPartBlock}), drawn by the client ExcavatorRenderer.
 */
public class AbyssalExcavatorBlock extends IndustryEntityBlock
{
    private final ExcavatorTier tier;

    /** Codec / registration constructor: Mk1. */
    public AbyssalExcavatorBlock(Properties properties)
    {
        this(properties, ExcavatorTier.MK1);
    }

    public AbyssalExcavatorBlock(Properties properties, ExcavatorTier tier)
    {
        super(properties, MachineKind.ABYSSAL_EXCAVATOR);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LIT, false).setValue(WATERLOGGED, false));
    }

    public ExcavatorTier tier()
    {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return simpleCodec(AbyssalExcavatorBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AbyssalExcavatorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** removing the master takes the part cells with it (contents are dropped by the super call) */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock()) && !level.isClientSide) ExcavatorStructure.remove(level, pos);
    }
}
