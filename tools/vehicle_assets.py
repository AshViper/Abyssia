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
    # BAL01: upgrades were as dear as the submarine itself (iron / gold blocks) -> plates / ingots, ~40-75% of the hull
    # SUB03 upgrades (spec inbox/specs/SUB03-submarine-upgrades.md section 4): pressure glass = pressure_shell,
    # motor = conductive_component (the closest existing electric part besides the propulsion screw)
    "submarine_upgrade_pressure_hull": (["APA", "PIP", "AAA"], {"A": "abyssia:abyssal_alloy_ingot", "P": "abyssia:pressure_shell",
                                                                "I": "abyssia:iron_plate"}),
    "submarine_upgrade_high_capacity_battery": (["CUC", "RER", "CGC"], {"C": "abyssia:conductive_alloy_ingot", "U": "minecraft:copper_block",
                                                                        "R": "minecraft:redstone", "E": "abyssia:abyssal_energy_cell",
                                                                        "G": "minecraft:gold_ingot"}),
    "submarine_upgrade_maneuver_thruster": (["ASA", "IMI", "ARA"], {"A": "abyssia:abyssal_alloy_ingot", "S": "abyssia:propulsion_screw",
                                                                    "I": "abyssia:iron_plate", "M": "abyssia:conductive_component",
                                                                    "R": "minecraft:redstone"}),
    # SUB06 Depth Hull Mk1..Mk3: each tier is built on the one below
    "submarine_upgrade_depth_mk1": (["AIA", "PIP", "AIA"], {"A": "abyssia:abyssal_alloy_ingot", "I": "abyssia:iron_plate",
                                                            "P": "abyssia:pressure_shell"}),
    "submarine_upgrade_depth_mk2": (["TPT", "AUA", "TPT"], {"T": "abyssia:titanium_ingot", "P": "abyssia:pressure_shell",
                                                            "A": "abyssia:abyssal_alloy_ingot", "U": "abyssia:submarine_upgrade_depth_mk1"}),
    "submarine_upgrade_depth_mk3": (["WHW", "PUP", "WHW"], {"W": "abyssia:tungsten_ingot", "H": "abyssia:hadal_plating",
                                                            "P": "abyssia:pressure_shell", "U": "abyssia:submarine_upgrade_depth_mk2"}),
    "submarine_upgrade_sonar_scanner": (["LPL", "KOK", "RAR"], {"L": "minecraft:sea_lantern", "P": "abyssia:pressure_shell",
                                                                "K": "minecraft:copper_ingot", "O": "abyssia:bio_oil",
                                                                "R": "minecraft:redstone", "A": "abyssia:abyssal_alloy_ingot"}),
    # TRP01 boss-material upgrades: Mk2 of the hull / battery / thruster / sonar slots (SubmarineUpgrades.ADV_IDS), each built on its Mk1
    "submarine_upgrade_serpent_scale_hull": (["SSS", "SUS", "ASA"], {"S": "abyssia:abyss_serpent_scale", "U": "abyssia:submarine_upgrade_pressure_hull",
                                                                    "A": "abyssia:abyssal_alloy_ingot"}),
    "submarine_upgrade_jet_thruster": (["IKI", "IUI", "IKI"], {"I": "abyssia:kraken_ink_sac", "K": "abyssia:kraken_beak",
                                                              "U": "abyssia:submarine_upgrade_maneuver_thruster"}),
    "submarine_upgrade_abyss_heart_cell": (["GHG", "GUG"], {"G": "abyssia:mother_bell_gel", "H": "abyssia:abyss_heart",
                                                           "U": "abyssia:submarine_upgrade_high_capacity_battery"}),
    "submarine_upgrade_lure_sonar": (["LCL", "LUL", "LCL"], {"L": "abyssia:angler_lure", "C": "abyssia:luminous_crystal",
                                                            "U": "abyssia:submarine_upgrade_sonar_scanner"}),
    # TRP01 boss-material gear (MaterialTools.HEART_TANK / FANG_DRILL)
    "heart_tank": (["GHG", "GTG"], {"G": "abyssia:mother_bell_gel", "H": "abyssia:abyss_heart", "T": "abyssia:pressure_dive_tank"}),
    "fang_drill": (["FCF", " D ", "C F"], {"F": "abyssia:trench_wyrm_fang", "C": "abyssia:pressure_crystal_shard", "D": "abyssia:abyssal_drill"}),
}

# TRP01: Mk2 upgrade -> the Mk1 item whose icon it reuses (no new texture; the enchantment glint tells them apart)
ADV_ICON = {}   # Mk2 icons now have their own textures; kept empty as an override hook

# BOSS01 boss materials: plain generated item models (textures by ChatGPT, locked in tools/texture_locks).
BOSS_ITEMS = ["abyss_serpent_scale", "trench_wyrm_fang", "kraken_beak", "kraken_ink_sac", "mother_bell_gel", "abyss_heart", "angler_lure"]

# TRP01 gear items: id -> (English, Japanese, model parent, texture reused, JEI source en, ja)
TRP01_ITEMS = {
    "heart_tank": (
        "Heart Tank", "心臓タンク", "minecraft:item/generated", "heart_tank",
        "Chest dive tank, pressure tier 4: 1,800 s of oxygen, durability 800, no depth limit; also heat, cold and toxic proof. "
        "Built from an Abyss Heart, 4 Mother Bell Gel and a Pressure Dive Tank.",
        "胸装備のダイブタンク (耐圧ティア 4)。酸素 1,800 秒、耐久 800、水深制限なし。耐熱・耐寒・耐毒つき。"
        "深淵の核 1・母の傘ゲル 4・耐圧ダイブタンク 1 で作る。"),
    "fang_drill": (
        "Fang Drill", "牙のドリル", "minecraft:item/handheld", "fang_drill",
        "A drill with a Trench Wyrm fang bit: mining speed 16, durability 4,000. Built from 3 Trench Wyrm Fangs, an Abyssal Drill "
        "and 2 Pressure Crystal Shards.",
        "大ワームの牙を刃にしたドリル。採掘速度 16、耐久 4,000。大ワームの牙 3・深海ドリル 1・水圧結晶の欠片 2 で作る。"),
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
        "Forward 0.56, side 0.51, up / down 0.48 b/t", "前進 0.56、横 0.51、上下 0.48 b/t",
        "Thrust 8 -> 12 FE/t", "推進 8 → 12 FE/t",
        "Submarine upgrade (propulsion slot). Top speeds forward 0.42 -> 0.56, sideways 0.38 -> 0.51, up / down "
        "0.36 -> 0.48, reverse = sideways x 5/11.5 blocks per tick, quicker acceleration. Thrust costs 12 FE per moving tick "
        "instead of 8. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (推進スロット)。最高速度 前進 0.42 → 0.56、横 0.38 → 0.51、上下 0.36 → 0.48、後退 = 横 × 5/11.5 ブロック/tick、加速も向上。"
        "推進の消費は移動中 8 → 12 FE/tick。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_depth_mk1": (
        "Depth Hull Mk1", "深度船体 Mk1",
        "Crush depth Y -300 -> Y -380", "耐圧深度 Y -300 → Y -380",
        "Below it the hull takes crush damage", "超えると船体に圧壊ダメージ",
        "Submarine upgrade (depth slot). Lowers the crush depth from Y -300 to Y -380. Below that height the hull takes "
        "crush damage every second (4 plus 1 per 200 blocks deeper; the hull breaks at 40, or 70 with the Pressure Hull), so surface or "
        "fit a deeper hull in time. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (深度スロット)。耐圧深度を Y -300 から Y -380 に下げる。その高さより下では毎秒船体に圧壊ダメージ "
        "(4 + 200 ブロック深くなるごとに 1、船体は 40 で壊れる。耐圧船体があれば 70) を受けるので、早めに浮上するかより深い船体に換えること。"
        "無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_depth_mk2": (
        "Depth Hull Mk2", "深度船体 Mk2",
        "Crush depth Y -300 -> Y -450", "耐圧深度 Y -300 → Y -450",
        "Below it the hull takes crush damage", "超えると船体に圧壊ダメージ",
        "Submarine upgrade (depth slot). Lowers the crush depth from Y -300 to Y -450. Below that height the hull takes "
        "crush damage every second (4 plus 1 per 200 blocks deeper; the hull breaks at 40, or 70 with the Pressure Hull), so surface or "
        "fit a deeper hull in time. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (深度スロット)。耐圧深度を Y -300 から Y -450 に下げる。その高さより下では毎秒船体に圧壊ダメージ "
        "(4 + 200 ブロック深くなるごとに 1、船体は 40 で壊れる。耐圧船体があれば 70) を受けるので、早めに浮上するかより深い船体に換えること。"
        "無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_depth_mk3": (
        "Depth Hull Mk3", "深度船体 Mk3",
        "Crush depth Y -300 -> Y -566", "耐圧深度 Y -300 → Y -566",
        "Below it the hull takes crush damage", "超えると船体に圧壊ダメージ",
        "Submarine upgrade (depth slot). Lowers the crush depth from Y -300 to Y -566. Below that height the hull takes "
        "crush damage every second (4 plus 1 per 200 blocks deeper; the hull breaks at 40, or 70 with the Pressure Hull), so surface or "
        "fit a deeper hull in time. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (深度スロット)。耐圧深度を Y -300 から Y -566 に下げる。その高さより下では毎秒船体に圧壊ダメージ "
        "(4 + 200 ブロック深くなるごとに 1、船体は 40 で壊れる。耐圧船体があれば 70) を受けるので、早めに浮上するかより深い船体に換えること。"
        "無人の潜水艦をスニーク + 右クリックで装着画面。"),
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
    # TRP01 Mk2 (boss materials): same slot as the Mk1 item, never together with it
    "submarine_upgrade_serpent_scale_hull": (
        "Serpent-Scale Hull", "大海蛇鱗の船体",
        "Hull 110, 35% damage at Y -64 and below", "船体耐久 110、深層 (Y -64 以下) で被ダメージ 35%",
        "No speed penalty", "速度低下なし",
        "Submarine upgrade (hull slot, Mk2 of the Pressure Hull). Raises the hull's break threshold to 110, cuts the damage taken at "
        "Y -64 and below to 35% (rounded up) and costs no speed. Replaces the Pressure Hull in the same slot. Install with "
        "Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (船体スロット、耐圧船体の Mk2)。船体の破壊しきい値を 110 に上げ、Y -64 以下の被ダメージを 35% (切り上げ) にし、速度も落ちない。"
        "耐圧船体と同じスロットに入る。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_jet_thruster": (
        "Jet Thruster", "噴流推進器",
        "Forward 0.68, side 0.60, up / down 0.58 b/t", "前進 0.68、横 0.60、上下 0.58 b/t",
        "Thrust 14 FE/t", "推進 14 FE/t",
        "Submarine upgrade (propulsion slot, Mk2 of the Maneuver Thruster). Top speeds forward 0.68, sideways 0.60, up / down 0.58 "
        "blocks per tick, acceleration 0.040. Thrust costs 14 FE per moving tick. Replaces the Maneuver Thruster in the same slot. "
        "Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (推進スロット、高出力推進器の Mk2)。最高速度 前進 0.68、横 0.60、上下 0.58 ブロック/tick、加速 0.040。"
        "推進の消費は移動中 14 FE/tick。高出力推進器と同じスロットに入る。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_abyss_heart_cell": (
        "Abyss Heart Cell", "深淵心臓セル",
        "Battery 60,000 -> 400,000 FE", "容量 60,000 → 400,000 FE",
        "Up / down speed x0.95 as the battery", "上下速度は大容量バッテリーと同じ ×0.95",
        "Submarine upgrade (power slot, Mk2 of the High-Capacity Battery). Battery 400,000 FE; removing it keeps the charge only "
        "up to the base size. Replaces the High-Capacity Battery in the same slot. Install with Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (電源スロット、大容量バッテリーの Mk2)。バッテリー容量 400,000 FE。取り外すと基本容量を超えた分の残量は失われる。"
        "大容量バッテリーと同じスロットに入る。無人の潜水艦をスニーク + 右クリックで装着画面。"),
    "submarine_upgrade_lure_sonar": (
        "Lure Sonar", "誘光ソナー",
        "HUD: creatures (48 m), current, docks (96 m)", "HUD に生物 (48m)・海流・ドック (96m) を表示",
        "4 FE/t while someone is aboard", "乗員がいる間 4 FE/t",
        "Submarine upgrade (utility slot, Mk2 of the Deep-Sea Sonar). The sonar HUD reaches 48 blocks for sea creatures and 96 "
        "blocks for the nearest Submarine Dock. 4 FE per tick. Replaces the Deep-Sea Sonar in the same slot. Install with "
        "Sneak + right-click on an unmanned submarine.",
        "潜水艦アップグレード (補助スロット、深海ソナーの Mk2)。ソナー HUD の探知が生物は半径 48、ドックは半径 96 に伸びる。4 FE/tick。"
        "深海ソナーと同じスロットに入る。無人の潜水艦をスニーク + 右クリックで装着画面。"),
}

# key -> (English, Japanese)
LANG = {
    f"entity.{MOD}.submarine": ("Submarine", "潜水艦"),
    f"item.{MOD}.submarine": ("Submarine", "潜水艦"),
    f"item.{MOD}.submarine.source": (
        "A one-seat submarine. Right-click to place it on water, on the ground or under water, right-click it to board. "
        "The hull faces where you look: W/S move along the nose (look up or down to climb or dive), A/D sideways, mouse to steer, Ctrl to release the dock, G headlights, Shift to get out. "
        "Runs on its 60,000 FE battery; dock it under a Submarine Dock (built in a Moon Pool with the Habitat Constructor) to charge and repair it.",
        "一人乗りの潜水艦。右クリックで水面・地面・水中に設置し、潜水艦を右クリックで乗り込む。"
        "船体は視点の向きに向く: W/S で機首方向に前後 (視点を上下で浮上・潜行)、A/D で左右、マウスで操舵、Ctrl でドック切り離し、G でライト、Shift で降りる。"
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
    f"key.{MOD}.submarine_light":("Light (submarine / propulsion screw)", "ライト（潜水艦・推進スクリュー）"),
    f"message.{MOD}.submarine.hud": ("⚡ %s%%  ❤ %s%%", "⚡ %s%%  ❤ %s%%"),
    f"message.{MOD}.submarine.docked": ("  Docked - Ctrl to release", "  ドック中 — Ctrl で切り離し"),
    f"message.{MOD}.submarine_dock.status": ("Dock buffer %s / %s FE - submarine %s%%", "ドックのバッファ %s / %s FE ・ 潜水艦 %s%%"),
    f"message.{MOD}.submarine_dock.empty": ("Dock buffer %s / %s FE - no submarine docked", "ドックのバッファ %s / %s FE ・ 潜水艦なし"),
    f"tooltip.{MOD}.submarine.energy": ("Energy %s / %s FE", "エネルギー %s / %s FE"),
    f"tooltip.{MOD}.submarine.controls": ("W/S along view, A/D sideways, Ctrl undock, G lights, Shift exit",
                                          "W/S 視点方向に前後、A/D 左右、Ctrl ドック切り離し、G ライト、Shift 降車"),
    # SUB03 upgrades: screen, item tooltips, sonar HUD
    f"container.{MOD}.submarine.upgrades": ("Submarine Systems", "潜水艦システム"),
    f"container.{MOD}.submarine.upgrades.status": ("⚡ %s / %s FE  ❤ %s%%", "⚡ %s / %s FE  ❤ %s%%"),
    f"container.{MOD}.submarine.upgrades.slot.hull": ("Hull slot: Pressure Hull", "船体スロット: 耐圧船体"),
    f"container.{MOD}.submarine.upgrades.slot.battery": ("Power slot: High-Capacity Battery", "電源スロット: 大容量バッテリー"),
    f"container.{MOD}.submarine.upgrades.slot.thruster": ("Propulsion slot: Maneuver Thruster", "推進スロット: 高出力推進器"),
    f"container.{MOD}.submarine.upgrades.slot.depth": ("Depth slot: Depth Hull Mk1-Mk3 (crush depth Y -300 without it)",
                                                       "深度スロット: 深度船体 Mk1〜Mk3 (なしは耐圧深度 Y -300)"),
    f"message.{MOD}.submarine.depth": ("  ⬇ Y %s / %s", "  ⬇ Y %s / %s"),
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
for _id, (_en, _ja, _parent, _tex, _src_en, _src_ja) in TRP01_ITEMS.items():
    LANG[f"item.{MOD}.{_id}"] = (_en, _ja)
    LANG[f"item.{MOD}.{_id}.source"] = (_src_en, _src_ja)

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
        write(im(upgrade), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{ADV_ICON.get(upgrade, upgrade)}"}})
    for item, (_en, _ja, parent, tex, _src_en, _src_ja) in TRP01_ITEMS.items():   # TRP01: reuse an existing texture
        write(im(item), {"parent": parent, "textures": {"layer0": f"{MOD}:item/{tex}"}})
    for item in BOSS_ITEMS:
        write(im(item), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{item}"}})
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