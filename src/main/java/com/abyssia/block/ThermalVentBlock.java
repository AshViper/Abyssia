package com.abyssia.block;

import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Core of a hydrothermal vent, where hot water emerges. Its type and activity live in the block state, so vents
 * need no block entity; plumes, updraft and temperature are derived client-side from these two properties.
 */
public class ThermalVentBlock extends Block
{
    public static final MapCodec<ThermalVentBlock> CODEC = simpleCodec(ThermalVentBlock::new);
    public static final EnumProperty<ThermalVentType> TYPE = EnumProperty.create("type", ThermalVentType.class);
    public static final EnumProperty<VentActivity> ACTIVITY = EnumProperty.create("activity", VentActivity.class);

    public ThermalVentBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, ThermalVentType.WHITE_SMOKER).setValue(ACTIVITY, VentActivity.ACTIVE));
    }

    @Override
    protected MapCodec<ThermalVentBlock> codec()
    {
        return CODEC;
    }

    public static int lightLevel(BlockState state)
    {
        return state.getValue(ACTIVITY).light;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(TYPE, ACTIVITY);
    }
}
