package com.abyssia.furniture;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What a hydro planter cell grows (features PL01 / PL02, inbox/specs/PL02-planter-remake.md): the seedling item, the
 * harvest item, its amount range and the growth time. NONE is an empty cell. The cell renderer draws
 * block/planter_&lt;name&gt;_&lt;stage&gt; (stage 0..2, see {@link #stage}).
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
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !Abyssia.MODID.equals(id.getNamespace())) return NONE;
        for (PlanterCrop crop : values())
            if (crop.seed != null && crop.seed.equals(id.getPath())) return crop;
        return NONE;
    }

    /** Growth stage 0 (planted), 1 (half grown) or 2 (ripe) for a progress in ticks. */
    public int stage(long progress)
    {
        if (this == NONE) return 0;
        if (progress >= ticks) return 2;
        return progress * 2 >= ticks ? 1 : 0;
    }

    /** Looks a crop up by its serialized name; NONE when unknown. */
    public static PlanterCrop byName(String name)
    {
        for (PlanterCrop crop : values())
            if (crop.name.equals(name)) return crop;
        return NONE;
    }

    /** One seedling of this crop (empty for NONE or an unregistered item). */
    public ItemStack seedStack()
    {
        if (seed == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, seed));
        return item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** One harvested item (count 1); empty if the item is not registered. */
    public Item resultItem()
    {
        return result == null ? null : ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, result));
    }
}
