package com.abyssia.habitat.scan.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModHabitat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client setup of the H07 scan console: hologram renderer and terminal screen. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class ScanClient
{
    private ScanClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModHabitat.SCAN_CONSOLE_ENTITY.get(), ScanConsoleRenderer::new);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event)
    {
        event.register(ModHabitat.SCAN_CONSOLE_MENU.get(), ScanConsoleScreen::new);
    }
}
