package com.abyssia.fauna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where one species lives: a data file in data/&lt;namespace&gt;/fauna_spawns/ (written by tools/gen_fauna.py from
 * the research notes of each species). Depths are real metres ({@link DepthZone} maps them onto the world); the
 * weight falls off linearly from the core range to 10 % at the recorded extremes.
 *
 * <pre>
 * entity        the animal
 * role          passive | neutral | predator | environmental (informational)
 * weight        relative spawn weight where its conditions hold
 * group         {min, max} animals per spawn
 * depth_m       {min, core_min, core_max, max} in metres
 * placement     open_water (clear of the seabed), near_floor (a few blocks above it) or seabed (standing on it)
 * cave_factor   weight multiplier under rock (caves, overhangs); 0 = never there
 * open_factor   weight multiplier under open water; 0 = caves only
 * max_light     brightest light level it spawns in
 * biomes        optional biome ids / #tags; empty = any
 * habitat       [{blocks, radius, min, factor, required}]: weight x factor when at least min matching blocks lie
 *               around (sampled); a required habitat needs one matching block anywhere within radius, and its
 *               absence rules the spot out (vent animals away from vents)
 * substrate     [{blocks, factor}]: weight x factor when the seabed below is one of these
 * clearance     open water needed in all six directions (blocks), for large animals; 0 = none
 * cap           {count, radius}: no more of this animal spawns while count are within radius
 * </pre>
 */
public record FaunaSpawnRule(EntityType<?> entity, String role, double weight, Range group, Depth depth, Placement placement,
                             float caveFactor, float openFactor, int maxLight, List<String> biomes,
                             List<Habitat> habitat, List<Substrate> substrate, int clearance, Cap cap)
{
    private static final double EDGE_WEIGHT = 0.1;

    public enum Placement implements StringRepresentable
    {
        OPEN_WATER("open_water"), NEAR_FLOOR("near_floor"), SEABED("seabed");

        public static final Codec<Placement> CODEC = StringRepresentable.fromEnum(Placement::values);
        private final String name;

        Placement(String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return this.name;
        }
    }

    public record Range(int min, int max)
    {
        public static final Codec<Range> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).fieldOf("min").forGetter(Range::min),
                Codec.intRange(1, 64).fieldOf("max").forGetter(Range::max)
        ).apply(i, Range::new));
    }

    public record Depth(double min, double coreMin, double coreMax, double max)
    {
        public static final Codec<Depth> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("min").forGetter(Depth::min),
                Codec.DOUBLE.fieldOf("core_min").forGetter(Depth::coreMin),
                Codec.DOUBLE.fieldOf("core_max").forGetter(Depth::coreMax),
                Codec.DOUBLE.fieldOf("max").forGetter(Depth::max)
        ).apply(i, Depth::new));

        /** 1 inside the core range, falling linearly to 10 % at the extremes, 0 outside. */
        public double factor(double metres)
        {
            if (metres < this.min || metres > this.max) return 0.0;
            if (metres < this.coreMin) return EDGE_WEIGHT + (1 - EDGE_WEIGHT) * (metres - this.min) / Math.max(1e-6, this.coreMin - this.min);
            if (metres > this.coreMax) return EDGE_WEIGHT + (1 - EDGE_WEIGHT) * (this.max - metres) / Math.max(1e-6, this.max - this.coreMax);
            return 1.0;
        }
    }

    public record Habitat(String blocks, int radius, int min, float factor, boolean required)
    {
        static final Codec<Habitat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("blocks").forGetter(Habitat::blocks),
                Codec.intRange(1, 8).optionalFieldOf("radius", 2).forGetter(Habitat::radius),
                Codec.intRange(1, 27).optionalFieldOf("min", 1).forGetter(Habitat::min),
                Codec.FLOAT.fieldOf("factor").forGetter(Habitat::factor),
                Codec.BOOL.optionalFieldOf("required", false).forGetter(Habitat::required)
        ).apply(i, Habitat::new));
    }

    public record Substrate(String blocks, float factor)
    {
        static final Codec<Substrate> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("blocks").forGetter(Substrate::blocks),
                Codec.FLOAT.fieldOf("factor").forGetter(Substrate::factor)
        ).apply(i, Substrate::new));
    }

    public record Cap(int count, int radius)
    {
        public static final Codec<Cap> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 256).fieldOf("count").forGetter(Cap::count),
                Codec.intRange(8, 256).fieldOf("radius").forGetter(Cap::radius)
        ).apply(i, Cap::new));
    }

    public static final Codec<FaunaSpawnRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(FaunaSpawnRule::entity),
            Codec.STRING.optionalFieldOf("role", "passive").forGetter(FaunaSpawnRule::role),
            Codec.doubleRange(0, 10000).fieldOf("weight").forGetter(FaunaSpawnRule::weight),
            Range.CODEC.optionalFieldOf("group", new Range(1, 1)).forGetter(FaunaSpawnRule::group),
            Depth.CODEC.fieldOf("depth_m").forGetter(FaunaSpawnRule::depth),
            Placement.CODEC.fieldOf("placement").forGetter(FaunaSpawnRule::placement),
            Codec.FLOAT.optionalFieldOf("cave_factor", 1.0F).forGetter(FaunaSpawnRule::caveFactor),
            Codec.FLOAT.optionalFieldOf("open_factor", 1.0F).forGetter(FaunaSpawnRule::openFactor),
            Codec.intRange(0, 15).optionalFieldOf("max_light", 15).forGetter(FaunaSpawnRule::maxLight),
            Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(FaunaSpawnRule::biomes),
            Habitat.CODEC.listOf().optionalFieldOf("habitat", List.of()).forGetter(FaunaSpawnRule::habitat),
            Substrate.CODEC.listOf().optionalFieldOf("substrate", List.of()).forGetter(FaunaSpawnRule::substrate),
            Codec.intRange(0, 16).optionalFieldOf("clearance", 0).forGetter(FaunaSpawnRule::clearance),
            Cap.CODEC.fieldOf("cap").forGetter(FaunaSpawnRule::cap)
    ).apply(i, FaunaSpawnRule::new));

    /** Vent animals: spawned around vent cores ({@link FaunaSpawner#ventAttempt}), never away from them. */
    public boolean anchoredToVents()
    {
        for (Habitat h : this.habitat)
        {
            if (h.required() && h.blocks().equals("#abyssia:fauna/vent")) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- evaluation

    /**
     * Where this animal would spawn around a sampled water position ({@code null}: its placement does not fit here).
     * Seabed animals go on the floor below, near-floor animals a little above it, open-water animals stay put.
     */
    @Nullable
    public BlockPos place(SpawnSite site, RandomSource random)
    {
        return switch (this.placement)
        {
            case OPEN_WATER -> site.floorDistance() >= 3 && site.ceilingDistance() >= 2 ? site.pos() : null;
            case NEAR_FLOOR -> site.hasFloor() && site.floorDistance() <= 12 ? site.waterAt(site.floorPos().above(1 + random.nextInt(3))) : null;
            case SEABED -> site.hasFloor() && site.floorDistance() <= 24 && site.solidFloor() ? site.floorPos() : null;
        };
    }

    /** Spawn weight at {@code pos} (from {@link #place}); 0 when it cannot live there. */
    public double weight(SpawnSite site, BlockPos pos)
    {
        if (this.weight <= 0) return 0.0;
        double w = this.weight * this.depth.factor(DepthZone.metres(site.level(), pos.getY()));
        if (w <= 0 || site.level().getMaxLocalRawBrightness(pos) > this.maxLight) return 0.0;
        w *= site.covered(pos) ? this.caveFactor : this.openFactor;
        if (w <= 0 || !this.biomeMatches(site.level().getBiome(pos))) return 0.0;
        if (this.clearance > 0 && !site.clear(pos, this.clearance)) return 0.0;
        for (Habitat h : this.habitat)
        {
            if (h.required())
            {
                // must be there: one matching block anywhere within the radius (a dense scan, only for vent animals)
                if (!site.any(pos, h.radius(), blockTest(h.blocks()))) return 0.0;
                w *= h.factor();
            }
            else if (site.count(pos, h.radius(), blockTest(h.blocks())) >= h.min())
            {
                w *= h.factor();
            }
        }
        for (Substrate s : this.substrate)
        {
            if (blockTest(s.blocks()).test(site.level().getBlockState(pos.below()))) w *= s.factor();
        }
        return w;
    }

    private boolean biomeMatches(Holder<Biome> biome)
    {
        if (this.biomes.isEmpty()) return true;
        for (String b : this.biomes)
        {
            if (b.startsWith("#") ? biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(b.substring(1)))) : biome.is(ResourceLocation.parse(b))) return true;
        }
        return false;
    }

    /** "#namespace:tag" or a block id. */
    public static Predicate<BlockState> blockTest(String spec)
    {
        if (spec.startsWith("#"))
        {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(spec.substring(1)));
            return s -> s.is(tag);
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(spec));
        return s -> s.is(block);
    }
}
