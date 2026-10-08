package com.abyssia.industry.client;

import com.abyssia.registry.ModIndustry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** ORE01 client registration (ClientBuildContent): the excavator renderer and its additional element models. */
public final class ExcavatorClient
{
    private ExcavatorClient() {}

    public static void register(IEventBus bus)
    {
        bus.addListener(ExcavatorClient::models);
        bus.addListener(ExcavatorClient::renderers);
    }

    private static void models(ModelEvent.RegisterAdditional event)
    {
        for (ResourceLocation id : ExcavatorRenderer.allModels()) event.register(id);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModIndustry.EXCAVATOR_ENTITY.get(), ExcavatorRenderer::new);
    }
}
