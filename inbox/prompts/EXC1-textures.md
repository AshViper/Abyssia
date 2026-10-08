# EXC1 深海採掘機 テクスチャ — ChatGPT ImageGen 用

採掘機 (Mk1 / Mk2) を発電機と同じ「コードで作る多ブロック構造」にするための、ブロックの面テクスチャ 8 枚とメニュー用アイコン 1 枚。
保存先 `inbox/textures/sheets/EXC1.png` / `EXC1I.png`。取込: `python tools/agentflow/sheets.py import auto --preset EXC1` (と EXC1I)。
Mk2 は取込後に `tools/excavator_textures.py` が色相をずらして `exc2_*` と Mk2 アイコンを作る (ChatGPT では描かない)。

## 共通プロンプト
```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft (iron block, blast furnace, observer, copper, sea lantern). Each texture is ONE opaque tile in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic view of one block face filling the whole square, no perspective, no 3D render.
Limited palette of 4-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no soft glow halo, no drop shadow. Draw at exactly 16x16 logical pixels (square pixels, integer scale). Never use magenta inside a tile.
Theme: deep-sea habitat equipment (same family as the existing base: dark gunmetal, blue-grey steel, thick plates with bolts, copper/bronze pipe accents, cyan light strips).
Palette guide: #14181e #232a33 #343e4a #4a5866 #6c7c8a #9aa8b4 (metal), #7a4a2a #b06a3a #d89060 (copper), #0f3a40 #1d6670 #2a9aa0 (teal), #3ee6f0 #b8f8ff #eafcff (glow), #0b2a3a #13506a (glass tint).
```

## シート EXC1 (8個 / 4列×2行)
```
（共通プロンプト）
このシートの内容（左→右、上→下の順）。すべて「海底で鉱床を掘る大型の採掘機」の外装パーツ:
1. exc_plate: 機体の外板。青灰色の厚い鋼板、四隅にリベット、細い継ぎ目線
2. exc_plate_dark: 暗いガンメタルの外板。横長の通気スリット 3 本、角にボルト
3. exc_hazard: 黄と黒の斜めの警告ストライプ (幅 4px 程度)、縁は暗い金属
4. exc_drill: ドリル/オーガーの鋼。明るい灰色の金属に斜めのらせん状の溝 (斜めの暗い帯が繰り返す)、先端側は少し擦れて明るい
5. exc_pipe: 銅/青銅の配管。横に走る太い管、中央に継ぎ目のバンド、小さなボルト
6. exc_glow: シアンに光るランプ/窓 (全面発光)。中央が最も明るく、縁は少し暗いシアン
7. exc_frame: 暗い鋼のトラス/脚部のフレーム。X 字の補強材、鉄の質感
8. exc_vent: 排気/冷却の格子。暗い金属の縦格子、奥にわずかに暖色のオレンジが覗く
```

## シート EXC1I
```
同じ「深海採掘機」の、拠点建設メニュー用アイコンを 1 個描いてください。
- 白い背景の中央に 1 個。16x16 ピクセルのドット絵を、ピクセルの形が崩れないように大きく拡大したもの (ぼかし・アンチエイリアス・影・文字は入れない)
- 採掘機を斜め前から見た全身: 四隅に太い脚、中央に青灰色の箱型の機体 (正面にシアンの発光窓、上に黄と黒の警告帯と小さな赤い回転灯)、機体の下の中央から地面へ突き出す大きなドリル (銀色、らせん溝)
- 背景の白とはっきり区別できる輪郭 (1 ピクセルの暗い縁取り)、使う色は 8-12 色
```
