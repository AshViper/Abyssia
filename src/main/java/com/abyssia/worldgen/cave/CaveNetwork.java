package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.thermal.ThermalVentField;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * The deep ocean's cave network for one world: the generation context shared by every chunk.
 * <p>
 * Systems sit on a coarse grid (at most one per cell) plus a finer grid of minor caves and sea arches. A system's
 * layout depends only on the world seed, its cell, the biome at its anchor and the seabed height (sampled from the
 * terrain's own density function), so any thread asking for a cell gets the same layout; layouts and the light
 * "plans" used to link neighbouring systems are cached. Neighbouring systems are joined by connector tunnels, which
 * turns isolated caves into one explorable network.
 */
public final class CaveNetwork
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MINOR_CELL = 72;
    public static final int MINOR_REACH = 56;
    private static final int CACHE_LIMIT = 4096;
    /** Luminous stretches: region cell size, and how far (blocks) and how coarsely their borders are warped. */
    private static final int LUMINOUS_REGION = 640;
    private static final double LUMINOUS_WARP = 240, LUMINOUS_WARP_FREQ = 1.0 / 360;

    private final long seed;
    private final RandomState randomState;
    private final CaveNoises noises;
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private final DensityFunction seabedDensity;
    private final int minY, maxY;
    private final int cellSize;
    private final Map<ResourceKey<Biome>, CaveProfile> profiles;
    private final Map<ResourceLocation, CaveEnvironment> environments;
    private final Map<ResourceLocation, CavernTemplate> cavernTemplates;
    private final Map<CaveProfile, Palette<CaveType>> typeWeights = new HashMap<>();
    private final ConcurrentHashMap<Long, FutureTask<CaveSystem>> systems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FutureTask<CaveSystem>> minors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Optional<CaveNetworkGenerator.Plan>> plans = new ConcurrentHashMap<>();
    /** Layout costs for the debug stats: plans, system builds, minor builds (count and nanoseconds). */
    static final java.util.concurrent.atomic.AtomicLongArray COST = new java.util.concurrent.atomic.AtomicLongArray(6);

    private CaveNetwork(long seed, RandomState randomState, BiomeSource biomeSource, NoiseSettings noise,
                        Map<ResourceKey<Biome>, CaveProfile> profiles, Map<ResourceLocation, CaveEnvironment> environments,
                        Map<ResourceLocation, CavernTemplate> cavernTemplates)
    {
        this.seed = seed;
        this.randomState = randomState;
        this.noises = new CaveNoises(seed);
        this.biomeSource = biomeSource;
        this.sampler = randomState.sampler();
        this.seabedDensity = randomState.router().initialDensityWithoutJaggedness();
        this.minY = noise.minY();
        this.maxY = noise.minY() + noise.height();
        this.cellSize = Config.CAVE_SYSTEM_SPACING.get();
        this.profiles = profiles;
        this.environments = environments;
        this.cavernTemplates = cavernTemplates;
        for (CaveProfile profile : profiles.values())
        {
            // Config rarity knobs reweight the datapack's cave type weights.
            typeWeights.computeIfAbsent(profile, p -> Palette.of(p.caveTypeList(), t -> switch (t.size)
            {
                case LARGE -> Config.LARGE_CAVE_WEIGHT.get();
                case MASSIVE -> Config.MASSIVE_CAVE_WEIGHT.get();
                default -> 1.0;
            }));
        }
    }

    public static CaveNetwork create(NoiseBasedChunkGenerator generator, RandomState randomState, RegistryAccess registries, long seed)
    {
        Map<ResourceKey<Biome>, CaveProfile> profiles = new HashMap<>();
        Map<ResourceLocation, CaveEnvironment> environments = new HashMap<>();
        registries.registry(CaveRegistries.ENVIRONMENTS).ifPresent(r -> r.entrySet().forEach(e -> environments.put(e.getKey().location(), e.getValue())));
        Map<ResourceLocation, CavernTemplate> templates = new HashMap<>();
        registries.registry(CaveRegistries.CAVERN_TEMPLATES).ifPresent(r -> r.entrySet().forEach(e -> templates.put(e.getKey().location(), e.getValue())));
        // Only biomes this generator can produce: the ocean world ends up with an empty network and does no cave work.
        java.util.Set<ResourceKey<Biome>> possible = new java.util.HashSet<>();
        generator.getBiomeSource().possibleBiomes().forEach(h -> h.unwrapKey().ifPresent(possible::add));
        Optional<? extends Registry<CaveProfile>> profileRegistry = registries.registry(CaveRegistries.PROFILES);
        profileRegistry.ifPresent(r -> r.entrySet().forEach(e -> {
            CaveProfile profile = e.getValue();
            if (!environments.containsKey(profile.environment))
            {
                LOGGER.warn("Cave profile {} uses unknown environment {}; skipped", e.getKey().location(), profile.environment);
                return;
            }
            for (Holder<Biome> biome : profile.biomes) biome.unwrapKey().filter(possible::contains).ifPresent(k -> profiles.put(k, profile));
        }));
        if (!profiles.isEmpty())
        {
            LOGGER.info("Cave network: {} cave environments, {} cavern templates, profiles for {} biomes, system spacing {}", environments.size(),
                    templates.size(), profiles.size(), Config.CAVE_SYSTEM_SPACING.get());
        }
        return new CaveNetwork(seed, randomState, generator.getBiomeSource(), generator.generatorSettings().value().noiseSettings(), profiles, environments, templates);
    }

    public boolean matches(RandomState randomState, long seed)
    {
        return this.randomState == randomState && this.seed == seed;
    }

    /** No biome of this dimension has a cave profile (the ocean world, or datapacks removed them). */
    public boolean isEmpty()
    {
        return profiles.isEmpty();
    }

    public long seed()
    {
        return seed;
    }

    public CaveNoises noises()
    {
        return noises;
    }

    public int minY()
    {
        return minY;
    }

    public int maxY()
    {
        return maxY;
    }

    public int cellSize()
    {
        return cellSize;
    }

    /** Highest seabed an entrance may open into: below the ceiling and the return-to-surface boundary. */
    public int maxEntranceY()
    {
        return Math.min(maxY - 16, Config.DEEP_OCEAN_RETURN_Y.get() - 10);
    }

    // ---------------------------------------------------------------- terrain and biome sampling

    /**
     * Seabed height (first non-solid block) of the undisturbed terrain, from the terrain's own density function:
     * a binary search along the column, the same answer on every thread and before the chunk exists.
     */
    public int seabed(int x, int z)
    {
        int lo = minY, hi = maxY - 1;
        if (solid(x, hi, z)) return maxY;
        if (!solid(x, lo, z)) return lo;
        while (hi - lo > 1)
        {
            int mid = (lo + hi) >> 1;
            if (solid(x, mid, z)) lo = mid;
            else hi = mid;
        }
        return lo + 1;
    }

    public int seabed(double x, double z)
    {
        return seabed((int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * Lowest seabed over a disc (centre, an inner ring and an outer ring spaced at most ~12 blocks apart), to keep a
     * chamber of this radius, and especially a lake's gas dome, under solid rock even where a narrow canyon crosses.
     */
    public int lowestSeabed(double x, double z, double radius)
    {
        int low = seabed(x, z);
        int outer = Math.max(6, Math.min(24, (int) Math.ceil(radius * Math.PI * 2 / 12)));
        for (int i = 0; i < outer; i++)
        {
            double a = i * Math.PI * 2 / outer;
            low = Math.min(low, seabed(x + Math.cos(a) * radius, z + Math.sin(a) * radius));
            if (radius > 16 && i % 2 == 0) low = Math.min(low, seabed(x + Math.cos(a + 0.3) * radius * 0.5, z + Math.sin(a + 0.3) * radius * 0.5));
        }
        return low;
    }

    private boolean solid(int x, int y, int z)
    {
        return seabedDensity.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0;
    }

    @Nullable
    public ResourceKey<Biome> biome(int x, int z)
    {
        return biomeSource.getNoiseBiome(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z), sampler).unwrapKey().orElse(null);
    }

    @Nullable
    public CaveProfile profile(int x, int z)
    {
        ResourceKey<Biome> biome = biome(x, z);
        return biome == null ? null : profiles.get(biome);
    }

    /**
     * Whether the caves here lie in a luminous stretch of the underground (about {@code luminous_chance} of each
     * biome's area). Whole stretches glow, every cave in them alike, so the underground changes character over
     * hundreds of blocks rather than from one cave to the next. One roll per region cell, looked up through a warped
     * position so the stretches are irregular rather than grid squares.
     */
    public boolean luminous(CaveProfile profile, int x, int z)
    {
        if (profile.luminousChance <= 0) return false;
        double fx = x * LUMINOUS_WARP_FREQ, fz = z * LUMINOUS_WARP_FREQ;
        int wx = Mth.floor(x + noises.field2(fx, fz, 31) * LUMINOUS_WARP), wz = Mth.floor(z + noises.field2(fx, fz, 32) * LUMINOUS_WARP);
        return noises.hash(Math.floorDiv(wx, LUMINOUS_REGION), 0, Math.floorDiv(wz, LUMINOUS_REGION), 601) < profile.luminousChance;
    }

    public Palette<CaveType> caveTypes(CaveProfile profile)
    {
        return typeWeights.getOrDefault(profile, profile.caveTypes);
    }

    public CaveEnvironment environment(ResourceLocation id, CaveProfile fallback)
    {
        CaveEnvironment env = environments.get(id);
        return env != null ? env : environments.get(fallback.environment);
    }

    public boolean hasCavernTemplate(ResourceLocation id)
    {
        return cavernTemplates.containsKey(id);
    }

    /** A cavern template by id, falling back to the mixed template; null when no template is loaded at all. */
    @Nullable
    public CavernTemplate cavernTemplate(ResourceLocation id)
    {
        CavernTemplate template = cavernTemplates.get(id);
        return template != null ? template : cavernTemplates.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "mixed"));
    }

    public ResourceLocation environmentId(String name, CaveProfile fallback)
    {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name);
        return environments.containsKey(id) ? id : fallback.environment;
    }

    /** A hydrothermal vent field lies within {@code radius} of this point (thermal caves and tunnels may form). */
    public boolean thermalContext(int x, int z, int radius)
    {
        return !ThermalVentField.near(seed, x - radius, z - radius, x + radius, z + radius, this::biome).isEmpty();
    }

    // ---------------------------------------------------------------- layout cache

    @Nullable
    CaveNetworkGenerator.Plan plan(int cellX, int cellZ)
    {
        if (plans.size() > CACHE_LIMIT * 4) plans.clear();
        return plans.computeIfAbsent(key(cellX, cellZ), k -> {
            long t = System.nanoTime();
            Optional<CaveNetworkGenerator.Plan> plan = Optional.ofNullable(CaveNetworkGenerator.plan(this, cellX, cellZ));
            COST.incrementAndGet(0);
            COST.addAndGet(1, System.nanoTime() - t);
            return plan;
        }).orElse(null);
    }

    public CaveSystem system(int cellX, int cellZ)
    {
        return cached(systems, cellX, cellZ, () -> {
            long t = System.nanoTime();
            CaveSystem built = CaveNetworkGenerator.build(this, cellX, cellZ);
            COST.incrementAndGet(2);
            COST.addAndGet(3, System.nanoTime() - t);
            return built;
        });
    }

    public CaveSystem minor(int cellX, int cellZ)
    {
        return cached(minors, cellX, cellZ, () -> {
            long t = System.nanoTime();
            CaveSystem built = CaveNetworkGenerator.buildMinor(this, cellX, cellZ);
            COST.incrementAndGet(4);
            COST.addAndGet(5, System.nanoTime() - t);
            return built;
        });
    }

    /**
     * Each layout is built once: the first thread asking builds it, threads asking meanwhile wait for that result
     * instead of building a duplicate. Building happens outside the map's locks (it reads other caches).
     */
    private static CaveSystem cached(ConcurrentHashMap<Long, FutureTask<CaveSystem>> cache, int cellX, int cellZ, Callable<CaveSystem> build)
    {
        if (cache.size() > CACHE_LIMIT) cache.clear();
        long k = key(cellX, cellZ);
        FutureTask<CaveSystem> task = cache.get(k);
        if (task == null)
        {
            FutureTask<CaveSystem> created = new FutureTask<>(build);
            task = cache.putIfAbsent(k, created);
            if (task == null)
            {
                task = created;
                created.run();
            }
        }
        try
        {
            return task.get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building cave layout " + cellX + "," + cellZ, e);
        }
        catch (ExecutionException e)
        {
            throw new IllegalStateException("Failed to build cave layout " + cellX + "," + cellZ, e.getCause());
        }
    }

    /** Every non-empty system or minor cave whose bounds touch the given block rectangle. */
    public List<CaveSystem> systemsNear(int x0, int z0, int x1, int z1)
    {
        List<CaveSystem> result = new ArrayList<>();
        // Connector tunnels run to the neighbouring cell's hub, so a system can reach about two cells from its own.
        int reach = cellSize * 2;
        for (int cx = Math.floorDiv(x0 - reach, cellSize); cx <= Math.floorDiv(x1 + reach, cellSize); cx++)
        {
            for (int cz = Math.floorDiv(z0 - reach, cellSize); cz <= Math.floorDiv(z1 + reach, cellSize); cz++)
            {
                CaveNetworkGenerator.Plan p = plan(cx, cz);
                if (p == null || !CaveNetworkGenerator.mayReach(this, p, x0, z0, x1, z1)) continue;
                CaveSystem s = system(cx, cz);
                if (s.intersects(x0, z0, x1, z1)) result.add(s);
            }
        }
        for (int cx = Math.floorDiv(x0 - MINOR_REACH, MINOR_CELL); cx <= Math.floorDiv(x1 + MINOR_REACH, MINOR_CELL); cx++)
        {
            for (int cz = Math.floorDiv(z0 - MINOR_REACH, MINOR_CELL); cz <= Math.floorDiv(z1 + MINOR_REACH, MINOR_CELL); cz++)
            {
                CaveSystem s = minor(cx, cz);
                if (s.intersects(x0, z0, x1, z1)) result.add(s);
            }
        }
        return result;
    }

    /** Test aid: every system reaching the rectangle, found by building all cells in reach (no plan pre-filter). */
    List<CaveSystem> systemsNearUnfiltered(int x0, int z0, int x1, int z1)
    {
        List<CaveSystem> result = new ArrayList<>();
        int reach = cellSize * 2;
        for (int cx = Math.floorDiv(x0 - reach, cellSize); cx <= Math.floorDiv(x1 + reach, cellSize); cx++)
        {
            for (int cz = Math.floorDiv(z0 - reach, cellSize); cz <= Math.floorDiv(z1 + reach, cellSize); cz++)
            {
                CaveSystem s = system(cx, cz);
                if (s.intersects(x0, z0, x1, z1) && !s.minor) result.add(s);
            }
        }
        return result;
    }

    private static long key(int cellX, int cellZ)
    {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }
}
