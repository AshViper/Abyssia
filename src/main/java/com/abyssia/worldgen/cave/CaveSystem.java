package com.abyssia.worldgen.cave;

import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The full layout of one cave system (or one minor cave): every shape, plus the points decoration needs, derived
 * purely from the world seed and a grid cell. Any chunk asking for the same cell gets the identical layout (and
 * cached), which is what lets each chunk carve and decorate only its own part of a cave spanning many chunks.
 */
public final class CaveSystem
{
    public enum SiteKind { ENTRANCE, ARCH, COLLAPSE }

    /** An area decorated as a whole: a landmark entrance, a sea arch, or the debris field under a collapse. */
    public record Site(SiteKind kind, double x, double y, double z, double radius, CaveSpace space) {}

    /** A hydrothermal vent standing on a cave floor near {@code yHint}; its column finds the exact floor. */
    public record Vent(int x, int z, int yHint, int height, ThermalVentType type, VentActivity activity, CaveSpace space) {}

    public final int cellX, cellZ;
    public final boolean minor;
    public final CaveType type;
    @Nullable
    public final CaveLandmark landmark;
    public final List<CaveShape> shapes;
    public final List<CaveSpace> spaces;
    public final List<Site> sites;
    public final List<Vent> vents;
    /** Human-readable route summary for the debug command. */
    public final String summary;
    public final int x, y, z;
    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public CaveSystem(int cellX, int cellZ, boolean minor, CaveType type, @Nullable CaveLandmark landmark, List<CaveShape> shapes,
                      List<CaveSpace> spaces, List<Site> sites, List<Vent> vents, String summary, int x, int y, int z)
    {
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.minor = minor;
        this.type = type;
        this.landmark = landmark;
        this.shapes = shapes;
        this.spaces = spaces;
        this.sites = sites;
        this.vents = vents;
        this.summary = summary;
        this.x = x;
        this.y = y;
        this.z = z;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (CaveShape s : shapes)
        {
            x0 = Math.min(x0, s.minX);
            y0 = Math.min(y0, s.minY);
            z0 = Math.min(z0, s.minZ);
            x1 = Math.max(x1, s.maxX);
            y1 = Math.max(y1, s.maxY);
            z1 = Math.max(z1, s.maxZ);
        }
        for (Site s : sites)
        {
            x0 = Math.min(x0, (int) Math.floor(s.x() - s.radius()));
            z0 = Math.min(z0, (int) Math.floor(s.z() - s.radius()));
            x1 = Math.max(x1, (int) Math.ceil(s.x() + s.radius()));
            z1 = Math.max(z1, (int) Math.ceil(s.z() + s.radius()));
            y0 = Math.min(y0, (int) Math.floor(s.y() - 24));
            y1 = Math.max(y1, (int) Math.ceil(s.y() + 24));
        }
        this.minX = x0;
        this.minY = y0;
        this.minZ = z0;
        this.maxX = x1;
        this.maxY = y1;
        this.maxZ = z1;
    }

    public boolean isEmpty()
    {
        return shapes.isEmpty();
    }

    public boolean intersects(int x0, int z0, int x1, int z1)
    {
        return !shapes.isEmpty() && maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }

    public CaveType.Rarity rarity()
    {
        return landmark != null ? CaveType.Rarity.VERY_RARE : type.size.rarity;
    }
}
