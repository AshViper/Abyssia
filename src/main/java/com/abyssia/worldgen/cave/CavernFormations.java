package com.abyssia.worldgen.cave;

import com.abyssia.registry.ModBlocks;
import com.abyssia.worldgen.cave.CavernPlanner.Site;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The structures of a cavern, as layout shapes. Everything is natural geology or life: rock that was left standing,
 * fell, or was worn away; ore and crystal that grew; plants. Shapes are never perfect cylinders or spheres: radii
 * swell and pinch along noise, axes wander, and big formations step, crack and branch.
 */
final class CavernFormations
{
    private CavernFormations() {}

    private static double tierScale(Site s, double small, double large)
    {
        return s.tier == Cavern.Tier.SMALL ? small : s.tier == Cavern.Tier.LARGE ? large : 1.0;
    }

    private static CaveShape.Capsule capsule(CaveSpace space, CaveShape.Kind kind, @Nullable BlockState block, boolean noisy, Vec3 a, Vec3 b, double ra, double rb)
    {
        return new CaveShape.Capsule(space, kind, block, null, noisy, a.x, a.y, a.z, b.x, b.y, b.z, ra, rb, 1.0);
    }

    private static CaveShape.Ellipsoid blob(CaveSpace space, CaveShape.Kind kind, @Nullable BlockState block, @Nullable BlockState host, boolean noisy,
                                            double x, double y, double z, double rx, double ry, double rz)
    {
        return new CaveShape.Ellipsoid(space, kind, block, host, noisy, x, y, z, rx, ry, rz, Double.NaN);
    }

    @Nullable
    private static BlockState ore(Site s)
    {
        return s.b.profile.ores.isEmpty() ? null : s.b.profile.ores.pick(s.b.rng.nextDouble());
    }

    private static BlockState sediment(Site s)
    {
        BlockState state = s.space.environment.floor.pick(s.b.rng.nextDouble());
        return state != null ? state : ModBlocks.CAVE_SEDIMENT.get().defaultBlockState();
    }

    // ---------------------------------------------------------------- 3. floor terrain

    /** Mounds of sediment and rock, low plateaus, hollows, shallow valleys, and rubble fields under collapses. */
    static void floorTerrain(Site s)
    {
        CaveBuilder b = s.b;
        int mounds = s.count(CavernStructure.MOUNDS);
        for (int i = 0; i < mounds; i++)
        {
            double[] p = s.point(0.85, 1);
            if (p == null) continue;
            CavernPatch patch = s.cavern.patchAt(p[0], p[1]);
            if (patch == CavernPatch.WATER) continue;
            double f = s.floor(p[0], p[1]);
            if (Double.isNaN(f)) continue;
            boolean plateau = b.rng.nextFloat() < 0.12f;
            double r = plateau ? b.range(5.0, 10.0) : b.range(1.0, 5.0);
            double h = plateau ? b.range(1.5, 2.5) : b.range(1.0, Math.min(5.0, r + 1.5));
            boolean rocky = patch == CavernPatch.ROCK || patch == CavernPatch.MINERAL || b.rng.nextFloat() < 0.25f;
            BlockState block = rocky ? (patch == CavernPatch.MINERAL ? ModBlocks.MINERAL_CAVE_ROCK.get().defaultBlockState() : null) : sediment(s);
            b.add(blob(s.formation, CaveShape.Kind.FILL, block, null, r > 2.5, p[0], f - h * 0.35, p[1], r * b.range(0.8, 1.2), h, r * b.range(0.8, 1.2)));
            if (!plateau && b.rng.nextFloat() < 0.2f)
            {
                double sr = b.range(0.7, 1.2);
                b.add(blob(s.formation, CaveShape.Kind.FILL, null, null, false, p[0] + b.range(-r / 2, r / 2), f + h * 0.55, p[1] + b.range(-r / 2, r / 2), sr, sr, sr));
            }
            if (r > 3) s.take(p[0], p[1], r * 0.8);
        }

        int hollows = s.count(CavernStructure.HOLLOWS);
        for (int i = 0; i < hollows; i++)
        {
            double[] p = s.point(0.8, 2);
            if (p == null) continue;
            double f = s.floor(p[0], p[1]);
            if (Double.isNaN(f)) continue;
            double r = b.range(3.0, 8.0);
            b.add(blob(s.space, CaveShape.Kind.CARVE, null, null, false, p[0], f - 0.3, p[1], r, b.range(1.2, 2.5), r * b.range(0.7, 1.3)));
            b.add(blob(s.space, CaveShape.Kind.CARVE, null, null, false, p[0] + b.range(-r / 2, r / 2), f - 0.3, p[1] + b.range(-r / 2, r / 2),
                    r * 0.6, b.range(1.0, 2.0), r * 0.6));
        }

        int valleys = s.count(CavernStructure.VALLEYS);
        for (int i = 0; i < valleys; i++)
        {
            double[] p = s.point(0.6, 2);
            if (p == null) continue;
            double a = b.rng.nextDouble() * Math.PI * 2, step = b.range(15.0, 40.0) / 4, radius = b.range(1.8, 3.2);
            Vec3 prev = null;
            for (int k = 0; k < 5; k++)
            {
                a += b.range(-0.4, 0.4);
                double px = p[0] + Math.cos(a) * step * k, pz = p[1] + Math.sin(a) * step * k;
                double f = s.floor(px, pz);
                if (Double.isNaN(f)) break;
                Vec3 cur = new Vec3(px, f - 0.4, pz);
                if (prev != null) b.add(new CaveShape.Capsule(s.space, CaveShape.Kind.CARVE, null, null, false, prev.x, prev.y, prev.z, cur.x, cur.y, cur.z,
                        radius, radius, 0.45));
                prev = cur;
            }
        }

        int rubble = s.count(CavernStructure.RUBBLE);
        for (int i = 0; i < rubble; i++) rubbleField(s);
    }

    /** Rock fallen from the roof: small, medium and large blocks around a scar in the ceiling; mineral-rich, plant-poor. */
    private static void rubbleField(Site s)
    {
        CaveBuilder b = s.b;
        double[] p = s.patchPoint(CavernPatch.ROCK);
        if (p == null) p = s.point(0.7, 6);
        if (p == null) return;
        double f = s.floor(p[0], p[1]), c = s.ceiling(p[0], p[1]);
        if (Double.isNaN(f)) return;
        double radius = b.range(6.0, 14.0) * tierScale(s, 0.6, 0.8);
        int[][] sizes = {{b.range(10, 20), 8, 14}, {b.range(4, 8), 15, 25}, {b.range(1, 3), 28, 45}};
        for (int[] size : sizes)
        {
            for (int k = 0; k < size[0]; k++)
            {
                double a = b.rng.nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng.nextDouble()) * radius;
                double bx = p[0] + Math.cos(a) * d, bz = p[1] + Math.sin(a) * d;
                double bf = s.floor(bx, bz);
                if (Double.isNaN(bf)) continue;
                double br = b.range(size[1], size[2]) / 10.0;
                b.add(blob(s.formation, CaveShape.Kind.FILL, null, null, br > 2, bx, bf + br * 0.2, bz, br, br * b.range(0.7, 1.0), br * b.range(0.8, 1.2)));
                if (br > 1.4) b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.FILL, null, null, false, bx, bf - s.anchor, bz, bx, bf, bz, br * 0.6, br * 0.8, 1.0));
                BlockState ore = ore(s);
                if (ore != null && br > 1.4 && b.rng.nextFloat() < 0.3f)
                {
                    b.add(blob(s.formation, CaveShape.Kind.ORE, ore, null, true, bx + b.range(-br / 2, br / 2), bf + br * 0.4, bz, br * 0.55, br * 0.5, br * 0.55));
                }
            }
        }
        if (!Double.isNaN(c) && !s.space.hasLake())
        {
            b.add(blob(s.space, CaveShape.Kind.CARVE, null, null, true, p[0], c, p[1], radius * 0.7, b.range(2.0, 3.5), radius * 0.7));
        }
        b.sites.add(new CaveSystem.Site(CaveSystem.SiteKind.COLLAPSE, p[0], f, p[1], radius + 3, s.space));
        s.take(p[0], p[1], radius * 0.6);
    }

    // ---------------------------------------------------------------- 4. wall formation

    /**
     * Pockets, short tunnels, ore-lined mineral pockets and overgrown plant chambers opening off the walls: the
     * lit foreground, then shelves and plants, then the openings, then their dark backs. (Flutes and notches on
     * the walls come from the cavern's wall relief.)
     */
    static void wallFormation(Site s)
    {
        CaveBuilder b = s.b;
        int count = s.count(CavernStructure.WALL_CAVES);
        ResourceLocation env = s.space.environmentId;
        for (int i = 0; i < count; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2;
            double y = s.height(b.range(0.08, 0.8));
            if (!s.belowLakeSurface(y + 4)) continue;
            double[] w = s.wall(a, y);
            if (w == null) continue;
            double ox = w[3], oz = w[4];
            double roll = b.rng.nextDouble();
            if (roll < 0.4)
            {
                double r = b.range(2.0, 5.0);
                pocket(s, env, w[0] + ox * r * 0.55, w[1], w[2] + oz * r * 0.55, r, 0.3, 1.0);
            }
            else if (roll < 0.6)
            {
                double r = b.range(1.3, 2.2), length = b.range(6.0, 16.0);
                double side = b.range(-6.0, 6.0);
                Vec3 start = new Vec3(w[0] - ox, w[1], w[2] - oz);
                Vec3 end = new Vec3(w[0] + ox * length - oz * side, w[1] + b.range(-3.0, 3.0), w[2] + oz * length + ox * side);
                CaveSpace tunnel = b.space(CaveSpace.Role.TUNNEL, CaveType.SMALL_SEA_CAVE, null, env, end.x, end.y, end.z, r, CaveSpace.NO_WATER_LEVEL);
                tunnel.glowScale = 0.4;
                CaveTunnelGenerator.tunnel(b, tunnel, start, end, r, r * 0.9, 0.8, 0.9);
                if (b.rng.nextFloat() < 0.4f)
                {
                    // Loops back out a little further along the wall.
                    double[] back = s.wall(a + (b.rng.nextBoolean() ? 1 : -1) * b.range(0.12, 0.3), y + b.range(-2.0, 2.0));
                    if (back != null) CaveTunnelGenerator.tunnel(b, tunnel, end, new Vec3(back[0] - back[3], back[1], back[2] - back[4]), r * 0.9, r, 0.8, 0.9);
                }
            }
            else if (roll < 0.8)
            {
                double r = b.range(2.0, 3.5);
                double px = w[0] + ox * r * 0.5, pz = w[2] + oz * r * 0.5;
                pocket(s, b.net.environmentId("mineral", b.profile), px, w[1], pz, r, 0.5, 0.8);
                BlockState ore = ore(s);
                b.add(blob(s.formation, CaveShape.Kind.ORE, ore != null ? ore : ModBlocks.CAVE_MINERAL_CRUST.get().defaultBlockState(),
                        ModBlocks.MINERAL_CAVE_ROCK.get().defaultBlockState(), true, px, w[1], pz, r + 1.6, (r + 1.6) * 0.8, r + 1.6));
            }
            else
            {
                double r = b.range(3.0, 6.0);
                ResourceLocation lush = b.net.environmentId(b.rng.nextBoolean() ? "forest" : "luminous", b.profile);
                pocket(s, lush, w[0] + ox * r * 0.5, w[1], w[2] + oz * r * 0.5, r, 1.6, 3.0);
            }
        }
    }

    private static void pocket(Site s, ResourceLocation env, double x, double y, double z, double r, double glow, double boost)
    {
        CaveBuilder b = s.b;
        CaveSpace pocket = b.space(CaveSpace.Role.POCKET, CaveType.SMALL_SEA_CAVE, null, env, x, y, z, r, CaveSpace.NO_WATER_LEVEL);
        pocket.glowScale = glow;
        pocket.vegetationBoost = boost;
        b.add(new CaveShape.Ellipsoid(pocket, CaveShape.Kind.CARVE, null, null, true, x, y, z, r, r * 0.75, r * b.range(0.8, 1.2), Double.NaN));
    }

    // ---------------------------------------------------------------- 5. rock pillars

    static void pillars(Site s)
    {
        CaveBuilder b = s.b;
        int plain = s.count(CavernStructure.PILLARS), mineral = s.count(CavernStructure.MINERAL_PILLARS);
        for (int i = 0; i < plain + mineral; i++)
        {
            boolean isMineral = i >= plain;
            double[] p = null;
            if (isMineral)
            {
                p = s.patchPoint(CavernPatch.MINERAL);
                if (p != null && !s.free(p[0], p[1], 6)) p = null;
            }
            if (p == null) p = s.point(isMineral ? 0.7 : 0.8, 6);
            if (p == null) continue;
            double baseR = Math.max(1.5, b.range(1.5, 7.5) * tierScale(s, 0.6, 0.8));
            if (pillar(s, p[0], p[1], baseR, isMineral)) s.take(p[0], p[1], baseR * 2);
        }
    }

    /**
     * A giant pillar from the floor toward the roof: flared where it grows from the floor, thick in the middle,
     * pinched above, and splitting into branches that fuse with the roof; its axis wanders and its radius swells and
     * pinches. In very tall caverns some stop short as free-standing columns. Mineral pillars are ore- and
     * crust-streaked rock.
     */
    static boolean pillar(Site s, double x, double z, double baseR, boolean mineral)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c) || c - f < 10) return false;
        double gap = c - f;
        boolean full = gap <= 60 || b.rng.nextFloat() < 0.3f;
        double bottomY = f - s.anchor, topY = full ? c + s.anchor : f + b.range(25.0, Math.min(55.0, gap - 6));
        int segments = Math.max(4, Mth.ceil((topY - bottomY) / 6));
        int salt = b.nextSalt();
        BlockState block = mineral ? ModBlocks.MINERAL_CAVE_ROCK.get().defaultBlockState() : null;
        Vec3[] axis = new Vec3[segments + 1];
        double[] radius = new double[segments + 1];
        for (int i = 0; i <= segments; i++)
        {
            double t = i / (double) segments;
            double py = Mth.lerp(t, bottomY, topY);
            double visible = Mth.clamp((py - f) / Math.max(1, topY - f), 0.0, 1.0);
            double wobble = baseR * 0.5;
            axis[i] = new Vec3(x + b.noises.path(t * 2, salt) * wobble, py, z + b.noises.path(t * 2, salt + 0.5) * wobble);
            double r = baseR * (0.55 + 0.45 * Math.sin(Math.PI * visible)) * (0.85 + 0.3 * (b.noises.path(visible * 3, salt + 0.25) * 0.5 + 0.5));
            r += baseR * 0.5 * (1 - smooth(visible / 0.12));
            if (full) r += baseR * 0.45 * smooth((visible - 0.85) / 0.15);
            else if (visible > 0.8) r = Mth.lerp((visible - 0.8) / 0.2, r, 0.4);
            radius[i] = Math.max(0.4, r);
        }
        for (int i = 0; i < segments; i++)
        {
            CaveShape.Capsule segment = capsule(s.formation, CaveShape.Kind.FILL, block, true, axis[i], axis[i + 1], radius[i], radius[i + 1]);
            if (!full && i == segments - 1) segment.tip = Direction.UP;
            b.add(segment);
        }
        if (full && b.rng.nextFloat() < 0.45f)
        {
            int branches = b.range(1, 2);
            int from = Math.max(1, Mth.floor(segments * 0.65));
            for (int k = 0; k < branches; k++)
            {
                double a = b.rng.nextDouble() * Math.PI * 2, reach = b.range(4.0, 9.0);
                Vec3 end = new Vec3(axis[from].x + Math.cos(a) * reach, c + s.anchor, axis[from].z + Math.sin(a) * reach);
                b.add(capsule(s.formation, CaveShape.Kind.FILL, block, true, axis[from], end, radius[from] * 0.5, baseR * 0.35 + 0.6));
            }
        }
        if (mineral)
        {
            BlockState ore = ore(s);
            double phase = b.rng.nextDouble() * Math.PI * 2;
            Vec3 prev = null;
            int turns = segments * 3;
            for (int k = 0; k <= turns && ore != null; k++)
            {
                double t = k / (double) turns;
                int i = Math.min(segments, Mth.floor(t * segments));
                double a = phase + k * 0.8, rr = radius[i] * 0.8;
                Vec3 cur = new Vec3(axis[i].x + Math.cos(a) * rr, Mth.lerp(t, f, full ? c : topY), axis[i].z + Math.sin(a) * rr);
                if (prev != null) b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.ORE, ore, null, false, prev.x, prev.y, prev.z, cur.x, cur.y, cur.z, 0.9, 0.9, 1.0));
                prev = cur;
            }
            int crusts = b.range(4, 8);
            for (int k = 0; k < crusts; k++)
            {
                int i = b.rng.nextInt(segments + 1);
                double a = b.rng.nextDouble() * Math.PI * 2, cr = b.range(1.2, 2.0);
                b.add(blob(s.formation, CaveShape.Kind.ORE, ModBlocks.CAVE_MINERAL_CRUST.get().defaultBlockState(), null, true,
                        axis[i].x + Math.cos(a) * radius[i], axis[i].y, axis[i].z + Math.sin(a) * radius[i], cr, cr, cr));
            }
        }
        return true;
    }

    private static double smooth(double t)
    {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- 6. rock shelves

    /** Big ledges along the walls, sometimes stacked into terraces; their tops are dressed by the decorator. */
    static void shelves(Site s)
    {
        CaveBuilder b = s.b;
        int count = s.count(CavernStructure.SHELVES);
        for (int i = 0; i < count; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2;
            double y = s.height(b.range(0.15, 0.75));
            double[] w = s.wall(a, y);
            if (w == null) continue;
            double protrusion = b.range(3.0, 7.0) * tierScale(s, 0.65, 0.85), half = b.range(4.0, 12.0);
            shelf(s, w, protrusion, half);
            if (b.rng.nextFloat() < 0.3f)
            {
                double[] upper = s.wall(a + b.range(-0.15, 0.15), y + b.range(4.0, 8.0));
                if (upper != null) shelf(s, upper, protrusion * 0.7, half * 0.6);
            }
        }
    }

    private static void shelf(Site s, double[] w, double protrusion, double half)
    {
        CaveBuilder b = s.b;
        double tx = -w[4], tz = w[3];
        double cx = w[0] - w[3] * protrusion * 0.25, cz = w[2] - w[4] * protrusion * 0.25;
        CaveShape.Capsule shelf = new CaveShape.Capsule(s.formation, CaveShape.Kind.FILL, null, null, true, cx - tx * half, w[1], cz - tz * half,
                cx + tx * half, w[1] + b.range(-1.0, 1.0), cz + tz * half, protrusion, protrusion * b.range(0.7, 1.1), 0.28);
        shelf.shelf = true;
        b.add(shelf);
    }

    // ---------------------------------------------------------------- 7. ceiling formations

    /**
     * The roof: giant stalactites (stepped, sometimes cracked or branching), clusters of thin rock spikes, rock
     * masses left hanging from a collapse on thin necks, and ore-bearing mineral pendants.
     */
    static void ceiling(Site s)
    {
        CaveBuilder b = s.b;
        int stalactites = s.count(CavernStructure.STALACTITES);
        for (int i = 0; i < stalactites; i++)
        {
            double[] p = s.point(0.8, 3);
            if (p == null) continue;
            double c = s.ceiling(p[0], p[1]), f = s.floor(p[0], p[1]);
            if (Double.isNaN(c) || Double.isNaN(f) || c - f < 14) continue;
            double length = Math.min(Math.max(10.0, (c - f) * b.range(0.3, 0.75)), Math.min(50.0, c - f - 3));
            giantStalactite(s, p[0], p[1], c, length);
        }

        int spikes = s.count(CavernStructure.ROCK_SPIKES);
        for (int i = 0; i < spikes; i++)
        {
            double[] p = s.point(0.85, 1);
            if (p == null) continue;
            int n = b.range(3, 6);
            for (int k = 0; k < n; k++)
            {
                double x = p[0] + b.range(-3.0, 3.0), z = p[1] + b.range(-3.0, 3.0);
                double c = s.ceiling(x, z);
                if (Double.isNaN(c)) continue;
                b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.FILL, null, null, false, x, c + 1.5, z, x + b.range(-1.0, 1.0),
                        c - b.range(4.0, 12.0), z + b.range(-1.0, 1.0), 0.9, 0.25, 1.0));
            }
        }

        int hanging = s.count(CavernStructure.FLOATING_ROCKS);
        for (int i = 0; i < hanging; i++)
        {
            double[] p = s.patchPoint(CavernPatch.OPEN);
            if (p == null || !s.free(p[0], p[1], 6)) p = s.point(0.7, 6);
            if (p == null) continue;
            double c = s.ceiling(p[0], p[1]), f = s.floor(p[0], p[1]);
            if (Double.isNaN(c) || Double.isNaN(f) || c - f < 20) continue;
            double by = c - (c - f) * b.range(0.18, 0.35), br = b.range(3.0, 8.0) * tierScale(s, 0.6, 0.85);
            b.add(blob(s.formation, CaveShape.Kind.FILL, null, null, true, p[0], by, p[1], br, br * 0.6, br * b.range(0.8, 1.2)));
            int necks = b.range(1, 3);
            for (int k = 0; k < necks; k++)
            {
                double ox = b.range(-br * 0.4, br * 0.4), oz = b.range(-br * 0.4, br * 0.4), nr = b.range(0.8, 1.6);
                b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.FILL, null, null, false, p[0] + ox, by + br * 0.4, p[1] + oz,
                        p[0] + ox * 1.3, c + s.anchor, p[1] + oz * 1.3, nr, nr * 1.3, 1.0));
            }
            BlockState ore = ore(s);
            if (b.rng.nextFloat() < 0.4f)
            {
                b.add(blob(s.formation, CaveShape.Kind.ORE, ore != null ? ore : ModBlocks.CAVE_MINERAL_CRUST.get().defaultBlockState(), null, true,
                        p[0] + b.range(-br / 2, br / 2), by - br * 0.35, p[1] + b.range(-br / 2, br / 2), b.range(1.5, 2.5), 1.5, b.range(1.5, 2.5)));
            }
            s.take(p[0], p[1], br);
        }

        // Ore-bearing mineral pendants over mineral ground.
        for (int i = 0; i < stalactites / 3; i++)
        {
            double[] p = s.patchPoint(CavernPatch.MINERAL);
            if (p == null) break;
            double c = s.ceiling(p[0], p[1]);
            BlockState ore = ore(s);
            if (Double.isNaN(c) || ore == null) continue;
            double low = c - b.range(3.0, 7.0);
            b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.FILL, ModBlocks.MINERAL_CAVE_ROCK.get().defaultBlockState(), null, true,
                    p[0], c + s.anchor, p[1], p[0], low, p[1], b.range(2.0, 3.5), b.range(1.2, 2.0), 1.0));
            b.add(blob(s.formation, CaveShape.Kind.ORE, ore, null, true, p[0], low + 1.5, p[1], 1.6, 2.0, 1.6));
        }
    }

    /** A giant stalactite in stepped segments, some cracked open, some sprouting side branches. */
    private static void giantStalactite(Site s, double x, double z, double c, double length)
    {
        CaveBuilder b = s.b;
        double baseR = Mth.clamp(length * b.range(0.08, 0.14), 1.5, 6.0);
        int segments = b.range(4, 6);
        Vec3 prev = new Vec3(x, c + s.anchor, z);
        double r0 = baseR * 1.2;
        Vec3[] joints = new Vec3[segments + 1];
        double[] radii = new double[segments + 1];
        joints[0] = prev;
        radii[0] = r0;
        for (int i = 1; i <= segments; i++)
        {
            double t = i / (double) segments;
            Vec3 cur = new Vec3(x + b.range(-0.8, 0.8) * t, c - length * t, z + b.range(-0.8, 0.8) * t);
            double r1 = i == segments ? 0.45 : baseR * Math.pow(1 - t, 0.8) * 0.78 + 0.4;
            CaveShape.Capsule segment = capsule(s.formation, CaveShape.Kind.FILL, null, false, prev, cur, r0, r1);
            if (i == segments) segment.tip = Direction.DOWN;
            b.add(segment);
            joints[i] = cur;
            radii[i] = r1;
            prev = cur;
            // The next segment starts thinner: a ledge at every joint.
            r0 = r1 * 0.85;
        }
        if (b.rng.nextFloat() < 0.4f && segments > 3)
        {
            int branches = b.range(1, 2);
            for (int k = 0; k < branches; k++)
            {
                int j = b.range(1, segments - 2);
                double a = b.rng.nextDouble() * Math.PI * 2, len = length * b.range(0.25, 0.45);
                Vec3 end = joints[j].add(Math.cos(a) * len * 0.5, -len * 0.8, Math.sin(a) * len * 0.5);
                CaveShape.Capsule branch = capsule(s.formation, CaveShape.Kind.FILL, null, false, joints[j], end, radii[j] * 0.45, 0.35);
                branch.tip = Direction.DOWN;
                b.add(branch);
            }
        }
        if (b.rng.nextFloat() < 0.35f)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, o = baseR * 0.45;
            b.add(new CaveShape.Capsule(s.formation, CaveShape.Kind.CUT, null, null, false, x + Math.cos(a) * o, c + 1, z + Math.sin(a) * o,
                    x + Math.cos(a) * o, c - length * 0.6, z + Math.sin(a) * o, 0.55, 0.55, 1.0));
        }
    }

    // ---------------------------------------------------------------- natural rock bridges

    /** Bridges left by erosion between walls, arching or sagging, uneven, sometimes worn through into a window. */
    static void bridges(Site s)
    {
        CaveBuilder b = s.b;
        int count = s.count(CavernStructure.BRIDGES);
        for (int i = 0; i < count; i++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, y = s.height(b.range(0.3, 0.7));
            double[] wa = s.wall(a, y), wb = s.wall(a + Math.PI + b.range(-0.9, 0.9), y + b.range(-4.0, 4.0));
            if (wa == null || wb == null) continue;
            Vec3 pa = new Vec3(wa[0], wa[1], wa[2]), pb = new Vec3(wb[0], wb[1], wb[2]);
            if (pa.distanceTo(pb) < 12) continue;
            Vec3 ina = pa.add(wa[3] * 3, 0, wa[4] * 3), inb = pb.add(wb[3] * 3, 0, wb[4] * 3);
            Vec3 mid = pa.lerp(pb, 0.5).add(0, b.rng.nextFloat() < 0.35f ? b.range(2.0, 6.0) : -b.range(1.5, 4.0), 0);
            Vec3 control = mid.scale(2).subtract(pa.lerp(pb, 0.5));
            Vec3[] points = {ina, pa, bezier(pa, control, pb, 0.25), mid, bezier(pa, control, pb, 0.75), pb, inb};
            double end = b.range(2.5, 4.0), middle = b.range(1.6, 2.8);
            double[] radii = {end * 1.1, end, Mth.lerp(0.5, end, middle), middle, Mth.lerp(0.5, end, middle), end, end * 1.1};
            for (int k = 0; k < points.length - 1; k++)
            {
                b.add(capsule(s.formation, CaveShape.Kind.FILL, null, true, points[k], points[k + 1], radii[k] * b.range(0.9, 1.1), radii[k + 1] * b.range(0.9, 1.1)));
            }
            if (b.rng.nextFloat() < 0.3f)
            {
                double hole = b.range(1.0, 1.6);
                b.add(blob(s.formation, CaveShape.Kind.CUT, null, null, false, mid.x, mid.y, mid.z, hole, 1.2, hole));
            }
        }
    }

    private static Vec3 bezier(Vec3 a, Vec3 control, Vec3 b, double t)
    {
        return a.scale((1 - t) * (1 - t)).add(control.scale(2 * t * (1 - t))).add(b.scale(t * t));
    }

    // ---------------------------------------------------------------- 8. large mineral formations

    /** Ore deposits big enough to be terrain: a bulging outcrop of host rock on the wall, cored with ore, haloed in crust. */
    static void megaOres(Site s)
    {
        CaveBuilder b = s.b;
        int count = s.count(CavernStructure.MEGA_ORES);
        for (int i = 0; i < count; i++)
        {
            BlockState ore = ore(s);
            if (ore == null) return;
            double[] mp = s.patchPoint(CavernPatch.MINERAL);
            double a = mp != null ? Math.atan2(mp[1] - s.chamber.z(), mp[0] - s.chamber.x()) + b.range(-0.4, 0.4) : b.rng.nextDouble() * Math.PI * 2;
            double[] w = s.wall(a, s.height(b.range(0.15, 0.7)));
            if (w == null) continue;
            double r = b.range(5.0, 11.0) * tierScale(s, 0.6, 0.8);
            BlockState host = ModBlocks.MINERAL_CAVE_ROCK.get().defaultBlockState();
            b.add(blob(s.formation, CaveShape.Kind.FILL, host, null, true, w[0] - w[3] * r * 0.25, w[1], w[2] - w[4] * r * 0.25, r * 0.7, r * 0.6, r * 0.7));
            b.add(blob(s.formation, CaveShape.Kind.ORE, ModBlocks.CAVE_MINERAL_CRUST.get().defaultBlockState(), null, true, w[0], w[1], w[2], r * 1.25, r, r * 1.25));
            b.add(blob(s.formation, CaveShape.Kind.ORE, ore, host, true, w[0], w[1], w[2], r, r * 0.8, r));
        }
    }

    // ---------------------------------------------------------------- 9. crystal formation

    /** Crystal forests: many tilted spires of the cavern's crystal colours, tallest at the heart of the forest. */
    static void crystalForests(Site s)
    {
        CaveBuilder b = s.b;
        int count = s.count(CavernStructure.CRYSTAL_FORESTS);
        for (int i = 0; i < count; i++)
        {
            double[] p = s.patchPoint(CavernPatch.CRYSTAL);
            if (p == null || !s.free(p[0], p[1], 4)) p = s.point(0.7, 6);
            if (p == null) continue;
            double radius = b.range(5.0, 14.0) * tierScale(s, 0.6, 0.8);
            int spires = (int) (b.range(8.0, 25.0) * tierScale(s, 0.5, 0.75));
            CavernTemplate.CrystalColor main = s.cavern.crystal(b.rng.nextDouble()), second = s.cavern.crystal(b.rng.nextDouble());
            for (int k = 0; k < spires && main != null; k++)
            {
                double a = b.rng.nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng.nextDouble()) * radius;
                double x = p[0] + Math.cos(a) * d, z = p[1] + Math.sin(a) * d;
                double f = s.floor(x, z);
                if (Double.isNaN(f)) continue;
                double height = b.range(3.0, 18.0) * (1 - 0.5 * d / radius);
                double width = Mth.clamp(height * b.range(0.1, 0.16), 0.7, 2.6);
                double tilt = b.range(0.0, 0.5), ta = b.rng.nextDouble() * Math.PI * 2;
                crystalSpire(s, x, f, z, height, width, Math.cos(ta) * tilt, Math.sin(ta) * tilt, (k % 3 == 0 ? second : main).crystal());
            }
            s.take(p[0], p[1], radius * 0.6);
        }
    }

    static void crystalSpire(Site s, double x, double floor, double z, double height, double width, double dx, double dz, BlockState block)
    {
        double len = Math.sqrt(dx * dx + 1 + dz * dz);
        Vec3 dir = new Vec3(dx / len, 1 / len, dz / len);
        Vec3 base = new Vec3(x, floor - 2, z);
        Vec3 mid = base.add(dir.scale(height * 0.6 + 2)), tip = base.add(dir.scale(height + 2));
        s.b.add(capsule(s.formation, CaveShape.Kind.FILL, block, false, base, mid, width, width * 0.8));
        s.b.add(capsule(s.formation, CaveShape.Kind.FILL, block, false, mid, tip, width * 0.8, 0.3));
    }

    // ---------------------------------------------------------------- 10. underground water

    static void lakes(Site s)
    {
        int count = s.count(CavernStructure.LAKES);
        for (int i = 0; i < count; i++)
        {
            double[] p = s.patchPoint(CavernPatch.WATER);
            if (p == null) p = s.point(0.5, 8);
            if (p != null) lake(s, p[0], p[1], s.b.range(0.7, 1.0));
        }
    }

    /**
     * A brine lake: an oriented basin (a chain of discs along its long axis) sunk into the floor, 10-60 blocks wide
     * and 10-100 long at most, its surface level with or below the lowest point of its shore so the surface never
     * hangs over open ground.
     */
    static boolean lake(Site s, double x, double z, double scale)
    {
        CaveBuilder b = s.b;
        double maxWidth = Math.min(s.chamber.rx(), s.chamber.rz()) * 0.45, maxLength = Math.max(s.chamber.rx(), s.chamber.rz()) * 0.6;
        if (maxWidth < 4) return false;
        double halfWidth = Mth.clamp(b.range(5.0, 30.0) * scale, 4, maxWidth);
        double halfLength = Mth.clamp(halfWidth * b.range(1.0, 2.2), halfWidth, Math.max(halfWidth, maxLength));
        double angle = b.rng.nextDouble() * Math.PI, cos = Math.cos(angle), sin = Math.sin(angle);
        double centre = s.floor(x, z);
        if (Double.isNaN(centre)) return false;
        double rim = centre;
        for (int k = 0; k < 12; k++)
        {
            double a = k * Math.PI / 6, u = Math.cos(a) * halfLength * 1.05, v = Math.sin(a) * halfWidth * 1.05;
            double f = s.floor(x + u * cos - v * sin, z + u * sin + v * cos);
            if (!Double.isNaN(f)) rim = Math.min(rim, f);
        }
        int surface = Mth.floor(rim) - 1;
        if (!s.belowLakeSurface(surface + 2)) return false;
        double depth = b.range(3.0, 9.0);
        CaveSpace lake = b.space(CaveSpace.Role.POCKET, CaveType.UNDERGROUND_SEA, null, b.net.environmentId("underground_sea", b.profile), x, surface, z, halfWidth,
                CaveSpace.NO_WATER_LEVEL);
        lake.vegetationBoost = 1.3;
        int discs = Math.max(1, Mth.ceil(halfLength / halfWidth));
        for (int i = 0; i < discs; i++)
        {
            double t = discs == 1 ? 0 : i / (double) (discs - 1) * 2 - 1;
            double dx = cos * t * (halfLength - halfWidth), dz = sin * t * (halfLength - halfWidth);
            double r = halfWidth * b.range(0.9, 1.1);
            b.add(new CaveShape.Ellipsoid(lake, CaveShape.Kind.CARVE, null, null, true, x + dx, surface + 0.5, z + dz, r, depth, r, Double.NaN));
        }
        s.cavern.addLake(new Cavern.Lake(x, z, halfLength, halfWidth, cos, sin, surface));
        s.take(x, z, halfWidth * 0.8);
        return true;
    }

    // ---------------------------------------------------------------- gardens and groves

    /** Cave gardens (rings around a crystal or rock centrepiece) and deep-sea groves, in plant patches. */
    static void gardens(Site s)
    {
        CaveBuilder b = s.b;
        int gardens = s.count(CavernStructure.GARDENS), groves = s.count(CavernStructure.GROVES);
        for (int i = 0; i < gardens + groves; i++)
        {
            boolean grove = i >= gardens;
            double[] p = s.patchPoint(CavernPatch.PLANT);
            if (p == null || !s.free(p[0], p[1], 4)) p = s.point(0.75, 6);
            if (p == null) continue;
            double radius = grove ? b.range(8.0, 16.0) : b.range(6.0, 12.0);
            garden(s, p[0], p[1], radius * tierScale(s, 0.7, 0.85), grove, false);
        }
    }

    static boolean garden(Site s, double x, double z, double radius, boolean grove, boolean tall)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z);
        if (Double.isNaN(f)) return false;
        if (!grove)
        {
            CavernTemplate.CrystalColor color = s.cavern.crystal(b.rng.nextDouble());
            if (b.rng.nextBoolean() && color != null)
            {
                int n = b.range(1, 3);
                for (int k = 0; k < n; k++)
                {
                    double a = b.rng.nextDouble() * Math.PI * 2, tilt = b.range(0.0, 0.35);
                    crystalSpire(s, x + b.range(-1.5, 1.5), f, z + b.range(-1.5, 1.5), b.range(3.0, 7.0), b.range(0.8, 1.3), Math.cos(a) * tilt, Math.sin(a) * tilt,
                            color.crystal());
                }
            }
            else
            {
                double r = b.range(1.5, 2.5);
                b.add(blob(s.formation, CaveShape.Kind.FILL, null, null, false, x, f + r * 0.2, z, r, r * 0.8, r));
            }
        }
        s.cavern.addGarden(new Cavern.Garden(x, z, radius, grove, tall));
        s.take(x, z, radius * 0.5);
        return true;
    }

    // ---------------------------------------------------------------- 15. centre landmark

    static void center(Site s, CavernCenter type)
    {
        CaveBuilder b = s.b;
        double x = s.chamber.x() + b.range(-0.12, 0.12) * s.chamber.rx(), z = s.chamber.z() + b.range(-0.12, 0.12) * s.chamber.rz();
        boolean placed = placeCenter(s, type, x, z);
        // A landmark that does not fit (an ancient plant under a low roof, say) leaves a garden rather than nothing.
        if (!placed && type != CavernCenter.DEEP_SEA_GARDEN)
        {
            type = CavernCenter.DEEP_SEA_GARDEN;
            placed = placeCenter(s, type, x, z);
        }
        if (placed)
        {
            s.cavern.setCenter(type, x, z);
            s.take(x, z, 8);
        }
    }

    private static boolean placeCenter(Site s, CavernCenter type, double x, double z)
    {
        CaveBuilder b = s.b;
        return switch (type)
        {
            case ANCIENT_PLANT -> ancientPlant(s, x, z);
            case GIANT_CRYSTAL -> giantCrystal(s, x, z);
            case MINERAL_PILLAR -> pillar(s, x, z, b.range(5.0, 8.0), true);
            case GIANT_PILLAR -> pillar(s, x, z, b.range(6.0, 9.0), false);
            case DEEP_SEA_GARDEN -> garden(s, x, z, b.range(12.0, 18.0), false, false);
            case KELP_GROVE -> garden(s, x, z, b.range(10.0, 16.0), true, true);
            case LAKE -> lake(s, x, z, 1.3);
            case ROCK_ISLAND -> rockIsland(s, x, z);
            case THERMAL_VENTS ->
            {
                double f = s.floor(x, z);
                if (Double.isNaN(f)) yield false;
                double r = b.range(6.0, 9.0);
                b.add(blob(s.formation, CaveShape.Kind.FILL, ModBlocks.VENT_ROCK.get().defaultBlockState(), null, true, x, f - 1, z, r, b.range(2.0, 3.0), r));
                s.vents(x, z, 7, b.range(3, 6));
                yield true;
            }
        };
    }

    /** A great mound of rock rising from the floor like an island, grown over like a grove. */
    private static boolean rockIsland(Site s, double x, double z)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c)) return false;
        double rx = b.range(8.0, 14.0), ry = Math.min(b.range(4.0, 9.0), (c - f) * 0.35);
        b.add(blob(s.formation, CaveShape.Kind.FILL, null, null, true, x, f - ry * 0.4, z, rx, ry, rx * b.range(0.7, 1.2)));
        CavernTemplate.CrystalColor color = s.cavern.crystal(b.rng.nextDouble());
        if (color != null) crystalSpire(s, x + b.range(-3.0, 3.0), f + ry * 0.5, z + b.range(-3.0, 3.0), b.range(4.0, 9.0), 1.2, 0, 0, color.crystal());
        s.cavern.addGarden(new Cavern.Garden(x, z, rx * 0.9, true, false));
        return true;
    }

    /**
     * An ancient deep-sea plant, 20-70 blocks tall and nothing like kelp: a thick, gently curving stem of living
     * wood, a drooping crown of frond masses, and roots running out over the floor and down into the rock.
     */
    private static boolean ancientPlant(Site s, double x, double z)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z), c = s.ceiling(x, z);
        if (Double.isNaN(f) || Double.isNaN(c)) return false;
        double height = Math.min(c - f - 4, b.range(20.0, 70.0));
        if (height < 16) return false;
        BlockState stem = ModBlocks.ANCIENT_STEM.get().defaultBlockState(), root = ModBlocks.ANCIENT_ROOT.get().defaultBlockState();
        BlockState frond = ModBlocks.ANCIENT_FROND.get().defaultBlockState();
        double baseR = Mth.clamp(height * 0.07, 2.2, 4.5);
        double lean = b.rng.nextDouble() * Math.PI * 2, leanAmount = height * b.range(0.08, 0.2);
        int salt = b.nextSalt();
        int segments = b.range(6, 10);
        Vec3 prev = new Vec3(x, f - 3, z);
        double prevR = baseR * 1.4;
        for (int i = 1; i <= segments; i++)
        {
            double t = i / (double) segments;
            Vec3 cur = new Vec3(x + Math.cos(lean) * leanAmount * t * t + b.noises.path(t * 2, salt) * 1.2, f + height * t,
                    z + Math.sin(lean) * leanAmount * t * t + b.noises.path(t * 2, salt + 0.5) * 1.2);
            double r = baseR * (1 - 0.45 * t);
            b.add(capsule(s.formation, CaveShape.Kind.FILL, stem, false, prev, cur, prevR, r));
            prev = cur;
            prevR = r;
        }
        Vec3 top = prev;
        b.add(blob(s.formation, CaveShape.Kind.FILL, frond, null, false, top.x, top.y + 1, top.z, b.range(2.0, 3.5), b.range(1.5, 2.5), b.range(2.0, 3.5)));
        int fronds = b.range(6, 10);
        for (int k = 0; k < fronds; k++)
        {
            double a = k * Math.PI * 2 / fronds + b.range(-0.3, 0.3), len = b.range(5.0, 12.0);
            Vec3 mid = top.add(Math.cos(a) * len * 0.55, len * 0.15, Math.sin(a) * len * 0.55);
            Vec3 end = top.add(Math.cos(a) * len, -len * 0.35, Math.sin(a) * len);
            b.add(capsule(s.formation, CaveShape.Kind.FILL, frond, false, top, mid, 1.3, 1.1));
            b.add(capsule(s.formation, CaveShape.Kind.FILL, frond, false, mid, end, 1.1, 0.6));
        }
        int roots = b.range(5, 9);
        for (int k = 0; k < roots; k++)
        {
            double a = k * Math.PI * 2 / roots + b.range(-0.3, 0.3), len = b.range(6.0, 16.0);
            Vec3 base = new Vec3(x, f + 0.6, z);
            Vec3 mid = new Vec3(x + Math.cos(a) * len * 0.5, f + 0.3, z + Math.sin(a) * len * 0.5);
            Vec3 end = new Vec3(x + Math.cos(a) * len, f - 3, z + Math.sin(a) * len);
            b.add(capsule(s.formation, CaveShape.Kind.FILL, root, false, base, mid, 1.1, 0.8));
            b.add(capsule(s.formation, CaveShape.Kind.FILL, root, false, mid, end, 0.8, 0.5));
        }
        for (int k = 0; k < 2; k++)
        {
            b.add(capsule(s.formation, CaveShape.Kind.FILL, root, false, new Vec3(x + b.range(-1.0, 1.0), f, z + b.range(-1.0, 1.0)),
                    new Vec3(x + b.range(-3.0, 3.0), f - 7, z + b.range(-3.0, 3.0)), 1.0, 0.5));
        }
        return true;
    }

    /** A giant crystal: one great tilted spire of the cavern's colour ringed by leaning satellites. */
    private static boolean giantCrystal(Site s, double x, double z)
    {
        CaveBuilder b = s.b;
        double f = s.floor(x, z), c = s.ceiling(x, z);
        CavernTemplate.CrystalColor main = s.cavern.crystal(b.rng.nextDouble()), second = s.cavern.crystal(b.rng.nextDouble());
        if (Double.isNaN(f) || Double.isNaN(c) || main == null) return false;
        double height = Math.min(c - f - 4, b.range(18.0, 40.0));
        if (height < 10) return false;
        double width = b.range(3.0, 6.0);
        double ta = b.rng.nextDouble() * Math.PI * 2, tilt = b.range(0.0, 0.2);
        crystalSpire(s, x, f, z, height, width, Math.cos(ta) * tilt, Math.sin(ta) * tilt, main.crystal());
        int satellites = b.range(4, 8);
        for (int k = 0; k < satellites; k++)
        {
            double a = b.rng.nextDouble() * Math.PI * 2, d = width + b.range(2.0, 6.0);
            double sx = x + Math.cos(a) * d, sz = z + Math.sin(a) * d;
            double sf = s.floor(sx, sz);
            if (Double.isNaN(sf)) continue;
            double out = b.range(0.25, 0.6);
            crystalSpire(s, sx, sf, sz, height * b.range(0.3, 0.6), width * b.range(0.35, 0.55), Math.cos(a) * out, Math.sin(a) * out,
                    (k % 2 == 0 && second != null ? second : main).crystal());
        }
        return true;
    }
}
