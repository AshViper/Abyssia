# B01 深海バイオーム5種追加（設計）
tier: heavy
files: tools/gen_worldgen.py, tools/gen_deep_assets.py(lang/biome名), src/main/resources/data/abyssia/worldgen/**(生成物・手編集禁止)
goal: deep_ocean に冒険向けの新バイオーム5種を追加。既存10種の比率・大きな連続領域を維持する。
biomes (id / 表示名 / 概要 / 新規ブロック案 [surface, sub, rock]):
  1. sunken_ruins   沈没遺跡帯   古代石造の遺跡が点在（探索・戦利品） [ruin_gravel, ruin_sediment, ancient_masonry]
  2. bone_graveyard 巨骨の墓場   巨大骨・化石の平原（不気味・探索） [bone_sediment, fossil_silt, fossil_rock]
  3. brine_lakes    塩水湖帯     海底の塩水溜まり・塩結晶（危険地形） [salt_crust, brine_silt, salt_rock]
  4. glow_gardens   発光花園     発光生物と光る植生の草原（景観・採取） [lumen_sand, glow_silt, lumen_rock]
  5. frost_abyss    氷晶の深淵   冷水域、氷晶と霜の堆積（見た目が異質） [frost_silt, icy_sediment, frozen_rock]
constraints:
  - biome は macro フィールドのみで決定（decision worldgen-macro-biomes）。局所地形で切替えない。
  - 配置は multi_noise の未使用軸（temperature/erosion 等）の裾か province で。現行の humidity/weirdness/continentalness の既存10種は面積が大きく変わらないこと（要 /abyssia map で確認）。
  - 海底 Y100 超は深海全体の約15%を維持。
  - 生成物(data/**/worldgen)は手編集せず gen_worldgen.py 経由。
  - 新ブロックのテクスチャは ChatGPT 生成(inbox/textures) → texture_studio/forge_textures で取込。rock系は texture_locks を壊さない。
accept:
  - gen_worldgen.py 実行で新5バイオームJSON/surface rule/biome_sources が出る。
  - /abyssia map で5種が各3〜10%程度で出現、既存種が消えない。
  - ビルド通過。
