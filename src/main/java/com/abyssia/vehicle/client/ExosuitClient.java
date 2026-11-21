package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Exosuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Exosuit pilot controls: W/S forward / back, A/D strafe (relative to the view yaw), Space = thrusters, Shift = vanilla dismount. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class ExosuitClient
{
    private ExosuitClient() {}

    /** {forward, strafe (left +), thrust (Space held)} of the local player; zero with a screen open */
    static int[] input()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return new int[]{0, 0, 0};
        Options o = mc.options;
        int forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        int strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        return new int[]{forward, strafe, o.keyJump.isDown() ? 1 : 0};
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event)
    {
        Exosuit.pilot = ExosuitClient::input;
    }
}
