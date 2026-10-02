package com.abyssia.industry.blockentity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;

/** Automation view of a machine inventory: insert into the inputs and / or the reagent slot, extract from the outputs only. */
public class SidedItemHandler implements IItemHandler
{
    private final MachineInventory inventory;
    private final boolean insertInputs;
    private final boolean insertReagent;
    private final boolean extract;

    /** Inserts into every input and the reagent slot (or nothing). */
    public SidedItemHandler(MachineInventory inventory, boolean insert, boolean extract)
    {
        this(inventory, insert, insert, extract);
    }

    public SidedItemHandler(MachineInventory inventory, boolean insertInputs, boolean insertReagent, boolean extract)
    {
        this.inventory = inventory;
        this.insertInputs = insertInputs;
        this.insertReagent = insertReagent;
        this.extract = extract;
    }

    private boolean canInsert(int slot, ItemStack stack)
    {
        boolean open = insertInputs && inventory.isInput(slot) || insertReagent && inventory.isReagent(slot);
        return open && inventory.automationAccepts(slot, stack);
    }

    @Override
    public int getSlots()
    {
        return inventory.getSlots();
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot)
    {
        return inventory.getStackInSlot(slot);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
    {
        if (!canInsert(slot, stack)) return stack;
        return inventory.insertItem(slot, stack, simulate);
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate)
    {
        if (!extract || !inventory.isOutput(slot)) return ItemStack.EMPTY;
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot)
    {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack)
    {
        return canInsert(slot, stack);
    }
}
