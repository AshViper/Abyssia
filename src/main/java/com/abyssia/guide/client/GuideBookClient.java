package com.abyssia.guide.client;

import com.abyssia.Abyssia;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client entry points of the guide book: opens the screen and reloads the data with the resources / language. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
