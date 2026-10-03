package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.fauna.external.ExternalSpawnProvider;
import com.abyssia.worldgen.DeepLayer;
import com.abyssia.worldgen.OceanChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Populates the deep sea around players from the fauna spawn rules, instead of vanilla's biome spawner lists: each
 * species gets its real depth range, its placement (open water, near the seabed, on it), cave or open water, light,
 * and the habitat around (rock, sediment...). Vanilla samples heights uniformly and caps whole categories, which
 * cannot put a walker on the seabed or keep a species to its depth band; this can.
 * <p>
 * Every {@code spawn_interval} ticks each player gets a few attempts: a random water position 24-56 blocks away and
 * within 24 blocks of their height is analysed ({@link SpawnSite}), every rule proposes its own spot there and a
 * weight, and one is picked (or nothing, which keeps sparse habitats sparse). Per-species caps and a per-player limit
 * bound the population; animals despawn the vanilla way when players leave.
 * <p>
 * The ocean world and the deep layer below its bedrock band are kept apart: attempts stay in the player's layer, and
 * the per-player limit and species caps only count animals in the same layer, as when they were two dimensions.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class FaunaSpawner
{
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 56;
    private static final int VERTICAL = 24;
    /**
     * Radius of the per-player limits: vanilla's instant despawn distance for water creatures (128), so an animal that
     * swims out of the counted range is gone instead of uncounted. With 96, animals drifting to 96-128 blocks (still
     * tracked and drawn by the client with the wider deep-sea view) no longer counted and the spawner kept refilling.
     */
    public static final int LIMIT_RADIUS = 128;
    /** All managed fauna (scenery colonies and vent communities included) may reach this multiple of the per-player limit. */
    private static final double TOTAL_LIMIT_FACTOR = 1.5;
    /** Weight of "nothing spawns here": sparse habitats stay sparse. */
    private static final double EMPTY_WEIGHT = 4.0;
    /** Native weight assumed for a bounded provider's budget where no native animal fits (a typical species weight). */
    private static final double BUDGET_FLOOR = 10.0;
    /** Native fauna first: bounded providers budget against it. */
    private static final List<FaunaSpawnProvider> PROVIDERS = List.of(FaunaSpawnProvider.NATIVE, ExternalSpawnProvider.INSTANCE);

    private FaunaSpawner() {}

    /** The ocean world when it is Abyssia's: its waters and the deep layer below the bedrock band share one depth scale. */
    public static boolean isFaunaLevel(ServerLevel level)
    {
        return level.dimension() == Level.OVERWORLD && level.getChunkSource().getGenerator() instanceof OceanChunkGenerator;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !Config.FAUNA_SPAWNING.get()) return;
        if (level.getGameTime() % Config.FAUNA_SPAWN_INTERVAL.get() != 0) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || !level.getServer().isSpawningAnimals()) return;
        if (level.players().isEmpty() || FaunaSpawnRules.rules().isEmpty() || !isFaunaLevel(level)) return;
        for (ServerPlayer player : level.players())
        {
            if (player.isSpectator()) continue;
            for (int i = 0; i < Config.FAUNA_SPAWN_ATTEMPTS.get(); i++) attempt(level, player.position(), level.random, null);
            ventAttempt(level, player.position(), level.random, null);
        }
    }

    /**
     * The deep layer has no drowned (its biomes spawn none since the fauna took over): ones saved in chunks from before
     * are cleared when their chunk loads. A named drowned is left alone.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (event.loadedFromDisk() && event.getEntity() instanceof Drowned drowned && !drowned.hasCustomName()
                && DeepLayer.isDeep(event.getLevel(), drowned.getY()))
        {
            event.setCanceled(true);
        }
    }

    /**
     * One spawn attempt around a player (or any point, for the debug command); returns how many animals spawned.
     * {@code log} gets the reasoning.
     */
    public static int attempt(ServerLevel level, Vec3 centre, RandomSource random, @Nullable List<String> log)
    {
        if (limitReached(level, BlockPos.containing(centre)))
        {
            if (log != null) log.add("player limit reached");
            return 0;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        // stay in the centre's layer: the ocean world above the bedrock band or the deep layer below it
        boolean deep = DeepLayer.isDeep(level, centre.y);
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        if (deep) maxY = DeepLayer.TOP_Y - 1;
        else if (level.dimension() == Level.OVERWORLD) minY = Math.max(minY, DeepLayer.TOP_Y);
        int y = Mth.clamp(Mth.floor(centre.y) + random.nextInt(VERTICAL * 2 + 1) - VERTICAL, minY, maxY);
        BlockPos pos = BlockPos.containing(centre.x + Math.cos(angle) * distance, y, centre.z + Math.sin(angle) * distance);
        return spawnAt(level, pos, random, log);
    }

    /**
     * Vent animals live on a few blocks around the vent cores, which random sampling would almost never hit: this
     * attempt picks a vent in a loaded chunk near the player (see {@link VentSites}) and samples right beside it.
     */
    public static int ventAttempt(ServerLevel level, Vec3 centre, RandomSource random, @Nullable List<String> log)
    {
        if (limitReached(level, BlockPos.containing(centre))) return 0;
        ChunkPos here = new ChunkPos(BlockPos.containing(centre));
        List<BlockPos> vents = List.of();
        // a few loaded chunks within 4 of the player (the scans are cached, so this stays cheap)
        for (int i = 0; i < 4 && vents.isEmpty(); i++)
        {
            vents = VentSites.vents(level, new ChunkPos(here.x + random.nextInt(9) - 4, here.z + random.nextInt(9) - 4));
        }
        if (vents.isEmpty())
        {
            if (log != null) log.add("no vents in the sampled chunk");
            return 0;
        }
        BlockPos vent = vents.get(random.nextInt(vents.size()));
        BlockPos pos = vent.offset(random.nextInt(9) - 4, 1 + random.nextInt(4), random.nextInt(9) - 4);
        if (log != null) log.add("vent at " + vent.toShortString());
        return spawnAt(level, pos, random, log, FaunaSpawnRule::anchoredToVents);
    }

    /** Evaluates every rule at a sampled position and spawns the chosen animal (group). */
    public static int spawnAt(ServerLevel level, BlockPos pos, RandomSource random, @Nullable List<String> log)
    {
        return spawnAt(level, pos, random, log, rule -> !rule.anchoredToVents());
    }

    private static int spawnAt(ServerLevel level, BlockPos pos, RandomSource random, @Nullable List<String> log, Predicate<FaunaSpawnRule> rules)
    {
        if (!level.isPositionEntityTicking(pos)) return 0;
        // outside the ocean world (the vanilla-style world) Abyssia fauna lives in the deep layer only
        if (!DeepLayer.OCEAN_WORLD_EFFECTS.equals(level.dimensionType().effectsLocation()) && !DeepLayer.isDeep(level, pos.getY()))
        {
            if (log != null) log.add("surface layer of a vanilla-style world at " + pos.toShortString());
            return 0;
        }
        SpawnSite site = SpawnSite.at(level, pos);
        if (site == null)
        {
            if (log != null) log.add("not open water at " + pos.toShortString());
            return 0;
        }
        List<FaunaSpawnRule> candidates = new ArrayList<>();
        List<FaunaSpawnProvider> owners = new ArrayList<>();
        List<BlockPos> spots = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        double unbounded = 0;
        double density = Config.FAUNA_DENSITY.get();
        for (FaunaSpawnProvider provider : PROVIDERS)
        {
            int first = candidates.size();
            double sum = 0;
            for (FaunaSpawnRule rule : provider.rules(level, site.pos()))
            {
                if (!rules.test(rule)) continue;
                BlockPos spot = rule.place(site, random);
                if (spot == null) continue;
                double w = rule.weight(site, spot) * density;
                if (w <= 0) continue;
                candidates.add(rule);
                owners.add(provider);
                spots.add(spot);
                weights.add(w);
                sum += w;
            }
            double share = provider.maxShare();
            if (share >= 1.0)
            {
                unbounded += sum;
            }
            else if (sum > 0)
            {
                // a bounded provider takes at most `share` of the weight next to the native fauna here
                double budget = share / (1.0 - share) * Math.max(unbounded, density * BUDGET_FLOOR);
                if (sum > budget)
                {
                    for (int i = first; i < weights.size(); i++) weights.set(i, weights.get(i) * budget / sum);
                    sum = budget;
                }
            }
            total += sum;
        }
        if (log != null)
        {
            for (int i = 0; i < candidates.size(); i++) log.add(String.format("%s: weight %.2f at %s", key(candidates.get(i)), weights.get(i), spots.get(i).toShortString()));
        }
        if (candidates.isEmpty()) return 0;
        double roll = random.nextDouble() * (total + EMPTY_WEIGHT);
        for (int i = 0; i < candidates.size(); i++)
        {
            roll -= weights.get(i);
            if (roll < 0) return spawnGroup(level, owners.get(i), candidates.get(i), spots.get(i), random, log);
        }
        if (log != null) log.add("rolled nothing");
        return 0;
    }

    private static int spawnGroup(ServerLevel level, FaunaSpawnProvider provider, FaunaSpawnRule rule, BlockPos spot, RandomSource random, @Nullable List<String> log)
    {
        int radius = rule.cap().radius();
        boolean deep = DeepLayer.isDeep(level, spot.getY());
        int present = level.getEntities(rule.entity(), new AABB(spot).inflate(radius), e -> e.isAlive() && !(e instanceof PartEntity<?>)
                && DeepLayer.isDeep(level, e.getY()) == deep).size();
        if (present >= rule.cap().count())
        {
            if (log != null) log.add(key(rule) + ": cap reached (" + present + " within " + radius + ")");
            return 0;
        }
        int size = rule.group().min() + random.nextInt(Math.max(1, rule.group().max() - rule.group().min() + 1));
        size = Math.min(size, rule.cap().count() - present);
        int spawned = 0;
        for (int i = 0; i < size * 4 && spawned < size; i++)
        {
            BlockPos p = i == 0 ? spot : spot.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
            if (i > 0 && !fitsLike(level, rule, p)) continue;
            if (spawnOne(level, provider, rule, p, random)) spawned++;
        }
        if (log != null) log.add("spawned " + spawned + " " + key(rule) + " at " + spot.toShortString());
        return spawned;
    }

    /** Group members follow the same placement as the first one. */
    private static boolean fitsLike(ServerLevel level, FaunaSpawnRule rule, BlockPos p)
    {
        if (!SpawnSite.isWater(level, p)) return false;
        return rule.placement() != FaunaSpawnRule.Placement.SEABED || level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP);
    }

    private static boolean spawnOne(ServerLevel level, FaunaSpawnProvider provider, FaunaSpawnRule rule, BlockPos p, RandomSource random)
    {
        Entity entity = rule.entity().create(level);
        if (!(entity instanceof Mob mob)) return false;
        mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob) || !provider.accepts(level, rule, mob, random))
        {
            mob.discard();
            return false;
        }
        boolean ok = ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
        provider.checked(rule, mob, ok);
        if (!ok)
        {
            mob.discard();
            return false;
        }
        ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(p), MobSpawnType.NATURAL, null, null);
        level.addFreshEntityWithPassengers(mob);
        return true;
    }

    /**
     * Whether no more animals may spawn around a point: the mobile fauna's per-player limit, or all managed fauna
     * (environmental colonies and vent communities, which the mobile limit leaves out) past {@link #TOTAL_LIMIT_FACTOR}
     * times it, both within {@link #LIMIT_RADIUS}. Without the second bound, colonies and vent animals were capped only
     * per species around each spawn spot, so their total grew with the area around the player.
     */
    public static boolean limitReached(ServerLevel level, BlockPos around)
    {
        int max = Config.FAUNA_MAX_PER_PLAYER.get();
        boolean deep = DeepLayer.isDeep(level, around.getY());
        int mobile = 0, all = 0;
        for (Entity e : level.getEntities((Entity) null, new AABB(around).inflate(LIMIT_RADIUS), e -> e.isAlive() && !(e instanceof PartEntity<?>)
                && FaunaSpawnRules.isFauna(e.getType()) && DeepLayer.isDeep(level, e.getY()) == deep))
        {
            all++;
            if (FaunaSpawnRules.countsTowardLimit(e.getType())) mobile++;
        }
        return mobile >= max || all >= max * TOTAL_LIMIT_FACTOR;
    }

    /** Fauna counting toward the per-player limit around a point, in the same layer (ocean world or deep layer) only. */
    public static int countFauna(ServerLevel level, BlockPos around, int radius)
    {
        boolean deep = DeepLayer.isDeep(level, around.getY());
        return level.getEntities((Entity) null, new AABB(around).inflate(radius), e -> e.isAlive() && !(e instanceof PartEntity<?>)
                && FaunaSpawnRules.countsTowardLimit(e.getType()) && DeepLayer.isDeep(level, e.getY()) == deep).size();
    }

    private static String key(FaunaSpawnRule rule)
    {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(rule.entity()).getPath();
    }
}
