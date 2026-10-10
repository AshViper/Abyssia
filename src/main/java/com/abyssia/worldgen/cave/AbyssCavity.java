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
 * {@link #SUPER} x {@link #SUPER} cells, at most one cavity each), and everything about one (cell, centre, radius, level, height)
 * comes from the super cell's own random stream and the biome at the spot, never from {@code planWindow}: ordinary systems ask
 * "is a cavity close?" and so a cavity's plan and the plans around it can never recurse into each other.
 */
final class AbyssCavity
{
    private static final long SALT = 0x6162797373L;
    /** Super cell edge in window cells, chance a super cell holds a cavity. */
    static final int SUPER = 4;
    static final double CHANCE = 0.8;
    /** Ordinary systems keep their hubs this far (blocks) beyond a cavity's radius. */
    static final double CLEARANCE = 60;
    /** A cavity of a lower window links to the nearest cavity of the window above within this horizontal distance. */
    static final double MAX_LINK = 430;
    /** The shaft of a link leaves / enters a cavity this far (share of its radius) from the centre, this far above its reference level. */
    static final double LINK_OFFSET = 0.5;
    static final double LINK_LIFT = 26;

    private AbyssCavity() {}

    /** A planned cavity: its cell, centre (x, z), reference level y (as {@link CaveChamberGenerator#hall}), radius and vault height. */
    record Site(int cellX, int cellZ, double x, double y, double z, double radius, double height, CaveProfile profile, ResourceLocation environment) {}

    /** The cavity of the super cell, or null (none rolled, no cave profile at the spot, or a network that is not a window). */
    @Nullable
    static Site compute(CaveNetwork net, int scx, int scz)
    {
        if (!net.isWindow()) return null;
        RandomSource rng = new XoroshiroRandomSource(RandomSupport.mixStafford13(net.seed() ^ net.salt() ^ SALT ^ scx * 0x632BE59BD9B4E019L ^ scz * 0x9E3779B97F4A7C15L));
        if (rng.nextDouble() >= CHANCE) return null;
        int cell = net.cellSize(), margin = cell / 6;
        // The two middle cells only, so cavities of neighbouring super cells are always more than their radii apart.
        int cellX = scx * SUPER + 1 + rng.nextInt(SUPER - 2), cellZ = scz * SUPER + 1 + rng.nextInt(SUPER - 2);
        int ax = cellX * cell + margin + rng.nextInt(cell - 2 * margin);
        int az = cellZ * cell + margin + rng.nextInt(cell - 2 * margin);
        double x = ax + (rng.nextDouble() - 0.5) * cell * 0.15, z = az + (rng.nextDouble() - 0.5) * cell * 0.15;
        CaveType type = CaveType.ABYSS_CAVITY;
        double radius = Mth.lerp(rng.nextDouble(), type.minRadius, type.maxRadius);
        // The radius stays, the height gives: the vault must fit the window (margin 12 above, 6 in the thin bottom window, 6 below).
        double y = Math.floor(net.minY() + 6 + CaveChamberGenerator.hallBelow(radius));
        double above = net.band() == DepthBand.E ? 6 : 12;
        double height = Math.min(CaveChamberGenerator.hallHeight(radius), net.maxY() - above - y - CaveChamberGenerator.hallAbove(0, radius));
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
        Site s = net.cavity(Math.floorDiv(cellX, SUPER), Math.floorDiv(cellZ, SUPER));
        return s != null && s.cellX() == cellX && s.cellZ() == cellZ ? s : null;
    }

    /** Whether a cavity of the 3 x 3 super cells around (x, z) keeps an ordinary system's hub there out. */
    static boolean suppresses(CaveNetwork net, double x, double z)
    {
        int size = net.cellSize() * SUPER;
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
        int size = net.cellSize() * SUPER;
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
