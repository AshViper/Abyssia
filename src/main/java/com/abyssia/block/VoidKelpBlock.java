package com.abyssia.block;

import com.abyssia.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.KelpBlock;

/** Growing tip of void kelp; behaves exactly like vanilla kelp. */
public class VoidKelpBlock extends KelpBlock
{
    public VoidKelpBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected Block getBodyBlock()
    {
        return ModBlocks.VOID_KELP_PLANT.get();
    }
}
