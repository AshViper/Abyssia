"""Hand-finished Abyssia ancient-wood blocks and item icons: recoloured vanilla pixels, no procedural look.

Replaces the smooth / glossy / thick-outline textures of the ancient wood family (planks, stems, stripped stems, door
and trapdoor) and of the non-mineral items (oils, fibres, cloths, gels, resin ...).  Each texture is a vanilla texture
of an analogous object (dark oak planks for planks, the bone for the hard stalk, the string for the fibre ...) whose
pixels are re-toned through a short per-texture ramp:

  * the source's lightness order is kept (its own 1px dark outline becomes the ramp's darkest tone, never black),
  * alpha is kept (same silhouette style as vanilla),
  * the palette is limited to <= 7 tones (+ a copper handle ramp for doors),
  * a seeded jitter nudges a few interior pixels one tone up / down, so grain and light are uneven and never
    perfectly symmetric.

Called at the end of forge_textures.run(): ``run(quiet) -> list of names written``.  Deterministic.

    python tools/redo_wood_items.py                 # write the missing ones (tools/texture_locks.py policy)
    python tools/redo_wood_items.py --textures all  # redraw existing ones too (locked ones are restored afterwards)
    python tools/redo_wood_items.py --preview x.png # before/after sheet (8x) from inbox/backup/textures-20260930
"""
from __future__ import annotations

import argparse
import colorsys
import os
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

import texture_locks

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures")
BACKUP = os.path.join(HERE, "..", "inbox", "backup", "textures-20260930")


def rgb(h: str) -> tuple[float, float, float]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def ramp(stops: tuple[str, ...], k: int) -> list[tuple[int, int, int]]:
    """k tones interpolated along the colour stops, darkest first."""
    cols = [rgb(s) for s in stops]
    out = []
    for i in range(k):
        t = i / (k - 1) * (len(cols) - 1)
        a = min(int(t), len(cols) - 2)
        f = t - a
        out.append(tuple(round(cols[a][c] + (cols[a + 1][c] - cols[a][c]) * f) for c in range(3)))
    return out


# name -> (kind, vanilla source, ramp stops dark->light, tones, jitter share, extra)
# extra: "copper" = bright yellow handle pixels of a door take the copper ramp
WOOD = ("#26332c", "#3f5446", "#5d7863", "#7d9a80")        # ancient grey-green wood
WOOD_STEM = ("#0f2a30", "#1c4a52", "#2c6a70")               # teal bark
WOOD_STRIP = ("#33443a", "#5a735f", "#8aa58a")              # pale inner wood
COPPER = ("#3a2014", "#8a4d2c", "#c88450")

SPECS: dict[str, tuple] = {
    "block/ancient_planks": ("block/dark_oak_planks", WOOD, 6, 0.10, ""),
    "block/ancient_stem": ("block/dark_oak_log", WOOD_STEM, 6, 0.09, ""),
    "block/ancient_stem_top": ("block/dark_oak_log_top", WOOD_STEM, 6, 0.08, ""),
    "block/stripped_ancient_stem": ("block/stripped_dark_oak_log", WOOD_STRIP, 5, 0.05, ""),
    "block/stripped_ancient_stem_top": ("block/stripped_dark_oak_log_top", WOOD_STRIP, 5, 0.04, ""),
    "block/ancient_door_top": ("block/dark_oak_door_top", WOOD, 6, 0.08, "copper"),
    "block/ancient_door_bottom": ("block/dark_oak_door_bottom", WOOD, 6, 0.08, "copper"),
    "block/ancient_trapdoor": ("block/dark_oak_trapdoor", WOOD, 6, 0.08, ""),
    "item/ancient_door": ("item/dark_oak_door", WOOD, 6, 0.06, "copper"),
    "item/bio_oil": ("item/experience_bottle", ("#171a0c", "#4a4f1c", "#8f9a3c", "#c8cf78"), 6, 0.0, "glass"),
    "item/refined_oil": ("item/honey_bottle", ("#2a1a08", "#7a4c12", "#c8892c", "#eec46a"), 6, 0.0, "glass"),
    "item/crystal_lens": ("item/heart_of_the_sea", ("#14303a", "#2a6474", "#58a4b4", "#a4dce0"), 6, 0.04, ""),
    "item/crystal_sap": ("item/ghast_tear", ("#183a48", "#3c7f92", "#8ccad4", "#d0f0f2"), 5, 0.0, ""),
    "item/deep_fiber": ("item/string", ("#1e2a1e", "#4a6048", "#8aa088", "#c4d2bc"), 5, 0.0, ""),
    "item/fiber_rope": ("item/lead", ("#2a2a1a", "#5e5f3c", "#8d8e62", "#b8b88a"), 5, 0.06, ""),
    "item/thermal_fiber": ("item/wheat", ("#3a1c0c", "#8a4a1c", "#c47a34", "#e8b466"), 6, 0.06, ""),
    "item/sea_cloth": ("item/paper", ("#0e3438", "#1e6060", "#3c9088", "#78c0b4"), 5, 0.10, ""),
    "item/thermal_felt": ("item/leather", ("#3a140c", "#7a2c18", "#b04a26", "#d8764a"), 6, 0.10, ""),
    "item/deep_pigment": ("item/purple_dye", ("#1c1030", "#43266e", "#6c46a4", "#a684d0"), 5, 0.06, ""),
    "item/hadal_husk": ("item/scute", ("#14101c", "#2c2640", "#4a4266", "#6e6690"), 5, 0.10, ""),
    "item/hadal_plating": ("item/iron_ingot", ("#15181f", "#2e3442", "#4d566a", "#7a869a"), 6, 0.10, ""),
    "item/hard_stalk": ("item/bone", ("#2a2a22", "#6a6a58", "#a4a48c", "#d8d8c2"), 5, 0.06, ""),
    "item/lumen_cell": ("item/blaze_rod", ("#0e2a34", "#1f6474", "#38a8b8", "#98ece8"), 6, 0.05, "capsule"),
    "item/lumen_gel": ("item/slime_ball", ("#0e3038", "#1f6a7c", "#3ea8bc", "#94e4ea"), 6, 0.06, ""),
    "item/marine_adhesive": ("item/clay_ball", ("#22190f", "#54402a", "#8a6c48", "#b8986a"), 5, 0.10, ""),
    "item/organic_matter": ("item/rotten_flesh", ("#1a1c10", "#3c3f1e", "#666a30", "#8c9048"), 6, 0.10, ""),
    "item/plant_resin": ("item/magma_cream", ("#2e1a08", "#8a520e", "#d08c22", "#f0c860"), 6, 0.06, ""),
}


def _lum(a: np.ndarray) -> np.ndarray:
    return (0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]) / 255.0


def _capsule(src: Image.Image) -> Image.Image:
    """Upright tube (6 x 14, rounded ends) whose cross-section shading is swept from the vanilla rod's own profile."""
    a = np.array(src.convert("RGBA")).astype(float)
    lum = _lum(a)
    prof: dict[int, list[float]] = {}
    for y, x in zip(*np.nonzero(a[..., 3] > 0)):
        prof.setdefault(int(x + y), []).append(lum[y, x])
    keys = sorted(prof)
    vals = [float(np.mean(prof[c])) for c in keys]
    idx = np.linspace(0, len(vals) - 1, 6)
    row = [vals[int(round(i))] for i in idx]
    out = np.zeros((16, 16, 4))
    for y in range(1, 15):
        for j, x in enumerate(range(5, 11)):
            if y in (1, 14) and j in (0, 5):
                continue
            v = row[j] * (0.55 if y in (1, 14) else 1.0)
            out[y, x] = (v * 255, v * 255, v * 255, 255)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def render(name: str, src: Image.Image) -> Image.Image:
    if SPECS[name][4] == "capsule":
        src = _capsule(src)
    _, stops, k, jit, extra = SPECS[name][0], SPECS[name][1], SPECS[name][2], SPECS[name][3], SPECS[name][4]
    a = np.array(src.convert("RGBA")).astype(float)
    op = a[..., 3] > 0
    lum = _lum(a)
    lo, hi = np.percentile(lum[op], 2), np.percentile(lum[op], 98)
    t = np.clip((lum - lo) / max(hi - lo, 1e-6), 0, 1)
    lv = np.rint(t * (k - 1)).astype(int)
    rng = np.random.default_rng(zlib.crc32(name.encode()))
    # interior pixels (not the darkest outline tone) get a one-tone nudge, biased toward the shadow side
    nudge = rng.random(lv.shape) < jit
    sign = np.where(rng.random(lv.shape) < 0.55, -1, 1)
    lv = np.where(nudge & (lv > 0) & (lv < k - 1), lv + sign, lv)
    cols = np.array(ramp(stops, k))
    out = np.zeros(a.shape, np.uint8)
    out[..., :3] = cols[lv]
    out[..., 3] = a[..., 3].astype(np.uint8)
    hsv = np.array([[colorsys.rgb_to_hsv(*(p / 255)) for p in row] for row in a[..., :3]])
    if extra == "copper":       # yellow door handle
        m = op & (hsv[..., 1] > 0.55) & (hsv[..., 2] > 0.6) & (hsv[..., 0] < 0.2)
        cc = np.array(ramp(COPPER, 3))
        out[m, :3] = cc[np.where(lum[m] > 0.75, 2, 1)]
    if extra == "glass":        # pale bottle glass / cork stay pale grey-teal
        m = op & (hsv[..., 1] < 0.3) & (hsv[..., 2] > 0.55)
        gc = np.array(ramp(("#3a4a52", "#7f97a0", "#c0d2d6"), 3))
        out[m, :3] = gc[np.where(lum[m] > 0.8, 2, 1)]
    return Image.fromarray(out, "RGBA")


def _vanilla():
    sys.path.insert(0, HERE)
    import forge_textures as ft
    return ft.Vanilla(ft.find_client_jar())


def run(quiet: bool = False) -> list[str]:
    v = _vanilla()
    if not v.zip:
        if not quiet:
            print("redo_wood_items: no vanilla jar, skipped")
        return []
    written = []
    for name, spec in SPECS.items():
        path = os.path.join(ASSETS, name + ".png")
        if not texture_locks.wants(path):
            continue                                   # kept as committed / locked
        src = v.get(spec[0])
        if src is None:
            continue
        old = Image.open(path).size if os.path.exists(path) else src.size
        img = render(name, src)
        if img.size != old:
            img = img.resize(old, Image.NEAREST)
        img.save(path)
        written.append(name.split("/")[1])
    if not quiet:
        print(f"redo_wood_items: {len(written)} textures written")
    return written


def preview(out: str, cols: int = 4) -> None:
    S = 128
    names = list(SPECS)
    rows = (len(names) + cols - 1) // cols
    cw, ch = S * 2 + 24, S + 22
    sheet = Image.new("RGBA", (cols * cw, rows * ch), (58, 58, 68, 255))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        x, y = (i % cols) * cw + 4, (i // cols) * ch + 4
        for k, p in enumerate((os.path.join(BACKUP, n + ".png"), os.path.join(ASSETS, n + ".png"))):
            bg = Image.new("RGBA", (S, S), (36, 36, 44, 255))
            if os.path.exists(p):
                bg.alpha_composite(Image.open(p).convert("RGBA").resize((S, S), Image.NEAREST))
            sheet.paste(bg, (x + k * (S + 6), y))
        d.text((x, y + S + 3), n + "  (before | after)", fill=(235, 235, 235, 255))
    sheet.save(out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--textures", choices=texture_locks.MODES, default=texture_locks.MODE)
    a = ap.parse_args()
    texture_locks.set_mode(a.textures)
    run()
    if a.preview:
        preview(a.preview)
