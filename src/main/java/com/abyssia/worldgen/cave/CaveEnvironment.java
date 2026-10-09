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
            Formations.CODEC.fieldOf("formations").forGetter(e -> e.formations),
            HallStyle.CODEC.optionalFieldOf("hall", HallStyle.DEFAULT).forGetter(e -> e.hall)
    ).apply(i, CaveEnvironment::new));

    /**
     * AB03: how a hall (the main chamber of a cavern of radius 16+, water-filled) of this environment looks. {@code look}: rock
     * of its floors, walls and roof (empty lists keep the environment's geology), the {@code basin} floor below the terraces
     * (magma fields, ice...), large organic {@code mosaic} patches covering {@code mosaic_coverage} of walls and roof (magma on
     * basalt, blue ice in packed ice...), embedded light blocks ({@code glow}, chance per surface block) and the bright blocks
     * lining the far-wall window. {@code life}: extra (luminous) plants on terrace ledges, walls and roof. {@code form}: terrace
     * steps, how much of the floor the basin covers, islands, plateaus, light columns ({@code beacon}: a luminous column plant,
     * {@code beacons} per hall size), extra vents, and the column clusters.
     */
    public record HallStyle(HallLook look, HallLife life, HallForm form)
    {
        public static final HallStyle DEFAULT = new HallStyle(HallLook.DEFAULT, HallLife.DEFAULT, HallForm.DEFAULT);
        public static final Codec<HallStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
                HallLook.CODEC.optionalFieldOf("look", HallLook.DEFAULT).forGetter(HallStyle::look),
                HallLife.CODEC.optionalFieldOf("life", HallLife.DEFAULT).forGetter(HallStyle::life),
                HallForm.CODEC.optionalFieldOf("form", HallForm.DEFAULT).forGetter(HallStyle::form)
        ).apply(i, HallStyle::new));
    }

    public record HallLook(SimpleWeightedRandomList<BlockState> wall, SimpleWeightedRandomList<BlockState> floor, SimpleWeightedRandomList<BlockState> ceiling,
                           SimpleWeightedRandomList<BlockState> mosaic, float mosaicCoverage, SimpleWeightedRandomList<BlockState> glow, float glowChance,
                           SimpleWeightedRandomList<BlockState> window, SimpleWeightedRandomList<BlockState> basin)
    {
        public static final HallLook DEFAULT = new HallLook(SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty(),
                SimpleWeightedRandomList.empty(), 0f, SimpleWeightedRandomList.empty(), 0f, SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty());
        public static final Codec<HallLook> CODEC = RecordCodecBuilder.create(i -> i.group(
                STATES.optionalFieldOf("wall", SimpleWeightedRandomList.empty()).forGetter(HallLook::wall),
                STATES.optionalFieldOf("floor", SimpleWeightedRandomList.empty()).forGetter(HallLook::floor),
                STATES.optionalFieldOf("ceiling", SimpleWeightedRandomList.empty()).forGetter(HallLook::ceiling),
                STATES.optionalFieldOf("mosaic", SimpleWeightedRandomList.empty()).forGetter(HallLook::mosaic),
                Codec.floatRange(0, 1).optionalFieldOf("mosaic_coverage", 0f).forGetter(HallLook::mosaicCoverage),
                STATES.optionalFieldOf("glow", SimpleWeightedRandomList.empty()).forGetter(HallLook::glow),
                Codec.floatRange(0, 1).optionalFieldOf("glow_chance", 0f).forGetter(HallLook::glowChance),
                STATES.optionalFieldOf("window", SimpleWeightedRandomList.empty()).forGetter(HallLook::window),
                STATES.optionalFieldOf("basin", SimpleWeightedRandomList.empty()).forGetter(HallLook::basin)
        ).apply(i, HallLook::new));
    }

    public record HallLife(SimpleWeightedRandomList<PlantEntry> floor, SimpleWeightedRandomList<PlantEntry> wall, SimpleWeightedRandomList<PlantEntry> ceiling,
                           float floorDensity, float wallDensity, float ceilingDensity)
    {
        public static final HallLife DEFAULT = new HallLife(SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty(), 0f, 0f, 0f);
        public static final Codec<HallLife> CODEC = RecordCodecBuilder.create(i -> i.group(
                PLANTS.optionalFieldOf("floor", SimpleWeightedRandomList.empty()).forGetter(HallLife::floor),
                PLANTS.optionalFieldOf("wall", SimpleWeightedRandomList.empty()).forGetter(HallLife::wall),
                PLANTS.optionalFieldOf("ceiling", SimpleWeightedRandomList.empty()).forGetter(HallLife::ceiling),
                Codec.floatRange(0, 1).optionalFieldOf("floor_density", 0f).forGetter(HallLife::floorDensity),
                Codec.floatRange(0, 1).optionalFieldOf("wall_density", 0f).forGetter(HallLife::wallDensity),
                Codec.floatRange(0, 1).optionalFieldOf("ceiling_density", 0f).forGetter(HallLife::ceilingDensity)
        ).apply(i, HallLife::new));
    }

    public record HallForm(int stepMin, int stepMax, float shoreMin, float shoreMax, float islands, float plateaus, Optional<PlantEntry> beacon, float beacons,
                           float vents, Optional<BlockState> columnBlock, float columnClusters, float trunks, float spires, float stalactites)
    {
        public static final HallForm DEFAULT = new HallForm(1, 2, 0.5f, 0.75f, 1f, 0f, Optional.empty(), 0f, 0f, Optional.empty(), 0f, 1f, 1f, 1f);
        public static final Codec<HallForm> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 4).optionalFieldOf("step_min", 1).forGetter(HallForm::stepMin),
                Codec.intRange(1, 4).optionalFieldOf("step_max", 2).forGetter(HallForm::stepMax),
                Codec.floatRange(0.2f, 0.95f).optionalFieldOf("shore_min", 0.5f).forGetter(HallForm::shoreMin),
                Codec.floatRange(0.2f, 0.95f).optionalFieldOf("shore_max", 0.75f).forGetter(HallForm::shoreMax),
                Codec.floatRange(0, 4).optionalFieldOf("islands", 1f).forGetter(HallForm::islands),
                Codec.floatRange(0, 1).optionalFieldOf("plateaus", 0f).forGetter(HallForm::plateaus),
                PlantEntry.CODEC.optionalFieldOf("beacon").forGetter(HallForm::beacon),
                Codec.floatRange(0, 8).optionalFieldOf("beacons", 0f).forGetter(HallForm::beacons),
                Codec.floatRange(0, 8).optionalFieldOf("vents", 0f).forGetter(HallForm::vents),
                BlockState.CODEC.optionalFieldOf("column_block").forGetter(HallForm::columnBlock),
                Codec.floatRange(0, 4).optionalFieldOf("column_clusters", 0f).forGetter(HallForm::columnClusters),
                Codec.floatRange(0, 4).optionalFieldOf("trunks", 1f).forGetter(HallForm::trunks),
                Codec.floatRange(0, 4).optionalFieldOf("spires", 1f).forGetter(HallForm::spires),
                Codec.floatRange(0, 4).optionalFieldOf("stalactites", 1f).forGetter(HallForm::stalactites)
        ).apply(i, HallForm::new));
    }

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

    /**
     * A plant and, for column plants (stacking, hanging), the range of column heights. {@code maxY}: placed only at
     * or below this world Y (RS01: resin roots in deep thermal caves only).
     */
    public record PlantEntry(BlockState state, int minHeight, int maxHeight, int maxY)
    {
        public PlantEntry(BlockState state, int minHeight, int maxHeight)
        {
            this(state, minHeight, maxHeight, Integer.MAX_VALUE);
        }


        public static final Codec<PlantEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(PlantEntry::state),
                Codec.intRange(1, 200).optionalFieldOf("min_height", 1).forGetter(PlantEntry::minHeight),
                Codec.intRange(1, 200).optionalFieldOf("max_height", 1).forGetter(PlantEntry::maxHeight),
                Codec.INT.optionalFieldOf("max_y", Integer.MAX_VALUE).forGetter(PlantEntry::maxY)
        ).apply(i, PlantEntry::new));
    }

    public final Geology geology;
    public final Flora flora;
    public final Formations formations;

    // Flattened for generation.
    public final Palette<BlockState> wall, floor, ceiling, accents, crystals, debris;
    public final Palette<PlantEntry> floorPlants, wallPlants, ceilingPlants, glowPlants, brightPlants, giantPlants;
    public final BlockState speleothem;
    public final HallStyle hall;
    /** Hall look and life, flattened; the hall rock falls back to the environment's own geology where empty. */
    public final Palette<BlockState> hallWall, hallFloor, hallCeiling, hallMosaic, hallGlow, hallWindow, hallBasin;
    public final Palette<PlantEntry> hallFloorPlants, hallWallPlants, hallCeilingPlants;

    public CaveEnvironment(Geology geology, Flora flora, Formations formations, HallStyle hall)
    {
        this.geology = geology;
        this.flora = flora;
        this.formations = formations;
        this.hall = hall;
        this.hallWall = hall.look().wall().unwrap().isEmpty() ? Palette.of(geology.wall()) : Palette.of(hall.look().wall());
        this.hallFloor = hall.look().floor().unwrap().isEmpty() ? Palette.of(geology.floor()) : Palette.of(hall.look().floor());
        this.hallCeiling = hall.look().ceiling().unwrap().isEmpty() ? Palette.of(geology.ceiling()) : Palette.of(hall.look().ceiling());
        this.hallMosaic = Palette.of(hall.look().mosaic());
        this.hallGlow = Palette.of(hall.look().glow());
        this.hallWindow = Palette.of(hall.look().window());
        this.hallBasin = hall.look().basin().unwrap().isEmpty() ? hallFloor : Palette.of(hall.look().basin());
        this.hallFloorPlants = Palette.of(hall.life().floor());
        this.hallWallPlants = Palette.of(hall.life().wall());
        this.hallCeilingPlants = Palette.of(hall.life().ceiling());
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
