# WRK01 残骸の中枢 テクスチャ — ChatGPT ImageGen 用

現在の `textures/block/wreck_core.png` は industrial_panel の色相をずらした仮 (tools/wreck_assets.py)。差し替えは 1 枚。
保存先に置けば、tools/wreck_assets.py は既存 PNG を上書きしない。

## プロンプト
```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft. ONE opaque tile, flat orthographic view of one block face filling the whole square, on flat solid magenta (#FF00FF) background, no labels, no text, no watermark.
Subject: the core of a rusted shipwreck, corroded dark iron plating with orange rust patches and bolts, a small glowing amber control panel / lamp in the middle that still shines faintly.
Limited palette of 5-7 colors, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow. Exactly 16x16 logical pixels. Never use magenta inside the tile.
```
