package com.abyssia.item.electric;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * I04 propulsion screw: hold right-click under water to thrust along the look vector. Player movement is computed on
 * the client, so {@link #thrust} runs in onUseTick on BOTH sides with the same formula; FE drain and the stop decision
 * are server side (the client stops when the synced stack has no FE left).
 */
public class PropulsionScrewItem extends Item implements ElectricTools.Electric
{
    public static final int CAPACITY = 100_000, COST_PER_TICK = 100;
    /** Blocks / tick of ordinary (sprint) swimming that the multipliers refer to. */
    public static final double NORMAL_SWIM_SPEED = 0.2;
    /** Thrust target = NORMAL_SWIM_SPEED * min(SPEED_FACTOR, MAX_FACTOR) * SWIM_SPEED attribute. */
    public static final double SPEED_FACTOR = 1.8, MAX_FACTOR = 2.0;
    /** Fraction of the gap to the target velocity closed per tick (currents are added after this and still show). */
    public static final double ACCEL = 0.2;

    public PropulsionScrewItem(Properties props) { super(props); }

    @Override public int capacity() { return CAPACITY; }

    /** Target speed in blocks / tick for this player (SWIM_SPEED attribute included). */
    public static double targetSpeed(Player player)
    {
        double swim = player.getAttributeValue(NeoForgeMod.SWIM_SPEED);
        return NORMAL_SWIM_SPEED * Math.min(SPEED_FACTOR, MAX_FACTOR) * swim;
    }

    /** One thrust tick: pulls the velocity toward look * target. Same call on client and server. */
    public static void thrust(Player player)
    {
        Vec3 target = player.getLookAngle().scale(targetSpeed(player));
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.add(target.subtract(v).scale(ACCEL)));
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    private static boolean inWater(Player player) { return player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (ElectricTools.getEnergy(stack) <= 0)
        {
            ElectricTools.emptyMessage(player);
            return InteractionResultHolder.fail(stack);
        }
        if (!inWater(player)) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining)
    {
        if (!(entity instanceof Player player)) return;
        boolean creative = player.getAbilities().instabuild;
        if (!inWater(player) || (!creative && ElectricTools.getEnergy(stack) <= 0))
        {
            if (!level.isClientSide && ElectricTools.getEnergy(stack) <= 0) ElectricTools.emptyMessage(player);
            player.stopUsingItem();
            return;
        }
        if (!level.isClientSide)
        {
            ElectricTools.consume(stack, COST_PER_TICK, player);
            if (player.tickCount % 8 == 0)
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_AMBIENT_SHORT,
                        SoundSource.PLAYERS, 0.6F, 0.5F);
        }
        else
        {
            Vec3 back = player.getLookAngle().scale(-0.9);
            Vec3 at = player.getEyePosition().add(back).add(0, -0.3, 0);
            level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
            level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 0.0, 0.05, 0.0);
        }
        thrust(player);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) { return oldStack.getItem() == newStack.getItem(); }
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
    }
}
