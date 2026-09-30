"""Abyssia resource-plant sprites: the new plants (with ripe / harvested states) and the plant material icons.

Drawn as 16x16 pixel art from scratch (no vanilla pixels). Plants keep dark, low-chroma deep-sea bodies; colour
sits on what the player harvests (resin beads, oil bladders, glowing polyps, crystal growths), so a ripe plant
reads at a glance. Only luminous plants get a glow layer (<name>_ripe_glow.png), and only on their small light
organs.

    python tools/plant_textures.py                     # write every texture into the assets
    python tools/plant_textures.py --list              # JSON: texture -> kind
    python tools/plant_textures.py --preview sheet.png # contact sheet (x4), nothing written
"""
from __future__ import annotations

import argparse
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures")


def rgb(h: str) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


class Canvas:
    def __init__(self, seed: int = 0):
        self.a = np.zeros((16, 16, 4), np.uint8)
        self.glow = np.zeros((16, 16, 4), np.uint8)
        self.rng = np.random.default_rng(seed)

    def put(self, x, y, col, glow=False):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < 16 and 0 <= y < 16:
            self.a[y, x] = rgb(col)
            if glow:
                self.glow[y, x] = rgb(col)

    def line(self, x0, y0, x1, y1, col, glow=False):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n):
            t = i / max(n - 1, 1)
            self.put(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, col, glow)

    def disc(self, cx, cy, r, col, glow=False):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.put(x, y, col, glow)

    def ellipse(self, cx, cy, rx, ry, ramp, light=(-0.6, -0.8)):
        """Shaded ellipse: ramp dark -> light, lit from the top left."""
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1.0:
                    lit = -(dx * light[0] + dy * light[1]) * 0.5 + 0.5 - d * 0.35
                    k = min(len(ramp) - 1, max(0, int(lit * len(ramp))))
                    self.put(x, y, ramp[k])

    def outline(self, col):
        """1-px dark outline around the sprite (item icons)."""
        alpha = self.a[..., 3] > 0
        grown = alpha.copy()
        grown[1:] |= alpha[:-1]
        grown[:-1] |= alpha[1:]
        grown[:, 1:] |= alpha[:, :-1]
        grown[:, :-1] |= alpha[:, 1:]
        for y, x in zip(*np.nonzero(grown & ~alpha)):
            self.a[y, x] = rgb(col)

    def image(self) -> Image.Image:
        return Image.fromarray(self.a, "RGBA")

    def glow_image(self) -> Image.Image | None:
        return Image.fromarray(self.glow, "RGBA") if self.glow[..., 3].any() else None


def seed_of(name: str) -> int:
    return sum((i + 1) * ord(c) for i, c in enumerate(name))


# ================================================================ plants (ripe = harvestable state)

def strandweed(ripe: bool) -> Canvas:
    c = Canvas(seed_of("strandweed"))
    body = ("#1c3a30", "#27503e", "#34664c")
    tuft = ("#6a9a78", "#8ab894", "#b0d4b0")
    for i, x0 in enumerate((4, 6, 7, 9, 10, 12)):
        h = (13 if ripe else 7) - (i % 3)
        phase = c.rng.uniform(0, 6.28)
        pts = [(x0 + 1.4 * math.sin(phase + k * 0.45) * (k / h), 15 - k) for k in range(h)]
        for k, (x, y) in enumerate(pts):
            c.put(x, y, body[min(2, k * 3 // h)])
        if ripe:
            x, y = pts[-1]
            c.put(x, y, tuft[2])
            c.put(x - 1, y + 1, tuft[1])
            c.put(x + 1, y + 1, tuft[0])
    return c


def amber_fan(ripe: bool) -> Canvas:
    c = Canvas(seed_of("amber_fan"))
    br = ("#3a1c18", "#52281f", "#6a3628")
    c.line(8, 15, 8, 11, br[0])
    tips = []
    for ang in (-62, -38, -14, 10, 34, 58):
        a = math.radians(ang)
        x1, y1 = 8 + math.sin(a) * 7, 11 - math.cos(a) * 9
        c.line(8, 11, x1, y1, br[1])
        mx, my = 8 + math.sin(a) * 3.5, 11 - math.cos(a) * 4.5
        c.line(mx, my, mx + math.sin(a + 0.5) * 2.5, my - math.cos(a + 0.5) * 2.5, br[2])
        tips.append((x1, y1))
        tips.append((mx + math.sin(a + 0.5) * 2.5, my - math.cos(a + 0.5) * 2.5))
    if ripe:
        for i, (x, y) in enumerate(tips):
            if i % 3 != 2:
                c.put(x, y, "#c8862a")
                c.put(x, y - 1, "#f0c05a")
    return c


def resin_root(tip: bool) -> Canvas:
    """Hanging roots: the body runs the full block height; the tip ends in resin droplets."""
    c = Canvas(seed_of("resin_root"))
    root = ("#2e2119", "#453224", "#5c4432")
    for i, x0 in enumerate((4, 7, 9, 12)):
        h = (13, 9, 12, 8)[i] if tip else 16
        pts = [(x0 + math.sin(k * 0.6 + i) * 0.9, k) for k in range(h)]
        for k, (x, y) in enumerate(pts):
            c.put(x, y, root[(k + i) % 3])
        if tip:
            x, y = pts[-1]
            c.put(x, y + 1, "#b87a24")
            c.put(x, y + 2, "#e8b050")
    return c


def knotstalk(top: bool) -> Canvas:
    """Bamboo coral: pale calcite segments joined by dark nodes."""
    c = Canvas(seed_of("knotstalk") + top)
    seg = ("#8e8a80", "#aaa698", "#c4c0b0")
    node = ("#2a221e", "#44362e")
    for i, x0 in enumerate((5, 10)):
        y_end = (4 if i == 0 else 7) if top else 0
        for y in range(15, y_end - 1, -1):
            if (y + 3 * i) % 5 == 0:
                c.put(x0, y, node[0])
                c.put(x0 + 1, y, node[1])
            else:
                c.put(x0, y, seg[1])
                c.put(x0 + 1, y, seg[2] if y % 2 else seg[0])
        if top:
            c.line(x0, y_end + 2, x0 - 2, y_end - 1, seg[1])
            c.line(x0 + 1, y_end + 4, x0 + 3, y_end + 1, seg[1])
            for px, py in ((x0 - 2, y_end - 2), (x0 + 3, y_end), (x0, y_end - 1)):
                c.put(px, py, "#b89a94")
    return c


def cinder_stalk(_: bool) -> Canvas:
    c = Canvas(seed_of("cinder_stalk"))
    char = ("#171312", "#241e1b", "#332a25")
    for i, (x0, h) in enumerate(((5, 11), (8, 14), (11, 9))):
        for k in range(h):
            y = 15 - k
            x = x0 + (1 if k > h * 0.6 and i != 1 else 0)
            c.put(x, y, char[(k + i) % 3])
            c.put(x + 1, y, char[0])
            if (k * 7 + i * 3) % 5 == 0:
                c.put(x + (k % 2), y, "#7a2c16")
        c.put(x0 + 1, 15 - h, "#5e5a54")
        c.put(x0, 15 - h, "#48443f")
    return c


def oil_bladder_weed(ripe: bool) -> Canvas:
    c = Canvas(seed_of("oil_bladder_weed"))
    frond = ("#2e2e16", "#424020", "#58552c")
    for i, (x0, lean) in enumerate(((6, -0.25), (9, 0.3), (8, 0.0))):
        h = (12, 13, 9)[i]
        pts = [(x0 + lean * k + 0.6 * math.sin(k * 0.7 + i), 15 - k) for k in range(h)]
        for k, (x, y) in enumerate(pts):
            c.put(x, y, frond[(k // 3 + i) % 3])
        for k in range(3, h, 4):
            x, y = pts[k]
            side = 1 if (k // 4 + i) % 2 else -1
            if ripe:
                bx = x + side * 1.5
                c.put(bx, y, "#a8862a")
                c.put(bx, y - 1, "#d8b848")
                c.put(bx + side, y, "#c4a03a")
                c.put(bx, y - 1, "#ecd680")
            else:
                c.put(x + side, y, "#4e4420")
    return c


def lumen_quill(ripe: bool) -> Canvas:
    """Sea pen: dark rachis, leaflets, small polyps that glow when ripe."""
    c = Canvas(seed_of("lumen_quill"))
    body = ("#1e1428", "#2c1e3c", "#3c2a50")
    c.line(8, 15, 8, 2, body[1])
    c.line(8, 15, 8, 12, body[0])
    for y in range(3, 12, 2):
        w = 3 if 4 <= y <= 9 else 2
        for s in (-1, 1):
            c.line(8 + s, y + 1, 8 + s * w, y - 1 + (y % 4 == 1), body[2])
            px, py = 8 + s * w, y - 1 + (y % 4 == 1)
            if ripe:
                c.put(px, py, "#a8e4ff" if (y + s) % 3 else "#e4f8ff", glow=True)
            else:
                c.put(px, py, "#44506a")
    c.put(8, 1, "#c8f0ff" if ripe else "#44506a", glow=ripe)
    return c


def glasslace(ripe: bool) -> Canvas:
    """Glass sponge: a vase-shaped lattice of silica."""
    c = Canvas(seed_of("glasslace"))
    lat = ("#5e6c72", "#84949a", "#aebcc0")
    rows = range(15, 1, -1)
    for y in rows:
        half = 1.2 + (15 - y) * 0.32
        xl, xr = 8 - half, 8 + half
        c.put(xl, y, lat[1])
        c.put(xr, y, lat[0])
        if (15 - y) % 3 == 0:
            c.line(xl, y, xr, y, lat[0])
    for k in range(-6, 7, 3):
        c.line(8 + k * 0.2, 15, 8 + k, 3, lat[2] if k < 0 else lat[1])
    c.line(8 - 5.3, 2, 8 + 5.3, 2, lat[2])
    if ripe:
        for (x, y) in ((4, 1), (5, 0), (11, 1), (12, 0), (6, 8), (10, 6)):
            c.put(x, y, "#58c4d0", glow=True)
            c.put(x, y - 1, "#bff4f8", glow=True)
    return c


def silt_comb(_: bool) -> Canvas:
    """Xenophyophore: fragile, lumpy plates built of sediment, pitted."""
    c = Canvas(seed_of("silt_comb"))
    ramp = ("#3e362a", "#554a3a", "#6c604c", "#83765e")
    for cx, cy, rx, ry in ((6, 11.5, 3.2, 3.8), (10.5, 12, 3.0, 3.4), (8, 8, 2.6, 3.0), (11, 7.5, 1.8, 2.2)):
        c.ellipse(cx, cy, rx, ry, ramp)
    for _ in range(9):
        x, y = c.rng.integers(4, 13), c.rng.integers(6, 15)
        if c.a[y, x, 3]:
            c.put(x, y, "#2a241c")
    return c


def pressure_gourd(ripe: bool) -> Canvas:
    c = Canvas(seed_of("pressure_gourd"))
    body = ("#141322", "#1f1d33", "#2b2946", "#3a385c")
    husk = ("#4e4f64", "#6a6c82", "#8a8ca2", "#aeb0c2")
    c.line(8, 15, 8, 13, "#23222e")
    if ripe:
        c.ellipse(8, 10.5, 5.0, 4.2, husk)
        for x in (5, 8, 11):
            c.line(x, 7, x + (0 if x == 8 else (1 if x > 8 else -1)), 14, "#3a3a4e")
        c.put(8, 6, "#23222e")
        c.put(8, 5, "#2e2d3c")
    else:
        c.ellipse(8, 12, 3.0, 2.6, body)
        c.put(8, 9, "#23222e")
    return c


# name -> (draw(state), mode): ripe = ripe / harvested pair, column = top / body, hang = tip / body, single = one texture
PLANT_SPRITES = {
    "strandweed": (strandweed, "ripe"), "amber_fan": (amber_fan, "ripe"), "resin_root": (resin_root, "hang"),
    "oil_bladder_weed": (oil_bladder_weed, "ripe"), "lumen_quill": (lumen_quill, "ripe"), "glasslace": (glasslace, "ripe"),
    "pressure_gourd": (pressure_gourd, "ripe"),
    "knotstalk": (knotstalk, "column"),
    "cinder_stalk": (cinder_stalk, "single"), "silt_comb": (silt_comb, "single"),
}


def plant_textures() -> dict[str, tuple[Image.Image, Image.Image | None]]:
    """texture name -> (image, glow). ripe: <n>_ripe + <n>; column: <n>_top + <n>; hang: <n>_tip + <n>; single: <n>."""
    out = {}
    for name, (draw, mode) in PLANT_SPRITES.items():
        if mode == "ripe":
            for ripe in (True, False):
                c = draw(ripe)
                out[name + ("_ripe" if ripe else "")] = (c.image(), c.glow_image())
        elif mode == "hang":
            for tip in (True, False):
                c = draw(tip)
                out[name + ("_tip" if tip else "")] = (c.image(), None)
        elif mode == "column":
            for top in (True, False):
                c = draw(top)
                out[name + ("_top" if top else "")] = (c.image(), None)
        else:
            c = draw(True)
            out[name] = (c.image(), None)
    return out


# ================================================================ material icons

OUTLINE = "#0e0c10"


def strands_icon(ramp, band):
    c = Canvas(1)
    for k in range(5):
        off = k - 2
        for t in range(12):
            x = 3 + t + off * 0.7 + 0.5 * math.sin(t * 0.8 + k)
            y = 13 - t + off * 0.7
            c.put(x, y, ramp[(k + t // 4) % len(ramp)])
    for d in range(-2, 3):
        c.put(8 + d * 0.5 + 0.5, 8 + d * 0.5 - 0.5, band[0])
        c.put(9 + d * 0.5, 8.5 + d * 0.5, band[1])
    c.outline(OUTLINE)
    return c


def rope_icon():
    c = Canvas(2)
    ramp = ("#4c5840", "#6a7a58", "#8c9a74", "#b0bc94")
    for i in range(48):
        a = i / 48 * 2 * math.pi
        for r in (4.0, 5.2):
            x, y = 8 + math.cos(a) * r, 8 + math.sin(a) * r
            c.put(x, y, ramp[(i // 2 + int(r)) % 4])
    c.line(11, 11, 14, 14, ramp[2])
    c.line(12, 11, 14, 13, ramp[1])
    c.outline(OUTLINE)
    return c


def cloth_icon(ramp, fuzzy=False):
    c = Canvas(3)
    for y in range(3, 14):
        for x in range(2, 14):
            if x + y > 26 - 0:
                continue
            fold = x + y > 20
            k = 1 + ((x + y) % 2 if not fuzzy else int(c.rng.integers(0, 2)))
            if fold:
                k = 3 if fuzzy else 3
            if x in (2,) or y == 3:
                k = 2 if not fold else 3
            c.put(x, y, ramp[min(k, len(ramp) - 1)] if not (x == 13 or y == 13) else ramp[0])
    c.outline(OUTLINE)
    return c


def drop_icon(ramp, hi):
    c = Canvas(4)
    for y in range(2, 15):
        w = 0.6 + (y - 2) * 0.55 if y < 9 else math.sqrt(max(0, 16 - (y - 10.2) ** 2)) * 1.05
        for x in range(16):
            if abs(x + 0.5 - 8) <= w:
                dx = (x + 0.5 - 8) / max(w, 0.1)
                k = int(np.clip((0.55 - dx * 0.45) * len(ramp) - (y - 2) * 0.1, 0, len(ramp) - 1))
                c.put(x, y, ramp[k])
    c.put(6, 9, hi)
    c.put(6, 10, hi)
    c.put(7, 8, hi)
    c.outline(OUTLINE)
    return c


def adhesive_icon():
    c = Canvas(5)
    c.ellipse(8, 11, 6.5, 3.6, ("#3c3a40", "#56545c", "#72707a", "#8c8a94"))
    c.ellipse(8, 10, 4.6, 2.2, ("#3a2008", "#5c3610", "#80501c", "#a87028"))
    c.put(6, 9, "#e8c070")
    c.put(10, 7, "#80501c")
    c.put(10, 6, "#a87028")
    c.outline(OUTLINE)
    return c


def stalk_icon():
    c = Canvas(6)
    seg = ("#7c786e", "#a29e90", "#c8c4b4")
    for t in range(11):
        x, y = 3 + t, 12 - t
        node = t % 4 == 1
        for d, tone in ((-1, 2), (0, 1), (1, 0)):
            c.put(x + (d > 0), y + (d < 0) * 0 + (1 if d > 0 else 0) - (1 if d < 0 else 0) * 0, "#2e2622" if node else seg[tone])
            c.put(x + d, y, "#44362e" if node else seg[tone])
    c.outline(OUTLINE)
    return c


def bladder_icon():
    c = Canvas(7)
    c.line(8, 1, 9, 4, "#424020")
    c.ellipse(8, 9.5, 5.0, 5.5, ("#6e5a18", "#a8862a", "#c8a43a", "#e0c458", "#f0e08c"))
    c.put(6, 7, "#fff4c0")
    c.put(6, 8, "#f8e8a0")
    c.outline(OUTLINE)
    return c


def flask_icon():
    c = Canvas(8)
    glass = "#8aa0a8"
    c.line(7, 2, 9, 2, "#6a5238")
    c.line(7, 3, 9, 3, "#8a6a48")
    for y in (4, 5):
        c.put(7, y, glass)
        c.put(9, y, glass)
        c.put(8, y, "#b8c8cc")
    c.ellipse(8, 10.5, 5.0, 4.6, ("#7a5a10", "#b08818", "#d8b030", "#f0d860"))
    for y in range(6, 9):
        c.put(8 - (y - 5), y, glass)
        c.put(8 + (y - 5), y, glass)
    c.put(6, 9, "#fff4c0")
    c.outline(OUTLINE)
    return c


def gel_icon():
    c = Canvas(9)
    ramp = ("#1e5470", "#2e7a9a", "#4aa6c4", "#86d4ec", "#d6f8ff")
    for cx, cy, r in ((7, 10, 4.2), (10.5, 7.5, 3.0), (5.5, 6.5, 2.2)):
        c.ellipse(cx, cy, r, r * 0.95, ramp)
    c.put(6, 8, "#ffffff")
    c.outline(OUTLINE)
    return c


def cell_icon():
    c = Canvas(10)
    for y in range(2, 14):
        for x in range(5, 11):
            if (y in (2, 13)) and x in (5, 10):
                continue
            end = y < 4 or y > 11
            col = ("#6c4412", "#9a6420", "#c08a34")[(x - 5) // 2] if end else ("#2e7a9a", "#6ac4e0", "#d6f8ff", "#86d4ec", "#4aa6c4", "#2e7a9a")[x - 5]
            c.put(x, y, col)
    c.outline(OUTLINE)
    return c


def heap_icon(ramp, specks):
    c = Canvas(11)
    for y in range(6, 15):
        w = (y - 5) * 0.85 + 1
        for x in range(16):
            if abs(x + 0.5 - 8) <= w and y >= 6 + abs(x + 0.5 - 8) * 0.55:
                k = int(np.clip(len(ramp) - 1 - (y - 6) * 0.45 - (x - 8) * 0.12 + c.rng.normal(0, 0.6), 0, len(ramp) - 1))
                c.put(x, y, ramp[k])
    for col in specks:
        for _ in range(4):
            x, y = int(c.rng.integers(3, 13)), int(c.rng.integers(8, 15))
            if c.a[y, x, 3]:
                c.put(x, y, col)
    c.outline(OUTLINE)
    return c


def lens_icon():
    c = Canvas(12)
    c.disc(8, 8, 6.2, "#4e5c62")
    c.ellipse(8, 8, 5.2, 5.2, ("#1e5a66", "#34808e", "#5aaab6", "#96d6de", "#d8f6fa"))
    c.line(5, 6, 6, 4, "#ffffff")
    c.outline(OUTLINE)
    return c


def husk_icon():
    c = Canvas(13)
    ramp = ("#1e1c30", "#2e2c46", "#44425e", "#5e5c7c", "#8a8aa6")
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 11.5
            r = math.hypot(dx, dy * 1.25)
            if 4.2 <= r <= 8.2 and dy < 1.5:
                k = int(np.clip((8.2 - r) / 4.0 * 4 - dx * 0.05, 0, 4))
                c.put(x, y, ramp[k])
    for x in (4, 8, 12):
        c.line(x, 4 if x == 8 else 6, x + (x - 8) * 0.25, 10, "#141322")
    c.outline(OUTLINE)
    return c


def plating_icon():
    c = Canvas(14)
    ramp = ("#2a2842", "#3c3a5a", "#545274", "#72708e", "#9a98b4")
    for y in range(3, 13):
        for x in range(2, 14):
            k = 2
            if x == 2 or y == 3:
                k = 4
            elif x == 13 or y == 12:
                k = 0
            elif x == 3 or y == 4:
                k = 3
            elif x == 12 or y == 11:
                k = 1
            c.put(x, y, ramp[k])
    for x, y in ((4, 5), (11, 5), (4, 10), (11, 10)):
        c.put(x, y, "#b8b8cc")
    c.line(5, 8, 10, 7, "#3c3a5a")
    c.outline(OUTLINE)
    return c


ICONS = {
    "deep_fiber": lambda: strands_icon(("#26503e", "#357058", "#4c8c70", "#78b294"), ("#a89a6a", "#c8ba88")),
    "thermal_fiber": lambda: strands_icon(("#6a2a14", "#8e421c", "#b8662c", "#d8964c"), ("#3a3a3e", "#5a5a60")),
    "fiber_rope": rope_icon,
    "sea_cloth": lambda: cloth_icon(("#163c3c", "#245a5a", "#347676", "#4e9490")),
    "thermal_felt": lambda: cloth_icon(("#4a2214", "#7a3a20", "#9e5630", "#c07a48"), fuzzy=True),
    "plant_resin": lambda: drop_icon(("#5a2e08", "#8a4e12", "#b87420", "#e0a43c", "#f4cc70"), "#fff0b8"),
    "marine_adhesive": adhesive_icon,
    "hard_stalk": stalk_icon,
    "bio_oil": bladder_icon,
    "refined_oil": flask_icon,
    "lumen_gel": gel_icon,
    "lumen_cell": cell_icon,
    "organic_matter": lambda: heap_icon(("#1e1a12", "#2e281c", "#403624", "#54482e"), ("#4c5c2a", "#8a8068")),
    "deep_pigment": lambda: heap_icon(("#2e1430", "#4a2048", "#682c64", "#884080", "#a860a0"), ("#c888c0",)),
    "crystal_sap": lambda: drop_icon(("#1e5260", "#2e7282", "#4a98a6", "#80c6d0", "#c8f0f4"), "#ffffff"),
    "crystal_lens": lens_icon,
    "hadal_husk": husk_icon,
    "hadal_plating": plating_icon,
}


def item_textures() -> dict[str, Image.Image]:
    return {name: make().image() for name, make in ICONS.items()}


# ================================================================ run

def run(quiet: bool = False) -> list[str]:
    written = []
    block_dir, item_dir = os.path.join(ASSETS, "block"), os.path.join(ASSETS, "item")
    os.makedirs(block_dir, exist_ok=True)
    os.makedirs(item_dir, exist_ok=True)
    for name, (img, glow) in plant_textures().items():
        img.save(os.path.join(block_dir, name + ".png"))
        if glow is not None:
            glow.save(os.path.join(block_dir, name + "_glow.png"))
        written.append(name)
    for name, img in item_textures().items():
        img.save(os.path.join(item_dir, name + ".png"))
        written.append(name)
    if not quiet:
        print(f"Plant textures: {len(written)} written")
    return written


def glowing() -> set[str]:
    return {n for n, (_, g) in plant_textures().items() if g is not None}


def preview(path: str, scale: int = 4) -> None:
    items = [(n, i, g) for n, (i, g) in plant_textures().items()] + [(n, i, None) for n, i in item_textures().items()]
    cols = 10
    cell = 16 * scale + 8
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell * 2), (40, 44, 52, 255))
    for i, (name, img, glow) in enumerate(items):
        x, y = (i % cols) * cell + 4, (i // cols) * cell * 2 + 4
        sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST), (x, y))
        dark = Image.new("RGBA", (16 * scale, 16 * scale), (8, 12, 20, 255))
        dark.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST))
        if glow is not None:
            dark.alpha_composite(glow.resize((16 * scale, 16 * scale), Image.NEAREST))
        sheet.alpha_composite(dark, (x, y + cell))
    sheet.save(path)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--preview")
    a = ap.parse_args()
    if a.list:
        print(json.dumps({**{n: "block" for n in plant_textures()}, **{n: "item" for n in ICONS}}, indent=1))
    elif a.preview:
        preview(a.preview)
    else:
        run()


if __name__ == "__main__":
    main()
