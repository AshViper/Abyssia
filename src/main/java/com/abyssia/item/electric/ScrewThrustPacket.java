package com.abyssia.item.electric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: the player started / stopped thrusting with the propulsion screw (left-click held). The server re-validates every tick. */
public record ScrewThrustPacket(boolean on)
{
    public static void encode(ScrewThrustPacket msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.on); }

    public static ScrewThrustPacket decode(FriendlyByteBuf buf) { return new ScrewThrustPacket(buf.readBoolean()); }

    public static void handle(ScrewThrustPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) PropulsionScrewServer.set(player, msg.on);
    }
}
