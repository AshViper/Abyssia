package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Boss payloads register themselves here so the shared network class stays untouched. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class BossNetwork
{
    private static final String PROTOCOL = "1";

    private BossNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToClient(BossChainPayload.TYPE, BossChainPayload.STREAM_CODEC, BossChainPayload::handle);
        registrar.playToClient(BossFarPayload.TYPE, BossFarPayload.STREAM_CODEC, BossFarPayload::handle);
    }
}
