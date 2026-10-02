# F01 深海生物のドロップ (魚の肉=食料、クラゲの触手=素材)
tier: standard
files: (3分割・互いに素) A: src/main/java/com/abyssia/registry/ModItems.java / B: tools/gen_deep_assets.py (+生成物 models/item, lang, recipes) / C: tools/fauna/<id>.py の INFO["loot"] + gen_fauna.py 実行物 (loot_tables/entities)
goal: 設計: ChatGPT (2026-10-02 チャット「資料準拠アイテム設計」)。深海魚・ウナギ・サメ・ギンザメは種ごとの肉をドロップし、生肉は かまど/燻製器/焚き火 で焼いて食料にできる。クラゲ5種は触手を落とし、既存素材と組み合わせてクラフトに使える。イカ・タコ・甲殻類・固着生物は今回ドロップなし。
constraints: テクスチャは ChatGPT 生成シートを取込済み (tools/texture_locks/assets/textures/item/<id>.png, ロック済み・再生成で消さない)。ドロップは生物の INFO["loot"] (gen_fauna.py の loot_table) 経由。要件「生物はドロップを持たない」は 2026-10-02 のユーザー依頼で例外として解除 (魚・ウナギ・サメ・クラゲのみ)。
accept: コンパイル通過 / check_recipes・check_textures OK / `/loot spawn` や kill で期待どおりに落ちる / 焼きレシピ3種が全肉に存在 / 触手レシピ4種が成立

## アイテム (16個、ID は ChatGPT 設計どおり)
| # | id | 英名 | 日本語名 | 種類 | nutrition/saturation | 追加効果 | レア度 |
|---|---|---|---|---|---|---|---|
| 1 | abyssal_fish_fillet | Abyssal Fish Fillet | 深海魚の切り身 | 生肉 | 2 / 0.3 | なし | common |
| 2 | cooked_abyssal_fish | Cooked Abyssal Fish | 焼き深海魚 | 焼肉 | 6 / 9.6 | なし | common |
| 3 | viper_flesh | Viperfish Flesh | ホウライエソの肉 | 生肉 | 2 / 0.2 | 30% 暗闇(darkness) 10秒 | uncommon |
| 4 | cooked_viper_flesh | Cooked Viperfish | 焼きホウライエソ | 焼肉 | 6 / 9.6 | なし | uncommon |
| 5 | shark_flesh | Deep Sea Shark Flesh | 深海ザメの肉 | 生肉 | 3 / 0.3 | 30% poison 10秒 | uncommon |
| 6 | cooked_shark_flesh | Cooked Deep Sea Shark | 焼き深海ザメ | 焼肉 | 8 / 12.8 | なし | uncommon |
| 7 | eelpout_flesh | Eelpout Flesh | 深海ウナギの肉 | 生肉 | 2 / 0.3 | 30% water_breathing 15秒 | common |
| 8 | cooked_eelpout_flesh | Cooked Eelpout | 焼き深海ウナギ | 焼肉 | 6 / 9.6 | なし | common |
| 9 | blobfish_flesh | Blobfish Flesh | ブロブフィッシュの肉 | 生肉 | 2 / 0.4 | なし | common |
| 10 | cooked_blobfish | Cooked Blobfish | 焼きブロブフィッシュ | 焼肉 | 5 / 8.0 | 20% regeneration 10秒 | common |
| 11 | angler_flesh | Anglerfish Flesh | アンコウの肉 | 生肉 | 2 / 0.3 | 50% night_vision 15秒 | uncommon |
| 12 | cooked_angler_flesh | Cooked Anglerfish | 焼きアンコウ | 焼肉 | 6 / 9.6 | なし | uncommon |
| 13 | jelly_tentacle | Silky Jelly Tentacle | 絹クラゲの触手 | 素材 | - | - | common |
| 14 | atolla_tentacle | Atolla Tentacle | アトラの触手 | 素材 | - | - | uncommon |
| 15 | phantom_tentacle | Phantom Jelly Tentacle | ファントムクラゲの触手 | 素材 | - | - | uncommon |
| 16 | deepstaria_tentacle | Deepstaria Tentacle | ディープスタリアの触手 | 素材 | - | - | rare |
食料: 食べる速さは標準 (32tick)。肉は meat 扱いにしない(魚だが犬猫の餌にならなくてよい)。生肉効果は ChatGPT の「確率」を effect の probability に。

## ドロップ表 (INFO["loot"]: item, count(min,max), looting, chance)
- lanternfish, hatchetfish: abyssal_fish_fillet 1-1 chance 0.8 looting 1
- barreleye, stoplight_loosejaw, tripod_fish, mariana_snailfish: abyssal_fish_fillet 1-2 chance 0.7 looting 1  (tripod_fish と mariana_snailfish は設計表の漏れを Claude が補完)
- anglerfish: angler_flesh 1-2 chance 0.7 looting 1
- viperfish, black_dragonfish, fangtooth: viper_flesh 1-2 chance 0.7 looting 1
- abyssal_grenadier, black_swallower: abyssal_fish_fillet 2-3 chance 0.7 looting 1
- blobfish: blobfish_flesh 1-2 chance 0.75 looting 1
- snipe_eel: eelpout_flesh 1-2 chance 0.7 looting 1
- gulper_eel, oarfish, hagfish: eelpout_flesh 1-3 chance 0.7 looting 1
- vent_eelpout: eelpout_flesh 2-3 chance 0.75 looting 1
- goblin_shark, frilled_shark, cookiecutter_shark, chimaera: shark_flesh 2-3 chance 0.7 (chimaera 0.65) looting 1
- pacific_sleeper_shark, bluntnose_sixgill_shark: shark_flesh 3-4 chance 0.7 looting 1
- silky_medusa, helmet_jelly: jelly_tentacle 1-2 chance 0.8 (helmet 0.75) looting 1
- atolla_jelly: atolla_tentacle 1-2 chance 0.75 looting 1
- giant_phantom_jelly: phantom_tentacle 1-3 chance 0.7 looting 1
- deepstaria: deepstaria_tentacle 1-2 chance 0.6 looting 1
- ドロップなし: イカ・タコ、甲殻類、固着/底生 (tubeworm 系, sea_pig, yumenamako, brittle_star, sea_lily, venus_flower_basket, scaly_foot_snail 等)

## レシピ
調理 (生肉 → 焼肉、全6組): smelting 200tick / smoking 100tick / campfire_cooking 600tick、経験値 0.35。
触手クラフト (shapeless):
- jelly_tentacle ×3 + deep_fiber ×2 → marine_adhesive ×1
- atolla_tentacle ×2 + lumen_gel ×1 → reinforced_fiber ×1
- phantom_tentacle ×3 + reinforced_fiber ×2 → reinforced_cable ×1
- deepstaria_tentacle ×2 + abyssal_composite ×1 → hadal_plating ×1  (hadal_plating は very_rare 素材: バランス注意、結果1個のまま)
既存の同名出力レシピとは別IDにする。
