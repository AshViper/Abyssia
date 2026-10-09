package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModBlocks;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import com.abyssia.worldgen.cave.CaveChamberGenerator.Chamber;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a large chamber into a cavern (the "massive cavern decoration" layer, added on top of the existing chamber
 * generation). Detection is by size: chambers of radius 16+ become small, large or massive caverns, and the tier
 * sets the budget of every structure.
 * <p>
 * Order, following the cavern pipeline: template and zones, ecology patches, floor terrain, wall formation, rock
 * pillars, rock shelves, ceiling formations, large mineral formations, crystal formations, underground water,
 * gardens and groves, then the centre landmark. All of it becomes layout shapes (and lakes/gardens the decorator
 * dresses later), so it is computed once per cavern and every chunk it spans agrees.
 */
final class CavernPlanner
{
    static final double MIN_RADIUS = 16;

    private static final Map<CaveLandmark, String> LANDMARK_TEMPLATES = Map.of(
            CaveLandmark.GIANT_KELP_CAVERN, "forest",
            CaveLandmark.DEEP_CAVE_FOREST, "forest",
            CaveLandmark.MASSIVE_CRYSTAL_CHAMBER, "crystal",
            CaveLandmark.ANCIENT_MINERAL_CHAMBER, "mineral",
            CaveLandmark.THERMAL_CATHEDRAL, "thermal",
            CaveLandmark.ABYSSAL_UNDERGROUND_LAKE, "lake",
            CaveLandmark.GIANT_STALACTITE_CHAMBER, "ruins_like_geology");
    private static final Map<String, String> ENVIRONMENT_TEMPLATES = Map.of(
            "forest", "forest", "crystal", "crystal", "mineral", "mineral", "thermal", "thermal", "underground_sea", "lake");

    private CavernPlanner() {}

    static void plan(CaveBuilder b, Chamber c)
    {
        CaveSpace s = c.space();
        ResourceLocation id = chooseTemplate(b, s);
        if (!b.net.hasCavernTemplate(id)) id = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "mixed");
        CavernTemplate template = b.net.cavernTemplate(id);
        if (template == null) return;
        double r = c.radius();
        Cavern.Tier tier = r < 24 ? Cavern.Tier.SMALL : r < 40 ? Cavern.Tier.LARGE : s.type == CaveType.MEGA_CAVERN ? Cavern.Tier.MEGA : Cavern.Tier.MASSIVE;
        double floor0 = c.floorAt(c.x(), c.z()), ceiling0 = c.ceilingAt(c.x(), c.z());
        if (Double.isNaN(floor0) || Double.isNaN(ceiling0)) return;
        double tierScale = tier == Cavern.Tier.SMALL ? 0.7 : tier == Cavern.Tier.LARGE ? 1.0 : tier == Cavern.Tier.MASSIVE ? 1.3 : 1.5;
        Cavern cavern = new Cavern(id, template, tier, s, c, b.noises, floor0, ceiling0, template.wallRelief * tierScale,
                Math.round(b.range(10, 22) * Math.sqrt(r / 24)), b.range(0, 11));
        s.cavern = cavern;
        // Rolling floor instead of a flat cut (bounds are recomputed including the wall relief).
        double relief = tier == Cavern.Tier.SMALL ? 1.5 : tier == Cavern.Tier.LARGE ? 2.5 : tier == Cavern.Tier.MASSIVE ? 3.5 : 4.5;
        for (CaveShape.Ellipsoid lobe : c.lobes()) lobe.setFloorRelief(b.noises, relief);
        if (c.hall() != null) c.hall().rebound();
        cavern.setCrystals(crystalColors(b));

        Site site = new Site(b, c, cavern, template, tier);
        CavernCenter center = null;
        double centerChance = template.centerChance * (tier.ordinal() >= Cavern.Tier.MASSIVE.ordinal() ? 1.0 : tier == Cavern.Tier.LARGE ? 0.65 : 0.3);
        if (!template.centers.isEmpty() && b.rng.nextDouble() < centerChance) center = template.centers.pick(b.rng.nextDouble());
        seedPatches(site, center != null);
        if (c.hall() != null)
        {
            // AB03: a hall has its own terraced floor, columns and stalactites.
            HallFormations.plan(site, center);
            cavern.freeze();
            return;
        }

        CavernFormations.floorTerrain(site);
        CavernFormations.wallFormation(site);
        CavernFormations.pillars(site);
        CavernFormations.shelves(site);
        CavernFormations.ceiling(site);
        CavernFormations.bridges(site);
        CavernFormations.megaOres(site);
        CavernFormations.crystalForests(site);
        CavernFormations.lakes(site);
        CavernFormations.gardens(site);
        if (center != null) CavernFormations.center(site, center);
        cavern.freeze();
    }

    private static ResourceLocation chooseTemplate(CaveBuilder b, CaveSpace s)
    {
        String forced = s.landmark != null ? LANDMARK_TEMPLATES.get(s.landmark) : null;
        if (forced == null && b.rng.nextFloat() < 0.6f) forced = ENVIRONMENT_TEMPLATES.get(s.environmentId.getPath());
        if (forced != null) return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, forced);
        ResourceLocation picked = s.profile.cavernTemplates.pick(b.rng.nextDouble());
        return picked != null ? picked : ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "mixed");
    }

    /** 1-3 crystal colours from the biome's palette (cyan deep crystal if it has none). */
    private static List<CavernTemplate.CrystalColor> crystalColors(CaveBuilder b)
    {
        List<CavernTemplate.CrystalColor> colors = new ArrayList<>();
        int count = 1 + (b.rng.nextFloat() < 0.6f ? 1 : 0) + (b.rng.nextFloat() < 0.25f ? 1 : 0);
        for (int i = 0; i < count && !b.profile.crystalColors.isEmpty(); i++)
        {
            CavernTemplate.CrystalColor color = b.profile.crystalColors.pick(b.rng.nextDouble());
            if (!colors.contains(color)) colors.add(color);
        }
        if (colors.isEmpty())
        {
            colors.add(new CavernTemplate.CrystalColor(ModBlocks.DEEP_CRYSTAL_BLOCK.get().defaultBlockState(), ModBlocks.DEEP_CRYSTAL_CLUSTER.get().defaultBlockState()));
        }
        return colors;
    }

    /**
     * One patch seed per sector (north, south, east, west; the diagonals too in massive caverns; the centre in larger
     * ones), typed from the template (deep-side seeds from its deep weights), so each direction looks different.
     * At least one open patch in larger caverns; the centre stays open around a landmark.
     */
    private static void seedPatches(Site site, boolean landmark)
    {
        CaveBuilder b = site.b;
        Chamber c = site.chamber;
        int sectors = site.tier == Cavern.Tier.MEGA ? 12 : site.tier == Cavern.Tier.MASSIVE ? 8 : 4;
        double spin = b.rng.nextDouble() * Math.PI * 2;
        boolean open = false;
        List<Cavern.Patch> list = new ArrayList<>();
        for (int i = 0; i < sectors; i++)
        {
            double a = spin + i * Math.PI * 2 / sectors + (b.rng.nextDouble() - 0.5) * 0.5;
            double d = b.range(0.4, 0.7);
            double px = c.x() + Math.cos(a) * c.rx() * d, pz = c.z() + Math.sin(a) * c.rz() * d;
            boolean deep = site.cavern.depth(px, pz) > 0.25;
            CavernPatch type = (deep ? site.template.deepPatches : site.template.patches).pick(b.rng.nextDouble());
            if (type == null) type = CavernPatch.PLANT;
            open |= type == CavernPatch.OPEN;
            list.add(new Cavern.Patch(px, pz, c.radius() * b.range(0.3, 0.5), type));
        }
        if (site.tier != Cavern.Tier.SMALL)
        {
            CavernPatch core = landmark || b.rng.nextFloat() < 0.4f ? CavernPatch.OPEN : site.template.patches.pick(b.rng.nextDouble());
            if (core == null) core = CavernPatch.OPEN;
            open |= core == CavernPatch.OPEN;
            list.add(new Cavern.Patch(c.x() + b.range(-3, 3), c.z() + b.range(-3, 3), c.radius() * 0.3, core));
            if (!open)
            {
                Cavern.Patch p = list.get(b.rng.nextInt(list.size() - 1));
                list.set(list.indexOf(p), new Cavern.Patch(p.x(), p.z(), p.radius(), CavernPatch.OPEN));
            }
        }
        for (Cavern.Patch p : list)
        {
            site.cavern.addPatch(p);
            // Thermal patches get their own vents; the existing vent code builds and zones them.
            if (p.type() == CavernPatch.THERMAL) site.vents(p.x(), p.z(), p.radius() * 0.6, b.range(1, site.tier == Cavern.Tier.MEGA ? 6 : site.tier == Cavern.Tier.MASSIVE ? 4 : 2));
        }
    }

    /** Planning context of one cavern: geometry probes, budget, and the spots big structures already occupy. */
    static final class Site
    {
        final CaveBuilder b;
        final Chamber chamber;
        final Cavern cavern;
        final CavernTemplate template;
        final Cavern.Tier tier;
        final CaveSpace space, formation;
        final double anchor;
        private final List<double[]> taken = new ArrayList<>();

        Site(CaveBuilder b, Chamber chamber, Cavern cavern, CavernTemplate template, Cavern.Tier tier)
        {
            this.b = b;
            this.chamber = chamber;
            this.cavern = cavern;
            this.template = template;
            this.tier = tier;
            this.space = chamber.space();
            this.formation = b.formationSpace(chamber.space());
            this.anchor = chamber.space().maxDisplacement() + 2;
        }

        /** How many of a structure this cavern gets: template rate x size (area, or perimeter for wall features), capped by tier. */
        int count(CavernStructure structure)
        {
            double rate = template.rate(structure);
            if (rate <= 0) return 0;
            double area = chamber.rx() * chamber.rz() / (24.0 * 24.0);
            boolean wall = structure == CavernStructure.WALL_CAVES || structure == CavernStructure.SHELVES;
            double expected = structure.base * rate * (wall ? Math.sqrt(area) * 1.3 : area);
            int n = Mth.floor(expected) + (b.rng.nextDouble() < expected - Mth.floor(expected) ? 1 : 0);
            return Math.min(n, structure.cap(tier));
        }

        /** A random point of the footprint within {@code extent} of the radius, not on top of big structures or lakes. */
        @Nullable
        double[] point(double extent, double clearance)
        {
            for (int attempt = 0; attempt < 8; attempt++)
            {
                double a = b.rng.nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng.nextDouble()) * extent;
                double px = chamber.x() + Math.cos(a) * chamber.rx() * d, pz = chamber.z() + Math.sin(a) * chamber.rz() * d;
                if (free(px, pz, clearance)) return new double[] {px, pz};
            }
            return null;
        }

        boolean free(double px, double pz, double clearance)
        {
            for (double[] t : taken)
            {
                if (Mth.square(px - t[0]) + Mth.square(pz - t[1]) < Mth.square(t[2] + clearance)) return false;
            }
            return cavern.lakeAt(px, pz, 1.15) == null;
        }

        void take(double px, double pz, double radius)
        {
            taken.add(new double[] {px, pz, radius});
        }

        /** Seed point of a patch of this type (with some scatter), or null if the cavern has none. */
        @Nullable
        double[] patchPoint(CavernPatch type)
        {
            List<Cavern.Patch> matching = cavern.patches().stream().filter(p -> p.type() == type).toList();
            if (matching.isEmpty()) return null;
            Cavern.Patch p = matching.get(b.rng.nextInt(matching.size()));
            double a = b.rng.nextDouble() * Math.PI * 2, d = b.rng.nextDouble() * p.radius() * 0.5;
            return new double[] {p.x() + Math.cos(a) * d, p.z() + Math.sin(a) * d};
        }

        // ---------- probes of the real (noise-displaced) cavern surface

        double field(double px, double py, double pz)
        {
            CaveShape.Hall hall = chamber.hall();
            if (hall != null)
            {
                double[] col = new double[4];
                hall.column(px, pz, col);
                return Math.max(hall.vault(col, py) + b.noises.displacement(space, px, py, pz), col[3] - py);
            }
            double d = Double.MAX_VALUE;
            for (CaveShape.Ellipsoid lobe : chamber.lobes()) d = Math.min(d, lobe.distance(px, py, pz));
            return d + b.noises.displacement(space, px, py, pz);
        }

        /**
         * Steps a floor / ceiling scan from the column's middle may take: 200 as ever, more for rooms taller than 400
         * (mega caverns: half the gap plus the wall relief), capped so a probe stays cheap.
         */
        private static int scanLimit(double top, double bottom)
        {
            return (int) Math.min(400, Math.max(200, Math.ceil((top - bottom) / 2) + 24));
        }

        /** First open height above the floor of this column (NaN outside the cavern). */
        double floor(double px, double pz)
        {
            CaveShape.Hall hall = chamber.hall();
            if (hall != null)
            {
                // The floor is exact (no wall noise on it); NaN where the wall stands over it.
                double f = hall.floor(px, pz);
                return field(px, f + 0.5, pz) < 0 ? f : Double.NaN;
            }
            double top = chamber.ceilingAt(px, pz), bottom = chamber.floorAt(px, pz);
            if (Double.isNaN(top) || Double.isNaN(bottom)) return Double.NaN;
            double yy = Math.floor((top + bottom) / 2);
            if (field(px, yy, pz) >= 0) return Double.NaN;
            for (int i = 0, limit = scanLimit(top, bottom); i < limit; i++, yy--)
            {
                if (field(px, yy - 1, pz) >= 0) return yy;
            }
            return Double.NaN;
        }

        /** Lowest solid height of the roof over this column (NaN outside the cavern). */
        double ceiling(double px, double pz)
        {
            CaveShape.Hall hall = chamber.hall();
            if (hall != null)
            {
                // From just under the vault's reach of wall noise, up to the first solid block.
                double c = hall.ceiling(px, pz), f = hall.floor(px, pz);
                if (Double.isNaN(c)) return Double.NaN;
                double yy = Math.floor(Math.max(f, c - space.maxDisplacement() - 2));
                if (field(px, yy + 0.5, pz) >= 0) return Double.NaN;
                for (int i = 0; i < 64; i++, yy++)
                {
                    if (field(px, yy + 1.5, pz) >= 0) return yy + 1;
                }
                return Double.NaN;
            }
            double top = chamber.ceilingAt(px, pz), bottom = chamber.floorAt(px, pz);
            if (Double.isNaN(top) || Double.isNaN(bottom)) return Double.NaN;
            double yy = Math.floor((top + bottom) / 2);
            if (field(px, yy, pz) >= 0) return Double.NaN;
            for (int i = 0, limit = scanLimit(top, bottom); i < limit; i++, yy++)
            {
                if (field(px, yy + 1, pz) >= 0) return yy + 1;
            }
            return Double.NaN;
        }

        /**
         * The wall at this height and bearing: {x, y, z} of the last open block before rock, then the outward unit
         * direction {dx, dz}; null if the ray never enters and leaves the cavern.
         */
        @Nullable
        double[] wall(double angle, double py)
        {
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double max = Math.max(chamber.rx(), chamber.rz()) * 1.8 + 12;
            boolean inside = false;
            for (double d = 0; d < max; d += 1)
            {
                double px = chamber.x() + dx * d, pz = chamber.z() + dz * d;
                if (field(px, py, pz) < 0) inside = true;
                else if (inside) return new double[] {px - dx, py, pz - dz, dx, dz};
            }
            return null;
        }

        /** A height at relative position t between the floor and roof at the centre. */
        double height(double t)
        {
            return Mth.lerp(t, cavern.floor0, cavern.ceiling0);
        }

        boolean belowLakeSurface(double py)
        {
            return !space.hasLake() || py < space.waterLevel - 2;
        }

        void vents(double px, double pz, double spread, int count)
        {
            for (int i = 0; i < count; i++)
            {
                double a = b.rng.nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng.nextDouble()) * spread;
                double vx = px + Math.cos(a) * d, vz = pz + Math.sin(a) * d;
                double f = floor(vx, vz);
                if (Double.isNaN(f)) continue;
                float roll = b.rng.nextFloat();
                ThermalVentType type = roll < 0.4f ? ThermalVentType.BLACK_SMOKER : roll < 0.75f ? ThermalVentType.WHITE_SMOKER : ThermalVentType.MINERAL;
                VentActivity activity = b.rng.nextFloat() < 0.25f ? VentActivity.WEAK : b.rng.nextFloat() < 0.7f ? VentActivity.ACTIVE : VentActivity.STRONG;
                b.vents.add(new CaveSystem.Vent(Mth.floor(vx), Mth.floor(vz), Mth.floor(f), b.range(2, 6), type, activity, space));
            }
        }
    }
}
