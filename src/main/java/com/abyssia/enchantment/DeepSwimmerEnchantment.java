package com.abyssia.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/** Boots: swim speed +10% per level (applied in EffectEvents). Not with Depth Strider / Frost Walker. */
public class DeepSwimmerEnchantment extends Enchantment
{
    public DeepSwimmerEnchantment()
    {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[] {EquipmentSlot.FEET});
    }

    @Override
    public int getMinCost(int level) { return 10 * level; }

    @Override
    public int getMaxCost(int level) { return getMinCost(level) + 15; }

    @Override
    public int getMaxLevel() { return 3; }

    @Override
    protected boolean checkCompatibility(Enchantment other)
    {
        return super.checkCompatibility(other) && other != Enchantments.DEPTH_STRIDER && other != Enchantments.FROST_WALKER;
    }
}
