package com.abyssia.entity.giant;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Giant payloads use their own channel so the shared network class stays untouched. */
public final class GiantNetwork
{
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Abyssia.MODID, "giant"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private GiantNetwork() {}

    /** Called once from {@link com.abyssia.registry.ModGiants#register}. */
    public static void register()
    {
        CHANNEL.messageBuilder(GiantChainPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GiantChainPayload::encode).decoder(GiantChainPayload::decode)
                .consumerMainThread(GiantChainPayload::handle).add();
    }

    public static void sendToPlayer(ServerPlayer player, Object msg)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, Object msg)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg);
    }
}
