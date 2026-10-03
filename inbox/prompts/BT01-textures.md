# BT01 建設ツール拡張 テクスチャ — ChatGPT ImageGen 用

デザイン画: inbox/designs/BT01.png。保存先 `inbox/textures/sheets/BT1A.png` など。取込: `python tools/agentflow/sheets.py import auto --preset BT1A`。
半透明にするもの (aquarium_glass, habitat_membrane, generator/bio_tank) は取込後に `tools/bt01/translucent.py` でアルファを付ける。aquarium_coral は item に取り込んでから block へ移す。

## 共通プロンプト
```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft (iron block, blast furnace, observer, copper, sea lantern). Each texture is ONE opaque tile in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic view of one block face filling the whole square, no perspective, no 3D render.
Limited palette of 4-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no soft glow halo, no drop shadow. Draw at exactly 16x16 logical pixels (square pixels, integer scale). Never use magenta inside a tile.
Theme: deep-sea habitat equipment (same family as the existing base: dark gunmetal, blue-grey steel, thick plates with bolts, copper/bronze pipe accents, cyan light strips).
Palette guide: #14181e #232a33 #343e4a #4a5866 #6c7c8a #9aa8b4 (metal), #7a4a2a #b06a3a #d89060 (copper), #0f3a40 #1d6670 #2a9aa0 (teal), #3ee6f0 #b8f8ff #eafcff (glow), #0b2a3a #13506a (glass tint).
```

## シート BT1A (12個 / 4列×3行)
```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. habitat_ladder_rail: 梯子の側柱。暗いガンメタルの縦長パネル、縦の継ぎ目線
2. habitat_ladder_rung: 梯子の横桟。明るめの灰色の擦れた金属バー
3. habitat_ladder_clamp: 銅/青銅の締め付けバンド、小さなボルト
4. habitat_ladder_light: 明るいシアンに光る縦のライト帯 (縁は暗い金属)
5. aquarium_frame: 水槽の枠。暗いガンメタルの板に銅のボルト
6. aquarium_frame_light: 同じ枠の中央に縦のシアン発光帯
7. aquarium_glass: 水槽のガラス。淡い水色の面に斜めの白いハイライト線 2-3 本 (後で半透明にする)
8. aquarium_sand: 海底の淡い砂と小石
9. charging_station: 充電ステーションの本体。暗い金属、上端と下端にシアンの帯、青銅のアクセント
10. charging_station_screen: 暗い縁の中にシアンに光る画面と稲妻マーク (中央 6x9px に収まる)
11. charging_station_cable: 暗い灰色のケーブルとプラグ、小さな青銅の帯
12. habitat_membrane: 空気の膜。明るい水色の面に波紋と屈折の細い線、小さな泡 (後で半透明にする)
```

## シート BT1B (8個 / 4列×2行)
```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. gen_frame: 発電機の本体パネル。暗い鋼の工業パネル、リベット
2. gen_copper: 銅の配管/ブラケット/帯
3. gen_glow: シアンに光るライト帯/窓 (全面発光)
4. turbine_blade: 灰色の金属のタービン羽根の面、縦の補強線
5. geo_rock: 暗い火山岩の瓦礫
6. geo_lava: 橙に光る溶岩/加熱室 (全面発光)
7. bio_tank: 緑の生体油タンクのガラス、液体と昆布の影 (後で半透明にする)
8. bio_burner: 橙に光るバーナーの格子窓
```

## シート BT2A (8個 / 4列×2行)
```
Minecraft 1.20.1 item icon, hand-drawn 16x16 pixel art in vanilla style, each item centred in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters, no labels, no text. Limited palette, hard pixel edges, dark 1px outline like vanilla items, no anti-aliasing, no gradients, no glow halo. Never use magenta inside an item.
Palette: #14181e #343e4a #6c7c8a #9aa8b4 #b06a3a #1d6670 #2a9aa0 #3ee6f0 #b8f8ff #13506a
このシートの内容（左→右、上→下の順）:
1. creature_capture_canister: 空の捕獲容器。両端に暗い金属キャップと銅の留め具、透明なガラスの胴、横に取っ手 (斜め45度)
2. creature_capture_canister_filled: 同じ容器に青い水と小さな青い光る魚
3. aquarium_coral: 紫とティールの珊瑚と海藻 (植物スプライト、下端から生える)
4. habitat_icon_ladder: 工業用の金属梯子
5. habitat_icon_vertical_hatch: 床の 1x1 ハッチを梯子が貫通している
6. habitat_icon_aquarium: 小さな水槽 (暗い枠、角のシアン灯、水と魚)
7. habitat_icon_open_entrance: 扉枠に青い膜が張られた出入口
8. habitat_icon_charging_station: シアンの稲妻のある充電柱
```

## シート BT2B (9個 / 5列×2行)
```
Minecraft 1.20.1 item icon, hand-drawn 16x16 pixel art in vanilla style, each item centred in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters, no labels, no text. Limited palette, hard pixel edges, dark 1px outline like vanilla items, no anti-aliasing, no gradients, no glow halo. Never use magenta inside an item.
Palette: #14181e #343e4a #6c7c8a #9aa8b4 #b06a3a #1d6670 #2a9aa0 #3ee6f0 #b8f8ff #13506a
このシートの内容（左→右、上→下の順）。各アイコンは深海基地の設備の小さな斜め見下ろし (3/4) の模型:
1. habitat_icon_glass_wall: 鋼の壁パネルがガラス張りに変わる (半分壁・半分ガラス、右向き矢印)
2. habitat_icon_wall_revert: ガラスが鋼の壁に戻る (戻る矢印)
3. habitat_icon_current_turbine: 3 枚羽根の海中タービン
4. habitat_icon_geothermal_generator: 噴出孔の上の塔、下に橙の光
5. habitat_icon_biofuel_generator: 緑のタンクと煙突の発電機
6. habitat_icon_scan_upgrade: レーダーのパラボラと上向き矢印
7. habitat_icon_dismantle: 鋼のパネルとレンチ、橙のアクセント
8. habitat_icon_large_locker: 2 枚扉の大型ロッカー
9. habitat_icon_wall_workbench: 壁掛けの作業台 (作業面と小さな画面)
```
