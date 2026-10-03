## FD01

```markdown
# FD01 食料関連30種追加
tier
MVP。既存の魚肉・植物素材システムを再利用し、深海で自給できる食料を30種追加する。

files
- src/main/java/**/item/ModItems.java
- src/main/java/**/item/ModFoods.java
- src/main/java/**/recipe/ModRecipes.java
- src/main/resources/data/abyssia/recipes/**
- src/main/resources/assets/abyssia/models/item/**（食料・新規食材のみ）
- src/main/resources/assets/abyssia/textures/item/**（食料・新規食材のみ）
※植物ブロック・既存魚・既存素材の登録ファイルは変更しない。

goal
植物・魚・クラゲ素材を利用して、深海で完結する食料を30種追加する。新規食材は3種程度に抑え、既存素材も積極的に料理へ利用する。

constraints
- 料理30種:
  | ID | 日本語名 | 英語名 | 満腹度 | 隠し満腹度 | 効果 | レシピ | 容器 |
  |---|---|---|---:|---:|---|---|---|
  | mushroom_cap | 深海キノコ傘 | Deep Mushroom Cap | 2 | 0.3 | - | abyssal_mushroom由来 | - |
  | gourd_flesh | ゴード果肉 | Gourd Flesh | 2 | 0.4 | - | pressure_gourd由来 | - |
  | kelp_leaf | 深海海藻葉 | Deep Kelp Leaf | 1 | 0.2 | - | 海藻/草系 | - |
  | fish_mushroom_skewer | 魚とキノコの串焼き | Fish Mushroom Skewer | 6 | 0.8 | 暗視10% | 串・不定形 | - |
  | gourd_fish_skewer | ゴード魚串 | Gourd Fish Skewer | 7 | 0.9 | 水中呼吸10% | 串・不定形 | - |
  | kelp_fish_skewer | 海藻魚串 | Kelp Fish Skewer | 5 | 0.7 | - | 串・不定形 | - |
  | mushroom_stew | 深海キノコシチュー | Abyssal Mushroom Stew | 7 | 0.8 | 暗視15% | ボウル+作業台 | bowl |
  | gourd_soup | ゴードスープ | Gourd Soup | 6 | 0.8 | 水中呼吸15% | ボウル+作業台 | bowl |
  | kelp_soup | 深海海藻スープ | Deep Kelp Soup | 5 | 0.7 | - | ボウル+作業台 | bowl |
  | fish_soup | 深海魚スープ | Abyssal Fish Soup | 8 | 1.0 | 水中呼吸15% | ボウル+作業台 | bowl |
  | mushroom_fish_stew | 魚キノコ煮込み | Fish Mushroom Stew | 9 | 1.0 | 再生5% | ボウル+作業台 | bowl |
  | gourd_fish_stew | ゴード魚煮込み | Gourd Fish Stew | 9 | 1.0 | 水中呼吸20% | ボウル+作業台 | bowl |
  | kelp_fish_stew | 海藻魚煮込み | Kelp Fish Stew | 8 | 0.9 | - | ボウル+作業台 | bowl |
  | mushroom_pie | 深海キノコパイ | Deep Mushroom Pie | 8 | 0.8 | - | 作業台 | - |
  | gourd_pie | ゴードパイ | Gourd Pie | 8 | 0.9 | 暗視10% | 作業台 | - |
  | fish_pie | 深海魚パイ | Abyssal Fish Pie | 10 | 1.0 | 再生5% | 作業台 | - |
  | mushroom_fish_pie | 魚キノコパイ | Fish Mushroom Pie | 10 | 1.1 | 暗視15% | 作業台 | - |
  | gourd_fish_pie | ゴード魚パイ | Gourd Fish Pie | 10 | 1.1 | 水中呼吸15% | 作業台 | - |
  | kelp_fish_pie | 海藻魚パイ | Kelp Fish Pie | 9 | 1.0 | - | 作業台 | - |
  | preserved_fish | 深海魚保存食 | Preserved Abyssal Fish | 7 | 1.0 | - | かまど | - |
  | smoked_mushroom | 燻製深海キノコ | Smoked Deep Mushroom | 4 | 0.7 | - | 燻製器 | - |
  | smoked_gourd | 燻製ゴード | Smoked Gourd | 5 | 0.8 | 水中呼吸5% | 燻製器 | - |
  | grilled_kelp | 焼き深海海藻 | Grilled Deep Kelp | 4 | 0.6 | - | かまど/焚き火 | - |
  | mushroom_fish_grill | キノコ魚焼き | Mushroom Fish Grill | 8 | 0.9 | 暗視10% | かまど/焚き火 | - |
  | gourd_fish_grill | ゴード魚焼き | Gourd Fish Grill | 9 | 1.0 | 水中呼吸10% | かまど/焚き火 | - |
  | kelp_fish_grill | 海藻魚焼き | Kelp Fish Grill | 7 | 0.8 | - | かまど/焚き火 | - |
  | jellyfish_skewer | クラゲ触手串 | Jellyfish Tentacle Skewer | 6 | 0.8 | 水中呼吸10% | 串・不定形 | - |
  | jellyfish_stew | クラゲ触手スープ | Jellyfish Tentacle Soup | 7 | 0.9 | 再生5% | ボウル+作業台 | bowl |
  | abyssal_survival_ration | 深海保存食 | Abyssal Survival Ration | 10 | 1.2 | 暗視10%・水中呼吸10% | 作業台 | - |
  | thermal_ration | 熱水保存食 | Thermal Fiber Ration | 9 | 1.1 | 再生5% | 作業台 | - |

- 新規食材:
  | ID | 名前 | 入手元 | 確率 |
  |---|---|---|---:|
  | mushroom_cap | 深海キノコ傘 | abyssal_mushroom収穫 | 35% |
  | gourd_flesh | ゴード果肉 | pressure_gourd収穫 | 40% |
  | kelp_leaf | 深海海藻葉 | 海藻/草系植物破壊 | 30% |
- hadal_husk、thermal_fiber、lumen_gel、bio_oil、organic_matter、plant_resin等の既存素材も料理材料として利用可能。ただし食材化のため既存アイテムの意味を変更しない。
- 効果は短時間・低確率を基本とし、既存の魚料理より明確に強くしない。
- 基本料理はバニラの同等食料より満腹度・隠し満腹度を大幅に上げない。複数素材料理のみ魚料理相当～やや上程度。
- 生食材は原則として直接食用不可。料理・加熱後に食用とする。
- ボウル料理は食後にminecraft:bowlを返却。
- かまど・燻製器・焚き火レシピは既存のForge/NeoForge双方の標準レシピ方式を使用。
- 料理ID・登録名はForge 1.20.1 / NeoForge 1.21.1で共通化できる命名にする。
- 必要テクスチャは16x16、既存Abyssia食料と同じ深海トーン。
- テクスチャ一覧: mushroom_cap, gourd_flesh, kelp_leaf、および上記30料理の計33枚。

accept
- 食料30種が登録され、JEI等で全レシピを確認できる。
- 新規食材3種が指定植物から指定確率で入手できる。
- かまど/燻製器/焚き火/作業台/ボウル料理が指定通り機能する。
- ボウル料理後にボウルが返却される。
- 料理の満腹度・効果が表と一致する。
- Forge 1.20.1 / NeoForge 1.21.1の双方で登録・レシピ・食事効果が正常動作する。
```

## TR01

```markdown
# TR01 水中で育つAncient木
tier
MVP。既存ancient木セットを再利用し、深海で木材を自給できるようにする。

files
- src/main/java/**/block/ModBlocks.java
- src/main/java/**/block/AncientSaplingBlock.java
- src/main/java/**/worldgen/AncientTreeFeature.java
- src/main/java/**/worldgen/**（ancient自然生成設定のみ）
- src/main/resources/data/abyssia/worldgen/**
- src/main/resources/data/abyssia/loot_tables/blocks/ancient_frond.json
- src/main/resources/assets/abyssia/models/block/ancient_sapling.json
- src/main/resources/assets/abyssia/models/item/ancient_sapling.json
- src/main/resources/assets/abyssia/textures/block/ancient_sapling.png

goal
水中に植えて成長させられるancient_saplingを追加し、巨大な自然生成構造に依存せずancient_stem等を継続的に収集できるようにする。

constraints
- ID: ancient_sapling
- 名前: Ancient Sapling / 古代樹の苗
- ancient_frond破壊時に5%で1個ドロップ。シルクタッチ有無に依存しない。
- 植え付け可能場所:
  - 水源ブロック内
  - waterlogged可能な対象ブロック上
  - 海底の土/砂/既存Abyssia植物系の生育基盤
- 空気中には植えられない。
- 成長条件:
  - 苗の位置が水中
  - 水源またはwaterlogged状態を維持
  - 深海層Y=-368..-64内
  - 明るさは不要（光量0でも成長可能）
- 成長はrandom tick方式。自然成長時間は平均20～40分程度。骨粉使用時は通常の苗木と同様に成長抽選を複数回行い、即時～数回で成長可能。
- 成長時に高さ5～9ブロック程度の小型ancient木を生成する。
- 木はancient_stemを幹、ancient_frondを葉として使用する。
- 幹は直立を基本とし、高さ5～9。幹径1ブロック。
- 葉は頂部から2～3層に配置し、最大横幅5ブロック程度。完全な巨大ancient構造は生成しない。
- 生成先に固体ブロックがある場合は成長失敗し、既存ブロックを破壊・上書きしない。
- 成長後も水中に存在できる構造にする。
- 自然生成:
  - 深海系バイオーム
  - Y=-300..-80を中心
  - 小型木を1チャンクあたり平均0～2本程度
  - 既存の巨大ancient構造と競合しない低密度
- ancient_frondの既存用途・ブロック性質を変更しない。
- ancient_frondの破壊ドロップだけancient_saplingを追加する。
- 新しい木材セットは作らない。既存ancient_stem/frond/planks等を再利用し、木材互換性を維持する。
- 必要テクスチャはancient_sapling.pngのみ。既存ancient_stem/frondテクスチャは変更禁止。

accept
- ancient_saplingを水中に植えられる。
- 空気中では植えられない。
- 光量0でも成長可能。
- random tickによる自然成長と骨粉成長が動作する。
- 成長後に高さ5～9の小型ancient木が生成される。
- 幹・葉が既存ancient_stem/ancient_frondで構成される。
- 生成時に周囲の既存ブロックを不正に上書きしない。
- ancient_frondから5%で苗が得られる。
- 指定深度・低密度で自然生成される。
- Forge 1.20.1 / NeoForge 1.21.1の双方で正常動作する。
```

## CB01

```markdown
# CB01 深海岩の丸石化
tier
MVP。主要7岩に丸石を追加し、深海でもバニラ相当の石材クラフト基盤を確保する。

files
- src/main/java/**/block/ModBlocks.java
- src/main/java/**/recipe/ModRecipes.java
- src/main/resources/data/abyssia/recipes/**
- src/main/resources/data/abyssia/tags/items/stone_tool_materials.json
- src/main/resources/data/abyssia/tags/items/stone_crafting_materials.json
- src/main/resources/data/abyssia/loot_tables/blocks/**
- src/main/resources/assets/abyssia/models/block/cobbled_*.json
- src/main/resources/assets/abyssia/models/item/cobbled_*.json
- src/main/resources/assets/abyssia/textures/block/cobbled_*.png
※既存7種の元岩テクスチャ・既存岩ブロック定義は変更しない。

goal
主要深海岩をシルクタッチ無しで採掘すると対応する丸石を得られ、丸石を石ツール・かまど等の素材として使用できるようにする。

constraints
- 対象7種:
  | 元岩 | 丸石ID | 日本語名 |
  |---|---|---|
  | deep_sea_rock | cobbled_deep_sea_rock | 深海岩の丸石 |
  | abyssal_rock | cobbled_abyssal_rock | 深淵岩の丸石 |
  | trench_rock | cobbled_trench_rock | 海溝岩の丸石 |
  | thermal_rock | cobbled_thermal_rock | 熱水岩の丸石 |
  | volcanic_rock | cobbled_volcanic_rock | 火山岩の丸石 |
  | crystal_rock | cobbled_crystal_rock | 結晶岩の丸石 |
  | mineral_host_rock | cobbled_mineral_host_rock | 鉱物母岩の丸石 |
- 通常採掘:
  - シルクタッチ無し → 対応するcobbled_*を1個
  - シルクタッチ有り → 元の岩を1個
- Fortuneは丸石ドロップ数を増加させない。
- 爆破・その他の間接破壊も通常ドロップ規則に準じる。
- 噴出孔岩・洞窟岩・B02岩等は今回の対象外。既存の自分自身ドロップを維持する。
- 各丸石をかまどで焼くと対応する元岩を1個生成。
- 各丸石を石切台で:
  - 丸石階段
  - 丸石ハーフ
  - 丸石塀
  を作成可能にする。
- 7種すべてに上記3派生ブロックを追加。
- 派生ブロックの硬さ・耐爆性等は対応する既存石材ファミリーと整合させる。
- 各cobbled_*をminecraft:stone_tool_materialsに登録する。
- 各cobbled_*をminecraft:stone_crafting_materialsに登録する。
- これによりバニラの石のツルハシ/斧/シャベル/クワ/剣、かまど等のクラフト材料として利用可能にする。
- 既存のpolished/bricks/cracked/chiseled系ファミリーは削除・変更しない。
- 元岩のテクスチャは変更禁止。丸石テクスチャのみ新規作成する。
- 丸石テクスチャは16x16、元岩の色相・明度・深海トーンを維持し、バニラcobblestone同様の不規則な石片パターンにする。
- 必要テクスチャ:
  - cobbled_deep_sea_rock
  - cobbled_abyssal_rock
  - cobbled_trench_rock
  - cobbled_thermal_rock
  - cobbled_volcanic_rock
  - cobbled_crystal_rock
  - cobbled_mineral_host_rock
  - 各丸石の階段/ハーフ/塀はブロックモデルが同一テクスチャを参照してよい。
  - 合計7枚の新規テクスチャを基本とする。

accept
- 7種の元岩をシルクタッチ無しで採掘すると対応丸石が得られる。
- シルクタッチでは元岩が得られる。
- Fortuneで不自然に増殖しない。
- 噴出孔岩・洞窟岩・B02岩のドロップが変化しない。
- 7種すべてを焼成して元岩へ戻せる。
- 7種すべてに丸石階段・ハーフ・塀が存在する。
- 7種の丸石すべてがstone_tool_materials / stone_crafting_materialsに登録される。
- バニラの石ツール・かまど等を深海丸石でクラフトできる。
- 既存岩テクスチャ・既存石材ファミリーに差分がない。
- Forge 1.20.1 / NeoForge 1.21.1の双方で採掘・焼成・石切台・クラフトが正常動作する。
```
