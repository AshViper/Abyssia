package com.abyssia.worldgen;

import com.abyssia.Config;
import com.abyssia.worldgen.cave.CaveGenerator;
import com.abyssia.worldgen.cave.CaveNetwork;
import com.abyssia.worldgen.structure.SeabedStructures;
import com.abyssia.worldgen.terrain.VanillaTerrainFallback;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.function.Supplier;

/**
 * Noise chunk generator whose open space is filled with the settings' default fluid at every height.
 * Vanilla hardcodes lava below Y -54, which would turn deep trenches and the hadal zone into lava.
 * <p>
 * It also carves the deep ocean's cave network ({@link CaveGenerator}) at the carver stage. The network is built
 * per world from the datapack cave profiles; dimensions whose biomes have no profile (the ocean world) get none.
 */
public class OceanChunkGenerator extends NoiseBasedChunkGenerator
{
    public static final Codec<OceanChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(i, i.stable(OceanChunkGenerator::new)));

    // NoiseBasedChunkGenerator.globalFluidPicker
    private static final String FLUID_PICKER_FIELD = "f_188607_";

    private volatile CaveNetwork caveNetwork;
    private volatile SeabedStructures seabedStructures;
    private volatile CaveNetwork structuresNetwork;

    public OceanChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings)
    {
        super(biomeSource, settings);
        Supplier<Aquifer.FluidPicker> picker = Suppliers.memoize(() -> {
            NoiseGeneratorSettings s = settings.value();
            Aquifer.FluidStatus fluid = new Aquifer.FluidStatus(s.seaLevel(), s.defaultFluid());
            return (x, y, z) -> fluid;
        });
        ObfuscationReflectionHelper.setPrivateValue(NoiseBasedChunkGenerator.class, this, picker, FLUID_PICKER_FIELD);
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

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step)
    {
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
