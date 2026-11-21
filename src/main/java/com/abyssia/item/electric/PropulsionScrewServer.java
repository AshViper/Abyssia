package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server side of the propulsion screw thrust: the set of players holding left-click, validated and paid for every tick. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class PropulsionScrewServer
{
    /** Players whose client says left-click is held (intent); only cleared by on=false, logout, dimension change, death. */
    private static final Set<UUID> THRUSTING = new HashSet<>();
    /** Subset of THRUSTING that currently passes validation (pose shown to trackers). */
    private static final Set<UUID> ACTIVE = new HashSet<>();
    /** Players already told "empty" during the current intent. */
    private static final Set<UUID> EMPTY_NOTIFIED = new HashSet<>();

    private PropulsionScrewServer() {}

    public static void set(ServerPlayer player, boolean on)
    {
        UUID id = player.getUUID();
        if (on) THRUSTING.add(id);
        else { THRUSTING.remove(id); EMPTY_NOTIFIED.remove(id); }
        if (!on) setActive(player, false);
    }

    private static void setActive(ServerPlayer player, boolean active)
    {
        if (active ? ACTIVE.add(player.getUUID()) : ACTIVE.remove(player.getUUID())) broadcast(player, active);
    }

    private static void broadcast(ServerPlayer player, boolean on)
    {
        PacketDistributor.sendToPlayersTrackingEntity(player, new ScrewThrustStatePacket(player.getId(), on));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !THRUSTING.contains(player.getUUID())) return;
        if (!player.isAlive())
        {
            set(player, false);
            return;
        }
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean creative = player.getAbilities().instabuild;
        boolean holding = stack.getItem() instanceof PropulsionScrewItem
                && !player.isPassenger()
                && player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        boolean hasEnergy = creative || (holding && ElectricTools.getEnergy(stack) > 0);
        if (holding && !hasEnergy && EMPTY_NOTIFIED.add(player.getUUID())) ElectricTools.emptyMessage(player);
        if (hasEnergy) EMPTY_NOTIFIED.remove(player.getUUID());
        boolean valid = holding && hasEnergy;
        setActive(player, valid);
        if (!valid) return;
        ElectricTools.consume(stack, PropulsionScrewItem.COST_PER_TICK, player);
        if (player.tickCount % 8 == 0)
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_AMBIENT_SHORT,
                    SoundSource.PLAYERS, 0.6F, 0.5F);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event)
    {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && ACTIVE.contains(target.getUUID()))
            PacketDistributor.sendToPlayer(viewer, new ScrewThrustStatePacket(target.getId(), true));
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) set(player, false);
        else THRUSTING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) set(player, false);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) set(player, false);
    }
}
