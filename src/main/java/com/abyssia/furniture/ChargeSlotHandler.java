package com.abyssia.furniture;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

/** The wall workbench's charge slot: one item that can receive Forge energy (any mod's FE item). */
public class ChargeSlotHandler extends ItemStackHandler
{
    private final Runnable onChanged;

    public ChargeSlotHandler(Runnable onChanged)
    {
        super(1);
        this.onChanged = onChanged;
    }

    public static boolean chargeable(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack)
    {
        return chargeable(stack);
    }

    @Override
    public int getSlotLimit(int slot)
    {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot)
    {
        onChanged.run();
    }
}
