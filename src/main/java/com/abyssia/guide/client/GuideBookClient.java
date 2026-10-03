package com.abyssia.guide.client;

import com.abyssia.Abyssia;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Client entry points of the guide book: opens the screen and reloads the data with the resources / language. */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideBookClient
{
    private GuideBookClient() {}

    public static void open()
    {
        Minecraft.getInstance().setScreen(new GuideBookScreen());
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event)
    {
        event.registerReloadListener(new GuideBookDataLoader());
    }
}
