"""Base furniture (features H04 large locker / H05 wall workbench, spec inbox/specs/H04-base-equipment.md).

Data plus the JSON writers (blockstates, block + item models, loot tables, recipes) and the names / pickaxe list;
gen_deep_assets.main() calls generate() and merges LANG / pickaxe_blocks().  The Java side is com.abyssia.furniture +
registry/ModFurniture.  Models come from tools/furniture_models/parts.json (build_models.py binds the texture
variables per variant).  Textures (textures/block/large_locker_*.png, wall_workbench_*.png) are imported from the
ChatGPT sheet HAB4, never written here.

The locker is four cells of one block (part=bl|br|tl|tr); only part=bl drops the locker (the Java side breaks the
base whenever another cell goes), so the loot table has a block_state_property condition.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "furniture_models"))
import build_models  # noqa: E402

MOD = "abyssia"
LOCKER = "large_locker"
WORKBENCH = "wall_workbench"
LOCKER_PARTS = ("bl", "br", "tl", "tr")

# id -> (English, Japanese)
NAMES = {
    LOCKER: ("Large Locker", "大型ロッカー"),
    WORKBENCH: ("Wall-Mounted Workbench", "壁掛け作業台"),
}

# every key besides the block names (LargeLockerBlockEntity, WallWorkbenchBlockEntity, WallWorkbenchScreen)
LANG = {
    f"container.{MOD}.{LOCKER}": ("Large Locker", "大型ロッカー"),
    f"container.{MOD}.{WORKBENCH}": ("Wall-Mounted Workbench", "壁掛け作業台"),
    f"gui.{MOD}.{WORKBENCH}.charge": ("Charge slot (any FE item)", "充電スロット (FE 対応アイテム)"),
}
LANG.update({f"block.{MOD}.{k}": v for k, v in NAMES.items()})

# model north -> blockstate facing
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def pickaxe_blocks():
    return list(NAMES)


def rl(name, kind="block"):
    return f"{MOD}:{kind}/{name}"


def _variant(model, y):
    v = {"model": rl(model)}
    if y:
        v["y"] = y
    return v


def _models(write, bm, im):
    t = build_models.templates()
    names = []
    for part in LOCKER_PARTS:
        side = "side_top" if part.startswith("t") else "side_bottom"
        for is_open in (False, True):
            name = f"{LOCKER}_{part}" + ("_open" if is_open else "")
            textures = {"front": rl(f"{LOCKER}_{'open' if is_open else 'front'}_{part}"), "side": rl(f"{LOCKER}_{side}"),
                        "top": rl(f"{LOCKER}_top"), "bottom": rl(f"{LOCKER}_bottom")}
            write(bm(name), build_models.build(t[LOCKER], textures, particle="side"))
            names.append(name)
    item_tex = {f"front_{p}": rl(f"{LOCKER}_front_{p}") for p in LOCKER_PARTS}
    item_tex.update({v: rl(f"{LOCKER}_{v}") for v in ("side_top", "side_bottom", "top", "bottom")})
    write(im(LOCKER), build_models.build(t[LOCKER + "_item"], item_tex, particle="side_bottom"))

    wb = {v: rl(f"{WORKBENCH}_{v}") for v in ("back", "frame", "surface", "teal", "display", "connector")}
    write(bm(WORKBENCH), build_models.build(t[WORKBENCH], wb, particle="frame"))
    write(bm(WORKBENCH + "_on"), build_models.build(t[WORKBENCH], {**wb, "display": rl(f"{WORKBENCH}_display_on")},
                                                    particle="frame"))
    write(im(WORKBENCH), {"parent": rl(WORKBENCH)})
    return names + [WORKBENCH, WORKBENCH + "_on"]


def _blockstates(write, bs):
    variants = {}
    for facing, y in FACING_Y.items():
        for part in LOCKER_PARTS:
            for is_open in (False, True):
                variants[f"facing={facing},open={str(is_open).lower()},part={part}"] = _variant(
                    f"{LOCKER}_{part}" + ("_open" if is_open else ""), y)
    write(bs(LOCKER), {"variants": variants})
    write(bs(WORKBENCH), {"variants": {
        f"facing={facing},powered={str(on).lower()}": _variant(WORKBENCH + ("_on" if on else ""), y)
        for facing, y in FACING_Y.items() for on in (False, True)}})


def _loot(write, data_dir):
    lt = lambda n: os.path.join(data_dir, MOD, "loot_tables", "blocks", n + ".json")
    survives = {"condition": "minecraft:survives_explosion"}
    write(lt(LOCKER), {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0,
        "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:{LOCKER}",
                        "properties": {"part": "bl"}}, survives],
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:{LOCKER}",
                     "functions": [{"function": "minecraft:copy_name", "source": "block_entity"}]}]}],
        "random_sequence": f"{MOD}:blocks/{LOCKER}"})
    write(lt(WORKBENCH), {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0, "conditions": [survives],
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:{WORKBENCH}"}]}],
        "random_sequence": f"{MOD}:blocks/{WORKBENCH}"})


def _recipes(write, data_dir):
    rd = lambda n: os.path.join(data_dir, MOD, "recipes", n + ".json")
    item = lambda n: {"item": n if ":" in n else f"{MOD}:{n}"}

    def shaped(out, pattern, key):
        write(rd(out), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                        "key": {k: item(v) for k, v in key.items()}, "result": {"item": f"{MOD}:{out}", "count": 1}})

    shaped(LOCKER, ["PPP", "AMA", "PPP"], {"P": "iron_plate", "A": "corrosion_alloy_ingot", "M": "machine_frame"})
    shaped(WORKBENCH, ["PPP", "IMI", "CKC"], {"P": "iron_plate", "I": "industrial_panel", "M": "machine_frame",
                                              "C": "conductive_alloy_ingot", "K": "minecraft:crafting_table"})


def generate(write, bs, bm, im, data_dir):
    """Writes blockstates, block + item models, loot tables and recipes.  The pickaxe tag entries come from
    gen_deep_assets.tags() via pickaxe_blocks().  Returns the number of block models."""
    models = _models(write, bm, im)
    _blockstates(write, bs)
    _loot(write, data_dir)
    _recipes(write, data_dir)
    return len(models)
