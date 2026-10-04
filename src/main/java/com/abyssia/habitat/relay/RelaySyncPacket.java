package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import com.abyssia.habitat.relay.client.RelayClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: every WR01 link of the player's current level (replaces the client's list). state: 0 paused (not drawn),
 * 1 idle, 2 a -> b, 3 b -> a; band 0..3 = delivered FE/t below 128 / 256 / 384 / above.
 */
public record RelaySyncPacket(List<Entry> links) implements CustomPacketPayload
{
    public static final Type<RelaySyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "relay_sync"));
    public static final StreamCodec<FriendlyByteBuf, RelaySyncPacket> STREAM_CODEC = StreamCodec.ofMember(RelaySyncPacket::encode, RelaySyncPacket::decode);

    private static final int MAX_LINKS = 16384;

    public record Entry(BlockPos a, BlockPos b, byte state, byte band) {}

    @Override
    public Type<RelaySyncPacket> type()
    {
        return TYPE;
    }

    public static void encode(RelaySyncPacket msg, FriendlyByteBuf buf)
    {
        int n = Math.min(msg.links.size(), MAX_LINKS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++)
        {
            Entry e = msg.links.get(i);
            buf.writeLong(e.a().asLong());
            buf.writeLong(e.b().asLong());
            buf.writeByte(e.state());
            buf.writeByte(e.band());
        }
    }

    public static RelaySyncPacket decode(FriendlyByteBuf buf)
    {
        int n = Math.min(buf.readVarInt(), MAX_LINKS);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            list.add(new Entry(BlockPos.of(buf.readLong()), BlockPos.of(buf.readLong()), buf.readByte(), buf.readByte()));
        return new RelaySyncPacket(list);
    }

    public static void handle(RelaySyncPacket msg, IPayloadContext ctx)
    {
        // playToClient handlers run on the client main thread.
        RelayClient.setLinks(msg.links);
    }
}
