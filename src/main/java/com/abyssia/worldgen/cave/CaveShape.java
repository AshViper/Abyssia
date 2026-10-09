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

    /**
     * AB03: a hall (the main chamber of a large, massive or mega cavern), water-filled like every cave. Its outline is an
     * ellipse with a few smooth lobes and bays; its walls stand vertical up to the spring line and then curve into a smooth
     * ellipsoidal vault ({@code vault} high). The floor is a height field around a reference {@code level}: a basin
     * ({@code depth} deep at the centre) out to {@code shore} of the radius, then a shore rising {@code rise} to the walls,
     * rolled by a gentle 2D noise and pushed up by islands (domes) and plateaus (flat-topped mesas); above the level it is cut
     * into terraces {@code step} blocks high, so every contour becomes a rounded ledge. Wall noise moves the vault only; the
     * floor and its terraces stay crisp. Below the level lies the basin, which the hall look paints (magma fields, ice...).
     */
    public static final class Hall extends CaveShape
    {
        public final double cx, cz, rx, rz;
        /** Reference level: basin below, terraces above (the lowest ledge's top block is at this Y). */
        public final int level;
        public final double springY, vault;
        public final double shore, depth, rise, relief;
        public final int step;
        /** Per outline harmonic k = 2, 3, ...: amplitude, cos and sin of its phase. */
        private final double[] harmonics;
        /** Per island: x, z, radius, height above the surface, flat top (1) or dome (0). */
        private final double[] islands;
        private final CaveNoises noises;
        private final double maxScale;

        public Hall(CaveSpace space, CaveNoises noises, double cx, double cz, double rx, double rz, int level, double springY, double vault,
                    double shore, double depth, double rise, double relief, int step, double[] harmonics, double[] islands)
        {
            super(space, Kind.CARVE, null, null, true);
            this.noises = noises;
            this.cx = cx;
            this.cz = cz;
            this.rx = rx;
            this.rz = rz;
            this.level = level;
            this.springY = springY;
            this.vault = vault;
            this.shore = shore;
            this.depth = depth;
            this.rise = rise;
            this.relief = relief;
            this.step = Math.max(1, step);
            this.harmonics = harmonics;
            this.islands = islands;
            double s = 1;
            for (int i = 0; i < harmonics.length; i += 3) s += Math.abs(harmonics[i]);
            this.maxScale = s;
            rebound();
        }

        /** Recomputes the bounding box (after the space gained a cavern whose wall relief reaches further). */
        public void rebound()
        {
            double reach = Math.max(rx, rz) * maxScale;
            bounds(cx - reach, level - depth - relief - 2, cz - reach, cx + reach, springY + vault, cz + reach);
        }

        public double apexY()
        {
            return springY + vault;
        }

        public int islandCount()
        {
            return islands.length / 5;
        }

        /**
         * Column values at (x, z) into {@code out}: horizontal distance from the centre, distance from the centre to the
         * outline along that bearing, normalized radius q (0 centre, 1 outline) and the floor (lowest open block).
         */
        public void column(double x, double z, double[] out)
        {
            double dx = x - cx, dz = z - cz, rho = Math.sqrt(dx * dx + dz * dz);
            double c = rho < 1e-6 ? 1 : dx / rho, s = rho < 1e-6 ? 0 : dz / rho;
            double scale = 1, ck = c, sk = s;
            for (int i = 0; i < harmonics.length; i += 3)
            {
                double nc = ck * c - sk * s, ns = ck * s + sk * c;
                ck = nc;
                sk = ns;
                scale += harmonics[i] * (ck * harmonics[i + 1] - sk * harmonics[i + 2]);
            }
            double boundary = scale / Math.sqrt(c * c / (rx * rx) + s * s / (rz * rz));
            double q = rho / boundary;
            out[0] = rho;
            out[1] = boundary;
            out[2] = q;
            out[3] = terrace(level(x, z, q));
        }

        /** The vault's signed distance at height y for a column filled by {@link #column}. */
        public double vault(double[] col, double y)
        {
            double rho = col[0], boundary = col[1], q = col[2];
            double h = y - springY;
            if (h <= 0) return rho - boundary;
            double hy = h / vault, big = Math.sqrt(q * q + hy * hy);
            if (big < 1e-9) return -Math.min(boundary, vault);
            return Math.sqrt(rho * rho + h * h) * (1 - 1 / big);
        }

        /** Height of the floor above the reference level before terracing (negative: the basin). */
        public double level(double x, double z, double q)
        {
            double f;
            if (q < shore)
            {
                double u = q / shore;
                f = -depth * (1 - u * u);
            }
            else
            {
                double u = Math.min(1, (q - shore) / (1 - shore));
                f = rise * Math.pow(u, 1.4);
            }
            f += relief * noises.floorRelief(x, z);
            for (int i = 0; i < islands.length; i += 5)
            {
                double d2 = (Mth.square(x - islands[i]) + Mth.square(z - islands[i + 1])) / Mth.square(islands[i + 2]);
                if (d2 >= 4) continue;
                double top = islands[i + 3];
                double h = islands[i + 4] > 0 ? Math.min(top, top * 1.8 * (1 - d2)) : top * (1 - d2);
                f = Math.max(f, h);
            }
            return f;
        }

        /** Terraces: steps of {@link #step} blocks above the reference level (the lowest ledge tops out at it), single blocks below. */
        public double terrace(double f)
        {
            return f >= 0 ? level + 1 + step * Math.floor(f / step) : level + 1 + Math.floor(f);
        }

        /** Lowest open block of the column (the floor's top block is one below it), outside the outline too. */
        public double floor(double x, double z)
        {
            double[] col = new double[4];
            column(x, z, col);
            return col[3];
        }

        /** Underside of the vault over (x, z) before wall noise, or NaN outside the outline. */
        public double ceiling(double x, double z)
        {
            double[] col = new double[4];
            column(x, z, col);
            return col[2] >= 1 ? Double.NaN : springY + vault * Math.sqrt(1 - col[2] * col[2]);
        }

        /** Normalized radius (0 at the centre, 1 on the outline). */
        public double q(double x, double z)
        {
            double[] col = new double[4];
            column(x, z, col);
            return col[2];
        }

        @Override
        public double distance(double x, double y, double z)
        {
            double[] col = new double[4];
            column(x, z, col);
            return Math.max(vault(col, y), col[3] - y);
        }
    }

    /**
     * AB03: a column of rock or crystal standing on a vertical axis between {@code y0} and {@code y1}: trunks, fused pillars,
     * spires and giant stalactites. The axis wanders and the radius swells and pinches through knots spaced evenly along the
     * height; optional ribs (vertical grooves, slowly twisting) streak its sides. The ends are cut flat: bury them in rock, or
     * let a knot radius run down to a point.
     */
    public static final class Column extends CaveShape
    {
        private final double y0, y1, step;
        private final double[] ax, az, radius;
        private final double ribs, ribAmp, ribPhase, twist;

        public Column(CaveSpace space, @Nullable BlockState block, double y0, double y1, double[] ax, double[] az, double[] radius,
                      double ribs, double ribAmp, double ribPhase, double twist)
        {
            super(space, Kind.FILL, block, null, false);
            this.y0 = y0;
            this.y1 = y1;
            this.ax = ax;
            this.az = az;
            this.radius = radius;
            this.step = (y1 - y0) / (radius.length - 1);
            this.ribs = ribs;
            this.ribAmp = ribAmp;
            this.ribPhase = ribPhase;
            this.twist = twist;
            double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (int i = 0; i < radius.length; i++)
            {
                double r = radius[i] * (1 + ribAmp);
                x0 = Math.min(x0, ax[i] - r);
                x1 = Math.max(x1, ax[i] + r);
                z0 = Math.min(z0, az[i] - r);
                z1 = Math.max(z1, az[i] + r);
            }
            bounds(x0, y0, z0, x1, y1, z1);
        }

        @Override
        public double distance(double x, double y, double z)
        {
            if (y < y0) return y0 - y;
            if (y > y1) return y - y1;
            double t = (y - y0) / step;
            int i = Math.min((int) t, radius.length - 2);
            double f = t - i;
            double px = ax[i] + (ax[i + 1] - ax[i]) * f, pz = az[i] + (az[i + 1] - az[i]) * f;
            double r = radius[i] + (radius[i + 1] - radius[i]) * f;
            double dx = x - px, dz = z - pz, rho = Math.sqrt(dx * dx + dz * dz);
            if (ribAmp > 0 && rho > 1e-6) r *= 1 + ribAmp * Math.cos(ribs * Math.atan2(dz, dx) + ribPhase + twist * (y - y0));
            return rho - r;
        }
    }
}
