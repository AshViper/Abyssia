package com.abyssia.waypoint;

import com.abyssia.Abyssia;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** C2S (QOL01): the travel screen's pick. The server re-checks everything in {@link BeaconTravel#travel}. */
public record BeaconTravelPacket(BlockPos from, BlockPos to) implements CustomPacketPayload
{
    public static final Type<BeaconTravelPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "beacon_travel"));
    public static final StreamCodec<FriendlyByteBuf, BeaconTravelPacket> STREAM_CODEC = StreamCodec.ofMember(BeaconTravelPacket::encode, BeaconTravelPacket::decode);

    @Override
    public Type<BeaconTravelPacket> type()
    {
        return TYPE;
    }

    public static void encode(BeaconTravelPacket msg, FriendlyByteBuf buf)
    {
        buf.writeBlockPos(msg.from);
        buf.writeBlockPos(msg.to);
    }

    public static BeaconTravelPacket decode(FriendlyByteBuf buf)
    {
        return new BeaconTravelPacket(buf.readBlockPos(), buf.readBlockPos());
    }

    public static void handle(BeaconTravelPacket msg, IPayloadContext ctx)
    {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        BeaconTravel.Result result = BeaconTravel.travel(player, msg.from, msg.to);
        player.displayClientMessage(Component.translatable("message.abyssia.beacon_travel." + result.name().toLowerCase(Locale.ROOT)), true);
    }
}
