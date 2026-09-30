"""Hand-finished plant / foliage block textures (replaces the procedural Texture Forge / pixelart look).

Method (decision vanilla-recolour-textures): the pixels of a suitable vanilla texture (seagrass, kelp, fern, vines,
corals, cornflower ...) are remapped by luminance through a small hand-picked colour ramp (3-4 tones, at most ~8
colours per texture) in the deep-sea palette, then broken up so they stop looking machine made: seeded tone
dithering, uneven blade lengths, wobbling stems, eroded silhouettes, one-sided rim light, random flips.

Glow companions (``<name>_glow.png``) are a subset of the base's own pixels (same colours, same positions).
Only companions that already exist in the assets are (re)written.  Sizes, animated strips and .mcmeta files match
the previous files.  Deterministic (fixed seeds).  Runs at the end of forge_textures.run(); CLI:

    python tools/redo_flora.py [--only a,b] [--out DIR] [--textures missing-only|locked-only|all]
Into the mod assets, writes follow tools/texture_locks.py (default: only missing PNGs, never locked ones) and the
glow companions are left to tools/derive_textures.py; ``--out DIR`` writes everything.
"""
from __future__ import annotations

import argparse
import colorsys
import glob
import json
import os
import zipfile
from io import BytesIO

import numpy as np
from PIL import Image

import texture_locks

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCK = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures", "block")
MC_VERSION = "1.20.1"


# ------------------------------------------------------------------ vanilla access
def _jar() -> str | None:
    env = os.environ.get("ABYSSIA_MC_JAR")
    if env and os.path.isfile(env):
        return env
    home = os.path.expanduser("~")
    for pat in (os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", MC_VERSION, "client-extra.jar"),
                os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", MC_VERSION, "client.jar"),
                os.path.join(home, ".gradle", "caches", "**", f"*{MC_VERSION}*client*.jar")):
        for p in glob.glob(pat, recursive=True):
            try:
                with zipfile.ZipFile(p) as z:
                    if "assets/minecraft/textures/block/stone.png" in z.namelist():
                        return p
            except (OSError, zipfile.BadZipFile):
                pass
    return None


def _van(z: zipfile.ZipFile, ref: str) -> np.ndarray:
    im = Image.open(BytesIO(z.read(f"assets/minecraft/textures/block/{ref}.png"))).convert("RGBA")
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    if im.size != (16, 16):
        im = im.resize((16, 16), Image.NEAREST)
    return np.asarray(im).copy()


# ------------------------------------------------------------------ ramps
def _rgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def R(dark: str, mid: str, light: str, n: int = 4) -> list:
    """n tones from dark to light through mid."""
    pts = [_rgb(dark), _rgb(mid), _rgb(light)]
    out = []
    for i in range(n):
        u = i / (n - 1) * 2
        a = min(int(u), 1)
        c = pts[a] + (pts[a + 1] - pts[a]) * (u - a)
        out.append(tuple(int(round(v)) for v in c))
    return out


# ------------------------------------------------------------------ sprite ops (rgb + alpha move together)
class Sp:
    def __init__(self, arr: np.ndarray):
        self.rgb = arr[..., :3].astype(float) / 255.0
        self.a = arr[..., 3] >= 128

    def each(self, f):
        self.rgb = f(self.rgb)
        self.a = f(self.a)

    def shift_row(self, y, dx):
        if dx == 0:
            return
        r, a = np.zeros_like(self.rgb[y]), np.zeros_like(self.a[y])
        if dx > 0:
            r[dx:], a[dx:] = self.rgb[y][:-dx], self.a[y][:-dx]
        else:
            r[:dx], a[:dx] = self.rgb[y][-dx:], self.a[y][-dx:]
        self.rgb[y], self.a[y] = r, a

    def kill(self, y, x):
        self.a[y, x] = False


def op_flipx(sp, rng): sp.each(lambda m: m[:, ::-1].copy())
def op_flipy(sp, rng): sp.each(lambda m: m[::-1].copy())
def op_transpose(sp, rng): sp.each(lambda m: np.swapaxes(m, 0, 1).copy())


def op_ground(sp, rng):
    rows = np.nonzero(sp.a.any(1))[0]
    if len(rows) == 0:
        return
    d = 15 - rows.max()
    if d:
        sp.each(lambda m: np.concatenate([np.zeros_like(m[:d]), m[:16 - d]]))


def op_wobble(sp, rng, amp=0.8, k=1):
    """Periodic sideways drift per row (tile safe): strands are no longer perfectly straight columns."""
    ph = rng.uniform(0, 6.28)
    for y in range(16):
        sp.shift_row(y, int(round(amp * np.sin(2 * np.pi * k * y / 16 + ph))))


def op_trim(sp, rng, mx=3, bottom=False):
    """Uneven blade lengths: cut 0..mx pixels off the free end of each column."""
    for x in range(16):
        ys = np.nonzero(sp.a[:, x])[0]
        if len(ys) < 3:
            continue
        k = int(rng.integers(0, mx + 1))
        for y in (ys[::-1][:k] if bottom else ys[:k]):
            sp.kill(y, x)


def op_erode(sp, rng, p=0.15, tile=False):
    """Nibble edge pixels: ragged silhouette."""
    a = sp.a
    pad = np.pad(a, 1)
    edge = a & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    for y, x in zip(*np.nonzero(edge)):
        if tile and y in (0, 15):
            continue
        if rng.random() < p:
            sp.kill(y, x)


def op_fold(sp, rng):
    """Mirror the top half over the bottom half: a stalk that stacks without a seam."""
    sp.each(lambda m: np.concatenate([m[:8], m[:8][::-1]]))


def op_crop(sp, rng, y0=0):
    def f(m):
        o = np.zeros_like(m)
        o[y0:] = m[y0:]
        return o
    sp.each(f)


def op_cover(sp, rng, frac=0.6, cell=4):
    """Cut a carpet down to organic patches (smooth random blobs, no straight edges)."""
    n = rng.integers(0, 255, (cell, cell), dtype=np.uint8)
    big = np.asarray(Image.fromarray(n).resize((16, 16), Image.BICUBIC)).astype(float)
    big += rng.normal(0, 10, big.shape)
    keep = big >= np.quantile(big, 1 - frac)
    pad = np.pad(keep, 1)
    nb = pad[:-2, 1:-1].astype(int) + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:]
    keep &= nb >= 1
    sp.a &= keep


def op_hole(sp, rng, r=4.6):
    """Round, slightly lopsided opening (top of a tube block)."""
    cx, cy = 7.5 + rng.uniform(-.5, .5), 7.5 + rng.uniform(-.5, .5)
    for y in range(16):
        for x in range(16):
            if (x - cx) ** 2 + (y - cy) ** 2 < (r + rng.uniform(-.5, .5)) ** 2:
                sp.rgb[y, x] = 0.02
                sp.a[y, x] = True
                sp.hole = getattr(sp, "hole", set()) | {(y, x)}



def op_bloom(sp, rng):
    """Keep the vanilla stem, replace the flower head by 1-2 square glowing bulbs on thin stalks."""
    h, sat, v = _hsv(sp)
    heads = sp.a & (sat > 0.25) & ((h < 0.17) | (h > 0.47))
    ys, xs = np.nonzero(heads)
    cy, cx = (int(round(ys.mean())), int(round(xs.mean()))) if len(ys) else (5, 8)
    cy, cx = int(np.clip(cy, 2, 7)), int(np.clip(cx, 2, 13))
    sp.a = np.zeros((16, 16), bool)     # thin dark stalks are redrawn below
    sp.tfix, mark = {}, np.zeros((16, 16), bool)
    def stalk(y0, x):
        for y in range(y0, 16):
            if rng.random() < 0.2 and y > y0:
                x = int(np.clip(x + rng.choice([-1, 1]), 0, 15))
            sp.a[y, x] = True
            sp.tfix[(y, x)] = 0.2 + 0.25 * rng.random()

    def bulb(y, x, n):
        cells = [(y + dy, x + dx) for dy in range(n) for dx in range(n)]
        if n == 3 and rng.random() < 0.25:
            cells.remove(cells[int(rng.choice([0, 2, 6, 8]))])
        core = (y + n // 2, x + n // 2)
        for (yy, xx) in cells:
            if 0 <= yy < 16 and 0 <= xx < 16:
                sp.a[yy, xx] = True
                mark[yy, xx] = True
                sp.tfix[(yy, xx)] = 1.0 if (yy, xx) == core else (0.9 if rng.random() < 0.3 else 0.8)
        return y + n, x + n // 2

    by, bx = bulb(cy - 1, cx - 1, 3)
    stalk(by, bx)
    side = -1 if cx > 8 else 1
    x2 = int(np.clip(cx + side * int(rng.integers(4, 6)), 1, 13))
    y2 = int(np.clip(cy + int(rng.integers(3, 6)), 6, 11))
    by, bx = bulb(y2, x2, 2)
    stalk(by, bx)
    sp.mark = mark


def op_roots(sp, rng, n=4, tip=False, beads=0, hair=True):
    """Clear hanging root strands: near-vertical, irregular 1px kinks, darker, no speckle."""
    sp.a = np.zeros((16, 16), bool)
    sp.tfix = {}
    mark = np.zeros((16, 16), bool)
    cols = np.linspace(2, 13, n) + rng.uniform(-1, 1, n)
    for c in cols:
        x = int(round(c))
        ln = int(rng.integers(8, 14)) if tip else 16
        base = rng.uniform(0.3, 0.55)
        for y in range(ln):
            if rng.random() < 0.17 and 0 < y < 14:
                x = int(np.clip(x + rng.choice([-1, 1]), 0, 15))
            sp.a[y, x] = True
            sp.tfix[(y, x)] = float(np.clip(base + rng.normal(0, 0.06) - (0.25 if tip and y >= ln - 2 else 0), 0.05, 0.9))
            if hair and rng.random() < 0.08 and 1 < y < ln - 2:
                hx = x + int(rng.choice([-1, 1]))
                if 0 <= hx < 16:
                    sp.a[y, hx] = True
                    sp.tfix[(y, hx)] = base * 0.8
        if beads:
            ys = [y for y in range(3, ln - 1) if sp.a[y, x]]
            for y in rng.choice(ys, size=min(len(ys), max(1, beads // n + 1)), replace=False):
                mark[y, x] = True
    sp.mark = mark


def op_mushroom(sp, rng, spots=7):
    """Big dome cap on a thin stem; several spots are the glowing organs."""
    a = np.zeros((16, 16), bool)
    for y in range(2, 8):
        hw = 7.6 * np.sqrt(max(0.0, 1 - ((7.4 - y) / 5.6) ** 2)) + rng.uniform(-0.4, 0.3)
        for x in range(16):
            if abs(x - 7.5) <= hw:
                a[y, x] = True
    stem = np.zeros((16, 16), bool)
    x = 7
    for y in range(8, 16):
        if rng.random() < 0.2:
            x = int(np.clip(x + rng.choice([-1, 1]), 6, 8))
        stem[y, x] = stem[y, x + 1] = True
    a |= stem
    sp.a, sp.stem = a, stem
    cap = list(zip(*np.nonzero(a & ~stem)))
    rng.shuffle(cap)
    mark = np.zeros((16, 16), bool)
    for (y, x) in cap:
        if mark.sum() >= spots:
            break
        if 3 <= y <= 6 and not mark[max(0, y - 2):y + 3, max(0, x - 2):x + 3].any():
            mark[y, x] = True
    sp.mark = mark


def op_basket(sp, rng, marks=0):
    """A cup of pale glass lace: tapered trapezoid, solid rim, dense diamond lattice."""
    a = np.zeros((16, 16), bool)
    sp.tfix = {}
    rim = []
    for y in range(3, 15):
        hw = 7.0 - (y - 3) * 0.33
        for x in range(16):
            d = abs(x - 7.5)
            if d > hw:
                continue
            edge = d > hw - 1
            if y in (3, 14) or edge or ((x + y) % 2 == 0 and rng.random() > 0.12) or (x - y) % 4 == 0:
                a[y, x] = True
                sp.tfix[(y, x)] = 0.95 if y == 3 else float(np.clip(0.55 + rng.normal(0, .1) + (0.15 if edge else 0), 0.2, 0.9))
                if y == 3:
                    rim.append((y, x))
    sp.a = a
    mark = np.zeros((16, 16), bool)
    rng.shuffle(rim)
    for (y, x) in rim[:marks]:
        mark[y, x] = True
    sp.mark = mark


def op_gourd(sp, rng, cx, cy, rx, ry):
    """Rounded gourd with a centre rib and a short stalk."""
    a = np.zeros((16, 16), bool)
    for y in range(16):
        for x in range(16):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1 + rng.uniform(-0.12, 0.12):
                a[y, x] = True
    sp.tfix = {}
    xc = int(round(cx))
    for y in np.nonzero(a.any(1))[0]:
        if a[y, xc]:
            sp.rgb[y, xc] *= 0.5
    top = int(np.nonzero(a.any(1))[0].min())
    for i, xx in enumerate((xc, xc + 1)):
        yy = top - 1 - i
        if yy >= 0:
            a[yy, xx] = True
            sp.tfix[(yy, xx)] = 0.1
    sp.a = a


OPS = {"bloom": op_bloom, "roots": op_roots, "mushroom": op_mushroom, "basket": op_basket, "gourd": op_gourd, "flipx": op_flipx, "flipy": op_flipy, "transpose": op_transpose, "ground": op_ground, "wobble": op_wobble,
       "trim": op_trim, "erode": op_erode, "fold": op_fold, "crop": op_crop, "cover": op_cover, "hole": op_hole}


# ------------------------------------------------------------------ classifiers (on the moved vanilla pixels)
def _hsv(sp):
    h = np.zeros((16, 16)); s = np.zeros((16, 16)); v = np.zeros((16, 16))
    for y in range(16):
        for x in range(16):
            h[y, x], s[y, x], v[y, x] = colorsys.rgb_to_hsv(*sp.rgb[y, x])
    return h, s, v


def classify(kind: str, sp: Sp, t: np.ndarray, rng) -> np.ndarray:
    h, s, v = _hsv(sp)
    a = sp.a
    if kind == "*":
        return a.copy()
    if kind == "mark":
        return a & getattr(sp, "mark", np.zeros((16, 16), bool))
    if kind == "stem":
        return a & getattr(sp, "stem", np.zeros((16, 16), bool))
    if kind == "nongreen":                       # flower heads: anything with colour that is not leaf green
        return a & (s > 0.25) & ((h < 0.17) | (h > 0.47))
    if kind == "orange":
        return a & (h > 0.02) & (h < 0.16) & (s > 0.5) & (v > 0.7)
    if kind == "red":
        return a & ((h < 0.05) | (h > 0.93)) & (s > 0.5)
    if kind == "bright":
        return a & (t > 0.68)
    if kind == "white":
        return a & (s < 0.25) & (v > 0.8)
    if kind == "spots_cap":                      # white speckles inside the red cap's rows
        red = a & ((h < 0.05) | (h > 0.93)) & (s > 0.5)
        rows = np.nonzero(red.any(1))[0]
        m = a & (s < 0.3) & (v > 0.8)
        if len(rows):
            m[:rows.min()] = False
            m[rows.max() + 1:] = False
        return m
    if kind.startswith("tips"):                  # free ends of strands: top-most (tips) or bottom-most (tipsb) pixel
        p = float(kind[4:].lstrip("b") or 0.85) if kind[4:].lstrip("b") else 0.85
        m = np.zeros_like(a)
        for x in range(16):
            ys = np.nonzero(a[:, x])[0]
            if len(ys) and rng.random() < p:
                m[ys[-1] if kind.startswith("tipsb") else ys[0], x] = True
        return m
    if kind.startswith("spots"):                 # a few isolated organs (bladders, resin beads, embers)
        n = int(kind[5:])
        cand = list(zip(*np.nonzero(a & (t > 0.3))))
        m = np.zeros_like(a)
        rng.shuffle(cand)
        for (y, x) in cand:
            if m.sum() >= n:
                break
            if not m[max(0, y - 2):y + 3, max(0, x - 2):x + 3].any():
                m[y, x] = True
        return m
    raise ValueError(kind)


# ------------------------------------------------------------------ deep-sea toning (non-emissive tones only)
def _to_oklab(c):
    r = np.array([v / 255.0 for v in c])
    r = np.where(r <= 0.04045, r / 12.92, ((r + 0.055) / 1.055) ** 2.4)
    l = 0.4122214708 * r[0] + 0.5363325363 * r[1] + 0.0514459929 * r[2]
    m = 0.2119034982 * r[0] + 0.6806995451 * r[1] + 0.1073969566 * r[2]
    s = 0.0883024619 * r[0] + 0.2817188376 * r[1] + 0.6299787005 * r[2]
    l, m, s = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return np.array([0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                     1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                     0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s])


def _from_oklab(L, a, b):
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = np.array([4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                  -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                  -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s])
    r = np.clip(r, 0, 1)
    r = np.where(r <= 0.0031308, r * 12.92, 1.055 * r ** (1 / 2.4) - 0.055)
    return tuple(int(round(v * 255)) for v in np.clip(r, 0, 1))


def tone(c):
    """Deep-sea mood: soft-compress lightness above 0.42 (hard cap 0.62), cut chroma 30%, keep the hue."""
    L, a, b = _to_oklab(c)
    if L > 0.42:
        L = 0.42 + (L - 0.42) * 0.45
    L = min(L, 0.62)
    return _from_oklab(L, a * 0.7, b * 0.7)


# ------------------------------------------------------------------ one texture
def paint(src: np.ndarray, layers, ops, seed, rim=True, jit=0.06, contrast=0.7):
    """-> (RGBA image array, glow RGBA array or None).  layers: [(class, ramp, glow_min_level or None), ...]."""
    rng = np.random.default_rng(seed)
    sp = Sp(src)
    for o in ops:
        name, *args = o if isinstance(o, tuple) else (o,)
        OPS[name](sp, rng, *args)
    a = sp.a
    lum = sp.rgb @ np.array([0.299, 0.587, 0.114])
    if a.any():
        lo, hi = np.percentile(lum[a], [3, 97])
    else:
        lo, hi = 0, 1
    t = np.clip((lum - lo) / max(hi - lo, 1e-3), 0, 1)
    t = 0.44 + (t - 0.5) * contrast          # softer than vanilla: fewer harsh speckles
    out = np.zeros((16, 16, 4), np.uint8)
    glow = np.zeros((16, 16, 4), np.uint8)
    taken = np.zeros_like(a)
    pad = np.pad(a, 1)
    noise = rng.normal(0, jit, (16, 16))
    coin = rng.random((16, 16))
    hole = getattr(sp, "hole", set())
    tfix = getattr(sp, 'tfix', {})
    for (y, x), val in tfix.items():
        t[y, x] = val
    for kind, ramp, gmin in layers:
        ramp = [c if (gmin is not None and i >= gmin) else tone(c) for i, c in enumerate(ramp)]
        m = classify(kind, sp, t, rng) & ~taken
        taken |= m
        n = len(ramp)
        idx = np.floor(np.clip(t + noise, 0, 0.999) * n).astype(int)
        for y, x in zip(*np.nonzero(m)):
            i = idx[y, x]
            if rim and (y, x) not in tfix:
                if not pad[y + 1, x] and coin[y, x] < 0.4:       # left / above lit
                    i += 1
                elif not pad[y + 1, x + 2] and coin[y, x] < 0.4:  # right unlit
                    i -= 1
            i = int(np.clip(i, 0, n - 1))
            if (y, x) in hole:
                i = 0
            out[y, x] = (*ramp[i], 255)
            if gmin is not None and i >= gmin:
                glow[y, x] = out[y, x]
    return out, (glow if glow[..., 3].any() else None)


def sway(img: np.ndarray, frames: int, hang: bool, amp: float = 1.0):
    h = img.shape[0]
    res = []
    for k in range(frames):
        ph = np.sin(2 * np.pi * k / frames)
        f = np.zeros_like(img)
        for y in range(h):
            tt = (y / (h - 1)) if hang else (1 - y / (h - 1))
            dx = int(round(amp * ph * tt))
            f[y] = np.roll(img[y], dx, axis=0)
            if dx > 0:
                f[y, :dx] = 0
            elif dx < 0:
                f[y, dx:] = 0
        res.append(f)
    return res


# ------------------------------------------------------------------ the table
# name: dict(src, layers=[(class, ramp, glowMinLevel|None)], ops, seed, anim=(frames, hang))
T: dict[str, dict] = {}


def add(name, src, layers, ops=(), seed=None, anim=None, contrast=0.7):
    T[name] = dict(src=src, layers=layers, ops=list(ops), anim=anim, contrast=contrast,
                   seed=seed if seed is not None else 5100 + sum(map(ord, name)))


def body(*r):  # single-ramp layer list
    return [("*", R(*r), None)]


# --- flowers: coloured heads glow, leaf-green stems become dark stems
def flower(name, src, petal, stem, ops=(), gmin=1, glow=True):
    add(name, src, [("mark", R(*petal), 1), ("*", R(*stem), None)], [("bloom",)] + list(ops), contrast=0.7)


STEM_TEAL = ("#0a1a24", "#123044", "#1f4e5e")
STEM_GREEN = ("#0d281c", "#17432f", "#2c6a48")
flower("abyssal_bloom", "cornflower", ("#1c4a78", "#3a86c4", "#a8e0ff"), STEM_TEAL)
flower("cave_bloom", "blue_orchid", ("#1a5a8a", "#48b0e8", "#c0f4ff"), STEM_TEAL, [("flipx",)])
flower("floating_bloom", "azure_bluet", ("#204a80", "#4a90c8", "#b0e0ff"), ("#0c1c2c", "#162e48", "#264c68"), [("flipx",)])
flower("hadal_bloom", "allium", ("#3a1a68", "#8a58d8", "#dcb8ff"), ("#140a22", "#241640", "#3a2a60"))
flower("cave_crystal_plant", "lily_of_the_valley", ("#0a5a70", "#2aa8c2", "#b0f8ff"), STEM_GREEN, [("flipx",)])
flower("crystal_plant", "spore_blossom", ("#0e6a70", "#30c0c0", "#c0fff0"), STEM_GREEN)

# --- corals / sponges
add("sponge_plant", "brain_coral", body("#5a3410", "#a86a20", "#e0a848"), [("erode", .12)])
add("cave_sponge", "horn_coral", body("#4a3c10", "#94802a", "#d0c060"), [("flipx",), ("erode", .12)])
add("glow_coral", "fire_coral", [("*", R("#5a4a08", "#a08a1a", "#f0e070"), 2)], [("erode", .1)])
add("cave_coral", "fire_coral", body("#5a1e0c", "#9a4a1c", "#e08a3a"), [("flipx",), ("erode", .14)])
add("soul_coral", "tube_coral", [("*", R("#5a7a88", "#a0c0cc", "#e8fbff"), 3)], [("erode", .12)])
add("black_coral", "fire_coral", [("spots2", R("#3a2470", "#7a50c0", "#b898ff"), None), ("*", R("#0c0c12", "#22222c", "#4a4a5a"), None)],
    [("erode", .12), ("flipx",)])
add("glow_anemone", "warped_roots", [("bright", R("#c04888", "#f070b8", "#ffc0e8"), 1), ("*", R("#3a0c26", "#7a2050", "#b04078"), None)],
    [("wobble", .5)])

# --- single-sprite grasses
add("glowtip_grass", "grass", [("tips0.9", R("#3ac0c8", "#7ae8ee", "#d0ffff"), 1), ("*", R("#0c2c34", "#1a5a62", "#3a969a"), None)],
    [("trim", 2), ("erode", .1)])
add("vent_grass", "grass", body("#2a2808", "#5e5a14", "#9a9438"), [("flipx",), ("trim", 3), ("erode", .1)])
add("thermal_plant", "grass", [("tips0.85", R("#a84a10", "#e07820", "#ffc850"), 1), ("*", R("#101c10", "#1f4224", "#3a7038"), None)],
    [("trim", 4), ("erode", .1)])
add("strandweed", "grass", body("#16281c", "#2e4a30", "#587a4c"), [("flipx",), ("trim", 3), ("erode", .1)], seed=901)
add("strandweed_ripe", "grass", [("tips0.9", R("#a8c8b0", "#d8f0e0", "#f4fff8"), None), ("*", R("#16281c", "#2e4a30", "#587a4c"), None)],
    [("flipx",), ("trim", 3), ("erode", .1)], seed=901)
add("sea_fern", "fern", body("#0f3020", "#28684a", "#5aa878"), [("erode", .1)])
add("cave_fern", "fern", body("#0d281a", "#215a3f", "#4a9868"), [("flipx",), ("erode", .12)])
add("wall_fern", "large_fern_top", body("#0d281a", "#215a3f", "#4a9868"), [("erode", .12)])
add("fallen_kelp", "kelp_plant", body("#122a16", "#2e5a30", "#5a9a4c"), [("transpose",), ("ground",), ("erode", .16)])

# --- stacking plants: stalk tile + top tile
def stack(name, stalk_src, top_src, ramp, stalk_ops=(), top_ops=(), anim=None, tip="_top", top_layers=None,
          stalk_layers=None, glow_tip=False, seed=None):
    s = seed if seed is not None else 3100 + sum(map(ord, name))
    add(name, stalk_src, stalk_layers or body(*ramp), [*stalk_ops], seed=s, anim=anim and (anim[0], anim[1]))
    add(name + tip, top_src, top_layers or body(*ramp), [*top_ops], seed=s + 1, anim=anim and (anim[0], anim[1]))


def grass(name, ramp, anim=None):
    stack(name, "tall_seagrass_bottom", "tall_seagrass_top", ramp, [("erode", .05, True)], [("trim", 4), ("erode", .08)], anim)


grass("abyssal_grass", ("#10302a", "#22583f", "#4a9560"))
grass("teal_abyssal_grass", ("#0b3038", "#1a5a62", "#3f9aa0"))
grass("violet_abyssal_grass", ("#1c1440", "#3c2a70", "#7a5cb8"))
grass("ashen_abyssal_grass", ("#22282a", "#414d46", "#7e8a80"))
grass("cave_grass", ("#0d271b", "#1b452f", "#3b7a4c"), anim=(4, False))

stack("deep_kelp", "kelp_plant", "kelp", ("#123018", "#28582c", "#4f8f45"), [("wobble", .6)], [("erode", .08)])
stack("cave_kelp", "kelp_plant", "kelp", ("#0f2c1a", "#1e4a28", "#438a50"), [("flipx",), ("wobble", .6)], [("flipx",), ("erode", .08)], anim=(4, False))
stack("giant_kelp", "kelp_plant", "kelp", ("#22220c", "#464214", "#7a7228"), [("wobble", .5)], [("erode", .08)],
      stalk_layers=[("spots3", R("#a86a10", "#d8a030", "#ffe080"), None), ("*", R("#22220c", "#464214", "#7a7228"), None)],
      top_layers=[("spots2", R("#a86a10", "#d8a030", "#ffe080"), None), ("*", R("#22220c", "#464214", "#7a7228"), None)])
stack("giant_cave_kelp", "kelp_plant", "kelp", ("#1c220c", "#343c18", "#5e6e2c"), [("flipx",), ("wobble", .5)], [("flipx",), ("erode", .08)],
      anim=(4, False),
      stalk_layers=[("spots3", R("#a86a10", "#d8a030", "#ffe080"), None), ("*", R("#1c220c", "#343c18", "#5e6e2c"), None)],
      top_layers=[("spots2", R("#a86a10", "#d8a030", "#ffe080"), None), ("*", R("#1c220c", "#343c18", "#5e6e2c"), None)])
CK = R("#083c48", "#146a7a", "#4ec0cc")
stack("crystal_kelp", "kelp_plant", "kelp", ("#083c48", "#146a7a", "#4ec0cc"), [("wobble", .6)], [("erode", .05)],
      top_layers=[("tips0.9", R("#5ad8e8", "#a0f4ff", "#e8ffff"), 1), ("spots2", R("#5ad8e8", "#a0f4ff", "#e8ffff"), 1), ("*", CK, None)])
add("void_kelp_plant", "kelp_plant", [("spots2", R("#5a3ca0", "#9a6cff", "#d8c0ff"), None), ("*", R("#120a22", "#28183c", "#5a3e8a"), None)],
    [("flipx",), ("wobble", .5)], seed=3777)
add("void_kelp", "kelp", [("tips0.9", R("#5a3ca0", "#9a6cff", "#d8c0ff"), None), ("*", R("#120a22", "#28183c", "#5a3e8a"), None)],
    [("flipx",), ("erode", .08)], seed=3778)
stack("hanging_kelp", "kelp_plant", "kelp", ("#0f2c1a", "#1e4a28", "#438a50"), [("flipy",), ("wobble", .6)], [("flipy",), ("erode", .08)],
      anim=(4, True), tip="_tip")

# hanging vines
LEAF = R("#0d2a16", "#245033", "#4a8a55")
BERRY = R("#20a888", "#6af0d0", "#d0fff0")
add("cave_vine", "cave_vines_plant_lit", [("orange", BERRY, None), ("*", LEAF, None)], [("wobble", .6)], seed=3211)
add("cave_vine_tip", "cave_vines_lit", [("orange", BERRY, 1), ("*", LEAF, None)], [("erode", .08)], seed=3212)
AV = R("#140a2a", "#382462", "#6a48a8")
AVG = R("#20a0a8", "#58e0e0", "#c8ffff")
add("abyssal_vine", "weeping_vines_plant", [("orange", AVG, None), ("*", AV, None)], [("wobble", .7)], seed=3311)
add("abyssal_vine_tip", "weeping_vines", [("orange", AVG, 1), ("tips0.8", AVG, 1), ("*", AV, None)], [("erode", .06)], seed=3312)
ROOT = ("#22150d", "#4a3122", "#7c5a3c")
add("cave_root", "hanging_roots", body(*ROOT), [("roots", 4)], seed=3411)
add("cave_root_tip", "hanging_roots", body(*ROOT), [("roots", 4, True)], seed=3412)
add("deep_root", "hanging_roots", body(*ROOT), [("roots", 3, False, 0, False)], seed=3421)
add("deep_root_tip", "hanging_roots", body(*ROOT), [("roots", 3, True, 0, False)], seed=3422)
RES = R("#a86a10", "#e0a020", "#ffe070")
add("resin_root", "hanging_roots", [("mark", RES, None), ("*", R("#2c1c10", "#5a3c24", "#8a6440"), None)], [("roots", 4, False, 5)], seed=3431)
add("resin_root_tip", "hanging_roots", [("mark", RES, None), ("*", R("#2c1c10", "#5a3c24", "#8a6440"), None)], [("roots", 4, True, 4)], seed=3432)
MV = R("#3a200c", "#9a622a", "#d89a50")
EMB = R("#c04a10", "#f08020", "#ffd060")
add("mineral_vine", "twisting_vines_plant", [("spots3", EMB, None), ("*", MV, None)], [("wobble", .5)], seed=3511)
add("mineral_vine_top", "twisting_vines", [("tips0.8", EMB, None), ("*", MV, None)], [("erode", .06)], seed=3512)
add("wall_mineral_vine", "vine", [("spots3", EMB, None), ("*", MV, None)], [("erode", .12)], seed=3513)

# --- carpets: moss films cut to organic patches
add("abyssal_moss", "moss_block", body("#0f2e1c", "#285234", "#4a8250"), [("cover", .72, 4)])
add("cave_moss", "moss_block", body("#0c2818", "#20462e", "#3c7a4c"), [("flipx",), ("cover", .6, 5)])
add("heat_moss", "moss_block", [("spots5", R("#7a8c20", "#9aa83a", "#c8d860"), None), ("*", R("#3a1206", "#7a2c10", "#c05a20"), None)],
    [("cover", .75, 4)])
add("luminous_moss", "glow_lichen", [("bright", R("#3a9ab0", "#6ad0e0", "#c8f8ff"), 0), ("*", R("#12303c", "#24596a", "#3f8ea0"), None)],
    [("cover", .5, 4)])

# --- tubes (vanilla sugar cane: segmented stalks)
def tube(name, ramp, rim=None, glow=False):
    add(name, "sugar_cane", body(*ramp), [("wobble", .5)], contrast=.45)
    layers = ([("tips0.95", R(*rim), 1 if glow else None)] if rim else []) + [("*", R(*ramp), None)]
    add(name + "_top", "sugar_cane", layers, [("trim", 3), ("erode", .1)], contrast=.45)


tube("tube_plant", ("#3a1220", "#78283a", "#a84a62"), ("#e07890", "#f0a0b0", "#ffd0d8"))
tube("cave_tube_plant", ("#301028", "#682050", "#984078"), ("#d870a8", "#f0a0d0", "#ffd8ee"))
tube("thermal_tube", ("#2a0c08", "#6a2018", "#b0482c"), ("#c05a10", "#f09030", "#ffd070"), glow=True)
add("knotstalk", "sugar_cane", body("#6a6658", "#b0ac98", "#ece8d8"), [("flipx",), ("wobble", .6)], seed=6101)
add("knotstalk_top", "sugar_cane", body("#6a6658", "#b0ac98", "#ece8d8"), [("flipx",), ("trim", 4), ("erode", .14)], seed=6102)
add("cinder_stalk", "weeping_vines_plant", [("orange", R("#c04a10", "#f08020", "#ffd060"), None), ("*", R("#0c0808", "#2a1a16", "#5a3a30"), None)],
    [("flipy",), ("wobble", .6), ("erode", .1)], seed=6201)

# --- giant tube block (opaque) and the ancient plant blocks
add("giant_tube", "chorus_plant", body("#241020", "#4a1c3c", "#7a3462"), [], seed=6301, contrast=0.45)
add("giant_tube_top", "chorus_plant", body("#241020", "#4a1c3c", "#7a3462"), [("hole", 4.6)], seed=6302, contrast=0.45)
add("ancient_cave_plant", "dried_kelp_side", body("#0a2a30", "#1a4a52", "#3a8088"), [], seed=6401)
add("ancient_cave_plant_top", "warped_fungus", body("#0a2a30", "#1a4a52", "#3a8088"), [("erode", .08)], seed=6402)
add("ancient_frond", "spore_blossom_base", body("#0f3a22", "#24703c", "#56b060"), [], seed=6403)
add("ancient_root", "mangrove_roots_side", body("#160e08", "#3a2416", "#6e4a2e"), [], seed=6404)

# --- mushroom
add("abyssal_mushroom", "moss_block",
    [("mark", R("#a0f0ff", "#d8fbff", "#ffffff", 3), 0), ("stem", R("#3a4048", "#6a747c", "#98a4aa"), None),
     ("*", R("#0c3a48", "#1e7a8a", "#58c0c8"), None)], [("mushroom", 7)], seed=6501)

# --- resource plants (base + ripe pair share seed and silhouette)
AMB = ("#2a1408", "#5a3016", "#8a5a30")
add("amber_fan", "dead_bush", body(*AMB), [("erode", .08)], seed=6601)
add("amber_fan_ripe", "dead_bush", [("tips0.95", R("#c06a10", "#f0a020", "#ffe070"), None), ("*", R(*AMB), None)], [("erode", .08)], seed=6601)
LQ = ("#180c2a", "#2e1e50", "#5a4890")
add("lumen_quill", "wheat_stage7", body(*LQ), [("trim", 3), ("erode", .06)], seed=6701)
add("lumen_quill_ripe", "wheat_stage7", [("tips0.9", R("#5ab0d0", "#a8e8ff", "#f0ffff"), 0), ("*", R(*LQ), None)], [("trim", 3), ("erode", .06)], seed=6701)
OB = ("#1c1c0a", "#403c16", "#7a7430")
add("oil_bladder_weed", "big_dripleaf_stem", body(*OB), [("erode", .08)], seed=6801)
add("oil_bladder_weed_ripe", "big_dripleaf_stem", [("spots5", R("#a08018", "#e0c040", "#fff090"), None), ("*", R(*OB), None)], [("erode", .08)], seed=6801)
add("pressure_gourd", "moss_block", body("#2a2740", "#454064", "#6a6494"), [("gourd", 7.5, 11.2, 4.6, 4.1)], seed=6901)
add("pressure_gourd_ripe", "moss_block", body("#3a3648", "#7c7894", "#c8c4dc"), [("gourd", 7.5, 9.5, 5.6, 5.4)], seed=6901)
add("silt_comb", "tube_coral_fan", body("#3a2e22", "#7a6850", "#b8a482"), [("erode", .1)], seed=7001)
GL = ("#56666e", "#94b0b8", "#d8eaec")
add("glasslace", "cobweb", body(*GL), [("basket",)], seed=7101)
add("glasslace_ripe", "cobweb", [("mark", R("#3ac0d8", "#8af0ff", "#e0ffff"), 0), ("*", R(*GL), None)], [("basket", 5)], seed=7101)


# ------------------------------------------------------------------ output
def _save_strip(frames, path, frametime=12):
    w, h = frames[0].shape[1], frames[0].shape[0]
    strip = Image.new("RGBA", (w, h * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(Image.fromarray(f, "RGBA"), (0, i * h))
    strip.save(path)
    with open(path + ".mcmeta", "w", encoding="utf-8") as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, fh, indent=2)
        fh.write("\n")


def run(quiet: bool = False, only: set | None = None, out: str | None = None) -> list[str]:
    jar = _jar()
    if not jar:
        if not quiet:
            print("redo_flora: no vanilla client jar found, skipped")
        return []
    z = zipfile.ZipFile(jar)
    out = out or BLOCK
    os.makedirs(out, exist_ok=True)
    done = []
    for name, sp in T.items():
        if only and name not in only:
            continue
        path = os.path.join(out, name + ".png")
        if not texture_locks.wants(path):
            continue                                   # kept as committed / locked
        img, glow = paint(_van(z, sp["src"]), sp["layers"], sp["ops"], sp["seed"], contrast=sp["contrast"])
        gpath = os.path.join(out, name + "_glow.png")
        if sp["anim"]:
            n, hang = sp["anim"]
            _save_strip(sway(img, n, hang), path)
        else:
            Image.fromarray(img, "RGBA").save(path)
            if os.path.exists(path + ".mcmeta"):
                os.remove(path + ".mcmeta")
        done.append(name)
        if glow is not None and texture_locks.save(glow, gpath):   # skipped inside the assets (derived)
            done.append(name + "_glow")
    if not quiet:
        print(f"redo_flora: {len(done)} plant textures written")
    return done


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--out")
    ap.add_argument("--textures", choices=texture_locks.MODES, default=texture_locks.MODE)
    a = ap.parse_args()
    texture_locks.set_mode(a.textures)
    run(only=set(a.only.split(",")) if a.only else None, out=a.out)
