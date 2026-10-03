# FD01 深海の料理30種 + 食材3種

依頼 20261003-152618。設計は ChatGPT (原文: `FD01-TR01-CB01-chatgpt-raw.md` の FD01 と `FD01-chatgpt-r2.md`)。下は Claude の実装用まとめで、ChatGPT 案からの変更は「Claude 補足」に書いた。
tier: standard
files: src/main/java/com/abyssia/registry/ModItems.java (食料登録部分のみ), tools/gen_deep_assets.py (ITEMS / ITEM_NAMES / 料理レシピ生成の部分のみ), tools/plant_defs.py (食材ドロップのみ), テクスチャは FOOD1-3 シート取込 (main が実施)
goal: 植物から食材3種 (mushroom_cap / gourd_flesh / kelp_leaf) が取れ、それと魚肉・既存素材で料理30種を作れる。
constraints: 生成物 (assets/.../models, lang, data/.../recipes, loot_tables) を手で書かない。ジェネレーターに定義を足す。既存アイテム・素材の意味は変えない。効果は短時間・低確率。ボウル料理は食後に minecraft:bowl を返す。
accept: gradle build 成功、check_recipes 通過、30料理+3食材が登録され lang/モデル/レシピがある、食材が指定植物から出る。

## 食材 (生食可)
| id | 日本語 | English | 満腹 | 隠し | 入手 |
|---|---|---|---:|---:|---|
| mushroom_cap | 深海キノコ傘 | Deep Mushroom Cap | 1 | 0.3 | abyssal_mushroom の収穫/破壊で 35% |
| gourd_flesh | ゴード果肉 | Gourd Flesh | 2 | 0.4 | pressure_gourd の収穫で 40% |
| kelp_leaf | 深海海藻葉 | Deep Kelp Leaf | 1 | 0.2 | 海藻/草系 (deep_fiber を落とす植物) の破壊・収穫で 30% |

## 料理 (満腹 / 隠し満腹度 は ChatGPT 表の値。隠し満腹度は ChatGPT の「saturation modifier」= FoodProperties.saturationMod としてそのまま使う)
| id | 日本語 | English | 満腹 | 隠し | 効果 (10/15s 程度、確率) | レシピ |
|---|---|---|---:|---:|---|---|
| fish_mushroom_skewer | 魚とキノコの串焼き | Fish Mushroom Skewer | 6 | 0.8 | 暗視 10% | 不定形 mushroom_cap, cooked_abyssal_fish, stick |
| gourd_fish_skewer | ゴード魚串 | Gourd Fish Skewer | 7 | 0.9 | 水中呼吸 10% | 不定形 gourd_flesh, cooked_shark_flesh, stick |
| kelp_fish_skewer | 海藻魚串 | Kelp Fish Skewer | 5 | 0.7 | - | 不定形 kelp_leaf, cooked_eelpout_flesh, stick |
| mushroom_stew | 深海キノコシチュー | Abyssal Mushroom Stew | 7 | 0.8 | 暗視 15% | 不定形 mushroom_cap x2, organic_matter, bowl |
| gourd_soup | ゴードスープ | Gourd Soup | 6 | 0.8 | 水中呼吸 15% | 不定形 gourd_flesh x2, deep_fiber, bowl |
| kelp_soup | 深海海藻スープ | Deep Kelp Soup | 5 | 0.7 | - | 不定形 kelp_leaf x3, plant_resin, bowl |
| fish_soup | 深海魚スープ | Abyssal Fish Soup | 8 | 1.0 | 水中呼吸 15% | 不定形 cooked_abyssal_fish, deep_fiber, organic_matter, bowl |
| mushroom_fish_stew | 魚キノコ煮込み | Fish Mushroom Stew | 9 | 1.0 | 再生 5% | 不定形 mushroom_cap, cooked_viper_flesh, organic_matter, bowl |
| gourd_fish_stew | ゴード魚煮込み | Gourd Fish Stew | 9 | 1.0 | 水中呼吸 20% | 不定形 gourd_flesh, cooked_shark_flesh, organic_matter, bowl |
| kelp_fish_stew | 海藻魚煮込み | Kelp Fish Stew | 8 | 0.9 | - | 不定形 kelp_leaf x2, cooked_eelpout_flesh, organic_matter, bowl |
| mushroom_pie | 深海キノコパイ | Deep Mushroom Pie | 8 | 0.8 | - | 定形 MMM/MCM/MMM (M mushroom_cap, C crystal_sap) |
| gourd_pie | ゴードパイ | Gourd Pie | 8 | 0.9 | 暗視 10% | 定形 GGG/GCG/GGG (G gourd_flesh) |
| fish_pie | 深海魚パイ | Abyssal Fish Pie | 10 | 1.0 | 再生 5% | 定形 FFF/FCF/FFF (F cooked_abyssal_fish) |
| mushroom_fish_pie | 魚キノコパイ | Fish Mushroom Pie | 10 | 1.1 | 暗視 15% | 定形 MFM/FCF/MFM (F cooked_viper_flesh) |
| gourd_fish_pie | ゴード魚パイ | Gourd Fish Pie | 10 | 1.1 | 水中呼吸 15% | 定形 GFG/FCF/GFG (F cooked_shark_flesh) |
| kelp_fish_pie | 海藻魚パイ | Kelp Fish Pie | 9 | 1.0 | - | 定形 KFK/FCF/KFK (K kelp_leaf, F cooked_eelpout_flesh) |
| preserved_fish | 深海魚保存食 | Preserved Abyssal Fish | 7 | 1.0 | - | 不定形 abyssal_fish_fillet, plant_resin, organic_matter (*1) |
| smoked_mushroom | 燻製深海キノコ | Smoked Deep Mushroom | 4 | 0.7 | - | 燻製器 mushroom_cap (*2) |
| smoked_gourd | 燻製ゴード | Smoked Gourd | 5 | 0.8 | 水中呼吸 5% | 燻製器 gourd_flesh (*2) |
| grilled_kelp | 焼き深海海藻 | Grilled Deep Kelp | 4 | 0.6 | - | 焚き火/かまど kelp_leaf (*2) |
| mushroom_fish_grill | キノコ魚焼き | Mushroom Fish Grill | 8 | 0.9 | 暗視 10% | 不定形 mushroom_cap, cooked_abyssal_fish, hard_stalk (*1) |
| gourd_fish_grill | ゴード魚焼き | Gourd Fish Grill | 9 | 1.0 | 水中呼吸 10% | 不定形 gourd_flesh, cooked_shark_flesh, hard_stalk (*1) |
| kelp_fish_grill | 海藻魚焼き | Kelp Fish Grill | 7 | 0.8 | - | 不定形 kelp_leaf, cooked_angler_flesh, hard_stalk (*1) |
| jellyfish_skewer | クラゲ触手串 | Jellyfish Tentacle Skewer | 6 | 0.8 | 水中呼吸 10% | 不定形 jelly_tentacle, kelp_leaf, stick |
| jellyfish_stew | クラゲ触手スープ | Jellyfish Tentacle Soup | 7 | 0.9 | 再生 5% | 不定形 jelly_tentacle, deep_fiber, lumen_gel, bowl |
| abyssal_survival_ration | 深海保存食 | Abyssal Survival Ration | 10 | 1.2 | 暗視 10%, 水中呼吸 10% | 不定形 cooked_shark_flesh, hard_stalk, organic_matter, bio_oil |
| thermal_ration | 熱水保存食 | Thermal Fiber Ration | 9 | 1.1 | 再生 5% | 不定形 cooked_angler_flesh, thermal_fiber, organic_matter |
| mushroom_salad | 深海キノコサラダ | Deep Mushroom Salad | 5 | 0.7 | - | 不定形 mushroom_cap x2, kelp_leaf, deep_fiber |
| gourd_kelp_salad | ゴード海藻サラダ | Gourd Kelp Salad | 5 | 0.7 | 水中呼吸 5% | 不定形 gourd_flesh, kelp_leaf x2, crystal_sap |
| abyssal_vegetable_stew | 深海野菜煮込み | Abyssal Vegetable Stew | 7 | 0.9 | 暗視 10% | 不定形 mushroom_cap, gourd_flesh, kelp_leaf, organic_matter, bowl |

ボウルを使う料理 (stew/soup/salad 系、*_stew・*_soup・*_salad) は食後に bowl を返し、スタック 1 (バニラのシチュー同様)。それ以外は 64。

## Claude 補足 (ChatGPT 案からの変更)
- (*1) ChatGPT 案は燻製器/焚き火で複数材料だったが、バニラの調理レシピは材料1つしか取れない。preserved_fish と *_fish_grill は不定形クラフトにした (材料は ChatGPT 案のまま、preserved_fish は organic_matter を塩代わりに足した)。生の abyssal_fish_fillet は既に焼き魚の材料なので調理レシピにすると衝突する。
- (*2) smoked_mushroom / smoked_gourd / grilled_kelp は材料1個 → 1個 (ChatGPT 案の x2 は調理レシピでは不可)。smoked 2種は燻製器 + 焚き火、grilled_kelp は かまど + 燻製器 + 焚き火 (既存の魚と同じ 200/100/600 tick)。
- ChatGPT の files 欄 (ModFoods.java, ModRecipes.java) は存在しないので、既存の ModItems.food() ヘルパーと gen_deep_assets.py に合わせた。
- 食材3種は ChatGPT r2 で「生食可」。満腹は r2 の値 (1/2/1)、隠し満腹度は r1 表の値 (0.3/0.4/0.2)。
