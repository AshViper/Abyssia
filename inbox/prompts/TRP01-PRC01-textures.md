# TRP01 / PRC01 の新規テクスチャ — ChatGPT ImageGen 用

2026-11-21 ユーザー指示「テクスチャは ChatGPT で新規に作成する」。既存テクスチャの流用をやめて専用の絵にする 16 枚:
- アイテム 6 (ボス素材、モデルもテクスチャも無かった分): `kraken_beak` / `kraken_ink_sac` / `mother_bell_gel` / `abyss_heart` / `trench_wyrm_fang` / `angler_lure`
- アイテム 6 (TRP01 の上位品、Mk1 の絵を流用していた分): `submarine_upgrade_serpent_scale_hull` / `submarine_upgrade_jet_thruster` / `submarine_upgrade_abyss_heart_cell` / `submarine_upgrade_lure_sonar` / `heart_tank` / `fang_drill`
- ブロック 4 (PRC01 熱水析出機、選択浸出分離機の絵を流用していた分): `hydrothermal_precipitator_front` / `_front_on` / `_side` / `_top`

保存先 `inbox/textures/sheets/<シート名>.png`。取込: `python tools/agentflow/sheets.py import auto --preset <シート名>` (先に `--dry-run`)。取込で texture_locks に固定される。
参考画像: `inbox/designs/ref-TRP01.png` (既存テクスチャを 8 倍に拡大したもの) を一緒に添付する。

## シート TRP1: ボス素材 6 種 (6個 / 3列×2行)

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items (nautilus shell, ink sac, heart of the sea, slime ball, prismarine shard). Match the attached reference icons from the same mod: same outline weight, size and shading.
Draw all 6 icons on ONE sheet image: a grid of 3 columns and 2 rows of equal square cells, left to right, top to bottom in the order listed below. Each icon is ONE object centered in its own cell on flat solid magenta (#FF00FF) with wide magenta gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside an icon.
Flat few-colour pixel art: 1 px dark outline, 5-7 colours per icon, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no 3D render. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
These are trophies dropped by giant deep-sea bosses: dark abyssal colours with one small bright accent each.
1. Kraken beak: a large hooked squid beak, two curved halves, glossy near-black brown with an amber edge.
2. Kraken ink sac: a plump dark indigo ink sac with a tied neck and one small cyan bioluminescent spot.
3. Mother bell gel: a wobbly blob of translucent deep-blue jellyfish gel with a pale cyan highlight.
4. Abyss heart: an organic heart-shaped core, dark violet flesh with glowing cyan veins and a bright centre.
5. Trench wyrm fang: one long curved fang, bone white fading to a teal-stained root.
6. Angler lure: a glowing anglerfish lure bulb, pale yellow-green light, on a short dark curved stalk.
```

## シート TRP2: 上位品 6 種 (6個 / 3列×2行)

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items (iron chestplate, netherite pickaxe, turtle shell). Match the attached reference icons from the same mod: same outline weight, size and shading.
Draw all 6 icons on ONE sheet image: a grid of 3 columns and 2 rows of equal square cells, left to right, top to bottom in the order listed below. Each icon is ONE object centered in its own cell on flat solid magenta (#FF00FF) with wide magenta gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside an icon.
Flat few-colour pixel art: 1 px dark outline, 5-7 colours per icon, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no 3D render. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
Icons 1-4 are upgraded submarine modules built from boss trophies: the same chunky module shapes as the reference submarine upgrade icons (hull plate, thruster, battery, sonar), but each visibly made of a creature part.
1. Serpent-scale hull: the reference hull-plate module covered in overlapping dark teal sea-serpent scales with a cyan sheen.
2. Jet thruster: the reference thruster module with a black kraken-beak nozzle and an indigo housing, a small cyan jet at the back.
3. Abyss heart cell: the reference battery module with a glowing violet-and-cyan organic heart sealed behind a glass window.
4. Lure sonar: the reference sonar module with a glowing yellow-green anglerfish lure bulb as its emitter.
5. Heart tank: back-mounted twin gunmetal diving cylinders like the reference pressure dive tank, joined by a glowing violet-cyan heart core between them.
6. Fang drill: a hand-held mining drill like the reference abyssal drill, its bit a spiral of bone-white wyrm fangs, dark teal body.
```

## シート PRC1: 熱水析出機 4 面 (4個 / 4列×1行)

```
Minecraft 1.20.1 block textures (opaque, seamless edges), hand-drawn 16x16 pixel art in the style of vanilla Minecraft machine blocks (blast furnace, smoker, lodestone). Match the look of the attached reference machine faces from the same mod (dark deep-sea industrial palette, calm flat areas, few colours, a thin darker frame around each face).
Draw all 4 tiles on ONE sheet image: a grid of 4 columns and 1 row of equal square cells, left to right in the order listed below, each tile filling its own square cell, with wide flat solid magenta (#FF00FF) gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside a tile.
Limited palette of 5-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no perspective, no 3D render. Sparse detail with large calm areas; avoid noisy AI grain. Each tile is a flat orthographic view of one block face filling the whole square. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
The block is a hydrothermal precipitator: a corrosion-proof machine that sits beside a deep-sea vent and collects metal from the hot mineral water. Dark gunmetal casing with copper-bronze fittings.
1. Front, idle: a round dark glass intake port in the middle with a copper ring and a small dark indicator lamp below it.
2. Front, running: exactly the same layout as tile 1, but the port glows orange-amber with hot mineral water and the lamp is lit amber.
3. Side: plain plated casing with two horizontal copper pipes and four corner bolts.
4. Top: plated lid with a square grated vent in the centre and four corner bolts.
```

## シート QOL1: 新アイテム 3 種 (3個 / 3列×1行)

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items (iron ingot, paper, shulker shell). Match the attached reference icons from the same mod: same outline weight, size and shading.
Draw all 3 icons on ONE sheet image: a grid of 3 columns and 1 row of equal square cells, left to right in the order listed below. Each icon is ONE object centered in its own cell on flat solid magenta (#FF00FF) with wide magenta gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside an icon.
Flat few-colour pixel art: 1 px dark outline, 5-7 colours per icon, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no 3D render. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
1. Hull patch kit: a small square steel patch plate with four rivets, lying on a rolled strip of orange sealant tape.
2. Hull reinforcement: a thick blue-grey titanium brace plate with a raised cross rib and bolts at the corners.
3. Supply contract: a rolled-up cream paper order sheet tied with a teal cord, with a small dark wax seal.
```

## シート LOG1: 物流ブロック 5 面 (5個 / 5列×1行)

```
Minecraft 1.20.1 block textures (opaque, seamless edges), hand-drawn 16x16 pixel art in the style of vanilla Minecraft machine blocks (dropper, observer, lodestone). Match the look of the attached reference machine faces from the same mod (dark deep-sea industrial palette, calm flat areas, few colours, a thin darker frame around each face).
Draw all 5 tiles on ONE sheet image: a grid of 5 columns and 1 row of equal square cells, left to right in the order listed below, each tile filling its own square cell, with wide flat solid magenta (#FF00FF) gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside a tile.
Limited palette of 5-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no perspective, no 3D render. Sparse detail with large calm areas; avoid noisy AI grain. Each tile is a flat orthographic view of one surface filling the whole square. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
1. Logistics pump metal: plain copper-bronze pump casing plating (used as a material wrapped around a small pipe valve), with one thin darker band and two small rivets. No picture in the middle.
Tiles 2-5 are a cargo uplink, a dark gunmetal machine with teal fittings that beams crates between bases.
2. Cargo uplink front, sending: a square cargo hatch in the middle with a teal frame and a small dark arrow-shaped lamp above it (unlit).
3. Cargo uplink front, receiving: exactly the same layout as tile 2, but the hatch frame and the lamp glow bright cyan.
4. Cargo uplink side: plain plated casing with a vertical row of three small teal signal slots and four corner bolts.
5. Cargo uplink top: plated lid with a round teal antenna dish socket in the centre and four corner bolts.
```

## シート TRP3: 潰れ気味だった 3 アイコンの作り直し (3個 / 3列×1行、2026-11-21)

TRP2 の `submarine_upgrade_serpent_scale_hull` / `submarine_upgrade_abyss_heart_cell` / `heart_tank` は 16px に落とすと細部が潰れたので、形を単純にして作り直した。抽出データは `inbox/textures/TRP01-PRC01/TRP3.txt` (decode.py で復元)。

```
Create exactly one image. Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items. Match the attached reference icons from the same mod (top row): same outline weight and shading. These three icons must stay readable when shrunk to a true 16x16 grid, so keep them VERY simple: one big bold silhouette each, large flat colour areas, only 4-5 colours per icon, no small details, no thin lines, no texture noise.
Draw all 3 icons on ONE sheet image: a grid of 3 columns and 1 row of equal square cells, left to right in the order listed below. Each icon is ONE object centered in its own cell and filling most of the cell, on flat solid magenta (#FF00FF) with wide magenta gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside an icon.
1 px dark outline, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no 3D render. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
1. Serpent-scale hull plate: a single tilted rectangular armour plate, dark teal, covered by three rows of big rounded cyan-edged scales, with a dark steel rim.
2. Abyss heart cell: an upright battery canister with dark steel caps at top and bottom and a wide glass middle showing one big glowing violet heart shape with a bright cyan centre.
3. Heart tank: two fat upright gunmetal diving cylinders side by side with rounded tops, joined in the middle by one big glowing violet-and-cyan heart core.
```
