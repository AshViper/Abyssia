package com.abyssia.research.client;

import com.abyssia.client.gui.UiTheme;
import com.abyssia.worldgen.DeepLayer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AB05 research database (display only, no menu). Technologies tab: a pannable diagram, one horizontal stripe per depth band, nodes
 * joined by prerequisite lines. Fauna / Resources / Places tabs: an icon grid. A detail panel sits along the bottom.
 * Reads {@link ResearchView} only; unknown / broken ids just show their raw name.
 */
public class DatabaseScreen extends Screen
{
    private enum Tab
    {
        FAUNA("fauna"), RESOURCES("resources"), TECHNOLOGIES("technologies"), PLACES("places");

        final String key;
        Tab(String key) { this.key = key; }

        /** ScanTarget category string to tab: unknown categories (wreck, vent, structure, cave...) land in Places */
        static Tab ofCategory(String category)
        {
            String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
            if (c.contains("vent") || c.contains("wreck") || c.contains("environment") || c.contains("site")) return PLACES;
            if (c.contains("fauna") || c.contains("creature") || c.contains("mob") || c.contains("animal") || c.contains("entity")) return FAUNA;
            if (c.contains("resource") || c.contains("plant") || c.contains("mineral") || c.contains("ore") || c.contains("deposit")) return RESOURCES;
            return PLACES;
        }
    }

    /** one node / cell; details are built when it is selected. icon is ItemStack.EMPTY when there is none. */
    private record Entry(Component name, String label, Component status, int color, List<Component> detail, ItemStack icon) {}

    private static final int PAD = 4, DETAIL_H = 62, TAB_BW = 52, TAB_BH = 16;
    private static final int LABEL_W = 38, NODE = 18, COL = 30, ROWP = 22, BAND_PAD = 4, MAX_ROWS = 4;
    private static final int CELL = 20, CELL_P = 22;
    private static final int GREEN = 0xFF58E6A0, GRAY = UiTheme.TEXT_MUTED, YELLOW = 0xFFFFD866, WHITE = UiTheme.TEXT;
    private static final int DARK_GREEN = 0xFF3A8F66, LOCKED_LINE = 0xFF3A4654, NODE_BG = 0xFF0C1620, CELL_BG_UNKNOWN = 0xFF101820;

    private int W = 360, H = 224;
    private Tab tab = Tab.TECHNOLOGIES;
    private final Button[] tabButtons = new Button[Tab.values().length];
    private List<Entry> entries = List.of();
    private int selected, detailScroll;
    private List<FormattedCharSequence> cachedLines;
    private Entry cachedFor;
    private int cachedWidth, cachedStatusLines;
    private int left, top;
    private int progressDone, progressTotal;

    // technology diagram
    private List<ResearchView.Tech> techs = List.of();
    private int[] nodeX = new int[0], nodeY = new int[0];
    private int[][] prereq = new int[0][];
    private final int[] bandTop = new int[6], bandHeight = new int[6];
    private final boolean[] bandPresent = new boolean[6];
    private int canvasW, canvasH, panX, panY;
    private boolean pressing, panning;
    private double pressX, pressY;
    private int pressPanX, pressPanY;

    // grid
    private int gridScroll;

    public DatabaseScreen()
    {
        super(Component.translatable("screen.abyssia.database.title"));
    }

    @Override
    protected void init()
    {
        W = Mth.clamp(width - 16, 360, 440);
        H = Mth.clamp(height - 16, 224, 300);
        left = (width - W) / 2;
        top = (height - H) / 2;
        int tx = left + W - PAD - (Tab.values().length * TAB_BW + (Tab.values().length - 1) * 2);
        for (Tab t : Tab.values())
        {
            int i = t.ordinal();
            tabButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.abyssia.database.tab." + t.key), b -> select(t))
                    .bounds(tx + i * (TAB_BW + 2), top + 3, TAB_BW, TAB_BH).build());
        }
        select(tab);
    }

    private void select(Tab t)
    {
        tab = t;
        for (Tab x : Tab.values()) if (tabButtons[x.ordinal()] != null) tabButtons[x.ordinal()].active = x != t;
        entries = build(t);
        selected = 0;
        gridScroll = 0;
        detailScroll = 0;
        panX = 0;
        panY = 0;
        pressing = false;
        panning = false;
        if (t == Tab.TECHNOLOGIES)
        {
            layoutTechs();
            // start on the top-left node (a shallow technology), not list index 0 which may sit off-screen
            for (int i = 1; i < nodeX.length; i++)
                if (nodeY[i] < nodeY[selected] || nodeY[i] == nodeY[selected] && nodeX[i] < nodeX[selected]) selected = i;
        }
    }

    // ----- data -----

    private List<Entry> build(Tab t)
    {
        List<Entry> out = new ArrayList<>();
        techs = List.of();
        progressDone = 0;
        try
        {
            if (t == Tab.TECHNOLOGIES)
            {
                List<ResearchView.Tech> list = new ArrayList<>();
                for (ResearchView.Tech tech : ResearchView.technologies())
                {
                    out.add(techEntry(tech));
                    list.add(tech);
                    if (ResearchView.isUnlocked(tech.id())) progressDone++;
                }
                techs = list;
            }
            else
            {
                for (ResearchView.Target target : ResearchView.targets())
                    if (Tab.ofCategory(target.category()) == t)
                    {
                        out.add(targetEntry(target));
                        if (ResearchView.isScanned(target.id())) progressDone++;
                    }
            }
        }
        catch (RuntimeException e)
        {
            // broken sync data: show what we have
        }
        if (t != Tab.TECHNOLOGIES)
        {
            // the fauna / resource lists are long: alphabetical by displayed name
            java.text.Collator collator = java.text.Collator.getInstance();
            out.sort((a, b) -> collator.compare(a.label(), b.label()));
        }
        progressTotal = out.size();
        return out;
    }

    private static ItemStack iconOf(ResourceLocation id)
    {
        if (id == null) return ItemStack.EMPTY;
        try
        {
            return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        }
        catch (RuntimeException e)
        {
            return ItemStack.EMPTY;
        }
    }

    private static Entry targetEntry(ResearchView.Target target)
    {
        ResourceLocation id = target.id();
        Component name = target.nameKey().isEmpty() ? Component.literal(id.getPath()) : Component.translatable(target.nameKey());
        int[] frag = ResearchView.fragmentStatus(id);
        Component status;
        int color;
        if (frag != null && frag[0] > 0 && frag[0] < frag[1]) { status = Component.translatable("hud.abyssia.scan.fragments", frag[0], frag[1]); color = YELLOW; }
        else if (ResearchView.isScanned(id)) { status = Component.translatable("screen.abyssia.database.status.scanned"); color = GREEN; }
        else { status = Component.translatable("screen.abyssia.database.status.unscanned"); color = GRAY; }
        List<Component> detail = new ArrayList<>();
        detail.add(Component.translatable("screen.abyssia.database.category", target.category()));
        if (!target.descKey().isEmpty() && I18n.exists(target.descKey())) detail.add(Component.translatable(target.descKey()));
        return new Entry(name, name.getString(), status, color, detail, iconOf(target.icon()));
    }

    private static Entry techEntry(ResearchView.Tech tech)
    {
        boolean unlocked = ResearchView.isUnlocked(tech.id());
        Component status = Component.translatable("screen.abyssia.database.status." + (unlocked ? "unlocked" : "locked"));
        List<Component> detail = new ArrayList<>();
        if (!tech.tier().isEmpty()) detail.add(Component.translatable("screen.abyssia.database.tier", tech.tier().toUpperCase(Locale.ROOT)));
        if (!tech.depthBand().isEmpty()) detail.add(Component.translatable("screen.abyssia.database.depth", tech.depthBand().toUpperCase(Locale.ROOT)));
        if (!tech.requirements().isEmpty())
        {
            detail.add(Component.translatable("screen.abyssia.database.requirements"));
            for (ResearchView.Req r : tech.requirements())
                detail.add(Component.literal("  ").append(ResearchView.targetName(r.target()))
                        .append(" " + ResearchView.progress(r) + " / " + r.count()));
        }
        if (!tech.prerequisites().isEmpty())
        {
            detail.add(Component.translatable("screen.abyssia.database.prerequisites"));
            for (ResourceLocation p : tech.prerequisites())
                detail.add(Component.literal("  ").append(ResearchView.techTitle(p)).append(Component.literal(ResearchView.isUnlocked(p) ? " [OK]" : "")));
        }
        if (!tech.unlocks().isEmpty())
        {
            detail.add(Component.translatable("screen.abyssia.database.unlocks"));
            for (String u : tech.unlocks()) detail.add(Component.literal("  " + u));
        }
        return new Entry(tech.title(), tech.title().getString(), status, unlocked ? GREEN : GRAY, detail, iconOf(tech.icon()));
    }

    // ----- technology diagram layout -----

    private static int bandIndex(String band)
    {
        if (band == null || band.isEmpty()) return 5;
        char c = Character.toUpperCase(band.charAt(0));
        return c >= 'A' && c <= 'E' ? c - 'A' : 5;
    }

    private static String bandName(int b) { return b < 5 ? String.valueOf((char) ('A' + b)) : "?"; }

    private static String bandBottom(int b)
    {
        return switch (b)
        {
            case 0 -> "Y" + DeepLayer.ABYSS_TOP_Y;
            case 1 -> "Y" + DeepLayer.BAND_C_TOP_Y;
            case 2 -> "Y" + DeepLayer.BAND_D_TOP_Y;
            case 3 -> "Y" + DeepLayer.BAND_E_TOP_Y;
            case 4 -> "Y" + DeepLayer.CRUST_BOTTOM_Y;
            default -> "";
        };
    }

    private int levelOf(int i, int[] memo, int[][] pre)
    {
        if (memo[i] >= 0) return memo[i];
        if (memo[i] == -2) return 0;
        memo[i] = -2;
        int l = 0;
        for (int p : pre[i]) l = Math.max(l, levelOf(p, memo, pre) + 1);
        memo[i] = l;
        return l;
    }

    private void layoutTechs()
    {
        int n = techs.size();
        nodeX = new int[n];
        nodeY = new int[n];
        prereq = new int[n][];
        java.util.Arrays.fill(bandHeight, 0);
        java.util.Arrays.fill(bandTop, 0);
        java.util.Arrays.fill(bandPresent, false);
        canvasW = LABEL_W;
        canvasH = 0;
        if (n == 0) return;

        Map<ResourceLocation, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(techs.get(i).id(), i);
        for (int i = 0; i < n; i++)
        {
            List<Integer> ps = new ArrayList<>();
            for (ResourceLocation p : techs.get(i).prerequisites())
            {
                Integer j = idx.get(p);
                if (j != null && j != i) ps.add(j);
            }
            prereq[i] = ps.stream().mapToInt(Integer::intValue).toArray();
        }
        int[] memo = new int[n];
        java.util.Arrays.fill(memo, -1);
        int[] level = new int[n];
        int maxLevel = 0;
        for (int i = 0; i < n; i++) { level[i] = levelOf(i, memo, prereq); maxLevel = Math.max(maxLevel, level[i]); }

        int[] band = new int[n], col = new int[n], row = new int[n];
        boolean[] placed = new boolean[n];
        int[][] used = new int[6][n + 2];
        for (int i = 0; i < n; i++) band[i] = bandIndex(techs.get(i).depthBand());

        for (int lvl = 0; lvl <= maxLevel; lvl++)
        {
            List<Integer> group = new ArrayList<>();
            for (int i = 0; i < n; i++) if (level[i] == lvl) group.add(i);
            Map<Integer, Double> avg = new HashMap<>();
            for (int i : group)
            {
                double sum = 0;
                int c = 0;
                for (int p : prereq[i]) if (placed[p]) { sum += band[p] * MAX_ROWS + row[p]; c++; }
                avg.put(i, c == 0 ? 0.0 : sum / c);
            }
            group.sort(Comparator.<Integer>comparingInt(i -> band[i]).thenComparingDouble(avg::get)
                    .thenComparing(i -> techs.get(i).title().getString()));
            for (int i : group)
            {
                int minCol = 0;
                for (int p : prereq[i]) if (placed[p]) minCol = Math.max(minCol, col[p] + 1);
                int c = minCol;
                while (c < n + 1 && used[band[i]][c] >= MAX_ROWS) c++;
                col[i] = c;
                row[i] = used[band[i]][c]++;
                placed[i] = true;
            }
        }

        int maxCol = 0;
        boolean[] present = new boolean[6];
        int[] rows = new int[6];
        for (int i = 0; i < n; i++)
        {
            present[band[i]] = true;
            rows[band[i]] = Math.max(rows[band[i]], row[i] + 1);
            maxCol = Math.max(maxCol, col[i]);
        }
        int y = 0;
        for (int b = 0; b < 6; b++)
        {
            if (!present[b]) continue;
            bandPresent[b] = true;
            bandTop[b] = y;
            bandHeight[b] = Math.max(1, rows[b]) * ROWP + 2 * BAND_PAD;
            y += bandHeight[b];
        }
        for (int i = 0; i < n; i++)
        {
            nodeX[i] = LABEL_W + col[i] * COL + 6;
            nodeY[i] = bandTop[band[i]] + BAND_PAD + row[i] * ROWP;
        }
        canvasW = LABEL_W + (maxCol + 1) * COL + 12;
        canvasH = y;
    }

    // ----- layout -----

    private int bodyX() { return left + PAD; }
    private int bodyY() { return top + 22; }
    private int bodyW() { return W - 2 * PAD; }
    private int detailY() { return top + H - PAD - DETAIL_H; }
    private int bodyBottom() { return detailY() - PAD; }
    private int bodyH() { return bodyBottom() - bodyY(); }

    private int gridCols() { return Math.max(1, (bodyW() - 4) / CELL_P); }
    private int gridVisibleRows() { return Math.max(1, (bodyH() - 4) / CELL_P); }
    private int gridRows() { return (entries.size() + gridCols() - 1) / gridCols(); }

    private void clampPan()
    {
        panX = Mth.clamp(panX, 0, Math.max(0, canvasW - bodyW()));
        panY = Mth.clamp(panY, 0, Math.max(0, canvasH - bodyH()));
    }

    private boolean inBody(double mx, double my)
    {
        return mx >= bodyX() && mx < bodyX() + bodyW() && my >= bodyY() && my < bodyBottom();
    }

    /** technology node under the point, or -1 */
    private int nodeAt(double mx, double my)
    {
        if (!inBody(mx, my) || mx < bodyX() + LABEL_W) return -1;
        for (int i = techs.size() - 1; i >= 0 && i < nodeX.length; i--)
        {
            int x = bodyX() - panX + nodeX[i], y = bodyY() - panY + nodeY[i];
            if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) return i;
        }
        return -1;
    }

    /** grid cell (entry index) under the point, or -1 */
    private int cellAt(double mx, double my)
    {
        if (!inBody(mx, my)) return -1;
        int lx = (int) mx - bodyX() - 2, ly = (int) my - bodyY() - 2;
        if (lx < 0 || ly < 0) return -1;
        int c = lx / CELL_P, r = ly / CELL_P;
        if (lx % CELL_P >= CELL || ly % CELL_P >= CELL || c >= gridCols()) return -1;
        int idx = (gridScroll + r) * gridCols() + c;
        return idx >= 0 && idx < entries.size() ? idx : -1;
    }

    // ----- render -----

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick)
    {
        super.render(g, mx, my, partialTick);
        int hover = tab == Tab.TECHNOLOGIES ? nodeAt(mx, my) : cellAt(mx, my);
        if (hover >= 0 && hover < entries.size()) g.renderTooltip(font, entries.get(hover).name(), mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick)
    {
        super.renderBackground(g, mx, my, partialTick);
        g.fill(left, top, left + W, top + H, UiTheme.PANEL);
        g.renderOutline(left, top, W, H, UiTheme.BORDER);
        g.drawString(font, title, left + PAD + 2, top + 7, WHITE, true);
        g.drawString(font, progressDone + " / " + progressTotal, left + PAD + 2 + font.width(title) + 8, top + 7, GRAY, false);

        int bx = bodyX(), by = bodyY(), bw = bodyW(), bh = bodyH();
        g.fill(bx, by, bx + bw, by + bh, UiTheme.SHADE);
        if (entries.isEmpty())
        {
            g.drawString(font, Component.translatable("screen.abyssia.database.empty"), bx + 4, by + 4, GRAY, false);
        }
        else if (tab == Tab.TECHNOLOGIES) renderDiagram(g, bx, by, bw, bh);
        else renderGrid(g, bx, by, bw, bh);

        renderDetail(g);
    }

    private void renderDiagram(GuiGraphics g, int bx, int by, int bw, int bh)
    {
        clampPan();
        int n = Math.min(techs.size(), nodeX.length);
        boolean[] unlocked = new boolean[n];
        for (int i = 0; i < n; i++) unlocked[i] = ResearchView.isUnlocked(techs.get(i).id());

        g.enableScissor(bx, by, bx + bw, by + bh);
        // band stripes
        for (int b = 0; b < 6; b++)
        {
            if (!bandPresent[b]) continue;
            int y0 = by - panY + bandTop[b], y1 = y0 + bandHeight[b];
            if ((b & 1) == 1) g.fill(bx, y0, bx + bw, y1, 0x14FFFFFF);
        }
        // lines and nodes are clipped right of the label column, so a horizontal pan never draws icons (z 150) over the labels
        g.enableScissor(bx + LABEL_W, by, bx + bw, by + bh);
        // prerequisite lines (the selected node's lines last, in white)
        for (int pass = 0; pass < 2; pass++)
        {
            for (int t = 0; t < n; t++)
            {
                for (int p : prereq[t])
                {
                    boolean sel = p == selected || t == selected;
                    if (sel != (pass == 1)) continue;
                    int color = sel ? WHITE : unlocked[p] ? DARK_GREEN : LOCKED_LINE;
                    int x1 = bx - panX + nodeX[p] + NODE, y1 = by - panY + nodeY[p] + NODE / 2;
                    int xm = bx - panX + nodeX[t] - 6, y2 = by - panY + nodeY[t] + NODE / 2;
                    int x2 = bx - panX + nodeX[t];
                    g.fill(Math.min(x1, xm), y1, Math.max(x1, xm) + 1, y1 + 1, color);
                    g.fill(xm, Math.min(y1, y2), xm + 1, Math.max(y1, y2) + 1, color);
                    g.fill(Math.min(xm, x2), y2, Math.max(xm, x2), y2 + 1, color);
                }
            }
        }
        // nodes
        for (int i = 0; i < n; i++)
        {
            int x = bx - panX + nodeX[i], y = by - panY + nodeY[i];
            if (x + NODE < bx || x > bx + bw || y + NODE < by || y > by + bh) continue;
            boolean ready = !unlocked[i];
            if (ready) for (int p : prereq[i]) if (!unlocked[p]) { ready = false; break; }
            int color = unlocked[i] ? GREEN : ready ? YELLOW : GRAY;
            g.fill(x, y, x + NODE, y + NODE, NODE_BG);
            g.renderOutline(x, y, NODE, NODE, color);
            drawIcon(g, entries.get(i), x + 1, y + 1, color);
            if (!unlocked[i] && !ready)
            {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, 0xA0000000);
                g.pose().popPose();
            }
            if (i == selected) g.renderOutline(x - 1, y - 1, NODE + 2, NODE + 2, WHITE);
        }
        g.disableScissor();
        // sticky band labels
        for (int b = 0; b < 6; b++)
        {
            if (!bandPresent[b]) continue;
            int y0 = by - panY + bandTop[b], y1 = y0 + bandHeight[b];
            g.fill(bx, y0, bx + LABEL_W, y1, UiTheme.SHADE);
            if ((b & 1) == 1) g.fill(bx, y0, bx + LABEL_W, y1, 0x14FFFFFF);
            g.fill(bx + LABEL_W - 1, y0, bx + LABEL_W, y1, UiTheme.BORDER);
            g.drawString(font, bandName(b), bx + 3, y0 + BAND_PAD, WHITE, false);
            g.drawString(font, bandBottom(b), bx + 3, y0 + BAND_PAD + 10, GRAY, false);
        }
        g.disableScissor();

        // position bars
        int maxX = canvasW - bw, maxY = canvasH - bh;
        if (maxX > 0)
        {
            g.fill(bx, by + bh - 2, bx + bw, by + bh, UiTheme.SHADE);
            int len = Math.max(8, bw * bw / canvasW);
            int pos = bx + (bw - len) * panX / maxX;
            g.fill(pos, by + bh - 2, pos + len, by + bh, UiTheme.TEXT_MUTED);
        }
        if (maxY > 0)
        {
            g.fill(bx + bw - 2, by, bx + bw, by + bh, UiTheme.SHADE);
            int len = Math.max(8, bh * bh / canvasH);
            int pos = by + (bh - len) * panY / maxY;
            g.fill(bx + bw - 2, pos, bx + bw, pos + len, UiTheme.TEXT_MUTED);
        }
    }

    /** item icon (16x16 at x,y), or the first letter of the name when there is none */
    private void drawIcon(GuiGraphics g, Entry e, int x, int y, int color)
    {
        if (!e.icon().isEmpty()) g.renderItem(e.icon(), x, y);
        else
        {
            String s = e.label().isEmpty() ? "?" : e.label().substring(0, e.label().offsetByCodePoints(0, 1));
            g.drawString(font, s, x + 8 - font.width(s) / 2, y + 4, color, false);
        }
    }

    private void renderGrid(GuiGraphics g, int bx, int by, int bw, int bh)
    {
        int cols = gridCols(), visRows = gridVisibleRows(), total = gridRows();
        gridScroll = Mth.clamp(gridScroll, 0, Math.max(0, total - visRows));
        g.enableScissor(bx, by, bx + bw, by + bh);
        for (int r = 0; r < visRows + 1; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                int idx = (gridScroll + r) * cols + c;
                if (idx >= entries.size()) break;
                int x = bx + 2 + c * CELL_P, y = by + 2 + r * CELL_P;
                if (y >= by + bh) break;
                Entry e = entries.get(idx);
                boolean unknown = e.color() == GRAY;
                int border = e.color() == YELLOW ? YELLOW : DARK_GREEN;
                if (unknown)
                {
                    g.fill(x, y, x + CELL, y + CELL, CELL_BG_UNKNOWN);
                    g.renderOutline(x, y, CELL, CELL, LOCKED_LINE);
                    g.drawString(font, "?", x + CELL / 2 - font.width("?") / 2, y + 6, GRAY, false);
                }
                else
                {
                    g.fill(x, y, x + CELL, y + CELL, NODE_BG);
                    g.renderOutline(x, y, CELL, CELL, border);
                    drawIcon(g, e, x + 2, y + 2, e.color());
                }
                if (idx == selected) g.renderOutline(x - 1, y - 1, CELL + 2, CELL + 2, WHITE);
            }
        }
        g.disableScissor();
        if (total > visRows)
        {
            int barH = Math.max(8, bh * visRows / total);
            int barY = by + (bh - barH) * gridScroll / Math.max(1, total - visRows);
            g.fill(bx + bw - 4, by, bx + bw, by + bh, UiTheme.SHADE);
            g.fill(bx + bw - 4, barY, bx + bw, barY + barH, UiTheme.TEXT_MUTED);
        }
    }

    private void renderDetail(GuiGraphics g)
    {
        int dx = bodyX(), dw = bodyW(), dt = detailY(), db = dt + DETAIL_H;
        g.fill(dx, dt, dx + dw, db, UiTheme.SHADE);
        if (selected >= 0 && selected < entries.size())
        {
            Entry e = entries.get(selected);
            // the wrapped lines are rebuilt only when the selection / tab / panel width changes (not every frame)
            if (cachedLines == null || cachedFor != e || cachedWidth != dw)
            {
                List<FormattedCharSequence> built = new ArrayList<>();
                built.addAll(font.split(e.name(), dw - 8));
                cachedStatusLines = font.split(e.status(), dw - 8).size();
                built.addAll(font.split(e.status(), dw - 8));
                built.add(FormattedCharSequence.EMPTY);
                for (Component c : e.detail()) built.addAll(font.split(c, dw - 8));
                cachedLines = built;
                cachedFor = e;
                cachedWidth = dw;
            }
            List<FormattedCharSequence> lines = cachedLines;
            int maxScroll = Math.max(0, lines.size() - ((db - dt - 4) / 10 + 1));
            detailScroll = Mth.clamp(detailScroll, 0, maxScroll);
            g.enableScissor(dx, dt, dx + dw, db);
            for (int i = detailScroll; i < lines.size() && dt + 3 + (i - detailScroll) * 10 < db; i++)
            {
                int color = i == 0 ? WHITE : i < 1 + cachedStatusLines ? e.color() : UiTheme.TEXT_PALE;
                g.drawString(font, lines.get(i), dx + 4, dt + 3 + (i - detailScroll) * 10, color, false);
            }
            g.disableScissor();
        }
    }

    // ----- input -----

    @Override
    public boolean mouseClicked(double mx, double my, int button)
    {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || entries.isEmpty()) return false;
        if (tab == Tab.TECHNOLOGIES)
        {
            if (inBody(mx, my))
            {
                pressing = true;
                panning = false;
                pressX = mx;
                pressY = my;
                pressPanX = panX;
                pressPanY = panY;
                return true;
            }
        }
        else
        {
            int idx = cellAt(mx, my);
            if (idx >= 0) { selected = idx; detailScroll = 0; return true; }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy)
    {
        if (pressing && button == 0)
        {
            double mvx = mx - pressX, mvy = my - pressY;
            if (!panning && Math.abs(mvx) + Math.abs(mvy) >= 3) panning = true;
            if (panning)
            {
                panX = pressPanX - (int) mvx;
                panY = pressPanY - (int) mvy;
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button)
    {
        if (pressing && button == 0)
        {
            pressing = false;
            if (!panning)
            {
                int idx = nodeAt(pressX, pressY);
                if (idx >= 0 && idx < entries.size()) { selected = idx; detailScroll = 0; }
            }
            panning = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY)
    {
        int dir = scrollY > 0 ? -1 : 1;
        if (my >= detailY() && my < top + H && mx >= left && mx < left + W) { if (scrollY != 0) detailScroll = Math.max(0, detailScroll + dir); }
        else if (inBody(mx, my))
        {
            if (tab == Tab.TECHNOLOGIES)
            {
                if (scrollX != 0) panX += (scrollX > 0 ? -1 : 1) * 20;
                else if (Screen.hasShiftDown()) panX += dir * 20;
                else panY += dir * 20;
                clampPan();
            }
            else if (scrollY != 0) gridScroll = Mth.clamp(gridScroll + dir, 0, Math.max(0, gridRows() - gridVisibleRows()));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (ResearchClient.DATABASE.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
