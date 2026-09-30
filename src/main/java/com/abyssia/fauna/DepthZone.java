package com.abyssia.fauna;

import com.abyssia.Config;
import com.abyssia.DeepOceanTransition;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Water depth in blocks and the real-ocean depth it stands for.
 * <p>
 * The mod's depth bands (marine snow, fog) change character at 40, 100, 200 and 300 blocks below the ocean surface,
 * continuous across the ocean world / deep ocean boundary. Those are the standard oceanographic zone boundaries at
 * 200, 1000, 4000 and 6000 m, so metres interpolate between them (380 blocks = 11000 m, the deepest trench). Fauna
 * spawn rules give real depth ranges in metres; this maps them onto the world.
 */
public final class DepthZone
{
    public static final int OCEAN_SURFACE_Y = 63;
    private static final double[] BLOCKS = {0, 40, 100, 200, 300, 380};
    private static final double[] METRES = {0, 200, 1000, 4000, 6000, 11000};

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

    /** Blocks below the ocean surface, continuous across the ocean world / deep ocean boundary. */
    public static double blocksBelowSurface(Level level, double y)
    {
        if (level.dimension() == DeepOceanTransition.DEEP_OCEAN) y -= Config.DEEP_OCEAN_COORDINATE_OFFSET_Y.get();
        return OCEAN_SURFACE_Y - y;
    }

    public static double metres(Level level, double y)
    {
        return metres(blocksBelowSurface(level, y));
    }

    /** Metres at a deep-ocean Y, without a Level (worldgen placement runs only in the deep ocean). */
    public static double deepOceanMetres(double y)
    {
        return metres(OCEAN_SURFACE_Y - (y - Config.DEEP_OCEAN_COORDINATE_OFFSET_Y.get()));
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
