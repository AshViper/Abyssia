package com.abyssia.network;

import com.abyssia.environment.CurrentStreams;
import com.abyssia.environment.NaturalCurrents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client on login: where natural currents and CU01 current streams are. Carries the one-way salt of the world
 * seed (never the seed), the server's natural-current chance and drift speed, and the server's current stream settings
 * (null when streams are off), so the client rebuilds exactly the server's streams.
 */
public record NaturalCurrentSaltPacket(boolean enabled, long salt, double chance, double maxSpeed, CurrentStreams.Params streams)
{
    public static void encode(NaturalCurrentSaltPacket msg, FriendlyByteBuf buf)
    {
        buf.writeBoolean(msg.enabled);
        buf.writeLong(msg.salt);
        buf.writeDouble(msg.chance);
        buf.writeDouble(msg.maxSpeed);
        CurrentStreams.Params p = msg.streams;
        buf.writeBoolean(p != null);
        if (p == null) return;
        buf.writeDouble(p.chance());
        buf.writeVarInt(p.cellSize());
        buf.writeDouble(p.minLength());
        buf.writeDouble(p.maxLength());
        buf.writeDouble(p.minWidth());
        buf.writeDouble(p.maxWidth());
        buf.writeDouble(p.minStrength());
        buf.writeDouble(p.maxStrength());
        buf.writeDouble(p.baseFlowSpeed());
        buf.writeDouble(p.maxFlowSpeed());
    }

    public static NaturalCurrentSaltPacket decode(FriendlyByteBuf buf)
    {
        boolean enabled = buf.readBoolean();
        long salt = buf.readLong();
        double chance = buf.readDouble(), maxSpeed = buf.readDouble();
        CurrentStreams.Params streams = buf.readBoolean()
                ? new CurrentStreams.Params(buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())
                : null;
        return new NaturalCurrentSaltPacket(enabled, salt, chance, maxSpeed, streams);
    }

    public static void handle(NaturalCurrentSaltPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        // Registered with consumerMainThread: already on the client thread.
        NaturalCurrents.setClientSettings(msg.enabled ? msg.salt : null, msg.chance, msg.maxSpeed);
        CurrentStreams.setClientSettings(msg.streams != null ? msg.salt : null, msg.streams);
    }
}
