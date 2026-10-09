package com.abyssia.worldgen.cave;

import com.abyssia.registry.ModBlocks;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Chambers and everything built inside them. A chamber is a Voronoi-like cluster of overlapping ellipsoid lobes
 * (chamber noise decides how many and where), cut to a flat sediment floor, with domed ceilings of varying height.
 * Then come the large formations (giant stalactites and stalagmites, hourglass pillars where the two have met, rock
 * bridges, shelves, ceiling pendants and collapses), large crystals, exposed ore bodies and vents, as the chamber's
 * environment and landmark call for.
 */
final class CaveChamberGenerator
{
    /** Gas-pocket shells: solid rock this thick (in field units) seals the air from any water around it. */
    static final double LAKE_SHELL = 3.0;

    private CaveChamberGenerator() {}

    /** {@code front}: horizontal angle toward where the way in arrives (entrance side), for the entrance-to-core gradient. */
    record Chamber(CaveSpace space, double x, double y, double z, double rx, double ry, double rz, double floorY, List<CaveShape.Ellipsoid> lobes,
                   double front, @Nullable CaveShape.Hall hall)
    {
        Chamber(CaveSpace space, double x, double y, double z, double rx, double ry, double rz, double floorY, List<CaveShape.Ellipsoid> lobes, double front)
        {
            this(space, x, y, z, rx, ry, rz, floorY, lobes, front, null);
        }

        double radius()
        {
            return Math.max(rx, rz);
        }

        /**
         * A point on the wall of the main lobe, for tunnels to attach to; below the water line of a lake. A hall's ports lie
         * out along the bearing over its floor, a few blocks above it, so passages open into the hall low on its walls.
         */
        Vec3 port(double angle, double yFraction)
        {
            if (hall != null)
            {
                double c = Math.cos(angle), s = Math.sin(angle);
                double px = x + c * rx * 0.72, pz = z + s * rz * 0.72;
                return new Vec3(px, hall.floor(px, pz) + 3 + Math.max(0, yFraction) * 8, pz);
            }
            double py = y + ry * yFraction;
            if (space.hasLake()) py = Math.min(py, space.waterLevel - 4);
            if (!Double.isNaN(floorY)) py = Math.max(py, floorY + 2.5);
            return new Vec3(x + Math.cos(angle) * rx * 0.72, py, z + Math.sin(angle) * rz * 0.72);
        }

        double ceilingAt(double px, double pz)
        {
            if (hall != null) return hall.ceiling(px, pz);
            double best = Double.NaN;
            for (CaveShape.Ellipsoid lobe : lobes)
            {
                double t = lobe.top(px, pz);
                if (!Double.isNaN(t) && (Double.isNaN(best) || t > best)) best = t;
            }
            return best;
        }

        double floorAt(double px, double pz)
        {
            if (hall != null) return hall.q(px, pz) >= 1 ? Double.NaN : hall.floor(px, pz);
            double best = Double.NaN;
            for (CaveShape.Ellipsoid lobe : lobes)
            {
                double b = lobe.bottom(px, pz);
                if (!Double.isNaN(b) && (Double.isNaN(best) || b < best)) best = b;
            }
            return best;
        }
    }

    /**
     * A chamber of {@code lobes} lobes around (cx, cy, cz). Extra lobes are offset and resized at random (chamber
     * noise), each with its own ceiling height (vertical noise), so no two chambers share an outline.
     */
    static Chamber chamber(CaveBuilder b, CaveSpace space, double cx, double cy, double cz, double r, double vertical, int lobes, boolean flatFloor)
    {
        return chamber(b, space, cx, cy, cz, r, vertical, lobes, flatFloor, b.rng.nextDouble() * Math.PI * 2);
    }

    static Chamber chamber(CaveBuilder b, CaveSpace space, double cx, double cy, double cz, double r, double vertical, int lobes, boolean flatFloor,
                           double front)
    {
        double rx = r * b.range(0.85, 1.15), rz = r * b.range(0.85, 1.15);
        double ry = Math.max(2.5, r * vertical * b.range(0.9, 1.1));
        double floorY = flatFloor ? cy - ry * b.range(0.45, 0.65) : Double.NaN;
        if (space.hasLake()) floorY = Double.NaN;
        List<CaveShape.Ellipsoid> list = new ArrayList<>();
        list.add(lobe(b, space, cx, cy, cz, rx, ry, rz, floorY));
        for (int i = 1; i < lobes; i++)
        {
            double angle = b.rng.nextDouble() * Math.PI * 2;
            double dist = r * b.range(0.35, 0.7);
            double lr = r * b.range(0.45, 0.8);
            double ly = cy + (b.rng.nextDouble() - 0.5) * ry * 0.5;
            double lry = Math.max(2.5, lr * vertical * b.range(0.8, 1.3));
            list.add(lobe(b, space, cx + Math.cos(angle) * dist, ly, cz + Math.sin(angle) * dist, lr * b.range(0.85, 1.15), lry, lr * b.range(0.85, 1.15), floorY));
        }
        return new Chamber(space, cx, cy, cz, rx, ry, rz, floorY, List.copyOf(list), front);
    }

    /**
     * The chamber of a mega cavern (radius r = 64-128): a wide, low main hall plus 5-8 more lobes of three kinds, so it
     * is not a ball: tall narrow chimneys rising from the hall's floor, wing halls (wide and flat, at their own heights,
     * with their own level floors: steps between halls) and rounder side rooms. Lobes stay within 1.5 r of the centre
     * and inside the window's Y range. {@code vertical} is the type's factor (the hall is a share of it).
     */
    static Chamber megaChamber(CaveBuilder b, CaveSpace space, double cx, double cy, double cz, double r, double vertical, boolean flatFloor)
    {
        double rx = r * b.range(0.9, 1.1), rz = r * b.range(0.9, 1.1);
        double ry = Math.max(2.5, r * vertical * b.range(0.55, 0.8));
        double floorY = flatFloor ? cy - ry * b.range(0.45, 0.65) : Double.NaN;
        double front = b.rng.nextDouble() * Math.PI * 2;
        double lowest = b.minCarveY() + 3, highest = b.net.maxY() - 8;
        List<CaveShape.Ellipsoid> list = new ArrayList<>();
        list.add(lobe(b, space, cx, cy, cz, rx, ry, rz, floorY));
        int extra = b.range(5, 8);
        for (int i = 0; i < extra; i++)
        {
            double angle = b.rng.nextDouble() * Math.PI * 2, roll = b.rng.nextDouble();
            double dist, lrx, lry, lrz, ly;
            int kind;
            if (roll < 0.3)
            {
                // Chimney: narrow and tall, standing on the hall's floor.
                kind = 0;
                dist = r * b.range(0.15, 0.65);
                double cr = r * b.range(0.14, 0.26);
                lrx = cr * b.range(0.9, 1.2);
                lrz = cr * b.range(0.9, 1.2);
                lry = r * vertical * b.range(0.7, 1.1);
                ly = (Double.isNaN(floorY) ? cy - ry * 0.5 : floorY - 2) + lry * 0.9;
            }
            else if (roll < 0.65)
            {
                // Wing hall: wide and flat, at its own height.
                kind = 1;
                dist = r * b.range(0.5, 0.8);
                double lr = r * b.range(0.4, 0.6);
                lrx = lr * b.range(0.85, 1.15);
                lrz = lr * b.range(0.85, 1.15);
                lry = Math.max(2.5, lr * vertical * b.range(0.4, 0.7));
                ly = cy + b.range(-0.4, 0.4) * ry;
            }
            else
            {
                // Side room: rounder.
                kind = 2;
                dist = r * b.range(0.55, 0.8);
                double lr = r * b.range(0.3, 0.5);
                lrx = lr * b.range(0.85, 1.15);
                lrz = lr * b.range(0.85, 1.15);
                lry = Math.max(2.5, lr * vertical * b.range(0.8, 1.2));
                ly = cy + b.range(-0.6, 0.6) * ry;
            }
            lry = Math.min(lry, (highest - lowest) / 2 - 2);
            ly = Mth.clamp(ly, lowest + lry, highest - lry);
            double lfloor = kind == 1 && flatFloor ? ly - lry * b.range(0.45, 0.65) : Double.NaN;
            list.add(lobe(b, space, cx + Math.cos(angle) * dist, ly, cz + Math.sin(angle) * dist, lrx, lry, lrz, lfloor));
        }
        return new Chamber(space, cx, cy, cz, rx, ry, rz, floorY, List.copyOf(list), front);
    }

    // ---------------------------------------------------------------- AB03 air halls

    /** Cave types whose main chamber becomes a hall (radius 16+; landmarks keep their own designs). */
    static boolean hallType(CaveType type)
    {
        return type == CaveType.LARGE_ABYSSAL_CAVE || type == CaveType.MASSIVE_CAVERN || type == CaveType.MEGA_CAVERN;
    }

    /** Vault apex above the reference level for a hall of radius r: 26 at r 16, 44 at 32, 70 at 64, 115 at 128. */
    static double hallHeight(double r)
    {
        if (r <= 32) return Mth.lerp(Mth.clamp((r - 16) / 16, 0, 1), 26, 44);
        if (r <= 64) return Mth.lerp((r - 32) / 32, 44, 70);
        return Mth.lerp(Math.min(1, (r - 64) / 64), 70, 115);
    }

    private static double hallDepth(double r)
    {
        return Mth.clamp(r * 0.12, 5, 16);
    }

    private static double hallRelief(double r)
    {
        return Mth.clamp(r * 0.05, 1.2, 4.0);
    }

    /** Room a hall needs below its reference level: the basin, its relief, the wall layer and a margin. */
    static double hallBelow(double r)
    {
        return hallDepth(r) * 1.15 + hallRelief(r) + 7;
    }

    /** Room a hall of this height needs above its reference level: the vault, its wall noise, the stalactite anchors and a margin. */
    static double hallAbove(double height, double r)
    {
        return height + Mth.clamp(r * 0.035, 1.2, 4.0) + 4.5 + 8;
    }

    /**
     * A hall (see {@link CaveShape.Hall}) around (cx, cz) whose reference level (basin below, terraces above) is {@code level},
     * its vault apex {@code height} above it. The environment's hall form sets the terrace steps, how far the basin reaches and
     * the islands and plateaus rising from it.
     */
    static Chamber hall(CaveBuilder b, CaveSpace space, double cx, double cz, double r, int level, double height, double front)
    {
        CaveEnvironment.HallForm form = space.environment.hall.form();
        double rx = r * b.range(0.9, 1.1), rz = r * b.range(0.9, 1.1);
        double relief = hallRelief(r), depth = hallDepth(r) * b.range(0.85, 1.15);
        double rise = Mth.clamp(height * 0.2, 4, 22) * b.range(0.8, 1.15);
        double spring = Math.max(rise + relief + 5, height * b.range(0.18, 0.32));
        double vault = Math.max(8, height - spring);
        double shore = b.range(form.shoreMin(), Math.max(form.shoreMin(), form.shoreMax()));
        int step = b.range(form.stepMin(), Math.max(form.stepMin(), form.stepMax()));
        // A few smooth lobes and bays on the outline (harmonics 2-5).
        double[] amplitudes = {0.07, 0.05, 0.035, 0.025};
        double[] harmonics = new double[amplitudes.length * 3];
        for (int k = 0; k < amplitudes.length; k++)
        {
            double phase = b.rng.nextDouble() * Math.PI * 2;
            harmonics[k * 3] = amplitudes[k] * b.range(0.4, 1.3);
            harmonics[k * 3 + 1] = Math.cos(phase);
            harmonics[k * 3 + 2] = Math.sin(phase);
        }
        // Islands and plateaus in the lake, about one per 2600 square blocks of lake.
        double lakeArea = Math.PI * rx * rz * shore * shore;
        int count = Math.min(9, Mth.floor(form.islands() * lakeArea / 2600 * b.range(0.6, 1.4) + b.rng.nextDouble()));
        double[] islands = new double[count * 5];
        for (int i = 0; i < count; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = b.range(0.12, 0.8) * shore;
            double ir = b.range(3.0, Mth.clamp(r * 0.14, 4, 16));
            boolean flat = b.rng.nextFloat() < form.plateaus();
            double top = flat ? b.range(2.0, Math.max(2.5, Math.min(rise + 6, ir * 0.7))) : b.range(1.0, Math.max(1.5, Math.min(rise + 3, ir * 0.8)));
            islands[i * 5] = cx + Math.cos(a) * rx * d;
            islands[i * 5 + 1] = cz + Math.sin(a) * rz * d;
            islands[i * 5 + 2] = ir;
            islands[i * 5 + 3] = top;
            islands[i * 5 + 4] = flat ? 1 : 0;
        }
        CaveShape.Hall shape = new CaveShape.Hall(space, b.noises, cx, cz, rx, rz, level, level + spring, vault, shore, depth, rise, relief,
                step, harmonics, islands);
        space.hall = shape;
        b.add(shape);
        return new Chamber(space, cx, level, cz, rx, spring + vault, rz, Double.NaN, List.of(), front, shape);
    }

    private static CaveShape.Ellipsoid lobe(CaveBuilder b, CaveSpace space, double x, double y, double z, double rx, double ry, double rz, double floorY)
    {
        CaveShape.Ellipsoid e = new CaveShape.Ellipsoid(space, CaveShape.Kind.CARVE, null, null, true, x, y, z, rx, ry, rz, floorY);
        b.add(e);
        return e;
    }

    /**
     * Vertical placement for a chamber of radius r: below the lowest seabed around it by {@code cover}, above the
     * bedrock. Returns NaN when there is no room.
     */
    static double fitY(CaveBuilder b, double x, double z, double r, double ry, double cover, double preferredY)
    {
        double top = b.net.lowestSeabed(x, z, r) - cover - ry;
        double bottom = b.minCarveY() + ry + 2;
        if (top < bottom) return Double.NaN;
        return Mth.clamp(preferredY, bottom, top);
    }

    /**
     * Centre height for an underground lake chamber near {@code preferredY}: low enough that its gas dome (with the
     * displaced wall, the sealing shell and a few blocks of rock) stays under the seabed everywhere above it.
     * NaN when there is no such room.
     */
    static double lakeY(CaveNetwork net, double x, double z, double r, double ry, double preferredY)
    {
        ry = Math.max(4, ry);
        // Lobes can rise to about 1.3 ry above the centre; lake walls displace at most 4.5 blocks.
        double clearance = ry * 1.35 + 4.5 + LAKE_SHELL + 5;
        double top = net.lowestSeabed(x, z, r * 1.05) - clearance;
        double bottom = net.minY() + 12 + ry;
        if (top < bottom) return Double.NaN;
        return Mth.clamp(preferredY, bottom, top);
    }

    /**
     * An underground lake: a chamber whose top holds trapped gas above a flat water surface at {@code waterLevel},
     * sealed from all other water by a solid shell. {@code y} must come from {@link #lakeY}.
     */
    static Chamber lake(CaveBuilder b, CaveType type, @Nullable CaveLandmark landmark, ResourceLocation env, double x, double y, double z,
                        double r, double vertical, int lobes, int waterLevel, double front)
    {
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, type, landmark, env, x, y, z, r, waterLevel);
        Chamber c = chamber(b, space, x, y, z, r, vertical, lobes, false, front);
        islands(b, c);
        return c;
    }

    /** Islands and shoals breaking a lake's surface: dry land inside the gas pocket. */
    static void islands(CaveBuilder b, Chamber c)
    {
        CaveSpace space = c.space();
        double r = c.radius();
        int islands = r < 12 ? b.range(0, 1) : b.range(1, 3);
        CaveSpace fs = b.formationSpace(space);
        for (int i = 0; i < islands; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = r * b.range(0.1, 0.6);
            double ir = b.range(3.0, Math.max(3.5, Math.min(9.0, r * 0.35)));
            b.add(new CaveShape.Ellipsoid(fs, CaveShape.Kind.FILL, null, null, true, c.x() + Math.cos(a) * d, space.waterLevel - b.range(1.5, 3.0),
                    c.z() + Math.sin(a) * d, ir, b.range(3.0, 4.5), ir * b.range(0.7, 1.3), Double.NaN));
        }
    }

    // ---------------------------------------------------------------- furnishing

    /** Everything a chamber's environment, type and landmark call for. */
    static void furnish(CaveBuilder b, Chamber c)
    {
        CaveSpace s = c.space();
        CaveLandmark landmark = s.landmark;
        String env = s.environmentId.getPath();
        double r = c.radius();
        // A hall raises its own columns and stalactites (HallFormations, via the cavern planner).
        if (c.hall() == null) rockFormations(b, c, landmark == CaveLandmark.GIANT_STALACTITE_CHAMBER ? 3.0 : landmark != null ? 1.4 : 1.0);
        if (env.equals("crystal") || landmark == CaveLandmark.MASSIVE_CRYSTAL_CHAMBER)
        {
            crystals(b, c, landmark == CaveLandmark.MASSIVE_CRYSTAL_CHAMBER ? Mth.floor(r / 2.2) : Mth.floor(r / 4.5) + 1,
                    landmark == CaveLandmark.MASSIVE_CRYSTAL_CHAMBER ? 1.6 : 1.0);
        }
        if (env.equals("mineral") || landmark == CaveLandmark.ANCIENT_MINERAL_CHAMBER)
        {
            oreBodies(b, c, landmark == CaveLandmark.ANCIENT_MINERAL_CHAMBER ? 8 + Mth.floor(r / 5) : 2 + Mth.floor(r / 6),
                    landmark == CaveLandmark.ANCIENT_MINERAL_CHAMBER ? 1.6 : 1.0);
        }
        else if (b.rng.nextFloat() < 0.35f)
        {
            // A stray vein here and there rewards exploring any cave.
            oreBodies(b, c, 1, 0.8);
        }
        if (env.equals("thermal") || landmark == CaveLandmark.THERMAL_CATHEDRAL)
        {
            vents(b, c, landmark == CaveLandmark.THERMAL_CATHEDRAL ? 6 + Mth.floor(r / 6) : 1 + Mth.floor(r / 9),
                    landmark == CaveLandmark.THERMAL_CATHEDRAL);
        }
        // Large caverns get the full cavern treatment on top: zones, ecology patches, structures, landmark.
        if (s.role == CaveSpace.Role.CHAMBER && r >= CavernPlanner.MIN_RADIUS) CavernPlanner.plan(b, c);
    }

    /**
     * Giant stalactites and stalagmites, pillars, rock bridges, shelves, ceiling pendants and collapses, in numbers
     * that grow with the chamber. Anchored well inside the rock above or below the (noisy) surface so none float.
     */
    static void rockFormations(CaveBuilder b, Chamber c, double intensity)
    {
        double r = c.radius();
        if (r < 6) return;
        CaveSpace fs = b.formationSpace(c.space());
        double anchor = c.space().maxDisplacement() + 2;
        float speleothems = c.space().environment.formations.speleothemDensity();
        int giants = Mth.floor(r / 6.0 * (0.4 + speleothems * 6) * intensity);
        for (int i = 0; i < giants; i++)
        {
            double[] p = footprintPoint(b, c, 0.8);
            double ceil = c.ceilingAt(p[0], p[1]), floor = c.floorAt(p[0], p[1]);
            if (Double.isNaN(ceil) || Double.isNaN(floor) || ceil - floor < 9) continue;
            double gap = ceil - floor;
            double baseR = Mth.clamp(r * 0.05 + b.range(0.6, 1.8), 1.0, 4.5);
            boolean down = b.rng.nextFloat() < 0.6f;
            double length = gap * b.range(0.2, down ? 0.5 : 0.35);
            if (down) spike(b, fs, p[0], ceil + anchor, p[1], ceil - length, baseR, Direction.DOWN);
            else spike(b, fs, p[0], floor - anchor, p[1], floor + length, baseR, Direction.UP);
        }

        // Pillars: a stalactite and stalagmite grown together, pinched where they met.
        int pillars = r < 12 ? 0 : Mth.floor((r / 16.0 + b.rng.nextDouble()) * intensity);
        for (int i = 0; i < pillars; i++)
        {
            double[] p = footprintPoint(b, c, 0.7);
            double ceil = c.ceilingAt(p[0], p[1]), floor = c.floorAt(p[0], p[1]);
            if (Double.isNaN(ceil) || Double.isNaN(floor) || ceil - floor < 8) continue;
            double mid = floor + (ceil - floor) * b.range(0.35, 0.65);
            double thick = Mth.clamp(r * 0.08 + b.range(0.5, 2.0), 2.0, 6.0);
            double waist = Math.max(1.2, thick * b.range(0.3, 0.55));
            double mx = p[0] + b.range(-1, 1), mz = p[1] + b.range(-1, 1);
            b.add(fill(fs, p[0], floor - anchor, p[1], mx, mid, mz, thick, waist, true));
            b.add(fill(fs, p[0] + b.range(-1.5, 1.5), ceil + anchor, p[1] + b.range(-1.5, 1.5), mx, mid, mz, thick * 0.9, waist, true));
        }

        // Rock bridges spanning the chamber, sagging in the middle.
        int bridges = r < 18 ? 0 : (b.rng.nextFloat() < 0.55f ? 1 : 0) + (r > 36 && b.rng.nextFloat() < 0.5f ? 1 : 0);
        for (int i = 0; i < bridges; i++)
        {
            double a = b.rng.nextDouble() * Math.PI;
            double ceil = c.ceilingAt(c.x(), c.z()), floor = c.floorAt(c.x(), c.z());
            if (Double.isNaN(ceil) || Double.isNaN(floor) || ceil - floor < 16) continue;
            double y = floor + (ceil - floor) * b.range(0.4, 0.7);
            double reach = c.radius() * 1.05;
            double rr = b.range(1.8, 3.2);
            double ox = Math.cos(a) * reach, oz = Math.sin(a) * reach;
            double sag = b.range(1.5, 4.0);
            b.add(fill(fs, c.x() - ox, y + b.range(-1, 2), c.z() - oz, c.x(), y - sag, c.z(), rr * 1.3, rr, true));
            b.add(fill(fs, c.x() + ox, y + b.range(-1, 2), c.z() + oz, c.x(), y - sag, c.z(), rr * 1.3, rr, true));
        }

        // Shelves: flat ledges along the walls.
        int shelves = r < 10 ? 0 : 1 + Mth.floor(r / 14);
        for (int i = 0; i < shelves; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2;
            double px = c.x() + Math.cos(a) * c.rx() * 0.88, pz = c.z() + Math.sin(a) * c.rz() * 0.88;
            double ceil = c.ceilingAt(c.x(), c.z()), floor = c.floorAt(c.x(), c.z());
            if (Double.isNaN(ceil) || Double.isNaN(floor)) continue;
            double y = floor + (ceil - floor) * b.range(0.2, 0.7);
            double len = b.range(4, 9);
            double tx = -Math.sin(a) * len, tz = Math.cos(a) * len;
            b.add(new CaveShape.Capsule(fs, CaveShape.Kind.FILL, null, null, true, px - tx, y, pz - tz, px + tx, y, pz + tz,
                    b.range(2.5, 4.0), b.range(2.0, 3.5), 0.35));
        }

        // Ceiling pendants: fat, short knobs of rock hanging from the roof.
        int pendants = r < 12 ? 0 : Mth.floor(r / 10 * intensity);
        for (int i = 0; i < pendants; i++)
        {
            double[] p = footprintPoint(b, c, 0.75);
            double ceil = c.ceilingAt(p[0], p[1]);
            if (Double.isNaN(ceil)) continue;
            b.add(fill(fs, p[0], ceil + anchor, p[1], p[0] + b.range(-1, 1), ceil - b.range(2, 5), p[1] + b.range(-1, 1),
                    b.range(2.5, 4.5), b.range(1.2, 2.2), true));
        }

        // A collapse: fallen boulders under a fresh scar in the roof.
        if (b.rng.nextFloat() < (r >= 12 ? 0.45f : 0.15f) * intensity)
        {
            double[] p = footprintPoint(b, c, 0.6);
            double ceil = c.ceilingAt(p[0], p[1]), floor = c.floorAt(p[0], p[1]);
            if (!Double.isNaN(ceil) && !Double.isNaN(floor) && ceil - floor >= 6)
            {
                double spread = Math.min(8, r * 0.4);
                int boulders = b.range(4, 9);
                for (int k = 0; k < boulders; k++)
                {
                    double a = b.rng.nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng.nextDouble()) * spread;
                    double br = b.range(1.1, k == 0 ? 3.4 : 2.4);
                    double bx = p[0] + Math.cos(a) * d, bz = p[1] + Math.sin(a) * d;
                    double bf = c.floorAt(bx, bz);
                    if (Double.isNaN(bf)) bf = floor;
                    b.add(new CaveShape.Ellipsoid(fs, CaveShape.Kind.FILL, null, null, true, bx, bf + br * 0.2, bz, br, br * b.range(0.7, 1.0), br, Double.NaN));
                    // Root into the floor so no boulder floats where the floor dips.
                    b.add(fill(fs, bx, bf - anchor, bz, bx, bf, bz, br * 0.6, br * 0.8, false));
                }
                if (c.space().role == CaveSpace.Role.CHAMBER && !c.space().hasLake())
                {
                    b.add(new CaveShape.Ellipsoid(c.space(), CaveShape.Kind.CARVE, null, null, true, p[0], ceil, p[1],
                            spread * 0.8, b.range(2.0, 3.5), spread * 0.8, Double.NaN));
                }
                b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.COLLAPSE, p[0], floor, p[1], spread + 4, c.space()));
            }
        }
    }

    /** A tapering spike (giant stalactite or stalagmite) in three slightly bent segments, ending in speleothem tips. */
    private static void spike(CaveBuilder b, CaveSpace fs, double x, double rootY, double z, double tipY, double baseR, Direction tip)
    {
        double x0 = x, y0 = rootY, z0 = z, r0 = baseR * 1.2;
        for (int seg = 1; seg <= 3; seg++)
        {
            double t = seg / 3.0;
            double x1 = x + b.range(-0.7, 0.7) * t, z1 = z + b.range(-0.7, 0.7) * t;
            double y1 = Mth.lerp(t, rootY, tipY);
            double r1 = seg == 3 ? 0.45 : baseR * (1.0 - t * 0.55);
            CaveShape.Capsule capsule = new CaveShape.Capsule(fs, CaveShape.Kind.FILL, null, null, false, x0, y0, z0, x1, y1, z1, r0, r1, 1.0);
            capsule.tip = tip;
            b.add(capsule);
            x0 = x1;
            y0 = y1;
            z0 = z1;
            r0 = r1;
        }
    }

    private static CaveShape.Capsule fill(CaveSpace fs, double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, boolean noisy)
    {
        return new CaveShape.Capsule(fs, CaveShape.Kind.FILL, null, null, noisy, ax, ay, az, bx, by, bz, ra, rb, 1.0);
    }

    private static double[] footprintPoint(CaveBuilder b, Chamber c, double extent)
    {
        double a = b.rng.nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng.nextDouble()) * extent;
        return new double[] {c.x() + Math.cos(a) * c.rx() * d, c.z() + Math.sin(a) * c.rz() * d};
    }

    /**
     * Large crystals: tilted, tapering columns of crystal rising from the floor or jutting from the walls, crisp
     * rather than noise-warped.
     */
    static void crystals(CaveBuilder b, Chamber c, int count, double scale)
    {
        CaveSpace fs = b.formationSpace(c.space());
        BlockState crystal = ModBlocks.DEEP_CRYSTAL_BLOCK.get().defaultBlockState();
        for (int i = 0; i < count; i++)
        {
            boolean wall = b.rng.nextFloat() < 0.35f;
            double x, y, z, dx, dy, dz;
            if (wall)
            {
                double a = b.rng.nextDouble() * Math.PI * 2;
                x = c.x() + Math.cos(a) * c.rx() * 0.98;
                z = c.z() + Math.sin(a) * c.rz() * 0.98;
                double floor = c.floorAt(c.x(), c.z()), ceil = c.ceilingAt(c.x(), c.z());
                if (Double.isNaN(floor) || Double.isNaN(ceil)) continue;
                y = Mth.lerp(b.range(0.2, 0.7), floor, ceil);
                dx = -Math.cos(a);
                dz = -Math.sin(a);
                dy = b.range(-0.2, 0.6);
            }
            else
            {
                double[] p = footprintPoint(b, c, 0.85);
                x = p[0];
                z = p[1];
                double floor = c.floorAt(x, z);
                if (Double.isNaN(floor)) continue;
                y = floor - 2;
                dx = b.range(-0.6, 0.6);
                dz = b.range(-0.6, 0.6);
                dy = 1;
            }
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double length = Math.min(c.radius() * 0.6, b.range(5, 16) * scale);
            double width = Mth.clamp(length * b.range(0.1, 0.16), 0.9, 3.2);
            double ex = x + dx / len * length, ey = y + dy / len * length, ez = z + dz / len * length;
            b.add(new CaveShape.Capsule(fs, CaveShape.Kind.FILL, crystal, null, false, x, y, z, Mth.lerp(0.6, x, ex), Mth.lerp(0.6, y, ey),
                    Mth.lerp(0.6, z, ez), width, width * 0.8, 1.0));
            b.add(new CaveShape.Capsule(fs, CaveShape.Kind.FILL, crystal, null, false, Mth.lerp(0.6, x, ex), Mth.lerp(0.6, y, ey),
                    Mth.lerp(0.6, z, ez), ex, ey, ez, width * 0.8, 0.35, 1.0));
        }
    }

    private enum OreShape { CLUSTER, VEIN, LAYER, BRANCH, RIBBON, IRREGULAR }

    /**
     * Ore bodies centred on the chamber wall, so the wall cuts through them and leaves their cores exposed. Six
     * shapes: compact clusters, thin wandering veins, flat layers along the strata, branching veins, twisted
     * ribbons and irregular masses. Ore replaces rock only; host rock rims the body.
     */
    static void oreBodies(CaveBuilder b, Chamber c, int count, double scale)
    {
        if (b.profile.ores.isEmpty()) return;
        CaveSpace fs = b.formationSpace(c.space());
        BlockState host = ModBlocks.MINERAL_CAVE_ROCK.get().defaultBlockState();
        double floor0 = c.floorAt(c.x(), c.z()), ceil0 = c.ceilingAt(c.x(), c.z());
        if (Double.isNaN(floor0) || Double.isNaN(ceil0)) return;
        for (int i = 0; i < count; i++)
        {
            BlockState ore = b.profile.ores.pick(b.rng.nextDouble());
            double a = b.rng.nextDouble() * Math.PI * 2;
            double wx = c.x() + Math.cos(a) * c.rx() * b.range(0.85, 1.0), wz = c.z() + Math.sin(a) * c.rz() * b.range(0.85, 1.0);
            double wy = Mth.lerp(b.range(0.15, 0.8), floor0, ceil0);
            double tx = -Math.sin(a), tz = Math.cos(a);
            OreShape shape = OreShape.values()[b.rng.nextInt(OreShape.values().length)];
            switch (shape)
            {
                case CLUSTER -> b.add(new CaveShape.Ellipsoid(fs, CaveShape.Kind.ORE, ore, host, true, wx, wy, wz,
                        b.range(2.5, 5.0) * scale, b.range(2.0, 4.0) * scale, b.range(2.5, 5.0) * scale, Double.NaN));
                case VEIN -> vein(b, fs, ore, host, wx, wy, wz, tx, tz, b.range(14, 28) * scale, b.range(0.9, 1.6), 1.0);
                case LAYER ->
                {
                    double len = b.range(8, 15) * scale;
                    b.add(new CaveShape.Capsule(fs, CaveShape.Kind.ORE, ore, host, true, wx - tx * len, wy, wz - tz * len, wx + tx * len,
                            wy + b.range(-1.5, 1.5), wz + tz * len, b.range(3.0, 5.0), b.range(3.0, 5.0), 0.3));
                }
                case BRANCH ->
                {
                    List<Vec3> trunk = vein(b, fs, ore, host, wx, wy, wz, tx, tz, b.range(16, 26) * scale, b.range(1.0, 1.6), 1.0);
                    for (int k = 0; k < 2 && trunk.size() > 2; k++)
                    {
                        Vec3 from = trunk.get(1 + b.rng.nextInt(trunk.size() - 2));
                        double ba = a + (b.rng.nextBoolean() ? 1 : -1) * b.range(0.5, 1.2);
                        vein(b, fs, ore, host, from.x, from.y + b.range(-3, 3), from.z, -Math.sin(ba), Math.cos(ba), b.range(8, 14) * scale, b.range(0.7, 1.1), 1.0);
                    }
                }
                case RIBBON -> vein(b, fs, ore, host, wx, wy, wz, tx, tz, b.range(18, 30) * scale, b.range(2.0, 3.0), 0.35);
                case IRREGULAR ->
                {
                    int blobs = b.range(3, 5);
                    for (int k = 0; k < blobs; k++)
                    {
                        double br = b.range(1.8, 3.5) * scale;
                        b.add(new CaveShape.Ellipsoid(fs, CaveShape.Kind.ORE, ore, host, true, wx + b.range(-4, 4) * scale, wy + b.range(-3, 3),
                                wz + b.range(-4, 4) * scale, br, br * b.range(0.6, 1.1), br, Double.NaN));
                    }
                }
            }
        }
    }

    /** A wandering vein centred on (x, y, z) running along (tx, tz); returns its path. */
    private static List<Vec3> vein(CaveBuilder b, CaveSpace fs, BlockState ore, BlockState host, double x, double y, double z,
                                   double tx, double tz, double length, double radius, double vertical)
    {
        int salt = b.nextSalt();
        int n = Math.max(3, Mth.ceil(length / 5));
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= n; i++)
        {
            double t = i / (double) n - 0.5;
            double wobble = b.noises.path(t * 2.5, salt) * length * 0.12;
            points.add(new Vec3(x + tx * t * length - tz * wobble * 0.4, y + b.noises.path(t * 2.5, salt + 0.5) * length * 0.18,
                    z + tz * t * length + tx * wobble * 0.4));
        }
        for (int i = 0; i < n; i++)
        {
            Vec3 p = points.get(i), q = points.get(i + 1);
            b.add(new CaveShape.Capsule(fs, CaveShape.Kind.ORE, ore, host, false, p.x, p.y, p.z, q.x, q.y, q.z, radius, radius, vertical));
        }
        return points;
    }

    /** Hydrothermal vents on the chamber floor; the cathedral's are taller and hotter. */
    static void vents(CaveBuilder b, Chamber c, int count, boolean cathedral)
    {
        for (int i = 0; i < count; i++)
        {
            double[] p = footprintPoint(b, c, 0.75);
            double floor = c.floorAt(p[0], p[1]);
            if (Double.isNaN(floor)) continue;
            float r = b.rng.nextFloat();
            ThermalVentType type = r < 0.38f ? ThermalVentType.BLACK_SMOKER : r < 0.7f ? ThermalVentType.WHITE_SMOKER
                    : r < 0.92f || !cathedral ? ThermalVentType.MINERAL : ThermalVentType.SUPERHEATED;
            float a = b.rng.nextFloat();
            VentActivity activity = type == ThermalVentType.SUPERHEATED ? VentActivity.SUPERHEATED
                    : a < 0.15f ? VentActivity.DORMANT : a < 0.35f ? VentActivity.WEAK : a < 0.8f ? VentActivity.ACTIVE : VentActivity.STRONG;
            int height = cathedral ? b.range(3, 9) : b.range(1, 5);
            b.vents.add(new CaveSystem.Vent(Mth.floor(p[0]), Mth.floor(p[1]), Mth.floor(floor), height, type, activity, c.space()));
        }
    }
}
