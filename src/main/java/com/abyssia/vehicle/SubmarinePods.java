package com.abyssia.vehicle;

/**
 * SUB02 side storage pods (bbmodel groups Rcheast / Lcheast): ray vs oriented box, no Minecraft types so it can be
 * checked standalone. Mesh frame = bbmodel + (0, +7.6005, +2.5) px, relative to the entity position; +x = the
 * submarine's right side, -z = front. The renderer rotates the mesh by 180 - yaw, so a world offset (wx, wz) maps to
 * model x = -cos(yaw) wx - sin(yaw) wz, model z = sin(yaw) wx - cos(yaw) wz.
 */
public final class SubmarinePods
{
    public static final int RIGHT = 0, LEFT = 1;

    /** bbmodel box R: from [13,7.4,2] to [19,19.4,16] (+ mesh shift), group origin [16,13.4,9], rotation z +12.5 deg */
    // y shift measured on the baked mesh (pod vertices y 1.0229..1.8363 blocks): 9.473 px, not the spec's 7.6005
    private static final double SHIFT_Y = 9.473, SHIFT_Z = 2.5, GROW = 1.0, ANGLE = 12.5;
    private static final double[] MIN = {13 - GROW, 7.4 + SHIFT_Y - GROW, 2 + SHIFT_Z - GROW};
    private static final double[] MAX = {19 + GROW, 19.4 + SHIFT_Y + GROW, 16 + SHIFT_Z + GROW};
    private static final double[] PIVOT = {16, 13.4 + SHIFT_Y, 9 + SHIFT_Z};

    private SubmarinePods() {}

    /**
     * @param yawDeg entity yaw; origin = ray start relative to the entity position (blocks); dir = ray direction
     * @return distance (blocks, along the unit-length dir) to the nearest pod hit, or -1; {@code which[0]} gets RIGHT / LEFT
     */
    public static double raycast(double yawDeg, double ox, double oy, double oz, double dx, double dy, double dz,
                                 double maxDist, int[] which)
    {
        double yaw = Math.toRadians(yawDeg), c = Math.cos(yaw), s = Math.sin(yaw);
        // world -> model (blocks -> px for the position)
        double mox = (-c * ox - s * oz) * 16, moy = oy * 16, moz = (s * ox - c * oz) * 16;
        double mdx = -c * dx - s * dz, mdy = dy, mdz = s * dx - c * dz;
        double best = -1;
        for (int side = RIGHT; side <= LEFT; side++)
        {
            double sign = side == RIGHT ? 1 : -1;
            double t = hitPod(sign, mox, moy, moz, mdx, mdy, mdz);
            if (t >= 0 && t <= maxDist && (best < 0 || t < best))
            {
                best = t;
                which[0] = side;
            }
        }
        return best;
    }

    /** model frame (origin px, unit direction); the left pod is the right one mirrored in x. Returns blocks or -1. */
    private static double hitPod(double sign, double ox, double oy, double oz, double dx, double dy, double dz)
    {
        ox *= sign;
        dx *= sign;
        double a = Math.toRadians(-ANGLE), ca = Math.cos(a), sa = Math.sin(a);   // undo the group's z rotation about the pivot
        double rx = ox - PIVOT[0], ry = oy - PIVOT[1];
        double[] o = {rx * ca - ry * sa + PIVOT[0], rx * sa + ry * ca + PIVOT[1], oz};
        double[] d = {dx * ca - dy * sa, dx * sa + dy * ca, dz};
        double t0 = 0, t1 = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++)
        {
            if (Math.abs(d[i]) < 1.0e-9)
            {
                if (o[i] < MIN[i] || o[i] > MAX[i]) return -1;
                continue;
            }
            double ta = (MIN[i] - o[i]) / d[i], tb = (MAX[i] - o[i]) / d[i];
            if (ta > tb) { double tmp = ta; ta = tb; tb = tmp; }
            t0 = Math.max(t0, ta);
            t1 = Math.min(t1, tb);
            if (t0 > t1) return -1;
        }
        return t0 / 16.0;
    }
}
