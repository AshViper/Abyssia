package com.abyssia.worldgen;

import javax.annotation.Nullable;

/**
 * AB02 cave windows: the absolute Y ranges of the free-floating cave networks inside the abyss crust (the shallow,
 * seabed-relative network keeps living at Y >= {@link DeepLayer#DEEP_BOTTOM_Y} and is not a window). Every window
 * has its own grid, caches and random streams; a vertical link may run up to {@link #OVERLAP} blocks (and further, to
 * the hub of a system above) into the window above. {@link #ordinal()} is the window index; B is the topmost.
 */
public enum DepthBand
{
    /** B' in the design notes: the crust right under the deep layer, with the hadal shafts reaching its upper part. */
    B("b", DeepLayer.CAVE_WINDOW_B_BOTTOM_Y, DeepLayer.ABYSS_TOP_Y - 4),
    C("c", DeepLayer.CRUST_BOTTOM_Y, DeepLayer.CAVE_WINDOW_B_BOTTOM_Y),
    // The world is only ~190 blocks of crust deep: D and E have no cave systems (zero height, CaveNetwork skips windows under 64 blocks).
    D("d", DeepLayer.CRUST_BOTTOM_Y, DeepLayer.CRUST_BOTTOM_Y),
    E("e", DeepLayer.CRUST_BOTTOM_Y, DeepLayer.CRUST_BOTTOM_Y);

    /** Blocks by which a vertical link reaches into the window above. */
    public static final int OVERLAP = 40;

    public final String id;
    /** Lowest and highest block a system of this window may occupy (caves keep clear of both by a margin). */
    public final int bottom, top;

    DepthBand(String id, int bottom, int top)
    {
        this.id = id;
        this.bottom = bottom;
        this.top = top;
    }

    public int height()
    {
        return top - bottom;
    }

    /** The window above this one (null for the topmost). */
    @Nullable
    public DepthBand above()
    {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }

    /** The window containing this Y, or null above the crust windows or below them. */
    @Nullable
    public static DepthBand forY(double y)
    {
        for (DepthBand band : values())
        {
            if (y >= band.bottom && y < band.top) return band;
        }
        return null;
    }

    /** Short label for debug output ("B'" for the topmost window). */
    public String label()
    {
        return this == B ? "B'" : id.toUpperCase();
    }
}
