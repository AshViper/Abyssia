package com.abyssia.habitat.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One block of a timed build, placed in list order. {@code upper} = second half placed above in the same step (doors).
 * {@code revert} = what the cell must still be (same block) until it is placed and what a cancel puts back; null =
 * a water cell (must stay {@code HabitatPlan.replaceable}, cancel reverts to water) - the module behaviour.
 */
public record BuildStep(BlockPos pos, BlockState state, @Nullable BlockState upper, @Nullable BlockState revert)
{
    public BuildStep(BlockPos pos, BlockState state, @Nullable BlockState upper)
    {
        this(pos, state, upper, null);
    }
}
