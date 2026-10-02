package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
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

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !remove && level != null && getBlockState().getBlock() instanceof LargeLockerBlock)
        {
            BlockPos base = LargeLockerBlock.basePos(worldPosition, getBlockState());
            if (level.isLoaded(base) && level.getBlockEntity(base) instanceof LargeLockerBlockEntity be) return be.getCapability(cap, side);
        }
        return super.getCapability(cap, side);
    }
}
