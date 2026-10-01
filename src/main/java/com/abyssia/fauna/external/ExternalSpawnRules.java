package com.abyssia.fauna.external;

import com.abyssia.Abyssia;
import com.abyssia.fauna.FaunaSpawnRule;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import net.neoforged.fml.common.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Datapack tuning for other mods' animals, reloaded with /reload:
 * <pre>
 * data/&lt;ns&gt;/external_fauna/entities/*.json   per-entity overrides (applied in file id order, later files win)
 *   entities            ["mod:entity" | "mod:*" | "#ns:entity_tag", ...]
 *   enabled             true: spawn even if not detected; false: never spawn (optional)
 *   category            ambient | small_creature | predator | large_creature | deep_sea | unknown
 *   depth               surface | upper | middle | deep | hadal (a {@link DepthAffinity})
 *   depth_m             {min, core_min, core_max, max} in metres (wins over depth)
 *   placement           open_water | near_floor | seabed
 *   weight_multiplier   on the category's weight (default 1)
 *   group, cap          {min, max} / {count, radius} as in fauna_spawns
 *   biomes              only these biome ids / #tags (default: wherever biome rules allow the category)
 *   min_player_distance blocks
 *
 * data/&lt;ns&gt;/external_fauna/biomes/*.json     which categories live in which deep-ocean biomes
 *   biomes              biome ids / #tags this rule covers; "fallback": true instead covers every biome no rule lists
 *   weight_multiplier   for the whole biome (default 1)
 *   categories          {category: multiplier}; a category not listed does not spawn there
 * </pre>
 * Entity ids of mods that are not installed are ignored, so one datapack can cover many optional mods.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class ExternalSpawnRules extends SimpleJsonResourceReloadListener
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<EntityRule> entityRules = List.of();
    private static volatile List<BiomeRule> biomeRules = List.of();

    public record EntityRule(ResourceLocation file, EntityPatterns matcher, Optional<Boolean> enabled,
                             Optional<DeepSeaSpawnCategory> category, Optional<DepthAffinity> depth,
                             Optional<FaunaSpawnRule.Depth> depthMetres, Optional<FaunaSpawnRule.Placement> placement,
                             float weightMultiplier, Optional<FaunaSpawnRule.Range> group, Optional<FaunaSpawnRule.Cap> cap,
                             List<String> biomes, Optional<Integer> minPlayerDistance)
    {
        private record Data(List<String> entities, Optional<Boolean> enabled, Optional<DeepSeaSpawnCategory> category,
                            Optional<DepthAffinity> depth, Optional<FaunaSpawnRule.Depth> depthMetres,
                            Optional<FaunaSpawnRule.Placement> placement, float weightMultiplier,
                            Optional<FaunaSpawnRule.Range> group, Optional<FaunaSpawnRule.Cap> cap, List<String> biomes,
                            Optional<Integer> minPlayerDistance)
        {
            static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.listOf().fieldOf("entities").forGetter(Data::entities),
                    Codec.BOOL.optionalFieldOf("enabled").forGetter(Data::enabled),
                    DeepSeaSpawnCategory.CODEC.optionalFieldOf("category").forGetter(Data::category),
                    DepthAffinity.CODEC.optionalFieldOf("depth").forGetter(Data::depth),
                    FaunaSpawnRule.Depth.CODEC.optionalFieldOf("depth_m").forGetter(Data::depthMetres),
                    FaunaSpawnRule.Placement.CODEC.optionalFieldOf("placement").forGetter(Data::placement),
                    Codec.floatRange(0.0F, 100.0F).optionalFieldOf("weight_multiplier", 1.0F).forGetter(Data::weightMultiplier),
                    FaunaSpawnRule.Range.CODEC.optionalFieldOf("group").forGetter(Data::group),
                    FaunaSpawnRule.Cap.CODEC.optionalFieldOf("cap").forGetter(Data::cap),
                    Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(Data::biomes),
                    Codec.intRange(0, 128).optionalFieldOf("min_player_distance").forGetter(Data::minPlayerDistance)
            ).apply(i, Data::new));

            EntityRule toRule(ResourceLocation file)
            {
                return new EntityRule(file, new EntityPatterns(this.entities), this.enabled, this.category, this.depth, this.depthMetres,
                        this.placement, this.weightMultiplier, this.group, this.cap, this.biomes, this.minPlayerDistance);
            }
        }

        /** The depth band this rule sets, if any. */
        public Optional<FaunaSpawnRule.Depth> depthBand()
        {
            return this.depthMetres.or(() -> this.depth.map(DepthAffinity::depth));
        }
    }

    public record BiomeRule(ResourceLocation file, List<String> biomes, boolean fallback, float weightMultiplier,
                            Map<DeepSeaSpawnCategory, Float> categories)
    {
        static final Codec<BiomeRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(BiomeRule::biomes),
                Codec.BOOL.optionalFieldOf("fallback", false).forGetter(BiomeRule::fallback),
                Codec.floatRange(0.0F, 100.0F).optionalFieldOf("weight_multiplier", 1.0F).forGetter(BiomeRule::weightMultiplier),
                Codec.unboundedMap(DeepSeaSpawnCategory.CODEC, Codec.floatRange(0.0F, 100.0F)).fieldOf("categories").forGetter(BiomeRule::categories)
        ).apply(i, (biomes, fallback, weight, categories) -> new BiomeRule(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "unnamed"), biomes, fallback, weight, categories)));

        BiomeRule withFile(ResourceLocation file)
        {
            return new BiomeRule(file, this.biomes, this.fallback, this.weightMultiplier, this.categories);
        }

        public float multiplier(DeepSeaSpawnCategory category)
        {
            return this.categories.getOrDefault(category, 0.0F) * this.weightMultiplier;
        }

        boolean lists(Holder<Biome> biome)
        {
            return matches(biome, this.biomes);
        }
    }

    private ExternalSpawnRules()
    {
        super(new Gson(), "external_fauna");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new ExternalSpawnRules());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        List<EntityRule> entities = new ArrayList<>();
        List<BiomeRule> biomes = new ArrayList<>();
        // sorted: entity rules apply in file id order
        new TreeMap<>(files).forEach((id, json) -> {
            if (id.getPath().startsWith("entities/"))
            {
                EntityRule.Data.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> LOGGER.error("External fauna entity rule {}: {}", id, error))
                        .ifPresent(d -> entities.add(d.toRule(id)));
            }
            else if (id.getPath().startsWith("biomes/"))
            {
                BiomeRule.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> LOGGER.error("External fauna biome rule {}: {}", id, error))
                        .ifPresent(r -> biomes.add(r.withFile(id)));
            }
            else
            {
                LOGGER.warn("External fauna rule {} is neither under entities/ nor biomes/; ignored", id);
            }
        });
        entityRules = List.copyOf(entities);
        biomeRules = List.copyOf(biomes);
        LOGGER.info("Loaded {} external fauna entity rules and {} biome rules", entities.size(), biomes.size());
        ExternalSpawnProvider.markDirty();
    }

    /** Entity rules that match, in application order. */
    public static List<EntityRule> entityRules(EntityType<?> type, ResourceLocation id)
    {
        List<EntityRule> matching = new ArrayList<>();
        for (EntityRule rule : entityRules)
        {
            if (rule.matcher().matches(type, id)) matching.add(rule);
        }
        return matching;
    }

    /** The rule listing this biome, else the fallback rule; null when neither exists (no external animals there). */
    @Nullable
    public static BiomeRule biomeRule(Holder<Biome> biome)
    {
        BiomeRule fallback = null;
        for (BiomeRule rule : biomeRules)
        {
            if (rule.lists(biome)) return rule;
            if (rule.fallback() && fallback == null) fallback = rule;
        }
        return fallback;
    }

    /** Biome ids or #tags. */
    public static boolean matches(Holder<Biome> biome, List<String> specs)
    {
        for (String b : specs)
        {
            if (b.startsWith("#"))
            {
                ResourceLocation tag = ResourceLocation.tryParse(b.substring(1));
                if (tag != null && biome.is(TagKey.create(Registries.BIOME, tag))) return true;
            }
            else
            {
                ResourceLocation id = ResourceLocation.tryParse(b);
                if (id != null && biome.is(id)) return true;
            }
        }
        return false;
    }
}
