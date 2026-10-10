package com.abyssia.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Swim pair: diving leggings + flippers worn together give a swim speed bonus and current resistance by the lower of
 * the two pieces' tiers (1 entry, 2 deep, 3 pressure). One transient SWIM_SPEED modifier, kept by the server.
 */
public final class DivingSwimGear
{
    private static final UUID PAIR_SPEED = UUID.fromString("5c1f6a0e-3b7d-4e29-9a41-d1a0b5e7c3f2");
    private static final String PAIR_NAME = "abyssia:diving_swim_pair";
    private static final double[] SPEED = {0.0, 0.20, 0.40, 0.50};
    private static final double[] RESISTANCE = {0.0, 0.30, 0.50, 0.70};

    private DivingSwimGear() {}

    public static void register()
    {
        MinecraftForge.EVENT_BUS.register(DivingSwimGear.class);
    }

    private static int pieceTier(ItemStack stack, ArmorItem.Type type)
    {
        return stack.getItem() instanceof ArmorItem armor && armor.getType() == type ? PressureGear.pieceTier(stack) : 0;
    }

    /** min(legs tier, feet tier); 0 when either slot is not diving gear. */
    public static int pairTier(Player player)
    {
        return Math.min(pieceTier(player.getItemBySlot(EquipmentSlot.LEGS), ArmorItem.Type.LEGGINGS),
                pieceTier(player.getItemBySlot(EquipmentSlot.FEET), ArmorItem.Type.BOOTS));
    }

    public static double currentResistance(Player player)
    {
        return RESISTANCE[pairTier(player)];
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        AttributeInstance swim = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swim == null) return;
        int tier = pairTier(player);
        AttributeModifier current = swim.getModifier(PAIR_SPEED);
        if (current != null && (tier == 0 || current.getAmount() != SPEED[tier])) swim.removeModifier(PAIR_SPEED);
        else if (current != null) return;
        if (tier > 0 && swim.getModifier(PAIR_SPEED) == null)
            swim.addTransientModifier(new AttributeModifier(PAIR_SPEED, PAIR_NAME, SPEED[tier], AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event)
    {
        ItemStack stack = event.getItemStack();
        int tier = Math.max(pieceTier(stack, ArmorItem.Type.LEGGINGS), pieceTier(stack, ArmorItem.Type.BOOTS));
        if (tier <= 0) return;
        event.getToolTip().add(Component.translatable("tooltip.abyssia.swim_pair", Math.round(SPEED[tier] * 100),
                Math.round(RESISTANCE[tier] * 100)).withStyle(ChatFormatting.GRAY));
    }
}
