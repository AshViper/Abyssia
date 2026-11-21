package com.abyssia.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/**
 * The only place of the logistics code that looks up item capabilities (like EnergyLookup for energy).
 * Lookups never load chunks; callers re-resolve on every use.
 */
public final class ItemLookup
{
    private ItemLookup() {}

    /** Item handler of the block at pos seen through its face side; null if none or not loaded. */
    @Nullable
    public static IItemHandler get(Level level, BlockPos pos, @Nullable Direction side)
    {
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
    }

    /** Inserts into any slot of the handler; returns what did not fit. */
    public static ItemStack insert(IItemHandler handler, ItemStack stack, boolean simulate)
    {
        return ItemHandlerHelper.insertItem(handler, stack, simulate);
    }
}
