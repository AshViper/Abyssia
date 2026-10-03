package com.abyssia.habitat.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.registry.ModHabitat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H02 / BT01a build menu: a tab per BuildCategory, then one row per entry of the selected category (icon, name,
 * detail, materials have / need, missing in red).
 */
public class HabitatMenuScreen extends Screen
{
    private static final int ROW_H = 38, WIDTH = 320, TAB_H = 14, TAB_GAP = 4;
    private static final Map<ResourceLocation, Boolean> ICON_PRESENT = new HashMap<>();
    private static final BuildCategory[] TABS = BuildCategory.values();
    private int category;
    private int selected;

    public HabitatMenuScreen(BuildEntry current)
    {
        super(Component.translatable("screen." + Abyssia.MODID + ".habitat.title"));
        this.category = current.category().ordinal();
        this.selected = Math.max(0, entries().indexOf(current));
    }

    private List<BuildEntry> entries()
    {
        return BuildRegistry.byCategory(TABS[category]);
    }

    private int rows()
    {
        return Math.max(1, entries().size());
    }

    private int left() { return (width - WIDTH) / 2; }

    private int tabTop() { return (height - ROW_H * rows() - TAB_H - TAB_GAP) / 2 + 6; }

    private int top() { return tabTop() + TAB_H + TAB_GAP; }

    private int tabWidth() { return WIDTH / TABS.length; }

    private static boolean hasIcon(ResourceLocation icon)
    {
        return ICON_PRESENT.computeIfAbsent(icon, r -> Minecraft.getInstance().getResourceManager().getResource(r).isPresent());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        // 1.21: Screen.render draws the (blurred) background first, so it runs before the menu instead of after
        super.render(g, mouseX, mouseY, partialTick);
        Player player = Minecraft.getInstance().player;
        boolean creative = player != null && player.getAbilities().instabuild;
        int x0 = left(), ty = tabTop(), y0 = top(), tw = tabWidth();
        g.drawCenteredString(font, title, width / 2, ty - 16, 0x9FEFFF);
        for (int t = 0; t < TABS.length; t++)
        {
            int tx = x0 + t * tw;
            boolean hover = mouseX >= tx && mouseX < tx + tw - 2 && mouseY >= ty && mouseY < ty + TAB_H;
            boolean empty = BuildRegistry.byCategory(TABS[t]).isEmpty();
            g.fill(tx, ty, tx + tw - 2, ty + TAB_H, t == category ? 0xC0205060 : hover ? 0xA0182830 : 0x90101820);
            Component name = TABS[t].displayName();
            int colour = t == category ? 0x9FEFFF : empty ? 0x607080 : 0xC0D0E0;
            float scale = Math.min(1.0f, (tw - 6) / (float) Math.max(1, font.width(name)));
            g.pose().pushPose();
            g.pose().translate(tx + (tw - 2) / 2.0f, ty + TAB_H / 2.0f - 4 * scale, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.drawCenteredString(font, name, 0, 0, colour);
            g.pose().popPose();
        }
        List<BuildEntry> entries = entries();
        if (entries.isEmpty())
            g.drawCenteredString(font, Component.translatable("screen." + Abyssia.MODID + ".habitat.empty"), width / 2, y0 + ROW_H / 2 - 4, 0x8090A0);
        for (int i = 0; i < entries.size(); i++)
        {
            BuildEntry entry = entries.get(i);
            int y = y0 + i * ROW_H;
            boolean hover = mouseX >= x0 && mouseX < x0 + WIDTH && mouseY >= y && mouseY < y + ROW_H - 2;
            g.fill(x0, y, x0 + WIDTH, y + ROW_H - 2, i == selected ? 0xC0205060 : hover ? 0xA0182830 : 0x90101820);
            if (i == selected) g.drawString(font, "▶", x0 + 3, y + 5, 0x9FEFFF);
            ResourceLocation icon = entry.icon();
            if (icon != null && hasIcon(icon)) g.blit(icon, x0 + 12, y + 2, 0, 0, 16, 16, 16, 16);
            else g.renderItem(new ItemStack(ModHabitat.CONSTRUCTOR.get()), x0 + 12, y + 2);
            g.drawString(font, entry.displayName(), x0 + 32, y + 2, 0xFFFFFF);
            Component detail = entry.detail();
            if (detail != null) g.drawString(font, detail, x0 + 32, y + 11, 0xA0B0C0);
            int cx = x0 + 32;
            for (ItemStack cost : entry.cost())
            {
                int have = player == null ? 0 : HabitatBuilder.count(player, cost.getItem());
                g.renderItem(cost.copyWithCount(1), cx, y + 21);
                String text = have + "/" + cost.getCount();
                g.drawString(font, text, cx + 18, y + 26, have >= cost.getCount() || creative ? 0xC0FFC0 : 0xFF5555);
                if (mouseX >= cx && mouseX < cx + 16 && mouseY >= y + 21 && mouseY < y + 37)
                    g.renderTooltip(font, cost.getHoverName(), mouseX, mouseY);
                cx += 18 + font.width(text) + 8;
            }
        }
        g.drawCenteredString(font, Component.translatable("screen." + Abyssia.MODID + ".habitat.hint"), width / 2,
                y0 + rows() * ROW_H + 2, 0x8090A0);
    }

    private void choose(int index)
    {
        List<BuildEntry> entries = entries();
        if (index < 0 || index >= entries.size()) return;
        HabitatClient.select(entries.get(index));
        onClose();
    }

    private void tab(int index)
    {
        int next = Math.floorMod(index, TABS.length);
        if (next == category) return;
        category = next;
        selected = 0;
        HabitatClient.click();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseX >= left() && mouseX < left() + WIDTH)
        {
            if (mouseY >= tabTop() && mouseY < tabTop() + TAB_H)
            {
                tab((int) ((mouseX - left()) / tabWidth()));
                return true;
            }
            int i = (int) Math.floor((mouseY - top()) / ROW_H);
            if (mouseY >= top() && i >= 0 && i < entries().size())
            {
                choose(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double delta)
    {
        move(delta > 0 ? -1 : 1);
        return true;
    }

    private void move(int by)
    {
        selected = Math.floorMod(selected + by, rows());
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if (HabitatClient.MENU.matches(key, scan))
        {
            onClose();
            return true;
        }
        switch (key)
        {
            case GLFW.GLFW_KEY_UP -> { move(-1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { move(1); return true; }
            case GLFW.GLFW_KEY_LEFT -> { tab(category - 1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { tab(category + 1); return true; }
            case GLFW.GLFW_KEY_TAB -> { tab(category + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1)); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { choose(selected); return true; }
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
    }

    @Override
    public void onClose()
    {
        HabitatClient.click();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
