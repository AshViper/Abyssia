package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.SubmarineUpgradeMenu;
import com.abyssia.vehicle.SubmarineUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SUB03 "Submarine Systems" screen on the vanilla chest texture: header, a status line ("⚡ x / y FE  ❤ z%"), the
 * 5 upgrade slots centred in one row, then the inventory. Empty slots name their kind; the battery / hull slots warn
 * about the charge lost on removal / the damaged hull lock.
 */
public class SubmarineUpgradeScreen extends AbstractContainerScreen<SubmarineUpgradeMenu>
{
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final String C = "container." + Abyssia.MODID + ".submarine.upgrades";

    public SubmarineUpgradeScreen(SubmarineUpgradeMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = SubmarineUpgradeMenu.WIDTH;
        imageHeight = SubmarineUpgradeMenu.HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y)
    {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index < SubmarineUpgrades.SLOTS)
        {
            SubmarineUpgrades.Kind kind = SubmarineUpgrades.Kind.values()[hoveredSlot.index];
            List<Component> lines = new ArrayList<>();
            if (!hoveredSlot.hasItem()) lines.add(Component.translatable(C + ".slot." + kind.slotName).withStyle(ChatFormatting.GRAY));
            else
            {
                ItemStack stack = hoveredSlot.getItem();
                lines.addAll(getTooltipFromContainerItem(stack));
                Submarine sub = menu.submarine();
                if (kind == SubmarineUpgrades.Kind.BATTERY && sub != null && sub.getEnergy() > Submarine.capacity())
                    lines.add(Component.translatable("tooltip." + Abyssia.MODID + ".submarine_upgrade.battery_warning",
                            NumberFormat.getIntegerInstance(Locale.US).format(Submarine.capacity())).withStyle(ChatFormatting.GOLD));
                if (kind == SubmarineUpgrades.Kind.HULL && menu.hullLocked())
                    lines.add(Component.translatable("message." + Abyssia.MODID + ".submarine.upgrade_hull_damaged").withStyle(ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, lines, x, y);
            return;
        }
        super.renderTooltip(g, x, y);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        int x = leftPos, y = topPos;
        int middle = SubmarineUpgradeMenu.SLOT_Y;   // header (17) .. bottom part: status line + slot row (frames end at SLOT_Y + 17)
        g.blit(CHEST, x, y, 0, 0, imageWidth, 17);
        // plain panel between the header and the inventory: the 1 px row above the chest slots, stretched
        g.blit(CHEST, x, y + 17, imageWidth, middle, 0, 16, imageWidth, 1, 256, 256);
        for (int i = 0; i < SubmarineUpgrades.SLOTS; i++)
            g.blit(CHEST, x + SubmarineUpgradeMenu.SLOT_X - 1 + i * 18, y + SubmarineUpgradeMenu.SLOT_Y - 1, 7, 17, 18, 18);
        g.blit(CHEST, x, y + 17 + middle, 0, 125, imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        super.renderLabels(g, mouseX, mouseY);
        Submarine sub = menu.submarine();
        if (sub == null) return;
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        Component status = Component.translatable(C + ".status", nf.format(sub.getEnergy()), nf.format(sub.maxEnergy()), sub.hullPercent());
        g.drawString(font, status, (imageWidth - font.width(status)) / 2, 18, 0x404040, false);
    }
}
