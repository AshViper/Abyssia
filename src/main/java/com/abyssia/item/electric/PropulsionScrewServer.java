package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import com.abyssia.network.AbyssiaNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server side of the propulsion screw thrust: the set of players holding left-click, validated and paid for every tick. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PropulsionScrewServer
{
    /** Players whose client says left-click is held (intent); only cleared by on=false, logout, dimension change, death. */
    private static final Set<UUID> THRUSTING = new HashSet<>();
    /** Subset of THRUSTING that currently passes validation (pose shown to trackers). */
    private static final Set<UUID> ACTIVE = new HashSet<>();
    /** Players already told "empty" during the current intent. */
    private static final Set<UUID> EMPTY_NOTIFIED = new HashSet<>();
    /** Test hook: number of state broadcasts sent. */
    public static int broadcasts;

    private PropulsionScrewServer() {}

    public static void set(ServerPlayer player, boolean on)
    {
        UUID id = player.getUUID();
        if (on) THRUSTING.add(id);
        else { THRUSTING.remove(id); EMPTY_NOTIFIED.remove(id); }
        if (!on) setActive(player, false);
    }

    /** Test hook: whether the server currently treats the player as thrusting. */
    public static boolean isActive(ServerPlayer player) { return ACTIVE.contains(player.getUUID()); }

    private static void setActive(ServerPlayer player, boolean active)
    {
        if (active ? ACTIVE.add(player.getUUID()) : ACTIVE.remove(player.getUUID())) broadcast(player, active);
    }

    private static void broadcast(ServerPlayer player, boolean on)
    {
        broadcasts++;
        AbyssiaNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new ScrewThrustStatePacket(player.getId(), on));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !THRUSTING.contains(player.getUUID())) return;
        if (!player.isAlive())
        {
            set(player, false);
            return;
        }
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean creative = player.getAbilities().instabuild;
        boolean holding = stack.getItem() instanceof PropulsionScrewItem
                && !player.isPassenger()
                && player.isEyeInFluidType(ForgeMod.WATER_TYPE.get());
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
            AbyssiaNetwork.sendTo(viewer, new ScrewThrustStatePacket(target.getId(), true));
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
