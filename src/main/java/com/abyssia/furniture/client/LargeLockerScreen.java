package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.furniture.LargeLockerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Large locker GUI: deep-sea console panel (textures/gui/large_locker.png from tools/locker_gui.py, 350 x 186, wider
 * than 256 so it is blitted with its own texture size): 9 x 9 storage left, inventory right; text colours as IndustryScreen.
 */
public class LargeLockerScreen extends AbstractContainerScreen<LargeLockerMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/gui/large_locker.png");
    private static final int TITLE = 0xB8C4CC;
    private static final int LABEL = 0x9AA8B4;
    /** title sits right of the lamp in the title bar */
    private static final int TITLE_X = 18;

    public LargeLockerScreen(LargeLockerMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = LargeLockerMenu.WIDTH;
        imageHeight = LargeLockerMenu.HEIGHT;
        inventoryLabelX = LargeLockerMenu.INV_LABEL_X;
        inventoryLabelY = LargeLockerMenu.INV_LABEL_Y;
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
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, LargeLockerMenu.WIDTH, LargeLockerMenu.HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        g.drawString(font, title, TITLE_X, titleLabelY, TITLE, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }
}
