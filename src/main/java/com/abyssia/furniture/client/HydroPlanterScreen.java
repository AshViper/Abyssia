package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.furniture.HydroPlanterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Hydro planter GUI (textures/gui/hydro_planter.png from tools/planter_gui.py, 256 x 256 sheet with the growth arrow
 * sprite at x=176): seedling slot, growth arrow and a status line. Text colours as IndustryScreen.
 */
public class HydroPlanterScreen extends AbstractContainerScreen<HydroPlanterMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/gui/hydro_planter.png");
    private static final int TITLE = 0xB8C4CC;
    private static final int LABEL = 0x9AA8B4;
    private static final int VALUE = 0xE1E8ED;
    private static final int TITLE_X = 18;

    public HydroPlanterScreen(HydroPlanterMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int total = menu.total();
        if (total > 0 && menu.growth() > 0)
        {
            int w = Math.min(HydroPlanterMenu.ARROW_W, (int) ((long) menu.growth() * HydroPlanterMenu.ARROW_W / total) + 1);
            g.blit(TEXTURE, leftPos + HydroPlanterMenu.ARROW_X, topPos + HydroPlanterMenu.ARROW_Y,
                    HydroPlanterMenu.SPRITE_X, HydroPlanterMenu.ARROW_V, w, HydroPlanterMenu.ARROW_H);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        g.drawString(font, title, TITLE_X, titleLabelY, TITLE, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        int total = menu.total();
        if (total <= 0)
            g.drawString(font, Component.translatableWithFallback("gui.abyssia.hydro_planter.empty", "Insert a seedling"), 8, 60, LABEL, false);
        else if (menu.growth() >= total)
            g.drawString(font, Component.translatableWithFallback("gui.abyssia.hydro_planter.ready", "Ready: right-click to harvest"), 8, 60, VALUE, false);
        else
            g.drawString(font, Component.translatableWithFallback("gui.abyssia.hydro_planter.growth", "Growth: %s%%",
                    (int) ((long) menu.growth() * 100 / total)), 8, 60, VALUE, false);
    }
}
