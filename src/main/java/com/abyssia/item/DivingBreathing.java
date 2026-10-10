package com.abyssia.item;

import com.abyssia.Config;
import com.abyssia.vehicle.Submarine;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Underwater breathing. Helmet + dive tank with oxygen = air pinned to max while the tank drains (server clock, see
 * DiveTank). Otherwise the air that vanilla baseTick spent during the tick is rescaled to the no-gear time, so Water
 * Breathing, conduit and Respiration still apply. Runs on both logical sides so the client's bubble HUD matches the server.
 */
public final class DivingBreathing
{
    private static final class State
    {
        int before;
        double acc;
        int tankTicks;
        int tankMode;
    }

    // One map per logical side: Entity.equals/hashCode use the entity id, which the integrated server's player and the
    // client's local player share, so a single map would merge both sides' accumulators (double drain).
    private static final Map<Player, State> CLIENT_STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, State> SERVER_STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<Player, State> states(Player player)
    {
        return player.level().isClientSide ? CLIENT_STATES : SERVER_STATES;
    }

    private DivingBreathing() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.register(DivingBreathing.class);
        NeoForge.EVENT_BUS.register(DiveTank.class);
    }

    @SubscribeEvent
    public static void playerTickPre(PlayerTickEvent.Pre event)
    {
        Player player = event.getEntity();
        states(player).computeIfAbsent(player, p -> new State()).before = player.getAirSupply();
    }

    @SubscribeEvent
    public static void playerTickPost(PlayerTickEvent.Post event)
    {
        Player player = event.getEntity();
        if (!Config.BREATHING_ENABLED.get()) return;
        State st = states(player).get(player);
        if (st == null) return;
        boolean inWater = player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        boolean pair = DiveTank.pairActive(player);
        if (!player.level().isClientSide) tankClock(player, st, inWater, pair);
        if (!inWater)
        {
            st.acc = 0;
            return;
        }
        if (pair)
        {
            // Breathing from the tank: pin the bubbles to max each tick (self-correcting; Submarine does the same).
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }
        int before = st.before;
        int delta = before - player.getAirSupply();
        if (before <= 0 || delta <= 0) return;

        st.acc += delta * (15.0 / Config.BREATHING_STAGE0_SECONDS.get());
        int consume = (int) Math.floor(st.acc);
        st.acc -= consume;
        player.setAirSupply(before - consume);
    }

    /** Server only: 1 s of oxygen per second drained underwater, refilled in steps above water. */
    private static void tankClock(Player player, State st, boolean inWater, boolean pair)
    {
        ItemStack tank = player.getItemBySlot(EquipmentSlot.CHEST);
        int tier = DiveTank.tankTier(tank);
        int capacity = DiveTank.capacitySeconds(tier);
        // A submarine keeps its crew's air full itself, so the tank is not drawn on aboard.
        boolean drain = inWater && pair && !(player.getVehicle() instanceof Submarine) && !MobEffectUtil.hasWaterBreathing(player);
        boolean refill = !inWater && tier > 0 && DiveTank.oxygen(tank) < capacity;
        int mode = drain ? 1 : refill ? 2 : 0;
        if (mode != st.tankMode)
        {
            st.tankMode = mode;
            st.tankTicks = 0;
        }
        if (mode == 0)
        {
            if (tier > 0) DiveTank.setOxygen(tank, DiveTank.oxygen(tank));   // clamp a stored value to the current capacity
            return;
        }
        if (++st.tankTicks < 20) return;
        st.tankTicks = 0;
        if (drain) DiveTank.setOxygen(tank, DiveTank.oxygen(tank) - 1);
        else DiveTank.setOxygen(tank, DiveTank.oxygen(tank) + (int) Math.ceil(capacity / (double) Config.TANK_REFILL_SECONDS.get()));
    }
}
