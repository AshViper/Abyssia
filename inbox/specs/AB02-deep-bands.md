# AB02 深度帯ワールド再設計（設計メモ）

依頼（2026-10-09）: Y -64〜-1865 を 5 深度帯 A〜E に分け、固い岩盤の中にバイオーム・巨大空洞・特殊環境・MK0〜MK2 鉱石・探索ルートを統合する。
既存の独自洞窟システム（worldgen/cave, 約 7600 行）を拡張する。バニラ地形は置き換えない。

## 決定（実調査に基づく）

1. **最深層を「開水域」から「固い地殻」に変える**（AB01 の中身の変更）。`abyss_density` は岩（正）で、降り口の縦穴（hadal shaft）だけが貫通し、洞窟システムが内部を作る。AB01 の定数・3 層ルーター・`AbyssFloorPlacement` は縦穴の底や露出部の植生用に残す。
2. **深度帯 = `DepthBand`**（Java/生成ツール共通の表）: A -64..-300 / B -300..-650 / C -650..-1100 / D -1100..-1550 / E -1550..-1865。境界は固定の水平線にしない: 帯の重みを滑らかに（±60 ブロックのノイズ付きで）ブレンドし、帯の判定は `bandAt(x, y, z)`（2D ノイズ + Y）。
3. **洞窟は帯ごとに 1 ネットワーク**（`CaveNetwork` を帯でパラメータ化: Y 窓、システム確率、種別重み、環境の割合）。`seabed()` の二分探索は旧深海層（minY=-368）に固定（単調性のため）。掘る床は -1872。
4. **特殊環境の面積割合**は、2D の領域ノイズ（luminous と同じ仕組み、640 セル + 240 ワープ）を帯ごとの確率で閾値化 `hazard(band, x, z)` → 環境を決める。面積比 = 確率。
5. **環境（新規 JSON）**: toxic（毒性）/ magma（火山・灼熱）/ frozen（極寒・結晶）/ anomaly（最深部異常）。既存の thermal / crystal / mineral は再利用。
6. **巨大空洞**: `CaveType.MEGA`（半径 64〜128 = 幅 128〜256）。到達距離（systemsNear の reach）を最大半径に比例させる。
7. **バイオーム**: 最深層の biome は 3D の気候（ルーター）で決まり、洞窟システムはアンカー位置・帯の Y でそのバイオームを引いて環境を選ぶ（`BIOME_QUART_Y` 固定をやめる）。
8. **鉱石**: MK1/MK2 を帯・環境・母岩ごとに定義（`ABYSS_ORES` 表）、地殻の鉱脈は新しい配置（`abyssia:crust_ore` 相当）で置く。設定は Config / 生成ツールの表。
9. **環境ギミック**（毒ガス・高熱・極寒）は新規の仕組み（`HazardZone`）が必要。装備との関係は調査結果を見て決める。

## 実装の順序（各段階でビルド + scratch サーバーで検証）

- P1 地殻化 + `DepthBand` + 帯別洞窟ネットワーク（既存環境のみで動く）
- P2 新環境 JSON + バイオーム + hazard 領域 + MEGA 空洞
- P3 鉱石（地殻の鉱脈・洞窟の鉱石パレット・帯別分布）
- P4 環境ギミック（HazardZone）+ 装備連携
- P5 検証ツール（帯別バイオーム/空洞/鉱石の分布を数える `/abyssia census`）・性能測定・Forge 移植

## 契約（実装担当 T/C/O/H が共有する名前。変えない）

**地形（済み）**: 最深層(Y<-376)は固い地殻。`abyss_density` は定数 2.0（固体）で、hadal shaft（`abyssia:abyss_rift`）だけが `shaft_bottom=-720` まで貫通する。`abyss_initial_density` も 2.0。`DeepLayer` に帯の定数（`BAND_B_TOP_Y=-300`, `BAND_C_TOP_Y=-650`, `BAND_D_TOP_Y=-1100`, `BAND_E_TOP_Y=-1550`, `CRUST_BOTTOM_Y=-1862`, `MIN_Y=-1872`）がある。生成ツール `tools/gen_worldgen.py` は `DeepLayer.java` の `static final int` を正規表現で読む。

**洞窟ネットワークの窓（絶対 Y）**: 既存ネットワーク（海底相対、Y>=-368）はそのまま「浅層」。新しい自由配置ネットワーク: B' -650..-380、C -1100..-650、D -1550..-1100、E -1862..-1550（`DepthBand`）。

**生物群系 ID（最深層、`#abyssia:deep_layer` に入る。ルーター continentalness -2..-1.7 を Y のグラデーション + 揺らぎで帯に割当てる）**:
`abyss_plain`(平原=既定), `abyss_garden`(既存), `abyss_crystal`(結晶空洞, 既存), `abyss_toxic`(Toxic Cavern), `abyss_toxic_vents`(Toxic Vent Field), `abyss_volcanic`(Volcanic Rift), `abyss_magma`(Magma Chamber), `abyss_geothermal`(Geothermal Cavern), `abyss_frozen`(Frozen Abyss), `abyss_cryo`(Cryogenic Cavern), `abyss_anomaly`(Abyssal Anomaly), `abyss_ruins`(Ancient Deep Ruins)。
通常系の Abyssal Plain/Ridge/Deep Trench/Deep Rift は既存の deep_sea / abyssal_trench / hadal_zone / hadal shaft が相当（新規にしない）。

**洞窟環境 ID（cave_environment）**: 既存 abyssal, luminous, thermal, crystal, mineral, eroded, forest, underground_sea, trench に加えて新規 `toxic`, `magma`, `frozen`, `anomaly`。cave_profile は生物群系ごと（`biomes` リスト）。

**環境ギミックのタグ（生成ツールが書く）**: `data/abyssia/tags/worldgen/biome/hazard/toxic.json`（abyss_toxic, abyss_toxic_vents, abyss_anomaly）、`.../hazard/heat.json`（abyss_volcanic, abyss_magma, abyss_geothermal）、`.../hazard/cold.json`（abyss_frozen, abyss_cryo）。Java は `TagKey<Biome>` `abyssia:hazard/toxic|heat|cold` を読む。強さは帯（Y）で上げる。

**ファイル所有（衝突を避ける）**: T = `tools/gen_worldgen.py` の生物群系/気候/地質/洞窟プロファイル/環境の表と `tools/gen_deep_assets.py` の BIOME_NAMES。C = `worldgen/cave/*.java`、`worldgen/DeepLayer.java`、`OceanChunkGenerator`（洞窟関連）。O = `worldgen/OreVeinFeature.java`、`worldgen/ConfigPlacement.java`、新規 `tools/crust_ores.py`、`Config.java` の ore 節。H = 新規 `com.abyssia.hazard` パッケージ、`Config.java` の hazard 節（新規節のみ）、`data/abyssia/tags/item`。**lang は直接編集せず `tools/lang_parts/<担当>.json`（{"key":[en,ja]}）に書く**（メイン担当が最後に統合）。gradle は回さない（メインが回す）。

**O interface（鉱石、`tools/crust_ores.py`）**: T は `gen_worldgen.py` の `main()` で、`abyss_features()` の後・`CONFIGURED`/`PLACED` を書くループの前に `try: import crust_ores` した上で `crust_ores.register(CONFIGURED, PLACED)` を 1 回呼ぶ（`crust_`で始まる 384 個の configured/placed を追加）。各 abyss 生物群系の decoration step 6 は `features[6] = ordered(crust_ores.VEIN_FEATURE_ORDER, crust_ores.biome_features(name))`（名前は namespace 無し、`biome_features` が既に順序済み）。配置は `[count|rarity_filter, abyssia:config(mk0_ore|mk1_ore|mk2_ore), in_square, height_range(absolute), minecraft:biome]` で floor 配置を持たない（`height_range` があるので既存の assert を通る）。configured は `abyssia:ore_vein` + `"crust": true`（Java 側の新フィールド、既定 false）。ホスト岩は `#abyssia:vein_replaceable`（`gen_deep_assets.py` で生成、abyssal/trench/deep_sea/crystal/volcanic/thermal/frozen/ancient_masonry/fossil/salt/mineral_host など既に含む）にある物だけ置換する。新しい地殻の岩ブロックを T が足すなら、そのタグ（`gen_deep_assets.py:1318`）にも足すこと。分布表は `inbox/specs/AB02-ores.md`。
