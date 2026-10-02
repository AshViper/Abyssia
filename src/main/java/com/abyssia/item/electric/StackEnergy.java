package com.abyssia.item.electric;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Receive-only ENERGY capability over the stack's "Energy" tag: chargers fill the tool, nothing can drain it. */
final class StackEnergy implements ICapabilityProvider, IEnergyStorage
{
    private final ItemStack stack;
    private final LazyOptional<IEnergyStorage> self = LazyOptional.of(() -> this);

    StackEnergy(ItemStack stack) { this.stack = stack; }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side)
    {
        return cap == ForgeCapabilities.ENERGY ? self.cast() : LazyOptional.empty();
    }

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
