package com.abyssia.entity.boss;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: root-relative joint positions of a boss chain (joints 1..n-1, {@link BossChain#UNIT} units, x y z each). {@code chain} 0 = the spine, 1.. = tentacles. */
public record BossChainPayload(int entityId, int chain, short[] rel)
{
    public BossChainPayload(int entityId, short[] rel)
    {
        this(entityId, 0, rel);
    }


    public static void encode(BossChainPayload msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.chain);
        buf.writeVarInt(msg.rel.length);
        for (short s : msg.rel) buf.writeShort(s);
    }

    public static BossChainPayload decode(FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        int chain = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 3 * 2048);
        short[] rel = new short[n];
        for (int i = 0; i < n; i++) rel[i] = buf.readShort();
        return new BossChainPayload(id, chain, rel);
    }

    public static void handle(BossChainPayload msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.abyssia.client.boss.BossChainClient.apply(msg)));
        ctx.get().setPacketHandled(true);
    }
}
