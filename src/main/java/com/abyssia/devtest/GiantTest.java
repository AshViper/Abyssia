package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.entity.giant.GiantPart;
import com.abyssia.fauna.GiantSpawner;
import com.abyssia.registry.ModGiants;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.VehicleContent;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

/**
 * Dev-only (GNT01): with -Dabyssia.gianttest (gradle runGiantTest) the dedicated server fills a 97 x 25 x 97 block box with
 * water, runs every giant species one after another in it (-Dabyssia.gianttest=<id> only that one) and checks (a) the measured length is within 1 block of the spec, (b) a hit on a body part
 * lowers the animal's health, (c) after 200 ticks of swimming (sampled every 10 ticks) no hitbox part was ever inside a solid
 * block. Species with an ability (lancejaw, rift_lamprey, duskmaw, glassback, crestfin) then run an ability check (see {@link #startAbility}). Logs "GNT01 name PASS|FAIL measured" lines and "GNT01 DONE pass= fail=", then halts.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class GiantTest
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CX = 12000, CZ = 12000, HALF = 72, HALF_Y = 12, RUN = 200;

    private static ServerLevel level;
    private static int serverTicks, passes, fails, swum, maxSolidParts, maxSolidJoints, waited, index;
    private static java.util.List<String> queue;
    private static boolean started, finished, filled;
    private static BlockPos centre;
    private static GiantFauna giant;
    private static Vec3 startHead;
    // ability check (GNT01 abilities)
    private static boolean abilityActive, abA, abB, abC;
    private static int abTicks, abFirstChase, abFe0;
    private static net.minecraft.world.phys.Vec3 abPos0;
    private static float abDmg0;
    private static Submarine abSub;
    private static net.minecraft.server.level.ServerPlayer abPlayer;

    private static void result(String name, boolean ok, String measured)
    {
        if (ok) passes++;
        else fails++;
        LOG.info("GNT01 {} {} {} {}", queue != null && index < queue.size() ? queue.get(index) : "-", name, ok ? "PASS" : "FAIL", measured);
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

    /** Ends the current species; the next one starts on the next tick, or the run finishes after the last. */
    private static void next(MinecraftServer server)
    {
        if (giant != null) giant.discard();
        giant = null;
        swum = maxSolidParts = maxSolidJoints = 0;
        if (++index >= queue.size()) finish(server);
    }

    private static void finish(MinecraftServer server)
    {
        finished = true;
        if (giant != null) giant.discard();
        giant = null;
        forceBox(false);
        LOG.info("GNT01 DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }

    // ---- ability checks

    private static Submarine spawnSub(Vec3 centre)
    {
        Submarine s = VehicleContent.SUBMARINE.get().create(level);
        s.setPos(centre.x, centre.y - s.getBbHeight() * 0.5, centre.z);
        level.addFreshEntity(s);
        return s;
    }

    /**
     * Sets up the ability check of the current species on a fresh animal (head at the box centre, swimming +x); false when the
     * species has none (or it was done at once). Later ticks go through {@link #abilityTick}.
     * lancejaw: reach test of the lance (a hull 6.5 ahead is hit, one 12 ahead is not). rift_lamprey: hold animation, hull speed halved
     * (the slow flag), hull damage. duskmaw: grab hold, speed halved, FE drain more than the lights alone. glassback: glow off
     * while chasing from afar, on again within 16. crestfin: hit by a player, holds crest_up in the 60 tick warning, then chases.
     * The giants are neutral, so rift_lamprey, duskmaw and glassback are provoked at tick 1 by a hit whose damage source entity is the test hull.
     */
    private static boolean startAbility()
    {
        String id = queue.get(index);
        if (!java.util.List.of("lancejaw", "rift_lamprey", "duskmaw", "glassback", "crestfin").contains(id)) return false;
        giant.discard();
        giant = GiantSpawner.create(level, ModGiants.byId(id), Vec3.atCenterOf(centre), new Vec3(1, 0, 0));
        if (giant == null)
        {
            result("ability_spawn", false, "GiantSpawner.create returned null");
            return false;
        }
        Vec3 head = giant.headPos();
        abA = abB = abC = false;
        abTicks = 0;
        abFirstChase = -1;
        switch (id)
        {
            case "lancejaw" ->
            {
                Submarine s = spawnSub(head.add(6.5, 0, 0));
                boolean near = Boolean.TRUE.equals(giant.ability().contact(s));
                s.setPos(head.x + 12, head.y - s.getBbHeight() * 0.5, head.z);
                boolean far = Boolean.TRUE.equals(giant.ability().contact(s));
                s.discard();
                result("ability_lance_reach", near && !far, "hull at 6.5 ahead hit=" + near + ", at 12 ahead hit=" + far);
                return false;
            }
            case "rift_lamprey" ->
            {
                abSub = spawnSub(head.add(25, 0, 0));
                abDmg0 = abSub.getDamage();
            }
            case "duskmaw" ->
            {
                abSub = spawnSub(head.add(25, 0, 0));
                // no rider can be seated (the hull only takes a real Player), and a hull without one switches its lights off each tick: the test switches them on again every tick, after the hull ticked
                abSub.setEnergy(Integer.MAX_VALUE);
                abSub.setLights(true);
                abFe0 = abSub.getEnergy();
            }
            case "glassback" -> abSub = spawnSub(head.add(40, 0, 0));
            case "crestfin" ->
            {
                abPlayer = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
                abPlayer.setPos(head.x + 30, head.y, head.z);
            }
            default -> { return false; }
        }
        abilityActive = true;
        return true;
    }

    private static void endAbility(MinecraftServer server)
    {
        abilityActive = false;
        if (abSub != null) abSub.discard();
        abSub = null;
        abPlayer = null;
        next(server);
    }

    private static void abilityTick(MinecraftServer server)
    {
        abTicks++;
        String id = queue.get(index);
        // the giants are neutral: they only go for the hull after being hit by it (getEntity() == hull)
        if (abTicks == 1 && abSub != null && java.util.List.of("rift_lamprey", "duskmaw", "glassback").contains(id))
            giant.hurt(new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC), abSub), 1.0F);
        boolean done = abTicks >= 300;
        switch (id)
        {
            case "rift_lamprey" ->
            {
                abA |= giant.holding();
                abB |= abSub.giantSlowed();
                abC |= abSub.getDamage() > abDmg0;
                done |= abA && abB && abC;
                if (done)
                    result("ability_latch", abA && abB && abC, String.format("hold anim %s, hull speed halved %s, hull damage %.1f -> %.1f after %d ticks", abA, abB, abDmg0, abSub.getDamage(), abTicks));
            }
            case "duskmaw" ->
            {
                abSub.setLights(true);
                abSub.setDamage(0);   // the giant's bites would wreck the test hull before the 160-tick grab ends
                abA |= giant.holding();
                abB |= abSub.giantSlowed();
                int spent = abFe0 - abSub.getEnergy();
                abC |= spent > 300 + (long) abTicks * Config.SUBMARINE_LIGHT_FE.get();
                if (abTicks == 1) abPos0 = abSub.position();                double moved = abPos0 == null ? 0 : abSub.position().distanceTo(abPos0);
                boolean abD = moved >= 8;
                done |= abA && abB && abC && abD;
                if (done)
                    result("ability_grab", abA && abB && abC && abD, String.format("grab anim %s, hull speed halved %s, FE spent %d (lights alone ~%d), hull moved %.1f blocks after %d ticks", abA, abB, spent, (long) abTicks * Config.SUBMARINE_LIGHT_FE.get(), moved, abTicks));
            }
            case "glassback" ->
            {
                if (giant.chasing() && !giant.glowOn()) abA = true;
                if (abA && giant.chasing() && giant.glowOn()) abB = true;
                done |= abB;
                if (done) result("ability_glow_switch", abA && abB, "dark while chasing from afar " + abA + ", lit again within 16 " + abB + ", after " + abTicks + " ticks");
            }
            case "crestfin" ->
            {
                if (abTicks == 1) giant.hurt(level.damageSources().playerAttack(abPlayer), 1.0F);
                GiantBrain.State st = giant.brain().state();
                if (st == GiantBrain.State.WARN && giant.holding()) abA = true;
                if (st == GiantBrain.State.CHASE && abFirstChase < 0) abFirstChase = abTicks;
                if (st == GiantBrain.State.CHASE && giant.holding()) abC = true;
                done |= abFirstChase >= 0 && abTicks > abFirstChase + 5;
                if (done)
                    result("ability_threat_charge", abA && abC && abFirstChase >= 55 && abFirstChase <= 70, String.format("crest_up in warning %s, in chase %s, chase began at tick %d (want ~61)", abA, abC, abFirstChase));
            }
            default -> done = true;
        }
        if (done) endAbility(server);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (System.getProperty("abyssia.gianttest") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        try
        {
            if (!started)
            {
                started = true;
                level = server.overworld();
                centre = new BlockPos(CX, level.getMinBuildHeight() + 100, CZ);
                forceBox(true);
                String only = System.getProperty("abyssia.gianttest", "");
                queue = new java.util.ArrayList<>(ModGiants.ids());
                if (queue.contains(only)) queue = new java.util.ArrayList<>(java.util.List.of(only));
                LOG.info("GNT01 start species {}", queue);
            }
            if (!filled)
            {
                if (!chunksReady())
                {
                    if (++waited > 1200) { result("chunks", false, "not ready after " + waited + " ticks"); finish(server); }
                    return;
                }
                LOG.info("GNT01 chunks ready after {} ticks", waited);
                fillWater();
                filled = true;
                return;
            }
            if (abilityActive)
            {
                abilityTick(server);
                return;
            }
            if (giant == null)
            {
                ModGiants.Species sp = ModGiants.byId(queue.get(index));
                startHead = Vec3.atCenterOf(centre);
                giant = GiantSpawner.create(level, sp, startHead, new Vec3(1, 0, 0));
                if (giant == null)
                {
                    result("spawn", false, "GiantSpawner.create returned null (free space check failed)");
                    next(server);
                }
                return;
            }
            if (swum == 0)
            {
                // (a) length, one tick after the spawn so the chain exists
                double len = giant.measuredLength();
                int parts = giant.getParts().length;
                result("length", Math.abs(len - giant.spec().length()) <= 1.0, String.format("measured %.2f of %.0f blocks, parts %d, joints %d", len, giant.spec().length(), parts, giant.chain().size()));
                result("parts_count", parts >= 4 && parts <= 12, "parts " + parts);
                // (b) a hit on a body part lowers the animal's health
                GiantPart part = (GiantPart) giant.getParts()[parts / 2];
                float before = giant.getHealth();
                boolean hit = part.hurt(level.damageSources().generic(), 10.0F);
                float after = giant.getHealth();
                result("part_damage", hit && after < before, String.format("hp %.2f -> %.2f (hit=%s, max %.0f)", before, after, hit, giant.getMaxHealth()));
            }
            // (c) swim, sampling the parts inside solid blocks every 10 ticks
            swum++;
            if (swum % 10 == 0)
            {
                maxSolidParts = Math.max(maxSolidParts, giant.partsInSolid());
                maxSolidJoints = Math.max(maxSolidJoints, giant.jointsInSolid());
            }
            if (swum >= RUN)
            {
                double moved = giant.headPos().distanceTo(startHead);
                result("swim_no_solid", maxSolidParts == 0 && giant.isAlive(),
                        String.format("%d ticks, moved %.1f blocks, max parts in solid %d, max joints in solid %d, alive %s", swum, moved, maxSolidParts, maxSolidJoints, giant.isAlive()));
                if (!startAbility()) next(server);
            }
        }
        catch (Exception e)
        {
            result("exception", false, e.toString());
            LOG.error("GNT01 exception", e);
            finish(server);
        }
    }
}
