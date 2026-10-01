package com.abyssia.worldgen.structure;

import com.abyssia.Config;
import com.abyssia.thermal.ThermalVentField;
import com.abyssia.worldgen.DeepLayer;
import com.abyssia.worldgen.OceanChunkGenerator;
import com.abyssia.worldgen.cave.CaveEnvironment;
import com.abyssia.worldgen.cave.CaveNetwork;
import com.abyssia.worldgen.cave.CaveShape;
import com.abyssia.worldgen.cave.CaveSpace;
import com.abyssia.worldgen.cave.CaveSystem;
import com.abyssia.worldgen.cave.Cavern;
import com.abyssia.worldgen.cave.Palette;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where this world's seabed structures stand. Built per world from the datapack profiles and definitions; every
 * answer is a pure function of the world seed, the profile entry and a grid cell (plus terrain sampled from the
 * density function and the analytic cave layout), cached, and shared by the worldgen threads.
 * <p>
 * Pipeline per candidate: profile entry grid cell -> chance roll -> biome at the centre -> terrain conditions
 * (depth, slope, room under the height cap) -> vent fields in the way -> overlap with larger (or higher-ranked
 * equal) structures -> cave mouths in the footprint (or caves under a dig) -> placed. Cheapest checks first. {@link #explain} reports each step for the debug command.
 */
public final class SeabedStructures
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_SIZE = 65536;

    public enum Status
    {
        PLACED, NO_ROLL, WRONG_BIOME, TERRAIN, CAVE, VENT_FIELD, NO_CAVERN, YIELDED;

        public String label()
        {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One profile entry, flattened for generation. */
    public record Slot(int index, ResourceLocation profileId, StructureProfile profile, ResourceLocation id, SeabedStructure definition,
                       StructureProfile.Entry entry, Set<ResourceKey<Biome>> biomes, long salt,
                       Palette<CaveEnvironment.PlantEntry> plants, Palette<BlockState> minerals)
    {
        public int cellSize()
        {
            return entry.maxDistance();
        }

        public SeabedStructure.Tier tier()
        {
            return definition.tier();
        }
    }

    /** A grid cell's candidate and its fate; {@code site} is set when it passed everything but the overlap check. */
    public record Candidate(Slot slot, int cellX, int cellZ, int x, int z, double chance, Status status, String reason, long rank, @Nullable Site site)
    {
        public boolean viable()
        {
            return site != null;
        }

        public boolean placed()
        {
            return status == Status.PLACED;
        }

        Candidate with(Status newStatus, String newReason)
        {
            return new Candidate(slot, cellX, cellZ, x, z, chance, newStatus, newReason, rank, site);
        }
    }

    private record Key(int slot, int cellX, int cellZ) {}

    private final CaveNetwork network;
    private final long seed;
    private final List<Slot> slots;
    private final int maxReach;
    private final Cache<Key, Candidate> viability = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
    private final Cache<Key, Candidate> resolved = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
    /** Biome at the corners of a coarse grid: a cheap, deterministic pre-filter before the exact centre lookup. */
    private final Cache<Long, ResourceKey<Biome>> coarse = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
    private final Cache<Long, ResourceKey<Biome>> points = CacheBuilder.newBuilder().maximumSize(4096).build();
    private final Cache<Key, String> caveVerdicts = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
    private final AtomicLong paintNanos = new AtomicLong(), lookupNanos = new AtomicLong(), dressNanos = new AtomicLong(),
            paintedChunks = new AtomicLong(), placedParts = new AtomicLong();
    /** Where first-time candidate evaluation spends its time (debug stats). */
    private final AtomicLong evalNanos = new AtomicLong(), terrainNanos = new AtomicLong(), caveNanos = new AtomicLong(), overlapNanos = new AtomicLong();

    private SeabedStructures(CaveNetwork network, long seed, List<Slot> slots)
    {
        this.network = network;
        this.seed = seed;
        this.slots = List.copyOf(slots);
        this.maxReach = slots.stream().mapToInt(s -> s.definition.footprintRadius()).max().orElse(0);
    }

    public static SeabedStructures create(CaveNetwork network, RegistryAccess registries, long seed)
    {
        List<Slot> slots = new ArrayList<>();
        var definitions = registries.registry(StructureRegistries.STRUCTURES).orElse(null);
        var profiles = registries.registry(StructureRegistries.PROFILES).orElse(null);
        if (definitions == null || profiles == null || network.isEmpty()) return new SeabedStructures(network, seed, slots);
        // Sorted by id so slot indices, and so salts and tie-breaks, never depend on registry iteration order.
        List<ResourceLocation> profileIds = profiles.keySet().stream().sorted().toList();
        for (ResourceLocation profileId : profileIds)
        {
            StructureProfile profile = profiles.get(profileId);
            Set<ResourceKey<Biome>> biomes = new HashSet<>();
            for (Holder<Biome> biome : profile.biomes()) biome.unwrapKey().ifPresent(biomes::add);
            for (StructureProfile.Entry entry : profile.structures())
            {
                SeabedStructure definition = definitions.get(entry.structure());
                if (definition == null)
                {
                    LOGGER.warn("Seabed structure profile {} names unknown structure {}; skipped", profileId, entry.structure());
                    continue;
                }
                long salt = RandomSupport.mixStafford13((long) profileId.toString().hashCode() * 0x9E3779B97F4A7C15L ^ entry.structure().toString().hashCode());
                slots.add(new Slot(slots.size(), profileId, profile, entry.structure(), definition, entry, Set.copyOf(biomes), salt,
                        definition.dressing().plantPalette(), definition.dressing().mineralPalette()));
            }
        }
        if (!slots.isEmpty()) LOGGER.info("Seabed structures: {} definitions, {} profile entries", definitions.size(), slots.size());
        return new SeabedStructures(network, seed, slots);
    }

    /** This level's structures, if its generator makes any (the deep ocean). */
    @Nullable
    public static SeabedStructures of(ServerLevel level)
    {
        if (!(level.getChunkSource().getGenerator() instanceof OceanChunkGenerator generator)) return null;
        SeabedStructures structures = generator.seabedStructures(level.getChunkSource().randomState(), level.registryAccess(), level.getSeed());
        return structures.isEmpty() ? null : structures;
    }

    public boolean isEmpty()
    {
        return slots.isEmpty();
    }

    public List<Slot> slots()
    {
        return slots;
    }

    public ResourceKey<Biome> biome(int x, int z)
    {
        return network.biome(x, z);
    }

    // ---------------------------------------------------------------- candidates

    private Candidate candidate(Slot slot, int cellX, int cellZ)
    {
        Key key = new Key(slot.index, cellX, cellZ);
        Candidate c = viability.getIfPresent(key);
        if (c == null)
        {
            long t = System.nanoTime();
            c = evaluate(slot, cellX, cellZ);
            evalNanos.addAndGet(System.nanoTime() - t);
            viability.put(key, c);
        }
        return c;
    }

    private Candidate evaluate(Slot slot, int cellX, int cellZ)
    {
        int cell = slot.cellSize();
        long cellSeed = seed ^ slot.salt ^ (cellX * 341873128712L + cellZ * 132897987541L);
        RandomSource random = new XoroshiroRandomSource(cellSeed);
        int margin = Math.min(slot.entry.minDistance() / 2, cell / 2 - 1);
        int x = cellX * cell + margin + random.nextInt(Math.max(1, cell - 2 * margin));
        int z = cellZ * cell + margin + random.nextInt(Math.max(1, cell - 2 * margin));
        double roll = random.nextDouble();
        long structureSeed = random.nextLong();
        long rank = RandomSupport.mixStafford13(cellSeed);
        SeabedStructure def = slot.definition;
        double chance = slot.entry.chance() * slot.profile.density() * Config.STRUCTURE_DENSITY.get()
                * (def.category() == SeabedStructure.Category.LANDMARK ? Config.LANDMARK_STRUCTURE_CHANCE.get() : 1.0);
        if (roll >= chance) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.NO_ROLL, "roll " + fmt(roll) + " >= " + fmt(chance), rank, null);

        // Biome regions span hundreds of blocks: if no corner of the coarse cell around the candidate is one of this
        // profile's biomes, it cannot be one either (short of a sliver), so skip the costly exact lookup.
        if (!coarseMayMatch(slot, x, z)) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.WRONG_BIOME, "biome (coarse)", rank, null);
        ResourceKey<Biome> biome = network.biome(x, z);
        if (biome == null || !slot.biomes.contains(biome))
        {
            return new Candidate(slot, cellX, cellZ, x, z, chance, Status.WRONG_BIOME, "biome " + (biome == null ? "?" : biome.location().getPath()), rank, null);
        }
        SeabedStructure.Conditions cond = def.conditions();
        int r = def.footprintRadius();
        Site site;
        if (def.anchor() == SeabedStructure.Anchor.CAVERN)
        {
            Cavern cavern = null;
            double best = 0.7;
            for (CaveSystem system : network.systemsNear(x - 1, z - 1, x + 1, z + 1))
            {
                for (CaveSpace space : system.spaces)
                {
                    if (space.cavern == null || space.cavern.space != space) continue;
                    double f = space.cavern.footprint(x, z);
                    if (f < best)
                    {
                        best = f;
                        cavern = space.cavern;
                    }
                }
            }
            if (cavern == null) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.NO_CAVERN, "no large cavern here", rank, null);
            double floor = cavern.floorAt(x, z), ceiling = cavern.ceilingAt(x, z);
            if (Double.isNaN(floor) || Double.isNaN(ceiling) || ceiling - floor < cond.minClearance())
            {
                return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "cavern too low here", rank, null);
            }
            int base = Mth.floor(floor) + 1;
            if (base < cond.minY() || base > cond.maxY()) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "cavern floor Y" + base + " outside " + cond.minY() + ".." + cond.maxY(), rank, null);
            if (cavern.lakeAt(x, z, r * 0.5) != null) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "cavern lake", rank, null);
            if (cavern.center() != null && Math.hypot(x - cavern.centerX(), z - cavern.centerZ()) < r + 12)
            {
                return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "cavern centrepiece " + cavern.center().getSerializedName(), rank, null);
            }
            // Roofs vary across the footprint; formations follow Site.ceilingAt, this only bounds the writes.
            int top = Math.min(maxTop(cond), Mth.floor(ceiling) + 16);
            site = new Site(slot.id, def, x, z, base, top, structureSeed, cavern, network);
        }
        else
        {
            long t0 = System.nanoTime();
            int base = network.seabed(x, z);
            if (base < cond.minY() || base > cond.maxY()) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "seabed Y" + base + " outside " + cond.minY() + ".." + cond.maxY(), rank, null);
            double reach = Math.max(6, r * 0.5);
            int slopeRise = 0;
            for (int i = 0; i < 4; i++)
            {
                double a = i * Math.PI / 2;
                slopeRise = Math.max(slopeRise, Math.abs(network.seabed(x + Math.cos(a) * reach, z + Math.sin(a) * reach) - base));
            }
            double slope = slopeRise / reach;
            if (slope < cond.minSlope() || slope > cond.maxSlope()) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "slope " + fmt(slope) + " outside " + fmt(cond.minSlope()) + ".." + fmt(cond.maxSlope()), rank, null);
            if (maxTop(cond) - base < 4) return new Candidate(slot, cellX, cellZ, x, z, chance, Status.TERRAIN, "no room under max_top_y " + maxTop(cond), rank, null);
            terrainNanos.addAndGet(System.nanoTime() - t0);
            if (def.tier() != SeabedStructure.Tier.SMALL && cond.avoidVentFields() && Config.THERMAL_VENTS.get()
                    && !ThermalVentField.near(seed, x - r, z - r, x + r, z + r, this::pointBiome).isEmpty())
            {
                return new Candidate(slot, cellX, cellZ, x, z, chance, Status.VENT_FIELD, "vent field in the footprint", rank, null);
            }
            site = new Site(slot.id, def, x, z, base, maxTop(cond), structureSeed, null, network);
        }
        return new Candidate(slot, cellX, cellZ, x, z, chance, Status.PLACED, "", rank, site);
    }

    private static final int COARSE = 64;
    /** Blocks kept between any structure top and the deep layer's rock ceiling (tools/gen_worldgen.py uses the same). */
    private static final int TOP_MARGIN = 4;

    private static int maxTop(SeabedStructure.Conditions cond)
    {
        return Math.min(cond.maxTopY(), DeepLayer.CEILING_BOTTOM_Y - TOP_MARGIN);
    }

    /** Why caves rule a viable seabed candidate out ("" if they do not), computed once per candidate. */
    private String caveVerdict(Candidate c)
    {
        if (c.site.cavern != null || !c.slot.definition.conditions().avoidCaves()) return "";
        Key key = new Key(c.slot.index, c.cellX, c.cellZ);
        String verdict = caveVerdicts.getIfPresent(key);
        if (verdict == null)
        {
            long t = System.nanoTime();
            String reason = caveConflict(c.slot.definition, c.x, c.z, c.site.baseY);
            caveNanos.addAndGet(System.nanoTime() - t);
            verdict = reason == null ? "" : reason;
            caveVerdicts.put(key, verdict);
        }
        return verdict;
    }

    /** Exact biome at a point, cached: vent field cells ask for the same cell centres over and over. */
    private ResourceKey<Biome> pointBiome(int x, int z)
    {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        ResourceKey<Biome> b = points.getIfPresent(key);
        if (b == null)
        {
            b = network.biome(x, z);
            if (b != null) points.put(key, b);
        }
        return b;
    }

    private boolean coarseMayMatch(Slot slot, int x, int z)
    {
        int cx = Math.floorDiv(x, COARSE), cz = Math.floorDiv(z, COARSE);
        for (int i = 0; i <= 1; i++)
        {
            for (int j = 0; j <= 1; j++)
            {
                long key = ((long) (cx + i) << 32) ^ ((cz + j) & 0xFFFFFFFFL);
                ResourceKey<Biome> b = coarse.getIfPresent(key);
                if (b == null)
                {
                    b = network.biome((cx + i) * COARSE, (cz + j) * COARSE);
                    if (b != null) coarse.put(key, b);
                }
                if (b != null && slot.biomes.contains(b)) return true;
            }
        }
        return false;
    }

    /**
     * A cave mouth under the structure's core (the solid body would bury or float over it; the thin outer apron may
     * reach one), or a cave under a digging structure anywhere in its footprint.
     */
    @Nullable
    private String caveConflict(SeabedStructure def, int x, int z, int base)
    {
        int r = def.footprintRadius();
        double core = r * 0.65;
        int dig = def.formation().carveDepth(def), digR = def.formation().carveReach(def);
        for (CaveSystem system : network.systemsNear(x - r, z - r, x + r, z + r))
        {
            for (CaveSpace space : system.spaces)
            {
                boolean mouth = space.role == CaveSpace.Role.ENTRANCE || space.role == CaveSpace.Role.SHAFT || space.role == CaveSpace.Role.ARCH;
                if (mouth && Math.hypot(space.x - x, space.z - z) < core + space.radius + 4) return "cave " + space.role.name().toLowerCase() + " at " + (int) space.x + " " + (int) space.z;
            }
            for (CaveSystem.Site s : system.sites)
            {
                if (Math.hypot(s.x() - x, s.z() - z) < core + s.radius() + 4) return "cave " + s.kind().name().toLowerCase() + " at " + (int) s.x() + " " + (int) s.z();
            }
            if (dig > 0)
            {
                for (CaveShape shape : system.shapes)
                {
                    if (shape.maxX >= x - digR && shape.minX <= x + digR && shape.maxZ >= z - digR && shape.minZ <= z + digR && shape.maxY >= base - dig - 6)
                    {
                        return "cave below the dig (Y" + shape.maxY + ")";
                    }
                }
            }
        }
        return null;
    }

    /** The candidate after overlaps: it yields to any viable larger one, or an equal one ranked higher. */
    public Candidate resolve(Slot slot, int cellX, int cellZ)
    {
        Key key = new Key(slot.index, cellX, cellZ);
        Candidate c = resolved.getIfPresent(key);
        if (c == null)
        {
            Candidate v = candidate(slot, cellX, cellZ);
            long t = System.nanoTime();
            c = overlap(v);
            overlapNanos.addAndGet(System.nanoTime() - t);
            // Caves last and only for survivors: the costliest check (it may build cave layouts).
            if (c.placed())
            {
                String cave = caveVerdict(c);
                if (!cave.isEmpty()) c = c.with(Status.CAVE, cave);
            }
            resolved.put(key, c);
        }
        return c;
    }

    private Candidate overlap(Candidate c)
    {
        if (!c.viable()) return c;
        int r = c.slot.definition.footprintRadius();
        for (Slot other : slots)
        {
            if (other.definition.anchor() != c.slot.definition.anchor() || other.tier().compareTo(c.slot.tier()) < 0) continue;
            int reach = r + other.definition.footprintRadius();
            int cell = other.cellSize();
            for (int cx = Math.floorDiv(c.x - reach, cell); cx <= Math.floorDiv(c.x + reach, cell); cx++)
            {
                for (int cz = Math.floorDiv(c.z - reach, cell); cz <= Math.floorDiv(c.z + reach, cell); cz++)
                {
                    if (other == c.slot && cx == c.cellX && cz == c.cellZ) continue;
                    Candidate o = candidate(other, cx, cz);
                    if (!o.viable()) continue;
                    double d = Math.hypot(o.x - c.x, o.z - c.z);
                    if (d >= reach) continue;
                    boolean wins = other.tier().compareTo(c.slot.tier()) > 0 || o.rank > c.rank;
                    // Only yield to a neighbour that will really stand (caves may still reject it).
                    if (wins && caveVerdict(o).isEmpty()) return c.with(Status.YIELDED, "overlaps " + other.id.getPath() + " at " + o.x + " " + o.z);
                }
            }
        }
        return c;
    }

    /** Every candidate (placed or not) of every entry whose footprint reaches the rectangle. */
    public List<Candidate> explain(int x0, int z0, int x1, int z1)
    {
        List<Candidate> out = new ArrayList<>();
        for (Slot slot : slots)
        {
            int r = slot.definition.footprintRadius(), cell = slot.cellSize();
            for (int cx = Math.floorDiv(x0 - r, cell); cx <= Math.floorDiv(x1 + r, cell); cx++)
            {
                for (int cz = Math.floorDiv(z0 - r, cell); cz <= Math.floorDiv(z1 + r, cell); cz++)
                {
                    Candidate c = candidate(slot, cx, cz);
                    if (!touches(c.x, c.z, r, x0, z0, x1, z1)) continue;
                    out.add(c.viable() ? resolve(slot, cx, cz) : c);
                }
            }
        }
        return out;
    }

    /** Placed structures whose footprint reaches the rectangle, largest first (paint order). */
    public List<Candidate> placedNear(int x0, int z0, int x1, int z1)
    {
        List<Candidate> out = new ArrayList<>();
        for (Slot slot : slots)
        {
            int r = slot.definition.footprintRadius(), cell = slot.cellSize();
            for (int cx = Math.floorDiv(x0 - r, cell); cx <= Math.floorDiv(x1 + r, cell); cx++)
            {
                for (int cz = Math.floorDiv(z0 - r, cell); cz <= Math.floorDiv(z1 + r, cell); cz++)
                {
                    Candidate c = candidate(slot, cx, cz);
                    if (!c.viable() || !touches(c.x, c.z, r, x0, z0, x1, z1)) continue;
                    c = resolve(slot, cx, cz);
                    if (c.placed()) out.add(c);
                }
            }
        }
        out.sort(Comparator.comparing((Candidate c) -> c.slot.tier()).reversed().thenComparingLong(Candidate::rank));
        return out;
    }

    /** Placed structures whose footprint contains the point (for fauna and the debug command). */
    public List<Candidate> at(int x, int z)
    {
        return placedNear(x, z, x, z).stream().filter(c -> Mth.square(c.x - x) + Mth.square(c.z - z) <= Mth.square(c.slot.definition.footprintRadius())).toList();
    }

    /** Nearest placed instance of a structure, searching its cells ring by ring out to {@code maxBlocks}. */
    @Nullable
    public Candidate locate(ResourceLocation id, int x, int z, int maxBlocks)
    {
        Candidate best = null;
        double bestDist = Double.MAX_VALUE;
        for (Slot slot : slots)
        {
            if (!slot.id.equals(id)) continue;
            int cell = slot.cellSize();
            int ox = Math.floorDiv(x, cell), oz = Math.floorDiv(z, cell);
            int rings = Math.max(1, maxBlocks / cell);
            for (int ring = 0; ring <= rings; ring++)
            {
                // Once a hit is closer than anything this ring can hold, stop.
                if (best != null && (ring - 1) * (double) cell > bestDist) break;
                for (int cx = ox - ring; cx <= ox + ring; cx++)
                {
                    for (int cz = oz - ring; cz <= oz + ring; cz++)
                    {
                        if (Math.max(Math.abs(cx - ox), Math.abs(cz - oz)) != ring) continue;
                        Candidate c = candidate(slot, cx, cz);
                        if (!c.viable()) continue;
                        c = resolve(slot, cx, cz);
                        double d = Math.hypot(c.x - x, c.z - z);
                        if (c.placed() && d < bestDist)
                        {
                            bestDist = d;
                            best = c;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean touches(int x, int z, int r, int x0, int z0, int x1, int z1)
    {
        int dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
        return dx * dx + dz * dz <= r * r;
    }

    // ---------------------------------------------------------------- painting

    /** Paints the bodies (or, in the dressing pass, the plants and minerals) of every structure reaching the chunk. */
    public boolean paint(WorldGenLevel level, ChunkPos chunk, boolean dressing)
    {
        long start = System.nanoTime();
        List<Candidate> here = placedNear(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ());
        long found = System.nanoTime();
        lookupNanos.addAndGet(found - start);
        for (Candidate c : here)
        {
            Painter painter = new Painter(level, chunk, c.site);
            if (painter.isEmpty()) continue;
            try
            {
                if (dressing)
                {
                    c.site.definition.formation().dress(c.site, painter);
                    dress(c.slot, c.site, painter);
                }
                else
                {
                    c.site.definition.formation().paint(c.site, painter);
                    placedParts.incrementAndGet();
                }
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Seabed structure {} at {} {} failed in chunk {}", c.slot.id, c.x, c.z, chunk, e);
            }
        }
        (dressing ? dressNanos : paintNanos).addAndGet(System.nanoTime() - found);
        if (!dressing) paintedChunks.incrementAndGet();
        return !here.isEmpty();
    }

    /** Plants and mineral crusts on every floor (and on the structure) near the centre, thinning outward. */
    private static void dress(Slot slot, Site site, Painter p)
    {
        SeabedStructure.Dressing d = site.definition.dressing();
        if (d.density() <= 0 || d.radius() <= 0) return;
        double reach = Math.min(d.radius(), site.radius());
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                double t = Math.hypot(x - site.x, z - site.z) / reach;
                if (t > 1 || !p.inReach(x, z)) continue;
                // Patchy rather than uniform: noise-gated clumps, denser near the structure.
                double patch = 0.55 + 0.45 * p.noise(x * 0.09, z * 0.09);
                if (p.hash(x, 0, z, 71) >= d.density() * (1 - t * t) * patch) continue;
                int y = p.floor(x, z);
                if (!slot.minerals.isEmpty() && p.hash(x, 1, z, 72) < d.mineralChance())
                {
                    p.recoat(x, y - 1, z, slot.minerals.pick(p.hash(x, 2, z, 73)));
                }
                if (!slot.plants.isEmpty()) p.plant(x, y, z, slot.plants.pick(p.hash(x, 3, z, 74)), p.hash(x, 4, z, 75));
            }
        }
    }

    public String stats()
    {
        long chunks = paintedChunks.get();
        double per = chunks == 0 ? 0 : 1e6 * chunks;
        return String.format(java.util.Locale.ROOT, "seabed structures: %d chunks, %d structure parts; ms/chunk: lookup %.3f (both passes), bodies %.3f, dressing %.3f; %d cached candidates",
                chunks, placedParts.get(), chunks == 0 ? 0 : lookupNanos.get() / per, chunks == 0 ? 0 : paintNanos.get() / per,
                chunks == 0 ? 0 : dressNanos.get() / per, viability.size())
                + String.format(java.util.Locale.ROOT, "; evaluation total %.0f ms (terrain %.0f, caves %.0f), overlap checks %.0f ms",
                evalNanos.get() / 1e6, terrainNanos.get() / 1e6, caveNanos.get() / 1e6, overlapNanos.get() / 1e6);
    }

    static String fmt(double v)
    {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
