package com.abyssia.worldgen.cave;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * An analytic solid in a cave layout: approximately a signed distance field in blocks (negative inside), so wall
 * noise measured in blocks can be added to it and nearby shapes blend into one continuous cave.
 * <ul>
 *     <li>{@link Kind#CARVE}: hollowed out (water, or air above a lake's water level).</li>
 *     <li>{@link Kind#FILL}: rock added back inside or beside caves: pillars, bridges, shelves, boulders,
 *     giant stalactites, large crystals, arches.</li>
 *     <li>{@link Kind#ORE}: replaces solid rock only: ore bodies that walls cut through and expose.</li>
 *     <li>{@link Kind#CUT}: carved out of formation rock after it is placed: cracks in giant stalactites, eroded
 *     holes through rock bridges.</li>
 * </ul>
 * Because a shape is a pure function of position, every chunk it crosses evaluates it identically: no seams.
 */
public abstract class CaveShape
{
    public enum Kind { CARVE, FILL, ORE, CUT }

    public final CaveSpace space;
    public final Kind kind;
    /** FILL: block to build with, null for the space's wall rock. ORE: the ore. */
    @Nullable
    public final BlockState block;
    /** ORE: rock around the ore at the body's rim. */
    @Nullable
    public final BlockState host;
    /** FILL formations that end in a point (giant stalactites/stalagmites) get speleothem tips in this direction. */
    @Nullable
    public Direction tip;
    /** FILL rock shelves: their tops are dressed with sediment, plants, ores, crystals and small rocks. */
    public boolean shelf;
    /** Whether wall noise displaces the surface (lumpy boulders and walls) or it stays crisp (crystals). */
    public final boolean noisy;
    public int minX, minY, minZ, maxX, maxY, maxZ;

    protected CaveShape(CaveSpace space, Kind kind, @Nullable BlockState block, @Nullable BlockState host, boolean noisy)
    {
        this.space = space;
        this.kind = kind;
        this.block = block;
        this.host = host;
        this.noisy = noisy;
    }

    /** Approximate signed distance to the surface, in blocks. */
    public abstract double distance(double x, double y, double z);

    /** Extra reach of the bounding box beyond the shape: noise, plus the wall layer painted around carved space. */
    protected double margin()
    {
        return (noisy ? space.maxDisplacement() : 0) + (kind == Kind.CARVE ? CaveChunk.WALL_DEPTH + 1 : 1);
    }

    protected void bounds(double x0, double y0, double z0, double x1, double y1, double z1)
    {
        double m = margin();
        minX = Mth.floor(x0 - m);
        minY = Mth.floor(y0 - m);
        minZ = Mth.floor(z0 - m);
        maxX = Mth.ceil(x1 + m);
        maxY = Mth.ceil(y1 + m);
        maxZ = Mth.ceil(z1 + m);
    }

    public boolean intersects(int x0, int y0, int z0, int x1, int y1, int z1)
    {
        return maxX >= x0 && minX <= x1 && maxY >= y0 && minY <= y1 && maxZ >= z0 && minZ <= z1;
    }

    /** A tapered capsule (radius {@code ra} at a, {@code rb} at b); {@code vertical} squashes it top to bottom. */
    public static final class Capsule extends CaveShape
    {
        private final double ax, ay, az, bx, by, bz, ra, rb, invVertical, invLength2;

        public Capsule(CaveSpace space, Kind kind, @Nullable BlockState block, @Nullable BlockState host, boolean noisy,
                       double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, double vertical)
        {
            super(space, kind, block, host, noisy);
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.ra = ra;
            this.rb = rb;
            this.invVertical = 1.0 / vertical;
            double lx = bx - ax, ly = by - ay, lz = bz - az;
            double l2 = lx * lx + ly * ly + lz * lz;
            this.invLength2 = l2 < 1e-9 ? 0 : 1.0 / l2;
            double r = Math.max(ra, rb);
            double rv = r * Math.max(1.0, vertical);
            bounds(Math.min(ax, bx) - r, Math.min(ay, by) - rv, Math.min(az, bz) - r, Math.max(ax, bx) + r, Math.max(ay, by) + rv, Math.max(az, bz) + r);
        }

        @Override
        public double distance(double x, double y, double z)
        {
            double px = x - ax, py = y - ay, pz = z - az;
            double lx = bx - ax, ly = by - ay, lz = bz - az;
            double t = Mth.clamp((px * lx + py * ly + pz * lz) * invLength2, 0.0, 1.0);
            double dx = px - lx * t, dy = (py - ly * t) * invVertical, dz = pz - lz * t;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) - (ra + (rb - ra) * t);
        }
    }

    /**
     * An ellipsoid, optionally cut flat at {@code floorY} (only the part above it counts), which gives chambers the
     * sediment-filled level floors real caves have.
     */
    public static final class Ellipsoid extends CaveShape
    {
        public final double cx, cy, cz, rx, ry, rz, floorY;
        private final double minRadius;
        /** Rolling floor: the flat floor cut rises and dips by up to this much (set for cavern lobes). */
        private double floorRelief;
        @Nullable
        private CaveNoises reliefNoise;

        public Ellipsoid(CaveSpace space, Kind kind, @Nullable BlockState block, @Nullable BlockState host, boolean noisy,
                         double cx, double cy, double cz, double rx, double ry, double rz, double floorY)
        {
            super(space, kind, block, host, noisy);
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.floorY = floorY;
            this.minRadius = Math.min(rx, Math.min(ry, rz));
            bounds(cx - rx, Double.isNaN(floorY) ? cy - ry : Math.max(cy - ry, floorY - 1), cz - rz, cx + rx, cy + ry, cz + rz);
        }

        @Override
        public double distance(double x, double y, double z)
        {
            double dx = x - cx, dy = y - cy, dz = z - cz;
            double qx = dx / rx, qy = dy / ry, qz = dz / rz;
            double q = Math.sqrt(qx * qx + qy * qy + qz * qz);
            // Radial distance to the surface along the ray from the centre: exact for spheres and keeps wall noise
            // the same size in blocks along the long and short axes of a stretched chamber.
            double d = q < 1e-6 ? -minRadius : Math.sqrt(dx * dx + dy * dy + dz * dz) * (1 - 1 / q);
            return Double.isNaN(floorY) ? d : Math.max(d, floor(x, z) - y);
        }

        private double floor(double x, double z)
        {
            return reliefNoise == null ? floorY : floorY + floorRelief * reliefNoise.floorRelief(x, z);
        }

        /** Makes the flat floor roll gently (hillocks, dips, shallow valleys); must be set before the layout is shared. */
        public void setFloorRelief(CaveNoises noise, double amplitude)
        {
            if (!Double.isNaN(floorY))
            {
                this.reliefNoise = noise;
                this.floorRelief = amplitude;
            }
            rebound();
        }

        /** Recomputes the bounding box, e.g. after the space gained a cavern whose wall relief reaches further. */
        public void rebound()
        {
            bounds(cx - rx, Double.isNaN(floorY) ? cy - ry : Math.max(cy - ry, floorY - floorRelief - 1), cz - rz, cx + rx, cy + ry, cz + rz);
        }

        /** Roof height over (x, z), or NaN outside the footprint. */
        public double top(double x, double z)
        {
            double q = Mth.square((x - cx) / rx) + Mth.square((z - cz) / rz);
            return q >= 1 ? Double.NaN : cy + ry * Math.sqrt(1 - q);
        }

        /** Floor height under (x, z), or NaN outside the footprint. */
        public double bottom(double x, double z)
        {
            double q = Mth.square((x - cx) / rx) + Mth.square((z - cz) / rz);
            if (q >= 1) return Double.NaN;
            double b = cy - ry * Math.sqrt(1 - q);
            return Double.isNaN(floorY) ? b : Math.max(b, floor(x, z));
        }
    }
}
