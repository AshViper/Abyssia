package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.upkeep.HabitatUpkeep;
import com.abyssia.industry.blockentity.PrecipitatorBlockEntity;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
