package com.abyssia.industry.blockentity;

import com.abyssia.industry.MachineKind;
import com.abyssia.industry.VentHeat;
import com.abyssia.industry.recipe.MachineRecipes;
import com.abyssia.industry.recipe.ProcessRecipe;
import com.abyssia.registry.ModIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Crusher, refinery furnace, alloy furnace, high-temperature furnace and selective leaching separator: one recipe at
 * a time from the input slots into the output slot, paying the recipe's energy spread over its ticks (pauses,
 * keeping progress, while short).
 * <p>
 * A machine with a reagent slot (I02) also uses one reagent per operation, rolls the recipe's rare outputs into the
 * rare slots (rolled items that do not fit wait in an overflow list and stop the machine until there is room), and
 * runs 10% faster (same energy) when it or a neighbour holds water.
 * Status bits: 1 the high-temperature furnace has no live thermal vent within 6 blocks; 2 no reagent; 4 rare outputs
 * full; 8 water bonus active (information only).
 */
public class ProcessingMachineBlockEntity extends IndustryBlockEntity
{
    public static final int STATUS_NO_HEAT = 1, STATUS_NO_REAGENT = 2, STATUS_RARE_FULL = 4, STATUS_WATER = 8;
    /** status bits that stop the machine */
    private static final int STATUS_BLOCKING = STATUS_NO_HEAT | STATUS_NO_REAGENT | STATUS_RARE_FULL;
    private static final int VENT_RADIUS = 6;

    @Nullable
    private ProcessRecipe recipe;
    private boolean recipeDirty = true;
    @Nullable
    private MachineRecipes recipesSeen;
    private boolean heated;
    private int heatCheck;
    private boolean wet;
    private int waterCheck;
    /** ticks of the running operation, fixed when it starts (the water bonus may change meanwhile) */
    private int cycleTicks;
    /** rolled rare outputs waiting for room in the rare slots (never dropped or duplicated) */
    private final List<ItemStack> overflow = new ArrayList<>();

    public ProcessingMachineBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.MACHINE_ENTITY.get(), pos, state, MachineKind.MACHINE_CAPACITY, MachineKind.MACHINE_RECEIVE, 0);
    }

    @Override
    protected void onItemsChanged()
    {
        super.onItemsChanged();
        recipeDirty = true;
    }

    @Override
    protected void work()
    {
        if (kind.needsVent() && --heatCheck <= 0)
        {
            heatCheck = 20;
            heated = VentHeat.liveVentNearby(level, worldPosition, VENT_RADIUS);
        }
        status = kind.needsVent() && !heated ? STATUS_NO_HEAT : 0;

        MachineRecipes recipes = MachineRecipes.get(level);
        if (recipeDirty || recipes != recipesSeen)
        {
            ProcessRecipe found = recipes.find(kind, inputs());
            // keep saved progress on the first lookup after loading and when /reload re-derived the same recipe
            if (recipesSeen != null && !sameRecipe(found, recipe)) progress = 0;
            recipeDirty = false;
            recipesSeen = recipes;
            recipe = found;
        }

        if (kind.reagent)
        {
            if (--waterCheck <= 0)
            {
                waterCheck = 20;
                wet = nearWater();
            }
            if (wet) status |= STATUS_WATER;
            if (!overflow.isEmpty()) flushOverflow();
            if (!overflow.isEmpty()) status |= STATUS_RARE_FULL;
            if (recipe != null && !kind.acceptsReagent(items.getStackInSlot(kind.reagentSlot()))) status |= STATUS_NO_REAGENT;
        }

        boolean working = false;
        rate = 0;
        if (recipe == null || !fitsOutput(recipe.result()) || (status & STATUS_BLOCKING) != 0)
        {
            progress = 0;
            maxProgress = recipe == null ? 0 : ticksFor(recipe);
        }
        else
        {
            if (progress == 0 || cycleTicks <= 0) cycleTicks = ticksFor(recipe);
            int ticks = cycleTicks;
            maxProgress = ticks;
            int cost = recipe.energyAt(Math.min(progress, ticks - 1), ticks);
            if (energy.getEnergyStored() >= cost)
            {
                energy.consume(cost);
                rate = cost;
                working = true;
                if (++progress >= ticks)
                {
                    progress = 0;
                    craft(recipe);
                }
                setChanged();
            }
        }
        setWorking(working);
    }

    /** Operation length: the recipe's ticks, x0.9 (rounded up) for a reagent machine in or next to water. */
    private int ticksFor(ProcessRecipe r)
    {
        return kind.reagent && wet ? (r.ticks() * 9 + 9) / 10 : r.ticks();
    }

    /** Waterlogged, or water (source or flowing) on any of the six sides; unloaded neighbours count as dry. */
    private boolean nearWater()
    {
        if (level.getFluidState(worldPosition).is(FluidTags.WATER)) return true;
        for (Direction dir : Direction.values())
        {
            BlockPos at = worldPosition.relative(dir);
            if (level.isLoaded(at) && level.getFluidState(at).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static boolean sameRecipe(@Nullable ProcessRecipe a, @Nullable ProcessRecipe b)
    {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.ticks() == b.ticks() && a.count() == b.count() && ItemStack.matches(a.result(), b.result());
    }

    private List<ItemStack> inputs()
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < kind.inputs; i++)
        {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    private boolean fitsOutput(ItemStack result)
    {
        ItemStack out = items.getStackInSlot(kind.outputSlot());
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), items.getSlotLimit(kind.outputSlot()));
    }

    private void craft(ProcessRecipe done)
    {
        for (int i = 0; i < kind.inputs; i++)
        {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack left = stack.copy();
            left.shrink(done.count());
            items.setStackInSlot(i, left);
        }
        if (kind.reagent)
        {
            ItemStack left = items.getStackInSlot(kind.reagentSlot()).copy();
            left.shrink(1);
            items.setStackInSlot(kind.reagentSlot(), left);
        }
        int slot = kind.outputSlot();
        ItemStack out = items.getStackInSlot(slot);
        if (out.isEmpty()) items.setStackInSlot(slot, done.result().copy());
        else
        {
            ItemStack grown = out.copy();
            grown.grow(done.result().getCount());
            items.setStackInSlot(slot, grown);
        }
        if (kind.outputs > 1 && level != null)
        {
            for (ProcessRecipe.Rare rare : done.rares())
                if (level.random.nextFloat() < rare.chance()) overflow.add(rare.stack().copy());
            flushOverflow();
        }
    }

    // ---------------------------------------------------------------- rare outputs

    /** Moves waiting rare outputs into the rare slots: onto the same item first, else into an empty slot. */
    private void flushOverflow()
    {
        for (int i = 0; i < overflow.size(); )
        {
            ItemStack left = putRare(overflow.get(i));
            if (left.isEmpty()) overflow.remove(i);
            else
            {
                overflow.set(i, left);
                i++;
            }
        }
    }

    /** Returns what did not fit. */
    private ItemStack putRare(ItemStack stack)
    {
        ItemStack left = stack.copy();
        int first = kind.outputSlot() + 1;
        for (int pass = 0; pass < 2; pass++)
        {
            for (int slot = first; slot < kind.slotCount(); slot++)
            {
                ItemStack in = items.getStackInSlot(slot);
                boolean usable = pass == 0 ? !in.isEmpty() && ItemStack.isSameItemSameComponents(in, left) : in.isEmpty();
                if (!usable) continue;
                int room = Math.min(left.getMaxStackSize(), items.getSlotLimit(slot)) - in.getCount();
                int move = Math.min(room, left.getCount());
                if (move <= 0) continue;
                ItemStack placed = left.copy();
                placed.setCount(in.getCount() + move);
                items.setStackInSlot(slot, placed);
                left.shrink(move);
                if (left.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return left;
    }

    @Override
    public void clearContent()
    {
        super.clearContent();
        overflow.clear();
    }

    @Override
    public void dropContents(Level level, BlockPos pos)
    {
        super.dropContents(level, pos);
        for (ItemStack stack : overflow)
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        overflow.clear();
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (!overflow.isEmpty())
        {
            ListTag list = new ListTag();
            for (ItemStack stack : overflow) list.add(stack.save(registries));
            tag.put("Overflow", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        overflow.clear();
        ListTag list = tag.getList("Overflow", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) overflow.add(stack);
        }
        cycleTicks = 0;
        recipeDirty = true; // inventory replaced wholesale (chunk load, /data merge, structure placement)
    }
}
