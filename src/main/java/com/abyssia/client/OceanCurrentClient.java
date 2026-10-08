package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.environment.OceanCurrentPush;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Carries the local player (and the boat it steers) with the ocean current: the client simulates their movement. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class OceanCurrentClient
{
    private OceanCurrentClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused() || (!Config.CURRENT_PUSH_PLAYERS.get() && !Config.STREAM_AFFECTS_PLAYERS.get() && !Config.STREAM_AFFECTS_BOATS.get())) return;
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
