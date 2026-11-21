package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.upkeep.HabitatUpkeep;
import com.abyssia.habitat.upkeep.UpkeepContent;
import com.abyssia.industry.ExcavatorEntry;
import com.abyssia.industry.ExcavatorStructure;
import com.abyssia.industry.ExcavatorTier;
import com.abyssia.industry.block.IndustryEntityBlock;
import com.abyssia.industry.blockentity.ExcavatorPartBlockEntity;
import com.abyssia.industry.blockentity.PrecipitatorBlockEntity;
import com.abyssia.industry.client.ExcavatorRenderer;
import net.minecraft.world.level.block.Block;
import com.abyssia.logistics.CargoUplinkBlock;
import com.abyssia.logistics.CargoUplinkBlockEntity;
import com.abyssia.logistics.LogisticsContent;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModHabitat;
import com.abyssia.registry.ModIndustry;
import com.abyssia.registry.ModItems;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.SubmarineUpgradeMenu;
import com.abyssia.vehicle.SubmarineUpgrades;
import com.abyssia.vehicle.VehicleContent;
import com.abyssia.waypoint.BeaconTravel;
import com.abyssia.waypoint.BeaconTravelPacket;
import com.abyssia.waypoint.WaypointBeaconBlockEntity;
import com.abyssia.waypoint.WaypointRegistry;
import com.abyssia.waypoint.client.WaypointClient;
import com.abyssia.waypoint.client.WaypointTravelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Dev-only: with -Dabyssia.contentshot (gradle runContentShot) the client enters the singleplayer world PrcGuiShot and
 * screenshots the content of the last batch (precipitator / pumps / uplinks, new items, uplink GUI, beacon travel,
 * tooltips, habitat upkeep) into run/screenshots/content_*.png, in two languages, then quits. Log lines "CONTENTSHOT ...".
 * The integrated server shares the JVM, so one client-driven script drives both sides (server work goes through
 * MinecraftServer#execute).
 */
public final class ContentShot
{
    private static final boolean ON = System.getProperty("abyssia.contentshot") != null;

    private static volatile BlockPos origin, beaconA, beaconB, uplinkSend;
    private static volatile boolean active;
    private static volatile String failScene, failReason;
    private static final List<BlockPos> POWERED = new CopyOnWriteArrayList<>();
    private static final List<BlockPos> BEACONS = new CopyOnWriteArrayList<>();

    // sub_drive / sub_upgrade (server-side probe of the submarine: x, z, energy once per server tick)
    private static volatile Submarine probeSub;
    private static volatile int subId = -1, boxX, boxY, boxZ;
    private static final List<double[]> PROBE = new ArrayList<>();
    private static final Map<BlockPos, BlockState> SAVED = new LinkedHashMap<>();

    private static final String[] ITEMS = {"kraken_beak", "kraken_ink_sac", "mother_bell_gel", "abyss_heart", "trench_wyrm_fang",
            "angler_lure", "abyss_serpent_scale", "submarine_upgrade_serpent_scale_hull", "submarine_upgrade_jet_thruster",
            "submarine_upgrade_abyss_heart_cell", "submarine_upgrade_lure_sonar", "heart_tank", "fang_drill", "hull_patch_kit",
            "hull_reinforcement", "supply_contract", "logistics_pump", "cargo_uplink", "hydrothermal_precipitator",
            "submarine_upgrade_pressure_hull", "pressure_dive_tank", "abyssal_drill"};

    private ContentShot() {}

    private static void log(String s)
    {
        System.out.println("CONTENTSHOT " + s);
    }

    // ------------------------------------------------------------------ server side

    @EventBusSubscriber(modid = Abyssia.MODID)
    public static final class Server
    {
        private static int tick;

        @SubscribeEvent
        public static void probe(ServerTickEvent.Post e)
        {
            if (!ON) return;
            Submarine s = probeSub;
            if (s == null || s.isRemoved()) return;
            synchronized (PROBE) { PROBE.add(new double[]{s.getX(), s.getZ(), s.getEnergy()}); }
        }

        @SubscribeEvent
        public static void tick(ServerTickEvent.Post e)
        {
            if (!ON || !active) return;
            var players = e.getServer().getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            ServerPlayer p = players.get(0);
            ServerLevel level = p.serverLevel();
            tick++;
            for (BlockPos pos : POWERED)
                if (level.getBlockEntity(pos) instanceof PrecipitatorBlockEntity be) be.energyStorage(null).receiveEnergy(256, false);
            for (BlockPos pos : BEACONS)
                if (level.getBlockEntity(pos) instanceof WaypointBeaconBlockEntity be) be.energy().setEnergy(be.energy().getMaxEnergyStored());
            if (tick % 100 == 1)
            {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
                level.setDayTime(6000);
                p.setAirSupply(p.getMaxAirSupply());
            }
        }
    }

    // ------------------------------------------------------------------ client script

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class Client
    {
        private record Step(String scene, BooleanSupplier run, int delay, int timeout) {}

        private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
        private static boolean built, mouseFail;
        private static int ct, wait, tries;
        private static String lang1, lang2, beforePos = "";

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e)
        {
            if (!ON) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (++ct < 100) return;
            if (!built)
            {
                built = true;
                build(mc);
            }
            String fs = failScene;
            if (fs != null)
            {
                String reason = failReason;
                failScene = null;
                fail(mc, fs, reason);
            }
            if (wait > 0) { wait--; return; }
            Step s = STEPS.peek();
            if (s == null) return;
            boolean ok;
            try { ok = s.run().getAsBoolean(); }
            catch (Throwable t) { fail(mc, s.scene(), t.toString()); return; }
            if (ok)
            {
                STEPS.poll();
                tries = 0;
                wait = s.delay();
            }
            else if (++tries > s.timeout()) fail(mc, s.scene(), "timeout");
        }

        private static void fail(Minecraft mc, String scene, String reason)
        {
            log("FAIL " + scene + " " + reason);
            STEPS.removeIf(x -> x.scene().equals(scene));
            tries = 0;
            wait = 0;
            if (mc.screen != null && mc.player != null) mc.player.closeContainer();
            if (mc.screen != null) mc.setScreen(null);
        }

        // ---- step helpers

        private static void cl(String scene, int delay, Runnable r)
        {
            STEPS.add(new Step(scene, () -> { r.run(); return true; }, delay, 100));
        }

        private static void until(String scene, int delay, int timeout, BooleanSupplier c)
        {
            STEPS.add(new Step(scene, c, delay, timeout));
        }

        private static void sv(String scene, int delay, Consumer<ServerPlayer> fn)
        {
            STEPS.add(new Step(scene, () ->
            {
                var server = Minecraft.getInstance().getSingleplayerServer();
                if (server == null) throw new IllegalStateException("no integrated server");
                server.execute(() ->
                {
                    try
                    {
                        var list = server.getPlayerList().getPlayers();
                        if (list.isEmpty()) throw new IllegalStateException("no player");
                        fn.accept(list.get(0));
                    }
                    catch (Throwable t)
                    {
                        failReason = t.toString();
                        failScene = scene;
                    }
                });
                return true;
            }, delay, 100));
        }

        private static void shot(String scene, String name, int delay)
        {
            cl(scene, delay, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                new java.io.File(mc.gameDirectory, "screenshots").mkdirs();
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> log("saved " + name));
            });
        }

        private static void closeScreen(String scene)
        {
            cl(scene, 5, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.closeContainer();
                if (mc.screen != null) mc.setScreen(null);
            });
        }

        // ---- script

        private static void build(Minecraft mc)
        {
            lang1 = mc.options.languageCode;
            lang2 = "ja_jp".equals(lang1) ? "en_us" : "ja_jp";
            log("lang pass1=" + lang1 + " pass2=" + lang2);
            sv("init", 10, p ->
            {
                origin = p.blockPosition();
                active = true;
                p.setInvulnerable(true);
                var level = p.serverLevel();
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                level.setWeatherParameters(6000, 0, false, false);
                level.setDayTime(6000);
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
            });
            pass("", 0, true);
            cl("lang", 200, () ->
            {
                mc.options.languageCode = lang2;
                mc.getLanguageManager().setSelected(lang2);
                mc.reloadResourcePacks();
            });
            pass("_" + lang2.substring(0, 2), 24, false);
            cl("end", 20, () -> log("done"));
            cl("end", 0, mc::stop);
        }

        private static void pass(String sfx, int habitatDx, boolean first)
        {
            blocks(sfx);
            inventory(sfx);
            uplinkGui(sfx);
            travel(sfx);
            if (first)
            {
                travelGo();
                tooltips();
            }
            habitat(sfx, habitatDx);
            if (first)
            {
                kitUse();
                subDrive();
                titan();
                excavator();
            }
        }

        // ---- 1 blocks

        private static void blocks(String sfx)
        {
            String sc = "blocks";
            sv(sc, 50, p ->
            {
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                ServerLevel l = p.serverLevel();
                BlockPos o = origin;
                int by = o.getY() + 1, z = o.getZ() + 6;
                clearBox(l, o.getX() - 9, o.getY(), o.getZ() - 1, o.getX() + 9, o.getY() + 5, o.getZ() + 9);
                platform(l, o.getX() - 9, o.getZ() - 1, o.getX() + 9, o.getZ() + 9, o.getY() - 1);
                p.teleportTo(l, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0f, 5f);
                POWERED.clear();
                var prec = ModIndustry.HYDROTHERMAL_PRECIPITATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
                BlockPos p0 = new BlockPos(o.getX() + 5, by, z);
                l.setBlock(p0, prec, 3);
                l.setBlock(p0.east(), ModBlocks.THERMAL_VENT.get().defaultBlockState()
                        .setValue(ThermalVentBlock.TYPE, ThermalVentType.BLACK_SMOKER).setValue(ThermalVentBlock.ACTIVITY, VentActivity.ACTIVE), 3);
                BlockPos p1 = p0.west(2);
                l.setBlock(p1, prec, 3);
                POWERED.add(p0);
                POWERED.add(p1);
                var pipe = ModIndustry.INDUSTRIAL_PIPE.get().defaultBlockState();
                var pump = LogisticsContent.LOGISTICS_PUMP.get().defaultBlockState();
                BlockPos pu1 = p0.west(4);
                l.setBlock(pu1, pump.setValue(BlockStateProperties.FACING, Direction.UP), 3);
                l.setBlock(pu1.below(), pipe, 3);
                l.setBlock(pu1.above(), pipe, 3);
                BlockPos pu2 = p0.west(6);
                l.setBlock(pu2, pump.setValue(BlockStateProperties.FACING, Direction.NORTH), 3);
                l.setBlock(pu2.north(), pipe, 3);
                l.setBlock(pu2.south(), pipe, 3);
                var up = LogisticsContent.CARGO_UPLINK.get().defaultBlockState().setValue(CargoUplinkBlock.FACING, Direction.NORTH);
                uplinkSend = p0.west(8);
                l.setBlock(uplinkSend, up.setValue(CargoUplinkBlock.RECEIVER, false), 3);
                l.setBlock(p0.west(10), up.setValue(CargoUplinkBlock.RECEIVER, true), 3);
            });
            shot(sc, "content_blocks" + sfx + ".png", 5);
            sv(sc, 40, p ->
            {
                BlockPos o = origin;
                p.getAbilities().mayfly = true;
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), o.getX() + 0.5, o.getY() + 5.0, o.getZ() + 1.5, 0f, 40f);
            });
            shot(sc, "content_blocks_top" + sfx + ".png", 5);
        }

        // ---- 2 inventory

        private static void inventory(String sfx)
        {
            String sc = "inventory";
            sv(sc, 30, p ->
            {
                BlockPos o = origin;
                p.getAbilities().flying = false;
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(p.serverLevel(), o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0f, 5f);
                fill(p, sfx.isEmpty());
            });
            until(sc, 25, 100, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                if (!(mc.screen instanceof InventoryScreen)) mc.setScreen(new InventoryScreen(mc.player));
                return mc.screen instanceof InventoryScreen;
            });
            shot(sc, "content_inventory" + sfx + ".png", 0);
            closeScreen(sc);
        }

        private static void fill(ServerPlayer p, boolean report)
        {
            p.getInventory().clearContent();
            int slot = 0;
            for (String id : ITEMS)
            {
                var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, id));
                if (item.isEmpty())
                {
                    if (report) log("FAIL inventory missing " + id);
                    continue;
                }
                p.getInventory().setItem(slot++, new ItemStack(item.get()));
            }
        }

        // ---- 3 uplink_gui

        private static void uplinkGui(String sfx)
        {
            String sc = "uplink_gui";
            sv(sc, 0, p ->
            {
                if (!(p.serverLevel().getBlockEntity(uplinkSend) instanceof CargoUplinkBlockEntity be))
                    throw new IllegalStateException("no cargo uplink at " + uplinkSend);
                be.setItem(0, new ItemStack(ModItems.RAW_COBALT.get(), 12));
                be.setItem(1, new ItemStack(ModItems.RAW_NICKEL.get(), 5));
                be.setItem(2, new ItemStack(Items.IRON_INGOT, 32));
                be.setItem(3, new ItemStack(Items.COPPER_INGOT, 16));
                be.setItem(4, new ItemStack(Items.GLASS, 64));
                p.openMenu(be);
            });
            until(sc, 20, 100, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                return mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof InventoryScreen);
            });
            shot(sc, "content_uplink_gui" + sfx + ".png", 0);
            closeScreen(sc);
        }

        // ---- 4 travel / 5 travel_go

        private static void travel(String sfx)
        {
            String sc = "travel";
            sv(sc, 30, p ->
            {
                ServerLevel l = p.serverLevel();
                BlockPos o = origin;
                int z = o.getZ() - 30;
                beaconA = new BlockPos(o.getX(), o.getY(), z + 3);
                beaconB = new BlockPos(o.getX() + 60, o.getY(), z + 3);
                BEACONS.clear();
                for (BlockPos b : new BlockPos[] {beaconA, beaconB})
                {
                    l.getChunk(b.getX() >> 4, b.getZ() >> 4);
                    int zMin = b == beaconA ? z - 2 : b.getZ() - 3, zMax = b == beaconA ? z + 6 : b.getZ() + 3;
                    clearBox(l, b.getX() - 3, b.getY(), zMin, b.getX() + 3, b.getY() + 4, zMax);
                    platform(l, b.getX() - 3, zMin, b.getX() + 3, zMax, b.getY() - 1);
                    l.setBlock(b, ModIndustry.WAYPOINT_BEACON.get().defaultBlockState(), 3);
                    if (!(l.getBlockEntity(b) instanceof WaypointBeaconBlockEntity be)) throw new IllegalStateException("no beacon entity at " + b);
                    be.setOwner(p.getUUID());
                    be.energy().setEnergy(be.energy().getMaxEnergyStored());
                    WaypointRegistry.refresh(l, b);
                    BEACONS.add(b);
                }
                p.teleportTo(l, o.getX() + 0.5, o.getY(), z + 0.5, 0f, 5f);
                WaypointRegistry.sendTo(p);
            });
            until(sc, 10, 300, () ->
            {
                boolean a = false, b = false;
                for (var e : WaypointClient.beacons())
                {
                    a |= e.pos().equals(beaconA);
                    b |= e.pos().equals(beaconB);
                }
                return a && b;
            });
            until(sc, 25, 100, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                if (!(mc.screen instanceof WaypointTravelScreen))
                {
                    if (mc.screen != null) mc.setScreen(null);
                    WaypointClient.openTravelScreen(beaconA);
                }
                return mc.screen instanceof WaypointTravelScreen;
            });
            shot(sc, "content_travel" + sfx + ".png", 0);
            closeScreen(sc);
        }

        private static void travelGo()
        {
            String sc = "travel_go";
            cl(sc, 20, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                beforePos = fmt(mc.player.position());
                AbyssiaNetwork.sendToServer(new BeaconTravelPacket(beaconA, beaconB));
            });
            sv(sc, 80, p ->
            {
                Vec3 dest = BeaconTravel.landing(p.serverLevel(), beaconB);
                log("travel pos before=" + beforePos + " after=" + fmt(p.position()) + " dest=" + (dest == null ? "none" : fmt(dest)));
                if (dest == null || p.position().distanceToSqr(dest) > 4.0) throw new IllegalStateException("player was not teleported");
            });
            shot(sc, "content_travel_go.png", 0);
        }

        private static String fmt(Vec3 v)
        {
            return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", v.x, v.y, v.z);
        }

        // ---- 6 tooltips

        private static void tooltips()
        {
            String sc = "tooltips";
            sv(sc, 20, p ->
            {
                p.setGameMode(GameType.SURVIVAL);
                fill(p, false);
                p.getInventory().setItem(35, new ItemStack(ModItems.MUSHROOM_PIE.get()));
            });
            until(sc, 20, 100, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                if (!(mc.screen instanceof InventoryScreen)) mc.setScreen(new InventoryScreen(mc.player));
                return mc.screen instanceof InventoryScreen;
            });
            for (String id : new String[] {"mushroom_pie", "supply_contract", "heart_tank", "submarine_upgrade_serpent_scale_hull"})
            {
                cl(sc, 12, () -> hover(Minecraft.getInstance(), id));
                cl(sc, 3, () -> { if (!mouseFail) shotNow("content_tooltips_" + id + ".png"); });
            }
            closeScreen(sc);
        }

        private static void shotNow(String name)
        {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            new java.io.File(mc.gameDirectory, "screenshots").mkdirs();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> log("saved " + name));
        }

        private static void hover(Minecraft mc, String id)
        {
            if (!(mc.screen instanceof InventoryScreen scr)) throw new IllegalStateException("no inventory screen");
            Slot target = null;
            for (Slot s : scr.getMenu().slots)
            {
                ItemStack st = s.getItem();
                if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(id))
                {
                    target = s;
                    break;
                }
            }
            if (target == null) throw new IllegalStateException("item not in inventory " + id);
            StringBuilder sb = new StringBuilder();
            for (Component c : Screen.getTooltipFromItem(mc, target.getItem()))
            {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(c.getString());
            }
            log("tooltip " + id + ": " + sb);
            try
            {
                var w = mc.getWindow();
                double gx = scr.getGuiLeft() + target.x + 8, gy = scr.getGuiTop() + target.y + 8;
                var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, gx * w.getScreenWidth() / w.getGuiScaledWidth());
                fy.setDouble(mc.mouseHandler, gy * w.getScreenHeight() / w.getGuiScaledHeight());
            }
            catch (Throwable t)
            {
                if (!mouseFail) log("tooltip mouse move failed (" + t + "), screenshots skipped");
                mouseFail = true;
            }
        }

        // ---- 7 habitat

        private static void habitat(String sfx, int dx)
        {
            String sc = "habitat";
            int[] id = {-1};
            BlockPos[] wall = {null};
            sv(sc, 50, p ->
            {
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
                ServerLevel l = p.serverLevel();
                BlockPos o = origin;
                int x0 = o.getX() + 30 + dx, y0 = o.getY(), z0 = o.getZ() + 20;
                l.getChunk(x0 >> 4, z0 >> 4);
                clearBox(l, x0 - 1, y0, z0 - 1, x0 + 5, y0 + 5, z0 + 5);
                for (int x = x0; x <= x0 + 4; x++)
                    for (int y = y0; y <= y0 + 4; y++)
                        for (int z = z0; z <= z0 + 4; z++)
                        {
                            boolean edge = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                            BlockState s = y == y0 ? ModHabitat.FLOOR.get().defaultBlockState()
                                    : y == y0 + 4 ? ModHabitat.CEILING.get().defaultBlockState()
                                    : edge ? ModHabitat.WALL.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
                            l.setBlock(new BlockPos(x, y, z), s, 3);
                        }
                var bases = HabitatBases.get(l);
                id[0] = bases.add(new BoundingBox(x0, y0, z0, x0 + 4, y0 + 4, z0 + 4));
                var data = HabitatUpkeep.get(l);
                data.forget(id[0]);
                data.setIntegrity(id[0], 25_500);
                bases.setEnergy(bases.root(id[0]), 400_000);
                wall[0] = new BlockPos(x0 + 2, y0 + 2, z0 + 4);
                p.teleportTo(l, x0 + 2.5, y0 + 1, z0 + 2.5, 0f, 0f);
            });
            sv(sc, 6, p ->
            {
                // the same event a sneaking, empty-handed right click on a shell block raises
                p.setShiftKeyDown(true);
                NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, wall[0],
                        new BlockHitResult(Vec3.atCenterOf(wall[0]), Direction.NORTH, wall[0], false)));
                p.setShiftKeyDown(false);
            });
            shot(sc, "content_habitat_status" + sfx + ".png", 100);
            shot(sc, "content_habitat_leak" + sfx + ".png", 4);
            shot(sc, "content_habitat_leak2" + sfx + ".png", 0);
            sv(sc, 4, p ->
            {
                ServerLevel l = p.serverLevel();
                if (!HabitatUpkeep.get(l).forceQuake(l, HabitatBases.get(l).root(id[0]), 600)) throw new IllegalStateException("forceQuake returned false");
            });
            shot(sc, "content_habitat_quake" + sfx + ".png", 0);
        }

        // ---- 8 kit_use: hull patch kit / reinforcement plate through the client's own right-click path

        private static boolean serverSneaking(Minecraft mc)
        {
            var server = mc.getSingleplayerServer();
            if (server == null) return false;
            var list = server.getPlayerList().getPlayers();
            return !list.isEmpty() && list.get(0).isShiftKeyDown();
        }

        private static void sneak(Minecraft mc, boolean on)
        {
            mc.options.keyShift.setDown(on);
            if (mc.player != null)
            {
                mc.player.setShiftKeyDown(on);
                mc.player.input.shiftKeyDown = on;
            }
        }

        private static String overlay(Minecraft mc)
        {
            try
            {
                var f = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
                f.setAccessible(true);
                Object o = f.get(mc.gui);
                return o instanceof Component c ? c.getString() : "none";
            }
            catch (Throwable t)
            {
                return "n/a(" + t.getClass().getSimpleName() + ")";
            }
        }

        /** a right click on the shell wall exactly as Minecraft#startUseItem makes it (MultiPlayerGameMode#useItemOn) */
        private static void click(Minecraft mc, BlockPos wall, String[] res)
        {
            Vec3 loc = new Vec3(wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ());
            var r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(loc, Direction.NORTH, wall, false));
            if (r.consumesAction() && r.shouldSwing()) mc.player.swing(InteractionHand.MAIN_HAND);
            res[0] = r.toString();
            res[1] = String.valueOf(mc.player.swinging);
        }

        /** read state, click, read state, report; st = {integrity, count, reinforced} before then after */
        private static void useAndReport(String sc, String label, int[] id, BlockPos[] wall, int[] st, String[] res,
                java.util.function.Function<int[], String> verdict)
        {
            sv(sc, 3, p ->
            {
                var d = HabitatUpkeep.get(p.serverLevel());
                st[0] = d.integrity(id[0]);
                st[1] = p.getMainHandItem().getCount();
                st[2] = d.isReinforced(id[0]) ? 1 : 0;
            });
            cl(sc, 6, () -> click(Minecraft.getInstance(), wall[0], res));
            sv(sc, 3, p ->
            {
                var d = HabitatUpkeep.get(p.serverLevel());
                st[3] = d.integrity(id[0]);
                st[4] = p.getMainHandItem().getCount();
                st[5] = d.isReinforced(id[0]) ? 1 : 0;
            });
            cl(sc, 0, () ->
            {
                log(label + " integrity before=" + st[0] + " after=" + st[3] + " count before=" + st[1] + " after=" + st[4]
                        + " reinforced before=" + (st[2] == 1) + " after=" + (st[5] == 1) + " result=" + res[0] + " swung=" + res[1]
                        + " msg='" + overlay(Minecraft.getInstance()) + "'");
                String why = verdict.apply(st);
                if (why != null) log("FAIL kit_use " + label + " " + why);
            });
        }

        private static void kitUse()
        {
            String sc = "kit_use";
            int[] id = {-1};
            BlockPos[] wall = {null};
            int[] st = new int[6];
            String[] res = {"?", "?"};
            sv(sc, 40, p ->
            {
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
                ServerLevel l = p.serverLevel();
                BlockPos o = origin;
                int x0 = o.getX() + 30, y0 = o.getY(), z0 = o.getZ() + 45;
                l.getChunk(x0 >> 4, z0 >> 4);
                clearBox(l, x0 - 1, y0, z0 - 1, x0 + 5, y0 + 5, z0 + 5);
                for (int x = x0; x <= x0 + 4; x++)
                    for (int y = y0; y <= y0 + 4; y++)
                        for (int z = z0; z <= z0 + 4; z++)
                        {
                            boolean edge = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                            BlockState s = y == y0 ? ModHabitat.FLOOR.get().defaultBlockState()
                                    : y == y0 + 4 ? ModHabitat.CEILING.get().defaultBlockState()
                                    : edge ? ModHabitat.WALL.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
                            l.setBlock(new BlockPos(x, y, z), s, 3);
                        }
                var bases = HabitatBases.get(l);
                id[0] = bases.add(new BoundingBox(x0, y0, z0, x0 + 4, y0 + 4, z0 + 4));
                var data = HabitatUpkeep.get(l);
                data.forget(id[0]);
                data.setIntegrity(id[0], 25_000);
                bases.setEnergy(bases.root(id[0]), 400_000);
                wall[0] = new BlockPos(x0 + 2, y0 + 2, z0 + 4);
                p.getInventory().selected = 0;
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UpkeepContent.HULL_PATCH_KIT.get(), 2));
                p.teleportTo(l, x0 + 2.5, y0 + 1, z0 + 2.5, 0f, 0f);
            });
            cl(sc, 20, () -> sneak(Minecraft.getInstance(), false));
            // 1. no sneak: the item must not be used
            useAndReport(sc, "kit_nosneak", id, wall, st, res, a ->
                    a[3] == a[0] && a[4] == a[1] && a[1] == 2 ? null : "a right click without sneaking used the kit");
            // 2. sneak (the server has to see it)
            cl(sc, 2, () -> sneak(Minecraft.getInstance(), true));
            until(sc, 3, 80, () -> serverSneaking(Minecraft.getInstance()));
            useAndReport(sc, "kit", id, wall, st, res, a ->
                    Math.abs((a[3] - a[0]) - 25_000) <= 100 && a[1] == 2 && a[4] == 1 ? null : "expected +25000 (+-100) and 2 -> 1");
            shot(sc, "content_kit_use.png", 0);
            // 3. reinforcement plate (2 -> 1, a second use is refused without consuming)
            sv(sc, 8, p -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UpkeepContent.HULL_REINFORCEMENT.get(), 2)));
            useAndReport(sc, "reinforce", id, wall, st, res, a ->
                    a[2] == 0 && a[5] == 1 && a[1] == 2 && a[4] == 1 ? null : "expected reinforced false -> true and 2 -> 1");
            shot(sc, "content_reinforce_use.png", 0);
            useAndReport(sc, "reinforce2", id, wall, st, res, a ->
                    a[2] == 1 && a[5] == 1 && a[1] == 1 && a[4] == 1 ? null : "the second plate use must not consume");
            cl(sc, 2, () -> sneak(Minecraft.getInstance(), false));
        }

        // ---- 9 sub_drive: real piloting of a submarine, then the upgrade screen with a real player

        private static ItemStack[] ups(int cfg)
        {
            return switch (cfg)
            {
                case 0 -> new ItemStack[0];
                case 1 -> new ItemStack[]{new ItemStack(SubmarineUpgrades.ITEMS[SubmarineUpgrades.THRUSTER].get())};
                case 2 -> new ItemStack[]{new ItemStack(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.THRUSTER].get())};
                case 3 -> new ItemStack[]{new ItemStack(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.THRUSTER].get()),
                        new ItemStack(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get())};
                default -> new ItemStack[]{new ItemStack(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.THRUSTER].get()),
                        new ItemStack(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get())};
            };
        }

        /** {expected forward speed, expected FE per tick} from the current Config values */
        private static double[] expected(int cfg)
        {
            double t2 = Config.SUB_THRUSTER2_FORWARD.get();
            return switch (cfg)
            {
                case 0 -> new double[]{Config.SUBMARINE_MAX_SPEED.get(), Config.SUBMARINE_THRUST_FE.get()};
                case 1 -> new double[]{Config.SUB_THRUSTER_FORWARD.get(), Config.SUB_THRUSTER_FE.get()};
                case 2 -> new double[]{t2, Config.SUB_THRUSTER2_FE.get()};
                case 3 -> new double[]{t2 * Config.SUB_HULL2_SPEED_MULT.get(), Config.SUB_THRUSTER2_FE.get()};
                default -> new double[]{t2 * Config.SUB_HULL_SPEED_MULT.get(), Config.SUB_THRUSTER2_FE.get()};
            };
        }

        private static final String[] CFG = {"no_thruster", "thruster_mk1", "thruster_mk2", "thruster_mk2+hull_mk2", "thruster_mk2+hull_mk1"};

        private static Submarine spawnSub(ServerLevel l, double x, double y, double z, ItemStack... upgrades)
        {
            if (probeSub != null) probeSub.discard();
            Submarine sub = VehicleContent.SUBMARINE.get().create(l);
            if (sub == null) throw new IllegalStateException("submarine create returned null");
            sub.moveTo(x, y, z, 180f, 0f);
            for (ItemStack st : upgrades) sub.upgrades().setItem(SubmarineUpgrades.slotOf(st), st);
            sub.setEnergy(sub.maxEnergy());
            l.addFreshEntity(sub);
            synchronized (PROBE) { PROBE.clear(); }
            probeSub = sub;
            subId = sub.getId();
            return sub;
        }

        /** a sealed glass tank (9 x 9 x 222) high in the sky holding a 7 x 7 x 220 block water channel; the old blocks are kept in SAVED */
        private static void buildBox(ServerLevel l)
        {
            BlockPos o = origin;
            boxX = o.getX();
            boxZ = o.getZ() - 60;
            boxY = BOX_Y;   // fixed shallow depth: water Y -153..-147 (centre -150), above the rated depth and below the sea surface
            SAVED.clear();
            int x1 = boxX - 4, x2 = boxX + 4, y1 = boxY - 1, y2 = boxY + 7, zA = boxZ + 4, zB = boxZ - BOX_LEN - 1;
            boxEndZ = zB;
            // the measurement must not depend on the world: currents off while the box exists (restored in restoreBox)
            if (savedCur == null)
                savedCur = new boolean[]{Config.NATURAL_CURRENTS.get(), Config.CURRENT_PUSH_ENTITIES.get(), Config.CURRENT_PUSH_PLAYERS.get()};
            Config.NATURAL_CURRENTS.set(false);
            Config.CURRENT_PUSH_ENTITIES.set(false);
            Config.CURRENT_PUSH_PLAYERS.set(false);
            for (int cx = x1 >> 4; cx <= x2 >> 4; cx++)
                for (int cz = zB >> 4; cz <= zA >> 4; cz++)
                    l.getChunk(cx, cz);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockState water = Blocks.WATER.defaultBlockState(), glass = Blocks.GLASS.defaultBlockState();
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = zB; z <= zA; z++)
                    {
                        m.set(x, y, z);
                        SAVED.put(m.immutable(), l.getBlockState(m));
                        boolean inner = x > x1 && x < x2 && y > y1 && y < y2 && z > zB && z < zA;
                        l.setBlock(m, inner ? water : glass, 2 | 16);
                    }
        }

        private static void restoreBox(ServerLevel l)
        {
            for (Map.Entry<BlockPos, BlockState> e : SAVED.entrySet()) l.setBlock(e.getKey(), e.getValue(), 2 | 16);
            SAVED.clear();
            if (savedCur != null)
            {
                Config.NATURAL_CURRENTS.set(savedCur[0]);
                Config.CURRENT_PUSH_ENTITIES.set(savedCur[1]);
                Config.CURRENT_PUSH_PLAYERS.set(savedCur[2]);
                savedCur = null;
            }
        }

        private static final int BOX_Y = -153, BOX_LEN = 260;
        private static volatile int boxEndZ;
        private static boolean[] savedCur;

        private static String idOf(ItemStack st)
        {
            return st.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(st.getItem()).getPath() + "x" + st.getCount();
        }

        private static final double[] AVG = new double[5];

        private static void subDrive()
        {
            String sc = "sub_drive";
            sv(sc, 20, p ->
            {
                p.setGameMode(GameType.SURVIVAL);
                buildBox(p.serverLevel());
            });
            cl(sc, 0, () -> sneak(Minecraft.getInstance(), false));
            for (int i = 0; i < CFG.length; i++)
            {
                int cfg = i;
                SimpleContainer c = new SimpleContainer(SubmarineUpgrades.SLOTS);
                for (ItemStack st : ups(cfg)) c.setItem(SubmarineUpgrades.slotOf(st), st);
                int mask = SubmarineUpgrades.mask(c);
                sv(sc, 5, p ->
                {
                    ServerLevel l = p.serverLevel();
                    Submarine sub = spawnSub(l, boxX + 0.5, boxY + 2.0, boxZ, ups(cfg));
                    p.teleportTo(l, sub.getX(), sub.getY() + 0.5, sub.getZ(), 180f, 0f);
                    if (!p.startRiding(sub, true)) throw new IllegalStateException("startRiding failed");
                });
                until(sc, 5, 400, () ->
                {
                    Minecraft mc = Minecraft.getInstance();
                    return mc.screen == null && mc.player.getVehicle() instanceof Submarine s && s.upgradeMask() == mask
                            && s.submergedFraction() > 0.99 && s.getEnergy() == s.maxEnergy();
                });
                cl(sc, 0, () ->
                {
                    Minecraft mc = Minecraft.getInstance();
                    mc.player.setYRot(180f);
                    mc.player.setXRot(0f);
                    synchronized (PROBE) { PROBE.clear(); }
                });
                int[] n = {0};
                boolean[] steady = {false};
                float[] dmg0 = {0f};
                // the forward key held down, the same input path Submarine reads (SubmarineClient#input -> options.keyUp)
                // steady = at least 200 ticks of driving and the last two 20-tick windows within 1 %; give up after 400 ticks
                until(sc, 0, 1000, () ->
                {
                    Minecraft mc = Minecraft.getInstance();
                    mc.player.setYRot(180f);
                    mc.player.setXRot(0f);
                    mc.options.keyUp.setDown(true);
                    n[0]++;
                    if (n[0] < 200) return false;
                    List<double[]> s;
                    synchronized (PROBE) { s = new ArrayList<>(PROBE); }
                    int k = s.size();
                    // per-tick steps jump (a ridden sub moves by the pilot's packets: 0 or 2 steps in one server tick),
                    // so compare the distance covered by the last two 20-tick windows instead
                    if (k >= 41)
                    {
                        double a = Math.hypot(s.get(k - 21)[0] - s.get(k - 41)[0], s.get(k - 21)[1] - s.get(k - 41)[1]);
                        double b = Math.hypot(s.get(k - 1)[0] - s.get(k - 21)[0], s.get(k - 1)[1] - s.get(k - 21)[1]);
                        if (b > 0.2 && Math.abs(b - a) < 0.01 * b) steady[0] = true;
                    }
                    if (steady[0] || n[0] >= 400)
                    {
                        if (mc.level.getEntity(subId) instanceof Submarine sb) dmg0[0] = sb.getDamage();
                        return true;
                    }
                    return false;
                });
                if (cfg == 2) shot(sc, "content_sub_drive.png", 0);
                int[] m = {0};
                until(sc, 0, 1000, () ->
                {
                    Minecraft mc = Minecraft.getInstance();
                    mc.player.setYRot(180f);
                    mc.player.setXRot(0f);
                    mc.options.keyUp.setDown(true);
                    return steady[0] ? ++m[0] >= 100 : true;
                });
                cl(sc, 3, () ->
                {
                    Minecraft mc = Minecraft.getInstance();
                    List<double[]> s;
                    synchronized (PROBE) { s = new ArrayList<>(PROBE); }
                    mc.options.keyUp.setDown(false);
                    if (!steady[0])
                    {
                        log("FAIL sub_drive " + CFG[cfg] + " not steady");
                        return;
                    }
                    int k = s.size();
                    if (k < 103)
                    {
                        log("FAIL sub_drive " + CFG[cfg] + " only " + k + " server samples");
                        return;
                    }
                    float dmg1 = mc.level.getEntity(subId) instanceof Submarine sb ? sb.getDamage() : -1f;
                    boolean nearEnd = false;
                    for (double[] q : s) if (q[1] < boxEndZ + 10) nearEnd = true;
                    log("sub_drive " + CFG[cfg] + " damage_before=" + dmg0[0] + " damage_after=" + dmg1 + " near_end=" + nearEnd);
                    // every sample is one server tick (ServerTickEvent.Post). Submarine#serverTick charges thrust in tick j only when the
                    // position moved > 0.01 since the start of that tick = the position delta recorded at sample j-1.
                    double sum = 0, max = 0, feTotal = 0;
                    int ticks = 100, drainTicks = 0, movingTicks = 0, idleTicks = 0;
                    for (int j = k - 100; j < k; j++)
                    {
                        double d = Math.hypot(s.get(j)[0] - s.get(j - 1)[0], s.get(j)[1] - s.get(j - 1)[1]);
                        double dPrev = Math.hypot(s.get(j - 1)[0] - s.get(j - 2)[0], s.get(j - 1)[1] - s.get(j - 2)[1]);
                        double drop = s.get(j - 1)[2] - s.get(j)[2];
                        sum += d;
                        max = Math.max(max, d);
                        if (d < 0.01) idleTicks++;
                        if (dPrev > 0.01) movingTicks++;
                        if (drop > 0) { drainTicks++; feTotal += drop; }
                    }
                    double avg = sum / 100.0, fe = feTotal / ticks;
                    double feMoving = movingTicks == 0 ? 0.0 : feTotal / movingTicks;
                    double[] e = expected(cfg);
                    AVG[cfg] = avg;
                    String f = String.format(java.util.Locale.ROOT, "speed_avg=%.4f speed_max=%.4f server_ticks=%d moving_ticks=%d drain_ticks=%d idle_pos_ticks=%d fe_total=%.0f fe_per_server_tick=%.2f fe_per_moving_tick=%.2f expected_speed=%.4f expected_fe=%.1f",
                            avg, max, ticks, movingTicks, drainTicks, idleTicks, feTotal, fe, feMoving, e[0], e[1]);
                    log("sub_drive " + CFG[cfg] + " " + f);
                    if (Math.abs(avg - e[0]) > 0.05 * e[0] || Math.abs(feMoving - e[1]) > 0.02 * e[1]) log("FAIL sub_drive " + CFG[cfg] + " " + f);
                    if (cfg == 4)
                        log(String.format(java.util.Locale.ROOT, "sub_drive hull_mk1/hull_mk2 speed ratio=%.4f expected=%.4f", AVG[4] / AVG[3],
                                Config.SUB_HULL_SPEED_MULT.get() / Config.SUB_HULL2_SPEED_MULT.get()));
                });
            }
            // the upgrade screen cannot be opened while riding (Submarine#interact passes with a passenger): get off, then use it as a real player
            String su = "sub_upgrade";
            sv(su, 10, p ->
            {
                ServerLevel l = p.serverLevel();
                p.stopRiding();
                spawnSub(l, boxX + 0.5, boxY + 2.0, boxZ, new ItemStack(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get()));
                p.getInventory().clearContent();
                p.getInventory().setItem(0, new ItemStack(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get()));
                p.getInventory().selected = 8;
                p.teleportTo(l, boxX + 3.2, boxY + 2.0, boxZ, 90f, 0f);
            });
            until(su, 10, 200, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                return !mc.player.isPassenger() && mc.level.getEntity(subId) instanceof Submarine && !mc.player.getInventory().getItem(0).isEmpty();
            });
            cl(su, 2, () -> sneak(Minecraft.getInstance(), true));
            until(su, 3, 80, () -> serverSneaking(Minecraft.getInstance()));
            cl(su, 4, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                var r = mc.gameMode.interact(mc.player, mc.level.getEntity(subId), InteractionHand.MAIN_HAND);
                log("sub_upgrade sneak right click on the submarine: " + r);
            });
            until(su, 5, 80, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                return mc.screen instanceof AbstractContainerScreen<?> scr && scr.getMenu() instanceof SubmarineUpgradeMenu;
            });
            cl(su, 3, () -> sneak(Minecraft.getInstance(), false));
            // pick the Mk2 hull from hotbar slot 0 (menu slot 32), click it onto the hull slot (0, holding Mk1), put the Mk1 back (32)
            cl(su, 5, () -> menuClick(32));
            cl(su, 5, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                log("sub_upgrade cursor after pickup=" + idOf(mc.player.containerMenu.getCarried()));
                menuClick(0);
            });
            cl(su, 5, () -> menuClick(32));
            String[] server = {"?"};
            boolean[] serverOk = {false};
            sv(su, 6, p ->
            {
                Submarine sub = p.serverLevel().getEntity(subId) instanceof Submarine s ? s : null;
                if (sub == null) throw new IllegalStateException("submarine gone");
                int mk1 = 0, mk2 = 0;
                for (int j = 0; j < p.getInventory().getContainerSize(); j++)
                {
                    ItemStack st = p.getInventory().getItem(j);
                    if (st.is(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get())) mk1 += st.getCount();
                    if (st.is(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get())) mk2 += st.getCount();
                }
                ItemStack carried = p.containerMenu.getCarried();
                if (carried.is(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get())) mk1 += carried.getCount();
                if (carried.is(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get())) mk2 += carried.getCount();
                for (int j = 0; j < sub.upgrades().getContainerSize(); j++)
                {
                    ItemStack st = sub.upgrades().getItem(j);
                    if (st.is(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get())) mk1 += st.getCount();
                    if (st.is(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get())) mk2 += st.getCount();
                }
                server[0] = "slot0=" + idOf(sub.upgrades().getItem(0)) + " advancedHull=" + sub.isAdvanced(SubmarineUpgrades.HULL) + " hotbar0="
                        + idOf(p.getInventory().getItem(0)) + " cursor=" + idOf(carried) + " mk1_total=" + mk1 + " mk2_total=" + mk2;
                serverOk[0] = sub.upgrades().getItem(0).is(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get()) && sub.isAdvanced(SubmarineUpgrades.HULL)
                        && p.getInventory().getItem(0).is(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get()) && p.getInventory().getItem(0).getCount() == 1
                        && carried.isEmpty() && mk1 == 1 && mk2 == 1;
            });
            cl(su, 0, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                var menu = mc.player.containerMenu;
                ItemStack slot0 = menu.getSlot(0).getItem(), hot = menu.getSlot(32).getItem(), cur = menu.getCarried();
                boolean ok = serverOk[0] && slot0.is(SubmarineUpgrades.ADV_ITEMS[SubmarineUpgrades.HULL].get()) && cur.isEmpty()
                        && hot.is(SubmarineUpgrades.ITEMS[SubmarineUpgrades.HULL].get()) && hot.getCount() == 1;
                String line = "client slot0=" + idOf(slot0) + " cursor=" + idOf(cur) + " hotbar0=" + idOf(hot) + " | server " + server[0];
                log("sub_upgrade " + line);
                if (!ok) log("FAIL sub_upgrade " + line);
            });
            shot(su, "content_sub_upgrade.png", 2);
            closeScreen(su);
            // always runs (own scene name): keys up, off the sub, remove submarines, put the sky blocks back
            String cu = "sub_cleanup";
            cl(cu, 2, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                mc.options.keyUp.setDown(false);
                sneak(mc, false);
            });
            sv(cu, 20, p ->
            {
                ServerLevel l = p.serverLevel();
                p.stopRiding();
                p.teleportTo(l, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0f, 5f);
                probeSub = null;
                if (!SAVED.isEmpty())
                {
                    restoreBox(l);
                    for (Submarine s : l.getEntitiesOfClass(Submarine.class, new AABB(boxX - 8, boxY - 4, boxZ - 225, boxX + 8, boxY + 12, boxZ + 10))) s.discard();
                }
            });
        }

        // ---- titan: boss kills credited to a real ServerPlayer -> dive/titan_slayer, dive/titan_hunter

        private static net.minecraft.advancements.AdvancementHolder adv(ServerPlayer p, String path)
        {
            var a = p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "dive/" + path));
            if (a == null) throw new IllegalStateException("advancement missing dive/" + path);
            return a;
        }

        private static void titanKill(ServerPlayer p, String name)
        {
            ServerLevel l = p.serverLevel();
            var e = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name)).create(l);
            if (!(e instanceof com.abyssia.entity.boss.AbyssBoss b)) throw new IllegalStateException("not a boss: " + name + " -> " + e);
            b.setNoAi(true);
            b.setPos(p.getX() - 40, p.getY(), p.getZ());
            if (!l.addFreshEntity(b)) throw new IllegalStateException("addFreshEntity refused " + name);
            // a player-attributed bypassing source: the wyrm cannot be attacked while it is burrowed (its first state)
            var killType = l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
            b.hurt(new net.minecraft.world.damagesource.DamageSource(killType, p), 100000F);
            boolean dead = b.isDeadOrDying();
            log("titan killed " + name + " dead=" + dead + " health=" + b.getHealth());
            if (!dead) { failReason = name + " survived the player attack"; failScene = "titan"; }
            AABB box = b.getBoundingBox().inflate(40);
            for (var it : l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) it.discard();
            for (var other : l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(b.position(), b.position()).inflate(150),
                    q -> q instanceof com.abyssia.entity.boss.AbyssBoss || q instanceof com.abyssia.entity.boss.AbyssMotherSpawn)) other.discard();
        }

        private static void titanCheck(ServerPlayer p, String path, String label)
        {
            boolean done = p.getAdvancements().getOrStartProgress(adv(p, path)).isDone();
            log("titan " + label + "=" + done);
            if (!done) { failReason = label + " not granted to a real player"; failScene = "titan"; }
        }

        private static void titan()
        {
            String sc = "titan";
            sv(sc, 20, p ->
            {
                for (String id : new String[]{"titan_slayer", "titan_hunter"})
                {
                    var a = adv(p, id);
                    for (String c : new ArrayList<>(a.value().criteria().keySet())) p.getAdvancements().revoke(a, c);
                }
                ServerLevel l = p.serverLevel();
                int bx = origin.getX() + 100, bz = origin.getZ(), y0 = origin.getY() + 50;
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                for (int x = bx - 50; x <= bx + 10; x++)
                    for (int y = y0; y <= y0 + 20; y++)
                        for (int z = bz - 10; z <= bz + 10; z++)
                            l.setBlock(m.set(x, y, z), Blocks.WATER.defaultBlockState(), 2);
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(l, bx + 5.5, y0 + 5, bz + 0.5, 90f, 0f);
                log("titan setup player=" + fmt(p.position()) + " revoked slayer=" + p.getAdvancements().getOrStartProgress(adv(p, "titan_slayer")).isDone()
                        + " hunter=" + p.getAdvancements().getOrStartProgress(adv(p, "titan_hunter")).isDone());
            });
            sv(sc, 20, p -> titanKill(p, "abyss_angler"));
            sv(sc, 5, p -> titanCheck(p, "titan_slayer", "slayer_after_angler"));
            cl(sc, 30, () ->
            {
                Minecraft mc = Minecraft.getInstance(); // no toast clear: the advancement toast / chat line is the point
                new java.io.File(mc.gameDirectory, "screenshots").mkdirs();
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "content_titan.png", mc.getMainRenderTarget(), c -> log("saved content_titan.png"));
            });
            sv(sc, 5, p ->
            {
                for (String n : new String[]{"abyss_serpent", "trench_wyrm", "abyss_kraken", "abyss_mother"}) titanKill(p, n);
            });
            sv(sc, 5, p -> titanCheck(p, "titan_hunter", "hunter_after_four"));
        }

        // ---- excavator: the three tiers side by side, built like ExcavatorEntry#layout (master + parts with the controller set)

        private static final ExcavatorEntry[] EXC = {new ExcavatorEntry(ExcavatorTier.MK1), new ExcavatorEntry(ExcavatorTier.MK2), new ExcavatorEntry(ExcavatorTier.MK3)};
        private static final String[] EXC_IDS = {"abyssal_excavator", "abyssal_excavator_mk2", "abyssal_excavator_mk3"};

        private static void excavator()
        {
            String sc = "excavator";
            int[] pos = new int[3]; // x0, y0, z0 of the first machine
            sv(sc, 20, p ->
            {
                ServerLevel l = p.serverLevel();
                int x0 = origin.getX() - 100, y0 = origin.getY() + 80, z0 = origin.getZ() + 150;
                pos[0] = x0; pos[1] = y0; pos[2] = z0;
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                for (int x = x0 - 12; x <= x0 + 36; x++)
                    for (int z = z0 - 6; z <= z0 + 45; z++)
                    {
                        l.setBlock(m.set(x, y0 - 1, z), Blocks.STONE.defaultBlockState(), 2);
                        for (int y = y0; y <= y0 + 10; y++) l.setBlock(m.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                Block[] blocks = {ModIndustry.ABYSSAL_EXCAVATOR.get(), ModIndustry.ABYSSAL_EXCAVATOR_MK2.get(), ModIndustry.ABYSSAL_EXCAVATOR_MK3.get()};
                for (int i = 0; i < 3; i++)
                {
                    BlockPos master = new BlockPos(x0 + 12 * i, y0, z0);
                    for (BlockPos part : ExcavatorStructure.partCells(master))
                        l.setBlock(part, ModIndustry.EXCAVATOR_PART.get().defaultBlockState(), 3);
                    l.setBlock(master, blocks[i].defaultBlockState().setValue(IndustryEntityBlock.FACING, Direction.SOUTH), 3);
                    for (BlockPos part : ExcavatorStructure.partCells(master))
                        if (l.getBlockEntity(part) instanceof ExcavatorPartBlockEntity be) be.setController(master);
                    log("excavator placed " + EXC_IDS[i] + " master=" + master.toShortString() + " be=" + l.getBlockEntity(master));
                }
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(l, x0 + 12.5, y0 + 4, z0 + 36.5, 180f, 5f);
            });
            cl(sc, 5, () ->
            {
                Minecraft mc = Minecraft.getInstance();
                var rm = mc.getResourceManager();
                for (int i = 0; i < 3; i++)
                {
                    ResourceLocation ic = EXC[i].icon();
                    log("excavator icon " + EXC_IDS[i] + "=" + ic + " exists=" + rm.getResource(ic).isPresent());
                    for (String suffix : new String[]{"_0_0_0_c", "_0_0_0_t", "_0_0_0_e", "_drill_c", "_drill_t", "_drill_e"})
                    {
                        var mm = mc.getModelManager();
                        boolean missing = mm.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                                ExcavatorRenderer.model(EXC_IDS[i] + suffix))) == mm.getMissingModel();
                        log("excavator model " + EXC_IDS[i] + suffix + " missing=" + missing);
                    }
                }
            });
            shot(sc, "content_excavator.png", 60);
            sv(sc, 5, p -> p.teleportTo(p.serverLevel(), pos[0] + 24.5, pos[1] + 3, pos[2] + 10.5, 180f, 10f));
            shot(sc, "content_excavator_mk3.png", 60);
            // always: remove the machines, the floor and the player's flight
            String cu = "excavator_cleanup";
            sv(cu, 5, p ->
            {
                ServerLevel l = p.serverLevel();
                int x0 = pos[0], y0 = pos[1], z0 = pos[2];
                if (y0 == 0) return;
                for (int i = 0; i < 3; i++) ExcavatorStructure.remove(l, new BlockPos(x0 + 12 * i, y0, z0));
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                for (int x = x0 - 12; x <= x0 + 36; x++)
                    for (int z = z0 - 6; z <= z0 + 45; z++)
                        for (int y = y0 - 1; y <= y0 + 10; y++) l.setBlock(m.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(l, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0f, 5f);
            });
        }

        private static void menuClick(int slot)
        {
            Minecraft mc = Minecraft.getInstance();
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.PICKUP, mc.player);
        }

        // ---- world helpers

        private static void clearBox(ServerLevel l, int x1, int y1, int z1, int x2, int y2, int z2)
        {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++)
                    {
                        m.set(x, y, z);
                        BlockState s = l.getBlockState(m);
                        if (!s.isAir() && s.getFluidState().isEmpty()) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                    }
        }

        private static void platform(ServerLevel l, int x1, int z1, int x2, int z2, int y)
        {
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++)
                    l.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
        }
    }
}
