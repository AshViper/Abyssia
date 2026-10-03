```markdown
# FD01 補足仕様

tier
standard

## 料理30種

| id | 日本語名 | 英語名 | 満腹度 | 隠し満腹度 | 効果 |
|---|---|---|---:|---:|---|
| fish_mushroom_skewer | 魚とキノコの串焼き | Fish Mushroom Skewer | 6 | 0.8 | 暗視10% |
| gourd_fish_skewer | ゴード魚串 | Gourd Fish Skewer | 7 | 0.9 | 水中呼吸10% |
| kelp_fish_skewer | 海藻魚串 | Kelp Fish Skewer | 5 | 0.7 | - |
| mushroom_stew | 深海キノコシチュー | Abyssal Mushroom Stew | 7 | 0.8 | 暗視15% |
| gourd_soup | ゴードスープ | Gourd Soup | 6 | 0.8 | 水中呼吸15% |
| kelp_soup | 深海海藻スープ | Deep Kelp Soup | 5 | 0.7 | - |
| fish_soup | 深海魚スープ | Abyssal Fish Soup | 8 | 1.0 | 水中呼吸15% |
| mushroom_fish_stew | 魚キノコ煮込み | Fish Mushroom Stew | 9 | 1.0 | 再生5% |
| gourd_fish_stew | ゴード魚煮込み | Gourd Fish Stew | 9 | 1.0 | 水中呼吸20% |
| kelp_fish_stew | 海藻魚煮込み | Kelp Fish Stew | 8 | 0.9 | - |
| mushroom_pie | 深海キノコパイ | Deep Mushroom Pie | 8 | 0.8 | - |
| gourd_pie | ゴードパイ | Gourd Pie | 8 | 0.9 | 暗視10% |
| fish_pie | 深海魚パイ | Abyssal Fish Pie | 10 | 1.0 | 再生5% |
| mushroom_fish_pie | 魚キノコパイ | Fish Mushroom Pie | 10 | 1.1 | 暗視15% |
| gourd_fish_pie | ゴード魚パイ | Gourd Fish Pie | 10 | 1.1 | 水中呼吸15% |
| kelp_fish_pie | 海藻魚パイ | Kelp Fish Pie | 9 | 1.0 | - |
| preserved_fish | 深海魚保存食 | Preserved Abyssal Fish | 7 | 1.0 | - |
| smoked_mushroom | 燻製深海キノコ | Smoked Deep Mushroom | 4 | 0.7 | - |
| smoked_gourd | 燻製ゴード | Smoked Gourd | 5 | 0.8 | 水中呼吸5% |
| grilled_kelp | 焼き深海海藻 | Grilled Deep Kelp | 4 | 0.6 | - |
| mushroom_fish_grill | キノコ魚焼き | Mushroom Fish Grill | 8 | 0.9 | 暗視10% |
| gourd_fish_grill | ゴード魚焼き | Gourd Fish Grill | 9 | 1.0 | 水中呼吸10% |
| kelp_fish_grill | 海藻魚焼き | Kelp Fish Grill | 7 | 0.8 | - |
| jellyfish_skewer | クラゲ触手串 | Jellyfish Tentacle Skewer | 6 | 0.8 | 水中呼吸10% |
| jellyfish_stew | クラゲ触手スープ | Jellyfish Tentacle Soup | 7 | 0.9 | 再生5% |
| abyssal_survival_ration | 深海保存食 | Abyssal Survival Ration | 10 | 1.2 | 暗視10%・水中呼吸10% |
| thermal_ration | 熱水保存食 | Thermal Fiber Ration | 9 | 1.1 | 再生5% |
| mushroom_salad | 深海キノコサラダ | Deep Mushroom Salad | 5 | 0.7 | - |
| gourd_kelp_salad | ゴード海藻サラダ | Gourd Kelp Salad | 5 | 0.7 | 水中呼吸5% |
| abyssal_vegetable_stew | 深海野菜煮込み | Abyssal Vegetable Stew | 7 | 0.9 | 暗視10% |

### 新規食材3種

| id | 名前 | 生食 | 生食時満腹度 |
|---|---|---|---:|
| mushroom_cap | 深海キノコ傘 | 可 | 1 |
| gourd_flesh | ゴード果肉 | 可 | 2 |
| kelp_leaf | 深海海藻葉 | 可 | 1 |

## レシピ30種

| id | 種別 | 材料（shapedは3x3） | 出力数 |
|---|---|---|---:|
| fish_mushroom_skewer | shapeless | abyssia:mushroom_cap x1, abyssia:cooked_abyssal_fish x1, minecraft:stick x1 | 1 |
| gourd_fish_skewer | shapeless | abyssia:gourd_flesh x1, abyssia:cooked_shark_flesh x1, minecraft:stick x1 | 1 |
| kelp_fish_skewer | shapeless | abyssia:kelp_leaf x1, abyssia:cooked_eelpout_flesh x1, minecraft:stick x1 | 1 |
| mushroom_stew | shapeless | abyssia:mushroom_cap x2, abyssia:organic_matter x1, minecraft:bowl x1 | 1 |
| gourd_soup | shapeless | abyssia:gourd_flesh x2, abyssia:deep_fiber x1, minecraft:bowl x1 | 1 |
| kelp_soup | shapeless | abyssia:kelp_leaf x3, abyssia:plant_resin x1, minecraft:bowl x1 | 1 |
| fish_soup | shapeless | abyssia:cooked_abyssal_fish x1, abyssia:deep_fiber x1, abyssia:organic_matter x1, minecraft:bowl x1 | 1 |
| mushroom_fish_stew | shapeless | abyssia:mushroom_cap x1, abyssia:cooked_viper_flesh x1, abyssia:organic_matter x1, minecraft:bowl x1 | 1 |
| gourd_fish_stew | shapeless | abyssia:gourd_flesh x1, abyssia:cooked_shark_flesh x1, abyssia:organic_matter x1, minecraft:bowl x1 | 1 |
| kelp_fish_stew | shapeless | abyssia:kelp_leaf x2, abyssia:cooked_eelpout_flesh x1, abyssia:organic_matter x1, minecraft:bowl x1 | 1 |
| mushroom_pie | shaped | `MMM / MCM / MMM`（M=mushroom_cap, C=crystal_sap） | 1 |
| gourd_pie | shaped | `GGG / GCG / GGG`（G=gourd_flesh, C=crystal_sap） | 1 |
| fish_pie | shaped | `FFF / FCF / FFF`（F=cooked_abyssal_fish, C=crystal_sap） | 1 |
| mushroom_fish_pie | shaped | `MFM / FCF / MFM`（M=mushroom_cap, F=cooked_viper_flesh, C=crystal_sap） | 1 |
| gourd_fish_pie | shaped | `GFG / FCF / GFG`（G=gourd_flesh, F=cooked_shark_flesh, C=crystal_sap） | 1 |
| kelp_fish_pie | shaped | `KFK / FCF / KFK`（K=kelp_leaf, F=cooked_eelpout_flesh, C=crystal_sap） | 1 |
| preserved_fish | smoking | abyssia:abyssal_fish_fillet x1, abyssia:plant_resin x1 | 1 |
| smoked_mushroom | smoking | abyssia:mushroom_cap x2 | 1 |
| smoked_gourd | smoking | abyssia:gourd_flesh x2 | 1 |
| grilled_kelp | campfire | abyssia:kelp_leaf x2 | 1 |
| mushroom_fish_grill | campfire | abyssia:mushroom_cap x1, abyssia:cooked_abyssal_fish x1, abyssia:hard_stalk x1 | 1 |
| gourd_fish_grill | campfire | abyssia:gourd_flesh x1, abyssia:cooked_shark_flesh x1, abyssia:hard_stalk x1 | 1 |
| kelp_fish_grill | campfire | abyssia:kelp_leaf x1, abyssia:cooked_angler_flesh x1, abyssia:hard_stalk x1 | 1 |
| jellyfish_skewer | shapeless | abyssia:jelly_tentacle x1, abyssia:kelp_leaf x1, minecraft:stick x1 | 1 |
| jellyfish_stew | shapeless | abyssia:jelly_tentacle x1, abyssia:deep_fiber x1, abyssia:lumen_gel x1, minecraft:bowl x1 | 1 |
| abyssal_survival_ration | shapeless | abyssia:cooked_shark_flesh x1, abyssia:hard_stalk x1, abyssia:organic_matter x1, abyssia:bio_oil x1 | 1 |
| thermal_ration | shapeless | abyssia:cooked_angler_flesh x1, abyssia:thermal_fiber x1, abyssia:organic_matter x1 | 1 |
| mushroom_salad | shapeless | abyssia:mushroom_cap x2, abyssia:kelp_leaf x1, abyssia:deep_fiber x1 | 1 |
| gourd_kelp_salad | shapeless | abyssia:gourd_flesh x1, abyssia:kelp_leaf x2, abyssia:crystal_sap x1 | 1 |
| abyssal_vegetable_stew | shapeless | abyssia:mushroom_cap x1, abyssia:gourd_flesh x1, abyssia:kelp_leaf x1, abyssia:organic_matter x1, minecraft:bowl x1 | 1 |
```
