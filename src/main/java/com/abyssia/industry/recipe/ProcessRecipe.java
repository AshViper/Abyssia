package com.abyssia.industry.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * One machine operation: count items of each input (any order) -> result plus independently rolled rare outputs,
 * taking ticks and energy FE in total.
 */
public record ProcessRecipe(List<Ingredient> inputs, int count, ItemStack result, List<Rare> rares, int ticks, int energy)
{
    /** An extra output given with the chance 0..1, rolled per operation. */
    public record Rare(ItemStack stack, float chance) {}

    /** One of each input, no rare outputs (the I01 machines). */
    public ProcessRecipe(List<Ingredient> inputs, ItemStack result, int ticks, int energy)
    {
        this(inputs, 1, result, List.of(), ticks, energy);
    }

    /** The non-empty stacks given must pair up one-to-one with the inputs (shapeless, up to 3), each holding count. */
    public boolean matches(List<ItemStack> stacks)
    {
        if (stacks.size() != inputs.size()) return false;
        return assign(stacks, 0, new boolean[inputs.size()]);
    }

    private boolean assign(List<ItemStack> stacks, int index, boolean[] used)
    {
        if (index == stacks.size()) return true;
        ItemStack stack = stacks.get(index);
        if (stack.getCount() < count) return false;
        for (int i = 0; i < inputs.size(); i++)
        {
            if (used[i] || !inputs.get(i).test(stack)) continue;
            used[i] = true;
            if (assign(stacks, index + 1, used)) return true;
            used[i] = false;
        }
        return false;
    }

    /** Energy to spend on tick number step (0-based): spreads energy over ticks with no rounding loss. */
    public int energyAt(int step)
    {
        return energyAt(step, ticks);
    }

    /** Same, for an operation sped up or slowed down to the given number of ticks (same total energy). */
    public int energyAt(int step, int ticks)
    {
        return (int) ((long) energy * (step + 1) / ticks - (long) energy * step / ticks);
    }
}
