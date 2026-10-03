package com.abyssia.furniture;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import com.abyssia.registry.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;

/**
 * What a hydro planter cell grows (features PL01 / PL02, inbox/specs/PL02-planter-remake.md): the seedling item, the
 * harvest item, its amount range and the growth time. NONE is an empty cell. The cell renderer draws
 * block/planter_&lt;name&gt;_&lt;stage&gt; (stage 0..2, see {@link #stage}).
 */
public enum PlanterCrop implements StringRepresentable
{
    NONE("none", null, null, 0, 0, 0),
    MUSHROOM("mushroom", "abyssal_mushroom", "mushroom_cap", 1, 2, Const.GROW),
    GOURD("gourd", "pressure_gourd", "gourd_flesh", 1, 2, Const.GROW),
    KELP("kelp", "deep_kelp", "kelp_leaf", 2, 3, Const.GROW),
    /** RS01: the amber fan block item is the seedling; one sea resin per harvest (less than a wild fan's 1..2). */
    AMBER_FAN("amber_fan", "amber_fan", "plant_resin", 1, 1, Const.GROW),
    /** Any other edible plant: seed and result are the planted item, kept per cell by the block entity. */
    GENERIC("generic", null, null, 1, 3, Const.GROW);

    /** Growth time of every crop: 3 minutes. */
    public static final int GROW_TICKS = Const.GROW;

    private static final class Const
    {
        static final int GROW = 3 * 60 * 20;
    }

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
        if (id != null && Abyssia.MODID.equals(id.getNamespace()))
            for (PlanterCrop crop : values())
                if (crop.seed != null && crop.seed.equals(id.getPath())) return crop;
        return isEdiblePlant(stack) ? GENERIC : NONE;
    }

    /** Edible and a plant: in #abyssia:planter_crops (fruits, vegetables) or placing a plant block (carrot, berries). */
    public static boolean isEdiblePlant(ItemStack stack)
    {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return false;
        if (stack.is(ModTags.PLANTER_CROPS)) return true;
        return stack.getItem() instanceof BlockItem bi && isPlantBlock(bi.getBlock());
    }

    private static boolean isPlantBlock(Block block)
    {
        return block instanceof BushBlock || block instanceof GrowingPlantBlock;
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
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, seed));
        return item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** One harvested item (count 1); empty if the item is not registered. */
    public Item resultItem()
    {
        return result == null ? null : BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, result));
    }
}
