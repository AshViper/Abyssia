# I02 選択浸出分離機 (Selective Leaching Separator)
tier: heavy
files: A (Java): com.abyssia.industry/** (MachineKind, GuiLayout, ProcessingMachineBlockEntity, ProcessRecipe, MachineRecipes, MachineInventory/SidedItemHandler, IndustryMenu, IndustryScreen), registry/ModIndustry.java / B (assets): tools/industrial_assets.py, tools/industrial_gui.py (+ generated JSON / GUI png)
goal: 依頼「クラストから、レア鉱石を取りやすくする工業ブロックがほしい」(2026-10-02)。設計 ChatGPT (チャット「工業ブロック実装提案」、raw: I02-chatgpt-raw.md / I02-chatgpt-r2.md)。クラスト (または精鉱3個) を酸性浸出試薬で湿式処理し、採掘の約4倍の確率でレア金属原石を回収する I01 系列の機械。
constraints: I01 の FE / ケーブル網 / GUI / 水中設置 / 共通筐体 (立方体 6 面、machine_bottom 共用) に揃える。幸運は無関係。主産物は再投入できない「粉」にして抽選ループを防ぐ (Claude 指摘 → ChatGPT 了承)。新しい精鉱 (鉄/銅) や使用済み浸出液は作らない。MachineKind は末尾に追加 (ordinal を同期に使っている)。
accept: コンパイル / check_recipes・check_textures OK / 各入力で主産物とレア抽選が表どおり (多数回実行で率を確認) / 試薬を1個消費 / 水中・水隣接で 10% 速い / GUI に入力・試薬・出力3枠・進捗・エネルギー / ホッパーで搬入出 / チャンク再ロード後も維持

## 処理表 (1 処理 = クラスト1個相当、レア判定は各行独立)
| 入力 | 主産物 | レア (確率) | 時間 / FE |
|---|---|---|---|
| cobalt_crust ×1 | cobalt_powder ×3 | raw_platinum 8%, raw_tellurium 8%, raw_yttrium 2% | 60t / 6,000 |
| cobalt_concentrate ×3 | cobalt_powder ×3 | 同上 | 40t / 4,000 |
| manganese_crust ×1 | manganese_powder ×3 | raw_molybdenum 8%, raw_vanadium 8%, raw_yttrium 2% | 60t / 6,000 |
| manganese_concentrate ×3 | manganese_powder ×3 | 同上 | 40t / 4,000 |
| nickel_crust ×1 | nickel_powder ×3 | raw_tungsten 8%, raw_yttrium 2% | 60t / 6,000 |
| nickel_concentrate ×3 | nickel_powder ×3 | 同上 | 40t / 4,000 |
| iron_crust ×1 | iron_powder ×3 | raw_yttrium 2% | 60t / 6,000 |
| copper_crust ×1 | minecraft:copper_ingot ×2 | raw_yttrium 2% | 60t / 6,000 |
全行で acidic_leaching_reagent ×1 を消費。機械が waterlogged か 6 方向のどれかに水があると処理時間 ×0.9 (FE は同じ総量)。
参考: 採掘はクラスト1個につき 2% / イットリウム 0.5% (幸運で最大 5% / 1.25%)。

## スロット / GUI
入力 (素材) 1、試薬 1、出力 3 (主産物 1 = 大きい枠、レア 2 = 小さい枠。レアが同時に2種以上出たら空き枠に入れ、満杯なら処理を止める)。エネルギーバー・進捗矢印は I01 と同じ。
ホッパー: 上から素材、横から試薬 (どちらも有効なアイテムだけ受け付ける)、下と横から出力を取り出し。

## アイテム
acidic_leaching_reagent (Acidic Leaching Reagent / 酸性浸出試薬): 不定形 sulfur ×2 + thermal_reagent ×1 + minecraft:water_bucket ×1 → ×4 (バケツは空で戻る)。アイコンは ChatGPT 生成 (シート I02ITEM、取込・ロック済み)。

## ブロック
selective_leaching_separator (Selective Leaching Separator / 選択浸出分離機): facing (水平) + lit + waterlogged、テクスチャ selective_leaching_separator_front / _front_on / _side / _top (ChatGPT シート I02、取込・ロック済み)、底面 machine_bottom。pickaxe / needs_stone_tool。クラフト (ChatGPT): 耐食合金 熱部品 耐食合金 / 圧力バルブ 機械枠 圧力バルブ / 鉄板 酸性浸出試薬 鉄板 → ×1。
