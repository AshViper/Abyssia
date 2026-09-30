package com.abyssia.network;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AbyssiaNetwork
{
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private AbyssiaNetwork() {}

    public static void register()
    {
        CHANNEL.messageBuilder(DepthSettingsPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DepthSettingsPacket::encode)
                .decoder(DepthSettingsPacket::decode)
                .consumerMainThread(DepthSettingsPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
