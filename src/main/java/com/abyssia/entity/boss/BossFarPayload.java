package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, every 20 ticks to players within {@link #RANGE} blocks: the boss kind, the head position and every
 * {@link #STEP}th joint (head-relative, {@link BossChain#UNIT} units, x y z each) for the far silhouette
 * (BOSS01 LOD), plus whether a fight is going on. Independent of entity tracking.
 */
public record BossFarPayload(int entityId, int kind, boolean combat, double hx, double hy, double hz, short[] rel) implements CustomPacketPayload
{
    public static final int RANGE = 512;
    public static final int STEP = 4;
    public static final Type<BossFarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "boss_far"));
    public static final StreamCodec<FriendlyByteBuf, BossFarPayload> STREAM_CODEC = StreamCodec.ofMember(BossFarPayload::encode, BossFarPayload::decode);

    @Override
    public Type<BossFarPayload> type()
    {
        return TYPE;
    }

    public static void encode(BossFarPayload msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.kind);
        buf.writeBoolean(msg.combat);
        buf.writeDouble(msg.hx);
        buf.writeDouble(msg.hy);
        buf.writeDouble(msg.hz);
        buf.writeVarInt(msg.rel.length);
        for (short s : msg.rel) buf.writeShort(s);
    }

    public static BossFarPayload decode(FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        int kind = buf.readByte();
        boolean combat = buf.readBoolean();
        double hx = buf.readDouble(), hy = buf.readDouble(), hz = buf.readDouble();
        int n = Math.min(buf.readVarInt(), 3 * 1024);
        short[] rel = new short[n];
        for (int i = 0; i < n; i++) rel[i] = buf.readShort();
        return new BossFarPayload(id, kind, combat, hx, hy, hz, rel);
    }

    public static void handle(BossFarPayload msg, IPayloadContext ctx)
    {
        com.abyssia.client.boss.BossFarClient.apply(msg);
    }
}
