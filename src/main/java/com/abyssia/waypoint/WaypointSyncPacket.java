package com.abyssia.waypoint;

import com.abyssia.Abyssia;
import com.abyssia.waypoint.client.WaypointClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** S2C: every beacon the player may see in their current dimension (replaces the client's list), plus the name limit. */
public record WaypointSyncPacket(List<WaypointEntry> beacons, int maxNameLength) implements CustomPacketPayload
{
    public static final Type<WaypointSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "waypoint_sync"));
    public static final StreamCodec<FriendlyByteBuf, WaypointSyncPacket> STREAM_CODEC = StreamCodec.ofMember(WaypointSyncPacket::encode, WaypointSyncPacket::decode);

    private static final int MAX_BEACONS = 8192;

    @Override
    public Type<WaypointSyncPacket> type()
    {
        return TYPE;
    }

    public static void encode(WaypointSyncPacket msg, FriendlyByteBuf buf)
    {
        int n = Math.min(msg.beacons.size(), MAX_BEACONS);
        buf.writeVarInt(msg.maxNameLength);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++)
        {
            WaypointEntry e = msg.beacons.get(i);
            buf.writeLong(e.pos().asLong());
            buf.writeUtf(e.name(), 256);
            buf.writeByte(e.color());
        }
    }

    public static WaypointSyncPacket decode(FriendlyByteBuf buf)
    {
        int maxName = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), MAX_BEACONS);
        List<WaypointEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            list.add(new WaypointEntry(BlockPos.of(buf.readLong()), buf.readUtf(256), WaypointColors.clamp(buf.readByte()), null));
        return new WaypointSyncPacket(list, maxName);
    }

    public static void handle(WaypointSyncPacket msg, IPayloadContext ctx)
    {
        // playToClient handlers run on the client main thread.
        WaypointClient.setBeacons(msg.beacons, msg.maxNameLength);
    }
}
