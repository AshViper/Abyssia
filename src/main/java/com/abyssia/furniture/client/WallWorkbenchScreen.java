package com.abyssia.furniture.client;

import com.abyssia.client.gui.UiTheme;
import com.abyssia.furniture.WallWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Wall workbench GUI: the deep-sea themed background (textures/gui/wall_workbench.png, slot and gauge frames
 * included) plus the fill of a vertical FE gauge in the strip right of the result slot.
 */
public class WallWorkbenchScreen extends AbstractContainerScreen<WallWorkbenchMenu>
{
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("abyssia", "textures/gui/wall_workbench.png");
    private static final int GAUGE_X = 155, GAUGE_Y = 17, GAUGE_W = 12, GAUGE_H = 32;

    public WallWorkbenchScreen(WallWorkbenchMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        titleLabelX = 29;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= GAUGE_X - 1 && x < GAUGE_X + GAUGE_W + 1 && y >= GAUGE_Y - 1 && y < GAUGE_Y + GAUGE_H + 1)
            g.renderTooltip(font, energyText(), mouseX, mouseY);
        else if (x >= WallWorkbenchMenu.CHARGE_X - 1 && x < WallWorkbenchMenu.CHARGE_X + 17
                && y >= WallWorkbenchMenu.CHARGE_Y - 1 && y < WallWorkbenchMenu.CHARGE_Y + 17
                && !menu.getSlot(WallWorkbenchMenu.CHARGE).hasItem())
            g.renderTooltip(font, Component.translatableWithFallback("gui.abyssia.wall_workbench.charge", "Charge slot (FE items)"), mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 166);
        // FE gauge fill (the frame is part of the texture)
        int gx = leftPos + GAUGE_X, gy = topPos + GAUGE_Y;
        g.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, UiTheme.GAUGE_BACK);
        int capacity = menu.capacity();
        if (capacity > 0)
        {
            int h = (int) Math.min(GAUGE_H, (long) menu.energy() * GAUGE_H / capacity);
            if (h > 0)
            {
                g.fill(gx, gy + GAUGE_H - h, gx + GAUGE_W, gy + GAUGE_H, UiTheme.GAUGE_FILL);
                g.fill(gx, gy + GAUGE_H - h, gx + GAUGE_W, gy + GAUGE_H - h + 1, UiTheme.GAUGE_TOP);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        g.drawString(font, title, titleLabelX, titleLabelY, UiTheme.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.TEXT, false);
    }

    private Component energyText()
    {
        return Component.translatableWithFallback("gui.abyssia.energy", "%s / %s FE",
                String.format("%,d", menu.energy()), String.format("%,d", menu.capacity()));
    }
}
