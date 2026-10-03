# CB01 深海岩の丸石

依頼 20261003-152743。設計は ChatGPT (原文: `FD01-TR01-CB01-chatgpt-raw.md` の CB01)。Claude 補足は末尾。
tier: standard
files: ModBlocks.java / ModBuildingBlocks.java の丸石登録, tools/gen_deep_assets.py (丸石のキューブ・名前・ルート・タグ), tools/building_assets.py (丸石の階段/ハーフ/塀・焼成・石切台), data/minecraft/tags/items/stone_tool_materials.json と stone_crafting_materials.json (ジェネレーターが書く), テクスチャは COB1 シート (main が取込、ロック)
goal: 主要7岩をシルクタッチ無しで掘ると丸石が出て、丸石でバニラのかまど・石ツールが作れる。
constraints: 元の岩テクスチャ・既存の石材ファミリー (polished/bricks/cracked/chiseled) は変えない。噴出孔岩・洞窟岩・B02 岩のドロップは変えない。生成物を手で書かない。

| 元岩 | 丸石 ID | 日本語 | English |
|---|---|---|---|
| deep_sea_rock | cobbled_deep_sea_rock | 深海岩の丸石 | Cobbled Deep Sea Rock |
| abyssal_rock | cobbled_abyssal_rock | 深淵岩の丸石 | Cobbled Abyssal Rock |
| trench_rock | cobbled_trench_rock | 海溝岩の丸石 | Cobbled Trench Rock |
| thermal_rock | cobbled_thermal_rock | 熱水岩の丸石 | Cobbled Thermal Rock |
| volcanic_rock | cobbled_volcanic_rock | 火山岩の丸石 | Cobbled Volcanic Rock |
| crystal_rock | cobbled_crystal_rock | 結晶岩の丸石 | Cobbled Crystal Rock |
| mineral_host_rock | cobbled_mineral_host_rock | 鉱物母岩の丸石 | Cobbled Mineral Host Rock |

- ドロップ: シルクタッチ無し → 丸石 1 (幸運で増えない)、シルクタッチ → 元の岩。爆発は survives_explosion (バニラの石と同じ)。
- 丸石ブロック: 元の岩と同じ硬さ・音・pickaxe 必須 (バニラ丸石は石より少し硬いが、ここは元岩に合わせる)。
- 焼成: 丸石 → 元の岩 (かまど、0.1 xp、200 tick)。
- 派生: 丸石ごとに 階段 / ハーフ / 塀 (cobbled_<rock>_stairs / _slab / _wall)。作業台レシピ + 石切台。既存の SIMPLE_STONES と同じ作り。
- タグ: 7 種の丸石を `minecraft:stone_tool_materials` と `minecraft:stone_crafting_materials` (items、replace:false) に追加 → バニラのかまど・石ツール・石の道具が作れる。`minecraft:mineable/pickaxe`、`c:cobblestone` / `forge:cobblestone` にも入れる。

## Claude 補足
- ChatGPT の files 欄の `data/abyssia/tags/...` は効かない (バニラのタグは `data/minecraft/tags/items/` に置く) ので直した。ModRecipes.java は存在しないのでジェネレーターに合わせた。
- 丸石テクスチャは元岩の配色 (ロック済み PNG から抽出した 6 色) を ChatGPT に渡して描かせる。
