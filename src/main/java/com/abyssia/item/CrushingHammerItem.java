package com.abyssia.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Crushing hammer (material system): an ingredient of every crushing recipe that is not consumed. The crafting
 * remainder is the hammer itself with 1 more damage; a hammer on its last point of durability breaks (no remainder).
 * Not a digging tool: it mines like an empty hand.
 */
public class CrushingHammerItem extends Item
{
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public CrushingHammerItem(Properties properties, double attackDamage, double attackSpeed)
    {
        super(properties);
        // Same convention as the vanilla tool constructors (player base damage 1 / speed 4 are added on top).
        modifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Tool modifier", attackDamage, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Tool modifier", attackSpeed, AttributeModifier.Operation.ADDITION))
                .build();
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

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }
}
