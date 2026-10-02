package com.abyssia.network;

import com.abyssia.Abyssia;
import com.abyssia.environment.NaturalCurrents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client on login: where natural currents are. Carries the one-way salt of the world seed (never the seed) and
 * the server's stream chance and drift speed, or nothing when the server has them switched off.
 */
public record NaturalCurrentSaltPacket(boolean enabled, long salt, double chance, double maxSpeed) implements CustomPacketPayload
{
    public static final Type<NaturalCurrentSaltPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "natural_current_salt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NaturalCurrentSaltPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) ->
            {
                buf.writeBoolean(msg.enabled);
                buf.writeLong(msg.salt);
                buf.writeDouble(msg.chance);
                buf.writeDouble(msg.maxSpeed);
            },
            buf -> new NaturalCurrentSaltPacket(buf.readBoolean(), buf.readLong(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(NaturalCurrentSaltPacket msg, IPayloadContext ctx)
    {
        // NeoForge runs payload handlers on the main thread by default.
        NaturalCurrents.setClientSettings(msg.enabled ? msg.salt : null, msg.chance, msg.maxSpeed);
    }
}
