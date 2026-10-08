package com.abyssia.client.title;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Optional;

/**
 * Swaps the menu panorama (Screen.PANORAMA, made non-final by the access transformer) for the Abyssia one when the title
 * screen opens, unless a resource pack already replaces it. Restores the vanilla renderer otherwise.
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class TitlePanoramaHandler
{
    private static final ResourceLocation VANILLA_FACE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/title/background/panorama_0.png");
    private static final PanoramaRenderer VANILLA = Screen.PANORAMA;
    private static AbyssPanorama panorama;

    private TitlePanoramaHandler() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        Screen.PANORAMA = wanted() ? panorama() : VANILLA;
    }

    private static AbyssPanorama panorama()
    {
        if (panorama == null) panorama = new AbyssPanorama();
        return panorama;
    }

    private static boolean wanted()
    {
        if (!ClientConfig.TITLE_PANORAMA.get()) return false;
        var rm = Minecraft.getInstance().getResourceManager();
        if (rm.getResource(AbyssPanorama.FIRST_FACE).isEmpty()) return false;
        Optional<Resource> vanilla = rm.getResource(VANILLA_FACE);
        return vanilla.isEmpty() || "vanilla".equals(vanilla.get().sourcePackId());
    }
}
