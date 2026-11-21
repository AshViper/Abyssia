#!/usr/bin/env python3
"""Skin and glow textures of the four abyss bosses (BOSS03 smooth mesh models, client/boss/BossMesh).

    python tools/boss_textures.py [--out DIR] [--only serpent|wyrm|kraken|mother] [--dry-run]

Writes <name>.png (cutout skin) and <name>_glow.png (additive glow, black = none) for abyss_serpent, trench_wyrm,
abyss_kraken, abyss_mother into src/main/resources/assets/abyssia/textures/entity/ (or --out). Deterministic. All four
share one 256x256 layout, the regions of BossMesh.java (keep both in step):

    SKIN     0,0    128x64   body wrap: u around (right side at 0, back at 1/4, left at 1/2, belly at 3/4), v tiles per joint
    SKIN2    128,0  128x64   second wrap (jaw / arm / tentacle), same convention
    HEAD     0,64   128x64   head or mantle wrap, v runs once along the loft
    MEMBRANE 128,64 64x64    fins and frills: u along the fin, v from the root (0) to the free edge (1), ragged alpha edge
    TOOTH 192,64  GLOW 208,64  EYE 224,64  FLESH 240,64  HORN 192,80   (16x16 swatches)
    INNER    0,128  128x64   mouth lining / under-bell wrap
"""
import argparse
import math
import os
import random

from PIL import Image

W = 256
SKIN, SKIN2, HEAD, INNER = (0, 0, 128, 64), (128, 0, 128, 64), (0, 64, 128, 64), (0, 128, 128, 64)
MEMBRANE = (128, 64, 64, 64)
TOOTH, GLOW, EYE, FLESH, HORN = (192, 64, 16, 16), (208, 64, 16, 16), (224, 64, 16, 16), (240, 64, 16, 16), (192, 80, 16, 16)


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


def hashf(*k):
    h = 0x9E3779B9
    for v in k:
        h = (h ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B & 0xFFFFFFFF
        h ^= h >> 13
    return (h & 0xFFFF) / 65535.0


def back(u):
    """1 on the back (u = 1/4), 0 on the belly (u = 3/4)."""
    return 0.5 + 0.5 * math.sin(u * math.tau)


class Sheet:
    def __init__(self, seed):
        self.skin = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (W, W), (0, 0, 0, 255))
        self.rnd = random.Random(seed)

    def fill(self, region, fn, glow=None):
        """fn(u, v, px, py) -> (r, g, b) or (r, g, b, a) or palette-less None (transparent); glow likewise or None."""
        x0, y0, w, h = region
        for py in range(h):
            for px in range(w):
                u, v = (px + 0.5) / w, (py + 0.5) / h
                c = fn(u, v, px, py)
                if c is not None:
                    self.skin.putpixel((x0 + px, y0 + py), c if len(c) == 4 else c + (255,))
                if glow is not None:
                    g = glow(u, v, px, py)
                    if g is not None:
                        self.glow.putpixel((x0 + px, y0 + py), g + (255,))


def ramp(pal, t, px, py, dither=0.06):
    """Palette colour for t in 0..1 (dark to light), ordered-dithered so bands break up into pixels."""
    t = t + (hashf(px, py, 7) - 0.5) * dither * 2
    i = max(0, min(len(pal) - 1, int(t * len(pal))))
    return pal[i]


def scales(u, v, px, py, cell=8):
    """Staggered scale rows: 0 at a scale's shaded lower rim, up to 1 on its crown."""
    row = py // cell
    sx = (px + (cell // 2 if row & 1 else 0)) % cell
    sy = py % cell
    d = math.hypot(sx - cell / 2 + 0.5, sy - cell * 0.35)
    return max(0.0, 1.0 - d / (cell * 0.62))


def membrane(pal, edge, rays=7):
    def fn(u, v, px, py):
        ragged = 0.80 + 0.16 * math.sin(u * math.tau * rays) * (0.5 + 0.5 * hashf(px // 3, 3))
        if v > ragged:
            return None
        ray = abs(((u * rays) % 1.0) - 0.5) < 0.09
        t = 0.55 - 0.35 * v + (0.25 if ray else 0.0)
        if v > ragged - 0.07:
            return edge
        return ramp(pal, t, px, py)
    return fn


def swatches(s, tooth, glow, flesh, horn):
    s.fill(TOOTH, lambda u, v, px, py: ramp(tooth, 0.25 + 0.7 * (1 - v) - 0.3 * abs(u - 0.5), px, py, 0.03))
    s.fill(GLOW, lambda u, v, px, py: glow, lambda u, v, px, py: glow)
    s.fill(FLESH, lambda u, v, px, py: ramp(flesh, 0.5 + 0.4 * math.sin(v * 9 + hashf(px, 1) * 3), px, py, 0.15))
    s.fill(HORN, lambda u, v, px, py: ramp(horn, 0.15 + 0.75 * v + 0.15 * math.sin(v * 22), px, py, 0.05))


def eye(s, iris, pupil, slit=True):
    def fn(u, v, px, py):
        dx, dy = u - 0.5, v - 0.5
        d = math.hypot(dx, dy)
        if d > 0.5:
            return None
        if (abs(dx) < 0.07 + 0.05 * (0.5 - abs(dy)) if slit else abs(dy) < 0.10 or (abs(dx) > 0.2 and dy < 0.0 and abs(dy) < 0.22)):
            return pupil
        return ramp(iris, 1.0 - d * 1.7, px, py, 0.04)
    s.fill(EYE, fn, lambda u, v, px, py: (ramp(iris, 1.0 - math.hypot(u - 0.5, v - 0.5) * 1.7, px, py, 0.04)
                                         if fn(u, v, px, py) not in (None, pupil) else None))


# ---------------------------------------------------------------- abyss serpent: armoured deep-blue leviathan
def serpent():
    s = Sheet(11)
    hide = [rgb(c) for c in ("#07111d", "#0b1a2b", "#10263a", "#173449", "#20465c", "#2c5c72")]
    belly = [rgb(c) for c in ("#3e5a62", "#5e7f84", "#86a3a2", "#aec4bd", "#cfdcd2")]
    cyan = rgb("#46e6ff")

    def skin(u, v, px, py):
        b = back(u)
        if b < 0.22:                                   # belly scutes: broad plates across the underside
            edge = (py % 16) in (0, 15)
            return ramp(belly, 0.15 if edge else 0.45 + 0.4 * (1 - b / 0.22) - 0.2 * ((py % 16) / 16), px, py)
        t = 0.12 + 0.45 * scales(u, v, px, py) + 0.25 * (1 - b)
        if b > 0.93:
            t *= 0.45                                  # dark dorsal stripe under the sail
        if abs(b - 0.5) < 0.035 and (py % 16) < 11:
            t = 0.05                                   # lateral line groove
        return ramp(hide, t, px, py)

    def skin_glow(u, v, px, py):
        b = back(u)
        if abs(b - 0.5) < 0.03 and (py % 16) in (4, 5, 6):   # lateral line photophores
            return cyan
        if 0.70 < b < 0.76 and py % 32 in (14, 15) and hashf(px // 2, py // 32, 3) > 0.4:
            return rgb("#1f8fb0")
        return None

    def head(u, v, px, py):
        b = back(u)
        if b < 0.18:
            return ramp(belly, 0.35 + 0.3 * hashf(px // 3, py // 3), px, py)
        t = 0.10 + 0.40 * scales(u, v, px, py, 6) + 0.2 * (1 - b)
        if v > 0.80 and (px % 9) in (0, 1) and 0.3 < b < 0.75:
            t = 0.02                                   # gill slits at the back of the skull
        if b > 0.8 and abs(((v * 5) % 1) - 0.5) < 0.12:
            t += 0.2                                   # brow / crest ridges
        return ramp(hide, t, px, py)

    s.fill(SKIN, skin, skin_glow)
    s.fill(HEAD, head, lambda u, v, px, py: cyan if (0.42 < back(u) < 0.47 and int(v * 20) % 3 == 0 and v < 0.7) else None)
    s.fill(SKIN2, lambda u, v, px, py: ramp(belly if back(u) < 0.55 else hide,
                                           0.3 + 0.35 * scales(u, v, px, py, 6) + (0.2 if back(u) < 0.55 else 0.0), px, py))
    s.fill(MEMBRANE, membrane(hide[2:] + belly[:1], rgb("#3bb4cf")),
           lambda u, v, px, py: cyan if (0.66 < v < 0.74 and (px % 9) in (4, 5)) else None)
    s.fill(INNER, lambda u, v, px, py: ramp([rgb(c) for c in ("#2a0a12", "#4a121c", "#701c28", "#96303a")],
                                           0.3 + 0.5 * math.sin(v * 14) * 0.5 + 0.25 * hashf(px // 2, py // 2), px, py))
    swatches(s, [rgb(c) for c in ("#8d8a78", "#bdb9a2", "#e3dfc8", "#f6f3e4")], cyan,
             [rgb(c) for c in ("#4a121c", "#701c28", "#96303a")], [rgb(c) for c in ("#05090f", "#0b1a2b", "#1c3346", "#3a5a6c")])
    eye(s, [rgb(c) for c in ("#0b6f8c", "#18a8cc", "#46e6ff", "#c8fbff")], rgb("#02060a"))
    return s


# ---------------------------------------------------------------- trench wyrm: ringed, plated bristle worm
def wyrm():
    s = Sheet(23)
    plate = [rgb(c) for c in ("#160806", "#2a100c", "#431a12", "#5f2718", "#7f3a20", "#a2562e")]
    flesh = [rgb(c) for c in ("#5c2a26", "#84443a", "#a9665a", "#c98c78", "#e0b29c")]
    ember = rgb("#ff7a1e")

    def skin(u, v, px, py):
        b = back(u)
        ring = (v * 2) % 1.0                           # two rings per joint
        if b < 0.25:                                   # soft belly, creased per ring
            return ramp(flesh, 0.2 + 0.55 * math.sin(ring * math.pi) + 0.1 * hashf(px // 2, py // 2), px, py)
        if ring < 0.10:
            return ramp(plate, 0.02, px, py)           # seam between plates
        t = 0.25 + 0.5 * (1 - ring) ** 2 + 0.15 * (b - 0.5)   # plate: bright leading edge, falling off
        if abs(((u * 12) % 1) - 0.5) < 0.06:
            t -= 0.18                                  # lengthwise ribs on the armour
        if hashf(px // 4, py // 4, 5) > 0.93:
            t -= 0.2                                   # pits
        return ramp(plate, t, px, py)

    def skin_glow(u, v, px, py):
        ring = (v * 2) % 1.0
        if ring < 0.10 and 0.3 < back(u) < 0.7 and hashf(px // 3, py // 8) > 0.35:
            return ember if ring < 0.05 else rgb("#b0400c")
        return None

    s.fill(SKIN, skin, skin_glow)
    s.fill(HEAD, lambda u, v, px, py: ramp(plate, 0.2 + 0.45 * v + (0.2 if abs(((u * 16) % 1) - 0.5) < 0.15 else 0)
                                          - (0.25 if v < 0.12 else 0), px, py),
           lambda u, v, px, py: ember if (v < 0.08 and (px % 8) < 3) else None)
    s.fill(SKIN2, lambda u, v, px, py: ramp(plate, 0.35 + 0.5 * back(u) * (1 - v) + 0.1 * math.sin(v * 30), px, py))
    s.fill(MEMBRANE, membrane(plate[2:], rgb("#d06a2a"), 5))
    s.fill(INNER, lambda u, v, px, py: ramp([rgb(c) for c in ("#1c0406", "#3c080c", "#681016", "#9a2420", "#c84a34")],
                                           0.15 + 0.6 * v + 0.2 * math.sin(v * 40) * 0.5 + 0.1 * hashf(px // 2, py // 2), px, py),
           lambda u, v, px, py: rgb("#7a1c08") if v > 0.85 else None)
    swatches(s, [rgb(c) for c in ("#6e5a40", "#a8946c", "#d8c8a0", "#f2e8cc")], ember,
             flesh[:3], [rgb(c) for c in ("#0e0504", "#2a100c", "#5f2718", "#a2562e")])
    eye(s, [rgb(c) for c in ("#7a2a06", "#c8540c", "#ff7a1e", "#ffd08a")], rgb("#120402"))
    return s


# ---------------------------------------------------------------- abyss kraken: blood-red colossal squid
def kraken():
    s = Sheet(37)
    red = [rgb(c) for c in ("#2a0610", "#450a18", "#641222", "#881c2c", "#ad3038", "#cc5248")]
    cream = [rgb(c) for c in ("#8a6258", "#b48a7a", "#d8b8a4", "#efdccb")]
    pale = rgb("#bfe6ff")

    def mottled(u, v, px, py, k):
        t = 0.30 + 0.25 * math.sin(u * math.tau * 3 + v * 5) * math.sin(v * 9 + u * 4) + 0.2 * hashf(px // 3, py // 3, k)
        if hashf(px // 2, py // 2, k + 1) > 0.94:
            t += 0.35                                  # pale chromatophore spots
        return t

    def mantle(u, v, px, py):
        return ramp(red, mottled(u, v, px, py, 1) + 0.15 * (1 - v), px, py)

    def arm(u, v, px, py):
        b = back(u)
        if b < 0.30:                                   # sucker face: two rows of ringed suckers
            cx = (px % 16) - 7.5
            cy = (py % 16) - 7.5
            d = math.hypot(cx * 1.0, cy)
            if d < 2.2:
                return ramp(red, 0.1, px, py)
            if d < 5.2:
                return ramp(cream, 0.85 - 0.12 * d + 0.3, px, py, 0.03)
            return ramp(cream, 0.25, px, py)
        return ramp(red, mottled(u, v, px, py, 2) + 0.1 * (b - 0.5), px, py)

    s.fill(SKIN, mantle, lambda u, v, px, py: pale if hashf(px // 2, py // 2, 9) > 0.975 else None)
    s.fill(HEAD, lambda u, v, px, py: ramp(red, mottled(u, v, px, py, 3) + 0.2 * v - 0.1, px, py),
           lambda u, v, px, py: pale if hashf(px // 2, py // 2, 12) > 0.985 else None)
    s.fill(SKIN2, arm, lambda u, v, px, py: (pale if (back(u) < 0.30 and math.hypot((px % 16) - 7.5, (py % 16) - 7.5) < 1.6
                                                     and (py // 16) % 2 == 0) else None))
    s.fill(MEMBRANE, membrane(red[1:], rgb("#e88a78"), 9))
    s.fill(INNER, lambda u, v, px, py: ramp(cream, 0.2 + 0.5 * v + 0.2 * hashf(px // 2, py // 2), px, py))
    swatches(s, [rgb(c) for c in ("#14100c", "#2e261c", "#57492f", "#8a7648")], pale,
             [rgb(c) for c in ("#641222", "#ad3038", "#e08a80")], [rgb(c) for c in ("#0c0806", "#2e261c", "#57492f", "#8a7648")])
    eye(s, [rgb(c) for c in ("#7a5a10", "#c89a18", "#f2d040", "#fff4a8")], rgb("#060404"), slit=False)
    return s


# ---------------------------------------------------------------- abyss mother: violet colossal jellyfish
def mother():
    s = Sheet(53)
    bell = [rgb(c) for c in ("#1c1040", "#2c1a5e", "#40287e", "#5a3ea0", "#7c5cc0", "#a488dc")]
    pink = [rgb(c) for c in ("#70205e", "#a43288", "#d850b4", "#ff8ade", "#ffd0f4")]
    magenta, ice = rgb("#ff5ad8"), rgb("#c4d4ff")

    def canal(u):
        return abs(((u * 16) % 1) - 0.5)

    def umbrella(u, v, px, py):                        # v: 0 = apex, 1 = rim
        t = 0.25 + 0.35 * v + 0.15 * math.sin(u * math.tau * 8 + v * 6) + 0.12 * hashf(px // 3, py // 3)
        if canal(u) < 0.19:
            t += 0.25                                  # radial canals
        if v > 0.90:
            t += 0.2                                   # thick margin
        if hashf(px // 2, py // 2, 4) > 0.955:
            t += 0.3
        return ramp(bell, t, px, py)

    def umbrella_glow(u, v, px, py):
        if canal(u) < 0.13 and v > 0.12:
            return rgb("#b040c0") if (py % 4) else magenta
        if v > 0.93 and (px % 8) in (3, 4):
            return ice                                 # marginal lights
        return rgb("#241048")                          # the whole bell glows faintly

    def strand(u, v, px, py):
        band = (py % 16) < 3
        return ramp(pink if band else bell[2:], 0.45 + 0.3 * back(u) + (0.25 if band else 0.0), px, py)

    s.fill(SKIN, umbrella, umbrella_glow)
    s.fill(HEAD, umbrella, umbrella_glow)
    s.fill(SKIN2, strand, lambda u, v, px, py: magenta if (py % 16) < 2 else None)
    s.fill(MEMBRANE, membrane(pink, rgb("#ffd0f4"), 11),
           lambda u, v, px, py: rgb("#a43288") if (v > 0.45 and hashf(px // 2, py // 2, 6) > 0.5) else None)

    def under(u, v, px, py):                           # under-bell: gonad horseshoes between the canals
        g = abs(v - 0.45 - 0.12 * math.cos(u * math.tau * 4)) < 0.07
        return ramp(pink if g else bell[:4], (0.6 if g else 0.2 + 0.4 * v) + 0.15 * hashf(px // 2, py // 2), px, py)

    s.fill(INNER, under, lambda u, v, px, py: (magenta if abs(v - 0.45 - 0.12 * math.cos(u * math.tau * 4)) < 0.05 else None))
    swatches(s, [rgb(c) for c in ("#a488dc", "#d0c0f0", "#f0e8ff")], magenta,
             pink[:3], [rgb(c) for c in ("#2c1a5e", "#5a3ea0", "#a488dc")])
    eye(s, [rgb(c) for c in ("#a43288", "#ff5ad8", "#ffb0f0", "#ffffff")], rgb("#ff5ad8"))
    return s


BOSSES = {"serpent": ("abyss_serpent", serpent), "wyrm": ("trench_wyrm", wyrm), "kraken": ("abyss_kraken", kraken),
          "mother": ("abyss_mother", mother)}


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--out", default=os.path.join(here, "..", "src", "main", "resources", "assets", "abyssia", "textures", "entity"))
    ap.add_argument("--only", choices=sorted(BOSSES))
    ap.add_argument("--dry-run", action="store_true", help="build the images, write nothing")
    a = ap.parse_args()
    for key, (name, fn) in BOSSES.items():
        if a.only and a.only != key:
            continue
        sheet = fn()
        for suffix, img in (("", sheet.skin), ("_glow", sheet.glow)):
            path = os.path.normpath(os.path.join(a.out, name + suffix + ".png"))
            if not a.dry_run:
                img.save(path)
            print(("would write " if a.dry_run else "wrote ") + path)


if __name__ == "__main__":
    main()
