package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The look and life of one kind of cave space (abyssal, luminous, thermal, crystal, mineral, eroded, forest,
 * underground sea, trench): the rock its walls are coated in, what settles on its floors, what grows on its floors,
 * walls and ceilings, and which formations and crystals it favours.
 * <p>
 * A datapack registry ({@code data/<ns>/abyssia/cave_environment/*.json}); environments classify caves without new
 * biomes, so a cave keeps the biome it lies under. Tunable and extensible without code changes.
 */
public final class CaveEnvironment
{
    // Declared before CODEC: building CODEC initialises the nested records, whose codecs use these.
    private static final Codec<SimpleWeightedRandomList<BlockState>> STATES = SimpleWeightedRandomList.wrappedCodecAllowingEmpty(BlockState.CODEC);
    private static final Codec<SimpleWeightedRandomList<PlantEntry>> PLANTS = SimpleWeightedRandomList.wrappedCodecAllowingEmpty(PlantEntry.CODEC);

    public static final Codec<CaveEnvironment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Geology.CODEC.fieldOf("geology").forGetter(e -> e.geology),
            Flora.CODEC.fieldOf("flora").forGetter(e -> e.flora),
            Formations.CODEC.fieldOf("formations").forGetter(e -> e.formations)
    ).apply(i, CaveEnvironment::new));

    /**
     * Surface coating of the cave: {@code wall} for the first blocks into every wall, {@code floor} for the sediment
     * settled on floors, {@code ceiling} for roofs, {@code accents} for patches (mineral crust, crystal walls) that
     * break the rock up with probability {@code accent_chance}. Deeper rock follows the biome's strata.
     */
    public record Geology(SimpleWeightedRandomList<BlockState> wall, SimpleWeightedRandomList<BlockState> floor,
                          SimpleWeightedRandomList<BlockState> ceiling, SimpleWeightedRandomList<BlockState> accents, float accentChance)
    {
        public static final Codec<Geology> CODEC = RecordCodecBuilder.create(i -> i.group(
                STATES.fieldOf("wall").forGetter(Geology::wall),
                STATES.fieldOf("floor").forGetter(Geology::floor),
                STATES.fieldOf("ceiling").forGetter(Geology::ceiling),
                STATES.optionalFieldOf("accents", SimpleWeightedRandomList.empty()).forGetter(Geology::accents),
                Codec.floatRange(0, 1).optionalFieldOf("accent_chance", 0f).forGetter(Geology::accentChance)
        ).apply(i, Geology::new));
    }

    /**
     * Plants by where they root. {@code glow} and {@code bright} are the faintly and strongly luminous species; the
     * ratios give their share of all plants. {@code giant} grows in large caverns only, as a 3D forest from floor
     * to ceiling. {@code density} scales how much of the cave is overgrown (noise then splits it into sparse,
     * normal, dense and very dense zones); {@code moss} is the chance of moss films on any bare face.
     */
    public record Flora(SimpleWeightedRandomList<PlantEntry> floor, SimpleWeightedRandomList<PlantEntry> wall,
                        SimpleWeightedRandomList<PlantEntry> ceiling, SimpleWeightedRandomList<PlantEntry> glow,
                        SimpleWeightedRandomList<PlantEntry> bright, SimpleWeightedRandomList<PlantEntry> giant,
                        float density, float glowRatio, float brightRatio, float giantDensity, float moss)
    {
        public static final Codec<Flora> CODEC = RecordCodecBuilder.create(i -> i.group(
                PLANTS.optionalFieldOf("floor", SimpleWeightedRandomList.empty()).forGetter(Flora::floor),
                PLANTS.optionalFieldOf("wall", SimpleWeightedRandomList.empty()).forGetter(Flora::wall),
                PLANTS.optionalFieldOf("ceiling", SimpleWeightedRandomList.empty()).forGetter(Flora::ceiling),
                PLANTS.optionalFieldOf("glow", SimpleWeightedRandomList.empty()).forGetter(Flora::glow),
                PLANTS.optionalFieldOf("bright", SimpleWeightedRandomList.empty()).forGetter(Flora::bright),
                PLANTS.optionalFieldOf("giant", SimpleWeightedRandomList.empty()).forGetter(Flora::giant),
                Codec.floatRange(0, 4).optionalFieldOf("density", 0.3f).forGetter(Flora::density),
                Codec.floatRange(0, 1).optionalFieldOf("glow_ratio", 0.12f).forGetter(Flora::glowRatio),
                Codec.floatRange(0, 1).optionalFieldOf("bright_ratio", 0.02f).forGetter(Flora::brightRatio),
                Codec.floatRange(0, 1).optionalFieldOf("giant_density", 0f).forGetter(Flora::giantDensity),
                Codec.floatRange(0, 1).optionalFieldOf("moss", 0f).forGetter(Flora::moss)
        ).apply(i, Flora::new));
    }

    /**
     * Mineral growths: the speleothem block and how thickly stalactites/stalagmites cover ceilings and floors,
     * crystal clusters (on any face) and their density, and the loose debris strewn around collapses.
     */
    public record Formations(Optional<BlockState> speleothem, float speleothemDensity, SimpleWeightedRandomList<BlockState> crystals,
                             float crystalDensity, SimpleWeightedRandomList<BlockState> debris)
    {
        public static final Codec<Formations> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.optionalFieldOf("speleothem").forGetter(Formations::speleothem),
                Codec.floatRange(0, 1).optionalFieldOf("speleothem_density", 0.05f).forGetter(Formations::speleothemDensity),
                STATES.optionalFieldOf("crystals", SimpleWeightedRandomList.empty()).forGetter(Formations::crystals),
                Codec.floatRange(0, 1).optionalFieldOf("crystal_density", 0f).forGetter(Formations::crystalDensity),
                STATES.optionalFieldOf("debris", SimpleWeightedRandomList.empty()).forGetter(Formations::debris)
        ).apply(i, Formations::new));
    }

    /** A plant and, for column plants (stacking, hanging), the range of column heights. */
    public record PlantEntry(BlockState state, int minHeight, int maxHeight)
    {
        public static final Codec<PlantEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(PlantEntry::state),
                Codec.intRange(1, 200).optionalFieldOf("min_height", 1).forGetter(PlantEntry::minHeight),
                Codec.intRange(1, 200).optionalFieldOf("max_height", 1).forGetter(PlantEntry::maxHeight)
        ).apply(i, PlantEntry::new));
    }

    public final Geology geology;
    public final Flora flora;
    public final Formations formations;

    // Flattened for generation.
    public final Palette<BlockState> wall, floor, ceiling, accents, crystals, debris;
    public final Palette<PlantEntry> floorPlants, wallPlants, ceilingPlants, glowPlants, brightPlants, giantPlants;
    public final BlockState speleothem;

    public CaveEnvironment(Geology geology, Flora flora, Formations formations)
    {
        this.geology = geology;
        this.flora = flora;
        this.formations = formations;
        this.wall = Palette.of(geology.wall());
        this.floor = Palette.of(geology.floor());
        this.ceiling = Palette.of(geology.ceiling());
        this.accents = Palette.of(geology.accents());
        this.crystals = Palette.of(formations.crystals());
        this.debris = Palette.of(formations.debris());
        this.floorPlants = Palette.of(flora.floor());
        this.wallPlants = Palette.of(flora.wall());
        this.ceilingPlants = Palette.of(flora.ceiling());
        this.glowPlants = Palette.of(flora.glow());
        this.brightPlants = Palette.of(flora.bright());
        this.giantPlants = Palette.of(flora.giant());
        this.speleothem = formations.speleothem().orElse(null);
    }
}
