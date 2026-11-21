package com.abyssia.worldgen;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.registry.ModBlocks;
import com.abyssia.worldgen.cave.CaveGenerator;
import com.abyssia.worldgen.cave.CaveNetwork;
import com.abyssia.worldgen.structure.SeabedStructures;
import com.abyssia.worldgen.terrain.VanillaTerrainFallback;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.shorts.ShortList;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.util.Mth;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Noise chunk generator whose open space is filled with the settings' default fluid at every height.
 * Vanilla hardcodes lava below Y -54, which would turn deep trenches and the hadal zone into lava.
 * <p>
 * It also carves the deep layer's cave network ({@link CaveGenerator}) at the carver stage. The network is built
 * per world from the datapack cave profiles; dimensions whose biomes have no profile get none.
 * <p>
 * Below the bedrock band lies the deep layer ({@link DeepLayer}). Vanilla generation (above_bottom anchors of ores
 * and surface rules, carvers, mineshafts, strongholds) is told the world starts at the band, so it stays in the ocean
 * world as before; the deep layer's own data uses absolute heights.
 * <p>
 * {@code vanilla_fluids} (the default, vanilla-land world, inbox/specs/M02-vanilla-default-deep-layer.md): above the
 * bedrock band the fluids are vanilla's (lava below Y -54) except where {@code fluid_zone} &gt; 0 (around the deep
 * fissures, which stay water down to the deep layer); at and below the band's bottom everything is water. Without it
 * (the Abyssia ocean world) every height gets the settings' default fluid.
 */
public class OceanChunkGenerator extends NoiseBasedChunkGenerator
{
    public static final Codec<OceanChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            Codec.BOOL.optionalFieldOf("vanilla_fluids", false).forGetter(g -> g.vanillaFluids),
            DensityFunction.CODEC.optionalFieldOf("fluid_zone").forGetter(g -> g.fluidZone)
    ).apply(i, i.stable(OceanChunkGenerator::new)));

    // NoiseBasedChunkGenerator.globalFluidPicker
    private static final String FLUID_PICKER_FIELD = "f_188607_";

    private volatile CaveNetwork caveNetwork;
    private volatile SeabedStructures seabedStructures;
    private volatile CaveNetwork structuresNetwork;

    private final boolean vanillaFluids;
    /** the picker handed to vanilla (kept for {@link DeepAquifer}) */
    private final Supplier<Aquifer.FluidPicker> fluidPicker;
    private final Optional<Holder<DensityFunction>> fluidZone;
    /** {@link #fluidZone} wired to the world's noises; null until {@link #bindFluidZone} ran. */
    private volatile ZoneBinding zoneBinding;

    public OceanChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings)
    {
        this(biomeSource, settings, false, Optional.empty());
    }

    public OceanChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, boolean vanillaFluids, Optional<Holder<DensityFunction>> fluidZone)
    {
        super(biomeSource, settings);
        this.vanillaFluids = vanillaFluids;
        this.fluidZone = fluidZone;
        Supplier<Aquifer.FluidPicker> picker = Suppliers.memoize(() -> {
            NoiseGeneratorSettings s = settings.value();
            Aquifer.FluidStatus fluid = new Aquifer.FluidStatus(s.seaLevel(), s.defaultFluid());
            if (!vanillaFluids) return (x, y, z) -> fluid;
            // Vanilla's lava sheet (NoiseBasedChunkGenerator.createFluidPicker), only between the band's bottom and
            // Y -54, and not inside the fissure zone.
            Aquifer.FluidStatus lava = new Aquifer.FluidStatus(VANILLA_LAVA_LEVEL, Blocks.LAVA.defaultBlockState());
            int lavaBelow = Math.min(VANILLA_LAVA_LEVEL, s.seaLevel());
            return (x, y, z) -> y > DeepLayer.TOP_Y && y < lavaBelow && fissureZone(x, z) <= 0 ? lava : fluid;
        });
        ObfuscationReflectionHelper.setPrivateValue(NoiseBasedChunkGenerator.class, this, picker, FLUID_PICKER_FIELD);
        this.fluidPicker = picker;
    }

    // ---------------------------------------------------------------- vanilla fluids: the fissure zone

    private static final int VANILLA_LAVA_LEVEL = -54;
    private static final int ZONE_CACHE_SIZE = 64;

    /** Last zone values per thread, direct-mapped by column (the zone is 2D; the aquifer asks a few columns in turn). */
    private static final class ZoneCache
    {
        final long[] keys = new long[ZONE_CACHE_SIZE];
        final double[] values = new double[ZONE_CACHE_SIZE];

        ZoneCache()
        {
            java.util.Arrays.fill(keys, Long.MIN_VALUE);
        }
    }

    private record ZoneBinding(RandomState randomState, DensityFunction zone, ThreadLocal<ZoneCache> cache) {}

    public boolean vanillaFluids()
    {
        return vanillaFluids;
    }

    /**
     * Wires {@code fluid_zone} to this world's noises (as {@link RandomState} wires the router). Called when the
     * world's chunk map is created ({@link #createState}), on level load and lazily before generation work; the latest
     * random state wins.
     */
    public void bindFluidZone(RandomState randomState)
    {
        if (!vanillaFluids || fluidZone.isEmpty()) return;
        ZoneBinding binding = zoneBinding;
        if (binding != null && binding.randomState() == randomState) return;
        synchronized (this)
        {
            binding = zoneBinding;
            if (binding != null && binding.randomState() == randomState) return;
            DensityFunction wired = fluidZone.get().value().mapAll(new DensityFunction.Visitor()
            {
                @Override
                public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise)
                {
                    return new DensityFunction.NoiseHolder(noise.noiseData(), randomState.getOrCreateNoise(noise.noiseData().unwrapKey().orElseThrow()));
                }

                @Override
                public DensityFunction apply(DensityFunction function)
                {
                    // Outside a NoiseChunk the cache markers and holder indirections only cost time.
                    if (function instanceof DensityFunctions.HolderHolder holder) return holder.function().value();
                    if (function instanceof DensityFunctions.MarkerOrMarked marker) return marker.wrapped();
                    return function;
                }
            });
            zoneBinding = new ZoneBinding(randomState, wired, ThreadLocal.withInitial(ZoneCache::new));
        }
    }

    /** The fissure zone at a column (&gt; 0: no lava); without a zone (or before binding) -1, i.e. vanilla lava. */
    private double fissureZone(int x, int z)
    {
        ZoneBinding binding = zoneBinding;
        if (binding == null) return -1;
        long key = ChunkPos.asLong(x, z);
        int slot = (int) HashCommon.mix(key) & (ZONE_CACHE_SIZE - 1);
        ZoneCache cache = binding.cache().get();
        if (cache.keys[slot] == key) return cache.values[slot];
        double value = binding.zone().compute(new DensityFunction.SinglePointContext(x, 0, z));
        cache.keys[slot] = key;
        cache.values[slot] = value;
        return value;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed)
    {
        bindFluidZone(randomState);
        return super.createState(structureSets, randomState, seed);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk)
    {
        bindFluidZone(randomState);
        return super.createBiomes(executor, randomState, blender, structureManager, chunk);
    }

    /**
     * With vanilla fluids the aquifer marks deep water for fluid ticks wherever its cells meet; the deep layer is one
     * body of still water, so those marks (sections below the bedrock band) are dropped once the noise is filled.
     */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk)
    {
        if (!vanillaFluids) return super.fillFromNoise(executor, blender, randomState, structureManager, chunk);
        bindFluidZone(randomState);
        if (DeepLayer.PERF06 && generatorSettings().value().isAquifersEnabled())
        {
            // PERF06: the chunk's noise chunk (vanilla's own from the biome step, or the same one built here after a reload)
            // gets the deep layer short cut in front of its aquifer before vanilla fills from it.
            return CompletableFuture.supplyAsync(() -> {
                DeepAquifer.install(chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, randomState,
                        Beardifier.forStructuresInChunk(structureManager, c.getPos()), generatorSettings().value(), fluidPicker.get(), blender)), fluidPicker.get());
                return chunk;
            }, executor).thenCompose(c -> super.fillFromNoise(executor, blender, randomState, structureManager, c)).thenApply(OceanChunkGenerator::clearDeepPostProcessing);
        }
        return super.fillFromNoise(executor, blender, randomState, structureManager, chunk).thenApply(OceanChunkGenerator::clearDeepPostProcessing);
    }

    private static ChunkAccess clearDeepPostProcessing(ChunkAccess chunk)
    {
        ShortList[] marks = chunk.getPostProcessing();
        int deepSections = Math.min(marks.length, (DeepLayer.TOP_Y - chunk.getMinBuildHeight()) >> 4);
        for (int s = 0; s < deepSections; s++)
        {
            if (marks[s] != null) marks[s].clear();
        }
        return chunk;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState)
    {
        bindFluidZone(randomState);
        return super.getBaseHeight(x, z, type, level, randomState);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState)
    {
        bindFluidZone(randomState);
        return super.getBaseColumn(x, z, level, randomState);
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID)
    public static final class Events
    {
        private Events() {}

        @SubscribeEvent
        public static void onLevelLoad(LevelEvent.Load event)
        {
            if (event.getLevel() instanceof ServerLevel level && level.getChunkSource().getGenerator() instanceof OceanChunkGenerator generator)
            {
                generator.bindFluidZone(level.getChunkSource().randomState());
            }
        }
    }

    /** This world's cave network, created on first use (and again if the world, and so its seed, changes). */
    public CaveNetwork caveNetwork(RandomState randomState, RegistryAccess registries, long seed)
    {
        CaveNetwork network = caveNetwork;
        if (network == null || !network.matches(randomState, seed))
        {
            synchronized (this)
            {
                network = caveNetwork;
                if (network == null || !network.matches(randomState, seed))
                {
                    network = CaveNetwork.create(this, randomState, registries, seed);
                    caveNetwork = network;
                }
            }
        }
        return network;
    }

    /** This world's seabed structure placement, rebuilt together with the cave network it samples. */
    public SeabedStructures seabedStructures(RandomState randomState, RegistryAccess registries, long seed)
    {
        CaveNetwork network = caveNetwork(randomState, registries, seed);
        SeabedStructures structures = seabedStructures;
        if (structures == null || structuresNetwork != network)
        {
            synchronized (this)
            {
                if (seabedStructures == null || structuresNetwork != network)
                {
                    seabedStructures = SeabedStructures.create(network, registries, seed);
                    structuresNetwork = network;
                }
                structures = seabedStructures;
            }
        }
        return structures;
    }

    /** The generation range vanilla sees ({@link net.minecraft.world.level.levelgen.WorldGenerationContext}): the ocean world only. */
    @Override
    public int getMinY()
    {
        return Math.max(super.getMinY(), DeepLayer.TOP_Y);
    }

    @Override
    public int getGenDepth()
    {
        return super.getMinY() + super.getGenDepth() - getMinY();
    }

    /**
     * Surface rules count stone depth from the last air above, and water does not reset that count: under the ocean
     * world's seabed, the ceiling and the deep water, the deep seabed would never be a "floor". While the surface is
     * built, the top block of each column's open deep water (right under the ceiling) is air: vanilla
     * {@code SurfaceSystem.buildSurface} resets the count on air and leaves it at 0 through the water below, so the deep
     * seabed counts from 1 as it did in the deep ocean dimension (stone depth below and the deep rules ignore water, and
     * the deep rules test no water height). Water-filled caves below the seabed stay water, as they were there.
     * <p>
     * Afterwards the deep layer's remaining stone (what the deep surface rules leave: buried rock and the ceiling)
     * becomes abyssal rock deep down and deep sea rock above, written straight into the sections (no heightmap change:
     * all block motion) instead of by one surface-rule write per block.
     */
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk)
    {
        bindFluidZone(randomState);
        if (chunk.getMinBuildHeight() >= DeepLayer.TOP_Y)
        {
            surfaceRules(region, structureManager, randomState, chunk);
            return;
        }
        crustToRock(chunk, randomState);
        boolean[] buried = DeepLayer.PERF04 ? buriedStone(chunk) : new boolean[chunk.getSections().length];
        deepStoneToRock(chunk, randomState, buried);
        int[] tops = hideDeepWater(chunk);
        LevelChunkSection[] crust = hideCrust(chunk, buried, DeepLayer.PERF04 ? openWater(chunk, tops) : new boolean[buried.length]);
        try
        {
            surfaceRules(region, structureManager, randomState, chunk);
        }
        finally
        {
            restoreCrust(chunk, crust);
            restoreDeepWater(chunk, tops);
        }
        deepStoneToRock(chunk, randomState, null);
    }

    /**
     * PERF04: deep layer sections of nothing but stone whose neighbours above and below are all stone too (the rock under
     * land, DL01). No surface rule reaches a block buried at least 16 deep on both sides, so these become rock before the
     * rules run; {@link #hideCrust} then swaps out the ones that keep a converted section on both sides.
     */
    private static boolean[] buriedStone(ChunkAccess chunk)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        int deepSections = Math.min(sections.length, (DeepLayer.TOP_Y - minY) >> 4);
        boolean[] stone = new boolean[deepSections];
        BlockState rock = ModBlocks.ABYSSAL_ROCK.get().defaultBlockState();
        for (int s = 0; s < deepSections; s++)
        {
            LevelChunkSection section = sections[s];
            // The crust's top section (already rock, never hidden) counts as solid cover for the section above it.
            stone[s] = !section.hasOnlyAir() && only(section, minY + (s << 4) + 15 >= DeepLayer.ABYSS_TOP_Y - 8 ? STONE : rock);
        }
        boolean[] buried = new boolean[sections.length];
        for (int s = 1; s + 1 < deepSections; s++)
            buried[s] = minY + (s << 4) + 15 >= DeepLayer.ABYSS_TOP_Y - 8 && stone[s - 1] && stone[s] && stone[s + 1];
        return buried;
    }

    /**
     * PERF03: vanilla's {@code buildSurface(WorldGenRegion, ...)} with the region's biome lookups served from the last chunk read
     * (the same chunk the region would hand out, at the same status) instead of one holder lookup per surface-rule biome test.
     */
    private void surfaceRules(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk)
    {
        if (SharedConstants.debugVoidTerrain(chunk.getPos())) return;
        buildSurface(chunk, new WorldGenerationContext(this, region), randomState, structureManager,
                region.getBiomeManager().withDifferentSource(new RegionBiomes(region)), region.registryAccess().registryOrThrow(Registries.BIOME), Blender.of(region));
    }

    private static final class RegionBiomes implements BiomeManager.NoiseBiomeSource
    {
        private final WorldGenRegion region;
        private int sectionX, sectionZ;
        private ChunkAccess chunk;
        private int lastX, lastY, lastZ;
        private Holder<Biome> last;

        RegionBiomes(WorldGenRegion region)
        {
            this.region = region;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z)
        {
            if (last != null && x == lastX && y == lastY && z == lastZ) return last;
            int sx = QuartPos.toSection(x), sz = QuartPos.toSection(z);
            if (chunk == null || sx != sectionX || sz != sectionZ)
            {
                chunk = region.getChunk(sx, sz, ChunkStatus.BIOMES, false);
                // As LevelReader.getNoiseBiome: a chunk the region does not hold falls back to the uncached biome.
                if (chunk == null) return region.getUncachedNoiseBiome(x, y, z);
                sectionX = sx;
                sectionZ = sz;
            }
            last = chunk.getNoiseBiome(x, y, z);
            lastX = x;
            lastY = y;
            lastZ = z;
            return last;
        }
    }

    /**
     * AB02: the abyss crust (everything under the deep layer's rock slab) is one uniform stone body with no surface of its
     * own (caves are carved later and painted by CaveGeology), so running the surface rules over it is pure cost: vanilla's
     * SurfaceSystem walks every block of the column. Here its stone is turned into abyssal rock before the rules run
     * (they only touch the default block) and the world's bedrock floor, which is a surface rule, is written directly with
     * the same gradient and random stream. Sections that reach the deep layer's slab are left to the surface rules.
     */
    private static void crustToRock(ChunkAccess chunk, RandomState randomState)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight(), baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockState rock = ModBlocks.ABYSSAL_ROCK.get().defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        int top = DeepLayer.ABYSS_TOP_Y - 8;
        PositionalRandomFactory floor = null;
        for (int s = 0; s < sections.length; s++)
        {
            int sectionY = minY + (s << 4);
            if (sectionY + 15 >= top) break;
            LevelChunkSection section = sections[s];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state == STONE)) continue;
            // PERF03: a section of nothing but stone clear of the bedrock band becomes one uniform rock section at once.
            if (sectionY >= DeepLayer.MIN_Y + 5 && onlyStone(section))
            {
                sections[s] = uniform(section, rock);
                continue;
            }
            PalettedContainer<BlockState> states = section.getStates();
            for (int ly = 0; ly < 16; ly++)
            {
                int y = sectionY + ly;
                boolean bedrockLayer = y < DeepLayer.MIN_Y + 5;
                if (bedrockLayer && floor == null) floor = randomState.getOrCreateRandomFactory(new ResourceLocation("bedrock_floor"));
                double chance = bedrockLayer ? Mth.map(y, DeepLayer.MIN_Y, DeepLayer.MIN_Y + 5, 1.0, 0.0) : 0;
                for (int z = 0; z < 16; z++)
                {
                    for (int x = 0; x < 16; x++)
                    {
                        if (states.get(x, ly, z) != STONE) continue;
                        boolean b = bedrockLayer && (y <= DeepLayer.MIN_Y || floor.at(baseX + x, y, baseZ + z).nextFloat() < chance);
                        // Stone, rock and bedrock are all solid, non-ticking, fluid-free: the section's block counts do not change.
                        states.getAndSetUnchecked(x, ly, z, b ? bedrock : rock);
                    }
                }
            }
        }
    }

    /**
     * After {@link #crustToRock} no crust section holds the default block, so the surface rules never write there, yet
     * vanilla's SurfaceSystem still reads every block of them (and its stone look-ahead runs through all of them). While
     * the rules run, those sections are swapped for empty ones (same biomes), which read as air at once. One converted
     * section is kept under the sections the rules work on, so their ceiling test (stone below, offset 0) sees the same
     * rock under them. Returns the originals (null where nothing was swapped).
     */
    /**
     * PERF05: deep layer sections of nothing but water that every column enters straight from the top of its first water
     * run (the block {@link #hideDeepWater} turned to air), with no rock ledge in between. The rules only walk through
     * such water with a stone depth of 0, exactly as they walk through air, so these sections can be hidden from them.
     * A column that passed a ledge carries its stone depth through the water below; nothing under one is hidden.
     */
    private static boolean[] openWater(ChunkAccess chunk, int[] tops)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        boolean[] hide = new boolean[sections.length];
        boolean[] run = new boolean[256];
        int running = 0;
        for (int s = (DeepLayer.TOP_Y - 1 - minY) >> 4; s >= 0 && minY + (s << 4) >= DeepLayer.ABYSS_TOP_Y; s--)
        {
            LevelChunkSection section = sections[s];
            int sectionY = minY + (s << 4);
            if (section.hasOnlyAir()) return hide;
            if (only(section, WATER))
            {
                if (running < 256) return hide;
                hide[s] = true;
                continue;
            }
            for (int i = 0; i < 256; i++)
            {
                for (int ly = 15; ly >= 0; ly--)
                {
                    if (sectionY + ly == tops[i])
                    {
                        run[i] = true;
                        running++;
                    }
                    else if (run[i] && section.getBlockState(i & 15, ly, i >> 4) != WATER) return hide;
                }
            }
        }
        return hide;
    }

    private static LevelChunkSection[] hideCrust(ChunkAccess chunk, boolean[] buried, boolean[] water)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight(), top = DeepLayer.ABYSS_TOP_Y - 8;
        int converted = 0;
        while (converted < sections.length && minY + (converted << 4) + 15 < top) converted++;
        LevelChunkSection[] originals = new LevelChunkSection[sections.length];
        for (int s = 0; s < originals.length; s++)
        {
            // The crust (all but its top section), and buried deep rock under three converted sections and over one: the
            // rules that count stone depth from the last air above reach more than one section down.
            // PERF05: and the open water sections of openWater.
            LevelChunkSection section = sections[s];
            if (section.hasOnlyAir()) continue;
            boolean rock = s < converted - 1 || (s > 0 && s + 3 < buried.length && buried[s - 1] && buried[s] && buried[s + 1] && buried[s + 2] && buried[s + 3]);
            if (!rock && !water[s]) continue;
            originals[s] = section;
            sections[s] = new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, AIR, PalettedContainer.Strategy.SECTION_STATES), section.getBiomes());
        }
        return originals;
    }

    private static void restoreCrust(ChunkAccess chunk, LevelChunkSection[] originals)
    {
        LevelChunkSection[] sections = chunk.getSections();
        for (int s = 0; s < originals.length; s++)
        {
            if (originals[s] != null) sections[s] = originals[s];
        }
    }

    /** Whether every block of the section is stone (counted from the storage: unused palette entries do not matter). */
    private static boolean onlyStone(LevelChunkSection section)
    {
        return only(section, STONE);
    }

    private static boolean only(LevelChunkSection section, BlockState block)
    {
        boolean[] other = new boolean[1];
        section.getStates().count((state, n) ->
        {
            if (state != block) other[0] = true;
        });
        return !other[0];
    }

    /** A section of one block state everywhere, keeping the section's biomes. */
    private static LevelChunkSection uniform(LevelChunkSection section, BlockState state)
    {
        return new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state, PalettedContainer.Strategy.SECTION_STATES), section.getBiomes());
    }

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final int NO_WATER = Integer.MIN_VALUE;
    private static final java.util.function.Predicate<BlockState> IS_WATER = state -> state == WATER;

    /**
     * Turns the top block of each column's first water run below the bedrock band, and of its first water run in the
     * abyss layer (under the deep layer's rock slab), into air; returns those Ys (deep at {@code i}, abyss at
     * {@code 256 + i}).
     */
    private static int[] hideDeepWater(ChunkAccess chunk)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        int[] tops = new int[512];
        for (int i = 0; i < 256; i++)
        {
            int x = i & 15, z = i >> 4;
            tops[i] = hideTop(sections, minY, x, z, DeepLayer.TOP_Y - 1, DeepLayer.ABYSS_TOP_Y);
            tops[256 + i] = hideTop(sections, minY, x, z, DeepLayer.ABYSS_TOP_Y - 1, minY - 1);
        }
        return tops;
    }

    /** Air in place of the first water block at or below {@code from} (and above {@code stop}); its Y, or NO_WATER. */
    private static int hideTop(LevelChunkSection[] sections, int minY, int x, int z, int from, int stop)
    {
        for (int y = from; y > stop && y >= minY; y--)
        {
            LevelChunkSection section = sections[(y - minY) >> 4];
            // AB02: the abyss is a solid crust now, so most columns hold no water down to the bedrock: skip dry sections whole.
            if (section.hasOnlyAir() || !section.maybeHas(IS_WATER))
            {
                y -= y & 15;
                continue;
            }
            if (section.getBlockState(x, y & 15, z) == WATER)
            {
                section.setBlockState(x, y & 15, z, AIR, false);
                return y;
            }
        }
        return NO_WATER;
    }

    private static void restoreDeepWater(ChunkAccess chunk, int[] tops)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        for (int i = 0; i < tops.length; i++)
        {
            int y = tops[i];
            if (y != NO_WATER) sections[(y - minY) >> 4].setBlockState(i & 15, y & 15, (i >> 4) & 15, WATER, false);
        }
    }

    /**
     * The abyssal layer: buried deep stone at and below old deep Y 100 is abyssal rock, above old deep Y 110 deep sea
     * rock, mixed in between exactly as a surface-rule {@code minecraft:vertical_gradient} with this random name did.
     */
    private static final ResourceLocation ABYSSAL_LAYER_RANDOM = ResourceLocation.fromNamespaceAndPath("abyssia", "abyssal_layer");
    private static final int ABYSSAL_TRUE_AT_AND_BELOW = (int) DeepLayer.fromDeepY(100);
    private static final int ABYSSAL_FALSE_AT_AND_ABOVE = (int) DeepLayer.fromDeepY(110);

    /** The deep layer's stone (sections below the bedrock band) to abyssal / deep sea rock, unlocked, in place. */
    private static void deepStoneToRock(ChunkAccess chunk, RandomState randomState, @Nullable boolean[] only)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight(), baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockState deepRock = ModBlocks.DEEP_SEA_ROCK.get().defaultBlockState();
        BlockState abyssalRock = ModBlocks.ABYSSAL_ROCK.get().defaultBlockState();
        PositionalRandomFactory gradient = null;
        int deepSections = Math.min(sections.length, (DeepLayer.TOP_Y - minY) >> 4);
        for (int s = 0; s < deepSections; s++)
        {
            if (only != null && !only[s]) continue;
            LevelChunkSection section = sections[s];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state == STONE)) continue;
            int sectionY = minY + (s << 4);
            // PERF03: a section of nothing but stone that lies wholly in one rock's range becomes one uniform section at once.
            if (sectionY + 15 <= ABYSSAL_TRUE_AT_AND_BELOW || sectionY >= ABYSSAL_FALSE_AT_AND_ABOVE)
            {
                if (onlyStone(section))
                {
                    sections[s] = uniform(section, sectionY + 15 <= ABYSSAL_TRUE_AT_AND_BELOW ? abyssalRock : deepRock);
                    continue;
                }
            }
            PalettedContainer<BlockState> states = section.getStates();
            for (int ly = 0; ly < 16; ly++)
            {
                int y = sectionY + ly;
                boolean mixed = y > ABYSSAL_TRUE_AT_AND_BELOW && y < ABYSSAL_FALSE_AT_AND_ABOVE;
                BlockState rock = y <= ABYSSAL_TRUE_AT_AND_BELOW ? abyssalRock : deepRock;
                double chance = mixed ? Mth.map(y, ABYSSAL_TRUE_AT_AND_BELOW, ABYSSAL_FALSE_AT_AND_ABOVE, 1.0, 0.0) : 0;
                if (mixed && gradient == null) gradient = randomState.getOrCreateRandomFactory(ABYSSAL_LAYER_RANDOM);
                for (int z = 0; z < 16; z++)
                {
                    for (int x = 0; x < 16; x++)
                    {
                        if (states.get(x, ly, z) != STONE) continue;
                        BlockState state = mixed && gradient.at(baseX + x, y, baseZ + z).nextFloat() < chance ? abyssalRock : rock;
                        states.getAndSetUnchecked(x, ly, z, state);
                    }
                }
            }
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step)
    {
        bindFluidZone(randomState);
        super.applyCarvers(region, seed, randomState, biomeManager, structureManager, chunk, step);
        if (step != GenerationStep.Carving.AIR) return;
        CaveGenerator.generate(caveNetwork(randomState, region.registryAccess(), seed), chunk);
        // Runs after the caves so their walls are converted too.
        if (!Config.CUSTOM_BLOCKS_ONLY.get()) VanillaTerrainFallback.apply(chunk);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }
}
