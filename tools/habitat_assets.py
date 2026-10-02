"""Deep-sea habitat modules (feature H01, spec inbox/specs/H01-habitat-modules.md): the blocks the habitat
constructor places and the constructor item.

Data plus the JSON writers (blockstates, block + item models, recipe) and the names / pickaxe list;
gen_deep_assets.main() calls generate() and merges LANG / pickaxe_blocks().  The Java side is com.abyssia.habitat +
registry/ModHabitat.  The blocks have NO block items and NO loot tables (they drop nothing): keep them out of
gen_deep_assets.NAMES, whose loop writes a self-drop table for every entry.  Textures (textures/block/habitat_*.png,
textures/item/habitat_constructor.png) are imported from the ChatGPT sheets HAB1 / HAB2, never written here.
"""
import json
import os

MOD = "abyssia"
CONSTRUCTOR = "habitat_constructor"

# id -> (English, Japanese)
NAMES = {
    "habitat_floor": ("Habitat Floor", "深海拠点床"),
    "habitat_trim": ("Habitat Hull Band", "深海拠点外壁(帯)"),
    "habitat_wall": ("Habitat Hull", "深海拠点外壁"),
    "habitat_ceiling": ("Habitat Ceiling", "深海拠点天井"),
    "habitat_window": ("Habitat Window", "深海拠点窓"),
    "habitat_light": ("Habitat Light", "深海拠点照明"),
    "habitat_door_frame": ("Habitat Door Frame", "深海拠点扉枠"),
    "habitat_hatch": ("Habitat Connector Hatch", "深海拠点接続ハッチ"),
    "habitat_door": ("Habitat Airlock Door", "深海拠点気密扉"),
    "scan_console": ("Scan Console", "スキャンコンソール"),     # H07, has a block entity
    "habitat_support": ("Habitat Support Leg", "深海拠点支柱"),     # H09, waterloggable post
}
CUBES = ["habitat_floor", "habitat_trim", "habitat_wall", "habitat_ceiling", "habitat_light", "habitat_door_frame"]

MODES = {
    "foundation": ("Foundation", "土台"),
    "room": ("Multipurpose Room", "多目的ルーム"),
    "corridor": ("Corridor", "廊下"),
    "entrance": ("Entrance", "出入り口"),
    "moon_pool": ("Moon Pool", "ムーンプール"),
    "scan_room": ("Scan Room", "スキャン室"),       # H07
}

# every key besides block.* / item.* names (HabitatConstructorItem, HabitatBuilder, HabitatMode)
LANG = {
    f"item.{MOD}.{CONSTRUCTOR}": ("Deep-Sea Habitat Constructor", "深海拠点建設装置"),
    f"tooltip.{MOD}.habitat.mode": ("Module: %s", "モジュール: %s"),
    f"tooltip.{MOD}.habitat.size": ("Size: %s x %s x %s (W x D x H)", "寸法: %s x %s x %s (幅x奥行x高さ)"),
    f"tooltip.{MOD}.habitat.cost": ("Cost:", "コスト:"),
    f"tooltip.{MOD}.habitat.creative": ("Free in creative mode", "クリエイティブでは無料"),
    f"tooltip.{MOD}.habitat.menu": ("Right-click / G: build menu", "右クリック / G: 建設メニュー"),
    f"tooltip.{MOD}.habitat.build": ("Left-click: build at the hologram (in water)", "左クリック: ホログラムの位置に建設 (水中)"),
    f"tooltip.{MOD}.habitat.rotate": ("Mouse wheel / R: rotate (Shift+R: back)", "ホイール / R: 回転 (Shift+R: 逆回転)"),
    # H02 controls (habitat/client/HabitatClient, HabitatMenuScreen)
    f"key.categories.{MOD}": ("Abyssia", "Abyssia"),
    f"key.{MOD}.habitat_menu": ("Habitat Build Menu", "拠点建設メニュー"),
    f"key.{MOD}.habitat_rotate": ("Rotate Habitat Module", "拠点モジュールを回転"),
    f"screen.{MOD}.habitat.title": ("Habitat Modules", "拠点モジュール"),
    f"screen.{MOD}.habitat.size": ("%s x %s x %s", "%s x %s x %s"),
    f"screen.{MOD}.habitat.hint": ("Click / Enter: select   Wheel / Up / Down: move   G / Esc: close",
                                  "クリック / Enter: 選択   ホイール / ↑↓: 移動   G / Esc: 閉じる"),
    f"message.{MOD}.habitat.started": ("Building %s...", "%s を建設中..."),
    f"message.{MOD}.habitat.busy": ("Already building", "建設中"),
    f"message.{MOD}.habitat.cancelled": ("Construction cancelled: %s (materials returned)", "建設を中断: %s (素材は返却)"),
    f"message.{MOD}.habitat.cancel.too_far": ("too far away", "離れすぎた"),
    f"message.{MOD}.habitat.cancel.switched": ("constructor put away", "装置を持ち替えた"),
    f"message.{MOD}.habitat.cancel.blocked": ("something got in the way", "範囲に別のブロックが置かれた"),
    f"message.{MOD}.habitat.cancel.left": ("builder left", "建設者がいなくなった"),
    f"message.{MOD}.habitat.cancel.cancelled": ("cancelled", "取り消し"),
    f"message.{MOD}.habitat.mode": ("Module: %s", "モジュール: %s"),
    f"message.{MOD}.habitat.built": ("%s built (%s connected)", "%s を建設 (接続 %s)"),
    f"message.{MOD}.habitat.missing": ("Missing: %s", "素材不足: %s"),
    f"message.{MOD}.habitat.not_water": ("The whole area must be water", "範囲がすべて水ではない"),
    f"message.{MOD}.habitat.entity": ("Something is in the way", "範囲内に生き物がいる"),
    f"message.{MOD}.habitat.permission": ("You can't build here", "ここには建設できない"),
    # H08 habitat power (habitat/power/HabitatPower, sneak + right-click a shell block with an empty hand)
    f"message.{MOD}.habitat.power": ("Base power %s / %s FE · in %s FE/t · out %s FE/t",
                                    "拠点電力 %s / %s FE ・入力 %s FE/t ・出力 %s FE/t"),
    f"message.{MOD}.habitat.power.none": ("This module is not part of a powered base (built before H08)",
                                         "このモジュールは拠点電力に未登録 (H08 以前に建設)"),
    # H07 scan console (habitat/scan/ScanConsoleScreen)
    f"container.{MOD}.scan_console": ("Scan Console", "スキャンコンソール"),
    f"gui.{MOD}.scan.all": ("All targets", "すべて"),
    f"gui.{MOD}.scan.button": ("SCAN", "スキャン"),
    f"gui.{MOD}.scan.progress": ("Scanning... %s%%", "スキャン中... %s%%"),
    f"gui.{MOD}.scan.ready": ("Ready (%s FE per scan)", "待機中 (1 回 %s FE)"),
    f"gui.{MOD}.scan.no_energy": ("Not enough energy (%s FE needed)", "エネルギー不足 (%s FE 必要)"),
    f"gui.{MOD}.scan.results": ("%s targets", "対象 %s 件"),
    f"gui.{MOD}.scan.none": ("No scan yet", "未スキャン"),
    f"gui.{MOD}.scan.hint": ("Drag: rotate  Wheel: zoom  R: north", "ドラッグ: 回転  ホイール: 拡大縮小  R: 北向き"),
}
LANG.update({f"habitat.{MOD}.mode.{k}": v for k, v in MODES.items()})
LANG.update({f"block.{MOD}.{k}": v for k, v in NAMES.items()})


def pickaxe_blocks():
    return list(NAMES)


def rl(name, kind="block"):
    return f"{MOD}:{kind}/{name}"


# door model rotation by facing (vanilla door blockstate)
DOOR_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
HATCH_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def _door_blockstate():
    variants = {}
    for facing, base in DOOR_Y.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for is_open in (False, True):
                    model = f"habitat_door_{part}_{hinge}" + ("_open" if is_open else "")
                    y = (base + ((90 if hinge == "left" else 270) if is_open else 0)) % 360
                    v = {"model": rl(model)}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},half={half},hinge={hinge},open={str(is_open).lower()}"] = v
    return {"variants": variants}


# Connected glass: per face, the world directions that are up / down / left / right as seen from outside (they match
# the vanilla default face UVs).  Texture habitat_window_c<mask> (tools/habitat_window_ctm.py), mask 0 = the base.
WINDOW_DIRS = ["north", "east", "south", "west", "up", "down"]
WINDOW_FACES = {
    "north": ("up", "down", "east", "west"), "south": ("up", "down", "west", "east"),
    "east": ("up", "down", "south", "north"), "west": ("up", "down", "north", "south"),
    "up": ("north", "south", "west", "east"), "down": ("south", "north", "west", "east"),
}


def _window(write, bs, bm):
    """64 states (one boolean per direction = same block there) -> one model each."""
    variants = {}
    for bits in range(64):
        on = {d: bool(bits >> i & 1) for i, d in enumerate(WINDOW_DIRS)}
        name = "habitat_window" if bits == 0 else f"habitat_window_{bits}"
        textures = {}
        for face, (u, d, l, r) in WINDOW_FACES.items():
            mask = on[u] * 1 | on[d] * 2 | on[l] * 4 | on[r] * 8
            textures[face] = rl("habitat_window" if mask == 0 else f"habitat_window_c{mask}")
        textures["particle"] = rl("habitat_window")
        write(bm(name), {"parent": "minecraft:block/cube", "render_type": "minecraft:translucent", "textures": textures})
        variants[",".join(f"{d}={str(on[d]).lower()}" for d in WINDOW_DIRS)] = {"model": rl(name)}
    write(bs("habitat_window"), {"variants": variants})


def generate(write, bs, bm, im, data_dir):
    """Writes blockstates, block models, the constructor item model and its recipe.  No loot tables; the pickaxe
    tag entries come from gen_deep_assets.tags() via pickaxe_blocks()."""
    for name in CUBES:
        write(bs(name), {"variants": {"": {"model": rl(name)}}})
        write(bm(name), {"parent": "minecraft:block/cube_all", "textures": {"all": rl(name)}})

    _window(write, bs, bm)

    # hatch: hatch face on the outside (north in the model) and the inside, hull on the rest
    write(bm("habitat_hatch"), {"parent": "minecraft:block/cube", "textures": {
        "particle": rl("habitat_hatch"), "north": rl("habitat_hatch"), "south": rl("habitat_hatch"),
        "east": rl("habitat_wall"), "west": rl("habitat_wall"), "up": rl("habitat_wall"), "down": rl("habitat_wall")}})
    write(bs("habitat_hatch"), {"variants": {
        f"facing={f}": ({"model": rl("habitat_hatch"), "y": y} if y else {"model": rl("habitat_hatch")})
        for f, y in HATCH_Y.items()}})

    door_tex = {"bottom": rl("habitat_door_bottom"), "top": rl("habitat_door_top")}
    for part in ("bottom", "top"):
        for hinge in ("left", "right"):
            for suffix in ("", "_open"):
                write(bm(f"habitat_door_{part}_{hinge}{suffix}"), {
                    "parent": f"minecraft:block/door_{part}_{hinge}{suffix}", "render_type": "minecraft:cutout",
                    "textures": door_tex})
    write(bs("habitat_door"), _door_blockstate())

    # H07 scan console: side / top texture pedestal (14 high) and the abyssia:scannable target tag
    write(bm("scan_console"), {"parent": "minecraft:block/block", "textures": {
        "particle": rl("scan_console_side"), "side": rl("scan_console_side"), "top": rl("scan_console_top")},
        "elements": [{"from": [1, 0, 1], "to": [15, 14, 15], "faces": {
            **{d: {"texture": "#side", "uv": [1, 2, 15, 16]} for d in ("north", "east", "south", "west")},
            "up": {"texture": "#top", "uv": [1, 1, 15, 15]},
            "down": {"texture": "#side", "uv": [1, 1, 15, 15], "cullface": "down"}}}]})
    write(bs("scan_console"), {"variants": {"": {"model": rl("scan_console")}}})
    scannable_tag(write, bs, data_dir)
    # H09 support leg: 8 px post, hull sides, ceiling ends; same model waterlogged or not
    post = {"from": [4, 0, 4], "to": [12, 16, 12], "faces": {
        **{d: {"texture": "#side", "uv": [4, 0, 12, 16]} for d in ("north", "east", "south", "west")},
        "up": {"texture": "#end", "uv": [4, 4, 12, 12], "cullface": "up"},
        "down": {"texture": "#end", "uv": [4, 4, 12, 12], "cullface": "down"}}}
    write(bm("habitat_support"), {"parent": "minecraft:block/block", "textures": {
        "particle": rl("habitat_wall"), "side": rl("habitat_wall"), "end": rl("habitat_ceiling")}, "elements": [post]})
    write(bs("habitat_support"), {"variants": {f"waterlogged={w}": {"model": rl("habitat_support")} for w in ("false", "true")}})

    # Water above a ceiling makes vanilla spawn dripping-water particles under it; impermeable blocks (like glass)
    # don't, so the whole hull (and the console) goes into minecraft:impermeable.
    write(os.path.join(data_dir, "minecraft", "tags", "blocks", "impermeable.json"),
          {"replace": False, "values": [f"{MOD}:{name}" for name in NAMES]})

    write(im(CONSTRUCTOR), constructor_model())
    recipes(write, data_dir)
    return {"blocks": len(NAMES)}


def scannable_blocks(blockstate_dir):
    """Abyssia ores, crusts (not the polished / brick building shapes or salt crust), nodules and mineral clusters,
    read from the blockstates already written (so it follows every generator)."""
    out = []
    for f in sorted(os.listdir(blockstate_dir)):
        n = f[:-5]
        if not f.endswith(".json") or n.startswith("polished_") or n == "salt_crust":
            continue
        if n.endswith("_ore") or n.endswith("_crust") or n.endswith("_nodules") or n.endswith("_cluster"):
            out.append(n)
    return out


def scannable_tag(write, bs, data_dir):
    names = scannable_blocks(os.path.dirname(bs("x")))
    write(os.path.join(data_dir, MOD, "tags", "blocks", "scannable.json"),
          {"replace": False, "values": [{"id": f"{MOD}:{n}", "required": False} for n in names]})


def constructor_model():
    """H06: the constructor's 3D item model from tools/habitat_models/constructor_parts.json (UVs follow the element
    position like the I01 parts models; emissive parts are full-bright)."""
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "habitat_models", "constructor_parts.json"),
              encoding="utf-8") as f:
        spec = json.load(f)
    textures = {"particle": rl(CONSTRUCTOR + "_body", "item")}
    elements = []
    for part in spec["parts"]:
        textures[part["tex"]] = rl(f"{CONSTRUCTOR}_{part['tex']}", "item")
        element = {"name": part["name"], "from": part["from"], "to": part["to"],
                   "faces": {d: {"texture": "#" + part["tex"]} for d in ("north", "east", "south", "west", "up", "down")}}
        if part.get("emissive"):
            element["forge_data"] = {"block_light": 15, "sky_light": 15}
        elements.append(element)
    return {"textures": textures, "elements": elements, "display": spec["display"]}


def recipes(write, data_dir):
    key = {"P": f"{MOD}:iron_plate", "H": f"{MOD}:high_strength_alloy_ingot", "C": "minecraft:copper_ingot",
           "A": f"{MOD}:abyssal_alloy_ingot", "I": "minecraft:iron_ingot", "R": "minecraft:redstone"}
    write(os.path.join(data_dir, MOD, "recipes", CONSTRUCTOR + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["PHP", "CAC", "IRI"],
        "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"{MOD}:{CONSTRUCTOR}", "count": 1}})

