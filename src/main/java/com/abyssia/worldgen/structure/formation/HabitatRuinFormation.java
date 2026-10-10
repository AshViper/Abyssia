package com.abyssia.worldgen.structure.formation;

import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatConnectedBlock;
import com.abyssia.habitat.HabitatLayout;
import com.abyssia.habitat.HabitatLayout.Part;
import com.abyssia.habitat.HabitatLightBlock;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatMode.Face;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.HabitatWindowBlock;
import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.SeabedStructure;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HR01: the wreck of a habitat built with the habitat constructor. A small grid of room / moon pool modules joined by
 * corridors, with one entrance, scan rooms and dead-end stubs on the free faces and the odd second floor, using the
 * real module shells ({@link HabitatBuilder#shellStates}). Every cell is then damaged by a pure function of its
 * position (hatches opened, windows broken, breach holes, rotted panels, dark lamps, doors ajar), the rooms are left
 * flooded with silt and debris, and barrels of loot (and, for the largest ruins, a wreck core) stand inside. The whole
 * layout is drawn up front in a fixed order; each chunk paints its own columns.
 */
public record HabitatRuinFormation(int grid, Span nodes, float stack, Span scanRooms, float stubs, float damage, Span breaches, Span sink,
                                   Mix decay, Mix debris, Mix silt, Mix crate, Optional<ResourceKey<LootTable>> loot, Span crates,
                                   Mix core) implements Formation
{
    public static final String TYPE = "habitat_ruin";
    public static final MapCodec<HabitatRuinFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 3).lenientOptionalFieldOf("grid", 1).forGetter(HabitatRuinFormation::grid),
            Span.CODEC.lenientOptionalFieldOf("nodes", Span.of(1, 1)).forGetter(HabitatRuinFormation::nodes),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("stack", 0f).forGetter(HabitatRuinFormation::stack),
            Span.CODEC.lenientOptionalFieldOf("scan_rooms", Span.of(0, 0)).forGetter(HabitatRuinFormation::scanRooms),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("stubs", 0.25f).forGetter(HabitatRuinFormation::stubs),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("damage", 0.3f).forGetter(HabitatRuinFormation::damage),
            Span.CODEC.lenientOptionalFieldOf("breaches", Span.of(0, 1)).forGetter(HabitatRuinFormation::breaches),
            Span.CODEC.lenientOptionalFieldOf("sink", Span.of(0, 1)).forGetter(HabitatRuinFormation::sink),
            Mix.CODEC.lenientOptionalFieldOf("decay", Mix.EMPTY).forGetter(HabitatRuinFormation::decay),
            Mix.CODEC.lenientOptionalFieldOf("debris", Mix.EMPTY).forGetter(HabitatRuinFormation::debris),
            Mix.CODEC.lenientOptionalFieldOf("silt", Mix.EMPTY).forGetter(HabitatRuinFormation::silt),
            Mix.CODEC.lenientOptionalFieldOf("crate", Mix.EMPTY).forGetter(HabitatRuinFormation::crate),
            ResourceKey.codec(Registries.LOOT_TABLE).lenientOptionalFieldOf("loot").forGetter(HabitatRuinFormation::loot),
            Span.CODEC.lenientOptionalFieldOf("crates", Span.of(1, 1)).forGetter(HabitatRuinFormation::crates),
            Mix.CODEC.lenientOptionalFieldOf("core", Mix.EMPTY).forGetter(HabitatRuinFormation::core)
    ).apply(i, HabitatRuinFormation::new));

    /** Distance between module centres on the grid (13-wide room + 7-long corridor). */
    private static final int PITCH = 20;
    /** Room centre to the first cell of a corridor / leaf beyond it (6 to the wall + 1). */
    private static final int REACH = 7;
    /** Grid steps of the four directions {F, R, -F, -R}: i runs along R, j along F. */
    private static final int[] DI = {0, 1, 0, -1}, DJ = {1, 0, -1, 0};

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public int carveDepth(SeabedStructure definition)
    {
        return (int) Math.ceil(sink.max()) + 4 + 1;
    }

    // ---------------------------------------------------------------- layout

    private record Breach(BlockPos centre, double radius)
    {
        boolean contains(BlockPos pos)
        {
            return centre.distSqr(pos) <= radius * radius;
        }
    }

    /** One placed module: its plan, damage factor, which faces are joined to another module, and its random parts. */
    private static final class Module
    {
        final HabitatPlan plan;
        final boolean upper;
        final double scale;
        /** Footprint columns (inclusive) and the farthest corner from the site centre. */
        final int x0, z0, x1, z1;
        final double far;
        final EnumSet<Face> linked = EnumSet.noneOf(Face.class);
        final List<Breach> breaches = new ArrayList<>();
        final List<BlockPos> crates = new ArrayList<>();
        BlockPos core, coreCeiling;
        Map<BlockPos, BlockState> states;

        Module(Site site, HabitatPlan plan, double factor, boolean upper)
        {
            this.plan = plan;
            this.upper = upper;
            AABB box = plan.box();
            double cx = site.x + 0.5, cz = site.z + 0.5;
            double dist = Math.hypot((box.minX + box.maxX) / 2 - cx, (box.minZ + box.maxZ) / 2 - cz);
            this.scale = (0.6 + 0.8 * Mth.clamp(dist / site.radius(), 0, 1)) * factor;
            this.x0 = Mth.floor(box.minX);
            this.z0 = Mth.floor(box.minZ);
            this.x1 = (int) box.maxX - 1;
            this.z1 = (int) box.maxZ - 1;
            this.far = Math.max(Math.max(Math.hypot(box.minX - cx, box.minZ - cz), Math.hypot(box.maxX - cx, box.minZ - cz)),
                    Math.max(Math.hypot(box.minX - cx, box.maxZ - cz), Math.hypot(box.maxX - cx, box.maxZ - cz)));
        }

        boolean near(Painter p, int margin)
        {
            return x0 - margin <= p.x1 && x1 + margin >= p.x0 && z0 - margin <= p.z1 && z1 + margin >= p.z0;
        }

        boolean room()
        {
            return plan.mode() == HabitatMode.ROOM || plan.mode() == HabitatMode.MOON_POOL;
        }
    }

    /** A module waiting for its nodes to be kept: node a (and b for a corridor between two nodes), leaf = hangs off a. */
    private record Candidate(Module module, int a, int b, int k, boolean leaf) {}

    private static int cell(int[][] at, int i, int j)
    {
        return i < 0 || j < 0 || i >= at.length || j >= at.length ? -1 : at[i][j];
    }

    /** How many grid cells a floor taken from ground {@code g0} fits (buried <= 4 at the highest corner, float <= 5 at the lowest). */
    private static int fits(int[][] wmin, int[][] wmax, int g0)
    {
        int count = 0;
        for (int i = 0; i < wmin.length; i++)
            for (int j = 0; j < wmin.length; j++)
                if (wmax[i][j] - g0 <= 4 && g0 - wmin[i][j] <= 5) count++;
        return count;
    }

    private static Face faceFor(HabitatPlan plan, Direction out)
    {
        for (Face face : Face.values())
            if (plan.outward(face) == out) return face;
        return Face.NEAR;
    }

    /** Mode-specific spots that stay clear of the pool water and the scan console. */
    private static int avoidX(HabitatMode mode, int x, int z)
    {
        int mid = mode.depth / 2;
        if (mode == HabitatMode.MOON_POOL && Math.abs(x) <= 3 && Math.abs(z - mid) <= 3) return x >= 0 ? 4 : -4;
        if (mode == HabitatMode.SCAN_ROOM && x == 0 && z == mid) return 1;
        return x;
    }

    /** The kept modules of this site in a fixed order (empty when it does not fit). */
    private List<Module> layout(Site site)
    {
        // Every random draw happens here, in this order, whatever later gets dropped.
        RandomSource r = site.random();
        Direction f = HabitatPlan.facing(r.nextInt(4)), rr = f.getClockWise();
        Direction[] dirs = {f, rr, f.getOpposite(), rr.getOpposite()};

        int want = Mth.clamp(nodes.sampleInt(r), 1, grid * grid);
        int[][] at = new int[grid][grid];
        for (int[] row : at) Arrays.fill(row, -1);
        int[] ci = new int[want], cj = new int[want];
        boolean[][] edge = new boolean[want][4];
        int n = 1;
        ci[0] = r.nextInt(grid);
        cj[0] = r.nextInt(grid);
        at[ci[0]][cj[0]] = 0;
        // Pre-filter on terrain: per grid cell (centred on the whole grid, only for this test) the ground and 13x13 corner range;
        // the walk only grows into cells that fit the start cell's floor. baseAt uses no randomness, so draws stay site-determined.
        int[][] wmin = new int[grid][grid], wmax = new int[grid][grid], wgr = new int[grid][grid];
        for (int i = 0; i < grid; i++)
            for (int j = 0; j < grid; j++)
            {
                int gi = 2 * i - (grid - 1), gj = 2 * j - (grid - 1);
                int px = site.x + (gi * rr.getStepX() + gj * f.getStepX()) * PITCH / 2;
                int pz = site.z + (gi * rr.getStepZ() + gj * f.getStepZ()) * PITCH / 2;
                int g0 = site.baseAt(px + 0.5, pz + 0.5);
                wgr[i][j] = wmin[i][j] = wmax[i][j] = g0;
                for (int dx = -6; dx <= 6; dx += 12)
                    for (int dz = -6; dz <= 6; dz += 12)
                    {
                        int g = site.baseAt(px + dx + 0.5, pz + dz + 0.5);
                        wmin[i][j] = Math.min(wmin[i][j], g);
                        wmax[i][j] = Math.max(wmax[i][j], g);
                    }
            }
        // Start from the drawn cell unless another cell has more cells around the grid at a fitting height.
        int bestFits = fits(wmin, wmax, wgr[ci[0]][cj[0]]);
        for (int i = 0; i < grid; i++)
            for (int j = 0; j < grid; j++)
                if (fits(wmin, wmax, wgr[i][j]) > bestFits)
                {
                    bestFits = fits(wmin, wmax, wgr[i][j]);
                    at[ci[0]][cj[0]] = -1;
                    ci[0] = i;
                    cj[0] = j;
                    at[i][j] = 0;
                }
        int start = wgr[ci[0]][cj[0]];
        for (int t = 0; t < 80 && n < want; t++)
        {
            int a = r.nextInt(n), k = r.nextInt(4);
            int ni = ci[a] + DI[k], nj = cj[a] + DJ[k];
            if (ni < 0 || nj < 0 || ni >= grid || nj >= grid || at[ni][nj] >= 0) continue;
            if (wmax[ni][nj] - start > 4 || start - wmin[ni][nj] > 5) continue;
            ci[n] = ni;
            cj[n] = nj;
            at[ni][nj] = n;
            edge[a][k] = true;
            edge[n][(k + 2) % 4] = true;
            n++;
        }
        // Neighbouring nodes without a corridor get one 30% of the time.
        for (int a = 0; a < n; a++)
            for (int k = 0; k < 2; k++)
            {
                int b = cell(at, ci[a] + DI[k], cj[a] + DJ[k]);
                if (b >= 0 && !edge[a][k] && r.nextFloat() < 0.3f)
                {
                    edge[a][k] = true;
                    edge[b][k + 2] = true;
                }
            }
        boolean[] pool = new boolean[n], stacked = new boolean[n];
        for (int a = 0; a < n; a++) pool[a] = r.nextFloat() < 0.25f;

        // Leaves: faces with no corridor and no node beyond. One entrance (facing out of the grid if it can), scan rooms, stubs.
        List<int[]> free = new ArrayList<>(), outer = new ArrayList<>();
        for (int a = 0; a < n; a++)
            for (int k = 0; k < 4; k++)
            {
                int ni = ci[a] + DI[k], nj = cj[a] + DJ[k];
                if (edge[a][k] || cell(at, ni, nj) >= 0) continue;
                int[] e = {a, k};
                free.add(e);
                if (ni < 0 || nj < 0 || ni >= grid || nj >= grid) outer.add(e);
            }
        int[] entrance = null;
        if (!free.isEmpty())
        {
            List<int[]> from = outer.isEmpty() ? free : outer;
            entrance = from.get(r.nextInt(from.size()));
            free.remove(entrance);
        }
        List<int[]> scans = new ArrayList<>(), dead = new ArrayList<>();
        for (int s = scanRooms.sampleInt(r); s > 0 && !free.isEmpty(); s--) scans.add(free.remove(r.nextInt(free.size())));
        for (int[] e : free)
            if (r.nextFloat() < stubs) dead.add(e);
        for (int a = 0; a < n; a++)
        {
            boolean roll = r.nextFloat() < stack;
            stacked[a] = roll && !pool[a];
        }
        int sunk = Math.max(0, sink.sampleInt(r));

        // Module centres: the whole grid centred on the site, the same cells the terrain pre-check above measured.
        int[] cx = new int[n], cz = new int[n], ground = new int[n];
        for (int a = 0; a < n; a++)
        {
            int gi = 2 * ci[a] - (grid - 1), gj = 2 * cj[a] - (grid - 1);
            cx[a] = site.x + (gi * rr.getStepX() + gj * f.getStepX()) * PITCH / 2;
            cz[a] = site.z + (gi * rr.getStepZ() + gj * f.getStepZ()) * PITCH / 2;
            ground[a] = site.baseAt(cx[a] + 0.5, cz[a] + 0.5);
        }
        // Floor = the candidate (ground - sunk) that fits the most 13x13 footprints: buried <= 4 at the highest corner, float <= 5 at the lowest.
        int[] gmin = new int[n], gmax = new int[n];
        for (int a = 0; a < n; a++)
        {
            gmin[a] = gmax[a] = ground[a];
            for (int dx = -6; dx <= 6; dx += 12)
                for (int dz = -6; dz <= 6; dz += 12)
                {
                    int g = site.baseAt(cx[a] + dx + 0.5, cz[a] + dz + 0.5);
                    gmin[a] = Math.min(gmin[a], g);
                    gmax[a] = Math.max(gmax[a], g);
                }
        }
        // The node the floor was taken from always stays, so a placed ruin is never empty on rough ground.
        int base = ground[0] - sunk, bestFit = -1, anchor = 0;
        for (int c = 0; c < n; c++)
        {
            int cand = ground[c] - sunk, fit = 0;
            for (int a = 0; a < n; a++)
                if (gmax[a] - cand <= 4 && cand - gmin[a] <= 5) fit++;
            if (fit > bestFit)
            {
                bestFit = fit;
                base = cand;
                anchor = c;
            }
        }

        // Never below the depth the cave check covers (carveDepth), however low the anchor node sits.
        base = Math.max(base, site.baseY - (int) Math.ceil(sink.max()) - 4);
        int footprint = site.radius() - 3;
        List<Candidate> all = new ArrayList<>();
        Module[] nodeModule = new Module[n];
        boolean[] keep = new boolean[n];
        for (int a = 0; a < n; a++)
        {
            HabitatMode mode = pool[a] ? HabitatMode.MOON_POOL : HabitatMode.ROOM;
            nodeModule[a] = new Module(site, new HabitatPlan(mode, new BlockPos(cx[a], base, cz[a]).relative(f, -6), f, false), 1.0, false);
            keep[a] = (a == anchor || gmax[a] - base <= 4 && base - gmin[a] <= 5) && nodeModule[a].far <= footprint;
        }
        for (int a = 0; a < n; a++)
            if (stacked[a])
            {
                HabitatPlan lower = nodeModule[a].plan;
                all.add(new Candidate(new Module(site, new HabitatPlan(HabitatMode.ROOM, lower.origin().above(HabitatPlan.STACK_STEP), f, false), 1.6, true), a, -1, -1, false));
            }
        for (int a = 0; a < n; a++)
            for (int k = 0; k < 2; k++)
            {
                int b = cell(at, ci[a] + DI[k], cj[a] + DJ[k]);
                if (b < 0 || !edge[a][k]) continue;
                BlockPos origin = new BlockPos(cx[a], base, cz[a]).relative(dirs[k], REACH);
                all.add(new Candidate(new Module(site, new HabitatPlan(HabitatMode.CORRIDOR, origin, dirs[k], false), 1.0, false), a, b, k, false));
            }
        if (entrance != null) all.add(leaf(site, HabitatMode.ENTRANCE, entrance, cx, cz, base, dirs, 0.7));
        for (int[] e : scans) all.add(leaf(site, HabitatMode.SCAN_ROOM, e, cx, cz, base, dirs, 1.0));
        for (int[] e : dead) all.add(leaf(site, HabitatMode.CORRIDOR, e, cx, cz, base, dirs, 1.4));

        List<Module> out = new ArrayList<>();
        for (int a = 0; a < n; a++)
            if (keep[a]) out.add(nodeModule[a]);
        for (Candidate c : all)
        {
            if (!keep[c.a] || c.b >= 0 && !keep[c.b] || c.module.far > footprint) continue;
            Module m = c.module;
            if (c.b >= 0)
            {
                m.linked.add(Face.NEAR);
                m.linked.add(Face.FAR);
                nodeModule[c.a].linked.add(faceFor(nodeModule[c.a].plan, dirs[c.k]));
                nodeModule[c.b].linked.add(faceFor(nodeModule[c.b].plan, dirs[(c.k + 2) % 4]));
            }
            else if (c.leaf)
            {
                m.linked.add(Face.NEAR);
                nodeModule[c.a].linked.add(faceFor(nodeModule[c.a].plan, dirs[c.k]));
            }
            out.add(m);
        }
        if (out.isEmpty()) return out;
        // Under a low ceiling the second floors go first; without room for one storey there is no ruin.
        if (site.clampHeight(base, 2 * HabitatPlan.STACK_STEP) < 2 * HabitatPlan.STACK_STEP) out.removeIf(m -> m.upper);
        if (out.isEmpty() || site.clampHeight(base, HabitatPlan.STACK_STEP) < HabitatPlan.STACK_STEP) return List.of();

        // Per-module randoms: breach spheres and barrels in the rooms.
        for (Module m : out)
        {
            HabitatMode mode = m.plan.mode();
            if (mode != HabitatMode.ROOM && mode != HabitatMode.MOON_POOL && mode != HabitatMode.SCAN_ROOM) continue;
            int hw = HabitatLayout.halfWidth(mode), d = mode.depth;
            for (int s = breaches.sampleInt(r); s > 0; s--)
            {
                boolean roof = r.nextFloat() < 0.6f;
                int face = r.nextInt(4), u = r.nextInt(2 * hw - 1) - (hw - 1), w = r.nextInt(d - 2) + 1;
                int y = 1 + r.nextInt(Math.max(1, mode.height - 2));
                double radius = 2.0 + r.nextFloat() * 2.0;
                BlockPos centre = roof ? m.plan.at(u, mode.height - 1, w) : switch (face)
                {
                    case 0 -> m.plan.at(u, y, 0);
                    case 1 -> m.plan.at(u, y, d - 1);
                    case 2 -> m.plan.at(-hw, y, w);
                    default -> m.plan.at(hw, y, w);
                };
                m.breaches.add(new Breach(centre, radius));
            }
            for (int s = crates.sampleInt(r); s > 0; s--)
            {
                int x = r.nextInt(2 * hw - 1) - (hw - 1), z = r.nextInt(d - 2) + 1;
                m.crates.add(m.plan.at(avoidX(mode, x, z), 1, z));
            }
        }
        // The wreck core: on the floor of the most damaged room, whose ceiling above it is always gone.
        if (!core.isEmpty())
        {
            Module worst = null;
            for (Module m : out)
                if (m.room() && (worst == null || m.scale > worst.scale)) worst = m;
            if (worst != null)
            {
                HabitatMode mode = worst.plan.mode();
                int hw = HabitatLayout.halfWidth(mode);
                int x = r.nextInt(2 * hw - 1) - (hw - 1), z = r.nextInt(mode.depth - 2) + 1;
                x = avoidX(mode, x, z);
                worst.core = worst.plan.at(x, 1, z);
                worst.coreCeiling = worst.plan.at(x, mode.height - 1, z);
            }
        }
        return out;
    }

    private static Candidate leaf(Site site, HabitatMode mode, int[] e, int[] cx, int[] cz, int base, Direction[] dirs, double factor)
    {
        BlockPos origin = new BlockPos(cx[e[0]], base, cz[e[0]]).relative(dirs[e[1]], REACH);
        return new Candidate(new Module(site, new HabitatPlan(mode, origin, dirs[e[1]], false), factor, false), e[0], -1, e[1], true);
    }

    // ---------------------------------------------------------------- damage

    /** Whether a cell of the module is destroyed: a pure function of its position, the same from every chunk. */
    private boolean gone(Painter p, Module m, BlockPos pos, int ly)
    {
        if (pos.equals(m.coreCeiling)) return true;
        if (m.core != null && pos.equals(m.core.below())) return false; // the core never floats
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (Breach b : m.breaches)
            if (b.contains(pos) && (ly > 0 || p.hash(x, y, z, 47) < 0.5)) return true;
        int height = m.plan.mode().height;
        double h = ly == 0 ? 0.2 : 0.4 + 0.9 * ly / (double) (height - 1);
        double c = Mth.clamp(1 + 1.3 * p.noise(x * 0.09, y * 0.09, z * 0.09), 0, 2.2);
        return p.hash(x, y, z, 41) < damage * m.scale * h * c;
    }

    private static Face hatchFace(HabitatMode mode, int x, int z)
    {
        for (Face face : mode.connectors)
            for (int[] col : HabitatPlan.panelColumns(mode, face))
                if (col[0] == x && col[1] == z) return face;
        return null;
    }

    /** The surviving blocks of one module, by position (before the connected-texture pass). */
    private Map<BlockPos, BlockState> states(Painter p, Module m)
    {
        HabitatPlan plan = m.plan;
        HabitatMode mode = plan.mode();
        int hw = HabitatLayout.halfWidth(mode);
        Map<BlockPos, BlockState> shell = HabitatBuilder.shellStates(plan);
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (int y = 0; y < mode.height; y++)
            for (int z = 0; z < mode.depth; z++)
                for (int x = -hw; x <= hw; x++)
                {
                    Part part = HabitatLayout.partAt(mode, x, y, z);
                    if (part == Part.KEEP || part == Part.AIR || part == Part.WATER || part == Part.CONSOLE || part == Part.DOOR_UPPER) continue;
                    BlockPos pos = plan.at(x, y, z);
                    BlockState state = shell.get(pos);
                    if (state == null) continue;
                    int wx = pos.getX(), wy = pos.getY(), wz = pos.getZ();
                    switch (part)
                    {
                        case DOOR_LOWER ->
                        {
                            // Half the doors are gone, the rest hang open; none where the floor under them is.
                            if (gone(p, m, plan.at(x, 0, z), 0) || p.hash(wx, wy, wz, 45) < 0.5) continue;
                            BlockState upper = shell.get(pos.above());
                            if (upper == null || p.inReach(wx, wz) && !(p.open(wx, wy, wz) && p.open(wx, wy + 1, wz))) continue;
                            out.put(pos, state.setValue(DoorBlock.OPEN, true));
                            out.put(pos.above(), upper.setValue(DoorBlock.OPEN, true));
                            continue;
                        }
                        case HATCH ->
                        {
                            Face face = hatchFace(mode, x, z);
                            if (face != null && m.linked.contains(face) || p.hash(wx, wy, wz, 44) < 0.25) continue;
                            out.put(pos, state);
                            continue;
                        }
                        case WINDOW ->
                        {
                            if (p.hash(wx, wy, wz, 46) < 0.85) continue;
                        }
                        default -> {}
                    }
                    if (gone(p, m, pos, y)) continue;
                    if (part == Part.LIGHT) state = state.setValue(HabitatLightBlock.LIT, false);
                    else if ((part == Part.FLOOR || part == Part.TRIM || part == Part.WALL || part == Part.CEILING)
                            && !decay.isEmpty() && p.hash(wx, wy, wz, 43) < 0.2)
                        state = p.pick(decay, wx, wy, wz, 4);
                    out.put(pos, state);
                }
        return out;
    }

    /** The connected-texture booleans of a hull or window block, from what is really next to it in the ruin. */
    private static BlockState linked(Map<BlockPos, BlockState> all, BlockPos pos, BlockState state)
    {
        Block block = state.getBlock();
        if (!(block instanceof HabitatConnectedBlock) && !(block instanceof HabitatWindowBlock)) return state;
        return HabitatWindowBlock.connected(state, d ->
        {
            BlockState next = all.get(pos.relative(d));
            return next != null && next.getBlock() == block;
        });
    }

    // ---------------------------------------------------------------- painting

    @Override
    public void paint(Site site, Painter p)
    {
        if (p.isEmpty()) return;
        List<Module> mods = layout(site);
        List<Module> near = new ArrayList<>();
        for (Module m : mods)
            if (m.near(p, 4)) near.add(m);
        if (near.isEmpty()) return;
        // 1. interiors, hatches and doors: open whatever rock is in the way (a column stops where it meets air or bedrock)
        for (Module m : near)
        {
            HabitatMode mode = m.plan.mode();
            int hw = HabitatLayout.halfWidth(mode);
            for (int z = 0; z < mode.depth; z++)
                for (int x = -hw; x <= hw; x++)
                {
                    BlockPos col = m.plan.at(x, 0, z);
                    if (!p.inReach(col.getX(), col.getZ())) continue;
                    for (int y = 0; y < mode.height; y++)
                    {
                        Part part = HabitatLayout.partAt(mode, x, y, z);
                        boolean hatch = false;
                        if (part == Part.HATCH)
                        {
                            Face face = hatchFace(mode, x, z);
                            hatch = face != null && m.linked.contains(face) && y >= 1 && y <= 3;
                        }
                        if (part != Part.AIR && part != Part.WATER && part != Part.CONSOLE && part != Part.DOOR_LOWER && part != Part.DOOR_UPPER && !hatch) continue;
                        int wy = col.getY() + y;
                        if (p.get(col.getX(), wy, col.getZ()).isAir()) break;
                        if (!p.open(col.getX(), wy, col.getZ()) && !p.carve(col.getX(), wy, col.getZ())) break;
                    }
                }
        }
        Map<BlockPos, BlockState> all = new HashMap<>();
        for (Module m : near)
        {
            m.states = states(p, m);
            all.putAll(m.states);
        }

        // 2. shells
        for (Module m : near)
            for (Map.Entry<BlockPos, BlockState> e : m.states.entrySet())
            {
                BlockPos pos = e.getKey();
                if (p.inReach(pos.getX(), pos.getZ())) p.place(pos.getX(), pos.getY(), pos.getZ(), linked(all, pos, e.getValue()));
            }
        // 3. barrels and the core
        for (Module m : near)
        {
            if (!crate.isEmpty())
                for (BlockPos c : m.crates)
                    if (!c.equals(m.core) && p.inReach(c.getX(), c.getZ()) && m.states.containsKey(c.below()))
                        p.container(c.getX(), c.getY(), c.getZ(), crate.pick(p.hash(c.getX(), c.getY(), c.getZ(), 51)), loot.orElse(null), site.seed ^ c.asLong());
            if (m.core != null && p.inReach(m.core.getX(), m.core.getZ())) p.set(m.core.getX(), m.core.getY(), m.core.getZ(), core.pick(0));
        }
        // 4. silt over the interior floors, then drifted against the outer walls
        for (Module m : near)
        {
            if (m.upper || silt.isEmpty()) continue;
            HabitatMode mode = m.plan.mode();
            int hw = HabitatLayout.halfWidth(mode);
            for (int z = 1; z < mode.depth - 1; z++)
                for (int x = -hw + 1; x < hw; x++)
                {
                    BlockPos c = m.plan.at(x, 1, z);
                    int wx = c.getX(), wy = c.getY(), wz = c.getZ();
                    if (!p.inReach(wx, wz) || HabitatLayout.partAt(mode, x, 1, z) != Part.AIR || p.open(wx, wy - 1, wz)) continue;
                    int k = Math.min(2, (int) Math.floor((0.5 + 0.5 * p.noise(wx * 0.25, wz * 0.25)) * 3));
                    for (int i = 0; i < k; i++) p.fill(wx, wy + i, wz, p.pick(silt, wx, wy + i, wz, 5));
                }
        }
        for (int x = p.x0; x <= p.x1; x++)
            for (int z = p.z0; z <= p.z1; z++)
            {
                if (!p.inReach(x, z)) continue;
                double outside = Double.MAX_VALUE;
                for (Module m : mods)
                    if (m.near(p, 3))
                        outside = Math.min(outside, Math.hypot(Math.max(Math.max(m.x0 - x, x - m.x1), 0), Math.max(Math.max(m.z0 - z, z - m.z1), 0)));
                if (outside == 0 || outside >= 3) continue;
                if (!silt.isEmpty())
                {
                    double n = 0.5 + 0.5 * p.noise(x * 0.25, z * 0.25);
                    int k = (int) Math.round(2.2 * (1 - outside / 3.0) * n), surface = p.floor(x, z);
                    for (int i = 0; i < k; i++) p.fill(x, surface + i, z, p.pick(silt, x, surface + i, z, 5));
                }
                // 5b. a little rubble in the ring right around the walls
                if (outside <= 1 && !debris.isEmpty() && p.hash(x, 0, z, 50) < 0.08)
                {
                    int surface = p.floor(x, z);
                    p.fill(x, surface, z, p.pick(debris, x, surface, z, 6));
                }
            }
        // 5. fallen ceiling: broken roof cells drop rubble on the floor below
        if (!debris.isEmpty())
            for (Module m : near)
            {
                HabitatMode mode = m.plan.mode();
                int hw = HabitatLayout.halfWidth(mode);
                for (int z = 1; z < mode.depth - 1; z++)
                    for (int x = -hw + 1; x < hw; x++)
                    {
                        BlockPos roof = m.plan.at(x, mode.height - 1, z);
                        Part part = HabitatLayout.partAt(mode, x, mode.height - 1, z);
                        if ((part != Part.CEILING && part != Part.LIGHT) || m.states.containsKey(roof)) continue;
                        BlockPos floor = m.plan.at(x, 0, z);
                        if (!p.inReach(floor.getX(), floor.getZ()) || !m.states.containsKey(floor) || p.hash(roof.getX(), roof.getY(), roof.getZ(), 49) >= 0.15) continue;
                        for (int y = 1; y <= 3; y++)
                            if (p.fill(floor.getX(), floor.getY() + y, floor.getZ(), p.pick(debris, floor.getX(), floor.getY() + y, floor.getZ(), 6))) break;
                    }
            }
        // 6. legs under the rooms: the four columns of HabitatBuilder.legs (lx = +-(hw - 1), lz = 1 / depth - 2, from plan.at(lx, -1, lz)),
        // here at most 12 long, a third of them missing; the second floors have none.
        for (Module m : near)
        {
            if (m.upper || !HabitatBuilder.hasLegs(m.plan.mode())) continue;
            HabitatMode mode = m.plan.mode();
            int lx = HabitatLayout.halfWidth(mode) - 1;
            for (int sx : new int[]{-lx, lx})
                for (int lz : new int[]{1, mode.depth - 2})
                {
                    BlockPos top = m.plan.at(sx, -1, lz);
                    if (!p.inReach(top.getX(), top.getZ()) || p.hash(top.getX(), top.getY(), top.getZ(), 48) < 0.3) continue;
                    p.root(top.getX(), top.getY(), top.getZ(), HabitatBuilder.support(true), 12);
                }
        }
    }
}
