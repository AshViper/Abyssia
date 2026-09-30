"""Abyssia mineral sprites: raw lumps, crystals and powders, drawn as pixel art from scratch.

Every mineral texture belongs to one category, each with its own vanilla reference.  Raw lumps and ingots are
recoloured vanilla pixels (Mojang derivatives, listed in RECOLOURED); crystals and powders are drawn from scratch
with vanilla as a style reference only:

    raw      mined lumps: the vanilla Raw Iron / Gold / Copper icons, colours remapped to the mineral (every pixel,
             the alpha and the light -> dark order kept; see recolour())
    ingot    the vanilla Iron / Gold / Copper ingots, remapped the same way with the same palette as the raw lump
    crust    blocks: the vanilla Raw Iron / Gold / Copper Blocks, remapped the same way; their dark gap tones
             (Recolour.host) become the deep-sea host rock, the rest the mineral (same palette as the raw lump)
    crystal  grown prisms (Amethyst shard / cluster): sharp columns, lit face / main face / shadow face, a vertical
             ridge highlight, clean tips; only the special crystals get a glowing core (+ <name>_glow.png)
    powder   fine deposits (Brown Dye): a low heap on a brown matrix, fine grains, dense clumps, loose specks

Blocks written here: crusts (recoloured) and crystal clusters / buds.  Nodules and the crystal needle stay with
Texture Forge; drafts of them in this style live in BLOCK_DRAFTS (shown by --preview, never written).

    python tools/mineral_textures.py                        # write every texture (16x16) into the assets
    python tools/mineral_textures.py --only raw_cobalt,sulfur
    python tools/mineral_textures.py --list                 # JSON: texture -> category, mineral
    python tools/mineral_textures.py --preview sheet.png    # contact sheet at 16 / 32 / 64
    python tools/mineral_textures.py --size 32 --out DIR    # other sizes go to DIR, never into the assets
    python tools/mineral_textures.py --dry-run              # render, write nothing, print JSON
"""
from __future__ import annotations

import argparse
import json
import math
import os
from dataclasses import dataclass

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures")


def rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mix(a: str, b: str, t: float) -> str:
    ca, cb = rgb(a), rgb(b)
    return "#%02x%02x%02x" % tuple(round(x + (y - x) * t) for x, y in zip(ca, cb))


# ================================================================ palettes
# Low-saturation deep-sea tones; each mineral keeps its own hue (cobalt blue, copper orange + patina, manganese
# purple-gray, sulfur yellow...) at a slightly lowered value / chroma.

SLATE_MATRIX = ("#16181f", "#22252e", "#2e323c", "#3b404b")   # deepslate host rock left on a lump
WARM_MATRIX = ("#1c1511", "#2b221b", "#3a2e25", "#4a3c30")    # brown host rock (copper, gold, sulfur)
BROWN_DYE = ("#24170e", "#422a18", "#5f3d23", "#7c5433", "#9a6e46")   # powder base, dark -> light


@dataclass(frozen=True)
class Mineral:
    body: tuple[str, ...]                 # darkest, dark, main, light, highlight (raw lumps and crystals)
    matrix: tuple[str, ...] = SLATE_MATRIX
    accent: tuple[str, ...] = ()          # dark, light: patina / rust / glints on a raw lump
    powder: tuple[str, ...] = ()          # dark brown -> palest grain; derived from BROWN_DYE when empty
    glow: str | None = None               # core colour of glowing crystals

    def powder_ramp(self) -> tuple[str, ...]:
        if self.powder:
            return self.powder
        return tuple(mix(b, m, t) for b, m, t in zip(BROWN_DYE, self.body, (0.1, 0.3, 0.45, 0.55, 0.6)))


MINERALS: dict[str, Mineral] = {
    # metals and ore minerals
    "iron": Mineral(("#22252c", "#40454f", "#646a75", "#8b919b", "#b8bdc4"),
                    accent=("#5e3624", "#8a5238")),
    "copper": Mineral(("#28160f", "#552e1d", "#8a4d2c", "#b56e3e", "#d6945c"), WARM_MATRIX,
                      accent=("#2c6259", "#4a8f7c")),
    "gold": Mineral(("#2a1d0f", "#5b4216", "#936f22", "#bf9632", "#dcbc5a"), WARM_MATRIX),
    "cobalt": Mineral(("#141f47", "#223a76", "#3160a4", "#4e8bc2", "#8cc0dc"),
                      accent=("#6c7c98", "#a8b8c8")),
    "manganese": Mineral(("#211a21", "#393240", "#57495f", "#78697f", "#a095a8"),
                         matrix=("#141419", "#1e1f25", "#292a32", "#353640"), accent=("#3a2c26", "#5a4538"),
                         powder=("#1c1411", "#31231e", "#473233", "#5f464b", "#7a6068")),
    "nickel": Mineral(("#232b24", "#445241", "#6a7a60", "#93a484", "#c0cab0"),
                      accent=("#2f6a40", "#4f965c")),
    "sulfur": Mineral(("#2a200f", "#5a4815", "#967e22", "#c2a73a", "#ddcb74"), WARM_MATRIX,
                      powder=("#2a1c0e", "#584219", "#8c7426", "#b89c3a", "#dcc66a")),
    # rare metals (M01): true hues, value / chroma lowered slightly
    "platinum": Mineral(("#2a2c31", "#4f535b", "#7b808a", "#a8adb5", "#d4d7dc")),               # bright grey-white
    "tellurium": Mineral(("#1f2530", "#3a4555", "#5d6c80", "#8494a8", "#b4c2d2")),              # silvery blue-grey
    "molybdenum": Mineral(("#1a1f29", "#333c4c", "#505d72", "#728198", "#9eacc0")),             # lead blue
    "vanadium": Mineral(("#18242a", "#304349", "#4c666a", "#6e8a8a", "#9cb4b0"),                # blue-green grey
                        accent=("#2c5a62", "#4e8a8c")),
    "tungsten": Mineral(("#16171a", "#2a2c30", "#43464b", "#5f6268", "#868a90")),               # dark grey
    "yttrium": Mineral(("#2e2c26", "#57544a", "#838071", "#afab98", "#d8d4c0"),                 # pale yellow-white grey
                       accent=("#6a6a58", "#9a9a84")),
    # crystals
    "thermal": Mineral(("#2c0e09", "#6a2211", "#a24319", "#cf6c29", "#ecaa5a"), glow="#ffc878"),
    "abyssal": Mineral(("#1e1439", "#3a2a68", "#5f4698", "#886cc4", "#c0a8e8"), glow="#dcc8ff"),
    "deep": Mineral(("#0a202d", "#124654", "#1c6d7c", "#3b9cab", "#88cdd6"), glow="#bff4f8"),
    "pressure": Mineral(("#1b1a2e", "#343151", "#544e75", "#7a749b", "#a7a3c3")),
    "pale": Mineral(("#222935", "#485364", "#728094", "#9eaaba", "#d2dae4")),
    "ice": Mineral(("#172c3b", "#2b566b", "#46839b", "#74b3c8", "#bce3ee")),
    # crust-only minerals
    "iron_oxide": Mineral(("#2a140c", "#5a2c17", "#8a4a26", "#b0683a", "#d49a68")),    # ferromanganese rust
    "ochre": Mineral(("#24160b", "#4a2e16", "#734a24", "#9a6a38", "#c4985e")),         # limonite cave crust
}

# ================================================================ shapes (px16 units, y down)
# A raw lump is a pile of fragments: (cx, cy, radius, sides, jag, matrix?).  Few sides = angular broken chunks,
# many sides + low jag = rounded nodules.  Fragments lower on the sprite sit in front.

@dataclass(frozen=True)
class RawShape:
    fragments: tuple[tuple, ...]
    grain: float = 0.12        # rough-surface clumps (share of interior pixels)
    highlight: float = 0.06    # highlight pixels per fragment area
    specks: int = 3            # accent specks (patina, rust, glints)
    ore_specks: int = 2        # mineral grains left in the matrix fragments
    spin: float = 0.0          # vertex angle offset (turns the fragments' facets)


# Raw lumps and ingots are vanilla icons recoloured: every pixel, the alpha and the light -> dark order stay; only
# hue / chroma / value change.  Pixels of the source's green-blue patina (raw copper) take the mineral's accent.
# A crust also has host tones: source colours (the gaps between the lumps) that become the deep-sea host rock.
@dataclass(frozen=True)
class Recolour:
    ref: str                   # vanilla texture, e.g. "item/raw_iron"
    host: tuple[str, ...] = () # source colours -> host rock ramp (crusts)


# The vanilla Raw Ore Blocks' gap tones (their darkest / olive, non-patina colours): the crust's host rock.
RAW_BLOCK_HOST = {
    "block/raw_iron_block": ("#665237", "#7c623f"),
    "block/raw_gold_block": ("#ae6d26",),
    "block/raw_copper_block": ("#716745", "#7f7257", "#988c69"),
}


def crust(mineral: str, ref: str) -> "Texture":
    return Texture("crust", mineral, Recolour(ref, RAW_BLOCK_HOST[ref]), kind="block")


# Crystals are prisms: (base x, base y, lean in degrees (0 = up, + = right), length, width, tip length,
# base taper).  Listed back to front.
@dataclass(frozen=True)
class CrystalShape:
    prisms: tuple[tuple, ...]
    core: bool = False         # glowing core (special crystals)


# A prism's optional extras (tuple fields 8 and 9): ``cut`` moves the tip's apex off-centre, an irregular broken
# point (+1 = toward the lit / left edge, -1 = toward the right), ``base_cut`` slants the bottom end, so a loose
# fragment reads as broken off rather than grown from the ground.

# Shards (Quartz-style items): broken, elongated crystal fragments - sharp, angular, several faces.
@dataclass(frozen=True)
class ShardShape:
    prisms: tuple[tuple, ...]
    core: bool = False


# Clusters (Amethyst-Cluster-style blocks): crystals grown together from one base.  The layout comes from the size
# class; the mineral's habit keeps each mineral's cluster its own shape.
@dataclass(frozen=True)
class Habit:
    width: float = 1.0         # crystal thickness
    length: float = 1.0        # crystal length
    tip: float = 1.0           # tip length (small = flat, cubic ends)
    bipyramid: bool = False    # tapered at the base too (sulfur)
    extra: int = 0             # additional thin crystals (needle sprays)


@dataclass(frozen=True)
class ClusterShape:
    size: str                  # small | medium | large
    habit: Habit = Habit()
    core: bool = False


# size class -> crystals (base x, lean, length, width, tip), back to front; the largest (central) one is last
CLUSTER_LAYOUTS = {
    "small": ((10.4, 26.57, 4.6, 3.8, 1.8), (7.0, 0, 6.4, 4.6, 2.2)),
    "medium": ((4.8, -26.57, 5.6, 4.0, 2.0), (11.4, 45, 4.6, 3.6, 1.8), (8.0, 0, 8.8, 4.8, 2.6)),
    "large": ((2.8, -45, 6.6, 3.4, 2.0), (13.2, 45, 6.0, 3.4, 2.0), (5.2, -26.57, 10.2, 4.2, 2.6),
              (10.8, 26.57, 9.0, 4.0, 2.6), (8.0, 0, 14.2, 5.0, 3.2)),
}


# Powders are heaps on a base line: mounds (centre x, half width, height); rest of the look is shared.
@dataclass(frozen=True)
class PowderShape:
    mounds: tuple[tuple, ...]
    base: float = 13.0
    layer: tuple[float, float] = (2.0, 14.0)   # (clipped to 2px either side of the heap)   # thin powder layer at the foot (x from, x to)
    clumps: int = 3            # dense dark accumulations
    grains: float = 0.16       # fine light grains (share of heap pixels)
    loose: int = 5             # loose grains around the foot


@dataclass(frozen=True)
class Texture:
    category: str              # raw | ingot | crust | crystal | powder
    mineral: str
    shape: object
    kind: str = "item"         # item | block (block sprites are crossed planes)
    seed: int = 0


TEXTURES: dict[str, Texture] = {
    # ---- raw lumps and ingots (items): vanilla Raw Ore / Ingot icons, colours remapped only (see recolour()).
    # One vanilla pair per mineral, so raw and ingot share shape family and palette.
    "raw_cobalt": Texture("raw", "cobalt", Recolour("item/raw_iron")),
    "cobalt_ingot": Texture("ingot", "cobalt", Recolour("item/iron_ingot")),
    "raw_manganese": Texture("raw", "manganese", Recolour("item/raw_gold")),
    "manganese_ingot": Texture("ingot", "manganese", Recolour("item/gold_ingot")),
    "raw_nickel": Texture("raw", "nickel", Recolour("item/raw_copper")),     # copper's patina -> garnierite green
    "nickel_ingot": Texture("ingot", "nickel", Recolour("item/copper_ingot")),
    # rare metals (M01): two per vanilla family (platinum / tellurium = iron, molybdenum / tungsten = gold,
    # vanadium / yttrium = copper, whose patina takes the accent ramp)
    "raw_platinum": Texture("raw", "platinum", Recolour("item/raw_iron")),
    "platinum_ingot": Texture("ingot", "platinum", Recolour("item/iron_ingot")),
    "raw_tellurium": Texture("raw", "tellurium", Recolour("item/raw_iron")),
    "tellurium_ingot": Texture("ingot", "tellurium", Recolour("item/iron_ingot")),
    "raw_molybdenum": Texture("raw", "molybdenum", Recolour("item/raw_gold")),
    "molybdenum_ingot": Texture("ingot", "molybdenum", Recolour("item/gold_ingot")),
    "raw_tungsten": Texture("raw", "tungsten", Recolour("item/raw_gold")),
    "tungsten_ingot": Texture("ingot", "tungsten", Recolour("item/gold_ingot")),
    "raw_vanadium": Texture("raw", "vanadium", Recolour("item/raw_copper")),
    "vanadium_ingot": Texture("ingot", "vanadium", Recolour("item/copper_ingot")),
    "raw_yttrium": Texture("raw", "yttrium", Recolour("item/raw_copper")),
    "yttrium_ingot": Texture("ingot", "yttrium", Recolour("item/copper_ingot")),

    # ---- crusts (blocks): vanilla Raw Ore Blocks, colours remapped only; same vanilla family and palette as the
    # mineral's raw lump (cobalt = iron, manganese = gold, nickel = copper)
    "cobalt_crust": crust("cobalt", "block/raw_iron_block"),
    "manganese_crust": crust("manganese", "block/raw_gold_block"),
    "nickel_crust": crust("nickel", "block/raw_copper_block"),
    "copper_crust": crust("copper", "block/raw_copper_block"),
    "iron_crust": crust("iron_oxide", "block/raw_iron_block"),
    "cave_mineral_crust": crust("ochre", "block/raw_gold_block"),

    # ---- shards (items): Quartz-style broken fragments; prisms lean at clean pixel slopes (0, +-26.57, +-45,
    # +-63.43 degrees).  (base x, base y, lean, length, width, tip, base taper, cut, base cut)
    # abyssal: slender, roughly parallel fragments leaning right, the longest in the middle
    "abyssal_crystal_shard": Texture("crystal", "abyssal", ShardShape((
        (4.0, 12.8, 26.57, 9.6, 4.4, 3.2, 0, -0.7, 0.8), (6.8, 15.4, 26.57, 14.2, 5.4, 4.6, 0, 0.8, -0.8),
        (4.6, 15.8, 45, 7.4, 4.0, 2.8, 0, -0.5, 0.6))),
        seed=53),
    # thermal: thick, stubby fragments leaning left (mirror of abyssal's lean), warm core
    "thermal_crystal_shard": Texture("crystal", "thermal", ShardShape((
        (12.4, 13.4, -26.57, 8.4, 4.6, 3.0, 0, 0.6, -0.8), (10.4, 15.6, -26.57, 12.4, 6.2, 3.8, 0, -0.7, 0.8),
        (6.2, 15.4, -45, 5.4, 4.0, 2.2, 0, 0.5, -0.6)), core=True),
        seed=59),

    # ---- crystal clusters (blocks; crossed planes, also their item icon): Amethyst-Cluster-style groups
    "cobalt_cluster": Texture("crystal", "cobalt", ClusterShape("large", Habit(width=1.25, tip=0.45)),
                              kind="block", seed=61),                      # blocky columns, flat cubic tips
    "nickel_cluster": Texture("crystal", "nickel", ClusterShape("large", Habit(width=0.6, tip=1.2, extra=2)),
                              kind="block", seed=67),                      # millerite needle spray
    "sulfur_cluster": Texture("crystal", "sulfur", ClusterShape("large", Habit(width=1.3, length=0.85, tip=1.3,
                                                                                bipyramid=True)),
                              kind="block", seed=71),                      # stubby dipyramids
    "abyssal_crystal_cluster": Texture("crystal", "abyssal", ClusterShape("large", core=True),
                                       kind="block", seed=73),             # the classic fan, glowing cores
    "deep_crystal_cluster": Texture("crystal", "deep", ClusterShape("large", Habit(width=0.72, length=1.05),
                                                                    core=True),
                                    kind="block", seed=79),                # slender columns, glowing cores
    "pressure_crystal_cluster": Texture("crystal", "pressure", ClusterShape("large", Habit(width=1.05, tip=1.8)),
                                        kind="block", seed=83),            # blades with long tapering tips
    "pale_crystal_cluster": Texture("crystal", "pale", ClusterShape("large", Habit(width=0.9, tip=0.85)),
                                    kind="block", seed=89),                # quartz-like upright prisms
    # thermal: the one small -> medium -> large growth series; stubby, warm glowing cores
    "small_thermal_crystal_bud": Texture("crystal", "thermal", ClusterShape("small", Habit(width=1.1), core=True),
                                         kind="block", seed=97),
    "medium_thermal_crystal_bud": Texture("crystal", "thermal", ClusterShape("medium", Habit(width=1.1), core=True),
                                          kind="block", seed=101),
    "thermal_crystal_cluster": Texture("crystal", "thermal", ClusterShape("large", Habit(width=1.1, length=0.92),
                                                                          core=True),
                                       kind="block", seed=103),

    # ---- powders (items)
    "sulfur": Texture("powder", "sulfur", PowderShape(((6.6, 5.2, 7.4), (10.8, 4.0, 5.6), (3.4, 2.4, 3.0)), base=13.5, clumps=3, grains=0.35),
                      seed=109),
}

# Textures made of recoloured vanilla pixels (Mojang derivatives; forge_textures.RECOLOURED includes them).
RECOLOURED = sorted(n for n, t in TEXTURES.items() if isinstance(t.shape, Recolour))
# Blocks with an emissive overlay (<name>_glow.png, gen_deep_assets' cross model draws it full-bright).
GLOWING = sorted(n for n, t in TEXTURES.items() if t.kind == "block" and getattr(t.shape, "core", False))

# Block sprites in the same style: drafts only (the mod's block textures stay with Texture Forge).
BLOCK_DRAFTS: dict[str, Texture] = {
    # manganese nodules on the seabed: three loose lumps (crossed-plane block sprite)
    "manganese_nodules": Texture("raw", "manganese", RawShape((
        (4.0, 12.8, 2.6, 9, 0.08, False), (9.4, 12.0, 3.3, 9, 0.07, False), (13.2, 13.6, 2.0, 8, 0.1, False),
        (7.0, 14.0, 1.8, 8, 0.1, True)), grain=0.06, highlight=0.04, specks=1, ore_specks=0), kind="block", seed=41),

    # crystal needle (cave speleothem sprite): three long thin ice-blue needles
    "crystal_needle": Texture("crystal", "ice", CrystalShape((
        (5.0, 16.4, -26.57 / 2, 9.6, 2.6, 4.0, 0), (11.0, 16.4, 26.57 / 2, 11.6, 2.6, 4.4, 0),
        (8.0, 16.6, 0, 15.4, 3.2, 5.6, 0))), kind="block", seed=107),
}


# ================================================================ helpers

N4 = ((0, 1), (0, -1), (1, 0), (-1, 0))
LIGHT = np.array([-0.55, -0.83])   # from the top left, more from above


def shift(a: np.ndarray, dy: int, dx: int, fill=0) -> np.ndarray:
    """a moved by (dy, dx); cells that come in from outside get ``fill``."""
    out = np.full_like(a, fill)
    h, w = a.shape
    out[max(0, dy):h + min(0, dy), max(0, dx):w + min(0, dx)] = a[max(0, -dy):h + min(0, -dy), max(0, -dx):w + min(0, -dx)]
    return out


def distance_inside(mask: np.ndarray) -> np.ndarray:
    """4-neighbour distance of each mask cell to the nearest cell outside the mask (edge cells = 0)."""
    d = np.where(mask, 10 ** 6, 0)
    for _ in range(2):
        for y in range(d.shape[0]):
            for x in range(d.shape[1]):
                if mask[y, x]:
                    for dy, dx in ((-1, 0), (0, -1)):
                        yy, xx = y + dy, x + dx
                        v = d[yy, xx] + 1 if 0 <= yy < d.shape[0] and 0 <= xx < d.shape[1] else 0
                        d[y, x] = min(d[y, x], v)
        d = d[::-1, ::-1]
        mask = mask[::-1, ::-1]
    return np.maximum(d - 1, 0)


def grid16(n: int) -> tuple[np.ndarray, np.ndarray]:
    """Pixel-centre coordinates in px16 units."""
    c = (np.arange(n) + 0.5) * 16.0 / n
    return np.meshgrid(c, c)


class Sprite:
    """A sprite as tone labels: (ramp name, level) per pixel, resolved to colours at the end."""

    def __init__(self, n: int):
        self.n = n
        self.ramp = np.full((n, n), "", dtype=object)
        self.level = np.zeros((n, n), dtype=np.int32)
        self.glow = np.zeros((n, n), dtype=bool)

    def set(self, mask: np.ndarray, ramp: str, level) -> None:
        self.ramp[mask] = ramp
        self.level[mask] = level if np.isscalar(level) else np.asarray(level)[mask]

    def image(self, ramps: dict[str, tuple[str, ...]]) -> tuple[Image.Image, Image.Image | None]:
        out = np.zeros((self.n, self.n, 4), dtype=np.uint8)
        for name, tones in ramps.items():
            sel = self.ramp == name
            for lv, col in enumerate(tones):
                out[sel & (self.level == lv)] = (*rgb(col), 255)
        glow = None
        if self.glow.any():
            g = np.zeros_like(out)
            g[self.glow] = out[self.glow]
            glow = Image.fromarray(g, "RGBA")
        return Image.fromarray(out, "RGBA"), glow


# ================================================================ raw lumps

def _fragment_polygon(frag, n: int, rng: np.random.Generator, spin: float) -> list[tuple[float, float]]:
    cx, cy, r, sides, jag, _ = frag
    s = n / 16.0
    start = rng.uniform(0, 2 * math.pi / sides) + spin * 2 * math.pi
    pts = []
    for i in range(sides):
        a = start + 2 * math.pi * i / sides + rng.uniform(-0.22, 0.22) * 2 * math.pi / sides
        rr = r * (1 + rng.uniform(-jag, jag))
        pts.append(((cx + rr * math.cos(a)) * s - 0.5, (cy + rr * math.sin(a) * 0.9) * s - 0.5))
    return pts


def draw_raw(t: Texture, n: int) -> Sprite:
    sh: RawShape = t.shape
    rng = np.random.default_rng(t.seed)
    s = n / 16.0
    frags = sorted(sh.fragments, key=lambda f: f[1])   # lower fragments in front
    owner = np.full((n, n), -1, dtype=np.int32)
    for i, f in enumerate(frags):
        im = Image.new("L", (n, n), 0)
        ImageDraw.Draw(im).polygon(_fragment_polygon(f, n, rng, sh.spin), fill=255)
        owner[np.asarray(im) > 0] = i

    # silhouette clean-up: fill pinholes, drop one-pixel spurs
    for _ in range(2):
        filled = owner >= 0
        nb = sum(shift(filled.astype(np.int32), dy, dx) for dy, dx in N4)
        for y, x in zip(*np.nonzero(~filled & (nb >= 3))):
            cand = [owner[y + dy, x + dx] for dy, dx in N4 if 0 <= y + dy < n and 0 <= x + dx < n and owner[y + dy, x + dx] >= 0]
            owner[y, x] = max(cand)
        filled = owner >= 0
        nb = sum(shift(filled.astype(np.int32), dy, dx) for dy, dx in N4)
        owner[filled & (nb <= 1)] = -1

    sp = Sprite(n)
    ys, xs = np.mgrid[0:n, 0:n]
    filled = owner >= 0
    for i, f in enumerate(frags):
        mine = owner == i
        if not mine.any():
            continue
        cx, cy, r = f[0] * s - 0.5, f[1] * s - 0.5, f[2] * s
        sdir = ((xs - cx) * LIGHT[0] + (ys - cy) * LIGHT[1]) / r
        d16 = distance_inside(mine) * 16.0 / n
        level = np.zeros((n, n), dtype=np.int32)
        v = sdir + 0.28 * (np.minimum(d16, 3) - 1.5) / 1.5
        level[mine] = np.where(v > 0.08, 3, np.where(v > -0.5, 2, 1))[mine]

        crevice = np.zeros_like(mine)
        rim = np.zeros_like(mine)
        outline = np.zeros_like(mine)
        for dy, dx in N4:
            other = shift(owner, dy, dx, fill=-1)
            outline |= mine & (other == -1)
            crevice |= mine & (other > i)
            rim |= mine & (other >= 0) & (other < i)
        rim &= ~outline & ~crevice
        level[rim] = np.where(sdir > 0.0, 3, 2)[rim]
        level[outline] = np.where(sdir > 0.45, 1, 0)[outline]

        ramp = "matrix" if f[5] else "body"
        interior = mine & ~outline & ~crevice & ~rim
        # rough surface: two-pixel clumps one tone up or down, never into outline or highlight tones
        cells = list(zip(*np.nonzero(interior)))
        k = int(len(cells) * sh.grain * min(1.0, 0.3 * s) / 2)
        for idx in rng.permutation(len(cells))[:k]:
            y, x = cells[idx]
            dy, dx = N4[rng.integers(4)]
            delta = 1 if rng.random() < 0.45 else -1
            for yy, xx in ((y, x), (y + dy, x + dx)):
                if 0 <= yy < n and 0 <= xx < n and interior[yy, xx]:
                    level[yy, xx] = int(np.clip(level[yy, xx] + delta, 1, 3))
        # highlight: the few most-lit interior pixels of the fragment (upper-left bump)
        if ramp == "body":
            lit = np.nonzero(interior & (level >= 2))
            if len(lit[0]):
                order = np.argsort(-sdir[lit])
                k = max(1, round(mine.sum() * sh.highlight))
                sel = (lit[0][order[:k]], lit[1][order[:k]])
                level[sel] = 4
        mat_level = np.minimum(level, 3)
        sp.set(mine, ramp, mat_level if ramp == "matrix" else level)
        # gaps between fragments show the dark host rock
        # the seam where a fragment dips behind the one in front of it: its own dark tone
        sp.set(crevice, ramp, 1)
        if ramp == "matrix" and sh.ore_specks:
            spots = list(zip(*np.nonzero(interior)))
            for idx in rng.permutation(len(spots))[:sh.ore_specks * max(1, round(s))]:
                sp.ramp[spots[idx]], sp.level[spots[idx]] = "body", 3

    # accent specks (patina, rust, glints): on shadowed pixels next to gaps and edges, in small clumps
    mineral = MINERALS[t.mineral]
    if mineral.accent and sh.specks:
        body = sp.ramp == "body"
        edge = np.zeros_like(body)
        for dy, dx in N4:
            edge |= shift((sp.ramp == "matrix") | ~filled, dy, dx, fill=1).astype(bool)
        cand = list(zip(*np.nonzero(body & edge & (sp.level >= 1) & (sp.level <= 2))))
        for idx in rng.permutation(len(cand))[:sh.specks * max(1, round(s))]:
            y, x = cand[idx]
            sp.ramp[y, x] = "accent"
            sp.level[y, x] = 1 if sp.level[y, x] >= 2 else 0
    return sp


# ================================================================ crystals

def cluster_prisms(t: Texture) -> tuple[tuple, ...]:
    """The size class's layout, shaped by the mineral's habit and jittered a little per texture."""
    sh: ClusterShape = t.shape
    h = sh.habit
    rng = np.random.default_rng(t.seed)
    prisms = []
    layout = CLUSTER_LAYOUTS[sh.size]
    for i, (bx, lean, length, width, tip) in enumerate(layout):
        main = i == len(layout) - 1
        length = min(15.4, length * h.length + (0 if main else rng.uniform(-0.6, 0.6)))
        width = max(2.2, width * h.width)
        tip = max(1.0, min(length * 0.5, tip * h.tip))
        prisms.append((bx, 16.4 if not main else 16.6, lean, length, width, tip, tip * 0.7 if h.bipyramid else 0))
    for k in range(h.extra):   # extra thin crystals between the others
        side = -1 if k % 2 == 0 else 1
        prisms.insert(0, (8.0 + side * 1.6, 16.4, side * 26.57 / 2, 11.5 * h.length - k, 2.2, 3.0, 0))
    return tuple(prisms)


def draw_crystal(t: Texture, n: int) -> Sprite:
    sh = t.shape
    prisms = cluster_prisms(t) if isinstance(sh, ClusterShape) else sh.prisms
    X, Y = grid16(n)
    owner = np.full((n, n), -1, dtype=np.int32)
    U = np.zeros((n, n))
    V = np.zeros((n, n))
    TIP = np.zeros((n, n), dtype=bool)
    LEN = np.zeros((n, n))
    BIAS = np.zeros((n, n))
    for i, p in enumerate(prisms):
        bx, by, lean, length, width, tip, base_tip = p[:7]
        cut, base_cut = (tuple(p[7:9]) + (0.0, 0.0))[:2]
        a = math.radians(lean)
        ux, uy = math.sin(a), -math.cos(a)
        vx, vy = math.cos(a), math.sin(a)
        u = (X - bx) * ux + (Y - by) * uy
        v = (X - bx) * vx + (Y - by) * vy
        vn = v / (width / 2)
        # the tip narrows to an apex; ``cut`` moves the apex toward one edge (an irregular, broken point)
        k = np.clip((u - (length - tip)) / tip, 0, 1)
        apex = -cut * 0.7
        lo, hi = -1 + (1 + apex) * k, 1 - (1 - apex) * k
        if base_tip:
            b = np.clip(0.35 + u / base_tip, 0, 1)
            lo, hi = np.maximum(lo, -b), np.minimum(hi, b)
        end = np.full_like(u, length)
        start = tip * 0.6 * (1 - base_cut * vn) / 2 if base_cut else 0.0
        inside = (u >= start) & (u <= end) & (vn >= lo - 1e-6) & (vn <= hi + 1e-6)
        owner[inside] = i
        U[inside], V[inside] = u[inside], vn[inside]
        TIP[inside] = (u > length - tip)[inside]
        LEN[inside] = length
        # each crystal faces the light by its lean: leaning left shows more lit face, leaning right more shadow
        BIAS[inside] = -0.35 * math.sin(a)

    sp = Sprite(n)
    filled = owner >= 0
    # faces: lit (left) / main / shadow (right); tip facets lighter on the lit side
    # (the outline takes the outermost pixel, so the lit face starts close to the axis)
    level = np.where(V < -0.12 + BIAS, 3, np.where(V < 0.42 + BIAS, 2, 1))
    level = np.where(TIP, np.where(V < 0.0 + BIAS, 4, np.where(V < 0.5 + BIAS, 3, 2)), level)
    # vertical ridge highlight: lit-face pixels that touch the main face, in the upper body
    ridge = np.zeros_like(filled)
    for dy, dx in N4:
        ridge |= (shift(level, dy, dx, fill=-1) == 2) & (shift(owner, dy, dx, fill=-1) == owner)
    ridge &= filled & (level == 3) & ~TIP & (U > 0.3 * LEN)
    level[ridge] = 4

    edge = np.zeros_like(filled)
    for dy, dx in N4:
        other = shift(owner, dy, dx, fill=-1)
        edge |= filled & (other != owner) & ((other == -1) | (other > owner))
    level[edge] = np.where(TIP & (V < 0), 2, np.where(V < -0.2, 1, 0))[edge]
    sp.set(filled, "body", level)

    # special crystals: a warm light core shows through the middle of each prism
    if getattr(sh, "core", False):
        core = filled & ~edge & (np.abs(V) < 0.2) & (U > 0.15 * LEN) & (U < 0.55 * LEN) & ~TIP
        sp.set(core, "glow", 0)
        sp.glow |= core
        sp.glow |= filled & (level == 4) & TIP
    return sp


# ================================================================ powders

def draw_powder(t: Texture, n: int) -> Sprite:
    sh: PowderShape = t.shape
    rng = np.random.default_rng(t.seed)
    s = n / 16.0
    xs = (np.arange(n) + 0.5) / s
    height = np.zeros(n)
    for cx, hw, h in sh.mounds:
        height = np.maximum(height, h * np.clip(1 - ((xs - cx) / hw) ** 2, 0, None) ** 0.5)
    height_px = np.round(height * s).astype(int)
    base = int(round(sh.base * s))
    heap = np.zeros((n, n), dtype=bool)
    for x in range(n):
        if height_px[x] > 0:
            heap[base - height_px[x] + 1:base + 1, x] = True
    # thin layer at the foot
    layer = np.zeros_like(heap)
    lx0, lx1 = int(sh.layer[0] * s), int(sh.layer[1] * s)
    layer[base, lx0:lx1] = True
    gaps = rng.random(n) < 0.3
    layer[base, gaps] = False
    span = np.nonzero(heap[base])[0]
    if len(span):
        near = np.zeros(n, dtype=bool)
        near[max(0, span.min() - round(2 * s)):span.max() + round(2 * s) + 1] = True
        layer[base, ~near] = False
    layer &= ~heap

    ys = np.arange(n)[:, None]
    top = base - height_px[None, :] + 1
    tfrac = np.clip((base - ys) / np.maximum(height_px[None, :], 1), 0, 1)
    slope = np.gradient(height_px.astype(float))[None, :]
    # height sets the tone (dark foot -> light top), the left-facing slope is lit, and every pixel is a grain:
    # a third of them one tone up or down, so the heap reads as fine particles rather than a smooth surface
    lit = np.where(slope > 0.4, 0.6, np.where(slope < -0.4, -0.5, 0.0))
    jitter = rng.choice([-1, 0, 0, 1], size=(n, n), p=[0.16, 0.34, 0.34, 0.16])
    level = np.clip(np.round(1 + 2.1 * tfrac + lit + jitter * (rng.random((n, n)) < sh.grains * 2)), 1, 3).astype(int)
    surface = heap & (ys == top)
    peak = surface & (np.abs(slope) < 0.6) & (height_px[None, :] >= height_px.max())
    peak &= np.cumsum(peak, axis=1) <= max(1, round(2 * s))
    level = np.where(peak, 4, level)
    level = np.where(ys == base, np.where(rng.random((n, n)) < 0.3, 1, 0), level)
    side = np.zeros_like(heap)
    for dx in (-1, 1):
        side |= heap & ~shift(heap, 0, dx, fill=False)
    level = np.where(side & ~surface, np.where(slope > 0, 1, 0), level)

    body = heap & ~side & (ys < base)
    cells = list(zip(*np.nonzero(body)))
    # dense accumulations: small dark patches low on the heap
    low = [c for c in cells if tfrac[c] < 0.55]
    for idx in rng.permutation(len(low))[:sh.clumps]:
        y, x = low[idx]
        for yy, xx in ((y, x), (y, x + 1), (y - 1, x)):
            if 0 <= yy < n and 0 <= xx < n and body[yy, xx]:
                level[yy, xx] = 1
    sp = Sprite(n)
    sp.set(heap, "powder", level)
    sp.set(layer, "powder", np.where(rng.random((n, n)) < 0.3, 2, 1))
    # loose grains around the foot
    free = [(base - dy, x) for dy in (0, 1) for x in range(n) if not heap[base - dy, x] and not layer[base - dy, x]
            and (heap[base, max(0, x - 3):x + 4].any() or dy == 0)]
    for idx in rng.permutation(len(free))[:sh.loose]:
        y, x = free[idx]
        sp.ramp[y, x], sp.level[y, x] = "powder", int(rng.integers(1, 4))
    return sp


# ================================================================ output

_VANILLA = []


def vanilla(ref: str) -> Image.Image:
    """A vanilla texture from the Minecraft client jar (located the same way as forge_textures does)."""
    if not _VANILLA:
        from forge_textures import Vanilla, find_client_jar   # lazy: forge_textures imports this module
        jar = find_client_jar()
        if not jar:
            raise SystemExit("Minecraft 1.20.1 client jar not found (set ABYSSIA_MC_JAR)")
        _VANILLA.append(Vanilla(jar))
    im = _VANILLA[0].get(ref)
    if im is None:
        raise SystemExit(f"vanilla texture {ref} not found")
    return im


def _lightness(c: np.ndarray) -> np.ndarray:
    """Perceptual lightness (OKLab-like L) of sRGB colours, 0..1."""
    lin = np.where(c / 255 <= 0.04045, c / 255 / 12.92, ((c / 255 + 0.055) / 1.055) ** 2.4)
    return np.cbrt(lin @ np.array([0.2126, 0.7152, 0.0722]))


def _ramp_at(tones: tuple[str, ...], t: float) -> np.ndarray:
    pts = np.array([rgb(h) for h in tones], dtype=np.float64)
    x = t * (len(pts) - 1)
    i = min(int(x), len(pts) - 2)
    return pts[i] + (pts[i + 1] - pts[i]) * (x - i)


def _patina(c: np.ndarray) -> np.ndarray:
    """Green-blue pixels (copper's oxidation): a tone group of their own."""
    r, g, b = c[:, 0].astype(int), c[:, 1].astype(int), c[:, 2].astype(int)
    return (g > r + 8) | (b > r + 8)


def host_ramp() -> tuple[str, ...]:
    """The deep-sea host rock ramp (mineral_host_rock, the ores' host), dark -> light."""
    from forge_textures import ROCKS, slate_ramp   # lazy: forge_textures imports this module
    return tuple(slate_ramp(*ROCKS["mineral_host_rock"][0]).split(","))


def recolour(t: Texture) -> Image.Image:
    """The vanilla icon with each colour moved onto the mineral's ramp by its lightness within its tone group.

    Shape, alpha, pixel positions and the light -> dark structure are the source's; only colours change.
    """
    src = vanilla(t.shape.ref)
    m = MINERALS[t.mineral]
    a = np.asarray(src).copy()
    vis = a[..., 3] > 0
    cols, inv = np.unique(a[vis][:, :3], axis=0, return_inverse=True)
    host = np.array([tuple(c) in {rgb(h) for h in t.shape.host} for c in cols], dtype=bool)
    patina = _patina(cols) & ~host
    patina_ramp = (m.body[0], *m.accent, mix(m.accent[-1], "#ffffff", 0.35)) if m.accent else m.body
    out = np.zeros(cols.shape, dtype=np.float64)
    for sel, tones in ((~patina, m.body), (patina, patina_ramp)):
        if not sel.any():
            continue
        L = _lightness(cols[sel].astype(np.float64))
        span = max(L.max() - L.min(), 1e-6)
        out[sel] = [_ramp_at(tones, (v - L.min()) / span) for v in L]
    # host rock tones: the rock colour as light as the mineral colour they would have had, so every tone keeps
    # its place in the light -> dark order
    if host.any():
        rock = [_ramp_at(host_ramp(), i / 63) for i in range(64)]
        rock_L = _lightness(np.array(rock))
        for i in np.nonzero(host)[0]:
            out[i] = rock[int(np.abs(rock_L - _lightness(out[i:i + 1])[0]).argmin())]
    new = np.clip(np.round(out), 0, 255).astype(np.uint8)
    # keep distinct source colours distinct (a collision would merge two shades)
    seen = set()
    for i in np.argsort(_lightness(cols.astype(np.float64))):
        while tuple(new[i]) in seen:
            new[i] = np.clip(new[i].astype(int) + 1, 0, 255)
        seen.add(tuple(new[i]))
    a[vis, :3] = new[inv.ravel()]
    img = Image.fromarray(a, "RGBA")
    _check_remap(src, img)
    return img


def _check_remap(src: Image.Image, out: Image.Image) -> None:
    """A recolour must keep the silhouette, the alpha and the pixel-to-colour structure exactly."""
    s, o = np.asarray(src), np.asarray(out)
    assert s.shape == o.shape and (s[..., 3] == o[..., 3]).all(), "alpha / silhouette changed"
    vis = s[..., 3] > 0
    pairs = {(tuple(x), tuple(y)) for x, y in zip(s[vis][:, :3], o[vis][:, :3])}
    assert len(pairs) == len({q[0] for q in pairs}) == len({q[1] for q in pairs}), "colour mapping is not one-to-one"


DRAW = {"raw": draw_raw, "crystal": draw_crystal, "powder": draw_powder}


def render(name: str, size: int = 16) -> tuple[Image.Image, Image.Image | None]:
    t = TEXTURES.get(name) or BLOCK_DRAFTS[name]
    if isinstance(t.shape, Recolour):
        img = recolour(t)
        return (img if size == img.width else img.resize((size, size), Image.NEAREST)), None
    m = MINERALS[t.mineral]
    sp = DRAW[t.category](t, size)
    # crystals sit one step up the mineral's ramp with a pale top tone: brighter and more translucent than the
    # raw lump / ingot of the same mineral, same hue family
    body = (*m.body[1:], mix(m.body[4], "#ffffff", 0.45)) if t.category == "crystal" else m.body
    ramps = {"body": body, "matrix": m.matrix, "accent": m.accent, "powder": m.powder_ramp(),
             "glow": (mix(m.body[3], m.glow, 0.55),) if m.glow else ()}
    img, glow = sp.image(ramps)
    if t.kind == "block" and t.category == "raw":
        img = _ground(img)
    return img, (glow if t.kind == "block" else None)


def _ground(img: Image.Image) -> Image.Image:
    a = np.asarray(img)
    rows = np.nonzero(a[..., 3].any(axis=1))[0]
    out = np.zeros_like(a)
    dy = a.shape[0] - 1 - rows.max()
    out[dy:] = a[:a.shape[0] - dy]
    return Image.fromarray(out, "RGBA")


def run(only: set[str] | None = None, size: int = 16, out: str | None = None, dry_run: bool = False,
        quiet: bool = False) -> list[dict]:
    """Render the textures; writes into the assets only at 16x16 without ``out``."""
    if size != 16 and not out and not dry_run:
        raise SystemExit("sizes other than 16 need --out (the mod's textures stay 16x16)")
    report = []
    for name, t in TEXTURES.items():
        if only and name not in only:
            continue
        img, glow = render(name, size)
        folder = out or os.path.join(ASSETS, t.kind)
        path = os.path.join(folder, name + ".png")
        glow_path = os.path.join(folder, name + "_glow.png")
        if not dry_run:
            os.makedirs(folder, exist_ok=True)
            img.save(path)
            if glow is not None:
                glow.save(glow_path)
            elif os.path.exists(glow_path) and not out:
                os.remove(glow_path)
        report.append({"name": name, "category": t.category, "mineral": t.mineral, "kind": t.kind, "size": size,
                       "glow": glow is not None, "path": None if dry_run else os.path.normpath(path)})
    if not quiet:
        print(f"Mineral textures: {len(report)} {'rendered (dry run)' if dry_run else 'written'}")
    return report


def contact_sheet(path: str, names: list[str], sizes=(16, 32, 64), cell: int = 128) -> None:
    from PIL import ImageDraw as D
    cols = len(sizes) + 1
    sheet = Image.new("RGBA", (cols * (cell + 6) + 6, len(names) * (cell + 18) + 6), (139, 139, 139, 255))
    d = D.Draw(sheet)
    for r, name in enumerate(names):
        y = 6 + r * (cell + 18)
        for c, size in enumerate(sizes):
            img, glow = render(name, size)
            sheet.alpha_composite(img.resize((cell, cell), Image.NEAREST), (6 + c * (cell + 6), y))
            if c == 0:
                bg = Image.new("RGBA", (cell, cell), (20, 22, 30, 255))
                bg.alpha_composite((glow or img).resize((cell, cell), Image.NEAREST) if glow else Image.new("RGBA", (cell, cell)))
                sheet.alpha_composite(bg, (6 + len(sizes) * (cell + 6), y))
        t = TEXTURES.get(name) or BLOCK_DRAFTS[name]
        d.text((6, y + cell + 2), f"{name}  [{t.category}/{t.mineral}]", fill=(0, 0, 0, 255))
    sheet.save(path)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--only", help="comma separated texture names")
    ap.add_argument("--size", type=int, default=16, help="16 (default), 32 or 64")
    ap.add_argument("--out", help="output folder (required for sizes other than 16)")
    ap.add_argument("--dry-run", action="store_true", help="render only, write nothing")
    ap.add_argument("--list", action="store_true", help="print the texture table as JSON and exit")
    ap.add_argument("--preview", help="contact sheet (16 | 32 | 64 | glow) of the items and block drafts")
    args = ap.parse_args()
    only = set(args.only.split(",")) if args.only else None
    if args.list:
        print(json.dumps({n: {"category": t.category, "mineral": t.mineral, "kind": t.kind}
                          for n, t in TEXTURES.items()}, indent=2))
        return
    if args.preview:
        contact_sheet(args.preview, [n for n in {**TEXTURES, **BLOCK_DRAFTS} if not only or n in only])
        return
    report = run(only, args.size, args.out, args.dry_run, quiet=True)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
