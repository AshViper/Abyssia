package com.abyssia.item.client;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.item.DiveTank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Dive tank oxygen gauge on the right, in the row of the vanilla air bubbles (shown with helmet + tank worn). */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class OxygenHud
{
    private static final int BAR_H = 5, FILL = 0xFF3FA9F5, LOW = 0xFFE0533D, BACK = 0x80000000;

    private OxygenHud() {}

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event)
    {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "oxygen"), OxygenHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta)
    {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !Config.BREATHING_ENABLED.get() || mc.options.hideGui || player.isCreative() || player.isSpectator()) return;
        ItemStack tank = player.getItemBySlot(EquipmentSlot.CHEST);
        int tier = DiveTank.tankTier(tank);
        if (tier <= 0 || DiveTank.helmetTier(player.getItemBySlot(EquipmentSlot.HEAD)) <= 0) return;
        int capacity = DiveTank.capacitySeconds(tier), oxygen = DiveTank.oxygen(tank);
        if (!player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()) && oxygen >= capacity) return;

        int x0 = g.guiWidth() / 2 + 10, width = 81;
        int y = g.guiHeight() - mc.gui.rightHeight + 2;
        int fill = capacity <= 0 ? 0 : width * oxygen / capacity;
        g.fill(x0, y, x0 + width, y + BAR_H, BACK);
        g.fill(x0, y, x0 + fill, y + BAR_H, oxygen * 5 < capacity ? LOW : FILL);
        String text = DiveTank.clock(oxygen);
        g.drawString(mc.font, text, x0 + width + 3, y - 2, 0xFFFFFFFF, true);
        mc.gui.rightHeight += 10;
    }
}
