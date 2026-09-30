package com.abyssia.worldgen.cave;

import com.abyssia.Config;
import com.abyssia.block.HangingPlantBlock;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import javax.annotation.Nullable;

/**
 * Decorates the deep ocean's terrain caves: the cheese caverns and spaghetti tunnels its own density function leaves
 * below the seabed. They are not part of the planned cave network, so nothing else dresses them. Each takes the look
 * and life of its biome's cave environment: sediment settled on floors (heaped into mounds in the big ones), mineral
 * accent patches on the bare rock, crystals, stalactites and stalagmites, moss, floor, wall and ceiling plants, and in
 * the big caverns stands of giant kelp rising toward curtains of roots and vines hanging from the roof.
 * <p>
 * Walls keep the biome's own rock: only the chunk itself is classified, so repainting whole walls would leave seams
 * where a wall meets a chunk border. Floors and roofs are judged within their column. The planned structures of the
 * massive caverns (pillars, bridges, landmarks) need a layout and stay theirs.
 */
final class TerrainCaveDecorator
{
    /** Open runs at least this tall count as a cavern: long speleothems, tall plants, mounds, kelp and root forests. */
    private static final int LARGE_RUN = 14;
    /** Kelp and root clusters and floor mounds sit on jittered grids of these sizes. */
    private static final int CELL = 10, MOUND_CELL = 9;
    private static final int MAX_KELP = 40;
    /** Undergrowth multiplier in big terrain caverns, and how thick kelp stands and root curtains grow there. */
    private static final double VEGETATION_BOOST = 2.0, KELP = 2.0, HANGING = 1.7;
    private static final Direction[] EXPOSED = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private TerrainCaveDecorator() {}

    static void decorate(CaveNetwork network, ChunkAccess chunk)
    {
        ChunkPos cp = chunk.getPos();
        int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
        CaveProfile profile = network.profile(cx, cz);
        if (profile == null) return;
        ResourceLocation envId = network.luminous(profile, cx, cz) ? network.environmentId("luminous", profile) : profile.environment;
        CaveEnvironment env = network.environment(envId, profile);
        CaveSpace small = space(CaveType.MEDIUM_SEA_CAVE, envId, env, profile, cx, cz, 8);
        CaveSpace large = space(CaveType.LARGE_ABYSSAL_CAVE, envId, env, profile, cx, cz, 28);
        // Environment densities are tuned for side caves; a big open cavern needs more to not read as bare.
        small.vegetationBoost = 1.2;
        large.vegetationBoost = VEGETATION_BOOST;
        long t = System.nanoTime();
        CaveChunk ctx = CaveChunk.terrain(network, chunk, small, large, LARGE_RUN);
        t = phase(0, t);
        if (ctx == null) return;
        floors(ctx);
        t = phase(1, t);
        CaveDecorationGenerator.crystals(ctx);
        t = phase(2, t);
        CaveDecorationGenerator.speleothems(ctx);
        t = phase(3, t);
        forests(ctx);
        t = phase(4, t);
        // Both spaces share the environment: skip the giants' scan where it grows none.
        if (!env.giantPlants.isEmpty()) CaveVegetationGenerator.giants(ctx);
        t = phase(5, t);
        CaveVegetationGenerator.grow(ctx);
        phase(6, t);
    }

    private static final String[] PHASES = {"classify", "floors", "crystals", "speleothems", "forests", "giants", "plants"};
    private static final java.util.concurrent.atomic.AtomicLongArray PHASE_NANOS = new java.util.concurrent.atomic.AtomicLongArray(PHASES.length);

    private static long phase(int index, long start)
    {
        long now = System.nanoTime();
        PHASE_NANOS.addAndGet(index, now - start);
        return now;
    }

    /** Time per step, averaged over {@code chunks} chunks, for the debug stats. */
    static String stats(long chunks)
    {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < PHASES.length; i++) s.append(i == 0 ? "" : ", ").append(PHASES[i]).append(String.format(" %.2f", PHASE_NANOS.get(i) / 1e6 / chunks));
        return s.toString();
    }

    static void resetStats()
    {
        for (int i = 0; i < PHASES.length; i++) PHASE_NANOS.set(i, 0);
    }

    private static CaveSpace space(CaveType type, ResourceLocation envId, CaveEnvironment env, CaveProfile profile, int x, int z, double radius)
    {
        return new CaveSpace(CaveSpace.Role.CHAMBER, type, null, envId, env, profile, 0, 0, 0, 0, 9, CaveSpace.NO_WATER_LEVEL,
                x, 0, z, radius, envId.hashCode());
    }

    // ---------------------------------------------------------------- floors and bare rock

    /** Sediment on floors, mounds of it in the big caves, and mineral accent patches on exposed walls and roofs. */
    static void floors(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                int x = ctx.x0 + lx, z = ctx.z0 + lz;
                for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null) continue;
                    CaveEnvironment env = space.environment;
                    if (!ctx.carvedHere(lx, y - 1, lz) && ctx.get(lx, y - 1, lz).is(ModTags.VEIN_REPLACEABLE) && !env.floor.isEmpty())
                    {
                        ctx.set(lx, y - 1, lz, env.floor.pick(noises.mottle(x, y - 1, z, 2)));
                        // A second layer where the floor is thick enough that it cannot show on a roof below.
                        if (ctx.get(lx, y - 2, lz).is(ModTags.VEIN_REPLACEABLE) && !ctx.open(lx, y - 3, lz))
                        {
                            ctx.set(lx, y - 2, lz, env.floor.pick(noises.mottle(x, y - 2, z, 2)));
                        }
                        if (space.isCavern())
                        {
                            int built = mound(ctx, lx, y, lz, x, z, env);
                            y += built;
                            if (built > 0) continue;
                        }
                    }
                    accents(ctx, lx, y, lz, x, z, env);
                }
            }
        }
    }

    /** Mineral crust and layered rock in patches on the rock around an open block (neighbours inside this chunk). */
    private static void accents(CaveChunk ctx, int lx, int y, int lz, int x, int z, CaveEnvironment env)
    {
        float accent = env.geology.accentChance();
        if (accent <= 0 || env.accents.isEmpty()) return;
        for (Direction d : EXPOSED)
        {
            int nx = lx + d.getStepX(), ny = y + d.getStepY(), nz = lz + d.getStepZ();
            if (!ctx.inChunk(nx, nz) || ctx.carvedHere(nx, ny, nz) || !ctx.get(nx, ny, nz).is(ModTags.VEIN_REPLACEABLE)) continue;
            int ax = x + d.getStepX(), az = z + d.getStepZ();
            if (ctx.noises.patch(ax, ny, az, 5.3) > 1 - accent * 2.2) ctx.set(nx, ny, nz, env.accents.pick(ctx.noises.mottle(ax, ny, az, 4)));
        }
    }

    /**
     * A low heap of sediment on the floor of a big cave (2-10 blocks across, 1-4 high), from a jittered grid of mound
     * centres, so the floor rolls instead of lying flat. Returns how many blocks it raised this column.
     */
    private static int mound(CaveChunk ctx, int lx, int y, int lz, int x, int z, CaveEnvironment env)
    {
        CaveNoises noises = ctx.noises;
        int gx = Math.floorDiv(x, MOUND_CELL), gz = Math.floorDiv(z, MOUND_CELL);
        double height = 0;
        for (int i = -1; i <= 1; i++)
        {
            for (int j = -1; j <= 1; j++)
            {
                int cx = gx + i, cz = gz + j;
                if (noises.hash(cx, 0, cz, 611) >= 0.4) continue;
                double mx = cx * MOUND_CELL + noises.hash(cx, 1, cz, 611) * MOUND_CELL, mz = cz * MOUND_CELL + noises.hash(cx, 2, cz, 611) * MOUND_CELL;
                double r = 1.5 + noises.hash(cx, 3, cz, 611) * 3.5;
                double q = (Mth.square(x + 0.5 - mx) + Mth.square(z + 0.5 - mz)) / (r * r);
                if (q < 1) height = Math.max(height, (1 + noises.hash(cx, 4, cz, 611) * Math.min(3.0, r - 1)) * (1 - q));
            }
        }
        int h = Mth.floor(height + 0.5), built = 0;
        while (built < h && ctx.carvedHere(lx, y + built, lz) && ctx.water(lx, y + built, lz) && ctx.carvedHere(lx, y + built + 2, lz))
        {
            ctx.set(lx, y + built, lz, env.floor.pick(noises.mottle(x, y + built, z, 2)));
            built++;
        }
        return built;
    }

    // ---------------------------------------------------------------- kelp stands and root curtains

    /** A cluster of the jittered cluster grid covering a column: its cell, how far out the column is (0..1), its make-up. */
    private record Cluster(int cellX, int cellZ, double q, double height, double roll) {}

    @Nullable
    private static Cluster cluster(CaveNoises noises, int x, int z)
    {
        int gx = Math.floorDiv(x, CELL), gz = Math.floorDiv(z, CELL);
        Cluster best = null;
        for (int i = -1; i <= 1; i++)
        {
            for (int j = -1; j <= 1; j++)
            {
                int cx = gx + i, cz = gz + j;
                double mx = cx * CELL + 1 + noises.hash(cx, 0, cz, 621) * (CELL - 2), mz = cz * CELL + 1 + noises.hash(cx, 1, cz, 621) * (CELL - 2);
                double r = 2.5 + noises.hash(cx, 2, cz, 621) * 3.5;
                double q = Math.sqrt(Mth.square(x + 0.5 - mx) + Mth.square(z + 0.5 - mz)) / r;
                if (q < 1 && (best == null || q < best.q)) best = new Cluster(cx, cz, q, 0.35 + 0.4 * noises.hash(cx, 3, cz, 621), noises.hash(cx, 4, cz, 621));
            }
        }
        return best;
    }

    /**
     * In every open run tall enough to be a cavern: stands of giant kelp on the floor, clumped and thinned by the
     * vegetation zones (dense stands, sparse ones, clearings), and curtains of roots and vines from the roof. Where a
     * cell holds both, the curtain stops a few blocks above the kelp, so the growth reads as one column from floor to
     * roof; now and then a root reaches all the way down.
     */
    static void forests(CaveChunk ctx)
    {
        double vegetation = Config.CAVE_VEGETATION.get();
        if (vegetation <= 0) return;
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                int y = ctx.yMin + 1;
                while (y < ctx.yMax)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz) || !ctx.sturdy(lx, y - 1, lz, Direction.UP))
                    {
                        y++;
                        continue;
                    }
                    int top = y;
                    while (top + 1 < ctx.yMax && ctx.carvedHere(lx, top + 1, lz) && ctx.open(lx, top + 1, lz)) top++;
                    if (top - y + 1 >= LARGE_RUN) stand(ctx, lx, lz, y, top, vegetation);
                    y = top + 2;
                }
            }
        }
    }

    private static void stand(CaveChunk ctx, int lx, int lz, int floor, int top, double vegetation)
    {
        CaveNoises noises = ctx.noises;
        int x = ctx.x0 + lx, z = ctx.z0 + lz, gap = top - floor + 1;
        CaveSpace space = ctx.space(lx, floor, lz);
        if (space == null) return;
        CaveEnvironment env = space.environment;
        Cluster cluster = cluster(noises, x, z);
        if (cluster == null) return;
        // 0 in sparse vegetation zones, 1 in very dense ones.
        double zone = Mth.clamp((noises.vegetationZone(x, floor, z) - 0.25) / 2.95, 0.0, 1.0);
        double lush = env.flora.density() * vegetation;
        boolean stalk = noises.hash(x, floor, z, 623) < Mth.lerp(Math.pow(cluster.q, 1.3), 0.75, 0.12);

        int kelp = 0;
        BlockState plant = kelpFor(env, space.environmentId.getPath(), cluster.roll, noises.hash(x, floor, z, 624));
        if (plant != null && stalk && noises.hash(cluster.cellX, 5, cluster.cellZ, 622) < Math.min(0.9, lush * KELP * (0.3 + 0.7 * zone)))
        {
            double h = gap * cluster.height * (1 - 0.4 * Math.pow(cluster.q, 1.5)) * (0.85 + 0.3 * noises.hash(x, floor, z, 625));
            int height = Math.min(Mth.floor(h), Math.min(MAX_KELP, gap - 3));
            if (height >= 4 && CavePlacer.plant(ctx, lx, floor, lz, new CaveEnvironment.PlantEntry(plant, height, height), Direction.DOWN, 0, height)) kelp = height;
        }

        if (!ctx.sturdy(lx, top + 1, lz, Direction.DOWN) || env.ceilingPlants.isEmpty()) return;
        boolean hangStalk = noises.hash(x, top, z, 626) < Mth.lerp(Math.pow(cluster.q, 1.3), 0.6, 0.1);
        if (!hangStalk || noises.hash(cluster.cellX, 6, cluster.cellZ, 622) >= Math.min(0.85, lush * HANGING * (0.4 + 0.6 * zone))) return;
        CaveEnvironment.PlantEntry entry = env.ceilingPlants.pick(Mth.clamp(noises.species(x + 53, top, z) + (noises.hash(x, top, z, 627) - 0.5) * 0.2, 0, 0.999));
        if (!(entry.state().getBlock() instanceof HangingPlantBlock)) return;
        int length;
        double roll = noises.hash(x, top, z, 628);
        if (kelp > 0) length = gap - kelp - 3 - Mth.floor(roll * 5);
        else if (roll < 0.08) length = gap - 1;
        else length = Math.max(2, Mth.floor(gap * (0.15 + 0.4 * noises.hash(x, top, z, 629))));
        if (length >= 2) CavePlacer.plant(ctx, lx, top, lz, new CaveEnvironment.PlantEntry(entry.state(), length, length), Direction.UP, 0, length);
    }

    /**
     * The giant of a cave environment: its own giants where it has them (forests, underground seas), crystal kelp in
     * crystal caves, a mix of giant, deep and cave kelp elsewhere; none in hot, mineral or hadal caves.
     */
    @Nullable
    private static BlockState kelpFor(CaveEnvironment env, String envName, double clusterRoll, double stalkRoll)
    {
        if (!env.giantPlants.isEmpty()) return env.giantPlants.pick(Mth.clamp(clusterRoll * 0.85 + stalkRoll * 0.15, 0, 0.999)).state();
        return switch (envName)
        {
            case "thermal", "mineral", "trench" -> null;
            case "crystal" -> ModBlocks.CRYSTAL_KELP.get().defaultBlockState();
            default -> (clusterRoll < 0.35 ? ModBlocks.GIANT_CAVE_KELP : clusterRoll < 0.7 ? ModBlocks.DEEP_KELP : ModBlocks.CAVE_KELP).get().defaultBlockState();
        };
    }
}
