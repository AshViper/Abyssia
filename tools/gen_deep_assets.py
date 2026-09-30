"""Generates every Abyssia block/item asset: original pixel-art textures, models, blockstates, loot tables,
tags, smelting recipes and translations.

Run from anywhere:  python tools/gen_deep_assets.py [--no-forge] [--textures missing-only|locked-only|all]
Textures are first drawn procedurally (tools/pixelart.py), then regenerated with Texture Forge by forge_textures.py
(vanilla textures as style references; --no-forge skips that pass).  Everything is deterministic per name, so
re-running only changes what you edit.  Building blocks (stone families, ancient wood) come from building_assets.py.

The committed textures are the source of truth: with the default ``--textures missing-only`` only textures whose PNG
does not exist yet are drawn, ``locked-only`` redraws every unlocked texture, ``all`` is the old redraw-everything
(see tools/texture_locks.py).  Locked textures are never drawn (except with ``all``, then restored), and derived
textures (stone family variants, crust bricks / polished crusts, *_glow overlays) always come from
tools/derive_textures.py, run at the end.  Blockstates, models, block loot tables and recipes are wiped and rewritten
each run, so removed blocks leave no JSON behind; the textures folders are never wiped.
"""
import json
import os
import shutil
import sys

from PIL import Image

import building_assets
import cave_assets
import gen_fauna
import mineral_textures
import plant_assets
import texture_locks

from pixelart import (Canvas, N, blade, branch, darken, disc, facets, flecks, glow_mask, hexrgb, item_outline, lighten,
                      mud, ore, palette, rng_for, rock, sediment, shard, veined)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "abyssia")
DATA = os.path.join(ROOT, "data")
BLOCK_TEX = os.path.join(ASSETS, "textures", "block")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
PARTICLE_TEX = os.path.join(ASSETS, "textures", "particle")


_save = texture_locks.save       # every texture write goes through the lock / --textures policy


def _emit(make, path):
    """Draw and write a texture only when the policy wants it (``make`` is not called otherwise)."""
    if texture_locks.wants(path):
        texture_locks.save(make(), path)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


P = palette

# ================================================================ palettes

ROCK = {
    "deep_sea_rock": P("#2c3440", "#3a4452", "#4a5564", "#5b6776", "#6e7b89"),
    "abyssal_rock": P("#15161f", "#1f2130", "#2a2d40", "#363a50", "#444962"),
    "trench_rock": P("#141c28", "#1c2838", "#253548", "#30435a", "#3d526a"),
    "thermal_rock": P("#2a0f0c", "#3c1712", "#52201a", "#6a2c20", "#7f3a26"),
    "volcanic_rock": P("#1b1a1c", "#252427", "#302f33", "#3c3b40", "#4a4950"),
    "crystal_rock": P("#4a5a6e", "#5b6e84", "#6f849a", "#85a0b4", "#a3c2d2"),
    "mineral_host_rock": P("#3a2e28", "#4a3b32", "#5c4a3e", "#6e5a4a", "#82705c"),
    "vent_rock": P("#5b5a57", "#6c6a66", "#7e7c77", "#918e88", "#a6a39c"),
    "black_vent_rock": P("#0e0f12", "#16181c", "#1f2228", "#2a2e36", "#384050"),
    "mineral_vent_rock": P("#4f2e1c", "#653a22", "#7c4a2c", "#93593a", "#a86c48"),
    # new biomes (B02)
    "ancient_masonry": P("#2c3a3e", "#3a4c50", "#4b6064", "#5e777a", "#748f90"),
    "fossil_rock": P("#5a5346", "#726a5a", "#8c8370", "#a89f8a", "#c4bca8"),
    "salt_rock": P("#8a7e80", "#a89ca0", "#c4b8bb", "#dcd0d2", "#f0e6e6"),
    "lumen_rock": P("#123a3a", "#1a4e48", "#256658", "#348068", "#4a9c7c"),
    "frozen_rock": P("#4a5e74", "#5f7690", "#7a92ac", "#98b0c6", "#b8d0e2"),
}
SED = {
    "deep_sediment": P("#3b4148", "#474e56", "#545c65", "#626b74"),
    "abyssal_mud": P("#1f2229", "#282c35", "#323742", "#3d4350"),
    "deep_mud": P("#22302f", "#2b3b3a", "#354846", "#405553"),
    "mineral_sediment": P("#5a3a24", "#6e4a2e", "#83593a", "#9a6b48"),
    "crystal_sediment": P("#3d5a6a", "#4b6d80", "#5b8297", "#6d98ad"),
    "organic_sediment": P("#22301f", "#2c3d27", "#374a30", "#43583a"),
    "volcanic_ash": P("#3f3c3c", "#4c4948", "#5a5655", "#6a6564"),
    "sulfur_deposit": P("#8a7a18", "#b09a22", "#d2bc30", "#ecd850"),
    # new biomes (B02)
    "ruin_gravel": P("#3c4a4c", "#4a5c5e", "#5a6f70", "#6c8383"),
    "ruin_sediment": P("#34464a", "#425659", "#52686b", "#647d7f"),
    "bone_sediment": P("#8a8270", "#a59c86", "#c0b8a0", "#dcd4bc"),
    "fossil_silt": P("#6a6252", "#7e7563", "#948a76", "#aaa08a"),
    "salt_crust": P("#b4a8aa", "#cec2c4", "#e4d8d8", "#f6eeee"),
    "brine_silt": P("#a88a90", "#be9fa4", "#d2b4b8", "#e4c8ca"),
    "lumen_sand": P("#2a6a68", "#358078", "#43988a", "#58b09c"),
    "glow_silt": P("#1c4a44", "#255c52", "#307064", "#3e8676"),
    "frost_silt": P("#7a94aa", "#92acc0", "#aac4d6", "#c4dcea"),
    "icy_sediment": P("#5c7a94", "#7090aa", "#88a8c0", "#a4c4d8"),
}
MINERAL = {
    "iron": P("#4a2418", "#7a3e26", "#a8603a", "#d09a70", "#f0d0b0"),
    "copper": P("#5a3016", "#9a5626", "#d0843e", "#8fd8b4", "#d4fff0"),
    "sulfur": P("#7a6a10", "#b8a022", "#e4cc3a", "#fff08a", "#fffbd8"),
    "thermal": P("#6a2208", "#b4480c", "#e87c18", "#ffc056", "#fff0c0"),
    "abyssal": P("#2e1450", "#562a94", "#8a52d4", "#c8a0ff", "#f4e8ff"),
    "manganese": P("#140e12", "#2e2228", "#4e3c48", "#8a7086", "#d0b8cc"),
    "cobalt": P("#0a1640", "#16348a", "#2a5cd0", "#78a8ff", "#d8e8ff"),
    "nickel": P("#34402e", "#5c6a50", "#8e9e7e", "#c8d6b4", "#f4faea"),
    "deep_crystal": P("#0d4a5c", "#13667c", "#1b86a0", "#2aa8c2", "#5fd3e6", "#c8f6ff"),
    "pressure": P("#4a4060", "#6e6090", "#9a8cc0", "#cabfe8", "#f6f0ff"),
    "pale": P("#5e6878", "#8e9aac", "#c0cad8", "#e4ecf6", "#ffffff"),
    # rare metals (M01); placeholder palettes, the final textures come from forge_textures / mineral_textures
    "platinum": P("#2a2c31", "#4f535b", "#7b808a", "#a8adb5", "#d4d7dc"),
    "tellurium": P("#1f2530", "#3a4555", "#5d6c80", "#8494a8", "#b4c2d2"),
    "molybdenum": P("#1a1f29", "#333c4c", "#505d72", "#728198", "#9eacc0"),
    "vanadium": P("#18242a", "#304349", "#4c666a", "#6e8a8a", "#9cb4b0"),
    "tungsten": P("#16171a", "#2a2c30", "#43464b", "#5f6268", "#868a90"),
    "yttrium": P("#2e2c26", "#57544a", "#838071", "#afab98", "#d8d4c0"),
}

# ================================================================ rare metals (M01)
# Real deep-sea resources.  Each metal: raw_<id>, <id>_ingot, <id>_ore.  Raw lumps drop (rarely) from the crusts that
# concentrate them; the ores are tiny, very rare veins in the deep biomes (tools/gen_worldgen.py RARE_VEINS).
RARE_METALS = {
    # id: (English, Japanese, ore host rock, tooltip en, tooltip ja)
    "platinum": ("Platinum", "白金", "trench_rock",
                 "Cobalt-rich crusts of the trench seamounts", "海溝の海山を覆うコバルトリッチクラスト"),
    "tellurium": ("Tellurium", "テルル", "trench_rock",
                  "Enriched in cobalt-rich crusts", "コバルトリッチクラストに濃集する"),
    "molybdenum": ("Molybdenum", "モリブデン", "abyssal_rock",
                   "Manganese crusts of the abyssal plains", "深淵平原のマンガンクラスト"),
    "vanadium": ("Vanadium", "バナジウム", "abyssal_rock",
                 "Adsorbed in manganese crusts", "マンガンクラストに吸着している"),
    "tungsten": ("Tungsten", "タングステン", "trench_rock",
                 "Nickel crusts of the hadal zone", "超深海帯のニッケルクラスト"),
    "yttrium": ("Yttrium", "イットリウム", "abyssal_rock",
                "Rare-earth mud; a trace in every crust", "レアアース泥由来 ― どのクラストにもごく僅か"),
}
# crust -> [(rare metal, fortune chances 0..3)]: extra raw_<metal> drops when a crust is mined without silk touch
COMMON_RARE = [0.02, 0.03, 0.04, 0.05]
TRACE_RARE = [0.005, 0.0075, 0.01, 0.0125]
CRUST_RARE_DROPS = {
    "cobalt_crust": [("platinum", COMMON_RARE), ("tellurium", COMMON_RARE), ("yttrium", TRACE_RARE)],
    "manganese_crust": [("molybdenum", COMMON_RARE), ("vanadium", COMMON_RARE), ("yttrium", TRACE_RARE)],
    "nickel_crust": [("tungsten", COMMON_RARE), ("yttrium", TRACE_RARE)],
    "iron_crust": [("yttrium", TRACE_RARE)],
    "copper_crust": [("yttrium", TRACE_RARE)],
}
RARE_ORES = [m + "_ore" for m in RARE_METALS]
PLANT = {
    "green": P("#0f2a1c", "#184030", "#22583f", "#2f7050", "#4a9068"),
    "teal": P("#0c2a30", "#124048", "#1a5a62", "#267a80", "#3fa0a0"),
    "violet": P("#1c1438", "#2a1e52", "#3c2a70", "#52388e", "#7050b0"),
    "ashen": P("#1f2622", "#2e3833", "#414d46", "#56655c", "#748478"),
    "vent": P("#3a3a10", "#5a5a14", "#7e7a1c", "#a88a24", "#d0902c"),
    "fern": P("#123828", "#1c5038", "#28684a", "#3a825e"),
    "tube": P("#4a1a2a", "#6e2a3a", "#963c4a", "#bc5a5a", "#e08a78"),
    "thermal_tube": P("#2a0a08", "#4a1410", "#6e2018", "#922c1c", "#b0442a"),
    "kelp": P("#0f2a14", "#1a4020", "#28582c", "#3a703a", "#52883e"),
    "giant_kelp": P("#2a2a0e", "#403e14", "#5a561c", "#767026", "#948c34"),
    "giant_tube": P("#3a1238", "#54204e", "#702e68", "#8e4284", "#b060a4"),
    "sponge": P("#6a3a10", "#8e5418", "#b47226", "#d49438", "#ecb858"),
    "void": P("#0e0816", "#1a1028", "#28183c", "#3a2452", "#4e3470"),
    "black_coral": P("#0a0a0e", "#16161c", "#24242e", "#34343e", "#484856"),
    "soul": P("#4a6a78", "#7a9aa8", "#a0c0cc", "#c8e0e8"),
    "glow_coral": P("#4a3e08", "#6a5a10", "#9a8418", "#c8ac24"),
    "anemone": P("#3a1028", "#5a1a3a", "#8a2a5a", "#b84a7a"),
    "moss": P("#10261a", "#1a3a26", "#285234", "#3a6a44"),
    "heat_moss": P("#3a0e08", "#5a1a0c", "#7a2c10", "#a04a18"),
}
GLOW = {
    "cyan": hexrgb("#9ff6ff"), "pink": hexrgb("#ffb0e0"), "yellow": hexrgb("#fff08a"), "orange": hexrgb("#ffae4a"),
    "white": hexrgb("#e8fcff"), "violet": hexrgb("#f0d0ff"), "blue": hexrgb("#c8f0ff"),
}

# ================================================================ block textures

def rock_tex(name, **kw):
    return rock(name, ROCK[name], **kw)


def terrain_textures():
    t = {}
    t["deep_sea_rock"] = rock_tex("deep_sea_rock", strata=0.18, cracks=1, speckle=8)
    t["abyssal_rock"] = rock_tex("abyssal_rock", strata=0.12, cracks=2, speckle=6)
    t["trench_rock"] = rock_tex("trench_rock", strata=0.3, cracks=1, speckle=5)
    t["thermal_rock"] = veined(rock_tex("thermal_rock", cracks=1, holes=3), "thermal_rock",
                               P("#c24a14", "#e87424", "#ffa84a"), count=2)
    t["volcanic_rock"] = rock_tex("volcanic_rock", holes=7, columns=True, speckle=4)
    t["molten_volcanic_rock"] = veined(rock("molten_volcanic_rock", ROCK["volcanic_rock"], holes=3),
                                       "molten_volcanic_rock", P("#ff7a1a", "#ffb13b", "#ffd96a"), count=4, width=2)
    t["volcanic_glass"] = facets("volcanic_glass", P("#0d0b14", "#16121f", "#211a2e", "#2e2440", "#3a2e52", "#8070b0"), glints=3)
    t["crystal_rock"] = flecks(rock_tex("crystal_rock", strata=0.1, speckle=4), "crystal_rock",
                               P("#7fe0f0", "#bff6ff"), density=0.1, threshold=0.82)
    t["mineral_host_rock"] = flecks(rock_tex("mineral_host_rock", strata=0.35, speckle=4), "mineral_host_rock",
                                    P("#8a4a26", "#b0683a"), density=0.15, threshold=0.8)
    t["ancient_masonry"] = rock_tex("ancient_masonry", strata=0.5, cracks=2, speckle=3)
    t["fossil_rock"] = flecks(rock_tex("fossil_rock", strata=0.4, speckle=4), "fossil_rock",
                              P("#e8e0cc", "#f6f0e0"), density=0.12, threshold=0.8)
    t["salt_rock"] = flecks(rock_tex("salt_rock", strata=0.15, speckle=5), "salt_rock",
                            P("#ffffff", "#ffd8e0"), density=0.14, threshold=0.78)
    t["lumen_rock"] = flecks(rock_tex("lumen_rock", strata=0.2, speckle=4), "lumen_rock",
                             P("#7affd0", "#c8fff0"), density=0.12, threshold=0.8)
    t["frozen_rock"] = flecks(rock_tex("frozen_rock", strata=0.25, cracks=1, speckle=4), "frozen_rock",
                              P("#e0f4ff", "#ffffff"), density=0.12, threshold=0.8)
    for name, pal in SED.items():
        if name in ("abyssal_mud", "deep_mud", "organic_sediment", "brine_silt", "glow_silt", "fossil_silt"):
            t[name] = mud(name, pal)
        else:
            t[name] = sediment(name, pal, grain=0.7 if name == "sulfur_deposit" else 0.55)
    t["crystal_sediment"] = flecks(t["crystal_sediment"], "crystal_sediment", P("#8ae8f8", "#d8fcff"), density=0.08, threshold=0.85)
    for name, fl in (("bone_sediment", P("#f6f0dc", "#ffffff")), ("salt_crust", P("#ffffff", "#ffe0e8")),
                     ("lumen_sand", P("#9affd8", "#e0fff4")), ("glow_silt", P("#7ae8c0", "#c8fff0")),
                     ("frost_silt", P("#ffffff", "#e0f4ff")), ("icy_sediment", P("#e8f8ff", "#ffffff"))):
        t[name] = flecks(t[name], name, fl, density=0.09, threshold=0.84)
    t["organic_sediment"] = flecks(t["organic_sediment"], "organic_sediment", P("#4a6a30", "#6a8a3a"), density=0.1, threshold=0.8)
    t["sulfur_deposit"] = flecks(t["sulfur_deposit"], "sulfur_deposit", P("#fff29a", "#fffbd8"), density=0.1, threshold=0.85)
    # Vent geology
    t["vent_rock"] = rock_tex("vent_rock", holes=10, speckle=4)
    t["black_vent_rock"] = flecks(rock_tex("black_vent_rock", holes=4), "black_vent_rock", P("#6a7890", "#aab8cc"), density=0.08, threshold=0.9)
    t["sulfur_vent_rock"] = veined(rock("sulfur_vent_rock", ROCK["vent_rock"], holes=6), "sulfur_vent_rock",
                                   P("#b8a22c", "#d8c43c", "#f0e070"), count=4)
    t["mineral_vent_rock"] = rock_tex("mineral_vent_rock", strata=0.3, holes=4)
    t["black_mineral_deposit"] = flecks(sediment("black_mineral_deposit", P("#07080a", "#0e1014", "#161a20", "#20262e"), grain=0.4),
                                        "black_mineral_deposit", P("#5a6680", "#8e9bb0", "#c5d0e0"), density=0.12, threshold=0.88)
    t["deep_crystal_block"] = facets("deep_crystal_block", MINERAL["deep_crystal"], glints=5)
    vent = rock("thermal_vent", ROCK["thermal_rock"], cracks=2)
    for (x, y), col in {(7, 7): "#fff0b0", (8, 7): "#ffd070", (7, 8): "#ffd070", (8, 8): "#ffb040",
                        (6, 7): "#e07020", (9, 8): "#e07020", (7, 6): "#c04a10", (8, 9): "#c04a10"}.items():
        vent.put(x, y, hexrgb(col))
    t["thermal_vent"] = vent
    return t


def ore_textures(t):
    ores = {
        "abyssal_iron_ore": ("abyssal_rock", "iron"), "deep_copper_ore": ("deep_sea_rock", "copper"),
        "sulfur_ore": ("vent_rock", "sulfur"), "thermal_crystal_ore": ("thermal_rock", "thermal"),
        "abyssal_crystal_ore": ("abyssal_rock", "abyssal"), "manganese_ore": ("abyssal_rock", "manganese"),
        "cobalt_ore": ("trench_rock", "cobalt"), "deep_nickel_ore": ("mineral_host_rock", "nickel"),
    }
    ores.update({m + "_ore": (v[2], m) for m, v in RARE_METALS.items()})
    for name, (host, mineral) in ores.items():
        t[name] = ore(t[host], name, MINERAL[mineral][1:], blobs=5 if "crystal" in name else 4)
    crusts = {"manganese_crust": "manganese", "cobalt_crust": "cobalt", "nickel_crust": "nickel",
              "iron_crust": "iron", "copper_crust": "copper"}
    for name, mineral in crusts.items():
        t[name] = flecks(t["deep_sediment"], name, MINERAL[mineral][:4], density=0.45, threshold=0.42)
    # Polished crusts (the crust art itself) and crust bricks (crust + dark running-bond joints) are derived from the
    # current crust textures by tools/derive_textures.py.


# ---------------------------------------------------------------- cross sprites (clusters, plants)

def crystal_cluster(name, pal, shards):
    c = Canvas()
    rng = rng_for(name)
    for i in range(shards):
        x = 3 + (i * 10 // max(1, shards - 1)) + int(rng.integers(-1, 2)) if shards > 1 else 8
        h = int(rng.integers(5, 15)) if shards > 2 else int(rng.integers(3, 7))
        shard(c, x, 15, h, int(rng.integers(2, 4)), float(rng.random() - 0.5) * 0.4, pal)
    return c


def nodules(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for _ in range(4):
        disc(c, rng.integers(3, 13), 15 - rng.integers(1, 4), rng.integers(2, 4), pal)
    return c


def grass(name, pal, top, glow=None, blades=6):
    c = Canvas()
    rng = rng_for(name, 1 if top else 2)
    tips = []
    for i in range(blades):
        x = 1 + i * 14 // blades + int(rng.integers(0, 2))
        h = int(rng.integers(8, 15)) if top else 16
        lean = float(rng.random() - 0.5) * (1.6 if top else 0.4)
        blade(c, x, 15, h, lean, pal[1:] if top else pal[1:-1], curl=float(rng.random() - 0.5) * 1.5)
        if top:
            tips.append((int(round(x + lean * h * 0.3)), 16 - h))
    g = glow_mask(c, tips, glow) if glow else None
    return c, g


def fern(name, pal):
    c = Canvas()
    for y in range(3, 16):
        c.put(7, y, pal[1])
    for i, y in enumerate(range(13, 3, -2)):
        length = 2 + (13 - y) // 3 if y > 8 else 7 - (8 - y)
        for k in range(1, max(2, length)):
            side = 1 if i % 2 == 0 else -1
            c.put(7 + side * k, y - k // 2, pal[min(len(pal) - 1, 1 + k // 2)])
            c.put(7 - side * k, y - 1 - k // 2, pal[min(len(pal) - 1, k // 2)])
    return c


def tube(name, pal, top, glow=None, count=2):
    c = Canvas()
    rng = rng_for(name, 3 if top else 4)
    glows = []
    for i in range(count):
        x0 = 3 + i * 6 + int(rng.integers(0, 2))
        h = int(rng.integers(10, 15)) if top else 16
        for y in range(16 - h, 16):
            band = (y % 4 == 0) and not top
            c.put(x0, y, pal[1])
            c.put(x0 + 1, y, pal[3] if not band else pal[2])
            c.put(x0 + 2, y, pal[2] if not band else pal[1])
            c.put(x0 + 3, y, pal[0])
        if top:
            rim = 16 - h
            for dx in range(-1, 5):
                c.put(x0 + dx, rim, pal[-1] if 0 <= dx <= 3 else pal[3])
            c.put(x0 + 1, rim, darken(pal[0], 0.4))
            c.put(x0 + 2, rim, darken(pal[0], 0.4))
            glows += [(x0 + 1, rim), (x0 + 2, rim)]
    g = glow_mask(c, glows, glow) if glow and top else None
    return c, g


def kelp(name, pal, top, bulb=None, thick=False):
    c = Canvas()
    rng = rng_for(name, 5 if top else 6)
    stem_w = 3 if thick else 2
    for y in range(0 if not top else 5, 16):
        for k in range(stem_w):
            c.put(7 + k, y, pal[1 + k % 2])
    for y in range(1 if not top else 6, 16, 3):
        side = 1 if (y // 3) % 2 == 0 else -1
        length = int(rng.integers(3, 6)) + (2 if thick else 0)
        for k in range(1, length):
            x = (7 + stem_w) + k - 1 if side > 0 else 7 - k
            c.put(x, y - k // 2, pal[min(len(pal) - 1, 2 + k // 3)])
            c.put(x, y - k // 2 + 1, pal[1])
    if top:
        if bulb:
            disc(c, 8, 4, 2.2, bulb)
        else:
            for k, y in enumerate(range(5, 0, -1)):
                for dx in range(-k, k + 1):
                    c.put(8 + dx, y, pal[min(len(pal) - 1, 2 + abs(dx) // 2)])
    return c


def coral(name, pal, glow=None, seed_salt=0):
    c = Canvas()
    rng = rng_for(name, seed_salt)
    tips = []
    branch(c, 8, 16, 6, 1.57, pal, rng, 0, tips)
    g = glow_mask(c, tips, glow) if glow else None
    return c, g


def tentacles(name, pal, glow):
    c = Canvas()
    rng = rng_for(name)
    tips = []
    for y in range(13, 16):
        for x in range(5, 11):
            c.put(x, y, pal[1])
    for i in range(7):
        x = 5 + i
        h = int(rng.integers(5, 10))
        lean = (i - 3) * 0.5
        blade(c, x, 12, h, lean, pal, curl=float(rng.random() - 0.5))
        tips.append((int(round(x + lean * h * 0.3)), 12 - h + 1))
    return c, glow_mask(c, tips, glow)


def mushroom(name, stem, cap, glow):
    c = Canvas()
    for y in range(8, 16):
        c.put(7, y, stem[0])
        c.put(8, y, stem[1])
    spots = []
    for y in range(3, 9):
        w = 6 - abs(y - 5)
        for x in range(8 - w, 8 + w):
            c.put(x, y, cap[min(len(cap) - 1, (x - (8 - w)) * len(cap) // (2 * w))])
    for x in range(3, 13, 2):
        spots.append((x, 8))
    spots += [(6, 4), (9, 5)]
    return c, glow_mask(c, spots, glow)


def flower(name, stem_col, petals, glow, center):
    c = Canvas()
    for y in range(7, 16):
        c.put(8, y, stem_col)
    c.put(7, 12, stem_col)
    c.put(6, 11, stem_col)
    glows = []
    for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1), (-3, 0), (3, 0), (0, -3)):
        c.put(8 + dx, 5 + dy, petals[min(len(petals) - 1, abs(dx) + abs(dy) - 1)])
        if abs(dx) + abs(dy) >= 2:
            glows.append((8 + dx, 5 + dy))
    c.put(8, 5, center)
    glows.append((8, 5))
    return c, glow_mask(c, glows, glow)


def sponge(name, pal):
    c = Canvas()
    rng = rng_for(name)
    disc(c, 8, 10, 5.5, pal)
    disc(c, 5, 6, 2.5, pal)
    for _ in range(9):
        x, y = rng.integers(4, 13), rng.integers(6, 15)
        if c.alpha(x, y):
            c.put(x, y, darken(pal[0], 0.4))
    return c


def crystal_plant(name):
    c = Canvas()
    for y in range(9, 16):
        c.put(8, y, hexrgb("#1a4a50"))
    blade(c, 7, 15, 4, -2, P("#1a3a40", "#2a5a60", "#3a7a80"))
    blade(c, 9, 15, 4, 2, P("#1a3a40", "#2a5a60", "#3a7a80"))
    crystals = Canvas()
    shard(crystals, 8, 9, 7, 3, 0, MINERAL["deep_crystal"][2:])
    shard(crystals, 6, 9, 4, 2, -0.4, MINERAL["deep_crystal"][2:])
    shard(crystals, 10, 9, 5, 2, 0.4, MINERAL["deep_crystal"][2:])
    pts = []
    for y in range(N):
        for x in range(N):
            if crystals.alpha(x, y):
                c.put(x, y, tuple(crystals.rgba[y, x][:3]))
                pts.append((x, y))
    g = Canvas()
    for x, y in pts:
        g.put(x, y, tuple(c.rgba[y, x][:3]))
    return c, g


def floating_bloom(name):
    c = Canvas()
    rng = rng_for(name)
    for i in range(4):
        x = 5 + i * 2
        for y in range(9, 9 + int(rng.integers(4, 7))):
            c.put(x + (y % 3 == 0), y, hexrgb("#3a6aa8"), 180)
    disc(c, 8, 6, 4.2, P("#2a4a7a", "#3a6aa8", "#5a94d0", "#8ac0f0"), alpha=210)
    core = [(7, 5), (8, 5), (7, 6), (8, 6), (8, 4)]
    return c, glow_mask(c, core, GLOW["blue"])


def carpet(name, pal, cover, specks=None):
    c = Canvas()
    rng = rng_for(name)
    v = rng.random((N, N))
    for y in range(N):
        for x in range(N):
            if v[y, x] < cover:
                c.put(x, y, pal[int(rng.integers(0, len(pal)))])
    if specks:
        for _ in range(10):
            x, y = rng.integers(0, N, 2)
            c.put(x, y, specks)
    return c


def pebbles(name):
    c = Canvas()
    rng = rng_for(name)
    for _ in range(6):
        disc(c, rng.integers(2, 14), rng.integers(2, 14), rng.integers(1, 3) + 0.2, P("#2e333a", "#454b54", "#626a74", "#818a94"))
    return c


def giant_tube_side(name, pal):
    c = Canvas()
    for y in range(N):
        for x in range(N):
            shade = 1 + (x * 3) // N
            if y % 5 == 0:
                shade = max(0, shade - 1)
            c.put(x, y, pal[min(len(pal) - 1, shade + (1 if x in (3, 4) else 0))])
    return c


def giant_tube_top(name, pal):
    c = Canvas()
    for y in range(N):
        for x in range(N):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c.put(x, y, pal[-1] if d > 5 else hexrgb("#1a0818") if d < 4 else pal[2])
    return c


# ---------------------------------------------------------------- items

def lump(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for cx, cy, r in ((7, 8, 4.5), (10, 6, 3), (5, 10, 3)):
        disc(c, cx + int(rng.integers(-1, 2)), cy, r, pal)
    item_outline(c, darken(pal[0], 0.5))
    return c


def ingot(name, pal):
    c = Canvas()
    for y in range(5, 12):
        inset = (y - 5) // 2
        for x in range(2 + 6 - inset * 2 - (11 - y), 14 - (y - 5) // 3):
            if x < 1:
                continue
            shade = 3 if y == 5 else 2 if y < 8 else 1
            c.put(x, y, pal[shade])
    for x in range(3, 13):
        c.put(x, 11, pal[0])
    for x in range(8, 12):
        c.put(x, 6, pal[-1])
    item_outline(c, darken(pal[0], 0.6))
    return c


def powder(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for y in range(8, 15):
        w = (y - 7)
        for x in range(8 - w, 8 + w):
            c.put(x, y, pal[int(rng.integers(0, len(pal)))])
    item_outline(c, darken(pal[0], 0.5))
    return c


def shards_item(name, pal):
    c = Canvas()
    shard(c, 6, 14, 11, 4, 0.25, pal)
    shard(c, 11, 14, 7, 3, -0.2, pal)
    item_outline(c, darken(pal[0], 0.5))
    return c


def soft_dot(size, radius, rgb, core=None):
    c = Canvas(size)
    ctr = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = ((x - ctr) ** 2 + (y - ctr) ** 2) ** 0.5
            a = max(0.0, min(1.0, radius - d + 0.5))
            if a > 0:
                c.put(x, y, core if core and d < radius * 0.4 else rgb, int(255 * a))
    return c


# ================================================================ block catalogue

CUBES = ["deep_sea_rock", "abyssal_rock", "trench_rock", "thermal_rock", "volcanic_rock", "molten_volcanic_rock",
         "volcanic_glass", "crystal_rock", "mineral_host_rock", "deep_sediment", "abyssal_mud", "deep_mud",
         "mineral_sediment", "crystal_sediment", "organic_sediment", "volcanic_ash", "thermal_vent", "vent_rock",
         "black_vent_rock", "sulfur_vent_rock", "mineral_vent_rock", "sulfur_deposit", "black_mineral_deposit",
         "ruin_gravel", "ruin_sediment", "ancient_masonry", "bone_sediment", "fossil_silt", "fossil_rock", "salt_crust",
         "brine_silt", "salt_rock", "lumen_sand", "glow_silt", "lumen_rock", "frost_silt", "icy_sediment", "frozen_rock",
         "abyssal_iron_ore", "deep_copper_ore", "sulfur_ore", "thermal_crystal_ore", "abyssal_crystal_ore",
         "manganese_ore", "cobalt_ore", "deep_nickel_ore", "manganese_crust", "cobalt_crust", "nickel_crust",
         "iron_crust", "copper_crust", "deep_crystal_block"] + RARE_ORES
SOFT = ["deep_sediment", "abyssal_mud", "deep_mud", "mineral_sediment", "crystal_sediment", "organic_sediment", "volcanic_ash",
        "ruin_gravel", "ruin_sediment", "bone_sediment", "fossil_silt", "salt_crust", "brine_silt", "lumen_sand", "glow_silt",
        "frost_silt", "icy_sediment"]
CLUSTERS = {
    "manganese_nodules": lambda: nodules("manganese_nodules", MINERAL["manganese"]),
    "cobalt_cluster": lambda: crystal_cluster("cobalt_cluster", MINERAL["cobalt"], 4),
    "nickel_cluster": lambda: crystal_cluster("nickel_cluster", MINERAL["nickel"], 4),
    "sulfur_cluster": lambda: crystal_cluster("sulfur_cluster", MINERAL["sulfur"], 5),
    "abyssal_crystal_cluster": lambda: crystal_cluster("abyssal_crystal_cluster", MINERAL["abyssal"], 5),
    "deep_crystal_cluster": lambda: crystal_cluster("deep_crystal_cluster", MINERAL["deep_crystal"], 5),
    "pressure_crystal_cluster": lambda: crystal_cluster("pressure_crystal_cluster", MINERAL["pressure"], 5),
    "pale_crystal_cluster": lambda: crystal_cluster("pale_crystal_cluster", MINERAL["pale"], 5),
    "small_thermal_crystal_bud": lambda: crystal_cluster("small_thermal_crystal_bud", MINERAL["thermal"], 2),
    "medium_thermal_crystal_bud": lambda: crystal_cluster("medium_thermal_crystal_bud", MINERAL["thermal"], 3),
    "thermal_crystal_cluster": lambda: crystal_cluster("thermal_crystal_cluster", MINERAL["thermal"], 5),
}
# name -> () -> (texture, glow overlay or None)
PLANTS = {
    "glowtip_grass": lambda: grass("glowtip_grass", PLANT["teal"], True, GLOW["cyan"]),
    "sea_fern": lambda: (fern("sea_fern", PLANT["fern"]), None),
    "sponge_plant": lambda: (sponge("sponge_plant", PLANT["sponge"]), None),
    "crystal_plant": lambda: crystal_plant("crystal_plant"),
    "vent_grass": lambda: grass("vent_grass", PLANT["vent"], True),
    "glow_anemone": lambda: tentacles("glow_anemone", PLANT["anemone"], GLOW["pink"]),
    "glow_coral": lambda: coral("glow_coral", PLANT["glow_coral"], GLOW["yellow"]),
    "abyssal_mushroom": lambda: mushroom("abyssal_mushroom", P("#9aa8b0", "#c0ccd2"), P("#0e4a52", "#16687a", "#2a8aa0"), GLOW["cyan"]),
    "abyssal_bloom": lambda: flower("abyssal_bloom", hexrgb("#1a3a4a"), P("#2a6a9a", "#4a90c8", "#8ac8f0"), GLOW["blue"], hexrgb("#e0f8ff")),
    "soul_coral": lambda: coral("soul_coral", PLANT["soul"], GLOW["white"], 1),
    "black_coral": lambda: coral("black_coral", PLANT["black_coral"], None, 2),
    "hadal_bloom": lambda: flower("hadal_bloom", hexrgb("#2a1a3a"), P("#4a2a7a", "#6a40a8", "#9a70d8"), GLOW["violet"], hexrgb("#fff0ff")),
}
# name -> (top () -> (tex, glow), body () -> tex)
STACKING = {
    "abyssal_grass": (lambda: grass("abyssal_grass", PLANT["green"], True), lambda: grass("abyssal_grass", PLANT["green"], False)[0]),
    "teal_abyssal_grass": (lambda: grass("teal_abyssal_grass", PLANT["teal"], True), lambda: grass("teal_abyssal_grass", PLANT["teal"], False)[0]),
    "violet_abyssal_grass": (lambda: grass("violet_abyssal_grass", PLANT["violet"], True), lambda: grass("violet_abyssal_grass", PLANT["violet"], False)[0]),
    "ashen_abyssal_grass": (lambda: grass("ashen_abyssal_grass", PLANT["ashen"], True), lambda: grass("ashen_abyssal_grass", PLANT["ashen"], False)[0]),
    "tube_plant": (lambda: tube("tube_plant", PLANT["tube"], True), lambda: tube("tube_plant", PLANT["tube"], False)[0]),
    "thermal_tube": (lambda: tube("thermal_tube", PLANT["thermal_tube"], True, GLOW["orange"]), lambda: tube("thermal_tube", PLANT["thermal_tube"], False)[0]),
    "mineral_vine": (lambda: (mineral_vine("mineral_vine", True), None), lambda: mineral_vine("mineral_vine", False)),
    "deep_kelp": (lambda: (kelp("deep_kelp", PLANT["kelp"], True, bulb=P("#3a5a1c", "#5a7a2a", "#7a9a3a")), None), lambda: kelp("deep_kelp", PLANT["kelp"], False)),
    "giant_kelp": (lambda: (kelp("giant_kelp", PLANT["giant_kelp"], True, thick=True), None), lambda: kelp("giant_kelp", PLANT["giant_kelp"], False, thick=True)),
}
CARPETS = {
    "abyssal_moss": lambda: carpet("abyssal_moss", PLANT["moss"], 0.7),
    "heat_moss": lambda: carpet("heat_moss", PLANT["heat_moss"], 0.75, hexrgb("#9aa83a")),
    "seafloor_pebbles": lambda: pebbles("seafloor_pebbles"),
}


def mineral_vine(name, top):
    c = Canvas()
    rng = rng_for(name, 1 if top else 2)
    stem = P("#6a4a2a", "#8a6038", "#a87a48")
    for i, x0 in enumerate((5, 10)):
        h = int(rng.integers(9, 15)) if top else 16
        for k, y in enumerate(range(15, 15 - h, -1)):
            c.put(x0 + (1 if (y // 3) % 2 else 0), y, stem[k % 3])
        for j, y in enumerate(range(15 - h + 2, 15, 5)):
            disc(c, x0 + (2.5 if j % 2 else -0.5), y, 1.0, P("#8a4a10", "#c0701c", "#f0b040"))
    return c


ITEMS = {
    "raw_manganese": lambda: lump("raw_manganese", MINERAL["manganese"]),
    "raw_cobalt": lambda: lump("raw_cobalt", MINERAL["cobalt"]),
    "raw_nickel": lambda: lump("raw_nickel", MINERAL["nickel"]),
    "manganese_ingot": lambda: ingot("manganese_ingot", P("#3a2c34", "#5e4a56", "#8a7280", "#b8a4b2", "#e0d4dc")),
    "cobalt_ingot": lambda: ingot("cobalt_ingot", P("#1a2e6a", "#2c4ea8", "#4a78d8", "#8ab0f0", "#d0e2ff")),
    "nickel_ingot": lambda: ingot("nickel_ingot", P("#5a6452", "#7e8a72", "#a4b096", "#c8d2bc", "#eef4e4")),
    # Abyssal alloy (M03): same teal palette as the alloy tool icons in alloy_tool_icon().
    "abyssal_alloy_ingot": lambda: ingot("abyssal_alloy_ingot", P("#26343a", "#45656a", "#70a4a1", "#b5d4c8", "#e6f2df")),
    "sulfur": lambda: powder("sulfur", MINERAL["sulfur"][1:4]),
    "thermal_crystal_shard": lambda: shards_item("thermal_crystal_shard", MINERAL["thermal"]),
    "abyssal_crystal_shard": lambda: shards_item("abyssal_crystal_shard", MINERAL["abyssal"]),
    # Locked art (grey-brown recolour of deep_pigment); kept in texture_locks so regeneration cannot change it.
    "crust_powder": lambda: Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "texture_locks", "assets",
                                                    "textures", "item", "crust_powder.png")).convert("RGBA"),
}

def alloy_tool_icon(name, kind):
    c = Canvas()
    metal = P("#26343a", "#45656a", "#70a4a1", "#b5d4c8", "#e6f2df")
    # Compact 16px silhouettes, drawn directly so generated models always have an icon.
    if kind in ("pickaxe", "axe", "shovel", "hoe"):
        for y in range(6, 15): c.put(7 + (y // 4), y, P("#49392a", "#806044", "#b18a5b")[min(2, (y-6)//4)])
        if kind == "pickaxe":
            for x in range(2, 14): c.put(x, 4 + abs(x-8)//4, metal[2 + (x % 3 == 0)])
            c.put(2, 5, metal[1]); c.put(13, 5, metal[1])
        elif kind == "axe":
            for x in range(5, 12):
                for y in range(3, 8 - abs(x-7)//2): c.put(x, y, metal[min(4, 1 + y//3)])
        elif kind == "shovel":
            for y in range(2, 7):
                for x in range(5, 11):
                    if abs(x-8) <= 3 - abs(y-4)//2: c.put(x, y, metal[min(4, y//2)])
        else:
            for x in range(4, 13): c.put(x, 4 + abs(x-8)//3, metal[2 + x%2])
            c.put(12, 3, metal[1])
    elif kind == "sword":
        for y in range(2, 12): c.put(10-y//3, y, metal[min(4, 1+y//3)])
        for x in range(4, 9): c.put(x, 11, metal[1])
        c.put(4, 12, P("#49392a", "#806044")[0]); c.put(5, 13, P("#49392a", "#806044")[1])
    elif kind == "helmet":
        for y in range(4, 12):
            for x in range(3, 14):
                if (y < 6 or x > 4) and (y > 6 or x < 13): c.put(x, y, metal[min(4, y//2)])
        for x in range(6, 12): c.put(x, 8, P("#173849", "#4d98a2")[x%2])
    else:
        for side in (0, 1):
            x0 = 3 + side*7
            for y in range(5, 13):
                for x in range(x0, x0+4): c.put(x, y, metal[2 + ((x+y)%2)])
            for x in range(x0-1, x0+5): c.put(x, 13, metal[1])
    item_outline(c, darken(metal[0], 0.55))
    return c

for _id, _kind in (("abyssal_alloy_pickaxe", "pickaxe"), ("abyssal_alloy_axe", "axe"),
                   ("abyssal_alloy_shovel", "shovel"), ("abyssal_alloy_hoe", "hoe"),
                   ("abyssal_alloy_sword", "sword"), ("deep_diver_helmet", "helmet"),
                   ("abyssal_flippers", "flippers")):
    ITEMS[_id] = (lambda n, k: lambda: alloy_tool_icon(n, k))(_id, _kind)
for _m in RARE_METALS:
    ITEMS["raw_" + _m] = (lambda m: lambda: lump("raw_" + m, MINERAL[m]))(_m)
    ITEMS[_m + "_ingot"] = (lambda m: lambda: ingot(m + "_ingot", MINERAL[m]))(_m)

NAMES = {
    # name: (English, Japanese)
    "deep_sea_rock": ("Deep Sea Rock", "深海岩"), "abyssal_rock": ("Abyssal Rock", "深淵岩"),
    "trench_rock": ("Trench Rock", "海溝岩"), "thermal_rock": ("Thermal Rock", "熱水岩"),
    "volcanic_rock": ("Volcanic Rock", "海底火山岩"), "molten_volcanic_rock": ("Molten Volcanic Rock", "灼熱の火山岩"),
    "volcanic_glass": ("Volcanic Glass", "火山ガラス"), "crystal_rock": ("Crystal Rock", "結晶岩"),
    "mineral_host_rock": ("Mineral Host Rock", "鉱床母岩"), "deep_sediment": ("Deep Sediment", "深海堆積物"),
    "abyssal_mud": ("Abyssal Mud", "深淵泥"), "deep_mud": ("Deep Mud", "深海泥"),
    "mineral_sediment": ("Mineral Sediment", "鉱物堆積物"), "crystal_sediment": ("Crystal Sediment", "結晶堆積物"),
    "organic_sediment": ("Organic Sediment", "有機堆積物"), "volcanic_ash": ("Volcanic Ash", "火山灰"),
    "thermal_vent": ("Thermal Vent", "熱水噴出孔"), "vent_rock": ("Vent Rock", "噴出孔岩"),
    "black_vent_rock": ("Black Vent Rock", "黒色噴出孔岩"), "sulfur_vent_rock": ("Sulfur Vent Rock", "硫黄噴出孔岩"),
    "mineral_vent_rock": ("Mineral Vent Rock", "鉱物噴出孔岩"), "sulfur_deposit": ("Sulfur Deposit", "硫黄堆積物"),
    "black_mineral_deposit": ("Black Mineral Deposit", "黒色鉱物堆積物"),
    "abyssal_iron_ore": ("Abyssal Iron Ore", "深淵鉄鉱石"), "deep_copper_ore": ("Deep Copper Ore", "深海銅鉱石"),
    "sulfur_ore": ("Sulfur Ore", "硫黄鉱石"), "thermal_crystal_ore": ("Thermal Crystal Ore", "熱結晶鉱石"),
    "abyssal_crystal_ore": ("Abyssal Crystal Ore", "深淵結晶鉱石"), "manganese_ore": ("Manganese Ore", "マンガン鉱石"),
    "cobalt_ore": ("Cobalt Ore", "コバルト鉱石"), "deep_nickel_ore": ("Deep Nickel Ore", "深海ニッケル鉱石"),
    "manganese_crust": ("Manganese Crust", "マンガンクラスト"), "cobalt_crust": ("Cobalt Crust", "コバルトクラスト"),
    "nickel_crust": ("Nickel Crust", "ニッケルクラスト"), "iron_crust": ("Iron Crust", "鉄クラスト"),
    "copper_crust": ("Copper Crust", "銅クラスト"), "deep_crystal_block": ("Deep Crystal Block", "深海結晶ブロック"),
    "manganese_nodules": ("Manganese Nodules", "マンガン団塊"), "cobalt_cluster": ("Cobalt Cluster", "コバルトの結晶塊"),
    "nickel_cluster": ("Nickel Cluster", "ニッケルの結晶塊"), "sulfur_cluster": ("Sulfur Cluster", "硫黄の結晶塊"),
    "abyssal_crystal_cluster": ("Abyssal Crystal Cluster", "深淵結晶の塊"),
    "deep_crystal_cluster": ("Deep Crystal Cluster", "深海結晶の塊"),
    "pressure_crystal_cluster": ("Pressure Crystal Cluster", "水圧結晶の塊"),
    "pale_crystal_cluster": ("Pale Crystal Cluster", "白い結晶の塊"),
    "small_thermal_crystal_bud": ("Small Thermal Crystal Bud", "小さな熱結晶の芽"),
    "medium_thermal_crystal_bud": ("Medium Thermal Crystal Bud", "中くらいの熱結晶の芽"),
    "thermal_crystal_cluster": ("Thermal Crystal Cluster", "熱結晶の塊"),
    "abyssal_grass": ("Abyssal Grass", "深海草"), "teal_abyssal_grass": ("Teal Abyssal Grass", "青緑の深海草"),
    "violet_abyssal_grass": ("Violet Abyssal Grass", "青紫の深海草"), "ashen_abyssal_grass": ("Ashen Abyssal Grass", "灰緑の深海草"),
    "glowtip_grass": ("Glowtip Grass", "光端草"), "sea_fern": ("Sea Fern", "ウミシダ"),
    "abyssal_moss": ("Abyssal Moss", "深海苔"), "seafloor_pebbles": ("Seafloor Pebbles", "海底の小石"),
    "tube_plant": ("Tube Plant", "チューブ植物"), "sponge_plant": ("Sponge Plant", "カイメン植物"),
    "crystal_plant": ("Crystal Plant", "結晶植物"), "deep_kelp": ("Deep Kelp", "深海コンブ"),
    "giant_kelp": ("Giant Kelp", "巨大コンブ"), "giant_tube": ("Giant Tube", "巨大チューブ"),
    "void_kelp": ("Void Kelp", "虚無のコンブ"), "void_kelp_plant": ("Void Kelp Plant", "虚無のコンブの茎"),
    "floating_bloom": ("Floating Bloom", "浮遊花"), "vent_grass": ("Vent Grass", "噴出孔草"),
    "thermal_tube": ("Thermal Tube", "熱水チューブ"), "heat_moss": ("Heat Moss", "熱苔"),
    "mineral_vine": ("Mineral Vine", "鉱物蔓"), "glow_anemone": ("Glow Anemone", "ヒカリイソギンチャク"),
    "glow_coral": ("Glow Coral", "ヒカリサンゴ"), "abyssal_mushroom": ("Abyssal Mushroom", "深淵キノコ"),
    "abyssal_bloom": ("Abyssal Bloom", "深淵の花"), "soul_coral": ("Soul Coral", "ソウルサンゴ"),
    "black_coral": ("Black Coral", "黒サンゴ"), "hadal_bloom": ("Hadal Bloom", "超深海の花"),
}
ITEM_NAMES = {
    "raw_manganese": ("Raw Manganese", "マンガンの原石"), "raw_cobalt": ("Raw Cobalt", "コバルトの原石"),
    "raw_nickel": ("Raw Nickel", "ニッケルの原石"), "manganese_ingot": ("Manganese Ingot", "マンガンインゴット"),
    "cobalt_ingot": ("Cobalt Ingot", "コバルトインゴット"), "nickel_ingot": ("Nickel Ingot", "ニッケルインゴット"),
    "sulfur": ("Sulfur", "硫黄"), "thermal_crystal_shard": ("Thermal Crystal Shard", "熱結晶の欠片"),
    "abyssal_crystal_shard": ("Abyssal Crystal Shard", "深淵結晶の欠片"),
    "crust_powder": ("Crust Powder", "クラスト粉末"),
}
ITEM_NAMES.update({
    "abyssal_alloy_ingot": ("Abyssal Alloy Ingot", "深海合金インゴット"),
    "abyssal_alloy_pickaxe": ("Abyssal Alloy Pickaxe", "深海合金のツルハシ"),
    "abyssal_alloy_axe": ("Abyssal Alloy Axe", "深海合金の斧"),
    "abyssal_alloy_shovel": ("Abyssal Alloy Shovel", "深海合金のシャベル"),
    "abyssal_alloy_hoe": ("Abyssal Alloy Hoe", "深海合金のクワ"),
    "abyssal_alloy_sword": ("Abyssal Alloy Sword", "深海合金の剣"),
    "deep_diver_helmet": ("Deep Diver's Helmet", "深海潜水ヘルム"),
    "abyssal_flippers": ("Abyssal Flippers", "深海フィン"),
})
for _m, (_en, _ja, _host, _src_en, _src_ja) in RARE_METALS.items():
    NAMES[_m + "_ore"] = (f"{_en} Ore", f"{_ja}鉱石")
    ITEM_NAMES["raw_" + _m] = (f"Raw {_en}", f"{_ja}の原石")
    ITEM_NAMES[_m + "_ingot"] = (f"{_en} Ingot", f"{_ja}インゴット")
    # tooltip line (com.abyssia.item.MaterialItem: item.abyssia.<id>.source)
    ITEM_NAMES["raw_" + _m + ".source"] = (_src_en, _src_ja)
    ITEM_NAMES[_m + "_ingot.source"] = (_src_en, _src_ja)
NAMES.update({
    "ruin_gravel": ("Ruin Gravel", "遺跡の砂利"), "ruin_sediment": ("Ruin Sediment", "遺跡の堆積物"),
    "ancient_masonry": ("Ancient Masonry", "古代の石積み"), "bone_sediment": ("Bone Sediment", "骨片堆積物"),
    "fossil_silt": ("Fossil Silt", "化石質シルト"), "fossil_rock": ("Fossil Rock", "化石岩"),
    "salt_crust": ("Salt Crust", "塩の外殻"), "brine_silt": ("Brine Silt", "塩水シルト"),
    "salt_rock": ("Salt Rock", "岩塩"), "lumen_sand": ("Lumen Sand", "発光砂"),
    "glow_silt": ("Glow Silt", "発光シルト"), "lumen_rock": ("Lumen Rock", "発光岩"),
    "frost_silt": ("Frost Silt", "霜のシルト"), "icy_sediment": ("Icy Sediment", "氷質堆積物"),
    "frozen_rock": ("Frozen Rock", "凍結岩"),
})
NAMES.update(cave_assets.CAVE_NAMES)
NAMES.update(plant_assets.BLOCK_NAMES)
ITEM_NAMES.update(plant_assets.ITEM_NAMES)
BIOME_NAMES = {
    "twilight_reef": ("Twilight Reef", "薄明の礁"), "deep_sea": ("Deep Sea", "深海"),
    "abyssal_ocean": ("Abyssal Ocean", "深淵の海"), "abyssal_trench": ("Abyssal Trench", "深淵の海溝"),
    "hadal_zone": ("Hadal Zone", "超深海帯"), "volcanic_deep": ("Volcanic Deep", "深海火山帯"),
    "thermal_vents": ("Thermal Vents", "熱水噴出域"), "abyssal_forest": ("Abyssal Forest", "深淵の海藻林"),
    "deep_crystal_fields": ("Deep Crystal Fields", "深海結晶原"), "deep_forest": ("Deep Forest", "深海の森"),
    "sunken_ruins": ("Sunken Ruins", "沈没遺跡帯"), "bone_graveyard": ("Bone Graveyard", "巨骨の墓場"),
    "brine_lakes": ("Brine Lakes", "塩水湖帯"), "glow_gardens": ("Glow Gardens", "発光花園"),
    "frost_abyss": ("Frost Abyss", "氷晶の深淵"), "abyssal_rift": ("Abyssal Rift", "深淵の裂け目"),
}

# ================================================================ models

CROSS_PLANES = [
    ({"from": [0.8, 0, 8], "to": [15.2, 16, 8]}, ("north", "south")),
    ({"from": [8, 0, 0.8], "to": [8, 16, 15.2]}, ("west", "east")),
]


def cross_model(tex, glow=None):
    if glow is None:
        return {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tex}}
    elements = []
    for texture, offset, extra in (("#cross", 0.0, {}), ("#glow", 0.02, {"forge_data": {"block_light": 15, "sky_light": 15}})):
        for bounds, faces in CROSS_PLANES:
            e = {"from": list(bounds["from"]), "to": list(bounds["to"]),
                 "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, "shade": False,
                 "faces": {f: {"uv": [0, 0, 16, 16], "texture": texture} for f in faces}}
            axis = 2 if faces[0] == "north" else 0
            e["from"][axis] += offset
            e["to"][axis] += offset
            e.update(extra)
            elements.append(e)
    # Only the glow layer's pixels (tips, spots, crystal parts) render full-bright.
    return {"ambientocclusion": False, "render_type": "minecraft:cutout",
            "textures": {"particle": tex, "cross": tex, "glow": glow}, "elements": elements}


def tube_column_model(side, top):
    faces = {f: {"texture": "#side", "uv": [2, 0, 14, 16]} for f in ("north", "south", "east", "west")}
    faces["up"] = {"texture": "#top", "uv": [2, 2, 14, 14]}
    faces["down"] = {"texture": "#side", "uv": [2, 2, 14, 14]}
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": side, "side": side, "top": top},
            "elements": [{"from": [2, 0, 2], "to": [14, 16, 14], "faces": faces}]}


# ================================================================ main

def reset_dirs():
    """Wipe the generated JSON folders.  The textures folders are left alone: committed and locked PNGs are the
    source of truth, and the texture writers only replace what the --textures mode asks for."""
    for d in (os.path.join(ASSETS, "blockstates"), os.path.join(ASSETS, "models"),
              os.path.join(DATA, "abyssia", "loot_tables", "blocks"), os.path.join(DATA, "abyssia", "loot_tables", "harvest"),
              os.path.join(DATA, "abyssia", "recipes")):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d, exist_ok=True)


def main():
    texture_locks.mode_from_argv()
    reset_dirs()
    bs = lambda n: os.path.join(ASSETS, "blockstates", n + ".json")
    bm = lambda n: os.path.join(ASSETS, "models", "block", n + ".json")
    im = lambda n: os.path.join(ASSETS, "models", "item", n + ".json")
    tex = lambda n: os.path.join(BLOCK_TEX, n + ".png")
    ref = lambda n: "abyssia:block/" + n

    t = {}
    if any(texture_locks.wants(tex(name)) for name in CUBES):
        t = terrain_textures()
        ore_textures(t)
    for name in CUBES:
        if name in t:
            _save(t[name], tex(name))
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), {"parent": "minecraft:block/cube_all", "textures": {"all": ref(name)}})
        write(im(name), {"parent": ref(name)})

    # Polished metal crusts reuse the crust texture and bricks draw joints on it (derive_textures.py); their models,
    # blockstates, loot, recipes, tags and names come from building_assets.py.

    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
           "up": {}, "west": {"x": 90, "y": 270}}
    for name, make in CLUSTERS.items():
        _emit(make, tex(name))
        # special crystals glow at their cores; the overlay comes from derive_textures.py
        glow = name in mineral_textures.GLOWING
        write(bs(name), {"variants": {f"facing={f}": {"model": ref(name), **r} for f, r in rot.items()}})
        write(bm(name), cross_model(ref(name), ref(name + "_glow") if glow else None))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name)}})

    for name, make in PLANTS.items():
        base, glow = make()
        _save(base, tex(name))                   # the glow overlay (if any) comes from derive_textures.py
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), cross_model(ref(name), ref(name + "_glow") if glow else None))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name)}})

    for name, (make_top, make_body) in STACKING.items():
        top, glow = make_top()
        _save(top, tex(name + "_top"))
        _emit(make_body, tex(name))
        write(bs(name), {"variants": {"top=true": {"model": ref(name + "_top")}, "top=false": {"model": ref(name)}}})
        write(bm(name + "_top"), cross_model(ref(name + "_top"), ref(name + "_top_glow") if glow else None))
        write(bm(name), cross_model(ref(name)))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name + "_top")}})

    # Giant tube: a real 3D column rather than crossed planes.
    _save(giant_tube_side("giant_tube", PLANT["giant_tube"]), tex("giant_tube"))
    _save(giant_tube_top("giant_tube_top", PLANT["giant_tube"]), tex("giant_tube_top"))
    write(bs("giant_tube"), {"variants": {"top=true": {"model": ref("giant_tube_top")}, "top=false": {"model": ref("giant_tube")}}})
    write(bm("giant_tube_top"), tube_column_model(ref("giant_tube"), ref("giant_tube_top")))
    write(bm("giant_tube"), tube_column_model(ref("giant_tube"), ref("giant_tube")))
    write(im("giant_tube"), {"parent": ref("giant_tube_top")})

    for name, make in CARPETS.items():
        _emit(make, tex(name))
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), {"parent": "minecraft:block/carpet", "render_type": "minecraft:cutout", "textures": {"wool": ref(name)}})
        write(im(name), {"parent": ref(name)})

    bloom, bloom_glow = floating_bloom("floating_bloom")
    _save(bloom, tex("floating_bloom"))
    write(bs("floating_bloom"), {"variants": {"": {"model": ref("floating_bloom")}}})
    write(bm("floating_bloom"), cross_model(ref("floating_bloom"), ref("floating_bloom_glow")))
    write(im("floating_bloom"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("floating_bloom")}})

    _save(kelp("void_kelp", PLANT["void"], True), tex("void_kelp"))
    _save(kelp("void_kelp_plant", PLANT["void"], False), tex("void_kelp_plant"))
    for name in ("void_kelp", "void_kelp_plant"):
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), cross_model(ref(name)))
    write(im("void_kelp"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("void_kelp")}})

    # Deep-sea cave network: cave rock, speleothems, crystals and cave flora.
    cave_assets.generate(write, bs, bm, im, tex, ref, cross_model, tube_column_model)

    # Building blocks: stone families and the ancient wood set (textures come from the Texture Forge pass below).
    building_assets.generate(write, bs, bm, im, DATA)

    for name, make in ITEMS.items():
        _emit(make, os.path.join(ITEM_TEX, name + ".png"))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": "abyssia:item/" + name}})

    # Fauna spawn eggs (the rest of the fauna assets come from gen_fauna.py)
    gen_fauna.item_models(write, im)

    # Plant particles
    os.makedirs(PARTICLE_TEX, exist_ok=True)
    _save(soft_dot(8, 1.4, hexrgb("#c8d88a"), hexrgb("#f0ffc0")), os.path.join(PARTICLE_TEX, "spore.png"))
    _save(soft_dot(8, 2.0, hexrgb("#7ae8ff"), hexrgb("#e8ffff")), os.path.join(PARTICLE_TEX, "glow_dust.png"))
    for p in ("spore", "glow_dust"):
        write(os.path.join(ASSETS, "particles", p + ".json"), {"textures": ["abyssia:" + p]})

    loot_tables()
    tags()
    recipes()
    # Resource plants (plant_defs.py): after the generic loot tables and recipes, which it extends / overrides.
    plants = plant_assets.generate(write, bs, bm, im, cross_model, DATA)
    lang()
    print(f"Resource plants: {plants}")
    print(f"{len(NAMES) - 1} blocks + {len(building_assets.NAMES)} building blocks, {len(ITEMS)} items")
    if "--no-forge" not in sys.argv:
        import forge_textures
        forge_textures.run()
    # Approved textures and models are frozen against regeneration (also applied inside forge_textures.run).
    texture_locks.apply()
    # Stone family variants, crust bricks and glow overlays follow the current base textures.
    import derive_textures
    derive_textures.run()
    # Texture Studio specs (tools/texture_studio) are the user's hand-made textures: they win over every generator.
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'texture_studio'))
    import texture_engine as _studio
    _studio.apply_all(True)


# ================================================================ data

ORE_DROPS = {
    # ore: (item, min, max, fortune formula)
    "abyssal_iron_ore": ("minecraft:raw_iron", 1, 1, "ore_drops"),
    "deep_copper_ore": ("minecraft:raw_copper", 2, 5, "ore_drops"),
    "sulfur_ore": ("abyssia:sulfur", 2, 4, "ore_drops"),
    "thermal_crystal_ore": ("abyssia:thermal_crystal_shard", 1, 3, "ore_drops"),
    "abyssal_crystal_ore": ("abyssia:abyssal_crystal_shard", 1, 3, "ore_drops"),
    "manganese_ore": ("abyssia:raw_manganese", 1, 1, "ore_drops"),
    "cobalt_ore": ("abyssia:raw_cobalt", 1, 1, "ore_drops"),
    "deep_nickel_ore": ("abyssia:raw_nickel", 1, 1, "ore_drops"),
    "manganese_nodules": ("abyssia:raw_manganese", 1, 2, "ore_drops"),
    "cobalt_cluster": ("abyssia:raw_cobalt", 1, 1, "ore_drops"),
    "nickel_cluster": ("abyssia:raw_nickel", 1, 1, "ore_drops"),
    "sulfur_cluster": ("abyssia:sulfur", 1, 3, "ore_drops"),
    "abyssal_crystal_cluster": ("abyssia:abyssal_crystal_shard", 2, 4, "ore_drops"),
    "thermal_crystal_cluster": ("abyssia:thermal_crystal_shard", 2, 4, "ore_drops"),
}
ORE_DROPS.update({m + "_ore": ("abyssia:raw_" + m, 1, 1, "ore_drops") for m in RARE_METALS})
SILK_ONLY = ["small_thermal_crystal_bud", "medium_thermal_crystal_bud"]


def silk():
    return [{"condition": "minecraft:match_tool", "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}]


def rare_pools(name):
    """Extra pools of a crust: each concentrated rare metal rolls on its own (fortune raises the chance)."""
    return [{"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:item", "name": "abyssia:raw_" + metal}],
             "conditions": [{"condition": "minecraft:inverted", "term": silk()[0]},
                            {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": chances},
                            {"condition": "minecraft:survives_explosion"}]}
            for metal, chances in CRUST_RARE_DROPS.get(name, [])]


def loot_tables():
    lt = lambda n: os.path.join(DATA, "abyssia", "loot_tables", "blocks", n + ".json")
    all_blocks = list(NAMES)
    for name in all_blocks:
        if name in ORE_DROPS:
            item, lo, hi, formula = ORE_DROPS[name]
            funcs = [] if lo == hi == 1 else [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
            funcs += [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:" + formula},
                      {"function": "minecraft:explosion_decay"}]
            entries = [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": "abyssia:" + name, "conditions": silk()},
                {"type": "minecraft:item", "name": item, "functions": funcs}]}]
            pool = {"rolls": 1, "bonus_rolls": 0, "entries": entries}
        elif name in cave_assets.CAVERN_NO_DROP:
            write(lt(name), {"type": "minecraft:block", "pools": [], "random_sequence": "abyssia:blocks/" + name})
            continue
        elif name in SILK_ONLY:
            pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "abyssia:" + name}], "conditions": silk()}
        else:
            drop = "void_kelp" if name == "void_kelp_plant" else name
            pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "abyssia:" + drop}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}
        write(lt(name), {"type": "minecraft:block", "pools": [pool] + rare_pools(name), "random_sequence": "abyssia:blocks/" + name})


def tags():
    blocks = os.path.join(DATA, "minecraft", "tags", "blocks")
    ours = os.path.join(DATA, "abyssia", "tags", "blocks")
    a = lambda names: ["abyssia:" + n for n in names]
    rocks = ["deep_sea_rock", "abyssal_rock", "trench_rock", "thermal_rock", "volcanic_rock", "molten_volcanic_rock",
             "volcanic_glass", "crystal_rock", "mineral_host_rock", "vent_rock", "black_vent_rock", "sulfur_vent_rock",
             "mineral_vent_rock", "sulfur_deposit", "black_mineral_deposit", "deep_crystal_block", "thermal_vent",
             "ancient_masonry", "fossil_rock", "salt_rock", "lumen_rock", "frozen_rock"]
    ores = [n for n in CUBES if n.endswith("_ore")]
    crusts = [n for n in CUBES if n.endswith("_crust")]
    speleothems = list(cave_assets.SPELEOTHEM)
    write(os.path.join(blocks, "mineable", "pickaxe.json"), {"replace": False, "values": a(rocks + ores + crusts + list(CLUSTERS)
                                                                                          + cave_assets.CAVE_ROCKS + speleothems + ["crystal_needle"]
                                                                                          + list(cave_assets.CAVERN_CRYSTALS)
                                                                                          + building_assets.pickaxe_blocks())})
    write(os.path.join(blocks, "mineable", "shovel.json"), {"replace": False, "values": a(SOFT + cave_assets.CAVE_SOFT)})
    write(os.path.join(blocks, "mineable", "axe.json"), {"replace": False, "values": a(cave_assets.CAVERN_AXE + building_assets.axe_blocks())})
    write(os.path.join(blocks, "mineable", "hoe.json"), {"replace": False, "values": a(cave_assets.CAVERN_HOE)})
    write(os.path.join(blocks, "needs_stone_tool.json"), {"replace": False, "values": a(["abyssal_iron_ore", "deep_copper_ore", "sulfur_ore", "manganese_ore"])})
    write(os.path.join(blocks, "needs_iron_tool.json"), {"replace": False, "values": a(["cobalt_ore", "deep_nickel_ore", "thermal_crystal_ore", "abyssal_crystal_ore"] + RARE_ORES)})
    write(os.path.join(blocks, "crystal_sound_blocks.json"), {"replace": False, "values": a(["deep_crystal_block"] + list(cave_assets.CAVERN_CRYSTALS))})
    # Veins stay visible: plants cannot root in ore, crust or hot vent minerals (heat moss and mineral vines can).
    write(os.path.join(ours, "inhibits_plants.json"), {"values": a(ores + crusts + list(CLUSTERS) + [
        "sulfur_deposit", "black_mineral_deposit", "mineral_host_rock", "molten_volcanic_rock", "volcanic_glass",
        "thermal_vent", "vent_rock", "black_vent_rock", "sulfur_vent_rock", "mineral_vent_rock", "cave_mineral_crust", "crystal_needle"])})
    write(os.path.join(ours, "vein_replaceable.json"), {"values": a([
        "deep_sea_rock", "abyssal_rock", "trench_rock", "thermal_rock", "volcanic_rock", "crystal_rock", "mineral_host_rock",
        "deep_sediment", "abyssal_mud", "deep_mud", "mineral_sediment", "crystal_sediment", "organic_sediment", "volcanic_ash",
        "ruin_gravel", "ruin_sediment", "ancient_masonry", "bone_sediment", "fossil_silt", "fossil_rock", "salt_crust",
        "brine_silt", "salt_rock", "lumen_sand", "glow_silt", "lumen_rock", "frost_silt", "icy_sediment", "frozen_rock"]
        # Cave walls: ore veins and spires may cut through them too.
        + [n for n in cave_assets.CAVE_CUBES if n != "cave_mineral_crust"])})
    write(os.path.join(DATA, "abyssia", "tags", "items", "crusts.json"),
          {"replace": False, "values": a(list(building_assets.CRUSTS) + ["cave_mineral_crust"])})
    write(os.path.join(DATA, "abyssia", "tags", "items", "underwater_tools.json"),
          {"replace": False, "values": ["abyssia:abyssal_alloy_pickaxe", "abyssia:abyssal_alloy_axe",
           "abyssia:abyssal_alloy_shovel", "abyssia:abyssal_alloy_hoe", "abyssia:abyssal_alloy_sword"]})


def recipes():
    rd = lambda n: os.path.join(DATA, "abyssia", "recipes", n + ".json")
    smelt = {
        "raw_manganese": "abyssia:manganese_ingot", "raw_cobalt": "abyssia:cobalt_ingot", "raw_nickel": "abyssia:nickel_ingot",
        "manganese_ore": "abyssia:manganese_ingot", "cobalt_ore": "abyssia:cobalt_ingot", "deep_nickel_ore": "abyssia:nickel_ingot",
        "abyssal_iron_ore": "minecraft:iron_ingot", "deep_copper_ore": "minecraft:copper_ingot",
    }
    for m in RARE_METALS:
        smelt["raw_" + m] = smelt[m + "_ore"] = f"abyssia:{m}_ingot"
    for src, result in smelt.items():
        for kind, time in (("smelting", 200), ("blasting", 100)):
            write(rd(f"{result.split(':')[1]}_from_{kind}_{src}"), {
                "type": "minecraft:" + kind, "category": "misc", "ingredient": {"item": "abyssia:" + src},
                "result": result, "experience": 0.7, "cookingtime": time})

    # Mineral crusts are both useful ore concentrates and durable building material.
    crust_smelt = {"manganese_crust": ("abyssia:manganese_ingot", 2), "cobalt_crust": ("abyssia:cobalt_ingot", 2),
                   "nickel_crust": ("abyssia:nickel_ingot", 2), "iron_crust": ("minecraft:iron_ingot", 2),
                   "copper_crust": ("minecraft:copper_ingot", 2), "cave_mineral_crust": ("abyssia:sulfur", 2)}
    for crust, (result, count) in crust_smelt.items():
        for kind, time in (("smelting", 200), ("blasting", 100)):
            write(rd(f"{crust}_to_{kind}"), {"type": "minecraft:" + kind, "category": "misc",
                  "ingredient": {"item": "abyssia:" + crust}, "result": result,
                  "count": count, "experience": 0.5, "cookingtime": time})

    # Crust powder: ground from any crust; a mild pigment / adhesive helper.
    cp = {"item": "abyssia:crust_powder"}
    write(rd("crust_powder_from_crusts"), {"type": "minecraft:crafting_shapeless", "category": "misc",
          "ingredients": [{"tag": "abyssia:crusts"}], "result": {"item": "abyssia:crust_powder", "count": 2}})
    write(rd("deep_pigment_from_crust_powder"), {"type": "minecraft:crafting_shapeless", "category": "misc",
          "ingredients": [cp, cp, {"item": "abyssia:organic_matter"}], "result": {"item": "abyssia:deep_pigment"}})
    write(rd("marine_adhesive_from_crust_powder"), {"type": "minecraft:crafting_shapeless", "category": "misc",
          "ingredients": [cp, {"item": "abyssia:plant_resin"}], "result": {"item": "abyssia:marine_adhesive", "count": 2}})

    write(rd("abyssal_alloy_ingot"), {"type": "minecraft:crafting_shapeless", "category": "misc",
          "ingredients": [{"item": f"abyssia:{m}_ingot"} for m in ("vanadium", "cobalt", "nickel")],
          "result": {"item": "abyssia:abyssal_alloy_ingot", "count": 2}})
    for name, pattern in (("pickaxe", ["AAA", " S ", " S "]), ("axe", ["AA", "AS", " S"]),
                          ("shovel", ["A", "S", "S"]), ("hoe", ["AA", " S", " S"]),
                          ("sword", ["A", "A", "S"])):
        write(rd("abyssal_alloy_" + name), {"type": "minecraft:crafting_shaped", "category": "equipment",
              "pattern": pattern, "key": {"A": {"item": "abyssia:abyssal_alloy_ingot"}, "S": {"item": "minecraft:stick"}},
              "result": {"item": "abyssia:abyssal_alloy_" + name}})
    write(rd("deep_diver_helmet"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "pattern": ["AAA", "A A"], "key": {"A": {"item": "abyssia:abyssal_alloy_ingot"}},
          "result": {"item": "abyssia:deep_diver_helmet"}})
    write(rd("abyssal_flippers"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "pattern": ["A A", "S S"], "key": {"A": {"item": "abyssia:abyssal_alloy_ingot"}, "S": {"item": "abyssia:sea_cloth"}},
          "result": {"item": "abyssia:abyssal_flippers"}})


def lang():
    for lang_code, idx in (("en_us", 0), ("ja_jp", 1)):
        path = os.path.join(ASSETS, "lang", lang_code + ".json")
        old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        data = {k: v for k, v in old.items() if not k.startswith(("block.abyssia.", "item.abyssia.", "biome.abyssia."))}
        data.update({"block.abyssia." + k: v[idx] for k, v in NAMES.items()})
        data.update({"item.abyssia." + k: v[idx] for k, v in ITEM_NAMES.items()})
        data.update({"item.abyssia." + k: v[idx] for k, v in gen_fauna.ITEM_NAMES.items()})
        data.update({"biome.abyssia." + k: v[idx] for k, v in BIOME_NAMES.items()})
        data.update({"block.abyssia." + k: v[idx] for k, v in building_assets.NAMES.items()})
        data["itemGroup.abyssia"] = "Abyssia"
        write(path, dict(sorted(data.items())))


if __name__ == "__main__":
    main()
