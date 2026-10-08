package com.abyssia.industry.client;

import com.abyssia.registry.ModIndustry;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

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
        for (ResourceLocation id : ExcavatorRenderer.allModels()) event.register(ModelResourceLocation.standalone(id));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModIndustry.EXCAVATOR_ENTITY.get(), ExcavatorRenderer::new);
    }
}
