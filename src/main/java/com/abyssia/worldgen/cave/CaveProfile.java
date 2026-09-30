package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

/**
 * How caves form under a group of biomes: how often a cave system (and how often a minor cave or sea arch) forms,
 * which cave types and landmarks it may be and how likely each is, how often neighbouring systems are joined by
 * tunnels, the environment generic caves take on, the rock strata cave walls cut through, and the ores exposed in them.
 * <p>
 * A datapack registry ({@code data/<ns>/abyssia/cave_profile/*.json}). A biome without a profile gets no cave network.
 */
public final class CaveProfile
{
    /** Rock at and below {@code depth} blocks under the seabed, until the next stratum. */
    public record Stratum(int depth, BlockState state)
    {
        public static final Codec<Stratum> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 512).fieldOf("depth").forGetter(Stratum::depth),
                BlockState.CODEC.fieldOf("state").forGetter(Stratum::state)
        ).apply(i, Stratum::new));
    }

    public static final Codec<CaveProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(p -> p.biomes),
            Codec.floatRange(0, 1).fieldOf("system_chance").forGetter(p -> p.systemChance),
            Codec.floatRange(0, 1).optionalFieldOf("minor_cave_chance", 0.3f).forGetter(p -> p.minorCaveChance),
            Codec.floatRange(0, 1).optionalFieldOf("connection_chance", 0.4f).forGetter(p -> p.connectionChance),
            SimpleWeightedRandomList.wrappedCodec(CaveType.CODEC).fieldOf("cave_types").forGetter(p -> p.caveTypeList),
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CaveType.CODEC).optionalFieldOf("minor_types", SimpleWeightedRandomList.empty()).forGetter(p -> p.minorTypeList),
            Codec.floatRange(0, 1).optionalFieldOf("landmark_chance", 0f).forGetter(p -> p.landmarkChance),
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CaveLandmark.CODEC).optionalFieldOf("landmarks", SimpleWeightedRandomList.empty()).forGetter(p -> p.landmarkList),
            ResourceLocation.CODEC.fieldOf("environment").forGetter(p -> p.environment),
            Codec.floatRange(0, 1).optionalFieldOf("luminous_chance", 0f).forGetter(p -> p.luminousChance),
            Stratum.CODEC.listOf().fieldOf("strata").forGetter(p -> p.strata),
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(BlockState.CODEC).optionalFieldOf("ores", SimpleWeightedRandomList.empty()).forGetter(p -> p.oreList),
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(ResourceLocation.CODEC).optionalFieldOf("cavern_templates", SimpleWeightedRandomList.empty()).forGetter(p -> p.templateList),
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CavernTemplate.CrystalColor.CODEC).optionalFieldOf("crystal_colors", SimpleWeightedRandomList.empty()).forGetter(p -> p.crystalList)
    ).apply(i, CaveProfile::new));

    public final HolderSet<Biome> biomes;
    public final float systemChance;
    public final float minorCaveChance;
    public final float connectionChance;
    public final float landmarkChance;
    public final float luminousChance;
    public final ResourceLocation environment;
    public final List<Stratum> strata;
    public final Palette<CaveType> caveTypes;
    public final Palette<CaveType> minorTypes;
    public final Palette<CaveLandmark> landmarks;
    public final Palette<BlockState> ores;
    /** Cavern templates large caverns under these biomes use, and the crystal colours of their crystal forests. */
    public final Palette<ResourceLocation> cavernTemplates;
    public final Palette<CavernTemplate.CrystalColor> crystalColors;

    private final SimpleWeightedRandomList<CaveType> caveTypeList;
    private final SimpleWeightedRandomList<CaveType> minorTypeList;
    private final SimpleWeightedRandomList<CaveLandmark> landmarkList;
    private final SimpleWeightedRandomList<BlockState> oreList;
    private final SimpleWeightedRandomList<ResourceLocation> templateList;
    private final SimpleWeightedRandomList<CavernTemplate.CrystalColor> crystalList;

    private CaveProfile(HolderSet<Biome> biomes, float systemChance, float minorCaveChance, float connectionChance,
                        SimpleWeightedRandomList<CaveType> caveTypes, SimpleWeightedRandomList<CaveType> minorTypes, float landmarkChance,
                        SimpleWeightedRandomList<CaveLandmark> landmarks, ResourceLocation environment, float luminousChance,
                        List<Stratum> strata, SimpleWeightedRandomList<BlockState> ores, SimpleWeightedRandomList<ResourceLocation> templates,
                        SimpleWeightedRandomList<CavernTemplate.CrystalColor> crystals)
    {
        this.biomes = biomes;
        this.systemChance = systemChance;
        this.minorCaveChance = minorCaveChance;
        this.connectionChance = connectionChance;
        this.caveTypeList = caveTypes;
        this.minorTypeList = minorTypes;
        this.landmarkChance = landmarkChance;
        this.landmarkList = landmarks;
        this.environment = environment;
        this.luminousChance = luminousChance;
        this.strata = strata.stream().sorted(Comparator.comparingInt(Stratum::depth)).toList();
        this.oreList = ores;
        this.caveTypes = Palette.of(caveTypes);
        this.minorTypes = Palette.of(minorTypes);
        this.landmarks = Palette.of(landmarks);
        this.ores = Palette.of(ores);
        this.templateList = templates;
        this.crystalList = crystals;
        this.cavernTemplates = Palette.of(templates);
        this.crystalColors = Palette.of(crystals);
    }

    public SimpleWeightedRandomList<CaveType> caveTypeList()
    {
        return caveTypeList;
    }

    /** Stratum rock at this depth below the seabed. */
    public BlockState stratum(int depth)
    {
        BlockState result = strata.isEmpty() ? null : strata.get(0).state();
        for (Stratum s : strata)
        {
            if (depth >= s.depth()) result = s.state();
            else break;
        }
        return result;
    }
}
