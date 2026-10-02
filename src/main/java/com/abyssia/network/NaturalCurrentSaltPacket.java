package com.abyssia.network;

import com.abyssia.environment.NaturalCurrents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client on login: where natural currents are. Carries the one-way salt of the world seed (never the seed) and
 * the server's stream chance and drift speed, or nothing when the server has them switched off.
 */
public record NaturalCurrentSaltPacket(boolean enabled, long salt, double chance, double maxSpeed)
{
    public static void encode(NaturalCurrentSaltPacket msg, FriendlyByteBuf buf)
    {
        buf.writeBoolean(msg.enabled);
        buf.writeLong(msg.salt);
        buf.writeDouble(msg.chance);
        buf.writeDouble(msg.maxSpeed);
    }

    public static NaturalCurrentSaltPacket decode(FriendlyByteBuf buf)
    {
        return new NaturalCurrentSaltPacket(buf.readBoolean(), buf.readLong(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(NaturalCurrentSaltPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        // Registered with consumerMainThread: already on the client thread.
        NaturalCurrents.setClientSettings(msg.enabled ? msg.salt : null, msg.chance, msg.maxSpeed);
    }
}
