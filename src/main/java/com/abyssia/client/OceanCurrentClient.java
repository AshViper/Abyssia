package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.environment.OceanCurrentPush;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Carries the local player (and the boat it steers) with the ocean current: the client simulates their movement. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class OceanCurrentClient
{
    private OceanCurrentClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused() || (!Config.CURRENT_PUSH_PLAYERS.get() && !Config.CURRENT_STREAM_PLAYERS.get() && !Config.CURRENT_STREAM_BOATS.get())) return;
        Entity vehicle = player.getVehicle();
        if (vehicle != null)
        {
            if (vehicle.isControlledByLocalInstance()) OceanCurrentPush.push(vehicle);
        }
        else
        {
            OceanCurrentPush.push(player);
        }
    }
}
