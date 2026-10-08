"""Building blocks: stone families and the ancient deep-sea wood set.

Every main terrain rock gets a vanilla-style family (stairs / slab / wall, polished, bricks with their own shapes,
cracked bricks, chiseled); vent and cave rocks get stairs / slab / wall; the ancient deep-sea plant's stem becomes
a full wood set (stripped stem, wood, planks, stairs, slab, fence, fence gate, door, trapdoor, pressure plate,
button).

This module is data plus the JSON writers (models, blockstates, loot tables, recipes, tags, names); it is not run
on its own - gen_deep_assets.main() calls generate().  The stone family textures (polished / bricks / cracked
bricks / chiseled) are derived from their base texture by
derive_textures.py; the ancient wood textures come from forge_textures.py.  The Java side is
com.abyssia.registry.ModBuildingBlocks.
"""
import os
from dataclasses import dataclass

MOD = "abyssia"


@dataclass(frozen=True)
class StoneFamily:
    rock: str
    en: str
    ja: str

    @property
    def polished(self):
        return f"polished_{self.rock}"

    @property
    def bricks(self):
        return f"{self.rock}_bricks"

    @property
    def cracked_bricks(self):
        return f"cracked_{self.rock}_bricks"

    @property
    def chiseled(self):
        return f"chiseled_{self.rock}"

    @property
    def brick(self):
        return f"{self.rock}_brick"


# Their textures are derived from the family's base rock texture by derive_textures.py.
STONE_FAMILIES = [
    StoneFamily("deep_sea_rock", "Deep Sea Rock", "深海岩"),
    StoneFamily("abyssal_rock", "Abyssal Rock", "深淵岩"),
    StoneFamily("trench_rock", "Trench Rock", "海溝岩"),
    StoneFamily("thermal_rock", "Thermal Rock", "熱水岩"),
    StoneFamily("volcanic_rock", "Volcanic Rock", "海底火山岩"),
    StoneFamily("crystal_rock", "Crystal Rock", "結晶岩"),
    StoneFamily("mineral_host_rock", "Mineral Host Rock", "鉱床母岩"),
]

# Rocks that only get stairs / slab / wall (their own texture on every shape): name -> (English, Japanese)
SIMPLE_STONES = {
    "vent_rock": ("Vent Rock", "噴出孔岩"), "black_vent_rock": ("Black Vent Rock", "黒色噴出孔岩"),
    "sulfur_vent_rock": ("Sulfur Vent Rock", "硫黄噴出孔岩"), "mineral_vent_rock": ("Mineral Vent Rock", "鉱物噴出孔岩"),
    "abyssal_cave_rock": ("Abyssal Cave Rock", "深淵の洞窟岩"), "dark_cave_rock": ("Dark Cave Rock", "暗色洞窟岩"),
    "wet_cave_rock": ("Wet Cave Rock", "濡れた洞窟岩"), "layered_cave_rock": ("Layered Cave Rock", "層状洞窟岩"),
    "mineral_cave_rock": ("Mineral Cave Rock", "鉱物洞窟岩"), "organic_cave_rock": ("Organic Cave Rock", "有機質洞窟岩"),
    "eroded_cave_rock": ("Eroded Cave Rock", "侵食洞窟岩"),
}

# CB01 cobbled main rocks (blocks come from gen_deep_assets.COBBLED): shapes like SIMPLE_STONES, smelt back to the rock
COBBLED_ROCKS = {"deep_sea_rock": ("Deep Sea Rock", "深海岩"), "abyssal_rock": ("Abyssal Rock", "深淵岩"),
                 "trench_rock": ("Trench Rock", "海溝岩"), "thermal_rock": ("Thermal Rock", "熱水岩"),
                 "volcanic_rock": ("Volcanic Rock", "火山岩"), "crystal_rock": ("Crystal Rock", "結晶岩"),
                 "mineral_host_rock": ("Mineral Host Rock", "鉱物母岩")}
for _r, (_en, _ja) in COBBLED_ROCKS.items():
    SIMPLE_STONES["cobbled_" + _r] = (f"Cobbled {_en}", f"{_ja}の丸石")

# The ancient deep-sea plant's wood (colours shared with forge_textures.py)
WOOD = {"name": "ancient", "wood": "#627c69", "bark": "#2c5c62", "fittings": "copper", "en": "Ancient",
        "ja": "古代植物"}

W = WOOD["name"]
STEM, STRIPPED_STEM = f"{W}_stem", f"stripped_{W}_stem"
WOOD_BLOCK, STRIPPED_WOOD = f"{W}_wood", f"stripped_{W}_wood"
PLANKS = f"{W}_planks"


def shape_names(stem):
    return f"{stem}_stairs", f"{stem}_slab", f"{stem}_wall"


# ================================================================ names

def _names():
    n = {}
    for f in STONE_FAMILIES:
        n[f.polished] = (f"Polished {f.en}", f"磨かれた{f.ja}")
        n[f.bricks] = (f"{f.en} Bricks", f"{f.ja}レンガ")
        n[f.cracked_bricks] = (f"Cracked {f.en} Bricks", f"ひび割れた{f.ja}レンガ")
        n[f.chiseled] = (f"Chiseled {f.en}", f"模様入りの{f.ja}")
        for stem, en, ja in ((f.rock, f.en, f.ja), (f.polished, f"Polished {f.en}", f"磨かれた{f.ja}"),
                             (f.brick, f"{f.en} Brick", f"{f.ja}レンガ")):
            st, sl, wa = shape_names(stem)
            n[st], n[sl], n[wa] = (f"{en} Stairs", f"{ja}の階段"), (f"{en} Slab", f"{ja}のハーフブロック"), (f"{en} Wall", f"{ja}の塀")
    for rock, (en, ja) in SIMPLE_STONES.items():
        st, sl, wa = shape_names(rock)
        n[st], n[sl], n[wa] = (f"{en} Stairs", f"{ja}の階段"), (f"{en} Slab", f"{ja}のハーフブロック"), (f"{en} Wall", f"{ja}の塀")
    en, ja = WOOD["en"], WOOD["ja"]
    n.update({
        STRIPPED_STEM: (f"Stripped {en} Stem", f"樹皮を剥いだ{ja}の幹"),
        WOOD_BLOCK: (f"{en} Wood", f"{ja}の木"),
        STRIPPED_WOOD: (f"Stripped {en} Wood", f"樹皮を剥いだ{ja}の木"),
        PLANKS: (f"{en} Planks", f"{ja}の板材"),
        f"{W}_stairs": (f"{en} Stairs", f"{ja}の階段"),
        f"{W}_slab": (f"{en} Slab", f"{ja}のハーフブロック"),
        f"{W}_fence": (f"{en} Fence", f"{ja}のフェンス"),
        f"{W}_fence_gate": (f"{en} Fence Gate", f"{ja}のフェンスゲート"),
        f"{W}_door": (f"{en} Door", f"{ja}のドア"),
        f"{W}_trapdoor": (f"{en} Trapdoor", f"{ja}のトラップドア"),
        f"{W}_pressure_plate": (f"{en} Pressure Plate", f"{ja}の感圧板"),
        f"{W}_button": (f"{en} Button", f"{ja}のボタン"),
    })
    return n


NAMES = _names()


# ================================================================ block lists (for tags)

def stone_cubes():
    out = []
    for f in STONE_FAMILIES:
        out += [f.polished, f.bricks, f.cracked_bricks, f.chiseled]
    return out


def stone_shapes():
    """(stairs, slabs, walls) of every stone shape."""
    stairs, slabs, walls = [], [], []
    stems = [s for f in STONE_FAMILIES for s in (f.rock, f.polished, f.brick)] + list(SIMPLE_STONES)
    for stem in stems:
        st, sl, wa = shape_names(stem)
        stairs.append(st)
        slabs.append(sl)
        walls.append(wa)
    return stairs, slabs, walls


def pickaxe_blocks():
    stairs, slabs, walls = stone_shapes()
    return stone_cubes() + stairs + slabs + walls


def axe_blocks():
    return [STRIPPED_STEM, WOOD_BLOCK, STRIPPED_WOOD, PLANKS] + [f"{W}_{s}" for s in (
        "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "pressure_plate", "button")]


# ================================================================ JSON helpers

def rl(name, kind="block"):
    return f"{MOD}:{kind}/{name}"


def _rot(y=0, x=0, uvlock=False):
    out = {}
    if x % 360:
        out["x"] = x % 360
    if y % 360:
        out["y"] = y % 360
    if uvlock and out:
        out["uvlock"] = True
    return out


# facing -> y rotation of the model that faces east / north (vanilla conventions)
EAST0 = {"east": 0, "south": 90, "west": 180, "north": 270}
NORTH0 = {"north": 0, "east": 90, "south": 180, "west": 270}
SOUTH0 = {"south": 0, "west": 90, "north": 180, "east": 270}


def pillar_blockstate(model, horizontal):
    return {"variants": {"axis=x": {"model": horizontal, "x": 90, "y": 90}, "axis=y": {"model": model},
                         "axis=z": {"model": horizontal, "x": 90}}}


def stairs_blockstate(model, inner, outer):
    v = {}
    for facing, r in EAST0.items():
        for half in ("bottom", "top"):
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                m = inner if shape.startswith("inner") else outer if shape.startswith("outer") else model
                left = shape.endswith("left")
                if half == "bottom":
                    rot = _rot(r - 90 if left else r, 0, True)
                else:
                    rot = _rot(r + 90 if shape.endswith("right") else r, 180, True)
                v[f"facing={facing},half={half},shape={shape}"] = {"model": m, **rot}
    return {"variants": v}


def slab_blockstate(bottom, top, double):
    return {"variants": {"type=bottom": {"model": bottom}, "type=double": {"model": double}, "type=top": {"model": top}}}


def wall_blockstate(post, side, tall):
    parts = [{"apply": {"model": post}, "when": {"up": "true"}}]
    for model, value in ((side, "low"), (tall, "tall")):
        for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            parts.append({"apply": {"model": model, **({"y": y} if y else {}), "uvlock": True}, "when": {d: value}})
    return {"multipart": parts}


def fence_blockstate(post, side):
    parts = [{"apply": {"model": post}}]
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        parts.append({"apply": {"model": side, **({"y": y} if y else {}), "uvlock": True}, "when": {d: "true"}})
    return {"multipart": parts}


def fence_gate_blockstate(base):
    v = {}
    for facing, r in SOUTH0.items():
        for in_wall in ("false", "true"):
            for open_ in ("false", "true"):
                m = base + ("_wall" if in_wall == "true" else "") + ("_open" if open_ == "true" else "")
                v[f"facing={facing},in_wall={in_wall},open={open_}"] = {"model": m, **_rot(r), "uvlock": True}
    return {"variants": v}


def door_blockstate(base):
    v = {}
    for facing, r in EAST0.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for open_ in ("false", "true"):
                    m = f"{base}_{part}_{hinge}" + ("_open" if open_ == "true" else "")
                    y = r if open_ == "false" else (r + 90 if hinge == "left" else r + 270)
                    v[f"facing={facing},half={half},hinge={hinge},open={open_}"] = {"model": m, **_rot(y)}
    return {"variants": v}


def trapdoor_blockstate(base):
    v = {}
    for facing, r in NORTH0.items():
        for half in ("bottom", "top"):
            for open_ in ("false", "true"):
                if open_ == "true":
                    rot = _rot(r + 180, 180) if half == "top" else _rot(r)
                    m = base + "_open"
                else:
                    rot = _rot(r)
                    m = f"{base}_{half}"
                v[f"facing={facing},half={half},open={open_}"] = {"model": m, **rot}
    return {"variants": v}


def pressure_plate_blockstate(up, down):
    return {"variants": {"powered=false": {"model": up}, "powered=true": {"model": down}}}


def button_blockstate(base, pressed):
    v = {}
    for face in ("ceiling", "floor", "wall"):
        for facing, r in NORTH0.items():
            for powered in ("false", "true"):
                m = pressed if powered == "true" else base
                if face == "floor":
                    rot = _rot(r)
                elif face == "wall":
                    rot = _rot(r, 90, True)
                else:
                    rot = _rot(r + 180, 180)
                v[f"face={face},facing={facing},powered={powered}"] = {"model": m, **rot}
    return {"variants": v}


# ================================================================ generate

def generate(write, bs, bm, im, data_dir):
    """Writes blockstates, models, item models, loot tables, recipes and tags of every building block."""
    ref = rl
    cube = lambda tex: {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}

    def cube_block(name, tex=None):
        write(bs(name), {"variants": {"": {"model": ref(name)}}})
        write(bm(name), cube(ref(tex or name)))
        write(im(name), {"parent": ref(name)})

    def shapes(stem, tex, full_model):
        """Stairs, slab and wall of ``stem`` textured with ``tex``; ``full_model`` is the double slab."""
        st, sl, wa = shape_names(stem)
        t = ref(tex)
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            write(bm(st + suffix), {"parent": f"minecraft:block/{parent}", "textures": {"bottom": t, "top": t, "side": t}})
        write(bs(st), stairs_blockstate(ref(st), ref(st + "_inner"), ref(st + "_outer")))
        write(im(st), {"parent": ref(st)})
        write(bm(sl), {"parent": "minecraft:block/slab", "textures": {"bottom": t, "top": t, "side": t}})
        write(bm(sl + "_top"), {"parent": "minecraft:block/slab_top", "textures": {"bottom": t, "top": t, "side": t}})
        write(bs(sl), slab_blockstate(ref(sl), ref(sl + "_top"), full_model))
        write(im(sl), {"parent": ref(sl)})
        for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                               ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
            write(bm(wa + suffix), {"parent": f"minecraft:block/{parent}", "textures": {"wall": t}})
        write(bs(wa), wall_blockstate(ref(wa + "_post"), ref(wa + "_side"), ref(wa + "_side_tall")))
        write(im(wa), {"parent": ref(wa + "_inventory")})

    for f in STONE_FAMILIES:
        for name in (f.polished, f.bricks, f.cracked_bricks, f.chiseled):
            cube_block(name)
        shapes(f.rock, f.rock, ref(f.rock))
        shapes(f.polished, f.polished, ref(f.polished))
        shapes(f.brick, f.bricks, ref(f.bricks))
    for rock in SIMPLE_STONES:
        shapes(rock, rock, ref(rock))

    # ---- wood
    def log(name, side, end):
        write(bm(name), {"parent": "minecraft:block/cube_column", "textures": {"end": ref(end), "side": ref(side)}})
        write(bm(name + "_horizontal"), {"parent": "minecraft:block/cube_column_horizontal",
                                         "textures": {"end": ref(end), "side": ref(side)}})
        write(bs(name), pillar_blockstate(ref(name), ref(name + "_horizontal")))
        write(im(name), {"parent": ref(name)})

    log(STEM, STEM, STEM + "_top")
    log(STRIPPED_STEM, STRIPPED_STEM, STRIPPED_STEM + "_top")
    log(WOOD_BLOCK, STEM, STEM)
    log(STRIPPED_WOOD, STRIPPED_STEM, STRIPPED_STEM)
    cube_block(PLANKS)
    p = ref(PLANKS)
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(bm(f"{W}_stairs{suffix}"), {"parent": f"minecraft:block/{parent}", "textures": {"bottom": p, "top": p, "side": p}})
    write(bs(f"{W}_stairs"), stairs_blockstate(ref(f"{W}_stairs"), ref(f"{W}_stairs_inner"), ref(f"{W}_stairs_outer")))
    write(im(f"{W}_stairs"), {"parent": ref(f"{W}_stairs")})
    write(bm(f"{W}_slab"), {"parent": "minecraft:block/slab", "textures": {"bottom": p, "top": p, "side": p}})
    write(bm(f"{W}_slab_top"), {"parent": "minecraft:block/slab_top", "textures": {"bottom": p, "top": p, "side": p}})
    write(bs(f"{W}_slab"), slab_blockstate(ref(f"{W}_slab"), ref(f"{W}_slab_top"), p))
    write(im(f"{W}_slab"), {"parent": ref(f"{W}_slab")})
    fence = f"{W}_fence"
    for suffix, parent in (("_post", "fence_post"), ("_side", "fence_side"), ("_inventory", "fence_inventory")):
        write(bm(fence + suffix), {"parent": f"minecraft:block/{parent}", "textures": {"texture": p}})
    write(bs(fence), fence_blockstate(ref(fence + "_post"), ref(fence + "_side")))
    write(im(fence), {"parent": ref(fence + "_inventory")})
    gate = f"{W}_fence_gate"
    for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"),
                           ("_wall", "template_fence_gate_wall"), ("_wall_open", "template_fence_gate_wall_open")):
        write(bm(gate + suffix), {"parent": f"minecraft:block/{parent}", "textures": {"texture": p}})
    write(bs(gate), fence_gate_blockstate(ref(gate)))
    write(im(gate), {"parent": ref(gate)})
    door = f"{W}_door"
    for part in ("bottom", "top"):
        for hinge in ("left", "right"):
            for op in ("", "_open"):
                write(bm(f"{door}_{part}_{hinge}{op}"), {
                    "parent": f"minecraft:block/door_{part}_{hinge}{op}", "render_type": "minecraft:cutout",
                    "textures": {"bottom": ref(door + "_bottom"), "top": ref(door + "_top")}})
    write(bs(door), door_blockstate(ref(door)))
    write(im(door), {"parent": "minecraft:item/generated", "textures": {"layer0": rl(door, "item")}})
    trap = f"{W}_trapdoor"
    for suffix, parent in (("_bottom", "template_orientable_trapdoor_bottom"),
                           ("_top", "template_orientable_trapdoor_top"), ("_open", "template_orientable_trapdoor_open")):
        write(bm(trap + suffix), {"parent": f"minecraft:block/{parent}", "render_type": "minecraft:cutout",
                                  "textures": {"texture": ref(trap)}})
    write(bs(trap), trapdoor_blockstate(ref(trap)))
    write(im(trap), {"parent": ref(trap + "_bottom")})
    plate = f"{W}_pressure_plate"
    write(bm(plate), {"parent": "minecraft:block/pressure_plate_up", "textures": {"texture": p}})
    write(bm(plate + "_down"), {"parent": "minecraft:block/pressure_plate_down", "textures": {"texture": p}})
    write(bs(plate), pressure_plate_blockstate(ref(plate), ref(plate + "_down")))
    write(im(plate), {"parent": ref(plate)})
    button = f"{W}_button"
    for suffix, parent in (("", "button"), ("_pressed", "button_pressed"), ("_inventory", "button_inventory")):
        write(bm(button + suffix), {"parent": f"minecraft:block/{parent}", "textures": {"texture": p}})
    write(bs(button), button_blockstate(ref(button), ref(button + "_pressed")))
    write(im(button), {"parent": ref(button + "_inventory")})

    loot_tables(write, data_dir)
    recipes(write, data_dir)
    tags(write, data_dir)


# ================================================================ loot tables

def _loot(name, pool):
    return {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{MOD}:blocks/{name}"}


def loot_tables(write, data_dir):
    lt = lambda n: os.path.join(data_dir, MOD, "loot_tables", "blocks", n + ".json")
    survives = [{"condition": "minecraft:survives_explosion"}]
    stairs, slabs, walls = stone_shapes()
    selfdrop = stone_cubes() + stairs + walls + [n for n in axe_blocks() if not n.endswith(("_slab", "_door"))]
    for name in selfdrop:
        write(lt(name), _loot(name, {"rolls": 1, "bonus_rolls": 0, "conditions": survives,
                                     "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}]}))
    for name in slabs + [f"{W}_slab"]:
        write(lt(name), _loot(name, {"rolls": 1, "bonus_rolls": 0, "entries": [{
            "type": "minecraft:item", "name": f"{MOD}:{name}",
            "functions": [{"function": "minecraft:set_count", "add": False, "count": 2, "conditions": [{
                "condition": "minecraft:block_state_property", "block": f"{MOD}:{name}",
                "properties": {"type": "double"}}]}, {"function": "minecraft:explosion_decay"}]}]}))
    door = f"{W}_door"
    write(lt(door), _loot(door, {"rolls": 1, "bonus_rolls": 0, "entries": [{
        "type": "minecraft:item", "name": f"{MOD}:{door}", "conditions": [{
            "condition": "minecraft:block_state_property", "block": f"{MOD}:{door}", "properties": {"half": "lower"}}]}],
        "conditions": survives}))


# ================================================================ recipes

def recipes(write, data_dir):
    rd = lambda n: os.path.join(data_dir, MOD, "recipes", n + ".json")
    item = lambda n: {"item": f"{MOD}:{n}"}

    def shaped(out, name, pattern, key, count=1, category="building"):
        write(rd(name), {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                         "key": key, "result": {"item": f"{MOD}:{out}", "count": count}})

    def cutting(src, out, count=1):
        write(rd(f"{out}_from_{src}_stonecutting"), {"type": "minecraft:stonecutting", "ingredient": item(src),
                                                     "result": f"{MOD}:{out}", "count": count})

    def shape_recipes(stem, material, sources):
        st, sl, wa = shape_names(stem)
        shaped(st, st, ["#  ", "## ", "###"], {"#": item(material)}, 4)
        shaped(sl, sl, ["###"], {"#": item(material)}, 6)
        shaped(wa, wa, ["###", "###"], {"#": item(material)}, 6, "misc")
        for src in sources:
            cutting(src, st)
            cutting(src, sl, 2)
            cutting(src, wa)

    for f in STONE_FAMILIES:
        shaped(f.polished, f.polished, ["##", "##"], {"#": item(f.rock)}, 4)
        shaped(f.bricks, f.bricks, ["##", "##"], {"#": item(f.polished)}, 4)
        shaped(f.chiseled, f.chiseled, ["#", "#"], {"#": item(shape_names(f.brick)[1])})
        cutting(f.rock, f.polished)
        cutting(f.rock, f.bricks)
        cutting(f.polished, f.bricks)
        for src in (f.rock, f.polished, f.bricks):
            cutting(src, f.chiseled)
        write(rd(f"{f.cracked_bricks}_from_smelting"), {
            "type": "minecraft:smelting", "category": "blocks", "ingredient": item(f.bricks),
            "result": f"{MOD}:{f.cracked_bricks}", "experience": 0.1, "cookingtime": 200})
        shape_recipes(f.rock, f.rock, [f.rock])
        shape_recipes(f.polished, f.polished, [f.rock, f.polished])
        shape_recipes(f.brick, f.bricks, [f.rock, f.polished, f.bricks])
    for rock in SIMPLE_STONES:
        shape_recipes(rock, rock, [rock])
    for rock in COBBLED_ROCKS:
        write(rd(f"{rock}_from_smelting_cobbled_{rock}"), {
            "type": "minecraft:smelting", "category": "blocks", "ingredient": item(f"cobbled_{rock}"),
            "result": f"{MOD}:{rock}", "experience": 0.1, "cookingtime": 200})

    # ---- wood
    stems = {"tag": f"{MOD}:{W}_stems"}
    write(rd(PLANKS), {"type": "minecraft:crafting_shapeless", "category": "building", "group": "planks",
                       "ingredients": [stems], "result": {"item": f"{MOD}:{PLANKS}", "count": 4}})
    shaped(WOOD_BLOCK, WOOD_BLOCK, ["##", "##"], {"#": item(STEM)}, 3)
    shaped(STRIPPED_WOOD, STRIPPED_WOOD, ["##", "##"], {"#": item(STRIPPED_STEM)}, 3)
    pk = {"#": item(PLANKS)}
    stick = {"tag": "forge:rods/wooden"}
    shaped(f"{W}_stairs", f"{W}_stairs", ["#  ", "## ", "###"], pk, 4)
    shaped(f"{W}_slab", f"{W}_slab", ["###"], pk, 6)
    shaped(f"{W}_fence", f"{W}_fence", ["W#W", "W#W"], {"W": item(PLANKS), "#": stick}, 3, "misc")
    shaped(f"{W}_fence_gate", f"{W}_fence_gate", ["#W#", "#W#"], {"W": item(PLANKS), "#": stick}, 1, "redstone")
    shaped(f"{W}_door", f"{W}_door", ["##", "##", "##"], pk, 3, "redstone")
    shaped(f"{W}_trapdoor", f"{W}_trapdoor", ["###", "###"], pk, 2, "redstone")
    shaped(f"{W}_pressure_plate", f"{W}_pressure_plate", ["##"], pk, 1, "redstone")
    write(rd(f"{W}_button"), {"type": "minecraft:crafting_shapeless", "category": "redstone",
                              "ingredients": [item(PLANKS)], "result": {"item": f"{MOD}:{W}_button"}})


# ================================================================ tags

def tags(write, data_dir):
    a = lambda names: [f"{MOD}:{n}" for n in names]
    mc_blocks = os.path.join(data_dir, "minecraft", "tags", "blocks")
    mc_items = os.path.join(data_dir, "minecraft", "tags", "items")
    ours_items = os.path.join(data_dir, MOD, "tags", "items")
    ours_blocks = os.path.join(data_dir, MOD, "tags", "blocks")
    stairs, slabs, walls = stone_shapes()
    stems = [STEM, STRIPPED_STEM, WOOD_BLOCK, STRIPPED_WOOD]
    shared = {
        "stairs": stairs + [f"{W}_stairs"], "slabs": slabs + [f"{W}_slab"], "walls": walls,
        "wooden_stairs": [f"{W}_stairs"], "wooden_slabs": [f"{W}_slab"], "wooden_fences": [f"{W}_fence"],
        "fence_gates": [f"{W}_fence_gate"], "wooden_doors": [f"{W}_door"], "wooden_trapdoors": [f"{W}_trapdoor"],
        "wooden_buttons": [f"{W}_button"], "wooden_pressure_plates": [f"{W}_pressure_plate"], "planks": [PLANKS],
        "logs": stems,
    }
    for tag, names in shared.items():
        write(os.path.join(mc_blocks, tag + ".json"), {"replace": False, "values": a(names)})
        write(os.path.join(mc_items, tag + ".json"), {"replace": False, "values": a(names)})
    # CB01: cobbled rocks stand in for cobblestone (vanilla stone tools / furnace / etc. and the common cobblestone tags)
    cobbled = a(f"cobbled_{r}" for r in COBBLED_ROCKS)
    for ns, tag in (("minecraft", "stone_tool_materials"), ("minecraft", "stone_crafting_materials"),
                    ("forge", "cobblestone"), ("c", "cobblestone")):
        write(os.path.join(data_dir, ns, "tags", "items", tag + ".json"), {"replace": False, "values": cobbled})
    for ns in ("forge", "c"):
        write(os.path.join(data_dir, ns, "tags", "blocks", "cobblestone.json"), {"replace": False, "values": cobbled})
    write(os.path.join(ours_blocks, f"{W}_stems.json"), {"values": a(stems)})
    write(os.path.join(ours_items, f"{W}_stems.json"), {"values": a(stems)})
