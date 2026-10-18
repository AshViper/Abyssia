package com.abyssia.client.gui;

import com.abyssia.Abyssia;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * Vanilla container screens draw their labels in 0x404040, unreadable on the navy recoloured textures.
 * After renderLabels (pose already at leftPos/topPos) the same strings are redrawn in the theme text colour.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VanillaContainerLabels
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TRADES = Component.translatable("merchant.trades");

    private static boolean ready;
    private static boolean disabled;
    private static Field titleX;
    private static Field titleY;
    private static Field invX;
    private static Field invY;
    private static Field invTitle;
    private static Field creativeTab;

    private VanillaContainerLabels()
    {
    }

    private static void init()
    {
        // SRG names (1.20.1): titleLabelX/Y, inventoryLabelX/Y, playerInventoryTitle, CreativeModeInventoryScreen.selectedTab
        titleX = ObfuscationReflectionHelper.findField(AbstractContainerScreen.class, "f_97728_");
        titleY = ObfuscationReflectionHelper.findField(AbstractContainerScreen.class, "f_97729_");
        invX = ObfuscationReflectionHelper.findField(AbstractContainerScreen.class, "f_97730_");
        invY = ObfuscationReflectionHelper.findField(AbstractContainerScreen.class, "f_97731_");
        invTitle = ObfuscationReflectionHelper.findField(AbstractContainerScreen.class, "f_169604_");
        creativeTab = ObfuscationReflectionHelper.findField(CreativeModeInventoryScreen.class, "f_98507_");
        ready = true;
    }

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event)
    {
        if (disabled)
        {
            return;
        }
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!screen.getClass().getName().startsWith("net.minecraft.") || screen instanceof BeaconScreen)
        {
            return;
        }
        try
        {
            if (!ready)
            {
                init();
            }
            GuiGraphics g = event.getGuiGraphics();
            Font font = Minecraft.getInstance().font;
            if (screen instanceof InventoryScreen)
            {
                draw(g, font, screen.getTitle(), titleX.getInt(screen), titleY.getInt(screen));
            }
            else if (screen instanceof CreativeModeInventoryScreen)
            {
                CreativeModeTab tab = (CreativeModeTab) creativeTab.get(null);
                if (tab != null && tab.showTitle() && tab.getLabelColor() == 0x404040)
                {
                    draw(g, font, tab.getDisplayName(), 8, 6);
                }
            }
            else if (screen instanceof MerchantScreen merchant)
            {
                MerchantMenu menu = merchant.getMenu();
                int level = menu.getTraderLevel();
                Component title = screen.getTitle();
                if (level > 0 && level <= 5 && menu.showProgressBar())
                {
                    title = screen.getTitle().copy().append(" - ").append(Component.translatable("merchant.level." + level));
                }
                draw(g, font, title, 49 + screen.getXSize() / 2 - font.width(title) / 2, 6);
                draw(g, font, (Component) invTitle.get(screen), invX.getInt(screen), invY.getInt(screen));
                draw(g, font, TRADES, 5 - font.width(TRADES) / 2 + 48, 6);
            }
            else
            {
                draw(g, font, screen.getTitle(), titleX.getInt(screen), titleY.getInt(screen));
                draw(g, font, (Component) invTitle.get(screen), invX.getInt(screen), invY.getInt(screen));
            }
        }
        catch (Throwable t)
        {
            disabled = true;
            LOGGER.warn("Vanilla container label recolour disabled", t);
        }
    }

    private static void draw(GuiGraphics g, Font font, Component text, int x, int y)
    {
        g.drawString(font, text, x, y, UiTheme.TEXT, false);
    }
}
