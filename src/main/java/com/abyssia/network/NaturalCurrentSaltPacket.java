package com.abyssia.network;

import com.abyssia.Abyssia;
import com.abyssia.environment.CurrentStreams;
import com.abyssia.environment.NaturalCurrents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client on login: where natural currents and CU01 current streams are. Carries the one-way salt of the world
 * seed (never the seed), the server's natural-current chance and drift speed, and the server's stream settings, so the
 * client rebuilds exactly the server's streams.
 */
public record NaturalCurrentSaltPacket(boolean enabled, long salt, double chance, double maxSpeed,
                                       boolean streamsEnabled, double streamChance, int streamCell,
                                       double minLength, double maxLength, double minWidth, double maxWidth,
                                       double minStrength, double maxStrength, double baseFlowSpeed, double maxFlowSpeed) implements CustomPacketPayload
{
    public static final Type<NaturalCurrentSaltPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "natural_current_salt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NaturalCurrentSaltPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) ->
            {
                buf.writeBoolean(msg.enabled);
                buf.writeLong(msg.salt);
                buf.writeDouble(msg.chance);
                buf.writeDouble(msg.maxSpeed);
                buf.writeBoolean(msg.streamsEnabled);
                buf.writeDouble(msg.streamChance);
                buf.writeVarInt(msg.streamCell);
                buf.writeDouble(msg.minLength);
                buf.writeDouble(msg.maxLength);
                buf.writeDouble(msg.minWidth);
                buf.writeDouble(msg.maxWidth);
                buf.writeDouble(msg.minStrength);
                buf.writeDouble(msg.maxStrength);
                buf.writeDouble(msg.baseFlowSpeed);
                buf.writeDouble(msg.maxFlowSpeed);
            },
            buf -> new NaturalCurrentSaltPacket(buf.readBoolean(), buf.readLong(), buf.readDouble(), buf.readDouble(),
                    buf.readBoolean(), buf.readDouble(), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    /** The packet for a server with these stream settings ({@code streams} null when streams are off). */
    public static NaturalCurrentSaltPacket of(boolean naturalEnabled, long salt, double chance, double maxSpeed, CurrentStreams.Settings streams)
    {
        if (streams == null) return new NaturalCurrentSaltPacket(naturalEnabled, salt, chance, maxSpeed, false, 0, 192, 0, 0, 0, 0, 0, 0, 0, 0);
        return new NaturalCurrentSaltPacket(naturalEnabled, salt, chance, maxSpeed, true, streams.chance(), streams.cell(),
                streams.minLength(), streams.maxLength(), streams.minWidth(), streams.maxWidth(),
                streams.minStrength(), streams.maxStrength(), streams.baseSpeed(), streams.maxSpeed());
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(NaturalCurrentSaltPacket msg, IPayloadContext ctx)
    {
        // NeoForge runs payload handlers on the main thread by default.
        NaturalCurrents.setClientSettings(msg.enabled ? msg.salt : null, msg.chance, msg.maxSpeed);
        CurrentStreams.setClientSettings(msg.streamsEnabled ? new CurrentStreams.Settings(msg.salt, msg.streamChance, Math.max(1, msg.streamCell),
                msg.minLength, msg.maxLength, msg.minWidth, msg.maxWidth, msg.minStrength, msg.maxStrength, msg.baseFlowSpeed, msg.maxFlowSpeed) : null);
    }
}
