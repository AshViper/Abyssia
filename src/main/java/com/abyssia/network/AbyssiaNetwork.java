package com.abyssia.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AbyssiaNetwork
{
    // 2: the deep ocean depth settings packet was removed with the deep ocean dimension. 3: natural current salt.
    private static final String PROTOCOL = "3";

    private AbyssiaNetwork() {}

    /** Forge's SimpleChannel became NeoForge payloads: register CustomPacketPayload types on the registrar here. */
    public static void register(RegisterPayloadHandlersEvent event)
    {
        event.registrar(PROTOCOL)
                .playToClient(NaturalCurrentSaltPacket.TYPE, NaturalCurrentSaltPacket.STREAM_CODEC, NaturalCurrentSaltPacket::handle);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload message)
    {
        PacketDistributor.sendToPlayer(player, message);
    }
}
