package com.abyssia.item;

import com.abyssia.Config;
import com.abyssia.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Dive tank oxygen: the tank (chest slot) stores the seconds of oxygen left in the abyssia:oxygen component (absent =
 * full). Worn with any diving helmet it is breathed from underwater (see DivingBreathing) and refills above water.
 */
public final class DiveTank
{
    private DiveTank() {}

    private static int pieceTier(ItemStack stack, ArmorItem.Type type)
    {
        return stack.getItem() instanceof ArmorItem armor && armor.getType() == type ? PressureGear.pieceTier(stack) : 0;
    }

    /** Tank tier 1..3 of a chest piece, 0 if it is not a dive tank. */
    public static int tankTier(ItemStack stack)
    {
        return pieceTier(stack, ArmorItem.Type.CHESTPLATE);
    }

    /** Helmet tier 1..3 of a head piece, 0 if it is not a diving helmet. */
    public static int helmetTier(ItemStack stack)
    {
        return pieceTier(stack, ArmorItem.Type.HELMET);
    }

    public static int capacitySeconds(int tier)
    {
        return tier >= 3 ? Config.TANK_PRESSURE_SECONDS.get() : tier == 2 ? Config.TANK_DEEP_SECONDS.get()
                : tier == 1 ? Config.TANK_ENTRY_SECONDS.get() : 0;
    }

    /** Seconds of oxygen left, clamped to the current capacity (absent component = full). */
    public static int oxygen(ItemStack stack)
    {
        int capacity = capacitySeconds(tankTier(stack));
        Integer value = stack.get(ModDataComponents.OXYGEN.get());
        return value == null ? capacity : Math.max(0, Math.min(capacity, value));
    }

    /** Stores the oxygen; a full tank drops the component. Writes only when the stored value changes. */
    public static void setOxygen(ItemStack stack, int seconds)
    {
        int capacity = capacitySeconds(tankTier(stack));
        int value = Math.max(0, Math.min(capacity, seconds));
        Integer current = stack.get(ModDataComponents.OXYGEN.get());
        if (value >= capacity)
        {
            if (current != null) stack.remove(ModDataComponents.OXYGEN.get());
        }
        else if (current == null || current != value)
            stack.set(ModDataComponents.OXYGEN.get(), value);
    }

    /** Diving helmet on the head, dive tank with oxygen on the chest, survival / adventure. */
    public static boolean pairActive(Player player)
    {
        if (player.isCreative() || player.isSpectator()) return false;
        ItemStack tank = player.getItemBySlot(EquipmentSlot.CHEST);
        return helmetTier(player.getItemBySlot(EquipmentSlot.HEAD)) >= 1 && tankTier(tank) >= 1 && oxygen(tank) > 0;
    }

    public static String clock(int seconds)
    {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event)
    {
        ItemStack stack = event.getItemStack();
        int tank = tankTier(stack);
        if (tank > 0)
        {
            event.getToolTip().add(Component.translatable("tooltip.abyssia.tank_oxygen", clock(oxygen(stack)),
                    clock(capacitySeconds(tank))).withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable("tooltip.abyssia.tank_use").withStyle(ChatFormatting.GRAY));
        }
        else if (helmetTier(stack) > 0)
            event.getToolTip().add(Component.translatable("tooltip.abyssia.helmet_use").withStyle(ChatFormatting.GRAY));
    }
}
