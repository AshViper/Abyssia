package com.abyssia.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;

/**
 * The spine of a long boss: joint 0 is the head, every further joint is pulled to a fixed distance behind the previous one
 * (follow-the-leader). The same code runs on the server and on the client; the client is nudged to the server's shape by
 * {@link #receive(short[])} snapshots (head-relative offsets, {@link #UNIT} blocks per unit).
 */
public final class BossChain
{
    /** Snapshot units per block. Range of a short is +-2047 blocks, so even a 360 joint test chain fits. */
    public static final double UNIT = 16.0;
    private static final double CORRECTION = 0.3;
    private static final int CORRECTION_TICKS = 3;

    private final int n;
    private final double spacing;
    private final double[] x, y, z, ox, oy, oz;
    private final double[] rel;
    private boolean init;
    private boolean relFresh;
    private boolean snapshotApplied;
    private int correct;
    private boolean smooth;

    public BossChain(int joints, double spacing)
    {
        this.n = joints;
        this.spacing = spacing;
        this.x = new double[joints];
        this.y = new double[joints];
        this.z = new double[joints];
        this.ox = new double[joints];
        this.oy = new double[joints];
        this.oz = new double[joints];
        this.rel = new double[joints * 3];
    }

    /** Keep blending toward the latest snapshot every tick (tentacles: the client does not know the server's reach target). */
    public BossChain smooth()
    {
        smooth = true;
        return this;
    }

    public int size() { return n; }
    public double spacing() { return spacing; }
    public boolean isInitialised() { return init; }
    public double x(int i) { return x[i]; }
    public double y(int i) { return y[i]; }
    public double z(int i) { return z[i]; }
    public double lerpX(int i, float pt) { return ox[i] + (x[i] - ox[i]) * pt; }
    public double lerpY(int i, float pt) { return oy[i] + (y[i] - oy[i]) * pt; }
    public double lerpZ(int i, float pt) { return oz[i] + (z[i] - oz[i]) * pt; }

    /** Moves the head to (hx, hy, hz) and drags the rest behind it. (bx, by, bz) is the "behind" direction used when a straight line is needed. */
    public void tick(double hx, double hy, double hz, double bx, double by, double bz)
    {
        System.arraycopy(x, 0, ox, 0, n);
        System.arraycopy(y, 0, oy, 0, n);
        System.arraycopy(z, 0, oz, 0, n);
        double dx = hx - x[0], dy = hy - y[0], dz = hz - z[0];
        if (!init || dx * dx + dy * dy + dz * dz > 4 * spacing * spacing)
        {
            straight(hx, hy, hz, bx, by, bz);
        }
        else
        {
            x[0] = hx;
            y[0] = hy;
            z[0] = hz;
            for (int i = 1; i < n; i++)
            {
                double ex = x[i] - x[i - 1], ey = y[i] - y[i - 1], ez = z[i] - z[i - 1];
                double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
                if (len < 1.0E-6)
                {
                    ex = bx;
                    ey = by;
                    ez = bz;
                    len = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    if (len < 1.0E-6)
                    {
                        ex = 0;
                        ey = 0;
                        ez = -1;
                        len = 1;
                    }
                }
                double s = spacing / len;
                x[i] = x[i - 1] + ex * s;
                y[i] = y[i - 1] + ey * s;
                z[i] = z[i - 1] + ez * s;
            }
        }
        if (relFresh)
        {
            relFresh = false;
            if (!snapshotApplied)
            {
                snapshotApplied = true;
                for (int i = 1; i < n; i++)
                {
                    x[i] = x[0] + rel[3 * i];
                    y[i] = y[0] + rel[3 * i + 1];
                    z[i] = z[0] + rel[3 * i + 2];
                }
                System.arraycopy(x, 0, ox, 0, n);
                System.arraycopy(y, 0, oy, 0, n);
                System.arraycopy(z, 0, oz, 0, n);
            }
            else
            {
                correct = CORRECTION_TICKS;
            }
        }
        if (correct > 0 || (smooth && snapshotApplied))
        {
            if (correct > 0) correct--;
            double k = smooth ? 0.3 : CORRECTION;
            for (int i = 1; i < n; i++)
            {
                x[i] += (x[0] + rel[3 * i] - x[i]) * k;
                y[i] += (y[0] + rel[3 * i + 1] - y[i]) * k;
                z[i] += (z[0] + rel[3 * i + 2] - z[i]) * k;
            }
        }
    }

    /** Server: moves joint i by (dx, dy, dz); a flow that grows with i bends the chain, the next {@link #tick} restores the spacing. */
    public void shift(int i, double dx, double dy, double dz)
    {
        x[i] += dx;
        y[i] += dy;
        z[i] += dz;
    }

    /** Server: raises joint i to at least minY. */
    public void lift(int i, double minY)
    {
        if (y[i] < minY) y[i] = minY;
    }

    /** Server, after {@link #tick}: FABRIK from the free end toward (tx, ty, tz) with joint 0 pinned, {@code iterations} passes. */
    public void reach(double tx, double ty, double tz, int iterations)
    {
        if (!init) return;
        double rx = x[0], ry = y[0], rz = z[0];
        for (int it = 0; it < iterations; it++)
        {
            x[n - 1] = tx;
            y[n - 1] = ty;
            z[n - 1] = tz;
            for (int i = n - 2; i >= 0; i--) pull(i, i + 1);
            x[0] = rx;
            y[0] = ry;
            z[0] = rz;
            for (int i = 1; i < n; i++) pull(i, i - 1);
        }
    }

    /** Puts joint i at the fixed spacing from joint j along their current line. */
    private void pull(int i, int j)
    {
        double dx = x[i] - x[j], dy = y[i] - y[j], dz = z[i] - z[j];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6)
        {
            dx = 0;
            dy = -1;
            dz = 0;
            len = 1;
        }
        double s = spacing / len;
        x[i] = x[j] + dx * s;
        y[i] = y[j] + dy * s;
        z[i] = z[j] + dz * s;
    }

    private void straight(double hx, double hy, double hz, double bx, double by, double bz)
    {
        double len = Math.sqrt(bx * bx + by * by + bz * bz);
        if (len < 1.0E-6)
        {
            bx = 0;
            by = 0;
            bz = -1;
            len = 1;
        }
        double s = spacing / len;
        for (int i = 0; i < n; i++)
        {
            x[i] = hx + bx * s * i;
            y[i] = hy + by * s * i;
            z[i] = hz + bz * s * i;
        }
        System.arraycopy(x, 0, ox, 0, n);
        System.arraycopy(y, 0, oy, 0, n);
        System.arraycopy(z, 0, oz, 0, n);
        init = true;
    }

    /** Rotates every joint behind {@code pivot} about the vertical axis through the pivot joint (tail swipe). */
    public void rotateTail(int pivot, double angle)
    {
        if (!init || pivot < 0 || pivot >= n - 1) return;
        double c = Math.cos(angle), s = Math.sin(angle);
        for (int i = pivot + 1; i < n; i++)
        {
            double dx = x[i] - x[pivot], dz = z[i] - z[pivot];
            x[i] = x[pivot] + dx * c - dz * s;
            z[i] = z[pivot] + dx * s + dz * c;
        }
    }

    /** Head-relative positions of joints 1..n-1 in {@link #UNIT} units (x, y, z per joint). */
    public short[] snapshot()
    {
        short[] q = new short[3 * (n - 1)];
        for (int i = 1; i < n; i++)
        {
            q[3 * (i - 1)] = quant(x[i] - x[0]);
            q[3 * (i - 1) + 1] = quant(y[i] - y[0]);
            q[3 * (i - 1) + 2] = quant(z[i] - z[0]);
        }
        return q;
    }

    private static short quant(double d)
    {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(d * UNIT)));
    }

    /** Client: take a server snapshot. The first one is assigned at once, later ones are blended in over a few ticks. */
    public void receive(short[] q)
    {
        if (q.length != 3 * (n - 1)) return;
        for (int i = 1; i < n; i++)
        {
            rel[3 * i] = q[3 * (i - 1)] / UNIT;
            rel[3 * i + 1] = q[3 * (i - 1) + 1] / UNIT;
            rel[3 * i + 2] = q[3 * (i - 1) + 2] / UNIT;
        }
        relFresh = true;
    }

    /**
     * Client LOD only: fills every joint (current and previous alike) from sparse samples with a Catmull-Rom curve.
     * Sample k is joint {@code idx[k]} (ascending, first 0, last n-1), its x y z at {@code p[off + 3k]}.
     */
    public void fillSampled(int[] idx, double[] p, int off)
    {
        int m = idx.length;
        if (m < 2 || idx[0] != 0 || idx[m - 1] != n - 1) return;
        int s = 0;
        for (int i = 0; i < n; i++)
        {
            while (s < m - 2 && i > idx[s + 1]) s++;
            double t = (i - idx[s]) / (double) (idx[s + 1] - idx[s]);
            double t2 = t * t, t3 = t2 * t;
            int a = Math.max(s - 1, 0), d = Math.min(s + 2, m - 1);
            double[] out = new double[3];
            for (int c = 0; c < 3; c++)
            {
                double p0 = p[off + 3 * a + c], p1 = p[off + 3 * s + c], p2 = p[off + 3 * (s + 1) + c], p3 = p[off + 3 * d + c];
                out[c] = 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (3 * p1 - p0 - 3 * p2 + p3) * t3);
            }
            x[i] = ox[i] = out[0];
            y[i] = oy[i] = out[1];
            z[i] = oz[i] = out[2];
        }
        init = true;
    }

    /** Squared distance from a point to the nearest segment of the chain. */
    public double distanceSqr(double px, double py, double pz)
    {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n - 1; i++)
        {
            double ax = x[i], ay = y[i], az = z[i];
            double bx = x[i + 1] - ax, by = y[i + 1] - ay, bz = z[i + 1] - az;
            double l2 = bx * bx + by * by + bz * bz;
            double t = l2 < 1.0E-9 ? 0 : ((px - ax) * bx + (py - ay) * by + (pz - az) * bz) / l2;
            t = Math.max(0, Math.min(1, t));
            double dx = ax + bx * t - px, dy = ay + by * t - py, dz = az + bz * t - pz;
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }

    public AABB bounds(double inflate)
    {
        double x0 = x[0], y0 = y[0], z0 = z[0], x1 = x0, y1 = y0, z1 = z0;
        for (int i = 1; i < n; i++)
        {
            x0 = Math.min(x0, x[i]);
            y0 = Math.min(y0, y[i]);
            z0 = Math.min(z0, z[i]);
            x1 = Math.max(x1, x[i]);
            y1 = Math.max(y1, y[i]);
            z1 = Math.max(z1, z[i]);
        }
        return new AABB(x0, y0, z0, x1, y1, z1).inflate(inflate);
    }

    public void save(CompoundTag tag)
    {
        if (!init) return;
        ListTag list = new ListTag();
        for (int i = 0; i < n; i++)
        {
            list.add(DoubleTag.valueOf(x[i]));
            list.add(DoubleTag.valueOf(y[i]));
            list.add(DoubleTag.valueOf(z[i]));
        }
        tag.put("Chain", list);
    }

    public void load(CompoundTag tag)
    {
        if (!tag.contains("Chain", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("Chain", Tag.TAG_DOUBLE);
        if (list.size() != n * 3) return;
        for (int i = 0; i < n; i++)
        {
            x[i] = list.getDouble(3 * i);
            y[i] = list.getDouble(3 * i + 1);
            z[i] = list.getDouble(3 * i + 2);
        }
        System.arraycopy(x, 0, ox, 0, n);
        System.arraycopy(y, 0, oy, 0, n);
        System.arraycopy(z, 0, oz, 0, n);
        init = true;
    }
}
