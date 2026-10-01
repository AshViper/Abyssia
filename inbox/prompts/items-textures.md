# アイテムテクスチャ 再生成プロンプト (ChatGPT ImageGen 用)

シート単位（A/B/C）で生成する。Claude は画像を生成しない。生成画像は `inbox/textures/items/sheet_<A|B|C>.png` に保存（並び順は下記の番号順、左→右・上→下）。
取込は画像受領後に Claude が行う（マゼンタ背景を透過化→セル分割→16×16→減色→`textures/item/<id>.png`）。

## 共通プロンプト（各シートの先頭に付ける）

```
Minecraft 1.20.1 item icon sheet, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items
(iron ingot, raw copper, amethyst shard, slime ball). Each icon is ONE object, centered in its own square cell,
drawn on a flat solid magenta (#FF00FF) background that contains no magenta in the icon itself.
Grid of equal square cells with wide magenta gutters between icons, no cell borders, no labels, no text, no watermark.
Bold readable silhouette with a 1px dark outline, limited palette of 4-6 colors per icon, hard pixel edges,
no anti-aliasing, no gradients, no blur, no glow, no drop shadow, no perspective. Slightly asymmetric hand-made look;
avoid over-detailed noise. Diagonal / tilted composition for tools. Each icon must be clearly different from the others.
Every icon is drawn at exactly 16x16 logical pixels (square pixels, integer scale).
```

## シートA: 装備 (7個 / 4列×2行)

方針: 深海合金 ティール青錆 #26343a #45656a #70a4a1 #b5d4c8 #e6f2df / 柄 #49392a #806044 #b18a5b

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. 深海合金のつるはし。ティールのヘッド、暗い木の柄、斜め構図
2. 深海合金の斧。幅広の刃、木の柄、斜め構図
3. 深海合金のシャベル。丸い刃、木の柄、斜め構図
4. 深海合金のクワ。細い刃、木の柄、斜め構図
5. 深海合金の剣。ティールの刃と暗い鍔・柄、斜め構図
6. 深海潜水ヘルム。丸い金属ヘルメットと丸窓のガラス、リベット。正面図
7. 深海フィン。ダイバー用の足ヒレ一足、ティールと暗い青の水かき。斜め構図
```

対応ID順: abyssal_alloy_pickaxe, abyssal_alloy_axe, abyssal_alloy_shovel, abyssal_alloy_hoe, abyssal_alloy_sword, deep_diver_helmet, abyssal_flippers

## シートB: 金属 (19個 / 5列×4行)

方針: インゴットは同じ形(斜めに置いた延べ棒)で色と質感だけ変える。生鉱石(raw_)は不揃いな原石の塊で、金属色の粒/縞が入る

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. 深海合金インゴット。青錆ティール #26343a #45656a #70a4a1 #b5d4c8
2. コバルトインゴット。深い青 #10204a #1f4a9a #3f7ad0 #a9c8f0
3. マンガンインゴット。赤紫がかった灰 #2b1f2a #5a3f55 #8a6a86 #c4a8c0
4. モリブデンインゴット。青みのある鋼色 #232b33 #4c5c6b #7f93a4 #c4d2dc
5. ニッケルインゴット。明るい銀白 #3a3f40 #7d8a88 #b9c6c2 #eef4f0
6. プラチナインゴット。輝く白金 #4a4d55 #9a9fab #d6dae2 #ffffff
7. テルルインゴット。紫がかった銀灰 #2a2433 #5a4f6b #8f84a3 #cbc3d8
8. タングステンインゴット。ほぼ黒の重い灰 #14161a #2d3238 #4b525b #7d868f
9. バナジウムインゴット。緑がかった鋼 #1c2a24 #3c5a4b #6b9a80 #b5dcc5
10. イットリウムインゴット。温かい淡金グレー #3a3428 #7a6f55 #b5a680 #ebdfb8
11. 生コバルト。青い粒が入った暗灰の原石
12. 生マンガン。赤紫の縞が入った暗灰の原石
13. 生モリブデン。青灰の金属粒が入った原石
14. 生ニッケル。銀白の粒が入った灰緑の原石
15. 生プラチナ。白く光る粒が入った暗灰の原石
16. 生テルル。紫の結晶粒が入った原石
17. 生タングステン。黒い重そうな塊、灰の縞
18. 生バナジウム。緑の斑が入った原石
19. 生イットリウム。淡金の粒が入った砂色の原石
```

対応ID順: abyssal_alloy_ingot, cobalt_ingot, manganese_ingot, molybdenum_ingot, nickel_ingot, platinum_ingot, tellurium_ingot, tungsten_ingot, vanadium_ingot, yttrium_ingot, raw_cobalt, raw_manganese, raw_molybdenum, raw_nickel, raw_platinum, raw_tellurium, raw_tungsten, raw_vanadium, raw_yttrium

## シートC: 素材 (21個 / 6列×4行)

方針: 深海のバイオ/結晶/熱水由来の素材。各アイテムは形で見分けられること

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. 深淵結晶の欠片。紫がかった暗青の尖った結晶
2. 熱結晶の欠片。橙赤に光る尖った結晶
3. 結晶レンズ。淡いシアンの丸いレンズ、ハイライト1点
4. 結晶樹液。シアンの雫
5. 生体油。黄褐色の油が入った小瓶
6. 精製油。明るい琥珀色の油が入った角瓶
7. 深海繊維。青緑の繊維の束
8. 深海色素。青紫の粉の小山
9. 繊維ロープ。巻いた麻色のロープ
10. 超深海殻。暗い青黒の巻貝/瓢のような殻
11. 超深海殻板。青黒の四角い殻の板、リベット
12. 硬質茎。暗い茶灰の節のある硬い茎
13. 発光素子。緑青に淡く光る小さな六角のセル
14. 発光ゲル。黄緑に光る粘つく塊
15. 海洋接着剤。ねばつく灰白のチューブ/塊
16. 有機物。暗褐色の湿った塊
17. 植物樹脂。琥珀色の半透明の固まり
18. 海布。青緑の織り目のある四角い布
19. 硫黄。鮮やかな黄色の結晶の山
20. 熱フェルト。赤茶のフェルト板
21. 熱繊維。赤橙の繊維の束
```

対応ID順: abyssal_crystal_shard, thermal_crystal_shard, crystal_lens, crystal_sap, bio_oil, refined_oil, deep_fiber, deep_pigment, fiber_rope, hadal_husk, hadal_plating, hard_stalk, lumen_cell, lumen_gel, marine_adhesive, organic_matter, plant_resin, sea_cloth, sulfur, thermal_felt, thermal_fiber

