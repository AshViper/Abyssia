Claude案をベースにしつつ、**OL01は油嚢を苗にする循環を避けるため専用の苗アイテムを追加**、**PL01は水・FEとも必須にせず、拠点内で使える代わりに成長時間を長め**にする方針にします。

## OL01

```markdown
# OL01 栽培可能な生体油昆布

tier
standard

files
- src/main/java/**/block/OilKelpBlock.java
- src/main/java/**/block/ModBlocks.java（oil_kelp系登録のみ）
- src/main/java/**/item/ModItems.java（oil_sac / oil_kelp_seed登録のみ）
- src/main/java/**/recipe/ModRecipes.java（oil_sac加工のみ）
- src/main/resources/data/abyssia/recipes/oil_sac_to_bio_oil.json
- src/main/resources/data/abyssia/loot_tables/blocks/oil_kelp.json
- src/main/resources/data/abyssia/worldgen/**
- src/main/resources/assets/abyssia/blockstates/oil_kelp.json
- src/main/resources/assets/abyssia/models/block/oil_kelp_*.json
- src/main/resources/assets/abyssia/models/item/oil_sac.json
- src/main/resources/assets/abyssia/models/item/oil_kelp_seed.json
- src/main/resources/assets/abyssia/textures/block/oil_kelp_*.png
- src/main/resources/assets/abyssia/textures/item/oil_sac.png
- src/main/resources/assets/abyssia/textures/item/oil_kelp_seed.png

goal
浅めの深海でも生体油を栽培できるようにし、既存oil_bladder_weedだけに依存しないbio_oilの安定した入手手段を追加する。

constraints
- ブロックID: oil_kelp
- 名前: 生体油昆布 / Oil Kelp
- アイテムID: oil_sac（油嚢）、oil_kelp_seed（油昆布の種）
- oil_kelpは水中専用の成長植物。
- 1ブロック単位で縦方向に成長し、最大長8ブロック。
- 基部から上向きに成長する。下向きには成長しない。
- 各節に `ripe` プロパティを持つ。
- 非熟成節から熟成節になる確率はrandom tickごとに5%。平均成長時間は通常プレイで数分～十数分程度。
- 成熟した節を右クリックするとoil_sacを1個確定入手し、節は未熟成状態へ戻る。ブロックを破壊した場合は成熟節のみoil_sacを1個、未熟成節は種を低確率でドロップ。
- 1本から同時に複数のoil_sacを取得できるが、各節につき1個まで。
- oil_sacは食用ではなく、主用途はbio_oilの原料。
- oil_sac → bio_oil:
  - smelting: abyssia:oil_sac x1 → abyssia:bio_oil x2
  - cooking timeは通常の燃料精製素材相当。
- oil_kelp_seedはoil_kelp破壊時5%でドロップ。
- oil_kelp_seedは水源ブロック中の海底面に植えられる。
- 骨粉使用時は1回の成長抽選を行い、未熟成節なら成長または熟成を進める。
- 水中専用だが、既存oil_bladder_weedより浅い場所で栽培可能。
- 自然生成:
  - 深さY=-250..-64を基本とする（既存の「200～2500m」という水深表現をAbyssiaのY座標へ統一）
  - 深海系海底に低密度生成
  - 1チャンク平均0～2本
  - 最大長4～6程度で自然生成
- 既存oil_bladder_weedの生成・ドロップは変更しない。
- bio_oilの既存用途・燃料値は変更しない。
- テクスチャは既存deep_kelp系と差別化し、暗い緑～青緑の茎に油嚢を示す暗色の実を付ける。
- 必要テクスチャ:
  - oil_kelp_base
  - oil_kelp_middle
  - oil_kelp_ripe
  - oil_sac
  - oil_kelp_seed

accept
- oil_kelpを水中に植えられる。
- 最大8ブロックまで上向きに成長する。
- 節ごとに熟成状態が管理される。
- 成熟節を右クリックするとoil_sacを1個取得でき、再び未熟成になる。
- 破壊でも成熟節からoil_sacを取得できる。
- oil_sacを焼くとbio_oilが2個得られる。
- oil_kelp_seedから再栽培できる。
- 骨粉で成長・熟成を促進できる。
- Y=-250..-64に低密度で自然生成される。
- 既存oil_bladder_weedとbio_oilの既存挙動に差分がない。
- Forge 1.20.1 / NeoForge 1.21.1で正常動作する。
```

## PL01

```markdown
# PL01 水耕栽培プランター

tier
standard

files
- src/main/java/**/block/HydroPlanterBlock.java
- src/main/java/**/blockentity/HydroPlanterBlockEntity.java
- src/main/java/**/block/ModBlocks.java（hydro_planter登録のみ）
- src/main/java/**/menu/HydroPlanterMenu.java
- src/main/java/**/screen/HydroPlanterScreen.java
- src/main/resources/assets/abyssia/blockstates/hydro_planter.json
- src/main/resources/assets/abyssia/models/block/hydro_planter_*.json
- src/main/resources/assets/abyssia/models/item/hydro_planter.json
- src/main/resources/assets/abyssia/textures/block/hydro_planter_*.png
- src/main/resources/assets/abyssia/textures/gui/hydro_planter.png

goal
水のない拠点内部でも既存の食用植物を栽培できる専用プランターを追加し、mushroom_cap / gourd_flesh / kelp_leafを継続的に生産できるようにする。

constraints
- ブロックID: hydro_planter
- 名前: 水耕栽培プランター / Hydro Planter
- 水ブロックは不要。
- FE電力も不要。拠点内で常時使用できる代わりに成長時間を長めに設定する。
- GUIは1スロットの簡易GUIとする。
- 左クリックではなく右クリックでGUIを開く。
- GUIへ対応する植物の種/苗アイテムを投入すると自動的に栽培開始。
- 既存植物ブロックそのものを直接GUIへ投入する方式にはしない。栽培用アイテムが存在しない植物については、対応する植物アイテムを「苗」として扱える内部マッピングを実装する。
- 対応植物:
  | 入力 | 収穫物 | 収穫量 | 成長時間 |
  |---|---|---:|---:|
  | abyssia:abyssal_mushroom | abyssia:mushroom_cap | 1～2 | 8分 |
  | abyssia:pressure_gourd | abyssia:gourd_flesh | 1～2 | 12分 |
  | abyssia:deep_kelp | abyssia:kelp_leaf | 2～3 | 6分 |
- 収穫可能状態になったら右クリックで収穫。
- 収穫時に食材だけを取り出し、苗は消費せず同じ植物を再成長可能とする。
- 収穫後は成長0%へ戻る。
- 成長中に右クリックした場合はGUIを開くだけで、途中収穫は不可。
- GUIを閉じても成長は継続する。
- 骨粉は使用可能。使用1回につき成長時間を10%短縮する。
- FEは受け付けない。
- 水は内部タンクとしても保持しない。完全に乾式の水耕装置として扱う。
- プランター同士が上下左右に隣接した場合、隣接面の外枠を描画しない。
- 接続判定はblockstateの上下左右接続プロパティで行う。
- 前後方向の接続はしない。
- 側面:
  - 単体: 四辺すべて枠あり
  - 左隣接: 左枠なし
  - 右隣接: 右枠なし
  - 上隣接: 上枠なし
  - 下隣接: 下枠なし
- 上面は隣接していても完全には透明化せず、内部の栽培土/水耕面が連続して見えるデザインにする。
- 3x3以上に連結しても同じ規則でシームレスに見えること。
- 接続しているだけで当たり判定や内部インベントリを共有しない。各ブロックは独立した1スロット装置。
- レシピ:
  - shaped 3x3
  - `IPI / G G / IPI`
  - I=minecraft:iron_ingot
  - P=minecraft:glass_pane
  - G=abyssia:organic_matter
  - 空欄は空
  - 出力: hydro_planter x1
- 必要テクスチャ:
  - hydro_planter_top
  - hydro_planter_side
  - hydro_planter_bottom
  - hydro_planter_inner
  - hydro_planter_gui
- 接続面の枠消去はテクスチャを個別に大量作成せず、blockstate/modelの接続パーツ切替で実現する。
- 食材の既存ドロップ確率・既存植物の自然生成は変更しない。

accept
- 水なしの拠点内でhydro_planterを設置できる。
- 対応する3植物を投入して栽培できる。
- 指定時間経過後に指定量の食材を収穫できる。
- 収穫後に同じ苗で再栽培できる。
- GUIを閉じても成長が継続する。
- 骨粉で成長時間を短縮できる。
- 水・FEなしで動作する。
- 隣接するプランターの上下左右の枠が正しく消え、3x3以上でも連続した大型プランターに見える。
- 隣接していても各プランターのインベントリ・成長状態は独立する。
- 指定レシピで作成できる。
- Forge 1.20.1 / NeoForge 1.21.1で正常動作する。
```
