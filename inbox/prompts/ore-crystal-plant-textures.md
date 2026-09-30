# 鉱石・結晶塊・植物テクスチャ 再生成プロンプト (ChatGPT ImageGen 用)

シートごとに生成し、`inbox/textures/sheets/<シート名>.png` に保存する。Claude/Codex は画像を生成しない。
取込: ブロック(鉱石)は `tools/import_chatgpt_textures.py` 系、透過スプライト(結晶塊・植物)は `tools/import_item_sheet.py` 系で分割→16×16。
方針: バニラの手描き風、色数4-6、静かな面と少数の特徴。以前の「AI生成感が強い」を避ける。

## シートORE: 鉱石ブロック (14個 / 5列)

```
Minecraft 1.20.1 block texture (opaque, seamless), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (kelp, seagrass, amethyst cluster, diamond ore). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each tile is a fully opaque stone block face: dark deep-sea host rock as the base with the ore embedded in it, like vanilla ores. Keep the host rock identical across tiles.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. 深淵結晶鉱石: 深淵結晶(紫がかった暗青の結晶)が母岩に埋まる
2. 深淵鉄鉱石: 赤錆色の鉄の粒
3. コバルト鉱石: 深い青の鉱物粒
4. 深海銅鉱石: 橙〜緑青(青緑の錆)の銅の斑
5. 深海ニッケル鉱石: 明るい銀緑の粒
6. マンガン鉱石: 赤紫がかった黒い粒
7. モリブデン鉱石: 青灰の金属粒
8. 白金鉱石: 白く光る粒
9. 硫黄鉱石: 鮮黄色の結晶
10. テルル鉱石: 紫の結晶粒
11. 熱結晶鉱石: 橙赤に光る熱結晶が埋まる
12. タングステン鉱石: 黒く重い塊、灰の縞
13. バナジウム鉱石: 緑の斑
14. イットリウム鉱石: 淡金の粒
```

対応ID順: abyssal_crystal_ore, abyssal_iron_ore, cobalt_ore, deep_copper_ore, deep_nickel_ore, manganese_ore, molybdenum_ore, platinum_ore, sulfur_ore, tellurium_ore, thermal_crystal_ore, tungsten_ore, vanadium_ore, yttrium_ore

## シートCRYSTAL: 結晶塊・結晶の芽 (透過スプライト) (12個 / 4列)

```
Minecraft 1.20.1 crystal cluster sprite (cross-model plant style, transparent), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (kelp, seagrass, amethyst cluster, diamond ore). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each icon is drawn on flat magenta (#FF00FF) that contains no magenta in the icon; the crystal grows from the bottom edge, 1px dark outline, glowing core colors allowed but no glow/blur.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. 深淵結晶の塊: 紫がかった暗青の結晶の塊
2. コバルトの結晶塊: コバルト青の結晶塊
3. 深海結晶の塊: 深いシアンの結晶塊
4. ニッケルの結晶塊: 銀緑の結晶塊
5. 白い結晶の塊: 白い結晶の塊
6. 水圧結晶の塊: 水圧に耐える青白の緻密な結晶塊
7. 硫黄の結晶塊: 黄色い硫黄の結晶塊
8. 熱結晶の塊: 橙赤に光る熱結晶の塊
9. 小さな熱結晶の芽: 小さな橙赤の熱結晶の芽
10. 中くらいの熱結晶の芽: 中くらいの橙赤の熱結晶の芽
11. 結晶の針: 細く尖ったガラス質の結晶の針
12. 砕けた結晶: 砕けた結晶の破片
```

対応ID順: abyssal_crystal_cluster, cobalt_cluster, deep_crystal_cluster, nickel_cluster, pale_crystal_cluster, pressure_crystal_cluster, sulfur_cluster, thermal_crystal_cluster, small_thermal_crystal_bud, medium_thermal_crystal_bud, crystal_needle, crystal_shards

## シートPLANT1: 植物 1 (16個 / 4列)

```
Minecraft 1.20.1 plant sprite (cross-model, transparent), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (kelp, seagrass, amethyst cluster, diamond ore). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each icon is drawn on flat magenta (#FF00FF) that contains no magenta in the icon; the plant fills the tile from the bottom edge up, 1px dark outline, tall thin shapes stay within the tile.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. 深淵の花: 名前から連想される深海の植物
2. 深海草 (別途 top 版も必要): 名前から連想される深海の植物
3. 深海苔: 名前から連想される深海の植物
4. 深淵キノコ: 名前から連想される深海の植物
5. 深淵の蔓 (別途 tip 版も必要): 名前から連想される深海の植物
6. アンバーファン (別途 ripe 版も必要): 名前から連想される深海の植物
7. 古代植物の葉: 名前から連想される深海の植物
8. 灰緑の深海草 (別途 top 版も必要): 名前から連想される深海の植物
9. 洞窟の光花: 名前から連想される深海の植物
10. 洞窟サンゴ: 名前から連想される深海の植物
11. 洞窟シダ: 名前から連想される深海の植物
12. 洞窟草 (別途 top 版も必要): 名前から連想される深海の植物
13. 洞窟コンブ (別途 top 版も必要): 名前から連想される深海の植物
14. 洞窟苔: 名前から連想される深海の植物
15. 洞窟の根 (別途 tip 版も必要): 名前から連想される深海の植物
16. 洞窟カイメン: 名前から連想される深海の植物
```

対応ID順: abyssal_bloom, abyssal_grass, abyssal_moss, abyssal_mushroom, abyssal_vine, amber_fan, ancient_frond, ashen_abyssal_grass, cave_bloom, cave_coral, cave_fern, cave_grass, cave_kelp, cave_moss, cave_root, cave_sponge

## シートPLANT2: 植物 2 (16個 / 4列)

```
Minecraft 1.20.1 plant sprite (cross-model, transparent), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (kelp, seagrass, amethyst cluster, diamond ore). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each icon is drawn on flat magenta (#FF00FF) that contains no magenta in the icon; the plant fills the tile from the bottom edge up, 1px dark outline, tall thin shapes stay within the tile.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. 洞窟チューブ植物 (別途 top 版も必要): 名前から連想される深海の植物
2. 洞窟の蔓 (別途 tip 版も必要): 名前から連想される深海の植物
3. 燠茎: 名前から連想される深海の植物
4. 深海コンブ (別途 top 版も必要): 名前から連想される深海の植物
5. 深根植物 (別途 tip 版も必要): 名前から連想される深海の植物
6. 倒れたコンブ: 名前から連想される深海の植物
7. 浮遊花: 名前から連想される深海の植物
8. 巨大洞窟コンブ (別途 top 版も必要): 名前から連想される深海の植物
9. 巨大コンブ (別途 top 版も必要): 名前から連想される深海の植物
10. 巨大チューブ (別途 top 版も必要): 名前から連想される深海の植物
11. グラスレース (別途 ripe 版も必要): 名前から連想される深海の植物
12. ヒカリイソギンチャク: 名前から連想される深海の植物
13. ヒカリサンゴ: 名前から連想される深海の植物
14. 光端草: 名前から連想される深海の植物
15. 超深海の花: 名前から連想される深海の植物
16. 垂れコンブ (別途 tip 版も必要): 名前から連想される深海の植物
```

対応ID順: cave_tube_plant, cave_vine, cinder_stalk, deep_kelp, deep_root, fallen_kelp, floating_bloom, giant_cave_kelp, giant_kelp, giant_tube, glasslace, glow_anemone, glow_coral, glowtip_grass, hadal_bloom, hanging_kelp

## シートPLANT3: 植物 3 (16個 / 4列)

```
Minecraft 1.20.1 plant sprite (cross-model, transparent), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (kelp, seagrass, amethyst cluster, diamond ore). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each icon is drawn on flat magenta (#FF00FF) that contains no magenta in the icon; the plant fills the tile from the bottom edge up, 1px dark outline, tall thin shapes stay within the tile.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. 熱苔: 名前から連想される深海の植物
2. フシクキ (別途 top 版も必要): 名前から連想される深海の植物
3. ヒカリバネ (別途 ripe 版も必要): 名前から連想される深海の植物
4. 発光苔: 名前から連想される深海の植物
5. 鉱物蔓 (別途 top 版も必要): 名前から連想される深海の植物
6. 油胞藻 (別途 ripe 版も必要): 名前から連想される深海の植物
7. 耐圧瓢 (別途 ripe 版も必要): 名前から連想される深海の植物
8. 樹脂根 (別途 tip 版も必要): 名前から連想される深海の植物
9. ウミシダ: 名前から連想される深海の植物
10. ソウルサンゴ: 名前から連想される深海の植物
11. カイメン植物: 名前から連想される深海の植物
12. ストランド藻 (別途 ripe 版も必要): 名前から連想される深海の植物
13. 青緑の深海草 (別途 top 版も必要): 名前から連想される深海の植物
14. 熱水植物: 名前から連想される深海の植物
15. 熱水チューブ (別途 top 版も必要): 名前から連想される深海の植物
16. チューブ植物 (別途 top 版も必要): 名前から連想される深海の植物
```

対応ID順: heat_moss, knotstalk, lumen_quill, luminous_moss, mineral_vine, oil_bladder_weed, pressure_gourd, resin_root, sea_fern, soul_coral, sponge_plant, strandweed, teal_abyssal_grass, thermal_plant, thermal_tube, tube_plant

## シートPLANT4: 植物 4 (6個 / 4列)

```
Minecraft 1.20.1 plant sprite (cross-model, transparent), hand-drawn 16x16 pixel art in the style of vanilla Minecraft (kelp, seagrass, amethyst cluster, diamond ore). Each texture is ONE tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Each icon is drawn on flat magenta (#FF00FF) that contains no magenta in the icon; the plant fills the tile from the bottom edge up, 1px dark outline, tall thin shapes stay within the tile.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain. Dark deep-sea mood, but each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. 噴出孔草: 名前から連想される深海の植物
2. 青紫の深海草 (別途 top 版も必要): 名前から連想される深海の植物
3. 虚無のコンブ: 名前から連想される深海の植物
4. 虚無のコンブの茎: 名前から連想される深海の植物
5. 壁シダ: 名前から連想される深海の植物
6. 壁面鉱物蔓: 名前から連想される深海の植物
```

対応ID順: vent_grass, violet_abyssal_grass, void_kelp, void_kelp_plant, wall_fern, wall_mineral_vine

