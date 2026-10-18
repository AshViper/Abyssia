package com.abyssia.worldgen.cave;

import com.abyssia.worldgen.DepthBand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * AB06: the giant water-filled cavities of the crust windows. They sit on a coarse grid of the window (a super cell is
 * {@link #SUPER_BLOCKS} blocks square, shared by all windows, at most one cavity each), and everything about one (cell, centre, radius, level, height)
 * comes from the super cell's own random stream and the biome at the spot, never from {@code planWindow}: ordinary systems ask
 * "is a cavity close?" and so a cavity's plan and the plans around it can never recurse into each other.
 */
final class AbyssCavity
{
    private static final long SALT = 0x6162797373L;
    /** Super cell edge in blocks, shared by all four windows (their cell sizes differ), so a column of cavities stacks. */
    static final int SUPER_BLOCKS = 256;
    /** Chance a super cell holds a column of cavities (one roll shared by all windows), then chance each window of the column has its cavity. */
    static final double CHANCE = 0.92;
    static final double BAND_CHANCE = 0.97;
    /** Share of the super cell the centre may wander over (centred): small, so two centres are always more than a system cell (224) apart and a cell holds at most one cavity. */
    static final double JITTER = 0.08;
    /** Ordinary systems keep their hubs this far (blocks) beyond a cavity's radius. */
    static final double CLEARANCE = 60;
    /** A cavity of a lower window links to the nearest cavity of the window above within this horizontal distance. */
    static final double MAX_LINK = 430;
    /** The shaft of a link leaves / enters a cavity this far (share of its radius) from the centre, this far above its reference level. */
    static final double LINK_OFFSET = 0.5;
    static final double LINK_LIFT = 26;
    /** Cavities whose centres are closer than this (blocks) are one stacked column: their link is a wide shaft through both centres. */
    static final double STACK_DISTANCE = 8;

    private AbyssCavity() {}

    /** A planned cavity: its cell, centre (x, z), reference level y (as {@link CaveChamberGenerator#hall}), radius and vault height. */
    record Site(int cellX, int cellZ, double x, double y, double z, double radius, double height, CaveProfile profile, ResourceLocation environment) {}

    /**
     * The cavity of the super cell, or null (none rolled, no cave profile at the spot, or a network that is not a window).
     * Centre and radius come from a stream without the window's salt, so the four windows stack; only the per-window roll
     * and the height (fitted to the window) differ.
     */
    @Nullable
    static Site compute(CaveNetwork net, int scx, int scz)
    {
        if (!net.isWindow()) return null;
        long base = net.seed() ^ SALT ^ scx * 0x632BE59BD9B4E019L ^ scz * 0x9E3779B97F4A7C15L;
        RandomSource rng = new XoroshiroRandomSource(RandomSupport.mixStafford13(base));
        if (rng.nextDouble() >= CHANCE) return null;
        // Near the middle of the super cell; neighbouring halls may overlap into one big room (centres stay >= 0.92 x SUPER_BLOCKS apart).
        double x = scx * (double) SUPER_BLOCKS + SUPER_BLOCKS * (0.5 - JITTER / 2) + rng.nextDouble() * SUPER_BLOCKS * JITTER;
        double z = scz * (double) SUPER_BLOCKS + SUPER_BLOCKS * (0.5 - JITTER / 2) + rng.nextDouble() * SUPER_BLOCKS * JITTER;
        CaveType type = CaveType.ABYSS_CAVITY;
        double radius = Mth.lerp(rng.nextDouble(), type.minRadius, type.maxRadius);
        if (new XoroshiroRandomSource(RandomSupport.mixStafford13(base ^ net.salt())).nextDouble() >= BAND_CHANCE) return null;
        int cell = net.cellSize();
        int cellX = Math.floorDiv(Mth.floor(x), cell), cellZ = Math.floorDiv(Mth.floor(z), cell);
        // The radius stays, the height gives: the vault must fit the window (margin 8 above, 4 in the thin bottom window, 4 below),
        // so the hall fills the window (about 100 of 160 blocks, the rest being basin + thin floor and roof slabs).
        double y = Math.floor(net.minY() + 4 + CaveChamberGenerator.hallBelow(radius));
        double above = net.band() == DepthBand.E ? 4 : 8;
        double height = Math.min(radius * 1.2, net.maxY() - above - y - CaveChamberGenerator.hallAbove(0, radius));
        if (height < 40) return null;
        CaveProfile profile = net.profileAt(Mth.floor(x), Mth.floor(y + height * 0.5), Mth.floor(z));
        if (profile == null) return null;
        ResourceLocation env = net.luminous(profile, Mth.floor(x), Mth.floor(z)) ? net.environmentId("luminous", profile) : profile.environment;
        return new Site(cellX, cellZ, x, y, z, radius, height, profile, env);
    }

    /** The cavity whose cell is exactly this one, or null. */
    @Nullable
    static Site inCell(CaveNetwork net, int cellX, int cellZ)
    {
        int cell = net.cellSize();
        for (int scx = Math.floorDiv(cellX * cell, SUPER_BLOCKS); scx <= Math.floorDiv(cellX * cell + cell - 1, SUPER_BLOCKS); scx++)
        {
            for (int scz = Math.floorDiv(cellZ * cell, SUPER_BLOCKS); scz <= Math.floorDiv(cellZ * cell + cell - 1, SUPER_BLOCKS); scz++)
            {
                Site s = net.cavity(scx, scz);
                if (s != null && s.cellX() == cellX && s.cellZ() == cellZ) return s;
            }
        }
        return null;
    }

    /** Whether a cavity of the 3 x 3 super cells around (x, z) keeps an ordinary system's hub there out. */
    static boolean suppresses(CaveNetwork net, double x, double z)
    {
        int size = SUPER_BLOCKS;
        int scx = Math.floorDiv(Mth.floor(x), size), scz = Math.floorDiv(Mth.floor(z), size);
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                Site s = net.cavity(scx + dx, scz + dz);
                if (s != null && Math.hypot(x - s.x(), z - s.z()) < s.radius() + CLEARANCE) return true;
            }
        }
        return false;
    }

    /** Every cavity whose centre lies within {@code range} of (x, z), nearest first. */
    static List<Site> near(CaveNetwork net, double x, double z, double range)
    {
        int size = SUPER_BLOCKS;
        List<Site> list = new ArrayList<>();
        for (int scx = Math.floorDiv(Mth.floor(x - range), size); scx <= Math.floorDiv(Mth.floor(x + range), size); scx++)
        {
            for (int scz = Math.floorDiv(Mth.floor(z - range), size); scz <= Math.floorDiv(Mth.floor(z + range), size); scz++)
            {
                Site s = net.cavity(scx, scz);
                if (s != null && Math.hypot(x - s.x(), z - s.z()) <= range) list.add(s);
            }
        }
        list.sort(java.util.Comparator.comparingDouble(s -> Math.hypot(x - s.x(), z - s.z())));
        return list;
    }
}
