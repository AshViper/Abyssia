package com.abyssia.worldgen.structure;

import com.abyssia.worldgen.cave.CaveNetwork;
import com.abyssia.worldgen.cave.Cavern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import javax.annotation.Nullable;

/**
 * One realised structure: its definition, centre, base and height limit, and the seed its whole layout derives
 * from. Heights come from the terrain's density function (or the cavern's analytic floor and roof), never from
 * generated blocks, so every chunk computes the same layout.
 */
public final class Site
{
    public final ResourceLocation id;
    public final SeabedStructure definition;
    public final int x, z;
    /** First open block above the seabed (or cavern floor) at the centre. */
    public final int baseY;
    /** Nothing is built at or above this height. */
    public final int topY;
    public final long seed;
    @Nullable
    public final Cavern cavern;
    private final CaveNetwork network;

    Site(ResourceLocation id, SeabedStructure definition, int x, int z, int baseY, int topY, long seed, @Nullable Cavern cavern, CaveNetwork network)
    {
        this.id = id;
        this.definition = definition;
        this.x = x;
        this.z = z;
        this.baseY = baseY;
        this.topY = topY;
        this.seed = seed;
        this.cavern = cavern;
        this.network = network;
    }

    /** A fresh random at the start of the layout; every chunk draws the same sequence. */
    public RandomSource random()
    {
        return new XoroshiroRandomSource(seed);
    }

    public int radius()
    {
        return definition.footprintRadius();
    }

    /** Undisturbed floor height (first open block) at another point of the structure, e.g. under a satellite spire. */
    public int baseAt(double px, double pz)
    {
        if (cavern != null)
        {
            double f = cavern.floorAt(px, pz);
            return Double.isNaN(f) ? baseY : (int) Math.floor(f) + 1;
        }
        return network.seabed(px, pz);
    }

    /** Roof of the cavern above a point (the height limit on the open seabed). */
    public int ceilingAt(double px, double pz)
    {
        if (cavern != null)
        {
            double c = cavern.ceilingAt(px, pz);
            return Double.isNaN(c) ? topY : (int) Math.floor(c);
        }
        return topY;
    }

    /** Height budget from a base, clamped to the definition's max_height and under the top limit. */
    public int clampHeight(int base, int height)
    {
        return Math.max(0, Math.min(Math.min(height, definition.maxHeight()), topY - 1 - base));
    }
}
