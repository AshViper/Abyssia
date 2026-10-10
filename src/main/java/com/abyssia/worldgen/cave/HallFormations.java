package com.abyssia.worldgen.cave;

import com.abyssia.worldgen.cave.CavernPlanner.Site;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * AB03: the formations of a hall (see {@link CaveShape.Hall}), as layout shapes. Counts follow the hall's floor area
 * (a mega hall of radius 100 has about 31 000 square blocks), each kind bounded so even the largest stays cheap:
 * <ul>
 *     <li>trunks: a few massive columns, floor to roof, ribbed and streaked, flared at both ends</li>
 *     <li>fused pillars: a stalactite and a stalagmite grown together, pinched where they met</li>
 *     <li>spires: thin stalagmites in small groups, up to most of the hall's height</li>
 *     <li>giant stalactite clusters: a long, rough, ribbed stalactite with shorter ones around it</li>
 *     <li>column clusters of the environment's block (basalt, blue ice, crystal) on islands and terraces</li>
 *     <li>light columns (tall luminous plants), vents, a glowing niche high in the far wall, and the cavern's usual wall
 *     pockets, ledges, bridges, ore outcrops, crystal stands, gardens and centrepiece</li>
 * </ul>
 */
final class HallFormations
{
    private HallFormations() {}

    static void plan(Site s, @Nullable CavernCenter center)
    {
        CaveShape.Hall h = s.chamber.hall();
        CaveEnvironment.HallForm form = s.space.environment.hall.form();
        CaveBuilder b = s.b;
        double area = Math.PI * h.rx * h.rz;
        double r = Math.max(h.rx, h.rz);

        CavernFormations.wallFormation(s);
        if (center != null && center != CavernCenter.LAKE) CavernFormations.center(s, center);

        int trunks = count(b, form.trunks() * area / 7000, 10);
        for (int i = 0; i < trunks; i++)
        {
            double[] p = s.point(0.75, 8);
            if (p != null && trunk(s, p[0], p[1], Mth.clamp(r * 0.075, 2.2, 7.5) * b.range(0.8, 1.15), false)) s.cavern.trunks++;
        }
        int pillars = count(b, form.trunks() * area / 2800, 28);
        for (int i = 0; i < pillars; i++)
        {
            double[] p = s.point(0.8, 5);
            if (p != null && trunk(s, p[0], p[1], Mth.clamp(r * 0.045, 1.6, 5.0) * b.range(0.7, 1.2), true)) s.cavern.trunks++;
        }
        int spires = count(b, form.spires() * area / 1300, 48);
        for (int i = 0; i < spires; )
        {
            double[] p = s.point(0.85, 3);
            int group = Math.min(spires - i, b.range(1, 4));
            i += group;
            if (p == null) continue;
            for (int k = 0; k < group; k++)
            {
                double a = b.rng.nextDouble() * Math.PI * 2, d = k == 0 ? 0 : b.range(2.5, 7.0);
                if (spire(s, p[0] + Math.cos(a) * d, p[1] + Math.sin(a) * d, b.range(0.8, Mth.clamp(r * 0.025, 1.2, 2.6)) * (k == 0 ? 1.0 : 0.75))) s.cavern.spires++;
            }
        }
        int clusters = count(b, form.stalactites() * area / 1700, 36);
        for (int i = 0; i < clusters; i++)
        {
            double[] p = s.point(0.8, 4);
            if (p != null) stalactiteCluster(s, p[0], p[1]);
        }
        if (form.columnBlock().isPresent() && form.columnClusters() > 0)
        {
            int n = count(b, form.columnClusters() * (1 + area / 4000), 16);
            for (int i = 0; i < n; i++) columnCluster(s, h, form.columnBlock().get());
        }

        // The cavern's other structures, on a smaller budget (the hall's walls are tall and plain).
        CavernFormations.shelves(s);
        if (s.tier.ordinal() >= Cavern.Tier.MASSIVE.ordinal()) CavernFormations.bridges(s);
        CavernFormations.megaOres(s);
        CavernFormations.crystalForests(s);
        CavernFormations.gardens(s);

        if (form.vents() > 0)
        {
            int n = count(b, form.vents() * (1 + area / 5000), 10);
            for (int i = 0; i < n; i++)
            {
                double[] p = s.point(0.8, 2);
                if (p != null) s.vents(p[0], p[1], b.range(3.0, 9.0), b.range(1, 4));
            }
        }
        if (form.beacon().isPresent() && form.beacons() > 0) beacons(s, h, count(b, form.beacons() * (1 + area / 8000), 10));
        lighting(s, h, form);
    }

    /** Expected count with a random spread, rounded at random, capped. */
    private static int count(CaveBuilder b, double expected, int cap)
    {
        double e = expected * b.range(0.6, 1.4);
        return Math.min(cap, Mth.floor(e) + (b.rng.nextDouble() < e - Mth.floor(e) ? 1 : 0));
    }

    private static double smooth(double t)
    {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    /** Lowest floor and highest roof over a disc (centre and four points at {@code r}); NaN where it leaves the hall. */
    private static double[] span(Site s, double x, double z, double r)
    {
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c)) return null;
        double lo = f, hi = c;
        for (int k = 0; k < 4; k++)
        {
            double a = k * Math.PI / 2;
            double pf = s.floor(x + Math.cos(a) * r, z + Math.sin(a) * r), pc = s.chamber.ceilingAt(x + Math.cos(a) * r, z + Math.sin(a) * r);
            if (!Double.isNaN(pf)) lo = Math.min(lo, pf);
            if (!Double.isNaN(pc)) hi = Math.max(hi, pc);
        }
        return new double[] {f, c, lo, hi};
    }

    /**
     * Knots of a column from y0 to y1 (about every 3 blocks): the axis wanders by {@code wobble} along path noise, and
     * {@code profile} gives the radius at each knot's visible height v (0 at {@code vis0}, 1 at {@code vis1}), swollen and
     * pinched by a slow noise.
     */
    private static CaveShape.Column column(Site s, @Nullable BlockState block, double x, double z, double y0, double y1, double vis0, double vis1,
                                           double wobble, double lumpiness, java.util.function.DoubleUnaryOperator profile, double ribs, double ribAmp)
    {
        CaveBuilder b = s.b;
        int knots = Math.max(3, Mth.ceil((y1 - y0) / 3) + 1);
        double[] ax = new double[knots], az = new double[knots], radius = new double[knots];
        int salt = b.nextSalt();
        for (int i = 0; i < knots; i++)
        {
            double t = i / (double) (knots - 1), y = Mth.lerp(t, y0, y1);
            double v = Mth.clamp((y - vis0) / Math.max(1, vis1 - vis0), 0.0, 1.0);
            ax[i] = x + b.noises.path(t * 2.2, salt) * wobble;
            az[i] = z + b.noises.path(t * 2.2, salt + 0.5) * wobble;
            radius[i] = Math.max(0.35, profile.applyAsDouble(v) * (1 + lumpiness * b.noises.path(v * 5, salt + 0.25)));
        }
        return new CaveShape.Column(s.formation, block, y0, y1, ax, az, radius, ribs, ribAmp, b.rng.nextDouble() * Math.PI * 2, b.range(-0.04, 0.04));
    }

    /**
     * A trunk (thick, nearly straight, flared at both ends) or a fused pillar (an hourglass pinched where stalactite and
     * stalagmite met), floor to roof, ribbed.
     */
    private static boolean trunk(Site s, double x, double z, double rb, boolean fused)
    {
        CaveBuilder b = s.b;
        double[] sp = span(s, x, z, rb);
        if (sp == null || sp[1] - sp[0] < 10) return false;
        double meet = b.range(0.35, 0.65), waist = fused ? b.range(0.22, 0.5) : b.range(0.7, 0.9);
        double ribs = Math.round(b.range(5, 9) + rb * 0.6), ribAmp = fused ? b.range(0.04, 0.1) : b.range(0.07, 0.15);
        s.b.add(column(s, null, x, z, sp[2] - s.anchor, sp[3] + s.anchor, sp[0], sp[1], rb * 0.35, 0.18, v -> {
            double u = v < meet ? 1 - v / meet : (v - meet) / (1 - meet);
            double r = rb * (waist + (1 - waist) * Math.pow(u, 1.4));
            return r + rb * 0.55 * (1 - smooth(v / 0.1)) + rb * 0.6 * (1 - smooth((1 - v) / 0.12));
        }, ribs, ribAmp));
        s.take(x, z, rb * 2.2);
        return true;
    }

    /** A spire: a thin stalagmite from the floor, up to most of the height, pointed. */
    private static boolean spire(Site s, double x, double z, double rb)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c) || c - f < 12) return false;
        double height = Math.min(c - f - 5, (c - f) * b.range(0.35, 0.8));
        if (height < 6) return false;
        CaveShape.Column spire = column(s, null, x, z, f - 3, f + height, f, f + height, rb * 0.4, 0.12,
                v -> rb * Math.pow(1 - v, 0.85) + 0.3 + rb * 0.6 * (1 - smooth(v / 0.1)), 0, 0);
        spire.tip = Direction.UP;
        b.add(spire);
        return true;
    }

    /** A giant stalactite (long, tapering, rough and ribbed) with two to five shorter ones around it. */
    private static void stalactiteCluster(Site s, double x, double z)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c) || c - f < 14) return;
        double gap = c - f;
        double length = Math.min(gap - 6, gap * b.range(0.3, 0.72));
        double rb = Mth.clamp(length * b.range(0.07, 0.12), 1.5, 8.0);
        if (stalactite(s, x, z, length, rb)) s.cavern.stalactites++;
        int around = b.range(2, 5);
        for (int k = 0; k < around; k++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = rb * b.range(1.6, 3.5) + 2;
            if (stalactite(s, x + Math.cos(a) * d, z + Math.sin(a) * d, length * b.range(0.25, 0.7), rb * b.range(0.35, 0.7))) s.cavern.stalactites++;
        }
        s.take(x, z, rb * 1.5);
    }

    private static boolean stalactite(Site s, double x, double z, double length, double rb)
    {
        CaveBuilder b = s.b;
        double c = s.ceiling(x, z), f = s.floor(x, z);
        if (Double.isNaN(c) || Double.isNaN(f) || length < 4) return false;
        length = Math.min(length, c - f - 4);
        if (length < 4) return false;
        double top = c + s.anchor, tip = c - length;
        // v runs from the tip (0) to the roof (1).
        CaveShape.Column column = column(s, null, x, z, tip, top, tip, c, rb * 0.3, 0.22,
                v -> rb * Math.pow(v, 0.9) + 0.3 + rb * 0.5 * (1 - smooth((1 - v) / 0.12)),
                rb >= 2.5 ? Math.round(b.range(6, 11)) : 0, rb >= 2.5 ? b.range(0.08, 0.18) : 0);
        column.tip = Direction.DOWN;
        b.add(column);
        return true;
    }

    /** A cluster of thin columns of the environment's block (basalt, blue ice, crystal), tallest at the middle. */
    private static void columnCluster(Site s, CaveShape.Hall h, BlockState block)
    {
        CaveBuilder b = s.b;
        double[] p = null;
        // Islands and plateaus first.
        for (int attempt = 0; attempt < 6 && p == null; attempt++)
        {
            double[] q = s.point(0.8, 3);
            if (q != null && (attempt >= 3 || h.floor(q[0], q[1]) > h.level)) p = q;
        }
        if (p == null) return;
        int n = b.range(5, 14);
        double spread = b.range(2.5, 5.5), tall = b.range(5.0, 14.0);
        for (int k = 0; k < n; k++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = k == 0 ? 0 : Math.sqrt(b.rng.nextDouble()) * spread;
            double x = p[0] + Math.cos(a) * d, z = p[1] + Math.sin(a) * d;
            double f = s.floor(x, z), c = s.ceiling(x, z);
            if (Double.isNaN(f) || Double.isNaN(c)) continue;
            double height = Math.min(c - f - 3, Math.max(2, tall * (1 - d / (spread + 1)) * b.range(0.5, 1.1)));
            if (height < 2) continue;
            double cr = b.range(0.6, 1.4);
            b.add(new CaveShape.Column(s.formation, block, f - 2, Math.round(f + height), new double[] {x, x}, new double[] {z, z}, new double[] {cr, cr},
                    6, 0, 0, 0));
        }
        s.cavern.clusters++;
        s.take(p[0], p[1], spread);
    }

    /** Light columns: a tall luminous plant at a block column (grown by {@link HallDecorator}). */
    private static void beacons(Site s, CaveShape.Hall h, int count)
    {
        for (int i = 0; i < count; i++)
        {
            double[] p = s.point(0.8, 3);
            if (p == null) continue;
            s.cavern.addBeacon(new Cavern.Beacon(Mth.floor(p[0]), Mth.floor(p[1])));
        }
    }

    // ---------------------------------------------------------------- AB04 lighting

    /** Hall size class for the lighting budgets: 0 large, 1 massive, 2 mega. */
    private static int sizeClass(Site s)
    {
        if (s.space.type.size == CaveType.Size.MEGA) return 2;
        return Math.max(s.chamber.rx(), s.chamber.rz()) >= 32 ? 1 : 0;
    }

    /**
     * AB04: light reaches about 15 blocks, so a hall is lit near and middle distance by a grid of light patches over its
     * surfaces (spacing set here, placed per block by {@link HallDecorator#lights}), and read from afar by its landmarks,
     * whose glowing blocks show as silhouettes: crystal clusters hanging from the roof, giant columns veined with light,
     * windows (wall niches whose mouths are outlined in light) and lights standing on islands.
     * <pre>
     *            roof clusters  glowing columns  windows  island lights  grid spacing wall / roof / floor
     *   large    1              0-1              1        0-1            ~13 / 13 / 8
     *   massive  1-2            1-2              2-4      1              ~18 / 18 / 11
     *   mega     2-3            2-4              4-6      1-2            ~27 / 27 / 16 (radius 100)
     * </pre>
     */
    private static void lighting(Site s, CaveShape.Hall h, CaveEnvironment.HallForm form)
    {
        CaveBuilder b = s.b;
        int size = sizeClass(s);
        double r = Math.max(h.rx, h.rz);
        double spacing = Mth.clamp(10 + r * 0.17, 13, 30) * form.lightSpacing();
        s.cavern.lightWall = spacing;
        s.cavern.lightCeiling = spacing;
        s.cavern.lightFloor = Math.max(6, spacing * 0.6);
        s.cavern.lightCap = size == 0 ? 160 : size == 1 ? 128 : 112;
        Palette<BlockState> landmark = s.space.environment.hallLandmark;
        if (landmark.isEmpty()) return;

        int roof = size == 0 ? 1 : size == 1 ? b.range(1, 2) : b.range(2, 3);
        for (int i = 0; i < roof; i++)
        {
            double[] p = s.point(0.65, 6);
            if (p != null && roofCluster(s, p[0], p[1], landmark, size)) s.cavern.landmarks++;
        }
        int columns = size == 0 ? b.range(0, 1) : size == 1 ? b.range(1, 2) : b.range(2, 4);
        for (int i = 0; i < columns; i++)
        {
            double[] p = s.point(0.7, 10);
            if (p != null && glowColumn(s, p[0], p[1], Mth.clamp(r * 0.06, 2.5, 6.0) * b.range(0.85, 1.15), landmark)) s.cavern.landmarks++;
        }
        int windows = size == 0 ? 1 : size == 1 ? b.range(2, 4) : b.range(4, 6);
        double base = s.chamber.front() + Math.PI;
        for (int i = 0; i < windows; i++)
        {
            // The first faces the entrance side from across the hall; the others spread round the walls.
            double angle = base + (i == 0 ? 0 : (i % 2 == 1 ? 1 : -1) * ((i + 1) / 2) * Math.PI * 2 / (windows + 1)) + b.range(-0.25, 0.25);
            window(s, h, angle, b.range(0.25, 0.55), Mth.clamp(r * (size == 2 ? 0.07 : 0.1), 4, 11) * b.range(0.8, 1.2));
        }
        int lights = size == 0 ? b.range(0, 1) : size == 1 ? 1 : b.range(1, 2);
        for (int i = 0; i < lights; i++)
        {
            if (islandLight(s, h, i, landmark, size)) s.cavern.landmarks++;
        }
    }

    /** Crystals of light hanging from the roof in a cluster: one long, the rest shorter around it. */
    private static boolean roofCluster(Site s, double x, double z, Palette<BlockState> palette, int size)
    {
        CaveBuilder b = s.b;
        double c = s.ceiling(x, z), f = s.floor(x, z);
        if (Double.isNaN(c) || Double.isNaN(f) || c - f < 16) return false;
        BlockState block = palette.pick(b.rng.nextDouble());
        int n = b.range(6, 10 + size * 4);
        double spread = 3 + size * 2.5;
        for (int k = 0; k < n; k++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = k == 0 ? 0 : Math.sqrt(b.rng.nextDouble()) * spread;
            double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
            double pc = s.ceiling(px, pz);
            if (Double.isNaN(pc)) continue;
            double length = Math.min((pc - f) * 0.4, (k == 0 ? 8 + size * 5 : 3 + size * 2) * b.range(0.7, 1.3));
            double width = (k == 0 ? 1.4 + size * 0.4 : 0.8 + size * 0.2) * b.range(0.8, 1.2);
            double tilt = b.range(0.0, 0.35);
            double ex = px + Math.cos(a) * length * tilt, ez = pz + Math.sin(a) * length * tilt;
            b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.FILL, block, null, false, px, pc + 2, pz, ex, pc - length, ez, width, 0.35, 1.0));
        }
        s.take(x, z, spread);
        return true;
    }

    /** A giant column, floor to roof, straight and ribbed, with veins of light spiralling round its skin. */
    private static boolean glowColumn(Site s, double x, double z, double rb, Palette<BlockState> palette)
    {
        CaveBuilder b = s.b;
        double[] sp = span(s, x, z, rb);
        if (sp == null || sp[1] - sp[0] < 16) return false;
        double waist = b.range(0.7, 0.85);
        java.util.function.DoubleUnaryOperator profile = v -> {
            double u = Math.abs(v - 0.5) * 2;
            return rb * (waist + (1 - waist) * u * u) + rb * 0.5 * (1 - smooth(v / 0.1)) + rb * 0.5 * (1 - smooth((1 - v) / 0.12));
        };
        b.add(column(s, null, x, z, sp[2] - s.anchor, sp[3] + s.anchor, sp[0], sp[1], 0, 0.06, profile, Math.round(b.range(6, 10)), b.range(0.05, 0.1)));
        BlockState light = palette.pick(b.rng.nextDouble());
        int veins = b.range(2, 3);
        double turns = (sp[1] - sp[0]) / b.range(14, 22), phase = b.rng.nextDouble() * Math.PI * 2;
        int steps = Mth.ceil((sp[1] - sp[0]) / 2.5);
        for (int vIdx = 0; vIdx < veins; vIdx++)
        {
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int i = 0; i <= steps; i++)
            {
                double v = i / (double) steps, y = Mth.lerp(v, sp[0], sp[1]);
                double a = phase + vIdx * Math.PI * 2 / veins + v * turns * Math.PI * 2;
                double rr = profile.applyAsDouble(v) * 0.95;
                double px = x + Math.cos(a) * rr, pz = z + Math.sin(a) * rr;
                if (i > 0) b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.ORE, light, null, false, prevX, prevY, prevZ, px, y, pz, 0.9, 0.9, 1.0));
                prevX = px;
                prevY = y;
                prevZ = pz;
            }
        }
        s.take(x, z, rb * 2.4);
        return true;
    }

    /**
     * A window: a niche cut into the wall at this bearing and relative height, its mouth outlined in light by
     * {@link HallDecorator#windows} (the outline and a few points inside, never filled).
     */
    private static void window(Site s, CaveShape.Hall h, double angle, double height, double radius)
    {
        double y = h.level + (h.apexY() - h.level) * height;
        double[] w = s.wall(angle, y);
        if (w == null) return;
        double x = w[0] + w[3] * radius * 0.5, z = w[2] + w[4] * radius * 0.5;
        s.b.add(new CaveShape.Ellipsoid(s.space, CaveShape.Kind.CARVE, null, null, true, x, y, z, radius, radius * 1.3, radius, Double.NaN));
        s.cavern.addWindow(new Cavern.Window(x, y, z, radius, w[3], w[4]));
    }

    /** A light standing on an island (or a high terrace): a rock tower with a lamp of light blocks on top. */
    private static boolean islandLight(Site s, CaveShape.Hall h, int index, Palette<BlockState> palette, int size)
    {
        CaveBuilder b = s.b;
        double x, z;
        if (index < h.islandCount())
        {
            double[] island = h.island(index);
            x = island[0];
            z = island[1];
        }
        else
        {
            double[] p = null;
            for (int attempt = 0; attempt < 6 && p == null; attempt++)
            {
                double[] q = s.point(0.8, 6);
                if (q != null && h.floor(q[0], q[1]) > h.level + 1) p = q;
            }
            if (p == null) return false;
            x = p[0];
            z = p[1];
        }
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c) || c - f < 16) return false;
        double height = Math.min(c - f - 6, (6 + size * 5) * b.range(0.8, 1.3));
        double rb = 1.4 + size * 0.4;
        b.add(column(s, null, x, z, f - 3, f + height, f, f + height, 0.3, 0.1, v -> rb * (1.35 - 0.45 * v), 0, 0));
        double lamp = 1.4 + size * 0.45;
        b.add(new CaveShape.Ellipsoid(s.formation, CaveShape.Kind.FILL, palette.pick(b.rng.nextDouble()), null, false, x, f + height + lamp * 0.6, z,
                lamp, lamp * 1.2, lamp, Double.NaN));
        s.take(x, z, 4);
        return true;
    }
}
