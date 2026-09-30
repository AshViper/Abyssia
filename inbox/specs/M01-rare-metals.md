# M01 レアメタル6種の追加
tier: heavy
files: tools/gen_deep_assets.py, tools/mineral_textures.py, tools/gen_worldgen.py, tools/seabed_structures.py(必要時のみ), src/main/java/com/abyssia/registry/**(手書き部分のみ), 生成物
goal: 実在の深海資源に基づくレアメタル6種を追加: molybdenum(モリブデン) / vanadium(バナジウム) / tellurium(テルル) / platinum(白金) / tungsten(タングステン) / yttrium(イットリウム, レアアース泥由来)。各: raw_<id>, <id>_ingot, <id>_ore(深層岩の鉱石ブロック)。
- 入手: 既存クラスト(cobalt/manganese/nickel/iron/copper_crust)を掘ると低確率で raw_<rare> を追加ドロップ(loot 表。M02 と整合: tellurium/platinum=cobalt_crust, molybdenum/vanadium=manganese_crust, tungsten=nickel_crust, yttrium=abyssal_crust系 or 全クラストごく低確率)。鉱石ブロックは depth 深いバイオーム(abyssal_trench/hadal_zone 等)にごく稀な小鉱脈(OreVeinFeature/既存 ore 配置に倣う)。
- 精錬: raw/ore → ingot (smelting+blasting、既存 manganese と同じ仕組み)。ingot 9 ⇄ block 不要。
constraints: テクスチャは ChatGPT を使わず mineral_textures.py の Recolour(バニラ raw_iron/gold/copper, 各ingot)方式で色替えのみ(mineral-texture-categories 決定)。既存 rock テクスチャ(texture_locks)厳守。生成物は手編集しない、生成元を直して gen_deep_assets → forge_textures/mineral_textures を再実行。各金属は実在に即した色相(白金=明灰白、テルル=銀青灰、モリブデン=鉛青、バナジウム=青緑灰、タングステン=暗灰、イットリウム=淡黄白灰)。ID/lang(en/ja)/tooltip(産地の一言)を付ける。ビルドはメインが実行。
accept: 全アイテム/ブロックの blockstate/model/lang/loot/recipe/tag が生成され、tools 再実行が通り、ID 一覧を STATUS に列挙する。
