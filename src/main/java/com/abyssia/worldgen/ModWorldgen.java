package com.abyssia.worldgen;

import com.abyssia.Abyssia;
import com.abyssia.thermal.ThermalVentGenerator;
import com.abyssia.worldgen.structure.SeabedStructureFeature;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.mojang.serialization.MapCodec;

public final class ModWorldgen
{
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Abyssia.MODID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Abyssia.MODID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Abyssia.MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Abyssia.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Abyssia.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<OceanChunkGenerator>> OCEAN_NOISE = CHUNK_GENERATORS.register("ocean_noise", () -> OceanChunkGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<LayeredBiomeSource>> LAYERED = BIOME_SOURCES.register("layered", () -> LayeredBiomeSource.CODEC);
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RiftDensityFunction>> RIFT = DENSITY_FUNCTIONS.register("rift", RiftDensityFunction.CODEC::codec);

    public static final DeferredHolder<Feature<?>, Feature<ColumnPlantFeature.Config>> COLUMN_PLANT = FEATURES.register("column_plant", ColumnPlantFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<OreVeinFeature.VeinConfig>> ORE_VEIN = FEATURES.register("ore_vein", OreVeinFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<RockSpireFeature.Config>> ROCK_SPIRE = FEATURES.register("rock_spire", RockSpireFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> THERMAL_VENT_FIELD = FEATURES.register("thermal_vent_field", ThermalVentGenerator::new);
    public static final DeferredHolder<Feature<?>, Feature<CrystalSpikeFeature.Config>> CRYSTAL_SPIKE = FEATURES.register("crystal_spike", CrystalSpikeFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> ANCIENT_TREE = FEATURES.register("ancient_tree", AncientTreeFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> OIL_KELP = FEATURES.register("oil_kelp", OilKelpFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<RootArchFeature.Config>> ROOT_ARCH = FEATURES.register("root_arch", RootArchFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> SEABED_STRUCTURES = FEATURES.register("seabed_structures", () -> new SeabedStructureFeature(false));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> SEABED_STRUCTURE_DRESSING = FEATURES.register("seabed_structure_dressing", () -> new SeabedStructureFeature(true));

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigPlacement>> CONFIG_PLACEMENT = PLACEMENTS.register("config", () -> () -> ConfigPlacement.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<DepthFilter>> DEPTH_FILTER = PLACEMENTS.register("depth", () -> () -> DepthFilter.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<DeepFloorPlacement>> DEEP_FLOOR = PLACEMENTS.register("deep_floor", () -> () -> DeepFloorPlacement.CODEC);

    private ModWorldgen() {}

    public static void register(IEventBus modBus)
    {
        FEATURES.register(modBus);
        PLACEMENTS.register(modBus);
        CHUNK_GENERATORS.register(modBus);
        DENSITY_FUNCTIONS.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
