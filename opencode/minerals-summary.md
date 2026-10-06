# Abyssia 鉱物・鉱脈システム まとめ

## 概要
深海次元全体が Abyssia 独自のブロックで構成され、バニラの石系ブロックは一切使用されない。鉱脈は **OreVeinFeature** として実装され、ノイズ変形された鉱体を生成する。

---

## 鉱脈生成 (`OreVeinFeature.java`)

### 鉱脈サイズ (`Size` enum)
| サイズ | ブロック数範囲 (設定値) | 備考 |
|--------|------------------------|------|
| SMALL  | 5〜15 | 最小単位 |
| MEDIUM | 15〜40 | |
| LARGE  | 40〜100 | × `large_vein_multiplier` (デフォルト 1.0) |
| HUGE   | 100〜300 | × `large_vein_multiplier`、露出確率 3倍 |

### 形状 (`Shape` enum)
- **HORIZONTAL** : 水平に伸びる
- **VERTICAL** : 垂直 (ピッチ 75〜90°)
- **DIAGONAL** : 斜め (ピッチ 35〜55°)
- **VEIN** : 細長い脈状
- **CLUSTER** : 球状クラスター (半径 × 1.2)
- **STRATUM** : 地層に沿った平板状 (縦方向潰し 2.2倍)

### 生成アルゴリズム
1. **目標体積** から半径 `r` と長さ `length` を算出
2. **シンプレックスノイズ** で中心線をワープ (長さの 12%)
3. **各サンプル点** で球状にブロックを走査、距離 `d` で純度 `purity` 計算
   - `d < 0.55` : コア → 90% 鉱石, 10% 母岩
   - `0.55 ≤ d < 0.9` : 中間 → 40% 鉱石, 60% 母岩
   - `d ≥ 0.9` : 縁 → 母岩のみ (水中なら生成せず)
4. **底面から上向き**に描画 (露出部が海底ブロックの上に乗るように)
5. **表面装飾** : 露出脈は半径+4、埋没脈は半径+1 の範囲に鉱物クラスト/ノジュール配置

### 設定項目 (`Config.java` - ore カテゴリ)
```properties
surface_veins_enabled = true          # 海底露出を許可
large_vein_multiplier = 1.0           # LARGE/HUGE 倍率
exposed_vein_chance = 0.15            # 基準露出確率 (大型脈ほど高確率)
small_vein_min/max = 5 / 15
medium_vein_min/max = 15 / 40
large_vein_min/max = 40 / 100
huge_vein_min/max = 100 / 300
```

---

## 鉱物ブロック定義 (`ModBlocks.java`)

### 主要鉱石 (通常鉱脈用)
| 鉱石ブロック | XP | 発光 | 用途 |
|-------------|-----|------|------|
| `abyssal_iron_ore` | 0 | 0 | 基本鉄源 |
| `deep_copper_ore` | 0 | 0 | 基本銅源 |
| `sulfur_ore` | 1〜3 | 0 | 硫黄、熱水噴出孔周辺 |
| `thermal_crystal_ore` | 2〜5 | 3 | 熱水結晶、噴出孔周辺 |
| `abyssal_crystal_ore` | 2〜5 | 3 | 深海結晶、結晶フィールド |
| `manganese_ore` | 0 | 0 | マンガン、マンガンクラスト/ノジュール付き |
| `cobalt_ore` | 0 | 0 | コバルト、コバルトクラスト/クラスター付き |
| `deep_nickel_ore` | 0 | 0 | ニッケル、ニッケルクラスト/クラスター付き |

### レアメタル鉱石 (極小・超希少脈)
| 鉱石 | 親岩 | 生成バイオーム | 確率 (チャンク当たり) |
|------|------|----------------|---------------------|
| `platinum_ore` | trench_rock | abyssal_trench, hadal_zone | 1/48 |
| `tellurium_ore` | trench_rock | abyssal_trench, hadal_zone | 1/40 |
| `molybdenum_ore` | abyssal_rock | abyssal_ocean, abyssal_trench | 1/40 |
| `vanadium_ore` | abyssal_rock | abyssal_ocean, abyssal_trench | 1/40 |
| `tungsten_ore` | trench_rock | hadal_zone | 1/48 |
| `yttrium_ore` | abyssal_rock | abyssal_ocean, hadal_zone | 1/56 |

### バニラ鉱物の深海版
| 鉱石 | サイズ | 確率 | 生成バイオーム |
|------|--------|------|----------------|
| `abyssal_diamond_ore` | small | 1/3 | trench, hadal, crystal_fields, frost_abyss |
| `abyssal_emerald_ore` | small | 1/4 | crystal_fields, deep_forest, abyssal_forest, glow_gardens |
| `abyssal_lapis_ore` | medium | 1/3 | abyssal_ocean, crystal_fields, frost_abyss, sunken_ruins, brine_lakes |
| `abyssal_gold_ore` | medium | 1/3 | thermal_vents, volcanic_deep, deep_sea, sunken_ruins, deep_forest |
| `abyssal_redstone_ore` | medium | 1/2 | trench, hadal, deep_sea, abyssal_ocean, bone_graveyard, glow_gardens |
| `abyssal_quartz_ore` | large | 1/4 | volcanic_deep, thermal_vents, brine_lakes, bone_graveyard |

### 鉱物クラスト (海底露出マーカー)
`manganese_crust`, `cobalt_crust`, `nickel_crust`, `iron_crust`, `copper_crust`, `diamond_crust`, `gold_crust`, `redstone_crust`, `lapis_crust`, `emerald_crust`, `quartz_crust`

### 鉱物クラスター/ノジュール (露出脈の表面装飾)
- `manganese_nodules` (黒、高さ4)
- `cobalt_cluster` (青、高さ6)
- `nickel_cluster` (黄緑、高さ6)
- `sulfur_cluster` (黄、高さ5)
- `abyssal_crystal_cluster` (紫、高さ7、発光5)
- `thermal_crystal_cluster` (橙、高さ7、発光5) - 芽→クラスター成長

---

## バイオーム別鉱脈構成 (`gen_worldgen.py` - `VEINS`)

```python
VEINS = {
    "deep_sea":           [("iron", "small"), ("manganese", "small"), ("copper", "small")],
    "abyssal_ocean":      [("manganese", "small"), ("manganese", "medium"), ("iron", "medium"), ("nickel", "small")],
    "abyssal_forest":     [("iron", "small"), ("copper", "small")],
    "deep_forest":        [("iron", "small"), ("manganese", "small")],
    "abyssal_trench":     [("cobalt", "medium"), ("cobalt", "large"), ("manganese", "medium"), ("nickel", "medium")],
    "hadal_zone":         [("cobalt", "huge"), ("nickel", "large"), ("nickel", "huge"), ("manganese", "large"), ("abyssal_crystal", "medium")],
    "volcanic_deep":      [("sulfur", "large"), ("sulfur", "huge"), ("cobalt", "large"), ("nickel", "large")],
    "thermal_vents":      [("sulfur", "large"), ("copper", "large"), ("thermal_crystal", "large")],
    "deep_crystal_fields": [("abyssal_crystal", "medium"), ("abyssal_crystal", "large"), ("thermal_crystal", "medium")],
    "sunken_ruins":       [("iron", "small"), ("copper", "medium")],
    "bone_graveyard":     [("manganese", "small"), ("nickel", "medium")],
    "brine_lakes":        [("sulfur", "medium"), ("thermal_crystal", "small")],
    "glow_gardens":       [("iron", "small"), ("copper", "small")],
    "frost_abyss":        [("cobalt", "medium"), ("abyssal_crystal", "small")],
}
# + RARE_VEINS (レアメタル) + VANILLA_VEINS (バニラ鉱物)
```

---

## 鉱物アイテム (`ModItems.java`)

### 原料系
| アイテム | 入手元 | 用途 |
|---------|--------|------|
| `raw_manganese` | manganese_ore | 精錬→マンガンインゴット |
| `raw_cobalt` | cobalt_ore | 精錬→コバルトインゴット |
| `raw_nickel` | deep_nickel_ore | 精錬→ニッケルインゴット |
| `sulfur` | sulfur_ore | 火薬、薬品、合金原料 |
| `thermal_crystal_shard` | thermal_crystal_ore | 熱水結晶加工品 |
| `abyssal_crystal_shard` | abyssal_crystal_ore | 深海結晶加工品 |
| `crust_powder` | クラスト破砕 | 微量金属抽出 |

### レアメタル (MaterialItem: ツールチップに産地表示)
| 原石 | インゴット | 備考 |
|------|-----------|------|
| `raw_platinum` | `platinum_ingot` | トレンチ/ハダル |
| `raw_tellurium` | `tellurium_ingot` | トレンチ/ハダル |
| `raw_molybdenum` | `molybdenum_ingot` | アビサルオーシャン/トレンチ |
| `raw_vanadium` | `vanadium_ingot` | アビサルオーシャン/トレンチ |
| `raw_tungsten` | `tungsten_ingot` | ハダル |
| `raw_yttrium` | `yttrium_ingot` | アビサルオーシャン/ハダル |

### 素材加工システム (docs/material-system.md 連携)
- **粉末** (crusher/クラッシングハンマー製): `*_powder` (iron, cobalt, nickel, manganese, vanadium, tungsten, tellurium, yttrium)
- **濃縮物**: `*_concentrate` (cobalt, manganese, nickel)
- **基本部品**: `iron_plate`, `iron_rod`, `iron_gear`, `copper_wire`
- **合金インゴット**: `corrosion_alloy_ingot`, `high_strength_alloy_ingot`, `heat_resistant_alloy_ingot`, `tungsten_alloy_ingot`, `conductive_alloy_ingot`, `thermal_alloy_ingot`, `abyssal_alloy_ingot`
- **植物系**: `reinforced_fiber`, `reinforced_cable`, `marine_resin`, `abyssal_composite`
- **熱水系**: `thermal_core`, `thermal_reagent`
- **結晶/エネルギー**: `luminous_crystal`, `crystal_core`, `abyssal_energy_cell`, `advanced_lumen_cell`, `abyssal_light_core`, `advanced_energy_cell`, `abyssal_power_core`
- **共通部品**: `hardened_tip`, `tungsten_tip`, `drill_head`, `pressure_valve`, `pressure_shell`, `thermal_component`, `conductive_component`, `machine_frame`

---

## 表層地質・鉱物クラスト (`GEOLOGY` in gen_worldgen.py)

バイオームごとに表層堆積物・母岩・鉱物クラストを定義:

| バイオーム | 表層 (ノイズ順) | 母岩 | 岩盤 | 鉱物クラスト (閾値) |
|-----------|----------------|------|------|-------------------|
| `volcanic_deep` | molten_volcanic_rock → volcanic_ash → volcanic_rock → volcanic_glass | volcanic_rock | volcanic_rock | なし |
| `thermal_vents` | sulfur_deposit → mineral_sediment | mineral_sediment | thermal_rock | **copper_crust (0.72)** |
| `deep_crystal_fields` | crystal_rock → crystal_sediment | crystal_sediment | crystal_rock | なし |
| `abyssal_trench` | deep_mud → mineral_sediment | mineral_sediment | trench_rock | **cobalt_crust (0.66)** |
| `hadal_zone` | abyssal_mud → deep_mud | deep_sediment | trench_rock | **nickel_crust (0.68)** |
| `abyssal_ocean` | abyssal_mud → deep_sediment | deep_sediment | abyssal_rock | **manganese_crust (0.62)** |
| `deep_sea` | abyssal_mud → deep_sediment | mineral_sediment | deep_sea_rock | なし |
| `sunken_ruins` | ancient_masonry → ruin_gravel | ruin_sediment | ancient_masonry | なし |
| `bone_graveyard` | fossil_rock → bone_sediment | fossil_silt | fossil_rock | なし |
| `brine_lakes` | brine_silt → salt_crust | brine_silt | salt_rock | なし |
| `glow_gardens` | glow_silt → lumen_sand | glow_silt | lumen_rock | なし |
| `frost_abyss` | icy_sediment → frost_silt | icy_sediment | frozen_rock | なし |

---

## 採掘・処理フロー

```
鉱脈発見 (クラスト/ノジュールで露出判定)
    ↓
採掘 (シルクタッチで鉱石ブロック、通常でコブルドロップ)
    ↓
クラッシャー/粉砕ハンマー → 粉末 (*_powder)
    ↓
精錬炉/リファイナリー → インゴット (*_ingot)
    ↓
合金炉/ハイテンプ炉 → 合金インゴット
    ↓
工作機械/部品製造 → ツール・アーマー・機械フレーム
```

### 選択的浸出分離機 (Selective Leaching Separator)
- **試薬**: `acidic_leaching_reagent` (酸性浸出試薬)
- 入力1スロット + 試薬1スロット → 主出力1 + レア出力3スロット
- レア出力は独立確率ロール、溢れはオーバーフロー保持
- 水隣接で 10% 高速化 (同エネルギー)

---

## 熱水噴出孔との関連

| 要素 | 内容 |
|------|------|
| `thermal_vent` ブロック | 噴出孔コア、タイプ/活動度をブロックステートで保持 |
| `VentActivity` | NONE, LOW, MEDIUM, HIGH, EXTREME (乗数: 0.0, 0.5, 1.0, 1.5, 2.0) |
| 水熱発電機 | 80 FE/t × 活動度乗数、32k FE バッファ |
| 高温炉 | 半径 6 ブロック以内に活性噴出孔必須 |
| 熱水鉱物 | sulfur_ore, thermal_crystal_ore, sulfur_deposit, thermal_crystal_cluster, vent_rock 系 |
| 熱水植物 | vent_grass, thermal_tube, heat_moss, mineral_vine |

---

## 設定で制御可能な項目

```properties
# config/abyssia-common.toml
[ore]
surface_veins_enabled = true
large_vein_multiplier = 1.0
exposed_vein_chance = 0.15
small_vein_min = 5
small_vein_max = 15
medium_vein_min = 15
medium_vein_max = 40
large_vein_min = 40
large_vein_max = 100
huge_vein_min = 100
huge_vein_max = 300

[thermal]
mineral_vent_chance = 0.25       # 噴出孔鉱物生成確率
mineral_generation = true        # 硫黄/硫化鉱石の噴出孔生成
```

---

## データ駆動な定義ファイル

| ファイル | 内容 |
|---------|------|
| `tools/gen_worldgen.py` | 鉱脈配置・バイオーム割当・レアメタル・バニラ鉱物定義 |
| `tools/gen_deep_assets.py` | RARE_METALS, VANILLA_MINERALS 定義 (アイテム名・レアリティ・産地) |
| `data/abyssia/worldgen/configured_feature/vein_*.json` | 生成済み鉱脈フィーチャー (自動生成) |
| `data/abyssia/tags/blocks/vein_replaceable.json` | 鉱脈が置換可能なブロックタグ |

---

## 実装のポイント

1. **ノイズワープ鉱脈**: 単純な球/楕円体でなく、中心線に沿ってノイズでうねる自然な形状
2. **純度グラデーション**: 中心ほど高品位、外縁は母岩混じり
3. **露出/埋没の使い分け**: 露出脈は遠方から視認可能 (クラスト/ノジュール)、埋没脈は微細なクラストのみ
4. **バイオーム特化**: 各バイオームで偏った鉱物構成 → 探検・拠点選定の戦略性
5. **レアメタルは実在モデル準拠**: プラチナ・テンルムはトレンチ、イットリウムはハダル等、地質学的分布を反映
6. **バニラ鉱物の深海版**: 同等ドロップ/XP でバニラ互換レシピに対応
7. **素材システムと直結**: 採掘→粉末→濃縮→合金→部品 の一貫したチェーン