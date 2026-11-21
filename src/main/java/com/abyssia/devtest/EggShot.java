package com.abyssia.devtest;

import com.abyssia.Abyssia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only (EGG01): with -Dabyssia.eggshot (gradle runEggShot) the client enters a singleplayer world, the server counts the
 * abyssia spawn eggs, uses three of them with a FakePlayer and counts the entities, and the client checks the item models
 * for the missing model and screenshots a grid of all eggs (run/screenshots/egg_grid.png). Results go to run/eggshot.txt.
 */
public final class EggShot
{
    private static final boolean ON = System.getProperty("abyssia.eggshot") != null;
    private static Path out = Path.of("eggshot.txt");
    private static int st = -1;
    private static boolean serverDone = false;

    private static void log(String s)
    {
        try { Files.writeString(out, s + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    private static List<Item> eggs()
    {
        List<Item> l = new ArrayList<>();
        for (var e : BuiltInRegistries.ITEM.entrySet())
            if (e.getKey().location().getNamespace().equals(Abyssia.MODID) && e.getKey().location().getPath().endsWith("_spawn_egg"))
                l.add(e.getValue());
        return l;
    }

    @EventBusSubscriber(modid = Abyssia.MODID)
    public static final class Server
    {
        private static BlockPos base;
        private static final String[] USE = {"anglerfish", "abyss_angler", "lancejaw"};

        @SubscribeEvent
        public static void tick(ServerTickEvent.Post e)
        {
            if (!ON || serverDone) return;
            if (e.getServer().getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayer p = e.getServer().getPlayerList().getPlayers().get(0);
            st++;
            var level = p.serverLevel();
            if (st == 20)
            {
                p.setGameMode(GameType.CREATIVE);
                log("EGG items=" + eggs().size());
                base = p.blockPosition().offset(0, 60, 0);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                var fake = FakePlayerFactory.getMinecraft(level);
                for (String id : USE)
                {
                    Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, id + "_spawn_egg"));
                    ItemStack s = new ItemStack(it);
                    fake.setItemInHand(InteractionHand.MAIN_HAND, s);
                    var r = it.useOn(new UseOnContext(level, fake, InteractionHand.MAIN_HAND, s,
                            new BlockHitResult(Vec3.atCenterOf(base.below()).add(0, 0.5, 0), Direction.UP, base.below(), false)));
                    log("EGG use " + id + " -> " + r);
                }
            }
            if (st == 60)
            {
                for (String id : USE)
                {
                    int n = level.getEntitiesOfClass(Entity.class, new AABB(base).inflate(6),
                            en -> BuiltInRegistries.ENTITY_TYPE.getKey(en.getType()).getPath().equals(id)).size();
                    log("EGG entities " + id + "=" + n);
                }
                serverDone = true;
            }
        }
    }

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class Client
    {
        private static int ct = 0, shotAt = -1;

        @SubscribeEvent
        public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e)
        {
            if (!ON) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            ct++;
            if (ct == 150)
            {
                int missing = 0, n = 0;
                var miss = mc.getModelManager().getMissingModel();
                for (Item it : eggs())
                {
                    n++;
                    var m = mc.getItemRenderer().getModel(new ItemStack(it), mc.level, null, 0);
                    String sprite = m.getParticleIcon().contents().name().toString();
                    if (m == miss || sprite.contains("missingno")) { missing++; log("EGG MISSING " + BuiltInRegistries.ITEM.getKey(it) + " " + sprite); }
                }
                log("EGG client models=" + n + " missing=" + missing);
                mc.setScreen(new Grid());
                shotAt = ct + 40;
            }
            if (shotAt > 0 && ct == shotAt)
            {
                mc.getToasts().clear();
                new java.io.File(mc.gameDirectory, "screenshots").mkdirs();
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "egg_grid.png", mc.getMainRenderTarget(), c -> log("EGG saved screenshot"));
            }
            if (shotAt > 0 && ct >= shotAt + 40 && serverDone) { log("EGG done"); shotAt = -1; mc.stop(); }
        }
    }

    private static final class Grid extends Screen
    {
        Grid() { super(Component.literal("eggs")); }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt)
        {
            g.fill(0, 0, width, height, 0xFF505050);
            List<Item> l = eggs();
            g.pose().pushPose();
            g.pose().scale(1.5f, 1.5f, 1f);
            for (int i = 0; i < l.size(); i++)
                g.renderItem(new ItemStack(l.get(i)), 6 + (i % 14) * 18, 6 + (i / 14) * 18);
            g.pose().popPose();
        }
    }
}
