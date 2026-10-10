package com.abyssia.worldgen.cave;

import com.abyssia.Config;
import com.abyssia.worldgen.DepthBand;
import com.abyssia.worldgen.cave.CaveChamberGenerator.Chamber;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Builds cave systems: what each grid cell holds and how its parts hang together.
 * <p>
 * Hierarchy: a system has a main cave (its hub chamber) reached from the seabed by an entrance and a narrow
 * tunnel; branches lead off it to side caves (often holding ore), deeper chambers (mineral, crystal, or an
 * underground lake), skylight shafts back up to the seabed, and thermal tunnels to thermal caverns; branches may
 * branch once more. Rare landmark systems become a whole expedition route ending at the landmark. Connector tunnels
 * join each system to its east and south neighbours, so caves form a network rather than isolated holes.
 * <p>
 * A light {@link Plan} (type, size, hub position) is computed first and cached separately, so a system can aim a
 * connector at its neighbour's hub without building the neighbour.
 */
final class CaveNetworkGenerator
{
    private static final long PLAN_SALT = 0x63617665506CL;
    private static final long BUILD_SALT = 0x6275696C64L;
    private static final long LINK_SALT = 0x6C696E6B73L;
    private static final long MINOR_SALT = 0x6D696E6F72L;
    private static final long VLINK_SALT = 0x766C696E6BL;

    private CaveNetworkGenerator() {}

    /** Where a system's hub is and what it is; enough for neighbours to connect to it. */
    /**
     * {@code hall} (AB03): the hub is a hall; then {@code y} is its reference level (basin below, terraces above) and
     * {@code radius * vertical} the height of its vault above it.
     */
    record Plan(int cellX, int cellZ, long seed, CaveProfile profile, CaveType type, @Nullable CaveLandmark landmark, ResourceLocation environment,
                double x, double y, double z, double radius, double vertical, int waterLevel, boolean grand, boolean hall)
    {
        /** Where tunnels meet the hub: below the water of a lake, a little under the centre otherwise. */
        double portY()
        {
            if (hall) return y + 3;
            double ry = Math.max(2.5, radius * vertical);
            return waterLevel != CaveSpace.NO_WATER_LEVEL ? Math.min(y, waterLevel - 5) : y - ry * 0.2;
        }
    }

    /** Random stream of a cell: the window's salt (0 for the shallow network) keeps every window's streams apart. */
    private static long cellSeed(CaveNetwork net, long salt, int cellX, int cellZ)
    {
        return RandomSupport.mixStafford13(net.seed() ^ net.salt() ^ salt ^ cellX * 0x632BE59BD9B4E019L ^ cellZ * 0x9E3779B97F4A7C15L);
    }

    // ---------------------------------------------------------------- plans

    @Nullable
    static Plan plan(CaveNetwork net, int cellX, int cellZ)
    {
        if (net.isWindow()) return planWindow(net, cellX, cellZ);
        long seed = cellSeed(net, PLAN_SALT, cellX, cellZ);
        RandomSource rng = new XoroshiroRandomSource(seed);
        int cell = net.cellSize(), margin = cell / 6;
        int ax = cellX * cell + margin + rng.nextInt(cell - 2 * margin);
        int az = cellZ * cell + margin + rng.nextInt(cell - 2 * margin);
        CaveProfile profile = net.profile(ax, az);
        if (profile == null || rng.nextFloat() >= profile.systemChance * Config.CAVE_SYSTEM_CHANCE.get()) return null;
        if (net.seabed(ax, az) < net.minY() + 28) return null;

        CaveLandmark landmark = null;
        if (!profile.landmarks.isEmpty() && rng.nextFloat() < profile.landmarkChance * Config.LANDMARK_CHANCE.get())
        {
            landmark = profile.landmarks.pick(rng.nextDouble());
        }
        CaveType type = landmark != null ? (landmark.hasLake() ? CaveType.UNDERGROUND_SEA : CaveType.MASSIVE_CAVERN) : net.caveTypes(profile).pick(rng.nextDouble());
        if (type == null || type == CaveType.MEGA_CAVERN || type == CaveType.ABYSS_CAVITY) return null;  // mega caverns and abyss cavities only exist in the crust windows
        boolean lake = Config.UNDERGROUND_LAKES.get() && (type == CaveType.UNDERGROUND_SEA || (landmark != null && landmark.hasLake()));
        if (type == CaveType.UNDERGROUND_SEA && !lake) type = CaveType.MASSIVE_CAVERN;

        // Regional cave size noise: some stretches of seabed, several hundred blocks across, hide bigger caves than others.
        double sizeBias = 0.85 + 0.3 * (net.noises().field2(ax * 0.0015, az * 0.0015, 33) * 0.5 + 0.5);
        double radius = landmark != null ? Mth.lerp(rng.nextDouble(), landmark.minRadius, landmark.maxRadius)
                : Mth.lerp(rng.nextDouble(), type.minRadius, type.maxRadius) * sizeBias;
        double vertical = landmark != null ? landmark.vertical : type.vertical;
        double x = ax + (rng.nextDouble() - 0.5) * cell * 0.15, z = az + (rng.nextDouble() - 0.5) * cell * 0.15;

        // Fit under the seabed, shrinking where the sea floor is too low; a landmark that cannot fit is given up.
        boolean hall = landmark == null && CaveChamberGenerator.hallType(type);
        double hallFactor = hall ? 0.9 + 0.2 * rng.nextDouble() : 1;
        double y = Double.NaN;
        for (int attempt = 0; attempt < 8 && Double.isNaN(y); attempt++)
        {
            double ry = Math.max(2.5, radius * vertical);
            double cover = 8 + rng.nextDouble() * (12 + radius * 0.35);
            if (type == CaveType.VERTICAL_SHAFT) cover += 30 + rng.nextDouble() * 70;
            if (hall && radius >= CavernPlanner.MIN_RADIUS && CaveChamberGenerator.hallType(type))
            {
                // A hall: its reference level, low enough that the vault stays under the seabed.
                double height = CaveChamberGenerator.hallHeight(radius) * hallFactor;
                double top = net.lowestSeabed(x, z, radius * 1.3) - cover - CaveChamberGenerator.hallAbove(height, radius);
                double bottom = net.minY() + 12 + CaveChamberGenerator.hallBelow(radius);
                y = top >= bottom ? Math.floor(top - rng.nextDouble() * Math.min(16, top - bottom)) : Double.NaN;
            }
            else if (lake)
            {
                y = CaveChamberGenerator.lakeY(net, x, z, radius, ry, net.lowestSeabed(x, z, radius) - cover - ry);
            }
            else
            {
                double top = net.lowestSeabed(x, z, radius) - cover - ry, bottom = net.minY() + 12 + ry;
                if (type == CaveType.VERTICAL_SHAFT) top = Math.max(top, Math.min(bottom + 4, net.seabed(x, z) - 24.0 - ry));
                y = top >= bottom ? top - rng.nextDouble() * Math.min(16, top - bottom) : Double.NaN;
            }
            if (!Double.isNaN(y)) break;
            radius *= 0.8;
            if (radius < Math.max(3, type.minRadius * 0.55))
            {
                if (landmark != null || type.size.ordinal() >= CaveType.Size.LARGE.ordinal())
                {
                    landmark = null;
                    lake = false;
                    type = CaveType.MEDIUM_SEA_CAVE;
                    radius = Mth.lerp(rng.nextDouble(), 8, 12);
                    vertical = type.vertical;
                }
                else
                {
                    type = CaveType.SMALL_SEA_CAVE;
                    radius = 4;
                    vertical = type.vertical;
                }
            }
        }
        if (Double.isNaN(y)) return null;

        String envName = landmark != null ? landmark.environment : type.environment;
        ResourceLocation env = envName != null ? net.environmentId(envName, profile) : profile.environment;
        if (envName == null && net.luminous(profile, Mth.floor(x), Mth.floor(z))) env = net.environmentId("luminous", profile);
        hall = hall && radius >= CavernPlanner.MIN_RADIUS && CaveChamberGenerator.hallType(type);
        if (hall) vertical = CaveChamberGenerator.hallHeight(radius) * hallFactor / radius;
        int waterLevel = lake && !hall ? Mth.floor(y + Math.max(2.5, radius * vertical) * Mth.lerp(rng.nextDouble(), 0.1, 0.35)) : CaveSpace.NO_WATER_LEVEL;
        boolean grand = landmark != null || (type.size.ordinal() >= CaveType.Size.LARGE.ordinal() && type != CaveType.UNDERGROUND_SEA && rng.nextFloat() < 0.25f);
        return new Plan(cellX, cellZ, seed, profile, type, landmark, env, x, y, z, radius, vertical, waterLevel, grand, hall);
    }

    // ---------------------------------------------------------------- window plans

    /** Vertical room (blocks above / below the centre) a system of this type and radius needs inside its window. */
    private static double[] verticalNeed(CaveType type, double radius, double ry, boolean lake)
    {
        if (type == CaveType.MEGA_CAVERN) return new double[] {radius * 1.05 + 14, radius * 0.5 + 14};
        if (lake) return new double[] {ry * 1.35 + 14, ry * 1.2 + 10};
        return new double[] {ry * 1.5 + 14, ry * 1.5 + 14};
    }

    /**
     * A free-floating system of a crust window: its height is drawn first (one draw places it in the window whatever it
     * turns out to be), the biome and so the profile and environment are read at that very spot, and the hub is
     * squeezed into the room its size needs (or the system shrinks until it fits). No seabed is involved.
     */
    @Nullable
    private static Plan planWindow(CaveNetwork net, int cellX, int cellZ)
    {
        // AB06: the cell of an abyss cavity holds that cavity (decided from the super cell alone, so nothing recurses).
        AbyssCavity.Site cavity = AbyssCavity.inCell(net, cellX, cellZ);
        if (cavity != null) return cavityPlan(net, cavity);
        long seed = cellSeed(net, PLAN_SALT, cellX, cellZ);
        RandomSource rng = new XoroshiroRandomSource(seed);
        int index = net.band().ordinal();
        int cell = net.cellSize(), margin = cell / 6;
        int ax = cellX * cell + margin + rng.nextInt(cell - 2 * margin);
        int az = cellZ * cell + margin + rng.nextInt(cell - 2 * margin);
        double yFrac = rng.nextDouble();
        CaveProfile profile = net.profileAt(ax, net.minY() + (int) (yFrac * (net.maxY() - net.minY())), az);
        if (profile == null) return null;
        if (rng.nextFloat() >= profile.systemChance * Config.CAVE_SYSTEM_CHANCE.get() * Config.BAND_SYSTEM_CHANCE[index].get()) return null;

        CaveLandmark landmark = null;
        if (!profile.landmarks.isEmpty() && rng.nextFloat() < profile.landmarkChance * Config.LANDMARK_CHANCE.get())
        {
            landmark = profile.landmarks.pick(rng.nextDouble());
        }
        CaveType type = landmark != null ? (landmark.hasLake() ? CaveType.UNDERGROUND_SEA : CaveType.MASSIVE_CAVERN) : net.caveTypes(profile).pick(rng.nextDouble());
        if (type == null || !net.allows(type)) return null;
        boolean lake = Config.UNDERGROUND_LAKES.get() && (type == CaveType.UNDERGROUND_SEA || (landmark != null && landmark.hasLake()));
        if (type == CaveType.UNDERGROUND_SEA && !lake) type = CaveType.MASSIVE_CAVERN;

        double sizeBias = 0.85 + 0.3 * (net.noises().field2(ax * 0.0015, az * 0.0015, 33) * 0.5 + 0.5);
        double sizeMultiplier = Config.BAND_SIZE[index].get();
        double radius = landmark != null ? Mth.lerp(rng.nextDouble(), landmark.minRadius, landmark.maxRadius)
                : Mth.lerp(rng.nextDouble(), type.minRadius, type.maxRadius) * sizeBias * sizeMultiplier;
        if (type == CaveType.MEGA_CAVERN) radius = Mth.clamp(radius, type.minRadius, type.maxRadius);
        double vertical = landmark != null ? landmark.vertical : type.vertical;
        double x = ax + (rng.nextDouble() - 0.5) * cell * 0.15, z = az + (rng.nextDouble() - 0.5) * cell * 0.15;
        if (AbyssCavity.suppresses(net, x, z)) return null;  // AB06: no ordinary hub inside a cavity or its 60 block rim
        boolean hall = landmark == null && CaveChamberGenerator.hallType(type);
        double hallFactor = hall ? 0.9 + 0.2 * rng.nextDouble() : 1;

        double y = Double.NaN;
        for (int attempt = 0; attempt < 10 && Double.isNaN(y); attempt++)
        {
            double ry = Math.max(2.5, radius * vertical);
            // A hall: y is its reference level, the vault above it, the basin below.
            boolean asHall = hall && radius >= CavernPlanner.MIN_RADIUS && CaveChamberGenerator.hallType(type);
            double[] need = asHall ? new double[] {CaveChamberGenerator.hallAbove(CaveChamberGenerator.hallHeight(radius) * hallFactor, radius),
                    CaveChamberGenerator.hallBelow(radius)} : verticalNeed(type, radius, ry, lake);
            double lo = net.minY() + 12 + need[1], hi = net.maxY() - 12 - need[0];
            if (hi >= lo)
            {
                y = lo + yFrac * (hi - lo);
                if (asHall) y = Math.floor(y);
                break;
            }
            radius *= 0.85;
            if (type == CaveType.MEGA_CAVERN && radius < type.minRadius * 0.85)
            {
                type = CaveType.MASSIVE_CAVERN;
                radius = Math.min(radius, 60);
                vertical = type.vertical;
            }
            else if (type != CaveType.MEGA_CAVERN && radius < Math.max(3, type.minRadius * 0.55))
            {
                if (landmark != null || type.size.ordinal() >= CaveType.Size.LARGE.ordinal())
                {
                    landmark = null;
                    lake = false;
                    type = CaveType.MEDIUM_SEA_CAVE;
                    radius = Mth.lerp(rng.nextDouble(), 8, 12);
                    vertical = type.vertical;
                }
                else
                {
                    type = CaveType.SMALL_SEA_CAVE;
                    radius = 4;
                    vertical = type.vertical;
                }
            }
        }
        if (Double.isNaN(y)) return null;

        // The environment is the one of the biome at the system's own spot (it may differ from the biome at the drawn height).
        CaveProfile here = net.profileAt(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (here != null) profile = here;
        String envName = landmark != null ? landmark.environment : type.environment;
        ResourceLocation env = envName != null ? net.environmentId(envName, profile) : profile.environment;
        if (envName == null && net.luminous(profile, Mth.floor(x), Mth.floor(z))) env = net.environmentId("luminous", profile);
        hall = hall && radius >= CavernPlanner.MIN_RADIUS && CaveChamberGenerator.hallType(type);
        if (hall)
        {
            y = Math.floor(y);
            vertical = CaveChamberGenerator.hallHeight(radius) * hallFactor / radius;
        }
        int waterLevel = lake && !hall ? Mth.floor(y + Math.max(2.5, radius * vertical) * Mth.lerp(rng.nextDouble(), 0.1, 0.35)) : CaveSpace.NO_WATER_LEVEL;
        boolean grand = landmark != null || (type.size.ordinal() >= CaveType.Size.LARGE.ordinal() && type.size != CaveType.Size.MEGA
                && type != CaveType.UNDERGROUND_SEA && rng.nextFloat() < 0.25f);
        return new Plan(cellX, cellZ, seed, profile, type, landmark, env, x, y, z, radius, vertical, waterLevel, grand, hall);
    }

    /** AB06: the plan of an abyss cavity: a hall hub whose vault height was fitted to the window. */
    private static Plan cavityPlan(CaveNetwork net, AbyssCavity.Site s)
    {
        return new Plan(s.cellX(), s.cellZ(), cellSeed(net, PLAN_SALT, s.cellX(), s.cellZ()), s.profile(), CaveType.ABYSS_CAVITY, null, s.environment(),
                s.x(), s.y(), s.z(), s.radius(), s.height() / s.radius(), CaveSpace.NO_WATER_LEVEL, false, true);
    }

    // ---------------------------------------------------------------- systems

    static CaveSystem build(CaveNetwork net, int cellX, int cellZ)
    {
        Plan p = net.plan(cellX, cellZ);
        if (p == null) return empty(net, cellX, cellZ, false);
        CaveBuilder b = new CaveBuilder(net, p.seed() ^ BUILD_SALT, p.profile(), Mth.floor(p.x()), Mth.floor(p.z()));
        if (p.grand()) grandRoute(b, p);
        else
        {
            switch (p.type())
            {
                case SMALL_SEA_CAVE -> small(b, p);
                case LARGE_ABYSSAL_CAVE -> large(b, p, 3, 4, 0.5);
                case MASSIVE_CAVERN -> massive(b, p);
                case MEGA_CAVERN ->
                {
                    if (p.hall()) megaHall(b, p);
                    else mega(b, p);
                }
                case ABYSS_CAVITY -> cavity(b, p);
                case SEA_TUNNEL -> seaTunnel(b, p);
                case VERTICAL_SHAFT -> verticalShaft(b, p);
                case TRENCH_CAVE -> trench(b, p);
                case UNDERGROUND_SEA -> undergroundSea(b, p);
                case SEA_ARCH -> arch(b, p);
                default -> medium(b, p);
            }
        }
        connectors(b, p);
        return b.finish(cellX, cellZ, false, p.type(), p.landmark(), p.x(), p.y(), p.z());
    }

    private static CaveSystem empty(CaveNetwork net, int cellX, int cellZ, boolean minor)
    {
        return new CaveSystem(cellX, cellZ, minor, CaveType.SMALL_SEA_CAVE, null, List.of(), List.of(), List.of(), List.of(), "", 0, 0, 0, net.band());
    }

    /** Windows have no minor caves (they are seabed-bound). */
    static CaveSystem noMinor(CaveNetwork net, int cellX, int cellZ)
    {
        return empty(net, cellX, cellZ, true);
    }

    /** The system's main chamber at its hub. */
    private static Chamber hub(CaveBuilder b, Plan p, int lobes, boolean flatFloor)
    {
        if (p.hall()) return hallHub(b, p);
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, p.type(), p.landmark(), p.environment(), p.x(), p.y(), p.z(), p.radius(), p.waterLevel());
        Chamber c = CaveChamberGenerator.chamber(b, space, p.x(), p.y(), p.z(), p.radius(), p.vertical(), lobes, flatFloor && !space.hasLake());
        if (space.hasLake()) CaveChamberGenerator.islands(b, c);
        CaveChamberGenerator.furnish(b, c);
        b.route.add(p.landmark() != null ? p.landmark().getSerializedName() : describe(c));
        return c;
    }

    /** AB03: the hub of a large, massive or mega cavern: a hall (domed vault, terraced basin, columns; see {@link CaveShape.Hall}). */
    private static Chamber hallHub(CaveBuilder b, Plan p)
    {
        CaveSpace space = b.hallSpace(p.type(), p.landmark(), p.environment(), p.x(), p.y(), p.z(), p.radius());
        Chamber c = CaveChamberGenerator.hall(b, space, p.x(), p.z(), p.radius(), Mth.floor(p.y()), p.radius() * p.vertical(), b.rng.nextDouble() * Math.PI * 2);
        CaveChamberGenerator.furnish(b, c);
        b.route.add(describe(c));
        return c;
    }

    /**
     * AB03: a mega cavern whose main hall is one immense vault (r 64-128), with a few satellite halls around it (each joined to it
     * by a wide passage) and the usual branches.
     */
    private static void megaHall(CaveBuilder b, Plan p)
    {
        Chamber hub = hallHub(b, p);
        int rooms = b.range(2, 4);
        double base = b.rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < rooms; i++) satelliteHall(b, p, hub, base + i * Math.PI * 2 / rooms + (b.rng.nextDouble() - 0.5) * 0.6);
        branches(b, p, hub, b.range(2, 3), 0);
    }

    /**
     * AB06: an abyss cavity: one immense water-filled hall (no satellite halls) with the usual branches. The topmost window's
     * cavities also get a descent route up to the deep seabed; the lower windows' ones link up to the window above in
     * {@link #verticalLink}.
     */
    private static void cavity(CaveBuilder b, Plan p)
    {
        Chamber hub = hallHub(b, p);
        branches(b, p, hub, b.range(2, 3), 0);
        if (b.net.band() == DepthBand.B) seabedRoute(b, p, hub);
    }

    /**
     * AB06: from the cavity's wall up to an opening in the deep seabed R+80..R+160 blocks from its centre: 3-4 switchback legs
     * (more where the seabed is high above) of water-filled tunnel, a small room at every turn, ending in a wide funnel that
     * reaches 4 blocks over the seabed. Every point stays within about 400 blocks of the hub, inside the system's reach.
     */
    private static void seabedRoute(CaveBuilder b, Plan p, Chamber hub)
    {
        CaveNetwork root = b.net.root();
        if (root == null) return;
        double base = b.rng.nextDouble() * Math.PI * 2, angle = base, ex = 0, ez = 0;
        int sy = 0;
        boolean found = false;
        for (int attempt = 0; attempt < 24 && !found; attempt++)
        {
            angle = base + attempt * 2.39996;
            double d = p.radius() + b.range(80, 160);
            ex = p.x() + Math.cos(angle) * d;
            ez = p.z() + Math.sin(angle) * d;
            sy = root.seabed(ex, ez);
            found = sy <= root.maxEntranceY() && sy >= root.minY() + 12;
        }
        if (!found)
        {
            b.route.add("descent route: no seabed opening");
            return;
        }
        Vec3 start = hub.port(angle, 1.0), top = new Vec3(ex, sy - 14, ez);
        double dy = top.y - start.y;
        int legs = Mth.clamp(Math.max(b.range(3, 4), Mth.ceil(dy / 80)), 3, 7);
        double hx = top.x - start.x, hz = top.z - start.z, hl = Math.max(1, Math.hypot(hx, hz));
        double px = -hz / hl, pz = hx / hl, sign = b.rng.nextBoolean() ? 1 : -1;
        ResourceLocation env = hub.space().environmentId;
        Vec3 prev = start;
        for (int i = 1; i <= legs; i++)
        {
            Vec3 next = top;
            if (i < legs)
            {
                // A turn of the switchback: the way swings to alternate sides of the straight line, with a small room at the bend.
                double t = i / (double) legs, off = sign * hl * b.range(0.14, 0.22), r = b.range(8, 12);
                sign = -sign;
                next = new Vec3(start.x + hx * t + px * off, start.y + dy * t, start.z + hz * t + pz * off);
                // Only the final funnel may reach the seabed: keep the offset rooms and legs clear below it.
                next = new Vec3(next.x, Math.min(next.y, root.seabed(next.x, next.z) - 18), next.z);
                CaveSpace room = b.space(CaveSpace.Role.CHAMBER, CaveType.MEDIUM_SEA_CAVE, null, env, next.x, next.y, next.z, r, CaveSpace.NO_WATER_LEVEL);
                Chamber c = CaveChamberGenerator.chamber(b, room, next.x, next.y, next.z, r, 0.75, b.range(1, 2), false);
                if (next.y + r < b.net.maxY() - 12) CaveChamberGenerator.furnish(b, c);
            }
            double tr = b.range(6.0, 8.0);
            CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, env, tr), prev, next, tr, tr, 0.5, 0.9);
            prev = next;
        }
        CaveSpace mouth = b.space(CaveSpace.Role.ENTRANCE, CaveType.MEDIUM_SEA_CAVE, null, env, ex, sy, ez, 10, CaveSpace.NO_WATER_LEVEL);
        b.add(new CaveShape.Capsule(mouth, CaveShape.Kind.CARVE, null, null, true, ex, sy - 14, ez, ex, sy + 4, ez, 7, 10, 1.0));
        b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ENTRANCE, ex, sy, ez, 22, mouth));
        b.route.add("descent route");
        b.route.add("seabed entrance@" + Mth.floor(ex) + " " + sy + " " + Mth.floor(ez) + " (" + legs + " legs)");
    }

    /** A hall (or, too small for one, a plain chamber) beside a mega hall, clear of its outline, joined by a wide passage. */
    private static void satelliteHall(CaveBuilder b, Plan p, Chamber hub, double angle)
    {
        double r = p.radius() * b.range(0.2, 0.32);
        double reach = Math.max(hub.rx(), hub.rz()) * 1.2;
        double dist = reach + r * 1.2 + b.range(10, 22);
        double x = hub.x() + Math.cos(angle) * dist, z = hub.z() + Math.sin(angle) * dist;
        Chamber c;
        if (r >= CavernPlanner.MIN_RADIUS)
        {
            double height = CaveChamberGenerator.hallHeight(r) * b.range(0.85, 1.1);
            double lo = b.minCarveY() + CaveChamberGenerator.hallBelow(r) + 4, hi = b.net.maxY() - 12 - CaveChamberGenerator.hallAbove(height, r);
            if (hi < lo) return;
            int level = Mth.floor(Mth.clamp(hub.y() + b.range(-14, 14), lo, hi));
            CaveType type = r >= 32 ? CaveType.MASSIVE_CAVERN : CaveType.LARGE_ABYSSAL_CAVE;
            CaveSpace space = b.hallSpace(type, null, hub.space().environmentId, x, level, z, r);
            c = CaveChamberGenerator.hall(b, space, x, z, r, level, height, angle + Math.PI);
        }
        else
        {
            double ry = Math.max(2.5, r * 0.62);
            double lo = b.minCarveY() + ry * 1.6 + 14, hi = b.net.maxY() - 12 - ry * 1.6 - 14;
            if (hi < lo) return;
            double y = Mth.clamp(hub.y() + b.range(-6, 10), lo, hi);
            CaveSpace space = b.space(CaveSpace.Role.CHAMBER, CaveType.MASSIVE_CAVERN, null, hub.space().environmentId, x, y, z, r, CaveSpace.NO_WATER_LEVEL);
            c = CaveChamberGenerator.chamber(b, space, x, y, z, r, 0.62, b.range(3, 5), b.rng.nextFloat() < 0.7f, angle + Math.PI);
        }
        CaveChamberGenerator.furnish(b, c);
        double tr = b.range(5.0, 8.5);
        CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, hub.space().environmentId, tr), hub.port(angle, 0), c.port(angle + Math.PI, 0), tr, tr, 0.8, 0.9);
        b.route.add("hall > " + describe(c));
    }

    private static String describe(Chamber c)
    {
        String env = c.space().environmentId.getPath();
        String size = c.space().type.size == CaveType.Size.MEGA ? "mega" : c.radius() < 8 ? "small" : c.radius() < 16 ? "medium" : c.radius() < 32 ? "large" : "massive";
        if (c.hall() != null) return size + " " + env + " hall r" + Mth.floor(c.radius()) + " h" + Mth.floor(c.hall().apexY() - c.hall().level);
        return (c.space().hasLake() ? "underground lake" : size + " " + env + " chamber") + " r" + Mth.floor(c.radius());
    }

    private static CaveSpace tunnelSpace(CaveBuilder b, Plan p, ResourceLocation env, double radius)
    {
        CaveType type = p.type() == CaveType.ERODED_CAVE || p.type() == CaveType.TRENCH_CAVE ? p.type() : CaveType.MEDIUM_SEA_CAVE;
        return b.space(CaveSpace.Role.TUNNEL, type, p.landmark(), env, b.anchorX, p.y(), b.anchorZ, radius, CaveSpace.NO_WATER_LEVEL);
    }

    /**
     * An entrance on the seabed beside a chamber, tunnelling down to it. Seabed peaks too close to the dimension's
     * ceiling cannot hold one, so it searches around (golden-angle steps, nearer and farther); if every spot fails it
     * falls back to a skylight shaft straight up from the chamber, so no system is left unreachable.
     */
    @Nullable
    private static Vec3 entranceTo(CaveBuilder b, Plan p, Chamber c, double angle, double distance, double radius, boolean landmark)
    {
        CaveSpace tunnel = tunnelSpace(b, p, c.space().environmentId, radius);
        for (int attempt = 0; attempt < 12; attempt++)
        {
            double a = angle + attempt * 2.39996;
            double d = c.radius() + distance * (attempt < 4 ? 1.0 : attempt < 8 ? 0.4 : 1.8);
            double ex = c.x() + Math.cos(a) * d, ez = c.z() + Math.sin(a) * d;
            int sy = b.net.seabed(ex, ez);
            if (sy > b.net.maxEntranceY() || sy < b.minCarveY() + 12) continue;
            Vec3 throat = CaveEntranceGenerator.entrance(b, tunnel, ex, ez, c.port(a, -0.1), radius, landmark);
            if (throat != null)
            {
                b.route.add(0, radius < 3.3 ? "entrance > narrow tunnel" : "entrance > tunnel");
                return throat;
            }
        }
        if (!c.space().hasLake())
        {
            for (int attempt = 0; attempt < 6; attempt++)
            {
                if (skylight(b, p, c, angle + attempt * 1.05)) return null;
            }
        }
        return null;
    }

    private static void small(CaveBuilder b, Plan p)
    {
        Chamber c = hub(b, p, b.range(1, 2), b.rng.nextBoolean());
        entranceTo(b, p, c, c.front(), b.range(4, 12), b.range(2.0, 3.2), false);
        if (b.rng.nextFloat() < 0.3f) sideCave(b, p, c, b.rng.nextDouble() * Math.PI * 2);
    }

    private static void medium(CaveBuilder b, Plan p)
    {
        Chamber c = hub(b, p, b.range(2, 3), b.rng.nextFloat() < 0.6f);
        double angle = c.front();
        Vec3 throat = entranceTo(b, p, c, angle, b.range(12, 30), b.range(2.5, 4.0), false);
        if (throat != null && p.type() == CaveType.ERODED_CAVE && b.rng.nextFloat() < 0.5f)
        {
            // Long erosion has left an arch standing over the mouth.
            CaveSpace arch = b.space(CaveSpace.Role.ARCH, CaveType.SEA_ARCH, null, p.environment(), throat.x, throat.y, throat.z, 6, CaveSpace.NO_WATER_LEVEL);
            CaveEntranceGenerator.arch(b, arch, throat.x, throat.z, angle + Math.PI / 2, b.range(14, 24), b.range(8, 14), b.range(2.4, 3.4));
        }
        branches(b, p, c, b.range(p.type().minBranches, p.type().maxBranches), 0);
    }

    private static void large(CaveBuilder b, Plan p, int minLobes, int maxLobes, double skylightChance)
    {
        Chamber c = hub(b, p, b.range(minLobes, maxLobes), b.rng.nextFloat() < 0.7f);
        double angle = c.front();
        entranceTo(b, p, c, angle, b.range(18, 40), b.range(3.0, 4.5), b.rng.nextBoolean());
        branches(b, p, c, b.range(p.type().minBranches, p.type().maxBranches), 0);
        if (b.rng.nextDouble() < skylightChance) skylight(b, p, c, angle + Math.PI);
    }

    private static void massive(CaveBuilder b, Plan p)
    {
        Chamber c = hub(b, p, b.range(4, 7), b.rng.nextFloat() < 0.8f);
        double angle = c.front();
        entranceTo(b, p, c, angle, b.range(24, 50), b.range(3.5, 5.0), true);
        if (b.rng.nextBoolean()) entranceTo(b, p, c, angle + Math.PI, b.range(20, 40), b.range(3.0, 4.0), false);
        branches(b, p, c, b.range(2, 4), 0);
        if (b.rng.nextFloat() < 0.6f) skylight(b, p, c, angle + Math.PI / 2);
    }

    /**
     * A mega cavern (crust windows only): a wide, low main hall with tall chimneys and wing halls (see
     * {@link CaveChamberGenerator#megaChamber}), a few satellite rooms joined to it by wide passages, and rifts cut into the
     * floor; the cavern pipeline adds pillars, steps and the rest. No way to the sea: it is reached by connectors and links.
     */
    private static void mega(CaveBuilder b, Plan p)
    {
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, p.type(), p.landmark(), p.environment(), p.x(), p.y(), p.z(), p.radius(), CaveSpace.NO_WATER_LEVEL);
        Chamber hub = CaveChamberGenerator.megaChamber(b, space, p.x(), p.y(), p.z(), p.radius(), p.vertical(), b.rng.nextFloat() < 0.75f);
        CaveChamberGenerator.furnish(b, hub);
        b.route.add(describe(hub));
        int rooms = b.range(2, 4);
        double base = b.rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < rooms; i++) satelliteRoom(b, p, hub, base + i * Math.PI * 2 / rooms + (b.rng.nextDouble() - 0.5) * 0.6);
        int rifts = b.range(1, 3);
        for (int i = 0; i < rifts; i++)
        {
            double a = b.rng.nextDouble() * Math.PI;
            double fx = hub.x() + b.range(-0.5, 0.5) * hub.rx(), fz = hub.z() + b.range(-0.5, 0.5) * hub.rz();
            double floor = hub.floorAt(fx, fz);
            if (Double.isNaN(floor)) continue;
            double half = b.range(15, 35), w = b.range(2.2, 4.0);
            b.add(new CaveShape.Capsule(hub.space(), CaveShape.Kind.CARVE, null, null, true, fx - Math.cos(a) * half, floor - 4, fz - Math.sin(a) * half,
                    fx + Math.cos(a) * half, floor - 4, fz + Math.sin(a) * half, w, w, b.range(4.0, 7.0)));
        }
        if (rifts > 0) b.route.add("floor rifts");
        branches(b, p, hub, b.range(2, 3), 0);
    }

    /** A massive hall beside a mega cavern's main hall, joined to it by a wide passage. */
    private static void satelliteRoom(CaveBuilder b, Plan p, Chamber hub, double angle)
    {
        double r = p.radius() * b.range(0.2, 0.32);
        double dist = p.radius() * b.range(1.0, 1.2);
        double x = hub.x() + Math.cos(angle) * dist, z = hub.z() + Math.sin(angle) * dist;
        double ry = Math.max(2.5, r * 0.62);
        double lo = b.minCarveY() + ry * 1.6 + 14, hi = b.net.maxY() - 12 - ry * 1.6 - 14;
        if (hi < lo) return;
        double y = Mth.clamp(hub.y() + b.range(-0.5, 0.5) * p.radius() * 0.5, lo, hi);
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, CaveType.MASSIVE_CAVERN, null, hub.space().environmentId, x, y, z, r, CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, space, x, y, z, r, 0.62, b.range(3, 5), b.rng.nextFloat() < 0.7f, angle + Math.PI);
        CaveChamberGenerator.furnish(b, c);
        double tr = b.range(5.0, 8.5);
        CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, hub.space().environmentId, tr), hub.port(angle, -0.1), c.port(angle + Math.PI, -0.1), tr, tr, 0.8, 0.9);
        b.route.add("hall > " + describe(c));
    }

    private static void trench(CaveBuilder b, Plan p)
    {
        Chamber c = hub(b, p, b.range(2, 3), false);
        entranceTo(b, p, c, c.front(), b.range(8, 20), b.range(3.0, 4.5), b.rng.nextBoolean());
        int count = b.range(p.type().minBranches, p.type().maxBranches);
        double base = b.rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) deepCave(b, p, c, base + i * Math.PI * 2 / count);
    }

    private static void undergroundSea(CaveBuilder b, Plan p)
    {
        Chamber c = hub(b, p, b.range(5, 7), false);
        double angle = c.front();
        entranceTo(b, p, c, angle, b.range(25, 50), b.range(3.0, 4.5), true);
        branches(b, p, c, b.range(1, 2), 0);
    }

    private static void verticalShaft(CaveBuilder b, Plan p)
    {
        int sy = b.net.seabed(p.x(), p.z());
        double r = p.radius();
        CaveSpace bottom = b.space(CaveSpace.Role.CHAMBER, p.type(), null, p.environment(), p.x(), p.y(), p.z(), r * 1.6, CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, bottom, p.x(), p.y(), p.z(), r * 1.6, 0.6, b.range(2, 3), true);
        CaveChamberGenerator.furnish(b, c);
        if (sy <= b.net.maxEntranceY())
        {
            CaveSpace shaft = b.space(CaveSpace.Role.SHAFT, p.type(), null, p.environment(), p.x(), (sy + p.y()) / 2, p.z(), r, CaveSpace.NO_WATER_LEVEL);
            CaveTunnelGenerator.shaft(b, shaft, p.x(), p.z(), sy + 2, p.y(), r);
            b.add(new CaveShape.Capsule(shaft, CaveShape.Kind.CARVE, null, null, true, p.x(), sy - 4, p.z(), p.x(), sy + 3, p.z(), r, r * 1.7, 1.0));
            b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ENTRANCE, p.x(), sy, p.z(), r * 3 + 8, shaft));
            b.route.add("vertical shaft " + Mth.floor(sy - p.y()) + "m");
        }
        else
        {
            entranceTo(b, p, c, b.rng.nextDouble() * Math.PI * 2, b.range(10, 24), b.range(2.5, 3.5), false);
        }
        b.route.add(describe(c));
        // Vertical Shaft -> Underground Lake, through a flooded U-bend.
        if (!(Config.UNDERGROUND_LAKES.get() && b.rng.nextFloat() < 0.45f && lakeBranch(b, p, c, b.rng.nextDouble() * Math.PI * 2) != null))
        {
            branches(b, p, c, 1, 1);
        }
    }

    private static void seaTunnel(CaveBuilder b, Plan p)
    {
        CaveSpace space = b.space(CaveSpace.Role.TUNNEL, CaveType.SEA_TUNNEL, null, p.environment(), p.x(), p.y(), p.z(), p.radius(), CaveSpace.NO_WATER_LEVEL);
        double angle = b.rng.nextDouble() * Math.PI;
        CaveTunnelGenerator.seaTunnel(b, space, p.x(), p.z(), angle, b.range(90, 190), p.radius());
        // Make sure the hub point neighbours aim their connectors at is open and joined to the tunnel.
        CaveSpace link = tunnelSpace(b, p, p.environment(), 2.8);
        int sy = b.net.seabed(p.x(), p.z());
        Vec3 hubPoint = new Vec3(p.x(), p.portY(), p.z());
        CaveTunnelGenerator.tunnel(b, link, hubPoint, new Vec3(p.x(), Math.min(sy - p.radius() - 3, hubPoint.y + 12), p.z()), 2.8, 3.0, 0.5, 1.0);
        if (b.rng.nextFloat() < 0.4f)
        {
            CaveSpace stub = b.space(CaveSpace.Role.CHAMBER, CaveType.SMALL_SEA_CAVE, null, p.environment(), p.x(), p.portY(), p.z(), 5, CaveSpace.NO_WATER_LEVEL);
            Chamber c = CaveChamberGenerator.chamber(b, stub, p.x(), p.portY(), p.z(), b.range(4, 7), 0.7, 1, true);
            CaveChamberGenerator.furnish(b, c);
        }
    }

    private static void arch(CaveBuilder b, Plan p)
    {
        CaveSpace arch = b.space(CaveSpace.Role.ARCH, CaveType.SEA_ARCH, null, p.environment(), p.x(), p.y(), p.z(), p.radius(), CaveSpace.NO_WATER_LEVEL);
        CaveEntranceGenerator.arch(b, arch, p.x(), p.z(), b.rng.nextDouble() * Math.PI, b.range(18, 34), b.range(10, 22), b.range(2.8, 4.5));
        // A small cave under it; also keeps the hub point open for connectors.
        CaveSpace cave = b.space(CaveSpace.Role.CHAMBER, CaveType.SMALL_SEA_CAVE, null, p.environment(), p.x(), p.y(), p.z(), p.radius(), CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, cave, p.x(), p.y(), p.z(), p.radius(), 0.75, 1, true);
        CaveChamberGenerator.furnish(b, c);
        entranceTo(b, p, c, b.rng.nextDouble() * Math.PI * 2, b.range(3, 8), 2.4, false);
    }

    // ---------------------------------------------------------------- branches

    private enum Branch { SIDE, DEEP, SHAFT, THERMAL }

    /** Side caves, deep tunnels, skylight shafts and thermal tunnels leading off a chamber; may branch once more. */
    private static void branches(CaveBuilder b, Plan p, Chamber from, int count, int level)
    {
        double base = b.rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++)
        {
            double angle = base + i * Math.PI * 2 / Math.max(1, count) + (b.rng.nextDouble() - 0.5) * 0.8;
            double roll = b.rng.nextDouble();
            Branch kind = roll < 0.4 ? Branch.SIDE : roll < 0.72 ? Branch.DEEP : roll < 0.86 ? Branch.SHAFT : Branch.THERMAL;
            if (kind == Branch.THERMAL && !b.thermalContext() && !from.space().environmentId.getPath().equals("thermal")) kind = Branch.DEEP;
            if (kind == Branch.SHAFT && (level > 0 || from.space().hasLake() || b.net.isWindow())) kind = Branch.SIDE;  // no sea floor to open onto in a window
            Chamber sub = switch (kind)
            {
                case SIDE -> sideCave(b, p, from, angle);
                case DEEP -> deepCave(b, p, from, angle);
                case SHAFT ->
                {
                    skylight(b, p, from, angle);
                    yield null;
                }
                case THERMAL -> thermalCave(b, p, from, angle);
            };
            if (sub != null && level < 1 && b.rng.nextFloat() < 0.35f) branches(b, p, sub, 1, level + 1);
        }
    }

    /** A small side cave off a chamber, half the time a mineral pocket with an exposed vein. */
    @Nullable
    private static Chamber sideCave(CaveBuilder b, Plan p, Chamber from, double angle)
    {
        Vec3 origin = from.port(angle, -0.2);
        return sideCaveFrom(b, p, origin, angle, from.radius() * 0.3 + b.range(14, 36), from.space().environmentId);
    }

    @Nullable
    private static Chamber sideCaveFrom(CaveBuilder b, Plan p, Vec3 origin, double angle, double distance, ResourceLocation fromEnv)
    {
        double r = b.range(3.5, 8.5);
        double x = origin.x + Math.cos(angle) * distance, z = origin.z + Math.sin(angle) * distance;
        double y = CaveChamberGenerator.fitY(b, x, z, r, r * 0.7, 6, origin.y + b.range(-8, 4));
        if (Double.isNaN(y)) return null;
        boolean mineral = b.rng.nextBoolean();
        ResourceLocation env = mineral ? b.net.environmentId("mineral", b.profile) : fromEnv;
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, CaveType.SMALL_SEA_CAVE, null, env, x, y, z, r, CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, space, x, y, z, r, 0.7, b.range(1, 2), b.rng.nextBoolean(), angle + Math.PI);
        CaveChamberGenerator.furnish(b, c);
        if (mineral) CaveChamberGenerator.oreBodies(b, c, 1, 0.9);
        double tr = b.range(2.2, 3.4);
        CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, fromEnv, tr), origin, c.port(angle + Math.PI, -0.1), tr, tr * 0.9, 1.0, 0.85);
        b.route.add(mineral ? "side cave (vein)" : "side cave");
        return c;
    }

    /** A deep tunnel descending to a mineral, crystal or luminous chamber, or to an underground lake. */
    @Nullable
    private static Chamber deepCave(CaveBuilder b, Plan p, Chamber from, double angle)
    {
        if (Config.UNDERGROUND_LAKES.get() && b.rng.nextFloat() < 0.25f)
        {
            Chamber lake = lakeBranch(b, p, from, angle);
            if (lake != null) return lake;
        }
        double r = b.range(6, 13);
        double dist = from.radius() + b.range(12, 30);
        double x = from.x() + Math.cos(angle) * dist, z = from.z() + Math.sin(angle) * dist;
        double y = CaveChamberGenerator.fitY(b, x, z, r, r * 0.7, 8, from.y() - b.range(16, 40));
        if (Double.isNaN(y)) return null;
        float roll = b.rng.nextFloat();
        String envName = roll < 0.35f ? "mineral" : roll < 0.55f ? "crystal" : roll < 0.7f ? "luminous" : null;
        ResourceLocation env = envName != null ? b.net.environmentId(envName, b.profile) : from.space().environmentId;
        CaveType type = "mineral".equals(envName) ? CaveType.MINERAL_CAVE : "crystal".equals(envName) ? CaveType.CRYSTAL_CAVE : CaveType.MEDIUM_SEA_CAVE;
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, type, null, env, x, y, z, r, CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, space, x, y, z, r, 0.7, b.range(1, 3), b.rng.nextFloat() < 0.6f, angle + Math.PI);
        CaveChamberGenerator.furnish(b, c);
        double tr = b.range(2.5, 4.0);
        CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, from.space().environmentId, tr), from.port(angle, -0.3), c.port(angle + Math.PI, 0.1), tr, tr, 1.1, 0.9);
        b.route.add("deep tunnel > " + describe(c));
        return c;
    }

    /** An underground lake off a chamber, reached through a flooded tunnel entering below its water line. */
    @Nullable
    private static Chamber lakeBranch(CaveBuilder b, Plan p, Chamber from, double angle)
    {
        double r = b.range(10, 20);
        double dist = from.radius() + r + b.range(8, 20);
        double x = from.x() + Math.cos(angle) * dist, z = from.z() + Math.sin(angle) * dist;
        double vertical = b.range(0.45, 0.65);
        double y = CaveChamberGenerator.lakeY(b.net, x, z, r, r * vertical, from.y() - b.range(0, 14));
        if (Double.isNaN(y)) return null;
        Chamber lake = CaveChamberGenerator.lake(b, CaveType.UNDERGROUND_SEA, null, b.net.environmentId("underground_sea", b.profile), x, y, z, r, vertical,
                b.range(2, 3), Mth.floor(y + r * vertical * b.range(0.1, 0.35)), angle + Math.PI);
        CaveChamberGenerator.furnish(b, lake);
        double tr = b.range(2.6, 3.8);
        CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, from.space().environmentId, tr), from.port(angle, -0.3), lake.port(angle + Math.PI, -0.6),
                tr, tr, 1.0, 0.9);
        b.route.add("underground lake r" + Mth.floor(r));
        return lake;
    }

    /** A skylight: a shaft from the chamber roof up through the seabed, a second way in. False if none fits here. */
    private static boolean skylight(CaveBuilder b, Plan p, Chamber from, double angle)
    {
        double px = from.x() + Math.cos(angle) * from.rx() * 0.4, pz = from.z() + Math.sin(angle) * from.rz() * 0.4;
        double ceil = from.ceilingAt(px, pz);
        int sy = b.net.seabed(px, pz);
        if (Double.isNaN(ceil) || sy > b.net.maxEntranceY() || sy - ceil < 4 || from.space().hasLake()) return false;
        double r = b.range(2.5, 5.0);
        CaveSpace shaft = b.space(CaveSpace.Role.SHAFT, CaveType.VERTICAL_SHAFT, null, from.space().environmentId, px, (sy + ceil) / 2, pz, r,
                CaveSpace.NO_WATER_LEVEL);
        CaveTunnelGenerator.shaft(b, shaft, px, pz, sy + 2, ceil - 2, r);
        b.add(new CaveShape.Capsule(shaft, CaveShape.Kind.CARVE, null, null, true, px, sy - 3, pz, px, sy + 3, pz, r, r * 1.6, 1.0));
        b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.ENTRANCE, px, sy, pz, r * 3 + 6, shaft));
        b.route.add("skylight shaft");
        return true;
    }

    /** A thermal tunnel to a thermal cavern with vents, where vent fields are near. */
    @Nullable
    private static Chamber thermalCave(CaveBuilder b, Plan p, Chamber from, double angle)
    {
        double r = b.range(8, 16);
        double dist = from.radius() + r + b.range(8, 24);
        double x = from.x() + Math.cos(angle) * dist, z = from.z() + Math.sin(angle) * dist;
        double y = CaveChamberGenerator.fitY(b, x, z, r, r * 0.7, 8, from.y() - b.range(6, 22));
        if (Double.isNaN(y)) return null;
        ResourceLocation env = b.net.environmentId("thermal", b.profile);
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, CaveType.THERMAL_CAVE, null, env, x, y, z, r, CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, space, x, y, z, r, 0.7, b.range(2, 3), true, angle + Math.PI);
        CaveChamberGenerator.furnish(b, c);
        double tr = b.range(2.5, 3.5);
        CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, env, tr), from.port(angle, -0.2), c.port(angle + Math.PI, 0.0), tr, tr, 1.0, 0.85);
        b.route.add("thermal tunnel > thermal cavern r" + Mth.floor(r));
        return c;
    }

    /**
     * The expedition route of a landmark (and of some large caves): seabed entrance, narrow tunnel with a side
     * cave holding a vein, a main chamber with crystal formations, an underground lake, a water tunnel, and the
     * goal chamber at the hub, possibly continuing through a thermal tunnel to a thermal cavern.
     */
    private static void grandRoute(CaveBuilder b, Plan p)
    {
        Chamber goal = hub(b, p, p.type().size == CaveType.Size.MASSIVE ? b.range(5, 7) : b.range(3, 4), b.rng.nextFloat() < 0.7f);
        double heading = goal.front();

        Chamber before = goal;
        if (!goal.space().hasLake() && Config.UNDERGROUND_LAKES.get())
        {
            double lr = b.range(12, 20), vertical = b.range(0.45, 0.6);
            double la = heading + b.range(-0.5, 0.5);
            double ld = goal.radius() + lr + b.range(10, 22);
            double lx = goal.x() + Math.cos(la) * ld, lz = goal.z() + Math.sin(la) * ld;
            double ly = CaveChamberGenerator.lakeY(b.net, lx, lz, lr, lr * vertical, goal.y() + b.range(-4, 12));
            if (!Double.isNaN(ly))
            {
                Chamber lake = CaveChamberGenerator.lake(b, CaveType.UNDERGROUND_SEA, null, b.net.environmentId("underground_sea", b.profile),
                        lx, ly, lz, lr, vertical, b.range(2, 3), Mth.floor(ly + lr * vertical * b.range(0.1, 0.3)), la);
                CaveChamberGenerator.furnish(b, lake);
                double tr = b.range(2.8, 3.8);
                CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, lake.space().environmentId, tr), lake.port(la + Math.PI, -0.6), goal.port(la, -0.2),
                        tr, tr, 1.0, 0.9);
                b.route.add(0, "underground lake r" + Mth.floor(lr) + " > water tunnel");
                before = lake;
                heading = la;
            }
        }

        Chamber first = before;
        double mr = b.range(11, 18);
        double ma = heading + b.range(-0.6, 0.6);
        double md = before.radius() + mr + b.range(14, 26);
        double mx = before.x() + Math.cos(ma) * md, mz = before.z() + Math.sin(ma) * md;
        double my = CaveChamberGenerator.fitY(b, mx, mz, mr, mr * 0.65, 10, before.y() + b.range(4, 16));
        if (!Double.isNaN(my))
        {
            ResourceLocation env = b.rng.nextFloat() < 0.4f ? b.net.environmentId("luminous", b.profile) : b.profile.environment;
            CaveSpace space = b.space(CaveSpace.Role.CHAMBER, CaveType.LARGE_ABYSSAL_CAVE, null, env, mx, my, mz, mr, CaveSpace.NO_WATER_LEVEL);
            Chamber main = CaveChamberGenerator.chamber(b, space, mx, my, mz, mr, 0.65, b.range(2, 4), true, ma);
            CaveChamberGenerator.furnish(b, main);
            CaveChamberGenerator.crystals(b, main, b.range(2, 5), 0.8);
            double tr = b.range(3.0, 4.0);
            CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, env, tr), main.port(ma + Math.PI, -0.1), before.port(ma, -0.4), tr, tr, 1.0, 0.9);
            b.route.add(0, "main chamber r" + Mth.floor(mr) + " (crystals)");
            first = main;
        }

        double ea = ma + b.range(-0.5, 0.5);
        Vec3 throat = entranceTo(b, p, first, ea, b.range(12, 26), b.range(2.2, 3.2), true);
        if (throat != null)
        {
            // Side cave with a vein off the narrow tunnel.
            Vec3 port = first.port(ea, -0.1);
            Vec3 mid = throat.lerp(port, b.range(0.35, 0.65));
            sideCaveFrom(b, p, mid, ea + (b.rng.nextBoolean() ? 1.3 : -1.3), b.range(12, 22), first.space().environmentId);
        }

        if (p.landmark() != CaveLandmark.THERMAL_CATHEDRAL && b.thermalContext()) thermalCave(b, p, goal, heading + Math.PI);
        branches(b, p, goal, b.range(1, 2), 1);
    }

    // ---------------------------------------------------------------- network links

    private static final int[][] LINKS = {{1, 0}, {0, 1}};

    /** The connector from p toward its east (d = 0) or south (d = 1) neighbour q, if that pair is linked: its random source. */
    @Nullable
    private static RandomSource link(CaveNetwork net, Plan p, Plan q, int d)
    {
        RandomSource r = new XoroshiroRandomSource(cellSeed(net, LINK_SALT + LINKS[d][0], p.cellX(), p.cellZ()));
        double chance = (p.profile().connectionChance + q.profile().connectionChance) * 0.5 * Config.CAVE_CONNECTION_CHANCE.get();
        if (net.isWindow()) chance *= Config.BAND_CONNECTION[net.band().ordinal()].get();
        return r.nextDouble() < chance ? r : null;
    }

    /**
     * Whether the system planned for this cell could reach the block rectangle, judged from plans alone: its local
     * parts stay within a bound of its hub (the longest grand routes about 330 blocks), and a connector stays within
     * its meander of the line between two hubs. Saves building most of the systems around a chunk.
     */
    static boolean mayReach(CaveNetwork net, Plan p, int x0, int z0, int x1, int z1)
    {
        // Mega caverns: lobes reach 1.5 x the radius, satellite rooms about 1.75 x, plus the branches off them.
        double local = p.type().size == CaveType.Size.MEGA ? p.radius() * 2.1 + 240 : p.grand() ? 340 : p.radius() + 200;
        if (distance(p.x(), p.z(), x0, z0, x1, z1) <= local) return true;
        for (int d = 0; d < LINKS.length; d++)
        {
            Plan q = net.plan(p.cellX() + LINKS[d][0], p.cellZ() + LINKS[d][1]);
            if (q == null || link(net, p, q, d) == null) continue;
            // The meander scales with the tunnel's full 3D length (hubs can sit at very different depths).
            double length = Math.sqrt(Mth.square(q.x() - p.x()) + Mth.square(q.portY() - p.portY()) + Mth.square(q.z() - p.z()));
            double margin = length * 0.33 + 24;
            if (Math.max(p.x(), q.x()) + margin >= x0 && Math.min(p.x(), q.x()) - margin <= x1
                    && Math.max(p.z(), q.z()) + margin >= z0 && Math.min(p.z(), q.z()) - margin <= z1) return true;
        }
        VLink v = net.isWindow() ? vlink(net, p) : null;
        if (v != null)
        {
            Plan q = v.q();
            double length = Math.sqrt(Mth.square(q.x() - p.x()) + Mth.square(q.portY() - p.portY()) + Mth.square(q.z() - p.z()));
            double margin = length * 0.33 + 24;
            if (Math.max(p.x(), q.x()) + margin >= x0 && Math.min(p.x(), q.x()) - margin <= x1
                    && Math.max(p.z(), q.z()) + margin >= z0 && Math.min(p.z(), q.z()) - margin <= z1) return true;
        }
        return false;
    }

    /** A vertical link from a system up to a system of the window above: its target and the stream deciding its details. */
    record VLink(Plan q, RandomSource rng) {}

    /**
     * The vertical link of this system, if any: with {@code vertical_link_chance} a system gets a shaft up to the hub of
     * the nearest system of the window above (searched in the 3 x 3 cells around it, within a bounded distance), so the
     * player can climb through the bands. None if the window above has no system close enough (nothing to meet), none
     * from a lake hub (its gas shell would seal the shaft), none from the topmost window.
     */
    @Nullable
    static VLink vlink(CaveNetwork net, Plan p)
    {
        CaveNetwork above = net.above();
        if (above == null || p.waterLevel() != CaveSpace.NO_WATER_LEVEL) return null;
        RandomSource r = new XoroshiroRandomSource(cellSeed(net, VLINK_SALT, p.cellX(), p.cellZ()));
        double roll = r.nextDouble();
        if (p.type() == CaveType.ABYSS_CAVITY)
        {
            // AB06: a cavity always links up, to the nearest cavity of the window above if one lies within reach.
            for (AbyssCavity.Site s : AbyssCavity.near(above, p.x(), p.z(), AbyssCavity.MAX_LINK))
            {
                Plan q = above.plan(s.cellX(), s.cellZ());
                if (q != null) return new VLink(q, r);
            }
        }
        else if (roll >= Config.BAND_VERTICAL_LINK_CHANCE.get()) return null;
        int ac = above.cellSize();
        int cx = Math.floorDiv(Mth.floor(p.x()), ac), cz = Math.floorDiv(Mth.floor(p.z()), ac);
        Plan best = null;
        double bestDistance = Math.max(ac, 240);
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                Plan q = above.plan(cx + dx, cz + dz);
                if (q == null) continue;
                double d = Math.hypot(q.x() - p.x(), q.z() - p.z());
                if (d < bestDistance)
                {
                    bestDistance = d;
                    best = q;
                }
            }
        }
        return best == null ? null : new VLink(best, r);
    }

    private static double distance(double x, double z, int x0, int z0, int x1, int z1)
    {
        double dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Tunnels to the east and south neighbours' hubs, each pair deciding independently from its own seed. */
    private static void connectors(CaveBuilder b, Plan p)
    {
        for (int d = 0; d < LINKS.length; d++)
        {
            Plan q = b.net.plan(p.cellX() + LINKS[d][0], p.cellZ() + LINKS[d][1]);
            if (q == null) continue;
            RandomSource r = link(b.net, p, q, d);
            if (r == null) continue;
            double radius = 2.4 + r.nextDouble() * 1.8;
            CaveSpace tunnel = b.space(CaveSpace.Role.TUNNEL, CaveType.SEA_TUNNEL, null, p.profile().environment, (p.x() + q.x()) / 2,
                    (p.portY() + q.portY()) / 2, (p.z() + q.z()) / 2, radius, CaveSpace.NO_WATER_LEVEL);
            CaveTunnelGenerator.tunnel(b, tunnel, new Vec3(p.x(), p.portY(), p.z()), new Vec3(q.x(), q.portY(), q.z()), radius, radius, 1.2, 0.85);
            b.route.add("connector to cell " + q.cellX() + "," + q.cellZ());
        }
        if (b.net.isWindow()) verticalLink(b, p);
    }

    /**
     * The shaft from the hub up past the window's top (at least {@link DepthBand#OVERLAP} blocks into the window above,
     * up to the level of the target's hub), then a passage over to that hub.
     */
    private static void verticalLink(CaveBuilder b, Plan p)
    {
        VLink v = vlink(b.net, p);
        if (v == null) return;
        Plan q = v.q();
        boolean fromCavity = p.type() == CaveType.ABYSS_CAVITY, toCavity = q.type() == CaveType.ABYSS_CAVITY;
        double radius = fromCavity ? 5.0 + v.rng().nextDouble() * 2.0 : 3.0 + v.rng().nextDouble() * 2.5;
        // AB06: a cavity's shaft leaves (and enters) the hall half way out toward the other end, above the terraces, not at its centre.
        double bearing = Math.atan2(q.z() - p.z(), q.x() - p.x());
        double sx = p.x(), sz = p.z(), y0 = p.portY(), tx = q.x(), tz = q.z(), ty = q.portY();
        if (fromCavity)
        {
            sx += Math.cos(bearing) * p.radius() * AbyssCavity.LINK_OFFSET;
            sz += Math.sin(bearing) * p.radius() * AbyssCavity.LINK_OFFSET;
            y0 = p.y() + AbyssCavity.LINK_LIFT;
        }
        if (toCavity)
        {
            tx -= Math.cos(bearing) * q.radius() * AbyssCavity.LINK_OFFSET;
            tz -= Math.sin(bearing) * q.radius() * AbyssCavity.LINK_OFFSET;
            ty = q.y() + AbyssCavity.LINK_LIFT;
        }
        double yTop = Math.max(ty, b.net.maxY() + DepthBand.OVERLAP);
        CaveSpace shaft = b.space(CaveSpace.Role.SHAFT, CaveType.VERTICAL_SHAFT, null, p.environment(), sx, (y0 + yTop) / 2, sz, radius, CaveSpace.NO_WATER_LEVEL);
        CaveTunnelGenerator.shaft(b, shaft, sx, sz, yTop, y0, radius);
        if (Math.hypot(tx - sx, tz - sz) > radius)
        {
            CaveTunnelGenerator.tunnel(b, tunnelSpace(b, p, p.environment(), radius), new Vec3(sx, yTop, sz), new Vec3(tx, ty, tz), radius, radius, 0.6, 0.85);
        }
        b.route.add("vertical link up to " + b.net.above().label() + (toCavity ? " cavity " : " cell ") + q.cellX() + "," + q.cellZ() + " @" + Mth.floor(q.x()) + " " + Mth.floor(q.y()) + " " + Mth.floor(q.z()));
    }

    // ---------------------------------------------------------------- minor caves

    /** Small sea caves, sea arches and short eroded passages scattered between the systems. */
    static CaveSystem buildMinor(CaveNetwork net, int cellX, int cellZ)
    {
        RandomSource rng = new XoroshiroRandomSource(cellSeed(net, MINOR_SALT, cellX, cellZ));
        int cell = CaveNetwork.MINOR_CELL, margin = 10;
        int ax = cellX * cell + margin + rng.nextInt(cell - 2 * margin), az = cellZ * cell + margin + rng.nextInt(cell - 2 * margin);
        CaveProfile profile = net.profile(ax, az);
        if (profile == null || rng.nextFloat() >= profile.minorCaveChance * Config.MINOR_CAVE_CHANCE.get()) return empty(net, cellX, cellZ, true);
        int sy = net.seabed(ax, az);
        if (sy > net.maxEntranceY() || sy < net.minY() + 24) return empty(net, cellX, cellZ, true);
        CaveType type = profile.minorTypes.isEmpty() ? CaveType.SMALL_SEA_CAVE : profile.minorTypes.pick(rng.nextDouble());
        CaveBuilder b = new CaveBuilder(net, rng.nextLong(), profile, ax, az);
        ResourceLocation env = type.environment != null ? net.environmentId(type.environment, profile) : profile.environment;
        if (type.environment == null && net.luminous(profile, ax, az)) env = net.environmentId("luminous", profile);
        Plan p = new Plan(cellX, cellZ, 0, profile, type, null, env, ax, sy - 10, az, 5, type.vertical, CaveSpace.NO_WATER_LEVEL, false, false);
        switch (type)
        {
            case SEA_ARCH ->
            {
                CaveSpace arch = b.space(CaveSpace.Role.ARCH, CaveType.SEA_ARCH, null, env, ax, sy, az, 6, CaveSpace.NO_WATER_LEVEL);
                double angle = b.rng.nextDouble() * Math.PI;
                double span = b.range(14, 30);
                if (!CaveEntranceGenerator.arch(b, arch, ax, az, angle, span, b.range(7, 18), b.range(2.2, 4.0))) return empty(net, cellX, cellZ, true);
                if (b.rng.nextFloat() < 0.35f) minorCave(b, p, ax + Math.cos(angle) * span * 0.6, az + Math.sin(angle) * span * 0.6);
            }
            case ERODED_CAVE -> erodedPassage(b, p, ax, az, sy);
            default -> minorCave(b, p, ax, az);
        }
        return b.finish(cellX, cellZ, true, type, null, ax, sy, az);
    }

    private static void minorCave(CaveBuilder b, Plan p, double x, double z)
    {
        double r = b.range(3.0, 7.5), ry = r * 0.75;
        double y = CaveChamberGenerator.fitY(b, x, z, r, ry, 4, b.net.seabed(x, z) - ry - b.range(5, 12));
        if (Double.isNaN(y)) return;
        CaveSpace space = b.space(CaveSpace.Role.CHAMBER, CaveType.SMALL_SEA_CAVE, null, p.environment(), x, y, z, r, CaveSpace.NO_WATER_LEVEL);
        Chamber c = CaveChamberGenerator.chamber(b, space, x, y, z, r, 0.75, b.range(1, 2), b.rng.nextBoolean());
        CaveChamberGenerator.furnish(b, c);
        entranceTo(b, p, c, b.rng.nextDouble() * Math.PI * 2, b.range(3, 10), b.range(1.8, 2.8), false);
        b.route.add(describe(c));
    }

    /** A U-shaped, water-polished passage under the seabed, open at both ends. */
    private static void erodedPassage(CaveBuilder b, Plan p, double x, double z, int sy)
    {
        double length = b.range(24, 48), angle = b.rng.nextDouble() * Math.PI;
        double r = b.range(2.5, 4.0);
        double lowY = Math.max(b.minCarveY() + r, sy - b.range(8, 16));
        Vec3 low = new Vec3(x, lowY, z);
        CaveSpace tunnel = b.space(CaveSpace.Role.TUNNEL, CaveType.ERODED_CAVE, null, p.environment(), x, lowY, z, r, CaveSpace.NO_WATER_LEVEL);
        for (int side : new int[] {-1, 1})
        {
            double ex = x + Math.cos(angle) * length / 2 * side, ez = z + Math.sin(angle) * length / 2 * side;
            CaveEntranceGenerator.entrance(b, tunnel, ex, ez, low, r, false);
        }
        b.route.add("eroded passage " + Mth.floor(length) + "m");
    }
}
