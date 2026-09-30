package com.abyssia.block;

import com.abyssia.registry.ModBlocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpPlantBlock;

/** Stem segment of void kelp. */
public class VoidKelpPlantBlock extends KelpPlantBlock
{
    public VoidKelpPlantBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock()
    {
        return (GrowingPlantHeadBlock) ModBlocks.VOID_KELP.get();
    }
}
