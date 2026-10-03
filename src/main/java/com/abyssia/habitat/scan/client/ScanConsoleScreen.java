package com.abyssia.habitat.scan.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.scan.ScanConsoleBlockEntity;
import com.abyssia.habitat.scan.ScanConsoleMenu;
import com.abyssia.habitat.scan.ScanData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * H07 terminal: target list (all + kinds found, with counts) on the left, the scan map on the right (drag = rotate,
 * wheel = zoom, R = face north), FE gauge, SCAN button and progress.
 */
public class ScanConsoleScreen extends AbstractContainerScreen<ScanConsoleMenu>
{
    private static final int LIST_W = 110, ROW = 11, MAP_X = 118, MAP_Y = 16, MAP_W = 194, MAP_H = 148;
    private static final float PITCH = 25.0f;
    private static final String K = "gui." + Abyssia.MODID + ".scan.";

    private float yaw, pitch = PITCH, zoom = 1.6f;
    private int listScroll;
    private Button scanButton;

    public ScanConsoleScreen(ScanConsoleMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 196;
    }

    private ScanConsoleBlockEntity console()
    {
        return minecraft == null || minecraft.player == null ? null : menu.console(minecraft.player);
    }

    @Override
    protected void init()
    {
        super.init();
        scanButton = addRenderableWidget(Button.builder(Component.translatable(K + "button"), b -> button(ScanConsoleMenu.BUTTON_SCAN))
                .bounds(leftPos + 8, topPos + imageHeight - 26, 60, 18).build());
    }

    private void button(int id)
    {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY)
    {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xE0081418);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFF2A8A9A);
        g.fill(x + 6, y + MAP_Y, x + 6 + LIST_W, y + MAP_Y + MAP_H, 0xC0040A0C);
        g.fill(x + MAP_X, y + MAP_Y, x + MAP_X + MAP_W, y + MAP_Y + MAP_H, 0xC0020608);
        g.renderOutline(x + MAP_X, y + MAP_Y, MAP_W, MAP_H, 0xFF1C5A66);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(g);
        scanButton.active = !menu.scanning() && menu.energy() >= ScanConsoleBlockEntity.SCAN_COST;
        super.render(g, mouseX, mouseY, partialTick);
        ScanConsoleBlockEntity be = console();
        if (be != null)
        {
            renderList(g, be, mouseX, mouseY);
            renderMap(g, be);
        }
        renderStatus(g, be);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY)
    {
        g.drawString(font, title, 8, 5, 0x9FEFFF, false);
        ScanConsoleBlockEntity be = console();
        if (be != null)
        {
            Component level = Component.translatable(K + "level", be.upgrade(), be.radius(), be.halfHeight());
            g.drawString(font, level, imageWidth - 8 - font.width(level), 5, 0x7FD0E0, false);
        }
        g.drawString(font, Component.translatable(K + "hint"), MAP_X, MAP_Y + MAP_H + 3, 0x6A8A94, false);
    }

    private int rows()
    {
        return MAP_H / ROW;
    }

    private void renderList(GuiGraphics g, ScanConsoleBlockEntity be, int mouseX, int mouseY)
    {
        ScanData data = be.result();
        int x = leftPos + 8, y = topPos + MAP_Y + 2;
        int entries = data.palette.size() + 1;
        listScroll = Math.max(0, Math.min(listScroll, entries - rows()));
        for (int row = 0; row < rows() && row + listScroll < entries; row++)
        {
            int i = row + listScroll;
            int ry = y + row * ROW;
            boolean selected = i == 0 ? be.target() == null : data.palette.get(i - 1).equals(be.target());
            boolean hover = mouseX >= x - 2 && mouseX < x + LIST_W - 2 && mouseY >= ry - 1 && mouseY < ry + ROW - 1;
            if (selected || hover) g.fill(x - 2, ry - 1, x + LIST_W - 2, ry + ROW - 1, selected ? 0xC0205060 : 0x80183038);
            Component name;
            int count;
            if (i == 0)
            {
                name = Component.translatable(K + "all");
                count = data.total();
            }
            else
            {
                ResourceLocation id = data.palette.get(i - 1);
                name = BuiltInRegistries.BLOCK.get(id).getName();
                count = data.counts[i - 1];
            }
            String num = String.valueOf(count);
            String text = font.plainSubstrByWidth(name.getString(), LIST_W - 10 - font.width(num));
            g.drawString(font, text, x, ry, selected ? 0xFFFFFF : 0xB0D8E0, false);
            g.drawString(font, num, x + LIST_W - 6 - font.width(num), ry, 0x7FD0E0, false);
        }
    }

    private void renderMap(GuiGraphics g, ScanConsoleBlockEntity be)
    {
        int x0 = leftPos + MAP_X, y0 = topPos + MAP_Y;
        g.enableScissor(x0 + 1, y0 + 1, x0 + MAP_W - 1, y0 + MAP_H - 1);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x0 + MAP_W / 2.0f, y0 + MAP_H / 2.0f, 300.0f);
        float z = zoom * 32.0f / ScanData.radius(be.upgrade());   // bigger maps start at the same on-screen size
        pose.scale(z, -z, z);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        ScanMapRenderer.draw(pose, g.bufferSource(), be, 1.0f);
        g.flush();
        pose.popPose();
        g.disableScissor();
    }

    private void renderStatus(GuiGraphics g, ScanConsoleBlockEntity be)
    {
        int x = leftPos + 74, y = topPos + imageHeight - 25;
        Component status;
        int colour = 0xB0D8E0;
        if (menu.scanning()) status = Component.translatable(K + "progress", menu.progress() / 10);
        else if (menu.energy() < ScanConsoleBlockEntity.SCAN_COST)
        {
            status = Component.translatable(K + "no_energy", ScanConsoleBlockEntity.SCAN_COST);
            colour = 0xFF6060;
        }
        else status = Component.translatable(K + "ready", ScanConsoleBlockEntity.SCAN_COST);
        g.drawString(font, status, x, y, colour, false);
        Component results = be == null || !be.result().done ? Component.translatable(K + "none")
                : Component.translatable(K + "results", be.resultCount());
        g.drawString(font, results, x, y + 10, 0x7FD0E0, false);

        // FE gauge
        int gx = leftPos + MAP_X + MAP_W - 100, gy = topPos + imageHeight - 22, gw = 100;
        int energy = menu.energy();
        g.fill(gx, gy, gx + gw, gy + 6, 0xFF101C20);
        g.fill(gx, gy, gx + (int) ((long) gw * energy / ScanConsoleBlockEntity.CAPACITY), gy + 6, 0xFF30D0E8);
        g.renderOutline(gx - 1, gy - 1, gw + 2, 8, 0xFF2A8A9A);
        String fe = energy + " / " + ScanConsoleBlockEntity.CAPACITY + " FE";
        g.drawString(font, fe, gx + gw - font.width(fe), gy + 9, 0x9FC8D0, false);
        if (menu.scanning())
        {
            int px = leftPos + MAP_X, py = topPos + MAP_Y + MAP_H - 3;
            g.fill(px + 1, py, px + 1 + (MAP_W - 2) * menu.progress() / 1000, py + 2, 0xFF60F0FF);
        }
    }

    private boolean inMap(double mx, double my)
    {
        return mx >= leftPos + MAP_X && mx < leftPos + MAP_X + MAP_W && my >= topPos + MAP_Y && my < topPos + MAP_Y + MAP_H;
    }

    private boolean inList(double mx, double my)
    {
        return mx >= leftPos + 6 && mx < leftPos + 6 + LIST_W && my >= topPos + MAP_Y && my < topPos + MAP_Y + MAP_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button)
    {
        ScanConsoleBlockEntity be = console();
        if (button == 0 && be != null && inList(mx, my))
        {
            int i = (int) ((my - topPos - MAP_Y - 1) / ROW) + listScroll;
            if (i == 0)
            {
                button(ScanConsoleMenu.BUTTON_ALL);
                return true;
            }
            if (i > 0 && i <= be.result().palette.size())
            {
                button(ScanConsoleMenu.BUTTON_KIND + i - 1);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy)
    {
        if (button == 0 && inMap(mx, my))
        {
            yaw += (float) dx * 0.8f;
            pitch = Math.max(-89.0f, Math.min(89.0f, pitch + (float) dy * 0.8f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta)
    {
        if (inMap(mx, my))
        {
            zoom = (float) Math.max(0.6, Math.min(8.0, zoom * Math.pow(1.15, delta)));
            return true;
        }
        if (inList(mx, my))
        {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if (key == GLFW.GLFW_KEY_R)
        {
            yaw = 0.0f;
            pitch = PITCH;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
