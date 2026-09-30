package com.abyssia.worldgen.cave;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Cave mouths as landmarks and sea arches. A mouth is a flared funnel through the seabed into a narrowing throat,
 * framed by overhanging rock shelves and big fallen rocks; its site is later dressed with kelp, vines hanging under
 * the shelves, crystals, mineral crust and sediment, so it can be spotted and recognised from a distance.
 */
final class CaveEntranceGenerator
{
    private CaveEntranceGenerator() {}

    /**
     * An entrance at (ex, ez) on the seabed leading down to {@code target}. Returns the throat (where the tunnel
     * starts), or null when the seabed there is too high to open an entrance.
     */
    @Nullable
    static Vec3 entrance(CaveBuilder b, CaveSpace tunnelSpace, double ex, double ez, Vec3 target, double r, boolean landmark)
    {
        int sy = b.net.seabed(ex, ez);
        if (sy > b.net.maxEntranceY() || sy < b.minCarveY() + 12) return null;
        CaveSpace mouth = b.space(CaveSpace.Role.ENTRANCE, tunnelSpace.type, tunnelSpace.landmark, tunnelSpace.environmentId, ex, sy, ez, r * 2,
                CaveSpace.NO_WATER_LEVEL);
        double hx = target.x - ex, hz = target.z - ez;
        double h = Math.sqrt(hx * hx + hz * hz);
        double dx = h < 1e-3 ? 1 : hx / h, dz = h < 1e-3 ? 0 : hz / h;
        Vec3 lip = new Vec3(ex, sy + 2.5, ez);
        Vec3 throat = new Vec3(ex + dx * r * 1.2, Math.max(b.minCarveY() + r, sy - r * 1.6 - 3), ez + dz * r * 1.2);
        b.add(new CaveShape.Capsule(mouth, CaveShape.Kind.CARVE, null, null, true, lip.x, lip.y, lip.z, throat.x, throat.y, throat.z, r * 2.0, r * 1.05, 1.0));
        CaveTunnelGenerator.tunnel(b, tunnelSpace, throat, target, r, r * 0.9, 1.0, tunnelSpace.type == CaveType.TRENCH_CAVE ? 1.2 : 0.9);

        CaveSpace rock = b.formationSpace(mouth);
        double anchor = rock.maxDisplacement() + 2;
        // Overhanging shelves around the rim: part rests on the seabed, part juts out over the mouth.
        int shelves = landmark ? b.range(2, 3) : b.range(1, 2);
        for (int i = 0; i < shelves; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2;
            double sx = ex + Math.cos(a) * r * 1.9, sz = ez + Math.sin(a) * r * 1.9;
            double tx = -Math.sin(a) * b.range(2.5, 5), tz = Math.cos(a) * b.range(2.5, 5);
            double y = Math.max(sy, b.net.seabed(sx, sz)) + b.range(0.5, 2.0);
            b.add(new CaveShape.Capsule(rock, CaveShape.Kind.FILL, null, null, true, sx - tx, y, sz - tz, sx + tx, y + b.range(-0.5, 1), sz + tz,
                    b.range(2.5, 4.0), b.range(2.5, 4.0), 0.4));
        }
        // Huge rocks strewn around the mouth.
        int boulders = landmark ? b.range(3, 6) : b.range(1, 3);
        for (int i = 0; i < boulders; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = r * 2.4 + 2 + b.rng.nextDouble() * 8;
            double bx = ex + Math.cos(a) * d, bz = ez + Math.sin(a) * d;
            double by = b.net.seabed(bx, bz);
            double br = b.range(1.5, landmark ? 5.0 : 3.5);
            b.add(new CaveShape.Ellipsoid(rock, CaveShape.Kind.FILL, null, null, true, bx, by + br * 0.3, bz, br, br * b.range(0.7, 1.1), br * b.range(0.8, 1.2), Double.NaN));
            b.add(new CaveShape.Capsule(rock, CaveShape.Kind.FILL, null, null, false, bx, by - anchor, bz, bx, by, bz, br * 0.6, br * 0.8, 1.0));
        }
        b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ENTRANCE, ex, sy, ez, r * 4 + (landmark ? 12 : 8), mouth));
        return throat;
    }

    /**
     * A sea arch: a half-ring of rock standing on the seabed, thickest at its feet, carrying a thin ore vein along
     * its span. Its site is dressed with plants on top, vines beneath and crystals.
     */
    static boolean arch(CaveBuilder b, CaveSpace space, double cx, double cz, double angle, double span, double height, double thick)
    {
        int sy = b.net.seabed(cx, cz);
        if (sy + height > b.net.maxEntranceY() || sy < b.minCarveY() + 8) return false;
        double dx = Math.cos(angle), dz = Math.sin(angle);
        int salt = b.nextSalt();
        int n = 12;
        Vec3[] points = new Vec3[n + 1];
        double[] radii = new double[n + 1];
        for (int i = 0; i <= n; i++)
        {
            double t = Math.PI * i / n;
            double u = Math.cos(t) * span / 2, v = Math.sin(t) * height;
            double w = b.noises.path(t, salt) * span * 0.08;
            points[i] = new Vec3(cx + dx * u - dz * w, sy - 1 + v, cz + dz * u + dx * w);
            radii[i] = thick * (1.3 - 0.45 * Math.sin(t)) * (0.9 + 0.2 * (b.noises.path(t * 2, salt + 0.5) * 0.5 + 0.5));
        }
        for (int i = 0; i < n; i++)
        {
            b.add(new CaveShape.Capsule(space, CaveShape.Kind.FILL, null, null, true, points[i].x, points[i].y, points[i].z,
                    points[i + 1].x, points[i + 1].y, points[i + 1].z, radii[i], radii[i + 1], 1.0));
        }
        // Feet sunk into the seabed wherever it drops away.
        for (int end : new int[] {0, n})
        {
            Vec3 p = points[end];
            b.add(new CaveShape.Capsule(space, CaveShape.Kind.FILL, null, null, true, p.x, p.y, p.z, p.x, b.net.seabed(p.x, p.z) - 5, p.z,
                    radii[end] * 1.2, radii[end] * 1.4, 1.0));
        }
        if (!b.profile.ores.isEmpty())
        {
            BlockState ore = b.profile.ores.pick(b.rng.nextDouble());
            // Just under the top surface, so the vein shows along the crest.
            for (int i = 3; i < 9; i++)
            {
                b.add(new CaveShape.Capsule(space, CaveShape.Kind.ORE, ore, null, false, points[i].x, points[i].y + radii[i] * 0.75, points[i].z,
                        points[i + 1].x, points[i + 1].y + radii[i + 1] * 0.75, points[i + 1].z, 1.0, 1.0, 1.0));
            }
        }
        b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ARCH, cx, sy + height * 0.5, cz, span / 2 + thick + 8, space));
        b.route.add("sea arch " + Mth.floor(span) + "x" + Mth.floor(height));
        return true;
    }
}
