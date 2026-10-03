package com.abyssia.habitat.scan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Result of one scan, relative to the console: the nearest target blocks (offset + palette index), how many of each
 * kind were found in range, and the coarse terrain (4 x 4 x 4 cells, set when at least half the scanned blocks are
 * solid). Immutable once built.
 */
public final class ScanData
{
    public static final int CELL = 4;
    /** BT01f upgrade levels 0..3: horizontal radius / half height */
    public static final int MAX_TIER = 3;
    private static final int[] RADII = {32, 48, 64, 96}, HALF_HEIGHTS = {24, 32, 40, 48};
    public static final ScanData EMPTY = new ScanData(List.of(), new int[0], new int[0], new int[0], new BitSet(), false, 0);

    public static int clampTier(int tier)
    {
        return Math.max(0, Math.min(MAX_TIER, tier));
    }

    public static int radius(int tier)
    {
        return RADII[clampTier(tier)];
    }

    public static int halfHeight(int tier)
    {
        return HALF_HEIGHTS[clampTier(tier)];
    }

    /** terrain cells along x / z (cells of 4 around the block centres, 2 blocks of margin) */
    public static int gridXZ(int tier)
    {
        return Math.floorDiv(radius(tier) * 2 + 2, CELL) + 1;
    }

    public static int gridY(int tier)
    {
        return Math.floorDiv(halfHeight(tier) * 2 + 2, CELL) + 1;
    }

    /** block ids, sorted */
    public final List<ResourceLocation> palette;
    /** total found in range per palette entry */
    public final int[] counts;
    /** packed offsets (see {@link #pack}) of the kept hits, nearest first */
    public final int[] hits;
    /** palette index of each hit */
    public final int[] kinds;
    public final BitSet terrain;
    public final boolean done;
    /** upgrade level this result was scanned at (sizes the terrain grid) */
    public final int tier;

    public ScanData(List<ResourceLocation> palette, int[] counts, int[] hits, int[] kinds, BitSet terrain, boolean done, int tier)
    {
        this.tier = clampTier(tier);
        this.palette = palette;
        this.counts = counts;
        this.hits = hits;
        this.kinds = kinds;
        this.terrain = terrain;
        this.done = done;
    }

    public static int pack(int dx, int dy, int dz)
    {
        return ((dx + 128) & 0xFF) << 16 | ((dy + 128) & 0xFF) << 8 | ((dz + 128) & 0xFF);
    }

    public static int dx(int packed) { return ((packed >> 16) & 0xFF) - 128; }

    public static int dy(int packed) { return ((packed >> 8) & 0xFF) - 128; }

    public static int dz(int packed) { return (packed & 0xFF) - 128; }

    public static int cellIndex(int tier, int dx, int dy, int dz)
    {
        int r = radius(tier);
        int cx = Math.floorDiv(dx + r + 2, CELL), cy = Math.floorDiv(dy + halfHeight(tier) + 2, CELL), cz = Math.floorDiv(dz + r + 2, CELL);
        return cell(tier, cx, cy, cz);
    }

    public static int cell(int tier, int cx, int cy, int cz)
    {
        int gx = gridXZ(tier);
        return (cy * gx + cz) * gx + cx;
    }

    public boolean solid(int cx, int cy, int cz)
    {
        int gx = gridXZ(tier);
        if (cx < 0 || cy < 0 || cz < 0 || cx >= gx || cy >= gridY(tier) || cz >= gx) return false;
        return terrain.get(cell(tier, cx, cy, cz));
    }

    public int total()
    {
        int sum = 0;
        for (int c : counts) sum += c;
        return sum;
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        ListTag ids = new ListTag();
        for (ResourceLocation id : palette) ids.add(StringTag.valueOf(id.toString()));
        tag.put("Palette", ids);
        tag.putIntArray("Counts", counts);
        tag.putIntArray("Hits", hits);
        tag.putIntArray("Kinds", kinds);
        tag.putLongArray("Terrain", terrain.toLongArray());
        tag.putBoolean("Done", done);
        tag.putInt("Tier", tier);
        return tag;
    }

    public static ScanData load(CompoundTag tag)
    {
        List<ResourceLocation> palette = new ArrayList<>();
        for (Tag t : tag.getList("Palette", Tag.TAG_STRING))
        {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            palette.add(id == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "air") : id);
        }
        int[] hits = tag.getIntArray("Hits"), kinds = tag.getIntArray("Kinds");
        if (kinds.length != hits.length) kinds = new int[hits.length];
        return new ScanData(palette, tag.getIntArray("Counts"), hits, kinds, BitSet.valueOf(tag.getLongArray("Terrain")),
                tag.getBoolean("Done"), tag.getInt("Tier"));
    }
}
