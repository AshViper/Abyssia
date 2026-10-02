package com.abyssia.industry.block;

import com.abyssia.industry.MachineKind;
import com.mojang.serialization.MapCodec;
import com.abyssia.industry.blockentity.EnergyDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;

/** Energy device (battery): FACING + CHARGE 0..3 (fill level, drives the front glow) + WATERLOGGED. */
public class EnergyDeviceBlock extends IndustryEntityBlock
{
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 3);

    public EnergyDeviceBlock(Properties properties)
    {
        super(properties, MachineKind.ENERGY_DEVICE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return simpleCodec(EnergyDeviceBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new EnergyDeviceBlockEntity(pos, state);
    }
}
