# Abyssia 素材加工システム設計 (v1)

目的: 採掘 → 破砕 → 精錬 → 合金 → 中間部品 → 組立 → 最終品 の段階加工を、**新規GUIなし**(バニラ作業台/かまど/溶鉱炉/石切台/鍛冶台+破砕ハンマー)で実装する。機械は全て phase2。既存ID(素材/レシピ)は改名・削除・統合しない。生成元: `tools/material_spec.json`(機械可読) 本書はその要約。

- 新規アイテム: **56** 種 / 新規レシピ行: **81** (焼成はsmelting+blastingの2行) / 新規ツール 11 / 新規防具 3 / 機械 7 (phase2)

## 1. 設計原則

- 金属の専門性: 鉄=構造/基礎部品、銅=配線/配管、コバルト=高速/耐食/刃先、マンガン=高強度/硬化、モリブデン=耐熱、ニッケル=耐食/潜水/耐圧、白金=触媒/高性能エネルギー、テルル=導電/蓄電、タングステン=超硬/重量、バナジウム=合金強化、イットリウム=発光/結晶。
- 粉(破砕品)は『インゴットを消費せず合金量を増やす混ぜ物』にして、インゴットと役割を分ける。
- 合金は用途特化で、性能の重複を避ける。最強ツールに全素材を集中させない(ツールは素材ごとに専門化)。
- 破砕ハンマーは crafting remainder(耐久-1、消費されない)。
- 序盤は軽く: フェーズ1は鉄・銅・コバルト・ニッケル+植物系のみで、破砕/板/棒/歯車/コバルトツールまで完結。

## 2. 素材一覧

既存素材(変更なし)は `docs/materials.txt` 参照。以下は**新規**。

### 道具/ツール (11)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `crushing_hammer` | 破砕ハンマー | Crushing Hammer | common | 1 | tool |
| `cobalt_pickaxe` | コバルトのツルハシ | Cobalt Pickaxe | uncommon | 1 | tool |
| `cobalt_shovel` | コバルトのシャベル | Cobalt Shovel | uncommon | 1 | tool |
| `manganese_axe` | マンガンの斧 | Manganese Axe | uncommon | 2 | tool |
| `manganese_sword` | マンガンの剣 | Manganese Sword | uncommon | 2 | tool |
| `molybdenum_pickaxe` | モリブデンのツルハシ | Molybdenum Pickaxe | rare | 3 | tool |
| `tungsten_pickaxe` | タングステンのツルハシ | Tungsten Pickaxe | rare | 3 | tool |
| `tungsten_axe` | タングステンの斧 | Tungsten Axe | rare | 3 | tool |
| `abyssal_drill` | 深海ドリル | Abyssal Drill | epic | 5 | tool |
| `abyssal_cutter` | 深海カッター | Abyssal Cutter | epic | 5 | tool |
| `crystal_pickaxe` | 結晶ツルハシ | Crystal Pickaxe | rare | 4 | tool |

### 粉・精鉱 (11)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `iron_powder` | 鉄粉 | Iron Powder | common | 1 | material |
| `cobalt_powder` | コバルト粉 | Cobalt Powder | common | 1 | material |
| `nickel_powder` | ニッケル粉 | Nickel Powder | common | 1 | material |
| `manganese_powder` | マンガン粉 | Manganese Powder | common | 2 | material |
| `vanadium_powder` | バナジウム粉 | Vanadium Powder | uncommon | 3 | material |
| `tungsten_powder` | タングステン粉 | Tungsten Powder | uncommon | 3 | material |
| `tellurium_powder` | テルル粉 | Tellurium Powder | uncommon | 4 | material |
| `yttrium_powder` | イットリウム粉 | Yttrium Powder | uncommon | 4 | material |
| `cobalt_concentrate` | コバルト精鉱 | Cobalt Concentrate | common | 1 | material |
| `manganese_concentrate` | マンガン精鉱 | Manganese Concentrate | common | 2 | material |
| `nickel_concentrate` | ニッケル精鉱 | Nickel Concentrate | common | 2 | material |

### 基礎部品(鉄/銅) (4)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `iron_plate` | 鉄板 | Iron Plate | common | 1 | component |
| `iron_rod` | 鉄棒 | Iron Rod | common | 1 | component |
| `iron_gear` | 鉄歯車 | Iron Gear | common | 1 | component |
| `copper_wire` | 銅線 | Copper Wire | common | 1 | component |

### 合金 (6)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `corrosion_alloy_ingot` | 耐食合金インゴット | Corrosion Alloy Ingot | uncommon | 2 | material |
| `high_strength_alloy_ingot` | 高強度合金インゴット | High-Strength Alloy Ingot | uncommon | 3 | material |
| `heat_resistant_alloy_ingot` | 耐熱合金インゴット | Heat-Resistant Alloy Ingot | uncommon | 3 | material |
| `tungsten_alloy_ingot` | タングステン合金インゴット | Tungsten Alloy Ingot | rare | 3 | material |
| `conductive_alloy_ingot` | 導電合金インゴット | Conductive Alloy Ingot | rare | 4 | material |
| `thermal_alloy_ingot` | 熱合金インゴット | Thermal Alloy Ingot | rare | 5 | material |

### 結晶・エネルギー (7)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `luminous_crystal` | 発光結晶 | Luminous Crystal | rare | 4 | material |
| `crystal_core` | 結晶コア | Crystal Core | rare | 4 | component |
| `abyssal_energy_cell` | 深海エネルギーセル | Abyssal Energy Cell | rare | 4 | component |
| `advanced_lumen_cell` | 高度発光素子 | Advanced Lumen Cell | rare | 4 | component |
| `abyssal_light_core` | 深海光コア | Abyssal Light Core | epic | 5 | component |
| `advanced_energy_cell` | 高度エネルギーセル | Advanced Energy Cell | epic | 5 | component |
| `abyssal_power_core` | 深海動力コア | Abyssal Power Core | epic | 6 | component |

### 植物系 (4)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `reinforced_fiber` | 強化繊維 | Reinforced Fiber | common | 1 | material |
| `reinforced_cable` | 強化ケーブル | Reinforced Cable | uncommon | 2 | component |
| `marine_resin` | 海洋樹脂 | Marine Resin | uncommon | 1 | material |
| `abyssal_composite` | 深海複合材 | Abyssal Composite | rare | 2 | material |

### 熱系 (2)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `thermal_core` | 熱コア | Thermal Core | rare | 3 | component |
| `thermal_reagent` | 熱試薬 | Thermal Reagent | rare | 3 | material |

### 共有部品 (8)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `hardened_tip` | 硬化チップ | Hardened Tip | uncommon | 2 | component |
| `tungsten_tip` | タングステンチップ | Tungsten Tip | rare | 3 | component |
| `drill_head` | ドリルヘッド | Drill Head | rare | 3 | component |
| `pressure_valve` | 圧力バルブ | Pressure Valve | uncommon | 2 | component |
| `pressure_shell` | 耐圧殻 | Pressure Shell | epic | 6 | component |
| `thermal_component` | 熱部品 | Thermal Component | rare | 3 | component |
| `conductive_component` | 導電部品 | Conductive Component | rare | 4 | component |
| `machine_frame` | 機械枠 | Machine Frame | uncommon | 2 | component |

### 防具 (3)

| id | 日本語名 | English | 希少度 | フェーズ | 種別 |
|---|---|---|---|---|---|
| `dive_tank` | 潜水タンク | Dive Tank | uncommon | 2 | armor |
| `diving_suit_leggings` | 潜水スーツのレギンス | Diving Suit Leggings | uncommon | 2 | armor |
| `pressure_diver_helmet` | 耐圧潜水ヘルム | Pressure Diver Helmet | epic | 6 | armor |

## 3. 依存関係

```text
[鉱石/クラスト] --破砕ハンマー--> 粉(x2) / 精鉱(クラスト→x3) --かまど/溶鉱炉--> 粉 --> インゴット
鉄: iron_crust/raw_iron -> iron_powder -> iron_ingot -> iron_plate / iron_rod(石切台) -> iron_gear -> machine_frame
銅: copper_ingot/copper_crust -> copper_wire(石切台) -> reinforced_cable / pressure_valve / conductive_component
合金(作業台・形なし; インゴット+粉で x2):
  corrosion   = nickel_ingot + cobalt_powder + iron_powder
  high_str    = manganese_ingot + vanadium_powder + iron_powder
  heat_resist = molybdenum_ingot + nickel_powder + iron_powder
  tungsten_a  = tungsten_ingot + nickel_powder
  conductive  = copper_ingot + tellurium_powder
  thermal_a   = thermal_reagent + molybdenum_ingot + tungsten_powder
  (abyssal_alloy_ingot は既存のまま: vanadium+cobalt+nickel)
熱: sulfur + thermal_crystal_shard + molten_volcanic_rock + salt_crust -> thermal_reagent
    thermal_crystal_shard x3 + adhesive -> thermal_core; heat_resist + thermal_felt + thermal_core -> thermal_component
植物: deep_fiber -> reinforced_fiber; fiber_rope + copper_wire -> reinforced_cable; plant_resin + adhesive + salt_crust -> marine_resin
      reinforced_fiber + marine_resin + abyssal_crystal_shard -> abyssal_composite (グリップ/柄)
結晶: abyssal_crystal_shard x3 + adhesive -> crystal_core; yttrium_powder + shard + crystal_lens -> luminous_crystal
  lumen_cell + tellurium_ingot + crystal_core -> abyssal_energy_cell
  lumen_cell + lumen_gel + yttrium_ingot -> advanced_lumen_cell -> (+crystal_lens +platinum) -> abyssal_light_core
  abyssal_energy_cell + thermal_core + platinum -> advanced_energy_cell
  abyssal_power_core = advanced_energy_cell + abyssal_light_core + thermal_alloy_ingot + luminous_crystal  (循環回避: power_coreは終端)
部品: hardened_tip(cobalt+mn粉+板) -> tungsten_tip(+tungsten合金+W粉) -> drill_head(+高強度合金+歯車)
      pressure_valve(耐食合金+歯車+銅線+精製油) -> pressure_shell(+超深海殻板+海洋樹脂)
```

**クラストの役割**

- iron_crust: 破砕→iron_powder x3。copper_crust: 石切台→copper_wire x5(既存の直接精錬x2も維持)。
- cobalt/manganese/nickel_crust: 破砕→精鉱x3→粉→インゴット(既存の直接精錬x2も維持: 速い/少ない vs 遅い/多い+粉が得られる)。
- cave_mineral_crust: 破砕→硫黄x3(既存のかまど精錬x2より多い)。
- salt_crust: 硬化塩として marine_resin と thermal_reagent の材料(tag abyssia:crusts には追加しない: 粉末用途と分ける)。

## 4. レシピ一覧(要約)

全行: フェーズ / 作業台 / 入力 / 出力 / 焼成時間 / レシピid。`crushing_hammer` は破砕行で消費されずに耐久-1(表では省略せず入力に含む)。

### 破砕・精錬(金属) (35)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 1 | 作業台 | crushing_hammer + minecraft:raw_iron | iron_powder x2 | - | `iron_powder_from_raw` |
| 1 | かまど | iron_powder | minecraft:iron_ingot x1 | 200t | `iron_ingot_from_powder_smelting` |
| 1 | 溶鉱炉 | iron_powder | minecraft:iron_ingot x1 | 100t | `iron_ingot_from_powder_blasting` |
| 1 | 作業台 | crushing_hammer + raw_cobalt | cobalt_powder x2 | - | `cobalt_powder_from_raw` |
| 1 | かまど | cobalt_powder | cobalt_ingot x1 | 200t | `cobalt_ingot_from_powder_smelting` |
| 1 | 溶鉱炉 | cobalt_powder | cobalt_ingot x1 | 100t | `cobalt_ingot_from_powder_blasting` |
| 1 | 作業台 | crushing_hammer + raw_nickel | nickel_powder x2 | - | `nickel_powder_from_raw` |
| 1 | かまど | nickel_powder | nickel_ingot x1 | 200t | `nickel_ingot_from_powder_smelting` |
| 1 | 溶鉱炉 | nickel_powder | nickel_ingot x1 | 100t | `nickel_ingot_from_powder_blasting` |
| 2 | 作業台 | crushing_hammer + raw_manganese | manganese_powder x2 | - | `manganese_powder_from_raw` |
| 2 | かまど | manganese_powder | manganese_ingot x1 | 200t | `manganese_ingot_from_powder_smelting` |
| 2 | 溶鉱炉 | manganese_powder | manganese_ingot x1 | 100t | `manganese_ingot_from_powder_blasting` |
| 3 | 作業台 | crushing_hammer + raw_vanadium | vanadium_powder x2 | - | `vanadium_powder_from_raw` |
| 3 | かまど | vanadium_powder | vanadium_ingot x1 | 200t | `vanadium_ingot_from_powder_smelting` |
| 3 | 溶鉱炉 | vanadium_powder | vanadium_ingot x1 | 100t | `vanadium_ingot_from_powder_blasting` |
| 3 | 作業台 | crushing_hammer + raw_tungsten | tungsten_powder x2 | - | `tungsten_powder_from_raw` |
| 3 | かまど | tungsten_powder | tungsten_ingot x1 | 200t | `tungsten_ingot_from_powder_smelting` |
| 3 | 溶鉱炉 | tungsten_powder | tungsten_ingot x1 | 100t | `tungsten_ingot_from_powder_blasting` |
| 4 | 作業台 | crushing_hammer + raw_tellurium | tellurium_powder x2 | - | `tellurium_powder_from_raw` |
| 4 | かまど | tellurium_powder | tellurium_ingot x1 | 200t | `tellurium_ingot_from_powder_smelting` |
| 4 | 溶鉱炉 | tellurium_powder | tellurium_ingot x1 | 100t | `tellurium_ingot_from_powder_blasting` |
| 4 | 作業台 | crushing_hammer + raw_yttrium | yttrium_powder x2 | - | `yttrium_powder_from_raw` |
| 4 | かまど | yttrium_powder | yttrium_ingot x1 | 200t | `yttrium_ingot_from_powder_smelting` |
| 4 | 溶鉱炉 | yttrium_powder | yttrium_ingot x1 | 100t | `yttrium_ingot_from_powder_blasting` |
| 1 | 作業台 | crushing_hammer + cobalt_crust | cobalt_concentrate x3 | - | `cobalt_concentrate_from_crust` |
| 1 | かまど | cobalt_concentrate | cobalt_powder x1 | 200t | `cobalt_powder_from_concentrate_smelting` |
| 1 | 溶鉱炉 | cobalt_concentrate | cobalt_powder x1 | 100t | `cobalt_powder_from_concentrate_blasting` |
| 2 | 作業台 | crushing_hammer + manganese_crust | manganese_concentrate x3 | - | `manganese_concentrate_from_crust` |
| 2 | かまど | manganese_concentrate | manganese_powder x1 | 200t | `manganese_powder_from_concentrate_smelting` |
| 2 | 溶鉱炉 | manganese_concentrate | manganese_powder x1 | 100t | `manganese_powder_from_concentrate_blasting` |
| 1 | 作業台 | crushing_hammer + nickel_crust | nickel_concentrate x3 | - | `nickel_concentrate_from_crust` |
| 1 | かまど | nickel_concentrate | nickel_powder x1 | 200t | `nickel_powder_from_concentrate_smelting` |
| 1 | 溶鉱炉 | nickel_concentrate | nickel_powder x1 | 100t | `nickel_powder_from_concentrate_blasting` |
| 1 | 作業台 | crushing_hammer + iron_crust | iron_powder x3 | - | `iron_powder_from_iron_crust` |
| 1 | 作業台 | crushing_hammer + cave_mineral_crust | sulfur x3 | - | `sulfur_from_cave_mineral_crust` |

### 基礎部品 (7)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 1 | 作業台 | minecraft:iron_ingot x3 + minecraft:stick x2 | crushing_hammer x1 | - | `crushing_hammer` |
| 1 | 石切台 | minecraft:iron_ingot | iron_plate x1 | - | `iron_plate_from_iron_ingot` |
| 1 | 石切台 | minecraft:iron_ingot | iron_rod x2 | - | `iron_rod_from_iron_ingot` |
| 1 | 石切台 | minecraft:copper_ingot | copper_wire x3 | - | `copper_wire_from_copper_ingot` |
| 1 | 石切台 | copper_crust | copper_wire x5 | - | `copper_wire_from_copper_crust` |
| 1 | 作業台 | iron_rod x4 + iron_plate x1 | iron_gear x1 | - | `iron_gear` |
| 2 | 作業台 | iron_plate x4 + iron_rod x4 + iron_gear x1 | machine_frame x1 | - | `machine_frame` |

### 合金・素材 (7)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 2 | 作業台 | nickel_ingot + cobalt_powder + iron_powder | corrosion_alloy_ingot x2 | - | `corrosion_alloy_ingot` |
| 3 | 作業台 | manganese_ingot + vanadium_powder + iron_powder | high_strength_alloy_ingot x2 | - | `high_strength_alloy_ingot` |
| 3 | 作業台 | molybdenum_ingot + nickel_powder + iron_powder | heat_resistant_alloy_ingot x2 | - | `heat_resistant_alloy_ingot` |
| 3 | 作業台 | tungsten_ingot + nickel_powder | tungsten_alloy_ingot x2 | - | `tungsten_alloy_ingot` |
| 4 | 作業台 | minecraft:copper_ingot + tellurium_powder | conductive_alloy_ingot x2 | - | `conductive_alloy_ingot` |
| 5 | 作業台 | thermal_reagent + molybdenum_ingot + tungsten_powder | thermal_alloy_ingot x2 | - | `thermal_alloy_ingot` |
| 4 | 作業台 | yttrium_powder + abyssal_crystal_shard + crystal_lens | luminous_crystal x1 | - | `luminous_crystal` |

### 植物系 (4)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 1 | 作業台 | deep_fiber x3 + marine_adhesive | reinforced_fiber x2 | - | `reinforced_fiber` |
| 2 | 作業台 | copper_wire x1 + reinforced_fiber x1 + fiber_rope x1 | reinforced_cable x2 | - | `reinforced_cable` |
| 1 | 作業台 | plant_resin + marine_adhesive + salt_crust | marine_resin x2 | - | `marine_resin` |
| 2 | 作業台 | reinforced_fiber + marine_resin + abyssal_crystal_shard | abyssal_composite x2 | - | `abyssal_composite` |

### 結晶・熱・エネルギー (8)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 4 | 作業台 | abyssal_crystal_shard x3 + marine_adhesive x1 | crystal_core x1 | - | `crystal_core` |
| 3 | 作業台 | thermal_crystal_shard x3 + marine_adhesive x1 | thermal_core x1 | - | `thermal_core` |
| 3 | 作業台 | sulfur + thermal_crystal_shard + molten_volcanic_rock + salt_crust | thermal_reagent x2 | - | `thermal_reagent` |
| 4 | 作業台 | lumen_cell + tellurium_ingot + crystal_core | abyssal_energy_cell x1 | - | `abyssal_energy_cell` |
| 4 | 作業台 | lumen_cell + lumen_gel + yttrium_ingot | advanced_lumen_cell x1 | - | `advanced_lumen_cell` |
| 5 | 作業台 | advanced_lumen_cell + crystal_lens + platinum_ingot | abyssal_light_core x1 | - | `abyssal_light_core` |
| 5 | 作業台 | abyssal_energy_cell + thermal_core + platinum_ingot | advanced_energy_cell x1 | - | `advanced_energy_cell` |
| 6 | 作業台 | advanced_energy_cell + abyssal_light_core + thermal_alloy_ingot + luminous_crystal | abyssal_power_core x1 | - | `abyssal_power_core` |

### 共有部品 (7)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 2 | 作業台 | cobalt_ingot + manganese_powder + iron_plate | hardened_tip x2 | - | `hardened_tip` |
| 3 | 作業台 | tungsten_alloy_ingot + tungsten_powder + hardened_tip | tungsten_tip x2 | - | `tungsten_tip` |
| 3 | 作業台 | tungsten_tip x3 + high_strength_alloy_ingot x2 + iron_gear x1 | drill_head x1 | - | `drill_head` |
| 2 | 作業台 | iron_gear x1 + corrosion_alloy_ingot x2 + copper_wire x1 + refined_oil x1 | pressure_valve x1 | - | `pressure_valve` |
| 6 | 作業台 | corrosion_alloy_ingot x1 + hadal_plating x1 + marine_resin x1 + pressure_valve x1 | pressure_shell x1 | - | `pressure_shell` |
| 3 | 作業台 | heat_resistant_alloy_ingot + thermal_felt + thermal_core | thermal_component x1 | - | `thermal_component` |
| 4 | 作業台 | conductive_alloy_ingot + reinforced_cable + copper_wire | conductive_component x1 | - | `conductive_component` |

### ツール (10)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 1 | 作業台 | cobalt_ingot x2 + hardened_tip x1 + minecraft:stick x2 | cobalt_pickaxe x1 | - | `cobalt_pickaxe` |
| 1 | 作業台 | hardened_tip x1 + cobalt_ingot x1 + minecraft:stick x1 | cobalt_shovel x1 | - | `cobalt_shovel` |
| 3 | 作業台 | high_strength_alloy_ingot x1 + manganese_ingot x2 + minecraft:stick x2 | manganese_axe x1 | - | `manganese_axe` |
| 3 | 作業台 | high_strength_alloy_ingot x1 + manganese_ingot x1 + abyssal_composite x1 | manganese_sword x1 | - | `manganese_sword` |
| 3 | 作業台 | heat_resistant_alloy_ingot x2 + thermal_component x1 + minecraft:stick x2 | molybdenum_pickaxe x1 | - | `molybdenum_pickaxe` |
| 3 | 作業台 | tungsten_alloy_ingot x2 + tungsten_tip x1 + minecraft:stick x2 | tungsten_pickaxe x1 | - | `tungsten_pickaxe` |
| 3 | 作業台 | tungsten_alloy_ingot x2 + tungsten_tip x1 + minecraft:stick x2 | tungsten_axe x1 | - | `tungsten_axe` |
| 4 | 作業台 | crystal_core x2 + luminous_crystal x1 + abyssal_composite x1 + minecraft:stick x1 | crystal_pickaxe x1 | - | `crystal_pickaxe` |
| 5 | 作業台 | tungsten_tip x2 + abyssal_energy_cell x1 + conductive_component x1 + abyssal_composite x1 | abyssal_cutter x1 | - | `abyssal_cutter` |
| 5 | 作業台 | drill_head x1 + thermal_component x1 + conductive_component x1 + machine_frame x1 + advanced_energy_cell x1 | abyssal_drill x1 | - | `abyssal_drill` |

### 防具 (3)

| P | 作業台 | 入力 | 出力 | 時間 | id |
|---|---|---|---|---|---|
| 2 | 作業台 | corrosion_alloy_ingot x6 + pressure_valve x1 + reinforced_cable x1 | dive_tank x1 | - | `dive_tank` |
| 2 | 作業台 | corrosion_alloy_ingot x3 + sea_cloth x4 | diving_suit_leggings x1 | - | `diving_suit_leggings` |
| 6 | 鍛冶台 | hadal_plating(型) + deep_diver_helmet + pressure_shell | pressure_diver_helmet x1 | - | `pressure_diver_helmet_upgrade` |

## 5. ツール一覧

既存: abyssal_alloy 5種(uses1800/speed9/+3.5/ench18)は据え置き。以下は差別化ツール。ctor値(attack_damage/attack_speed)はバニラコンストラクタ引数。

| id | 素材 | 耐久 | 速度 | 攻撃補正 | エンチャ | 攻撃dmg/速度 | 特殊効果 |
|---|---|---|---|---|---|---|---|
| `crushing_hammer` | iron | 250 | 1.0 | 0.0 | 5 | 2 / -3.0 | crafting_remainder: 破砕クラフトで耐久-1(破損で消滅)。通常のブロック破壊は不可 |
| `cobalt_pickaxe` | cobalt | 420 | 11.0 | 2.0 | 16 | 1 / -2.6 | fast_dig |
| `cobalt_shovel` | cobalt | 420 | 11.0 | 2.0 | 16 | 1.5 / -3.0 | fast_dig |
| `manganese_axe` | manganese | 2600 | 6.0 | 3.0 | 8 | 5.5 / -3.0 | none |
| `manganese_sword` | manganese | 2600 | 6.0 | 3.0 | 8 | 3 / -2.4 | none |
| `molybdenum_pickaxe` | molybdenum | 1300 | 7.5 | 2.5 | 12 | 1 / -2.8 | heat_guard: 手持ち中は炎上/溶岩/マグマブロック/熱噴出孔の被ダメージ50%減、アイテム自体が溶岩で燃えない(fireResistant) |
| `tungsten_pickaxe` | tungsten | 3200 | 7.0 | 3.5 | 10 | 1 / -3.1 | hard_block_bonus: destroy hardness>=3(黒曜石/深層岩/鉱石)の採掘速度x1.5 (BreakSpeedイベント) |
| `tungsten_axe` | tungsten | 3200 | 7.0 | 3.5 | 10 | 6.5 / -3.3 | heavy: 攻撃速度低下(-3.3)、クールダウン中断で追加ノックバック無し。hard_block_bonusは持たない |
| `crystal_pickaxe` | crystal | 900 | 8.0 | 2.0 | 22 | 1 / -2.8 | crystal_harvest: タグabyssia:crystal_blocksのブロックをシルクタッチ相当で回収、クラスターは破壊速度x2 (loot modifier + BreakSpeed) |
| `abyssal_drill` | drill | 2200 | 12.0 | 2.0 | 14 | 1 / -3.0 | drill: 石/岩/鉱石系で縦2ブロック同時破壊(+1下)。underwater_toolsタグ。耐久消費x1.5。エネルギー消費はphase2 |
| `abyssal_cutter` | cutter | 1500 | 7.0 | 1.5 | 20 | 2 / -1.8 | plant_cut: ハサミ相当(植物/コンブ/ロープ系を即時カット・ハサミ判定)、深海繊維ドロップ+1、underwater_toolsタグ |

方針: コバルト=高速・低耐久、マンガン=高耐久・低速、モリブデン=耐熱、タングステン=硬いブロック特化・超高耐久・重い、結晶=結晶回収、ドリル=縦2ブロック、カッター=植物即断。全て harvest tag は `minecraft:needs_diamond_tool`(既存alloyと同等)、水中ペナルティ緩和が要るものは `abyssia:underwater_tools` に追加(abyssal_drill/abyssal_cutter/crystal_pickaxe/cobalt系)。

## 6. 装備一覧

| id | スロット | 防御 | 靭性 | 耐久 | エンチャ | 特殊 |
|---|---|---|---|---|---|---|
| `deep_diver_helmet`(既存) | head | 3 | 2.0 | 330 | 18 | 水中呼吸+暗視(Config) |
| `abyssal_flippers`(既存) | feet | 3 | 2.0 | 390 | 18 | 泳ぎ速度+35% |
| `dive_tank` | chest | 6 | 1.5 | 448 | 12 | tank_breathing: 水中で水中呼吸(220t更新、暗視なし)。ヘルム+レギンス+フィン+タンクの4点で泳ぎ速度+10% |
| `diving_suit_leggings` | legs | 5 | 1.5 | 420 | 12 | suit_swim: 泳ぎ速度+5%(ForgeMod.SWIM_SPEED MULTIPLY_TOTAL 0.05) |
| `pressure_diver_helmet` | head | 4 | 3.0 | 462 | 15 | deep_diver_helmet継承(水中呼吸+暗視Config)+hadal_pressure:将来の超深海圧力ダメージ免除フラグ(現状は装備タグabyssia:pressure_proof) |

潜水セット = ヘルム(deep_diver_helmet→pressure_diver_helmetへ鍛冶台で改良; 元レシピは残す) + dive_tank + diving_suit_leggings + abyssal_flippers。鍛冶台テンプレートは既存の `hadal_plating` を流用(新規テンプレート不要)。消耗品は今回追加なし(open question)。

## 7. 機械一覧 (phase2: 今回は未実装)

| id | 名称 | 用途 | 入力 | 置換対象 | 建造コスト |
|---|---|---|---|---|---|
| `crusher` | 粉砕機 | 原石/クラスト→粉/精鉱を自動化(x2.5得率) | raw_*, *_crust | crushing_hammer | machine_frame+drill_head |
| `refinery_furnace` | 精錬炉 | 精鉱→粉→インゴットを一括、燃料効率向上 | *_concentrate, *_powder | furnace/blast_furnace | machine_frame+thermal_component |
| `alloy_furnace` | 合金炉 | 合金をインゴット+粉2種から自動生成、歩留まり+50% | *_ingot, *_powder | alloy crafting | machine_frame+heat_resistant_alloy_ingot |
| `crystal_processor` | 結晶加工機 | 結晶コア/発光結晶/レンズの加工 | abyssal_crystal_shard, crystal_sap, luminous_crystal | crystal crafting | machine_frame+crystal_core |
| `high_temp_furnace` | 高温炉 | 熱試薬/熱合金/タングステン系 | thermal_reagent, tungsten_powder, molybdenum_ingot | blast furnace | machine_frame+thermal_alloy_ingot |
| `energy_device` | エネルギー装置 | エネルギーセル充填/動力供給(ツールへの給電) | abyssal_energy_cell, advanced_energy_cell, abyssal_power_core | none | machine_frame+conductive_component |
| `mining_machine` | 採掘機 | 設置型自動採掘。ドリルヘッドで岩盤外を掘削 | drill_head, abyssal_power_core | none | machine_frame+abyssal_drill |

今回は機械無しで全工程が完結する。phase2 で機械が同じレシピを高得率で自動化する(`machine_frame` は今回から入手可能)。

## 8. 使用率チェック表

列: 素材 / 経由中間物(最短経路) / 最終品 / 数量(その素材が最初の消費レシピで必要な個数) / 用途数(最終品の種類数)。最終品 = ツール・防具・abyssal_power_core・バニラ品。判定基準: 金属>=3(白金/テルル/バナジウム/イットリウム>=2)、植物/結晶/熱/クラスト各カテゴリの最終品>=3。

### 8.1 サマリ

| 素材 | 区分 | 用途数 | 必要数 | 判定 |
|---|---|---|---|---|
| iron | metal | 13 | 3 | OK |
| copper | metal | 4 | 3 | OK |
| cobalt | metal | 17 | 3 | OK |
| manganese | metal | 9 | 3 | OK |
| molybdenum | metal | 3 | 3 | OK |
| nickel | metal | 15 | 3 | OK |
| platinum | metal | 2 | 2 | OK |
| tellurium | metal | 3 | 2 | OK |
| tungsten | metal | 5 | 3 | OK |
| vanadium | metal | 10 | 2 | OK |
| yttrium | metal | 2 | 2 | OK |
| deep_fiber | plant | 11 | 1 | OK |
| plant_resin | plant | 13 | 1 | OK |
| marine_adhesive | plant | 13 | 1 | OK |
| sea_cloth | plant | 4 | 1 | OK |
| fiber_rope | plant | 5 | 1 | OK |
| bio_oil | plant | 4 | 1 | OK |
| refined_oil | plant | 3 | 1 | OK |
| hard_stalk | plant | 11 | 1 | OK |
| organic_matter | plant | 6 | 1 | OK |
| deep_pigment | plant | 3 | 1 | OK |
| abyssal_crystal_shard | crystal | 5 | 1 | OK |
| thermal_crystal_shard | thermal | 3 | 1 | OK |
| crystal_sap | crystal | 5 | 1 | OK |
| crystal_lens | crystal | 4 | 1 | OK |
| lumen_gel | crystal | 7 | 1 | OK |
| lumen_cell | crystal | 5 | 1 | OK |
| thermal_fiber | thermal | 4 | 1 | OK |
| thermal_felt | thermal | 4 | 1 | OK |
| hadal_husk | other | 3 | 1 | OK |
| sulfur | thermal | 4 | 1 | OK |
| molten_volcanic_rock | thermal | 1 | 1 | OK |
| salt_crust | crust | 5 | 1 | OK |
| cave_mineral_crust | crust | 4 | 1 | OK |
| iron_crust | crust | 13 | 1 | OK |
| copper_crust | crust | 4 | 1 | OK |
| cobalt_crust | crust | 16 | 1 | OK |
| manganese_crust | crust | 8 | 1 | OK |
| nickel_crust | crust | 15 | 1 | OK |
| crust_powder | other | 16 | 1 | OK |

### 8.2 詳細(素材, 中間, 最終品, 数量)

| 素材 | 中間 | 最終品 | 数量 | 用途数 |
|---|---|---|---|---|
| iron | iron_plate > hardened_tip > tungsten_tip | abyssal_cutter | 1 | 13 |
|  | iron_plate > machine_frame | abyssal_drill | 1 |  |
|  | iron_plate > hardened_tip | cobalt_pickaxe | 1 |  |
|  | iron_plate > hardened_tip | cobalt_shovel | 1 |  |
|  | - | crushing_hammer | 3 |  |
|  | corrosion_alloy_ingot | dive_tank | 1 |  |
|  | corrosion_alloy_ingot | diving_suit_leggings | 1 |  |
|  | high_strength_alloy_ingot | manganese_axe | 1 |  |
|  | high_strength_alloy_ingot | manganese_sword | 1 |  |
|  | heat_resistant_alloy_ingot | molybdenum_pickaxe | 1 |  |
|  | corrosion_alloy_ingot > pressure_shell | pressure_diver_helmet | 1 |  |
|  | iron_plate > hardened_tip > tungsten_tip | tungsten_axe | 1 |  |
|  | iron_plate > hardened_tip > tungsten_tip | tungsten_pickaxe | 1 |  |
| copper | copper_wire > conductive_component | abyssal_cutter | 1 | 4 |
|  | copper_wire > conductive_component | abyssal_drill | 1 |  |
|  | copper_wire > reinforced_cable | dive_tank | 1 |  |
|  | copper_wire > pressure_valve > pressure_shell | pressure_diver_helmet | 1 |  |
| cobalt | abyssal_alloy_ingot | abyssal_alloy_axe | 1 | 17 |
|  | abyssal_alloy_ingot | abyssal_alloy_hoe | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_pickaxe | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_shovel | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_sword | 1 |  |
|  | hardened_tip > tungsten_tip | abyssal_cutter | 1 |  |
|  | hardened_tip > tungsten_tip > drill_head | abyssal_drill | 1 |  |
|  | abyssal_alloy_ingot | abyssal_flippers | 1 |  |
|  | - | cobalt_pickaxe | 2 |  |
|  | - | cobalt_shovel | 1 |  |
|  | abyssal_alloy_ingot | deep_diver_helmet | 1 |  |
|  | corrosion_alloy_ingot | dive_tank | 1 |  |
|  | corrosion_alloy_ingot | diving_suit_leggings | 1 |  |
|  | - | minecraft:blue_dye | 1 |  |
|  | corrosion_alloy_ingot > pressure_shell | pressure_diver_helmet | 1 |  |
|  | hardened_tip > tungsten_tip | tungsten_axe | 1 |  |
|  | hardened_tip > tungsten_tip | tungsten_pickaxe | 1 |  |
| manganese | hardened_tip > tungsten_tip | abyssal_cutter | 1 | 9 |
|  | high_strength_alloy_ingot > drill_head | abyssal_drill | 1 |  |
|  | hardened_tip | cobalt_pickaxe | 1 |  |
|  | hardened_tip | cobalt_shovel | 1 |  |
|  | - | manganese_axe | 2 |  |
|  | - | manganese_sword | 1 |  |
|  | - | minecraft:black_dye | 1 |  |
|  | hardened_tip > tungsten_tip | tungsten_axe | 1 |  |
|  | hardened_tip > tungsten_tip | tungsten_pickaxe | 1 |  |
| molybdenum | heat_resistant_alloy_ingot > thermal_component | abyssal_drill | 1 | 3 |
|  | thermal_alloy_ingot | abyssal_power_core | 1 |  |
|  | heat_resistant_alloy_ingot | molybdenum_pickaxe | 1 |  |
| nickel | abyssal_alloy_ingot | abyssal_alloy_axe | 1 | 15 |
|  | abyssal_alloy_ingot | abyssal_alloy_hoe | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_pickaxe | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_shovel | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_sword | 1 |  |
|  | tungsten_alloy_ingot > tungsten_tip | abyssal_cutter | 1 |  |
|  | heat_resistant_alloy_ingot > thermal_component | abyssal_drill | 1 |  |
|  | abyssal_alloy_ingot | abyssal_flippers | 1 |  |
|  | abyssal_alloy_ingot | deep_diver_helmet | 1 |  |
|  | corrosion_alloy_ingot | dive_tank | 1 |  |
|  | corrosion_alloy_ingot | diving_suit_leggings | 1 |  |
|  | heat_resistant_alloy_ingot | molybdenum_pickaxe | 1 |  |
|  | corrosion_alloy_ingot > pressure_shell | pressure_diver_helmet | 1 |  |
|  | tungsten_alloy_ingot | tungsten_axe | 1 |  |
|  | tungsten_alloy_ingot | tungsten_pickaxe | 1 |  |
| platinum | advanced_energy_cell | abyssal_drill | 1 | 2 |
|  | abyssal_light_core | abyssal_power_core | 1 |  |
| tellurium | abyssal_energy_cell | abyssal_cutter | 1 | 3 |
|  | abyssal_energy_cell > advanced_energy_cell | abyssal_drill | 1 |  |
|  | abyssal_energy_cell > advanced_energy_cell | abyssal_power_core | 1 |  |
| tungsten | tungsten_tip | abyssal_cutter | 1 | 5 |
|  | tungsten_tip > drill_head | abyssal_drill | 1 |  |
|  | thermal_alloy_ingot | abyssal_power_core | 1 |  |
|  | tungsten_alloy_ingot | tungsten_axe | 1 |  |
|  | tungsten_alloy_ingot | tungsten_pickaxe | 1 |  |
| vanadium | abyssal_alloy_ingot | abyssal_alloy_axe | 1 | 10 |
|  | abyssal_alloy_ingot | abyssal_alloy_hoe | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_pickaxe | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_shovel | 1 |  |
|  | abyssal_alloy_ingot | abyssal_alloy_sword | 1 |  |
|  | high_strength_alloy_ingot > drill_head | abyssal_drill | 1 |  |
|  | abyssal_alloy_ingot | abyssal_flippers | 1 |  |
|  | abyssal_alloy_ingot | deep_diver_helmet | 1 |  |
|  | high_strength_alloy_ingot | manganese_axe | 1 |  |
|  | high_strength_alloy_ingot | manganese_sword | 1 |  |
| yttrium | luminous_crystal | abyssal_power_core | 1 | 2 |
|  | luminous_crystal | crystal_pickaxe | 1 |  |
| deep_fiber | reinforced_fiber > abyssal_composite | abyssal_cutter | 3 | 11 |
|  | reinforced_fiber > reinforced_cable > conductive_component | abyssal_drill | 3 |  |
|  | sea_cloth | abyssal_flippers | 1 |  |
|  | reinforced_fiber > abyssal_composite | crystal_pickaxe | 3 |  |
|  | reinforced_fiber > reinforced_cable | dive_tank | 3 |  |
|  | sea_cloth | diving_suit_leggings | 1 |  |
|  | reinforced_fiber > abyssal_composite | manganese_sword | 3 |  |
|  | fiber_rope | minecraft:lead | 1 |  |
|  | sea_cloth | minecraft:leather | 1 |  |
|  | fiber_rope | minecraft:scaffolding | 1 |  |
|  | sea_cloth | minecraft:white_wool | 1 |  |
| plant_resin | marine_resin > abyssal_composite | abyssal_cutter | 1 | 13 |
|  | marine_adhesive > thermal_core > advanced_energy_cell | abyssal_drill | 1 |  |
|  | marine_adhesive > thermal_core > advanced_energy_cell | abyssal_power_core | 1 |  |
|  | marine_resin > abyssal_composite | crystal_pickaxe | 1 |  |
|  | marine_adhesive > reinforced_fiber > reinforced_cable | dive_tank | 1 |  |
|  | marine_resin > abyssal_composite | manganese_sword | 1 |  |
|  | marine_adhesive > hadal_plating | minecraft:conduit | 1 |  |
|  | marine_adhesive > lumen_cell | minecraft:redstone_lamp | 1 |  |
|  | marine_adhesive > lumen_cell | minecraft:sea_lantern | 1 |  |
|  | marine_adhesive | minecraft:sticky_piston | 1 |  |
|  | marine_adhesive > hadal_plating | minecraft:turtle_helmet | 1 |  |
|  | marine_adhesive > thermal_core > thermal_component | molybdenum_pickaxe | 1 |  |
|  | marine_resin > pressure_shell | pressure_diver_helmet | 1 |  |
| marine_adhesive | reinforced_fiber > abyssal_composite | abyssal_cutter | 1 | 13 |
|  | thermal_core > advanced_energy_cell | abyssal_drill | 1 |  |
|  | thermal_core > advanced_energy_cell | abyssal_power_core | 1 |  |
|  | crystal_core | crystal_pickaxe | 1 |  |
|  | reinforced_fiber > reinforced_cable | dive_tank | 1 |  |
|  | reinforced_fiber > abyssal_composite | manganese_sword | 1 |  |
|  | hadal_plating | minecraft:conduit | 1 |  |
|  | lumen_cell | minecraft:redstone_lamp | 1 |  |
|  | lumen_cell | minecraft:sea_lantern | 1 |  |
|  | - | minecraft:sticky_piston | 1 |  |
|  | hadal_plating | minecraft:turtle_helmet | 1 |  |
|  | thermal_core > thermal_component | molybdenum_pickaxe | 1 |  |
|  | hadal_plating | pressure_diver_helmet | 1 |  |
| sea_cloth | - | abyssal_flippers | 1 | 4 |
|  | - | diving_suit_leggings | 4 |  |
|  | - | minecraft:leather | 1 |  |
|  | - | minecraft:white_wool | 1 |  |
| fiber_rope | reinforced_cable > conductive_component | abyssal_cutter | 1 | 5 |
|  | reinforced_cable > conductive_component | abyssal_drill | 1 |  |
|  | reinforced_cable | dive_tank | 1 |  |
|  | - | minecraft:lead | 1 |  |
|  | - | minecraft:scaffolding | 1 |  |
| bio_oil | refined_oil > pressure_valve | dive_tank | 1 | 4 |
|  | refined_oil | minecraft:fire_charge | 1 |  |
|  | - | minecraft:torch | 1 |  |
|  | refined_oil > pressure_valve > pressure_shell | pressure_diver_helmet | 1 |  |
| refined_oil | pressure_valve | dive_tank | 1 | 3 |
|  | - | minecraft:fire_charge | 1 |  |
|  | pressure_valve > pressure_shell | pressure_diver_helmet | 1 |  |
| hard_stalk | minecraft:stick | cobalt_pickaxe | 1 | 11 |
|  | minecraft:stick | cobalt_shovel | 1 |  |
|  | minecraft:stick | crushing_hammer | 1 |  |
|  | minecraft:stick | crystal_pickaxe | 1 |  |
|  | minecraft:stick | manganese_axe | 1 |  |
|  | - | minecraft:campfire | 1 |  |
|  | - | minecraft:scaffolding | 1 |  |
|  | - | minecraft:torch | 1 |  |
|  | minecraft:stick | molybdenum_pickaxe | 1 |  |
|  | minecraft:stick | tungsten_axe | 1 |  |
|  | minecraft:stick | tungsten_pickaxe | 1 |  |
| organic_matter | deep_pigment | minecraft:black_dye | 1 | 6 |
|  | deep_pigment | minecraft:blue_dye | 1 |  |
|  | - | minecraft:bone_meal | 1 |  |
|  | - | minecraft:gunpowder | 1 |  |
|  | - | minecraft:packed_mud | 1 |  |
|  | deep_pigment | minecraft:yellow_dye | 1 |  |
| deep_pigment | - | minecraft:black_dye | 1 | 3 |
|  | - | minecraft:blue_dye | 1 |  |
|  | - | minecraft:yellow_dye | 1 |  |
| abyssal_crystal_shard | abyssal_composite | abyssal_cutter | 1 | 5 |
|  | crystal_core > abyssal_energy_cell > advanced_energy_cell | abyssal_drill | 3 |  |
|  | luminous_crystal | abyssal_power_core | 1 |  |
|  | luminous_crystal | crystal_pickaxe | 1 |  |
|  | abyssal_composite | manganese_sword | 1 |  |
| thermal_crystal_shard | thermal_core > advanced_energy_cell | abyssal_drill | 3 | 3 |
|  | thermal_core > advanced_energy_cell | abyssal_power_core | 3 |  |
|  | thermal_core > thermal_component | molybdenum_pickaxe | 3 |  |
| crystal_sap | crystal_lens > luminous_crystal | abyssal_power_core | 1 | 5 |
|  | crystal_lens > luminous_crystal | crystal_pickaxe | 1 |  |
|  | crystal_lens | minecraft:daylight_detector | 1 |  |
|  | crystal_lens | minecraft:spyglass | 1 |  |
|  | - | minecraft:tinted_glass | 1 |  |
| crystal_lens | luminous_crystal | abyssal_power_core | 1 | 4 |
|  | luminous_crystal | crystal_pickaxe | 1 |  |
|  | - | minecraft:daylight_detector | 1 |  |
|  | - | minecraft:spyglass | 1 |  |
| lumen_gel | lumen_cell > abyssal_energy_cell | abyssal_cutter | 1 | 7 |
|  | lumen_cell > abyssal_energy_cell > advanced_energy_cell | abyssal_drill | 1 |  |
|  | advanced_lumen_cell > abyssal_light_core | abyssal_power_core | 1 |  |
|  | - | minecraft:glow_item_frame | 1 |  |
|  | - | minecraft:glowstone_dust | 1 |  |
|  | lumen_cell | minecraft:redstone_lamp | 1 |  |
|  | lumen_cell | minecraft:sea_lantern | 1 |  |
| lumen_cell | abyssal_energy_cell | abyssal_cutter | 1 | 5 |
|  | abyssal_energy_cell > advanced_energy_cell | abyssal_drill | 1 |  |
|  | abyssal_energy_cell > advanced_energy_cell | abyssal_power_core | 1 |  |
|  | - | minecraft:redstone_lamp | 1 |  |
|  | - | minecraft:sea_lantern | 1 |  |
| thermal_fiber | thermal_felt > thermal_component | abyssal_drill | 1 | 4 |
|  | thermal_felt | minecraft:blast_furnace | 1 |  |
|  | thermal_felt | minecraft:campfire | 1 |  |
|  | thermal_felt > thermal_component | molybdenum_pickaxe | 1 |  |
| thermal_felt | thermal_component | abyssal_drill | 1 | 4 |
|  | - | minecraft:blast_furnace | 1 |  |
|  | - | minecraft:campfire | 1 |  |
|  | thermal_component | molybdenum_pickaxe | 1 |  |
| hadal_husk | hadal_plating | minecraft:conduit | 1 | 3 |
|  | hadal_plating | minecraft:turtle_helmet | 1 |  |
|  | hadal_plating | pressure_diver_helmet | 1 |  |
| sulfur | thermal_reagent > thermal_alloy_ingot | abyssal_power_core | 1 | 4 |
|  | - | minecraft:fire_charge | 1 |  |
|  | - | minecraft:gunpowder | 1 |  |
|  | - | minecraft:yellow_dye | 1 |  |
| molten_volcanic_rock | thermal_reagent > thermal_alloy_ingot | abyssal_power_core | 1 | 1 |
| salt_crust | marine_resin > abyssal_composite | abyssal_cutter | 1 | 5 |
|  | thermal_reagent > thermal_alloy_ingot | abyssal_power_core | 1 |  |
|  | marine_resin > abyssal_composite | crystal_pickaxe | 1 |  |
|  | marine_resin > abyssal_composite | manganese_sword | 1 |  |
|  | marine_resin > pressure_shell | pressure_diver_helmet | 1 |  |
| cave_mineral_crust | sulfur > thermal_reagent > thermal_alloy_ingot | abyssal_power_core | 1 | 4 |
|  | sulfur | minecraft:fire_charge | 1 |  |
|  | sulfur | minecraft:gunpowder | 1 |  |
|  | sulfur | minecraft:yellow_dye | 1 |  |
| iron_crust | minecraft:iron_ingot > iron_plate > hardened_tip > tungsten_tip | abyssal_cutter | 1 | 13 |
|  | iron_powder > high_strength_alloy_ingot > drill_head | abyssal_drill | 1 |  |
|  | minecraft:iron_ingot > iron_plate > hardened_tip | cobalt_pickaxe | 1 |  |
|  | minecraft:iron_ingot > iron_plate > hardened_tip | cobalt_shovel | 1 |  |
|  | minecraft:iron_ingot | crushing_hammer | 1 |  |
|  | iron_powder > corrosion_alloy_ingot | dive_tank | 1 |  |
|  | iron_powder > corrosion_alloy_ingot | diving_suit_leggings | 1 |  |
|  | iron_powder > high_strength_alloy_ingot | manganese_axe | 1 |  |
|  | iron_powder > high_strength_alloy_ingot | manganese_sword | 1 |  |
|  | iron_powder > heat_resistant_alloy_ingot | molybdenum_pickaxe | 1 |  |
|  | iron_powder > corrosion_alloy_ingot > pressure_shell | pressure_diver_helmet | 1 |  |
|  | minecraft:iron_ingot > iron_plate > hardened_tip > tungsten_tip | tungsten_axe | 1 |  |
|  | minecraft:iron_ingot > iron_plate > hardened_tip > tungsten_tip | tungsten_pickaxe | 1 |  |
| copper_crust | copper_wire > conductive_component | abyssal_cutter | 1 | 4 |
|  | copper_wire > conductive_component | abyssal_drill | 1 |  |
|  | copper_wire > reinforced_cable | dive_tank | 1 |  |
|  | copper_wire > pressure_valve > pressure_shell | pressure_diver_helmet | 1 |  |
| cobalt_crust | cobalt_ingot > abyssal_alloy_ingot | abyssal_alloy_axe | 1 | 16 |
|  | cobalt_ingot > abyssal_alloy_ingot | abyssal_alloy_hoe | 1 |  |
|  | cobalt_ingot > abyssal_alloy_ingot | abyssal_alloy_pickaxe | 1 |  |
|  | cobalt_ingot > abyssal_alloy_ingot | abyssal_alloy_shovel | 1 |  |
|  | cobalt_ingot > abyssal_alloy_ingot | abyssal_alloy_sword | 1 |  |
|  | cobalt_ingot > hardened_tip > tungsten_tip | abyssal_cutter | 1 |  |
|  | cobalt_ingot > hardened_tip > tungsten_tip > drill_head | abyssal_drill | 1 |  |
|  | cobalt_ingot > abyssal_alloy_ingot | abyssal_flippers | 1 |  |
|  | cobalt_ingot | cobalt_pickaxe | 1 |  |
|  | cobalt_ingot | cobalt_shovel | 1 |  |
|  | cobalt_ingot > abyssal_alloy_ingot | deep_diver_helmet | 1 |  |
|  | cobalt_concentrate > cobalt_powder > corrosion_alloy_ingot | dive_tank | 1 |  |
|  | cobalt_concentrate > cobalt_powder > corrosion_alloy_ingot | diving_suit_leggings | 1 |  |
|  | cobalt_concentrate > cobalt_powder > corrosion_alloy_ingot > pressure_shell | pressure_diver_helmet | 1 |  |
|  | cobalt_ingot > hardened_tip > tungsten_tip | tungsten_axe | 1 |  |
|  | cobalt_ingot > hardened_tip > tungsten_tip | tungsten_pickaxe | 1 |  |
| manganese_crust | manganese_concentrate > manganese_powder > hardened_tip > tungsten_tip | abyssal_cutter | 1 | 8 |
|  | manganese_ingot > high_strength_alloy_ingot > drill_head | abyssal_drill | 1 |  |
|  | manganese_concentrate > manganese_powder > hardened_tip | cobalt_pickaxe | 1 |  |
|  | manganese_concentrate > manganese_powder > hardened_tip | cobalt_shovel | 1 |  |
|  | manganese_ingot | manganese_axe | 1 |  |
|  | manganese_ingot | manganese_sword | 1 |  |
|  | manganese_concentrate > manganese_powder > hardened_tip > tungsten_tip | tungsten_axe | 1 |  |
|  | manganese_concentrate > manganese_powder > hardened_tip > tungsten_tip | tungsten_pickaxe | 1 |  |
| nickel_crust | nickel_ingot > abyssal_alloy_ingot | abyssal_alloy_axe | 1 | 15 |
|  | nickel_ingot > abyssal_alloy_ingot | abyssal_alloy_hoe | 1 |  |
|  | nickel_ingot > abyssal_alloy_ingot | abyssal_alloy_pickaxe | 1 |  |
|  | nickel_ingot > abyssal_alloy_ingot | abyssal_alloy_shovel | 1 |  |
|  | nickel_ingot > abyssal_alloy_ingot | abyssal_alloy_sword | 1 |  |
|  | nickel_concentrate > nickel_powder > tungsten_alloy_ingot > tungsten_tip | abyssal_cutter | 1 |  |
|  | nickel_concentrate > nickel_powder > heat_resistant_alloy_ingot > thermal_component | abyssal_drill | 1 |  |
|  | nickel_ingot > abyssal_alloy_ingot | abyssal_flippers | 1 |  |
|  | nickel_ingot > abyssal_alloy_ingot | deep_diver_helmet | 1 |  |
|  | nickel_ingot > corrosion_alloy_ingot | dive_tank | 1 |  |
|  | nickel_ingot > corrosion_alloy_ingot | diving_suit_leggings | 1 |  |
|  | nickel_concentrate > nickel_powder > heat_resistant_alloy_ingot | molybdenum_pickaxe | 1 |  |
|  | nickel_ingot > corrosion_alloy_ingot > pressure_shell | pressure_diver_helmet | 1 |  |
|  | nickel_concentrate > nickel_powder > tungsten_alloy_ingot | tungsten_axe | 1 |  |
|  | nickel_concentrate > nickel_powder > tungsten_alloy_ingot | tungsten_pickaxe | 1 |  |
| crust_powder | marine_adhesive > reinforced_fiber > abyssal_composite | abyssal_cutter | 1 | 16 |
|  | marine_adhesive > thermal_core > advanced_energy_cell | abyssal_drill | 1 |  |
|  | marine_adhesive > thermal_core > advanced_energy_cell | abyssal_power_core | 1 |  |
|  | marine_adhesive > crystal_core | crystal_pickaxe | 1 |  |
|  | marine_adhesive > reinforced_fiber > reinforced_cable | dive_tank | 1 |  |
|  | marine_adhesive > reinforced_fiber > abyssal_composite | manganese_sword | 1 |  |
|  | deep_pigment | minecraft:black_dye | 1 |  |
|  | deep_pigment | minecraft:blue_dye | 1 |  |
|  | marine_adhesive > hadal_plating | minecraft:conduit | 1 |  |
|  | marine_adhesive > lumen_cell | minecraft:redstone_lamp | 1 |  |
|  | marine_adhesive > lumen_cell | minecraft:sea_lantern | 1 |  |
|  | marine_adhesive | minecraft:sticky_piston | 1 |  |
|  | marine_adhesive > hadal_plating | minecraft:turtle_helmet | 1 |  |
|  | deep_pigment | minecraft:yellow_dye | 1 |  |
|  | marine_adhesive > thermal_core > thermal_component | molybdenum_pickaxe | 1 |  |
|  | marine_adhesive > hadal_plating | pressure_diver_helmet | 1 |  |

### 8.3 カテゴリ別の最終品数

- plant: 16 種 (abyssal_cutter, abyssal_drill, abyssal_flippers, abyssal_power_core, cobalt_pickaxe, cobalt_shovel, crushing_hammer, crystal_pickaxe...)
- crystal: 5 種 (abyssal_cutter, abyssal_drill, abyssal_power_core, crystal_pickaxe, manganese_sword)
- thermal: 3 種 (abyssal_drill, abyssal_power_core, molybdenum_pickaxe)
- crust: 22 種 (abyssal_alloy_axe, abyssal_alloy_hoe, abyssal_alloy_pickaxe, abyssal_alloy_shovel, abyssal_alloy_sword, abyssal_cutter, abyssal_drill, abyssal_flippers...)

### 8.4 用途が1つ以下の素材

- `molten_volcanic_rock`(ブロック): 用途は thermal_reagent の1つ。正当化: 溶岩ブロックは耐熱装備なしでは入手が危険で、熱系への入口ゲートとして意図的に単用途。thermal_reagent は熱合金(=動力コア)へ繋がり、行き止まりではない。追加接続案(未実装): モリブデンのツルハシで採取、将来の高温炉(phase2)の燃料。
- 他はすべて2用途以上。ただし platinum(2: 動力コア/ドリル)・yttrium(2: 結晶ツルハシ/動力コア) は最小ライン。

## 9. 新規素材の正当化 (7項目)

7項目: 1既存で代替不可の理由 / 2分類 / 3何に変化するか / 4どの最終品で消費 / 5既存との関係 / 6レシピ数(入手/消費) / 7集める理由

- **crushing_hammer** (破砕ハンマー): 1)破砕を作業台で行うための再利用可能な道具。機械(phase2)無しで粉砕工程を表現できる既存アイテムは無い 2)tool 3)(最終品) 4)(final product) 5)鉄+棒から作成。破砕レシピで耐久が1減る(crafting remainder) 6)入手1/消費13 7)全ての金属で必須。耐久250
- **iron_powder** (鉄粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)corrosion_alloy_ingot, heat_resistant_alloy_ingot, high_strength_alloy_ingot, minecraft:iron_ingot 4)abyssal_cutter, abyssal_drill, cobalt_pickaxe, cobalt_shovel, crushing_hammer, dive_tank... 5)ironの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手2/消費5 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **cobalt_powder** (コバルト粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)cobalt_ingot, corrosion_alloy_ingot 4)abyssal_alloy_axe, abyssal_alloy_hoe, abyssal_alloy_pickaxe, abyssal_alloy_shovel, abyssal_alloy_sword, abyssal_cutter... 5)cobaltの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手3/消費3 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **nickel_powder** (ニッケル粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)heat_resistant_alloy_ingot, nickel_ingot, tungsten_alloy_ingot 4)abyssal_alloy_axe, abyssal_alloy_hoe, abyssal_alloy_pickaxe, abyssal_alloy_shovel, abyssal_alloy_sword, abyssal_cutter... 5)nickelの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手3/消費4 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **manganese_powder** (マンガン粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)hardened_tip, manganese_ingot 4)abyssal_cutter, abyssal_drill, cobalt_pickaxe, cobalt_shovel, manganese_axe, manganese_sword... 5)manganeseの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手3/消費3 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **vanadium_powder** (バナジウム粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)high_strength_alloy_ingot, vanadium_ingot 4)abyssal_alloy_axe, abyssal_alloy_hoe, abyssal_alloy_pickaxe, abyssal_alloy_shovel, abyssal_alloy_sword, abyssal_drill... 5)vanadiumの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手1/消費3 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **tungsten_powder** (タングステン粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)thermal_alloy_ingot, tungsten_ingot, tungsten_tip 4)abyssal_cutter, abyssal_drill, abyssal_power_core, tungsten_axe, tungsten_pickaxe 5)tungstenの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手1/消費4 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **tellurium_powder** (テルル粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)conductive_alloy_ingot, tellurium_ingot 4)abyssal_cutter, abyssal_drill, abyssal_power_core 5)telluriumの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手1/消費3 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **yttrium_powder** (イットリウム粉): 1)『破砕→精錬』の中間形態。粉のまま合金に混ぜるため(インゴットを消費せず合金量を増やせる)。原石・インゴットでは代替不可 2)processed_metal 3)luminous_crystal, yttrium_ingot 4)abyssal_power_core, crystal_pickaxe 5)yttriumの原石/精鉱→粉→インゴット。インゴットとは別に合金の混ぜ物になる 6)入手1/消費3 7)原石を破砕すると2倍(原石1→粉2)。合金の増量材
- **cobalt_concentrate** (コバルト精鉱): 1)クラストを破砕した不純な中間物。クラスト→精鉱→粉→インゴットの段階を表現するため。粉とは純度・工程が異なる 2)processed_metal 3)cobalt_powder 4)abyssal_alloy_axe, abyssal_alloy_hoe, abyssal_alloy_pickaxe, abyssal_alloy_shovel, abyssal_alloy_sword, abyssal_cutter... 5)cobalt_crust→精鉱→cobalt_powder 6)入手1/消費2 7)クラスト1個から3個(直接精錬の2個より多いが工程が長い)
- **manganese_concentrate** (マンガン精鉱): 1)クラストを破砕した不純な中間物。クラスト→精鉱→粉→インゴットの段階を表現するため。粉とは純度・工程が異なる 2)processed_metal 3)manganese_powder 4)abyssal_cutter, abyssal_drill, cobalt_pickaxe, cobalt_shovel, manganese_axe, manganese_sword... 5)manganese_crust→精鉱→manganese_powder 6)入手1/消費2 7)クラスト1個から3個(直接精錬の2個より多いが工程が長い)
- **nickel_concentrate** (ニッケル精鉱): 1)クラストを破砕した不純な中間物。クラスト→精鉱→粉→インゴットの段階を表現するため。粉とは純度・工程が異なる 2)processed_metal 3)nickel_powder 4)abyssal_alloy_axe, abyssal_alloy_hoe, abyssal_alloy_pickaxe, abyssal_alloy_shovel, abyssal_alloy_sword, abyssal_cutter... 5)nickel_crust→精鉱→nickel_powder 6)入手1/消費2 7)クラスト1個から3個(直接精錬の2個より多いが工程が長い)
- **iron_plate** (鉄板): 1)構造部品の基礎板。石切台加工が既存に無い 2)intermediate 3)hardened_tip, iron_gear, machine_frame 4)abyssal_cutter, abyssal_drill, cobalt_pickaxe, cobalt_shovel, dive_tank, pressure_diver_helmet... 5)鉄インゴット→板 6)入手1/消費3 7)鉄インゴット1→1板
- **iron_rod** (鉄棒): 1)歯車・機械枠用の棒材 2)intermediate 3)iron_gear, machine_frame 4)abyssal_drill, dive_tank, pressure_diver_helmet 5)鉄インゴット→棒x2 6)入手1/消費2 7)鉄インゴット1→2棒
- **iron_gear** (鉄歯車): 1)可動部品。既存に歯車が無い 2)intermediate 3)drill_head, machine_frame, pressure_valve 4)abyssal_drill, dive_tank, pressure_diver_helmet 5)鉄板+鉄棒→歯車 6)入手1/消費3 7)棒4+板1で1個
- **copper_wire** (銅線): 1)配線・熱交換。銅の専門用途。既存に無い 2)intermediate 3)conductive_component, pressure_valve, reinforced_cable 4)abyssal_cutter, abyssal_drill, dive_tank, pressure_diver_helmet 5)銅インゴットまたは銅クラスト→線 6)入手2/消費3 7)銅インゴット1→3線、クラスト1→5線
- **corrosion_alloy_ingot** (耐食合金インゴット): 1)nickel+cobalt+iron。耐食・耐圧(潜水装備/圧力容器)専用。他合金と性能用途が重ならない 2)alloy 3)dive_tank, diving_suit_leggings, pressure_shell, pressure_valve 4)dive_tank, diving_suit_leggings, pressure_diver_helmet 5)素材金属の粉+インゴットを混ぜて2個(粉が増量材) 6)入手1/消費4 7)専用部品の必須材料
- **high_strength_alloy_ingot** (高強度合金インゴット): 1)manganese+vanadium+iron。高硬度・耐久(耐久型ツール/掘削ヘッド)専用。他合金と性能用途が重ならない 2)alloy 3)drill_head, manganese_axe, manganese_sword 4)abyssal_drill, manganese_axe, manganese_sword 5)素材金属の粉+インゴットを混ぜて2個(粉が増量材) 6)入手1/消費3 7)専用部品の必須材料
- **heat_resistant_alloy_ingot** (耐熱合金インゴット): 1)molybdenum+nickel+iron。高温機器・耐熱ツール専用。他合金と性能用途が重ならない 2)alloy 3)molybdenum_pickaxe, thermal_component 4)abyssal_drill, molybdenum_pickaxe 5)素材金属の粉+インゴットを混ぜて2個(粉が増量材) 6)入手1/消費2 7)専用部品の必須材料
- **tungsten_alloy_ingot** (タングステン合金インゴット): 1)tungsten+nickel。超硬・重量用(タングステンツール/チップ)。他合金と性能用途が重ならない 2)alloy 3)tungsten_axe, tungsten_pickaxe, tungsten_tip 4)abyssal_cutter, abyssal_drill, tungsten_axe, tungsten_pickaxe 5)素材金属の粉+インゴットを混ぜて2個(粉が増量材) 6)入手1/消費3 7)専用部品の必須材料
- **conductive_alloy_ingot** (導電合金インゴット): 1)copper+tellurium。導電部品専用。他合金と性能用途が重ならない 2)alloy 3)conductive_component 4)abyssal_cutter, abyssal_drill 5)素材金属の粉+インゴットを混ぜて2個(粉が増量材) 6)入手1/消費1 7)専用部品の必須材料
- **thermal_alloy_ingot** (熱合金インゴット): 1)thermal_reagent+molybdenum+tungsten。終盤高温動力用。他合金と性能用途が重ならない 2)alloy 3)abyssal_power_core 4)abyssal_power_core 5)素材金属の粉+インゴットを混ぜて2個(粉が増量材) 6)入手1/消費1 7)専用部品の必須材料
- **luminous_crystal** (発光結晶): 1)イットリウムの発光特性を結晶に付与した素材。既存の結晶素材に発光/イットリウムの接点が無い 2)crystal 3)abyssal_power_core, crystal_pickaxe 4)abyssal_power_core, crystal_pickaxe 5)yttrium_powder+深淵結晶の欠片+結晶レンズ 6)入手1/消費2 7)結晶ツルハシと動力コアの必須材料
- **reinforced_fiber** (強化繊維): 1)繊維を接着剤で束ねた高強度繊維。ロープ/海布とは別(束+接着) 2)bio 3)abyssal_composite, reinforced_cable 4)abyssal_cutter, abyssal_drill, crystal_pickaxe, dive_tank, manganese_sword 5)deep_fiber+marine_adhesive 6)入手1/消費2 7)海藻繊維の中間加工先
- **reinforced_cable** (強化ケーブル): 1)繊維+銅線で作る導線被覆ケーブル。銅線単体は被覆・耐圧が無い 2)bio 3)conductive_component, dive_tank 4)abyssal_cutter, abyssal_drill, dive_tank 5)reinforced_fiber+fiber_rope+copper_wire 6)入手1/消費2 7)植物と銅を機械部品に結ぶ橋渡し
- **marine_resin** (海洋樹脂): 1)塩で硬化した高粘度樹脂。接着剤の上位で耐圧シール用 2)bio 3)abyssal_composite, pressure_shell 4)abyssal_cutter, crystal_pickaxe, manganese_sword, pressure_diver_helmet 5)plant_resin+marine_adhesive+salt_crust 6)入手1/消費2 7)塩クラストの用途。複合材/圧力殻のシール
- **abyssal_composite** (深海複合材): 1)植物+鉱物(深淵結晶)を結ぶ素材。ツールのグリップ/柄に使える軽量高強度材で、既存に該当なし 2)bio 3)abyssal_cutter, crystal_pickaxe, manganese_sword 4)abyssal_cutter, crystal_pickaxe, manganese_sword 5)reinforced_fiber+marine_resin+abyssal_crystal_shard 6)入手1/消費3 7)植物と結晶を最終品に接続
- **crystal_core** (結晶コア): 1)深淵結晶の欠片を圧縮した動力/光学部品の芯。欠片は素材、コアは部品 2)crystal 3)abyssal_energy_cell, crystal_pickaxe 4)abyssal_cutter, abyssal_drill, abyssal_power_core, crystal_pickaxe 5)abyssal_crystal_shard x3+marine_adhesive 6)入手1/消費2 7)エネルギーセル/結晶ツルハシ
- **thermal_core** (熱コア): 1)熱結晶の欠片を圧縮した熱部品の芯 2)thermal 3)advanced_energy_cell, thermal_component 4)abyssal_drill, abyssal_power_core, molybdenum_pickaxe 5)thermal_crystal_shard x3+marine_adhesive 6)入手1/消費2 7)熱部品/高度エネルギーセル
- **thermal_reagent** (熱試薬): 1)硫黄+熱結晶+溶岩で作る熱合金の反応剤。単純な粉では表現できない 2)thermal 3)thermal_alloy_ingot 4)abyssal_power_core 5)sulfur+thermal_crystal_shard+molten_volcanic_rock+salt_crust 6)入手1/消費1 7)熱合金の必須材料
- **abyssal_energy_cell** (深海エネルギーセル): 1)発光素子は光のみ。テルルの導電+結晶コアで蓄電できる部品 2)crystal 3)abyssal_cutter, advanced_energy_cell 4)abyssal_cutter, abyssal_drill, abyssal_power_core 5)lumen_cell+tellurium_ingot+crystal_core 6)入手1/消費2 7)切断器/高度エネルギーセル
- **advanced_lumen_cell** (高度発光素子): 1)発光素子+イットリウムで輝度を上げた上位素子 2)crystal 3)abyssal_light_core 4)abyssal_power_core 5)lumen_cell+lumen_gel+yttrium_ingot 6)入手1/消費1 7)光コアの材料
- **abyssal_light_core** (深海光コア): 1)高度発光素子+レンズ+白金触媒で作る光学コア 2)crystal 3)abyssal_power_core 4)abyssal_power_core 5)advanced_lumen_cell+crystal_lens+platinum_ingot 6)入手1/消費1 7)動力コア
- **advanced_energy_cell** (高度エネルギーセル): 1)エネルギーセル+熱コア+白金触媒。高出力機器用 2)crystal 3)abyssal_drill, abyssal_power_core 4)abyssal_drill, abyssal_power_core 5)abyssal_energy_cell+thermal_core+platinum_ingot 6)入手1/消費2 7)掘削機/動力コア
- **abyssal_power_core** (深海動力コア): 1)最終動力源。循環参照を避け、advanced_energy_cell+abyssal_light_core+thermal_alloy_ingotで構成 2)crystal 3)(最終品) 4) 5)advanced_energy_cell+abyssal_light_core+thermal_alloy_ingot 6)入手1/消費0 7)フェーズ2以降の機械の心臓部(現状は素材として保管/展示)
- **hardened_tip** (硬化チップ): 1)コバルト+マンガン粉で作る先端部品。工具の刃先として合金インゴットより少量で済む(複数の最終品で共有) 2)component 3)cobalt_pickaxe, cobalt_shovel, tungsten_tip 4)abyssal_cutter, abyssal_drill, cobalt_pickaxe, cobalt_shovel, tungsten_axe, tungsten_pickaxe 5)中間素材を組み立てた部品 6)入手1/消費3 7)共有部品:複数の最終品で必須
- **tungsten_tip** (タングステンチップ): 1)超硬の刃先。タングステン合金+硬化チップ(複数の最終品で共有) 2)component 3)abyssal_cutter, drill_head, tungsten_axe, tungsten_pickaxe 4)abyssal_cutter, abyssal_drill, tungsten_axe, tungsten_pickaxe 5)中間素材を組み立てた部品 6)入手1/消費4 7)共有部品:複数の最終品で必須
- **drill_head** (ドリルヘッド): 1)掘削用回転刃。タングステンチップ+高強度合金+歯車(複数の最終品で共有) 2)component 3)abyssal_drill 4)abyssal_drill 5)中間素材を組み立てた部品 6)入手1/消費1 7)共有部品:複数の最終品で必須
- **pressure_valve** (圧力バルブ): 1)耐食合金と銅線と歯車の封止弁(複数の最終品で共有) 2)component 3)dive_tank, pressure_shell 4)dive_tank, pressure_diver_helmet 5)中間素材を組み立てた部品 6)入手1/消費2 7)共有部品:複数の最終品で必須
- **pressure_shell** (耐圧殻): 1)超深海殻板+耐食合金+バルブの耐圧容器(複数の最終品で共有) 2)component 3)pressure_diver_helmet 4)pressure_diver_helmet 5)中間素材を組み立てた部品 6)入手1/消費1 7)共有部品:複数の最終品で必須
- **thermal_component** (熱部品): 1)耐熱合金+耐熱フェルト+熱コアの断熱/放熱部品(複数の最終品で共有) 2)component 3)abyssal_drill, molybdenum_pickaxe 4)abyssal_drill, molybdenum_pickaxe 5)中間素材を組み立てた部品 6)入手1/消費2 7)共有部品:複数の最終品で必須
- **conductive_component** (導電部品): 1)導電合金+ケーブルの配線モジュール(複数の最終品で共有) 2)component 3)abyssal_cutter, abyssal_drill 4)abyssal_cutter, abyssal_drill 5)中間素材を組み立てた部品 6)入手1/消費2 7)共有部品:複数の最終品で必須
- **machine_frame** (機械枠): 1)鉄板/棒/歯車の骨組み。機械(phase2)とドリル共通(複数の最終品で共有) 2)component 3)abyssal_drill 4)abyssal_drill 5)中間素材を組み立てた部品 6)入手1/消費1 7)共有部品:複数の最終品で必須
- **cobalt_pickaxe** (コバルトのツルハシ): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **cobalt_shovel** (コバルトのシャベル): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **manganese_axe** (マンガンの斧): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **manganese_sword** (マンガンの剣): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **molybdenum_pickaxe** (モリブデンのツルハシ): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **tungsten_pickaxe** (タングステンのツルハシ): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **tungsten_axe** (タングステンの斧): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **abyssal_drill** (深海ドリル): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **abyssal_cutter** (深海カッター): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **crystal_pickaxe** (結晶ツルハシ): 1)専用ステータス/特殊効果を持つ差別化ツール 2)tool 3)(最終品) 4)(final product) 5)素材金属+部品から作成 6)入手1/消費0 7)最終品(消費先なし)
- **dive_tank** (潜水タンク): 1)潜水装備。専用の防御/酸素・水中特性 2)armor 3)(最終品) 4)(final product) 5)耐食合金+部品から作成 6)入手1/消費0 7)最終品
- **diving_suit_leggings** (潜水スーツのレギンス): 1)潜水装備。専用の防御/酸素・水中特性 2)armor 3)(最終品) 4)(final product) 5)耐食合金+部品から作成 6)入手1/消費0 7)最終品
- **pressure_diver_helmet** (耐圧潜水ヘルム): 1)潜水装備。専用の防御/酸素・水中特性 2)armor 3)(最終品) 4)(final product) 5)耐食合金+部品から作成 6)入手1/消費0 7)最終品

## 10. 進行フェーズ

- **Phase 1**: 浅海: 破砕ハンマー、鉄/コバルト/ニッケルの粉、板/棒/歯車/銅線、強化繊維、海洋樹脂、コバルト工具
  - 新規: crushing_hammer, iron_powder, cobalt_powder, nickel_powder, cobalt_concentrate, iron_plate, iron_rod, iron_gear, copper_wire, reinforced_fiber, marine_resin, cobalt_pickaxe, cobalt_shovel
- **Phase 2**: 中層: マンガン、耐食合金、硬化チップ、機械枠、圧力バルブ、強化ケーブル、複合材、潜水タンク/レギンス、マンガン粉/精鉱
  - 新規: manganese_powder, manganese_concentrate, nickel_concentrate, corrosion_alloy_ingot, reinforced_cable, abyssal_composite, hardened_tip, pressure_valve, machine_frame, manganese_axe, manganese_sword, dive_tank, diving_suit_leggings
- **Phase 3**: 熱水/火山: バナジウム/タングステン、高強度/耐熱/タングステン合金、熱コア/試薬、熱部品、ドリルヘッド、マンガン/モリブデン/タングステン工具
  - 新規: vanadium_powder, tungsten_powder, high_strength_alloy_ingot, heat_resistant_alloy_ingot, tungsten_alloy_ingot, thermal_core, thermal_reagent, tungsten_tip, drill_head, thermal_component, molybdenum_pickaxe, tungsten_pickaxe, tungsten_axe
- **Phase 4**: 結晶帯: テルル/イットリウム、導電合金、結晶コア、エネルギーセル、発光結晶、導電部品、結晶ツルハシ
  - 新規: tellurium_powder, yttrium_powder, conductive_alloy_ingot, luminous_crystal, crystal_core, abyssal_energy_cell, advanced_lumen_cell, conductive_component, crystal_pickaxe
- **Phase 5**: 深層終盤: 白金、光コア、高度エネルギーセル、熱合金、深海ドリル、深海カッター
  - 新規: thermal_alloy_ingot, abyssal_light_core, advanced_energy_cell, abyssal_drill, abyssal_cutter
- **Phase 6**: 超深海: 耐圧殻、耐圧潜水ヘルム、動力コア(終端)
  - 新規: abyssal_power_core, pressure_shell, pressure_diver_helmet

## 11. 実装メモ (implementerへ)

- 破砕ハンマーのcrafting remainder: `hasCraftingRemainingItem()` + `getCraftingRemainingItem()` で damage+1 (耐久尽きたら EMPTY)。ハンマー以外のレシピに使えるので破砕以外の消費は不要。
- 全レシピは `spec.recipes[]` を JSON 変換。smelting/blasting は id 末尾 `_smelting`/`_blasting` 付きで別ファイル。
- 既存レシピID(566件)と衝突しないことは検証済み。
- `minecraft:raw_iron` が abyssal_iron_ore のドロップである前提。異なる場合は ingredient を差し替え。
- ツールの `special` は Forge イベント(BreakSpeed / LivingHurt / LootModifier) で実装。データ駆動できるのは harvest tag と underwater_tools タグ登録、材料テーブル。
