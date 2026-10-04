package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModFurniture;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client setup of the furniture: the large locker and wall workbench screens, the hydro planter crop renderer. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FurnitureClient
{
    private FurnitureClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            MenuScreens.register(ModFurniture.WALL_WORKBENCH_MENU.get(), WallWorkbenchScreen::new);
            MenuScreens.register(ModFurniture.LARGE_LOCKER_MENU.get(), LargeLockerScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModFurniture.HYDRO_PLANTER_ENTITY.get(), HydroPlanterRenderer::new);
        event.registerBlockEntityRenderer(ModFurniture.LARGE_LOCKER_ENTITY.get(), LargeLockerRenderer::new);
    }
}
