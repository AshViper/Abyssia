package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * A cavern template (forest, crystal, mineral, thermal, lake, ruins-like geology, mixed): which ecology patches a
 * large cavern is divided into, how many of each structure it gets relative to its size, how its giant kelp forest
 * and hanging root curtains grow, how much its roof glitters, and which landmark may stand at its core. Everything is
 * natural geology and life; no built structures.
 * <p>
 * A datapack registry ({@code data/<ns>/abyssia/cavern_template/*.json}); cave profiles weight which templates their
 * biomes use, and landmarks force a matching one.
 */
public final class CavernTemplate
{
    private static final Codec<SimpleWeightedRandomList<CavernPatch>> PATCHES = SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CavernPatch.CODEC);
    private static final Codec<SimpleWeightedRandomList<CaveEnvironment.PlantEntry>> PLANTS =
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CaveEnvironment.PlantEntry.CODEC);

    /**
     * Giant kelp forest on the floor: {@code density} of the forest, {@code clearings} how often open glades break it,
     * {@code lean} the share of clusters whose stalks lean, heights (grown by up to 60% in very tall caverns).
     */
    public record Forest(float density, float clearings, float lean, int minHeight, int maxHeight, SimpleWeightedRandomList<CaveEnvironment.PlantEntry> plants)
    {
        public static final Codec<Forest> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 4).optionalFieldOf("density", 0.5f).forGetter(Forest::density),
                Codec.floatRange(0, 1).optionalFieldOf("clearings", 0.3f).forGetter(Forest::clearings),
                Codec.floatRange(0, 1).optionalFieldOf("lean", 0.3f).forGetter(Forest::lean),
                Codec.intRange(2, 120).optionalFieldOf("min_height", 10).forGetter(Forest::minHeight),
                Codec.intRange(2, 120).optionalFieldOf("max_height", 50).forGetter(Forest::maxHeight),
                PLANTS.optionalFieldOf("plants", SimpleWeightedRandomList.empty()).forGetter(Forest::plants)
        ).apply(i, Forest::new));
    }

    /** Roots, vines and kelp hanging from the roof; {@code reach_floor} is the share that hang all the way down. */
    public record Hanging(float density, float reachFloor, SimpleWeightedRandomList<CaveEnvironment.PlantEntry> plants)
    {
        public static final Codec<Hanging> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 4).optionalFieldOf("density", 0.4f).forGetter(Hanging::density),
                Codec.floatRange(0, 1).optionalFieldOf("reach_floor", 0.1f).forGetter(Hanging::reachFloor),
                PLANTS.optionalFieldOf("plants", SimpleWeightedRandomList.empty()).forGetter(Hanging::plants)
        ).apply(i, Hanging::new));
    }

    /** A crystal colour: the solid crystal forests are built from and the cluster growing around them. */
    public record CrystalColor(BlockState crystal, BlockState cluster)
    {
        public static final Codec<CrystalColor> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("crystal").forGetter(CrystalColor::crystal),
                BlockState.CODEC.fieldOf("cluster").forGetter(CrystalColor::cluster)
        ).apply(i, CrystalColor::new));
    }

    public static final Codec<CavernTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            PATCHES.fieldOf("patches").forGetter(t -> t.patchList),
            PATCHES.optionalFieldOf("deep_patches", SimpleWeightedRandomList.empty()).forGetter(t -> t.deepPatchList),
            Codec.unboundedMap(CavernStructure.CODEC, Codec.floatRange(0, 20)).optionalFieldOf("structures", Map.of()).forGetter(t -> t.structures),
            Forest.CODEC.optionalFieldOf("kelp_forest", new Forest(0, 0, 0, 10, 50, SimpleWeightedRandomList.empty())).forGetter(t -> t.forest),
            Hanging.CODEC.optionalFieldOf("hanging", new Hanging(0, 0, SimpleWeightedRandomList.empty())).forGetter(t -> t.hanging),
            Codec.floatRange(0, 1).optionalFieldOf("ceiling_glow", 0.02f).forGetter(t -> t.ceilingGlow),
            Codec.floatRange(0, 8).optionalFieldOf("wall_relief", 1.5f).forGetter(t -> t.wallRelief),
            Codec.floatRange(0, 1).optionalFieldOf("center_chance", 0.4f).forGetter(t -> t.centerChance),
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CavernCenter.CODEC).optionalFieldOf("centers", SimpleWeightedRandomList.empty()).forGetter(t -> t.centerList)
    ).apply(i, CavernTemplate::new));

    private final SimpleWeightedRandomList<CavernPatch> patchList, deepPatchList;
    private final SimpleWeightedRandomList<CavernCenter> centerList;
    public final Map<CavernStructure, Float> structures;
    public final Forest forest;
    public final Hanging hanging;
    public final float ceilingGlow, wallRelief, centerChance;
    public final Palette<CavernPatch> patches, deepPatches;
    public final Palette<CavernCenter> centers;
    public final Palette<CaveEnvironment.PlantEntry> forestPlants, hangingPlants;

    private CavernTemplate(SimpleWeightedRandomList<CavernPatch> patches, SimpleWeightedRandomList<CavernPatch> deepPatches,
                           Map<CavernStructure, Float> structures, Forest forest, Hanging hanging, float ceilingGlow, float wallRelief,
                           float centerChance, SimpleWeightedRandomList<CavernCenter> centers)
    {
        this.patchList = patches;
        this.deepPatchList = deepPatches;
        this.structures = structures;
        this.forest = forest;
        this.hanging = hanging;
        this.ceilingGlow = ceilingGlow;
        this.wallRelief = wallRelief;
        this.centerChance = centerChance;
        this.centerList = centers;
        this.patches = Palette.of(patches);
        this.deepPatches = deepPatches.isEmpty() ? this.patches : Palette.of(deepPatches);
        this.centers = Palette.of(centers);
        this.forestPlants = Palette.of(forest.plants());
        this.hangingPlants = Palette.of(hanging.plants());
    }

    /** How strongly this template calls for a structure (0 when it does not). */
    public double rate(CavernStructure structure)
    {
        return structures.getOrDefault(structure, 0f);
    }
}
