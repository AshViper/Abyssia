package com.abyssia.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A plant material (tools/plant_defs.py). Base materials say where to look for them ({@code item.abyssia.<id>.source}),
 * so the player knows which biome to explore; some burn as fuel.
 */
public class MaterialItem extends Item
{
    private final int burnTime;
    private final boolean hasSource;

    public MaterialItem(Properties properties, int burnTime, boolean hasSource)
    {
        super(properties);
        this.burnTime = burnTime;
        this.hasSource = hasSource;
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType)
    {
        return Math.max(burnTime, 0); // NeoForge 1.21: a negative value throws (it is 0 for "not a fuel")
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        if (hasSource) tooltip.add(Component.translatable(getDescriptionId() + ".source").withStyle(ChatFormatting.GRAY));
    }
}
