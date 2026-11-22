package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.AbyssBoss;
import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.fauna.GiantSpawner;
import com.abyssia.registry.ModEntities;
import com.abyssia.registry.ModGiants;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.VehicleContent;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only (GNT02): with -Dabyssia.gnt02test (gradlew runServer -Pgnt02Test=1) the dedicated server fills a water box and checks that the
 * giant wild animals are neutral and the bosses still hostile. Per species: (a) no_unprovoked: a survival fake player 20 blocks
 * and an unridden hull 25 blocks away are never targeted for 200 ticks; (b) player_retaliate: a hit by the player gives WARN then
 * CHASE with the player as target (a friendly species stays WANDER); (c) hull_retaliate: a hit whose damage source entity is the
 * hull makes the giant damage the hull. Then each boss must pick the fake player 20 blocks away as its target (boss_unprovoked_target).
 * The fake player is really added to level.players(). Logs "GNT02 id check PASS|FAIL numbers" and "GNT02 DONE pass= fail=", then halts.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Gnt02Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CX = 16000, CZ = 16000, HALF = 72, HALF_Y = 12;
    private static final String[] BOSSES = {"abyss_serpent", "abyss_kraken", "trench_wyrm", "abyss_mother", "abyss_angler"};

    private record Case(String check, String id) {}

    private static ServerLevel level;
    private static int serverTicks, passes, fails, waited, index, t;
    private static boolean started, finished, filled, playerOk;
    private static BlockPos centre;
    private static List<Case> queue;
    private static FakePlayer fake;
    private static GiantFauna giant;
    private static AbyssBoss boss;
    private static Submarine hull;
    private static float dmg0;
    private static boolean okAll, warnSeen;
    private static int chaseTick, firstHit;
    private static double minDist, startDist;
    private static final EnumSet<GiantBrain.State> seen = EnumSet.noneOf(GiantBrain.State.class);

    private static void result(String id, String name, boolean ok, String measured)
    {
        if (ok) passes++;
        else fails++;
        LOG.info("GNT02 {} {} {} {}", id, name, ok ? "PASS" : "FAIL", measured);
    }

    private static void forceBox(boolean on)
    {
        for (int cx = (CX - HALF) >> 4; cx <= (CX + HALF) >> 4; cx++)
            for (int cz = (CZ - HALF) >> 4; cz <= (CZ + HALF) >> 4; cz++) level.setChunkForced(cx, cz, on);
    }

    private static boolean chunksReady()
    {
        for (int cx = (CX - HALF) >> 4; cx <= (CX + HALF) >> 4; cx++)
            for (int cz = (CZ - HALF) >> 4; cz <= (CZ + HALF) >> 4; cz++)
            {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
                long cp = ChunkPos.asLong(cx, cz);
                if (!level.getChunk(cx, cz).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) || !level.areEntitiesLoaded(cp) || !level.shouldTickBlocksAt(cp))
                    return false;
            }
        return true;
    }

    private static void fillWater()
    {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        var water = Blocks.WATER.defaultBlockState();
        for (int x = CX - HALF; x <= CX + HALF; x++)
            for (int z = CZ - HALF; z <= CZ + HALF; z++)
                for (int y = centre.getY() - HALF_Y; y <= centre.getY() + HALF_Y; y++)
                    level.setBlock(m.set(x, y, z), water, 2 | 16);
    }

    private static Submarine spawnSub(Vec3 c)
    {
        Submarine s = VehicleContent.SUBMARINE.get().create(level);
        s.setPos(c.x, c.y - s.getBbHeight() * 0.5, c.z);
        level.addFreshEntity(s);
        return s;
    }

    private static void placeHull(Vec3 c)
    {
        hull.setPos(c.x, c.y - hull.getBbHeight() * 0.5, c.z);
        hull.setDeltaMovement(Vec3.ZERO);
    }

    private static void placePlayer(Vec3 p)
    {
        fake.setPos(p.x, p.y, p.z);
        fake.setDeltaMovement(Vec3.ZERO);
    }

    private static DamageSource hullHit()
    {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC), hull);
    }

    private static void cleanup()
    {
        if (giant != null) giant.discard();
        if (boss != null) boss.discard();
        if (hull != null) hull.discard();
        giant = null;
        boss = null;
        hull = null;
    }

    private static void next(MinecraftServer server)
    {
        cleanup();
        t = 0;
        if (++index >= queue.size()) finish(server);
    }

    private static void finish(MinecraftServer server)
    {
        finished = true;
        cleanup();
        if (fake != null) level.removePlayerImmediately(fake, Entity.RemovalReason.DISCARDED);
        forceBox(false);
        LOG.info("GNT02 DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }

    private static GiantBrain.State state() { return giant.brain().state(); }

    @SuppressWarnings("unchecked")
    private static Entity bossTarget()
    {
        try
        {
            for (Class<?> c = boss.getClass(); c != null; c = c.getSuperclass())
            {
                try
                {
                    var f = c.getDeclaredField("target");
                    f.setAccessible(true);
                    return (Entity) f.get(boss);
                }
                catch (NoSuchFieldException ignored) {}
            }
        }
        catch (Exception e) { LOG.error("GNT02 reflection", e); }
        return null;
    }

    private static EntityType<? extends AbyssBoss> bossType(String id)
    {
        return switch (id)
        {
            case "abyss_serpent" -> ModEntities.ABYSS_SERPENT.get();
            case "abyss_kraken" -> ModEntities.ABYSS_KRAKEN.get();
            case "trench_wyrm" -> ModEntities.TRENCH_WYRM.get();
            case "abyss_mother" -> ModEntities.ABYSS_MOTHER.get();
            default -> ModEntities.ABYSS_ANGLER.get();
        };
    }

    /** Returns true when the case ended this tick. */
    private static boolean caseTick(MinecraftServer server)
    {
        Case c = queue.get(index);
        Vec3 centreV = Vec3.atCenterOf(centre);
        if (t == 0)
        {
            seen.clear();
            okAll = true;
            warnSeen = false;
            chaseTick = firstHit = -1;
            if (c.check.equals("boss_unprovoked_target"))
            {
                boss = bossType(c.id).create(level);
                if (boss == null) { result(c.id, c.check, false, "create returned null"); return true; }
                boss.setPos(centreV.x, centreV.y - boss.getBbHeight() * 0.5, centreV.z);
                boss.bindArena(centreV, 150, new Vec3(0.8, 0, 0));
                level.addFreshEntity(boss);
                placePlayer(centreV.add(0, 0, 20));
                startDist = minDist = boss.headPos().distanceTo(fake.position());
                t = 1;
                return false;
            }
            giant = GiantSpawner.create(level, ModGiants.byId(c.id), centreV, new Vec3(1, 0, 0));
            if (giant == null) { result(c.id, c.check, false, "GiantSpawner.create returned null"); return true; }
            Vec3 head = giant.headPos();
            switch (c.check)
            {
                case "no_unprovoked" ->
                {
                    hull = spawnSub(head.add(0, 0, -25));
                    placePlayer(head.add(0, 0, 20));
                }
                case "player_retaliate" -> placePlayer(head.add(0, 0, 20));
                default ->
                {
                    hull = spawnSub(head.add(0, 0, 25));
                    placePlayer(head.add(-60, 0, -60));
                }
            }
            if (hull != null) dmg0 = hull.getDamage();
            t = 1;
            return false;
        }
        t++;
        switch (c.check)
        {
            case "boss_unprovoked_target" ->
            {
                Entity tg = bossTarget();
                double d = boss.headPos().distanceTo(fake.position());
                minDist = Math.min(minDist, d);
                boolean got = tg == fake;
                if (got || t > 200)
                {
                    result(c.id, c.check, got, String.format("target=%s after %d ticks, boss-player distance start %.1f min %.1f", tg == null ? "null" : tg.getName().getString(), t, startDist, minDist));
                    return true;
                }
            }
            case "no_unprovoked" ->
            {
                Vec3 head = giant.headPos();
                seen.add(state());
                if (state() != GiantBrain.State.WANDER || giant.brain().target() != null) okAll = false;
                placePlayer(head.add(0, 0, 20));
                placeHull(head.add(0, 0, -25));
                if (t >= 200)
                {
                    boolean ok = okAll && hull.getDamage() == dmg0 && giant.isAlive();
                    result(c.id, c.check, ok, String.format("%d ticks, states %s, hull damage %.1f -> %.1f", t, seen, dmg0, hull.getDamage()));
                    return true;
                }
            }
            case "player_retaliate" ->
            {
                Vec3 head = giant.headPos();
                boolean friendly = giant.spec().temper() == GiantBrain.Temper.FRIENDLY;
                if (t == 2) giant.hurt(level.damageSources().playerAttack(fake), 1.0F);
                seen.add(state());
                if (state() == GiantBrain.State.WARN) warnSeen = true;
                boolean chased = state() == GiantBrain.State.CHASE && giant.brain().target() == fake;
                if (chased && chaseTick < 0) chaseTick = t;
                placePlayer(head.add(0, 0, 20));
                int warn = giant.ability().warnTicks();
                if (friendly)
                {
                    if (t >= 100)
                    {
                        result(c.id, c.check, state() == GiantBrain.State.WANDER && seen.size() == 1, "friendly, states " + seen + " after " + t + " ticks");
                        return true;
                    }
                }
                else if (chaseTick >= 0 || t > 2 + warn + 40)
                {
                    result(c.id, c.check, warnSeen && chaseTick >= 0, String.format("warn seen %s, CHASE (target=player) at tick %d (hurt at 2, warnTicks %d), states %s", warnSeen, chaseTick, warn, seen));
                    return true;
                }
            }
            case "hull_retaliate" ->
            {
                if (t == 2) giant.hurt(hullHit(), 1.0F);
                placeHull(hull.position().add(0, hull.getBbHeight() * 0.5, 0)); // hold it where it is (no drift)
                seen.add(state());
                if (hull.getDamage() > dmg0 && firstHit < 0) firstHit = t;
                if (firstHit >= 0 || t >= 400)
                    {
                        result(c.id, c.check, firstHit >= 0, String.format("first hull damage at tick %d (hurt at 2), damage %.1f -> %.1f, states %s", firstHit, dmg0, hull.getDamage(), seen));
                        return true;
                    }
            }
            default -> { return true; }
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (System.getProperty("abyssia.gnt02test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        try
        {
            if (!started)
            {
                started = true;
                level = server.overworld();
                centre = new BlockPos(CX, com.abyssia.Config.SUBMARINE_RATED_Y_BASE.get() + 40, CZ); // above the hull crush depth, else the hull damages itself
                forceBox(true);
                queue = new ArrayList<>();
                for (String id : ModGiants.ids())
                {
                    queue.add(new Case("no_unprovoked", id));
                    queue.add(new Case("player_retaliate", id));
                    if (ModGiants.byId(id).spec().temper() != GiantBrain.Temper.FRIENDLY) queue.add(new Case("hull_retaliate", id));
                }
                for (String b : BOSSES) queue.add(new Case("boss_unprovoked_target", b));
                LOG.info("GNT02 start {} cases", queue.size());
            }
            if (!filled)
            {
                if (!chunksReady())
                {
                    if (++waited > 1200) { result("-", "chunks", false, "not ready after " + waited + " ticks"); finish(server); }
                    return;
                }
                LOG.info("GNT02 chunks ready after {} ticks", waited);
                fillWater();
                filled = true;
                fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000a02"), "[Gnt02]"));
                fake.setPos(CX, centre.getY(), CZ + 60);
                level.addNewPlayer(fake);
                playerOk = level.players().contains(fake) && !fake.isCreative() && !fake.isSpectator();
                result("-", "player_in_level", playerOk, "players().contains=" + level.players().contains(fake) + ", creative=" + fake.isCreative() + ", spectator=" + fake.isSpectator());
                if (!playerOk) finish(server);
                return;
            }
            if (caseTick(server)) next(server);
        }
        catch (Exception e)
        {
            result("-", "exception", false, e.toString());
            LOG.error("GNT02 exception", e);
            finish(server);
        }
    }
}
