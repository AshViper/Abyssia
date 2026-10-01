package com.abyssia.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AbyssiaNetwork
{
    // 2: the deep ocean depth settings packet was removed with the deep ocean dimension.
    private static final String PROTOCOL = "2";

    private AbyssiaNetwork() {}

    /**
     * No messages at present; the registrar stays so client and server still agree on the mod's protocol version.
     * Forge's SimpleChannel became NeoForge payloads: register CustomPacketPayload types on the registrar here.
     */
    public static void register(RegisterPayloadHandlersEvent event)
    {
        event.registrar(PROTOCOL);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload message)
    {
        PacketDistributor.sendToPlayer(player, message);
    }
}
