package com.abyssia.industry.client;

import com.abyssia.industry.GuiLayout;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.blockentity.AbyssalExcavatorBlockEntity;
import com.abyssia.industry.blockentity.ProcessingMachineBlockEntity;
import com.abyssia.industry.menu.IndustryMenu;
import com.abyssia.thermal.VentActivity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

import static com.abyssia.industry.GuiLayout.*;

/**
 * GUI of every industrial block (textures from tools/industrial_gui.py): energy bar on the left, progress arrow,
 * fuel flame, and a status line. Texts use gui.abyssia.* keys with English fallbacks.
 */
public class IndustryScreen extends AbstractContainerScreen<IndustryMenu>
{
    // text colours of the deep-sea console panel (inbox/specs/I01-gui-spec.md)
    private static final int TITLE = 0xB8C4CC;
    private static final int LABEL = 0x9AA8B4;
    private static final int TEXT = 0xB8C4CC;
    private static final int VALUE = 0xE1E8ED;
    private static final int WARN = 0xFF6B5A;
    /** title sits right of the lamp in the title bar */
    private static final int TITLE_X = 18;

    public IndustryScreen(IndustryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (x >= ENERGY_X - 1 && x < ENERGY_X + ENERGY_W + 1 && y >= ENERGY_Y - 1 && y < ENERGY_Y + ENERGY_H + 1)
            g.renderTooltip(font, energyText(), mouseX, mouseY);
        GuiLayout layout = menu.kind().layout;
        if (menu.kind().reagent && x >= layout.arrowX && x < layout.arrowX + ARROW_W && y >= layout.arrowY && y < layout.arrowY + ARROW_H)
        {
            List<Component> lines = leachingStatus();
            if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Selective leaching separator: why it stopped, and the water bonus (shown over the arrow). */
    private List<Component> leachingStatus()
    {
        int status = menu.status();
        List<Component> lines = new ArrayList<>();
        if ((status & ProcessingMachineBlockEntity.STATUS_NO_REAGENT) != 0)
            lines.add(Component.translatableWithFallback("gui.abyssia.no_reagent", "Needs Acidic Leaching Reagent").withStyle(s -> s.withColor(WARN)));
        if ((status & ProcessingMachineBlockEntity.STATUS_RARE_FULL) != 0)
            lines.add(Component.translatableWithFallback("gui.abyssia.rare_full", "Rare output slots full").withStyle(s -> s.withColor(WARN)));
        if ((status & ProcessingMachineBlockEntity.STATUS_WATER) != 0)
            lines.add(Component.translatableWithFallback("gui.abyssia.water_bonus", "Water cooled: 10% faster"));
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        GuiLayout layout = menu.kind().layout;
        g.blit(layout.texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int capacity = menu.capacity();
        if (capacity > 0)
        {
            int h = (int) Math.min(ENERGY_H, (long) menu.energy() * ENERGY_H / capacity);
            if (h > 0)
                g.blit(layout.texture, leftPos + ENERGY_X, topPos + ENERGY_Y + ENERGY_H - h, SPRITE_X, ENERGY_V + ENERGY_H - h, ENERGY_W, h);
        }
        if (layout.arrowX >= 0 && menu.maxProgress() > 0 && menu.progress() > 0)
        {
            int w = Math.min(ARROW_W, menu.progress() * ARROW_W / menu.maxProgress() + 1);
            g.blit(layout.texture, leftPos + layout.arrowX, topPos + layout.arrowY, SPRITE_X, ARROW_V, w, ARROW_H);
        }
        if (layout.flameX >= 0 && menu.burnMax() > 0 && menu.burn() > 0)
        {
            int h = Math.min(FLAME_SIZE, (int) ((long) menu.burn() * (FLAME_SIZE - 1) / menu.burnMax()) + 1);
            g.blit(layout.texture, leftPos + layout.flameX, topPos + layout.flameY + FLAME_SIZE - h, SPRITE_X, FLAME_V + FLAME_SIZE - h, FLAME_SIZE, h);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        g.drawString(font, title, TITLE_X, titleLabelY, TITLE, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        MachineKind kind = menu.kind();
        int x = kind.layout.textX;
        switch (kind)
        {
            case HYDROTHERMAL_GENERATOR ->
            {
                g.drawString(font, energyText(), x, 24, VALUE, false);
                g.drawString(font, rateText("gui.abyssia.output", "Output: %s FE/t"), x, 36, TEXT, false);
                int status = menu.status();
                Component vent = status == 0
                        ? Component.translatableWithFallback("gui.abyssia.no_vent", "No thermal vent adjacent")
                        : Component.translatableWithFallback("gui.abyssia.vent", "Vent: %s",
                                ventName(VentActivity.values()[Math.min(status - 1, VentActivity.values().length - 1)]));
                g.drawString(font, vent, x, 48, status <= 1 ? WARN : TEXT, false);
            }
            case AUXILIARY_GENERATOR ->
            {
                g.drawString(font, energyText(), x, 24, VALUE, false);
                g.drawString(font, rateText("gui.abyssia.output", "Output: %s FE/t"), x, 36, TEXT, false);
            }
            case ENERGY_DEVICE -> g.drawString(font, energyText(), x, 36, VALUE, false);
            case ABYSSAL_EXCAVATOR ->
            {
                g.drawString(font, energyText(), x, 60, VALUE, false);
                int status = menu.status();
                if (status > 0)
                    g.drawString(font, Component.translatableWithFallback("gui.abyssia.excavator." + status, "Excavator status " + status),
                            x, 18, status == AbyssalExcavatorBlockEntity.STATUS_MINING ? TEXT : WARN, false);
            }
            default ->
            {
                g.drawString(font, energyText(), x, 60, VALUE, false);
                if ((menu.status() & ProcessingMachineBlockEntity.STATUS_NO_HEAT) != 0)
                    g.drawString(font, Component.translatableWithFallback("gui.abyssia.no_heat", "No active vent nearby"), x, 18, WARN, false);
            }
        }
    }

    private Component energyText()
    {
        return Component.translatableWithFallback("gui.abyssia.energy", "%s / %s FE",
                String.format("%,d", menu.energy()), String.format("%,d", menu.capacity()));
    }

    private Component rateText(String key, String fallback)
    {
        return Component.translatableWithFallback(key, fallback, menu.rate());
    }

    private static Component ventName(VentActivity activity)
    {
        String name = activity.getSerializedName();
        return Component.translatableWithFallback("gui.abyssia.vent." + name, Character.toUpperCase(name.charAt(0)) + name.substring(1));
    }
}
