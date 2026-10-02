"""Worn-armor layer textures (64x32 ``<material>_layer_1.png`` / ``_layer_2.png``) for the four Abyssia armor materials.

Each material = a vanilla layer as shading base (read from the 1.20.1 client jar in the Gradle cache, never extracted
into the repo), a dark->light colour ramp the base luminance is mapped onto (so vanilla bevels survive), optional
zones (a rect recoloured from another base/ramp) and pixel-rect detail ops on the vanilla UV faces.

    python tools/armor_layers.py                       # write every material (and refresh the texture locks)
    python tools/armor_layers.py --material entry_diving
    python tools/armor_layers.py --dry-run             # report only, write nothing
    python tools/armor_layers.py --preview out.png     # all layers x6 on grey (also written without --dry-run)
    python tools/armor_layers.py --no-lock             # do not copy the results into tools/texture_locks

layer_1 = helmet / chestplate / boots, layer_2 = leggings.  Vanilla UV (64x32): head cube (0,0) 8x8x8 (front 8,8),
body (16,16) 8x12x4 (front 20,20 / back 32,20), arms (40,16) (front 44,20), legs (0,16) (front 4,20; boots = rows 26-31
on layer_1; leggings use rows 20-28 and the waist rows 27-31 of the body on layer_2).
"""
from __future__ import annotations

import argparse
import glob
import io
import os
import shutil
import sys
import zipfile

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(ROOT, "..", "src", "main", "resources", "assets", "abyssia", "textures", "models", "armor"))
LOCK = os.path.join(ROOT, "texture_locks", "assets", "textures", "models", "armor")
GRADLE = os.path.join(os.path.expanduser("~"), ".gradle", "caches")
VANILLA = "assets/minecraft/textures/models/armor/{}_layer_{}.png"

# --------------------------------------------------------------------------- faces (x, y, w, h) on the 64x32 sheet
FACES = {
    "hf": (8, 8, 8, 8), "hr": (0, 8, 8, 8), "hl": (16, 8, 8, 8), "hb": (24, 8, 8, 8), "ht": (8, 0, 8, 8),
    "bf": (20, 20, 8, 12), "bb": (32, 20, 8, 12), "br": (16, 20, 4, 12), "bl": (28, 20, 4, 12),
    "af": (44, 20, 4, 12), "ab": (52, 20, 4, 12), "ao": (40, 20, 4, 12), "ai": (48, 20, 4, 12),
    "lf": (4, 20, 4, 12), "lb": (12, 20, 4, 12), "lo": (0, 20, 4, 12), "li": (8, 20, 4, 12),
}


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# --------------------------------------------------------------------------- op helpers (face-local coordinates)
def R(face, x, y, w, h, col):
    return ("rect", face, x, y, w, h, col)


def P(face, x, y, col):
    return ("rect", face, x, y, 1, 1, col)


def D(face, cx, cy, r, col):
    return ("disc", face, cx, cy, r, col)


def both(faces, *ops_args):
    """Repeat a (fn, *args-after-face) op over several faces."""
    fn, rest = ops_args[0], ops_args[1:]
    return [fn(f, *rest) for f in faces]


# --------------------------------------------------------------------------- material data
LEFT_RIGHT = ("hr", "hl")

MATERIALS = {
    # deep navy/teal metal, old brass-style diving helmet, flippers on the boots
    "abyssal_alloy": {
        "layer_1": {
            "base": "iron", "ramp": ["#1E2E3A", "#2C4655", "#427686", "#5FA8B0"],
            "fills": [(0, 8, 32, 8)],
            "zones": [((0, 26, 16, 6), "iron", ["#121C24", "#1E3340", "#2F5663", "#4A8A92"])],
            "ops": [
                # visor: brass-dark frame, glass disc, glints
                D("hf", 4, 4, 3.7, "#10181E"), D("hf", 4, 4, 2.8, "#78B9C5"),
                P("hf", 2, 2, "#B7D6D8"), P("hf", 3, 2, "#B7D6D8"), P("hf", 2, 3, "#B7D6D8"),
                P("hf", 5, 5, "#4E8F9C"), P("hf", 4, 5, "#5EA0AC"), P("hf", 5, 4, "#5EA0AC"),
                # rivets around the visor and brass collar
                *[P("hf", x, y, "#C09A52") for x, y in ((0, 1), (7, 1), (0, 6), (7, 6), (3, 0), (4, 0))],
                R("hf", 0, 7, 8, 1, "#8A6A32"), R("hr", 0, 7, 8, 1, "#8A6A32"), R("hl", 0, 7, 8, 1, "#8A6A32"),
                R("hb", 0, 7, 8, 1, "#8A6A32"),
                # side portholes
                *both(LEFT_RIGHT, R, 2, 2, 4, 4, "#10181E"), *both(LEFT_RIGHT, R, 3, 3, 2, 2, "#78B9C5"),
                *both(LEFT_RIGHT, P, 3, 3, "#B7D6D8"),
                *both(LEFT_RIGHT, P, 1, 1, "#C09A52"), *both(LEFT_RIGHT, P, 6, 1, "#C09A52"),
                *both(LEFT_RIGHT, P, 1, 6, "#C09A52"), *both(LEFT_RIGHT, P, 6, 6, "#C09A52"),
                # back plate + top ridge
                R("hb", 2, 2, 4, 4, "#1A2832"), R("hb", 3, 3, 2, 2, "#2F5663"), P("hb", 1, 1, "#C09A52"),
                P("hb", 6, 1, "#C09A52"), P("hb", 1, 6, "#C09A52"), P("hb", 6, 6, "#C09A52"),
                R("ht", 3, 0, 2, 8, "#2F5663"), R("ht", 0, 3, 8, 2, "#2F5663"),
                # chest/arm seams so the (unused) rest still matches
                R("bf", 3, 0, 2, 9, "#1E3340"), R("bb", 3, 0, 2, 9, "#1E3340"),
                # flippers: teal fin edge on the lower leg front
                R("lf", 0, 9, 4, 1, "#5FA8B0"), P("lf", 0, 8, "#5FA8B0"), P("lf", 3, 8, "#5FA8B0"),
                P("lf", 1, 7, "#427686"), P("lf", 2, 7, "#427686"),
                R("lb", 0, 10, 4, 2, "#2F5663"), R("lo", 0, 9, 4, 3, "#2F5663"), R("li", 0, 9, 4, 3, "#2F5663"),
                R("lo", 0, 11, 4, 1, "#5FA8B0"), R("li", 0, 11, 4, 1, "#5FA8B0"),
            ],
        },
        "layer_2": {
            "base": "iron", "ramp": ["#1E2E3A", "#2C4655", "#427686", "#5FA8B0"], "fills": [], "zones": [],
            "ops": [R("bf", 0, 7, 8, 1, "#10181E"), R("bb", 0, 7, 8, 1, "#10181E"),
                    P("bf", 3, 7, "#C09A52"), P("bf", 4, 7, "#C09A52")],
        },
    },
    # slate suit, twin air tank on the back, knee pads on the leggings
    "diving_alloy": {
        "layer_1": {
            "base": "leather", "gain": 1.6, "ramp": ["#26323A", "#3C505B", "#55707C", "#6E8A96"],
            "fills": [(0, 8, 32, 8)],
            "zones": [],
            "ops": [
                # helmet part (unused piece, plain slate hood with a visor slit)
                R("hf", 1, 3, 6, 3, "#141C21"), R("hf", 2, 4, 4, 1, "#6E8A96"),
                # chest front: harness straps + buckle
                R("bf", 1, 0, 2, 9, "#161F24"), R("bf", 5, 0, 2, 9, "#161F24"),
                R("bf", 1, 0, 1, 9, "#2E3C44"), R("bf", 5, 0, 1, 9, "#2E3C44"),
                R("bf", 1, 3, 6, 1, "#161F24"), P("bf", 3, 3, "#B87333"), P("bf", 4, 3, "#B87333"),
                R("bf", 0, 8, 8, 1, "#1B262C"),
                # back: two cylinders with copper valves, straps
                R("bb", 0, 1, 3, 8, "#9AA6AC"), R("bb", 5, 1, 3, 8, "#9AA6AC"),
                R("bb", 0, 1, 1, 8, "#C4CDD0"), R("bb", 5, 1, 1, 8, "#C4CDD0"),
                R("bb", 2, 1, 1, 8, "#6E7A80"), R("bb", 7, 1, 1, 8, "#6E7A80"),
                R("bb", 0, 2, 3, 1, "#B0BABF"), R("bb", 5, 2, 3, 1, "#B0BABF"),
                R("bb", 0, 4, 3, 1, "#3A464C"), R("bb", 5, 4, 3, 1, "#3A464C"),
                R("bb", 0, 7, 3, 1, "#3A464C"), R("bb", 5, 7, 3, 1, "#3A464C"),
                R("bb", 1, 0, 6, 1, "#B87333"), P("bb", 1, 0, "#D9944F"), P("bb", 6, 0, "#D9944F"),
                R("bb", 3, 3, 2, 1, "#B87333"),
                # sides + arms: light cuffs
                R("br", 0, 0, 4, 1, "#6E8A96"), R("bl", 0, 0, 4, 1, "#6E8A96"),
                *both(("af", "ab", "ao", "ai"), R, 0, 0, 4, 1, "#55707C"),
                *both(("af", "ab", "ao", "ai"), R, 0, 4, 4, 1, "#161F24"),
                # boots: dark suit boots with a light sole line
                *both(("lf", "lb", "lo", "li"), R, 0, 11, 4, 1, "#161F24"),
                *both(("lf", "lb", "lo", "li"), R, 0, 9, 4, 1, "#55707C"),
            ],
        },
        "layer_2": {
            "base": "leather", "gain": 1.6, "ramp": ["#26323A", "#3C505B", "#55707C", "#6E8A96"],
            "fills": [], "zones": [],
            "ops": [
                # belt with copper buckle
                R("bf", 0, 7, 8, 2, "#161F24"), R("bb", 0, 7, 8, 2, "#161F24"),
                R("br", 0, 7, 4, 2, "#161F24"), R("bl", 0, 7, 4, 2, "#161F24"),
                R("bf", 3, 7, 2, 2, "#B87333"), P("bf", 3, 7, "#D9944F"),
                # knee pads
                *both(("lf", "lb"), R, 0, 4, 4, 3, "#161F24"), *both(("lf", "lb"), R, 0, 5, 4, 1, "#7A949F"),
                R("lf", 1, 4, 2, 3, "#7A949F"), R("lf", 1, 5, 2, 1, "#9AB3BD"),
                *both(("lo", "li"), R, 1, 4, 2, 3, "#6E8A96"),
                *both(("lf", "lb", "lo", "li"), R, 0, 0, 4, 1, "#161F24"),
            ],
        },
    },
    # heavy gunmetal with a purple-blue cast, small thick bolted visor
    "pressure_alloy": {
        "layer_1": {
            "base": "diamond", "ramp": ["#1A1C2A", "#2C3050", "#464C74", "#6A6F90"],
            "fills": [(0, 8, 32, 8)],
            "zones": [],
            "ops": [
                R("hf", 1, 2, 6, 4, "#0C0D16"), R("hf", 2, 3, 4, 2, "#5A6FB0"), P("hf", 2, 3, "#8FA3DC"),
                P("hf", 3, 3, "#8FA3DC"), R("hf", 4, 4, 2, 1, "#3C4C86"),
                *[P("hf", x, y, "#A3A8CC") for x, y in ((0, 1), (7, 1), (0, 6), (7, 6), (3, 1), (4, 1), (3, 6), (4, 6),
                                                         (1, 1), (6, 1), (1, 6), (6, 6))],
                R("hf", 0, 7, 8, 1, "#0C0D16"), R("hr", 0, 7, 8, 1, "#0C0D16"), R("hl", 0, 7, 8, 1, "#0C0D16"),
                R("hb", 0, 7, 8, 1, "#0C0D16"),
                *both(LEFT_RIGHT, R, 1, 1, 6, 5, "#2C3050"), *both(LEFT_RIGHT, R, 2, 3, 4, 1, "#161828"),
                *[P(f, x, y, "#A3A8CC") for f in LEFT_RIGHT for x, y in ((1, 1), (6, 1), (1, 5), (6, 5), (3, 2), (4, 2))],
                R("hb", 1, 1, 6, 5, "#2C3050"), R("hb", 3, 1, 2, 5, "#161828"),
                *[P("hb", x, y, "#A3A8CC") for x, y in ((1, 1), (6, 1), (1, 5), (6, 5))],
                R("ht", 0, 0, 8, 1, "#161828"), R("ht", 0, 7, 8, 1, "#161828"), R("ht", 3, 1, 2, 6, "#161828"),
                # chest: broad plates with bolts
                R("bf", 0, 4, 8, 1, "#0C0D16"), R("bf", 3, 0, 2, 4, "#161828"), R("bb", 0, 4, 8, 1, "#0C0D16"),
                *[P(f, x, y, "#A3A8CC") for f in ("bf", "bb") for x, y in ((1, 1), (6, 1), (1, 6), (6, 6))],
                *both(("af", "ab", "ao", "ai"), R, 0, 3, 4, 1, "#0C0D16"),
                *both(("af", "ao"), P, 1, 1, "#A3A8CC"),
                # boots: dark heavy
                *both(("lf", "lb", "lo", "li"), R, 0, 8, 4, 1, "#0C0D16"),
                *both(("lf", "lb"), P, 0, 10, "#A3A8CC"), *both(("lf", "lb"), P, 3, 10, "#A3A8CC"),
            ],
        },
        "layer_2": {
            "base": "diamond", "ramp": ["#1A1C2A", "#2C3050", "#464C74", "#6A6F90"], "fills": [], "zones": [],
            "ops": [
                R("bf", 0, 7, 8, 1, "#0C0D16"), R("bb", 0, 7, 8, 1, "#0C0D16"),
                *[P("bf", x, 8, "#A3A8CC") for x in (1, 3, 4, 6)],
                *both(("lf", "lb", "lo", "li"), R, 0, 4, 4, 3, "#161828"),
                *both(("lf", "lb", "lo", "li"), R, 1, 5, 2, 1, "#6A6F90"),
                *both(("lf", "lb"), P, 0, 4, "#A3A8CC"), *both(("lf", "lb"), P, 3, 4, "#A3A8CC"),
                *both(("lf", "lb"), P, 0, 6, "#A3A8CC"), *both(("lf", "lb"), P, 3, 6, "#A3A8CC"),
            ],
        },
    },
    # starter kit: iron helmet with a glass window, leather harness, dark suit leggings, teal flippers
    "entry_diving": {
        "layer_1": {
            "base": "iron", "ramp": ["#3F4648", "#6B7375", "#A4ACAE"],
            "fills": [(0, 8, 32, 8)],
            "zones": [
                ((16, 16, 24, 16), "leather", ["#1E2628", "#303B3D", "#4A5A5D"]),   # chest = dark suit
                ((40, 16, 16, 16), "leather", ["#1E2628", "#303B3D", "#4A5A5D"]),   # arms
                ((0, 26, 16, 6), "leather", ["#2A3C3E", "#40595B", "#5D7C7E"]),     # flippers
            ],
            "ops": [
                # helmet: glass window, dark frame, leather trim
                R("hf", 1, 1, 6, 5, "#2B3032"), R("hf", 2, 2, 4, 3, "#8FC1CC"),
                R("hf", 2, 2, 2, 1, "#BFD9DD"), P("hf", 2, 3, "#BFD9DD"), R("hf", 4, 4, 2, 1, "#6FA3AF"),
                R("hf", 0, 7, 8, 1, "#8A4F32"), P("hf", 0, 0, "#8A4F32"), P("hf", 7, 0, "#8A4F32"),
                *both(LEFT_RIGHT, R, 0, 7, 8, 1, "#8A4F32"), R("hb", 0, 7, 8, 1, "#8A4F32"),
                *both(LEFT_RIGHT, R, 3, 0, 2, 7, "#8A4F32"), *both(LEFT_RIGHT, R, 3, 0, 1, 7, "#A5643F"),
                R("hb", 3, 0, 2, 7, "#8A4F32"), R("hb", 3, 0, 1, 7, "#A5643F"),
                R("ht", 3, 0, 2, 8, "#8A4F32"), R("ht", 3, 0, 1, 8, "#A5643F"),
                # chest front: leather harness (two straps, cross strap) + iron buckle
                R("bf", 1, 0, 2, 9, "#8A4F32"), R("bf", 5, 0, 2, 9, "#8A4F32"),
                R("bf", 1, 0, 1, 9, "#A5643F"), R("bf", 5, 0, 1, 9, "#A5643F"),
                R("bf", 1, 3, 6, 1, "#6C3D27"), P("bf", 3, 3, "#A4ACAE"), P("bf", 4, 3, "#A4ACAE"),
                R("bf", 0, 8, 8, 1, "#242C2E"),
                # chest back: one iron bottle, copper valve, leather bands
                R("bb", 2, 2, 4, 7, "#6B7375"), R("bb", 3, 1, 2, 1, "#8A9395"),
                R("bb", 2, 2, 1, 7, "#A4ACAE"), R("bb", 5, 2, 1, 7, "#4D5557"),
                R("bb", 3, 0, 2, 1, "#B87333"), P("bb", 3, 0, "#D9944F"),
                R("bb", 1, 3, 6, 1, "#8A4F32"), R("bb", 1, 6, 6, 1, "#8A4F32"),
                P("bb", 3, 3, "#A4ACAE"), P("bb", 3, 6, "#A4ACAE"),
                # arms: leather cuffs
                *both(("af", "ab", "ao", "ai"), R, 0, 4, 4, 1, "#70462F"),
                # flippers: leather straps + lighter tip
                *both(("lf", "lb", "lo", "li"), R, 0, 7, 4, 1, "#70462F"),
                *both(("lf", "lb", "lo", "li"), R, 0, 11, 4, 1, "#5D7C7E"),
                P("lf", 1, 6, "#A4ACAE"), P("lb", 1, 6, "#A4ACAE"),
                R("lf", 0, 10, 4, 1, "#243234"), R("lo", 0, 10, 4, 1, "#243234"), R("li", 0, 10, 4, 1, "#243234"),
            ],
        },
        "layer_2": {
            "base": "leather", "ramp": ["#1E2628", "#303B3D", "#4A5A5D"], "fills": [], "zones": [],
            "ops": [
                # belt: leather with iron buckle
                R("bf", 0, 7, 8, 2, "#70462F"), R("bb", 0, 7, 8, 2, "#70462F"),
                R("br", 0, 7, 4, 2, "#70462F"), R("bl", 0, 7, 4, 2, "#70462F"),
                R("bf", 0, 7, 8, 1, "#8A5A3C"), R("bf", 3, 7, 2, 2, "#A4ACAE"), R("bf", 4, 8, 1, 1, "#6B7375"),
                # knee patches
                *both(("lf",), R, 1, 5, 2, 2, "#70462F"), *both(("lf",), R, 1, 5, 2, 1, "#8A5A3C"),
                *both(("lf", "lb", "lo", "li"), R, 0, 0, 4, 1, "#1E2628"),
            ],
        },
    },
}

# --------------------------------------------------------------------------- vanilla access
def find_client_jar() -> str:
    pats = [os.path.join(GRADLE, "forge_gradle", "minecraft_repo", "versions", "1.20.1", "client*.jar"),
            os.path.join(GRADLE, "**", "1.20.1", "client*.jar")]
    for p in pats:
        for jar in sorted(glob.glob(p, recursive=True)):
            try:
                if VANILLA.format("iron", 1) in zipfile.ZipFile(jar).namelist():
                    return jar
            except zipfile.BadZipFile:
                pass
    raise SystemExit(f"error: no 1.20.1 client jar with {VANILLA.format('iron', 1)} under {GRADLE}\n"
                     f"  (run a Forge gradle task once so ForgeGradle downloads it)")


_cache: dict = {}


def vanilla(name: str, layer: int) -> Image.Image:
    key = (name, layer)
    if key not in _cache:
        with zipfile.ZipFile(find_client_jar()) as z:
            _cache[key] = Image.open(io.BytesIO(z.read(VANILLA.format(name, layer)))).convert("RGBA")
    return _cache[key]


def pixels(img):
    return list(getattr(img, 'get_flattened_data', img.getdata)())


def lum(px) -> float:
    return (0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]) / 255.0


def lum_range(img: Image.Image):
    vals = sorted(lum(p) for p in pixels(img) if p[3] > 0)
    return vals[int(len(vals) * 0.03)], vals[int(len(vals) * 0.97) - 1]


def ramp_at(ramp, t):
    t = min(1.0, max(0.0, t)) * (len(ramp) - 1)
    i = min(int(t), len(ramp) - 2)
    f = t - i
    a, b = hexc(ramp[i]), hexc(ramp[i + 1])
    return tuple(round(a[k] + (b[k] - a[k]) * f) for k in range(3))


# --------------------------------------------------------------------------- rendering
def recolour(out, mask, base, ramp, gain, rect):
    x0, y0, w, h = rect
    lo, hi = lum_range(base)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if not mask[x, y]:
                continue
            p = base.getpixel((x, y))
            if p[3] == 0:           # filled area the vanilla layer leaves open: borrow the pixel above
                yy = y
                while yy > 0 and base.getpixel((x, yy))[3] == 0:
                    yy -= 1
                p = base.getpixel((x, yy))
                if p[3] == 0:
                    p = (128, 128, 128, 255)
            t = (lum(p) - lo) / max(1e-6, hi - lo)
            t = 0.5 + (t - 0.5) * gain
            out.putpixel((x, y), ramp_at(ramp, t) + (255,))


def apply_ops(img, ops):
    dr = ImageDraw.Draw(img)
    for op in ops:
        kind, face, a, b, c, d, *rest = op if op[0] == "rect" else (op[0], op[1], op[2], op[3], op[4], op[5])
        fx, fy, fw, fh = FACES[face]
        if kind == "rect":
            col = hexc(rest[0]) + (255,)
            for y in range(b, b + d):
                for x in range(a, a + c):
                    if 0 <= x < fw and 0 <= y < fh:
                        img.putpixel((fx + x, fy + y), col)
        else:  # disc: a=cx, b=cy, c=r, d=colour
            col = hexc(d) + (255,)
            for y in range(fh):
                for x in range(fw):
                    if (x + 0.5 - a) ** 2 + (y + 0.5 - b) ** 2 <= c * c:
                        img.putpixel((fx + x, fy + y), col)


def build(spec: dict, layer: int) -> Image.Image:
    base = vanilla(spec["base"], layer)
    mask_src = vanilla("iron", layer)
    mask = Image.new("L", (64, 32), 0)
    for y in range(32):
        for x in range(64):
            if mask_src.getpixel((x, y))[3] > 0 or base.getpixel((x, y))[3] > 0 and spec["base"] != "chainmail":
                mask.putpixel((x, y), 255)
    for (x0, y0, w, h) in spec["fills"]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                mask.putpixel((x, y), 255)
    out = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    mpx = mask.load()
    recolour(out, mpx, base, spec["ramp"], spec.get("gain", 1.0), (0, 0, 64, 32))
    for rect, zbase, zramp in spec["zones"]:
        recolour(out, mpx, vanilla(zbase, layer), zramp, spec.get("gain", 1.0) if zbase == "leather" else 1.0, rect)
    apply_ops(out, spec["ops"])
    return out


def preview(images: dict, path: str, scale: int = 6):
    names = list(images)
    W, H = 64 * scale, 32 * scale
    sheet = Image.new("RGBA", (W * 2 + 30, (H + 24) * len(names) + 10), (96, 96, 96, 255))
    dr = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        y = 10 + i * (H + 24)
        dr.text((10, y), n, fill=(255, 255, 255, 255))
        for j, layer in enumerate((1, 2)):
            im = images[n][layer].resize((W, H), Image.NEAREST)
            sheet.paste(im, (10 + j * (W + 10), y + 12), im)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    sheet.save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--material", choices=sorted(MATERIALS))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--preview", metavar="OUT.png")
    ap.add_argument("--no-lock", action="store_true", help="do not copy into tools/texture_locks")
    a = ap.parse_args()
    mats = [a.material] if a.material else list(MATERIALS)
    images = {}
    for m in mats:
        images[m] = {}
        for layer in (1, 2):
            img = build(MATERIALS[m][f"layer_{layer}"], layer)
            images[m][layer] = img
            name = f"{m}_layer_{layer}.png"
            opaque = sum(1 for p in pixels(img) if p[3] > 0)
            print(f"{name}: {opaque} opaque px" + (" (dry-run)" if a.dry_run else ""))
            if not a.dry_run:
                os.makedirs(OUT, exist_ok=True)
                img.save(os.path.join(OUT, name))
                if not a.no_lock:
                    os.makedirs(LOCK, exist_ok=True)
                    shutil.copyfile(os.path.join(OUT, name), os.path.join(LOCK, name))
    if a.preview:
        preview(images, a.preview)
        print("preview:", a.preview)


if __name__ == "__main__":
    main()
