package com.abyssia.research;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * One scannable thing (data/abyssia/scan_targets/*.json). {@code nameKey} is a translation key, {@code scanSeconds} the hold
 * time, {@code range} the longest scan distance in blocks (before the scanner tier), {@code match} how the server
 * recognises it, {@code fragmentKey} what makes two scans of it "the same discovery".
 */
public record ScanTarget(ResourceLocation id, String category, String nameKey, float scanSeconds, double range,
                         Match match, FragmentKey fragmentKey)
{
    public enum MatchType
    {
        BLOCK("block"), BLOCK_TAG("block_tag"), ENTITY("entity"), DEPOSIT_MINERAL("deposit_mineral"), STRUCTURE("structure");

        public final String json;

        MatchType(String json)
        {
            this.json = json;
        }

        public static MatchType byJson(String s)
        {
            for (MatchType t : values()) if (t.json.equals(s)) return t;
            return null;
        }
    }

    /** position: every block position counts as a new fragment; type: the target counts once. */
    public enum FragmentKey
    {
        POSITION, TYPE
    }

    public record Match(MatchType type, ResourceLocation value, int radius)
    {
        public Match(MatchType type, ResourceLocation value) { this(type, value, 0); }
    }

    public static void write(FriendlyByteBuf buf, ScanTarget t)
    {
        buf.writeResourceLocation(t.id);
        buf.writeUtf(t.category, 64);
        buf.writeUtf(t.nameKey, 256);
        buf.writeFloat(t.scanSeconds);
        buf.writeDouble(t.range);
        buf.writeEnum(t.match.type);
        buf.writeResourceLocation(t.match.value);
        buf.writeEnum(t.fragmentKey);
    }

    public static ScanTarget read(FriendlyByteBuf buf)
    {
        ResourceLocation id = buf.readResourceLocation();
        String category = buf.readUtf(64);
        String nameKey = buf.readUtf(256);
        float seconds = buf.readFloat();
        double range = buf.readDouble();
        MatchType type = buf.readEnum(MatchType.class);
        ResourceLocation value = buf.readResourceLocation();
        FragmentKey key = buf.readEnum(FragmentKey.class);
        return new ScanTarget(id, category, nameKey, seconds, range, new Match(type, value), key);
    }
}
