"""Regenerates Abyssia's block and item textures with Texture Forge (tools/texture_forge).

Two kinds of textures come out of here:

* Recoloured vanilla textures (Texture Forge's ``recolor_source``): rocks and ore hosts are vanilla deepslate,
  sediments vanilla sand / clay, ores the vanilla deepslate ores, building stone the vanilla deepslate building
  blocks - recoloured through Abyssia's own palettes, with Abyssia's decorations drawn on top.  These PNGs are
  derivatives of Mojang's textures (``RECOLOURED`` lists them); keep that in mind before publishing the mod.
* Mineral items (raw lumps and ingots recoloured from vanilla, crystal shards and powders drawn from scratch) come
  from mineral_textures.py, run at the end of ``run``.
* Everything else (plants, crystals, wood, speleothems...) is generated from scratch; the vanilla texture
  is only a *style reference* (tone levels, cluster size, direction, silhouette statistics).  The vanilla textures are read straight from the
client jar in the Gradle cache (set ABYSSIA_MC_JAR to point somewhere else); without it the generator falls back
to its built-in style profiles.

    python tools/forge_textures.py                     # draw the textures whose PNG is missing
    python tools/forge_textures.py --textures all      # old behaviour: redraw every texture
    python tools/forge_textures.py --only deep_sea_rock,cobalt_ore --textures locked-only
    python tools/forge_textures.py --preview sheet.png # also write a contact sheet

``--textures`` (tools/texture_locks.py): ``missing-only`` (default) draws only missing PNGs, ``locked-only`` redraws
every unlocked one, ``all`` redraws everything (the locks are re-applied afterwards).  Locked textures are not even
rendered in the first two modes.  The stone family jobs (polished / bricks / cracked / chiseled) are kept for their
seed slots but never written, and glow overlays are not written either: tools/derive_textures.py rebuilds both from
the current base textures at the end of ``run``.

gen_deep_assets.py runs this after writing the models, so a full regenerate is still one command.  Textures that
are not listed here (tube plants, giant tube, ancient cave plant, abyssal mushroom, frond leaves, debris
carpets, brine surface) keep the procedural pixel art from pixelart.py.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import zipfile
from dataclasses import dataclass, field
from io import BytesIO

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
FORGE = os.path.join(HERE, "texture_forge")
sys.path.insert(0, FORGE)

from core import noise  # noqa: E402
from core import pixel_art as pa  # noqa: E402
from core.generator import TextureGenerator  # noqa: E402
from core.palette import lch_to_oklab, luminance, oklab_to_rgb, rgb_to_hex  # noqa: E402
from core.settings import TextureSettings  # noqa: E402

import building_assets as ba  # noqa: E402
import texture_locks  # noqa: E402
import mineral_textures as mt  # noqa: E402
import importlib, importlib.util  # noqa: E402

REDO_MODULES = ("redo_flora", "redo_formations", "redo_wood_items")

ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures")
MC_VERSION = "1.20.1"


# ================================================================ vanilla references

def find_client_jar() -> str | None:
    env = os.environ.get("ABYSSIA_MC_JAR")
    if env and os.path.isfile(env):
        return env
    home = os.path.expanduser("~")
    patterns = [
        os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", MC_VERSION, "client-extra.jar"),
        os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", MC_VERSION, "client.jar"),
        os.path.join(home, ".gradle", "caches", "**", f"*{MC_VERSION}*client*.jar"),
    ]
    for pattern in patterns:
        for path in glob.glob(pattern, recursive=True):
            try:
                with zipfile.ZipFile(path) as z:
                    if "assets/minecraft/textures/block/stone.png" in z.namelist():
                        return path
            except (OSError, zipfile.BadZipFile):
                continue
    return None


class Vanilla:
    def __init__(self, jar: str | None):
        self.zip = zipfile.ZipFile(jar) if jar else None
        self.names = set(self.zip.namelist()) if self.zip else set()

    def get(self, ref: str | None) -> Image.Image | None:
        """``block/stone`` or ``item/iron_ingot`` -> RGBA image (first animation frame), or None."""
        if not ref or not self.zip:
            return None
        path = f"assets/minecraft/textures/{ref}.png"
        if path not in self.names:
            print(f"  ! vanilla reference {ref} not found, using the built-in profile")
            return None
        im = Image.open(BytesIO(self.zip.read(path))).convert("RGBA")
        return im.crop((0, 0, im.width, im.width)) if im.height > im.width else im


# ================================================================ jobs

@dataclass
class Job:
    ref: str | None                    # vanilla style reference, e.g. "block/stone"
    settings: dict                     # TextureSettings fields
    kind: str = "block"                # block | item
    post: tuple = ()                   # post steps: "flip", "ground", ("cover", fraction)
    frames: int = 0                    # >0: sway animation strip (standing plants)
    hang: bool = False                 # sway anchored at the top instead of the bottom
    extra: dict = field(default_factory=dict)


JOBS: dict[str, Job] = {}
_SEED_GAP = [0]


def job(name: str, ref: str | None, **kw) -> None:
    opts = {k: kw.pop(k) for k in ("kind", "post", "frames", "hang") if k in kw}
    kw.setdefault("seed", 7000 + (len(JOBS) + _SEED_GAP[0]) * 37)
    JOBS[name] = Job(ref, kw, **opts)


def moved_jobs(count: int) -> None:
    """Jobs moved to another generator keep their seed slots, so later default seeds (and textures) stay put."""
    _SEED_GAP[0] += count


# ================================================================ the deepslate rock family
#
# Every rock is vanilla deepslate, recoloured and decorated for its environment.  The base pixels are deepslate's
# own; each rock recolours them through the ancestor ramp (dark blue-gray to desaturated cyan, never black) turned
# a little toward its environment, then adds decorations: crying-obsidian-like crack veins in its environment's
# colour, damp patches, extra bedding planes, alteration patches (secondary ramp: heat staining, volcanic glass,
# mineral and living films), mineral deposits (accent) and, for glowing rocks, emissive vein cores.

# OKLCH anchors of the ancestor ramp, shadow -> highlight
ANCESTOR = [(0.27, 0.016, 268), (0.33, 0.020, 258), (0.39, 0.022, 250), (0.45, 0.020, 244), (0.51, 0.017, 238),
            (0.57, 0.013, 232)]


def _hex(L: float, C: float, h: float) -> str:
    return rgb_to_hex(np.clip(oklab_to_rgb(lch_to_oklab(np.array([L, C, h % 360]))), 0, 255))


def slate_ramp(dl: float = 0.0, chroma: float = 1.0, hue: float | None = None) -> str:
    """The ancestor ramp, lighter / darker by ``dl``, more or less saturated, turned toward ``hue``."""
    return ",".join(_hex(max(0.12, L + dl), C * chroma, h if hue is None else hue + (h - 248)) for L, C, h in ANCESTOR)


def film_ramp(L: float, C: float, h: float, span: float = 0.2) -> str:
    """A short ramp around one OKLCH colour, for alteration patches."""
    return ",".join(_hex(L + (i / 4 - 0.5) * span, C * (1 - 0.3 * abs(i / 4 - 0.5)), h) for i in range(5))


SLATE = dict(category="terrain", material="slate", variant="strata", palette="abyss", levels=5, hue_shift=0.0,
             roughness=0.6, noise=0.4, crystal=0.0, mineral=0.0, layering=0.35, cracks=0.25, moisture=0.2,
             recolor_source=True)

HEAT_FILM = film_ramp(0.34, 0.06, 35)          # red-brown heat alteration
VENT_FILM = film_ramp(0.38, 0.055, 42)         # hydrothermal rust-brown
ORE_FILM = film_ramp(0.40, 0.045, 62)          # ochre mineral film
GLASS_FILM = film_ramp(0.21, 0.012, 290, 0.12)  # volcanic glass, near black but readable

# rock: (tint (dl, chroma, hue), environment settings; crack_color = colour of the crack veins)
ROCKS = {
    # open seabed: the standard deepslate descendant, a little wet, sea-water seeps in its cracks
    "deep_sea_rock": ((0.0, 1.0, None), dict(moisture=0.35, crack_color="#3f8f94")),
    # abyssal plain: darker and bluer
    "abyssal_rock": ((-0.035, 1.5, 262), dict(crack_color="#4a5cc0")),
    # trenches: darker, strongly bedded, more pressure cracks
    "trench_rock": ((-0.045, 1.3, 252), dict(layering=0.85, cracks=0.5, crack_color="#3464a8")),
    # vents and volcanoes: heat-stained, mineral deposits, hot cracks
    "thermal_rock": ((-0.03, 0.5, None), dict(palette="thermal", alteration=0.32, secondary_color=HEAT_FILM,
                                              accent="heat", mineral=0.14, crack_color="#e06a1c")),
    "volcanic_rock": ((-0.055, 0.6, 285), dict(palette="volcanic", alteration=0.35, secondary_color=GLASS_FILM,
                                               accent="#a8321e", mineral=0.12, cracks=0.35, crack_color="#c43a1e")),
    "molten_volcanic_rock": ((-0.055, 0.6, 285), dict(palette="volcanic", alteration=0.3, secondary_color=GLASS_FILM,
                                                      accent="heat", mineral=0.1, cracks=0.7, glow=0.85,
                                                      crack_color="#f07a1a")),
    "vent_rock": ((-0.015, 0.75, None), dict(palette="thermal", alteration=0.22, secondary_color=VENT_FILM,
                                             accent="#d88a38", mineral=0.2, crack_color="#e08a2a")),
    "black_vent_rock": ((-0.075, 0.6, 275), dict(palette="thermal", accent="pyrite", mineral=0.16, crack_color="#bfa548")),
    "sulfur_vent_rock": ((-0.015, 0.75, None), dict(palette="thermal", alteration=0.2, secondary_color=film_ramp(0.42, 0.06, 80),
                                                    accent="sulfur", mineral=0.3, crack_color="#e4cc3a")),
    "mineral_vent_rock": ((-0.015, 0.75, None), dict(palette="thermal", alteration=0.4, secondary_color=VENT_FILM,
                                                     accent="copper", mineral=0.22, crack_color="#c86a3a")),
    # crystal and ore country: crystals and minerals exposed by erosion
    "crystal_rock": ((0.015, 1.15, 222), dict(accent="glow_cyan", mineral=0.28, crack_color="#3fc0d8")),
    "mineral_host_rock": ((-0.01, 0.8, None), dict(alteration=0.25, secondary_color=ORE_FILM, accent="copper",
                                                   mineral=0.26, crack_color="#c8763a")),
    # new biomes (B02 placeholders; replace via tools/import_chatgpt_textures.py)
    "ancient_masonry": ((-0.02, 0.9, 200), dict(layering=0.9, cracks=0.4, moisture=0.3, crack_color="#4a9a98")),
    "fossil_rock": ((0.03, 0.7, 75), dict(layering=0.6, alteration=0.3, secondary_color=film_ramp(0.5, 0.05, 80),
                                           accent="#e8e0cc", mineral=0.22, crack_color="#d8ceb0")),
    "salt_rock": ((0.1, 0.5, 350), dict(alteration=0.3, secondary_color=film_ramp(0.62, 0.05, 350), accent="#ffffff",
                                         mineral=0.3, crack_color="#e8a8b8")),
    "lumen_rock": ((-0.03, 1.4, 170), dict(accent="glow_cyan", mineral=0.26, glow=0.35, glow_out=True, crack_color="#3fe0b0")),
    "frozen_rock": ((0.05, 1.2, 225), dict(moisture=0.4, accent="glow_cyan", mineral=0.2, cracks=0.4, crack_color="#c0e8ff")),
    # caves: very dark, toward blue-violet, wet
    "abyssal_cave_rock": ((-0.06, 1.4, 272), dict(moisture=0.5, cracks=0.3, crack_color="#6a4ac0")),
    "dark_cave_rock": ((-0.08, 0.9, 285), dict(moisture=0.4, cracks=0.35, crack_color="#5a3a9a")),
    "wet_cave_rock": ((-0.05, 1.4, 256), dict(moisture=0.9, layering=0.2, crack_color="#3a8aa8")),
    "layered_cave_rock": ((-0.06, 1.2, 272), dict(layering=0.95, moisture=0.45, crack_color="#5a5ab8")),
    "mineral_cave_rock": ((-0.06, 1.0, 275), dict(moisture=0.4, alteration=0.22, secondary_color=VENT_FILM,
                                                  accent="copper", mineral=0.26, crack_color="#c8763a")),
    "thermal_cave_rock": ((-0.065, 0.6, None), dict(palette="thermal", alteration=0.28, secondary_color=film_ramp(0.3, 0.06, 32),
                                                    accent="heat", mineral=0.12, cracks=0.5, glow=0.6, glow_out=True,
                                                    crack_color="#f07a1a")),
    "crystal_cave_rock": ((-0.06, 1.3, 240), dict(accent="glow_cyan", mineral=0.3, glow=0.45, cracks=0.35, glow_out=True,
                                                  crack_color="#3fd0e8")),
    "organic_cave_rock": ((-0.06, 1.1, 225), dict(moisture=0.55, alteration=0.38, secondary_color=film_ramp(0.32, 0.05, 150),
                                                  accent="moss", mineral=0.14, crack_color="#4aa860")),
    "eroded_cave_rock": ((-0.045, 1.2, 225), dict(moisture=0.6, cracks=0.15, layering=0.45, crack_color="#4a9aa0")),
    # the vent chimney block itself: black vent rock split by glowing hot veins
    "thermal_vent": ((-0.075, 0.6, 275), dict(palette="thermal", alteration=0.3, secondary_color=HEAT_FILM, accent="heat",
                                              mineral=0.15, cracks=0.9, glow=0.9, crack_color="#f08a24")),
}


def rock_settings(name: str, **changes) -> dict:
    (dl, chroma, hue), env = ROCKS[name]
    d = dict(SLATE, base_color=slate_ramp(dl, chroma, hue), seed=900 + sum(map(ord, name)))
    d.update(env)
    d.update(changes)
    return d


for _name in ROCKS:
    job(_name, "block/deepslate", **rock_settings(_name))
job("volcanic_glass", "block/obsidian", category="crystal", variant="block", palette="crystal",
    base_color="#1c1628", accent="#3a2e52", facet=0.7, glow=0.0)

# ---------------------------------------------------------------- sediments: recoloured vanilla sand / clay
# Granular sediments are sand, fine muds and oozes are clay; each keeps its own colours and gets its own grains.
SEDIMENTS = {
    # name: (base, ramp dark -> light, settings)
    "deep_sediment": ("sand", "#3b4148,#474e56,#545c65,#626b74", dict(palette="deep_ocean")),
    "abyssal_mud": ("clay", "#1f2229,#282c35,#323742,#3d4350", dict(palette="abyss", moisture=0.5)),
    "deep_mud": ("clay", "#22302f,#2b3b3a,#354846,#405553", dict(palette="deep_ocean", moisture=0.5)),
    "mineral_sediment": ("sand", "#5a3a24,#6e4a2e,#83593a,#9a6b48", dict(palette="ancient", accent="rust", mineral=0.22)),
    "crystal_sediment": ("sand", "#3d5a6a,#4b6d80,#5b8297,#6d98ad", dict(palette="crystal", accent="glow_cyan", mineral=0.2)),
    "organic_sediment": ("clay", "#22301f,#2c3d27,#374a30,#43583a", dict(palette="organic", accent="moss", mineral=0.14,
                                                                          moisture=0.5)),
    "volcanic_ash": ("sand", "#3f3c3c,#4c4948,#5a5655,#6a6564", dict(palette="volcanic", accent="#a8321e", mineral=0.08)),
    "sulfur_deposit": ("sand", "#8a7a18,#b09a22,#d2bc30,#ecd850", dict(palette="thermal", accent="sulfur", mineral=0.3)),
    "black_mineral_deposit": ("sand", "#14171c,#1c2027,#252a33,#30363f", dict(palette="abyss", accent="manganese",
                                                                               mineral=0.3)),
    # new biomes (B02 placeholders)
    "ruin_gravel": ("sand", "#3c4a4c,#4a5c5e,#5a6f70,#6c8383", dict(palette="cold")),
    "ruin_sediment": ("sand", "#34464a,#425659,#52686b,#647d7f", dict(palette="cold", moisture=0.2)),
    "bone_sediment": ("sand", "#8a8270,#a59c86,#c0b8a0,#dcd4bc", dict(palette="ancient", accent="#f6f0dc", mineral=0.18)),
    "fossil_silt": ("clay", "#6a6252,#7e7563,#948a76,#aaa08a", dict(palette="ancient", accent="#e8e0cc", mineral=0.12)),
    "salt_crust": ("sand", "#b4a8aa,#cec2c4,#e4d8d8,#f6eeee", dict(palette="cold", accent="#ffffff", mineral=0.2)),
    "brine_silt": ("clay", "#a88a90,#be9fa4,#d2b4b8,#e4c8ca", dict(palette="cold", accent="#ffe0e8", mineral=0.12, moisture=0.4)),
    "lumen_sand": ("sand", "#2a6a68,#358078,#43988a,#58b09c", dict(palette="crystal", accent="glow_cyan", mineral=0.22)),
    "glow_silt": ("clay", "#1c4a44,#255c52,#307064,#3e8676", dict(palette="organic", accent="glow_cyan", mineral=0.16, moisture=0.5)),
    "frost_silt": ("clay", "#7a94aa,#92acc0,#aac4d6,#c4dcea", dict(palette="cold", accent="#ffffff", mineral=0.14)),
    "icy_sediment": ("sand", "#5c7a94,#7090aa,#88a8c0,#a4c4d8", dict(palette="cold", accent="glow_cyan", mineral=0.18)),
    "cave_sediment": ("sand", "#3f454d,#4b525b,#59616b,#69717c", dict(palette="cold")),
    "cave_mud": ("clay", "#231a14,#2e231b,#3a2d23,#47382c", dict(palette="organic", moisture=0.55)),
}
for _name, (_base, _ramp, _kw) in SEDIMENTS.items():
    job(_name, f"block/{_base}", **dict(dict(category="terrain", material="sediment", variant="smooth", levels=4,
                                             hue_shift=0.0, recolor_source=True, base_color=_ramp, cracks=0.0,
                                             layering=0.0, moisture=0.0, mineral=0.0, crystal=0.0,
                                             seed=900 + sum(map(ord, _name))), **_kw))

# ---------------------------------------------------------------- ores: the vanilla deepslate ores, recoloured
# Host pixels (deepslate's own colours) take the host rock's ramp, ore pixels the ore colour; nothing else changes.
ORES = {
    # ore: (vanilla deepslate ore, host rock, ore colour / accent preset)
    "abyssal_iron_ore": ("block/deepslate_iron_ore", "abyssal_rock", "#c08a62"),
    "deep_copper_ore": ("block/deepslate_copper_ore", "deep_sea_rock", "copper"),
    "sulfur_ore": ("block/deepslate_gold_ore", "vent_rock", "sulfur"),
    "thermal_crystal_ore": ("block/deepslate_redstone_ore", "thermal_rock", "heat"),
    "abyssal_crystal_ore": ("block/deepslate_lapis_ore", "abyssal_rock", "amethyst"),
    "manganese_ore": ("block/deepslate_coal_ore", "abyssal_rock", "#6a5a78"),
    "cobalt_ore": ("block/deepslate_diamond_ore", "trench_rock", "#3a6ad8"),
    "deep_nickel_ore": ("block/deepslate_emerald_ore", "mineral_host_rock", "#a4b88a"),
}
DEEPSLATE_COLOURS = "#2F2F37,#3D3D43,#515151,#646464,#797979"   # vanilla deepslate's five tones (the ore host)
# Rare-metal ores (M01): same recolour; their jobs are added after the building blocks (end of the job list) so
# the default seed slots of every earlier texture stay put.
RARE_ORES = {
    "platinum_ore": ("block/deepslate_gold_ore", "trench_rock", "#c8ccd4"),      # bright grey-white
    "tellurium_ore": ("block/deepslate_lapis_ore", "trench_rock", "#8a9cb4"),    # silvery blue-grey
    "molybdenum_ore": ("block/deepslate_iron_ore", "abyssal_rock", "#6a7a98"),   # lead blue
    "vanadium_ore": ("block/deepslate_emerald_ore", "abyssal_rock", "#6a9a94"),  # blue-green grey
    "tungsten_ore": ("block/deepslate_coal_ore", "trench_rock", "#5a5e66"),      # dark grey
    "yttrium_ore": ("block/deepslate_redstone_ore", "abyssal_rock", "#c8c2a4"),  # pale yellow-white grey
    # ECO02 (placeholders until the ChatGPT sheet, inbox/prompts/ORE02-textures.md)
    "titanium_ore": ("block/deepslate_iron_ore", "abyssal_rock", "#8a9aab"),     # steel blue
    "lead_ore": ("block/deepslate_coal_ore", "mineral_host_rock", "#6d7384"),    # dull blue-grey
    "zinc_ore": ("block/deepslate_lapis_ore", "thermal_rock", "#98b0b8"),        # bluish white
    "iridium_ore": ("block/deepslate_gold_ore", "trench_rock", "#c0c4cc"),       # silver-white
    "uranium_ore": ("block/deepslate_emerald_ore", "abyssal_rock", "#86b03e"),   # yellow-green
    "neodymium_ore": ("block/deepslate_redstone_ore", "abyssal_rock", "#9a82b8"),  # violet
    "thorium_ore": ("block/deepslate_emerald_ore", "trench_rock", "#86a08c"),    # grey-green
}


def _ore_job(name: str, ref: str, host: str, ore: str) -> None:
    (dl, chroma, hue), env = ROCKS[host]
    job(name, ref, category="ore", material="slate", variant="cluster", palette=env.get("palette", "abyss"),
        levels=5, hue_shift=0.0, recolor_source=True, source_host=DEEPSLATE_COLOURS,
        base_color=slate_ramp(dl, chroma, hue), accent=ore, cracks=0.0, layering=0.0, moisture=0.0,
        alteration=0.0, mineral=0.0, glow=0.0, seed=900 + sum(map(ord, name)))


for _name, (_ref, _host, _ore) in ORES.items():
    _ore_job(_name, _ref, _host, _ore)

# ---------------------------------------------------------------- mineral crusts
# Vanilla Raw Ore Blocks, colours remapped only: mineral_textures.py.
moved_jobs(6)

# ---------------------------------------------------------------- crystal masses (emissive overlays)
job("deep_crystal_block", "block/amethyst_block", category="crystal", variant="block", palette="deep_ocean",
    accent="#2aa8c2", glow=0.3)
for _name, _col in {"cyan_crystal_block": "#2aa8c2", "blue_crystal_block": "#3a6ad8",
                    "violet_crystal_block": "#8a52d4", "green_crystal_block": "#5ad08a",
                    "white_crystal_block": "#c8dcf0", "amber_crystal_block": "#e89030"}.items():
    job(_name, "block/amethyst_block", category="crystal", variant="block", palette="crystal", accent=_col,
        glow=0.55, glow_out=True)

# ---------------------------------------------------------------- crystal needle (cross sprite)
# The crystal clusters and thermal buds come from mineral_textures.py (Amethyst-Cluster-style, small / medium / large).
moved_jobs(10)
CLUSTERS = {
    "crystal_needle": ("block/pointed_dripstone_up_tip", "shard", "cold", "#9fe0f4", 0.2, 1.0),
}
for _name, (_ref, _var, _theme, _acc, _glow, _h) in CLUSTERS.items():
    job(_name, _ref, category="crystal", variant=_var, palette=_theme, accent=_acc, glow=_glow, height=_h)
# Manganese nodules: a rounded lump lying on the seabed.
job("manganese_nodules", "item/raw_iron", category="item", variant="raw", base_color="#3a2e38",
    accent="manganese", mineral=0.25, post=("ground",))


# ---------------------------------------------------------------- speleothems
SPELEOTHEMS = {
    # name: (base colour, theme, glow, mineral, accent)
    "abyssal_stalactite": (slate_ramp(-0.03, 1.4, 265), "abyss", 0.0, 0.0, ""),
    "mineral_stalactite": ("#76502e", "ancient", 0.0, 0.25, "rust"),
    "crystal_stalactite": ("#1f8aa4", "deep_ocean", 0.55, 0.0, ""),
    "thermal_stalactite": ("#5a2212", "thermal", 0.5, 0.1, "heat"),
}
for _name, (_base, _theme, _glow, _min, _acc) in SPELEOTHEMS.items():
    for _thick in ("tip_merge", "tip", "frustum", "middle", "base"):
        for _dir in ("down", "up"):
            job(f"{_name}_{_dir}_{_thick}", f"block/pointed_dripstone_{_dir}_{_thick}", category="dripstone",
                variant=_thick, part=_dir, base_color=_base, palette=_theme, glow=_glow, mineral=_min,
                accent=_acc, seed=900 + sum(map(ord, _name)), glow_out=_glow > 0)


# ---------------------------------------------------------------- plants: single cross sprites
def plant(name, ref, variant, base, palette="organic", glow=0.0, **kw):
    job(name, ref, category="plant", variant=variant, base_color=base, palette=palette, glow=glow,
        glow_out=glow > 0, **kw)


plant("glowtip_grass", "block/grass", "grass", "#1a5a62", "bioluminescent", 0.7)
plant("sea_fern", "block/fern", "fern", "#28684a", "deep_ocean")
plant("sponge_plant", "block/brain_coral", "bush", "#b47226", "organic", leaf=0.7)
plant("crystal_plant", "block/allium", "bulb", "#1c5038", "bioluminescent", 0.7, accent="glow_cyan")
plant("vent_grass", "block/grass", "grass", "#7e7a1c", "thermal")
plant("glow_anemone", "block/sea_pickle", "grass", "#8a2a5a", "crystal", 0.75, height=0.45)
plant("glow_coral", "block/fire_coral", "bush", "#9a8418", "volcanic", 0.55)
plant("abyssal_bloom", "block/cornflower", "bulb", "#1a3a4a", "bioluminescent", 0.7, accent="#4a90c8")
plant("soul_coral", "block/tube_coral", "bush", "#a0c0cc", "bioluminescent", 0.45)
plant("black_coral", "block/dead_brain_coral", "bush", "#2a2a34", "abyss")
plant("hadal_bloom", "block/allium", "bulb", "#2a1a3a", "crystal", 0.75, accent="#6a40a8")
plant("floating_bloom", "block/blue_orchid", "bulb", "#1f3a52", "bioluminescent", 0.7, accent="#4a90c8")
plant("cave_fern", "block/fern", "fern", "#215a3f", "deep_ocean", seed=411)
plant("cave_coral", "block/fire_coral", "bush", "#96421c", "thermal")
plant("cave_sponge", "block/horn_coral", "bush", "#9c8424", "organic", leaf=0.7)
plant("cave_crystal_plant", "block/allium", "bulb", "#17432f", "bioluminescent", 0.7, accent="#2aa8c2", seed=515)
plant("cave_bloom", "block/cornflower", "bulb", "#1a3a4a", "bioluminescent", 0.8, accent="#58aee0", seed=517)
plant("thermal_plant", "block/grass", "grass", "#1f4224", "thermal", 0.6, height=0.55)
plant("wall_fern", "block/fern", "fern", "#215a3f", "deep_ocean", seed=419)


# ---------------------------------------------------------------- plants: stacking (body tiles vertically, top ends)
def stacking(name, variant, base, palette="organic", ref="block/kelp_plant", top_ref="block/kelp", glow=0.0,
             frames=0, hang=False, **kw):
    post = ("flip",) if hang else ()
    tip = "_tip" if hang else "_top"
    seed = kw.pop("seed", 3100 + sum(map(ord, name)))
    job(name, ref, category="kelp", variant=variant, part="stalk", base_color=base, palette=palette, seed=seed,
        frames=frames, hang=hang, post=post, **kw)
    job(name + tip, top_ref, category="kelp", variant=variant, part="top", base_color=base, palette=palette,
        seed=seed, glow=glow, glow_out=glow > 0, frames=frames, hang=hang, post=post, **kw)


GRASS = dict(ref="block/tall_seagrass_bottom", top_ref="block/tall_seagrass_top")
stacking("abyssal_grass", "seagrass", "#22583f", "organic", **GRASS)
stacking("teal_abyssal_grass", "seagrass", "#1a5a62", "bioluminescent", **GRASS)
stacking("violet_abyssal_grass", "seagrass", "#3c2a70", "crystal", **GRASS)
stacking("ashen_abyssal_grass", "seagrass", "#414d46", "cold", **GRASS)
stacking("cave_grass", "seagrass", "#1b452f", "organic", frames=4, **GRASS)
stacking("mineral_vine", "thermal", "#9a622a", "thermal")
stacking("deep_kelp", "deep", "#28582c", "deep_ocean")
stacking("giant_kelp", "giant", "#5a561c", "ancient")
stacking("cave_kelp", "deep", "#1e4a28", "deep_ocean", frames=4)
stacking("giant_cave_kelp", "giant", "#40491f", "ancient", frames=4)
stacking("crystal_kelp", "abyssal", "#146a7a", "deep_ocean", glow=0.7)
# Void kelp: the top block is "void_kelp", the stalk "void_kelp_plant".
job("void_kelp_plant", "block/kelp_plant", category="kelp", variant="abyssal", part="stalk", base_color="#28183c",
    palette="trench", seed=3777)
job("void_kelp", "block/kelp", category="kelp", variant="abyssal", part="top", base_color="#28183c",
    palette="trench", seed=3777)
# Hanging plants: the same strands, flipped so the free end hangs down.
ROOTS = dict(ref="block/hanging_roots", top_ref="block/hanging_roots")
stacking("cave_vine", "seagrass", "#245033", "organic", glow=0.7, hang=True, ref="block/cave_vines_plant",
         top_ref="block/cave_vines")
stacking("hanging_kelp", "deep", "#1e4a28", "deep_ocean", frames=4, hang=True)
stacking("cave_root", "seagrass", "#4a3122", "ancient", hang=True, density=0.8, leaf=0.1, **ROOTS)
stacking("abyssal_vine", "seagrass", "#382462", "crystal", glow=0.7, hang=True, ref="block/weeping_vines_plant",
         top_ref="block/weeping_vines")
stacking("deep_root", "seagrass", "#4a3122", "ancient", hang=True, density=0.35, leaf=1.0, **ROOTS)
job("wall_mineral_vine", "block/vine", category="kelp", variant="seagrass", part="top", base_color="#9a622a",
    palette="thermal", density=0.7, post=("flip",))

# ---------------------------------------------------------------- films and carpets (tool texture, cut to a cover)
job("abyssal_moss", "block/moss_block", category="organic", variant="moss", palette="organic", base_color="#285234",
    post=(("cover", 0.72),))
job("heat_moss", "block/moss_block", category="organic", variant="moss", palette="thermal", base_color="#7a2c10",
    accent="#9aa83a", post=(("cover", 0.75),))
job("cave_moss", "block/moss_block", category="organic", variant="moss", palette="organic", base_color="#20462e",
    post=(("cover", 0.6),))
job("luminous_moss", "block/glow_lichen", category="organic", variant="moss", palette="bioluminescent",
    base_color="#16343f", glow=0.6, glow_out=True, post=(("cover", 0.5),))

# ---------------------------------------------------------------- ancient deep-sea plant (wood)
WOOD = dict(category="wood", palette="ancient", base_color=ba.WOOD["wood"], secondary_color=ba.WOOD["bark"],
            accent=ba.WOOD["fittings"], mineral=0.0, seed=4242)
job("ancient_stem", "block/oak_log", variant="log", **WOOD)
job("ancient_stem_top", "block/oak_log_top", variant="log_top", **WOOD)
job("ancient_root", "block/mangrove_log", category="wood", variant="log", palette="ancient",
    base_color="#5c3e2c", secondary_color="#472f21", organic=0.8, roughness=0.8, seed=4243)

# ---------------------------------------------------------------- items
# Raw lumps, ingots, crystal shards and the sulfur powder: mineral_textures.py.
moved_jobs(9)

# ---------------------------------------------------------------- building blocks
# Building stone: vanilla deepslate's building blocks (polished, bricks, cracked bricks, chiseled), recoloured
# through the family's rock ramp (thermal and ore stone slightly tinted so their blocks keep their colour).
BUILDING_TINT = {"thermal_rock": (-0.03, 1.1, 28), "mineral_host_rock": (-0.01, 1.0, 62)}


def _building_jobs() -> None:
    for fam in ba.STONE_FAMILIES:
        (dl, chroma, hue), env = ROCKS[fam.rock]
        dl, chroma, hue = BUILDING_TINT.get(fam.rock, (dl, chroma, hue))
        common = dict(category="terrain", material="slate", variant="strata", palette=env.get("palette", "abyss"),
                      levels=5, hue_shift=0.0, recolor_source=True, base_color=slate_ramp(dl, chroma, hue),
                      cracks=0.0, layering=0.0, moisture=0.0, alteration=0.0, mineral=0.0, glow=0.0,
                      seed=5000 + sum(map(ord, fam.rock)))
        job(fam.polished, "block/polished_deepslate", **common)
        job(fam.bricks, "block/deepslate_bricks", **common)
        job(fam.cracked_bricks, "block/cracked_deepslate_bricks", **common)
        job(fam.chiseled, "block/chiseled_deepslate", **common)
    w = ba.WOOD["name"]
    job(f"stripped_{w}_stem", "block/stripped_oak_log", variant="stripped_log", **WOOD)
    job(f"stripped_{w}_stem_top", "block/stripped_oak_log_top", variant="stripped_log_top", **WOOD)
    job(f"{w}_planks", "block/oak_planks", variant="planks", **WOOD)
    job(f"{w}_door_top", "block/oak_door_top", variant="door_top", **WOOD)
    job(f"{w}_door_bottom", "block/oak_door_bottom", variant="door_bottom", **WOOD)
    job(f"{w}_trapdoor", "block/oak_trapdoor", variant="trapdoor", **WOOD)
    job(f"{w}_door", "item/oak_door", kind="item", variant="door_item", **WOOD)


_building_jobs()
for _name, (_ref, _host, _ore) in RARE_ORES.items():
    _ore_job(_name, _ref, _host, _ore)

# Textures whose pixels are recoloured vanilla textures (derivatives of Mojang's art).
RECOLOURED = sorted([n for n, j in JOBS.items() if j.settings.get("recolor_source")] + mt.RECOLOURED)


# ================================================================ generation

def glow_mask(res) -> Image.Image:
    """Emissive overlay: the generator's emission, else the brightest glow / accent pixels."""
    if res.emission is not None and np.asarray(res.emission)[..., 3].any():
        return res.emission
    a = np.asarray(res.image).copy()
    rgb = a[..., :3]
    vis = a[..., 3] > 0
    cols = [tuple(c) for c in res.palette.ramp("glow").colors] + [tuple(c) for c in res.palette.ramp("accent").colors[-2:]]
    pick = np.zeros(vis.shape, dtype=bool)
    for c in cols:
        pick |= (rgb == np.array(c, dtype=np.uint8)).all(-1)
    pick &= vis
    if not pick.any() and vis.any():
        lum = luminance(rgb.reshape(-1, 3).astype(np.float64)).reshape(vis.shape)
        pick = vis & (lum >= np.quantile(lum[vis], 0.94))
    out = np.zeros_like(a)
    out[pick] = a[pick]
    return Image.fromarray(out, "RGBA")


def cover(img: Image.Image, fraction: float, seed: int) -> Image.Image:
    """Cut an opaque tile down to ``fraction`` coverage with a tileable blob mask (moss films and carpets)."""
    a = np.asarray(img).copy()
    rng = np.random.default_rng(seed)
    f = noise.fbm(a.shape[1], a.shape[0], rng, 4.0, 2, 0.5, "value")
    keep = f >= np.quantile(f, 1 - fraction)
    keep = pa.remove_small_clusters(keep.astype(np.int32), 3, True).astype(bool)
    a[~keep] = 0
    return Image.fromarray(a, "RGBA")


def ground(img: Image.Image) -> Image.Image:
    """Move a sprite down so it rests on the bottom edge."""
    a = np.asarray(img)
    rows = np.nonzero(a[..., 3].any(axis=1))[0]
    if not len(rows):
        return img
    out = np.zeros_like(a)
    dy = a.shape[0] - 1 - rows.max()
    out[dy:] = a[:a.shape[0] - dy]
    return Image.fromarray(out, "RGBA")


def sway(img: Image.Image, frames: int, hang: bool, amplitude: float = 1.0) -> list[Image.Image]:
    """Frames of a plant swaying: a horizontal shear growing away from its anchored end."""
    a = np.asarray(img)
    h = a.shape[0]
    out = []
    for k in range(frames):
        phase = np.sin(2 * np.pi * k / frames)
        f = np.zeros_like(a)
        for y in range(h):
            t = (y / (h - 1)) if hang else (1 - y / (h - 1))
            dx = int(round(amplitude * phase * t))
            f[y] = np.roll(a[y], dx, axis=0)
            if dx > 0:
                f[y, :dx] = 0
            elif dx < 0:
                f[y, dx:] = 0
        out.append(Image.fromarray(f, "RGBA"))
    return out


def render(gen: TextureGenerator, vanilla: Vanilla, name: str, j: Job):
    settings = dict(j.settings)
    glow_out = settings.pop("glow_out", False)   # also write <name>_glow.png (emissive overlay)
    s = TextureSettings.from_dict(dict(name=name, **settings))
    res = gen.generate(vanilla.get(j.ref), s)
    img, glow = res.image, (glow_mask(res) if glow_out else None)
    for step in j.post:
        if step == "flip":
            img = img.transpose(Image.FLIP_TOP_BOTTOM)
            glow = glow.transpose(Image.FLIP_TOP_BOTTOM) if glow else None
        elif step == "ground":
            img = ground(img)
        elif isinstance(step, tuple) and step[0] == "cover":
            img = cover(img, step[1], s.seed)
            if glow is not None:
                keep = np.asarray(img)[..., 3] > 0
                g = np.asarray(glow).copy()
                g[~keep] = 0
                glow = Image.fromarray(g, "RGBA")
    return img, glow, res


def save_strip(frames: list[Image.Image], path: str, frametime: int = 12) -> None:
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * h))
    strip.save(path)
    with open(path + ".mcmeta", "w", encoding="utf-8") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f, indent=2)
        f.write("\n")


def run(only: set[str] | None = None, preview: str | None = None, quiet: bool = False) -> list[str]:
    jar = find_client_jar()
    vanilla = Vanilla(jar)
    if not quiet:
        print(f"Texture Forge: vanilla references from {jar or '(none - built-in profiles)'}")
    gen = TextureGenerator()
    written = []
    shots = []
    for name, j in JOBS.items():
        if only and name not in only:
            continue
        folder = os.path.join(ASSETS, j.kind)
        path = os.path.join(folder, name + ".png")
        if not texture_locks.wants(path):
            continue                                   # locked, derived or kept as committed
        img, glow, res = render(gen, vanilla, name, j)
        os.makedirs(folder, exist_ok=True)
        if j.frames:
            save_strip(sway(img, j.frames, j.hang), path)
        else:
            img.save(path)
            if os.path.exists(path + ".mcmeta"):
                os.remove(path + ".mcmeta")
        # glow overlays: derive_textures.py (texture_locks.save skips derived files)
        written.append(name)
        shots.append((name, vanilla.get(j.ref), img, glow))
    if preview:
        contact_sheet(shots, preview)
    if not quiet:
        print(f"Texture Forge: {len(written)} textures written")
    written += [r["name"] for r in mt.run(only, quiet=quiet)]
    # Hand-finished redo modules (vanilla-based, replace the procedural look): tools/redo_*.py, each exposes run(quiet).
    for mod in REDO_MODULES:
        if importlib.util.find_spec(mod) is not None:
            written += list(importlib.import_module(mod).run(quiet=quiet) or [])
    # Approved textures are frozen: whatever the jobs produce, the locked files win (tools/texture_locks.py).
    texture_locks.apply(quiet)
    # Stone family variants, crust bricks and glow overlays follow the current base textures.
    import derive_textures
    derive_textures.run(quiet=quiet)
    # Texture Studio specs (tools/texture_studio) are the user's hand-made textures: they win over every generator.
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'texture_studio'))
    import texture_engine as _studio
    _studio.apply_all(True)
    return written


def contact_sheet(shots, path: str, cols: int = 8, S: int = 64) -> None:
    from PIL import ImageDraw
    rows = (len(shots) + cols - 1) // cols
    cell_w = S * 3 + 16
    sheet = Image.new("RGBA", (cols * cell_w, rows * (S + 22)), (58, 58, 68, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, ref, img, glow) in enumerate(shots):
        x, y = (i % cols) * cell_w + 4, (i // cols) * (S + 22) + 4
        for k, im in enumerate((ref, img, glow)):
            bg = Image.new("RGBA", (S, S), (36, 36, 44, 255))
            if im is not None:
                bg.alpha_composite(im.crop((0, 0, im.width, im.width)).resize((S, S), Image.NEAREST))
            sheet.paste(bg, (x + k * (S + 2), y))
        d.text((x, y + S + 3), name[:30], fill=(235, 235, 235, 255))
    sheet.save(path)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--only", help="comma separated texture names")
    ap.add_argument("--preview", help="write a contact sheet (reference | texture | glow) to this PNG")
    ap.add_argument("--textures", choices=texture_locks.MODES, default=texture_locks.MODE,
                    help="which existing PNGs to redraw (tools/texture_locks.py)")
    args = ap.parse_args()
    texture_locks.set_mode(args.textures)
    only = set(args.only.split(",")) if args.only else None
    run(only, args.preview)


if __name__ == "__main__":
    main()
