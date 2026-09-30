package com.abyssia.worldgen.cave;

import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * A weighted list flattened for fast, allocation-free picks from a 0..1 value. World generation picks with
 * position hashes rather than a RandomSource, so the same block always gets the same choice from any chunk.
 */
public final class Palette<T>
{
    private static final Palette<?> EMPTY = new Palette<>(SimpleWeightedRandomList.empty(), t -> 1.0);

    private final Object[] items;
    private final double[] cumulative;

    private Palette(SimpleWeightedRandomList<T> list, ToDoubleFunction<T> scale)
    {
        List<WeightedEntry.Wrapper<T>> entries = list.unwrap();
        items = new Object[entries.size()];
        cumulative = new double[entries.size()];
        double[] weights = new double[entries.size()];
        double total = 0;
        for (int i = 0; i < entries.size(); i++)
        {
            weights[i] = Math.max(0, entries.get(i).getWeight().asInt() * scale.applyAsDouble(entries.get(i).getData()));
            total += weights[i];
        }
        double sum = 0;
        for (int i = 0; i < entries.size(); i++)
        {
            items[i] = entries.get(i).getData();
            sum += weights[i];
            cumulative[i] = total > 0 ? sum / total : 1;
        }
    }

    public static <T> Palette<T> of(SimpleWeightedRandomList<T> list)
    {
        return of(list, t -> 1.0);
    }

    /** Weights multiplied by {@code scale} (config rarity knobs applied on top of datapack weights). */
    @SuppressWarnings("unchecked")
    public static <T> Palette<T> of(SimpleWeightedRandomList<T> list, ToDoubleFunction<T> scale)
    {
        return list.isEmpty() ? (Palette<T>) EMPTY : new Palette<>(list, scale);
    }

    public boolean isEmpty()
    {
        return items.length == 0;
    }

    /** The entry covering {@code r} (0..1) of the total weight; null when empty. */
    @SuppressWarnings("unchecked")
    public T pick(double r)
    {
        for (int i = 0; i < cumulative.length; i++)
        {
            if (r < cumulative[i]) return (T) items[i];
        }
        return items.length == 0 ? null : (T) items[items.length - 1];
    }
}
