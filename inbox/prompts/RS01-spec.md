Abyssia (Minecraft Forge 1.20.1 深海Mod、NeoForge 1.21.1 にも同時実装) の設計担当として RS01 の仕様書を短く作ってください。実装は Claude。ウェブ検索・引用なし、普通のテキストで。形式: # RS01 <title> / tier / files / goal / constraints / accept。

## 依頼 (ユーザー原文)
海樹脂の入手範囲を広げたい

## 現状
- 海樹脂 (plant_resin) の入手: アンバーファン amber_fan (資源植物、common、バイオーム abyssal_forest / deep_forest / abyssal_ocean / cave:forest、深さ 500-5000 (海面からの深さ相当)、壊すか熟したら右クリックで 1-2 個、tries 12) と 樹脂根 resin_root (洞窟の天井から垂れる、uncommon、cave:abyssal / cave:forest / cave:cavern、全深度、先端から 1-2 個) の 2 つだけ。
- 使い道: 海洋接着剤 (精錬)、海洋樹脂 marine_resin、保存魚、昆布スープ など 5 レシピ。
- 全バイオーム: deep_sea, abyssal_forest, abyssal_ocean, abyssal_trench, deep_forest, deep_crystal_fields, hadal_zone, thermal_vents, volcanic_deep, 洞窟: cave:abyssal / cavern / crystal / forest / luminous / mineral / thermal。
- 植物はシルクタッチ/はさみ以外で壊すと素材のみ落とす。資源植物は熟すと右クリックで収穫・再生。
- 水耕プランター (4 区画) で育てられるのはキノコ・瓢箪・昆布の 3 種のみ。

## 決めてほしいこと
1. 自然生成の拡大: どのバイオーム/深さにアンバーファン・樹脂根を追加するか、密度 (tries)
2. 別の入手経路を足すか (例: 他の植物の副産物として低確率、古代樹の幹を剥ぐと樹脂、プランターでアンバーファンを育てられるようにする、など) — 足すなら具体的な数値
3. 入手しやすくなりすぎないための上限 (バランス)
4. accept
