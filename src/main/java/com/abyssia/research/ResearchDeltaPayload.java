package com.abyssia.research;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S2C change since the last sync: targets whose counted discoveries changed (also marks them scanned) and technologies
 * newly unlocked. Resets are sent as a {@link ResearchSyncPayload} instead.
 */
public record ResearchDeltaPayload(Map<ResourceLocation, Integer> fragments, List<ResourceLocation> unlocked)
        implements CustomPacketPayload
{
    public static final Type<ResearchDeltaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "research_delta"));
    public static final StreamCodec<FriendlyByteBuf, ResearchDeltaPayload> STREAM_CODEC = StreamCodec.ofMember(ResearchDeltaPayload::encode, ResearchDeltaPayload::decode);

    @Override
    public Type<ResearchDeltaPayload> type()
    {
        return TYPE;
    }

    public static void encode(ResearchDeltaPayload msg, FriendlyByteBuf buf)
    {
        ResearchSyncPayload.writeFragments(buf, msg.fragments);
        buf.writeVarInt(msg.unlocked.size());
        for (ResourceLocation id : msg.unlocked) buf.writeResourceLocation(id);
    }

    public static ResearchDeltaPayload decode(FriendlyByteBuf buf)
    {
        Map<ResourceLocation, Integer> fragments = ResearchSyncPayload.readFragments(buf);
        int n = Math.min(buf.readVarInt(), 4096);
        List<ResourceLocation> unlocked = new ArrayList<>(n);
        for (int i = 0; i < n; i++) unlocked.add(buf.readResourceLocation());
        return new ResearchDeltaPayload(fragments, unlocked);
    }

    public static void handle(ResearchDeltaPayload msg, IPayloadContext ctx)
    {
        ClientResearch.applyDelta(msg);
    }
}
