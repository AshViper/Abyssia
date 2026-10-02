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
    public static final int RADIUS = 32, HALF_HEIGHT = 24, CELL = 4;
    public static final int GRID_X = 17, GRID_Y = 13, GRID_Z = 17;
    public static final ScanData EMPTY = new ScanData(List.of(), new int[0], new int[0], new int[0], new BitSet(), false);

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

    public ScanData(List<ResourceLocation> palette, int[] counts, int[] hits, int[] kinds, BitSet terrain, boolean done)
    {
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

    public static int cellIndex(int dx, int dy, int dz)
    {
        int cx = Math.floorDiv(dx + RADIUS + 2, CELL), cy = Math.floorDiv(dy + HALF_HEIGHT + 2, CELL), cz = Math.floorDiv(dz + RADIUS + 2, CELL);
        return cell(cx, cy, cz);
    }

    public static int cell(int cx, int cy, int cz)
    {
        return (cy * GRID_Z + cz) * GRID_X + cx;
    }

    public boolean solid(int cx, int cy, int cz)
    {
        if (cx < 0 || cy < 0 || cz < 0 || cx >= GRID_X || cy >= GRID_Y || cz >= GRID_Z) return false;
        return terrain.get(cell(cx, cy, cz));
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
                tag.getBoolean("Done"));
    }
}
