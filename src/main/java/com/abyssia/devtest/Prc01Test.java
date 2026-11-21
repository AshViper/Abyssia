package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.industry.PrecipitatorYields;
import com.abyssia.industry.blockentity.IndustryBlockEntity;
import com.abyssia.industry.blockentity.PrecipitatorBlockEntity;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModIndustry;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Dev-only (PRC01): with -Dabyssia.prc01test (gradle runPrc01Test) the dedicated server places the precipitator cases in
 * forced chunks 100 blocks apart, runs them in parallel in three phases (default config, precipitator_enabled=false,
 * precipitator_speed=2.0), logs one "PRC01 name PASS|FAIL measured" line each and halts.
 * Run lengths are the spec's ticks plus a margin of 10 (the first tick is spent before the block entity ticks).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Prc01Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int BASE_X = 6004, BASE_Z = 4, SPACING = 100, MARGIN = 10;
    private static final Set<String> BLACK = Set.of("raw_cobalt", "raw_nickel", "raw_zinc", "raw_lead");

    private static final class Case
    {
        final String name;
        final int ticks;
        final boolean fe;
        final Function<Case, String> check; // null = PASS, else a FAIL reason; the measured value is always logged
        BlockPos pos;
        BlockPos pos2;
        PrecipitatorBlockEntity be, be2;
        long consumed;
        int lastStored;
        String measured = "";
        boolean done;
        int st1;
        long fe1, fe2;

        Case(String name, int ticks, boolean fe, Function<Case, String> check)
        {
            this.name = name;
            this.ticks = ticks;
            this.fe = fe;
            this.check = check;
        }

        int items(PrecipitatorBlockEntity b)
        {
            int n = 0;
            for (int i = 0; i < b.items().getSlots(); i++) n += b.items().getStackInSlot(i).getCount();
            return n;
        }

        int status(PrecipitatorBlockEntity b)
        {
            return b.data().get(IndustryBlockEntity.DATA_STATUS);
        }
    }

    private record Setup(Case c, ThermalVentType type, VentActivity activity, boolean twoMachines, boolean fullOutput) {}

    private static final List<List<Setup>> PHASES = new ArrayList<>();
    private static ServerLevel level;
    private static int phase, t, serverTicks, passes, fails, next, waited;
    private static boolean started, finished, running, prepared;
    private static List<Setup> active = List.of();

    private static String expect(Case c, boolean ok, String measured)
    {
        c.measured = measured;
        return ok ? null : "expected different";
    }

    private static void define()
    {
        int run = 2400 + MARGIN;
        List<Setup> a = new ArrayList<>();
        a.add(new Setup(new Case("black_smoker_active", run, true, c ->
        {
            int n = c.items(c.be);
            boolean kinds = true;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < c.be.items().getSlots(); i++)
            {
                ItemStack s = c.be.items().getStackInSlot(i);
                if (s.isEmpty()) continue;
                String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                sb.append(p).append('x').append(s.getCount()).append(' ');
                kinds &= BLACK.contains(p);
            }
            boolean ok = n == 6 && kinds && Math.abs(c.consumed - 48_000) <= 400;
            return expect(c, ok, "items=" + n + " fe=" + c.consumed + " [" + sb.toString().trim() + "]");
        }), ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        a.add(new Setup(new Case("superheated_vent", run, true, c ->
        {
            int n = c.items(c.be);
            List<net.minecraft.world.item.Item> set = PrecipitatorYields.superheatedSet(
                    PrecipitatorYields.ventKey(level.getSeed(), c.pos.east()));
            Set<net.minecraft.world.item.Item> kinds = new java.util.HashSet<>();
            boolean inSet = true;
            for (int i = 0; i < c.be.items().getSlots(); i++)
            {
                ItemStack s = c.be.items().getStackInSlot(i);
                if (s.isEmpty()) continue;
                kinds.add(s.getItem());
                inSet &= set.contains(s.getItem());
            }
            return expect(c, n == 12 && inSet, "items=" + n + " kinds=" + kinds.size() + " inSet=" + inSet);
        }), ThermalVentType.SUPERHEATED, VentActivity.SUPERHEATED, false, false));
        a.add(new Setup(new Case("superheated_mk2_share", 1, false, c ->
        {
            RandomSource rng = RandomSource.create(20261121L);
            int mk2 = 0;
            for (int i = 0; i < 1000; i++) if (PrecipitatorYields.isMk2(PrecipitatorYields.roll(ThermalVentType.SUPERHEATED, 12345L, rng))) mk2++;
            return expect(c, Math.abs(mk2 - 200) <= 30, "mk2=" + mk2 + "/1000");
        }), null, null, false, false));
        a.add(new Setup(new Case("superheated_sets", 1, false, c ->
        {
            java.util.Set<net.minecraft.world.item.Item> seenMk1 = new java.util.HashSet<>(), seenMk2 = new java.util.HashSet<>();
            java.util.Set<java.util.Set<net.minecraft.world.item.Item>> distinct = new java.util.HashSet<>();
            boolean ok = true;
            for (int i = 0; i < 200; i++)
            {
                long key = PrecipitatorYields.ventKey(20261121L, new BlockPos(16 * i, 40, 7));
                List<net.minecraft.world.item.Item> set = PrecipitatorYields.superheatedSet(key);
                ok &= set.size() == 6 && new java.util.HashSet<>(set).size() == 6;
                ok &= set.equals(PrecipitatorYields.superheatedSet(key));
                for (int k = 0; k < set.size(); k++)
                {
                    boolean mk2 = PrecipitatorYields.isMk2(new ItemStack(set.get(k)));
                    ok &= mk2 == (k >= 4);
                    (mk2 ? seenMk2 : seenMk1).add(set.get(k));
                }
                distinct.add(new java.util.HashSet<>(set));
            }
            ok &= seenMk1.size() == 8 && seenMk2.size() == 8 && distinct.size() >= 20;
            return expect(c, ok, "mk1=" + seenMk1.size() + " mk2=" + seenMk2.size() + " distinct=" + distinct.size());
        }), null, null, false, false));
        a.add(new Setup(new Case("research_gate", 1, false, c ->
        {
            // a survival player without the technology cannot place the machine; after the unlock he can
            net.minecraft.server.level.ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("prc01".getBytes()), "prc01"));
            ResourceLocation tech = new ResourceLocation(Abyssia.MODID, "technology/hydrothermal_precipitation");
            com.abyssia.research.ResearchManager.reset(p, null);
            net.minecraft.world.level.block.Block block = ModIndustry.HYDROTHERMAL_PRECIPITATOR.get();
            level.setBlock(c.pos, block.defaultBlockState(), 3);
            String lockedBy = com.abyssia.research.gate.MachineGate.lockedBy(p, block).map(x -> x.id().toString()).orElse("none");
            boolean cancelledBefore = placeCancelled(c.pos, p);
            boolean unlocked = com.abyssia.research.ResearchManager.unlock(p, tech);
            boolean freeAfter = com.abyssia.research.gate.MachineGate.lockedBy(p, block).isEmpty();
            boolean cancelledAfter = placeCancelled(c.pos, p);
            com.abyssia.research.ResearchManager.reset(p, null);
            return expect(c, lockedBy.equals(tech.toString()) && cancelledBefore && unlocked && freeAfter && !cancelledAfter,
                    "lockedBy=" + lockedBy + " placeCancelled=" + cancelledBefore + " unlock=" + unlocked
                            + " freeAfter=" + freeAfter + " placeCancelledAfter=" + cancelledAfter);
        }), null, null, false, false));
        a.add(new Setup(new Case("weak_800", 800 + MARGIN, true, c ->
                expect(c, c.items(c.be) == 1, "items=" + c.items(c.be))), ThermalVentType.BLACK_SMOKER, VentActivity.WEAK, false, false));
        a.add(new Setup(new Case("strong_267", 267 + MARGIN, true, c ->
                expect(c, c.items(c.be) == 1, "items=" + c.items(c.be))), ThermalVentType.BLACK_SMOKER, VentActivity.STRONG, false, false));
        a.add(new Setup(new Case("dormant", run, true, c ->
                expect(c, c.items(c.be) == 0 && c.status(c.be) == 2, "items=" + c.items(c.be) + " status=" + c.status(c.be))),
                ThermalVentType.BLACK_SMOKER, VentActivity.DORMANT, false, false));
        a.add(new Setup(new Case("no_vent", run, true, c ->
                expect(c, c.items(c.be) == 0 && c.status(c.be) == 1, "items=" + c.items(c.be) + " status=" + c.status(c.be))),
                null, null, false, false));
        a.add(new Setup(new Case("two_on_one_vent", run, true, c ->
        {
            int n = c.items(c.be) + c.items(c.be2);
            int s1 = c.status(c.be), s2 = c.status(c.be2);
            return expect(c, n == 6 && (s1 == 3 || s2 == 3), "items=" + n + " status=" + s1 + "/" + s2);
        }), ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, true, false));
        a.add(new Setup(new Case("no_power", 500, false, c ->
                expect(c, c.items(c.be) == 0 && c.status(c.be) == 5, "items=" + c.items(c.be) + " status=" + c.status(c.be))),
                ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        a.add(new Setup(new Case("black_smoker_mix", 1, false, c ->
        {
            RandomSource rng = RandomSource.create(20261121L);
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (int i = 0; i < 1000; i++)
                counts.merge(BuiltInRegistries.ITEM.getKey(PrecipitatorYields.roll(ThermalVentType.BLACK_SMOKER, 0L, rng).getItem()).getPath(), 1, Integer::sum);
            boolean ok = counts.size() == 4;
            ok &= Math.abs(counts.getOrDefault("raw_cobalt", 0) - 300) <= 40;
            ok &= Math.abs(counts.getOrDefault("raw_nickel", 0) - 300) <= 40;
            ok &= Math.abs(counts.getOrDefault("raw_zinc", 0) - 200) <= 40;
            ok &= Math.abs(counts.getOrDefault("raw_lead", 0) - 200) <= 40;
            return expect(c, ok, counts.toString());
        }), null, null, false, false));
        a.add(new Setup(new Case("output_full", 2400 + MARGIN, true, c ->
                expect(c, c.items(c.be) == 9 * 64 && c.status(c.be) == 4 && c.consumed == 0,
                        "items=" + c.items(c.be) + " status=" + c.status(c.be) + " fe=" + c.consumed)),
                ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, true));
        a.add(new Setup(new Case("pending_dropped", 440, true, c ->
        {
            // breaking the machine while a metal waits for room: 9 stone and the held metal drop, nothing is lost
            int st = c.status(c.be);
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(c.pos).inflate(3);
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) e.discard();
            c.be.dropContents(level, c.pos);
            int stone = 0, metal = 0;
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box))
            {
                if (e.getItem().is(net.minecraft.world.item.Items.STONE)) stone += e.getItem().getCount();
                else if (BLACK.contains(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath())) metal += e.getItem().getCount();
                e.discard();
            }
            c.be.dropContents(level, c.pos); // a second call must drop nothing
            int again = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size();
            return expect(c, st == 4 && stone == 9 && metal == 1 && again == 0,
                    "status=" + st + " stone=" + stone + " metal=" + metal + " second_drop=" + again);
        }), ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        a.add(new Setup(new Case("pending_kept", 470, true, c ->
        {
            int freed = 0;
            for (int i = 0; i < 9; i++) if (!c.be.items().getStackInSlot(i).isEmpty() && BLACK.contains(
                    BuiltInRegistries.ITEM.getKey(c.be.items().getStackInSlot(i).getItem()).getPath())) freed += c.be.items().getStackInSlot(i).getCount();
            boolean ok = c.st1 == 4 && c.fe1 == c.fe2 && c.fe1 > 0 && c.status(c.be) == 7 && freed == 1 && c.items(c.be) == 9;
            return expect(c, ok, "status_stopped=" + c.st1 + " fe(430)=" + c.fe1 + " fe(450)=" + c.fe2 + " status_after=" + c.status(c.be)
                    + " metal=" + freed + " items=" + c.items(c.be));
        }), ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        a.add(new Setup(new Case("save_load_progress", 200, true, c ->
        {
            int before = c.be.data().get(IndustryBlockEntity.DATA_PROGRESS);
            CompoundTag tag = c.be.saveWithoutMetadata();
            PrecipitatorBlockEntity copy = new PrecipitatorBlockEntity(c.pos, c.be.getBlockState());
            copy.load(tag);
            int after = copy.data().get(IndustryBlockEntity.DATA_PROGRESS);
            return expect(c, before > 0 && before == after, "progress " + before + " -> " + after);
        }), ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        PHASES.add(a);

        List<Setup> b = new ArrayList<>();
        b.add(new Setup(new Case("config_disabled", run, true, c ->
                expect(c, c.items(c.be) == 0 && c.status(c.be) == 6, "items=" + c.items(c.be) + " status=" + c.status(c.be))),
                ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        PHASES.add(b);

        List<Setup> s = new ArrayList<>();
        s.add(new Setup(new Case("speed_2_0", run, true, c ->
                expect(c, c.items(c.be) == 12, "items=" + c.items(c.be) + " fe=" + c.consumed)), ThermalVentType.BLACK_SMOKER, VentActivity.ACTIVE, false, false));
        PHASES.add(s);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || System.getProperty("abyssia.prc01test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        if (!started)
        {
            started = true;
            define();
            level = server.overworld();
            LOG.info("PRC01 start");
        }
        if (!running)
        {
            if (!beginPhase(phase)) return;
            running = true;
        }
        t++;
        boolean allDone = true;
        for (Setup s : active)
        {
            Case c = s.c;
            if (c.be != null) IBE.feed(c);
            if (c.name.equals("pending_kept") && !c.done)
            {
                if (t == 430) c.fe1 = c.consumed;
                if (t == 450)
                {
                    c.fe2 = c.consumed;
                    c.st1 = c.status(c.be);
                    c.be.items().setStackInSlot(0, ItemStack.EMPTY); // free one slot: the held metal must go in
                }
            }
            if (!c.done && t >= c.ticks) finish(c);
            allDone &= c.done;
        }
        if (allDone)
        {
            running = false;
            prepared = false;
            phase++;
        }
    }

    /** energy accounting and refill (256 FE/t is the machine's receive limit, it needs 20) */
    private static final class IBE
    {
        static void feed(Case c)
        {
            if (c.done || !c.fe) return;
            int stored = energyOf(c.be).getEnergyStored();
            if (c.be2 != null) stored += energyOf(c.be2).getEnergyStored();
            c.consumed += Math.max(0, c.lastStored - stored);
            energyOf(c.be).receiveEnergy(256, false);
            if (c.be2 != null) energyOf(c.be2).receiveEnergy(256, false);
            c.lastStored = energyOf(c.be).getEnergyStored() + (c.be2 != null ? energyOf(c.be2).getEnergyStored() : 0);
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
        LOG.info("PRC01 {} {} {}", c.name, ok ? "PASS" : "FAIL", c.measured);
        if (c.pos != null)
        {
            clear(c.pos);
            level.setChunkForced(c.pos.getX() >> 4, c.pos.getZ() >> 4, false);
        }
    }

    private static net.minecraftforge.energy.IEnergyStorage energyOf(IndustryBlockEntity be)
    {
        return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, null).orElseThrow(IllegalStateException::new);
    }

    /** posts the place event MachineGate listens to, for the block already standing at pos */
    private static boolean placeCancelled(BlockPos pos, net.minecraft.server.level.ServerPlayer player)
    {
        var event = new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(
                net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, pos),
                level.getBlockState(pos.below()), player);
        return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    }

    /** removes the machine, the vent and the second machine of a case (block entities go with them) */
    private static void clear(BlockPos pos)
    {
        for (int i = 0; i < 3; i++)
        {
            BlockPos p = pos.east(i);
            if (level.getBlockEntity(p) instanceof PrecipitatorBlockEntity be)
                for (int s = 0; s < be.items().getSlots(); s++) be.items().setStackInSlot(s, ItemStack.EMPTY);
            level.removeBlock(p, false);
        }
    }

    /** returns false while the chunks are still loading */
    private static boolean beginPhase(int index)
    {
        if (index >= PHASES.size())
        {
            finished = true;
            Config.PRECIPITATOR_ENABLED.set(true);
            Config.PRECIPITATOR_SPEED.set(1.0);
            LOG.info("PRC01 DONE pass={} fail={}", passes, fails);
            level.getServer().halt(false);
            return false;
        }
        if (!prepared)
        {
            prepared = true;
            active = PHASES.get(index);
            for (Setup s : active)
            {
                s.c.pos = new BlockPos(BASE_X + SPACING * next++, Math.max(70, level.getMinBuildHeight() + 10), BASE_Z);
                level.setChunkForced(s.c.pos.getX() >> 4, s.c.pos.getZ() >> 4, true);
            }
        }
        for (Setup s : active)
            if (level.getChunkSource().getChunkNow(s.c.pos.getX() >> 4, s.c.pos.getZ() >> 4) == null) return false;
        // a loaded chunk does not tick its block entities until its neighbours are in: starting the clock before
        // that loses the first ticks of the run
        for (Setup s : active)
        {
            // the same test LevelChunk#isTicking makes before it runs a block entity ticker (the ticket range alone is
            // true tens of ticks earlier when the server is busy loading)
            long cp = net.minecraft.world.level.ChunkPos.asLong(s.c.pos);
            if (!level.getChunkAt(s.c.pos).getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.BLOCK_TICKING)
                    || !level.areEntitiesLoaded(cp) || !level.shouldTickBlocksAt(cp))
            {
                waited++;
                return false;
            }
        }
        LOG.info("PRC01 phase {} waited {} ticks for block ticking", index, waited);
        waited = 0;
        Config.PRECIPITATOR_ENABLED.set(index != 1);
        Config.PRECIPITATOR_SPEED.set(index == 2 ? 2.0 : 1.0);
        BlockState machine = ModIndustry.HYDROTHERMAL_PRECIPITATOR.get().defaultBlockState();
        for (Setup s : active)
        {
            Case c = s.c;
            if (s.type == null && !c.name.equals("no_vent")) continue; // pure-calculation case: no blocks
            clear(c.pos); // run/ keeps its world: a machine left by an earlier run would keep its items and progress
            level.setBlock(c.pos, machine, 3);
            if (s.type != null)
            {
                BlockState vent = ModBlocks.THERMAL_VENT.get().defaultBlockState()
                        .setValue(ThermalVentBlock.TYPE, s.type).setValue(ThermalVentBlock.ACTIVITY, s.activity);
                level.setBlock(c.pos.east(), vent, 3);
                if (s.twoMachines)
                {
                    c.pos2 = c.pos.east(2);
                    level.setBlock(c.pos2, machine, 3);
                    c.be2 = (PrecipitatorBlockEntity) level.getBlockEntity(c.pos2);
                }
            }
            c.be = (PrecipitatorBlockEntity) level.getBlockEntity(c.pos);
            if (s.fullOutput)
            {
                ItemStack cobalt = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(Abyssia.MODID, "raw_cobalt")), 64);
                for (int i = 0; i < 9; i++) c.be.items().setStackInSlot(i, cobalt.copy());
            }
            if (c.name.startsWith("pending_")) // not in the yield table: nothing can merge, every slot is taken
                for (int i = 0; i < 9; i++) c.be.items().setStackInSlot(i, new ItemStack(net.minecraft.world.item.Items.STONE));
        }
        t = 0;
        return true;
    }
}
