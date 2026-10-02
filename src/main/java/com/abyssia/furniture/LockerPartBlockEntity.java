package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Stateless block entity on the large locker's br / tl / tr cells: it stores nothing and only hands out the base's
 * item capability (same face rules), so pipes and hoppers work on every cell.
 */
public class LockerPartBlockEntity extends BlockEntity
{
    public LockerPartBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModFurniture.LOCKER_PART_ENTITY.get(), pos, state);
    }

    /** The base locker's handler for this face, or null while the base is missing / unloaded. */
    @Nullable
    public IItemHandler itemHandler(@Nullable Direction side)
    {
        if (level == null || !(getBlockState().getBlock() instanceof LargeLockerBlock)) return null;
        BlockPos base = LargeLockerBlock.basePos(worldPosition, getBlockState());
        if (level.isLoaded(base) && level.getBlockEntity(base) instanceof LargeLockerBlockEntity be) return be.itemHandler(side);
        return null;
    }
}
