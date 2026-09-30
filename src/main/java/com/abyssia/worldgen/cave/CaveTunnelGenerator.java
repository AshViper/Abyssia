package com.abyssia.worldgen.cave;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tunnels as worm splines: control points between two ends are pushed sideways and up/down by smooth tunnel noise
 * (fading to zero at the ends so they always meet their targets), radius swells and pinches along the way (cave size
 * noise), and consecutive points become tapered capsules. Eroded tunnels add long, regular meanders.
 */
final class CaveTunnelGenerator
{
    private CaveTunnelGenerator() {}

    /**
     * Carves a tunnel from {@code from} to {@code to}; returns its centre line. {@code wander} scales the meandering,
     * {@code vertical} squashes the cross-section (below 1: wider than tall).
     */
    static List<Vec3> tunnel(CaveBuilder b, CaveSpace space, Vec3 from, Vec3 to, double r0, double r1, double wander, double vertical)
    {
        double length = from.distanceTo(to);
        int n = Math.max(2, Mth.ceil(length / 6.0));
        Vec3 dir = to.subtract(from);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = dir.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : dir.cross(side).normalize();
        if (up.y < 0) up = up.scale(-1);
        int salt = b.nextSalt();
        boolean sinuous = space.smoothness > 0.5;
        double waves = 1.5 + (salt % 3);
        List<Vec3> points = new ArrayList<>(n + 1);
        double[] radii = new double[n + 1];
        for (int i = 0; i <= n; i++)
        {
            double t = i / (double) n;
            double fade = Math.sin(Math.PI * t);
            double sideways = b.noises.path(t * length / 40.0, salt) * wander * length * 0.2 * fade;
            if (sinuous) sideways += Math.sin(t * Math.PI * waves) * wander * Math.min(length * 0.12, 10) * fade;
            double lift = b.noises.path(t * length / 30.0, salt + 0.37) * wander * length * 0.06 * fade;
            Vec3 p = from.lerp(to, t).add(side.scale(sideways)).add(up.scale(lift));
            double radius = Mth.lerp(t, r0, r1) * (0.8 + 0.4 * (b.noises.path(t * length / 18.0, salt + 0.71) * 0.5 + 0.5));
            if (p.y < b.minCarveY() + radius) p = new Vec3(p.x, b.minCarveY() + radius, p.z);
            points.add(p);
            radii[i] = radius;
        }
        for (int i = 0; i < n; i++)
        {
            Vec3 p = points.get(i), q = points.get(i + 1);
            b.add(new CaveShape.Capsule(space, CaveShape.Kind.CARVE, null, null, true, p.x, p.y, p.z, q.x, q.y, q.z, radii[i], radii[i + 1], vertical));
        }
        return points;
    }

    /** A vertical shaft wobbling slightly on its way down; the shaft space's shelf noise ribs it with ledges. */
    static void shaft(CaveBuilder b, CaveSpace space, double x, double z, double yTop, double yBottom, double r)
    {
        int salt = b.nextSalt();
        int n = Math.max(2, Mth.ceil((yTop - yBottom) / 9.0));
        Vec3 prev = null;
        double prevR = r;
        for (int i = 0; i <= n; i++)
        {
            double t = i / (double) n;
            double wx = b.noises.path(t * 3, salt) * r * 0.6, wz = b.noises.path(t * 3, salt + 0.5) * r * 0.6;
            Vec3 p = new Vec3(x + wx, Mth.lerp(t, yTop, yBottom), z + wz);
            double radius = r * (0.85 + 0.3 * (b.noises.path(t * 4, salt + 0.25) * 0.5 + 0.5));
            if (prev != null)
            {
                b.add(new CaveShape.Capsule(space, CaveShape.Kind.CARVE, null, null, true, prev.x, prev.y, prev.z, p.x, p.y, p.z, prevR, radius, 1.0));
            }
            prev = p;
            prevR = radius;
        }
    }

    /**
     * A sea tunnel: a long passage just under the seabed, following it up and down, open to the sea at both ends
     * and through skylights along the way.
     */
    static void seaTunnel(CaveBuilder b, CaveSpace space, double cx, double cz, double angle, double length, double r)
    {
        int n = Math.max(3, Mth.ceil(length / 12.0));
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double[] xs = new double[n + 1], ys = new double[n + 1], zs = new double[n + 1];
        double depth = b.range(5, 12) + r;
        for (int i = 0; i <= n; i++)
        {
            double t = i / (double) n - 0.5;
            double bend = b.noises.path(t * 2, b.nextSalt()) * length * 0.15;
            xs[i] = cx + dx * t * length - dz * bend;
            zs[i] = cz + dz * t * length + dx * bend;
            ys[i] = b.net.seabed(xs[i], zs[i]) - depth;
        }
        // Smooth the profile so it glides under bumps rather than copying every one.
        for (int pass = 0; pass < 2; pass++)
        {
            for (int i = 1; i < n; i++) ys[i] = (ys[i - 1] + ys[i] * 2 + ys[i + 1]) / 4;
        }
        for (int i = 0; i <= n; i++)
        {
            ys[i] = Math.min(ys[i], b.net.seabed(xs[i], zs[i]) - r - 2);
            ys[i] = Math.max(ys[i], b.minCarveY() + r);
        }
        for (int i = 0; i < n; i++)
        {
            tunnel(b, space, new Vec3(xs[i], ys[i], zs[i]), new Vec3(xs[i + 1], ys[i + 1], zs[i + 1]), r, r, 0.4, 0.8);
        }
        CaveSpace mouth = b.space(CaveSpace.Role.ENTRANCE, space.type, null, space.environmentId, cx, ys[0], cz, r, CaveSpace.NO_WATER_LEVEL);
        for (int end : new int[] {0, n})
        {
            int sy = b.net.seabed(xs[end], zs[end]);
            if (sy > b.net.maxEntranceY()) continue;
            b.add(new CaveShape.Capsule(mouth, CaveShape.Kind.CARVE, null, null, true, xs[end], ys[end], zs[end], xs[end], sy + 2, zs[end], r, r * 1.6, 1.0));
            b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ENTRANCE, xs[end], sy, zs[end], r * 2 + 6, mouth));
        }
        int skylights = b.range(1, 3);
        for (int k = 0; k < skylights; k++)
        {
            int i = 1 + b.rng.nextInt(Math.max(1, n - 1));
            int sy = b.net.seabed(xs[i], zs[i]);
            if (sy > b.net.maxEntranceY()) continue;
            b.add(new CaveShape.Capsule(mouth, CaveShape.Kind.CARVE, null, null, true, xs[i], ys[i], zs[i], xs[i], sy + 2, zs[i],
                    r * 0.55, r * 0.9, 1.0));
            b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ENTRANCE, xs[i], sy, zs[i], r + 5, mouth));
        }
        b.route.add("sea tunnel " + Mth.floor(length) + "m");
    }
}
