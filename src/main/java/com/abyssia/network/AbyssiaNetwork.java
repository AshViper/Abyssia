package com.abyssia.network;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatControlPacket;
import com.abyssia.waypoint.WaypointSavePacket;
import com.abyssia.waypoint.WaypointSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AbyssiaNetwork
{
    // 2: the deep ocean depth settings packet was removed with the deep ocean dimension. 3: natural current salt.
    // 4: habitat constructor controls (H02). 5: waypoint beacon list / settings (W01). 7: submarine lights / undock (SUB02).
    private static final String PROTOCOL = "7";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private AbyssiaNetwork() {}

    public static void register()
    {
        CHANNEL.messageBuilder(NaturalCurrentSaltPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NaturalCurrentSaltPacket::encode).decoder(NaturalCurrentSaltPacket::decode)
                .consumerMainThread(NaturalCurrentSaltPacket::handle).add();
        CHANNEL.messageBuilder(HabitatControlPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HabitatControlPacket::encode).decoder(HabitatControlPacket::decode)
                .consumerMainThread(HabitatControlPacket::handle).add();
        CHANNEL.messageBuilder(WaypointSyncPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WaypointSyncPacket::encode).decoder(WaypointSyncPacket::decode)
                .consumerMainThread(WaypointSyncPacket::handle).add();
        CHANNEL.messageBuilder(WaypointSavePacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WaypointSavePacket::encode).decoder(WaypointSavePacket::decode)
                .consumerMainThread(WaypointSavePacket::handle).add();
        CHANNEL.messageBuilder(com.abyssia.vehicle.SubmarineLightPacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.abyssia.vehicle.SubmarineLightPacket::encode).decoder(com.abyssia.vehicle.SubmarineLightPacket::decode)
                .consumerMainThread(com.abyssia.vehicle.SubmarineLightPacket::handle).add();
    }

    public static void sendToServer(Object message)
    {
        CHANNEL.sendToServer(message);
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
