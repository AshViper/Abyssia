package com.abyssia.block;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** A log that an axe strips into another log, keeping its axis. */
public class StrippableLogBlock extends RotatedPillarBlock
{
    private final Supplier<? extends Block> stripped;

    public StrippableLogBlock(Properties properties, Supplier<? extends Block> stripped)
    {
        super(properties);
        this.stripped = stripped;
    }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility toolAction, boolean simulate)
    {
        if (toolAction == ItemAbilities.AXE_STRIP)
        {
            return stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }
}
