package com.abyssia.fauna.external;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Options for other mods' sea animals in the deep ocean: {@code config/abyssia-external-fauna.toml} (common). A file of
 * its own so abyssia-common.toml keeps its shape. Changes apply on the next spawn round (the profiles are rebuilt).
 * Per-entity and per-biome rules are datapack data: data/&lt;namespace&gt;/external_fauna/{entities,biomes}.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExternalFaunaConfig
{
    public static final String FILE_NAME = Abyssia.MODID + "-external-fauna.toml";
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("Other mods' sea animals, detected automatically from the entity registry and spawned in the deep ocean",
                "by biome and depth next to Abyssia's own fauna. Inspect with /abyssia fauna external").push("external_mobs");
    }

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Spawn detected sea animals of other mods (and vanilla's) in the deep ocean")
            .define("enabled", true);
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG = BUILDER
            .comment("Log every detected animal with its score, category, depth and biomes when the profiles are built")
            .define("debug_log", false);
    public static final ForgeConfigSpec.IntValue MIN_SCORE = BUILDER
            .comment("Score an entity needs to count as a sea animal (see OceanMobClassifier; cod scores 100, drowned -20)")
            .defineInRange("min_score", 50, -100, 300);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST = BUILDER
            .comment("Entities added even when not detected (\"mod:entity\", \"mod:*\" or \"#namespace:entity_tag\")")
            .defineListAllowEmpty("whitelist", List.of(), ExternalFaunaConfig::isString);
    public static final ForgeConfigSpec.BooleanValue WHITELIST_ONLY = BUILDER
            .comment("Spawn only whitelisted entities (detection still picks their category)")
            .define("whitelist_only", false);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST = BUILDER
            .comment("Entities never spawned, even when whitelisted. Same formats as the whitelist.",
                    "Defaults: the elder guardian (its mining fatigue would cover the whole dimension), the drowned (the deep ocean has none)")
            .defineListAllowEmpty("blacklist", List.of("minecraft:elder_guardian", "minecraft:drowned", "#forge:bosses"), ExternalFaunaConfig::isString);
    public static final ForgeConfigSpec.BooleanValue RESPECT_SPAWN_PREDICATES = BUILDER
            .comment("Also require each entity's own spawn predicate (SpawnPlacements). Most sea animals only accept the top",
                    "13 blocks of water there (vanilla fish do), so this keeps nearly all of them out of the deep ocean")
            .define("respect_spawn_predicates", false);
    public static final ForgeConfigSpec.BooleanValue ALLOW_PERSISTENT = BUILDER
            .comment("Also spawn detected animals that never despawn (they would pile up as players travel)")
            .define("allow_persistent_mobs", false);

    static {
        BUILDER.push("spawn");
    }

    public static final ForgeConfigSpec.DoubleValue WEIGHT_MULTIPLIER = BUILDER
            .comment("Multiplier on every external animal's spawn weight")
            .defineInRange("weight_multiplier", 1.0, 0.0, 10.0);
    public static final ForgeConfigSpec.DoubleValue MAX_SHARE = BUILDER
            .comment("At most this share of the spawn weight at any spot goes to external animals (the rest to Abyssia's fauna)")
            .defineInRange("max_share", 0.35, 0.0, 0.9);
    public static final ForgeConfigSpec.IntValue MAX_PER_BIOME = BUILDER
            .comment("At most this many external species per biome (the highest-scoring ones)")
            .defineInRange("max_external_mobs_per_biome", 20, 0, 500);
    public static final ForgeConfigSpec.IntValue MAX_PER_PLAYER = BUILDER
            .comment("At most this many external animals (spawned by this system) within 96 blocks of a player")
            .defineInRange("max_per_player", 16, 0, 500);
    public static final ForgeConfigSpec.IntValue LARGE_MIN_DISTANCE = BUILDER
            .comment("Large animals spawn at least this many blocks from every player")
            .defineInRange("large_min_player_distance", 40, 0, 128);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_MULTIPLIERS = BUILDER
            .comment("Per-biome weight multipliers, \"biome_id=multiplier\" (e.g. \"abyssia:hadal_zone=0.5\"; 0 = none there)")
            .defineListAllowEmpty("biome_multipliers", List.of(), ExternalFaunaConfig::isString);

    static {
        BUILDER.pop(2);
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ExternalFaunaConfig() {}

    private static boolean isString(Object o)
    {
        return o instanceof String;
    }

    /** "biome=multiplier" entries; malformed ones are ignored. */
    public static Map<ResourceLocation, Double> biomeMultipliers()
    {
        Map<ResourceLocation, Double> map = new HashMap<>();
        for (String entry : BIOME_MULTIPLIERS.get())
        {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq).trim());
            try
            {
                if (id != null) map.put(id, Math.max(0.0, Double.parseDouble(entry.substring(eq + 1).trim())));
            }
            catch (NumberFormatException ignored)
            {
                // skipped
            }
        }
        return map;
    }

    @SubscribeEvent
    public static void onConfig(ModConfigEvent event)
    {
        if (event.getConfig().getSpec() == SPEC) ExternalSpawnProvider.markDirty();
    }
}
