"""Generates Abyssia worldgen data: terrain density functions, noises, biomes, features, surface rules and
biome sources, for both the ocean world and the deep ocean dimension.

Run from anywhere:  python tools/gen_worldgen.py
The few vanilla files it builds on (warm ocean features) are read straight from the Minecraft jar in the Gradle cache.
Biome, feature, density function and noise folders are regenerated from scratch.
"""
import copy
import json
import os
import shutil
import zipfile

import plant_defs
import seabed_structures

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data")
WG = os.path.join(ROOT, "abyssia", "worldgen")
VANILLA_JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1", "client-extra.jar")


def write(rel, obj):
    path = os.path.join(WG, rel + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def read(rel):
    with open(os.path.join(WG, rel + ".json"), encoding="utf-8") as f:
        return json.load(f)


def vanilla(rel):
    with zipfile.ZipFile(VANILLA_JAR) as jar:
        return json.loads(jar.read("data/minecraft/worldgen/" + rel + ".json"))


A = lambda n: "abyssia:" + n

# ================================================================ density functions

def add(a, b): return {"type": "minecraft:add", "argument1": a, "argument2": b}
def mul(a, b): return {"type": "minecraft:mul", "argument1": a, "argument2": b}
def dmax(a, b): return {"type": "minecraft:max", "argument1": a, "argument2": b}
def dmin(a, b): return {"type": "minecraft:min", "argument1": a, "argument2": b}
def sq(a): return {"type": "minecraft:square", "argument": a}
def dabs(a): return {"type": "minecraft:abs", "argument": a}
def clamp(a, lo, hi):
    # The clamp codec only takes an inline function: a reference is wrapped in a no-op add.
    return {"type": "minecraft:clamp", "input": add(a, 0.0) if isinstance(a, str) else a, "min": lo, "max": hi}
def flat(a): return {"type": "minecraft:flat_cache", "argument": a}
def interp(a): return {"type": "minecraft:interpolated", "argument": a}
def grad(y0, y1, v0, v1): return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": v0, "to_value": v1}
def noise3(n, xz, y): return {"type": "minecraft:noise", "noise": n, "xz_scale": xz, "y_scale": y}
def snoise(n, xz):
    # Vanilla's shift noise jitters the sample point by up to ~4 noise units, i.e. 4 / xz blocks. Below vanilla's
    # 0.25 it is scaled down so stretched noises keep the same ~16 block jitter instead of shredding their edges.
    shift = lambda s: s if xz >= 0.25 else mul(xz / 0.25, s)
    return {"type": "minecraft:shifted_noise", "noise": n, "xz_scale": xz, "y_scale": 0,
            "shift_x": shift("minecraft:shift_x"), "shift_y": 0, "shift_z": shift("minecraft:shift_z")}
def ridge(n, xz, width, k):
    """1 on the noise's zero line, falling to 0 at |n| = width: linear ridges and trenches."""
    return band(snoise(n, xz), width, k)
def band(v, width, k):
    """sq(k * (width - |v|)) inside the band |v| < width, 0 outside."""
    return sq(dmax(0, mul(k, add(width, mul(-1, dabs(v))))))
def ramp(v, lo, hi):
    """0 at v <= lo, 1 at v >= hi, smoothstep in between (v is evaluated twice: pass a cached reference)."""
    t = clamp(mul(1.0 / (hi - lo), add(v, -lo) if lo else v), 0.0, 1.0)
    return mul(sq(t), add(3.0, mul(-2.0, t)))


OCEAN_GRAD = grad(-64, 320, 1.0, -5.0)
DEEP_GRAD = grad(-128, 256, 2.0, -4.0)  # seabed Y = 64 * offset in both dimensions

# ---------------------------------------------------------------- world scale
# Biomes follow large, smooth fields only, so each forms a wide region with a smooth border: the seabed's macro
# layout (basins, shelves, trench systems), the ocean world's climate belts and the deep ocean's habitat / volcanic
# provinces. Local landforms (hills, ridges, canyons, cones, bumps) are added to the terrain on top of the macro layout
# and never switch the biome.
MACRO_SCALE = 2.5    # ocean basins, shelves, mountain chains and trench lines, x the vanilla-sized original
CLIMATE_SCALE = 2.0  # ocean world temperature belts
REGION_SCALE = 9.0   # deep ocean habitat, volcanic and crystal provinces (about 1-3k blocks across)

# ---------------------------------------------------------------- deep ocean relief (Y)
# The deep ocean is one open ocean: its seabed lies mostly below Y 100 with open water above. The ocean world's macro
# seabed lays it out, so the deep's shelves lie under the ocean world's shallowest seas and its basins under the
# deepest; the ocean world's trench lines carry on down as the deep's trench systems.
DEEP = dict(
    plain=36, plain_slope=50,      # abyssal plains: Y 36 + 50 * macro seabed (about Y 15..50)
    shelf=(0.18, 0.4), rise=84,    # shelves: +84 blocks as the macro seabed goes 0.18 -> 0.4 (coastline near Y 100)
    shelf_slope=60,                # plateau tops keep rising gently (Y 130..160)
    bank=(0.3, 0.55), bank_rise=56,  # banks: flat-topped shallows on the shelves, up to about Y 190..215
    basin=(-0.24, -0.46), drop=56, # abyssal basins: -56 blocks as it goes -0.24 -> -0.46
    # Trench systems along the trench lines (|e| small) inside trench provinces: a broad outer trough whose floor is
    # the trench zone, an inner trough down to the hadal floor, and the narrow trench cut along the axis.
    outer=(0.14, 0.07), outer_depth=50,
    inner=(0.06, 0.02), inner_depth=62,
    trench_province=(0.0, 0.2),    # trench_region noise: no system below 0.0, full system above 0.2 (~40% of lines)
    cleft=64,
    open_y=100,                    # open water above this, apart from the shelves, their banks and a few volcanoes
    arrival_y=190,                 # under the ocean world's deep seas and rifts the seabed stays below this
    top_y=230,                     # nothing rises above this (players return to the ocean world at Y 240)
)

# ---------------------------------------------------------------- abyssal rifts (ocean world)
# The only way down: bedrock covers the ocean world's floor (Y -64..-60) and players move to the deep ocean below
# Y -61. A rift is a water-filled shaft from the seabed through the bedrock band. abyssia:rift (RiftDensityFunction)
# places at most one per cell where the seabed at its axis is deep enough and returns 1 on the axis, 0 at its radius,
# -1 from 2 radii out. The terrain opens where it is > 0 and the biome source puts abyssia:abyssal_rift where it is
# > -0.2, so the biome (no bedrock, no features) always covers the shaft and its walls.
RIFT = dict(
    cell=384, chance=0.6,          # at most one rift per 384 x 384 cell, 60% of cells (deep seas only): ~700 blocks apart
    radius=(14, 24),
    max_seabed=0.0,                # seabed_offset at the axis: seabed at or below Y 0
    carve=16.0,                    # density at the axis; keeps the floor at Y -64 thin inside one 8-block cell
    funnel=16,                     # blocks the seabed sinks at the shaft's edge, fading out 1.5 radii from the axis
    biome=-0.2,                    # rift value above which the biome is abyssal_rift
)


def terrain():
    # Separate noises so each landform has its own scale and seed.
    write("noise/region_volcanic", {"firstOctave": -8, "amplitudes": [1.0, 0.6, 0.3]})
    write("noise/region_habitat", {"firstOctave": -8, "amplitudes": [1.0, 0.6, 0.3]})
    write("noise/region_temperature", {"firstOctave": -8, "amplitudes": [1.0, 0.6, 0.3]})
    write("noise/region_erosion", {"firstOctave": -8, "amplitudes": [1.0, 0.6, 0.3]})
    write("noise/volcano", {"firstOctave": -8, "amplitudes": [1.0, 1.0, 0.5]})
    write("noise/trench_region", {"firstOctave": -8, "amplitudes": [1.0, 0.5]})
    write("noise/bank", {"firstOctave": -8, "amplitudes": [1.0, 0.5]})
    write("noise/hills", {"firstOctave": -6, "amplitudes": [1.0, 0.5, 0.25]})
    write("noise/deep_ridge", {"firstOctave": -7, "amplitudes": [1.0, 0.5, 0.25]})
    write("noise/canyon", {"firstOctave": -7, "amplitudes": [1.0, 0.4]})
    write("noise/biome_fuzz", {"firstOctave": -7, "amplitudes": [1.0, 0.5]})
    write("noise/cavern", {"firstOctave": -7, "amplitudes": [1.0, 0.5, 0.5]})
    write("noise/rift", {"firstOctave": -4, "amplitudes": [1.0]})  # only seeds rift placement per world

    # ---- ocean world seabed: macro layout + local relief
    write("density_function/base", mul(0.7, snoise("minecraft:continentalness", 0.25 / MACRO_SCALE)))
    write("density_function/mountains", mul(1.1, ridge("minecraft:ridge", 0.25 / MACRO_SCALE, 0.25, 4.0)))
    write("density_function/trench_field", flat(snoise("minecraft:erosion", 0.5 / MACRO_SCALE)))
    write("density_function/trenches", mul(-1.5, band(A("trench_field"), 0.1, 10.0)))
    write("density_function/canyons", mul(-0.3, ridge("minecraft:ridge", 1.0, 0.08, 12.5)))
    write("density_function/detail", mul(0.08, snoise("minecraft:surface", 1.0)))
    write("density_function/seabed_raw", flat(add(add(add(add(A("base"), A("mountains")), A("trenches")), A("canyons")), A("detail"))))
    write("density_function/seabed_offset", flat(clamp(A("seabed_raw"), -0.95, 1.9)))
    # What the ocean world's biomes follow: basins and shallows, without ridges, trenches and canyons.
    write("density_function/seabed_macro", flat(A("base")))
    r = RIFT
    write("density_function/rift", flat({"type": A("rift"), "noise": A("rift"), "seabed": A("seabed_offset"), "cell_size": r["cell"],
                                         "chance": r["chance"], "min_radius": r["radius"][0], "max_radius": r["radius"][1],
                                         "max_seabed": r["max_seabed"]}))

    # ---- deep ocean
    write("density_function/region_volcanic", flat(snoise(A("region_volcanic"), 1.0 / REGION_SCALE)))
    write("density_function/region_habitat", flat(snoise(A("region_habitat"), 1.0 / REGION_SCALE)))
    # Water-mass and relic provinces (biome choice only, no terrain): cold / warm-brine water, ruin and bone fields.
    write("density_function/region_temperature", flat(snoise(A("region_temperature"), 1.0 / REGION_SCALE)))
    write("density_function/region_erosion", flat(snoise(A("region_erosion"), 1.0 / REGION_SCALE)))
    y = lambda blocks: blocks / 64.0
    d = DEEP
    write("density_function/deep_shelf", flat(ramp(A("seabed_macro"), *d["shelf"])))
    write("density_function/bank_field", flat(snoise(A("bank"), 1.0)))
    write("density_function/deep_bank", flat(mul(A("deep_shelf"), ramp(A("bank_field"), *d["bank"]))))
    write("density_function/deep_basin", flat(ramp(mul(-1, A("seabed_macro")), -d["basin"][0], -d["basin"][1])))
    write("density_function/trench_region", flat(snoise(A("trench_region"), 1.0 / REGION_SCALE)))
    closeness = mul(-1, dabs(A("trench_field")))  # 0 on a trench line, more negative away from it
    trough = add(mul(y(d["outer_depth"]), ramp(closeness, -d["outer"][0], -d["outer"][1])),
                 mul(y(d["inner_depth"]), ramp(closeness, -d["inner"][0], -d["inner"][1])))
    # Depth of the trench system in blocks/64: full in trench provinces, halved under the shelves.
    write("density_function/deep_trough", flat(mul(mul(trough, ramp(A("trench_region"), *d["trench_province"])),
                                                   add(1.0, mul(-0.5, A("deep_shelf"))))))
    # The macro seabed alone, which the depth zones (biomes) follow.
    macro = add(add(y(d["plain"]), mul(y(d["plain_slope"]), A("seabed_macro"))),
                add(add(add(mul(y(d["rise"]), A("deep_shelf")), mul(y(d["shelf_slope"]), dmax(0, add(A("seabed_macro"), -d["shelf"][1])))),
                        mul(y(d["bank_rise"]), A("deep_bank"))),
                    add(mul(-y(d["drop"]), A("deep_basin")), mul(-1, A("deep_trough")))))
    write("density_function/deep_macro_offset", flat(macro))

    write("density_function/volcano_cones", flat(snoise(A("volcano"), 1.0)))
    # Volcanic provinces: several cones wherever region_volcanic is high (the volcanic_deep biome), none outside.
    province = clamp(mul(5.0, add(A("region_volcanic"), -0.2)), 0.0, 1.0)
    volcano = mul(province, add(mul(2.0, sq(dmax(0, mul(2.2, add(A("volcano_cones"), -0.35))))),
                                mul(-10.0, dmax(0, add(A("volcano_cones"), -0.65)))))
    # Ridge chains rise up to 100 blocks where there is room but top out around Y 90 (24 blocks on the shelves),
    # so the water above Y 100 stays open instead of being crossed by a mesh of mountain crests.
    ridge_height = clamp(mul(0.9, add(y(d["open_y"]), mul(-1, A("deep_macro_offset")))), y(24), 1.6)
    landforms = add(add(mul(0.3, snoise(A("hills"), 1.0)),                         # abyssal hills, +-19 blocks
                        mul(0.06, snoise("minecraft:surface", 4.0))),                # small bumps: no perfectly flat plains
                    add(add(mul(ridge_height, ridge(A("deep_ridge"), 0.5, 0.2, 5.0)),  # deep mountain chains
                            volcano),                                                # cones with summit craters
                        add(mul(-0.9, ridge(A("canyon"), 0.35, 0.06, 16.0)),          # abyssal canyons: narrow, ~58 blocks deep
                            mul(y(d["cleft"]) / 1.5, A("trenches")))))                # the trench's axial cleft
    # Divers coming down a rift arrive at Y 200: keep that water open under the ocean world's deep seas. The limit
    # only lifts once the ocean floor is ~10 blocks above Y 0, and never within 1.5 radii of a rift's axis.
    near_rift = mul(-4.0, dmax(0, add(A("rift"), 0.5)))
    arrival = add(y(d["arrival_y"]), mul(8.0, dmax(0, add(add(A("seabed_offset"), near_rift), -0.15))))
    write("density_function/deep_seabed_offset", flat(clamp(dmin(add(A("deep_macro_offset"), landforms), arrival), -1.9, y(d["top_y"]))))

    def caves(cheese, cheese_threshold, min_y):
        cheese_d = mul(2.0, add(cheese_threshold, mul(-1, cheese)))
        tunnels = mul(10.0, add(dmax(dabs(noise3("minecraft:spaghetti_3d_1", 1.0, 1.0)),
                                     dabs(noise3("minecraft:spaghetti_3d_2", 1.0, 1.0))), -0.07))
        # Stay solid near the world bottom so caves never expose the void.
        return dmax(dmin(cheese_d, tunnels), grad(min_y, min_y + 12, 1.0, -1.0))

    write("density_function/ocean_caves", caves(noise3("minecraft:cave_cheese", 1.0, 0.6667), 0.55, -64))
    write("density_function/deep_caves", caves(noise3(A("cavern"), 1.0, 0.5), 0.5, -128))

    # Density is sampled at 4x8 cell corners and interpolated (smooth per block, cheap).
    # Rifts: min() with -carve * rift opens the shaft (rift > 0) at every height; at the bottom cell corner (Y -64) the
    # carve is 1, so a one-block floor keeps the void closed. Subtracting the funnel sinks the seabed (and widens
    # caves) around the edge. Both are exact no-ops where rift <= -0.5.
    r = RIFT
    carve = dmax(mul(-r["carve"], A("rift")), grad(-64, -56, 1.0, -r["carve"]))
    funnel = mul(grad(-64, -56, 0.0, 1.0), mul(y(r["funnel"]), sq(clamp(mul(2.0, add(A("rift"), 0.5)), 0.0, 1.0))))
    write("density_function/final_density", interp(dmin(add(dmin(add(OCEAN_GRAD, A("seabed_offset")), A("ocean_caves")), mul(-1, funnel)),
                                                        carve)))
    write("density_function/deep_final_density", interp(dmin(add(DEEP_GRAD, A("deep_seabed_offset")), A("deep_caves"))))


# ================================================================ surface rules (deep ocean: custom blocks only)

def block(name, **props):
    s = {"Name": name if ":" in name else A(name)}
    if props:
        s["Properties"] = props
    return {"type": "minecraft:block", "result_state": s}
def cond(c, then): return {"type": "minecraft:condition", "if_true": c, "then_run": then}
def seq(*rules): return {"type": "minecraft:sequence", "sequence": [r for r in rules if r]}
def biome_is(*names): return {"type": "minecraft:biome", "biome_is": [A(n) for n in names]}
def noise_above(t, noise="minecraft:surface"): return {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": t, "max_threshold": 10.0}
def noise_band(lo, hi, noise): return {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi}
def depth(surface, offset=0, add_surface_depth=True):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": surface, "add_surface_depth": add_surface_depth, "secondary_depth_range": 0}
STEEP = {"type": "minecraft:steep"}
BEDROCK = cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, block("minecraft:bedrock"))

# Geology per biome: surface sediments by noise (first match wins), the layer beneath, the rock crust,
# an optional mineral crust patch (block, noise threshold) and cave ceilings.
GEOLOGY = {
    "volcanic_deep": dict(surface=[(0.55, "molten_volcanic_rock"), (0.0, "volcanic_ash"), (-0.6, "volcanic_rock"), (None, "volcanic_glass")],
                          sub="volcanic_rock", rock="volcanic_rock",
                          ceiling=[(0.5, "molten_volcanic_rock"), (None, "volcanic_rock")]),
    "thermal_vents": dict(surface=[(0.5, "sulfur_deposit"), (None, "mineral_sediment")], sub="mineral_sediment", rock="thermal_rock",
                          crust=("copper_crust", 0.72)),
    "deep_crystal_fields": dict(surface=[(0.3, "crystal_rock"), (None, "crystal_sediment")], sub="crystal_sediment", rock="crystal_rock",
                                ceiling=[(0.4, "deep_crystal_block"), (None, "crystal_rock")]),
    "abyssal_trench": dict(surface=[(0.1, "deep_mud"), (None, "mineral_sediment")], sub="mineral_sediment", rock="trench_rock",
                           crust=("cobalt_crust", 0.66)),
    "hadal_zone": dict(surface=[(0.0, "abyssal_mud"), (None, "deep_mud")], sub="deep_sediment", rock="trench_rock",
                       crust=("nickel_crust", 0.68), ceiling=[(0.45, "deep_crystal_block"), (None, "trench_rock")]),
    "abyssal_forest": dict(surface=[(0.0, "organic_sediment"), (None, "abyssal_mud")], sub="abyssal_mud", rock="deep_sea_rock"),
    "deep_forest": dict(surface=[(-0.2, "organic_sediment"), (None, "deep_sediment")], sub="abyssal_mud", rock="deep_sea_rock"),
    "abyssal_ocean": dict(surface=[(0.35, "abyssal_mud"), (None, "deep_sediment")], sub="deep_sediment", rock="abyssal_rock",
                          crust=("manganese_crust", 0.62)),
    "deep_sea": dict(surface=[(0.3, "abyssal_mud"), (None, "deep_sediment")], sub="mineral_sediment", rock="deep_sea_rock"),
    # Water-mass / relic provinces on the abyssal plains (see biome_sources)
    "sunken_ruins": dict(surface=[(0.55, "ancient_masonry"), (None, "ruin_gravel")], sub="ruin_sediment", rock="ancient_masonry"),
    "bone_graveyard": dict(surface=[(0.5, "fossil_rock"), (None, "bone_sediment")], sub="fossil_silt", rock="fossil_rock"),
    "brine_lakes": dict(surface=[(0.4, "brine_silt"), (None, "salt_crust")], sub="brine_silt", rock="salt_rock"),
    "glow_gardens": dict(surface=[(0.4, "glow_silt"), (None, "lumen_sand")], sub="glow_silt", rock="lumen_rock"),
    "frost_abyss": dict(surface=[(0.4, "icy_sediment"), (None, "frost_silt")], sub="icy_sediment", rock="frozen_rock"),
}
DEFAULT_BIOME = "deep_sea"


def choose(options):
    return seq(*[cond(noise_above(t), block(b)) if t is not None else block(b) for t, b in options])


def per_biome(make):
    """A biome-dispatched rule; the default biome's rule doubles as the fallback."""
    rules = [cond(biome_is(b), make(g)) for b, g in GEOLOGY.items() if b != DEFAULT_BIOME and make(g)]
    return seq(*rules, make(GEOLOGY[DEFAULT_BIOME]))


def deep_surface_rule():
    floor = per_biome(lambda g: seq(
        cond(STEEP, block(g["rock"])),                                                       # cliffs and spire flanks stay bare rock
        cond(noise_band(-0.035, 0.035, "minecraft:surface_secondary"), block(g["rock"])),    # hairline cracks through the sediment
        cond(noise_above(g["crust"][1]), block(g["crust"][0])) if "crust" in g else None,     # mineral crust patches
        choose(g["surface"])))
    sub = per_biome(lambda g: block(g["sub"]))
    ceiling = per_biome(lambda g: choose(g["ceiling"]) if "ceiling" in g else block(g["rock"]))
    rock = per_biome(lambda g: block(g["rock"]))
    # Layers: surface sediment, the layer beneath, then the biome's rock crust. Buried stone skips every
    # biome test (surface rules run once per stone block), keeping the default deep sea rock.
    return seq(BEDROCK,
               cond(depth("floor", 12), seq(cond(depth("floor"), floor), cond(depth("floor", 4), sub),
                                            cond(depth("ceiling", 0, False), ceiling), rock)),
               cond({"type": "minecraft:vertical_gradient", "random_name": A("abyssal_layer"),
                     "true_at_and_below": {"absolute": 100}, "false_at_and_above": {"absolute": 110}}, block("abyssal_rock")))


# ================================================================ feature building blocks

IN_WATER = {"type": "minecraft:matching_blocks", "blocks": ["minecraft:water"]}


def state(name, **props):
    s = {"Name": name if ":" in name else A(name)}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


def placed(feature, placement=()):
    return {"feature": feature, "placement": list(placement)}


def single(name, **props):
    """One block, only into water and only where it can survive."""
    st = state(name, **props)
    return placed({"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": st}}},
                  [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                      IN_WATER, {"type": "minecraft:would_survive", "state": st}]}}])


def column(name, lo, hi, scale=0.05):
    """A stacking plant column; heights follow noise so neighbours form groves."""
    return placed({"type": A("column_plant"), "config": {"head": state(name, top=True), "body": state(name, top=False),
                                                         "min_height": lo, "max_height": hi, "cluster_scale": scale}})


def kelp_column(head, body, lo, hi, scale):
    return placed({"type": A("column_plant"), "config": {"head": {"Name": head}, "body": {"Name": body}, "min_height": lo, "max_height": hi, "cluster_scale": scale}})


def selector(*features):
    """Mixed species: each attempt picks one of the given features."""
    return placed({"type": "minecraft:simple_random_selector", "config": {"features": list(features)}})


def patch(inner, tries=48, spread=6, y_spread=1):
    return {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": spread, "y_spread": y_spread, "feature": inner}}


def cfg(option): return {"type": A("config"), "option": option}
def count(n): return {"type": "minecraft:count", "count": n}
def rarity(n): return {"type": "minecraft:rarity_filter", "chance": n}
def clustered(ratio, offset=0.15):
    """Dense and sparse zones instead of uniform spread: count follows a large-scale noise."""
    return {"type": "minecraft:noise_based_count", "noise_to_count_ratio": ratio, "noise_factor": 60.0, "noise_offset": offset}
def on_floor(*extra):
    return [*extra, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}, {"type": "minecraft:biome"}]
def in_caves(st, n):
    return [count(n), {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -120}, "max_inclusive": {"absolute": 220}}},
            {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 12, "allowed_search_condition": IN_WATER,
             "target_condition": {"type": "minecraft:all_of", "predicates": [IN_WATER, {"type": "minecraft:would_survive", "state": st}]}},
            {"type": "minecraft:biome"}]


def x(n, feature):
    return [feature] * n


CLUSTER = lambda n: state(n, facing="up", waterlogged=True)

# ================================================================ features

CONFIGURED = {}
PLACED = {}


def feature(name, configured, placement):
    CONFIGURED[name] = configured
    PLACED[name] = (name, placement)


MINERALS = {
    # mineral: (ore, crust, cluster, host)
    "iron": ("abyssal_iron_ore", "iron_crust", None, "mineral_host_rock"),
    "copper": ("deep_copper_ore", "copper_crust", None, "mineral_host_rock"),
    "manganese": ("manganese_ore", "manganese_crust", "manganese_nodules", "mineral_host_rock"),
    "cobalt": ("cobalt_ore", "cobalt_crust", "cobalt_cluster", "mineral_host_rock"),
    "nickel": ("deep_nickel_ore", "nickel_crust", "nickel_cluster", "mineral_host_rock"),
    "sulfur": ("sulfur_ore", "sulfur_deposit", "sulfur_cluster", "mineral_host_rock"),
    "thermal_crystal": ("thermal_crystal_ore", "mineral_sediment", "thermal_crystal_cluster", "thermal_rock"),
    "abyssal_crystal": ("abyssal_crystal_ore", "crystal_sediment", "abyssal_crystal_cluster", "crystal_rock"),
}
VEIN_PLACEMENT = {"small": [count(2)], "medium": [rarity(2)], "large": [rarity(5)], "huge": [rarity(14)]}
VEINS = {
    # biome: [(mineral, size), ...]  -- each biome favours its own minerals and vein sizes
    "deep_sea": [("iron", "small"), ("manganese", "small"), ("copper", "small")],
    "abyssal_ocean": [("manganese", "small"), ("manganese", "medium"), ("iron", "medium"), ("nickel", "small")],
    "abyssal_forest": [("iron", "small"), ("copper", "small")],
    "deep_forest": [("iron", "small"), ("manganese", "small")],
    "abyssal_trench": [("cobalt", "medium"), ("cobalt", "large"), ("manganese", "medium"), ("nickel", "medium")],
    "hadal_zone": [("cobalt", "huge"), ("nickel", "large"), ("nickel", "huge"), ("manganese", "large"), ("abyssal_crystal", "medium")],
    "volcanic_deep": [("sulfur", "large"), ("sulfur", "huge"), ("cobalt", "large"), ("nickel", "large")],
    "thermal_vents": [("sulfur", "large"), ("copper", "large"), ("thermal_crystal", "large")],
    "deep_crystal_fields": [("abyssal_crystal", "medium"), ("abyssal_crystal", "large"), ("thermal_crystal", "medium")],
    "sunken_ruins": [("iron", "small"), ("copper", "medium")],
    "bone_graveyard": [("manganese", "small"), ("nickel", "medium")],
    "brine_lakes": [("sulfur", "medium"), ("thermal_crystal", "small")],
    "glow_gardens": [("iron", "small"), ("copper", "small")],
    "frost_abyss": [("cobalt", "medium"), ("abyssal_crystal", "small")],
}


# Rare metals (M01, tools/gen_deep_assets.py RARE_METALS): one tiny, very rare vein per metal ("vein_<metal>_rare",
# size small, no crust / nodules), only in the deep biomes that match the real deposit.
RARE_VEINS = {
    # metal: (ore, host, rarity 1/n chunks, biomes)
    "platinum": ("platinum_ore", "trench_rock", 48, ["abyssal_trench", "hadal_zone"]),
    "tellurium": ("tellurium_ore", "trench_rock", 40, ["abyssal_trench", "hadal_zone"]),
    "molybdenum": ("molybdenum_ore", "abyssal_rock", 40, ["abyssal_ocean", "abyssal_trench"]),
    "vanadium": ("vanadium_ore", "abyssal_rock", 40, ["abyssal_ocean", "abyssal_trench"]),
    "tungsten": ("tungsten_ore", "trench_rock", 48, ["hadal_zone"]),
    "yttrium": ("yttrium_ore", "abyssal_rock", 56, ["abyssal_ocean", "hadal_zone"]),
}
for _metal, (_ore, _host, _n, _biomes) in RARE_VEINS.items():
    for _b in _biomes:
        VEINS[_b].append((_metal, "rare"))


def veins():
    for mineral, (ore, crust, cluster, host) in MINERALS.items():
        for size in ("small", "medium", "large", "huge"):
            cfg_ = {"ore": state(ore), "host": state(host), "size": size}
            if crust:
                cfg_["crust"] = state(crust)
            if cluster:
                cfg_["cluster"] = CLUSTER(cluster)
            feature(f"vein_{mineral}_{size}", {"type": A("ore_vein"), "config": cfg_}, on_floor(*VEIN_PLACEMENT[size]))
    for metal, (ore, host, n, _) in RARE_VEINS.items():
        feature(f"vein_{metal}_rare", {"type": A("ore_vein"), "config": {"ore": state(ore), "host": state(host), "size": "small"}},
                on_floor(rarity(n)))


def spires():
    def spire(rock, accent, chance, lo, hi, rmin, rmax):
        return {"type": A("rock_spire"), "config": {"rock": state(rock), "accent": state(accent), "accent_chance": chance,
                                                    "min_height": lo, "max_height": hi, "min_radius": rmin, "max_radius": rmax}}
    feature("rock_spire", spire("deep_sea_rock", "mineral_host_rock", 0.15, 10, 30, 2, 4), on_floor(rarity(20)))
    feature("abyssal_spire", spire("abyssal_rock", "manganese_ore", 0.08, 12, 40, 2.5, 4.5), on_floor(rarity(16)))
    feature("trench_spire", spire("trench_rock", "cobalt_ore", 0.08, 30, 50, 3.5, 6), on_floor(rarity(10)))
    feature("thermal_spire", spire("thermal_rock", "molten_volcanic_rock", 0.2, 10, 35, 2, 4), on_floor(rarity(12)))
    feature("crystal_spire", spire("crystal_rock", "deep_crystal_block", 0.3, 12, 40, 2, 4), on_floor(cfg("crystals"), rarity(8)))


def vegetation():
    V = cfg("vegetation")
    # --- low layer: mixed-species meadows, one palette per habitat ---
    meadows = {
        "green": [*x(3, column("abyssal_grass", 1, 3)), column("teal_abyssal_grass", 1, 2), *x(2, single("sea_fern")), *x(2, single("abyssal_moss"))],
        "organic": [*x(3, column("abyssal_grass", 1, 3)), *x(2, column("teal_abyssal_grass", 1, 3)), *x(2, single("sea_fern")),
                    *x(2, single("abyssal_moss")), single("sponge_plant")],
        "ashen": [*x(3, column("ashen_abyssal_grass", 1, 3)), column("teal_abyssal_grass", 1, 2), single("sea_fern")],
        "ancient": [*x(2, column("teal_abyssal_grass", 1, 3)), *x(2, column("violet_abyssal_grass", 1, 3)), single("sea_fern"), single("abyssal_moss")],
        "crystal": [*x(2, column("teal_abyssal_grass", 1, 2)), *x(2, column("violet_abyssal_grass", 1, 2)), single("crystal_plant")],
        "thermal": [*x(3, single("vent_grass")), *x(2, single("heat_moss")), column("ashen_abyssal_grass", 1, 2)],
    }
    for name, species in meadows.items():
        CONFIGURED["meadow_" + name] = selector(*species)["feature"]
    # Meadow plants are placed one by one on the exact seabed surface (a chunk has 256 columns); a large-scale
    # noise sets how many, giving dense stretches, sparse ones and bare gaps. Coverage roughly matches the
    # spec's densities: plain ~20%, normal ~30%, kelp abyss ~80%, deep forest ~100%.
    tiers = {"sparse": [clustered(200, 0.15)], "normal": [clustered(260, 0.3)], "dense": [clustered(600, 0.6)], "full": [clustered(800, 0.8)]}
    for name, tier in (("green", "normal"), ("green", "sparse"), ("organic", "dense"), ("organic", "full"), ("ashen", "normal"),
                       ("ancient", "normal"), ("crystal", "normal"), ("thermal", "sparse"), ("thermal", "normal")):
        PLACED[f"meadow_{name}_{tier}"] = ("meadow_" + name, on_floor(V, *tiers[tier]))

    # --- medium layer ---
    feature("tube_plants", patch(column("tube_plant", 2, 8, 0.08), 20, 4), on_floor(V, clustered(2)))
    PLACED["tube_plants_dense"] = ("tube_plants", on_floor(V, count(3), clustered(3)))
    feature("sponge_plants", patch(single("sponge_plant"), 12, 4), on_floor(V, clustered(1)))
    feature("thermal_tubes", patch(column("thermal_tube", 1, 4, 0.1), 16, 4), on_floor(V, clustered(2)))
    feature("mineral_vines", patch(column("mineral_vine", 1, 3, 0.1), 12, 4), on_floor(V, clustered(1)))
    feature("crystal_plants", patch(single("crystal_plant"), 24, 5), on_floor(V, cfg("crystals"), clustered(2)))

    # --- high layer ---
    feature("deep_kelp", patch(column("deep_kelp", 5, 30, 0.03), 24, 6, 1), on_floor(V, clustered(2)))
    PLACED["deep_kelp_dense"] = ("deep_kelp", on_floor(V, count(2), clustered(3)))
    feature("large_deep_kelp", patch(column("deep_kelp", 30, 60, 0.02), 16, 6, 1), on_floor(cfg("giant_plants"), rarity(3)))

    # --- canopy layer: giant plants, as forests in their biomes and as rare giants elsewhere ---
    feature("giant_kelp_forest", patch(column("giant_kelp", 20, 80, 0.015), 64, 7, 1), on_floor(cfg("abyssal_forests"), count(2)))
    feature("giant_kelp_scattered", patch(column("giant_kelp", 20, 60, 0.02), 12, 6, 1), on_floor(cfg("giant_plants"), rarity(4)))
    feature("giant_tube_forest", patch(column("giant_tube", 5, 20, 0.04), 40, 7, 1), on_floor(cfg("abyssal_forests"), count(2)))
    feature("giant_tube_scattered", patch(column("giant_tube", 5, 14, 0.05), 8, 5, 1), on_floor(cfg("giant_plants"), rarity(3)))
    feature("void_kelp_forest", patch(kelp_column(A("void_kelp"), A("void_kelp_plant"), 20, 70, 0.02), 48, 7, 1), on_floor(cfg("abyssal_forests"), count(1)))
    feature("void_kelp", patch(kelp_column(A("void_kelp"), A("void_kelp_plant"), 8, 30, 0.03), 12, 5, 1), on_floor(V, rarity(3)))

    # --- glowing plants: only a few percent of the vegetation ---
    glows = {
        "glow_plants_shallow": [*x(3, single("glowtip_grass")), single("glow_coral"), single("glow_anemone")],
        "glow_plants_deep": [*x(2, single("glowtip_grass")), single("abyssal_bloom"), single("abyssal_mushroom"), single("soul_coral")],
        "glow_plants_ancient": [single("glowtip_grass"), *x(2, single("hadal_bloom")), single("abyssal_mushroom")],
        "glow_plants_crystal": [*x(2, single("glowtip_grass")), *x(2, single("crystal_plant"))],
    }
    for name, species in glows.items():
        feature(name, patch(selector(*species), 24, 5), on_floor(cfg("glowing_plants"), rarity(2)))

    # --- other species patches ---
    for name in ("glow_coral", "abyssal_mushroom", "abyssal_bloom", "soul_coral", "black_coral", "hadal_bloom", "sea_fern", "glow_anemone"):
        feature("patch_" + name, patch(single(name)), on_floor(V, clustered(2)))
    feature("patch_pressure_crystal", patch(single("pressure_crystal_cluster", facing="up", waterlogged=True), 24, 5), on_floor(cfg("crystals"), rarity(2)))
    feature("patch_deep_crystal_cluster", patch(single("deep_crystal_cluster", facing="up", waterlogged=True), 24, 5), on_floor(cfg("crystals"), count(3)))
    # Manganese nodule fields: the abyssal plain's signature.
    feature("manganese_nodule_field", patch(single("manganese_nodules", facing="up", waterlogged=True), 48, 7), on_floor(clustered(2, 0.0)))
    # Floating plants hang 4-12 blocks above the seabed.
    feature("floating_blooms", patch(single("floating_bloom"), 8, 8, 6),
            [V, clustered(1), {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
             {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": {"type": "minecraft:uniform", "value": {"min_inclusive": 4, "max_inclusive": 12}}},
             {"type": "minecraft:biome"}])
    feature("seafloor_pebbles", patch(single("seafloor_pebbles"), 16, 6), on_floor(clustered(1)))

    # --- crystals ---
    spike = lambda lo, hi: {"type": A("crystal_spike"), "config": {"crystal": state("deep_crystal_block"), "cluster": CLUSTER("deep_crystal_cluster"),
                                                                   "min_height": lo, "max_height": hi}}
    feature("crystal_spikes", spike(5, 12), on_floor(cfg("crystals"), count(2)))
    # Crystal garden: small spires, crystal plants and clusters grown together.
    feature("crystal_garden", patch(selector(placed(spike(2, 6)), *x(3, single("crystal_plant")), single("deep_crystal_cluster", facing="up", waterlogged=True),
                                             single("glowtip_grass")), 20, 6), on_floor(cfg("crystals"), rarity(3)))

    # --- caves ---
    feature("cave_deep_crystals", single("deep_crystal_cluster", facing="up", waterlogged=True)["feature"],
            [cfg("crystals")] + in_caves(CLUSTER("deep_crystal_cluster"), 40))
    feature("cave_abyssal_mushrooms", single("abyssal_mushroom")["feature"], [V] + in_caves(state("abyssal_mushroom"), 24))

    # --- ocean world (vanilla blocks are fine there) ---
    feature("reef_kelp", patch(kelp_column("minecraft:kelp", "minecraft:kelp_plant", 6, 20, 0.03), 16, 6, 1), on_floor(V, clustered(2)))


# ---------------------------------------------------------------- resource plants (tools/plant_defs.py)

def on_floor_depth(depth, *extra):
    """on_floor() limited to a depth band in metres; the depth filter needs the seabed Y, so it follows the heightmap."""
    lo, hi = depth
    return [*extra, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
            {"type": A("depth"), "min_depth": lo, "max_depth": hi}, {"type": "minecraft:biome"}]


def resource_plants():
    """One patch per new seabed plant (its biomes + depth band, so species change with depth), and the Phase 3
    plant communities that act as landmarks. Cave plants join CAVE_ENVIRONMENTS / CAVERN_TEMPLATES directly."""
    V = cfg("vegetation")
    for p in plant_defs.NEW_PLANTS:
        if not p.place:
            continue
        pl = p.place
        inner = column(p.id, *pl["height"], 0.08) if p.form == "column" else single(p.id)
        density = ([clustered(pl["clustered"])] if "clustered" in pl else [rarity(pl["rarity"])] if "rarity" in pl
                   else [count(pl.get("count", 1))])
        feature("plant_" + p.id, patch(inner, pl["tries"], pl["spread"]), on_floor_depth(p.depth, V, *density))
    L = plant_defs.LANDMARKS
    # Abyssal Root Colony: arches of ancient root; resin roots hang from them, knotstalk and amber fans shelter beneath.
    feature("abyssal_root_colony", {"type": A("root_arch"), "config": {
        "root": state("ancient_root"), "hanging": state("resin_root"), "column": state("knotstalk"),
        "floor": [state("amber_fan"), state("amber_fan"), state("strandweed"), state("lumen_quill")], "min_arches": 3, "max_arches": 6}},
        on_floor_depth(L["abyssal_root_colony"][1], cfg("giant_plants"), rarity(28)))
    # Deep Sea Bloom Colony: a dense ring of glowing plants, visible from afar in the dark.
    feature("deep_bloom_colony", patch(selector(*x(3, single("lumen_quill")), *x(2, single("abyssal_bloom")), single("glowtip_grass")), 40, 4),
            on_floor_depth(L["deep_bloom_colony"][1], cfg("glowing_plants"), rarity(12)))
    # Thermal Tube Forest: tall thermal tubes over heat moss, the thermal fiber source.
    feature("thermal_tube_forest", patch(selector(*x(3, column("thermal_tube", 5, 12, 0.05)), single("heat_moss"), single("vent_grass")), 56, 6),
            on_floor_depth(L["thermal_tube_forest"][1], V, rarity(4)))


def add_resource_plants():
    """Adds the resource plant features to VEG_ORDER and to each biome's vegetation."""
    wanted = {}
    for p in plant_defs.NEW_PLANTS:
        for b in p.biomes:
            assert b.startswith("cave:") or b in DEEP_BIOMES, (p.id, b)
            if p.place and b in DEEP_BIOMES:
                wanted.setdefault(b, []).append("plant_" + p.id)
        for b in p.biomes:
            env = b[5:] if b.startswith("cave:") else None
            assert env is None or env in CAVE_ENVIRONMENTS or env == "cavern", (p.id, b)
    for name, (biomes, _, _) in plant_defs.LANDMARKS.items():
        for b in biomes:
            wanted.setdefault(b, []).append(name)
    new = [n for n in ["abyssal_root_colony", "thermal_tube_forest", "deep_bloom_colony"]
           + ["plant_" + p.id for p in plant_defs.NEW_PLANTS if p.place] if n not in VEG_ORDER]
    i = VEG_ORDER.index("floating_blooms")
    VEG_ORDER[i:i] = new
    for b, names in wanted.items():
        veg = DEEP_BIOMES[b][4]
        veg += [n for n in names if n not in veg]


def vent_fields():
    # One pass per chunk; the feature itself decides which vent fields overlap the chunk.
    feature("vent_fields", {"type": A("thermal_vent_field"), "config": {}}, [])
    # Seabed structures: one pass per chunk each; every deep biome lists both, unfiltered, so a structure crossing a
    # biome border is painted whole (tools/seabed_structures.py holds the data they read).
    feature("seabed_structures", {"type": A("seabed_structures"), "config": {}}, [])
    feature("seabed_structure_dressing", {"type": A("seabed_structure_dressing"), "config": {}}, [])


# ================================================================ cave network (datapack registries read by the Java cave generator)
#
# abyssia:cave_environment - how a kind of cave space looks and what lives in it (no new biomes: caves keep the biome above)
# abyssia:cave_profile     - per biome group: how often systems form, which cave types / landmarks and how rare, strata, ores

CAVE_DIR = os.path.join(ROOT, "abyssia", "abyssia")


def weighted(items):
    return [{"data": d, "weight": w} for d, w in items]


def blocks(items):
    return weighted([(state(n), w) for n, w in items])


def pl(name, lo=1, hi=1):
    e = {"state": state(name)}
    if lo != 1:
        e["min_height"] = lo
    if hi != 1:
        e["max_height"] = max(lo, hi)
    return e


def plants(items):
    return weighted([(pl(*p) if isinstance(p, tuple) else pl(p), w) for p, w in items])


def environment(wall, floor, ceiling, accents=(), accent_chance=0.0, *, flora, speleothem, speleothem_density, crystals=(),
                crystal_density=0.0, debris=()):
    fl = {k: plants(flora[k]) for k in ("floor", "wall", "ceiling", "glow", "bright", "giant") if flora.get(k)}
    fl.update({k: flora[k] for k in ("density", "glow_ratio", "bright_ratio", "giant_density", "moss") if k in flora})
    return {"geology": {"wall": blocks(wall), "floor": blocks(floor), "ceiling": blocks(ceiling), "accents": blocks(accents),
                        "accent_chance": accent_chance},
            "flora": fl,
            "formations": {"speleothem": state(speleothem), "speleothem_density": speleothem_density, "crystals": blocks(crystals),
                           "crystal_density": crystal_density, "debris": blocks(debris)}}


CAVE_ENVIRONMENTS = {
    # Default deep cave: dark wet rock, sediment floors, modest plant life.
    "abyssal": environment(
        [("abyssal_cave_rock", 6), ("dark_cave_rock", 3), ("wet_cave_rock", 2)],
        [("cave_sediment", 5), ("cave_mud", 3), ("deep_sediment", 1)],
        [("wet_cave_rock", 3), ("dark_cave_rock", 2)],
        [("cave_mineral_crust", 3), ("layered_cave_rock", 2)], 0.08,
        flora=dict(floor=[(("cave_grass", 1, 3), 5), ("cave_fern", 3), (("cave_tube_plant", 2, 5), 2), ("cave_sponge", 1), ("cave_coral", 1),
                          ("seafloor_pebbles", 1)],
                   wall=[("wall_fern", 3), ("wall_mineral_vine", 1)],
                   ceiling=[(("cave_root", 1, 4), 3), (("cave_vine", 2, 6), 2), (("hanging_kelp", 2, 7), 1), (("resin_root", 1, 4), 1)],
                   glow=[("cave_crystal_plant", 2), ("glowtip_grass", 1), ("abyssal_mushroom", 1), (("cave_vine", 2, 5), 1), ("lumen_quill", 1)],
                   bright=[("cave_bloom", 2), (("abyssal_vine", 3, 8), 1)],
                   density=0.35, glow_ratio=0.12, bright_ratio=0.02, moss=0.12),
        speleothem="abyssal_stalactite", speleothem_density=0.08, crystals=[("deep_crystal_cluster", 1)], crystal_density=0.004,
        debris=[("cave_rubble", 4), ("seafloor_pebbles", 2), ("fallen_kelp", 1)]),
    # Luminous caves: the same rock, but a quarter of the plants glow.
    "luminous": environment(
        [("wet_cave_rock", 4), ("abyssal_cave_rock", 3), ("crystal_cave_rock", 1)],
        [("cave_sediment", 4), ("cave_mud", 2), ("crystal_sediment", 1)],
        [("wet_cave_rock", 3), ("crystal_cave_rock", 1)],
        [("crystal_cave_rock", 2), ("cave_mineral_crust", 1)], 0.08,
        flora=dict(floor=[(("cave_grass", 1, 3), 4), ("cave_fern", 2), (("teal_abyssal_grass", 1, 2), 2), (("cave_tube_plant", 2, 5), 2), ("cave_coral", 1)],
                   wall=[("wall_fern", 3)],
                   ceiling=[(("cave_vine", 2, 7), 3), (("cave_root", 1, 3), 1), (("hanging_kelp", 2, 6), 1), ("luminous_moss", 1)],
                   glow=[("cave_crystal_plant", 3), ("glowtip_grass", 2), ("abyssal_mushroom", 2), ("glow_anemone", 1), (("cave_vine", 3, 8), 2),
                         ("lumen_quill", 2)],
                   bright=[("cave_bloom", 3), (("abyssal_vine", 4, 10), 2), ("abyssal_bloom", 1)],
                   density=0.45, glow_ratio=0.22, bright_ratio=0.045, moss=0.15),
        speleothem="abyssal_stalactite", speleothem_density=0.07, crystals=[("deep_crystal_cluster", 3), ("crystal_needle", 1)],
        crystal_density=0.012, debris=[("cave_rubble", 3), ("crystal_shards", 1)]),
    # Thermal caves: dark rock with orange, red, yellow and dark green accents; kept dim.
    "thermal": environment(
        [("thermal_cave_rock", 6), ("dark_cave_rock", 2), ("black_vent_rock", 1), ("mineral_cave_rock", 1)],
        [("mineral_sediment", 3), ("volcanic_ash", 1), ("cave_mud", 1)],
        [("thermal_cave_rock", 3), ("dark_cave_rock", 1)],
        [("sulfur_deposit", 3), ("cave_mineral_crust", 2), ("sulfur_vent_rock", 1)], 0.12,
        flora=dict(floor=[(("thermal_tube", 1, 4), 3), ("vent_grass", 3), ("heat_moss", 2), (("ashen_abyssal_grass", 1, 2), 1)],
                   wall=[("wall_mineral_vine", 3)],
                   ceiling=[(("cave_root", 1, 3), 1)],
                   glow=[("thermal_plant", 3)],
                   density=0.3, glow_ratio=0.18, bright_ratio=0.0),
        speleothem="thermal_stalactite", speleothem_density=0.1,
        crystals=[("small_thermal_crystal_bud", 3), ("medium_thermal_crystal_bud", 2), ("thermal_crystal_cluster", 1), ("sulfur_cluster", 2)],
        crystal_density=0.03, debris=[("cave_rubble", 3), ("seafloor_pebbles", 1)]),
    "crystal": environment(
        [("crystal_cave_rock", 6), ("crystal_rock", 2), ("wet_cave_rock", 1)],
        [("crystal_sediment", 4), ("cave_sediment", 1)],
        [("crystal_cave_rock", 3), ("deep_crystal_block", 1)],
        [("deep_crystal_block", 3), ("abyssal_crystal_ore", 1)], 0.1,
        flora=dict(floor=[(("teal_abyssal_grass", 1, 2), 2), ("crystal_plant", 2), (("violet_abyssal_grass", 1, 2), 1), ("cave_crystal_plant", 1),
                          ("glasslace", 1)],
                   wall=[("wall_fern", 1)],
                   ceiling=[(("cave_vine", 2, 4), 1)],
                   glow=[("cave_crystal_plant", 3), ("crystal_plant", 2), ("glowtip_grass", 1), ("glasslace", 1)],
                   bright=[("cave_bloom", 1)],
                   density=0.2, glow_ratio=0.25, bright_ratio=0.04),
        speleothem="crystal_stalactite", speleothem_density=0.1,
        crystals=[("deep_crystal_cluster", 4), ("crystal_needle", 3), ("abyssal_crystal_cluster", 2), ("pressure_crystal_cluster", 1), ("pale_crystal_cluster", 1)],
        crystal_density=0.1, debris=[("crystal_shards", 4), ("cave_rubble", 1)]),
    "mineral": environment(
        [("mineral_cave_rock", 6), ("layered_cave_rock", 2), ("mineral_host_rock", 2)],
        [("mineral_sediment", 3), ("cave_sediment", 2), ("cave_mud", 1)],
        [("mineral_cave_rock", 2), ("layered_cave_rock", 1)],
        [("cave_mineral_crust", 4), ("iron_crust", 1), ("copper_crust", 1), ("manganese_crust", 1)], 0.18,
        flora=dict(floor=[(("cave_grass", 1, 2), 2), ("cave_fern", 1), ("seafloor_pebbles", 1)],
                   wall=[("wall_mineral_vine", 3), ("wall_fern", 1)],
                   ceiling=[(("cave_root", 1, 3), 2)],
                   glow=[("cave_crystal_plant", 1)],
                   density=0.15, glow_ratio=0.08, bright_ratio=0.0),
        speleothem="mineral_stalactite", speleothem_density=0.08,
        crystals=[("cobalt_cluster", 1), ("nickel_cluster", 1), ("manganese_nodules", 1), ("sulfur_cluster", 1)], crystal_density=0.015,
        debris=[("cave_rubble", 4), ("seafloor_pebbles", 2)]),
    # Long erosion by seawater: smooth rounded walls, sediment, kelp.
    "eroded": environment(
        [("eroded_cave_rock", 6), ("wet_cave_rock", 3)],
        [("cave_sediment", 5), ("deep_sediment", 2)],
        [("eroded_cave_rock", 3), ("wet_cave_rock", 2)],
        [("layered_cave_rock", 2)], 0.05,
        flora=dict(floor=[(("cave_kelp", 4, 14), 3), (("cave_grass", 1, 3), 3), ("cave_sponge", 1), ("cave_coral", 1), ("seafloor_pebbles", 1)],
                   wall=[("wall_fern", 2)],
                   ceiling=[(("hanging_kelp", 2, 8), 3), (("cave_vine", 2, 5), 1)],
                   glow=[("cave_crystal_plant", 1), ("glowtip_grass", 1)],
                   bright=[("cave_bloom", 1)],
                   density=0.4, glow_ratio=0.08, bright_ratio=0.015, moss=0.08),
        speleothem="abyssal_stalactite", speleothem_density=0.02, debris=[("fallen_kelp", 3), ("cave_rubble", 2), ("seafloor_pebbles", 2)]),
    # Cave forests: layered 3D growth from floor to ceiling in large caverns.
    "forest": environment(
        [("organic_cave_rock", 5), ("wet_cave_rock", 2), ("abyssal_cave_rock", 1)],
        [("organic_sediment", 4), ("cave_mud", 2)],
        [("organic_cave_rock", 2), ("wet_cave_rock", 1)],
        flora=dict(floor=[(("cave_grass", 1, 4), 5), ("cave_fern", 3), (("cave_tube_plant", 2, 6), 3), (("cave_kelp", 4, 16), 3),
                          ("cave_sponge", 1), ("cave_coral", 1), (("abyssal_grass", 1, 3), 2), ("amber_fan", 2)],
                   wall=[("wall_fern", 4)],
                   ceiling=[(("cave_vine", 2, 8), 3), (("hanging_kelp", 3, 10), 3), (("cave_root", 1, 5), 2), (("deep_root", 2, 6), 1),
                            (("resin_root", 2, 6), 2)],
                   glow=[("cave_crystal_plant", 1), ("glowtip_grass", 2), ("abyssal_mushroom", 1), (("cave_vine", 3, 8), 2)],
                   bright=[("cave_bloom", 2), (("abyssal_vine", 6, 16), 2)],
                   giant=[(("giant_cave_kelp", 16, 60), 5), (("ancient_cave_plant", 6, 20), 2), (("deep_kelp", 10, 30), 2)],
                   density=0.75, glow_ratio=0.12, bright_ratio=0.025, giant_density=0.05, moss=0.25),
        speleothem="abyssal_stalactite", speleothem_density=0.03, debris=[("fallen_kelp", 4), ("cave_rubble", 2)]),
    # Underground seas and lakes: wet walls, kelp reaching for the surface, crystals in the gas pocket.
    "underground_sea": environment(
        [("wet_cave_rock", 4), ("eroded_cave_rock", 3), ("abyssal_cave_rock", 2)],
        [("cave_sediment", 3), ("cave_mud", 3)],
        [("wet_cave_rock", 3), ("dark_cave_rock", 1)],
        [("cave_mineral_crust", 2)], 0.06,
        flora=dict(floor=[(("cave_kelp", 4, 20), 4), (("cave_grass", 1, 3), 3), ("cave_sponge", 1), ("cave_coral", 1), (("crystal_kelp", 4, 12), 1)],
                   wall=[("wall_fern", 1)],
                   ceiling=[(("hanging_kelp", 2, 6), 1)],
                   glow=[("cave_crystal_plant", 1), ("glowtip_grass", 1)],
                   bright=[("cave_bloom", 1)],
                   giant=[(("giant_cave_kelp", 10, 40), 2), (("crystal_kelp", 8, 24), 1)],
                   density=0.45, glow_ratio=0.1, bright_ratio=0.02, giant_density=0.012, moss=0.1),
        speleothem="abyssal_stalactite", speleothem_density=0.1, crystals=[("deep_crystal_cluster", 3), ("crystal_needle", 2)],
        crystal_density=0.02, debris=[("cave_rubble", 2), ("crystal_shards", 1)]),
    "trench": environment(
        [("dark_cave_rock", 5), ("abyssal_cave_rock", 3), ("trench_rock", 2)],
        [("deep_mud", 3), ("cave_mud", 2)],
        [("dark_cave_rock", 1)],
        [("cobalt_crust", 2), ("cave_mineral_crust", 1)], 0.08,
        flora=dict(floor=[(("ashen_abyssal_grass", 1, 2), 2), ("black_coral", 1), ("cave_fern", 1)],
                   wall=[("wall_fern", 1)],
                   ceiling=[(("cave_root", 1, 3), 1)],
                   glow=[("soul_coral", 1)],
                   bright=[("hadal_bloom", 1)],
                   density=0.15, glow_ratio=0.12, bright_ratio=0.03),
        speleothem="abyssal_stalactite", speleothem_density=0.07, crystals=[("pressure_crystal_cluster", 3), ("deep_crystal_cluster", 1)],
        crystal_density=0.02, debris=[("cave_rubble", 3)]),
}


def profile(biomes, system_chance, cave_types, environment, strata, ores, *, minor=0.3, connection=0.4, minor_types=None,
            landmark_chance=0.0, landmarks=None, luminous=0.0, templates=None, crystals=None):
    return {"biomes": [A(b) for b in biomes], "system_chance": system_chance, "minor_cave_chance": minor, "connection_chance": connection,
            "cave_types": weighted(list(cave_types.items())),
            "minor_types": weighted(list((minor_types or {"small_sea_cave": 6, "sea_arch": 2, "eroded_cave": 2}).items())),
            "landmark_chance": landmark_chance, "landmarks": weighted(list((landmarks or {}).items())),
            "environment": A(environment), "luminous_chance": luminous,
            "strata": [{"depth": d, "state": state(b)} for d, b in strata],
            "ores": blocks(list(ores.items())),
            "cavern_templates": weighted([(A(t), w) for t, w in (templates or {"mixed": 1}).items()]),
            "crystal_colors": weighted([(CRYSTAL_COLORS[c], w) for c, w in (crystals or {"cyan": 1}).items()])}


# Crystal colours of cavern crystal forests: the solid crystal (emissive texture, no block light) and its cluster.
CRYSTAL_COLORS = {
    "cyan": {"crystal": state("cyan_crystal_block"), "cluster": state("deep_crystal_cluster")},
    "blue": {"crystal": state("blue_crystal_block"), "cluster": state("cobalt_cluster")},
    "violet": {"crystal": state("violet_crystal_block"), "cluster": state("abyssal_crystal_cluster")},
    "green": {"crystal": state("green_crystal_block"), "cluster": state("nickel_cluster")},
    "white": {"crystal": state("white_crystal_block"), "cluster": state("pale_crystal_cluster")},
    "amber": {"crystal": state("amber_crystal_block"), "cluster": state("thermal_crystal_cluster")},
}


# Rarity: small/medium caves common, large uncommon (~10%), massive caverns rare (~2%), landmarks very rare (~2-4% of systems).
CAVE_PROFILES = {
    "deep_sea": profile(["deep_sea"], 0.8,
                        {"small_sea_cave": 30, "medium_sea_cave": 28, "large_abyssal_cave": 10, "massive_cavern": 2, "sea_tunnel": 10,
                         "vertical_shaft": 5, "eroded_cave": 10, "mineral_cave": 5, "crystal_cave": 2, "underground_sea": 1},
                        "abyssal", [(0, "cave_sediment"), (5, "wet_cave_rock"), (18, "layered_cave_rock"), (40, "abyssal_cave_rock"), (90, "dark_cave_rock")],
                        {"abyssal_iron_ore": 3, "manganese_ore": 2, "deep_copper_ore": 2},
                        minor=0.45, connection=0.45, landmark_chance=0.02, luminous=0.25,
                        landmarks={"giant_stalactite_chamber": 2, "abyssal_underground_lake": 2, "ancient_mineral_chamber": 1, "massive_crystal_chamber": 1},
                             templates={"mixed": 4, "lake": 2, "ruins_like_geology": 2, "forest": 1, "crystal": 1, "mineral": 1}, crystals={"cyan": 3, "blue": 2, "white": 1}),
    "abyssal_ocean": profile(["abyssal_ocean", "sunken_ruins", "bone_graveyard"], 0.8,
                             {"small_sea_cave": 24, "medium_sea_cave": 26, "large_abyssal_cave": 14, "massive_cavern": 3, "sea_tunnel": 6,
                              "vertical_shaft": 7, "eroded_cave": 6, "mineral_cave": 8, "crystal_cave": 3, "underground_sea": 2},
                             "abyssal", [(0, "cave_mud"), (4, "wet_cave_rock"), (16, "abyssal_cave_rock"), (50, "layered_cave_rock"), (80, "dark_cave_rock")],
                             {"manganese_ore": 4, "abyssal_iron_ore": 2, "deep_nickel_ore": 1},
                             minor=0.35, connection=0.5, landmark_chance=0.025, luminous=0.3,
                             landmarks={"giant_stalactite_chamber": 2, "abyssal_underground_lake": 3, "ancient_mineral_chamber": 2},
                             templates={"mixed": 3, "ruins_like_geology": 3, "mineral": 2, "lake": 2, "crystal": 1}, crystals={"violet": 3, "cyan": 2, "blue": 1}),
    "forests": profile(["abyssal_forest", "deep_forest", "glow_gardens"], 0.8,
                       {"small_sea_cave": 20, "medium_sea_cave": 25, "large_abyssal_cave": 14, "massive_cavern": 3, "eroded_cave": 10,
                        "sea_tunnel": 6, "underground_sea": 1},
                       "forest", [(0, "organic_sediment"), (4, "organic_cave_rock"), (20, "wet_cave_rock"), (45, "layered_cave_rock"), (80, "abyssal_cave_rock")],
                       {"abyssal_iron_ore": 2, "deep_copper_ore": 2},
                       minor=0.4, connection=0.45, landmark_chance=0.03, luminous=0.15,
                       landmarks={"giant_kelp_cavern": 3, "deep_cave_forest": 3, "abyssal_underground_lake": 1},
                             templates={"forest": 6, "mixed": 2, "lake": 2}, crystals={"green": 3, "cyan": 2, "white": 1}),
    "abyssal_trench": profile(["abyssal_trench"], 0.75,
                              {"trench_cave": 30, "medium_sea_cave": 15, "small_sea_cave": 15, "vertical_shaft": 12, "mineral_cave": 10,
                               "large_abyssal_cave": 8, "massive_cavern": 2},
                              "trench", [(0, "deep_mud"), (4, "dark_cave_rock"), (25, "trench_rock"), (60, "dark_cave_rock")],
                              {"cobalt_ore": 3, "deep_nickel_ore": 2, "manganese_ore": 1},
                              minor=0.25, connection=0.35, landmark_chance=0.02, luminous=0.1,
                              landmarks={"ancient_mineral_chamber": 2, "giant_stalactite_chamber": 2},
                             templates={"ruins_like_geology": 4, "mineral": 3, "mixed": 2, "crystal": 1}, crystals={"blue": 3, "violet": 2, "white": 1}),
    "hadal_zone": profile(["hadal_zone"], 0.7,
                          {"small_sea_cave": 30, "medium_sea_cave": 20, "trench_cave": 15, "crystal_cave": 10, "mineral_cave": 10},
                          "trench", [(0, "abyssal_mud"), (3, "dark_cave_rock"), (20, "trench_rock")],
                          {"cobalt_ore": 3, "deep_nickel_ore": 3, "abyssal_crystal_ore": 1},
                          minor=0.2, connection=0.3, landmark_chance=0.02, luminous=0.2,
                          landmarks={"massive_crystal_chamber": 2},
                             templates={"ruins_like_geology": 3, "crystal": 3, "mixed": 2, "mineral": 2}, crystals={"violet": 3, "white": 2, "blue": 1}),
    "volcanic_deep": profile(["volcanic_deep"], 0.8,
                             {"thermal_cave": 30, "medium_sea_cave": 15, "small_sea_cave": 15, "large_abyssal_cave": 8, "vertical_shaft": 8,
                              "massive_cavern": 2},
                             "thermal", [(0, "volcanic_ash"), (4, "volcanic_rock"), (20, "thermal_cave_rock"), (55, "dark_cave_rock")],
                             {"sulfur_ore": 3, "cobalt_ore": 1, "deep_nickel_ore": 1},
                             minor=0.3, connection=0.4, landmark_chance=0.03,
                             landmarks={"thermal_cathedral": 3}, minor_types={"small_sea_cave": 6, "sea_arch": 1},
                             templates={"thermal": 6, "mineral": 2, "ruins_like_geology": 2}, crystals={"amber": 4, "white": 1}),
    "thermal_vents": profile(["thermal_vents", "brine_lakes"], 0.8,
                             {"thermal_cave": 35, "medium_sea_cave": 15, "small_sea_cave": 15, "mineral_cave": 10, "large_abyssal_cave": 6,
                              "massive_cavern": 2},
                             "thermal", [(0, "mineral_sediment"), (4, "thermal_cave_rock"), (25, "mineral_cave_rock"), (60, "dark_cave_rock")],
                             {"sulfur_ore": 3, "deep_copper_ore": 2, "thermal_crystal_ore": 1},
                             minor=0.3, connection=0.45, landmark_chance=0.035,
                             landmarks={"thermal_cathedral": 4}, minor_types={"small_sea_cave": 6, "sea_arch": 1},
                             templates={"thermal": 6, "mineral": 3, "mixed": 1}, crystals={"amber": 3, "green": 1, "white": 1}),
    "deep_crystal_fields": profile(["deep_crystal_fields", "frost_abyss"], 0.8,
                                   {"crystal_cave": 35, "medium_sea_cave": 15, "small_sea_cave": 15, "large_abyssal_cave": 8, "eroded_cave": 6,
                                    "massive_cavern": 2},
                                   "crystal", [(0, "crystal_sediment"), (4, "crystal_cave_rock"), (25, "layered_cave_rock"), (55, "abyssal_cave_rock")],
                                   {"abyssal_crystal_ore": 3, "thermal_crystal_ore": 1},
                                   minor=0.35, connection=0.45, landmark_chance=0.035, luminous=0.4,
                                   landmarks={"massive_crystal_chamber": 4},
                             templates={"crystal": 6, "mixed": 2, "lake": 1}, crystals={"cyan": 3, "blue": 2, "violet": 1, "white": 1, "green": 1}),
}


def cavern_template(patches, structures, *, deep=None, forest=None, hanging=None, ceiling_glow=0.02, wall_relief=1.5, center_chance=0.4,
                    centers=None):
    t = {"patches": weighted(list(patches.items())), "structures": structures, "ceiling_glow": ceiling_glow, "wall_relief": wall_relief,
         "center_chance": center_chance}
    if deep:
        t["deep_patches"] = weighted(list(deep.items()))
    if forest:
        density, clearings, lean, lo, hi, species = forest
        t["kelp_forest"] = {"density": density, "clearings": clearings, "lean": lean, "min_height": lo, "max_height": hi,
                            "plants": plants(list(species.items()))}
    if hanging:
        density, reach, species = hanging
        t["hanging"] = {"density": density, "reach_floor": reach, "plants": plants(list(species.items()))}
    if centers:
        t["centers"] = weighted(list(centers.items()))
    return t


def rates(**kw):
    """Structure rates relative to the defaults (1.0 = the base count for a radius-24 cavern)."""
    return kw


# Large caverns (radius 16+) are dressed from one of these. Landmarks force a matching template.
CAVERN_TEMPLATES = {
    "forest": cavern_template(
        {"plant": 6, "open": 2, "water": 1, "rock": 1, "crystal": 1}, deep={"plant": 4, "water": 2, "crystal": 1, "open": 1},
        structures=rates(pillars=0.8, floating_rocks=0.6, bridges=0.5, shelves=1.0, wall_caves=1.0, mounds=1.2, hollows=0.8, valleys=1.0, rubble=0.3,
                         stalactites=0.6, rock_spikes=0.4, mega_ores=0.2, mineral_pillars=0.1, crystal_forests=0.2, lakes=0.8, gardens=1.2, groves=1.5),
        forest=(1.0, 0.35, 0.4, 12, 50, {"giant_cave_kelp": 5, "giant_kelp": 2, "ancient_cave_plant": 1, "crystal_kelp": 1}),
        hanging=(0.8, 0.12, {"deep_root": 3, "cave_root": 3, "abyssal_vine": 2, "hanging_kelp": 3, "cave_vine": 2, "resin_root": 3}),
        ceiling_glow=0.03, center_chance=0.7, centers={"ancient_plant": 6, "kelp_grove": 3, "deep_sea_garden": 2, "rock_island": 1}),
    "crystal": cavern_template(
        {"crystal": 6, "open": 2, "rock": 1, "plant": 1, "mineral": 1}, deep={"crystal": 5, "water": 1, "open": 1},
        structures=rates(pillars=0.6, floating_rocks=0.5, bridges=0.4, shelves=0.8, wall_caves=1.0, mounds=0.6, hollows=0.6, valleys=0.3, rubble=0.4,
                         stalactites=1.0, rock_spikes=1.2, mega_ores=0.3, mineral_pillars=0.2, crystal_forests=2.0, lakes=0.4, gardens=0.6, groves=0.2),
        forest=(0.15, 0.3, 0.2, 8, 30, {"crystal_kelp": 3, "giant_cave_kelp": 1}),
        hanging=(0.3, 0.05, {"abyssal_vine": 2, "cave_vine": 2}),
        ceiling_glow=0.06, center_chance=0.75, centers={"giant_crystal": 6, "deep_sea_garden": 1, "lake": 1}),
    "mineral": cavern_template(
        {"mineral": 6, "rock": 2, "open": 2, "plant": 1}, deep={"mineral": 5, "thermal": 1, "rock": 1},
        structures=rates(pillars=0.8, floating_rocks=0.4, bridges=0.4, shelves=1.2, wall_caves=1.2, mounds=0.8, hollows=0.6, valleys=0.3, rubble=1.0,
                         stalactites=0.8, rock_spikes=0.8, mega_ores=2.0, mineral_pillars=1.5, crystal_forests=0.3, lakes=0.2, gardens=0.2, groves=0.2),
        forest=(0.2, 0.3, 0.3, 8, 30, {"giant_cave_kelp": 1}),
        hanging=(0.3, 0.08, {"cave_root": 3, "deep_root": 1}),
        ceiling_glow=0.01, center_chance=0.65, centers={"mineral_pillar": 6, "giant_pillar": 2, "thermal_vents": 1}),
    "thermal": cavern_template(
        {"thermal": 5, "mineral": 2, "rock": 2, "open": 2, "plant": 1}, deep={"thermal": 5, "mineral": 2},
        structures=rates(pillars=0.6, floating_rocks=0.3, bridges=0.3, shelves=0.8, wall_caves=0.8, mounds=1.0, hollows=0.8, valleys=0.4, rubble=0.8,
                         stalactites=0.8, rock_spikes=0.6, mega_ores=1.0, mineral_pillars=0.8, crystal_forests=0.2, lakes=0.3, gardens=0.1, groves=0.1),
        forest=(0.1, 0.3, 0.2, 8, 24, {"giant_cave_kelp": 1}),
        hanging=(0.2, 0.05, {"cave_root": 1}),
        ceiling_glow=0.01, center_chance=0.7, centers={"thermal_vents": 6, "mineral_pillar": 2}),
    "lake": cavern_template(
        {"water": 5, "plant": 3, "open": 2, "crystal": 1}, deep={"water": 4, "plant": 2, "crystal": 1},
        structures=rates(pillars=0.6, floating_rocks=0.4, bridges=0.8, shelves=1.0, wall_caves=1.0, mounds=0.8, hollows=0.4, valleys=0.8, rubble=0.3,
                         stalactites=0.8, rock_spikes=0.5, mega_ores=0.2, mineral_pillars=0.1, crystal_forests=0.3, lakes=2.5, gardens=0.6, groves=0.8),
        forest=(0.5, 0.35, 0.3, 10, 40, {"giant_cave_kelp": 3, "crystal_kelp": 2}),
        hanging=(0.5, 0.1, {"hanging_kelp": 3, "cave_vine": 2, "abyssal_vine": 1}),
        ceiling_glow=0.03, center_chance=0.7, centers={"lake": 5, "rock_island": 2, "kelp_grove": 1}),
    # Geology on a grand scale: forests of pillars, bridges, hanging rock, giant stalactites, deep-cut walls. Sparse life.
    "ruins_like_geology": cavern_template(
        {"rock": 4, "open": 4, "plant": 1, "mineral": 1}, deep={"rock": 3, "open": 2, "mineral": 2, "crystal": 1},
        structures=rates(pillars=2.5, floating_rocks=2.0, bridges=2.0, shelves=2.0, wall_caves=1.5, mounds=0.8, hollows=0.6, valleys=0.6, rubble=2.0,
                         stalactites=2.5, rock_spikes=2.0, mega_ores=0.5, mineral_pillars=0.4, crystal_forests=0.2, lakes=0.2, gardens=0.1, groves=0.2),
        forest=(0.2, 0.4, 0.3, 10, 40, {"giant_cave_kelp": 1}),
        hanging=(0.5, 0.2, {"deep_root": 3, "cave_root": 3, "abyssal_vine": 1, "resin_root": 2}),
        ceiling_glow=0.015, wall_relief=3.0, center_chance=0.8, centers={"giant_pillar": 5, "rock_island": 2, "giant_crystal": 1}),
    "mixed": cavern_template(
        {"plant": 3, "open": 3, "rock": 2, "mineral": 2, "crystal": 2, "water": 1}, deep={"crystal": 2, "mineral": 2, "water": 2, "plant": 2, "thermal": 1},
        structures=rates(pillars=1.0, floating_rocks=1.0, bridges=0.8, shelves=1.0, wall_caves=1.0, mounds=1.0, hollows=1.0, valleys=0.8, rubble=0.8,
                         stalactites=1.0, rock_spikes=1.0, mega_ores=0.8, mineral_pillars=0.6, crystal_forests=0.8, lakes=0.8, gardens=0.6, groves=0.8),
        forest=(0.6, 0.35, 0.35, 10, 45, {"giant_cave_kelp": 4, "giant_kelp": 1, "crystal_kelp": 1}),
        hanging=(0.5, 0.12, {"cave_root": 2, "deep_root": 1, "abyssal_vine": 1, "hanging_kelp": 2, "cave_vine": 2, "resin_root": 1}),
        ceiling_glow=0.025, center_chance=0.5,
        centers={"ancient_plant": 3, "giant_crystal": 2, "giant_pillar": 2, "deep_sea_garden": 2, "rock_island": 1, "lake": 1, "mineral_pillar": 1}),
}


def caves():
    for sub in ("cave_environment", "cave_profile", "cavern_template"):
        shutil.rmtree(os.path.join(CAVE_DIR, sub), ignore_errors=True)
    for name, env in CAVE_ENVIRONMENTS.items():
        write_data(os.path.join(CAVE_DIR, "cave_environment", name + ".json"), env)
    for name, prof in CAVE_PROFILES.items():
        write_data(os.path.join(CAVE_DIR, "cave_profile", name + ".json"), prof)
    for name, template in CAVERN_TEMPLATES.items():
        write_data(os.path.join(CAVE_DIR, "cavern_template", name + ".json"), template)
    covered = {b for p in CAVE_PROFILES.values() for b in p["biomes"]}
    missing = {A(b) for b in DEEP_BIOMES} - covered
    assert not missing, f"deep biomes without a cave profile: {missing}"


def write_data(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# ================================================================ biomes

# Global orders per generation step: every biome lists a subset in this order, so feature order stays consistent.
LANDFORM_ORDER = ["seabed_structures", "rock_spire", "abyssal_spire", "trench_spire", "thermal_spire", "crystal_spire"]
VEIN_ORDER = [f"vein_{m}_{s}" for m in MINERALS for s in ("small", "medium", "large", "huge")] + [f"vein_{m}_rare" for m in RARE_VEINS]
DECOR_ORDER = ["vent_fields", "cave_deep_crystals", "cave_abyssal_mushrooms"]
VEG_ORDER = ["seabed_structure_dressing", "meadow_green_normal", "meadow_green_sparse", "meadow_organic_dense", "meadow_organic_full", "meadow_ashen_normal",
             "meadow_ancient_normal", "meadow_crystal_normal", "meadow_thermal_sparse", "meadow_thermal_normal",
             "tube_plants", "tube_plants_dense", "sponge_plants", "thermal_tubes", "mineral_vines", "crystal_plants",
             "deep_kelp", "deep_kelp_dense", "large_deep_kelp", "giant_kelp_forest", "giant_kelp_scattered",
             "giant_tube_forest", "giant_tube_scattered", "void_kelp_forest", "void_kelp",
             "patch_glow_coral", "patch_abyssal_mushroom", "patch_abyssal_bloom", "patch_soul_coral", "patch_black_coral",
             "patch_hadal_bloom", "glow_plants_shallow", "glow_plants_deep", "glow_plants_ancient", "glow_plants_crystal",
             "floating_blooms", "crystal_garden", "crystal_spikes", "patch_deep_crystal_cluster", "patch_pressure_crystal",
             "manganese_nodule_field", "seafloor_pebbles"]

DEEP_BIOMES = {
    # biome: (water colour, fog colour, landforms, cave decorations, vegetation)
    "deep_sea": (0x1B3F8B, 0x061A40, ["rock_spire"], [],
                 ["meadow_green_normal", "tube_plants", "deep_kelp", "giant_kelp_scattered", "patch_glow_coral", "glow_plants_shallow",
                  "floating_blooms", "seafloor_pebbles"]),
    "abyssal_ocean": (0x2B1F6B, 0x0A0620, ["rock_spire", "abyssal_spire"], ["cave_abyssal_mushrooms"],
                      ["meadow_green_sparse", "tube_plants", "sponge_plants", "void_kelp", "patch_abyssal_mushroom", "patch_soul_coral",
                       "glow_plants_deep", "floating_blooms", "manganese_nodule_field", "seafloor_pebbles"]),
    "abyssal_forest": (0x1A3A2A, 0x051510, [], ["cave_abyssal_mushrooms"],
                       ["meadow_organic_dense", "sponge_plants", "deep_kelp_dense", "large_deep_kelp", "giant_kelp_forest", "void_kelp_forest",
                        "patch_abyssal_bloom", "glow_plants_shallow", "floating_blooms", "seafloor_pebbles"]),
    "deep_forest": (0x243A30, 0x06140E, [], ["cave_abyssal_mushrooms"],
                    ["meadow_organic_full", "tube_plants_dense", "sponge_plants", "deep_kelp", "giant_kelp_scattered", "giant_tube_forest",
                     "glow_plants_shallow", "floating_blooms"]),
    "abyssal_trench": (0x14103A, 0x040214, ["trench_spire"], ["cave_abyssal_mushrooms"],
                       ["meadow_ashen_normal", "tube_plants", "giant_tube_scattered", "patch_soul_coral", "patch_black_coral",
                        "glow_plants_deep", "patch_pressure_crystal", "seafloor_pebbles"]),
    "hadal_zone": (0x0A0A1A, 0x010104, ["abyssal_spire", "trench_spire"], ["cave_deep_crystals"],
                   ["meadow_ancient_normal", "large_deep_kelp", "giant_kelp_scattered", "giant_tube_scattered", "patch_black_coral",
                    "patch_hadal_bloom", "glow_plants_ancient", "floating_blooms", "patch_deep_crystal_cluster", "patch_pressure_crystal"]),
    "volcanic_deep": (0x3A2A3A, 0x1A0A08, ["thermal_spire"], [],
                      ["meadow_thermal_sparse", "thermal_tubes", "mineral_vines"]),
    "thermal_vents": (0x2A4A5A, 0x0E1A1E, ["thermal_spire"], [],
                      ["meadow_thermal_normal", "thermal_tubes", "mineral_vines", "patch_abyssal_mushroom"]),
    "deep_crystal_fields": (0x2A5A8A, 0x0A2040, ["crystal_spire"], ["cave_deep_crystals"],
                            ["meadow_crystal_normal", "crystal_plants", "glow_plants_crystal", "floating_blooms", "crystal_garden",
                             "crystal_spikes", "patch_deep_crystal_cluster"]),
    "sunken_ruins": (0x3A5A6A, 0x10222A, ["rock_spire"], ["cave_abyssal_mushrooms"],
                     ["meadow_green_sparse", "tube_plants", "sponge_plants", "patch_soul_coral", "glow_plants_deep", "seafloor_pebbles"]),
    "bone_graveyard": (0x3E3C4E, 0x121118, ["abyssal_spire"], [],
                       ["meadow_ashen_normal", "patch_black_coral", "patch_soul_coral", "glow_plants_ancient", "seafloor_pebbles"]),
    "brine_lakes": (0x2F6A66, 0x0C2624, [], [],
                    ["meadow_thermal_sparse", "mineral_vines", "patch_pressure_crystal", "seafloor_pebbles"]),
    "glow_gardens": (0x1E7A5A, 0x082C20, [], ["cave_abyssal_mushrooms"],
                     ["meadow_organic_dense", "sponge_plants", "patch_glow_coral", "patch_abyssal_mushroom", "patch_abyssal_bloom",
                      "glow_plants_shallow", "glow_plants_deep", "floating_blooms"]),
    "frost_abyss": (0x4A7AAE, 0x13304C, ["rock_spire"], ["cave_deep_crystals"],
                    ["meadow_ancient_normal", "void_kelp", "patch_pressure_crystal", "patch_deep_crystal_cluster", "seafloor_pebbles"]),
}
# Deep biomes without seabed structure profiles yet (tools/seabed_structures.py PROFILES): the painter skips them.
NO_STRUCTURE_PROFILE = {"sunken_ruins", "bone_graveyard", "brine_lakes", "glow_gardens", "frost_abyss"}


def ordered(order, wanted):
    missing = set(wanted) - set(order)
    assert not missing, missing
    return [A(f) for f in order if f in wanted]


def deep_biome(name):
    water, fog, landforms, decor, veg = DEEP_BIOMES[name]
    features = [[] for _ in range(11)]
    features[2] = ordered(LANDFORM_ORDER, ["seabed_structures", *landforms])          # structure bodies, then large landforms
    features[6] = ordered(VEIN_ORDER, [f"vein_{m}_{s}" for m, s in VEINS[name]])      # ore veins and their surface crusts
    features[7] = ordered(DECOR_ORDER, ["vent_fields", *decor])                        # vents after veins, then caves
    features[9] = ordered(VEG_ORDER, ["seabed_structure_dressing", *veg])              # structure dressing, then vegetation, crystals, small decorations
    effects = {"fog_color": fog, "sky_color": 0, "water_color": water, "water_fog_color": fog,
               "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 3000}}
    return {"carvers": {}, "downfall": 0.5, "has_precipitation": False, "temperature": 0.5, "effects": effects,
            "features": features, "spawn_costs": {},
            "spawners": {"ambient": [], "axolotls": [], "creature": [], "misc": [],
                         # no drowned: the deep ocean belongs to its own fauna (tools/gen_fauna.py, FaunaSpawner)
                         "monster": [],
                         "underground_water_creature": [{"type": "minecraft:glow_squid", "weight": 10, "minCount": 2, "maxCount": 4}],
                         "water_ambient": [{"type": "minecraft:cod", "weight": 5, "minCount": 2, "maxCount": 4}],
                         "water_creature": [{"type": "minecraft:squid", "weight": 3, "minCount": 1, "maxCount": 3}]}}


def biomes():
    for name in DEEP_BIOMES:
        write("biome/" + name, deep_biome(name))
    # Twilight Reef lives in the ocean world: warm-ocean features minus land ones, plus reef plants.
    warm = vanilla("biome/warm_ocean")
    land = {"minecraft:trees_water", "minecraft:flower_default", "minecraft:patch_grass_badlands", "minecraft:brown_mushroom_normal",
            "minecraft:red_mushroom_normal", "minecraft:patch_sugar_cane", "minecraft:patch_pumpkin"}
    reef = copy.deepcopy(warm)
    reef["features"][9] = [f for f in warm["features"][9] if f not in land] + [A("reef_kelp"), A("patch_sea_fern"), A("patch_glow_anemone")]
    reef["carvers"] = {}
    reef["effects"].update({"water_color": 0x2E8FD6, "water_fog_color": 0x0A4D8C})
    write("biome/twilight_reef", reef)
    write("biome/abyssal_rift", rift_biome())


def rift_biome():
    # Abyssal Rift (ocean world): the shaft down to the deep ocean. No features (nothing grows into or blocks the
    # shaft) and not in any vanilla biome tag, so ocean structures stay clear of it; its dark water shows from above.
    effects = {"fog_color": 12638463, "sky_color": 8103167, "water_color": 0x1B2A6B, "water_fog_color": 0x030A1E,
               "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 3000}}
    return {"carvers": {}, "downfall": 0.5, "has_precipitation": True, "temperature": 0.5, "effects": effects,
            "features": [[] for _ in range(11)], "spawn_costs": {},
            "spawners": {"ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
                         "underground_water_creature": [{"type": "minecraft:glow_squid", "weight": 10, "minCount": 2, "maxCount": 4}],
                         "water_ambient": [{"type": "minecraft:cod", "weight": 5, "minCount": 2, "maxCount": 4}],
                         "water_creature": [{"type": "minecraft:squid", "weight": 3, "minCount": 1, "maxCount": 3}]}}


def params(biome, temperature=(-1, 1), humidity=(-1, 1), continentalness=(-2, 2), weirdness=(-1, 1), erosion=(-1, 1)):
    return {"biome": biome, "parameters": {"temperature": list(temperature), "humidity": list(humidity), "continentalness": list(continentalness),
                                           "erosion": list(erosion), "weirdness": list(weirdness), "depth": 0, "offset": 0}}


def biome_sources():
    # Deep ocean: continentalness = 0.4 * macro seabed offset (macro seabed Y = 160 * c, fuzzed), humidity = habitat
    # province, weirdness = volcanic / crystal province. Depth zones by macro seabed Y: shelves and upper slopes, abyssal
    # plains, trench system flanks and basins, hadal floors.
    Y = lambda lo, hi: (lo / 160 if lo > -300 else -2, hi / 160 if hi < 300 else 2)
    # Province thresholds sit in the noises' tails (std ~0.27): each province type covers roughly 7-10% of the seabed.
    H, W = (-0.42, 0.36), (-0.37, 0.38)
    normal = dict(humidity=H, weirdness=W)
    # Water-mass / relic provinces (temperature = region_temperature, erosion = region_erosion; same noise shape, std
    # ~0.28) only replace abyssal_ocean, the dominant plains zone (~50% of the seabed): every other biome keeps its area.
    # Cold tail -> frost_abyss; warm tail -> brine_lakes (dry habitat side) / glow_gardens (humid side); off-tail
    # temperatures: erosion tails -> sunken_ruins (low) / bone_graveyard (high). Each ends up ~4-6% of the seabed.
    T, E, HS = (-0.36, 0.27), (-0.32, 0.32), -0.03
    plains = dict(continentalness=Y(0, 80), weirdness=W)
    deep = [
        params(A("deep_sea"), continentalness=Y(80, 999), **normal),
        params(A("abyssal_ocean"), temperature=T, erosion=E, continentalness=Y(0, 80), **normal),
        params(A("frost_abyss"), temperature=(-2, T[0]), humidity=H, **plains),
        params(A("brine_lakes"), temperature=(T[1], 2), humidity=(H[0], HS), **plains),
        params(A("glow_gardens"), temperature=(T[1], 2), humidity=(HS, H[1]), **plains),
        params(A("sunken_ruins"), temperature=T, erosion=(-2, E[0]), humidity=H, **plains),
        params(A("bone_graveyard"), temperature=T, erosion=(E[1], 2), humidity=H, **plains),
        params(A("abyssal_trench"), continentalness=Y(-40, 0), **normal),
        params(A("hadal_zone"), continentalness=Y(-999, -40), **normal),
        params(A("volcanic_deep"), continentalness=Y(-999, 150), weirdness=(W[1], 2)),
        params(A("deep_crystal_fields"), continentalness=Y(-999, 125), weirdness=(-2, W[0])),
        params(A("abyssal_forest"), continentalness=Y(0, 999), humidity=(H[1], 0.6), weirdness=W),
        params(A("deep_forest"), continentalness=Y(0, 999), humidity=(0.6, 2), weirdness=W),
        params(A("thermal_vents"), continentalness=Y(-95, 125), humidity=(-2, H[0]), weirdness=W),
    ]
    dim_path = os.path.join(ROOT, "abyssia", "dimension", "deep_ocean.json")
    dim = json.load(open(dim_path))
    dim["generator"]["biome_source"] = {"type": "minecraft:multi_noise", "biomes": deep}
    with open(dim_path, "w") as f:
        json.dump(dim, f, indent=2)
        f.write("\n")

    # Ocean world: Twilight Reef takes the warm, mid-depth band (seabed ~0..25) between shallow warm seas and the deep.
    T = {"frozen": (-2, -0.45), "cold": (-0.45, -0.15), "n": (-0.15, 0.2), "luke": (0.2, 0.55), "warm": (0.55, 2)}
    S, M, D = (0.4, 2), (0, 0.4), (-2, 0)
    mc = lambda n: "minecraft:" + n
    ocean = [
        params(mc("frozen_ocean"), T["frozen"], continentalness=(0, 2)), params(mc("deep_frozen_ocean"), T["frozen"], continentalness=D),
        params(mc("cold_ocean"), T["cold"], continentalness=(0, 2)), params(mc("deep_cold_ocean"), T["cold"], continentalness=D),
        params(mc("ocean"), T["n"], continentalness=(0, 2)), params(mc("deep_ocean"), T["n"], continentalness=D),
        params(mc("lukewarm_ocean"), T["luke"], continentalness=S), params(mc("deep_lukewarm_ocean"), T["luke"], continentalness=D),
        params(mc("warm_ocean"), T["warm"], continentalness=S), params(mc("deep_lukewarm_ocean"), T["warm"], continentalness=D),
        params(A("twilight_reef"), (0.2, 2), continentalness=M),
        # erosion = 1.2 + rift (noise_settings): 0.2 away from rifts, where the entries above (erosion -1..1) match
        # exactly; above 1 (rift > -0.2) only this one does. Parameters are limited to -2..2.
        params(A("abyssal_rift"), (-2, 2), erosion=(1.0, 2.0)),
    ]
    preset = read("world_preset/ocean_world")
    preset["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["biomes"] = ocean
    write("world_preset/ocean_world", preset)


def noise_settings():
    deep = read("noise_settings/deep_ocean")
    deep["default_block"] = {"Name": A("deep_sea_rock")}
    r = deep["noise_router"]
    # Depth zones follow the macro seabed, not the local relief; the fuzz only waves their borders.
    r["continents"] = add(mul(0.4, A("deep_macro_offset")), mul(0.04, snoise(A("biome_fuzz"), 1.0)))
    r["vegetation"] = A("region_habitat")
    r["ridges"] = A("region_volcanic")
    r["depth"] = 0
    r["temperature"] = A("region_temperature")
    r["erosion"] = A("region_erosion")
    r["initial_density_without_jaggedness"] = add(DEEP_GRAD, A("deep_seabed_offset"))
    r["final_density"] = A("deep_final_density")
    deep["surface_rule"] = deep_surface_rule()
    write("noise_settings/deep_ocean", deep)

    ocean = read("noise_settings/ocean")
    o = ocean["noise_router"]
    o["temperature"] = snoise("minecraft:temperature", 0.25 / CLIMATE_SCALE)
    # Deep / shallow / reef follow the seabed's macro layout, not every ridge, trench and canyon.
    o["continents"] = add(A("seabed_macro"), mul(0.02, snoise(A("biome_fuzz"), 1.0)))
    # Rifts pick their biome from the same field that opens their shaft (see biome_sources).
    o["erosion"] = add(1.0 - RIFT["biome"], A("rift"))

    def patch_rule(rule):  # the warm-biome sand rule also covers the reef
        if isinstance(rule, dict):
            if rule.get("type") == "minecraft:biome" and "minecraft:warm_ocean" in rule["biome_is"] and A("twilight_reef") not in rule["biome_is"]:
                rule["biome_is"].append(A("twilight_reef"))
            for v in rule.values():
                patch_rule(v)
        elif isinstance(rule, list):
            for v in rule:
                patch_rule(v)
    patch_rule(ocean["surface_rule"])
    # Rifts: no bedrock, and solid deepslate instead of loose gravel on the floor above the void (before the bedrock rule).
    below_bedrock_top = {"type": "minecraft:not", "invert": {"type": "minecraft:y_above", "anchor": {"absolute": -56},
                                                              "surface_depth_multiplier": 0, "add_stone_depth": False}}
    rift_rule = cond(biome_is("abyssal_rift"), cond(below_bedrock_top, block("minecraft:deepslate", axis="y")))
    rules = ocean["surface_rule"]["sequence"]
    if rift_rule not in rules:
        rules.insert(0, rift_rule)
    write("noise_settings/ocean", ocean)


def main():
    for d in ("biome", "configured_feature", "placed_feature", "density_function", "noise"):
        shutil.rmtree(os.path.join(WG, d), ignore_errors=True)
    terrain()
    veins()
    spires()
    vegetation()
    resource_plants()
    add_resource_plants()
    vent_fields()
    for name, cf in CONFIGURED.items():
        write("configured_feature/" + name, cf)
    for name, (cf, placement) in PLACED.items():
        write("placed_feature/" + name, {"feature": A(cf), "placement": placement})
    biomes()
    biome_sources()
    noise_settings()
    caves()
    profiled = {b for biomes, _, _ in seabed_structures.PROFILES.values() for b in biomes}
    counts = seabed_structures.write(CAVE_DIR, set(DEEP_BIOMES) - (NO_STRUCTURE_PROFILE - profiled))
    print(f"{len(CONFIGURED)} configured features, {len(PLACED)} placed features, {len(DEEP_BIOMES) + 2} biomes, "
          f"{len(CAVE_ENVIRONMENTS)} cave environments, {len(CAVE_PROFILES)} cave profiles, {len(CAVERN_TEMPLATES)} cavern templates, "
          f"{counts[0]} seabed structures, {counts[1]} structure profiles")


if __name__ == "__main__":
    main()
