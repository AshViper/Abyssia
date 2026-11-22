package com.abyssia.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * Diving gear tier of the worn set (the lowest of head / chest / legs / feet; an empty slot is tier 0). Used for air
 * time (DiveTank), swim speed (DivingSwimGear) and advancements. Players take no water-pressure damage (removed
 * 2026-11-22, user request); only submarines have a crush depth. Tier 1 = entry gear, 2 = deep gear, 3 = pressure gear.
 */
public final class PressureGear
{
    public static final int MAX_TIER = 3;
    /** TRP01 Heart Tank: above the pressure tier (MAX_TIER comparisons use >=) */
    public static final int HEART_TIER = 4;

    private PressureGear() {}

    private static boolean is(ItemStack stack, RegistryObject<Item> object)
    {
        return object != null && object.isPresent() && stack.is(object.get());
    }

    /** Gear tier of one piece (0 for anything that is not diving gear). */
    public static int pieceTier(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        if (is(stack, MaterialTools.HEART_TANK)) return HEART_TIER;
        if (is(stack, MaterialTools.PRESSURE_DIVER_HELMET) || is(stack, MaterialTools.PRESSURE_DIVE_TANK)
                || is(stack, MaterialTools.PRESSURE_SUIT_LEGGINGS) || is(stack, MaterialTools.PRESSURE_FLIPPERS)) return 3;
        if (is(stack, ModTools.DIVER_HELMET) || is(stack, ModTools.FLIPPERS)
                || is(stack, MaterialTools.DIVE_TANK) || is(stack, MaterialTools.DIVING_SUIT_LEGGINGS)) return 2;
        if (is(stack, EntryDivingGear.HELMET) || is(stack, EntryDivingGear.TANK)
                || is(stack, EntryDivingGear.LEGGINGS) || is(stack, EntryDivingGear.FLIPPERS)) return 1;
        return 0;
    }

    /** Worn set tier: the minimum over the four armor slots. */
    public static int wornTier(Player player)
    {
        int tier = MAX_TIER;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            tier = Math.min(tier, pieceTier(player.getItemBySlot(slot)));
        return tier;
    }
}
