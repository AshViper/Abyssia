package com.abyssia.worldgen.structure;

import com.abyssia.worldgen.cave.CaveEnvironment;
import com.abyssia.worldgen.cave.Palette;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * One kind of natural seabed structure (a rock spire, a crystal cluster, a chimney group, a volcano...): which
 * {@link Formation} builds it and with what parameters, where it may stand, what grows around it and which fauna
 * favour it. A datapack registry ({@code data/<ns>/abyssia/seabed_structure/*.json}); which biomes get it and how
 * often is decided by {@link StructureProfile}s, so the same structure can appear in several biomes.
 * <p>
 * {@code footprint_radius} is a hard bound: nothing of the structure (dressing included) is painted farther than
 * that from its centre, which is what lets a chunk find every structure reaching it.
 */
public record SeabedStructure(Category category, Tier tier, Anchor anchor, int footprintRadius, int maxHeight, Formation formation,
                              Conditions conditions, Dressing dressing, List<ResourceLocation> preferredMobs)
{
    public static final Codec<SeabedStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            Category.CODEC.fieldOf("category").forGetter(SeabedStructure::category),
            Tier.CODEC.fieldOf("tier").forGetter(SeabedStructure::tier),
            Anchor.CODEC.optionalFieldOf("anchor", Anchor.SEABED).forGetter(SeabedStructure::anchor),
            Codec.intRange(4, 160).fieldOf("footprint_radius").forGetter(SeabedStructure::footprintRadius),
            Codec.intRange(1, 200).fieldOf("max_height").forGetter(SeabedStructure::maxHeight),
            Formation.CODEC.fieldOf("formation").forGetter(SeabedStructure::formation),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.DEFAULT).forGetter(SeabedStructure::conditions),
            Dressing.CODEC.optionalFieldOf("dressing", Dressing.NONE).forGetter(SeabedStructure::dressing),
            ResourceLocation.CODEC.listOf().optionalFieldOf("preferred_mobs", List.of()).forGetter(SeabedStructure::preferredMobs)
    ).apply(i, SeabedStructure::new));

    /** What the structure is, geologically; {@code LANDMARK} marks the rare, memorable ones (config multiplier). */
    public enum Category implements StringRepresentable
    {
        GEOLOGICAL, CRYSTAL, THERMAL, CAVE, VEGETATION, SEDIMENT, LANDMARK;

        public static final Codec<Category> CODEC = StringRepresentable.fromEnum(Category::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Size class: small (5-15 blocks), medium (15-40), large (40-100), colossal (100+). Larger tiers win overlaps and
     * are placed first, so a small outcrop never cuts into a landmark.
     */
    public enum Tier implements StringRepresentable
    {
        SMALL, MEDIUM, LARGE, COLOSSAL;

        public static final Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Built on the open seabed, or on the floor of a large cavern (between its floor and roof). */
    public enum Anchor implements StringRepresentable
    {
        SEABED, CAVERN;

        public static final Codec<Anchor> CODEC = StringRepresentable.fromEnum(Anchor::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Terrain requirements at the centre. {@code min_y}/{@code max_y} bound the seabed (or cavern floor) height, i.e.
     * the depth; slope is the steepest rise per block over the footprint (0 flat, 1 = 45 degrees); nothing is built
     * above {@code max_top_y} (keeps the water above Y 100 open); cavern structures need {@code min_clearance}
     * blocks between floor and roof. {@code avoid_caves} rejects spots over cave entrances and shafts;
     * {@code avoid_vent_fields} keeps medium and larger structures out of hydrothermal vent fields (whose chimneys
     * are painted later and would cut through them) - volcanoes and chimney groups may share ground with them.
     */
    public record Conditions(int minY, int maxY, float minSlope, float maxSlope, int maxTopY, int minClearance, boolean avoidCaves,
                             boolean avoidVentFields)
    {
        public static final Conditions DEFAULT = new Conditions(-128, 256, 0f, 10f, 110, 12, true, true);
        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("min_y", DEFAULT.minY).forGetter(Conditions::minY),
                Codec.INT.optionalFieldOf("max_y", DEFAULT.maxY).forGetter(Conditions::maxY),
                Codec.floatRange(0, 10).optionalFieldOf("min_slope", DEFAULT.minSlope).forGetter(Conditions::minSlope),
                Codec.floatRange(0, 10).optionalFieldOf("max_slope", DEFAULT.maxSlope).forGetter(Conditions::maxSlope),
                Codec.INT.optionalFieldOf("max_top_y", DEFAULT.maxTopY).forGetter(Conditions::maxTopY),
                Codec.intRange(4, 200).optionalFieldOf("min_clearance", DEFAULT.minClearance).forGetter(Conditions::minClearance),
                Codec.BOOL.optionalFieldOf("avoid_caves", DEFAULT.avoidCaves).forGetter(Conditions::avoidCaves),
                Codec.BOOL.optionalFieldOf("avoid_vent_fields", DEFAULT.avoidVentFields).forGetter(Conditions::avoidVentFields)
        ).apply(i, Conditions::new));
    }

    /**
     * The structure's surroundings, painted after all ore and vent generation: plants (and crystal clusters, pebbles,
     * rubble) on the floor and on the structure itself within {@code radius} of the centre, thinning toward the edge,
     * and mineral crusts replacing the block they grow from with {@code mineral_chance}.
     */
    public record Dressing(float radius, float density, SimpleWeightedRandomList<CaveEnvironment.PlantEntry> plants,
                           SimpleWeightedRandomList<BlockState> minerals, float mineralChance)
    {
        private static final Codec<SimpleWeightedRandomList<CaveEnvironment.PlantEntry>> PLANTS =
                SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CaveEnvironment.PlantEntry.CODEC);
        public static final Dressing NONE = new Dressing(0, 0, SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty(), 0);
        public static final Codec<Dressing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 160).fieldOf("radius").forGetter(Dressing::radius),
                Codec.floatRange(0, 1).fieldOf("density").forGetter(Dressing::density),
                PLANTS.optionalFieldOf("plants", SimpleWeightedRandomList.empty()).forGetter(Dressing::plants),
                SimpleWeightedRandomList.wrappedCodecAllowingEmpty(BlockState.CODEC).optionalFieldOf("minerals", SimpleWeightedRandomList.empty()).forGetter(Dressing::minerals),
                Codec.floatRange(0, 1).optionalFieldOf("mineral_chance", 0f).forGetter(Dressing::mineralChance)
        ).apply(i, Dressing::new));

        public Palette<CaveEnvironment.PlantEntry> plantPalette()
        {
            return Palette.of(plants);
        }

        public Palette<BlockState> mineralPalette()
        {
            return Palette.of(minerals);
        }
    }
}
