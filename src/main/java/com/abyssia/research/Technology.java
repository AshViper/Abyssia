package com.abyssia.research;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A technology (data/abyssia/technologies/*.json): unlocked when every scan requirement and prerequisite is met; its
 * {@code unlocks} keys ({@code abyssia:building/...}, {@code abyssia:machine/...}, {@code abyssia:recipe_group/...}) are what the game checks.
 * {@code titleKey} is a translation key (the description is {@code titleKey + ".desc"}); {@code icon} an item id or null.
 */
public record Technology(ResourceLocation id, String type, String titleKey, String tier, String depthBand,
                         List<Requirement> requirements, List<ResourceLocation> prerequisites,
                         List<ResourceLocation> unlocks, ResourceLocation icon)
{
    public record Requirement(ResourceLocation target, int count) {}

    private static final int MAX_LIST = 256;

    public static void write(FriendlyByteBuf buf, Technology t)
    {
        buf.writeResourceLocation(t.id);
        buf.writeUtf(t.type, 64);
        buf.writeUtf(t.titleKey, 256);
        buf.writeUtf(t.tier, 16);
        buf.writeUtf(t.depthBand, 16);
        buf.writeVarInt(t.requirements.size());
        for (Requirement r : t.requirements)
        {
            buf.writeResourceLocation(r.target);
            buf.writeVarInt(r.count);
        }
        writeIds(buf, t.prerequisites);
        writeIds(buf, t.unlocks);
        buf.writeBoolean(t.icon != null);
        if (t.icon != null) buf.writeResourceLocation(t.icon);
    }

    public static Technology read(FriendlyByteBuf buf)
    {
        ResourceLocation id = buf.readResourceLocation();
        String type = buf.readUtf(64);
        String title = buf.readUtf(256);
        String tier = buf.readUtf(16);
        String band = buf.readUtf(16);
        int n = Math.min(buf.readVarInt(), MAX_LIST);
        List<Requirement> reqs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) reqs.add(new Requirement(buf.readResourceLocation(), buf.readVarInt()));
        List<ResourceLocation> pre = readIds(buf);
        List<ResourceLocation> unlocks = readIds(buf);
        ResourceLocation icon = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new Technology(id, type, title, tier, band, List.copyOf(reqs), pre, unlocks, icon);
    }

    private static void writeIds(FriendlyByteBuf buf, List<ResourceLocation> ids)
    {
        buf.writeVarInt(ids.size());
        for (ResourceLocation id : ids) buf.writeResourceLocation(id);
    }

    private static List<ResourceLocation> readIds(FriendlyByteBuf buf)
    {
        int n = Math.min(buf.readVarInt(), MAX_LIST);
        List<ResourceLocation> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readResourceLocation());
        return List.copyOf(list);
    }
}
