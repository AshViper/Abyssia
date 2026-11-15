package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Boss payloads use their own channel so the shared network class stays untouched. */
public final class BossNetwork
{
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Abyssia.MODID, "boss"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private BossNetwork() {}

    /** Called once from {@link BossContent#register}. */
    public static void register()
    {
        CHANNEL.messageBuilder(BossChainPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BossChainPayload::encode).decoder(BossChainPayload::decode)
                .consumerMainThread(BossChainPayload::handle).add();
        CHANNEL.messageBuilder(BossFarPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BossFarPayload::encode).decoder(BossFarPayload::decode)
                .consumerMainThread(BossFarPayload::handle).add();
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
