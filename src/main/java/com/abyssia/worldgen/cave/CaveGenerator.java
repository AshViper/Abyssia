package com.abyssia.worldgen.cave;

import com.abyssia.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entry point: generates the cave network's part of one chunk, right after the terrain and its surface exist
 * (the carver stage) and before any feature, so ore veins, vents and biome vegetation placed later all see the caves.
 * <p>
 * Order within the chunk (base terrain and its density-function caves already exist):
 * <ol>
 *     <li>cave field: main caves, network connectors, chambers, tunnels and shafts as one signed distance field</li>
 *     <li>carving, including underground water: lakes and their sealed gas pockets</li>
 *     <li>geological layers on every cave surface</li>
 *     <li>large rock formations (pillars, bridges, shelves, boulders, giant speleothems, large crystals, arches)</li>
 *     <li>mineral veins (ore bodies exposed in walls)</li>
 *     <li>thermal vents (chimneys and cores; they claim their floor before anything grows there)</li>
 *     <li>crystals, then stalactites and stalagmites</li>
 *     <li>collapse debris and entrance / arch landmark dressing</li>
 *     <li>giant vegetation (the canopy of cavern forests), then cave vegetation</li>
 *     <li>thermal zones around the vents; marine snow and other effects are client-side</li>
 *     <li>then, in every chunk, the terrain's own caves outside the network ({@link TerrainCaveDecorator})</li>
 * </ol>
 * Nothing here schedules ticks, creates block entities or scans beyond the chunk and a one-block rim.
 */
public final class CaveGenerator
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong CHUNKS = new AtomicLong();
    private static final AtomicLong CAVE_CHUNKS = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();
    private static final AtomicLong MAX_NANOS = new AtomicLong();
    private static final AtomicLong TERRAIN_NANOS = new AtomicLong();
    private static final String[] PHASES = {"field", "carve", "geology+formations+ores", "decoration", "vegetation"};
    private static final AtomicLong[] PHASE_NANOS = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};

    private CaveGenerator() {}

    public static void generate(CaveNetwork network, ChunkAccess chunk)
    {
        if (!Config.CAVES_ENABLED.get() || network.isEmpty()) return;
        long start = System.nanoTime();
        ChunkPos cp = chunk.getPos();
        List<CaveSystem> systems = network.systemsNear(cp.getMinBlockX() - 1, cp.getMinBlockZ() - 1, cp.getMaxBlockX() + 1, cp.getMaxBlockZ() + 1);
        CaveChunk ctx = systems.isEmpty() ? null : CaveChunk.create(network, chunk, systems);
        if (ctx != null)
        {
            long t = System.nanoTime();
            ctx.computeField();
            t = phase(0, t);
            ctx.carve();
            t = phase(1, t);
            CaveGeology.paint(ctx);
            ctx.placeFills();
            ctx.placeOres();
            ctx.placeCuts();
            t = phase(2, t);
            // Massive cavern decoration applies only where a planned cavern reaches this chunk.
            List<Cavern> caverns = MassiveCavernDecorator.caverns(ctx);
            if (!caverns.isEmpty()) MassiveCavernDecorator.water(ctx, caverns);
            ThermalCaveGenerator.vents(ctx);
            CaveDecorationGenerator.formationTips(ctx);
            CaveDecorationGenerator.crystals(ctx);
            CaveDecorationGenerator.speleothems(ctx);
            CaveDecorationGenerator.collapses(ctx);
            CaveDecorationGenerator.sites(ctx);
            t = phase(3, t);
            if (!caverns.isEmpty())
            {
                MassiveCavernDecorator.giants(ctx, caverns);
                MassiveCavernDecorator.hanging(ctx, caverns);
            }
            CaveVegetationGenerator.giants(ctx);
            ThermalCaveGenerator.zones(ctx);
            if (!caverns.isEmpty()) MassiveCavernDecorator.gardensAndShores(ctx, caverns);
            CaveVegetationGenerator.grow(ctx);
            if (!caverns.isEmpty()) MassiveCavernDecorator.surfaces(ctx, caverns);
            t = phase(4, t);
            ctx.finish();
            CAVE_CHUNKS.incrementAndGet();
        }
        // The terrain's own caves (outside the network) in every chunk; after the network's context, whose buffers it reuses.
        long t = System.nanoTime();
        TerrainCaveDecorator.decorate(network, chunk);
        TERRAIN_NANOS.addAndGet(System.nanoTime() - t);
        record(System.nanoTime() - start);
    }

    private static long phase(int index, long start)
    {
        long now = System.nanoTime();
        PHASE_NANOS[index].addAndGet(now - start);
        return now;
    }

    private static void record(long nanos)
    {
        long n = CHUNKS.incrementAndGet();
        NANOS.addAndGet(nanos);
        MAX_NANOS.accumulateAndGet(nanos, Math::max);
        if (Config.CAVE_DEBUG_TIMING.get() && n % 256 == 0) LOGGER.info("[Abyssia caves] {}", stats());
    }

    /** Generation cost so far: chunks processed, how many held caves, average and worst time per chunk. */
    public static String stats()
    {
        long n = Math.max(1, CHUNKS.get()), caves = Math.max(1, CAVE_CHUNKS.get());
        StringBuilder phases = new StringBuilder();
        for (int i = 0; i < PHASES.length; i++) phases.append(i == 0 ? "" : ", ").append(PHASES[i]).append(String.format(" %.2f", PHASE_NANOS[i].get() / 1e6 / caves));
        var cost = CaveNetwork.COST;
        return String.format("%d chunks (%d with caves), avg %.2f ms, max %.2f ms per chunk; per cave chunk: %s ms; terrain caves %.2f ms per chunk (%s); "
                        + "layouts: %d plans %.2f ms, %d systems %.2f ms, %d minor %.2f ms each",
                CHUNKS.get(), CAVE_CHUNKS.get(), NANOS.get() / 1e6 / n, MAX_NANOS.get() / 1e6, phases, TERRAIN_NANOS.get() / 1e6 / n, TerrainCaveDecorator.stats(n),
                cost.get(0), cost.get(1) / 1e6 / Math.max(1, cost.get(0)), cost.get(2), cost.get(3) / 1e6 / Math.max(1, cost.get(2)),
                cost.get(4), cost.get(5) / 1e6 / Math.max(1, cost.get(4)));
    }

    public static void resetStats()
    {
        CHUNKS.set(0);
        CAVE_CHUNKS.set(0);
        NANOS.set(0);
        MAX_NANOS.set(0);
        TERRAIN_NANOS.set(0);
        TerrainCaveDecorator.resetStats();
        for (AtomicLong phase : PHASE_NANOS) phase.set(0);
    }
}
