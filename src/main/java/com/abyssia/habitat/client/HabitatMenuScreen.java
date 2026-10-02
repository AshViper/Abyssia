package com.abyssia.habitat.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.registry.ModHabitat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/** H02 build menu: one row per module (icon, name, size, materials have / need, missing in red). */
public class HabitatMenuScreen extends Screen
{
    private static final int ROW_H = 38, WIDTH = 320;
    private static final Map<HabitatMode, Boolean> ICON_PRESENT = new EnumMap<>(HabitatMode.class);
    private int selected;

    public HabitatMenuScreen(HabitatMode current)
    {
        super(Component.translatable("screen." + Abyssia.MODID + ".habitat.title"));
        this.selected = current.ordinal();
    }

    private int left() { return (width - WIDTH) / 2; }

    private int top() { return (height - ROW_H * HabitatMode.values().length) / 2 + 6; }

    private static ResourceLocation icon(HabitatMode mode)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/item/habitat_icon_" + mode.id + ".png");
    }

    private static boolean hasIcon(HabitatMode mode)
    {
        return ICON_PRESENT.computeIfAbsent(mode, m -> Minecraft.getInstance().getResourceManager().getResource(icon(m)).isPresent());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        // 1.21: Screen.render draws the (blurred) background first, so it runs before the menu instead of after
        super.render(g, mouseX, mouseY, partialTick);
        Player player = Minecraft.getInstance().player;
        int x0 = left(), y0 = top();
        g.drawCenteredString(font, title, width / 2, y0 - 16, 0x9FEFFF);
        HabitatMode[] modes = HabitatMode.values();
        for (int i = 0; i < modes.length; i++)
        {
            HabitatMode mode = modes[i];
            int y = y0 + i * ROW_H;
            boolean hover = mouseX >= x0 && mouseX < x0 + WIDTH && mouseY >= y && mouseY < y + ROW_H - 2;
            g.fill(x0, y, x0 + WIDTH, y + ROW_H - 2, i == selected ? 0xC0205060 : hover ? 0xA0182830 : 0x90101820);
            if (i == selected) g.drawString(font, "▶", x0 + 3, y + 5, 0x9FEFFF);
            if (hasIcon(mode)) g.blit(icon(mode), x0 + 12, y + 2, 0, 0, 16, 16, 16, 16);
            else g.renderItem(new ItemStack(ModHabitat.CONSTRUCTOR.get()), x0 + 12, y + 2);
            g.drawString(font, mode.displayName(), x0 + 32, y + 2, 0xFFFFFF);
            g.drawString(font, Component.translatable("screen." + Abyssia.MODID + ".habitat.size", mode.width, mode.depth, mode.height),
                    x0 + 32, y + 11, 0xA0B0C0);
            int cx = x0 + 32;
            for (HabitatMode.Cost cost : mode.cost)
            {
                Item item = cost.item().get();
                int have = player == null ? 0 : HabitatBuilder.count(player, item);
                boolean creative = player != null && player.getAbilities().instabuild;
                g.renderItem(new ItemStack(item), cx, y + 21);
                String text = have + "/" + cost.count();
                g.drawString(font, text, cx + 18, y + 26, have >= cost.count() || creative ? 0xC0FFC0 : 0xFF5555);
                if (mouseX >= cx && mouseX < cx + 16 && mouseY >= y + 21 && mouseY < y + 37)
                    g.renderTooltip(font, item.getDescription(), mouseX, mouseY);
                cx += 18 + font.width(text) + 8;
            }
        }
        g.drawCenteredString(font, Component.translatable("screen." + Abyssia.MODID + ".habitat.hint"), width / 2,
                y0 + modes.length * ROW_H + 2, 0x8090A0);
    }

    private void choose(int index)
    {
        HabitatClient.select(HabitatMode.values()[index]);
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseX >= left() && mouseX < left() + WIDTH)
        {
            int i = (int) Math.floor((mouseY - top()) / ROW_H);
            if (mouseY >= top() && i >= 0 && i < HabitatMode.values().length)
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
        selected = Math.floorMod(selected + by, HabitatMode.values().length);
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
