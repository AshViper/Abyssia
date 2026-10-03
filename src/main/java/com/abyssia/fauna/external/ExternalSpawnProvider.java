package com.abyssia.fauna.external;

import com.abyssia.Abyssia;
import com.abyssia.fauna.FaunaSpawnProvider;
import com.abyssia.fauna.FaunaSpawnRule;
import com.abyssia.fauna.FaunaSpawnRules;
import com.abyssia.worldgen.DeepLayer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Other mods' sea animals in the deep ocean: the external counterpart of the native fauna rules, feeding the same
 * {@link com.abyssia.fauna.FaunaSpawner}. Nothing here names another mod; everything comes from the registries.
 * <pre>
 * server start / datapack reload / config change
 *   -> every EntityType in BuiltInRegistries.ENTITY_TYPE (minus Abyssia's own)
 *   -> probe instance + registry data + natural spawn evidence -> {@link OceanMobClassifier} (score, category)
 *   -> whitelist / blacklist / data rules -> {@link DeepSeaSpawnProfile}
 *   -> per deep-ocean biome: biome rule x category -> FaunaSpawnRule list          (one immutable snapshot)
 * spawn attempt in the deep ocean
 *   -> the rules of the biome there, evaluated with the native ones (depth, placement, caps), bounded share
 *   -> extra checks: peaceful, distance to players, water placement, optional spawn predicate
 * </pre>
 * The snapshot is rebuilt as a whole and swapped in, never appended to, so repeated or overlapping triggers cannot
 * register an animal twice; the expensive part (probe instances) is cached for the game session.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class ExternalSpawnProvider implements FaunaSpawnProvider
{
    public static final ExternalSpawnProvider INSTANCE = new ExternalSpawnProvider();
    /** Persistent-data flag on every animal this system spawned (its per-player limit counts only these). */
    public static final String SPAWNED_TAG = Abyssia.MODID + ":external_spawn";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Same as the native fauna limit: vanilla's instant despawn distance, so nothing counted can hide just outside it. */
    private static final int LIMIT_RADIUS = com.abyssia.fauna.FaunaSpawner.LIMIT_RADIUS;
    /** A type whose spawn checks keep failing in the deep ocean is dropped for the session after this many tries... */
    private static final int DEMOTE_AFTER = 40;
    /** ...when fewer than this share passed. */
    private static final double DEMOTE_BELOW = 0.05;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static volatile boolean dirty = true;
    private static final Map<EntityType<?>, int[]> CHECKS = new ConcurrentHashMap<>();
    private static final Set<EntityType<?>> DEMOTED = ConcurrentHashMap.newKeySet();

    /**
     * @param classifications every scanned type (for the debug command)
     * @param profiles        the external animals that may spawn
     * @param dropped         sea animals kept out, with the reason
     * @param byBiome         spawn rules per deep-ocean biome
     */
    public record Snapshot(Map<EntityType<?>, OceanMobClassification> classifications, Map<EntityType<?>, DeepSeaSpawnProfile> profiles,
                           Map<EntityType<?>, String> dropped, Map<ResourceKey<Biome>, List<FaunaSpawnRule>> byBiome, int detected)
    {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), 0);
    }

    private ExternalSpawnProvider() {}

    // ---------------------------------------------------------------- lifecycle

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        rebuild(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        snapshot = Snapshot.EMPTY;
        dirty = true;
        CHECKS.clear();
        DEMOTED.clear();
    }

    /** Tags (#forge:bosses, biome tags, whitelist tags) change with datapacks: rebuild once they are bound. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event)
    {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) markDirty();
    }

    /** Rebuild on the next use (from any thread: config and reload events). */
    public static void markDirty()
    {
        dirty = true;
    }

    /** The current snapshot, rebuilt first when something changed. Server thread only. */
    public static Snapshot snapshot(MinecraftServer server)
    {
        if (dirty) rebuild(server);
        return snapshot;
    }

    /** Rebuilds now and forgets which types were dropped for failing their spawn checks (debug command). */
    public static Snapshot rescan(MinecraftServer server)
    {
        CHECKS.clear();
        DEMOTED.clear();
        rebuild(server);
        return snapshot;
    }

    public static Set<EntityType<?>> demoted()
    {
        return Set.copyOf(DEMOTED);
    }

    private static void rebuild(MinecraftServer server)
    {
        dirty = false;
        long start = System.nanoTime();
        try
        {
            snapshot = build(server);
        }
        catch (RuntimeException e)
        {
            // a broken mod must not take the native fauna down with it
            LOGGER.error("External fauna: building the spawn profiles failed; no external animals this session", e);
            snapshot = Snapshot.EMPTY;
            return;
        }
        Snapshot s = snapshot;
        long foreign = s.profiles().keySet().stream().filter(t -> !EntityType.getKey(t).getNamespace().equals("minecraft")).count();
        long biomes = s.byBiome().values().stream().filter(l -> !l.isEmpty()).count();
        LOGGER.info("External fauna: {} of {} entity types are sea animals; {} may spawn in the deep ocean ({} from other mods) in {} biomes ({} ms)",
                s.detected(), s.classifications().size(), s.profiles().size(), foreign, biomes, (System.nanoTime() - start) / 1_000_000);
        if (ExternalFaunaConfig.DEBUG_LOG.get()) logDetails(s);
    }

    // ---------------------------------------------------------------- building

    private static Snapshot build(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        FeatureFlagSet features = overworld.enabledFeatures();
        Map<EntityType<?>, SpawnEvidence> evidence = SpawnEvidence.scan(server.registryAccess());
        EntityPatterns whitelist = new EntityPatterns(ExternalFaunaConfig.WHITELIST.get());
        EntityPatterns blacklist = new EntityPatterns(ExternalFaunaConfig.BLACKLIST.get());
        boolean whitelistOnly = ExternalFaunaConfig.WHITELIST_ONLY.get();
        boolean allowPersistent = ExternalFaunaConfig.ALLOW_PERSISTENT.get();
        int minScore = ExternalFaunaConfig.MIN_SCORE.get();
        double weightMultiplier = ExternalFaunaConfig.WEIGHT_MULTIPLIER.get();
        int largeDistance = ExternalFaunaConfig.LARGE_MIN_DISTANCE.get();
        if (ExternalFaunaConfig.DEBUG_LOG.get()) LOGGER.info("External fauna: scanning {} entity types", BuiltInRegistries.ENTITY_TYPE.keySet().size());

        Map<EntityType<?>, OceanMobClassification> classifications = new LinkedHashMap<>();
        Map<EntityType<?>, DeepSeaSpawnProfile> profiles = new LinkedHashMap<>();
        Map<EntityType<?>, String> dropped = new LinkedHashMap<>();
        int detected = 0;
        List<Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>>> entries = new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().location()));
        for (Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> entry : entries)
        {
            ResourceLocation id = entry.getKey().location();
            EntityType<?> type = entry.getValue();
            // Abyssia's own animals keep their native rules
            if (id.getNamespace().equals(Abyssia.MODID) || FaunaSpawnRules.isFauna(type)) continue;
            if (!type.isEnabled(features)) continue;

            List<ExternalSpawnRules.EntityRule> rules = ExternalSpawnRules.entityRules(type, id);
            Optional<Boolean> dataEnabled = Optional.empty();
            for (ExternalSpawnRules.EntityRule r : rules) if (r.enabled().isPresent()) dataEnabled = r.enabled();
            boolean whitelisted = whitelist.matches(type, id) || dataEnabled.orElse(false);
            boolean blacklisted = blacklist.matches(type, id) || !dataEnabled.orElse(true);

            String pre = preExclusion(type);
            MobProbe probe = pre == null || whitelisted ? MobProbe.of(type, overworld) : null;
            OceanMobClassification c = OceanMobClassifier.classify(type, id, probe, whitelisted ? null : pre, evidence.getOrDefault(type, SpawnEvidence.NONE));
            classifications.put(type, c);
            boolean isSeaAnimal = c.detected(minScore);
            if (isSeaAnimal) detected++;
            if (!isSeaAnimal && !whitelisted) continue;

            String drop = null;
            if (blacklisted) drop = "blacklisted";
            else if (whitelistOnly && !whitelisted) drop = "not whitelisted (whitelist_only)";
            else if (probe != null && !probe.failed() && !probe.mob()) drop = "not a mob";
            else if (c.airBreather() && rules.stream().noneMatch(r -> r.depthBand().isPresent())) drop = "air breather (would drown)";
            else if (!c.despawns() && !allowPersistent && !whitelisted) drop = "never despawns (allow_persistent_mobs)";
            if (drop != null)
            {
                dropped.put(type, drop);
                continue;
            }
            String source = isSeaAnimal ? "detected" : dataEnabled.orElse(false) ? "data" : "whitelist";
            profiles.put(type, DeepSeaSpawnProfile.of(c, rules, source, weightMultiplier, largeDistance));
        }
        return new Snapshot(Collections.unmodifiableMap(classifications), Collections.unmodifiableMap(profiles), Collections.unmodifiableMap(dropped),
                indexBiomes(server, profiles), detected);
    }

    /** Types never instantiated for a probe unless whitelisted: vehicles, projectiles, items and the like. */
    @Nullable
    private static String preExclusion(EntityType<?> type)
    {
        if (type.getCategory() == MobCategory.MISC) return "misc category (vehicles, projectiles, items...)";
        if (!type.canSummon()) return "not summonable";
        if (!type.canSerialize()) return "not saved with the world";
        return null;
    }

    /** Per deep-layer biome: the profiles its biome rule allows, best-scoring first, as spawn rules. */
    private static Map<ResourceKey<Biome>, List<FaunaSpawnRule>> indexBiomes(MinecraftServer server, Map<EntityType<?>, DeepSeaSpawnProfile> profiles)
    {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || profiles.isEmpty()) return Map.of();
        Map<ResourceLocation, Double> biomeMultipliers = ExternalFaunaConfig.biomeMultipliers();
        int maxSpecies = ExternalFaunaConfig.MAX_PER_BIOME.get();
        Map<ResourceKey<Biome>, List<FaunaSpawnRule>> index = new HashMap<>();
        for (Holder<Biome> biome : deepLayerBiomes(server, overworld.getChunkSource().getGenerator().getBiomeSource()))
        {
            Optional<ResourceKey<Biome>> key = biome.unwrapKey();
            if (key.isEmpty()) continue;
            ExternalSpawnRules.BiomeRule rule = ExternalSpawnRules.biomeRule(biome);
            double biomeMultiplier = biomeMultipliers.getOrDefault(key.get().location(), 1.0);
            record Fit(DeepSeaSpawnProfile profile, double multiplier) {}
            List<Fit> fits = new ArrayList<>();
            for (DeepSeaSpawnProfile p : profiles.values())
            {
                double m = rule != null ? rule.multiplier(p.category()) : 0.0;
                // a data rule naming this biome for the animal outranks the biome's category list
                if (!p.biomes().isEmpty())
                {
                    if (!p.allows(biome)) continue;
                    if (m <= 0) m = 1.0;
                }
                m *= biomeMultiplier;
                if (m > 0 && p.weight() > 0) fits.add(new Fit(p, m));
            }
            fits.sort(Comparator.comparingInt((Fit f) -> -f.profile().score()).thenComparing(f -> f.profile().id()));
            index.put(key.get(), fits.stream().limit(maxSpecies).map(f -> f.profile().toRule(f.multiplier())).toList());
        }
        return Map.copyOf(index);
    }

    /** The deep layer's biomes, listed in data/abyssia/tags/worldgen/biome/deep_layer.json. */
    private static final TagKey<Biome> DEEP_LAYER_BIOMES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "deep_layer"));

    /** The biomes of the deep layer below the bedrock band: the {@code #abyssia:deep_layer} tag; every biome of the source if it is missing. */
    private static Iterable<Holder<Biome>> deepLayerBiomes(MinecraftServer server, BiomeSource source)
    {
        Optional<HolderSet.Named<Biome>> tag = server.registryAccess().registryOrThrow(Registries.BIOME).getTag(DEEP_LAYER_BIOMES);
        if (tag.isPresent() && tag.get().size() > 0) return tag.get();
        LOGGER.warn("External fauna: biome tag #{} is missing or empty, using every biome of the overworld source", DEEP_LAYER_BIOMES.location());
        return source.possibleBiomes();
    }

    private static void logDetails(Snapshot s)
    {
        int minScore = ExternalFaunaConfig.MIN_SCORE.get();
        for (DeepSeaSpawnProfile p : s.profiles().values())
        {
            LOGGER.info("External fauna: detected ocean entity {}", s.classifications().get(p.entityType()).describe());
            LOGGER.info("External fauna:   {}", p.describe());
            LOGGER.info("External fauna:   biomes: {}", String.join(", ", biomesOf(s, p.entityType())));
        }
        s.dropped().forEach((type, why) -> LOGGER.info("External fauna: not spawned {} ({})", EntityType.getKey(type), why));
        // near misses, to tune min_score or the lists
        s.classifications().values().stream()
                .filter(c -> !c.detected(minScore) && !s.dropped().containsKey(c.type()) && !s.profiles().containsKey(c.type()) && c.score() >= minScore - 25)
                .forEach(c -> LOGGER.info("External fauna: rejected {}", c.describe()));
    }

    /** Deep-ocean biomes where an animal may spawn. */
    public static List<String> biomesOf(Snapshot s, EntityType<?> type)
    {
        List<String> biomes = new ArrayList<>();
        s.byBiome().forEach((key, rules) -> {
            for (FaunaSpawnRule r : rules) if (r.entity() == type) biomes.add(key.location().toString());
        });
        biomes.sort(String::compareTo);
        return biomes;
    }

    // ---------------------------------------------------------------- spawning

    @Override
    public List<FaunaSpawnRule> rules(ServerLevel level, BlockPos pos)
    {
        if (!ExternalFaunaConfig.ENABLED.get() || !DeepLayer.isDeep(level, pos.getY())) return List.of();
        Snapshot s = snapshot(level.getServer());
        if (s.byBiome().isEmpty()) return List.of();
        List<FaunaSpawnRule> rules = level.getBiome(pos).unwrapKey().map(s.byBiome()::get).orElse(null);
        if (rules == null || rules.isEmpty()) return List.of();
        if (countSpawned(level, pos, s) >= ExternalFaunaConfig.MAX_PER_PLAYER.get()) return List.of();
        if (DEMOTED.isEmpty()) return rules;
        return rules.stream().filter(r -> !DEMOTED.contains(r.entity())).toList();
    }

    @Override
    public double maxShare()
    {
        return ExternalFaunaConfig.MAX_SHARE.get();
    }

    @Override
    public boolean accepts(ServerLevel level, FaunaSpawnRule rule, Mob mob, RandomSource random)
    {
        DeepSeaSpawnProfile p = snapshot.profiles().get(rule.entity());
        if (p == null) return false;
        if (p.hostile() && level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (p.minPlayerDistance() > 0 && level.hasNearbyAlivePlayer(mob.getX(), mob.getY(), mob.getZ(), p.minPlayerDistance())) return false;
        BlockPos pos = mob.blockPosition();
        // swimmers registered for water spawns get vanilla's water placement check; seabed walkers are placed by the rule
        if (p.spawnPlacement() == SpawnPlacementTypes.IN_WATER && !SpawnPlacementTypes.IN_WATER.isSpawnPositionOk(level, pos, rule.entity()))
        {
            recordCheck(rule.entity(), false);
            return false;
        }
        if (ExternalFaunaConfig.RESPECT_SPAWN_PREDICATES.get() && !SpawnPlacements.checkSpawnRules(rule.entity(), level, MobSpawnType.NATURAL, pos, random))
        {
            recordCheck(rule.entity(), false);
            return false;
        }
        return true;
    }

    @Override
    public void checked(FaunaSpawnRule rule, Mob mob, boolean ok)
    {
        recordCheck(rule.entity(), ok);
        if (ok) mob.getPersistentData().putBoolean(SPAWNED_TAG, true);
    }

    /** Animals this system spawned around {@code pos}. */
    public static int countSpawned(ServerLevel level, BlockPos pos, Snapshot s)
    {
        return level.getEntities((Entity) null, new AABB(pos).inflate(LIMIT_RADIUS),
                e -> e.isAlive() && s.profiles().containsKey(e.getType()) && e.getPersistentData().getBoolean(SPAWNED_TAG)).size();
    }

    /**
     * Mods' own spawn checks (Mob#checkSpawnRules, Forge spawn events) may reject the deep ocean outright, e.g. a
     * surface-only fish: such a type would only waste attempts, so it is dropped for the session.
     */
    private static void recordCheck(EntityType<?> type, boolean ok)
    {
        int[] c = CHECKS.computeIfAbsent(type, t -> new int[2]);
        int tries;
        int passed;
        synchronized (c)
        {
            tries = ++c[0];
            if (ok) c[1]++;
            passed = c[1];
        }
        if (tries >= DEMOTE_AFTER && passed < tries * DEMOTE_BELOW && DEMOTED.add(type))
        {
            LOGGER.info("External fauna: {} fails its own spawn checks in the deep ocean ({} of {} passed); not tried again this session",
                    EntityType.getKey(type), passed, tries);
        }
    }
}
