# ORE02 新鉱床 7 種のテクスチャ — ChatGPT ImageGen 用

ECO02 で増えた金属鉱床 (チタン / 鉛 / 亜鉛 / イリジウム / ウラン / ネオジム / トリウム) のブロック面。
保存先 `inbox/textures/sheets/ORE02.png`。取込: `python tools/agentflow/sheets.py import auto --preset ORE02` (ドライラン後に取り込む)。
それまでは `tools/forge_textures.py` の仮テクスチャ (既存の鉱石の再着色) が入っている。

## シート ORE02: 新鉱床ブロック (7個 / 4列)

```
Minecraft 1.20.1 block texture (opaque, seamless), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (deepslate ores, kelp, seagrass). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each tile is a fully opaque stone block face: dark deep-sea host rock as the base with the ore embedded in it, like vanilla ores. Keep the host rock identical across tiles.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. チタン鉱床: 銀青色の鋼のような細い筋
2. 鉛鉱床: 鈍い青灰色の重たい塊
3. 亜鉛鉱床: 青みがかった白い結晶の筋
4. イリジウム鉱床: 銀白色の硬く小さな粒
5. ウラン鉱床: 黄緑色の淡い粒 (ごくわずかに明るい)
6. ネオジム鉱床: 紫がかった銀の斑
7. トリウム鉱床: 灰緑色の鈍い塊
```

対応ID順: titanium_ore, lead_ore, zinc_ore, iridium_ore, uranium_ore, neodymium_ore, thorium_ore
