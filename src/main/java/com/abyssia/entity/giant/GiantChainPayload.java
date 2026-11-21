package com.abyssia.entity.giant;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: head-relative joint positions of a giant's chain (joints 1..n-1, {@link com.abyssia.entity.boss.BossChain#UNIT} units, x y z each). */
public record GiantChainPayload(int entityId, short[] rel)
{
    public static void encode(GiantChainPayload msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.rel.length);
        for (short s : msg.rel) buf.writeShort(s);
    }

    public static GiantChainPayload decode(FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 3 * 256);
        short[] rel = new short[n];
        for (int i = 0; i < n; i++) rel[i] = buf.readShort();
        return new GiantChainPayload(id, rel);
    }

    public static void handle(GiantChainPayload msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.abyssia.client.giant.GiantChainClient.apply(msg)));
        ctx.get().setPacketHandled(true);
    }
}
