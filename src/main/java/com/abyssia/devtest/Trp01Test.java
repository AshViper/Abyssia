package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.dismantle.Dismantler;
import com.abyssia.hazard.HazardZone;
import com.abyssia.industry.ExcavatorEntry;
import com.abyssia.industry.ExcavatorMinerals;
import com.abyssia.industry.ExcavatorStructure;
import com.abyssia.industry.ExcavatorTier;
import com.abyssia.industry.blockentity.AbyssalExcavatorBlockEntity;
import com.abyssia.item.DiveTank;
import com.abyssia.item.DivingBreathing;
import com.abyssia.item.MaterialTools;
import com.abyssia.item.PressureGear;
import com.abyssia.registry.ModIndustry;
import com.abyssia.research.ResearchManager;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.SubmarineItem;
import com.abyssia.vehicle.SubmarineSonar;
import com.abyssia.vehicle.SubmarineUpgrades;
import com.abyssia.vehicle.VehicleContent;
import com.abyssia.worldgen.OreVeinFeature;
import com.abyssia.worldgen.deposit.OreDeposit;
import com.abyssia.worldgen.deposit.OreDepositManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import com.abyssia.vehicle.SubmarineUpgradeMenu;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import com.abyssia.vehicle.SubmarineUpgrades.Kind;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only (TRP01): with -Dabyssia.trp01test (gradlew runServer -Ptrp01Test=1) the dedicated server checks the boss-material gear and halts
 * after logging "TRP01 name PASS|FAIL measured" lines and "TRP01 DONE pass= fail=".
 * Everything except the excavator run calls the effect-deciding static methods directly (SubmarineUpgrades.maxDamage(mask) etc.), no
 * submarine entity is spawned. The excavator run puts an Mk3 and an Mk2 on their own registered deposits, 200 blocks apart in
 * forced chunks, feeds them every tick and counts the output after 1200 ticks.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Trp01Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int BASE_X = 8004, BASE_Z = 4, SPACING = 200, RUN = 1200, MARGIN = 10;

    private static ServerLevel level;
    private static int serverTicks, t, passes, fails, waited;
    private static boolean started, finished, staticDone, placed;
    private static BlockPos pos3, pos2, posF;
    private static UUID dep3, dep2;
    private static AbyssalExcavatorBlockEntity be3, be2;

    private static void result(String name, boolean ok, String measured)
    {
        if (ok) passes++;
        else fails++;
        LOG.info("TRP01 {} {} {}", name, ok ? "PASS" : "FAIL", measured);
    }

    private static Item item(String path)
    {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(Abyssia.MODID, path));
    }

    private static ResourceLocation rl(String path)
    {
        return new ResourceLocation(Abyssia.MODID, path);
    }

    private static boolean near(double a, double b)
    {
        return Math.abs(a - b) < 1.0e-6;
    }

    /** mask of a container with the given items in slots 0..3 (null = empty) */
    private static int maskOf(ItemStack... stacks)
    {
        SimpleContainer c = new SimpleContainer(SubmarineUpgrades.SLOTS);
        for (int i = 0; i < stacks.length; i++) c.setItem(i, stacks[i]);
        return SubmarineUpgrades.maskOf(c);
    }

    /** {forward, side, vertical, accel} of a mask (Forge: the numbers live in SubmarineUpgrades) */
    private static double[] speeds(int mask)
    {
        return new double[]{SubmarineUpgrades.forwardSpeed(mask), SubmarineUpgrades.sideSpeed(mask), SubmarineUpgrades.verticalSpeed(mask), SubmarineUpgrades.accel(mask)};
    }

    private static ItemStack mk1(int slot)
    {
        return new ItemStack(VehicleContent.UPGRADE_ITEMS.get(Kind.values()[slot]).get());
    }

    private static ItemStack mk2(int slot)
    {
        return new ItemStack(VehicleContent.ADV_UPGRADE_ITEMS.get(slot).get());
    }

    private static int countOf(Recipe<?> recipe, Item item)
    {
        int n = 0;
        for (Ingredient ing : recipe.getIngredients())
            if (!ing.isEmpty() && ing.test(new ItemStack(item))) n++;
        return n;
    }

    private static void recipes(MinecraftServer server)
    {
        // recipe id -> {ingredient item path, count, ...}
        Map<String, Object[]> expected = new java.util.LinkedHashMap<>();
        expected.put("submarine_upgrade_serpent_scale_hull", new Object[]{"abyss_serpent_scale", 6, "submarine_upgrade_pressure_hull", 1, "abyssal_alloy_ingot", 2});
        expected.put("submarine_upgrade_jet_thruster", new Object[]{"kraken_beak", 2, "kraken_ink_sac", 6, "submarine_upgrade_maneuver_thruster", 1});
        expected.put("submarine_upgrade_abyss_heart_cell", new Object[]{"abyss_heart", 1, "mother_bell_gel", 4, "submarine_upgrade_high_capacity_battery", 1});
        expected.put("submarine_upgrade_lure_sonar", new Object[]{"angler_lure", 6, "submarine_upgrade_sonar_scanner", 1, "luminous_crystal", 2});
        expected.put("heart_tank", new Object[]{"abyss_heart", 1, "mother_bell_gel", 4, "pressure_dive_tank", 1});
        expected.put("fang_drill", new Object[]{"trench_wyrm_fang", 3, "abyssal_drill", 1, "pressure_crystal_shard", 2});
        for (Map.Entry<String, Object[]> e : expected.entrySet())
        {
            var holder = server.getRecipeManager().byKey(rl(e.getKey()));
            if (holder.isEmpty())
            {
                result("recipe_" + e.getKey(), false, "recipe missing");
                continue;
            }
            Recipe<?> r = holder.get();
            StringBuilder sb = new StringBuilder();
            boolean ok = r.getResultItem(server.registryAccess()).is(item(e.getKey()));
            for (int i = 0; i < e.getValue().length; i += 2)
            {
                int have = countOf(r, item((String) e.getValue()[i]));
                ok &= have == (Integer) e.getValue()[i + 1];
                sb.append(e.getValue()[i]).append('x').append(have).append(' ');
            }
            result("recipe_" + e.getKey(), ok, sb.toString().trim());
        }
        // the Mk3 excavator is built with the habitat constructor: the cost list of its build entry
        List<ItemStack> cost = new ExcavatorEntry(ExcavatorTier.MK3).cost();
        StringBuilder sb = new StringBuilder();
        int fang = 0, tungsten = 0, hadal = 0;
        for (ItemStack s : cost)
        {
            sb.append(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).append('x').append(s.getCount()).append(' ');
            if (s.is(item("trench_wyrm_fang"))) fang = s.getCount();
            if (s.is(item("tungsten_ingot"))) tungsten = s.getCount();
            if (s.is(item("hadal_plating"))) hadal = s.getCount();
        }
        result("recipe_abyssal_excavator_mk3(cost)", fang == 6 && tungsten == 4 && hadal == 2, sb.toString().trim());
    }

    private static void research()
    {
        ServerPlayer p = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("trp01".getBytes()), "trp01"));
        ResourceLocation tech = rl("technology/abyss_trophies");
        boolean exists = com.abyssia.research.TechnologyRegistry.exists(tech);
        ResearchManager.reset(p, null);
        String[] items = {"submarine_upgrade_serpent_scale_hull", "submarine_upgrade_jet_thruster", "submarine_upgrade_abyss_heart_cell",
                "submarine_upgrade_lure_sonar", "heart_tank", "fang_drill"};
        int lockedBefore = 0, freeAfter = 0;
        for (String i : items) if (!ResearchManager.isKeyUnlocked(p, ResearchManager.key("recipe", i))) lockedBefore++;
        if (!ResearchManager.isBuildingUnlocked(p, "abyssal_excavator_mk3")) lockedBefore++;
        boolean unlocked = ResearchManager.unlock(p, tech);
        for (String i : items) if (ResearchManager.isKeyUnlocked(p, ResearchManager.key("recipe", i))) freeAfter++;
        if (ResearchManager.isBuildingUnlocked(p, "abyssal_excavator_mk3")) freeAfter++;
        ResearchManager.reset(p, null);
        result("research", exists && unlocked && lockedBefore == 7 && freeAfter == 7,
                "exists=" + exists + " lockedBefore=" + lockedBefore + "/7 unlock=" + unlocked + " freeAfter=" + freeAfter + "/7");
    }

    private static void submarine()
    {
        int none = 0;
        int h1 = maskOf(mk1(0)), h2 = maskOf(mk2(0));
        double base = Config.SUBMARINE_MAX_SPEED.get();
        result("hull2", SubmarineUpgrades.maxDamage(h2) == 110.0f && near(SubmarineUpgrades.hullDeepReduction(h2), 0.35)
                        && near(speeds(h2)[0] / base, 1.0),
                "maxDamage=" + SubmarineUpgrades.maxDamage(h2) + " deepMult(Y-100)=" + SubmarineUpgrades.hullDeepReduction(h2) + " speedMult="
                        + speeds(h2)[0] / base + " damage(10)=" + (float) Math.ceil(10 * SubmarineUpgrades.hullDeepReduction(h2)) + " [method calls]");
        result("hull1_unchanged", SubmarineUpgrades.maxDamage(h1) == 70.0f && near(SubmarineUpgrades.hullDeepReduction(h1), 0.5)
                        && near(speeds(h1)[0] / base, 0.92) && SubmarineUpgrades.maxDamage(none) == Submarine.MAX_DAMAGE,
                "maxDamage=" + SubmarineUpgrades.maxDamage(h1) + " deepMult=" + SubmarineUpgrades.hullDeepReduction(h1) + " speedMult=" + speeds(h1)[0] / base
                        + " none=" + SubmarineUpgrades.maxDamage(none));
        int t1 = maskOf(ItemStack.EMPTY, ItemStack.EMPTY, mk1(2));
        int t2 = maskOf(ItemStack.EMPTY, ItemStack.EMPTY, mk2(2));
        double[] s2 = speeds(t2), s1 = speeds(t1);
        result("thruster2", near(s2[0], 0.68) && near(s2[1], 0.60) && near(s2[2], 0.58) && near(s2[3], 0.040) && SubmarineUpgrades.thrustFe(t2) == 14,
                "forward=" + s2[0] + " side=" + s2[1] + " vertical=" + s2[2] + " accel=" + s2[3] + " fe=" + SubmarineUpgrades.thrustFe(t2) + " [method calls]");
        // Mk1 follows its own config values (run/config may hold values older than the code defaults)
        result("thruster1_unchanged", near(s1[0], Config.SUBMARINE_THRUSTER_FORWARD.get()) && near(s1[1], Config.SUBMARINE_THRUSTER_SIDE.get())
                        && near(s1[2], Config.SUBMARINE_THRUSTER_VERTICAL.get()) && near(s1[3], Config.SUBMARINE_THRUSTER_ACCEL.get())
                        && SubmarineUpgrades.thrustFe(t1) == Config.SUBMARINE_THRUSTER_FE.get() && SubmarineUpgrades.thrustFe(none) == Config.SUBMARINE_THRUST_FE.get(),
                "forward=" + s1[0] + " side=" + s1[1] + " vertical=" + s1[2] + " accel=" + s1[3] + " fe=" + SubmarineUpgrades.thrustFe(t1)
                        + " base fe=" + SubmarineUpgrades.thrustFe(none) + " (config values)");
        int b1 = maskOf(ItemStack.EMPTY, mk1(1)), b2 = maskOf(ItemStack.EMPTY, mk2(1));
        ItemStack sub = new ItemStack(VehicleContent.SUBMARINE_ITEM.get());
        SimpleContainer c = new SimpleContainer(SubmarineUpgrades.SLOTS);
        c.setItem(1, mk2(1));
        SubmarineItem.setUpgrades(sub, SubmarineUpgrades.write(c));
        result("cell2", SubmarineUpgrades.capacity(b2) == 400_000 && SubmarineItem.capacity(sub) == 400_000,
                "mk2=" + SubmarineUpgrades.capacity(b2) + " item=" + SubmarineItem.capacity(sub) + " [method calls]");
        result("cell1_none_unchanged", SubmarineUpgrades.capacity(b1) == 150_000 && SubmarineUpgrades.capacity(none) == Submarine.capacity()
                        && SubmarineUpgrades.capacity(none) == Config.SUBMARINE_ENERGY_CAPACITY.get(),
                "mk1=" + SubmarineUpgrades.capacity(b1) + " none=" + SubmarineUpgrades.capacity(none));
        int k1 = maskOf(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, mk1(3)), k2 = maskOf(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, mk2(3));
        result("sonar2", SubmarineSonar.range(k2) == 48 && SubmarineSonar.dockRange(k2) == 96,
                "range=" + SubmarineSonar.range(k2) + " dock=" + SubmarineSonar.dockRange(k2) + " [method calls]");
        result("sonar1_unchanged", SubmarineSonar.range(k1) == 16 && SubmarineSonar.dockRange(k1) == 32,
                "range=" + SubmarineSonar.range(k1) + " dock=" + SubmarineSonar.dockRange(k1));
        // mask bits: a Mk2 sets bit(i) and bit(7+i); Mk1-only masks are the old values
        int m2 = maskOf(mk2(0), mk1(1), mk2(2), mk1(3));
        int mOld = maskOf(mk1(0), mk1(1), mk1(2), mk1(3));
        int mOld2 = maskOf(mk1(0), mk1(1));
        boolean bits = (m2 & (1 << 0)) != 0 && (m2 & (1 << 7)) != 0 && (m2 & (1 << 2)) != 0 && (m2 & (1 << 9)) != 0
                && (m2 & (1 << 1)) != 0 && (m2 & (1 << 8)) == 0 && (m2 & (1 << 3)) != 0 && (m2 & (1 << 10)) == 0;
        // the same through the saved compound (the item component / entity save)
        SimpleContainer sc = new SimpleContainer(SubmarineUpgrades.SLOTS);
        sc.setItem(0, mk2(0));
        sc.setItem(1, mk1(1));
        sc.setItem(2, mk2(2));
        sc.setItem(3, mk1(3));
        boolean tagSame = SubmarineUpgrades.maskOf(SubmarineUpgrades.write(sc)) == m2;
        result("mask", bits && tagSame && mOld == 15 && mOld2 == 3 && m2 == (1 | 2 | 4 | 8 | 1 << 7 | 1 << 9),
                "mk2 mix=" + m2 + " (bits 0,1,2,3,7,9) tagSame=" + tagSame + " mk1 all=" + mOld + " mk1 hull+battery=" + mOld2);
    }

    private static void gear()
    {
        ItemStack heart = new ItemStack(MaterialTools.HEART_TANK.get()), pressure = new ItemStack(MaterialTools.PRESSURE_DIVE_TANK.get());
        int tier = PressureGear.pieceTier(heart), seconds = DiveTank.capacitySeconds(DiveTank.tankTier(heart));
        boolean tags = heart.is(HazardZone.HEAT_PROOF) && heart.is(HazardZone.COLD_PROOF) && heart.is(HazardZone.TOXIC_PROOF_CHEST);
        result("heart_tank", tier == 4 && seconds == 1800 && heart.getMaxDamage() == 800 && tags && DiveTank.oxygen(heart) == 1800
                        && PressureGear.safeY(tier) == Integer.MIN_VALUE,
                "tier=" + tier + " seconds=" + seconds + " oxygen(new)=" + DiveTank.oxygen(heart) + " durability=" + heart.getMaxDamage()
                        + " tags(heat/cold/toxic)=" + tags + " safeY=" + PressureGear.safeY(tier));
        result("pressure_tank_unchanged", PressureGear.pieceTier(pressure) == 3 && DiveTank.capacitySeconds(DiveTank.tankTier(pressure)) == 720
                        && pressure.getMaxDamage() == 520,
                "tier=" + PressureGear.pieceTier(pressure) + " seconds=" + DiveTank.capacitySeconds(DiveTank.tankTier(pressure))
                        + " durability=" + pressure.getMaxDamage());
        Item fang = MaterialTools.FANG_DRILL.get(), drill = MaterialTools.ABYSSAL_DRILL.get();
        var ft = ((TieredItem) fang).getTier();
        var dt = ((TieredItem) drill).getTier();
        result("fang_drill", near(ft.getSpeed(), 16.0) && ft.getUses() == 4000 && near(ft.getAttackDamageBonus(), 2.5) && ft.getEnchantmentValue() == 14
                        && near(dt.getSpeed(), 12.0) && dt.getUses() == 2200,
                "speed=" + ft.getSpeed() + " uses=" + ft.getUses() + " attackBonus=" + ft.getAttackDamageBonus() + " enchant=" + ft.getEnchantmentValue()
                        + " | abyssal_drill speed=" + dt.getSpeed() + " uses=" + dt.getUses());
    }

    private static void excavatorConstants()
    {
        ExcavatorTier m3 = ExcavatorTier.MK3, m2 = ExcavatorTier.MK2;
        boolean ok = m3.cycleTicks == 24 && m3.orePerCycle == 3 && m3.energyPerTick == 60 && m3.workingRadius == 64 && m3.energyPerTick <= m3.energyReceive;
        ResourceLocation tungsten = rl("tungsten_ore");
        boolean mk2only = ExcavatorMinerals.canMine(m3, tungsten) && ExcavatorMinerals.canMine(m2, tungsten) && ExcavatorMinerals.requiredTier(tungsten) == 2;
        result("excavator_mk3_constants", ok && mk2only && m2.cycleTicks == 30 && m2.orePerCycle == 2 && m2.energyPerTick == 40 && m2.workingRadius == 48,
                "mk3=" + m3.cycleTicks + "t/" + m3.orePerCycle + "ore/" + m3.energyPerTick + "FE/r" + m3.workingRadius + " receive=" + m3.energyReceive
                        + " canMine(tungsten)=" + mk2only + " | mk2=" + m2.cycleTicks + "t/" + m2.orePerCycle + "ore/" + m2.energyPerTick + "FE/r" + m2.workingRadius);
    }

    private static void advancementsAndLoot(MinecraftServer server)
    {
        boolean slayer = server.getAdvancements().getAdvancement(rl("dive/titan_slayer")) != null;
        boolean hunter = server.getAdvancements().getAdvancement(rl("dive/titan_hunter")) != null;
        result("advancements", slayer && hunter, "titan_slayer=" + slayer + " titan_hunter=" + hunter);
        Map<String, Integer> expect = Map.of("abyss_serpent", 3, "trench_wyrm", 3, "abyss_kraken", 4, "abyss_mother", 4, "abyss_angler", 3);
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(expect).entrySet())
        {
            try
            {
                LootTable table = server.getLootData().getLootTable(rl("entities/" + e.getKey()));
                JsonElement json = net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create().toJsonTree(table);
                int entries = 0, pools = 0;
                JsonArray arr = json.getAsJsonObject().has("pools") ? json.getAsJsonObject().getAsJsonArray("pools") : new JsonArray();
                for (JsonElement pool : arr)
                {
                    pools++;
                    JsonObject po = pool.getAsJsonObject();
                    if (po.has("entries")) entries += po.getAsJsonArray("entries").size();
                }
                ok &= entries == e.getValue();
                sb.append(e.getKey()).append('=').append(entries).append("(pools ").append(pools).append(") ");
            }
            catch (Exception ex)
            {
                ok = false;
                sb.append(e.getKey()).append("=exception ").append(ex).append(' ');
            }
        }
        result("boss_loot", ok, sb.toString().trim());
    }

    /** registers a deposit around pos, starts a machine of the block on it and returns the id */
    private static UUID deposit(BlockPos pos)
    {
        AABB box = new AABB(pos.getX() - 4, pos.getY() - 4, pos.getZ() - 4, pos.getX() + 5, pos.getY() + 5, pos.getZ() + 5);
        return OreDepositManager.register(level, rl("tungsten_ore"), pos.below(2), box, OreVeinFeature.Size.SMALL,
                OreVeinFeature.Shape.VEIN, 100_000).map(OreDeposit::depositId).orElseThrow();
    }

    private static void clearBlock(BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof AbyssalExcavatorBlockEntity be)
            for (int s = 0; s < be.items().getSlots(); s++) be.items().setStackInSlot(s, ItemStack.EMPTY);
        level.removeBlock(pos, false);
    }

    private static int mined3, mined2;

    private static int drain(AbyssalExcavatorBlockEntity be)
    {
        int n = count(be);
        for (int i = 0; i < be.items().getSlots(); i++) be.items().setStackInSlot(i, ItemStack.EMPTY);
        return n;
    }

    private static int count(AbyssalExcavatorBlockEntity be)
    {
        int n = 0;
        for (int i = 0; i < be.items().getSlots(); i++) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    /** returns false while the chunks are not block-ticking yet */
    private static boolean placeExcavators()
    {
        if (pos3 == null)
        {
            int y = Math.max(70, level.getMinBuildHeight() + 10);
            pos3 = new BlockPos(BASE_X, y, BASE_Z);
            pos2 = new BlockPos(BASE_X + SPACING, y, BASE_Z);
            level.setChunkForced(pos3.getX() >> 4, pos3.getZ() >> 4, true);
            level.setChunkForced(pos2.getX() >> 4, pos2.getZ() >> 4, true);
            posF = pos3.offset(0, 0, 40);   // the sonar test's fish stands 40 blocks from the submarine
            level.setChunkForced(posF.getX() >> 4, posF.getZ() >> 4, true);
        }
        for (BlockPos p : new BlockPos[]{pos3, pos2, posF})
        {
            if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) return false;
            long cp = ChunkPos.asLong(p);
            if (!level.getChunkAt(p).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                    || !level.areEntitiesLoaded(cp) || !level.shouldTickBlocksAt(cp))
            {
                waited++;
                return false;
            }
        }
        LOG.info("TRP01 excavator chunks ready after {} ticks", waited);
        clearBlock(pos3); // run/ keeps its world: a machine left by an earlier run would keep its items and progress
        clearBlock(pos2);
        dep3 = deposit(pos3);
        dep2 = deposit(pos2);
        Block b3 = ModIndustry.ABYSSAL_EXCAVATOR_MK3.get(), b2 = ModIndustry.ABYSSAL_EXCAVATOR_MK2.get();
        level.setBlock(pos3, b3.defaultBlockState(), 3);
        level.setBlock(pos2, b2.defaultBlockState(), 3);
        be3 = (AbyssalExcavatorBlockEntity) level.getBlockEntity(pos3);
        be2 = (AbyssalExcavatorBlockEntity) level.getBlockEntity(pos2);
        t = 0;
        return be3 != null && be2 != null;
    }

    private static net.minecraftforge.energy.IEnergyStorage energyOf(AbyssalExcavatorBlockEntity be)
    {
        return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, null).orElseThrow(IllegalStateException::new);
    }

    private static void cleanup()
    {
        for (BlockPos p : new BlockPos[]{pos3, pos2, posF})
        {
            if (p == null) continue;
            if (p != posF) clearBlock(p);
            level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, false);
        }
        if (dep3 != null) OreDepositManager.remove(level, dep3);
        if (dep2 != null) OreDepositManager.remove(level, dep2);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || System.getProperty("abyssia.trp01test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        if (!started)
        {
            started = true;
            level = server.overworld();
            LOG.info("TRP01 start");
        }
        if (!staticDone)
        {
            staticDone = true;
            try
            {
                recipes(server);
                research();
                submarine();
                gear();
                excavatorConstants();
                advancementsAndLoot(server);
            }
            catch (Exception e)
            {
                result("static_exception", false, e.toString());
                LOG.error("TRP01 exception", e);
            }
        }
        if (!placed)
        {
            try
            {
                if (!placeExcavators()) return;
            }
            catch (Exception e)
            {
                result("excavator_run", false, "exception " + e);
                LOG.error("TRP01 exception", e);
                cleanup();
                finish(server);
                return;
            }
            placed = true;
        }
        // 256 FE/t is the machines' receive limit; they need 60 / 40
        energyOf(be3).receiveEnergy(256, false);
        energyOf(be2).receiveEnergy(256, false);
        // take the ore out every tick: the single ore slot fills at 64 and stops the machine (status 4)
        mined3 += drain(be3);
        mined2 += drain(be2);
        if (++t < RUN + MARGIN) return;
        int n3 = mined3, n2 = mined2;
        result("excavator_mk3_run", n3 >= 144 && n3 <= 156,
                "items=" + n3 + " in " + RUN + " ticks (expected ~150) status=" + be3.data().get(com.abyssia.industry.blockentity.IndustryBlockEntity.DATA_STATUS));
        result("excavator_mk2_run", n2 >= 76 && n2 <= 84, "items=" + n2 + " in " + RUN + " ticks (expected ~80) status=" + be2.data().get(com.abyssia.industry.blockentity.IndustryBlockEntity.DATA_STATUS));
        entityTests();
        cleanup();
        finish(server);
    }

    // ---------------------------------------------------------------- real entities and menus (all inside one server tick)

    private interface Body { void run() throws Exception; }

    private static final int AX0 = 8002, AX1 = 8014, AZ0 = 2, AZ1 = 14, ARENA_H = 12;
    private static final double SX = 8008.5, SZ = 8.5;
    private static int ay;
    private static final Map<BlockPos, BlockState> arenaSaved = new LinkedHashMap<>();
    private static final List<Entity> spawned = new ArrayList<>();

    private static void guard(String name, Body body)
    {
        try
        {
            body.run();
        }
        catch (Throwable e)
        {
            result(name, false, "exception " + e);
            LOG.error("TRP01 exception in " + name, e);
        }
    }

    /** a water cube in the forced chunk (the original blocks are kept and put back) */
    private static void arenaBuild()
    {
        ay = Math.min(150, level.getMaxBuildHeight() - 20);
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int x = AX0; x <= AX1; x++)
            for (int z = AZ0; z <= AZ1; z++)
                for (int dy = 0; dy <= ARENA_H; dy++)
                {
                    BlockPos pos = new BlockPos(x, ay + dy, z);
                    arenaSaved.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, water, 2);
                }
    }

    private static void arenaRestore()
    {
        for (Map.Entry<BlockPos, BlockState> e : arenaSaved.entrySet()) level.setBlock(e.getKey(), e.getValue(), 2);
        arenaSaved.clear();
    }

    private static ServerPlayer arenaPlayer(String name)
    {
        ServerPlayer p = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.getInventory().clearContent();
        if (p.isCreative() || p.isSpectator()) p.setGameMode(GameType.SURVIVAL);
        p.moveTo(SX, ay + 3, 3.5, 0.0f, 0.0f);
        p.baseTick();   // refreshes "eyes in water"
        return p;
    }

    private static Submarine newSub(double x, double y, double z)
    {
        Submarine s = VehicleContent.SUBMARINE.get().create(level);
        s.setPos(x, y, z);
        if (!level.addFreshEntity(s)) throw new IllegalStateException("addFreshEntity refused the submarine at y=" + y);
        spawned.add(s);
        return s;
    }

    private static void install(Submarine s, ItemStack... stacks)
    {
        for (int i = 0; i < stacks.length; i++) s.upgrades().setItem(i, stacks[i]);
    }

    private static List<ItemEntity> itemsAt(double x, double y, double z)
    {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(x - 4, y - 4, z - 4, x + 4, y + 4, z + 4));
    }

    private static String id(ItemStack s)
    {
        return s.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    private static void entityTests()
    {
        try
        {
            arenaBuild();
        }
        catch (Throwable e)
        {
            result("entity_arena", false, "exception " + e);
            LOG.error("TRP01 exception", e);
            arenaRestore();
            return;
        }
        try
        {
            guard("sub_entity_mk2_mask", Trp01Test::subEntityMask);
            guard("sub_entity_capacity", Trp01Test::subEntityCapacity);
            guard("sub_entity_damage", Trp01Test::subEntityDamage);
            guard("sub_entity_item_roundtrip", Trp01Test::subEntityRoundtrip);
            guard("sub_menu_swap", Trp01Test::subMenu);
            guard("sonar_entity", Trp01Test::sonarEntity);
            guard("excavator_mk3_build", Trp01Test::excavatorBuild);
            guard("items_foil", Trp01Test::itemsFoil);
            guard("heart_tank_breath", Trp01Test::heartTankBreath);
        }
        finally
        {
            for (Entity e : spawned) if (!e.isRemoved()) e.discard();
            spawned.clear();
            for (ItemEntity i : itemsAt(SX, -100, SZ)) i.discard();
            for (ItemEntity i : itemsAt(SX, ay + 4, SZ)) i.discard();
            arenaRestore();
        }
    }

    private static void subEntityMask()
    {
        Submarine s = newSub(SX, ay + 6, SZ);
        install(s, mk2(0), mk2(1), mk2(2), mk2(3));
        int expect = 0xF | (0xF << SubmarineUpgrades.ADV_SHIFT);
        boolean all = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++)
        {
            all &= s.has(Kind.values()[i]) && s.isAdvanced(i);
            sb.append(i).append(':').append(s.has(Kind.values()[i])).append('/').append(s.isAdvanced(i)).append(' ');
        }
        int synced = -1;
        var values = s.getEntityData().getNonDefaultValues();
        if (values != null)
            for (var v : values) if (v.value() instanceof Integer n && n == expect) synced = n;
        boolean ok = all && s.upgradeMask() == expect && SubmarineUpgrades.maskOf(s.upgrades()) == s.upgradeMask() && synced == expect;
        result("sub_entity_mk2_mask", ok, "upgradeMask=" + s.upgradeMask() + " expected=" + expect + " (bits 0-3,7-10) hasUpgrade/isAdvanced "
                + sb.toString().trim() + " syncedDataValue=" + synced + " containerMask=" + SubmarineUpgrades.maskOf(s.upgrades()) + " [entity]");
        s.discard();
    }

    private static void subEntityCapacity()
    {
        int base = Config.SUBMARINE_ENERGY_CAPACITY.get();
        Submarine s = newSub(SX, ay + 6, SZ);
        s.upgrades().setItem(Kind.BATTERY.slot(), mk2(1));
        int max1 = s.maxEnergy();
        s.setEnergy(400_000);
        int e1 = s.getEnergy();
        s.upgrades().setItem(Kind.BATTERY.slot(), mk1(1));
        int max2 = s.maxEnergy(), e2 = s.getEnergy();
        s.upgrades().setItem(Kind.BATTERY.slot(), ItemStack.EMPTY);
        int max3 = s.maxEnergy(), e3 = s.getEnergy();
        boolean ok = max1 == 400_000 && e1 == 400_000 && max2 == 150_000 && e2 == 150_000 && max3 == base && e3 == Math.min(150_000, base);
        result("sub_entity_capacity", ok, "Mk2 cell max=" + max1 + " energy(set 400000)=" + e1 + " | Mk1 max=" + max2 + " energy=" + e2
                + " | removed max=" + max3 + " (config base " + base + ") energy=" + e3 + " [entity]");
        s.discard();
    }

    private static float hurtDelta(ItemStack hull, double y)
    {
        Submarine s = newSub(SX, y, SZ);
        if (!hull.isEmpty()) s.upgrades().setItem(0, hull);
        float before = s.getDamage();
        s.hurt(level.damageSources().generic(), 1.0f);
        float delta = s.getDamage() - before;
        s.discard();
        return delta;
    }

    private static void subEntityDamage()
    {
        double deep = DeepLayer.TOP_Y - 36;   // below the deep layer's top
        float d2 = hurtDelta(mk2(0), deep), d1 = hurtDelta(mk1(0), deep), d0 = hurtDelta(ItemStack.EMPTY, deep);
        float surf2 = hurtDelta(mk2(0), 100);
        Submarine s = newSub(SX, deep, SZ);
        s.upgrades().setItem(0, mk2(0));
        s.setDamage(105.0f);
        s.hurt(level.damageSources().generic(), 1.0f);
        float at109 = s.getDamage();
        boolean alive109 = s.isAlive();
        s.setDamage(106.0f);
        s.hurt(level.damageSources().generic(), 1.0f);
        float at110 = s.getDamage();
        boolean alive110 = s.isAlive();
        s.setDamage(107.0f);
        s.hurt(level.damageSources().generic(), 1.0f);
        float at111 = s.getDamage();
        boolean broke = s.isRemoved();
        List<ItemEntity> drops = itemsAt(SX, deep, SZ);
        StringBuilder sb = new StringBuilder();
        for (ItemEntity d : drops) sb.append(id(d.getItem())).append('x').append(d.getItem().getCount()).append(' ').append(SubmarineItem.getUpgrades(d.getItem())).append(' ');
        boolean ok = d2 == 4.0f && d1 == 5.0f && d0 == 10.0f && at109 == 109.0f && alive109 && at111 == 111.0f && broke;
        result("sub_entity_damage", ok, "deep(Y" + (int) deep + ") hit(1.0): Mk2=" + d2 + " Mk1=" + d1 + " none=" + d0 + " | Mk2 surface(Y100)=" + surf2
                + " | Mk2 hull damage " + at109 + " alive=" + alive109 + ", " + at110 + " alive=" + alive110 + " (breaks on damage > maxDamage 110), " + at111 + " removed=" + broke
                + " | drops on break=" + drops.size() + " " + sb.toString().trim() + " upgradesLeftInEntity=" + SubmarineUpgrades.maskOf(s.upgrades()) + " [entity]");
        for (ItemEntity d : drops) d.discard();
        if (!s.isRemoved()) s.discard();
    }

    private static void subEntityRoundtrip()
    {
        ServerPlayer p = arenaPlayer("trp01r");
        Submarine src = newSub(SX, ay + 6, SZ + 3);
        install(src, mk2(0), mk2(1), mk2(2), mk2(3));
        src.setEnergy(300_000);
        int maskBefore = src.upgradeMask(), energyBefore = src.getEnergy();
        ItemStack stack = src.toItem();
        String comp = "nbt=" + stack.getTag();
        src.discard();
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var use = stack.getItem().use(level, p, InteractionHand.MAIN_HAND);
        List<Submarine> placed = level.getEntitiesOfClass(Submarine.class, new AABB(SX - 8, ay - 2, SZ - 8, SX + 8, ay + 14, SZ + 8));
        spawned.addAll(placed);
        if (placed.size() != 1)
        {
            result("sub_entity_item_roundtrip", false, "use()=" + use.getResult() + " placed=" + placed.size() + " item " + comp);
            return;
        }
        Submarine s = placed.get(0);
        StringBuilder ids = new StringBuilder();
        boolean same = true;
        for (int i = 0; i < 4; i++)
        {
            ids.append(id(s.upgrades().getItem(i))).append(' ');
            same &= s.upgrades().getItem(i).is(VehicleContent.ADV_UPGRADE_ITEMS.get(i).get());
        }
        boolean ok = same && s.upgradeMask() == maskBefore && s.getEnergy() == energyBefore && energyBefore == 300_000 && p.getMainHandItem().isEmpty();
        result("sub_entity_item_roundtrip", ok, "mask before=" + maskBefore + " after=" + s.upgradeMask() + " energy before=" + energyBefore + " after=" + s.getEnergy()
                + " items=" + ids.toString().trim() + " | " + comp + " | use()=" + use.getResult() + " handLeft=" + p.getMainHandItem().getCount() + " [entity, SubmarineItem.use]");
        s.discard();
    }

    private static String tally(Player p, Submarine s, AbstractContainerMenu m, Item... items)
    {
        StringBuilder sb = new StringBuilder();
        for (Item it : items)
        {
            int n = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(it)) n += p.getInventory().getItem(i).getCount();
            if (m.getCarried().is(it)) n += m.getCarried().getCount();
            for (int i = 0; i < SubmarineUpgrades.SLOTS; i++) if (s.upgrades().getItem(i).is(it)) n += s.upgrades().getItem(i).getCount();
            sb.append(n).append(',');
        }
        return sb.toString();
    }

    /** tally + the item ids of upgrade slots 0-3, the cursor and the mask (diagnostics) */
    private static String state(Player p, Submarine s, AbstractContainerMenu m, Item... items)
    {
        StringBuilder sb = new StringBuilder("{").append(tally(p, s, m, items)).append(" slots=");
        for (int i = 0; i < 4; i++) sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.upgrades().getItem(i).getItem()).getPath()).append(i < 3 ? "," : "");
        return sb.append(" cursor=").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(m.getCarried().getItem()).getPath())
                .append(" mask=").append(s.upgradeMask()).append('}').toString();
    }

    private static void subMenu()
    {
        ServerPlayer p = arenaPlayer("trp01m");
        Submarine s = newSub(SX, ay + 6, SZ);
        // menu slots: 0-4 upgrades, 5-31 inventory 9-35, 32-40 hotbar (inventory 0-8)
        ItemStack hull1 = mk1(0), hull2 = mk2(0), jet = mk2(2), cell = mk2(1), sonar = mk2(3);
        p.getInventory().setItem(0, hull1);
        p.getInventory().setItem(1, hull2);
        p.getInventory().setItem(2, jet);
        p.getInventory().setItem(3, cell);
        p.getInventory().setItem(4, sonar);
        SubmarineUpgradeMenu m = new SubmarineUpgradeMenu(77, p.getInventory(), s.upgrades(), s);
        // the item types are read before any click: a stack moved by the menu is emptied and an empty stack's getItem() is AIR
        Item iHull1 = hull1.getItem(), iHull2 = hull2.getItem(), iJet = jet.getItem(), iCell = cell.getItem(), iSonar = sonar.getItem();
        Item[] items = {iHull1, iHull2, iJet, iCell, iSonar};
        String base = tally(p, s, m, items);
        StringBuilder log = new StringBuilder();
        boolean ok = base.equals("1,1,1,1,1,");
        // (a) Mk1 hull into the empty hull slot
        m.clicked(32, 0, ClickType.PICKUP, p);
        m.clicked(0, 0, ClickType.PICKUP, p);
        boolean a = s.upgrades().getItem(0).is(iHull1) && m.getCarried().isEmpty() && tally(p, s, m, items).equals(base);
        log.append("(a) Mk1 in=").append(a).append(' ').append(state(p, s, m, items));
        // a thruster into the occupied hull slot is refused; then into the thrust slot
        m.clicked(34, 0, ClickType.PICKUP, p);
        m.clicked(0, 0, ClickType.PICKUP, p);
        boolean refused = s.upgrades().getItem(0).is(iHull1) && m.getCarried().is(iJet) && tally(p, s, m, items).equals(base);
        m.clicked(2, 0, ClickType.PICKUP, p);
        boolean thrust = s.upgrades().getItem(2).is(iJet) && m.getCarried().isEmpty() && s.isAdvanced(2) && tally(p, s, m, items).equals(base);
        log.append(" | jet->hull refused=").append(refused).append(" jet->thrust slot=").append(thrust);
        // (b) swap Mk1 -> Mk2 with the cursor
        m.clicked(33, 0, ClickType.PICKUP, p);
        m.clicked(0, 0, ClickType.PICKUP, p);
        boolean swapped = s.upgrades().getItem(0).is(iHull2) && m.getCarried().is(iHull1) && s.isAdvanced(0);
        m.clicked(32, 0, ClickType.PICKUP, p);
        boolean b = swapped && m.getCarried().isEmpty() && tally(p, s, m, items).equals(base);
        log.append(" | (b) swap Mk1->Mk2=").append(b).append(' ').append(state(p, s, m, items));
        // (c) take it out again
        m.clicked(0, 0, ClickType.PICKUP, p);
        boolean carried = m.getCarried().is(iHull2) && s.upgrades().getItem(0).isEmpty()
                && s.upgradeMask() == (SubmarineUpgrades.bit(2) | SubmarineUpgrades.bit(SubmarineUpgrades.ADV_SHIFT + 2));
        m.clicked(33, 0, ClickType.PICKUP, p);
        boolean c = carried && m.getCarried().isEmpty() && tally(p, s, m, items).equals(base);
        log.append(" | (c) take out=").append(c).append(' ').append(state(p, s, m, items));
        // shift-click Mk2 hull / cell / sonar from the inventory
        m.clicked(33, 0, ClickType.QUICK_MOVE, p);
        m.clicked(35, 0, ClickType.QUICK_MOVE, p);
        m.clicked(36, 0, ClickType.QUICK_MOVE, p);
        boolean q = s.upgrades().getItem(0).is(iHull2) && s.upgrades().getItem(1).is(iCell) && s.upgrades().getItem(2).is(iJet)
                && s.upgrades().getItem(3).is(iSonar) && tally(p, s, m, items).equals(base)
                && s.upgradeMask() == (0xF | (0xF << SubmarineUpgrades.ADV_SHIFT));
        log.append(" | shift-click in=").append(q).append(' ').append(state(p, s, m, items));
        // shift-click out (sonar slot back to the inventory)
        m.clicked(3, 0, ClickType.QUICK_MOVE, p);
        boolean out = s.upgrades().getItem(3).isEmpty() && tally(p, s, m, items).equals(base);
        log.append(" shift-click out=").append(out).append(' ').append(state(p, s, m, items));
        result("sub_menu_swap", ok && a && refused && thrust && b && c && q && out, log + " [FakePlayer + SubmarineUpgradeMenu.clicked]");
        m.setCarried(ItemStack.EMPTY);
        s.discard();
    }

    private static String lifeLine(Component c)
    {
        for (Component sib : c.getSiblings())
            if (sib.getContents() instanceof TranslatableContents tc && tc.getKey().endsWith("sonar.life"))
                return java.util.Arrays.toString(tc.getArgs());
        return "none";
    }

    private static long lifeNearest(Component c)
    {
        for (Component sib : c.getSiblings())
            if (sib.getContents() instanceof TranslatableContents tc && tc.getKey().endsWith("sonar.life") && tc.getArgs().length > 1 && tc.getArgs()[1] instanceof Number n)
                return n.longValue();
        return -1;
    }

    private static void sonarEntity()
    {
        double y = ay + 4;
        Submarine s = newSub(SX, y, 4.5);
        var fish = com.abyssia.registry.ModEntities.ANGLERFISH.get().create(level);
        fish.setPos(SX, y, 44.5);
        if (!level.addFreshEntity(fish)) throw new IllegalStateException("fish refused (chunk not entity-loaded)");
        spawned.add(fish);
        double dist = Math.sqrt(fish.distanceToSqr(s.getBoundingBox().getCenter()));
        s.upgrades().setItem(Kind.UTILITY.slot(), mk2(3));
        Component c2 = SubmarineSonar.scan(s, new java.util.HashSet<>());
        int range2 = SubmarineSonar.range(s.upgradeMask());
        s.upgrades().setItem(Kind.UTILITY.slot(), mk1(3));
        Component c1 = SubmarineSonar.scan(s, new java.util.HashSet<>());
        int range1 = SubmarineSonar.range(s.upgradeMask());
        long n2 = lifeNearest(c2), n1 = lifeNearest(c1);
        boolean ok = n2 >= 0 && n2 <= 41 && (n1 < 0 || n1 < 30);
        result("sonar_entity", ok, "fish at " + String.format("%.1f", dist) + " blocks | Mk2 (range " + range2 + ") life=" + lifeLine(c2)
                + " | Mk1 (range " + range1 + ") life=" + lifeLine(c1) + " [SubmarineSonar.scan on the server]");
        fish.discard();
        s.discard();
    }

    private static String inventorySummary(Player p)
    {
        Map<String, Integer> sum = new LinkedHashMap<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
        {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty()) sum.merge(id(st), st.getCount(), Integer::sum);
        }
        return sum.toString();
    }

    private static void excavatorBuild()
    {
        BuildEntry entry = BuildRegistry.get("abyssal_excavator_mk3");
        if (entry == null)
        {
            result("excavator_mk3_build", false, "build entry abyssal_excavator_mk3 is not registered");
            return;
        }
        List<ItemStack> cost = entry.cost();
        int fang = 0, tungsten = 0, hadal = 0;
        StringBuilder costs = new StringBuilder();
        for (ItemStack st : cost)
        {
            costs.append(id(st)).append('x').append(st.getCount()).append(' ');
            if (st.is(item("trench_wyrm_fang"))) fang = st.getCount();
            if (st.is(item("tungsten_ingot"))) tungsten = st.getCount();
            if (st.is(item("hadal_plating"))) hadal = st.getCount();
        }
        boolean aOk = fang == 6 && tungsten == 4 && hadal == 2;
        ServerPlayer p = arenaPlayer("trp01b");
        int unitId = -1;
        try
        {
            ResearchManager.reset(p, null);
            ResearchManager.unlock(p, rl("technology/wreck_research"));
            HabitatBuilder.Result locked = HabitatBuilder.startBuild(p, entry, 0, 6);
            ResearchManager.unlock(p, rl("technology/abyss_trophies"));
            for (ItemStack st : cost) p.getInventory().add(st.copy());
            p.getInventory().add(new ItemStack(item("trench_wyrm_fang"), 3));
            int fangBefore = HabitatBuilder.count(p, item("trench_wyrm_fang"));
            HabitatBuilder.Result started = HabitatBuilder.startBuild(p, entry, 0, 6);
            int fangAfter = HabitatBuilder.count(p, item("trench_wyrm_fang")), tungstenAfter = HabitatBuilder.count(p, item("tungsten_ingot")),
                    hadalAfter = HabitatBuilder.count(p, item("hadal_plating"));
            String left = inventorySummary(p);
            BuildPlacement placement = HabitatBuilder.activePlacement(p.getUUID()).orElse(null);
            boolean cancelled = HabitatBuilder.cancelBuild(p);
            int fangRefund = HabitatBuilder.count(p, item("trench_wyrm_fang"));
            boolean consumed = started == HabitatBuilder.Result.STARTED && fangBefore == 9 && fangAfter == 3 && tungstenAfter == 0 && hadalAfter == 0;
            boolean cancelOk = cancelled && fangRefund == 9;
            if (placement == null)
            {
                result("excavator_mk3_build", false, "cost: " + costs + "| locked->" + locked + " started->" + started + " no placement");
                return;
            }
            // HabitatBuilder.Job (the timed assembly) needs the player in the server player list; a FakePlayer is not, so the finishing
            // step is replayed here with the entry's own layout / complete and the BuiltUnits record, then dismantled with the real tool code.
            BlockPos master = placement.origin();
            BuildLayout layout = entry.layout(level, placement);
            for (BuildStep step : layout.steps()) level.setBlock(step.pos(), step.state(), Block.UPDATE_ALL);
            for (BlockPos pos : layout.air()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (BlockPos pos : layout.water()) level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            entry.complete(level, p, placement);
            AABB b = placement.box();
            List<ItemStack> paid = new ArrayList<>();
            for (ItemStack st : cost) paid.add(st.copy());
            unitId = BuiltUnits.get(level).add(entry.id(), master, placement.rot(), new BoundingBox((int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ),
                    (int) Math.ceil(b.maxX) - 1, (int) Math.ceil(b.maxY) - 1, (int) Math.ceil(b.maxZ) - 1), paid, -1);
            boolean masterOk = level.getBlockState(master).is(ModIndustry.ABYSSAL_EXCAVATOR_MK3.get());
            var be = level.getBlockEntity(master);
            String tier = be instanceof AbyssalExcavatorBlockEntity ex ? ex.tier().toString() : "no BE";
            boolean tierOk = tier.equals(ExcavatorTier.MK3.toString());
            int parts = 0, partCells = ExcavatorStructure.partCells(master).size();
            for (BlockPos pos : ExcavatorStructure.partCells(master)) if (level.getBlockState(pos).is(ModIndustry.EXCAVATOR_PART.get())) parts++;
            p.getInventory().clearContent();
            String problem = Dismantler.dismantleNow(level, p, unitId);
            String refund = inventorySummary(p);
            boolean gone = !level.getBlockState(master).is(ModIndustry.ABYSSAL_EXCAVATOR_MK3.get());
            int partsLeft = 0;
            for (BlockPos pos : ExcavatorStructure.partCells(master)) if (level.getBlockState(pos).is(ModIndustry.EXCAVATOR_PART.get())) partsLeft++;
            boolean recordGone = BuiltUnits.get(level).get(unitId) == null;
            if (recordGone) unitId = -1;
            boolean ok = aOk && locked == HabitatBuilder.Result.PERMISSION && consumed && cancelOk && masterOk && tierOk && parts == partCells && problem == null && gone
                    && partsLeft == 0 && recordGone;
            result("excavator_mk3_build", ok, "(a) cost: " + costs.toString().trim() + " | locked (no abyss_trophies)->" + locked + " | start->" + started + " paid: fang " + fangBefore + "->" + fangAfter
                    + " tungsten->" + tungstenAfter + " hadal->" + hadalAfter + " left=" + left + " | cancel refund fang=" + fangRefund + " | (b) master=" + masterOk + " tier=" + tier
                    + " parts=" + parts + "/" + partCells + " | (c) dismantle problem=" + problem + " masterGone=" + gone + " partsLeft=" + partsLeft + " recordGone=" + recordGone
                    + " refund (80%)=" + refund + " [HabitatBuilder.startBuild/cancelBuild + replayed Job.tick + Dismantler.dismantleNow]");
        }
        finally
        {
            if (unitId >= 0) BuiltUnits.get(level).remove(unitId);
            HabitatBuilder.cancelBuild(p);
            ResearchManager.reset(p, null);
        }
    }

    private static void itemsFoil()
    {
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (int i = 0; i < 4; i++)
        {
            boolean f = mk2(i).hasFoil(), g = mk1(i).hasFoil();
            ok &= f && !g;
            sb.append(id(mk2(i))).append('=').append(f).append(' ').append(id(mk1(i))).append('=').append(g).append(' ');
        }
        ItemStack[] on = {new ItemStack(MaterialTools.HEART_TANK.get()), new ItemStack(MaterialTools.FANG_DRILL.get())};
        ItemStack[] off = {new ItemStack(MaterialTools.PRESSURE_DIVE_TANK.get()), new ItemStack(MaterialTools.ABYSSAL_DRILL.get())};
        for (int i = 0; i < 2; i++)
        {
            boolean f = on[i].hasFoil(), g = off[i].hasFoil();
            ok &= f && !g;
            sb.append(id(on[i])).append('=').append(f).append(' ').append(id(off[i])).append('=').append(g).append(' ');
        }
        result("items_foil", ok, sb.toString().trim());
    }

    private static void heartTankBreath()
    {
        ServerPlayer p = arenaPlayer("trp01h");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MaterialTools.PRESSURE_DIVER_HELMET.get()));
        // control: the helmet alone, 60 ticks
        int maxAir = p.getMaxAirSupply();
        int controlAir = maxAir;
        for (int i = 0; i < 60; i++) controlAir = tickPlayer(p);
        p.setAirSupply(maxAir);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MaterialTools.HEART_TANK.get()));
        int full = DiveTank.oxygen(p.getItemBySlot(EquipmentSlot.CHEST));
        boolean wet = p.isEyeInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get());
        int minAir = maxAir;
        for (int i = 0; i < 200; i++) minAir = Math.min(minAir, tickPlayer(p));
        int left = DiveTank.oxygen(p.getItemBySlot(EquipmentSlot.CHEST));
        boolean ok = wet && full == 1800 && left < full && minAir == maxAir && controlAir < maxAir && DiveTank.clock(full).equals("30:00");
        result("heart_tank_breath", ok, "eyesInWater=" + wet + " full=" + full + "s (" + DiveTank.clock(full) + ") after 200 ticks=" + left + "s (-" + (full - left) + "s) min air with tank=" + minAir + "/" + maxAir
                + " | helmet only 60 ticks air=" + controlAir + "/" + maxAir + " [FakePlayer, DivingBreathing START/END handlers + baseTick]");
    }

    /** one player tick of the breathing code (a FakePlayer's tick() is empty): Pre handler, baseTick (drowning air), Post handler */
    private static int tickPlayer(ServerPlayer p)
    {
        DivingBreathing.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, p));
        p.baseTick();
        DivingBreathing.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
        return p.getAirSupply();
    }

    private static void finish(MinecraftServer server)
    {
        finished = true;
        LOG.info("TRP01 DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }
}
