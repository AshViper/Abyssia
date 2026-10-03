package com.abyssia.item;

import com.abyssia.Config;
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
 * D02 underwater air time by diving gear stage (spec inbox/specs/D02-breathing.md). Max air stays 300; the air that
 * vanilla baseTick spent during the tick is rescaled, so Water Breathing, conduit, creative and Respiration still apply.
 * Runs on both logical sides so the client's predicted bubble HUD matches the server.
 */
public final class DivingBreathing
{
    private static final class State
    {
        int before;
        double acc;
        int durabilityTicks;
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
    }

    private static int headStage(ItemStack head)
    {
        if (EntryDivingGear.HELMET != null && head.is(EntryDivingGear.HELMET.get())) return 1;
        if (head.is(ModTools.DIVER_HELMET.get()) || head.is(MaterialTools.PRESSURE_DIVER_HELMET.get())) return 2;
        return 0;
    }

    private static int chestStage(ItemStack chest)
    {
        if (EntryDivingGear.TANK != null && chest.is(EntryDivingGear.TANK.get())) return 1;
        if (chest.is(MaterialTools.DIVE_TANK.get())) return 2;
        return 0;
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
        if (!player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()))
        {
            st.acc = 0;
            st.durabilityTicks = 0;
            return;
        }
        int before = st.before;
        int delta = before - player.getAirSupply();
        if (before <= 0 || delta <= 0) return;

        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int headStage = headStage(head);
        int chestStage = chestStage(chest);
        int stage = Math.max(headStage, chestStage);
        int seconds = stage == 2 ? Config.BREATHING_STAGE2_SECONDS.get()
                : stage == 1 ? Config.BREATHING_STAGE1_SECONDS.get() : Config.BREATHING_STAGE0_SECONDS.get();

        st.acc += delta * (15.0 / seconds);
        int consume = (int) Math.floor(st.acc);
        st.acc -= consume;
        player.setAirSupply(before - consume);

        if (stage > 0 && !player.level().isClientSide)
        {
            int period = 20 * (stage == 2 ? Config.BREATHING_STAGE2_DURABILITY_SECONDS.get()
                    : Config.BREATHING_STAGE1_DURABILITY_SECONDS.get());
            if (++st.durabilityTicks >= period)
            {
                st.durabilityTicks = 0;
                EquipmentSlot slot = chestStage > 0 ? EquipmentSlot.CHEST : EquipmentSlot.HEAD;
                ItemStack piece = chestStage > 0 ? chest : head;
                piece.hurtAndBreak(1, player, slot);
            }
        }
    }
}
