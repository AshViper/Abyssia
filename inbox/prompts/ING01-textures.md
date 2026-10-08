# ING01 インゴット全作り直し + 新規7金属の原石 — ChatGPT ImageGen 用

全インゴット (金属16種 + 合金7種) と、ECO02 の新規 7 金属 (チタン / 鉛 / 亜鉛 / イリジウム / ウラン / ネオジム / トリウム) の原石 (raw_) の 16x16 アイコン。
保存先 `inbox/textures/sheets/<シート名>.png`。取込: `python tools/agentflow/sheets.py import auto --preset <シート名>` (先に `--dry-run`)。
方針: インゴットはすべて同じ形 (斜めに置いた延べ棒、1px の暗い輪郭) で、色と表面の質感だけを変える。原石は不揃いな鉱石の塊で、金属の色の粒 / 筋が入る。

## 共通プロンプト（各シートの先頭に付ける）

```
Minecraft 1.20.1 item icon sheet, hand-drawn 16x16 pixel art in the style of vanilla Minecraft items
(iron ingot, gold ingot, raw copper, amethyst shard). Each icon is ONE object, centered in its own square cell,
drawn on a flat solid magenta (#FF00FF) background that contains no magenta in the icon itself.
Grid of equal square cells with wide magenta gutters between icons, no cell borders, no labels, no text, no watermark.
Bold readable silhouette with a 1px dark outline, limited palette of 4-6 colors per icon, hard pixel edges,
no anti-aliasing, no gradients, no blur, no glow, no drop shadow, no perspective. Slightly asymmetric hand-made look;
avoid over-detailed noise. Each icon must be clearly different from the others.
Every icon is drawn at exactly 16x16 logical pixels (square pixels, integer scale).
```

## シートING1: 金属インゴット (16個 / 4列×4行)

方針: 全部同じ形の斜め延べ棒 (vanilla のインゴットと同じ向き)。色と艶・刃面の質感で区別する

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）。すべて同じ形の斜めに置いた金属インゴット:
1. コバルトインゴット。深い青 #10204a #1f4a9a #3f7ad0 #a9c8f0
2. マンガンインゴット。赤紫がかった灰 #2b1f2a #5a3f55 #8a6a86 #c4a8c0
3. ニッケルインゴット。明るい銀白 #3a3f40 #7d8a88 #b9c6c2 #eef4f0
4. チタンインゴット。銀青色の鋼、細かい筋 #1c2026 #5e6b7a #8a9aab #b8c6d4
5. 鉛インゴット。鈍い青灰、重くて艶なし #17191f #4b505e #6d7384 #9399aa
6. モリブデンインゴット。鉛のような青鋼 #232b33 #4c5c6b #7f93a4 #c4d2dc
7. バナジウムインゴット。青緑がかった鋼 #1c2a24 #3c5a4b #6b9a80 #b5dcc5
8. 亜鉛インゴット。青みがかった白、うっすら艶 #202a2e #6a838c #98b0b8 #c8dce0
9. タングステンインゴット。ほぼ黒の重い灰 #14161a #2d3238 #4b525b #7d868f
10. 白金インゴット。輝く銀白 #4a4d55 #9a9fab #d6dae2 #ffffff
11. テルルインゴット。紫がかった銀灰 #2a2433 #5a4f6b #8f84a3 #cbc3d8
12. イリジウムインゴット。冷たい銀白、面取りされた硬質な光沢 #2c2e33 #8c9099 #c0c4cc #eceef2
13. ウランインゴット。黄緑がかった鈍い金属 #1a2410 #587a2a #86b03e #bce068
14. ネオジムインゴット。紫がかった銀、磁石のような艶 #241a30 #6c5489 #9a82b8 #c8b6dc
15. トリウムインゴット。灰緑の重い金属 #1c2420 #5a6e60 #86a08c #b4cdb8
16. イットリウムインゴット。淡い黄白の灰 #3a3428 #7a6f55 #b5a680 #ebdfb8
```

対応ID順: cobalt_ingot, manganese_ingot, nickel_ingot, titanium_ingot, lead_ingot, molybdenum_ingot, vanadium_ingot, zinc_ingot, tungsten_ingot, platinum_ingot, tellurium_ingot, iridium_ingot, uranium_ingot, neodymium_ingot, thorium_ingot, yttrium_ingot

## シートING2: 合金インゴット (7個 / 4列×2行)

方針: 金属インゴットと同じ斜め延べ棒。合金は一回り明るく艶のある仕上げにする

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）。すべて同じ形の斜めに置いた合金インゴット (金属インゴットよりやや艶が強い):
1. 深海合金インゴット。ティール #26343a #45656a #70a4a1 #b5d4c8
2. 耐食合金インゴット。青緑の灰 #36585c #5f8f8a #a9d1c7
3. 高強度合金インゴット。紫がかった鋼 #3a2f45 #6d5a80 #b5a3c8
4. 耐熱合金インゴット。赤褐色の鋼 #4a2f24 #8a5a44 #d69a7a
5. タングステン合金インゴット。ほぼ黒の青灰 #25282e #464c58 #8a93a3
6. 導電合金インゴット。銅のような橙 #5a3020 #b8703c #f0c090
7. 熱合金インゴット。赤熱した橙 #4a1f14 #c0502a #ffb060
```

対応ID順: abyssal_alloy_ingot, corrosion_alloy_ingot, high_strength_alloy_ingot, heat_resistant_alloy_ingot, tungsten_alloy_ingot, conductive_alloy_ingot, thermal_alloy_ingot

## シートRAW2: 新規7金属の原石 (7個 / 4列×2行)

方針: 不揃いな鉱石の塊 (raw copper / raw iron のような形)。暗い岩に金属の色の粒・筋が入る。金属ごとに塊の形も変える

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）。すべて不揃いな原石の塊 (暗い岩の地に金属の色の粒や筋):
1. チタンの原石。銀青色の細い筋 #1c2026 #5e6b7a #8a9aab #b8c6d4
2. 鉛の原石。鈍い青灰の丸みのある塊 #17191f #4b505e #6d7384 #9399aa
3. 亜鉛の原石。青みがかった白い結晶が混じる #202a2e #6a838c #98b0b8 #c8dce0
4. イリジウムの原石。銀白の硬く小さな粒が散る #2c2e33 #8c9099 #c0c4cc #eceef2
5. ウランの原石。黄緑の淡い粒が散る #1a2410 #587a2a #86b03e #bce068
6. ネオジムの原石。紫がかった銀の斑 #241a30 #6c5489 #9a82b8 #c8b6dc
7. トリウムの原石。灰緑の鈍い塊 #1c2420 #5a6e60 #86a08c #b4cdb8
```

対応ID順: raw_titanium, raw_lead, raw_zinc, raw_iridium, raw_uranium, raw_neodymium, raw_thorium
