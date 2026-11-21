package com.abyssia.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

/** C2S (QOL01): the travel screen's pick. The server re-checks everything in {@link BeaconTravel#travel}. */
public record BeaconTravelPacket(BlockPos from, BlockPos to)
{
    public static void encode(BeaconTravelPacket msg, FriendlyByteBuf buf)
    {
        buf.writeBlockPos(msg.from);
        buf.writeBlockPos(msg.to);
    }

    public static BeaconTravelPacket decode(FriendlyByteBuf buf)
    {
        return new BeaconTravelPacket(buf.readBlockPos(), buf.readBlockPos());
    }

    /** registered with consumerMainThread (runs on the server thread) */
    public static void handle(BeaconTravelPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        BeaconTravel.Result result = BeaconTravel.travel(player, msg.from, msg.to);
        player.displayClientMessage(Component.translatable("message.abyssia.beacon_travel." + result.name().toLowerCase(Locale.ROOT)), true);
    }
}
