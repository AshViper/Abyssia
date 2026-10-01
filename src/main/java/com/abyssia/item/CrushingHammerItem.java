package com.abyssia.item;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Crushing hammer (material system): an ingredient of every crushing recipe that is not consumed. The crafting
 * remainder is the hammer itself with 1 more damage; a hammer on its last point of durability breaks (no remainder).
 * Not a digging tool: it mines like an empty hand.
 */
public class CrushingHammerItem extends Item
{
    public CrushingHammerItem(Properties properties, double attackDamage, double attackSpeed)
    {
        // Same convention as the vanilla tool constructors (player base damage 1 / speed 4 are added on top).
        super(properties.attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack)
    {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack)
    {
        if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        result.setCount(1);
        result.setDamageValue(stack.getDamageValue() + 1);
        return result;
    }

    @Override
    public int getEnchantmentValue()
    {
        return 5;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair)
    {
        return repair.is(Items.IRON_INGOT);
    }
}
