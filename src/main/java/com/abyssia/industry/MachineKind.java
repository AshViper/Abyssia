package com.abyssia.industry;

import com.abyssia.industry.blockentity.GeneratorBlockEntity;
import com.abyssia.industry.recipe.MachineRecipes;
import com.abyssia.registry.ModIndustry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The industrial blocks with a block entity and a GUI (specs I01, I02): slot counts, energy numbers and the GUI layout.
 * The ordinal is sent to the client when a menu opens, so only append new kinds.
 */
public enum MachineKind
{
    //                       id                        inputs output layout
    CRUSHER("crusher", 1, true, GuiLayout.MACHINE),
    REFINERY_FURNACE("refinery_furnace", 1, true, GuiLayout.MACHINE),
    ALLOY_FURNACE("alloy_furnace", 3, true, GuiLayout.ALLOY),
    HIGH_TEMP_FURNACE("high_temp_furnace", 3, true, GuiLayout.ALLOY),
    HYDROTHERMAL_GENERATOR("hydrothermal_generator", 0, false, GuiLayout.ENERGY),
    AUXILIARY_GENERATOR("auxiliary_generator", 1, false, GuiLayout.GENERATOR),
    ENERGY_DEVICE("energy_device", 0, false, GuiLayout.ENERGY),
    //                       id                        inputs reagent outputs (main + rare) layout
    SELECTIVE_LEACHING_SEPARATOR("selective_leaching_separator", 1, true, 4, GuiLayout.LEACHING);

    /** Energy buffer of every processing machine. */
    public static final int MACHINE_CAPACITY = 32_000;
    /** How fast a processing machine accepts energy (FE/t). */
    public static final int MACHINE_RECEIVE = 256;

    public final String id;
    public final int inputs;
    /** one reagent slot after the inputs, one used per operation */
    public final boolean reagent;
    /** output slots: the main output, then rare outputs */
    public final int outputs;
    public final boolean hasOutput;
    public final GuiLayout layout;

    MachineKind(String id, int inputs, boolean hasOutput, GuiLayout layout)
    {
        this(id, inputs, false, hasOutput ? 1 : 0, layout);
    }

    MachineKind(String id, int inputs, boolean reagent, int outputs, GuiLayout layout)
    {
        this.id = id;
        this.inputs = inputs;
        this.reagent = reagent;
        this.outputs = outputs;
        this.hasOutput = outputs > 0;
        this.layout = layout;
    }

    /** Inventory size: inputs (or the fuel slot) first, then the reagent, the main output and the rare outputs. */
    public int slotCount()
    {
        return insertSlots() + outputs;
    }

    /** Slots a player or a hopper may put items into: the inputs and the reagent, from slot 0. */
    public int insertSlots()
    {
        return inputs + (reagent ? 1 : 0);
    }

    public int reagentSlot()
    {
        return reagent ? inputs : -1;
    }

    /** The main output slot; rare outputs follow it. */
    public int outputSlot()
    {
        return hasOutput ? insertSlots() : -1;
    }

    /** Whether a stack may go into the reagent slot. */
    /** One reagent item for display (JEI), or empty for kinds without a reagent slot. */
    public ItemStack reagentStack()
    {
        return reagent ? new ItemStack(ModIndustry.ACIDIC_LEACHING_REAGENT.get()) : ItemStack.EMPTY;
    }

    public boolean acceptsReagent(ItemStack stack)
    {
        return reagent && !stack.isEmpty() && stack.is(ModIndustry.ACIDIC_LEACHING_REAGENT.get());
    }

    /** The high-temperature furnace only runs next to a live thermal vent. */
    public boolean needsVent()
    {
        return this == HIGH_TEMP_FURNACE;
    }

    /** Whether a stack may go into an input (or fuel) slot. */
    public boolean acceptsInput(@Nullable Level level, ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        if (this == AUXILIARY_GENERATOR) return GeneratorBlockEntity.fuelEnergy(stack) > 0;
        if (!hasOutput || level == null) return false;
        return MachineRecipes.get(level).isInput(this, stack);
    }

    public static MachineKind byOrdinal(int ordinal)
    {
        MachineKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CRUSHER;
    }
}
