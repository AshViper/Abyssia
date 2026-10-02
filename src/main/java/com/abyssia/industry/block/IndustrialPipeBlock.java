package com.abyssia.industry.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Industrial pipe: decoration only, connects to pipes, valve ends, machines, generators and the energy device. Core 4..12. */
public class IndustrialPipeBlock extends ConnectingBlock
{
    public IndustrialPipeBlock(Properties properties)
    {
        super(properties, 4, 12);
    }

    @Override
    protected boolean connectsTo(BlockGetter level, BlockPos pos, Direction dir)
    {
        BlockState state = level.getBlockState(pos.relative(dir));
        Block block = state.getBlock();
        // a valve is an inline segment: only its two ends take a pipe
        if (block instanceof ValveBlock) return state.getValue(ValveBlock.FACING).getAxis() == dir.getAxis();
        return block instanceof IndustrialPipeBlock || block instanceof IndustryEntityBlock;
    }
}
