package com.abyssia.waypoint.client;

import com.abyssia.ClientConfig;
import com.abyssia.waypoint.WaypointColors;
import com.abyssia.waypoint.WaypointEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * W01 HUD markers: each beacon of the current dimension within max_display_distance (0 = no limit) is projected with the camera
 * matrices of this frame and drawn as a diamond (dark outline + beacon colour) with its name and distance below.
 * Beacons off screen or behind the camera are pinned 16 px inside the screen edge with a small arrow towards them.
 * Markers whose labels would overlap are pushed apart vertically, nearest first.
 */
final class WaypointHud
{
    private static final int EDGE = 16;
    private static final int MAX_NAME_WIDTH = 160;
    private static final int LINE = 10;
    private static final int OUTLINE = 0xE0101418;

    private static Matrix4f view;
    private static Matrix4f projection;
    private static Vec3 cameraPos = Vec3.ZERO;

    private WaypointHud() {}

    static void captureCamera(RenderLevelStageEvent event)
    {
        view = new Matrix4f(event.getPoseStack().last().pose());
        projection = new Matrix4f(event.getProjectionMatrix());
        cameraPos = event.getCamera().getPosition();
    }

    private static final class Marker
    {
        WaypointEntry entry;
        double distance;
        String name;
        String dist;
        int x, y;
        boolean offscreen;
        double dirX, dirY;
        // label and box (filled by layout)
        int textX, textTop, textW;
        int x0, y0, x1, y1;
    }

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height)
    {
        Minecraft mc = Minecraft.getInstance();
        List<WaypointEntry> beacons = WaypointClient.beacons();
        if (mc.options.hideGui || mc.player == null || beacons.isEmpty() || view == null) return;
        Font font = mc.font;
        double maxDistance = ClientConfig.WAYPOINT_MAX_DISTANCE.get();
        double hideWithin = ClientConfig.WAYPOINT_HIDE_WITHIN.get();
        boolean showName = ClientConfig.WAYPOINT_SHOW_NAME.get();
        boolean showDistance = ClientConfig.WAYPOINT_SHOW_DISTANCE.get();
        boolean showOffscreen = ClientConfig.WAYPOINT_SHOW_OFFSCREEN.get();
        int half = ClientConfig.WAYPOINT_MARKER_SIZE.get() / 2;
        String defaultName = Component.translatable("block.abyssia.waypoint_beacon").getString();

        List<Marker> markers = new ArrayList<>();
        for (WaypointEntry e : beacons)
        {
            Vec3 p = Vec3.atCenterOf(e.pos());
            double distance = p.distanceTo(cameraPos);
            if ((maxDistance > 0 && distance > maxDistance) || distance <= hideWithin) continue;
            Vector4f v = new Vector4f((float) (p.x - cameraPos.x), (float) (p.y - cameraPos.y), (float) (p.z - cameraPos.z), 1.0f);
            view.transform(v);
            projection.transform(v);
            Marker m = new Marker();
            boolean behind = v.w <= 0.001f;
            double sx = 0, sy = 0;
            if (!behind)
            {
                sx = (v.x / v.w + 1.0) * 0.5 * width;
                sy = (1.0 - v.y / v.w) * 0.5 * height;
            }
            m.offscreen = behind || sx < 0 || sx > width || sy < 0 || sy > height;
            if (m.offscreen)
            {
                if (!showOffscreen) continue;
                // direction from the screen centre (behind the camera: the clip-space x / y still point the right way)
                double dx = behind ? v.x * 0.5 * width : sx - width * 0.5;
                double dy = behind ? -v.y * 0.5 * height : sy - height * 0.5;
                if (Math.abs(dx) < 1e-4 && Math.abs(dy) < 1e-4) dy = 1.0;
                double t = Math.min(Math.abs(dx) < 1e-6 ? Double.MAX_VALUE : (width * 0.5 - EDGE) / Math.abs(dx),
                        Math.abs(dy) < 1e-6 ? Double.MAX_VALUE : (height * 0.5 - EDGE) / Math.abs(dy));
                sx = width * 0.5 + dx * t;
                sy = height * 0.5 + dy * t;
                double len = Math.sqrt(dx * dx + dy * dy);
                m.dirX = dx / len;
                m.dirY = dy / len;
            }
            m.entry = e;
            m.distance = distance;
            m.x = (int) Math.round(sx);
            m.y = (int) Math.round(sy);
            m.name = showName ? ellipsize(font, e.name().isEmpty() ? defaultName : e.name()) : null;
            m.dist = showDistance ? formatDistance(distance) : null;
            markers.add(m);
        }
        if (markers.isEmpty()) return;

        // nearest first keeps its place; later ones move out of the way
        markers.sort(Comparator.comparingDouble(m -> m.distance));
        List<Marker> placed = new ArrayList<>();
        for (Marker m : markers)
        {
            layout(m, font, half, width, height);
            for (int tries = 0; tries < 8; tries++)
            {
                Marker hit = null;
                for (Marker o : placed)
                    if (m.x0 < o.x1 && m.x1 > o.x0 && m.y0 < o.y1 && m.y1 > o.y0)
                    {
                        hit = o;
                        break;
                    }
                if (hit == null) break;
                int down = hit.y1 - m.y0 + 2;
                m.y += m.y1 + down <= height ? down : -(m.y1 - hit.y0 + 2);
                layout(m, font, half, width, height);
            }
            placed.add(m);
        }

        // far ones first so the near ones end on top
        for (int i = placed.size() - 1; i >= 0; i--) draw(g, font, placed.get(i), half);
    }

    private static void layout(Marker m, Font font, int half, int width, int height)
    {
        int lines = (m.name != null ? 1 : 0) + (m.dist != null ? 1 : 0);
        int nameW = m.name != null ? font.width(m.name) : 0;
        int distW = m.dist != null ? font.width(m.dist) : 0;
        m.textW = Math.max(nameW, distW);
        m.textX = Math.max(2, Math.min(width - 2 - m.textW, m.x - m.textW / 2));
        m.textTop = m.y + half + 3;
        // no room below (bottom edge): the label goes above the diamond
        if (m.textTop + lines * LINE > height - 2) m.textTop = m.y - half - 3 - lines * LINE;
        m.x0 = Math.min(m.x - half, lines > 0 ? m.textX : m.x - half);
        m.x1 = Math.max(m.x + half + 1, lines > 0 ? m.textX + m.textW : m.x + half + 1);
        m.y0 = Math.min(m.y - half, lines > 0 ? m.textTop : m.y - half);
        m.y1 = Math.max(m.y + half + 1, m.textTop + lines * LINE);
    }

    private static void draw(GuiGraphics g, Font font, Marker m, int half)
    {
        int color = 0xFF000000 | WaypointColors.rgb(m.entry.color());
        diamond(g, m.x, m.y, half, OUTLINE);
        diamond(g, m.x, m.y, half - 1, color);
        if (m.offscreen) arrow(g, m.x + m.dirX * (half + 6), m.y + m.dirY * (half + 6), m.dirX, m.dirY, color);
        int y = m.textTop;
        if (m.name != null)
        {
            g.drawString(font, m.name, m.textX + (m.textW - font.width(m.name)) / 2, y, 0xFFFFFF, true);
            y += LINE;
        }
        if (m.dist != null) g.drawString(font, m.dist, m.textX + (m.textW - font.width(m.dist)) / 2, y, 0xD8D8D8, true);
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int half, int argb)
    {
        for (int dy = -half; dy <= half; dy++)
        {
            int hw = half - Math.abs(dy);
            g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, argb);
        }
    }

    /** A small filled triangle around (cx, cy) pointing along (dx, dy), with a dark shadow. */
    private static void arrow(GuiGraphics g, double cx, double cy, double dx, double dy, int argb)
    {
        for (int pass = 0; pass < 2; pass++)
        {
            int col = pass == 0 ? OUTLINE : argb;
            int off = pass == 0 ? 1 : 0;
            for (int py = -4; py <= 4; py++)
                for (int px = -4; px <= 4; px++)
                {
                    double u = px * dx + py * dy;          // along the arrow
                    double w = -px * dy + py * dx;         // across
                    if (u < -2.0 || u > 3.0 || Math.abs(w) > (3.0 - u) * 0.6) continue;
                    int x = (int) Math.round(cx) + px + off;
                    int y = (int) Math.round(cy) + py + off;
                    g.fill(x, y, x + 1, y + 1, col);
                }
        }
    }

    private static String ellipsize(Font font, String name)
    {
        if (font.width(name) <= MAX_NAME_WIDTH) return name;
        return font.plainSubstrByWidth(name, MAX_NAME_WIDTH - font.width("…")) + "…";
    }

    /** 182m / 1.24km (from 1000 m) / 10.0km (from 10000 m) */
    static String formatDistance(double metres)
    {
        if (metres < 1000) return (int) metres + "m";
        if (metres < 10000) return String.format(Locale.ROOT, "%.2fkm", metres / 1000.0);
        return String.format(Locale.ROOT, "%.1fkm", metres / 1000.0);
    }
}
