# PWR01 電力設備の Mk1〜Mk3 ランク分け
tier: heavy
files: P1 = industry/MachineKind.java, habitat/generator/GeneratorKind.java, lang ja/en。P2〜P4 は新規ブロック登録・モデル・テクスチャ (registry/ModIndustry・ModGenerators 系)。並列にしない
goal: 電力設備を Mk1 (小型) / Mk2 (中型) / Mk3 (大型) に分ける。工業の立方体は Mk1、拠点の多ブロック発電機は Mk2、Mk3 はタービンと原子力を新設する。

## What
- Mk1 小型: 地熱=熱水発電機 (HYDROTHERMAL_GENERATOR)、バイオ=補助発電機 (AUXILIARY_GENERATOR、生体油などが燃料)、水力=新設の立方体
- Mk2 中型: 水力=潮流タービン (CURRENT_TURBINE 5x5x5)、地熱=GEOTHERMAL (3x4x3)、バイオ=BIOFUEL (3x3x3)。既存のまま
- Mk3 大型: タービン (新設、既存の潮流タービンとは別物)、原子力 (新設)

## Why
- 電力設備に段階がなく、工業と拠点で同じ種類が別々に存在するため。ユーザー確定の割り当て。

## Decision
- 命名: 既存の表示名に Mk を付ける (例「Mk2 潮流タービン」)。id・クラス名は変えない。ja/en の lang を両方更新
- 出力の案 (FE/t。強度・倍率は既存式のまま):
  - Mk1 地熱 80×噴出孔倍率 / Mk1 バイオ 40 (現状維持)
  - Mk1 水力 40×海流強度 (上限 80、案)
  - Mk2 水力 120×海流強度 (上限 240、現状維持) / Mk2 地熱 0〜160 / Mk2 バイオ 100 (現状維持)
  - Mk3 タービン 480×海流強度 (上限 960、Mk2 水力の約 4 倍、案)
  - Mk3 原子力 640 (Mk2 地熱上限 160 の約 4 倍、案)
- 数値はすべて叩き台。実機で調整する

## Constraint
- 新設物はテクスチャ画像のみ ChatGPT で生成 (inbox/prompts/PWR01-textures.md)。モデル定義は Claude が tools/bbmodel-generator/definitions に作る
- texture_locks のテクスチャは触らない。tools/ の生成物は手編集しない
- 原子力は燃料だけ (深海産の鉱物系が叩き台)。炉心の暴走・放射性廃棄物は入れない
- 各段階は NeoForge 1.21.1 で実装・ビルド・検査 → Forge 1.20.1 へ移植 (別タスク `<id>-forge`)。パッケージ・modid は変えない

## 段階
| 段階 | 内容 | アセット | 完了条件 |
|---|---|---|---|
| P1 | 既存 6 種に Mk 表示名を付け、出力値を上の案の現状維持に整理 | 不要 | ja/en 表示、build 通過 |
| P2 | Mk1 水力の立方体を新設 | テクスチャ・モデル要 | 海流で 40×強度 FE/t 発電 |
| P3 | Mk3 タービンを新設 (潮流タービンとは別) | 要 | 上限 960 FE/t 案で発電 |
| P4 | Mk3 原子力を新設 (燃料消費で一定出力) | 要 | 燃料が減り、暴走なしで出力 |

accept: 各段階で gradle build 通過、ゲーム内で表示名と出力を確認 (スクリーンショットで Claude が見る)。

## 確定 (ユーザー 2026-10-08)
- Mk1 バイオ = 既存の補助発電機で可。
- サイズ: Mk1 水力 = 2x2、Mk3 タービン = 3x3。
- 原子力の燃料 = 既存のウラン鉱床 (raw_uranium)。廃棄物は不要。
- P1 (名前の Mk 付け) 実装済み: 熱水/補助 = Mk1、海流/地熱/バイオ燃料 = Mk2 (tools/industrial_assets.py、tools/lang_parts/bt01d.json、lang 2 言語)。

## 未決
- Mk3 の出力値 (タービン 480 / 原子力 640 FE/t は案)
- Mk3 が拠点用か工業用か (ユーザーは未検討 → 工業用の立方体系 + ケーブル接続を既定とする)
