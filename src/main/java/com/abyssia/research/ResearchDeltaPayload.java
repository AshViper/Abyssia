package com.abyssia.research;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C change since the last sync: targets whose counted discoveries changed (also marks them scanned) and technologies
 * newly unlocked. Resets are sent as a {@link ResearchSyncPayload} instead.
 */
public record ResearchDeltaPayload(Map<ResourceLocation, Integer> fragments, List<ResourceLocation> unlocked)
{
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

    public static void handle(ResearchDeltaPayload msg, Supplier<NetworkEvent.Context> ctx)
    {
        // Registered with consumerMainThread: already on the main thread.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientResearch.applyDelta(msg));
    }
}
