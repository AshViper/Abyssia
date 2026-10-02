package com.abyssia.furniture.client;

import com.abyssia.furniture.WallWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Wall workbench GUI: the vanilla crafting table background, plus a slot frame for the charge slot and a vertical FE
 * gauge (drawn with fills) in the empty strip right of the result slot.
 */
public class WallWorkbenchScreen extends AbstractContainerScreen<WallWorkbenchMenu>
{
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/crafting_table.png");
    private static final int GAUGE_X = 155, GAUGE_Y = 17, GAUGE_W = 12, GAUGE_H = 32;
    private static final int FRAME_DARK = 0xFF373737, FRAME_LIGHT = 0xFFFFFFFF, SLOT_FILL = 0xFF8B8B8B;
    private static final int GAUGE_BACK = 0xFF1A2228, GAUGE_FILL = 0xFF35E0E8, GAUGE_TOP = 0xFFA8FFFF;

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
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // charge slot frame (vanilla slot look)
        int sx = leftPos + WallWorkbenchMenu.CHARGE_X - 1, sy = topPos + WallWorkbenchMenu.CHARGE_Y - 1;
        g.fill(sx, sy, sx + 18, sy + 18, FRAME_DARK);
        g.fill(sx + 1, sy + 1, sx + 18, sy + 18, FRAME_LIGHT);
        g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_FILL);
        // FE gauge
        int gx = leftPos + GAUGE_X, gy = topPos + GAUGE_Y;
        g.fill(gx - 1, gy - 1, gx + GAUGE_W + 1, gy + GAUGE_H + 1, FRAME_DARK);
        g.fill(gx, gy, gx + GAUGE_W + 1, gy + GAUGE_H + 1, FRAME_LIGHT);
        g.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, GAUGE_BACK);
        int capacity = menu.capacity();
        if (capacity > 0)
        {
            int h = (int) Math.min(GAUGE_H, (long) menu.energy() * GAUGE_H / capacity);
            if (h > 0)
            {
                g.fill(gx, gy + GAUGE_H - h, gx + GAUGE_W, gy + GAUGE_H, GAUGE_FILL);
                g.fill(gx, gy + GAUGE_H - h, gx + GAUGE_W, gy + GAUGE_H - h + 1, GAUGE_TOP);
            }
        }
    }

    private Component energyText()
    {
        return Component.translatableWithFallback("gui.abyssia.energy", "%s / %s FE",
                String.format("%,d", menu.energy()), String.format("%,d", menu.capacity()));
    }
}
