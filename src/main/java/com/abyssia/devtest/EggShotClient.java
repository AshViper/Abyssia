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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Client half of EggShot (see there). */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EggShotClient
{
    private static int ct = 0, shotAt = -1;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e)
    {
        if (!EggShot.ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ct++;
        if (ct == 150)
        {
            int missing = 0, n = 0;
            var miss = mc.getModelManager().getMissingModel();
            for (Item it : EggShot.eggs())
            {
                n++;
                var m = mc.getItemRenderer().getModel(new ItemStack(it), mc.level, null, 0);
                String sprite = m.getParticleIcon().contents().name().toString();
                if (m == miss || sprite.contains("missingno")) { missing++; EggShot.log("EGG MISSING " + BuiltInRegistries.ITEM.getKey(it) + " " + sprite); }
            }
            EggShot.log("EGG client models=" + n + " missing=" + missing);
            mc.setScreen(new Grid());
            shotAt = ct + 40;
        }
        if (shotAt > 0 && ct == shotAt)
        {
            mc.getToasts().clear();
            new java.io.File(mc.gameDirectory, "screenshots").mkdirs();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "egg_grid.png", mc.getMainRenderTarget(), c -> EggShot.log("EGG saved screenshot"));
        }
        if (shotAt > 0 && ct >= shotAt + 40 && EggShot.serverDone) { EggShot.log("EGG done"); shotAt = -1; mc.stop(); }
    }

    private static final class Grid extends Screen
    {
        Grid() { super(Component.literal("eggs")); }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt)
        {
            g.fill(0, 0, width, height, 0xFF505050);
            List<Item> l = EggShot.eggs();
            g.pose().pushPose();
            g.pose().scale(1.5f, 1.5f, 1f);
            for (int i = 0; i < l.size(); i++)
                g.renderItem(new ItemStack(l.get(i)), 6 + (i % 14) * 18, 6 + (i / 14) * 18);
            g.pose().popPose();
        }
    }
}
