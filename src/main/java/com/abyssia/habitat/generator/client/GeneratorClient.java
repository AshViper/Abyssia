package com.abyssia.habitat.generator.client;

import com.abyssia.habitat.generator.ModGenerators;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** BT01d client registration (ClientBuildContent anchor): generator renderer + its additional element models. */
public final class GeneratorClient
{
    private GeneratorClient() {}

    public static void register(IEventBus bus)
    {
        bus.addListener(GeneratorClient::models);
        bus.addListener(GeneratorClient::renderers);
    }

    private static void models(ModelEvent.RegisterAdditional event)
    {
        for (ResourceLocation id : GeneratorRenderer.allModels()) event.register(ModelResourceLocation.standalone(id));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModGenerators.GENERATOR_ENTITY.get(), GeneratorRenderer::new);
    }
}
