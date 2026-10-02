package com.abyssia.habitat.scan.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModHabitat;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client setup of the H07 scan console: hologram renderer and terminal screen. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ScanClient
{
    private ScanClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModHabitat.SCAN_CONSOLE_ENTITY.get(), ScanConsoleRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModHabitat.SCAN_CONSOLE_MENU.get(), ScanConsoleScreen::new));
    }
}
