package com.abyssia.habitat;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * ECO03: the habitat lamp. {@code lit} is driven by the power of its base (habitat/power/HabitatPower): with no power
 * the lamp goes dark (unlit models, light level 0).
 */
public class HabitatLightBlock extends HabitatConnectedBlock
{
    public static final MapCodec<HabitatLightBlock> CODEC = simpleCodec(HabitatLightBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    public HabitatLightBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
