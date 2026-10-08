package com.abyssia.item;

import com.abyssia.Config;
import com.abyssia.fauna.DepthZone;
import com.abyssia.vehicle.Submarine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Water pressure: the worn diving set's tier (the lowest of head / chest / legs / feet; an empty slot is tier 0) sets
 * the safe depth. Deeper than that, a survival / adventure player outside a submarine takes damage and Slowness once
 * a second. Tier 1 = entry gear, 2 = deep gear, 3 = pressure gear.
 */
public final class PressureGear
{
    /** Safe depth in metres per tier 0..2; tier 3 has no limit. */
    private static final int[] SAFE_METRES = {200, 1700, 6000};
    public static final int MAX_TIER = 3;
    private static final int STEP_METRES = 2000;

    private PressureGear() {}

    public static void register()
    {
        MinecraftForge.EVENT_BUS.register(PressureGear.class);
    }

    private static boolean is(ItemStack stack, RegistryObject<Item> object)
    {
        return object != null && object.isPresent() && stack.is(object.get());
    }

    /** Pressure tier of one piece (0 for anything that is not diving gear). */
    public static int pieceTier(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
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

    /** Safe depth in metres for a tier, or {@link Integer#MAX_VALUE} when unlimited. */
    public static int safeMetres(int tier)
    {
        return tier >= MAX_TIER ? Integer.MAX_VALUE : SAFE_METRES[Math.max(0, tier)];
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        if (player.tickCount % 20 != 0 || !Config.PRESSURE_ENABLED.get()) return;
        if (!player.isUnderWater() || player.isCreative() || player.isSpectator()) return;
        if (player.getVehicle() instanceof Submarine) return;
        int safe = safeMetres(wornTier(player));
        if (safe == Integer.MAX_VALUE) return;
        double depth = DepthZone.metres(player.level(), player.getY());
        if (depth <= safe) return;
        float damage = Math.min(3, 1 + (int) ((depth - safe) / STEP_METRES));
        player.hurt(player.damageSources().generic(), damage);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
        player.displayClientMessage(Component.translatable("message.abyssia.pressure_warning", (int) depth, safe), true);
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event)
    {
        int tier = pieceTier(event.getItemStack());
        if (tier <= 0) return;
        event.getToolTip().add(line("tooltip.abyssia.pressure_tier", tier).withStyle(ChatFormatting.AQUA));
        Player player = event.getEntity();
        if (player != null)
            event.getToolTip().add(line("tooltip.abyssia.pressure_worn", wornTier(player)).withStyle(ChatFormatting.GRAY));
    }

    private static net.minecraft.network.chat.MutableComponent line(String key, int tier)
    {
        return tier >= MAX_TIER
                ? Component.translatable(key + "_unlimited", tier)
                : Component.translatable(key, tier, safeMetres(tier));
    }
}
