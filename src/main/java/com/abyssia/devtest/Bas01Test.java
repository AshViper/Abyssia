package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.habitat.power.HabitatAir;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.habitat.upkeep.HabitatUpkeep;
import com.abyssia.habitat.upkeep.UpkeepContent;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Dev-only (BAS01): with -Dabyssia.bas01test (gradle runBas01Test) the dedicated server builds habitat modules in forced
 * chunks, runs the cases of each config phase in parallel, logs one "BAS01 name PASS|FAIL measured" line each and halts
 * with "BAS01 DONE pass= fail=". Phases: A default config, B wear_multiplier=0 (quakes), C enabled=false,
 * D wear_multiplier=2.0. Every module, record, forced chunk and config value is restored at the end.
 * Integrity values are in 1/1000 %. Modules are 4x5x4 boxes, up to three per chunk (x offsets 1, 6, 11).
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class Bas01Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHUNK_X0 = 760, CHUNK_Z = 8, CHUNK_SPACING = 6, FAR_X = 200_000;
    private static final int FE = 100_000;

    private static final class Case
    {
        final String name;
        final int ticks;
        /** 0 = no FE (powered=false), 1 = FE refilled every tick, 2 = same as 0 (kept for readability of the case table) */
        final int mode;
        final int[] ys, start;
        final boolean[] reinf;
        final boolean far;
        final Function<Case, String> check;
        int chunkX;
        int[] ids, drain, oxy0;
        long t0;
        boolean done;
        String measured = "";

        Case(String name, int ticks, int mode, int[] ys, int[] start, boolean[] reinf, boolean far, Function<Case, String> check)
        {
            this.name = name;
            this.ticks = ticks;
            this.mode = mode;
            this.ys = ys;
            this.start = start;
            this.reinf = reinf;
            this.far = far;
            this.check = check;
        }

        int base(int i)
        {
            return HabitatBases.get(level).root(ids[i]);
        }

        BoundingBox box(int i)
        {
            return HabitatBases.get(level).module(ids[i]).box();
        }

        int integ(int i)
        {
            return HabitatUpkeep.get(level).integrity(ids[i]);
        }
    }

    private static final List<List<Case>> PHASES = new ArrayList<>();
    private static ServerLevel level;
    private static int phase, t, serverTicks, passes, fails, waited;
    private static boolean started, finished, running, prepared;
    private static List<Case> active = List.of();

    private static String expect(Case c, boolean ok, String measured)
    {
        c.measured = measured;
        return ok ? null : "expected different";
    }

    private static int[] a(int... v)
    {
        return v;
    }

    private static boolean[] r(boolean... v)
    {
        return v;
    }

    private static boolean near(int v, int expected, int tol)
    {
        return Math.abs(v - expected) <= tol;
    }

    private static void define()
    {
        int full = HabitatUpkeep.MAX;
        List<Case> p = new ArrayList<>();
        // 1. wear over 2000 ticks: depth multiplier 1.0 / 2.0 / 3.0 / 3.0 reinforced
        p.add(new Case("wear", 2000, 0, a(60, -350, -500, -500), a(full, full, full, full), r(false, false, false, true), false, c ->
        {
            int d0 = full - c.integ(0), d1 = full - c.integ(1), d2 = full - c.integ(2), d3 = full - c.integ(3);
            return expect(c, near(d0, 800, 40) && near(d1, 1600, 40) && near(d2, 2400, 40) && near(d3, 1200, 40),
                    "Y60=-" + d0 + " Y-350=-" + d1 + " Y-500=-" + d2 + " Y-500+reinforced=-" + d3);
        }));
        // 2. not loaded: no change, no extra FE, no leak
        p.add(new Case("unloaded", 2000, 1, a(60), a(50_000), r(false), true, c ->
        {
            var air = HabitatAir.get(level);
            int before = air.oxygen(c.ids[0]);
            HabitatUpkeep.leak(level, c.ids[0], air);
            int extra = HabitatUpkeep.extraFe(level, List.of(HabitatBases.get(level).module(c.ids[0])));
            boolean ticking = level.shouldTickBlocksAt(ChunkPos.asLong(c.box(0).getCenter()));
            return expect(c, c.integ(0) == 50_000 && extra == 0 && air.oxygen(c.ids[0]) == before && c.drain[0] == 20 && !ticking,
                    "integrity=" + c.integ(0) + " extraFe=" + extra + " lastDrain=" + c.drain[0] + " leakDelta=" + (before - air.oxygen(c.ids[0])) + " ticking=" + ticking);
        }));
        // 3a. extra FE: 100 % -> 20, 50 % -> 30, 25 % -> 40 per module
        p.add(new Case("leak_fe", 30, 1, a(60, 60, 60), a(full, 50_000, 25_000), r(false, false, false), false, c ->
                expect(c, c.drain[0] == 20 && c.drain[1] == 30 && c.drain[2] == 40,
                        "drain none=" + c.drain[0] + " small=" + c.drain[1] + " large=" + c.drain[2] + " (extra " + (c.drain[1] - c.drain[0]) + " / " + (c.drain[2] - c.drain[0]) + ")")));
        // 3b. oxygen without power over 200 ticks: small leak +400, large +800 more than no leak
        p.add(new Case("leak_oxygen_unpowered", 200, 0, a(60, 60, 60), a(full, 50_000, 25_000), r(false, false, false), false, c ->
        {
            var air = HabitatAir.get(level);
            int d0 = c.oxy0[0] - air.oxygen(c.ids[0]), d1 = c.oxy0[1] - air.oxygen(c.ids[1]), d2 = c.oxy0[2] - air.oxygen(c.ids[2]);
            return expect(c, near(d1 - d0, 400, 10) && near(d2 - d0, 800, 10), "drop none=" + d0 + " small=" + d1 + " large=" + d2
                    + " (more: " + (d1 - d0) + " / " + (d2 - d0) + ")");
        }));
        // 4. powered with a large leak: the reserve still rises (+10 refill, -4 leak per tick)
        p.add(new Case("leak_powered_refill", 200, 1, a(60), a(25_000), r(false), false, c ->
        {
            int gain = HabitatAir.get(level).oxygen(c.ids[0]) - c.oxy0[0];
            return expect(c, gain > 0 && near(gain, 1200, 40), "oxygen gain over 200 ticks=" + gain + " drain=" + c.drain[0]);
        }));
        // 4b. powered and full: a large leak does not touch the full reserve
        p.add(new Case("leak_full_powered", 200, 1, a(60), a(25_000), r(false), false, c ->
        {
            int oxy = HabitatAir.get(level).oxygen(c.ids[0]);
            return expect(c, c.oxy0[0] == HabitatAir.MAX && oxy == HabitatAir.MAX, "oxygen start=" + c.oxy0[0] + " end=" + oxy);
        }));
        // 4c. a one-block foundation takes neither kit nor plate
        p.add(new Case("kit_on_foundation", 1, 0, a(60), a(50_000), r(false), false, Bas01Test::checkFoundation));
        // 5. kit and reinforcement plate with a sneaking fake player
        p.add(new Case("items", 1, 0, a(60, 60, 60, 60), a(50_000, 90_000, full, full), r(false, false, false, false), false, Bas01Test::checkItems));
        // 6. floor
        p.add(new Case("floor", 4000, 0, a(-500), a(21_000), r(false), false, c ->
                expect(c, c.integ(0) >= 20_000, "integrity=" + c.integ(0))));
        // 9. no record = 100 %
        p.add(new Case("no_record", 1, 0, a(60), a(full), r(false), false, c ->
        {
            var data = HabitatUpkeep.get(level);
            String status = "";
            try
            {
                status = HabitatUpkeep.status(level, c.ids[0]).getString();
                HabitatUpkeep.leak(level, c.ids[0], HabitatAir.get(level));
                HabitatUpkeep.extraFe(level, List.of(HabitatBases.get(level).module(c.ids[0])));
            }
            catch (Exception e)
            {
                return expect(c, false, "exception " + e);
            }
            return expect(c, data.integrity(c.ids[0]) == full && !data.isReinforced(c.ids[0]), "integrity=" + data.integrity(c.ids[0]) + " status='" + status + "'");
        }));
        PHASES.add(p);

        // 7. quakes (wear off so the damage is exact)
        List<Case> q = new ArrayList<>();
        q.add(new Case("quake", 650, 0, a(60, -500, -500, 60), a(full, full, full, 25_000), r(false, false, true, false), false, c ->
        {
            var data = HabitatUpkeep.get(level);
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            int[] expectedDrop = {15_000, 30_000, 15_000, 5_000};
            for (int i = 0; i < 4; i++)
            {
                int drop = c.start[i] - c.integ(i);
                long delay = data.lastQuakeTick(c.base(i)) - c.t0;
                int msgs = data.warnMessages(c.base(i));
                boolean roll = data.canRollQuake(level, c.base(i));
                ok &= near(drop, expectedDrop[i], 1) && near((int) delay, 600, 1) && msgs == 6 && !roll;
                sb.append("[m").append(i).append(" drop=").append(drop).append(" landed=").append(delay).append(" msgs=").append(msgs).append(" canRoll=").append(roll).append("] ");
            }
            return expect(c, ok, sb.toString().trim());
        }));
        PHASES.add(q);

        // 8a. enabled=false
        List<Case> off = new ArrayList<>();
        off.add(new Case("config_disabled", 2000, 1, a(60), a(50_000), r(false), false, c ->
        {
            var data = HabitatUpkeep.get(level);
            int extra = HabitatUpkeep.extraFe(level, List.of(HabitatBases.get(level).module(c.ids[0])));
            boolean forced = data.forceQuake(level, c.base(0), 600);
            return expect(c, c.integ(0) == 50_000 && extra == 0 && c.drain[0] == 20 && !forced,
                    "integrity=" + c.integ(0) + " extraFe=" + extra + " lastDrain=" + c.drain[0] + " forceQuake=" + forced);
        }));
        PHASES.add(off);

        // 8b. wear_multiplier=2.0
        List<Case> x2 = new ArrayList<>();
        x2.add(new Case("wear_x2", 2000, 0, a(60), a(full), r(false), false, c ->
                expect(c, near(full - c.integ(0), 1600, 40), "Y60=-" + (full - c.integ(0)))));
        PHASES.add(x2);
    }

    private static String checkItems(Case c)
    {
        var data = HabitatUpkeep.get(level);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setShiftKeyDown(true);
        ItemStack kit = new ItemStack(UpkeepContent.HULL_PATCH_KIT.get(), 2);
        use(player, kit, c.box(0).getCenter());
        int a0 = c.integ(0), k0 = kit.getCount();
        ItemStack kit2 = new ItemStack(UpkeepContent.HULL_PATCH_KIT.get(), 1);
        use(player, kit2, c.box(1).getCenter());
        int a1 = c.integ(1), k1 = kit2.getCount();
        ItemStack kit3 = new ItemStack(UpkeepContent.HULL_PATCH_KIT.get(), 1);
        use(player, kit3, c.box(2).getCenter());
        int a2 = c.integ(2), k2 = kit3.getCount();
        ItemStack plate = new ItemStack(UpkeepContent.HULL_REINFORCEMENT.get(), 2);
        use(player, plate, c.box(3).getCenter());
        boolean r1 = data.isReinforced(c.ids[3]);
        int p1 = plate.getCount();
        use(player, plate, c.box(3).getCenter());
        int p2 = plate.getCount();
        player.setShiftKeyDown(false);
        boolean ok = a0 == 75_000 && k0 == 1 && a1 == 100_000 && k1 == 0 && a2 == 100_000 && k2 == 1 && r1 && p1 == 1 && p2 == 1;
        return expect(c, ok, "kit 50->" + a0 / 1000 + "% left=" + k0 + "; 90->" + a1 / 1000 + "% left=" + k1 + "; 100->" + a2 / 1000 + "% left=" + k2
                + "; plate reinforced=" + r1 + " left=" + p1 + " second use left=" + p2);
    }

    private static String checkFoundation(Case c)
    {
        var data = HabitatUpkeep.get(level);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setShiftKeyDown(true);
        ItemStack kit = new ItemStack(UpkeepContent.HULL_PATCH_KIT.get(), 2);
        use(player, kit, c.box(0).getCenter());
        ItemStack plate = new ItemStack(UpkeepContent.HULL_REINFORCEMENT.get(), 2);
        use(player, plate, c.box(0).getCenter());
        player.setShiftKeyDown(false);
        boolean ok = c.integ(0) == 50_000 && !data.isReinforced(c.ids[0]) && kit.getCount() == 2 && plate.getCount() == 2;
        return expect(c, ok, "integrity=" + c.integ(0) + " reinforced=" + data.isReinforced(c.ids[0]) + " kit=" + kit.getCount() + " plate=" + plate.getCount());
    }

    private static void use(net.minecraft.server.level.ServerPlayer player, ItemStack stack, BlockPos pos)
    {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event)
    {
        if (System.getProperty("abyssia.bas01test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        if (!started)
        {
            started = true;
            define();
            level = server.overworld();
            LOG.info("BAS01 start");
        }
        if (!running)
        {
            if (!beginPhase(phase)) return;
            running = true;
        }
        t++;
        boolean allDone = true;
        var bases = HabitatBases.get(level);
        for (Case c : active)
        {
            if (c.done) continue;
            for (int i = 0; i < c.ids.length; i++)
            {
                int base = c.base(i);
                if (c.mode == 1)
                {
                    int e = bases.energy(base);
                    if (e < FE) c.drain[i] = FE - e;
                    bases.setEnergy(base, FE);
                }
                else bases.setEnergy(base, 0);
            }
            if (t >= c.ticks) finish(c);
            allDone &= c.done;
        }
        if (allDone)
        {
            running = false;
            prepared = false;
            phase++;
        }
    }

    private static void finish(Case c)
    {
        c.done = true;
        String reason;
        try
        {
            reason = c.check.apply(c);
        }
        catch (Exception e)
        {
            c.measured = "exception " + e;
            reason = "exception";
        }
        boolean ok = reason == null;
        if (ok) passes++;
        else fails++;
        LOG.info("BAS01 {} {} {}", c.name, ok ? "PASS" : "FAIL", c.measured);
        cleanup(c);
    }

    /** modules, upkeep records, oxygen entries and the forced chunk of a case */
    private static void cleanup(Case c)
    {
        if (c.ids != null)
        {
            var data = HabitatUpkeep.get(level);
            var air = HabitatAir.get(level);
            for (int id : c.ids)
            {
                data.forget(id);
                for (int n = 0; n < HabitatAir.MAX / HabitatAir.REFILL + 2 && air.oxygen(id) < HabitatAir.MAX; n++) air.step(id, true);
                HabitatPower.remove(level, id);
            }
        }
        if (!c.far) level.setChunkForced(c.chunkX, CHUNK_Z, false);
    }

    private static void setConfig(int index)
    {
        Config.UPKEEP_ENABLED.set(index != 2);
        Config.UPKEEP_WEAR_MULTIPLIER.set(index == 1 ? 0.0 : index == 3 ? 2.0 : 1.0);
    }

    private static void resetConfig()
    {
        Config.UPKEEP_ENABLED.set(true);
        Config.UPKEEP_WEAR_MULTIPLIER.set(1.0);
        Config.UPKEEP_QUAKES_ENABLED.set(true);
    }

    /** returns false while the chunks are still loading */
    private static boolean beginPhase(int index)
    {
        if (index >= PHASES.size())
        {
            finished = true;
            resetConfig();
            LOG.info("BAS01 DONE pass={} fail={}", passes, fails);
            level.getServer().halt(false);
            return false;
        }
        if (!prepared)
        {
            prepared = true;
            active = PHASES.get(index);
            int k = 0;
            for (Case c : active)
            {
                c.chunkX = CHUNK_X0 + CHUNK_SPACING * (index * 8 + k++);
                if (!c.far) level.setChunkForced(c.chunkX, CHUNK_Z, true);
            }
        }
        for (Case c : active)
        {
            if (c.far) continue;
            BlockPos pos = new BlockPos(c.chunkX * 16 + 2, 70, CHUNK_Z * 16 + 2);
            if (level.getChunkSource().getChunkNow(c.chunkX, CHUNK_Z) == null) return false;
            // the same test LevelChunk#isTicking makes before it runs a ticker
            long cp = ChunkPos.asLong(c.chunkX, CHUNK_Z);
            if (!level.getChunkAt(pos).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                    || !level.areEntitiesLoaded(cp) || !level.shouldTickBlocksAt(cp))
            {
                waited++;
                return false;
            }
        }
        LOG.info("BAS01 phase {} waited {} ticks for block ticking", index, waited);
        waited = 0;
        setConfig(index);
        var bases = HabitatBases.get(level);
        var data = HabitatUpkeep.get(level);
        var air = HabitatAir.get(level);
        for (Case c : active)
        {
            int n = c.ys.length;
            c.ids = new int[n];
            c.drain = new int[n];
            c.oxy0 = new int[n];
            for (int i = 0; i < n; i++)
            {
                int x0 = (c.far ? FAR_X + 1 : c.chunkX * 16 + 1) + 5 * i, z0 = CHUNK_Z * 16 + 4;
                c.ids[i] = bases.add(new BoundingBox(x0, c.ys[i], z0, x0 + 3, c.ys[i] + (c.name.equals("kit_on_foundation") ? 0 : 4), z0 + 3));
                data.forget(c.ids[i]);
                if (c.start[i] < HabitatUpkeep.MAX) data.setIntegrity(c.ids[i], c.start[i]);
                if (c.reinf[i]) data.reinforce(c.ids[i]);
                if (c.name.equals("leak_powered_refill")) air.drain(c.ids[i], 3000);
                c.oxy0[i] = air.oxygen(c.ids[i]);
                bases.setEnergy(c.base(i), c.mode == 1 ? FE : 0);
            }
            c.t0 = level.getGameTime();
            if (c.name.equals("quake"))
                for (int i = 0; i < n; i++)
                    if (!data.canRollQuake(level, c.base(i)) || !data.forceQuake(level, c.base(i), 600))
                        LOG.info("BAS01 quake setup problem: module {} canRoll/force failed", i);
        }
        t = 0;
        return true;
    }
}
