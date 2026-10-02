package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModFurniture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client setup of the furniture: the large locker screen. */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FurnitureClient
{
    private FurnitureClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event)
    {
        event.register(ModFurniture.LARGE_LOCKER_MENU.get(), LargeLockerScreen::new);
    }
}
