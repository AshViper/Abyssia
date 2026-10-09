package com.abyssia.research.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AB05 research database (display only, no menu): tabs Fauna / Resources / Technologies / Places on the left, a scrollable list,
 * and a detail panel on the right. Reads {@link ResearchView} only; unknown / broken ids just show their raw name.
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

    /** one list row; details are built when it is selected */
    private record Entry(Component name, String label, Component status, int color, List<Component> detail) {}

    private static final int W = 360, H = 224, TAB_W = 70, LIST_W = 122, ROW = 12, PAD = 4;
    private static final int GREEN = 0xFF58E6A0, GRAY = 0xFF8A949E, YELLOW = 0xFFFFD866, WHITE = 0xFFFFFFFF;

    private Tab tab = Tab.TECHNOLOGIES;
    private final Button[] tabButtons = new Button[Tab.values().length];
    private List<Entry> entries = List.of();
    private int selected, listScroll, detailScroll;
    private boolean draggingBar;
    private List<FormattedCharSequence> cachedLines;
    private Entry cachedFor;
    private int cachedWidth, cachedStatusLines;
    private int left, top;

    public DatabaseScreen()
    {
        super(Component.translatable("screen.abyssia.database.title"));
    }

    @Override
    protected void init()
    {
        left = (width - W) / 2;
        top = (height - H) / 2;
        for (Tab t : Tab.values())
        {
            int i = t.ordinal();
            tabButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.abyssia.database.tab." + t.key), b -> select(t))
                    .bounds(left + PAD, top + 22 + i * 22, TAB_W, 20).build());
        }
        select(tab);
    }

    private void select(Tab t)
    {
        tab = t;
        for (Tab x : Tab.values()) if (tabButtons[x.ordinal()] != null) tabButtons[x.ordinal()].active = x != t;
        entries = build(t);
        selected = 0;
        listScroll = 0;
        detailScroll = 0;
    }

    // ----- data -----

    private List<Entry> build(Tab t)
    {
        List<Entry> out = new ArrayList<>();
        try
        {
            if (t == Tab.TECHNOLOGIES)
            {
                for (ResearchView.Tech tech : ResearchView.technologies()) out.add(techEntry(tech));
            }
            else
            {
                for (ResearchView.Target target : ResearchView.targets())
                    if (Tab.ofCategory(target.category()) == t) out.add(targetEntry(target));
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
        return out;
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
        return new Entry(name, name.getString(), status, color, detail);
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
        return new Entry(tech.title(), tech.title().getString(), status, unlocked ? GREEN : GRAY, detail);
    }

    // ----- layout -----

    private int listX() { return left + PAD + TAB_W + PAD; }
    private int listTop() { return top + 22; }
    private int listBottom() { return top + H - PAD; }
    private int detailX() { return listX() + LIST_W + PAD; }
    private int visibleRows() { return (listBottom() - listTop()) / ROW; }

    // ----- render -----

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick)
    {
        renderBackground(g);
        drawPanels(g);
        super.render(g, mx, my, partialTick);
    }

    private void drawPanels(GuiGraphics g)
    {
        g.fill(left, top, left + W, top + H, 0xE0101418);
        g.renderOutline(left, top, W, H, 0xFF3A4450);
        g.drawString(font, title, left + PAD + 2, top + 7, WHITE, true);

        // list
        int lx = listX(), lt = listTop(), lw = LIST_W;
        g.fill(lx, lt, lx + lw, listBottom(), 0x80000000);
        if (entries.isEmpty())
        {
            g.drawString(font, Component.translatable("screen.abyssia.database.empty"), lx + 4, lt + 4, GRAY, false);
        }
        int rows = visibleRows();
        for (int i = 0; i < rows && listScroll + i < entries.size(); i++)
        {
            int idx = listScroll + i, y = lt + i * ROW;
            Entry e = entries.get(idx);
            if (idx == selected) g.fill(lx, y, lx + lw, y + ROW, 0x80406080);
            // status marker (green = scanned / unlocked, yellow = fragments, gray = unknown / locked), then the name
            g.fill(lx + 3, y + 3, lx + 7, y + 7, e.color());
            g.drawString(font, font.plainSubstrByWidth(e.label(), lw - 18), lx + 10, y + 2, e.color(), false);
        }
        if (entries.size() > rows)
        {
            int barH = Math.max(8, (listBottom() - lt) * rows / entries.size());
            int barY = lt + (listBottom() - lt - barH) * listScroll / Math.max(1, entries.size() - rows);
            g.fill(lx + lw - 4, lt, lx + lw, listBottom(), 0x40FFFFFF);
            g.fill(lx + lw - 4, barY, lx + lw, barY + barH, draggingBar ? 0xFFD0D6DC : 0xFF8A949E);
        }

        // detail
        int dx = detailX(), dw = left + W - PAD - dx;
        g.fill(dx, lt, dx + dw, listBottom(), 0x80000000);
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
            int maxScroll = Math.max(0, lines.size() - (listBottom() - lt - 4) / 10);
            detailScroll = Mth.clamp(detailScroll, 0, maxScroll);
            g.enableScissor(dx, lt, dx + dw, listBottom());
            for (int i = detailScroll; i < lines.size() && lt + 3 + (i - detailScroll) * 10 < listBottom(); i++)
            {
                int color = i == 0 ? WHITE : i < 1 + cachedStatusLines ? e.color() : 0xFFD0D6DC;
                g.drawString(font, lines.get(i), dx + 4, lt + 3 + (i - detailScroll) * 10, color, false);
            }
            g.disableScissor();
        }
    }

    // ----- input -----

    @Override
    public boolean mouseClicked(double mx, double my, int button)
    {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && entries.size() > visibleRows() && mx >= listX() + LIST_W - 6 && mx < listX() + LIST_W
                && my >= listTop() && my < listBottom())
        {
            draggingBar = true;
            dragScrollTo(my);
            return true;
        }
        if (button == 0 && mx >= listX() && mx < listX() + LIST_W && my >= listTop() && my < listBottom())
        {
            int idx = listScroll + (int) ((my - listTop()) / ROW);
            if (idx >= 0 && idx < entries.size()) { selected = idx; detailScroll = 0; return true; }
        }
        return false;
    }

    private void dragScrollTo(double my)
    {
        int range = Math.max(1, entries.size() - visibleRows());
        double f = (my - listTop()) / Math.max(1, listBottom() - listTop());
        listScroll = Mth.clamp((int) Math.round(f * range), 0, range);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy)
    {
        if (draggingBar) { dragScrollTo(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button)
    {
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta)
    {
        int dir = delta > 0 ? -1 : 1;
        if (mx >= detailX()) detailScroll = Math.max(0, detailScroll + dir);
        else listScroll = Mth.clamp(listScroll + dir * 3, 0, Math.max(0, entries.size() - visibleRows()));
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
