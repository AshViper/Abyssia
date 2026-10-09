package com.abyssia.worldgen.cave;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A large cavern seen as a structured 3D environment rather than one hollow: its template, its vertical zones
 * (floor, lower, middle, upper, ceiling), its sectors (centre, north, south, east, west), a gradient from the
 * entrance side to the far side, and the ecology patches its floor is divided into (plant, mineral, crystal, rock,
 * water, thermal, open), plus the lakes and gardens planned in it.
 * <p>
 * Built once with the layout ({@link CavernPlanner}) and read-only afterwards; every query is a pure function of
 * position, so chunks decorating different parts of the same cavern agree.
 */
public final class Cavern
{
    public enum Tier { SMALL, LARGE, MASSIVE, MEGA }

    public enum Zone { FLOOR, LOWER, MIDDLE, UPPER, CEILING }

    public enum Sector { CENTER, NORTH, SOUTH, EAST, WEST }

    public record Patch(double x, double z, double radius, CavernPatch type) {}

    /** A brine lake: an oriented ellipse on the floor; {@code surfaceY} is the block holding its surface sheet. */
    public record Lake(double x, double z, double halfLength, double halfWidth, double cos, double sin, int surfaceY)
    {
        /** 0 at the centre, 1 at the shoreline. */
        public double normalized(double px, double pz)
        {
            double dx = px - x, dz = pz - z;
            double u = dx * cos + dz * sin, v = -dx * sin + dz * cos;
            return Math.sqrt(Mth.square(u / halfLength) + Mth.square(v / halfWidth));
        }
    }

    /** A cave garden (rings around a centrepiece) or a deep-sea grove (mixed stand of giants and undergrowth). */
    public record Garden(double x, double z, double radius, boolean grove, boolean tall) {}

    /** Where a column sits in a giant kelp clump: q 0 at its heart, 1 at its edge; lean per block of height. */
    public record Cluster(double q, double height, int leanX, int leanZ, int leanEvery) {}

    /** AB03: a column of light (a tall luminous plant) rising from a hall's floor, at a block column. */
    public record Beacon(int x, int z) {}

    /** AB03: a glowing niche high in a hall's far wall. */
    public record Window(double x, double y, double z, double radius) {}

    private static final int KELP_CELL = 11;
    private static final int ROOT_CELL = 17;

    public final ResourceLocation templateId;
    public final CavernTemplate template;
    public final Tier tier;
    public final CaveSpace space;
    public final double x, y, z, rx, rz;
    /** Unit vector toward the entrance side. */
    public final double frontX, frontZ;
    /** Floor and roof height at the centre (approximate), for zones. */
    public final double floor0, ceiling0;
    /** Giant plant height multiplier: very tall caverns grow 30-80 block kelp. */
    public final double forestScale;
    private final List<CaveShape.Ellipsoid> lobes;
    /** AB03: the hall this cavern is (null for a lobed cavern). */
    @Nullable
    public final CaveShape.Hall hall;
    private List<Beacon> beacons = new ArrayList<>();
    @Nullable
    private Window window;
    /** Planned formations, for the hall report: trunks and fused pillars, spires, giant stalactites, column clusters. */
    int trunks, spires, stalactites, clusters;
    private final CaveNoises noises;
    private final double relief, flutes, groovePhase;

    private List<Patch> patches = new ArrayList<>();
    private List<Lake> lakes = new ArrayList<>();
    private List<Garden> gardens = new ArrayList<>();
    private List<CavernTemplate.CrystalColor> crystals = new ArrayList<>();
    @Nullable
    private CavernCenter center;
    private double centerX, centerZ;

    Cavern(ResourceLocation templateId, CavernTemplate template, Tier tier, CaveSpace space, CaveChamberGenerator.Chamber chamber, CaveNoises noises,
           double floor0, double ceiling0, double relief, double flutes, double groovePhase)
    {
        this.templateId = templateId;
        this.template = template;
        this.tier = tier;
        this.space = space;
        this.x = chamber.x();
        this.y = chamber.y();
        this.z = chamber.z();
        this.rx = chamber.rx();
        this.rz = chamber.rz();
        this.frontX = Math.cos(chamber.front());
        this.frontZ = Math.sin(chamber.front());
        this.lobes = chamber.lobes();
        this.hall = chamber.hall();
        this.noises = noises;
        this.floor0 = floor0;
        this.ceiling0 = ceiling0;
        this.forestScale = ceiling0 - floor0 >= 70 ? 1.6 : ceiling0 - floor0 >= 45 ? 1.25 : 1.0;
        this.relief = relief;
        this.flutes = flutes;
        this.groovePhase = groovePhase;
    }

    // ---------------------------------------------------------------- planning (only while the layout is built)

    void addPatch(Patch patch)
    {
        patches.add(patch);
    }

    void addLake(Lake lake)
    {
        lakes.add(lake);
    }

    void addGarden(Garden garden)
    {
        gardens.add(garden);
    }

    void addBeacon(Beacon beacon)
    {
        beacons.add(beacon);
    }

    void setWindow(Window window)
    {
        this.window = window;
    }

    public List<Beacon> beacons()
    {
        return beacons;
    }

    @Nullable
    public Window window()
    {
        return window;
    }

    /** One-line hall report (dimensions and formations), or null for a lobed cavern. */
    @Nullable
    public String hallReport()
    {
        if (hall == null) return null;
        double floor = hall.floor(hall.cx, hall.cz);
        return String.format("hall %s, %s, %d x %d wide, basin floor y %d, terrace level y %d, roof apex y %d (%d above the level, %d above the basin), "
                        + "walls to y %d, terrace step %d, basin %.0f%% of the radius, %d islands, %d trunks/pillars, %d spires, %d giant stalactites, "
                        + "%d column clusters, %d light columns%s",
                space.environmentId.getPath(), tier.name().toLowerCase(), Math.round(hall.rx * 2), Math.round(hall.rz * 2), (int) floor, hall.level,
                (int) hall.apexY(), (int) (hall.apexY() - hall.level), (int) (hall.apexY() - floor), (int) hall.springY, hall.step, hall.shore * 100,
                hall.islandCount(), trunks, spires, stalactites, clusters, beacons.size(),
                window != null ? String.format(", window @ %d %d %d", (int) window.x(), (int) window.y(), (int) window.z()) : "");
    }

    void setCrystals(List<CavernTemplate.CrystalColor> colors)
    {
        crystals = colors;
    }

    void setCenter(CavernCenter center, double x, double z)
    {
        this.center = center;
        this.centerX = x;
        this.centerZ = z;
    }

    /** Ends planning; the layout is then shared read-only between threads. */
    void freeze()
    {
        patches = List.copyOf(patches);
        lakes = List.copyOf(lakes);
        gardens = List.copyOf(gardens);
        crystals = List.copyOf(crystals);
        beacons = List.copyOf(beacons);
    }

    List<Patch> patches()
    {
        return patches;
    }

    public List<Lake> lakes()
    {
        return lakes;
    }

    public List<Garden> gardens()
    {
        return gardens;
    }

    @Nullable
    public CavernCenter center()
    {
        return center;
    }

    public double centerX()
    {
        return centerX;
    }

    public double centerZ()
    {
        return centerZ;
    }

    public double reliefAmplitude()
    {
        return relief;
    }

    // ---------------------------------------------------------------- geometry and zones

    /** Horizontal distance from the centre relative to the footprint: 0 centre, 1 at the walls. */
    public double footprint(double px, double pz)
    {
        return Math.sqrt(Mth.square((px - x) / rx) + Mth.square((pz - z) / rz));
    }

    public double floorAt(double px, double pz)
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

    public double ceilingAt(double px, double pz)
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

    /** 0 at the floor, 1 at the roof of this column (the centre's heights where the column is outside the lobes). */
    public double relativeHeight(double px, double py, double pz)
    {
        double f = floorAt(px, pz), c = ceilingAt(px, pz);
        if (Double.isNaN(f) || Double.isNaN(c) || c - f < 2)
        {
            f = floor0;
            c = ceiling0;
        }
        return Mth.clamp((py - f) / Math.max(1, c - f), 0.0, 1.0);
    }

    public Zone zone(double px, double py, double pz)
    {
        double t = relativeHeight(px, py, pz);
        return t < 0.12 ? Zone.FLOOR : t < 0.35 ? Zone.LOWER : t < 0.65 ? Zone.MIDDLE : t < 0.88 ? Zone.UPPER : Zone.CEILING;
    }

    public Sector sector(double px, double pz)
    {
        double dx = (px - x) / rx, dz = (pz - z) / rz;
        if (dx * dx + dz * dz < 0.35 * 0.35) return Sector.CENTER;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Sector.EAST : Sector.WEST) : (dz > 0 ? Sector.SOUTH : Sector.NORTH);
    }

    /** -1 on the entrance side, 0 in the middle, +1 at the far end (entrance, middle, deep, core gradient). */
    public double depth(double px, double pz)
    {
        return Mth.clamp(-((px - x) * frontX + (pz - z) * frontZ) / Math.max(rx, rz), -1.0, 1.0);
    }

    // ---------------------------------------------------------------- ecology patches

    /** The patch owning a floor column: nearest seed relative to its size, with noisy borders. */
    public CavernPatch patchAt(double px, double pz)
    {
        CavernPatch best = CavernPatch.OPEN;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < patches.size(); i++)
        {
            Patch p = patches.get(i);
            double score = Math.sqrt(Mth.square(px - p.x()) + Mth.square(pz - p.z())) / p.radius() + noises.field2(px * 0.07 + i * 13.0, pz * 0.07, 7) * 0.3;
            if (score < bestScore)
            {
                bestScore = score;
                best = p.type();
            }
        }
        return best;
    }

    /**
     * Density multiplier for ordinary cave plants in this cavern: by patch, by the entrance-to-deep gradient (the
     * middle is lushest), by height (sparser high on the walls), thinner inside lakes and gardens (they are dressed
     * by their own pass).
     */
    public double vegetationFactor(double px, double py, double pz)
    {
        double f = patchAt(px, pz).vegetation;
        double g = depth(px, pz);
        f *= g < -0.35 ? 0.75 : g < 0.35 ? 1.25 : 1.0;
        Zone zone = zone(px, py, pz);
        if (zone == Zone.UPPER || zone == Zone.CEILING) f *= 0.7;
        if (lakeAt(px, pz, 1.0) != null || gardenAt(px, pz) != null) f *= 0.45;
        return f;
    }

    public double crystalFactor(double px, double pz)
    {
        return patchAt(px, pz).crystals;
    }

    public double mineralFactor(double px, double pz)
    {
        return patchAt(px, pz).minerals;
    }

    public double speleothemFactor(double px, double pz)
    {
        return patchAt(px, pz).speleothems;
    }

    /** One of this cavern's crystal colours (1-3, picked from its biome's palette). */
    @Nullable
    public CavernTemplate.CrystalColor crystal(double r)
    {
        return crystals.isEmpty() ? null : crystals.get(Math.min(crystals.size() - 1, (int) (r * crystals.size())));
    }

    @Nullable
    public BlockState crystalCluster(double r)
    {
        CavernTemplate.CrystalColor c = crystal(r);
        return c == null ? null : c.cluster();
    }

    @Nullable
    public Lake lakeAt(double px, double pz, double reach)
    {
        for (Lake lake : lakes)
        {
            if (lake.normalized(px, pz) < reach) return lake;
        }
        return null;
    }

    @Nullable
    public Garden gardenAt(double px, double pz)
    {
        for (Garden g : gardens)
        {
            if (Mth.square(px - g.x()) + Mth.square(pz - g.z()) < g.radius() * g.radius()) return g;
        }
        return null;
    }

    // ---------------------------------------------------------------- walls

    /**
     * Extra carving on the walls (negative displacement): vertical flutes and horizontal notches worn into the rock,
     * only on the walls (not floor or roof), so even distant walls carry detail.
     */
    public double wallRelief(double px, double py, double pz)
    {
        if (relief <= 0) return 0;
        double q = footprint(px, pz);
        if (q < 0.5) return 0;
        double t = (py - floor0) / Math.max(1, ceiling0 - floor0);
        if (t < 0.06 || t > 0.97) return 0;
        double wall = smooth((q - 0.5) / 0.3);
        double band = smooth(t / 0.15) * smooth((0.97 - t) / 0.15);
        double a = Math.atan2(pz - z, px - x);
        double flute = Math.sin(a * flutes + noises.field2(py * 0.04, a * 3.0, 5) * 2.0);
        double notch = Math.sin((py + groovePhase + noises.field2(a * 6.0, 0.5, 6) * 3.0) * (Math.PI * 2 / 11));
        double v = Mth.square(Math.max(0, (flute - 0.55) / 0.45)) + 0.8 * Mth.square(Math.max(0, (notch - 0.7) / 0.3));
        return -relief * v * wall * band;
    }

    private static double smooth(double t)
    {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- forests

    /**
     * Giant kelp forest density: broad sparse, medium and dense stretches, broken by round clearings (a glade in the
     * forest), from world-space noise so forests continue across chunks.
     */
    public double forestDensity(double px, double pz)
    {
        double n = noises.field2(px * 0.018, pz * 0.018, 21);
        double base = n < -0.45 ? 0.12 : n < -0.1 ? Mth.lerp((n + 0.45) / 0.35, 0.12, 0.45) : n < 0.15 ? Mth.lerp((n + 0.1) / 0.25, 0.45, 1.0) : 1.0;
        double c = noises.field2(px * 0.055, pz * 0.055, 22);
        double threshold = 1 - template.forest.clearings() * 1.2;
        if (c > threshold) base *= Math.max(0, 1 - (c - threshold) / 0.12);
        return base;
    }

    /** The giant kelp clump this column belongs to, if any: clumps sit on a jittered grid where the forest is. */
    @Nullable
    public Cluster kelpCluster(double px, double pz)
    {
        return cluster(px, pz, KELP_CELL, 301, true);
    }

    /** Clumps of hanging roots out over open ground, independent of the kelp. */
    @Nullable
    public Cluster rootCluster(double px, double pz)
    {
        return cluster(px, pz, ROOT_CELL, 401, false);
    }

    @Nullable
    private Cluster cluster(double px, double pz, int cell, int salt, boolean forest)
    {
        int gx0 = Math.floorDiv((int) Math.floor(px), cell), gz0 = Math.floorDiv((int) Math.floor(pz), cell);
        Cluster best = null;
        for (int i = -1; i <= 1; i++)
        {
            for (int j = -1; j <= 1; j++)
            {
                int gx = gx0 + i, gz = gz0 + j;
                double cx = gx * cell + noises.hash(gx, 7, gz, salt) * cell, cz = gz * cell + noises.hash(gx, 7, gz, salt + 1) * cell;
                double exists = forest ? forestDensity(cx, cz) * 0.9 : 0.35;
                if (noises.hash(gx, 7, gz, salt + 2) >= exists) continue;
                double radius = 2.5 + noises.hash(gx, 7, gz, salt + 3) * 4.5;
                double q = Math.sqrt(Mth.square(px - cx) + Mth.square(pz - cz)) / radius;
                if (q >= 1 || (best != null && q >= best.q())) continue;
                boolean leans = noises.hash(gx, 7, gz, salt + 4) < template.forest.lean();
                double a = noises.hash(gx, 7, gz, salt + 5) * Math.PI * 2;
                int every = 5 + (int) (noises.hash(gx, 7, gz, salt + 6) * 5);
                best = new Cluster(q, noises.hash(gx, 7, gz, salt + 7), leans ? (int) Math.round(Math.cos(a)) : 0, leans ? (int) Math.round(Math.sin(a)) : 0, every);
            }
        }
        return best;
    }

    /** One-line description for the debug command. */
    public String describe(double px, double py, double pz)
    {
        return templateId.getPath() + " cavern (" + tier.name().toLowerCase() + "), zone " + zone(px, py, pz).name().toLowerCase() + ", sector "
                + sector(px, pz).name().toLowerCase() + ", patch " + patchAt(px, pz).getSerializedName()
                + String.format(", depth %.2f", depth(px, pz)) + (center != null ? ", centre: " + center.getSerializedName() : "")
                + ", " + patches.size() + " patches, " + lakes.size() + " lakes, " + gardens.size() + " gardens";
    }
}
