package com.abyssia.network;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AbyssiaNetwork
{
    // 2: the deep ocean depth settings packet was removed with the deep ocean dimension.
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private AbyssiaNetwork() {}

    /** No messages at present; the channel stays so client and server still agree on the mod's protocol version. */
    public static void register()
    {
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
