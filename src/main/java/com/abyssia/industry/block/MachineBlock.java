package com.abyssia.industry.block;

import com.abyssia.industry.MachineKind;
import com.abyssia.industry.blockentity.GeneratorBlockEntity;
import com.abyssia.industry.blockentity.ProcessingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/** Crusher, furnaces and generators: FACING + LIT (while working) + WATERLOGGED. */
public class MachineBlock extends IndustryEntityBlock
{
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public MachineBlock(Properties properties, MachineKind kind)
    {
        super(properties, kind);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LIT, false).setValue(WATERLOGGED, false));
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
        return switch (kind())
        {
            case HYDROTHERMAL_GENERATOR, AUXILIARY_GENERATOR -> new GeneratorBlockEntity(pos, state);
            default -> new ProcessingMachineBlockEntity(pos, state);
        };
    }
}
