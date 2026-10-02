package com.abyssia.industry.blockentity;

import com.abyssia.industry.MachineKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Slots of an industrial block: inputs (or the fuel slot) first, then the reagent, the main output and the rare
 * outputs. Only the machine itself puts items into the outputs (setStackInSlot), so isItemValid refuses them.
 * Shared by the block entity and the client menu.
 */
public class MachineInventory extends ItemStackHandler
{
    private final MachineKind kind;
    private final Supplier<Level> level;
    private final Runnable onChanged;

    public MachineInventory(MachineKind kind, Supplier<Level> level, Runnable onChanged)
    {
        super(kind.slotCount());
        this.kind = kind;
        this.level = level;
        this.onChanged = onChanged;
    }

    public MachineKind kind()
    {
        return kind;
    }

    public boolean isInput(int slot)
    {
        return slot >= 0 && slot < kind.inputs;
    }

    public boolean isReagent(int slot)
    {
        return kind.reagent && slot == kind.reagentSlot();
    }

    /** Main or rare output slot. */
    public boolean isOutput(int slot)
    {
        return kind.hasOutput && slot >= kind.outputSlot() && slot < kind.slotCount();
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack)
    {
        if (isReagent(slot)) return kind.acceptsReagent(stack);
        return isInput(slot) && kind.acceptsInput(level.get(), stack);
    }

    /**
     * Automation (hoppers, pipes of other mods): a multi-input machine keeps one kind of item per slot, so a hopper
     * full of one ingot cannot clog every slot of an alloy recipe.
     */
    public boolean automationAccepts(int slot, ItemStack stack)
    {
        if (!isItemValid(slot, stack)) return false;
        if (kind.inputs < 2) return true;
        for (int i = 0; i < kind.inputs; i++)
        {
            if (i == slot) continue;
            if (ItemStack.isSameItemSameComponents(getStackInSlot(i), stack)) return false;
        }
        return true;
    }

    /**
     * Keeps this kind's slot count whatever "Size" was saved (a machine saved before a layout change, e.g. the I02 rare
     * slots going from two to three); items in slots that no longer exist are dropped from the list, not the world.
     */
    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt)
    {
        setSize(kind.slotCount());
        ListTag list = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag item = list.getCompound(i);
            int slot = item.getInt("Slot");
            if (slot >= 0 && slot < stacks.size()) stacks.set(slot, ItemStack.parseOptional(registries, item));
        }
        onLoad();
    }

    @Override
    protected void onContentsChanged(int slot)
    {
        onChanged.run();
    }
}
