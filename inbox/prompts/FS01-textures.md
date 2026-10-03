# FS01 食用魚8種 アイテムアイコン (ChatGPT ImageGen 用)

1枚のシート (4列×4行 = 16個) で生成する。保存先 `inbox/textures/sheets/FS01.png`、取込は `python tools/agentflow/sheets.py import auto --preset FS01`。
並び = 左→右・上→下。各行は「生・焼き」の組を2つ。焼きは生と同じシルエットで、きつね色〜こげ茶に焼けた色。
見本スタイル: 既存 abyssal_fish_fillet / cooked_abyssal_fish / shark_flesh / cooked_shark_flesh (斜めに置いた切り身、1px の暗い縁)。

## 共通プロンプト

```
Minecraft 1.20.1 item icon sheet, hand-drawn 16x16 pixel art in the style of vanilla Minecraft food items
(raw cod, cooked cod, raw salmon, cooked salmon, cooked porkchop). Each icon is ONE food item, centered in its own
square cell, drawn on a flat solid magenta (#FF00FF) background that contains no magenta in the icon itself.
Grid of 4 columns x 4 rows of equal square cells with wide magenta gutters between icons, no cell borders,
no labels, no text, no numbers, no watermark.
Bold readable silhouette with a 1px dark outline, limited palette of 4-6 colors per icon, hard pixel edges,
no anti-aliasing, no gradients, no blur, no glow, no drop shadow, no perspective. Items lie diagonally
(lower-left to upper-right) like vanilla fish. Every icon is drawn at exactly 16x16 logical pixels (square pixels,
integer scale). Each raw icon is a fish fillet / fish piece showing the species skin colour on one edge and the
flesh on the rest; the cooked icon right after it has EXACTLY the same silhouette, but golden-brown to dark-brown
roasted, with a few darker grill marks.
```

## シート FS01

```
（共通プロンプト）
Contents in reading order (left to right, top to bottom):
Row 1:
1. raw orange roughy: thick round fillet, bright orange-red skin edge, pale pink-white flesh
2. cooked orange roughy: same shape, golden brown with orange-red tinted edge
3. raw sablefish: long slim fillet, black / dark grey skin edge, creamy white flesh
4. cooked sablefish: same shape, glossy amber-brown with dark edge
Row 2:
5. raw patagonian toothfish: large thick steak, dark grey-brown skin edge, snow-white flesh
6. cooked patagonian toothfish: same shape, golden brown with brown edge
7. raw black scabbardfish: very long thin fillet, metallic black-silver skin edge, pale pinkish-white flesh
8. cooked black scabbardfish: same shape, browned with dark edge
Row 3:
9. raw greenland halibut: flat wide oval fillet, dark brown-grey skin edge, white flesh
10. cooked greenland halibut: same shape, light golden brown
11. raw alfonsino: small round chunky fillet, vivid red skin edge, pink flesh
12. cooked alfonsino: same shape, golden brown with red tinted edge
Row 4:
13. raw blue ling: long eel-like fillet, blue-grey / silver-grey skin edge, pale grey-white flesh
14. cooked blue ling: same shape, golden brown with grey-blue edge
15. raw deepwater redfish: small deep fillet, red to orange-red skin edge with a spiny fin stub, pink flesh
16. cooked deepwater redfish: same shape, golden brown with red tinted edge
```

対応ID順: raw_orange_roughy, cooked_orange_roughy, raw_sablefish, cooked_sablefish, raw_patagonian_toothfish, cooked_patagonian_toothfish, raw_black_scabbardfish, cooked_black_scabbardfish, raw_greenland_halibut, cooked_greenland_halibut, raw_alfonsino, cooked_alfonsino, raw_blue_ling, cooked_blue_ling, raw_deepwater_redfish, cooked_deepwater_redfish
