package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModFurniture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client setup of the furniture: the large locker and wall workbench screens, the hydro planter crop renderer. */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FurnitureClient
{
    private FurnitureClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event)
    {
        event.register(ModFurniture.WALL_WORKBENCH_MENU.get(), WallWorkbenchScreen::new);
        event.register(ModFurniture.LARGE_LOCKER_MENU.get(), LargeLockerScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModFurniture.HYDRO_PLANTER_ENTITY.get(), HydroPlanterRenderer::new);
        event.registerBlockEntityRenderer(ModFurniture.LARGE_LOCKER_ENTITY.get(), LargeLockerRenderer::new);
    }
}
