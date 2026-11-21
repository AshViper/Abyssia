package com.abyssia.industry;

import com.abyssia.Abyssia;
import com.abyssia.thermal.ThermalVentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** PRC01: what the hydrothermal precipitator makes per vent type (weights fixed here, raw metals of ExcavatorMinerals). */
public final class PrecipitatorYields
{
    private PrecipitatorYields() {}

    private static final String[] MK1 = {"cobalt", "nickel", "manganese", "titanium", "lead", "molybdenum", "vanadium", "zinc"};
    private static final String[] MK2 = {"tungsten", "platinum", "tellurium", "iridium", "uranium", "neodymium", "yttrium", "thorium"};
    /** share of a SUPERHEATED vent's output that is a Mk2 metal */
    public static final float SUPERHEATED_MK2_CHANCE = 0.2f;

    private static final String[] BLACK = {"cobalt", "nickel", "zinc", "lead"};
    private static final int[] BLACK_W = {3, 3, 2, 2};
    private static final String[] WHITE = {"manganese", "titanium", "zinc", "lead"};
    private static final int[] WHITE_W = {3, 3, 2, 2};
    private static final String[] MINERAL = {"molybdenum", "vanadium", "titanium", "manganese"};
    private static final int[] MINERAL_W = {3, 3, 2, 2};

    /** One raw metal for one cycle. */
    public static ItemStack roll(ThermalVentType type, long ventKey, RandomSource rng)
    {
        String metal = switch (type)
        {
            case BLACK_SMOKER -> weighted(BLACK, BLACK_W, rng);
            case WHITE_SMOKER -> weighted(WHITE, WHITE_W, rng);
            case MINERAL -> weighted(MINERAL, MINERAL_W, rng);
            case SUPERHEATED ->
            {
                String[] set = superheatedNames(ventKey);
                yield rng.nextFloat() < SUPERHEATED_MK2_CHANCE
                        ? set[SUPER_MK1 + rng.nextInt(SUPER_MK2)] : set[rng.nextInt(SUPER_MK1)];
            }
        };
        return item(metal);
    }

    private static final int SUPER_MK1 = 4, SUPER_MK2 = 2;

    private static ItemStack item(String metal)
    {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "raw_" + metal)));
    }

    /** the 4 Mk1 + 2 Mk2 metals a SUPERHEATED vent makes, fixed per vent */
    private static String[] superheatedNames(long ventKey)
    {
        RandomSource r = RandomSource.create(ventKey);
        String[] mk1 = MK1.clone(), mk2 = MK2.clone();
        shuffle(mk1, r);
        shuffle(mk2, r);
        String[] out = new String[SUPER_MK1 + SUPER_MK2];
        System.arraycopy(mk1, 0, out, 0, SUPER_MK1);
        System.arraycopy(mk2, 0, out, SUPER_MK1, SUPER_MK2);
        return out;
    }

    private static void shuffle(String[] a, RandomSource r)
    {
        for (int i = a.length - 1; i > 0; i--)
        {
            int j = r.nextInt(i + 1);
            String t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    /** Mk1 x4 then Mk2 x2 raw items of a SUPERHEATED vent. */
    public static List<Item> superheatedSet(long ventKey)
    {
        List<Item> out = new ArrayList<>();
        for (String n : superheatedNames(ventKey)) out.add(item(n).getItem());
        return out;
    }

    /** Same world + same vent position gives the same key. */
    public static long ventKey(long worldSeed, BlockPos vent)
    {
        return Mth.getSeed(vent) ^ worldSeed;
    }

    /** Whether the stack is the raw item of one of the Mk2 metals (for tests). */
    public static boolean isMk2(ItemStack stack)
    {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String m : MK2) if (id.getPath().equals("raw_" + m)) return true;
        return false;
    }

    private static String weighted(String[] names, int[] weights, RandomSource rng)
    {
        int total = 0;
        for (int w : weights) total += w;
        int r = rng.nextInt(total);
        for (int i = 0; i < names.length; i++)
        {
            r -= weights[i];
            if (r < 0) return names[i];
        }
        return names[names.length - 1];
    }
}
