# FD01 / TR01 / CB01 テクスチャ — ChatGPT ImageGen 用

## シート COB1 (7個 / 4列×2行、CB01 深海岩の丸石)

```
Minecraft 1.20.1 block textures, hand-drawn 16x16 pixel art in the exact style of vanilla Minecraft COBBLESTONE: irregular rounded stone chunks of different sizes packed together, each chunk with a lighter top-left edge and darker bottom-right edge, dark mortar gaps between chunks, tiles seamlessly. Each texture is ONE opaque tile in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic view of one block face filling the whole square, no perspective.
Limited palette of 5-6 colors per tile (use ONLY the palette given for that tile, darkest = mortar gaps, lightest = chunk highlights), hard pixel edges, no anti-aliasing, no gradients, no blur. Draw at exactly 16x16 logical pixels. Never use magenta inside a tile. Same chunk layout family for all 7 tiles but vary it slightly so they are not identical.
1. cobbled_deep_sea_rock: blue-grey deep sea rock. Palette #314561 #354a68 #3a5070 #42597a #486284 #336b89
2. cobbled_abyssal_rock: deep indigo-blue rock. Palette #1b3055 #1f365e #243d6f #254272 #2f3d89 #394fa2
3. cobbled_trench_rock: very dark navy rock. Palette #1a2d49 #1e3453 #1f3759 #243f62 #284569 #2d5a8f
4. cobbled_thermal_rock: dark red hydrothermal rock. Palette #4d2222 #542221 #5f2624 #752922 #923023 #a53721
5. cobbled_volcanic_rock: near-black basalt. Palette #131214 #171618 #1b1a1c #252427 #302f33 #3c3b40
6. cobbled_crystal_rock: pale slate-blue rock with a few tiny bright crystal flecks (#9fc4e8). Palette #334363 #405374 #485c7f #51678b #576e91 #637a9e
7. cobbled_mineral_host_rock: warm grey-brown rock. Palette #594b49 #62524f #665654 #72625f #766663 #8a7976
```

## シート TREE1 (1個 / 1列、TR01 古代樹の苗)

```
Minecraft 1.20.1 sapling texture, hand-drawn 16x16 pixel art in the style of vanilla saplings (oak/spruce/mangrove propagule): a small young underwater tree, thin dark teal-brown stem rising from the bottom centre, a few drooping deep-sea fronds (dark teal to sea green with a few faint cyan bioluminescent tips) at the top. The plant stands on the bottom edge of its square cell, centred, on flat solid magenta (#FF00FF) background with wide magenta margins, no ground, no labels, no text. Transparent-style sprite: only the plant, everything else magenta. Limited palette, hard pixel edges, no anti-aliasing, no gradients, no glow halo. Never use magenta inside the plant.
1. ancient_sapling
Palette: #1f2a26 #2e3b33 #3d4f45 (stem), #0f3a40 #1d6670 #2a8a7a #3fae94 (fronds), #7ff0e0 (tips)
```

## 食料アイコン共通プロンプト

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in the exact style of vanilla food items (cooked cod, mushroom stew, pumpkin pie, baked potato, kelp, rabbit stew). Each item centred in its own equal square cell on flat solid magenta (#FF00FF) with wide magenta gutters between cells, no cell borders, no labels, no text, no watermark. Limited palette per item, dark 1px outline like vanilla items, hard pixel edges, no anti-aliasing, no gradients, no glow halo, no drop shadow. Draw at exactly 16x16 logical pixels. Never use magenta inside an item.
Deep-sea theme: ingredients are deep-sea fungi (pale violet cap with faint cyan glowing spots), a pressure gourd (thick dark olive husk, pale teal-green flesh), dark sea-green kelp leaves, pale pink-white fish meat, translucent lavender jellyfish tentacles. Bowls are the vanilla wooden bowl. Skewers use a vanilla stick. Pies look like the vanilla pumpkin pie with a different filling colour. Grilled food has dark char stripes.
```

## シート FOOD1 (12個 / 4列×3行)

```
（共通プロンプト）
1. mushroom_cap: a single raw deep-sea mushroom cap, pale violet with cyan glowing dots
2. gourd_flesh: a slice of pressure gourd, pale teal-green flesh with a dark olive rind edge
3. kelp_leaf: one dark sea-green kelp leaf with wavy edges
4. fish_mushroom_skewer: stick skewer with cooked fish chunks and violet mushroom pieces
5. gourd_fish_skewer: stick skewer with grilled shark meat and teal-green gourd pieces
6. kelp_fish_skewer: stick skewer with fish wrapped in green kelp
7. mushroom_stew: wooden bowl of creamy violet mushroom stew
8. gourd_soup: wooden bowl of pale teal-green gourd soup
9. kelp_soup: wooden bowl of dark green kelp soup
10. fish_soup: wooden bowl of pale cream fish soup with fish chunks
11. mushroom_fish_stew: wooden bowl of brown stew with fish and violet mushroom pieces
12. gourd_fish_stew: wooden bowl of orange-brown stew with gourd and meat chunks
```

## シート FOOD2 (12個 / 4列×3行)

```
（共通プロンプト）
1. kelp_fish_stew: wooden bowl of dark green stew with fish chunks
2. mushroom_pie: pumpkin-pie-shaped pie with a violet mushroom filling
3. gourd_pie: pumpkin-pie-shaped pie with a pale teal-green filling
4. fish_pie: pumpkin-pie-shaped pie with a golden crust and fish heads poking out
5. mushroom_fish_pie: pie with crust lattice showing violet mushroom and fish filling
6. gourd_fish_pie: pie with crust lattice showing teal gourd and meat filling
7. kelp_fish_pie: pie with crust lattice showing green kelp and fish filling
8. preserved_fish: a dried, resin-glazed fish fillet, amber-brown and glossy
9. smoked_mushroom: a smoked, darkened brown-violet mushroom cap
10. smoked_gourd: a smoked gourd slice, darker teal with brown edges
11. grilled_kelp: a grilled kelp leaf, dark green with black char stripes
12. mushroom_fish_grill: grilled fish fillet with a violet mushroom on top, char stripes
```

## シート FOOD3 (9個 / 3列×3行)

```
（共通プロンプト）
1. gourd_fish_grill: grilled shark steak with gourd slices, char stripes
2. kelp_fish_grill: grilled fish wrapped in kelp, char stripes
3. jellyfish_skewer: stick skewer with translucent lavender jellyfish tentacle pieces and kelp
4. jellyfish_stew: wooden bowl of translucent lavender broth with tentacles and a faint glow dot
5. abyssal_survival_ration: compact ration bar wrapped in brown fibre cloth tied with string
6. thermal_ration: dark red-brown pressed ration block with orange fibre wrapping
7. mushroom_salad: wooden bowl of violet mushroom slices and green kelp leaves
8. gourd_kelp_salad: wooden bowl of teal gourd cubes on kelp leaves with clear sap drops
9. abyssal_vegetable_stew: wooden bowl of hearty stew with violet mushroom, teal gourd and green kelp
```
