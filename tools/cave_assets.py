"""Cave block assets for gen_deep_assets.py: textures (procedural pixel art), models, blockstates and names for the
deep-sea cave network's rock, speleothems, crystals and flora.

Not run on its own: gen_deep_assets.main() calls generate(), so the usual wipe-and-regenerate flow covers these too.
Plant sway is done with animated textures (a few frames of gentle shear), which costs nothing at runtime.
"""
import json
import math
import os

import numpy as np
from PIL import Image

from pixelart import (Canvas, N, blade, darken, disc, facets, fbm, flecks, glow_mask, hexrgb, lighten, mix, mud, palette, rng_for,
                      rock, sediment, shard, value_noise, veined)

P = palette

# ================================================================ palettes

CAVE_ROCK = {
    "abyssal_cave_rock": P("#101219", "#181b26", "#222636", "#2d3246", "#3a4058"),
    "dark_cave_rock": P("#09090d", "#111117", "#19191f", "#222229", "#2d2d36"),
    "wet_cave_rock": P("#1a2831", "#233440", "#2d4251", "#395263", "#4b687c"),
    "layered_cave_rock": P("#26272c", "#34363d", "#43464e", "#545861", "#676c77"),
    "mineral_cave_rock": P("#2b1f17", "#3a2a1f", "#4b3829", "#5f4835", "#775c44"),
    "thermal_cave_rock": P("#150a08", "#1f0f0b", "#2b1510", "#391c14", "#4a251a"),
    "crystal_cave_rock": P("#1a2836", "#223547", "#2c445a", "#38566f", "#486c89"),
    "organic_cave_rock": P("#141e17", "#1b2920", "#243529", "#2e4334", "#3a5341"),
    "eroded_cave_rock": P("#1f2c35", "#283843", "#324551", "#3e5562", "#4e6878"),
}
SPELEOTHEM = {
    # name: (palette dark -> light, glow colour or None)
    "abyssal_stalactite": (P("#15171e", "#20232d", "#2c303d", "#3b4152", "#4f576c"), None),
    "mineral_stalactite": (P("#34200f", "#4e3019", "#684226", "#865834", "#a87448"), None),
    "crystal_stalactite": (P("#0d4a5c", "#13667c", "#1b86a0", "#2aa8c2", "#5fd3e6"), hexrgb("#b8f4ff")),
    "thermal_stalactite": (P("#1e0a07", "#33110b", "#4d1a10", "#6e2714", "#96391a"), hexrgb("#ff9a3a")),
}
CAVE_PLANT = {
    "cave_green": P("#0c2016", "#133222", "#1b452f", "#255b3d", "#357550"),
    "cave_fern": P("#0f2e22", "#17432f", "#215a3f", "#2f7352"),
    "cave_tube": P("#361226", "#521b36", "#732747", "#973a5a", "#bd5a74"),
    "cave_coral": P("#4a1c0e", "#6e2c14", "#96421c", "#c0602a"),
    "cave_sponge": P("#5a4a12", "#7a661a", "#9c8424", "#c0a432", "#dcc24a"),
    "cave_kelp": P("#0c2414", "#14361d", "#1e4a28", "#2b6034", "#3d7a40"),
    "giant_cave_kelp": P("#1f2410", "#2e3517", "#40491f", "#555f28", "#6c7834"),
    "ancient": P("#10262a", "#17363b", "#20484e", "#2c5c62", "#3d757a"),
    "vine": P("#10281a", "#193b26", "#245033", "#326843"),
    "root": P("#231710", "#342218", "#472f21", "#5c3e2c", "#744f38"),
    "abyssal_vine": P("#1a1030", "#281a48", "#382462", "#4c3280", "#6448a0"),
    "thermal_plant": P("#0f1f12", "#16301b", "#1f4224", "#2a5630"),
    "moss": P("#0e2317", "#163422", "#20462e", "#2c5b3c"),
    "mineral_vine": P("#5a3818", "#7a4c20", "#9a622a", "#c08034"),
}
GLOW = {"cyan": hexrgb("#9ff6ff"), "blue": hexrgb("#c8f0ff"), "orange": hexrgb("#ffae4a"), "violet": hexrgb("#f0d0ff"),
        "green": hexrgb("#d4ffb0")}

# ================================================================ textures


def glow_diff(base, over):
    """The pixels `over` changed relative to `base`, as a separate emissive overlay."""
    g = Canvas()
    diff = np.any(base.rgba != over.rgba, axis=2)
    g.rgba[diff] = over.rgba[diff]
    return g


def rock_textures():
    t, glows = {}, {}
    t["abyssal_cave_rock"] = rock("abyssal_cave_rock", CAVE_ROCK["abyssal_cave_rock"], strata=0.1, cracks=2, speckle=5)
    t["dark_cave_rock"] = rock("dark_cave_rock", CAVE_ROCK["dark_cave_rock"], cracks=3, speckle=3)
    wet = rock("wet_cave_rock", CAVE_ROCK["wet_cave_rock"], strata=0.06, speckle=4)
    t["wet_cave_rock"] = flecks(wet, "wet_cave_rock", P("#6d8fa6", "#9dbdd0"), density=0.05, threshold=0.9)  # wet sheen
    t["layered_cave_rock"] = rock("layered_cave_rock", CAVE_ROCK["layered_cave_rock"], strata=0.45, speckle=3)
    t["mineral_cave_rock"] = flecks(rock("mineral_cave_rock", CAVE_ROCK["mineral_cave_rock"], strata=0.25, speckle=4),
                                    "mineral_cave_rock", P("#9a5a2a", "#c47a3a", "#e0a050"), density=0.12, threshold=0.82)
    thermal = rock("thermal_cave_rock", CAVE_ROCK["thermal_cave_rock"], cracks=1, holes=2)
    t["thermal_cave_rock"] = veined(thermal, "thermal_cave_rock", P("#a8380e", "#d85e18", "#f09a36"), count=3)
    glows["thermal_cave_rock"] = glow_diff(thermal, t["thermal_cave_rock"])
    crystal = rock("crystal_cave_rock", CAVE_ROCK["crystal_cave_rock"], strata=0.08, speckle=3)
    t["crystal_cave_rock"] = flecks(crystal, "crystal_cave_rock", P("#4fc6de", "#9ff0ff", "#e0ffff"), density=0.08, threshold=0.84)
    glows["crystal_cave_rock"] = glow_diff(crystal, t["crystal_cave_rock"])
    t["organic_cave_rock"] = flecks(rock("organic_cave_rock", CAVE_ROCK["organic_cave_rock"], strata=0.12, speckle=5),
                                    "organic_cave_rock", P("#3e5a2c", "#577838"), density=0.12, threshold=0.78)
    t["eroded_cave_rock"] = rock("eroded_cave_rock", CAVE_ROCK["eroded_cave_rock"], strata=0.08, speckle=1)
    t["cave_sediment"] = sediment("cave_sediment", P("#3f454d", "#4b525b", "#59616b", "#69717c"), grain=0.5)
    t["cave_mud"] = mud("cave_mud", P("#231a14", "#2e231b", "#3a2d23", "#47382c"))
    t["cave_mineral_crust"] = flecks(t["cave_sediment"], "cave_mineral_crust", P("#40200f", "#5e3016", "#7c441f", "#9a5a2a"),
                                     density=0.4, threshold=0.45)
    return t, glows


def speleothem_texture(name, thickness, down, pal, glow):
    """A tapering mineral column. Drawn root-at-top for stalactites, flipped for stalagmites."""
    c = Canvas()
    rng = rng_for(name + thickness)
    if thickness == "base":
        widths = [6.0] * N
    elif thickness == "middle":
        widths = [4.8] * N
    elif thickness == "frustum":
        widths = [3.6 - 1.8 * y / (N - 1) for y in range(N)]
    elif thickness == "tip":
        widths = [max(0.0, 2.2 - 2.0 * y / 10) if y <= 10 else 0 for y in range(N)]
    else:  # tip_merge: thin, reaching all the way to the other tip
        widths = [max(0.6, 2.2 - 1.6 * y / (N - 1)) for y in range(N)]
    glows = []
    for y in range(N):
        hw = widths[y] + (rng.random() - 0.5) * 0.4 if widths[y] > 0 else 0
        if hw <= 0.2:
            continue
        ring = (y + int(rng.integers(0, 2))) % 4 == 0
        for x in range(N):
            d = x + 0.5 - 8
            if abs(d) > hw:
                continue
            t = (d + hw) / (2 * hw)  # 0 left edge .. 1 right edge; lit from the left
            idx = int((1 - t) * (len(pal) - 1) + 0.5)
            if ring:
                idx = max(0, idx - 1)
            c.put(x, y, pal[max(0, min(len(pal) - 1, idx))])
            if glow is not None and abs(d) < 0.8 and rng.random() < 0.3:
                glows.append((x, y))
    g = glow_mask(c, glows, glow) if glow is not None else None
    if not down:
        c.rgba = c.rgba[::-1].copy()
        if g is not None:
            g.rgba = g.rgba[::-1].copy()
    return c, g


def sway_frames(c, frames=4, amplitude=1.0, anchor_top=False):
    """Frames of a plant swaying: horizontal shear growing away from its anchored end."""
    out = []
    for f in range(frames):
        phase = math.sin(f / frames * math.pi * 2)
        frame = np.zeros_like(c.rgba)
        for y in range(N):
            reach = (y / (N - 1)) if anchor_top else (1 - y / (N - 1))
            shift = int(round(phase * amplitude * reach))
            frame[y] = np.roll(c.rgba[y], shift, axis=0)
        out.append(frame)
    return out


def save_animated(frames, path, frametime=12):
    Image.fromarray(np.concatenate(frames, axis=0), "RGBA").save(path)
    with open(path + ".mcmeta", "w", encoding="utf-8") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f, indent=2)
        f.write("\n")


def grass_like(name, pal, top, blades=6):
    c = Canvas()
    rng = rng_for(name, 1 if top else 2)
    for i in range(blades):
        x = 1 + i * 14 // blades + int(rng.integers(0, 2))
        h = int(rng.integers(8, 15)) if top else 16
        lean = float(rng.random() - 0.5) * (1.6 if top else 0.4)
        blade(c, x, 15, h, lean, pal[1:] if top else pal[1:-1], curl=float(rng.random() - 0.5) * 1.5)
    return c


def kelp_like(name, pal, top, thick=False, hanging=False, bulb=None):
    """Kelp fronds on a stem. Hanging kelp is drawn attached at the top with its end below."""
    c = Canvas()
    rng = rng_for(name, 5 if top else 6)
    stem_w = 3 if thick else 2
    y0 = 5 if top else 0
    for y in range(y0, N):
        for k in range(stem_w):
            c.put(7 + k, y, pal[1 + k % 2])
    for y in range(y0 + 1, N, 3):
        side = 1 if (y // 3) % 2 == 0 else -1
        length = int(rng.integers(3, 6)) + (2 if thick else 0)
        for k in range(1, length):
            x = (7 + stem_w) + k - 1 if side > 0 else 7 - k
            c.put(x, y - k // 2, pal[min(len(pal) - 1, 2 + k // 3)])
            c.put(x, y - k // 2 + 1, pal[1])
    tips = []
    if top:
        if bulb:
            disc(c, 8, 3, 2.2, bulb)
            tips = [(7, 2), (8, 2), (8, 3), (7, 3)]
        else:
            for k, y in enumerate(range(5, 0, -1)):
                for dx in range(-k, k + 1):
                    c.put(8 + dx, y, pal[min(len(pal) - 1, 2 + abs(dx) // 2)])
    if hanging:
        c.rgba = c.rgba[::-1].copy()
        tips = [(x, N - 1 - y) for x, y in tips]
    return c, tips


def strands(name, pal, tip, bulb=None, glow=None, count=2, thick=1):
    """Hanging vines or roots: strands attached at the top; the tip segment ends partway down, maybe in a bulb."""
    c = Canvas()
    rng = rng_for(name, 11 if tip else 12)
    glows = []
    for i in range(count):
        x = 4 + i * (8 // max(1, count - 1)) + int(rng.integers(-1, 2)) if count > 1 else 8
        end = int(rng.integers(9, 14)) if tip else N
        for y in range(end):
            wobble = int(round(math.sin(y * 0.6 + i * 2.1)))
            for k in range(thick):
                c.put(x + wobble + k, y, pal[(y + k) % len(pal)])
            if y % 4 == 2 and not thick > 1:
                c.put(x + wobble + (1 if i % 2 else -1), y, pal[-1])
        if tip and bulb:
            disc(c, x + int(round(math.sin(end * 0.6 + i * 2.1))) + 0.5, end + 0.5, 1.6, bulb)
            glows += [(x, end), (x + 1, end), (x, end - 1)]
    g = glow_mask(c, glows, glow) if glow and glows else None
    return c, g


def cave_fern(name, pal):
    c = Canvas()
    for y in range(4, 16):
        c.put(8, y, pal[1])
    for i, y in enumerate(range(14, 4, -2)):
        length = 2 + (14 - y) // 3 if y > 9 else 6 - (9 - y)
        for k in range(1, max(2, length)):
            c.put(8 + k, y - k // 2, pal[min(len(pal) - 1, 1 + k // 2)])
            c.put(8 - k, y - k // 2, pal[min(len(pal) - 1, k // 2)])
    return c


def coral_like(name, pal):
    from pixelart import branch
    c = Canvas()
    rng = rng_for(name)
    branch(c, 8, 16, 5, 1.57, pal, rng, 0, [])
    branch(c, 5, 16, 3, 1.9, pal, rng, 1, [])
    return c


def sponge_like(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for cx, cy, r in ((5, 11, 3.5), (10, 10, 4.0), (8, 6, 3.0)):
        disc(c, cx, cy, r, pal)
    for _ in range(10):
        x, y = rng.integers(3, 14), rng.integers(4, 15)
        if c.alpha(x, y):
            c.put(x, y, darken(pal[0], 0.5))
    return c


def bloom(name, stem, petals, glow, centre):
    c = Canvas()
    for y in range(8, 16):
        c.put(8, y, stem)
    c.put(7, 13, stem)
    c.put(9, 11, stem)
    glows = []
    for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1), (-3, -1), (3, -1), (0, -3)):
        c.put(8 + dx, 5 + dy, petals[min(len(petals) - 1, abs(dx) + abs(dy) - 1)])
        glows.append((8 + dx, 5 + dy))
    c.put(8, 5, centre)
    glows.append((8, 5))
    return c, glow_mask(c, glows, glow)


def crystal_sprout(name, crystal_pal, stem_pal):
    c = Canvas()
    for y in range(9, 16):
        c.put(8, y, stem_pal[1])
    blade(c, 7, 15, 5, -2, stem_pal)
    blade(c, 9, 15, 5, 2, stem_pal)
    crystals = Canvas()
    shard(crystals, 8, 9, 6, 3, 0, crystal_pal)
    shard(crystals, 5, 11, 4, 2, -0.4, crystal_pal)
    pts = []
    for y in range(N):
        for x in range(N):
            if crystals.alpha(x, y):
                c.put(x, y, tuple(crystals.rgba[y, x][:3]))
                pts.append((x, y))
    return c, glow_mask(c, pts[::2], lighten(crystal_pal[-1], 0.3))


def tentacle_plant(name, pal, glow):
    c = Canvas()
    rng = rng_for(name)
    tips = []
    for y in range(12, 16):
        for x in range(6, 11):
            c.put(x, y, pal[1])
    for i in range(6):
        x = 5 + i
        h = int(rng.integers(5, 10))
        lean = (i - 2.5) * 0.6
        blade(c, x, 12, h, lean, pal, curl=float(rng.random() - 0.5))
        tips.append((int(round(x + lean * h * 0.3)), 12 - h + 1))
    return c, glow_mask(c, tips, glow)


def ancient_trunk(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for y in range(N):
        for x in range(N):
            shade = 1 + (x * 3) // N
            if (y + (x // 4) * 2) % 6 == 0:
                shade = max(0, shade - 1)  # scale ridges
            if rng.random() < 0.06:
                shade = min(len(pal) - 1, shade + 1)
            c.put(x, y, pal[min(len(pal) - 1, shade)])
    return c


def ancient_crown(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for y in range(8, 16):
        c.put(7, y, pal[1])
        c.put(8, y, pal[2])
    for i in range(7):
        a = math.pi * (0.1 + 0.8 * i / 6)
        length = int(rng.integers(5, 8))
        for k in range(1, length):
            x = int(round(8 + math.cos(a) * k * 1.1))
            y = int(round(8 - math.sin(a) * k + k * k * 0.06))
            c.put(x, y, pal[min(len(pal) - 1, 2 + k // 3)])
    return c


def rubble(name):
    c = Canvas()
    rng = rng_for(name)
    for _ in range(8):
        disc(c, rng.integers(2, 14), rng.integers(2, 14), rng.integers(1, 3) + 0.4, P("#1b1d24", "#2a2d36", "#3b3f4a", "#50555f"))
    return c


def shards_carpet(name):
    c = Canvas()
    rng = rng_for(name)
    glows = []
    for _ in range(7):
        x, y = int(rng.integers(1, 15)), int(rng.integers(1, 15))
        length = int(rng.integers(2, 4))
        dx = 1 if rng.random() < 0.5 else -1
        for k in range(length):
            c.put(x + dx * k, y + k // 2, (P("#2aa8c2", "#5fd3e6", "#c8f6ff"))[min(2, k)])
        glows.append((x, y))
    return c, glow_mask(c, glows, GLOW["cyan"])


def fallen_kelp(name, pal):
    c = Canvas()
    rng = rng_for(name)
    for i in range(3):
        y = 3 + i * 5 + int(rng.integers(-1, 2))
        for x in range(1, 15):
            yy = y + int(round(math.sin(x * 0.5 + i) * 1.2))
            c.put(x, yy, pal[1 + (x + i) % 3])
            if x % 4 == 0:
                c.put(x, yy - 1, pal[3])
    return c


def moss_film(name, pal):
    c = Canvas()
    rng = rng_for(name)
    v = rng.random((N, N))
    for y in range(N):
        for x in range(N):
            if v[y, x] < 0.62:
                c.put(x, y, pal[int(rng.integers(0, len(pal)))])
    return c


def needle(name, pal):
    c = Canvas()
    shard(c, 8, 15, 15, 3, 0, pal)
    shard(c, 5, 15, 9, 2, -0.15, pal)
    shard(c, 11, 15, 11, 2, 0.12, pal)
    return c

# ================================================================ block catalogue

CAVE_CUBES = ["abyssal_cave_rock", "dark_cave_rock", "wet_cave_rock", "layered_cave_rock", "mineral_cave_rock", "thermal_cave_rock",
              "crystal_cave_rock", "organic_cave_rock", "eroded_cave_rock", "cave_sediment", "cave_mud", "cave_mineral_crust"]
CAVE_SOFT = ["cave_sediment", "cave_mud"]
CAVE_ROCKS = [n for n in CAVE_CUBES if n not in CAVE_SOFT]

CAVE_NAMES = {
    "abyssal_cave_rock": ("Abyssal Cave Rock", "深淵の洞窟岩"), "dark_cave_rock": ("Dark Cave Rock", "暗色洞窟岩"),
    "wet_cave_rock": ("Wet Cave Rock", "濡れた洞窟岩"), "layered_cave_rock": ("Layered Cave Rock", "層状洞窟岩"),
    "mineral_cave_rock": ("Mineral Cave Rock", "鉱物洞窟岩"), "thermal_cave_rock": ("Thermal Cave Rock", "熱水洞窟岩"),
    "crystal_cave_rock": ("Crystal Cave Rock", "結晶洞窟岩"), "organic_cave_rock": ("Organic Cave Rock", "有機質洞窟岩"),
    "eroded_cave_rock": ("Eroded Cave Rock", "侵食洞窟岩"), "cave_sediment": ("Cave Sediment", "洞窟堆積物"),
    "cave_mud": ("Cave Mud", "洞窟泥"), "cave_mineral_crust": ("Cave Mineral Crust", "洞窟の鉱物クラスト"),
    "abyssal_stalactite": ("Abyssal Stalactite", "深淵の鍾乳石"), "mineral_stalactite": ("Mineral Stalactite", "鉱物鍾乳石"),
    "crystal_stalactite": ("Crystal Stalactite", "結晶鍾乳石"), "thermal_stalactite": ("Thermal Stalactite", "熱水鍾乳石"),
    "crystal_needle": ("Crystal Needle", "結晶の針"),
    "cave_grass": ("Cave Grass", "洞窟草"), "cave_fern": ("Cave Fern", "洞窟シダ"), "cave_tube_plant": ("Cave Tube Plant", "洞窟チューブ植物"),
    "cave_coral": ("Cave Coral Plant", "洞窟サンゴ"), "cave_sponge": ("Cave Sponge", "洞窟カイメン"), "cave_kelp": ("Cave Kelp", "洞窟コンブ"),
    "cave_crystal_plant": ("Cave Crystal Plant", "洞窟結晶植物"), "cave_bloom": ("Cave Bloom", "洞窟の光花"),
    "thermal_plant": ("Thermal Plant", "熱水植物"), "giant_cave_kelp": ("Giant Cave Kelp", "巨大洞窟コンブ"),
    "ancient_cave_plant": ("Ancient Cave Plant", "古代洞窟植物"),
    "cave_vine": ("Cave Vine", "洞窟の蔓"), "hanging_kelp": ("Hanging Kelp", "垂れコンブ"), "cave_root": ("Cave Root", "洞窟の根"),
    "abyssal_vine": ("Abyssal Vine", "深淵の蔓"), "deep_root": ("Deep Root Plant", "深根植物"),
    "wall_fern": ("Wall Fern", "壁シダ"), "wall_mineral_vine": ("Wall Mineral Vine", "壁面鉱物蔓"), "cave_moss": ("Cave Moss", "洞窟苔"),
    "cave_rubble": ("Cave Rubble", "洞窟の瓦礫"), "crystal_shards": ("Broken Crystal", "砕けた結晶"), "fallen_kelp": ("Fallen Kelp", "倒れたコンブ"),
}

# ================================================================ models


def cube_with_glow(base, glow):
    """A full cube plus a coplanar emissive overlay (like vanilla grass side overlays): glowing veins without block light."""
    faces = ("down", "up", "north", "south", "west", "east")
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": base, "all": base, "glow": glow},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: {"texture": "#all", "cullface": f} for f in faces}},
                {"from": [0, 0, 0], "to": [16, 16, 16], "forge_data": {"block_light": 15, "sky_light": 15},
                 "faces": {f: {"texture": "#glow", "cullface": f} for f in faces}}]}


def wall_plant_model(tex):
    """Built facing north (rooted in the block to the south): a layer flat on the wall and one standing off it."""
    elements = []
    for z in (15.4, 11.0):
        elements.append({"from": [0, 0, z], "to": [16, 16, z], "shade": False,
                         "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#plant"}, "south": {"uv": [16, 0, 0, 16], "texture": "#plant"}}})
    return {"ambientocclusion": False, "render_type": "minecraft:cutout", "textures": {"particle": tex, "plant": tex}, "elements": elements}


def multiface_model(tex):
    """One face of a film, on the north side; the blockstate rotates it onto each covered face."""
    return {"ambientocclusion": False, "render_type": "minecraft:cutout", "textures": {"particle": tex, "film": tex},
            "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1], "shade": False,
                          "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#film"}, "south": {"uv": [0, 0, 16, 16], "texture": "#film"}}}]}


def generate(write, bs, bm, im, tex, ref, cross_model, tube_column_model):
    """Writes every cave block's textures, models and blockstates; returns (cube names, cluster names) for tags."""
    t, glows = rock_textures()
    for name in CAVE_CUBES:
        t[name].save(tex(name))
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        if name in glows:
            glows[name].save(tex(name + "_glow"))
            write(bm(name), cube_with_glow(ref(name), ref(name + "_glow")))
        else:
            write(bm(name), {"parent": "minecraft:block/cube_all", "textures": {"all": ref(name)}})
        write(im(name), {"parent": ref(name)})

    # Speleothems: one model per thickness and direction, like pointed dripstone.
    for name, (pal, glow) in SPELEOTHEM.items():
        variants = {}
        for thickness in ("tip_merge", "tip", "frustum", "middle", "base"):
            for direction in ("down", "up"):
                key = f"{name}_{direction}_{thickness}"
                c, g = speleothem_texture(name, thickness, direction == "down", pal, glow)
                c.save(tex(key))
                if g is not None:
                    g.save(tex(key + "_glow"))
                write(bm(key), cross_model(ref(key), ref(key + "_glow") if g is not None else None))
                variants[f"thickness={thickness},vertical_direction={direction}"] = {"model": ref(key)}
        write(bs(name), {"variants": variants})
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(f"{name}_up_frustum")}})

    # Crystal needle: a cluster that can face any direction.
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
    needle("crystal_needle", P("#3a6f8a", "#5fa8c8", "#9fe0f4", "#dcfbff")).save(tex("crystal_needle"))
    write(bs("crystal_needle"), {"variants": {f"facing={f}": {"model": ref("crystal_needle"), **r} for f, r in rot.items()}})
    write(bm("crystal_needle"), cross_model(ref("crystal_needle")))
    write(im("crystal_needle"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("crystal_needle")}})

    # Single-block floor plants: (texture, glow overlay or None).
    singles = {
        "cave_fern": (cave_fern("cave_fern", CAVE_PLANT["cave_fern"]), None),
        "cave_coral": (coral_like("cave_coral", CAVE_PLANT["cave_coral"]), None),
        "cave_sponge": (sponge_like("cave_sponge", CAVE_PLANT["cave_sponge"]), None),
        "cave_crystal_plant": crystal_sprout("cave_crystal_plant", P("#1b86a0", "#2aa8c2", "#5fd3e6", "#c8f6ff"), CAVE_PLANT["cave_fern"]),
        "cave_bloom": bloom("cave_bloom", hexrgb("#1a3a4a"), P("#2a7ab0", "#58aee0", "#a8e0ff"), GLOW["blue"], hexrgb("#f0fcff")),
        "thermal_plant": tentacle_plant("thermal_plant", CAVE_PLANT["thermal_plant"], GLOW["orange"]),
    }
    for name, (c, g) in singles.items():
        c.save(tex(name))
        if g is not None:
            g.save(tex(name + "_glow"))
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), cross_model(ref(name), ref(name + "_glow") if g is not None else None))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name)}})

    # Stacking floor plants (top / body), with sway animation for the soft ones.
    stacking = {
        "cave_grass": (grass_like("cave_grass", CAVE_PLANT["cave_green"], True), grass_like("cave_grass", CAVE_PLANT["cave_green"], False), 1.0),
        "cave_kelp": (kelp_like("cave_kelp", CAVE_PLANT["cave_kelp"], True)[0], kelp_like("cave_kelp", CAVE_PLANT["cave_kelp"], False)[0], 1.0),
        "giant_cave_kelp": (kelp_like("giant_cave_kelp", CAVE_PLANT["giant_cave_kelp"], True, thick=True)[0],
                            kelp_like("giant_cave_kelp", CAVE_PLANT["giant_cave_kelp"], False, thick=True)[0], 1.0),
    }
    from gen_deep_assets import tube
    stacking["cave_tube_plant"] = (tube("cave_tube_plant", CAVE_PLANT["cave_tube"], True)[0], tube("cave_tube_plant", CAVE_PLANT["cave_tube"], False)[0], 0)
    for name, (top, body, sway) in stacking.items():
        for key, c in ((name + "_top", top), (name, body)):
            if sway:
                save_animated(sway_frames(c, amplitude=sway), tex(key))
            else:
                c.save(tex(key))
        write(bs(name), {"variants": {"top=true": {"model": ref(name + "_top")}, "top=false": {"model": ref(name)}}})
        write(bm(name + "_top"), cross_model(ref(name + "_top")))
        write(bm(name), cross_model(ref(name)))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name + "_top")}})

    # Ancient cave plant: a scaly 3D trunk with a frond crown on top.
    ancient_trunk("ancient_cave_plant", CAVE_PLANT["ancient"]).save(tex("ancient_cave_plant"))
    ancient_crown("ancient_cave_plant_top", CAVE_PLANT["ancient"]).save(tex("ancient_cave_plant_top"))
    write(bs("ancient_cave_plant"), {"variants": {"top=true": {"model": ref("ancient_cave_plant_top")}, "top=false": {"model": ref("ancient_cave_plant")}}})
    write(bm("ancient_cave_plant"), tube_column_model(ref("ancient_cave_plant"), ref("ancient_cave_plant")))
    write(bm("ancient_cave_plant_top"), cross_model(ref("ancient_cave_plant_top")))
    write(im("ancient_cave_plant"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("ancient_cave_plant_top")}})

    # Hanging plants (tip / body), attached at the top.
    hanging = {
        "cave_vine": (strands("cave_vine", CAVE_PLANT["vine"], True, P("#6a8a2a", "#a8c850", "#e0f890"), GLOW["green"]),
                      strands("cave_vine", CAVE_PLANT["vine"], False), 0),
        "hanging_kelp": (kelp_like("hanging_kelp", CAVE_PLANT["cave_kelp"], True, hanging=True)[0], None, 1.0),
        "cave_root": (strands("cave_root", CAVE_PLANT["root"], True, count=3), strands("cave_root", CAVE_PLANT["root"], False, count=3), 0),
        "abyssal_vine": (strands("abyssal_vine", CAVE_PLANT["abyssal_vine"], True, P("#6a40c0", "#a080f0", "#f0e0ff"), GLOW["violet"]),
                         strands("abyssal_vine", CAVE_PLANT["abyssal_vine"], False), 0),
        "deep_root": (strands("deep_root", CAVE_PLANT["root"], True, count=2, thick=3), strands("deep_root", CAVE_PLANT["root"], False, count=2, thick=3), 0),
    }
    for name, (tip, body, sway) in hanging.items():
        if name == "hanging_kelp":
            tip_c, tip_g = tip, None
            body_c = kelp_like("hanging_kelp", CAVE_PLANT["cave_kelp"], False, hanging=True)[0]
        else:
            tip_c, tip_g = tip
            body_c = body[0]
        for key, c in ((name + "_tip", tip_c), (name, body_c)):
            if sway:
                save_animated(sway_frames(c, amplitude=sway, anchor_top=True), tex(key))
            else:
                c.save(tex(key))
        if tip_g is not None:
            tip_g.save(tex(name + "_tip_glow"))
        write(bs(name), {"variants": {"tip=true": {"model": ref(name + "_tip")}, "tip=false": {"model": ref(name)}}})
        write(bm(name + "_tip"), cross_model(ref(name + "_tip"), ref(name + "_tip_glow") if tip_g is not None else None))
        write(bm(name), cross_model(ref(name)))
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name + "_tip")}})

    # Wall plants: rotate the north-facing model to each facing.
    from gen_deep_assets import fern
    walls = {"wall_fern": fern("wall_fern", CAVE_PLANT["cave_fern"]),
             "wall_mineral_vine": strands("wall_mineral_vine", CAVE_PLANT["mineral_vine"], False, count=3)[0]}
    for name, c in walls.items():
        c.save(tex(name))
        write(bm(name), wall_plant_model(ref(name)))
        write(bs(name), {"variants": {f"facing={f}": {"model": ref(name), **({"y": y} if y else {})}
                                      for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": ref(name)}})

    # Cave moss: a film on any combination of faces.
    moss_film("cave_moss", CAVE_PLANT["moss"]).save(tex("cave_moss"))
    write(bm("cave_moss"), multiface_model(ref("cave_moss")))
    face_rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    write(bs("cave_moss"), {"multipart": [{"when": {face: "true"}, "apply": {"model": ref("cave_moss"), **r, "uvlock": True}} for face, r in face_rot.items()]})
    write(im("cave_moss"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("cave_moss")}})

    # Debris carpets.
    shards, shards_glow = shards_carpet("crystal_shards")
    carpets = {"cave_rubble": (rubble("cave_rubble"), None), "crystal_shards": (shards, shards_glow),
               "fallen_kelp": (fallen_kelp("fallen_kelp", CAVE_PLANT["cave_kelp"]), None)}
    for name, (c, g) in carpets.items():
        c.save(tex(name))
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        if g is not None:
            g.save(tex(name + "_glow"))
            model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                     "textures": {"particle": ref(name), "wool": ref(name), "glow": ref(name + "_glow")},
                     "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#wool"},
                                                                                     "down": {"uv": [0, 0, 16, 16], "texture": "#wool", "cullface": "down"}}},
                                  {"from": [0, 0, 0], "to": [16, 1, 16], "forge_data": {"block_light": 15, "sky_light": 15},
                                   "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#glow"}}}]}
            write(bm(name), model)
        else:
            write(bm(name), {"parent": "minecraft:block/carpet", "render_type": "minecraft:cutout", "textures": {"wool": ref(name)}})
        write(im(name), {"parent": ref(name)})

    generate_cavern(write, bs, bm, im, tex, ref, cross_model)


# ================================================================ massive caverns
#
# Blocks the massive cavern decoration places: coloured crystal masses for crystal forests (glowing seams from an
# emissive overlay, no block light, so a forest of them stays dim), the ancient deep-sea plant (stem, roots, fronds),
# crystal kelp, luminous roof moss and the shimmering surface of brine pools.

CAVERN_CRYSTALS = {
    # name: crystal colour (the body is a dark shade of it; seams and glints glow)
    "cyan_crystal_block": "#2aa8c2", "blue_crystal_block": "#3a6ad8", "violet_crystal_block": "#8a52d4",
    "green_crystal_block": "#5ad08a", "white_crystal_block": "#e8f4ff", "amber_crystal_block": "#e89030",
}
CAVERN_AXE = ["ancient_stem", "ancient_root"]
CAVERN_HOE = ["ancient_frond"]
# Fluid surfaces drop nothing.
CAVERN_NO_DROP = ["brine_surface"]
CAVERN_PLANT = {
    "crystal_kelp": P("#08303a", "#0d4a58", "#146a7a", "#1f8fa2", "#4fc6de"),
    "ancient_bark": P("#12242a", "#1a3338", "#234449", "#2f575c", "#406f74"),
    "ancient_wood": P("#2a3a34", "#3a4d44", "#4d6456", "#627c69", "#7c957e"),
    "frond": P("#0d2819", "#143c26", "#1d5434", "#296c44", "#3a8656"),
}
CAVERN_NAMES = {
    "cyan_crystal_block": ("Cyan Crystal Block", "水色の結晶ブロック"), "blue_crystal_block": ("Blue Crystal Block", "青い結晶ブロック"),
    "violet_crystal_block": ("Violet Crystal Block", "紫の結晶ブロック"), "green_crystal_block": ("Green Crystal Block", "緑の結晶ブロック"),
    "white_crystal_block": ("White Crystal Block", "白い結晶ブロック"), "amber_crystal_block": ("Amber Crystal Block", "琥珀色の結晶ブロック"),
    "crystal_kelp": ("Crystal Kelp", "結晶コンブ"), "ancient_stem": ("Ancient Stem", "古代植物の幹"),
    "ancient_root": ("Ancient Root", "古代植物の根"), "ancient_frond": ("Ancient Frond", "古代植物の葉"),
    "luminous_moss": ("Luminous Moss", "発光苔"), "brine_surface": ("Brine Surface", "塩水湖の水面"),
}
CAVE_NAMES.update(CAVERN_NAMES)


def crystal_mass(name, colour):
    """A dark faceted crystal body; the seams between its facets and a few glints glow."""
    col = hexrgb(colour)
    body = [darken(col, t) for t in (0.9, 0.86, 0.82, 0.78, 0.74, 0.7)]
    base = facets(name, body, glints=0)
    over = base.copy()
    rng = rng_for(name, 9)
    for y in range(N):
        for x in range(N):
            here = base.get(x, y)
            if (here != base.get(x + 1, y) or here != base.get(x, y + 1)) and rng.random() < 0.35:
                over.put(x, y, mix(darken(col, 0.2), lighten(col, 0.25), rng.random()))
    for _ in range(int(rng.integers(4, 7))):
        x, y = rng.integers(0, N, 2)
        over.put(x, y, lighten(col, 0.75))
    return over, glow_diff(base, over)


def ancient_bark(name, pal):
    """Living-wood bark: vertical fibres, dark furrows and faint scale ridges."""
    rng = rng_for(name)
    fibre = rng.random(N)
    v = fbm(rng, ((2, 0.3), (4, 0.3), (8, 0.4))) * 0.45
    for y in range(N):
        for x in range(N):
            v[y, x] += fibre[x] * 0.4 + 0.15 * math.sin((y + fibre[x] * 9) * 0.9)
    c = Canvas.from_values(v, pal)
    for _ in range(3):
        x = int(rng.integers(0, N))
        for y in range(N):
            c.put(x % N, y, darken(pal[0], 0.25))
            if rng.random() < 0.25:
                x += int(rng.choice([-1, 1]))
    for y in range(0, N, 6):
        for x in range(N):
            if rng.random() < 0.35:
                c.put(x, (y + x // 4) % N, pal[-1])
    return c


def ancient_rings(name, bark, wood):
    """Cut end of the stem: growth rings inside a rim of bark."""
    rng = rng_for(name)
    wobble = value_noise(rng, 4)
    c = Canvas()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.8:
                c.put(x, y, bark[1 + int(wobble[y, x] * 2)])
            else:
                ring = int(d * 1.3 + wobble[y, x] * 1.5) % 3
                c.put(x, y, wood[1 + ring])
    c.put(7, 7, wood[-1])
    c.put(8, 8, wood[0])
    return c


def gnarled_root(name, pal):
    """Twisted root wood: diagonal fibres winding around dark knots."""
    rng = rng_for(name)
    n = fbm(rng)
    v = n * 0.5
    for y in range(N):
        for x in range(N):
            v[y, x] += 0.5 * (0.5 + 0.5 * math.sin((x * 0.785 + y * 0.393) + n[y, x] * 4))
    c = Canvas.from_values(v, pal)
    for _ in range(2):
        kx, ky = rng.integers(2, 14, 2)
        disc(c, kx, ky, 1.6, [pal[0], pal[1], pal[0]])
        c.put(kx, ky, darken(pal[0], 0.4))
    return c


def frond_leaves(name, pal):
    """Drooping fronds on a see-through background, like leaves: midribs with leaflets on both sides."""
    rng = rng_for(name)
    c = Canvas()
    for i in range(6):
        x = i * 16 / 6 + rng.random() * 2
        y = rng.random() * 16
        lean = rng.random() - 0.5
        for k in range(14):
            px, py = int(round(x + lean * k * 0.4)) % N, int(round(y + k)) % N
            c.put(px, py, pal[1 + (k // 5) % 2])
            if k % 2 == 0:
                for side in (-1, 1):
                    for j in range(1, 3 + int(rng.integers(0, 2))):
                        c.put((px + side * j) % N, (py + j // 2) % N, pal[min(len(pal) - 1, 2 + j)])
    return c


def luminous_film(name):
    """Sparse dim moss with pale cyan pinpoints; the pinpoints go on their own emissive layer."""
    rng = rng_for(name)
    film = Canvas()
    dim = P("#0b1c22", "#112c35", "#193f4b")
    for y in range(N):
        for x in range(N):
            if rng.random() < 0.26:
                film.put(x, y, dim[int(rng.integers(0, len(dim)))])
    points = []
    while len(points) < 9:
        x, y = (int(v) for v in rng.integers(0, N, 2))
        if all(abs(x - px) + abs(y - py) > 3 for px, py in points):
            points.append((x, y))
    glow = Canvas()
    for i, (x, y) in enumerate(points):
        col = hexrgb(("#9fe8ff", "#d8f8ff", "#7ac8ff")[i % 3])
        film.put(x, y, col)
        glow.put(x, y, col)
    return film, glow


def brine_frames(name, frames=8):
    """Translucent teal-grey sheet with slow interfering ripples; the pattern tiles and loops."""
    rng = rng_for(name)
    offset = value_noise(rng, 4)
    pal = P("#244a4a", "#2f5d5a", "#3e726c", "#5a9088", "#9cc8bc", "#dff4ea")
    out = []
    for f in range(frames):
        t = f / frames * math.pi * 2
        frame = np.zeros((N, N, 4), dtype=np.uint8)
        for y in range(N):
            for x in range(N):
                w1 = math.sin(math.pi * 2 * (2 * x + y) / N + t + offset[y, x] * 2)
                w2 = math.sin(math.pi * 2 * (x - 2 * y) / N - t)
                v = min(0.999, max(0.0, 0.5 + 0.22 * w1 + 0.22 * w2 + 0.3 * (offset[y, x] - 0.5)))
                idx = int(v * len(pal))
                frame[y, x] = (*pal[idx], 110 + int(v * 90))
        out.append(frame)
    return out


def glow_film_model(tex, glow):
    """A multiface film with an emissive layer just in front of it: its bright points render full-bright."""
    film = multiface_model(tex)
    film["textures"]["glow"] = glow
    film["elements"].append({"from": [0, 0, 0.15], "to": [16, 16, 0.15], "shade": False, "forge_data": {"block_light": 15, "sky_light": 15},
                             "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#glow"}, "south": {"uv": [0, 0, 16, 16], "texture": "#glow"}}})
    return film


def generate_cavern(write, bs, bm, im, tex, ref, cross_model):
    # Crystal masses: full cubes with an emissive overlay on their seams and glints.
    for name, colour in CAVERN_CRYSTALS.items():
        c, g = crystal_mass(name, colour)
        c.save(tex(name))
        g.save(tex(name + "_glow"))
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), cube_with_glow(ref(name), ref(name + "_glow")))
        write(im(name), {"parent": ref(name)})

    # Crystal kelp: stiff (no sway), glowing crystal bulbs on its tips only.
    top, tips = kelp_like("crystal_kelp", CAVERN_PLANT["crystal_kelp"], True, bulb=P("#2aa8c2", "#8ae8f8", "#e0fcff"))
    top_glow = glow_mask(top, tips, GLOW["cyan"])
    body = kelp_like("crystal_kelp", CAVERN_PLANT["crystal_kelp"], False)[0]
    top.save(tex("crystal_kelp_top"))
    top_glow.save(tex("crystal_kelp_top_glow"))
    body.save(tex("crystal_kelp"))
    write(bs("crystal_kelp"), {"variants": {"top=true": {"model": ref("crystal_kelp_top")}, "top=false": {"model": ref("crystal_kelp")}}})
    write(bm("crystal_kelp_top"), cross_model(ref("crystal_kelp_top"), ref("crystal_kelp_top_glow")))
    write(bm("crystal_kelp"), cross_model(ref("crystal_kelp")))
    write(im("crystal_kelp"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("crystal_kelp_top")}})

    # Ancient deep-sea plant: a bark column with ringed ends, gnarled roots, leafy fronds.
    ancient_bark("ancient_stem", CAVERN_PLANT["ancient_bark"]).save(tex("ancient_stem"))
    ancient_rings("ancient_stem_top", CAVERN_PLANT["ancient_bark"], CAVERN_PLANT["ancient_wood"]).save(tex("ancient_stem_top"))
    # The stem is a log (it has an axis): building_assets writes its blockstate and models with the rest of its wood set.
    gnarled_root("ancient_root", CAVE_PLANT["root"]).save(tex("ancient_root"))
    write(bs("ancient_root"), {"variants": {"": {"model": ref("ancient_root")}}})
    write(bm("ancient_root"), {"parent": "minecraft:block/cube_all", "textures": {"all": ref("ancient_root")}})
    write(im("ancient_root"), {"parent": ref("ancient_root")})
    frond_leaves("ancient_frond", CAVERN_PLANT["frond"]).save(tex("ancient_frond"))
    write(bs("ancient_frond"), {"variants": {"": {"model": ref("ancient_frond")}}})
    write(bm("ancient_frond"), {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout", "textures": {"all": ref("ancient_frond")}})
    write(im("ancient_frond"), {"parent": ref("ancient_frond")})

    # Luminous moss: laid out like cave moss, with full-bright pinpoints scattered over cavern roofs like stars.
    film, glow = luminous_film("luminous_moss")
    film.save(tex("luminous_moss"))
    glow.save(tex("luminous_moss_glow"))
    write(bm("luminous_moss"), glow_film_model(ref("luminous_moss"), ref("luminous_moss_glow")))
    face_rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    write(bs("luminous_moss"), {"multipart": [{"when": {face: "true"}, "apply": {"model": ref("luminous_moss"), **r, "uvlock": True}}
                                              for face, r in face_rot.items()]})
    write(im("luminous_moss"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("luminous_moss")}})

    # Brine surface: one translucent, double-sided shimmering plane near the top of its water block.
    save_animated(brine_frames("brine_surface"), tex("brine_surface"), frametime=5)
    face = {"uv": [0, 0, 16, 16], "texture": "#surface"}
    write(bs("brine_surface"), {"variants": {"": {"model": ref("brine_surface")}}})
    write(bm("brine_surface"), {"render_type": "minecraft:translucent", "ambientocclusion": False,
                                "textures": {"particle": ref("brine_surface"), "surface": ref("brine_surface")},
                                "elements": [{"from": [0, 14.5, 0], "to": [16, 14.5, 16], "shade": False,
                                              "faces": {"up": face, "down": face}}]})
    write(im("brine_surface"), {"parent": "minecraft:item/generated", "textures": {"layer0": ref("brine_surface")}})
