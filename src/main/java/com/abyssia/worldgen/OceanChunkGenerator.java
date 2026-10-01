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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.shorts.ShortList;
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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

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
    public static final MapCodec<OceanChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            Codec.BOOL.optionalFieldOf("vanilla_fluids", false).forGetter(g -> g.vanillaFluids),
            DensityFunction.CODEC.optionalFieldOf("fluid_zone").forGetter(g -> g.fluidZone)
    ).apply(i, i.stable(OceanChunkGenerator::new)));

    private volatile CaveNetwork caveNetwork;
    private volatile SeabedStructures seabedStructures;
    private volatile CaveNetwork structuresNetwork;

    private final boolean vanillaFluids;
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
        // NoiseBasedChunkGenerator.globalFluidPicker, made public non-final by META-INF/accesstransformer.cfg
        this.globalFluidPicker = picker;
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

    @EventBusSubscriber(modid = Abyssia.MODID)
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
            super.buildSurface(region, structureManager, randomState, chunk);
            return;
        }
        int[] tops = hideDeepWater(chunk);
        try
        {
            super.buildSurface(region, structureManager, randomState, chunk);
        }
        finally
        {
            restoreDeepWater(chunk, tops);
        }
        deepStoneToRock(chunk, randomState);
    }

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final int NO_WATER = Integer.MIN_VALUE;

    /** Turns the top block of each column's first water run below the bedrock band into air; returns those Ys. */
    private static int[] hideDeepWater(ChunkAccess chunk)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        int[] tops = new int[256];
        for (int i = 0; i < 256; i++)
        {
            int x = i & 15, z = i >> 4, top = NO_WATER;
            for (int y = DeepLayer.TOP_Y - 1; y >= minY; y--)
            {
                LevelChunkSection section = sections[(y - minY) >> 4];
                if (section.getBlockState(x, y & 15, z) == WATER)
                {
                    section.setBlockState(x, y & 15, z, AIR, false);
                    top = y;
                    break;
                }
            }
            tops[i] = top;
        }
        return tops;
    }

    private static void restoreDeepWater(ChunkAccess chunk, int[] tops)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        for (int i = 0; i < 256; i++)
        {
            int y = tops[i];
            if (y != NO_WATER) sections[(y - minY) >> 4].setBlockState(i & 15, y & 15, i >> 4, WATER, false);
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
    private static void deepStoneToRock(ChunkAccess chunk, RandomState randomState)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight(), baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockState deepRock = ModBlocks.DEEP_SEA_ROCK.get().defaultBlockState();
        BlockState abyssalRock = ModBlocks.ABYSSAL_ROCK.get().defaultBlockState();
        PositionalRandomFactory gradient = null;
        int deepSections = Math.min(sections.length, (DeepLayer.TOP_Y - minY) >> 4);
        for (int s = 0; s < deepSections; s++)
        {
            LevelChunkSection section = sections[s];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state == STONE)) continue;
            int sectionY = minY + (s << 4);
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
                        if (section.getBlockState(x, ly, z) != STONE) continue;
                        BlockState state = mixed && gradient.at(baseX + x, y, baseZ + z).nextFloat() < chance ? abyssalRock : rock;
                        section.setBlockState(x, ly, z, state, false);
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
    protected MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }
}
