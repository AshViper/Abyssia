# PH01 仮テクスチャの作り直し — ChatGPT ImageGen 用

残っていた仮テクスチャ 10 枚 (2026-10-09 時点):
- ブロック 4: `wreck_core` (WRK01、industrial_panel の色相ずらし)、`volcanic_ash` / `sulfur_deposit` / `black_mineral_deposit` (B02、forge_textures の手続き生成)
- アイテム 6: `pressure_dive_tank` / `pressure_suit_leggings` / `pressure_flippers` (PRS01、placeholder_items.py の色替え)、`submarine_upgrade_depth_mk1-3` (耐圧船体アイコンの色替え)

保存先 `inbox/textures/sheets/<シート名>.png`。取込: `python tools/agentflow/sheets.py import auto --preset <シート名>` (先に `--dry-run`)。取込で texture_locks に固定される。
参考画像: `inbox/designs/ref-PH01.png` (既存テクスチャを 8 倍に拡大したもの) を一緒に添付する。

## シート PH1: ブロック 4 種 (4個 / 4列×1行)

```
Minecraft 1.20.1 block textures (opaque, seamless), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (deepslate, sand, gravel, iron block). Match the look of the attached reference textures from the same mod (dark deep-sea palette, calm flat areas, few colours).
Draw all 4 tiles on ONE sheet image: a grid of 4 columns and 1 row of equal square cells, left to right in the order listed below, each tile filling its own square cell, with wide flat solid magenta (#FF00FF) gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside a tile.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Each tile is a flat orthographic view of one block face filling the whole square. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
1. Shipwreck core: corroded dark iron hull plating with orange-brown rust patches and a few bolts, a small amber control lamp/panel in the middle that still shines faintly.
2. Volcanic ash: dark grey-brown loose ash like vanilla sand/gravel, a few tiny dark-red ember specks.
3. Sulfur deposit: loose yellow sulfur sediment like vanilla sand, muted mustard yellow (not neon), a few pale crystal grains.
4. Black mineral deposit: near-black loose sediment with a cold blue-grey tint, a few small dull metallic manganese nodules.
```

## シート PH2: アイテム 6 種 (6個 / 3列×2行)

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items (iron chestplate, iron leggings, turtle shell). Match the attached reference icons from the same mod: same outline weight, size and shading.
Draw all 6 icons on ONE sheet image: a grid of 3 columns and 2 rows of equal square cells, left to right, top to bottom in the order listed below. Each icon is ONE object centered in its own cell on flat solid magenta (#FF00FF) with wide magenta gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside an icon.
Flat few-colour pixel art: 1 px dark outline, 5-7 colours per icon, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no 3D render. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
Icons 1-3 are "hadal pressure" diving gear like the reference pressure diver helmet: dark gunmetal with a purple-blue cast (#1A1C2A #2C3050 #464C74 #6A6F90), bolted plates, pale rivets (#A3A8CC).
1. Pressure dive tank: back-mounted twin gunmetal cylinders with thick bolted bands and a small valve on top.
2. Pressure suit leggings: sealed diving suit leggings, heavy plated knees with bolts.
3. Pressure flippers: a pair of reinforced flippers, dark plated tops with bolted straps.
Icons 4-6 are submarine depth upgrade modules: the same chunky reinforced hull-plate module shape as the reference submarine upgrade icons (a tilted metal plate block with a frame), with 1, 2 or 3 small bright depth marks on its lower edge.
4. Depth upgrade Mk1: teal-green plates, 1 mark.
5. Depth upgrade Mk2: bronze-brown titanium plates, 2 marks.
6. Depth upgrade Mk3: deep violet plates, 3 marks.
```
