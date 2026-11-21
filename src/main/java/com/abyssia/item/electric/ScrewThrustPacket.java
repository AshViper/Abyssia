package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: the player started / stopped thrusting with the propulsion screw (left-click held). The server re-validates every tick. */
public record ScrewThrustPacket(boolean on) implements CustomPacketPayload
{
    public static final Type<ScrewThrustPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "screw_thrust"));
    public static final StreamCodec<FriendlyByteBuf, ScrewThrustPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeBoolean(msg.on), buf -> new ScrewThrustPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScrewThrustPacket msg, IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            if (context.player() instanceof ServerPlayer player) PropulsionScrewServer.set(player, msg.on);
        });
    }
}
