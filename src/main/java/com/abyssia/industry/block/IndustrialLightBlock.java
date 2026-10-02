package com.abyssia.industry.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Work light / warning light (light level set in the block properties). FACING is the face it was placed on, so
 * the lamp sits against the opposite side: 4..12 across, 0..6 out from the mount (floor = facing up).
 */
public class IndustrialLightBlock extends FacingDecorBlock
{
    public IndustrialLightBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape shapeFor(Direction facing)
    {
        return oriented(facing, 4, 12, 0, 6);
    }
}
