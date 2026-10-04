package com.abyssia.habitat.relay;

import com.abyssia.habitat.relay.client.RelayClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C (WR01): every relay link of the player's current level, replacing the client's list. state 0 paused (not drawn),
 * 1 idle (link only), 2 a -> b, 3 b -> a; band 0..3 = delivered FE/t below 128 / 256 / 384 / above.
 */
public record RelaySyncPacket(List<Entry> links)
{
    private static final int MAX_LINKS = 16384;

    public record Entry(BlockPos a, BlockPos b, byte state, byte band) {}

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

    public static void handle(RelaySyncPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        // Registered with consumerMainThread: already on the client thread.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RelayClient.setLinks(msg.links));
    }
}
