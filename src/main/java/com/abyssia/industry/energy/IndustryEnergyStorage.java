package com.abyssia.industry.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * Forge energy buffer of an industrial block. The receive / extract limits apply to other blocks through the
 * capability; the owner generates and consumes directly. Every change runs the callback (BlockEntity#setChanged).
 */
public class IndustryEnergyStorage extends EnergyStorage
{
    private final Runnable onChanged;

    public IndustryEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChanged)
    {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate)
    {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (received > 0 && !simulate) onChanged.run();
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate)
    {
        int extracted = super.extractEnergy(maxExtract, simulate);
        if (extracted > 0 && !simulate) onChanged.run();
        return extracted;
    }

    /** Adds produced energy, ignoring the receive limit; returns what fitted. */
    public int generate(int amount)
    {
        int added = Math.min(capacity - energy, Math.max(0, amount));
        if (added > 0)
        {
            energy += added;
            onChanged.run();
        }
        return added;
    }

    /** Takes energy for work, ignoring the extract limit; returns what was taken. */
    public int consume(int amount)
    {
        int taken = Math.min(energy, Math.max(0, amount));
        if (taken > 0)
        {
            energy -= taken;
            onChanged.run();
        }
        return taken;
    }

    public int room()
    {
        return capacity - energy;
    }

    public int maxExtract()
    {
        return maxExtract;
    }

    /** For loading from NBT (clamped, no callback). */
    public void setEnergy(int value)
    {
        energy = Math.max(0, Math.min(capacity, value));
    }
}
