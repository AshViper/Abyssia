# GL01 堆積物からのガラス精錬

依頼 20261003-155057。設計は ChatGPT (原文: `GL01-ST01-FS01-BS01-VD01-chatgpt-raw.md`)。Claude 補足は末尾。
tier: light
files: tools/gen_deep_assets.py の recipes() に追加する1ブロックだけ (FS01 も同じ関数に追記するので、別の場所に足す)
goal: 砂・シルト系の無機堆積物をかまど/溶鉱炉で精錬するとバニラの glass 1個。
constraints: 色付き・特殊ガラスなし、新ブロックなし。バニラ sand→glass は変えない。泥・有機質・灰・骨・塩・特殊は対象外。JSON は手書きしない。

| 対象 (10) | 理由 |
|---|---|
| deep_sediment, mineral_sediment, crystal_sediment, ruin_sediment, icy_sediment, cave_sediment | 砂質の堆積物 |
| lumen_sand | 砂 |
| fossil_silt, frost_silt, glow_silt | シルト (fossil_silt / glow_silt はテクスチャ上は泥型だがシルト = 細かい砂なので入れる) |

対象外: abyssal_mud, deep_mud, cave_mud, organic_sediment (有機質/泥)、ruin_gravel (砂利), volcanic_ash, bone_sediment, salt_crust, brine_silt (塩水泥)。

- レシピ: smelting 200 tick / blasting 100 tick、xp 0.1、結果 `minecraft:glass` 1。id は `glass_from_smelting_<src>` / `glass_from_blasting_<src>`。
accept: 生成器を流すと対象10種×2のレシピ JSON が出る、対象外には出ない、JEI で確認できる、gradle build 通過。

## Claude 補足
- ChatGPT の files 欄の Java クラスは存在しない。レシピは gen_deep_assets.py が JSON を書く (Forge 1.20.1 は bare id の result で可)。NeoForge では mc_format が 1.21 形式に直す。
- 堆積物の一覧は Explore 調査 (gen_deep_assets.py SOFT、cave_assets.py CAVE_SOFT) から。ruin_gravel は「砂利」なので外した (ChatGPT 基準「主に無機質な堆積物」の砂・シルトのみ)。
