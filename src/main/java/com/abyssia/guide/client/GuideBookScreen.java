package com.abyssia.guide.client;

import com.abyssia.Abyssia;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * GB01 guide book screen. The book art is 256x180 "book pixels" (bp). Layout is screen independent: it always happens in
 * one virtual canvas = the book at K0 = 1.75 (448x315 units, text at scale 1 inside rects that are the bp rects times
 * K0), so pagination is identical on every screen. The whole canvas (art, text, items, buttons) is then drawn under one
 * pose.scale(s), s = min(width*0.92/448, height*0.88/315) clamped to 0.5..2 (snapped to 1 when only slightly above).
 * Pages are laid out into sheets (text that does not fit continues on the next sheet); there is no scrolling. The artwork is plain PNGs
 * (textures/gui/guide/book_bg.png, buttons.png, icons/&lt;chapter&gt;.png, pages/*.png).
 * TODO: JEI "show recipes" button on item pages (JEI is optional; needs a runtime handle from AbyssiaJeiPlugin).
 */
public class GuideBookScreen extends Screen
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int BOOK_W = 256, BOOK_H = 180;
    private static final int LEFT_X = 32, RIGHT_X = 140, PAGE_Y = 13;
    private static final int LH = 9;
    private static final int BTN_W = 20, BTN_H = 14, BTN_Y = 158;
    private static final int BTN_TOC_X = 26, BTN_PREV_X = 78, BTN_NEXT_X = 158, BTN_CLOSE_X = 210;

    private static final int COL_TEXT = 0xFF2B2A33, COL_TITLE = 0xFF0E5466, COL_DIM = 0xFF6B6558, COL_RULE = 0xFF4A8A94;
    /** Light colour for things drawn on the navy footer band. */
    private static final int COL_NAV = 0xFF8FD8F0;
    /** Subtle darker-parchment box behind slots. */
    private static final int COL_SLOT = 0x40705A3A;

    private static final ResourceLocation BG = tex("gui/guide/book_bg.png");
    private static final ResourceLocation BUTTONS = tex("gui/guide/buttons.png");
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
    private static final Map<ResourceLocation, int[]> DIMS = new HashMap<>();
    /** Images already reported missing (once per game session, not per frame / language). */
    private static final Set<String> MISSING_LOGGED = new HashSet<>();

    private GuideBookData data;
    private final List<Sheet> sheets = new ArrayList<>();
    private final Map<String, Integer> chapterStart = new HashMap<>();
    private int spread;
    /** Layout scale of the virtual canvas (constant), screen scale of the canvas, and its top-left corner on screen. */
    private static final float K0 = 1.75f;
    private float k = K0;
    private float s = 1f;
    private float originX, originY;
    /** Index the next laid-out sheet will get (even = left page). */
    private int sheetBase;
    private ItemStack tipStack = ItemStack.EMPTY;
    private int mouseRealX, mouseRealY;

    // Page geometry in GUI units (book-pixel rect * k). Measured on book_bg.png (86x106 bp pages): the text column is one
    // rectangle clear of all four corner ornaments; titles are indented past the top corners.
    private int colLo, colHi, colW, lim, bodyMin, titleY, titleLeftLo, titleRightHi;
    private float recipeScale;
    private int recipeH;

    public GuideBookScreen()
    {
        super(Component.translatable("item.abyssia.abyss_guide_book"));
    }

    // ------------------------------------------------------------------ setup

    /** Book-pixel length to GUI units. */
    private int u(float bp)
    {
        return Math.round(bp * k);
    }

    private void computeGeometry()
    {
        k = K0;
        float fit = Math.min(width * 0.92f / (BOOK_W * K0), height * 0.88f / (BOOK_H * K0));
        s = Math.max(0.5f, Math.min(2f, fit));
        if (s >= 1f && s < 1.1f) s = 1f;
        originX = (width - BOOK_W * K0 * s) / 2f;
        originY = (height - BOOK_H * K0 * s) / 2f;
        // one rectangle per page, x 16..68 bp, clear of all four corner coral (re-measured on book_bg.png: top-left
        // reaches x16 at y8-16 / x11 at y16-20 / x8 down to y32; bottom-left x13 until y100; top-right x>=71 for y>=12;
        // bottom-right x>=69 until y100); body text may run down to y98.
        colLo = u(16);
        colHi = u(68);
        colW = colHi - colLo;
        lim = u(98);
        bodyMin = u(20);
        titleY = u(12);
        titleLeftLo = colLo;
        titleRightHi = colHi;
        recipeScale = colW / 84f;
        recipeH = Math.round(54 * recipeScale);
    }

    @Override
    protected void init()
    {
        DIMS.clear();
        data = GuideBookDataLoader.get();
        computeGeometry();
        buildSheets();
        spread = Math.min(spread, maxSpread());
    }

    private void buildSheets()
    {
        sheets.clear();
        chapterStart.clear();
        sheetBase = 0;
        sheets.addAll(layoutToc());
        for (GuideBookData.Chapter ch : data.chapters)
        {
            int first = sheets.size();
            for (String id : ch.pageIds())
            {
                GuideBookPage page = data.pages.get(id);
                if (page == null)
                {
                    LOGGER.warn("Guide book: chapter {} lists unknown page {}", ch.id(), id);
                    continue;
                }
                try
                {
                    sheetBase = sheets.size();
                    sheets.addAll(layout(page));
                }
                catch (RuntimeException ex)
                {
                    LOGGER.warn("Guide book: cannot lay out page {}: {}", id, ex.toString());
                }
            }
            if (sheets.size() > first) chapterStart.put(ch.id(), first);
        }
        if (sheets.size() % 2 == 1) sheets.add(new PageSheet());
    }

    private int maxSpread()
    {
        return Math.max(0, (sheets.size() - 1) / 2);
    }

    private void goTo(int newSpread)
    {
        spread = Math.max(0, Math.min(maxSpread(), newSpread));
    }

    // ------------------------------------------------------------------ layout

    private List<Sheet> layout(GuideBookPage page)
    {
        if (page instanceof GuideBookPage.Text t) return layoutText(t);
        if (page instanceof GuideBookPage.Item i) return layoutItem(i);
        if (page instanceof GuideBookPage.Image im) return layoutImage(im);
        if (page instanceof GuideBookPage.Recipe r) return layoutRecipe(r);
        return List.of();
    }

    /**
     * Title lines for a width: at most 2. When the title is too long the base is shortened (with an ellipsis) and the
     * suffix (" (2)") always stays.
     */
    private List<String> titleLines(String base, String suffix, int width)
    {
        List<String> ls = wrap(base + suffix, width);
        if (ls.size() <= 2) return ls;
        String b = base;
        while (b.length() > 1)
        {
            b = b.substring(0, b.length() - 1).stripTrailing();
            List<String> t = wrap(b + "…" + suffix, width);
            if (t.size() <= 2) return t;
        }
        return ls.subList(0, 2);
    }

    private PageSheet newSheet(List<Sheet> out, String base, String suffix)
    {
        PageSheet s = new PageSheet();
        s.left = (sheetBase + out.size()) % 2 == 0;
        s.titleLo = titleLeftLo;
        s.titleHi = titleRightHi;
        s.titleLines = titleLines(base, suffix, s.titleHi - s.titleLo);
        s.ruleY = titleY + s.titleLines.size() * LH + 1;
        s.top = Math.max(bodyMin, s.ruleY + 5);
        return s;
    }

    private List<Sheet> layoutToc()
    {
        List<Sheet> out = new ArrayList<>();
        // rows are short and the TOC starts on a left page (no coral at its gutter side), so it may use x 10..94 bp
        int tocLo = u(10), tocHi = u(94), tocLim = u(100), iconSz = 10, romanW = 3;
        for (int i = 0; i < ROMAN.length; i++) romanW = Math.max(romanW, font.width(ROMAN[i]) + 3);
        int nameX = tocLo + iconSz + 3 + romanW;
        TocSheet cur = null;
        int y = 0;
        for (int i = 0; i < data.chapters.size() && i < 10; i++)
        {
            GuideBookData.Chapter ch = data.chapters.get(i);
            List<String> lines = wrap(data.tr(ch.titleKey()), tocHi - nameX);
            int h = Math.max(iconSz + 2, lines.size() * 8 + 2);
            if (cur == null || y + h > tocLim)
            {
                cur = new TocSheet();
                PageSheet tmp = newSheet(out, data.tr("guide.abyssia.ui.contents"), out.isEmpty() ? "" : " (" + (out.size() + 1) + ")");
                cur.copyHeader(tmp);
                cur.iconSz = iconSz;
                cur.nameX = nameX;
                cur.romanX = tocLo + iconSz + 3;
                cur.tocLo = tocLo;
                cur.tocHi = tocHi;
                out.add(cur);
                y = cur.top;
            }
            cur.rows.add(new TocRow(i, lines, y, h));
            y += h;
        }
        return out;
    }

    private List<Sheet> layoutText(GuideBookPage.Text t)
    {
        List<Sheet> out = new ArrayList<>();
        String title = data.tr(t.title());
        String rest = data.tr(t.text());
        String pos = t.imagePosition();
        ImageRef img = resolveImage(t.image(), t.imageW(), t.imageH(), colW, u(40), false);
        boolean first = true;
        for (int n = 1; n <= 24; n++)
        {
            PageSheet sheet = newSheet(out, title, n == 1 ? "" : " (" + n + ")");
            int[] y = {sheet.top};
            if (first && img != null && !"bottom".equals(pos))
            {
                sheet.images.add(img.at(colLo + (colW - img.w) / 2, y[0]));
                y[0] += img.h + 4;
            }
            rest = flow(sheet.lines, rest, COL_TEXT, y, lim);
            if (rest == null && first && img != null && "bottom".equals(pos))
            {
                if (y[0] + 2 + img.h > lim)
                {
                    out.add(sheet);
                    sheet = newSheet(out, title, " (2)");
                    y[0] = sheet.top;
                }
                sheet.images.add(img.at(colLo + (colW - img.w) / 2, y[0] + 2));
            }
            out.add(sheet);
            first = false;
            if (rest == null) break;
        }
        return out;
    }

    private List<Sheet> layoutItem(GuideBookPage.Item p)
    {
        ResourceLocation id = ResourceLocation.tryParse(p.item());
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id))
        {
            LOGGER.warn("Guide book: page {} uses unregistered item {}", p.id(), p.item());
            return List.of();
        }
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
        String title = p.title() != null ? data.tr(p.title()) : stack.getHoverName().getString();
        ResourceLocation recipe = p.recipe() == null ? null : ResourceLocation.tryParse(p.recipe());
        // description, then "Obtaining" + its text; flows over as many sheets as needed
        List<String> texts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        texts.add(data.tr(p.description()));
        colors.add(COL_TEXT);
        if (!p.obtaining().isEmpty())
        {
            texts.add(data.tr("guide.abyssia.ui.obtaining"));
            colors.add(COL_TITLE);
            texts.add(data.tr(p.obtaining()));
            colors.add(COL_TEXT);
        }
        List<Sheet> out = new ArrayList<>();
        int si = 0, lastY = 0;
        PageSheet last = null;
        for (int n = 1; n <= 24 && si < texts.size(); n++)
        {
            PageSheet s = newSheet(out, title, n == 1 ? "" : " (" + n + ")");
            int[] y = {s.top};
            if (n == 1)
            {
                // the icon gets its own centred row; text runs at full column width below it
                int box = Math.round(16 * k) + 4;
                s.icon = stack;
                s.iconBox = box;
                s.iconX = colLo + (colW - box) / 2;
                y[0] += box + 4;
            }
            while (si < texts.size())
            {
                // do not leave a lone "Obtaining" heading at the bottom of a sheet
                if (colors.get(si) == COL_TITLE && y[0] + 2 * LH > lim) break;
                String rest = flow(s.lines, texts.get(si), colors.get(si), y, lim);
                if (rest != null)
                {
                    texts.set(si, rest);
                    break;
                }
                si++;
                y[0] += 3;
            }
            out.add(s);
            last = s;
            lastY = y[0];
        }
        if (si < texts.size()) LOGGER.warn("Guide book: item page {} is too long, text clipped", p.id());
        if (recipe != null) attachRecipe(out, last, lastY, recipe, title);
        return out;
    }

    /** Puts the recipe under the text of the last sheet when it fits (anchored to the bottom), else on a new sheet. */
    private void attachRecipe(List<Sheet> out, @Nullable PageSheet last, int textEnd, ResourceLocation recipe, String title)
    {
        int bottomY = lim - recipeH;
        if (last != null && last.recipe == null && textEnd + 3 <= bottomY)
        {
            last.recipe = recipe;
            last.recipeY = bottomY;
            return;
        }
        PageSheet r = newSheet(out, title, " (" + (out.size() + 1) + ")");
        r.recipe = recipe;
        r.recipeY = r.top;
        out.add(r);
    }

    private List<Sheet> layoutImage(GuideBookPage.Image p)
    {
        List<Sheet> out = new ArrayList<>();
        PageSheet s = newSheet(out, data.tr(p.title()), "");
        ImageRef img = resolveImage(p.image(), 0, 0, colW, u(50), true);
        int y = s.top;
        s.images.add(img.at(colLo + (colW - img.w) / 2, y));
        y += img.h + 4;
        if (p.caption() != null) flow(s.lines, data.tr(p.caption()), COL_DIM, new int[]{y}, lim);
        out.add(s);
        return out;
    }

    /** Description and recipe grid share a sheet when they fit; otherwise the grid follows on its own sheet. */
    private List<Sheet> layoutRecipe(GuideBookPage.Recipe p)
    {
        List<Sheet> out = new ArrayList<>();
        String title = data.tr(p.title());
        ResourceLocation recipe = ResourceLocation.tryParse(p.recipe());
        if (recipe == null) throw new IllegalArgumentException("bad recipe id " + p.recipe());
        String rest = p.description() == null ? "" : data.tr(p.description());
        PageSheet last = null;
        int lastY = 0;
        for (int n = 1; n <= 24; n++)
        {
            PageSheet s = newSheet(out, title, n == 1 ? "" : " (" + n + ")");
            int[] y = {s.top};
            rest = flow(s.lines, rest, COL_TEXT, y, lim);
            out.add(s);
            last = s;
            lastY = y[0];
            if (rest == null) break;
        }
        attachRecipe(out, last, lastY, recipe, title);
        return out;
    }

    private record Ent(int lineIndex, int para, int start, int end, int y) {}

    /**
     * Wraps text into lines in the text column starting at y[0]; stops when the next line would pass limitY and returns
     * the unconsumed rest (null when everything fit). When the text continues, the sheet ends at a sentence end if one
     * falls within its last 4 lines of this text.
     */
    private String flow(List<Line> out, String text, int color, int[] y, int limitY)
    {
        String[] paragraphs = text.split("\n", -1);
        List<Ent> ents = new ArrayList<>();
        for (int pi = 0; pi < paragraphs.length; pi++)
        {
            String para = paragraphs[pi].strip();
            if (para.isEmpty())
            {
                y[0] += LH / 2;
                continue;
            }
            int pos = 0;
            while (pos < para.length())
            {
                if (y[0] + LH > limitY)
                {
                    // try to end the sheet at a sentence end within the last 4 lines
                    for (int e = ents.size() - 1; e >= Math.max(0, ents.size() - 4); e--)
                    {
                        Ent en = ents.get(e);
                        String pt = paragraphs[en.para()].strip();
                        int cut = -1;
                        for (int j = en.end() - 1; j >= en.start(); j--)
                        {
                            char c = pt.charAt(j);
                            boolean cjk = c == '。' || c == '！' || c == '？';
                            boolean ascii = c == '.' || c == '!' || c == '?';
                            if (cjk || (ascii && (j + 1 >= pt.length() || pt.charAt(j + 1) == ' ')))
                            {
                                cut = j + 1;
                                break;
                            }
                        }
                        if (cut < 0) continue;
                        while (out.size() > en.lineIndex() + 1) out.remove(out.size() - 1);
                        out.set(en.lineIndex(), new Line(pt.substring(en.start(), cut).stripTrailing(), colLo, en.y(), color));
                        y[0] = en.y() + LH;
                        StringBuilder sb = new StringBuilder(pt.substring(cut).stripLeading());
                        for (int k2 = en.para() + 1; k2 < paragraphs.length; k2++)
                        {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(paragraphs[k2]);
                        }
                        return sb.toString().isBlank() ? null : sb.toString();
                    }
                    StringBuilder sb = new StringBuilder(para.substring(pos));
                    for (int k2 = pi + 1; k2 < paragraphs.length; k2++) sb.append('\n').append(paragraphs[k2]);
                    return sb.toString();
                }
                String rem = para.substring(pos);
                Brk b = fit(rem, colW);
                ents.add(new Ent(out.size(), pi, pos, pos + b.take(), y[0]));
                out.add(new Line(rem.substring(0, b.take()).stripTrailing() + (b.hyphen() ? "-" : ""), colLo, y[0], color));
                pos += b.take();
                while (pos < para.length() && Character.isWhitespace(para.charAt(pos))) pos++;
                y[0] += LH;
            }
        }
        return null;
    }

    /** Wraps a single paragraph into lines of at most the given width. */
    private List<String> wrap(String text, int width)
    {
        List<String> out = new ArrayList<>();
        String rem = text.strip();
        while (!rem.isEmpty())
        {
            Brk b = fit(rem, width);
            out.add(rem.substring(0, b.take()).stripTrailing() + (b.hyphen() ? "-" : ""));
            rem = rem.substring(b.take()).stripLeading();
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    private record Brk(int take, boolean hyphen) {}

    /**
     * How many chars of s fit on one line of the given pixel width. Breaks only at allowed points: after a space, after
     * a hyphen inside a word (deep-/sea), and between CJK characters (never inside an ASCII run such as "Y=-64", never
     * before closing punctuation, and inside a katakana run only when nothing else is possible). A word longer than the
     * line is broken mid-word with a hyphen (last resort).
     */
    private Brk fit(String s, int width)
    {
        if (font.width(s) <= width) return new Brk(s.length(), false);
        for (int pass = 0; pass < 2; pass++)
        {
            int best = -1;
            for (int i = 1; i < s.length(); i++)
            {
                if (font.width(s.substring(0, i).stripTrailing()) > width) break;
                if (canBreak(s, i, pass == 1)) best = i;
            }
            if (best > 0) return new Brk(best, false);
        }
        int hw = font.width("-");
        int i = 1;
        while (i < s.length() - 1 && font.width(s.substring(0, i + 1)) + hw <= width) i++;
        boolean hyphen = s.charAt(i - 1) < 0x2E80 && s.charAt(i) < 0x2E80
            && Character.isLetterOrDigit(s.charAt(i - 1)) && Character.isLetterOrDigit(s.charAt(i));
        return new Brk(i, hyphen);
    }

    private static final String NO_LINE_START = "、。，．・：；？！ー）」』】〕〉》ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ々,.:;!?)]";
    private static final String NO_LINE_END = "（「『【〔〈《([";

    private static boolean katakana(char c)
    {
        return c >= 0x30A0 && c <= 0x30FF;
    }

    private static boolean canBreak(String s, int i, boolean lax)
    {
        char prev = s.charAt(i - 1), next = s.charAt(i);
        if (next == ' ') return false;
        if (prev == ' ') return true;
        if (prev == '-' && i >= 2 && Character.isLetter(s.charAt(i - 2)) && Character.isLetter(next)) return true;
        if (prev >= 0x2E80 || next >= 0x2E80)
        {
            if (!lax && katakana(prev) && katakana(next)) return false;
            return NO_LINE_START.indexOf(next) < 0 && NO_LINE_END.indexOf(prev) < 0;
        }
        return false;
    }

    // ------------------------------------------------------------------ images

    private record Line(String text, int x, int y, int color) {}

    private record ImageDraw(ResourceLocation loc, int x, int y, int w, int h, int texW, int texH, boolean missing) {}

    private record ImageRef(@Nullable ResourceLocation loc, int w, int h, int texW, int texH)
    {
        ImageDraw at(int x, int y)
        {
            return new ImageDraw(loc, x, y, w, h, texW, texH, loc == null);
        }
    }

    /** Resolves a page image; null when it is missing (a placeholder box for image pages when placeholder = true). */
    @Nullable
    private ImageRef resolveImage(@Nullable String path, int reqW, int reqH, int maxW, int maxH, boolean placeholder)
    {
        if (path != null)
        {
            ResourceLocation loc = imageLoc(path);
            int[] d = loc == null ? null : dims(loc);
            if (d != null)
            {
                int w = reqW > 0 ? reqW : d[0], h = reqH > 0 ? reqH : d[1];
                float f = Math.min((float) maxW / w, (float) maxH / h);
                return new ImageRef(loc, Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)), d[0], d[1]);
            }
            if (MISSING_LOGGED.add(path)) LOGGER.warn("Guide book: missing image {}", path);
        }
        return placeholder ? new ImageRef(null, maxW, Math.min(maxH, u(48)), 1, 1) : null;
    }

    @Nullable
    private static ResourceLocation imageLoc(String path)
    {
        if (path.indexOf(':') >= 0)
        {
            ResourceLocation rl = ResourceLocation.tryParse(path);
            if (rl == null) return null;
            return rl.getPath().startsWith("textures/") ? rl : ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), "textures/gui/" + rl.getPath());
        }
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, path.startsWith("textures/") ? path : "textures/gui/guide/" + path);
    }

    /** Native size of a texture, or null when it cannot be read. */
    @Nullable
    private static int[] dims(ResourceLocation loc)
    {
        if (DIMS.containsKey(loc)) return DIMS.get(loc);
        int[] d = null;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open();
             NativeImage img = NativeImage.read(in))
        {
            d = new int[]{img.getWidth(), img.getHeight()};
        }
        catch (Exception ex)
        {
            // missing or unreadable: caller falls back
        }
        DIMS.put(loc, d);
        return d;
    }

    private static ResourceLocation tex(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/" + path);
    }

    // ------------------------------------------------------------------ sheets

    /** One page of the book, in GUI units. Mouse coordinates are relative to the page's top-left corner. */
    private abstract class Sheet
    {
        abstract void render(GuiGraphics g, int mx, int my);

        boolean click(int mx, int my)
        {
            return false;
        }
    }

    private class PageSheet extends Sheet
    {
        boolean left;
        List<String> titleLines = List.of();
        int titleLo, titleHi, ruleY, top;
        int iconX, iconBox;
        final List<Line> lines = new ArrayList<>();
        final List<ImageDraw> images = new ArrayList<>();
        @Nullable ItemStack icon;
        @Nullable ResourceLocation recipe;
        int recipeY;

        void copyHeader(PageSheet o)
        {
            left = o.left;
            titleLines = o.titleLines;
            titleLo = o.titleLo;
            titleHi = o.titleHi;
            ruleY = o.ruleY;
            top = o.top;
        }

        @Override
        void render(GuiGraphics g, int mx, int my)
        {
            if (!titleLines.isEmpty())
            {
                for (int i = 0; i < titleLines.size(); i++) g.drawString(font, titleLines.get(i), titleLo, titleY + i * LH, COL_TITLE, false);
                g.fill(titleLo, ruleY, titleHi, ruleY + 1, COL_RULE);
            }
            for (ImageDraw im : images)
            {
                if (im.missing())
                {
                    g.renderOutline(im.x(), im.y(), im.w(), im.h(), COL_RULE);
                    g.drawCenteredString(font, data.tr("guide.abyssia.ui.image_missing"), im.x() + im.w() / 2, im.y() + im.h() / 2 - 4, COL_DIM);
                }
                else g.blit(im.loc(), im.x(), im.y(), im.w(), im.h(), 0f, 0f, im.texW(), im.texH(), im.texW(), im.texH());
            }
            if (icon != null)
            {
                g.fill(iconX, top, iconX + iconBox, top + iconBox, COL_SLOT);
                g.renderOutline(iconX, top, iconBox, iconBox, COL_RULE);
                g.pose().pushPose();
                g.pose().translate(iconX + 2, top + 2, 0);
                g.pose().scale(k, k, 1f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
                if (mx >= iconX && mx < iconX + iconBox && my >= top && my < top + iconBox) tipStack = icon;
            }
            for (Line l : lines) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
            if (recipe != null)
            {
                g.pose().pushPose();
                g.pose().translate(colLo, recipeY, 0);
                g.pose().scale(recipeScale, recipeScale, 1f);
                drawRecipe(g, recipe, (int) Math.floor((mx - colLo) / recipeScale), (int) Math.floor((my - recipeY) / recipeScale));
                g.pose().popPose();
            }
        }
    }

    private record TocRow(int chapter, List<String> lines, int y, int h) {}

    /** Contents sheet(s): icon, numeral and name at text scale, one aligned column; names wrap rather than shrink. */
    private class TocSheet extends PageSheet
    {
        final List<TocRow> rows = new ArrayList<>();
        int iconSz, nameX, romanX, tocLo, tocHi;

        @Override
        void render(GuiGraphics g, int mx, int my)
        {
            super.render(g, mx, my);
            for (TocRow r : rows)
            {
                GuideBookData.Chapter ch = data.chapters.get(r.chapter());
                boolean ok = chapterStart.containsKey(ch.id());
                boolean hover = ok && mx >= tocLo && mx < tocHi && my >= r.y() && my < r.y() + r.h();
                if (hover) g.fill(tocLo, r.y() - 1, tocHi, r.y() + r.h() - 1, 0x303FA0B0);
                ResourceLocation icon = ch.icon().isEmpty() ? null : imageLoc(ch.icon());
                if (icon != null && dims(icon) != null) g.blit(icon, tocLo, r.y(), iconSz, iconSz, 0f, 0f, 16, 16, 16, 16);
                int color = ok ? (hover ? COL_TITLE : COL_TEXT) : COL_DIM;
                g.drawString(font, r.chapter() < ROMAN.length ? ROMAN[r.chapter()] : "", romanX, r.y(), color, false);
                for (int i = 0; i < r.lines().size(); i++) g.drawString(font, r.lines().get(i), nameX, r.y() + i * 8, color, false);
            }
        }

        @Override
        boolean click(int mx, int my)
        {
            if (mx < tocLo || mx >= tocHi) return false;
            for (TocRow r : rows)
            {
                if (my < r.y() - 1 || my >= r.y() + r.h() - 1) continue;
                Integer start = chapterStart.get(data.chapters.get(r.chapter()).id());
                if (start == null) return false;
                goTo(start / 2);
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ recipes

    /** A 3x3 grid, an arrow and the result; shaped, shapeless and cooking recipes. 84x54 in the caller's scaled space. */
    private void drawRecipe(GuiGraphics g, ResourceLocation id, int mx, int my)
    {
        int x = 0, y = 0;
        ClientLevel level = Minecraft.getInstance().level;
        Optional<? extends Recipe<?>> found = level == null ? Optional.empty() : level.getRecipeManager().byKey(id);
        if (found.isEmpty())
        {
            g.drawString(font, data.tr("guide.abyssia.ui.recipe_missing"), x, y + 20, COL_DIM, false);
            return;
        }
        try
        {
            Recipe<?> r = found.get();
            List<Ingredient> ings = r.getIngredients();
            for (int i = 0; i < 9; i++) slot(g, x + (i % 3) * 18, y + (i / 3) * 18, ItemStack.EMPTY, mx, my);
            for (int i = 0; i < ings.size(); i++)
            {
                int cell;
                if (r instanceof ShapedRecipe sr) cell = (i / sr.getWidth()) * 3 + (i % sr.getWidth());
                else if (r instanceof AbstractCookingRecipe) cell = 4;
                else cell = i;
                if (cell >= 9) break;
                ItemStack[] options = ings.get(i).getItems();
                if (options.length == 0) continue;
                slot(g, x + (cell % 3) * 18, y + (cell / 3) * 18, options[(int) (Util.getMillis() / 1000 % options.length)], mx, my);
            }
            int ay = y + 27;
            g.fill(x + 56, ay - 1, x + 62, ay + 2, COL_TITLE);          // shaft
            for (int i = 0; i < 5; i++) g.fill(x + 62 + i, ay - 4 + i, x + 63 + i, ay + 5 - i, COL_TITLE);   // head
            slot(g, x + 66, y + 18, r.getResultItem(level.registryAccess()), mx, my);
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("Guide book: cannot draw recipe {}: {}", id, ex.toString());
            g.drawString(font, data.tr("guide.abyssia.ui.recipe_missing"), x, y + 20, COL_DIM, false);
        }
    }

    private void slot(GuiGraphics g, int x, int y, ItemStack stack, int mx, int my)
    {
        g.fill(x, y, x + 18, y + 18, COL_SLOT);
        g.renderOutline(x, y, 18, 18, COL_RULE);
        if (stack.isEmpty()) return;
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
        if (mx >= x && mx < x + 18 && my >= y && my < y + 18) tipStack = stack;
    }

    // ------------------------------------------------------------------ render / input

    /** Mouse position in book pixels (for the buttons). */
    private int logicalX(double mouse)
    {
        return (int) Math.floor((mouse - originX) / s / k);
    }

    private int logicalY(double mouse)
    {
        return (int) Math.floor((mouse - originY) / s / k);
    }

    private boolean over(int mx, int my, int bx)
    {
        return mx >= bx && mx < bx + BTN_W && my >= BTN_Y && my < BTN_Y + BTN_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(g);
        tipStack = ItemStack.EMPTY;
        mouseRealX = mouseX;
        mouseRealY = mouseY;
        int lx = logicalX(mouseX), ly = logicalY(mouseY);

        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(s, s, 1f);
        int cmx = (int) Math.floor((mouseX - originX) / s), cmy = (int) Math.floor((mouseY - originY) / s);
        // art at layout scale k inside the canvas
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        g.blit(BG, 0, 0, 0f, 0f, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
        button(g, BTN_TOC_X, 0, spread > 0, lx, ly);
        button(g, BTN_PREV_X, 1, spread > 0, lx, ly);
        button(g, BTN_NEXT_X, 2, spread < maxSpread(), lx, ly);
        button(g, BTN_CLOSE_X, 3, true, lx, ly);
        g.pose().popPose();

        // text at scale 1 inside the scaled page rects
        renderSheet(g, 2 * spread, LEFT_X, cmx, cmy);
        renderSheet(g, 2 * spread + 1, RIGHT_X, cmx, cmy);
        g.drawCenteredString(font, (spread + 1) + " / " + (maxSpread() + 1), u(BOOK_W / 2f), u(BTN_Y + BTN_H / 2f) - 4, COL_NAV);
        g.pose().popPose();

        Component hint = null;
        if (over(lx, ly, BTN_TOC_X) && spread > 0) hint = Component.literal(data.tr("guide.abyssia.ui.contents"));
        else if (over(lx, ly, BTN_PREV_X) && spread > 0) hint = Component.literal(data.tr("guide.abyssia.ui.prev"));
        else if (over(lx, ly, BTN_NEXT_X) && spread < maxSpread()) hint = Component.literal(data.tr("guide.abyssia.ui.next"));
        else if (over(lx, ly, BTN_CLOSE_X)) hint = Component.literal(data.tr("guide.abyssia.ui.close"));
        if (hint != null) g.renderTooltip(font, hint, mouseX, mouseY);
        else if (!tipStack.isEmpty()) g.renderTooltip(font, tipStack, mouseRealX, mouseRealY);
    }

    private void renderSheet(GuiGraphics g, int index, int pageXbp, int mouseX, int mouseY)
    {
        if (index >= sheets.size()) return;
        int px = u(pageXbp), py = u(PAGE_Y);
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        try
        {
            sheets.get(index).render(g, mouseX - px, mouseY - py);
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("Guide book: sheet {} failed to render: {}", index, ex.toString());
        }
        g.pose().popPose();
    }

    private void button(GuiGraphics g, int x, int row, boolean enabled, int lx, int ly)
    {
        int state = !enabled ? 2 : over(lx, ly, x) ? 1 : 0;
        g.blit(BUTTONS, x, BTN_Y, state * BTN_W, row * BTN_H, BTN_W, BTN_H, 60, 56);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            int lx = logicalX(mouseX), ly = logicalY(mouseY);
            if (over(lx, ly, BTN_CLOSE_X)) { onClose(); return true; }
            if (over(lx, ly, BTN_TOC_X) && spread > 0) { goTo(0); return true; }
            if (over(lx, ly, BTN_PREV_X) && spread > 0) { goTo(spread - 1); return true; }
            if (over(lx, ly, BTN_NEXT_X) && spread < maxSpread()) { goTo(spread + 1); return true; }
            int left = 2 * spread, right = left + 1;
            int mx = (int) Math.floor((mouseX - originX) / s), my = (int) Math.floor((mouseY - originY) / s) - u(PAGE_Y);
            if (left < sheets.size() && sheets.get(left).click(mx - u(LEFT_X), my)) return true;
            if (right < sheets.size() && sheets.get(right).click(mx - u(RIGHT_X), my)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods)
    {
        if (key == 263) { goTo(spread - 1); return true; }   // left arrow
        if (key == 262) { goTo(spread + 1); return true; }   // right arrow
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
