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
GANGWAY = "submarine_dock_gangway"   # SUB04 invisible helper blocks under the dock gangway

RECIPES = {
    # two propulsion screws (I04), alloy hull, marine resin seal, conductive wiring, glass canopy
    SUBMARINE: (["GGG", "AMA", "SCS"], {"G": "minecraft:glass", "A": "abyssia:abyssal_alloy_ingot", "M": "abyssia:marine_resin",
                                         "S": "abyssia:propulsion_screw", "C": "abyssia:conductive_alloy_ingot"}),
    # SUB03 upgrades (spec inbox/specs/SUB03-submarine-upgrades.md section 4): pressure glass = pressure_shell,
    # motor = conductive_component (the closest existing electric part besides the propulsion screw)
    "submarine_upgrade_pressure_hull": (["APA", "PIP", "AAA"], {"A": "abyssia:abyssal_alloy_ingot", "P": "abyssia:pressure_shell",
                                                                "I": "minecraft:iron_block"}),
    "submarine_upgrade_high_capacity_battery": (["CUC", "RER", "CGC"], {"C": "abyssia:conductive_alloy_ingot", "U": "minecraft:copper_block",
                                                                        "R": "minecraft:redstone", "E": "abyssia:abyssal_energy_cell",
                                                                        "G": "minecraft:gold_block"}),
    "submarine_upgrade_maneuver_thruster": (["ASA", "IMI", "ARA"], {"A": "abyssia:abyssal_alloy_ingot", "S": "abyssia:propulsion_screw",
                                                                    "I": "minecraft:iron_block", "M": "abyssia:conductive_component",
                                                                    "R": "minecraft:redstone"}),
    "submarine_upgrade_sonar_scanner": (["LPL", "KOK", "RAR"], {"L": "minecraft:sea_lantern", "P": "abyssia:pressure_shell",
                                                                "K": "minecraft:copper_ingot", "O": "abyssia:bio_oil",
                                                                "R": "minecraft:redstone", "A": "abyssia:abyssal_alloy_ingot"}),
}

# SUB03 upgrade items (ids / slot order: com.abyssia.vehicle.SubmarineUpgrades)
# id -> (English, Japanese, effect en, effect ja, cost en, cost ja, JEI source en, JEI source ja)
UPGRADES = {
    "submarine_upgrade_pressure_hull": (
        "Pressure Hull", "耐圧船体",
        "Hull 40 -> 70, half damage at Y -64 and below", "船体耐久 40 → 70、深層 (Y -64 以下) で被ダメージ半減",
        "All speeds x0.92", "全方向速度 ×0.92",
        "Submarine upgrade (hull slot). Raises the hull's break threshold from 40 to 70 and halves the damage the submarine "
        "takes at Y -64 and below (rounded up). All speeds x0.92. It cannot be taken out while the hull damage is above 40: "
        "repair at a dock first. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (船体スロット)。船体の破壊しきい値を 40 から 70 に上げ、Y -64 以下では潜水艦が受けるダメージを半分 (切り上げ) にする。"
        "全方向の速度 ×0.92。船体ダメージが 40 を超えている間は外せない (ドックで修理してから)。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_high_capacity_battery": (
        "High-Capacity Battery", "大容量バッテリー",
        "Battery 60,000 -> 150,000 FE", "容量 60,000 → 150,000 FE",
        "Up / down speed x0.95", "上下速度 ×0.95",
        "Submarine upgrade (power slot). Battery 60,000 -> 150,000 FE; installing keeps the current charge, removing it "
        "loses everything above 60,000 FE. Up / down speed x0.95. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (電源スロット)。バッテリー容量 60,000 → 150,000 FE。装着時は現在の残量を維持し、取り外すと 60,000 FE を超えた分は失われる。"
        "上下速度 ×0.95。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_maneuver_thruster": (
        "Maneuver Thruster", "高出力推進器",
        "Forward 0.56, back / side 0.30, up / down 0.23 b/t", "前進 0.56、後退/横 0.30、上下 0.23 b/t",
        "Thrust 8 -> 12 FE/t", "推進 8 → 12 FE/t",
        "Submarine upgrade (propulsion slot). Top speeds forward 0.42 -> 0.56, back / sideways 0.22 -> 0.30, up / down "
        "0.18 -> 0.23 blocks per tick, quicker acceleration. Thrust costs 12 FE per moving tick instead of 8. "
        "Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (推進スロット)。最高速度 前進 0.42 → 0.56、後退/横 0.22 → 0.30、上下 0.18 → 0.23 ブロック/tick、加速も向上。"
        "推進の消費は移動中 8 → 12 FE/tick。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_sonar_scanner": (
        "Deep-Sea Sonar", "深海ソナー",
        "HUD: creatures (16 m), current, docks (32 m)", "HUD に生物 (16m)・海流・ドック (32m) を表示",
        "4 FE/t while someone is aboard", "乗員がいる間 4 FE/t",
        "Submarine upgrade (utility slot). While piloted it adds to the HUD the number of sea creatures within 16 blocks "
        "and the nearest one's distance, the current at the hull (arrow relative to the heading, more arrows = stronger) "
        "and the nearest Submarine Dock within 32 blocks; newly detected creatures make a soft ping. 4 FE per tick. "
        "Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (補助スロット)。操縦中、半径 16 の海の生物の数と最寄りの距離、船体位置の海流 (矢印は進行方向基準、矢印が多いほど強い)、"
        "半径 32 以内の最寄りの潜水艦ドックを HUD に追加表示し、新しく見つけた生物は小さな音で知らせる。4 FE/tick。無人の潜水艦をスニーク + 右クリックで装着画面。"),
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
    f"block.{MOD}.submarine_dock_gangway": ("Dock Gangway", "ドックの足場"),
    f"habitat.{MOD}.mode.submarine_dock": ("Submarine Dock", "潜水艦ドック"),
    f"habitat.{MOD}.submarine_dock.detail": ("Moon pool only, over the pool centre: docks, charges (%s FE buffer) and repairs a submarine",
                                             "ムーンプール専用、プール中央の真上: 潜水艦を固定・充電 (バッファ %s FE)・修理"),
    f"message.{MOD}.habitat.dock_not_pool": ("Aim inside a Moon Pool", "ムーンプールの中を狙ってください"),
    f"message.{MOD}.habitat.dock_exists": ("This Moon Pool already has a dock", "このムーンプールにはもうドックがあります"),
    f"container.{MOD}.submarine.pod_right": ("Submarine Right Storage", "潜水艦の右収納"),
    f"container.{MOD}.submarine.pod_left": ("Submarine Left Storage", "潜水艦の左収納"),
    f"key.{MOD}.submarine_light":("Submarine Lights", "潜水艦ライト"),
    f"message.{MOD}.submarine.hud": ("⚡ %s%%  ❤ %s%%", "⚡ %s%%  ❤ %s%%"),
    f"message.{MOD}.submarine.docked": ("  Docked - Ctrl to release", "  ドック中 — Ctrl で切り離し"),
    f"message.{MOD}.submarine_dock.status": ("Dock buffer %s / %s FE - submarine %s%%", "ドックのバッファ %s / %s FE ・ 潜水艦 %s%%"),
    f"message.{MOD}.submarine_dock.empty": ("Dock buffer %s / %s FE - no submarine docked", "ドックのバッファ %s / %s FE ・ 潜水艦なし"),
    f"tooltip.{MOD}.submarine.energy": ("Energy %s / %s FE", "エネルギー %s / %s FE"),
    f"tooltip.{MOD}.submarine.controls": ("WASD move, Space up, Ctrl down, G lights, Shift exit",
                                          "WASD 移動、Space 上昇、Ctrl 下降、G ライト、Shift 降車"),
    # SUB03 upgrades: screen, item tooltips, sonar HUD
    f"container.{MOD}.submarine.upgrades": ("Submarine Systems", "潜水艦システム"),
    f"container.{MOD}.submarine.upgrades.status": ("⚡ %s / %s FE  ❤ %s%%", "⚡ %s / %s FE  ❤ %s%%"),
    f"container.{MOD}.submarine.upgrades.slot.hull": ("Hull slot: Pressure Hull", "船体スロット: 耐圧船体"),
    f"container.{MOD}.submarine.upgrades.slot.battery": ("Power slot: High-Capacity Battery", "電源スロット: 大容量バッテリー"),
    f"container.{MOD}.submarine.upgrades.slot.thruster": ("Propulsion slot: Maneuver Thruster", "推進スロット: 高出力推進器"),
    f"container.{MOD}.submarine.upgrades.slot.utility": ("Utility slot: Deep-Sea Sonar", "補助スロット: 深海ソナー"),
    f"tooltip.{MOD}.submarine_upgrade.battery_warning": ("Removing it caps the charge at %s FE (the rest is lost)",
                                                         "外すと残量は %s FE までになる (超過分は失われる)"),
    f"tooltip.{MOD}.submarine.upgrade": (" + %s", " + %s"),
    f"message.{MOD}.submarine.upgrade_hull_damaged": ("The hull is too damaged to remove the Pressure Hull - repair it at a dock first",
                                                      "船体の損傷が大きく耐圧船体を外せない — 先にドックで修理してください"),
    f"message.{MOD}.submarine.sonar.prefix": ("  |  ◎", "  |  ◎"),
    f"message.{MOD}.submarine.sonar.life": (" Life %s (%sm)", " 生物 %s (%sm)"),
    f"message.{MOD}.submarine.sonar.life_none": (" Life 0", " 生物 0"),
    f"message.{MOD}.submarine.sonar.current": (" Current %s %s", " 海流 %s %s"),
    f"message.{MOD}.submarine.sonar.current_none": (" Current -", " 海流 -"),
    f"message.{MOD}.submarine.sonar.weak": ("weak", "弱"),
    f"message.{MOD}.submarine.sonar.normal": ("medium", "中"),
    f"message.{MOD}.submarine.sonar.strong": ("strong", "強"),
    f"message.{MOD}.submarine.sonar.dock": (" Dock %s %sm", " ドック %s %sm"),
}
for _id, (_en, _ja, _effect_en, _effect_ja, _cost_en, _cost_ja, _src_en, _src_ja) in UPGRADES.items():
    LANG[f"item.{MOD}.{_id}"] = (_en, _ja)
    LANG[f"item.{MOD}.{_id}.source"] = (_src_en, _src_ja)
    LANG[f"tooltip.{MOD}.{_id}.effect"] = (_effect_en, _effect_ja)
    LANG[f"tooltip.{MOD}.{_id}.cost"] = (_cost_en, _cost_ja)

FRAME = f"{MOD}:block/habitat_door_frame"
LAMP = f"{MOD}:block/habitat_light"


def _box(frm, to, down_lamp=False):
    faces = {f: {"texture": "#frame"} for f in ("north", "east", "south", "west", "up", "down")}
    if down_lamp:
        faces["down"] = {"texture": "#lamp"}
    return {"from": frm, "to": to, "faces": faces}


def dock_model():
    """SUB04: the dock is drawn by DockRenderer (block entity); this model only serves the build ghost (the block's render
    shape is invisible) and the particles: base, ceiling beam, nose marker (-z) and a stub toward the gangway side (+x).
    Block px = dock frame + (8, 0, 8)."""
    return {"parent": "minecraft:block/block",
            "textures": {"particle": FRAME, "frame": FRAME, "lamp": LAMP},
            "elements": [_box([0, 1, 0], [16, 12, 16], True), _box([2, 8, -11], [14, 16, 27]),
                         _box([6, 6, -16], [10, 10, -11], True), _box([16, 8, 6], [24, 10, 10])]}


def gangway_model():
    return {"parent": "minecraft:block/block", "textures": {"particle": FRAME}, "elements": []}


def pickaxe_blocks():
    return [DOCK]   # gen_deep_assets.tags() adds the "abyssia:" prefix


def generate(write, bs, bm, im, data_dir):
    write(im(SUBMARINE), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{SUBMARINE}"}})
    for upgrade in UPGRADES:   # icons by ChatGPT, locked in tools/texture_locks
        write(im(upgrade), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{upgrade}"}})
    write(bm(DOCK), dock_model())
    write(bs(DOCK), {"variants": {f"facing={d}": {"model": f"{MOD}:block/{DOCK}", "y": y}
                                  for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write(bm(GANGWAY), gangway_model())
    write(bs(GANGWAY), {"variants": {"": {"model": f"{MOD}:block/{GANGWAY}"}}})
    for name, (pattern, key) in RECIPES.items():
        write(os.path.join(data_dir, MOD, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"{MOD}:{name}"}})
    return len(RECIPES)
