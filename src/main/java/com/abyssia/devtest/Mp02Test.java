package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.map.AbyssChartItem;
import com.abyssia.map.EntranceLocator;
import com.abyssia.registry.ModItems;
import com.abyssia.worldgen.DeepLayer;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
@EventBusSubscriber(modid = Abyssia.MODID)
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
    public static void tick(ServerTickEvent.Post event)
    {
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

    private static CraftingInput grid(Item a, Item b, Item c, Item d)
    {
        // S P L / P C P / L P S
        return CraftingInput.of(3, 3, List.of(new ItemStack(a), new ItemStack(b), new ItemStack(c),
                new ItemStack(b), new ItemStack(d), new ItemStack(b),
                new ItemStack(c), new ItemStack(b), new ItemStack(a)));
    }

    private static void testRecipe(MinecraftServer server)
    {
        Optional<RecipeHolder<CraftingRecipe>> ok = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(Items.INK_SAC, Items.PAPER, Items.GLOW_INK_SAC, Items.COMPASS), level);
        boolean okChart = ok.isPresent() && ok.get().value().getResultItem(level.registryAccess()).is(ModItems.ABYSS_CHART.get());
        Item sea = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("abyssia", "sea_cloth"));
        Item gel = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("abyssia", "lumen_gel"));
        Optional<RecipeHolder<CraftingRecipe>> old = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(sea, Items.PAPER, gel, Items.COMPASS), level);
        boolean oldChart = old.isPresent() && old.get().value().getResultItem(level.registryAccess()).is(ModItems.ABYSS_CHART.get());
        report("a_recipe", okChart && !oldChart, "vanillaIngredients->chart=" + okChart + " oldIngredients->chart=" + oldChart);
    }

    private static boolean isChartMap(ItemStack s)
    {
        var cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        return s.is(Items.FILLED_MAP) && cmd != null && cmd.value() == AbyssChartItem.CHART_MODEL_DATA;
    }

    private static void testB()
    {
        ItemStack hand = pB.getMainHandItem();
        int ch = count(pB, ModItems.ABYSS_CHART.get()), mp = count(pB, Items.FILLED_MAP);
        String name = hand.get(DataComponents.ITEM_NAME) == null ? "null" : hand.get(DataComponents.ITEM_NAME).toString();
        report("b_survival1", isChartMap(hand) && ch == 0 && mp == 1,
                "hand=" + hand.getItem() + " cmd=" + (hand.get(DataComponents.CUSTOM_MODEL_DATA) == null ? -1 : hand.get(DataComponents.CUSTOM_MODEL_DATA).value())
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
            MapDecorations decos = map.get(DataComponents.MAP_DECORATIONS);
            if (decos == null || decos.decorations().isEmpty())
            {
                report("f_location" + n++, false, "no decoration");
                continue;
            }
            for (var e : decos.decorations().values())
            {
                int x = (int) Math.floor(e.x()), z = (int) Math.floor(e.z());
                int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                var src = gen.getBiomeSource();
                var sampler = random.sampler();
                var b1 = src.getNoiseBiome(x >> 2, -40 >> 2, z >> 2, sampler);
                var b2 = src.getNoiseBiome(x >> 2, h >> 2, z >> 2, sampler);
                boolean inTag = b1.is(EntranceLocator.DEEP_ENTRANCES) || b2.is(EntranceLocator.DEEP_ENTRANCES);
                double dist = Math.sqrt((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()));
                report("f_location" + n++, h < DeepLayer.TOP_Y && inTag,
                        "origin=(" + (int) p.getX() + "," + (int) p.getZ() + ") red_x=(" + x + "," + z + ") floorY=" + h + " TOP_Y=" + DeepLayer.TOP_Y
                                + " biome@-40=" + b1.unwrapKey().map(k -> k.location().toString()).orElse("?")
                                + " biome@floor=" + b2.unwrapKey().map(k -> k.location().toString()).orElse("?") + " dist=" + (int) dist);
            }
        }
    }
}
