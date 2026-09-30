package com.abyssia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stacking plant whose stalk may lean: a segment is also held by the same plant diagonally below it, so giant
 * kelp can drift sideways a block every few blocks of height instead of standing ruler-straight.
 */
public class LeaningPlantBlock extends StackingPlantBlock
{
    public LeaningPlantBlock(Properties properties, double width, boolean anySubstrate, SporeEmitter spores)
    {
        super(properties, width, anySubstrate, spores);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        if (super.canSurvive(state, level, pos)) return true;
        BlockPos below = pos.below();
        for (Direction d : Direction.Plane.HORIZONTAL)
        {
            if (level.getBlockState(below.relative(d)).is(this)) return true;
        }
        return false;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        // A stalk continuing diagonally above is not a tip.
        if (updated.is(this) && direction == Direction.UP && updated.getValue(TOP))
        {
            for (Direction d : Direction.Plane.HORIZONTAL)
            {
                if (level.getBlockState(pos.above().relative(d)).is(this)) return updated.setValue(TOP, false);
            }
        }
        return updated;
    }
}
