package com.abyssia.item.electric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * I04 propulsion screw: hold left-click (main hand, eyes in water) to thrust along the look vector; G toggles a light
 * (custom data "Light"). Player movement is computed on the client, so {@link #thrust} runs on the client only
 * (PropulsionScrewInput); the server (PropulsionScrewServer) only drains FE and plays the sound.
 */
public class PropulsionScrewItem extends Item implements ElectricTools.Electric
{
    public static final int CAPACITY = 100_000, COST_PER_TICK = 100;
    /** Blocks / tick of ordinary (sprint) swimming that the multipliers refer to. */
    public static final double NORMAL_SWIM_SPEED = 0.2;
    /** Thrust target = NORMAL_SWIM_SPEED * min(SPEED_FACTOR, MAX_FACTOR) * SWIM_SPEED attribute. */
    public static final double SPEED_FACTOR = 2.0, MAX_FACTOR = 2.0;
    /** Fraction of the gap to the target velocity closed per tick (currents are added after this and still show). */
    public static final double ACCEL = 0.2;

    private static final String TAG_LIGHT = "Light";

    public PropulsionScrewItem(Properties props) { super(props); }

    @Override public int capacity() { return CAPACITY; }

    /** Target speed in blocks / tick for this player (SWIM_SPEED attribute included). */
    public static double targetSpeed(Player player)
    {
        double swim = player.getAttributeValue(NeoForgeMod.SWIM_SPEED);
        return NORMAL_SWIM_SPEED * Math.min(SPEED_FACTOR, MAX_FACTOR) * swim;
    }

    /**
     * Velocity pull strength that makes the steady-state speed equal the target. Each tick v += (T-v)*ACCEL, then vanilla
     * water drag d scales v: v = d*(v + (T-v)*ACCEL) => v = d*ACCEL*T / (1 - d*(1-ACCEL)). To end at the real target
     * speed, pull toward target * (1 - d*(1-ACCEL)) / (d*ACCEL).
     */
    private static double pullFactor(Player player)
    {
        double d = player.isSprinting() ? 0.9 : 0.8;
        return (1.0 - d * (1.0 - ACCEL)) / (d * ACCEL);
    }

    /** One thrust tick: pulls the velocity toward look * target. Client only (movement is client-authoritative). */
    public static void thrust(Player player)
    {
        if (!player.level().isClientSide) return;
        Vec3 target = player.getLookAngle().scale(targetSpeed(player) * pullFactor(player));
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.add(target.subtract(v).scale(ACCEL)));
        player.fallDistance = 0.0F;
    }

    public static boolean isLightOn(ItemStack stack)
    {
        if (!(stack.getItem() instanceof PropulsionScrewItem)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_LIGHT);
    }

    /** Server: flips the light flag on the stack and clicks like a lever. */
    public static void toggleLight(Player player, ItemStack stack)
    {
        boolean on = !isLightOn(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_LIGHT, on));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS,
                0.5f, on ? 1.2f : 0.9f);
    }

    @Override public boolean isRepairable(ItemStack stack) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return ElectricTools.barWidth(stack); }
    @Override public int getBarColor(ItemStack stack) { return ElectricTools.BAR_COLOR; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
    {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag)
    {
        ElectricTools.tooltip(stack, lines);
        lines.add(Component.translatable("tooltip.abyssia.propulsion_screw.use").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable(isLightOn(stack) ? "tooltip.abyssia.propulsion_screw.light_on" : "tooltip.abyssia.propulsion_screw.light_off")
                .withStyle(isLightOn(stack) ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
