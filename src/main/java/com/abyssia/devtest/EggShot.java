package com.abyssia.devtest;

import com.abyssia.Abyssia;
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
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EggShot
{
    static final boolean ON = System.getProperty("abyssia.eggshot") != null;
    static Path out = Path.of("eggshot.txt");
    private static int st = -1;
    static boolean serverDone = false;

    static void log(String s)
    {
        try { Files.writeString(out, s + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    static List<Item> eggs()
    {
        List<Item> l = new ArrayList<>();
        for (var e : BuiltInRegistries.ITEM.entrySet())
            if (e.getKey().location().getNamespace().equals(Abyssia.MODID) && e.getKey().location().getPath().endsWith("_spawn_egg"))
                l.add(e.getValue());
        return l;
    }

    private static BlockPos base;
    private static final String[] USE = {"anglerfish", "abyss_angler", "lancejaw"};

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e)
    {
        if (!ON || serverDone || e.phase != TickEvent.Phase.END) return;
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
                Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(Abyssia.MODID, id + "_spawn_egg"));
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
