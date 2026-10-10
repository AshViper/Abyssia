package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.thermal.ThermalVentField;
import com.abyssia.worldgen.DeepLayer;
import com.abyssia.worldgen.DepthBand;
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
 * The deep layer's cave network for one world: the generation context shared by every chunk.
 * <p>
 * Systems sit on a coarse grid (at most one per cell) plus a finer grid of minor caves and sea arches. A system's
 * layout depends only on the world seed, its cell, the biome at its anchor and the seabed height (sampled from the
 * terrain's own density function), so any thread asking for a cell gets the same layout; layouts and the light
 * "plans" used to link neighbouring systems are cached. Neighbouring systems are joined by connector tunnels, which
 * turns isolated caves into one explorable network.
 * <p>
 * AB02: the instance made by {@link #create} is the shallow network (seabed-relative, Y &gt;= {@link DeepLayer#DEEP_BOTTOM_Y}, behaviour
 * unchanged). It owns one more instance per {@link DepthBand}, a <em>window</em> network: free-floating systems in
 * the abyss crust with their own grid, caches and random streams, where the seabed is replaced by the window's top
 * (so nothing opens to the sea), linked to the window above by vertical shafts. Windows are reached through
 * {@link #windows()}; {@link #systemsNear} only ever returns the network's own systems.
 */
public final class CaveNetwork
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MINOR_CELL = 72;
    public static final int MINOR_REACH = 56;
    private static final int CACHE_LIMIT = 4096;
    /** Window systems are far bigger than shallow ones (mega caverns), so fewer are kept; each window has its own caches. */
    private static final int WINDOW_CACHE_LIMIT = 768;
    /** How far (blocks) from its hub a window system or its links can reach: covers 2.1 x the largest mega radius + the longest branches. */
    private static final int WINDOW_REACH = 640;
    /** Blocks between the highest entrance and the ceiling's lowest underside (seabeds reach SEABED_MAX_Y, 12 below it). */
    private static final int ENTRANCE_MARGIN = 8;
    /** Biomes are sampled in the deep layer (old deep-ocean Y 0), the same height ThermalVentManager uses. */
    public static final int BIOME_QUART_Y = QuartPos.fromBlock((int) DeepLayer.fromDeepY(0));
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
    /** Blocks around a chunk in which cells are searched for systems that may reach it. */
    private final int reach;
    /** The dimension's own height range (windows are cut to it). */
    private final int worldMinY, worldMaxY;
    /** The crust window this network is; null for the shallow network. */
    @Nullable
    private final DepthBand band;
    /** Root only: the window networks, topmost (B') first; empty when disabled or in a dimension without the crust. */
    private final CaveNetwork[] windows;
    /** Window only: the window above (null for the topmost). Set once by the root's constructor. */
    @Nullable
    private CaveNetwork above;
    /** Window only: the shallow network (its seabed is where the descent routes of AB06 end); null for the shallow network. */
    @Nullable
    private final CaveNetwork root;
    private final Map<ResourceKey<Biome>, CaveProfile> profiles;
    private final Map<ResourceLocation, CaveEnvironment> environments;
    private final Map<ResourceLocation, CavernTemplate> cavernTemplates;
    private final Map<CaveProfile, Palette<CaveType>> typeWeights = new HashMap<>();
    private final ConcurrentHashMap<Long, FutureTask<CaveSystem>> systems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FutureTask<CaveSystem>> minors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Optional<CaveNetworkGenerator.Plan>> plans = new ConcurrentHashMap<>();
    /** AB06: the abyss cavity of each super cell (window only; cheap, kept apart from the plans so planning never recurses). */
    private final ConcurrentHashMap<Long, Optional<AbyssCavity.Site>> cavities = new ConcurrentHashMap<>();
    /** Layout costs for the debug stats: plans, system builds, minor builds (count and nanoseconds). */
    static final java.util.concurrent.atomic.AtomicLongArray COST = new java.util.concurrent.atomic.AtomicLongArray(6);
    /** The same for each window (6 slots per window, in {@link DepthBand} order; windows have no minor caves). */
    static final java.util.concurrent.atomic.AtomicLongArray WINDOW_COST = new java.util.concurrent.atomic.AtomicLongArray(6 * DepthBand.values().length);

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
        // The deep layer: from the world bottom up to its rock ceiling (no cave ever cuts into the ceiling or the
        // ocean world above it).
        this.minY = Math.max(noise.minY(), DeepLayer.DEEP_BOTTOM_Y);  // the caves stay in the deep layer, not the abyss under it
        this.maxY = Math.min(noise.minY() + noise.height(), DeepLayer.CEILING_BOTTOM_Y);
        this.worldMinY = noise.minY();
        this.worldMaxY = noise.minY() + noise.height();
        this.cellSize = Config.CAVE_SYSTEM_SPACING.get();
        this.reach = cellSize * 2;
        this.band = null;
        this.root = null;
        this.profiles = profiles;
        this.environments = environments;
        this.cavernTemplates = cavernTemplates;
        initTypeWeights();
        // The crust windows: only where the dimension reaches down into the crust and some biome has a cave profile.
        DepthBand[] bands = DepthBand.values();
        CaveNetwork[] made = new CaveNetwork[bands.length];
        int count = 0;
        if (Config.BANDS_ENABLED.get() && !profiles.isEmpty())
        {
            for (DepthBand b : bands)
            {
                if (Math.min(b.top, worldMaxY) - Math.max(b.bottom, worldMinY) < 64) continue;
                made[b.ordinal()] = new CaveNetwork(this, b);
                count++;
            }
        }
        CaveNetwork[] present = new CaveNetwork[count];
        for (int i = 0, k = 0; i < made.length; i++)
        {
            if (made[i] == null) continue;
            present[k++] = made[i];
            // The window above is the previous band in the table, if it exists in this dimension.
            made[i].above = i > 0 ? made[i - 1] : null;
        }
        this.windows = present;
    }

    /** A crust window network of {@code root}: same world, biomes and profiles, its own grid, caches and Y range. */
    private CaveNetwork(CaveNetwork root, DepthBand band)
    {
        this.seed = root.seed;
        this.randomState = root.randomState;
        this.noises = root.noises;
        this.biomeSource = root.biomeSource;
        this.sampler = root.sampler;
        this.seabedDensity = root.seabedDensity;
        this.worldMinY = root.worldMinY;
        this.worldMaxY = root.worldMaxY;
        this.minY = Math.max(band.bottom, root.worldMinY);
        this.maxY = Math.min(band.top, root.worldMaxY);
        this.cellSize = Config.BAND_SPACING[band.ordinal()].get();
        this.reach = Math.max(cellSize * 2, WINDOW_REACH);
        this.band = band;
        this.root = root;
        this.windows = new CaveNetwork[0];
        this.profiles = root.profiles;
        this.environments = root.environments;
        this.cavernTemplates = root.cavernTemplates;
        initTypeWeights();
    }

    private void initTypeWeights()
    {
        for (CaveProfile profile : profiles.values())
        {
            // Config rarity knobs reweight the datapack's cave type weights.
            typeWeights.computeIfAbsent(profile, p -> Palette.of(p.caveTypeList(), t -> typeScale(t)));
        }
    }

    private double typeScale(CaveType t)
    {
        if (!allows(t) || t == CaveType.ABYSS_CAVITY) return 0;  // AB06 cavities come from their own grid, not the profile lottery
        return switch (t.size)
        {
            case LARGE -> Config.LARGE_CAVE_WEIGHT.get();
            case MASSIVE -> Config.MASSIVE_CAVE_WEIGHT.get();
            case MEGA -> band == null ? 0.0 : Config.BAND_MEGA_WEIGHT[band.ordinal()].get();
            default -> 1.0;
        };
    }

    /**
     * Whether this network builds systems of the type: mega caverns only in the crust windows; windows have no seabed,
     * so the seabed-bound types (sea tunnels and arches, vertical shafts up to the sea floor) are left out there.
     */
    boolean allows(CaveType type)
    {
        if (band == null) return type != CaveType.MEGA_CAVERN && type != CaveType.ABYSS_CAVITY;
        return type != CaveType.SEA_TUNNEL && type != CaveType.SEA_ARCH && type != CaveType.VERTICAL_SHAFT;
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
        CaveNetwork network = new CaveNetwork(seed, randomState, generator.getBiomeSource(), generator.generatorSettings().value().noiseSettings(), profiles, environments, templates);
        if (network.windows.length > 0) LOGGER.info("Cave network: {} crust windows", network.windows.length);
        return network;
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

    /** The crust window this network is, or null for the shallow network. */
    @Nullable
    public DepthBand band()
    {
        return band;
    }

    public boolean isWindow()
    {
        return band != null;
    }

    /** Debug label: the window's name or "shallow". */
    public String label()
    {
        return band == null ? "shallow" : band.label();
    }

    /** The crust window networks, topmost first (empty for a window and where windows are disabled). */
    public CaveNetwork[] windows()
    {
        return windows;
    }

    /** This network and its windows, shallow first. */
    public List<CaveNetwork> all()
    {
        List<CaveNetwork> all = new ArrayList<>(1 + windows.length);
        all.add(this);
        all.addAll(java.util.Arrays.asList(windows));
        return all;
    }

    /** Window only: the window above (null for the topmost). */
    @Nullable
    CaveNetwork above()
    {
        return above;
    }

    /** Mixed into every random salt of a window (0 for the shallow network, whose streams stay as they were). */
    long salt()
    {
        return band == null ? 0L : (band.ordinal() + 1) * 0x5851F42D4C957F2DL;
    }

    /** Blocks around a rectangle in which cells are searched for systems that may reach it. */
    int reach()
    {
        return reach;
    }

    /** Highest Y a shape of this network may reach: a window's vertical links run into the window above, up to its top. */
    public int carveTopY()
    {
        if (above != null) return above.maxY;
        // AB06: the topmost window's descent routes climb through the deep layer up to its seabed.
        return band == DepthBand.B && root != null ? root.maxY : maxY;
    }

    /** Window only: the shallow network, whose seabed the topmost window's descent routes open into. */
    @Nullable
    CaveNetwork root()
    {
        return root;
    }

    /** The abyss cavity of a super cell of this window ({@link AbyssCavity}), or null. */
    @Nullable
    AbyssCavity.Site cavity(int superX, int superZ)
    {
        if (band == null) return null;
        if (cavities.size() > CACHE_LIMIT) cavities.clear();
        return cavities.computeIfAbsent(key(superX, superZ), k -> Optional.ofNullable(AbyssCavity.compute(this, superX, superZ))).orElse(null);
    }

    /** Highest seabed an entrance may open into (or an arch rise to): a margin under the deep layer's rock ceiling. */
    public int maxEntranceY()
    {
        return maxY - ENTRANCE_MARGIN;
    }

    // ---------------------------------------------------------------- terrain and biome sampling

    /**
     * Seabed height (first non-solid block) of the undisturbed terrain, from the terrain's own density function:
     * a binary search along the column, the same answer on every thread and before the chunk exists. Searches the deep
     * layer under the ceiling, where the router's initial density is the deep seabed alone (falling with Y).
     */
    public int seabed(int x, int z)
    {
        // A window has no sea floor: its top acts as one, so nothing there ever opens to the sea.
        if (band != null) return maxY;
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
        if (band != null) return maxY;
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
        if (band != null) return biomeAt(x, (minY + maxY) / 2, z);
        return biomeSource.getNoiseBiome(QuartPos.fromBlock(x), BIOME_QUART_Y, QuartPos.fromBlock(z), sampler).unwrapKey().orElse(null);
    }

    /** The real biome at a block (the biome source sampled at that Y, as the chunk's own biomes are). */
    @Nullable
    public ResourceKey<Biome> biomeAt(int x, int y, int z)
    {
        return biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler).unwrapKey().orElse(null);
    }

    /** The cave profile of the biome at a block, or null when that biome has none. */
    @Nullable
    public CaveProfile profileAt(int x, int y, int z)
    {
        ResourceKey<Biome> biome = biomeAt(x, y, z);
        return biome == null ? null : profiles.get(biome);
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
        return noises.hash(Math.floorDiv(wx, LUMINOUS_REGION), 0, Math.floorDiv(wz, LUMINOUS_REGION), band == null ? 601 : 601 + 7 * (band.ordinal() + 1)) < profile.luminousChance;
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
        if (band != null) return false;  // vent fields lie on the deep seabed, far above the crust
        return !ThermalVentField.near(seed, x - radius, z - radius, x + radius, z + radius, this::biome).isEmpty();
    }

    // ---------------------------------------------------------------- layout cache

    private void cost(int kind, long nanos)
    {
        var costs = band == null ? COST : WINDOW_COST;
        int base = band == null ? 0 : band.ordinal() * 6;
        costs.incrementAndGet(base + kind * 2);
        costs.addAndGet(base + kind * 2 + 1, nanos);
    }

    @Nullable
    CaveNetworkGenerator.Plan plan(int cellX, int cellZ)
    {
        if (plans.size() > CACHE_LIMIT * 4) plans.clear();
        return plans.computeIfAbsent(key(cellX, cellZ), k -> {
            long t = System.nanoTime();
            Optional<CaveNetworkGenerator.Plan> plan = Optional.ofNullable(CaveNetworkGenerator.plan(this, cellX, cellZ));
            cost(0, System.nanoTime() - t);
            return plan;
        }).orElse(null);
    }

    public CaveSystem system(int cellX, int cellZ)
    {
        return cached(systems, band == null ? CACHE_LIMIT : WINDOW_CACHE_LIMIT, cellX, cellZ, () -> {
            long t = System.nanoTime();
            CaveSystem built = CaveNetworkGenerator.build(this, cellX, cellZ);
            cost(1, System.nanoTime() - t);
            return built;
        });
    }

    public CaveSystem minor(int cellX, int cellZ)
    {
        if (band != null) return CaveNetworkGenerator.noMinor(this, cellX, cellZ);
        return cached(minors, CACHE_LIMIT, cellX, cellZ, () -> {
            long t = System.nanoTime();
            CaveSystem built = CaveNetworkGenerator.buildMinor(this, cellX, cellZ);
            cost(2, System.nanoTime() - t);
            return built;
        });
    }

    /**
     * Each layout is built once: the first thread asking builds it, threads asking meanwhile wait for that result
     * instead of building a duplicate. Building happens outside the map's locks (it reads other caches).
     */
    private static CaveSystem cached(ConcurrentHashMap<Long, FutureTask<CaveSystem>> cache, int limit, int cellX, int cellZ, Callable<CaveSystem> build)
    {
        if (cache.size() > limit) cache.clear();
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

    /** Every non-empty system or minor cave of this network whose bounds touch the given block rectangle. */
    public List<CaveSystem> systemsNear(int x0, int z0, int x1, int z1)
    {
        List<CaveSystem> result = new ArrayList<>();
        // Connector tunnels run to the neighbouring cell's hub, so a system can reach about two cells from its own
        // (a window: far enough for its largest mega cavern and its vertical links).
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
        if (band != null) return result;
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

    /** The systems of this network and of every window touching the rectangle (debug commands, census). */
    public List<CaveSystem> systemsNearAll(int x0, int z0, int x1, int z1)
    {
        List<CaveSystem> result = systemsNear(x0, z0, x1, z1);
        for (CaveNetwork window : windows) result.addAll(window.systemsNear(x0, z0, x1, z1));
        return result;
    }

    /** Test aid: every system reaching the rectangle, found by building all cells in reach (no plan pre-filter). */
    List<CaveSystem> systemsNearUnfiltered(int x0, int z0, int x1, int z1)
    {
        List<CaveSystem> result = new ArrayList<>();
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
