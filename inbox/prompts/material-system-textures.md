# 素材システム 新規アイテムテクスチャ プロンプト (ChatGPT ImageGen 用)

シート単位で生成する。Claude は画像を生成しない。生成画像は Agent Flow の画像生成タブから取り込む（既存の仮テクスチャ `tools/placeholder_items.py` 生成物を上書きしてロックする）。並び順は番号順、左→右・上→下。

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

## シートM1: 粉・精鉱・基本部品 (16個 / 4列×4行)

方針: 粉末は小さな山、精鉱は粗い鉱石片、部品は金属色で形を変える

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. 破砕ハンマー (iron-headed hammer with short wooden handle, heavy square head) 配色 #3b3b3f #8a8a90 #c9c9cf #7a5a3a
2. 鉄粉 (fine iron metal dust pile in a small mound) 配色 #20202a #b8b8b8 #e8e8e8
3. コバルト粉 (fine cobalt metal dust pile in a small mound) 配色 #20202a #2f5fa8 #e8e8e8
4. ニッケル粉 (fine nickel metal dust pile in a small mound) 配色 #20202a #b3c2bc #e8e8e8
5. マンガン粉 (fine manganese metal dust pile in a small mound) 配色 #20202a #8a5f9a #e8e8e8
6. バナジウム粉 (fine vanadium metal dust pile in a small mound) 配色 #20202a #8caf6a #e8e8e8
7. タングステン粉 (fine tungsten metal dust pile in a small mound) 配色 #20202a #464c58 #e8e8e8
8. テルル粉 (fine tellurium metal dust pile in a small mound) 配色 #20202a #7f9f9a #e8e8e8
9. イットリウム粉 (fine yttrium metal dust pile in a small mound) 配色 #20202a #d8d59b #e8e8e8
10. コバルト精鉱 (coarse gritty cobalt-tinted ore chunks, dull) 配色 #2a2a2a #2f5fa8 #7a7a72
11. マンガン精鉱 (coarse gritty manganese-tinted ore chunks, dull) 配色 #2a2a2a #8a5f9a #7a7a72
12. ニッケル精鉱 (coarse gritty nickel-tinted ore chunks, dull) 配色 #2a2a2a #b3c2bc #7a7a72
13. 鉄板 (flat rectangular iron plate with bevelled edge) 配色 #4a4a50 #9a9aa2 #d0d0d6
14. 鉄棒 (thin iron rod, diagonal) 配色 #4a4a50 #9a9aa2 #d0d0d6
15. 鉄歯車 (small iron cog wheel with 8 teeth and center hole) 配色 #3a3a40 #8a8a92 #c8c8d0
16. 銅線 (coiled copper wire spool, orange) 配色 #5a2a1a #c8734a #f0a070
```

対応ID順: crushing_hammer, iron_powder, cobalt_powder, nickel_powder, manganese_powder, vanadium_powder, tungsten_powder, tellurium_powder, yttrium_powder, cobalt_concentrate, manganese_concentrate, nickel_concentrate, iron_plate, iron_rod, iron_gear, copper_wire

## シートM2: 合金・複合素材 (12個 / 4列×3行)

方針: 合金インゴットは同じ斜め延べ棒で色だけ変える

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. 耐食合金インゴット (metal ingot, corrosion alloy ingot, distinct hue) 配色 #36585c #5f8f8a #a9d1c7
2. 高強度合金インゴット (metal ingot, high-strength alloy ingot, distinct hue) 配色 #3a2f45 #6d5a80 #b5a3c8
3. 耐熱合金インゴット (metal ingot, heat-resistant alloy ingot, distinct hue) 配色 #4a2f24 #8a5a44 #d69a7a
4. タングステン合金インゴット (metal ingot, tungsten alloy ingot, distinct hue) 配色 #25282e #464c58 #8a93a3
5. 導電合金インゴット (metal ingot, conductive alloy ingot, distinct hue) 配色 #5a3020 #b8703c #f0c090
6. 熱合金インゴット (metal ingot, thermal alloy ingot, distinct hue) 配色 #4a1f14 #c0502a #ffb060
7. 発光結晶 (faceted glowing yellow-green crystal with inner light) 配色 #5a6a2a #d8d59b #ffffd0
8. 強化繊維 (bundle of dark green fibers bound with amber glue) 配色 #1f3a2a #4a7a50 #b9a050
9. 強化ケーブル (coiled black-green cable with copper core visible at the end) 配色 #1a2a22 #3a5a48 #c8734a
10. 海洋樹脂 (translucent amber-blue resin lump with salt specks) 配色 #3a4a5a #b8a060 #dfe8f0
11. 深海複合材 (dark teal woven composite plank with crystal flecks) 配色 #152a2e #2e5a5a #7fd0c8
12. 熱試薬 (glass vial-like pouch of glowing orange-yellow powder) 配色 #5a2a08 #e8901a #fff0a0
```

対応ID順: corrosion_alloy_ingot, high_strength_alloy_ingot, heat_resistant_alloy_ingot, tungsten_alloy_ingot, conductive_alloy_ingot, thermal_alloy_ingot, luminous_crystal, reinforced_fiber, reinforced_cable, marine_resin, abyssal_composite, thermal_reagent

## シートM3: コア・セル・機械部品 (15個 / 5列×3行)

方針: コア/セルは発光、機械部品は工業的な金属質

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. 結晶コア (cube-like blue crystal core with facets and glowing center) 配色 #0d2a4a #2f6fb0 #a8e0ff
2. 熱コア (red-orange crystal core with heat shimmer) 配色 #4a1208 #c04020 #ffc060
3. 深海エネルギーセル (cylindrical battery cell with blue glowing band and metal caps) 配色 #12324a #3f8fbf #b8f0ff
4. 高度発光素子 (brighter version of lumen cell, yellow-white glowing orb in metal frame) 配色 #5a5a2a #f0f090 #ffffff
5. 深海光コア (ornate lens core, white-blue glowing sphere in platinum ring) 配色 #20304a #a8d0f0 #ffffff
6. 高度エネルギーセル (large battery cell, red and blue glowing halves, platinum caps) 配色 #402030 #c04a60 #7fd0ff
7. 深海動力コア (spherical power core in cage, swirling cyan and orange energy) 配色 #0a1a2a #20a0c0 #ffb040
8. 硬化チップ (mechanical hardened tip part, industrial look) 配色 #2a3550 #4a6ab0 #a0c0f0
9. タングステンチップ (mechanical tungsten tip part, industrial look) 配色 #1e2026 #464c58 #9aa3b3
10. ドリルヘッド (mechanical drill head part, industrial look) 配色 #2a2a30 #5a5a68 #b0b0c0
11. 圧力バルブ (mechanical pressure valve part, industrial look) 配色 #2a4a4a #5a8a8a #c0e0d8
12. 耐圧殻 (mechanical pressure shell part, industrial look) 配色 #1a2a3a #3a5a7a #80b0d0
13. 熱部品 (mechanical thermal component part, industrial look) 配色 #4a2a20 #a05a3a #f0b080
14. 導電部品 (mechanical conductive component part, industrial look) 配色 #4a3020 #b0703c #f0c890
15. 機械枠 (mechanical machine frame part, industrial look) 配色 #303038 #6a6a78 #b8b8c8
```

対応ID順: crystal_core, thermal_core, abyssal_energy_cell, advanced_lumen_cell, abyssal_light_core, advanced_energy_cell, abyssal_power_core, hardened_tip, tungsten_tip, drill_head, pressure_valve, pressure_shell, thermal_component, conductive_component, machine_frame

## シートM4: 道具・防具 (13個 / 5列×3行)

方針: 道具は斜め構図、防具は正面図

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. コバルトのツルハシ (pickaxe with blue cobalt head, sharp) 配色 #202028 #2f5fa8 #e8e8f0
2. コバルトのシャベル (shovel with blue cobalt head) 配色 #202028 #2f5fa8 #e8e8f0
3. マンガンの斧 (axe with heavy violet-grey manganese head) 配色 #202028 #8a5f9a #e8e8f0
4. マンガンの剣 (sword with violet-grey blade and composite grip) 配色 #202028 #8a5f9a #e8e8f0
5. モリブデンのツルハシ (pickaxe steel-grey head with orange heat-vent fins) 配色 #202028 #8e97a3 #e8e8f0
6. タングステンのツルハシ (blocky dark tungsten-headed pickaxe, heavy) 配色 #202028 #464c58 #e8e8f0
7. タングステンの斧 (broad dark tungsten axe, heavy) 配色 #202028 #464c58 #e8e8f0
8. 深海ドリル (handheld drill with spiral bit, cyan energy cell) 配色 #202028 #5a5a68 #e8e8f0
9. 深海カッター (slim cutter blade with teal glow line and composite grip) 配色 #202028 #7fd0c8 #e8e8f0
10. 結晶ツルハシ (pickaxe with two blue crystal core prongs and glowing crystal) 配色 #202028 #2f6fb0 #e8e8f0
11. 潜水タンク (back-mounted oxygen tank chestpiece, teal metal with valve) 配色 #182a3a #3a7a8a #d0e8f0
12. 潜水スーツのレギンス (teal sealed diving leggings with cloth panels) 配色 #182a3a #3a7a8a #d0e8f0
13. 耐圧潜水ヘルム (reinforced diving helmet with thick porthole and hadal plating) 配色 #182a3a #5a7aa0 #d0e8f0
```

対応ID順: cobalt_pickaxe, cobalt_shovel, manganese_axe, manganese_sword, molybdenum_pickaxe, tungsten_pickaxe, tungsten_axe, abyssal_drill, abyssal_cutter, crystal_pickaxe, dive_tank, diving_suit_leggings, pressure_diver_helmet
