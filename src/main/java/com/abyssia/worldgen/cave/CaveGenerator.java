package com.abyssia.worldgen.cave;

import com.abyssia.Config;
import com.abyssia.worldgen.DepthBand;
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
 * <p>
 * AB02: the pipeline above runs once for the shallow network and then once for each crust window whose systems reach
 * the chunk (lowest window first), each with its own {@link CaveChunk} sized by that window's Y range.
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
    /** Per crust window: chunks with caves of that window and the time their pipeline took. */
    private static final AtomicLong[] WINDOW_CHUNKS = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};
    private static final AtomicLong[] WINDOW_NANOS = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};

    private CaveGenerator() {}

    public static void generate(CaveNetwork network, ChunkAccess chunk)
    {
        if (!Config.CAVES_ENABLED.get() || network.isEmpty()) return;
        long start = System.nanoTime();
        ChunkPos cp = chunk.getPos();
        pipeline(network, chunk, cp, true);
        // The crust windows, lowest first (a vertical link carved by a lower window runs into the window above: that one decorates last).
        CaveNetwork[] windows = network.windows();
        for (int i = windows.length - 1; i >= 0; i--)
        {
            long t = System.nanoTime();
            if (pipeline(windows[i], chunk, cp, false))
            {
                int w = windows[i].band().ordinal();
                WINDOW_CHUNKS[w].incrementAndGet();
                WINDOW_NANOS[w].addAndGet(System.nanoTime() - t);
            }
        }
        // The terrain's own caves (outside the network) in every chunk; after the network's context, whose buffers it reuses.
        long t = System.nanoTime();
        TerrainCaveDecorator.decorate(network, chunk);
        TERRAIN_NANOS.addAndGet(System.nanoTime() - t);
        record(System.nanoTime() - start);
    }

    /** One network's part of the chunk: the whole carve / geology / decoration pipeline. False when no cave of it reaches the chunk. */
    private static boolean pipeline(CaveNetwork network, ChunkAccess chunk, ChunkPos cp, boolean shallow)
    {
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
            List<Cavern> halls = HallDecorator.halls(caverns);
            if (!caverns.isEmpty()) MassiveCavernDecorator.water(ctx, caverns);
            if (!halls.isEmpty()) HallDecorator.windows(ctx, halls);
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
            if (!halls.isEmpty()) HallDecorator.beacons(ctx, halls);
            CaveVegetationGenerator.giants(ctx);
            ThermalCaveGenerator.zones(ctx);
            if (!caverns.isEmpty()) MassiveCavernDecorator.gardensAndShores(ctx, caverns);
            if (!halls.isEmpty()) HallDecorator.life(ctx);
            CaveVegetationGenerator.grow(ctx);
            if (!caverns.isEmpty()) MassiveCavernDecorator.surfaces(ctx, caverns);
            t = phase(4, t);
            ctx.finish();
            if (shallow) CAVE_CHUNKS.incrementAndGet();
        }
        return ctx != null;
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
                        + "layouts: %d plans %.2f ms, %d systems %.2f ms, %d minor %.2f ms each; crust windows: %s",
                CHUNKS.get(), CAVE_CHUNKS.get(), NANOS.get() / 1e6 / n, MAX_NANOS.get() / 1e6, phases, TERRAIN_NANOS.get() / 1e6 / n, TerrainCaveDecorator.stats(n),
                cost.get(0), cost.get(1) / 1e6 / Math.max(1, cost.get(0)), cost.get(2), cost.get(3) / 1e6 / Math.max(1, cost.get(2)),
                cost.get(4), cost.get(5) / 1e6 / Math.max(1, cost.get(4)), windowStats());
    }

    /** Per window: chunks holding its caves and the time per such chunk, plans and systems built (count and ms each). */
    private static String windowStats()
    {
        var cost = CaveNetwork.WINDOW_COST;
        StringBuilder out = new StringBuilder();
        for (DepthBand band : DepthBand.values())
        {
            int w = band.ordinal(), c = w * 6;
            long chunks = WINDOW_CHUNKS[w].get();
            out.append(w == 0 ? "" : "; ").append(band.label()).append(": ").append(chunks).append(" chunks ")
                    .append(String.format("%.2f ms each, %d plans %.2f ms, %d systems %.2f ms", WINDOW_NANOS[w].get() / 1e6 / Math.max(1, chunks),
                            cost.get(c), cost.get(c + 1) / 1e6 / Math.max(1, cost.get(c)), cost.get(c + 2), cost.get(c + 3) / 1e6 / Math.max(1, cost.get(c + 2))));
        }
        return out.toString();
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
        for (AtomicLong n : WINDOW_CHUNKS) n.set(0);
        for (AtomicLong n : WINDOW_NANOS) n.set(0);
    }
}
