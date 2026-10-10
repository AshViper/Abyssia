#!/usr/bin/env python3
"""UI01 part 2: deep-sea replacements for vanilla GUI textures, shipped under assets/minecraft/textures/gui/.

  python tools/vanilla_ui.py --loader neoforge|forge --root <project dir> [--preview <png>]

neoforge = Minecraft 1.21.1 (sprites/ + .png.mcmeta), forge = 1.20.1 (widgets.png sheets).
Built from tools/ui_kit.py (located next to this file, so the script can be run against another tree via --root).
Every output keeps the vanilla pixel size.  No vanilla file is read at run time: geometry / alpha levels below are
hard-coded constants measured while writing the code; no Mojang pixels are copied.
Writes only <root>/src/main/resources/assets/minecraft/textures/gui/**.
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from PIL import Image, ImageDraw  # noqa: E402

from ui_kit import (ABYSS, NAVY_DEEP, NAVY, NAVY_MID, BLUE, BLUE_LIGHT, STEEL_BLUE, PALE_BLUE, STEEL_DARK,  # noqa: E402
                    STEEL, STEEL_LIGHT, HIGHLIGHT, CYAN_DEEP, CYAN, CYAN_GLOW, CYAN_PALE,
                    rect, bevel, button, to_disabled, tile)

CLEAR = (0, 0, 0, 0)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def new(w, h):
    return Image.new("RGBA", (w, h), CLEAR)


# ---------------------------------------------------------------- widgets shared by both versions
def track(w, h, focused):
    """200x20 slider track / text field: dark sunken bar, 2px frame (outer steel, inner abyss)."""
    img = new(w, h)
    outer = CYAN if focused else STEEL_DARK
    rect(img, 0, 0, w - 1, h - 1, outer)
    rect(img, 1, 1, w - 2, h - 2, ABYSS)
    rect(img, 2, 2, w - 3, h - 3, NAVY_DEEP if not focused else NAVY)
    rect(img, 2, 2, w - 3, 2, ABYSS)
    rect(img, 2, 2, 2, h - 3, ABYSS)
    # no corner is cut: keep the corner pixel same as the outline for a clean nine-slice
    return img


def text_field(w, h, focused):
    img = new(w, h)
    outer = CYAN if focused else STEEL_DARK
    rect(img, 0, 0, w - 1, h - 1, outer)
    rect(img, 1, 1, w - 2, h - 2, ABYSS)
    rect(img, 2, 2, w - 3, h - 3, ABYSS)
    return img


def handle(hover):
    """8x20 slider handle (borders l2 t2 r2 b3)."""
    img = new(8, 20)
    hi, lo, fill, rim = (HIGHLIGHT, CYAN_DEEP, CYAN, ABYSS) if hover else (STEEL_LIGHT, STEEL_DARK, STEEL_BLUE, ABYSS)
    rect(img, 0, 0, 7, 19, fill)
    bevel(img, 0, 0, 7, 19, hi, lo)
    # 1px outline outside the bevel is not available (8px wide): the rim is the bevel itself, plus dark outer corners
    for x, y in ((0, 0), (7, 0), (0, 19), (7, 19)):
        img.putpixel((x, y), rgba(rim))
    rect(img, 3, 3, 4, 16, fill)
    rect(img, 3, 4, 3, 15, hi if not hover else CYAN_PALE)
    return img


def checkbox_variants():
    """20x20 checkbox from the traced tick-box tile: off / off focused / on / on focused."""
    on = tile("ui01b_checkbox").copy()
    off = on.copy()
    inner = on.getpixel((6, 4))  # interior colour of the tile
    for y in range(4, 16):
        for x in range(4, 17):
            if off.getpixel((x, y)) != inner:
                off.putpixel((x, y), inner)
    hot = {(0x45, 0x76, 0x9c): CYAN, (0x6c, 0x86, 0xa3): CYAN_PALE, (0x65, 0x86, 0xa3): CYAN_PALE,
           (0x80, 0xa4, 0xbe): HIGHLIGHT, (0x39, 0x5b, 0x79): CYAN_DEEP, (0x12, 0x42, 0x6b): BLUE}

    def recolour(img):
        out = img.copy()
        for y in range(out.height):
            for x in range(out.width):
                p = out.getpixel((x, y))
                k = (p[0], p[1], p[2])
                if k in hot:
                    out.putpixel((x, y), rgba(hot[k]))
        return out
    return off, recolour(off), on, recolour(on)


def with_icon(btn, icon_name, disabled=False):
    out = btn.copy()
    ic = tile(icon_name)
    if disabled:
        ic = to_disabled(ic)
    out.alpha_composite(ic, ((out.width - ic.width) // 2, (out.height - ic.height) // 2))
    return out


def lock_set(locked):
    name = "ui01b_lock_closed" if locked else "ui01b_lock_open"
    return (with_icon(button(20, 20, "normal"), name),
            with_icon(button(20, 20, "hover"), name),
            with_icon(button(20, 20, "disabled"), name, True))


def scroller(bg):
    img = new(6, 32)
    if bg:
        rect(img, 0, 0, 5, 31, NAVY_DEEP)
        bevel(img, 0, 0, 5, 31, ABYSS, STEEL_DARK)
    else:
        rect(img, 0, 0, 5, 31, STEEL_BLUE)
        bevel(img, 0, 0, 5, 31, STEEL_LIGHT, STEEL_DARK)
        rect(img, 2, 4, 3, 27, PALE_BLUE)
    return img


# ---------------------------------------------------------------- hotbar family (same geometry as vanilla)
HB_INTERIOR_ALPHA = 186


def panel_from_masks(w, h, opaque, interior):
    """opaque(x,y) / interior(x,y) -> bool.  Opaque pixels get a recessed look around interior (translucent) cells."""
    img = new(w, h)

    def solid(x, y):
        return 0 <= x < w and 0 <= y < h and opaque(x, y)

    def inner(x, y):
        return 0 <= x < w and 0 <= y < h and interior(x, y)

    for y in range(h):
        for x in range(w):
            if interior(x, y):
                img.putpixel((x, y), rgba(NAVY_DEEP, HB_INTERIOR_ALPHA))
                continue
            if not opaque(x, y):
                continue
            if inner(x + 1, y) or inner(x, y + 1):
                col = ABYSS
            elif inner(x - 1, y) or inner(x, y - 1):
                col = STEEL_BLUE
            else:
                d = 99
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    k = 0
                    while solid(x + dx * (k + 1), y + dy * (k + 1)):
                        k += 1
                    d = min(d, k)
                col = ABYSS if d == 0 else STEEL_DARK if d == 1 else NAVY_MID
            img.putpixel((x, y), rgba(col))
    return img


def hotbar():
    w, h = 182, 22

    def interior(x, y):
        return 3 <= y <= 18 and x >= 3 and (x - 3) % 20 < 16 and x <= 178

    img = panel_from_masks(w, h, lambda x, y: True, interior)
    for i in range(1, 9):                     # rivets in the 4px dividers
        cx = 20 * i
        for yy in (6, 14):
            rect(img, cx, yy, cx + 1, yy + 1, CYAN_DEEP)
    return img


def selection(h):
    """24 x h selection frame: 4px ring around a transparent 16x16 hole at (4,4)."""
    w = 24
    img = new(w, h)
    cols = [CYAN_DEEP, CYAN_GLOW, CYAN, CYAN_DEEP]
    for y in range(h):
        for x in range(w):
            if 4 <= x <= 19 and 4 <= y <= 19:
                continue
            d = min(x, y, w - 1 - x, h - 1 - y, 3)
            a = 0xAE if (y > 0 and (x == 0 or x == w - 1)) else 255
            img.putpixel((x, y), rgba(cols[d], a))
    return img


def offhand(right):
    w, h = 29, 24
    ox = 7 if right else 0

    def opaque(x, y):
        x -= ox
        if not 0 <= x <= 21 or not 1 <= y <= 22:
            return False
        if y in (1, 22):
            return 2 <= x <= 19
        if y in (2, 21):
            return 1 <= x <= 20
        return True

    def interior(x, y):
        x -= ox
        return 3 <= x <= 18 and 4 <= y <= 18

    return panel_from_masks(w, h, opaque, interior)


# ---------------------------------------------------------------- tabs
def tab_1_21(selected, hover):
    w, h = 130, 24
    img = new(w, h)
    outer = rgba(ABYSS, 191)
    inner = rgba((CYAN if hover else CYAN_DEEP) if selected else (STEEL_BLUE if hover else STEEL_DARK))
    fill = rgba(NAVY_MID if hover else NAVY, 219)
    top = 0 if selected else 4
    for y in range(top, h):
        for x in range(w):
            if selected and 2 <= x <= w - 3 and y >= 2:
                continue                      # hollow body, merges into the panel
            if selected and y >= 22 and 2 <= x <= w - 3:
                continue
            if x in (0, w - 1) or y == top or (y == h - 1 and not selected):
                img.putpixel((x, y), outer)
            elif x in (1, w - 2) or y == top + 1 or (y == h - 2 and not selected):
                img.putpixel((x, y), inner)
            elif selected and y >= 22:
                img.putpixel((x, y), inner if y == 22 else outer)
            else:
                img.putpixel((x, y), fill)
    if selected:                              # bottom corner stubs: row 22 inner, row 23 outer
        for x in (0, 1, w - 2, w - 1):
            img.putpixel((x, 22), inner)
            img.putpixel((x, 23), outer)
    return img


def tab_1_20(tall, hover):
    """130 x 24 opaque tab cell: tall = 24 rows with open bottom corners, short = 18 rows centred at +4."""
    img = new(130, 24)
    inner = CYAN if hover else STEEL_DARK
    fill = NAVY_MID if hover else NAVY
    y0, y1 = (0, 23) if tall else (4, 21)
    for y in range(y0, y1 + 1):
        for x in range(130):
            if tall and y >= 22 and (x < 2 or x > 127):
                continue
            edge = x in (0, 129) or y == y0 or (not tall and y == y1)
            ring = x in (1, 128) or y == y0 + 1 or (not tall and y == y1 - 1)
            img.putpixel((x, y), rgba(ABYSS if edge else inner if ring else fill))
    return img


# ---------------------------------------------------------------- backgrounds / separators
def _mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def band_tile():
    """16x16 calm tile: every row one uniform colour, 4 soft horizontal bands (4 rows each) from the navy family,
    adjacent bands only a few luminance steps apart, seamless vertically (no dots, specks or vertical edges)."""
    b1 = _mix(NAVY_DEEP[:3], NAVY[:3], 0.40)
    b2 = _mix(NAVY_DEEP[:3], NAVY[:3], 0.75)
    b3 = _mix(NAVY[:3], NAVY_MID[:3], 0.30)
    rows = [b1] * 4 + [b2] * 4 + [b3] * 4 + [b2] * 4
    return rows


BAND_LIGHT = (150, 180, 208)     # steel-blue the opaque 1.20.1 tiles blend toward when they must be brighter


def check_rows_uniform(img, name):
    for y in range(img.height):
        row = {img.getpixel((x, y)) for x in range(img.width)}
        assert len(row) == 1, f"{name}: row {y} is not uniform (dots would return)"


def menu_bg(alpha, level=1.0):
    """level <= 1 darkens the bands; level > 1 blends toward BAND_LIGHT (keeps the steel-blue hue, no saturation)."""
    out = new(16, 16)
    for y, c in enumerate(band_tile()):
        if level <= 1:
            c = tuple(min(255, round(v * level)) for v in c)
        else:
            c = _mix(c, BAND_LIGHT, min(1.0, level - 1))
        for x in range(16):
            out.putpixel((x, y), (c[0], c[1], c[2], alpha))
    check_rows_uniform(out, f"menu_bg(alpha={alpha}, level={level:.3f})")
    return out


def mean_rgb(img):
    px = [img.getpixel((x, y)) for y in range(img.height) for x in range(img.width)]
    return sum(p[0] + p[1] + p[2] for p in px) / (3 * len(px))


def opaque_bg(target):
    """16x16 opaque background whose mean RGB equals target (vanilla file's mean luminance) within ~1%."""
    lo, hi = 0.1, 2.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if mean_rgb(menu_bg(255, mid)) < target:
            lo = mid
        else:
            hi = mid
    return menu_bg(255, (lo + hi) / 2)


def sep_1_21(header):
    img = new(32, 2)
    light, dark = rgba(STEEL_DARK, 200), rgba(ABYSS, 191)
    rect(img, 0, 0, 31, 0, light if header else dark)
    rect(img, 0, 1, 31, 1, dark if header else light)
    return img


def sep_1_20(header):
    img = new(32, 2)
    rect(img, 0, 0, 31, 0, rgba(STEEL_DARK if header else ABYSS))
    rect(img, 0, 1, 31, 1, rgba(ABYSS if header else STEEL_DARK))
    return img


# ---------------------------------------------------------------- 1.20.1 widgets.png extras
def lamp(fill, edge, glow):
    img = new(15, 15)
    c = 7.0
    for y in range(15):
        for x in range(15):
            d2 = (x - c) ** 2 + (y - c) ** 2
            if d2 <= 7.5 ** 2:
                img.putpixel((x, y), rgba(edge if d2 > 6.0 ** 2 else fill))
    for x, y in ((4, 3), (5, 3), (6, 3), (3, 4), (3, 5)):
        img.putpixel((x, y), rgba(glow))
    return img


def plus():
    img = new(9, 9)
    rect(img, 3, 0, 5, 8, CYAN)
    rect(img, 0, 3, 8, 5, CYAN)
    rect(img, 4, 1, 4, 7, CYAN_PALE)
    rect(img, 1, 4, 7, 4, CYAN_PALE)
    return img


FONT3X5 = {
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "!": ["010", "010", "010", "000", "010"],
}


def digits():
    img = new(48, 8)
    for i, ch in enumerate("12345!"):
        ox = 8 * i + 2
        rows = FONT3X5[ch]
        for j, row in enumerate(rows):
            for k, v in enumerate(row):
                if v == "1":
                    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                        px, py = ox + k + dx, 1 + j + dy
                        if 0 <= px < 48 and 0 <= py < 8 and img.getpixel((px, py))[3] == 0:
                            img.putpixel((px, py), rgba(ABYSS))
        for j, row in enumerate(rows):
            for k, v in enumerate(row):
                if v == "1":
                    img.putpixel((ox + k, 1 + j), rgba(HIGHLIGHT))
    return img


def envelope(lighter):
    src = tile("ui01b_envelope").copy()
    out = new(15, 12)
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3] and lighter:
                p = tuple(int(c + (255 - c) * 0.35) for c in p[:3]) + (p[3],)
            out.putpixel((x, y), p)
    return out


def slider_handle_row(hover):
    """200x20 row: only the outer 4px of each end are drawn for an 8px handle."""
    h = handle(hover)
    row = new(200, 20)
    for x in range(200):
        sx = x if x < 4 else x - 192 if x >= 196 else 3
        for y in range(20):
            row.putpixel((x, y), h.getpixel((sx, y)))
    return row


# ---------------------------------------------------------------- build
def mcmeta(w, h, border):
    return {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}


def build_neoforge():
    """-> (images {relpath: Image}, mcmetas {relpath: dict})"""
    imgs, metas = {}, {}
    wd = "sprites/widget/"

    def put(path, img, meta=None):
        imgs[path] = img
        if meta:
            metas[path] = meta
    put(wd + "button.png", button(200, 20, "normal"), mcmeta(200, 20, 6))
    put(wd + "button_highlighted.png", button(200, 20, "hover"), mcmeta(200, 20, 6))
    put(wd + "button_disabled.png", button(200, 20, "disabled"), mcmeta(200, 20, 6))
    put(wd + "slider.png", track(200, 20, False), mcmeta(200, 20, 2))
    put(wd + "slider_highlighted.png", track(200, 20, True), mcmeta(200, 20, 2))
    hb = {"left": 2, "top": 2, "right": 2, "bottom": 3}
    put(wd + "slider_handle.png", handle(False), mcmeta(8, 20, hb))
    put(wd + "slider_handle_highlighted.png", handle(True), mcmeta(8, 20, hb))
    put(wd + "text_field.png", text_field(200, 20, False), mcmeta(200, 20, 2))
    put(wd + "text_field_highlighted.png", text_field(200, 20, True), mcmeta(200, 20, 2))
    off, offh, on, onh = checkbox_variants()
    put(wd + "checkbox.png", off)
    put(wd + "checkbox_highlighted.png", offh)
    put(wd + "checkbox_selected.png", on)
    put(wd + "checkbox_selected_highlighted.png", onh)
    for locked, base in ((True, "locked_button"), (False, "unlocked_button")):
        n, h, d = lock_set(locked)
        put(wd + base + ".png", n)
        put(wd + base + "_highlighted.png", h)
        put(wd + base + "_disabled.png", d)
    put(wd + "scroller.png", scroller(False), mcmeta(6, 32, 1))
    put(wd + "scroller_background.png", scroller(True), mcmeta(6, 32, 1))
    tb = {"left": 2, "top": 2, "right": 2, "bottom": 0}
    put(wd + "tab.png", tab_1_21(False, False), mcmeta(130, 24, tb))
    put(wd + "tab_highlighted.png", tab_1_21(False, True), mcmeta(130, 24, tb))
    put(wd + "tab_selected.png", tab_1_21(True, False), mcmeta(130, 24, tb))
    put(wd + "tab_selected_highlighted.png", tab_1_21(True, True), mcmeta(130, 24, tb))
    put("sprites/hud/hotbar.png", hotbar())
    put("sprites/hud/hotbar_selection.png", selection(23))
    put("sprites/hud/hotbar_offhand_left.png", offhand(False))
    put("sprites/hud/hotbar_offhand_right.png", offhand(True))
    put("menu_background.png", menu_bg(64))
    put("menu_list_background.png", menu_bg(112))
    put("inworld_menu_background.png", menu_bg(64))
    put("inworld_menu_list_background.png", menu_bg(112))
    put("tab_header_background.png", new(16, 16))          # vanilla file is fully transparent
    put("header_separator.png", sep_1_21(True))
    put("footer_separator.png", sep_1_21(False))
    put("inworld_header_separator.png", sep_1_21(True))
    put("inworld_footer_separator.png", sep_1_21(False))
    return imgs, metas


def build_forge():
    imgs = {}
    sheet = new(256, 256)
    sheet.alpha_composite(hotbar(), (0, 0))
    sheet.alpha_composite(selection(24), (0, 22))
    sheet.alpha_composite(offhand(False), (24, 22))
    sheet.alpha_composite(offhand(True), (53, 22))
    for v, st in ((46, "disabled"), (66, "normal"), (86, "hover")):
        sheet.alpha_composite(button(200, 20, st), (0, v))
    sheet.alpha_composite(with_icon(button(20, 20, "normal"), "ui01b_globe"), (0, 106))
    sheet.alpha_composite(with_icon(button(20, 20, "hover"), "ui01b_globe"), (0, 126))
    for x, locked in ((0, True), (20, False)):
        n, h, d = lock_set(locked)
        for v, im in ((146, n), (166, h), (186, d)):
            sheet.alpha_composite(im, (x, v))
    sheet.alpha_composite(lamp((0xC8, 0x32, 0x32), (0x7A, 0x1E, 0x1E), (0xFF, 0x9A, 0x9A)), (192, 0))
    sheet.alpha_composite(lamp((0x3C, 0xC8, 0x50), (0x1E, 0x7A, 0x32), (0xA6, 0xFF, 0xB4)), (208, 0))
    sheet.alpha_composite(lamp(STEEL_DARK, ABYSS, STEEL_LIGHT), (224, 0))
    sheet.alpha_composite(plus(), (243, 3))
    sheet.alpha_composite(envelope(False), (166, 24))
    sheet.alpha_composite(envelope(True), (182, 24))
    sheet.alpha_composite(digits(), (198, 22))
    imgs["widgets.png"] = sheet

    sl = new(256, 256)
    sl.alpha_composite(track(200, 20, False), (0, 0))
    sl.alpha_composite(track(200, 20, True), (0, 20))
    sl.alpha_composite(slider_handle_row(False), (0, 40))
    sl.alpha_composite(slider_handle_row(True), (0, 60))
    imgs["slider.png"] = sl

    cb = new(64, 64)
    off, offh, on, onh = checkbox_variants()
    for (x, y), im in (((0, 0), off), ((20, 0), offh), ((0, 20), on), ((20, 20), onh)):
        cb.alpha_composite(im, (x, y))
    imgs["checkbox.png"] = cb

    tb = new(256, 256)
    tb.alpha_composite(tab_1_20(True, False), (0, 0))
    tb.alpha_composite(tab_1_20(True, True), (0, 24))
    tb.alpha_composite(tab_1_20(False, False), (0, 48))
    tb.alpha_composite(tab_1_20(False, True), (0, 72))
    imgs["tab_button.png"] = tb

    imgs["options_background.png"] = opaque_bg(99.2)       # vanilla mean luminance (drawn x0.25)
    imgs["light_dirt_background.png"] = opaque_bg(29.4)   # vanilla mean luminance (drawn x0.125)
    imgs["header_separator.png"] = sep_1_20(True)
    imgs["footer_separator.png"] = sep_1_20(False)
    return imgs, {}


# ---------------------------------------------------------------- preview
def preview(imgs, path, loader):
    scale, pad, maxw = 3, 10, 1900
    items = sorted(imgs.items())
    done = None
    for name, im in items:
        if name.endswith("widget/button.png"):
            done = name
    tiles = []
    for name, im in items:
        im = im.copy()
        if name == done:
            d = ImageDraw.Draw(im)
            d.text((100 - 12, 5), "Done", fill=(255, 255, 255, 255))
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        tiles.append((name, big))
    x = y = pad
    rowh = 0
    place = []
    for name, big in tiles:
        if x + big.width + pad > maxw and x > pad:
            x = pad
            y += rowh + 24
            rowh = 0
        place.append((name, big, x, y))
        x += max(big.width, 100) + pad
        rowh = max(rowh, big.height)
    sheet = Image.new("RGBA", (maxw, y + rowh + 24), (128, 128, 128, 255))
    d = ImageDraw.Draw(sheet)
    for name, big, px, py in place:
        d.text((px, py), name.split('/')[-1][:-4], fill=(255, 255, 0, 255))
        sheet.alpha_composite(big, (px, py + 12))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    sheet.save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--loader", required=True, choices=("neoforge", "forge"))
    ap.add_argument("--root", required=True, help="project dir that receives src/main/resources/assets/minecraft/...")
    ap.add_argument("--preview", help="write a 3x contact sheet PNG")
    a = ap.parse_args()
    imgs, metas = build_neoforge() if a.loader == "neoforge" else build_forge()
    base = os.path.join(a.root, "src", "main", "resources", "assets", "minecraft", "textures", "gui")
    for rel, im in imgs.items():
        p = os.path.join(base, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
    for rel, meta in metas.items():
        with open(os.path.join(base, rel + ".mcmeta"), "w", newline="\n") as f:
            json.dump(meta, f, separators=(",", ":"))
            f.write("\n")
    print("%s: %d png, %d mcmeta -> %s" % (a.loader, len(imgs), len(metas), base))
    if a.preview:
        preview(imgs, a.preview, a.loader)


if __name__ == "__main__":
    main()
