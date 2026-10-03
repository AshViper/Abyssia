package com.abyssia.environment;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * CU01 current stream: a wide, strong band of water following a gently curving centreline (uniform Catmull-Rom spline
 * through 4-6 control points). The flow direction at any point is the centreline's tangent there. Immutable; generated
 * by {@link CurrentStreams}. The spline is pre-sampled into a polyline ({@link #path}) for queries and particles.
 *
 * @param id       deterministic id (the cell's RNG seed)
 * @param control  control points, upstream first
 * @param length   nominal length in blocks (the straight span the control points were laid along)
 * @param radius   half the band's diameter (4-10)
 * @param strength 0.65-1.2 (WEAK 0.65-0.80, NORMAL 0.80-1.00, STRONG 1.00-1.20)
 * @param center   the anchor drawn inside the cell (midpoint of the band)
 * @param path     the spline sampled at {@link #SAMPLES_PER_SEGMENT} points per control segment, upstream first
 */
public record CurrentStream(long id, List<Vec3> control, double length, double radius, double strength, Vec3 center, List<Vec3> path)
{
    public static final int SAMPLES_PER_SEGMENT = 12;

    public enum Tier { WEAK, NORMAL, STRONG }

    public Tier tier()
    {
        return strength < 0.80 ? Tier.WEAK : strength < 1.00 ? Tier.NORMAL : Tier.STRONG;
    }

    /** Builds the stream and its sampled centreline from the control points. */
    public static CurrentStream of(long id, List<Vec3> control, double length, double radius, double strength, Vec3 center)
    {
        return new CurrentStream(id, List.copyOf(control), length, radius, strength, center, List.copyOf(sampleSpline(control)));
    }

    /** Uniform Catmull-Rom through the points (end points repeated as phantom neighbours). */
    static java.util.ArrayList<Vec3> sampleSpline(List<Vec3> pts)
    {
        java.util.ArrayList<Vec3> out = new java.util.ArrayList<>();
        int n = pts.size();
        for (int i = 0; i < n - 1; i++)
        {
            Vec3 p0 = pts.get(Math.max(0, i - 1)), p1 = pts.get(i), p2 = pts.get(i + 1), p3 = pts.get(Math.min(n - 1, i + 2));
            for (int k = 0; k < SAMPLES_PER_SEGMENT; k++)
            {
                out.add(catmullRom(p0, p1, p2, p3, k / (double) SAMPLES_PER_SEGMENT));
            }
        }
        out.add(pts.get(n - 1));
        return out;
    }

    static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t)
    {
        double t2 = t * t, t3 = t2 * t;
        double a = -0.5 * t3 + t2 - 0.5 * t;
        double b = 1.5 * t3 - 2.5 * t2 + 1.0;
        double c = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
        double d = 0.5 * t3 - 0.5 * t2;
        return new Vec3(a * p0.x + b * p1.x + c * p2.x + d * p3.x,
                a * p0.y + b * p1.y + c * p2.y + d * p3.y,
                a * p0.z + b * p1.z + c * p2.z + d * p3.z);
    }

    /** Where on the centreline a point is closest: polyline segment index, fraction along it, and the distance. */
    public record Nearest(int segment, double t, double distance, Vec3 point, Vec3 tangent) {}

    /** The closest point of the centreline to (x, y, z). */
    public Nearest nearest(double x, double y, double z)
    {
        int best = 0;
        double bestT = 0.0, bestSq = Double.MAX_VALUE;
        for (int i = 0; i < path.size() - 1; i++)
        {
            Vec3 a = path.get(i), b = path.get(i + 1);
            double ax = b.x - a.x, ay = b.y - a.y, az = b.z - a.z;
            double lenSq = ax * ax + ay * ay + az * az;
            double t = lenSq < 1.0E-9 ? 0.0 : ((x - a.x) * ax + (y - a.y) * ay + (z - a.z) * az) / lenSq;
            t = Math.max(0.0, Math.min(1.0, t));
            double dx = x - (a.x + ax * t), dy = y - (a.y + ay * t), dz = z - (a.z + az * t);
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq)
            {
                bestSq = sq;
                best = i;
                bestT = t;
            }
        }
        Vec3 a = path.get(best), b = path.get(best + 1);
        Vec3 point = a.add(b.subtract(a).scale(bestT));
        return new Nearest(best, bestT, Math.sqrt(bestSq), point, tangent(best));
    }

    /** Unit tangent of polyline segment {@code i}. */
    public Vec3 tangent(int i)
    {
        Vec3 d = path.get(i + 1).subtract(path.get(i));
        return d.lengthSqr() < 1.0E-12 ? new Vec3(1, 0, 0) : d.normalize();
    }

    /** Arc length of the sampled centreline. */
    public double pathLength()
    {
        double sum = 0.0;
        for (int i = 0; i < path.size() - 1; i++) sum += path.get(i).distanceTo(path.get(i + 1));
        return sum;
    }

    /** Rough bound used to skip far streams: distance from the anchor to the furthest control point plus the radius. */
    public double reach()
    {
        double r = 0.0;
        for (Vec3 p : control) r = Math.max(r, p.distanceTo(center));
        return r + radius;
    }
}
