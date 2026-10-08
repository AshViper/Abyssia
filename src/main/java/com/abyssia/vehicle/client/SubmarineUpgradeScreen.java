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
import net.minecraft.world.inventory.Slot;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * SUB03 "Submarine Systems" screen on the vanilla chest background (generic_54: header, a text strip, one slot row
 * with only the 4 upgrade frames, the player inventory). Energy / hull come from the synced entity. Empty slots tell
 * what they take; the battery slot warns when taking it out would lose charge.
 */
public class SubmarineUpgradeScreen extends AbstractContainerScreen<SubmarineUpgradeMenu>
{
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final String[] SLOT_KEYS = {"hull", "battery", "thruster", "utility", "depth"};

    public SubmarineUpgradeScreen(SubmarineUpgradeMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 143;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        Slot slot = hoveredSlot;
        if (slot == null || slot.index >= SubmarineUpgrades.SLOTS || !menu.getCarried().isEmpty()) return;
        if (!slot.hasItem())
            g.renderTooltip(font, Component.translatable("container." + Abyssia.MODID + ".submarine.upgrades.slot." + SLOT_KEYS[slot.index]), mouseX, mouseY);
        else
        {
            Submarine sub = menu.submarine();
            if (sub == null) return;
            if (slot.index == SubmarineUpgrades.BATTERY && sub.getEnergy() > Submarine.capacity())
                g.renderComponentTooltip(font, List.of(Component.translatable("tooltip." + Abyssia.MODID + ".submarine_upgrade.battery_warning",
                        NumberFormat.getIntegerInstance(Locale.US).format(Submarine.capacity())).withStyle(ChatFormatting.RED)), mouseX, mouseY + 16);
            if (slot.index == SubmarineUpgrades.HULL && sub.getDamage() > Submarine.MAX_DAMAGE)
                g.renderComponentTooltip(font, List.of(Component.translatable("message." + Abyssia.MODID + ".submarine.upgrade_hull_damaged")
                        .withStyle(ChatFormatting.RED)), mouseX, mouseY + 16);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        int x = leftPos, y = topPos;
        g.blit(CHEST, x, y, 0, 0, imageWidth, 17);                 // header
        g.blit(CHEST, x, y + 17, 0, 5, imageWidth, 12);            // text strip (plain header rows)
        g.blit(CHEST, x, y + 29, 0, 5, imageWidth, 12);            // slot row backdrop
        g.blit(CHEST, x, y + 41, 0, 5, imageWidth, 6);
        for (int i = 0; i < SubmarineUpgrades.SLOTS; i++)
            g.blit(CHEST, x + SubmarineUpgradeMenu.SLOT_X - 1 + i * 18, y + SubmarineUpgradeMenu.SLOT_Y - 1, 7, 17, 18, 18);
        g.blit(CHEST, x, y + 47, 0, 126, imageWidth, 96);          // player inventory
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        super.renderLabels(g, mouseX, mouseY);
        Submarine sub = menu.submarine();
        if (sub == null) return;
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        Component info = Component.translatable("container." + Abyssia.MODID + ".submarine.upgrades.status",
                nf.format(sub.getEnergy()), nf.format(sub.maxEnergy()), sub.hullPercent());
        g.drawString(font, info, 8, 18, 0x404040, false);
    }
}
