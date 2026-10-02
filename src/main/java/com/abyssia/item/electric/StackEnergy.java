package com.abyssia.item.electric;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Receive-only item ENERGY capability over the stack's energy component: chargers fill the tool, nothing can drain it. */
final class StackEnergy implements IEnergyStorage
{
    private final ItemStack stack;

    StackEnergy(ItemStack stack) { this.stack = stack; }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate)
    {
        int accepted = Math.min(Math.max(0, maxReceive), getMaxEnergyStored() - getEnergyStored());
        if (!simulate && accepted > 0) ElectricTools.setEnergy(stack, getEnergyStored() + accepted);
        return accepted;
    }

    @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return ElectricTools.getEnergy(stack); }
    @Override public int getMaxEnergyStored() { return ElectricTools.capacity(stack); }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return true; }
}
