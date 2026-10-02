package com.abyssia.client.title;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.util.Optional;

/** Swaps the title screen's panorama for the Abyssia one unless a resource pack already replaces it. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TitlePanoramaHandler
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation VANILLA_FACE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/title/background/panorama_0.png");
    private static AbyssPanorama panorama;
    private static boolean failed;

    private TitlePanoramaHandler() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event)
    {
        if (failed || !(event.getScreen() instanceof TitleScreen screen) || !ClientConfig.TITLE_PANORAMA.get()) return;
        var rm = Minecraft.getInstance().getResourceManager();
        if (rm.getResource(AbyssPanorama.FIRST_FACE).isEmpty()) return;
        Optional<Resource> vanilla = rm.getResource(VANILLA_FACE);
        if (vanilla.isPresent() && !"vanilla".equals(vanilla.get().sourcePackId())) return;
        try
        {
            if (panorama == null) panorama = new AbyssPanorama();
            ObfuscationReflectionHelper.setPrivateValue(TitleScreen.class, screen, panorama, "f_96729_");
        }
        catch (Throwable t)
        {
            failed = true;
            LOGGER.warn("Could not replace the title screen panorama", t);
        }
    }
}
