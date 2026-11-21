package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.fauna.FaunaSpawner;
import com.abyssia.fauna.StructureFauna;
import com.abyssia.quest.SupplyContracts;
import com.abyssia.registry.ModIndustry;
import com.abyssia.waypoint.BeaconTravel;
import com.abyssia.waypoint.WaypointBeaconBlockEntity;
import com.abyssia.waypoint.WaypointRegistry;
import com.abyssia.worldgen.structure.SeabedStructures;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only (QOL01): with -Dabyssia.qol01test (gradle runQol01Test) the dedicated server checks the four QOL01 features
 * (A dish buffs, B beacon travel, C supply contracts, D structure fauna), logs one "QOL01 name PASS|FAIL measured" line
 * each and halts ("QOL01 DONE pass= fail="). Placed blocks, forced chunks, test beacons in the registry and Config
 * values are put back at the end. Every case catches its own exception and fails alone.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class Qol01Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int BEACON_Y = 120;
    private static final BlockPos BEACON_A = new BlockPos(7004, BEACON_Y, 4);
    private static final BlockPos BEACON_B = new BlockPos(8004, BEACON_Y, 4);
    private static final BlockPos BEACON_C = new BlockPos(7004, BEACON_Y, 64);

    private static final String[] DISHES = {
            "fish_mushroom_skewer", "gourd_fish_skewer", "kelp_fish_skewer", "mushroom_stew", "gourd_soup", "kelp_soup",
            "fish_soup", "mushroom_fish_stew", "gourd_fish_stew", "kelp_fish_stew", "mushroom_pie", "gourd_pie", "fish_pie",
            "mushroom_fish_pie", "gourd_fish_pie", "kelp_fish_pie", "preserved_fish", "smoked_mushroom", "smoked_gourd",
            "grilled_kelp", "mushroom_fish_grill", "gourd_fish_grill", "kelp_fish_grill", "jellyfish_skewer", "jellyfish_stew",
            "abyssal_survival_ration", "thermal_ration", "mushroom_salad", "gourd_kelp_salad", "abyssal_vegetable_stew"};

    private static int ticks, passes, fails;
    private static boolean started, finished, stage1Done;
    private static int stage1Tick;
    private static ServerLevel level;
    private static final Set<Long> FORCED = new HashSet<>();
    private static final Map<BlockPos, BlockState> OLD = new LinkedHashMap<>();
    private static int oldCooldown;
    private static double oldBoost, oldShare;
    // D
    private static BlockPos dStructurePos, dEmptyPos;
    private static String dWorld = "?";
    // B stage 2
    private static ServerPlayer fpB;

    private Qol01Test() {}

    private static void report(String name, boolean ok, String measured)
    {
        if (ok) passes++; else fails++;
        LOG.info("QOL01 {} {} {}", name, ok ? "PASS" : "FAIL", measured);
    }

    private interface Body { void run() throws Exception; }

    private static void guard(String name, Body body)
    {
        try
        {
            body.run();
        }
        catch (Throwable t)
        {
            report(name, false, "exception " + t);
            LOG.error("QOL01 " + name + " exception", t);
        }
    }

    private static ServerPlayer fake(String name)
    {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(("qol01" + name).getBytes()), "qol01" + name));
    }

    private static void force(BlockPos pos, int radius)
    {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
            {
                int cx = (pos.getX() >> 4) + dx, cz = (pos.getZ() >> 4) + dz;
                if (FORCED.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) level.setChunkForced(cx, cz, true);
            }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event)
    {
        if (System.getProperty("abyssia.qol01test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++ticks < 100) return;
        if (!started)
        {
            started = true;
            level = server.overworld();
            oldCooldown = Config.QOL01_TRAVEL_COOLDOWN.get();
            oldBoost = Config.QOL01_STRUCTURE_FAUNA_BOOST.get();
            oldShare = Config.FAUNA_FOOD_FISH_SHARE.get();
            LOG.info("QOL01 start");
            guard("setup", Qol01Test::setup);
            stage1Tick = ticks + 60;
            return;
        }
        if (!stage1Done)
        {
            if (ticks < stage1Tick) return;
            stage1Done = true;
            guard("A", Qol01Test::testA);
            guard("B1", Qol01Test::testB1);
            guard("C", Qol01Test::testC);
            guard("D", Qol01Test::testD);
            stage1Tick = ticks + 25;       // B2: the 20 tick cooldown has passed
            return;
        }
        if (ticks < stage1Tick) return;
        guard("B2", Qol01Test::testB2);
        guard("cleanup", Qol01Test::cleanup);
        finished = true;
        LOG.info("QOL01 DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }

    // ---------- setup / cleanup ----------

    private static void setup()
    {
        force(BEACON_A, 0);
        force(BEACON_B, 0);
        force(BEACON_C, 0);
        // D: the world kind decides what can be checked
        SeabedStructures structures = SeabedStructures.of(level);
        dWorld = structures == null ? "no_seabed_structures(generator=" + level.getChunkSource().getGenerator().getClass().getSimpleName() + ")" : "ocean_generator";
        if (structures == null)
        {
            dStructurePos = new BlockPos(0, 40, 0);
            force(dStructurePos, 1);
            return;
        }
        for (SeabedStructures.Slot slot : structures.slots())
        {
            if (slot.definition().preferredMobs().isEmpty()) continue;
            SeabedStructures.Candidate c = structures.locate(slot.id(), 0, 0, 4000);
            if (c == null) continue;
            int floor = level.getChunk(c.x() >> 4, c.z() >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, c.x() & 15, c.z() & 15);
            dStructurePos = new BlockPos(c.x(), floor + 5, c.z());
            force(dStructurePos, 1);
            LOG.info("QOL01 D structure {} at {} preferred {}", slot.id(), dStructurePos, slot.definition().preferredMobs());
            break;
        }
        // a spot with no structure around
        for (int i = 1; i < 60 && dStructurePos != null && dEmptyPos == null; i++)
        {
            int x = dStructurePos.getX() + 160 * i, z = dStructurePos.getZ() + 48 * i;
            if (!StructureFauna.preferredAt(level, new BlockPos(x, 0, z)).isEmpty()) continue;
            int floor = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
            dEmptyPos = new BlockPos(x, floor + 5, z);
            force(dEmptyPos, 1);
        }
    }

    private static void cleanup()
    {
        for (BlockPos p : OLD.keySet()) level.removeBlockEntity(p);
        // restore in reverse so the beacons go first (their removal drops them from the registry)
        List<BlockPos> order = new ArrayList<>(OLD.keySet());
        java.util.Collections.reverse(order);
        for (BlockPos p : order) level.setBlock(p, OLD.get(p), 3);
        for (BlockPos p : new BlockPos[] {BEACON_A, BEACON_B, BEACON_C}) WaypointRegistry.remove(level, p);
        for (long l : FORCED) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(l), net.minecraft.world.level.ChunkPos.getZ(l), false);
        Config.QOL01_TRAVEL_COOLDOWN.set(oldCooldown);
        Config.QOL01_STRUCTURE_FAUNA_BOOST.set(oldBoost);
        Config.FAUNA_FOOD_FISH_SHARE.set(oldShare);
        for (String n : new String[] {"a", "b", "c", "c2", "d"})
        {
            ServerPlayer p = fake(n);
            p.getInventory().clearContent();
            p.removeAllEffects();
            p.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        }
    }

    // ---------- A: dishes ----------

    private static String effectName(MobEffectInstance e)
    {
        return BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).getPath();
    }

    private static void testA()
    {
        ServerPlayer p = fake("a");
        Map<String, Map<String, Integer>> seen = new LinkedHashMap<>();
        for (String dish : DISHES)
        {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, dish));
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int i = 0; i < 20; i++)
            {
                p.removeAllEffects();
                new ItemStack(item).finishUsingItem(level, p);
                for (MobEffectInstance e : p.getActiveEffects())
                    counts.merge(effectName(e) + "|" + e.getAmplifier() + "|" + e.getDuration(), 1, Integer::sum);
            }
            seen.put(dish, counts);
        }
        p.removeAllEffects();
        int ok = 0;
        Map<String, List<String>> confirmed = new HashMap<>();
        for (String dish : DISHES)
        {
            List<String> c = new ArrayList<>();
            for (Map.Entry<String, Integer> e : seen.get(dish).entrySet()) if (e.getValue() == 20) c.add(e.getKey());
            confirmed.put(dish, c);
            if (!c.isEmpty()) ok++;
            StringBuilder sb = new StringBuilder();
            for (String s : c)
            {
                String[] f = s.split("\\|");
                sb.append(f[0]).append(f[1].equals("0") ? "" : " amp" + f[1]).append(' ').append(Integer.parseInt(f[2]) / 20).append("s; ");
            }
            LOG.info("QOL01 A_table {} -> {}", dish, sb.length() == 0 ? "(none)" : sb.toString());
        }
        report("A_all_dishes_guaranteed", ok == DISHES.length, ok + "/" + DISHES.length + " dishes with a 20/20 effect");
        checkDur("A_mushroom_pie", confirmed, "mushroom_pie", "deep_sight", 0, 3600);
        checkDur("A_gourd_soup", confirmed, "gourd_soup", "water_breathing", 0, 1200);
        checkDur("A_kelp_soup", confirmed, "kelp_soup", "abyssal_current", 0, 2400);
        checkDur("A_fish_pie", confirmed, "fish_pie", "haste", 0, 3600);
        checkDur("A_thermal_ration", confirmed, "thermal_ration", "fire_resistance", 0, 4800);
        int veg = 0;
        for (String s : confirmed.get("abyssal_vegetable_stew")) veg++;
        report("A_abyssal_vegetable_stew_3_effects", veg == 3, confirmed.get("abyssal_vegetable_stew").toString());
        // EN01 (unchanged): effect, amplifier, ticks
        checkDur("A_en01_fish_soup", confirmed, "fish_soup", "abyssal_current", 0, 3600);
        checkDur("A_en01_kelp_fish_stew", confirmed, "kelp_fish_stew", "deep_sight", 0, 3000);
        checkDur("A_en01_mushroom_fish_grill", confirmed, "mushroom_fish_grill", "abyssal_current", 1, 1200);
        checkDur("A_en01_jellyfish_skewer", confirmed, "jellyfish_skewer", "deep_sight", 0, 2400);
        checkDur("A_en01_jellyfish_stew", confirmed, "jellyfish_stew", "deep_sight", 0, 2400);
        checkDur("A_en01_survival_ration", confirmed, "abyssal_survival_ration", "deep_sight", 0, 1800);
    }

    private static void checkDur(String name, Map<String, List<String>> confirmed, String dish, String effect, int amp, int ticks)
    {
        boolean ok = false;
        for (String s : confirmed.get(dish))
        {
            String[] f = s.split("\\|");
            if (f[0].equals(effect) && Integer.parseInt(f[1]) == amp && Math.abs(Integer.parseInt(f[2]) - ticks) <= 20) ok = true;
        }
        report(name, ok, dish + " " + confirmed.get(dish));
    }

    // ---------- B: beacon travel ----------

    private static WaypointBeaconBlockEntity place(BlockPos pos, UUID owner, int energy)
    {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        for (BlockPos p : new BlockPos[] {pos, pos.above(), pos.above(2)}) OLD.putIfAbsent(p, level.getBlockState(p));
        level.setBlock(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.above(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModIndustry.WAYPOINT_BEACON.get().defaultBlockState(), 3);
        WaypointBeaconBlockEntity be = (WaypointBeaconBlockEntity) level.getBlockEntity(pos);
        be.setOwner(owner);
        be.energy().setEnergy(energy);
        WaypointRegistry.refresh(level, pos);
        return be;
    }

    private static void stand(ServerPlayer p, BlockPos pos, double dx)
    {
        p.setPos(pos.getX() + 0.5 + dx, pos.getY() + 1, pos.getZ() + 0.5);
    }

    private static void clearCooldown(ServerPlayer p)
    {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.remove(BeaconTravel.TAG);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void expectResult(String name, BeaconTravel.Result got, BeaconTravel.Result want, String extra)
    {
        report(name, got == want, got + " (want " + want + ") " + extra);
    }

    private static void testB1()
    {
        fpB = fake("b");
        clearCooldown(fpB);
        Config.QOL01_TRAVEL_COOLDOWN.set(20);
        WaypointBeaconBlockEntity a = place(BEACON_A, fpB.getUUID(), 0);
        WaypointBeaconBlockEntity b = place(BEACON_B, fpB.getUUID(), 1000);
        place(BEACON_C, UUID.nameUUIDFromBytes("someone else".getBytes()), 20000);
        double dist = Math.sqrt(BEACON_A.distSqr(new BlockPos(BEACON_B.getX(), BEACON_A.getY(), BEACON_B.getZ())));
        stand(fpB, BEACON_A, 0);
        double x0 = fpB.getX();
        expectResult("B_no_energy", BeaconTravel.travel(fpB, BEACON_A, BEACON_B), BeaconTravel.Result.NO_ENERGY, "pos unchanged=" + (fpB.getX() == x0) + " dist=" + dist);
        report("B_no_energy_pos_unchanged", fpB.getX() == x0, "x=" + fpB.getX());
        a.energy().setEnergy(20000);
        BeaconTravel.Result r = BeaconTravel.travel(fpB, BEACON_A, BEACON_B);
        double d = fpB.position().distanceTo(new net.minecraft.world.phys.Vec3(BEACON_B.getX() + 0.5, BEACON_B.getY() + 1, BEACON_B.getZ() + 0.5));
        expectResult("B_ok", r, BeaconTravel.Result.OK, "dist_to_dest=" + d);
        net.minecraft.world.phys.Vec3 land = BeaconTravel.landing(level, BEACON_B);
        double ld = land == null ? -1 : land.distanceTo(new net.minecraft.world.phys.Vec3(BEACON_B.getX() + 0.5, BEACON_B.getY() + 1, BEACON_B.getZ() + 0.5));
        net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        level.setBlock(BEACON_B.above(), stone, 3);
        net.minecraft.world.phys.Vec3 side = BeaconTravel.landing(level, BEACON_B);
        double sd = side == null ? -1 : side.distanceTo(new net.minecraft.world.phys.Vec3(BEACON_B.getX() + 0.5, BEACON_B.getY() + 1, BEACON_B.getZ() + 0.5));
        boolean sideOk = side != null && Math.abs(sd - 1.0) < 1e-6;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) level.setBlock(BEACON_B.relative(dir).above(), stone, 3);
        boolean allBlocked = BeaconTravel.landing(level, BEACON_B) == null;
        level.setBlock(BEACON_B.above(), air, 3);
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) level.setBlock(BEACON_B.relative(dir).above(), air, 3);
        report("B_landing", land != null && ld <= 1.5 && r == BeaconTravel.Result.OK && sideOk && allBlocked,
                "landing_dist=" + ld + " side_dist=" + sd + " all_blocked=" + allBlocked + "; position check skipped: FakePlayer ignores teleports (player dist=" + d + ")");
        report("B_ok_energy_left", a.energy().getEnergyStored() == 14000, "departure FE=" + a.energy().getEnergyStored() + " cost=" + BeaconTravel.cost(BEACON_A, BEACON_B));
        stand(fpB, BEACON_A, 0);
        expectResult("B_cooldown", BeaconTravel.travel(fpB, BEACON_A, BEACON_B), BeaconTravel.Result.COOLDOWN, "");
        // not mine
        clearCooldown(fpB);
        expectResult("B_not_owner", BeaconTravel.travel(fpB, BEACON_A, BEACON_C), BeaconTravel.Result.NOT_OWNER, "");
        // too far from the departure
        stand(fpB, BEACON_A, 10);
        expectResult("B_too_far", BeaconTravel.travel(fpB, BEACON_A, BEACON_B), BeaconTravel.Result.TOO_FAR, "10 blocks away");
        // destination without energy
        stand(fpB, BEACON_A, 0);
        a.energy().setEnergy(20000);
        b.energy().setEnergy(0);
        expectResult("B_dest_unpowered", BeaconTravel.travel(fpB, BEACON_A, BEACON_B), BeaconTravel.Result.DEST_UNPOWERED, "");
        // capability and NBT
        a.energy().setEnergy(0);
        IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, BEACON_A, null);
        int got = cap == null ? -1 : cap.receiveEnergy(5000, false);
        report("B_capability", cap != null && got == 2000, "cap=" + (cap != null) + " receiveEnergy(5000)=" + got);
        CompoundTag tag = a.saveWithoutMetadata(level.registryAccess());
        WaypointBeaconBlockEntity back = new WaypointBeaconBlockEntity(BEACON_A, level.getBlockState(BEACON_A));
        back.loadWithComponents(tag, level.registryAccess());
        CompoundTag old = tag.copy();
        old.remove("Energy");
        WaypointBeaconBlockEntity legacy = new WaypointBeaconBlockEntity(BEACON_A, level.getBlockState(BEACON_A));
        legacy.loadWithComponents(old, level.registryAccess());
        report("B_nbt_roundtrip", back.energy().getEnergyStored() == 2000 && legacy.energy().getEnergyStored() == 0,
                "saved=" + a.energy().getEnergyStored() + " loaded=" + back.energy().getEnergyStored() + " legacy=" + legacy.energy().getEnergyStored());
        // set up the second trip: powered again
        a.energy().setEnergy(20000);
        b.energy().setEnergy(1000);
        clearCooldownKeep();
    }

    /** the first trip's cooldown was cleared for the NOT_OWNER case; make a fresh one so B2 shows "after the wait -> OK" */
    private static void clearCooldownKeep()
    {
        stand(fpB, BEACON_A, 0);
        BeaconTravel.Result r = BeaconTravel.travel(fpB, BEACON_A, BEACON_B);          // starts a 20 tick cooldown
        stand(fpB, BEACON_A, 0);
        report("B_cooldown_started", r == BeaconTravel.Result.OK, r.toString());
        report("B_cooldown_blocks", BeaconTravel.travel(fpB, BEACON_A, BEACON_B) == BeaconTravel.Result.COOLDOWN, "immediately after");
    }

    private static void testB2()
    {
        stand(fpB, BEACON_A, 0);
        WaypointBeaconBlockEntity a = (WaypointBeaconBlockEntity) level.getBlockEntity(BEACON_A);
        a.energy().setEnergy(20000);
        expectResult("B_after_cooldown_ok", BeaconTravel.travel(fpB, BEACON_A, BEACON_B), BeaconTravel.Result.OK, "25 ticks later");
    }

    // ---------- C: supply contracts ----------

    private static int countOf(ServerPlayer p, String id)
    {
        return SupplyContracts.count(p, SupplyContracts.itemOf(id));
    }

    private static void testC()
    {
        int resolved = 0;
        StringBuilder bad = new StringBuilder();
        for (SupplyContracts.Contract c : SupplyContracts.POOL)
            for (String id : new String[] {c.item(), c.reward()})
            {
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, id);
                boolean ok = BuiltInRegistries.ITEM.containsKey(rl) && BuiltInRegistries.ITEM.get(rl) != Items.AIR;
                if (ok) resolved++; else bad.append(id).append(' ');
            }
        report("C_pool_ids", resolved == SupplyContracts.POOL.size() * 2 && bad.length() == 0, resolved + "/" + SupplyContracts.POOL.size() * 2 + " missing: " + bad);
        UUID u = UUID.nameUUIDFromBytes("qol01 contract".getBytes());
        report("C_same_index", SupplyContracts.indexOf(100, u) == SupplyContracts.indexOf(100, u), "index=" + SupplyContracts.indexOf(100, u));
        Set<Integer> kinds = new HashSet<>();
        for (long d = 0; d < 30; d++) kinds.add(SupplyContracts.indexOf(d, u));
        report("C_variety_30_days", kinds.size() >= 6, kinds.size() + " kinds of " + SupplyContracts.POOL.size());

        ServerPlayer p = fake("c");
        p.getInventory().clearContent();
        p.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        long day = SupplyContracts.dayOf(level.getGameTime());
        SupplyContracts.Contract c = SupplyContracts.of(day, p.getUUID());
        Item want = SupplyContracts.itemOf(c.item());
        p.getInventory().add(new ItemStack(want, c.count() - 1));
        int before = countOf(p, c.item());
        int xp0 = p.totalExperience;
        SupplyContracts.Result r = SupplyContracts.redeem(p);
        report("C_missing_unchanged", r == SupplyContracts.Result.MISSING && countOf(p, c.item()) == before && p.totalExperience == xp0,
                r + " items " + before + "->" + countOf(p, c.item()) + " (" + c.item() + " x" + c.count() + ")");
        p.getInventory().add(new ItemStack(want, 4));       // now count + 3
        xp0 = p.totalExperience;
        int rewardBefore = countOf(p, c.reward());
        r = SupplyContracts.redeem(p);
        report("C_redeem_exact", r == SupplyContracts.Result.OK && countOf(p, c.item()) == 3, r + " left=" + countOf(p, c.item()) + " (want 3)");
        report("C_reward", countOf(p, c.reward()) - rewardBefore == c.rewardCount(), c.reward() + " +" + (countOf(p, c.reward()) - rewardBefore) + " (want " + c.rewardCount() + ")");
        report("C_xp", p.totalExperience - xp0 == Config.QOL01_CONTRACT_XP.get(), "xp +" + (p.totalExperience - xp0));
        p.getInventory().add(new ItemStack(want, c.count()));
        r = SupplyContracts.redeem(p);
        report("C_second_same_day_refused", r == SupplyContracts.Result.DONE_TODAY, r.toString());
        // the next day (the game time cannot be moved here: put the record one day back)
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.getCompound(SupplyContracts.TAG).putLong(SupplyContracts.KEY_DAY, day - 1);
        r = SupplyContracts.redeem(p);
        report("C_next_day_allowed", r == SupplyContracts.Result.OK, r + " (record rewound by one day)");
        // death / clone keeps the record
        ServerPlayer fresh = fake("c2");
        fresh.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, p, true));
        report("C_record_survives_clone", SupplyContracts.doneOn(fresh, day),
                "contract_day copied=" + SupplyContracts.doneOn(fresh, day) + " (SupplyContracts.onClone; no earlier general copy exists)");
    }

    // ---------- D: structure fauna ----------

    private static final Pattern WEIGHT = Pattern.compile("^(\\S+): weight ([0-9.]+) at ");

    /** candidate name -> weights in order, from one spawnAt dry-ish run (spawned mobs are removed again) */
    private static List<Map.Entry<String, Double>> weightsAt(BlockPos pos, double boost)
    {
        Config.QOL01_STRUCTURE_FAUNA_BOOST.set(boost);
        AABB box = new AABB(pos).inflate(96);
        Set<Mob> before = new HashSet<>(level.getEntitiesOfClass(Mob.class, box));
        List<String> log = new ArrayList<>();
        FaunaSpawner.spawnAt(level, pos, RandomSource.create(12345L), log);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box)) if (!before.contains(m)) m.discard();
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        for (String l : log)
        {
            Matcher m = WEIGHT.matcher(l);
            if (m.find()) out.add(Map.entry(m.group(1), Double.parseDouble(m.group(2))));
        }
        return out;
    }

    private static void testD()
    {
        Config.FAUNA_FOOD_FISH_SHARE.set(0.0);
        if (dWorld.startsWith("no_seabed"))
        {
            List<Map.Entry<String, Double>> w1 = weightsAt(dStructurePos, 1.0);
            List<Map.Entry<String, Double>> w3 = weightsAt(dStructurePos, 3.0);
            boolean same = w1.toString().equals(w3.toString());
            report("D_no_structures_unchanged", same, "world=" + dWorld + " candidates=" + w1.size() + " identical=" + same);
            Set<ResourceLocation> p = StructureFauna.preferredAt(level, dStructurePos);
            report("D_preferred_empty", p.isEmpty(), "world=" + dWorld + " preferred=" + p);
            return;
        }
        if (dStructurePos == null)
        {
            report("D_structure_found", false, "world=" + dWorld + " no structure with preferred_mobs located within 4000 blocks");
            return;
        }
        Set<ResourceLocation> preferred = StructureFauna.preferredAt(level, dStructurePos);
        Set<String> paths = new HashSet<>();
        for (ResourceLocation rl : preferred) paths.add(rl.getPath());
        List<Map.Entry<String, Double>> w1 = weightsAt(dStructurePos, 1.0), w3 = weightsAt(dStructurePos, 3.0);
        boolean sizeOk = !w1.isEmpty() && w1.size() == w3.size();
        int prefN = 0, otherN = 0;
        boolean prefOk = true, otherOk = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; sizeOk && i < w1.size(); i++)
        {
            double a = w1.get(i).getValue(), b = w3.get(i).getValue();
            double ratio = a > 0 ? b / a : 0;
            double tol = 0.01 + 0.015 / Math.max(a, 0.01);       // the log prints two decimals
            if (paths.contains(w1.get(i).getKey()))
            {
                prefN++;
                prefOk &= Math.abs(ratio - 3.0) <= tol;
            }
            else
            {
                otherN++;
                otherOk &= Math.abs(ratio - 1.0) <= tol;
            }
            sb.append(w1.get(i).getKey()).append(String.format(" %.2f->%.2f(x%.3f); ", a, b, ratio));
        }
        report("D_preferred_x3", sizeOk && prefN > 0 && prefOk, "world=" + dWorld + " preferred=" + prefN + " " + preferred + " :: " + sb);
        report("D_others_x1", sizeOk && otherOk, "others=" + otherN);
        if (dEmptyPos == null)
        {
            report("D_no_structure_spot_x1", false, "no structure-free spot found");
        }
        else
        {
            List<Map.Entry<String, Double>> e1 = weightsAt(dEmptyPos, 1.0), e3 = weightsAt(dEmptyPos, 3.0);
            report("D_no_structure_spot_x1", !e1.isEmpty() && e1.toString().equals(e3.toString()), "candidates=" + e1.size() + " preferredAt=" + StructureFauna.preferredAt(level, dEmptyPos));
        }
        long t0 = System.nanoTime();
        int n = 0;
        for (int i = 0; i < 1000; i++) n += StructureFauna.preferredAt(level, dStructurePos).size();
        double ms = (System.nanoTime() - t0) / 1e6;
        report("D_cached_1000_calls", ms < 50, String.format("%.2f ms (sum %d)", ms, n));
    }
}
