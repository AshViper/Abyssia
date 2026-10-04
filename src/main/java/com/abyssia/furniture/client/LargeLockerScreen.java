package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.furniture.LargeLockerMenu;
import com.abyssia.furniture.LockerRenamePacket;
import com.abyssia.network.AbyssiaNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Large locker GUI: deep-sea console panel (textures/gui/large_locker.png from tools/locker_gui.py, 350 x 186, wider
 * than 256 so it is blitted with its own texture size): 9 x 9 storage left, inventory right; text colours as IndustryScreen.
 * The title is an editable name field: Enter or closing the screen sends the name ({@link LockerRenamePacket}), which
 * also shows on the locker's front (LargeLockerRenderer); an empty field clears it.
 */
public class LargeLockerScreen extends AbstractContainerScreen<LargeLockerMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/gui/large_locker.png");
    private static final int TITLE = 0xB8C4CC;
    private static final int HINT = 0x5E7480;
    private static final int LABEL = 0x9AA8B4;
    private static final int UNDERLINE = 0xFF5EA1AC;
    /** title sits right of the lamp in the title bar */
    private static final int TITLE_X = 18;
    private static final int NAME_W = 150;

    private EditBox nameBox;
    private String sentName;

    public LargeLockerScreen(LargeLockerMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = LargeLockerMenu.WIDTH;
        imageHeight = LargeLockerMenu.HEIGHT;
        inventoryLabelX = LargeLockerMenu.INV_LABEL_X;
        inventoryLabelY = LargeLockerMenu.INV_LABEL_Y;
    }

    /** The menu title is the custom name, or the translatable default when the locker has none. */
    private String initialName()
    {
        return title.getContents() instanceof TranslatableContents t && t.getKey().equals("container.abyssia.large_locker")
                ? "" : title.getString();
    }

    @Override
    protected void init()
    {
        super.init();
        sentName = initialName();
        nameBox = new EditBox(font, leftPos + TITLE_X, topPos + titleLabelY - 1, NAME_W, 10, Component.translatable("gui.abyssia.large_locker.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(LockerRenamePacket.MAX_LENGTH);
        nameBox.setTextColor(TITLE);
        nameBox.setValue(sentName);
        nameBox.moveCursorToStart(); // show a long name from its start
        nameBox.setHint(Component.translatable("gui.abyssia.large_locker.name_hint").withStyle(s -> s.withColor(HINT)));
        addRenderableWidget(nameBox);
    }

    private void sendName()
    {
        String name = nameBox.getValue().trim();
        if (name.equals(sentName)) return;
        sentName = name;
        AbyssiaNetwork.sendToServer(new LockerRenamePacket(name));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        if (nameBox.isFocused())
        {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
            {
                sendName();
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            // keep typed letters (E, number keys, ...) out of the container shortcuts; Escape still closes
            if (key != InputConstants.KEY_ESCAPE) return nameBox.keyPressed(key, scanCode, modifiers) || nameBox.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void removed()
    {
        sendName();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (nameBox.isFocused())
            g.fill(nameBox.getX(), nameBox.getY() + 9, nameBox.getX() + NAME_W, nameBox.getY() + 10, UNDERLINE);
        else if (nameBox.isHovered())
            g.renderTooltip(font, Component.translatable("gui.abyssia.large_locker.name_tooltip"), mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, LargeLockerMenu.WIDTH, LargeLockerMenu.HEIGHT);
    }

    /** The title is drawn by the name field. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }
}
