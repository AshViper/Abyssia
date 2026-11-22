package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.registry.ModGiants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GNT01 natural spawn of giants (see {@link GiantSpawnRules}). Every {@code giant_spawn_interval_ticks} each survival player
 * gets a roll (chance {@code giant_spawn_chance}); a hit spawns one 64-112 blocks away (horizontally) and the player waits
 * {@code giant_player_cooldown_ticks}. Limits: one non-friendly giant within 160 blocks of the player (a friendly one has
 * its own slot), one of a species within its rule's cap radius, {@code giant_max_loaded} in the whole server. The spot needs
 * water in a sphere of 8 blocks around the head and along the body line (all spawn-time lookups, nothing per tick).
 * Guarantee: when giants are assigned to the player's biome and depth (rule {@code biomes}) and none of them is alive within
 * 160 blocks, the roll and the cooldown are skipped and one of those species spawns (24 attempts; species cap and
 * max_loaded still apply, the hostile/friendly-near slot does not).
 * Giants are not fauna rules, so {@link FaunaSpawner} neither spawns nor counts them.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class GiantSpawner
{
    private static final String COOLDOWN_KEY = "abyssia_giant_cd";
    private static final double MIN_DIST = 64, MAX_DIST = 112, PLAYER_RADIUS = 160;
    private static final int ATTEMPTS = 8, GUARANTEED_ATTEMPTS = 24, FREE_SPHERE = 8, LINE_STEP = 4;

    private GiantSpawner() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Config.GIANT_ENABLED.get()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % Config.GIANT_SPAWN_INTERVAL.get() != 0 || GiantSpawnRules.rules().isEmpty()) return;
        int loaded = loadedCount(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
        {
            if (loaded >= Config.GIANT_MAX_LOADED.get()) return;
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            ServerLevel level = p.serverLevel();
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) continue;
            long now = level.getGameTime();
            Set<String> must = uncoveredSet(level, p);
            boolean guaranteed = must != null;
            if (!guaranteed)
            {
                if (now < p.getPersistentData().getLong(COOLDOWN_KEY)) continue;
                if (level.random.nextDouble() >= Config.GIANT_SPAWN_CHANCE.get()) continue;
            }
            GiantFauna spawned = spawnNatural(level, p, must);
            if (spawned != null)
            {
                if (!guaranteed) p.getPersistentData().putLong(COOLDOWN_KEY, now + Config.GIANT_PLAYER_COOLDOWN.get());
                loaded++;
                com.mojang.logging.LogUtils.getLogger().info("GNT01 natural spawn{} {} at {} {} {} dist {} loaded {}", guaranteed ? " (guaranteed)" : "", spawned.spec().id(),
                        Math.round(spawned.getX()), Math.round(spawned.getY()), Math.round(spawned.getZ()), Math.round(spawned.headPos().distanceTo(p.position())), loaded);
            }
        }
    }

    /** Live giants loaded in every dimension. */
    public static int loadedCount(MinecraftServer server)
    {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) n += level.getEntities(EntityTypeTest.forClass(GiantFauna.class), GiantFauna::isAlive).size();
        return n;
    }

    /**
     * Species ids assigned to the player's biome and depth when none of them is alive within {@link #PLAYER_RADIUS};
     * null when nothing is assigned or one is already near (normal roll path).
     */
    @Nullable
    private static Set<String> uncoveredSet(ServerLevel level, ServerPlayer p)
    {
        Holder<Biome> biome = level.getBiome(p.blockPosition());
        Set<String> assigned = new HashSet<>();
        for (GiantSpawnRules.Rule r : GiantSpawnRules.rules())
            if (r.weight() > 0 && ModGiants.byId(r.species()) != null && r.matches(biome, p.getY())) assigned.add(r.species());
        if (assigned.isEmpty()) return null;
        Vec3 pp = p.position();
        for (GiantFauna g : level.getEntitiesOfClass(GiantFauna.class, p.getBoundingBox().inflate(PLAYER_RADIUS), GiantFauna::isAlive))
            if (g.headPos().distanceToSqr(pp) <= PLAYER_RADIUS * PLAYER_RADIUS && assigned.contains(g.spec().id())) return null;
        return assigned;
    }

    @Nullable
    private static GiantFauna spawnNatural(ServerLevel level, ServerPlayer p, @Nullable Set<String> only)
    {
        int attempts = only != null ? GUARANTEED_ATTEMPTS : ATTEMPTS;
        Vec3 pp = p.position();
        boolean hostileNear = false, friendlyNear = false;
        for (GiantFauna g : level.getEntitiesOfClass(GiantFauna.class, p.getBoundingBox().inflate(PLAYER_RADIUS), GiantFauna::isAlive))
        {
            if (g.headPos().distanceToSqr(pp) > PLAYER_RADIUS * PLAYER_RADIUS) continue;
            if (g.spec().temper() == GiantBrain.Temper.FRIENDLY) friendlyNear = true;
            else hostileNear = true;
        }
        List<GiantFauna> nearby = level.getEntitiesOfClass(GiantFauna.class, p.getBoundingBox().inflate(256 + MAX_DIST), GiantFauna::isAlive);
        RandomSource random = level.random;
        for (int attempt = 0; attempt < attempts; attempt++)
        {
            double dist = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
            double ang = random.nextDouble() * Math.PI * 2;
            double x = pp.x + Math.cos(ang) * dist, z = pp.z + Math.sin(ang) * dist;
            // guaranteed path: a player on the sea floor has rock below, so also look up to 48 blocks above
            double y = only != null ? pp.y - 16 + random.nextDouble() * 64 : pp.y + (random.nextDouble() * 2 - 1) * 16;
            Vec3 head = new Vec3(x, y, z);
            Holder<Biome> biomeAt = level.getBiome(BlockPos.containing(head));
            List<GiantSpawnRules.Rule> fit = new ArrayList<>();
            double total = 0;
            for (GiantSpawnRules.Rule r : GiantSpawnRules.rules())
            {
                ModGiants.Species sp = ModGiants.byId(r.species());
                if (sp == null || r.weight() <= 0 || !r.matches(biomeAt, y)) continue;
                if (only != null)
                {
                    if (!only.contains(r.species())) continue;
                }
                else
                {
                    boolean friendly = sp.spec().temper() == GiantBrain.Temper.FRIENDLY;
                    if (friendly ? friendlyNear : hostileNear) continue;
                }
                if (speciesNear(nearby, sp, head, r.cap().radius()) >= r.cap().count()) continue;
                fit.add(r);
                total += r.weight();
            }
            if (fit.isEmpty()) continue;
            double pick = random.nextDouble() * total;
            GiantSpawnRules.Rule chosen = fit.get(fit.size() - 1);
            for (GiantSpawnRules.Rule r : fit)
            {
                pick -= r.weight();
                if (pick <= 0) { chosen = r; break; }
            }
            // swims level, a little off the straight line toward the player
            ModGiants.Species csp = ModGiants.byId(chosen.species());
            double toward = Math.atan2(pp.z - z, pp.x - x) + (random.nextDouble() - 0.5) * 1.0;
            // short simulation distance: keep head and tail inside the entity-ticking radius, head heading away so the tail is nearer
            double tickR = level.getServer().getPlayerList().getSimulationDistance() * 16.0;
            double maxD = Math.min(MAX_DIST, tickR - csp.spec().length() / 2 - 8);
            if (maxD < MAX_DIST)
            {
                double minD = Math.max(24, Math.min(MIN_DIST, maxD * 0.6));
                if (maxD < minD) continue;
                double d2 = minD + random.nextDouble() * (maxD - minD);
                head = new Vec3(pp.x + Math.cos(ang) * d2, y, pp.z + Math.sin(ang) * d2);
                toward += Math.PI;
            }
            GiantFauna g = create(level, csp, head, new Vec3(Math.cos(toward), 0, Math.sin(toward)));

            if (g != null) return g;
        }
        return null;
    }

    private static int speciesNear(List<GiantFauna> all, ModGiants.Species sp, Vec3 at, double radius)
    {
        AABB box = new AABB(at, at).inflate(radius);
        int n = 0;
        for (GiantFauna g : all)
            if (g.getType() == sp.type().get() && g.getBoundingBox().intersects(box)) n++;
        return n;
    }

    /**
     * Spawns {@code sp} with its head at {@code head} swimming along {@code dir}, if the space is free (see
     * {@link #isFree}); null when it is not. Also used by the command and the devtest.
     */
    @Nullable
    public static GiantFauna create(ServerLevel level, ModGiants.Species sp, Vec3 head, Vec3 dir)
    {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.normalize();
        if (!isFree(level, sp, head, d)) return null;
        GiantFauna g = sp.type().get().create(level);
        if (g == null) return null;
        g.launch(head, d);
        return level.addFreshEntity(g) ? g : null;
    }

    /**
     * Loaded chunks, water in a sphere of 8 blocks around the head, and water (clear of rock all round the body) along
     * the heading line every 4 blocks from one body length behind the head to one ahead.
     */
    public static boolean isFree(ServerLevel level, ModGiants.Species sp, Vec3 head, Vec3 dir)
    {
        double len = sp.spec().length();
        int reach = (int) Math.ceil(len) + FREE_SPHERE;
        BlockPos hp = BlockPos.containing(head);
        if (!level.hasChunksAt(hp.offset(-reach, 0, -reach), hp.offset(reach, 0, reach))) return false;
        // Only the entity's own position (the head) must tick: the body chain is driven from it, so a tail reaching past
        // the simulation distance is fine (with simulation-distance 4 a 50-60 block body cannot fit inside 64 blocks).
        if (!level.isPositionEntityTicking(hp)) return false;
        if (hp.getY() - FREE_SPHERE < level.getMinBuildHeight() || hp.getY() + FREE_SPHERE >= level.getMaxBuildHeight()) return false;
        for (int dx = -FREE_SPHERE; dx <= FREE_SPHERE; dx++)
            for (int dy = -FREE_SPHERE; dy <= FREE_SPHERE; dy++)
                for (int dz = -FREE_SPHERE; dz <= FREE_SPHERE; dz++)
                    if (dx * dx + dy * dy + dz * dz <= FREE_SPHERE * FREE_SPHERE && GiantFauna.blocked(level, head.x + dx, head.y + dy, head.z + dz))
                        return false;
        double r = sp.spec().radius() * 0.8;
        for (double t = -len; t <= len; t += LINE_STEP)
            if (GiantFauna.blockedAround(level, head.add(dir.scale(t)), dir, r)) return false;
        return true;
    }
}
