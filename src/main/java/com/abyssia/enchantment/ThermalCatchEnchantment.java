package com.abyssia.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/** Sword / axe: Abyssia fish drop their cooked meat (LivingDropsEvent in EffectEvents). Treasure only. */
public class ThermalCatchEnchantment extends Enchantment
{
    public ThermalCatchEnchantment()
    {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
    }

    @Override
    public boolean canEnchant(ItemStack stack)
    {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    @Override
    public int getMinCost(int level) { return 25; }

    @Override
    public int getMaxCost(int level) { return 75; }

    @Override
    public boolean isTreasureOnly() { return true; }
}
