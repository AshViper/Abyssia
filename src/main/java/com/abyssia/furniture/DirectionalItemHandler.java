package com.abyssia.furniture;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/** Automation view of an inventory that only lets items in and / or out (the large locker's top and bottom faces). */
public class DirectionalItemHandler implements IItemHandler
{
    private final IItemHandler inner;
    private final boolean insert;
    private final boolean extract;

    public DirectionalItemHandler(IItemHandler inner, boolean insert, boolean extract)
    {
        this.inner = inner;
        this.insert = insert;
        this.extract = extract;
    }

    @Override
    public int getSlots()
    {
        return inner.getSlots();
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot)
    {
        return inner.getStackInSlot(slot);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
    {
        return insert ? inner.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate)
    {
        return extract ? inner.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot)
    {
        return inner.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack)
    {
        return insert && inner.isItemValid(slot, stack);
    }
}
