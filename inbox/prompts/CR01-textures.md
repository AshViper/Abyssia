# CR01 結晶の欠片 — ChatGPT ImageGen 用

新しい素材アイテム 2 つ (2026-10-10): `deep_crystal_shard` (深海結晶の欠片、deep_crystal_cluster を壊すと落ちる)、`pressure_crystal_shard` (水圧結晶の欠片、pressure_crystal_cluster から)。仮は tools/mineral_textures.py の手続き生成。

保存先 `inbox/textures/sheets/CR1.png`。取込: `python tools/agentflow/sheets.py import auto --preset CR1` (先に `--dry-run`)。取込で texture_locks に固定される。NeoForge worktree にもコピーする。
参考画像: `inbox/designs/ref-CR01.png` (左から abyssal_crystal_shard、thermal_crystal_shard、deep_crystal_cluster、pressure_crystal_cluster を 12 倍に拡大) を一緒に添付する。

## シート CR1: アイテム 2 種 (2個 / 2列×1行)

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items (amethyst shard, prismarine shard, quartz). Match the attached reference images from the same mod: the first two are existing crystal shard icons (same outline weight, size and shading as these), the last two are the crystal cluster blocks the new shards drop from (take each shard's colours from its cluster).
Draw both icons on ONE sheet image: a grid of 2 columns and 1 row of equal square cells, left to right in the order listed below. Each icon is ONE object centered in its own cell on flat solid magenta (#FF00FF) with wide magenta gutters between cells. No labels, no text, no cell borders, no watermark. Never use magenta inside an icon.
Flat few-colour pixel art: 1 px dark outline, 5-7 colours per icon, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow, no 3D render. Draw at exactly 16x16 logical pixels (square pixels, integer scale). Each icon fills most of its cell like the reference shards (about 12-14 of 16 pixels tall). Each must have a silhouette clearly different from the reference shards and from each other.
1. Deep crystal shard: a small bundle of 2-3 slender, upright, glassy cyan-teal crystal prisms with sharp pointed tips and a broken flat base, the tallest in the middle; a bright pale-cyan inner glow line along the core. Colours from the deep crystal cluster: #164267 #19607F #1F708F #2C9FB9 #40B2C6 #5ED3DE.
2. Pressure crystal shard: one heavy, wide, flat crystal blade leaning diagonally (about 45 degrees), with 1-2 smaller blade fragments at its base, faceted like compressed glass, cold periwinkle blue-violet. Colours from the pressure crystal cluster: #303687 #4B5CB5 #596FD2 #7992E3 #8AA4E8 #A4BDEE.
```
