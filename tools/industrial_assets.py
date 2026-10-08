"""Industrial blocks (feature I01, spec inbox/specs/I01-industrial-blocks.md): panel / grating / beam / pipe / valve /
lights, the energy cables, three machines, two generators, the high-temperature furnace and the energy device.

Like building_assets.py this is data plus the JSON writers (blockstates, block + item models, loot tables, recipes,
tags, names); gen_deep_assets.main() calls generate().  The Java side is com.abyssia.industry + registry/ModIndustry.

Models: tools/industrial_models/<name>.json (exported from Blockbench) is copied to models/block/<name>.json when it
exists, otherwise a fallback (box geometry) is written.  The machine variants <id>_on and energy_device_c0..c3 are
always generated here (parent + front texture swap); energy_device_front_c1 / _c2 are blended from front and front_on.
Textures other than those two derived PNGs are never written here.
"""
import os
import shutil

from PIL import Image

import building_assets as ba

MOD = "abyssia"
HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_SRC = os.path.join(HERE, "industrial_models")

# Blocks whose loot table is empty even with silk touch (read by gen_deep_assets.loot_tables).
NO_DROP = ["thermal_vent"]

# ================================================================ data

PANEL = "industrial_panel"
GRATING = "metal_grating"
BEAM = "industrial_beam"
PIPE = "industrial_pipe"
VALVE = "industrial_valve"
WORK_LIGHT = "work_light"
WARNING_LIGHT = "warning_light"
CABLE = "energy_cable"
REINFORCED = "reinforced_energy_cable"
ENERGY = "energy_device"
BEACON = "waypoint_beacon"      # W01 (inbox/specs/W01-waypoint-beacon.md): work light model, tinted lamp

PANEL_STAIRS, PANEL_SLAB, PANEL_WALL = ba.shape_names(PANEL)
GRATING_SLAB = f"{GRATING}_slab"

# machines / generators with a lit property (<id>_on model); id -> (English, Japanese)
LIT_MACHINES = {
    "crusher": ("Crusher", "粉砕機"),
    "refinery_furnace": ("Refinery Furnace", "精錬炉"),
    "alloy_furnace": ("Alloy Furnace", "合金炉"),
    "hydrothermal_generator": ("Hydrothermal Generator Mk1", "熱水発電機 Mk1"),
    "auxiliary_generator": ("Auxiliary Generator Mk1", "補助発電機 Mk1"),
    "high_temp_furnace": ("High-Temperature Furnace", "高温炉"),
    "selective_leaching_separator": ("Selective Leaching Separator", "選択浸出分離機"),     # I02
}
# ORE01 abyssal excavator Mk1 / Mk2: multiblock drawn by industry/client/ExcavatorRenderer; blockstates + chunk models come from
# tools/bt01/excavator_assets.py; built with the habitat constructor (no item)
EXCAVATORS = {
    "abyssal_excavator": ("Abyssal Excavator Mk1", "深海採掘機 Mk1"),
    "abyssal_excavator_mk2": ("Abyssal Excavator Mk2", "深海採掘機 Mk2"),
}
# its invisible collision cells (lang: tools/lang_parts/ore01.json; blockstate: excavator_assets.py): no loot, pickaxe, stone tool
EXCAVATOR_PART = "excavator_part"
# I02 item (registered in ModIndustry); lang key item.abyssia.<id>
LEACHING_REAGENT = "acidic_leaching_reagent"
LEACHING_REAGENT_NAME = ("Acidic Leaching Reagent", "酸性浸出試薬")
ITEM_NAMES = {LEACHING_REAGENT: LEACHING_REAGENT_NAME}   # merged into gen_deep_assets.ITEM_NAMES
ENERGY_NAME = ("Energy Device", "エネルギー装置")
MACHINES = {**LIT_MACHINES, ENERGY: ENERGY_NAME}      # everything with a GUI (container.abyssia.<id>)

# (thickness of the cross-section in 16 units: low, high)
PIPE_PARTS = {PIPE: (4, 12), CABLE: (6, 10), REINFORCED: (5, 11)}

NAMES = {
    PANEL: ("Industrial Panel", "工業用金属パネル"),
    PANEL_STAIRS: ("Industrial Panel Stairs", "工業用金属パネルの階段"),
    PANEL_SLAB: ("Industrial Panel Slab", "工業用金属パネルのハーフブロック"),
    PANEL_WALL: ("Industrial Panel Wall", "工業用金属パネルの塀"),
    GRATING: ("Metal Grating", "金属グレーチング"),
    GRATING_SLAB: ("Metal Grating Slab", "金属グレーチングのハーフブロック"),
    BEAM: ("Industrial Beam", "工業用梁"),
    PIPE: ("Industrial Pipe", "工業用配管"),
    VALVE: ("Industrial Valve", "工業用バルブ"),
    WORK_LIGHT: ("Work Light", "作業灯"),
    WARNING_LIGHT: ("Warning Light", "警告灯"),
    BEACON: ("Waypoint Beacon", "迷子対策ビーコン"),
    CABLE: ("Energy Cable", "エネルギーケーブル"),
    REINFORCED: ("Reinforced Energy Cable", "強化エネルギーケーブル"),
    **MACHINES,
    **EXCAVATORS,
}

# GUI title keys (container.abyssia.<id>); same names as the blocks
CONTAINER_NAMES = {f"container.{MOD}.{k}": v for k, v in MACHINES.items()}
# GUI text (industry/client/IndustryScreen); merged into the lang files with the container titles
CONTAINER_NAMES.update({
    f"gui.{MOD}.energy": ("%s / %s FE", "%s / %s FE"),
    f"gui.{MOD}.output": ("Output: %s FE/t", "出力: %s FE/t"),
    f"gui.{MOD}.no_vent": ("No thermal vent adjacent", "隣に熱水噴出孔がない"),
    f"gui.{MOD}.vent": ("Vent: %s", "噴出孔: %s"),
    f"gui.{MOD}.vent.dormant": ("Dormant", "休止"),
    f"gui.{MOD}.vent.weak": ("Weak", "弱い"),
    f"gui.{MOD}.vent.active": ("Active", "活発"),
    f"gui.{MOD}.vent.strong": ("Strong", "強い"),
    f"gui.{MOD}.vent.superheated": ("Superheated", "超高温"),
    f"gui.{MOD}.no_heat": ("No active vent nearby", "近くに活動中の噴出孔がない"),
    f"gui.{MOD}.no_reagent": ("Needs Acidic Leaching Reagent", "酸性浸出試薬が必要"),
    f"gui.{MOD}.rare_full": ("Rare output slots full", "レア出力欄がいっぱい"),
    f"gui.{MOD}.water_bonus": ("Water cooled: 10% faster", "水冷中: 10% 高速"),
    # JEI machine categories (compat/jei/MachineCategory)
    "jei.abyssia.needs_vent": ("Needs an active vent within 6 blocks", "6ブロック以内に活動中の噴出孔が必要"),
    "jei.abyssia.process": ("%s s · %s FE", "%s 秒・%s FE"),
    "jei.abyssia.chance": ("%s%% chance", "確率 %s%%"),
    # JEI machine categories (com.abyssia.compat.jei.MachineCategory)
    f"jei.{MOD}.process": ("%s s · %s FE", "%s 秒・%s FE"),
    f"jei.{MOD}.needs_vent": ("Needs an active vent within 6 blocks", "6ブロック以内に活動中の噴出孔が必要"),
})

# W01 waypoint beacon: preset colours in palette order (index = blockstate color, same order as
# com.abyssia.waypoint.WaypointColors) -> (English, Japanese); the settings screen text
BEACON_COLORS = [
    ("white", "White", "白"), ("light_gray", "Light Gray", "薄灰"), ("gray", "Gray", "灰"), ("black", "Black", "黒"),
    ("red", "Red", "赤"), ("orange", "Orange", "橙"), ("yellow", "Yellow", "黄"), ("green", "Green", "緑"),
    ("light_blue", "Light Blue", "水色"), ("cyan", "Cyan", "シアン"), ("blue", "Blue", "青"), ("purple", "Purple", "紫"),
    ("pink", "Pink", "桃"), ("brown", "Brown", "茶"), ("deep_blue", "Deep Sea Blue", "深海青"),
    ("deep_purple", "Deep Sea Purple", "深海紫"),
]
CONTAINER_NAMES.update({f"gui.{MOD}.waypoint.color.{c}": (en, ja) for c, en, ja in BEACON_COLORS})
CONTAINER_NAMES.update({
    f"gui.{MOD}.waypoint.name": ("Name", "名前"),
    f"gui.{MOD}.waypoint.colour": ("Colour", "色"),
    f"gui.{MOD}.waypoint.current": ("Current colour: %s", "現在の色: %s"),
    f"gui.{MOD}.waypoint.save": ("Save", "保存"),
    f"gui.{MOD}.waypoint.cancel": ("Cancel", "キャンセル"),
})

ALL_BLOCKS = list(NAMES)


def pickaxe_blocks():
    return list(ALL_BLOCKS) + [EXCAVATOR_PART]


def needs_stone_tool_blocks():
    return list(MACHINES) + list(EXCAVATORS) + [EXCAVATOR_PART]


def block_names():
    return {f"block.{MOD}.{k}": v for k, v in NAMES.items()}


# ================================================================ helpers

def rl(name, kind="block"):
    return f"{MOD}:{kind}/{name}"


CUTOUT = "minecraft:cutout"
FACES = ("north", "south", "east", "west", "up", "down")


def _box(frm, to, uvs, tex="#t", cull=False):
    """One element; ``uvs`` is {face: [u0, v0, u1, v1]} (faces left out are not drawn)."""
    faces = {}
    for f, uv in uvs.items():
        faces[f] = {"uv": uv, "texture": tex, **({"cullface": f} if cull else {})}
    return {"from": list(frm), "to": list(to), "faces": faces}


def _uv_box(frm, to):
    """Per-face uv of a box taken from the texture region it covers (all six faces)."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {"north": [x0, 16 - y1, x1, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "east": [z0, 16 - y1, z1, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, z0, x1, z1]}


def _simple_model(frm, to, texture, faces=FACES):
    uv = {f: v for f, v in _uv_box(frm, to).items() if f in faces}
    return {"parent": "minecraft:block/block", "render_type": CUTOUT, "textures": {"t": texture, "particle": texture},
            "elements": [_box(frm, to, uv)]}


def _fallback_machine(machine):
    full = [0, 0, 0], [16, 16, 16]
    u = [0, 0, 16, 16]
    faces = {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#bottom"}
    return {"parent": "minecraft:block/block",
            "textures": {"front": rl(f"{machine}_front"), "side": rl(f"{machine}_side"),
                         "top": rl(f"{machine}_top"), "bottom": rl("machine_bottom"), "particle": "#side"},
            "elements": [{"from": full[0], "to": full[1],
                          "faces": {f: {"uv": u, "texture": t, "cullface": f} for f, t in faces.items()}}]}


def _fallback_models(name):
    """Fallback (box) model of one of the spec's model names."""
    for block, (lo, hi) in PIPE_PARTS.items():
        tex = rl(block)
        if name == f"{block}_core":
            return _simple_model([lo] * 3, [hi] * 3, tex)
        if name == f"{block}_arm":      # from the core face to the north edge
            return _simple_model([lo, lo, 0], [hi, hi, lo], tex, faces=("north", "east", "west", "up", "down"))
    if name == VALVE:
        return _simple_model([3, 3, 0], [13, 13, 16], rl(VALVE))
    if name in (WORK_LIGHT, WARNING_LIGHT):
        return _simple_model([4, 0, 4], [12, 6, 12], rl(name))
    if name in MACHINES:
        return _fallback_machine(name)
    raise KeyError(name)


# every model name of the spec's model section
def model_names():
    out = []
    for block in PIPE_PARTS:
        out += [f"{block}_core", f"{block}_arm"]
    return out + [VALVE, WORK_LIGHT, WARNING_LIGHT] + list(MACHINES)


# ================================================================ blockstates

def _rot(y=0, x=0):
    out = {}
    if x % 360:
        out["x"] = x % 360
    if y % 360:
        out["y"] = y % 360
    return out


# direction -> rotation of a model that points north
DIR_ROT = {"north": _rot(), "east": _rot(90), "south": _rot(180), "west": _rot(270),
           "up": _rot(x=270), "down": _rot(x=90)}
# facing of a light (away from the attached surface) -> rotation of the floor-mounted model (facing up)
LIGHT_ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
             "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}


def _facing_variants(model, directions):
    return {f"facing={d}": {"model": model, **r} for d, r in directions.items()}


def _pipe_blockstate(block):
    parts = [{"apply": {"model": rl(f"{block}_core")}}]
    for d in FACES:
        parts.append({"apply": {"model": rl(f"{block}_arm"), **DIR_ROT[d]}, "when": {d: "true"}})
    return {"multipart": parts}


def _machine_blockstate(machine):
    v = {}
    for facing, y in ba.NORTH0.items():
        for lit in ("false", "true"):
            v[f"facing={facing},lit={lit}"] = {"model": rl(machine + ("_on" if lit == "true" else "")), **_rot(y)}
    return {"variants": v}


def _machine_blockstate_single(model):
    """facing / lit variants that all use one model (no _on model)."""
    return {"variants": {f"facing={facing},lit={lit}": {"model": rl(model)}
                         for facing in ba.NORTH0 for lit in ("false", "true")}}


def _energy_blockstate():
    v = {}
    for facing, y in ba.NORTH0.items():
        for c in range(4):
            v[f"facing={facing},charge={c}"] = {"model": rl(f"{ENERGY}_c{c}"), **_rot(y)}
    return {"variants": v}


# ================================================================ derived textures

def derive_energy_textures(assets_dir):
    """energy_device_front_c1 / _c2 = front blended 1/3 and 2/3 towards front_on (only if both sources exist)."""
    tex = os.path.join(assets_dir, "textures", "block")
    a, b = (os.path.join(tex, f"{ENERGY}_front{s}.png") for s in ("", "_on"))
    if not (os.path.exists(a) and os.path.exists(b)):
        return []
    ia, ib = Image.open(a).convert("RGBA"), Image.open(b).convert("RGBA")
    if ia.size != ib.size:
        raise SystemExit(f"{ENERGY}_front and {ENERGY}_front_on differ in size: {ia.size} vs {ib.size}")
    out = []
    for n, t in ((1, 1 / 3), (2, 2 / 3)):
        path = os.path.join(tex, f"{ENERGY}_front_c{n}.png")
        Image.blend(ia, ib, t).save(path)
        out.append(path)
    return out


def derive_beacon_lamp(assets_dir):
    """waypoint_beacon_lamp = the work light texture in brightened grey, so the block colour tint shows true."""
    tex = os.path.join(assets_dir, "textures", "block")
    src = os.path.join(tex, f"{WORK_LIGHT}.png")
    if not os.path.exists(src):
        return []
    im = Image.open(src).convert("RGBA")
    out = Image.new("RGBA", im.size)
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = im.getpixel((x, y))
            v = min(255, round((0.299 * r + 0.587 * g + 0.114 * b) * 1.25))
            out.putpixel((x, y), (v, v, v, a))
    path = os.path.join(tex, f"{BEACON}_lamp.png")
    out.save(path)
    return [path]


def _beacon_model(work_light_model):
    """The work light model with the lamp body (element "light_body") on the grey lamp texture, tint index 0."""
    import copy
    model = copy.deepcopy(work_light_model)
    model["credit"] = "Abyssia W01 - work light model with a tinted lamp, built by tools/industrial_assets.py"
    model.setdefault("textures", {})["lamp"] = rl(f"{BEACON}_lamp")
    for element in model.get("elements", []):
        if element.get("name") == "light_body":
            for face in element["faces"].values():
                face["texture"] = "#lamp"
                face["tintindex"] = 0
    return model


# ================================================================ generate

def generate(write, bs, bm, im, data_dir):
    """Writes blockstates, models, item models, loot tables, recipes and tags of every industrial block."""
    assets_dir = os.path.dirname(os.path.dirname(os.path.dirname(bm("x"))))
    t_all = lambda tex: {"parent": "minecraft:block/cube_all", "render_type": CUTOUT, "textures": {"all": rl(tex)}}

    # ---- plain blocks
    write(bs(PANEL), {"variants": {"": {"model": rl(PANEL)}}})
    write(bm(PANEL), {"parent": "minecraft:block/cube_all", "textures": {"all": rl(PANEL)}})
    write(im(PANEL), {"parent": rl(PANEL)})
    write(bs(GRATING), {"variants": {"": {"model": rl(GRATING)}}})
    write(bm(GRATING), t_all(GRATING))
    write(im(GRATING), {"parent": rl(GRATING)})

    def shapes(stem, tex, full_model, cutout, stairs=True, wall=True):
        st, sl, wa = ba.shape_names(stem)
        t = rl(tex)
        extra = {"render_type": CUTOUT} if cutout else {}
        tx = {"bottom": t, "top": t, "side": t}
        if stairs:
            for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
                write(bm(st + suffix), {"parent": f"minecraft:block/{parent}", **extra, "textures": tx})
            write(bs(st), ba.stairs_blockstate(rl(st), rl(st + "_inner"), rl(st + "_outer")))
            write(im(st), {"parent": rl(st)})
        write(bm(sl), {"parent": "minecraft:block/slab", **extra, "textures": tx})
        write(bm(sl + "_top"), {"parent": "minecraft:block/slab_top", **extra, "textures": tx})
        write(bs(sl), ba.slab_blockstate(rl(sl), rl(sl + "_top"), full_model))
        write(im(sl), {"parent": rl(sl)})
        if wall:
            for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                                   ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
                write(bm(wa + suffix), {"parent": f"minecraft:block/{parent}", "textures": {"wall": t}})
            write(bs(wa), ba.wall_blockstate(rl(wa + "_post"), rl(wa + "_side"), rl(wa + "_side_tall")))
            write(im(wa), {"parent": rl(wa + "_inventory")})

    shapes(PANEL, PANEL, rl(PANEL), False)
    shapes(GRATING, GRATING, rl(GRATING), True, stairs=False, wall=False)

    # ---- beam (axis, like a log)
    beam_tex = {"end": rl("industrial_beam_end"), "side": rl("industrial_beam_side")}
    write(bm(BEAM), {"parent": "minecraft:block/cube_column", "textures": beam_tex})
    write(bm(BEAM + "_horizontal"), {"parent": "minecraft:block/cube_column_horizontal", "textures": beam_tex})
    write(bs(BEAM), ba.pillar_blockstate(rl(BEAM), rl(BEAM + "_horizontal")))
    write(im(BEAM), {"parent": rl(BEAM)})

    # ---- models of the spec's model section: Blockbench export if present, else the fallback
    copied, fallbacks = [], []
    for name in model_names():
        src = os.path.join(MODEL_SRC, name + ".json")
        if os.path.exists(src):
            os.makedirs(os.path.dirname(bm(name)), exist_ok=True)
            shutil.copyfile(src, bm(name))
            copied.append(name)
        else:
            write(bm(name), _fallback_models(name))
            fallbacks.append(name)
    for block in list(PIPE_PARTS) + [VALVE, WORK_LIGHT, WARNING_LIGHT] + list(MACHINES):
        inv = os.path.join(MODEL_SRC, block + "_inventory.json")
        if os.path.exists(inv):
            os.makedirs(os.path.dirname(bm(block)), exist_ok=True)
            shutil.copyfile(inv, bm(block + "_inventory"))

    def item_parent(block, default):
        has_inv = os.path.exists(os.path.join(MODEL_SRC, block + "_inventory.json"))
        return rl(block + "_inventory") if has_inv else default

    # ---- pipe / cables
    for block in PIPE_PARTS:
        write(bs(block), _pipe_blockstate(block))
        write(im(block), {"parent": item_parent(block, rl(f"{block}_core"))})

    # ---- valve, lights
    write(bs(VALVE), {"variants": _facing_variants(rl(VALVE), DIR_ROT)})
    write(im(VALVE), {"parent": item_parent(VALVE, rl(VALVE))})
    for light in (WORK_LIGHT, WARNING_LIGHT):
        write(bs(light), {"variants": _facing_variants(rl(light), LIGHT_ROT)})
        write(im(light), {"parent": item_parent(light, rl(light))})

    # ---- ORE01 excavators: blockstates / models are written by tools/bt01/excavator_assets.py (multiblock, drawn by its renderer)

    # ---- machines, generators, energy device
    for machine in LIT_MACHINES:
        write(bm(machine + "_on"), {"parent": rl(machine), "textures": {"front": rl(f"{machine}_front_on")}})
        write(bs(machine), _machine_blockstate(machine))
        write(im(machine), {"parent": item_parent(machine, rl(machine))})
    front = {0: f"{ENERGY}_front", 1: f"{ENERGY}_front_c1", 2: f"{ENERGY}_front_c2", 3: f"{ENERGY}_front_on"}
    for c, tex in front.items():
        write(bm(f"{ENERGY}_c{c}"), {"parent": rl(ENERGY), "textures": {"front": rl(tex)}})
    write(bs(ENERGY), _energy_blockstate())
    write(im(ENERGY), {"parent": item_parent(ENERGY, rl(f"{ENERGY}_c0"))})
    derived = derive_energy_textures(assets_dir)

    # ---- W01 waypoint beacon: work light geometry, lamp tinted by the block's COLOR (BlockColor / ItemColor)
    import json
    with open(bm(WORK_LIGHT), encoding="utf-8") as f:
        write(bm(BEACON), _beacon_model(json.load(f)))
    write(bs(BEACON), {"variants": _facing_variants(rl(BEACON), LIGHT_ROT)})
    write(im(BEACON), {"parent": rl(BEACON)})
    derived += derive_beacon_lamp(assets_dir)
    write(im(LEACHING_REAGENT), {"parent": "minecraft:item/generated",
                                 "textures": {"layer0": f"{MOD}:item/{LEACHING_REAGENT}"}})

    loot_tables(write, data_dir)
    recipes(write, data_dir)
    tags(write, data_dir)
    return {"models_copied": copied, "models_fallback": fallbacks, "derived_textures": len(derived)}


# ================================================================ loot tables

def loot_tables(write, data_dir):
    lt = lambda n: os.path.join(data_dir, MOD, "loot_tables", "blocks", n + ".json")
    survives = [{"condition": "minecraft:survives_explosion"}]
    slabs = (PANEL_SLAB, GRATING_SLAB)
    for name in ALL_BLOCKS:
        if name in EXCAVATORS:
            continue   # ORE01: built with the habitat constructor, drops nothing (no loot table, like the submarine dock)
        if name in slabs:
            pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{
                "type": "minecraft:item", "name": f"{MOD}:{name}",
                "functions": [{"function": "minecraft:set_count", "add": False, "count": 2, "conditions": [{
                    "condition": "minecraft:block_state_property", "block": f"{MOD}:{name}",
                    "properties": {"type": "double"}}]}, {"function": "minecraft:explosion_decay"}]}]}
        else:
            pool = {"rolls": 1, "bonus_rolls": 0, "conditions": survives,
                    "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}]}
        write(lt(name), {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{MOD}:blocks/{name}"})


# ================================================================ recipes

def recipes(write, data_dir):
    rd = lambda n: os.path.join(data_dir, MOD, "recipes", n + ".json")
    item = lambda n: {"item": n if ":" in n else f"{MOD}:{n}"}

    def shaped(out, pattern, key, count=1, category="building"):
        write(rd(out), {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                        # vanilla rejects key symbols that the pattern does not use
                        "key": {k: item(v) for k, v in key.items() if any(k in row for row in pattern)},
                        "result": {"item": f"{MOD}:{out}", "count": count}})

    def cutting(src, out, count=1):
        write(rd(f"{out}_from_{src}_stonecutting"), {"type": "minecraft:stonecutting", "ingredient": item(src),
                                                     "result": f"{MOD}:{out}", "count": count})

    P, R, W = "iron_plate", "iron_rod", "copper_wire"
    shaped(PANEL, ["PPP", "PPP", "PPP"], {"P": P}, 8)
    shaped(GRATING, ["RRR", "RWR", "RRR"], {"R": R, "W": W}, 8)
    shaped(BEAM, ["RR", "RR", "RR"], {"R": R}, 6)
    shaped(PIPE, ["PWP", "WRW", "PWP"], {"P": P, "W": W, "R": R}, 8, "misc")
    shaped(VALVE, [" R ", "RVR", " R "], {"R": R, "V": "pressure_valve"}, 2, "misc")
    shaped(WORK_LIGHT, ["PLP", "RWR", " R "], {"P": P, "L": "lumen_cell", "R": R, "W": W}, 2, "misc")
    shaped(WARNING_LIGHT, ["WLW", "PRP", " R "], {"P": P, "L": "lumen_cell", "R": R, "W": W}, 2, "misc")
    shaped(BEACON, ["IRI", "GWG", "ICI"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:glass",
                                           "W": WORK_LIGHT, "C": "minecraft:copper_ingot"}, 1, "misc")
    shaped(CABLE, ["WWW", "WFW", "WWW"], {"W": W, "F": "deep_fiber"}, 8, "redstone")
    shaped(REINFORCED, ["CCC", "WAW", "CCC"], {"C": "conductive_alloy_ingot", "W": W, "A": "abyssal_alloy_ingot"}, 6,
           "redstone")
    mach = {"G": "iron_gear", "F": "machine_frame", "V": "pressure_valve", "T": "thermal_component",
            "K": "conductive_component", "A": "corrosion_alloy_ingot", "P": P, "R": R, "W": W}
    shaped("crusher", ["PPP", "GFG", "RWR"], mach, 1, "misc")
    shaped("refinery_furnace", ["PPP", "VFV", "PTP"], mach, 1, "misc")
    # BAL01: the furnace that makes the alloys cannot need one (no conductive component / pressure valve / thermal
    # component): nickel ingots, thermal felt, a blast furnace and the frame stand in
    shaped("alloy_furnace", ["NTN", "WFW", "PBP"],
           {**mach, "N": "nickel_ingot", "T": "thermal_felt", "B": "minecraft:blast_furnace"}, 1, "misc")
    shaped("auxiliary_generator", ["PPP", "GFG", "WNW"], {**mach, "N": "minecraft:furnace"}, 1, "misc")
    shaped("hydrothermal_generator", ["ATA", "VFV", "PWP"], mach, 1, "misc")
    shaped("high_temp_furnace", ["HTH", "TFT", "PVP"], {**mach, "H": "heat_resistant_alloy_ingot"}, 1, "misc")
    # ORE01: the excavators have no recipe (habitat constructor build entries, industry/ExcavatorEntry)
    shaped("selective_leaching_separator", ["ATA", "VFV", "PRP"], {**mach, "R": LEACHING_REAGENT}, 1, "misc")
    write(rd(LEACHING_REAGENT), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [item("sulfur"), item("sulfur"), item("thermal_reagent"), item("minecraft:water_bucket")],
        "result": {"item": f"{MOD}:{LEACHING_REAGENT}", "count": 4}})
    shaped(ENERGY, ["CEC", "KFK", "ALA"], {**mach, "C": "conductive_alloy_ingot", "E": "abyssal_energy_cell",
                                          "A": "abyssal_alloy_ingot", "L": "lumen_cell"}, 1, "misc")

    # slab / stairs / wall: crafting + stonecutter (panel); the grating slab is crafted only
    shaped(PANEL_STAIRS, ["#  ", "## ", "###"], {"#": PANEL}, 4)
    shaped(PANEL_SLAB, ["###"], {"#": PANEL}, 6)
    shaped(PANEL_WALL, ["###", "###"], {"#": PANEL}, 6, "misc")
    cutting(PANEL, PANEL_STAIRS)
    cutting(PANEL, PANEL_SLAB, 2)
    cutting(PANEL, PANEL_WALL)
    shaped(GRATING_SLAB, ["###"], {"#": GRATING}, 6)


# ================================================================ tags

def tags(write, data_dir):
    """Adds the industrial shapes to the shared minecraft slab / stairs / wall tags (merged into what
    building_assets.tags just wrote; call after it)."""
    import json
    a = lambda names: [f"{MOD}:{n}" for n in names]
    shared = {"stairs": [PANEL_STAIRS], "slabs": [PANEL_SLAB, GRATING_SLAB], "walls": [PANEL_WALL]}
    for kind in ("blocks", "items"):
        d = os.path.join(data_dir, "minecraft", "tags", kind)
        for tag, names in shared.items():
            path = os.path.join(d, tag + ".json")
            values = []
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    values = json.load(f).get("values", [])
            write(path, {"replace": False, "values": values + [v for v in a(names) if v not in values]})
