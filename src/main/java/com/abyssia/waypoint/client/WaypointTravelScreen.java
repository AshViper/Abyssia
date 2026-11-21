package com.abyssia.waypoint.client;

import com.abyssia.client.gui.UiTheme;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.waypoint.BeaconTravel;
import com.abyssia.waypoint.BeaconTravelPacket;
import com.abyssia.waypoint.WaypointColors;
import com.abyssia.waypoint.WaypointEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * QOL01 beacon travel list (sneak + empty hand on a beacon): the other beacons of this dimension, nearest first, with
 * distance and FE cost. A click sends {@link BeaconTravelPacket}; the server checks ownership, energy and everything else.
 */
public class WaypointTravelScreen extends Screen
{
    private static final int W = 220, H = 166, ROW = 20, ROWS = 6, LIST_X = 8, LIST_Y = 34;

    private final BlockPos from;
    private final List<WaypointEntry> targets = new ArrayList<>();
    private int left, top, scroll;

    public WaypointTravelScreen(BlockPos from)
    {
        super(Component.translatable("gui.abyssia.beacon_travel.title"));
        this.from = from;
    }

    @Override
    protected void init()
    {
        left = (width - W) / 2;
        top = (height - H) / 2;
        targets.clear();
        for (WaypointEntry e : WaypointClient.beacons()) if (!e.pos().equals(from)) targets.add(e);
        targets.sort(Comparator.comparingDouble(e -> e.pos().distSqr(from)));
    }

    private static String nameOf(WaypointEntry e)
    {
        return e.name().isEmpty() ? Component.translatable("block.abyssia.waypoint_beacon").getString() : e.name();
    }

    private int rowAt(double mx, double my)
    {
        if (mx < left + LIST_X || mx >= left + W - LIST_X || my < top + LIST_Y) return -1;
        int r = (int) ((my - top - LIST_Y) / ROW);
        int i = r + scroll;
        return r < ROWS && i < targets.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button)
    {
        int i = button == 0 ? rowAt(mx, my) : -1;
        if (i >= 0)
        {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            AbyssiaNetwork.sendToServer(new BeaconTravelPacket(from, targets.get(i).pos()));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy)
    {
        scroll = Math.max(0, Math.min(Math.max(0, targets.size() - ROWS), scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick)
    {
        super.renderBackground(g, mx, my, partialTick);
        g.fill(left, top, left + W, top + H, UiTheme.BORDER_DARK);
        g.fill(left + 1, top + 1, left + W - 1, top + H - 1, UiTheme.BORDER_LIGHT);
        g.fill(left + 2, top + 2, left + W - 1, top + H - 1, UiTheme.BORDER);
        g.fill(left + 2, top + 2, left + W - 2, top + H - 2, UiTheme.PANEL_SOLID);
        g.drawString(font, title, left + (W - font.width(title)) / 2, top + 10, UiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.abyssia.beacon_travel.hint"), left + LIST_X, top + 22, UiTheme.TEXT_MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick)
    {
        super.render(g, mx, my, partialTick);
        int hover = rowAt(mx, my);
        if (targets.isEmpty())
            g.drawString(font, Component.translatable("gui.abyssia.beacon_travel.none"), left + LIST_X, top + LIST_Y + 6, UiTheme.TEXT_PALE, false);
        for (int r = 0; r < ROWS && r + scroll < targets.size(); r++)
        {
            int i = r + scroll;
            WaypointEntry e = targets.get(i);
            int y = top + LIST_Y + r * ROW;
            g.fill(left + LIST_X, y, left + W - LIST_X, y + ROW - 2, i == hover ? UiTheme.SELECTED : UiTheme.IDLE);
            g.fill(left + LIST_X + 3, y + 5, left + LIST_X + 11, y + 13, 0xFF000000 | WaypointColors.rgb(e.color()));
            double dx = e.pos().getX() - from.getX(), dz = e.pos().getZ() - from.getZ();
            long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
            String info = Component.translatable("gui.abyssia.beacon_travel.row", dist, BeaconTravel.cost(from, e.pos())).getString();
            int infoW = font.width(info);
            g.drawString(font, font.plainSubstrByWidth(nameOf(e), W - 2 * LIST_X - 20 - infoW), left + LIST_X + 16, y + 6, UiTheme.TEXT, false);
            g.drawString(font, info, left + W - LIST_X - 4 - infoW, y + 6, UiTheme.TEXT_PALE, false);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
