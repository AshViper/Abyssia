package com.abyssia.entity.giant;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: head-relative joint positions of a giant's chain (joints 1..n-1, {@link com.abyssia.entity.boss.BossChain#UNIT} units, x y z each). */
public record GiantChainPayload(int entityId, short[] rel) implements CustomPacketPayload
{
    public static final Type<GiantChainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "giant_chain"));
    public static final StreamCodec<FriendlyByteBuf, GiantChainPayload> STREAM_CODEC = StreamCodec.ofMember(GiantChainPayload::encode, GiantChainPayload::decode);

    @Override
    public Type<GiantChainPayload> type()
    {
        return TYPE;
    }

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

    public static void handle(GiantChainPayload msg, IPayloadContext ctx)
    {
        com.abyssia.client.giant.GiantChainClient.apply(msg);
    }
}
