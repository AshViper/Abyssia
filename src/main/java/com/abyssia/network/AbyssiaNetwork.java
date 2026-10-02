package com.abyssia.network;

import com.abyssia.habitat.HabitatControlPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AbyssiaNetwork
{
    // 2: the deep ocean depth settings packet was removed with the deep ocean dimension.
    private static final String PROTOCOL = "2";

    private AbyssiaNetwork() {}

    /** Forge's SimpleChannel became NeoForge payloads: register CustomPacketPayload types on the registrar here. */
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(HabitatControlPacket.TYPE, HabitatControlPacket.STREAM_CODEC, HabitatControlPacket::handle);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload message)
    {
        PacketDistributor.sendToPlayer(player, message);
    }
}
