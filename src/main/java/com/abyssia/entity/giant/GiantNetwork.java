package com.abyssia.entity.giant;

import com.abyssia.Abyssia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Giant payloads register themselves here so the shared network class stays untouched. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class GiantNetwork
{
    private GiantNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(GiantChainPayload.TYPE, GiantChainPayload.STREAM_CODEC, GiantChainPayload::handle);
    }
}
