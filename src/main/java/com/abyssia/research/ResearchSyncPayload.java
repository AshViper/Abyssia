package com.abyssia.research;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C full sync (login, respawn, dimension change, datapack reload, reset): the technology and scan target definitions
 * and the player's state. Replaces everything {@link ClientResearch} holds.
 *
 * @param fragments target id to the number of counted discoveries (every scanned target has an entry)
 */
public record ResearchSyncPayload(List<Technology> technologies, List<ScanTarget> targets,
                                  Map<ResourceLocation, Integer> fragments, List<ResourceLocation> unlocked)
        implements CustomPacketPayload
{
    public static final Type<ResearchSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "research_sync"));
    public static final StreamCodec<FriendlyByteBuf, ResearchSyncPayload> STREAM_CODEC = StreamCodec.ofMember(ResearchSyncPayload::encode, ResearchSyncPayload::decode);

    private static final int MAX = 4096;

    @Override
    public Type<ResearchSyncPayload> type()
    {
        return TYPE;
    }

    public static void encode(ResearchSyncPayload msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.technologies.size());
        for (Technology t : msg.technologies) Technology.write(buf, t);
        buf.writeVarInt(msg.targets.size());
        for (ScanTarget t : msg.targets) ScanTarget.write(buf, t);
        writeFragments(buf, msg.fragments);
        buf.writeVarInt(msg.unlocked.size());
        for (ResourceLocation id : msg.unlocked) buf.writeResourceLocation(id);
    }

    public static ResearchSyncPayload decode(FriendlyByteBuf buf)
    {
        int nt = Math.min(buf.readVarInt(), MAX);
        List<Technology> techs = new ArrayList<>(nt);
        for (int i = 0; i < nt; i++) techs.add(Technology.read(buf));
        int ns = Math.min(buf.readVarInt(), MAX);
        List<ScanTarget> targets = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) targets.add(ScanTarget.read(buf));
        Map<ResourceLocation, Integer> fragments = readFragments(buf);
        int nu = Math.min(buf.readVarInt(), MAX);
        List<ResourceLocation> unlocked = new ArrayList<>(nu);
        for (int i = 0; i < nu; i++) unlocked.add(buf.readResourceLocation());
        return new ResearchSyncPayload(techs, targets, fragments, unlocked);
    }

    static void writeFragments(FriendlyByteBuf buf, Map<ResourceLocation, Integer> map)
    {
        buf.writeVarInt(map.size());
        map.forEach((id, n) -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(n);
        });
    }

    static Map<ResourceLocation, Integer> readFragments(FriendlyByteBuf buf)
    {
        int n = Math.min(buf.readVarInt(), MAX);
        Map<ResourceLocation, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) map.put(buf.readResourceLocation(), buf.readVarInt());
        return map;
    }

    public static void handle(ResearchSyncPayload msg, IPayloadContext ctx)
    {
        // playToClient handlers run on the client main thread.
        ClientResearch.applyFull(msg);
    }
}
