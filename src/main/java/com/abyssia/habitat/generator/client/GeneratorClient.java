package com.abyssia.habitat.generator.client;

import com.abyssia.habitat.generator.ModGenerators;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

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
        for (ResourceLocation id : GeneratorRenderer.allModels()) event.register(id);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModGenerators.GENERATOR_ENTITY.get(), GeneratorRenderer::new);
    }
}
