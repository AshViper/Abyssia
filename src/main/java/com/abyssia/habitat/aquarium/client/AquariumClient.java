package com.abyssia.habitat.aquarium.client;

import com.abyssia.habitat.aquarium.AquariumContent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.IEventBus;

/** BT01g client registration (anchor line in ClientBuildContent): the aquarium renderer. */
public final class AquariumClient
{
    private AquariumClient() {}

    public static void register(IEventBus bus)
    {
        bus.addListener(AquariumClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(AquariumContent.AQUARIUM_ENTITY.get(), AquariumRenderer::new);
    }
}
