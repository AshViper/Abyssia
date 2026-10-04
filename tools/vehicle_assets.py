"""SUB02 submarine + submarine dock (spec inbox/specs/SUB02-submarine-dock.md): item / block models, blockstate,
shaped recipe, lang.  The dock has no item, recipe or loot (built with the habitat constructor, like the charging
station: com.abyssia.vehicle.SubmarineDockEntry).  gen_deep_assets.main() calls generate(); lang() merges LANG; tags() takes
pickaxe_blocks().  Java side: com.abyssia.vehicle.  The submarine mesh and its textures come from
tools/vehicle_model.py; the dock reuses existing habitat textures (no new texture).
"""
import os

MOD = "abyssia"
SUBMARINE = "submarine"
DOCK = "submarine_dock"

RECIPES = {
    # two propulsion screws (I04), alloy hull, marine resin seal, conductive wiring, glass canopy
    SUBMARINE: (["GGG", "AMA", "SCS"], {"G": "minecraft:glass", "A": "abyssia:abyssal_alloy_ingot", "M": "abyssia:marine_resin",
                                         "S": "abyssia:propulsion_screw", "C": "abyssia:conductive_alloy_ingot"}),
}

# key -> (English, Japanese)
LANG = {
    f"entity.{MOD}.submarine": ("Submarine", "潜水艦"),
    f"item.{MOD}.submarine": ("Submarine", "潜水艦"),
    f"item.{MOD}.submarine.source": (
        "A one-seat submarine. Right-click to place it on water, on the ground or under water, right-click it to board. "
        "W/S forward and back, A/D sideways, Space up, Ctrl down, mouse to turn, G headlights, Shift to get out. "
        "Runs on its 60,000 FE battery; dock it under a Submarine Dock (built in a Moon Pool with the Habitat Constructor) to charge and repair it.",
        "一人乗りの潜水艦。右クリックで水面・地面・水中に設置し、潜水艦を右クリックで乗り込む。"
        "W/S で前後、A/D で左右、Space で上昇、Ctrl で下降、マウスで旋回、G でライト、Shift で降りる。"
        "60,000 FE のバッテリーで動き、潜水艦ドック（ハビタット建設ツールでムーンプールに設置）の下に入れると充電・修理される。"),
    f"block.{MOD}.submarine_dock": ("Submarine Dock", "潜水艦ドック"),
    f"habitat.{MOD}.mode.submarine_dock": ("Submarine Dock", "潜水艦ドック"),
    f"habitat.{MOD}.submarine_dock.detail": ("Moon pool only, over the pool centre: docks, charges (%s FE buffer) and repairs a submarine",
                                             "ムーンプール専用、プール中央の真上: 潜水艦を固定・充電 (バッファ %s FE)・修理"),
    f"message.{MOD}.habitat.dock_not_pool": ("Aim inside a Moon Pool", "ムーンプールの中を狙ってください"),
    f"message.{MOD}.habitat.dock_exists": ("This Moon Pool already has a dock", "このムーンプールにはもうドックがあります"),
    f"key.{MOD}.submarine_light": ("Submarine Lights", "潜水艦ライト"),
    f"message.{MOD}.submarine.hud": ("⚡ %s%%  ❤ %s%%", "⚡ %s%%  ❤ %s%%"),
    f"message.{MOD}.submarine.docked": ("  Docked - Ctrl to release", "  ドック中 — Ctrl で切り離し"),
    f"message.{MOD}.submarine_dock.status": ("Dock buffer %s / %s FE - submarine %s%%", "ドックのバッファ %s / %s FE ・ 潜水艦 %s%%"),
    f"message.{MOD}.submarine_dock.empty": ("Dock buffer %s / %s FE - no submarine docked", "ドックのバッファ %s / %s FE ・ 潜水艦なし"),
    f"tooltip.{MOD}.submarine.energy": ("Energy %s / %s FE", "エネルギー %s / %s FE"),
    f"tooltip.{MOD}.submarine.controls": ("WASD move, Space up, Ctrl down, G lights, Shift exit",
                                          "WASD 移動、Space 上昇、Ctrl 下降、G ライト、Shift 降車"),
}

FRAME = f"{MOD}:block/habitat_door_frame"
LAMP = f"{MOD}:block/habitat_light"


def _box(frm, to, down_lamp=False):
    faces = {f: {"texture": "#frame"} for f in ("north", "east", "south", "west", "up", "down")}
    if down_lamp:
        faces["down"] = {"texture": "#lamp"}
    return {"from": frm, "to": to, "faces": faces}


def dock_model():
    """Ceiling clamp: stem from the top, a 12 x 3 x 12 plate (lamp underneath) and two jaws below it."""
    return {"parent": "minecraft:block/block",
            "textures": {"particle": FRAME, "frame": FRAME, "lamp": LAMP},
            "elements": [_box([6, 6, 6], [10, 16, 10]), _box([2, 3, 2], [14, 6, 14], True),
                         _box([2, 0, 2], [4, 3, 14]), _box([12, 0, 2], [14, 3, 14])]}


def pickaxe_blocks():
    return [DOCK]   # gen_deep_assets.tags() adds the "abyssia:" prefix


def generate(write, bs, bm, im, data_dir):
    write(im(SUBMARINE), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{SUBMARINE}"}})
    write(bm(DOCK), dock_model())
    write(bs(DOCK), {"variants": {"": {"model": f"{MOD}:block/{DOCK}"}}})
    for name, (pattern, key) in RECIPES.items():
        write(os.path.join(data_dir, MOD, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"{MOD}:{name}"}})
    return len(RECIPES)
