package com.abyssia.furniture;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * What a hydro planter grows (feature PL01, inbox/specs/PL01-hydro-planter.md): the seedling item, the harvest item,
 * its amount range and the growth time. NONE is the blockstate value of an empty planter.
 */
public enum PlanterCrop implements StringRepresentable
{
    NONE("none", null, null, 0, 0, 0),
    MUSHROOM("mushroom", "abyssal_mushroom", "mushroom_cap", 1, 2, 8 * 60 * 20),
    GOURD("gourd", "pressure_gourd", "gourd_flesh", 1, 2, 12 * 60 * 20),
    KELP("kelp", "deep_kelp", "kelp_leaf", 2, 3, 6 * 60 * 20);

    private final String name;
    private final String seed;
    private final String result;
    public final int min;
    public final int max;
    /** growth time in ticks */
    public final int ticks;

    PlanterCrop(String name, String seed, String result, int min, int max, int ticks)
    {
        this.name = name;
        this.seed = seed;
        this.result = result;
        this.min = min;
        this.max = max;
        this.ticks = ticks;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }

    /** The crop a seedling stack grows, or NONE. */
    public static PlanterCrop of(ItemStack stack)
    {
        if (stack.isEmpty()) return NONE;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !Abyssia.MODID.equals(id.getNamespace())) return NONE;
        for (PlanterCrop crop : values())
            if (crop.seed != null && crop.seed.equals(id.getPath())) return crop;
        return NONE;
    }

    /** One harvested item (count 1); empty if the item is not registered. */
    public Item resultItem()
    {
        return result == null ? null : BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, result));
    }
}
