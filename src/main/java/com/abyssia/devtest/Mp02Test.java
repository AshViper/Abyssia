package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.map.AbyssChartItem;
import com.abyssia.map.EntranceLocator;
import com.abyssia.registry.ModItems;
import com.abyssia.worldgen.DeepLayer;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only (MP02): with -Dabyssia.mp02test (gradle runMp02Test) the dedicated server checks the Abyss Chart recipe and
 * hand-over (chart -> map in the same hand, no new item), logs "MP02 name PASS|FAIL measured" lines and halts.
 * Fake players are put into the player list's uuid map for the run and removed again.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Mp02Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks, passes, fails, startTick;
    private static boolean started, finished;
    private static ServerLevel level;
    private static ServerPlayer pB, pC, pD, pF;
    private static final List<ServerPlayer> ALL = new ArrayList<>();

    private Mp02Test() {}

    private static void report(String name, boolean ok, String measured)
    {
        if (ok) passes++; else fails++;
        LOG.info("MP02 {} {} {}", name, ok ? "PASS" : "FAIL", measured);
    }

    private interface Body { void run() throws Exception; }

    private static void guard(String name, Body body)
    {
        try { body.run(); }
        catch (Throwable t)
        {
            report(name, false, "exception " + t);
            LOG.error("MP02 " + name + " exception", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(MinecraftServer server) throws Exception
    {
        Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(server.getPlayerList());
    }

    private static ServerPlayer fake(MinecraftServer server, String name, double x, double z, boolean creative, int charts) throws Exception
    {
        ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(("mp02" + name).getBytes()), "mp02" + name));
        p.setPos(x, 64, z);
        p.getAbilities().instabuild = creative;
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ABYSS_CHART.get(), charts));
        byUuid(server).put(p.getUUID(), p);
        ALL.add(p);
        return p;
    }

    private static int count(ServerPlayer p, Item item)
    {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
        {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static int total(ServerPlayer p)
    {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static boolean done(ServerPlayer p)
    {
        return p != null && count(p, net.minecraft.world.item.Items.FILLED_MAP) > 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (System.getProperty("abyssia.mp02test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++ticks < 100) return;
        if (!started)
        {
            started = true;
            startTick = ticks;
            level = server.overworld();
            LOG.info("MP02 start deepLayer={}", DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation()));
            guard("a_recipe", () -> testRecipe(server));
            guard("setup", () ->
            {
                pB = fake(server, "b", 0, 0, false, 1);
                pC = fake(server, "c", 0, 0, false, 2);
                pD = fake(server, "d", 0, 0, true, 1);
                pF = fake(server, "f", 2500, -1800, false, 1);
                for (ServerPlayer p : ALL) p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
            });
            return;
        }
        boolean all = done(pB) && done(pC) && done(pD) && done(pF);
        if (!all && ticks - startTick < 1500) return;
        if (!all) report("wait", false, "search not finished after " + (ticks - startTick) + " ticks");
        guard("b", () -> testB());
        guard("c", () -> testC());
        guard("d", () -> testD());
        guard("e", () -> testE());
        guard("f", () -> testF());
        guard("cleanup", () ->
        {
            for (ServerPlayer p : ALL) byUuid(server).remove(p.getUUID());
        });
        finished = true;
        LOG.info("MP02 DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }

    private static TransientCraftingContainer grid(Item a, Item b, Item c, Item d)
    {
        // S P L / P C P / L P S
        TransientCraftingContainer g = new TransientCraftingContainer(new AbstractContainerMenu(null, -1)
        {
            @Override public net.minecraft.world.item.ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player p) { return true; }
        }, 3, 3);
        Item[] items = {a, b, c, b, d, b, c, b, a};
        for (int i = 0; i < 9; i++) g.setItem(i, new ItemStack(items[i]));
        return g;
    }

    private static void testRecipe(MinecraftServer server)
    {
        Optional<CraftingRecipe> ok = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(Items.INK_SAC, Items.PAPER, Items.GLOW_INK_SAC, Items.COMPASS), level);
        boolean okChart = ok.isPresent() && ok.get().getResultItem(level.registryAccess()).is(ModItems.ABYSS_CHART.get());
        Item sea = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("abyssia", "sea_cloth"));
        Item gel = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("abyssia", "lumen_gel"));
        Optional<CraftingRecipe> old = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(sea, Items.PAPER, gel, Items.COMPASS), level);
        boolean oldChart = old.isPresent() && old.get().getResultItem(level.registryAccess()).is(ModItems.ABYSS_CHART.get());
        report("a_recipe", okChart && !oldChart, "vanillaIngredients->chart=" + okChart + " oldIngredients->chart=" + oldChart);
    }

    private static int cmd(ItemStack s)
    {
        return s.hasTag() && s.getTag().contains("CustomModelData") ? s.getTag().getInt("CustomModelData") : -1;
    }

    private static boolean isChartMap(ItemStack s)
    {
        return s.is(Items.FILLED_MAP) && cmd(s) == AbyssChartItem.CHART_MODEL_DATA;
    }

    private static void testB()
    {
        ItemStack hand = pB.getMainHandItem();
        int ch = count(pB, ModItems.ABYSS_CHART.get()), mp = count(pB, Items.FILLED_MAP);
        String name = hand.getHoverName().toString();
        report("b_survival1", isChartMap(hand) && ch == 0 && mp == 1,
                "hand=" + hand.getItem() + " cmd=" + cmd(hand)
                        + " charts=" + ch + " maps=" + mp + " name=" + name);
    }

    private static void testC()
    {
        int ch = count(pC, ModItems.ABYSS_CHART.get()), mp = count(pC, Items.FILLED_MAP);
        report("c_survival2", ch == 1 && mp == 1 && isChartMap(findMap(pC)), "charts=" + ch + " maps=" + mp);
    }

    private static void testD()
    {
        int ch = count(pD, ModItems.ABYSS_CHART.get()), mp = count(pD, Items.FILLED_MAP);
        report("d_creative1", ch == 0 && mp == 1 && isChartMap(pD.getMainHandItem()), "charts=" + ch + " maps=" + mp);
    }

    private static ItemStack findMap(ServerPlayer p)
    {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(Items.FILLED_MAP)) return p.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }

    private static void testE()
    {
        int t0 = total(pB), m0 = count(pB, Items.FILLED_MAP);
        ItemStack map = pB.getMainHandItem();
        for (int i = 0; i < 3; i++) map.use(level, pB, InteractionHand.MAIN_HAND);
        int t1 = total(pB), m1 = count(pB, Items.FILLED_MAP);
        report("e_use_map", t0 == t1 && m0 == m1, "total " + t0 + "->" + t1 + " maps " + m0 + "->" + m1);
    }

    private static void testF()
    {
        var gen = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int n = 0;
        for (ServerPlayer p : new ServerPlayer[] {pB, pF})
        {
            ItemStack map = p.getMainHandItem();
            ListTag decos = map.hasTag() ? map.getTag().getList("Decorations", 10) : new ListTag();
            if (decos.isEmpty())
            {
                report("f_location" + n++, false, "no decoration");
                continue;
            }
            for (int di = 0; di < decos.size(); di++)
            {
                CompoundTag e = decos.getCompound(di);
                int x = (int) Math.floor(e.getDouble("x")), z = (int) Math.floor(e.getDouble("z"));
                int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                var src = gen.getBiomeSource();
                var sampler = random.sampler();
                var b1 = src.getNoiseBiome(x >> 2, -40 >> 2, z >> 2, sampler);
                var b2 = src.getNoiseBiome(x >> 2, h >> 2, z >> 2, sampler);
                boolean inTag = b1.is(EntranceLocator.ENTRANCES) || b2.is(EntranceLocator.ENTRANCES);
                double dist = Math.sqrt((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()));
                report("f_location" + n++, h < DeepLayer.TOP_Y && inTag,
                        "origin=(" + (int) p.getX() + "," + (int) p.getZ() + ") red_x=(" + x + "," + z + ") floorY=" + h + " TOP_Y=" + DeepLayer.TOP_Y
                                + " biome@-40=" + b1.unwrapKey().map(k -> k.location().toString()).orElse("?")
                                + " biome@floor=" + b2.unwrapKey().map(k -> k.location().toString()).orElse("?") + " dist=" + (int) dist);
            }
        }
    }
}
