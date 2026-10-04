package com.abyssia.network;

import com.abyssia.habitat.HabitatControlPacket;
import com.abyssia.waypoint.WaypointSavePacket;
import com.abyssia.waypoint.WaypointSyncPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AbyssiaNetwork
{
    // 2: the deep ocean depth settings packet was removed with the deep ocean dimension. 3: natural current salt.
    private static final String PROTOCOL = "7";  // 7: WR01 relay links. 6: submarine lights / undock (SUB02)

    private AbyssiaNetwork() {}

    /** Forge's SimpleChannel became NeoForge payloads: register CustomPacketPayload types on the registrar here. */
    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToClient(NaturalCurrentSaltPacket.TYPE, NaturalCurrentSaltPacket.STREAM_CODEC, NaturalCurrentSaltPacket::handle);
        registrar.playToServer(HabitatControlPacket.TYPE, HabitatControlPacket.STREAM_CODEC, HabitatControlPacket::handle);
        // W01 waypoint beacon list (S2C) and settings save (C2S)
        registrar.playToClient(WaypointSyncPacket.TYPE, WaypointSyncPacket.STREAM_CODEC, WaypointSyncPacket::handle);
        registrar.playToServer(WaypointSavePacket.TYPE, WaypointSavePacket.STREAM_CODEC, WaypointSavePacket::handle);
        // SUB02 submarine lights / undock (the vehicle itself moves with vanilla's client-controlled vehicle packets)
        registrar.playToServer(com.abyssia.vehicle.SubmarineLightPacket.TYPE, com.abyssia.vehicle.SubmarineLightPacket.STREAM_CODEC,
                com.abyssia.vehicle.SubmarineLightPacket::handle);
        // WR01 wireless relay links (S2C)
        registrar.playToClient(com.abyssia.habitat.relay.RelaySyncPacket.TYPE, com.abyssia.habitat.relay.RelaySyncPacket.STREAM_CODEC,
                com.abyssia.habitat.relay.RelaySyncPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload message)
    {
        PacketDistributor.sendToServer(message);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload message)
    {
        PacketDistributor.sendToPlayer(player, message);
    }
}
