package com.abyssia.fauna;

import com.abyssia.worldgen.DeepLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Water depth in blocks and the real-ocean depth it stands for.
 * <p>
 * The mod's depth bands (marine snow, fog) change character at 40, 100, 200 and 300 blocks below the ocean surface,
 * continuing from the ocean world into the deep layer below its bedrock band. Those are the standard oceanographic zone boundaries at
 * 200, 1000, 4000 and 6000 m, so metres interpolate between them (380 blocks = 11000 m, the deepest trench). Fauna
 * spawn rules give real depth ranges in metres; this maps them onto the world.
 */
public final class DepthZone
{
    public static final int OCEAN_SURFACE_Y = 63;
    /** Deepest ocean world Y the depth scale follows (the old transition depth the depth bands were tuned to). */
    private static final int OCEAN_DEPTH_FLOOR_Y = 0;
    /** Old deep-ocean Y minus this = the ocean world Y of the same depth: deep Y 200 reads as Y 0 (63 blocks). */
    private static final int DEEP_OFFSET = DeepLayer.DEPTH_ORIGIN_DEEP_Y - OCEAN_DEPTH_FLOOR_Y;
    /** Overworld Y (-40) where the depth scale switches to the deep one: deep Y 200 = 63 blocks, the value the ocean clamp holds. */
    private static final double DEEP_SCALE_TOP_Y = DeepLayer.fromDeepY(DeepLayer.DEPTH_ORIGIN_DEEP_Y);
    /**
     * The last two points: 392 blocks is the old deep layer's floor (deep Y -128, 11750 m on the old extrapolation); under it
     * lies the abyss layer (to Y -1008, about 1030 blocks), whose scale rises slowly instead of at 62.5 m per block.
     */
    private static final double[] BLOCKS = {0, 40, 100, 200, 300, 380, 392, 1100};
    private static final double[] METRES = {0, 200, 1000, 4000, 6000, 11000, 11750, 14000};

    /** Pelagic zones by depth: sunlit, twilight, midnight, abyssal and hadal. */
    public enum Zone
    {
        EPIPELAGIC(0), MESOPELAGIC(200), BATHYPELAGIC(1000), ABYSSOPELAGIC(4000), HADALPELAGIC(6000);

        public final int fromMetres;

        Zone(int fromMetres)
        {
            this.fromMetres = fromMetres;
        }

        public static Zone of(double metres)
        {
            Zone zone = EPIPELAGIC;
            for (Zone z : values()) if (metres >= z.fromMetres) zone = z;
            return zone;
        }
    }

    private DepthZone() {}

    /**
     * Blocks below the ocean surface. Ocean world water from {@link #OCEAN_DEPTH_FLOOR_Y} down to Y -40 (trench floors,
     * abyssal rifts) holds 63 blocks, and from there down (shafts and the bedrock band included) the scale carries on in
     * old deep-ocean Y from {@link DeepLayer#DEPTH_ORIGIN_DEEP_Y} = 63 blocks, so it is continuous and never decreases
     * with depth, and the deep layer's depth bands stay where they were tuned.
     */
    public static double blocksBelowSurface(Level level, double y)
    {
        if (level.dimension() == Level.OVERWORLD)
        {
            // below the old transition depth (Y -40) the scale carries on in old deep-ocean Y, shafts through the bedrock band included
            if (y < DEEP_SCALE_TOP_Y) return deepBlocks(DeepLayer.toDeepY(y));
            y = Math.max(y, OCEAN_DEPTH_FLOOR_Y);
        }
        return OCEAN_SURFACE_Y - y;
    }

    /** Blocks below the ocean surface at an old deep-ocean Y. */
    public static double deepBlocks(double deepY)
    {
        return OCEAN_SURFACE_Y - (deepY - DEEP_OFFSET);
    }

    /** Old deep-ocean Y at a depth in blocks (inverse of {@link #deepBlocks}). */
    public static double deepY(double blocks)
    {
        return OCEAN_SURFACE_Y - blocks + DEEP_OFFSET;
    }

    /** Metres at a Y in a level: ocean world, or the deep layer below its bedrock band. */
    public static double metres(Level level, double y)
    {
        return metres(blocksBelowSurface(level, y));
    }

    /**
     * Metres at an old deep-ocean Y, without a Level (worldgen filters pass {@code DeepLayer.toDeepY(y)}).
     * Use {@link #metres(Level, double)} for overworld positions.
     */
    public static double deepOceanMetres(double deepY)
    {
        return metres(deepBlocks(deepY));
    }

    public static double metres(double blocks)
    {
        return interpolate(blocks, BLOCKS, METRES);
    }

    public static double blocks(double metres)
    {
        return interpolate(metres, METRES, BLOCKS);
    }

    private static double interpolate(double v, double[] from, double[] to)
    {
        if (v <= from[0]) return to[0] + (v - from[0]) * (to[1] - to[0]) / (from[1] - from[0]);
        for (int i = 1; i < from.length; i++)
        {
            if (v <= from[i]) return Mth.lerp((v - from[i - 1]) / (from[i] - from[i - 1]), to[i - 1], to[i]);
        }
        int n = from.length - 1;
        return to[n] + (v - from[n]) * (to[n] - to[n - 1]) / (from[n] - from[n - 1]);
    }
}
