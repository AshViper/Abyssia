package com.abyssia.vehicle;

/**
 * SUB02 side storage pods (model groups Rcheast = +x / Lcheast = -x): ray vs oriented box, plain doubles (no Minecraft
 * types, so it can be unit-tested). Mesh coordinates = bbmodel + (0, 7.6005, 2.5) px, in blocks, front = -Z; the renderer
 * turns the mesh by (180 - yaw) around Y, so a world offset from the submarine is un-rotated by the same angle here.
 * R pod: bbmodel from [13,7.4,2] to [19,19.4,16], group rotation z +12.5 deg around [16,13.4,9]; L is the x mirror (-12.5).
 */
public final class SubmarinePods
{
    public static final int RIGHT = 0, LEFT = 1, NONE = -1;
    /**
     * pod centre in mesh coordinates (the group origin is the box centre in x / y). Y measured from the baked mesh
     * (SubmarineMesh pod vertices y 1.0229..1.8363): the generator's y shift is 9.473 px, not the 7.6005 above.
     */
    private static final double CX = 16.0 / 16.0, CY = (13.4 + 9.473) / 16.0, CZ = (9.0 + 2.5) / 16.0;
    /** half extents (3, 6, 7 px) + 1 px of slack per side */
    private static final double HX = 4.0 / 16.0, HY = 7.0 / 16.0, HZ = 8.0 / 16.0;
    private static final double TILT = Math.toRadians(12.5);

    private SubmarinePods() {}

    /** world offset from the submarine origin {@code (x, y, z)} to hull-local (mesh) coordinates */
    public static double[] toLocal(float yawDeg, double x, double y, double z)
    {
        double a = Math.toRadians(180.0 - yawDeg), c = Math.cos(a), s = Math.sin(a);
        // the renderer's Y rotation maps mesh -> world as x' = x c + z s, z' = -x s + z c; this is its inverse
        return new double[]{x * c - z * s, y, x * s + z * c};
    }

    /** Distance along the (unit) ray to the pod, hull-local coordinates, or -1 on a miss. */
    public static double hit(int pod, double ox, double oy, double oz, double dx, double dy, double dz)
    {
        double sign = pod == RIGHT ? 1.0 : -1.0;
        double ang = sign * TILT, c = Math.cos(ang), s = Math.sin(ang);
        ox -= sign * CX; oy -= CY; oz -= CZ;
        // into the pod frame: inverse of the z rotation by ang
        double px = ox * c + oy * s, py = -ox * s + oy * c;
        double vx = dx * c + dy * s, vy = -dx * s + dy * c;
        double[] o = {px, py, oz}, d = {vx, vy, dz}, h = {HX, HY, HZ};
        double tMin = 0.0, tMax = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++)
        {
            if (Math.abs(d[i]) < 1.0e-9)
            {
                if (Math.abs(o[i]) > h[i]) return -1.0;
                continue;
            }
            double t1 = (-h[i] - o[i]) / d[i], t2 = (h[i] - o[i]) / d[i];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) return -1.0;
        }
        return tMin;
    }

    /** Nearest pod hit by a world ray (offset of the eye from the submarine origin, unit direction), within {@code reach}; RIGHT / LEFT / NONE. */
    public static int pick(float yawDeg, double ex, double ey, double ez, double dx, double dy, double dz, double reach)
    {
        double[] o = toLocal(yawDeg, ex, ey, ez), d = toLocal(yawDeg, dx, dy, dz);
        double tr = hit(RIGHT, o[0], o[1], o[2], d[0], d[1], d[2]);
        double tl = hit(LEFT, o[0], o[1], o[2], d[0], d[1], d[2]);
        if (tr >= 0.0 && (tl < 0.0 || tr <= tl)) return tr <= reach ? RIGHT : NONE;
        if (tl >= 0.0) return tl <= reach ? LEFT : NONE;
        return NONE;
    }
}
