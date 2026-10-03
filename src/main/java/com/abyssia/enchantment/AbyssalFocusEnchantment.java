package com.abyssia.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/** Helmet: Deep Sight I/II while in the deep layer (EffectEvents). Treasure only; not with Aqua Affinity. */
public class AbyssalFocusEnchantment extends Enchantment
{
    public AbyssalFocusEnchantment()
    {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR_HEAD, new EquipmentSlot[] {EquipmentSlot.HEAD});
    }

    @Override
    public int getMinCost(int level) { return 20 + 10 * level; }

    @Override
    public int getMaxCost(int level) { return getMinCost(level) + 50; }

    @Override
    public int getMaxLevel() { return 2; }

    @Override
    public boolean isTreasureOnly() { return true; }

    @Override
    protected boolean checkCompatibility(Enchantment other)
    {
        return super.checkCompatibility(other) && other != Enchantments.AQUA_AFFINITY;
    }
}
