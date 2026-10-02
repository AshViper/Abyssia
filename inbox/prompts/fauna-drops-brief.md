# 依頼資料: 深海生物のドロップアイテム (Abyssia)

Minecraft Forge 1.20.1 の深海Mod「Abyssia」。実装は別のAI(Claude Code)が行う。あなた(ChatGPT)は ①仕様書 ②アイテム設計 ③テクスチャ画像 を作る。コードは書かない。

## ユーザーの依頼
今いる深海魚のドロップアイテムを実装する。
- 魚の肉系は、焼いたり(かまど/燻製器/焚き火)して食料にできるようにする
- クラゲは触手を落とすようにして、素材に使えるようにする

## 現状
- 全生物のドロップは今まで空 (ルートテーブルなし)。今回の依頼でドロップを追加する
- 生物ごとに `tools/fauna/<id>.py` の INFO["loot"] に書くとルートテーブルが生成される。1ドロップ = {item, count(最小,最大), looting(ルーティング1レベルあたり追加数), chance(確率)}。item は abyssia のアイテムID、または minecraft:xxx
- 食料: FoodProperties(nutrition, saturation, 必要なら効果と確率)。バニラ比較: 生タラ 2/0.1、焼きタラ 5/6.0、生サケ 2/0.1、焼きサケ 6/9.6、河豚は毒
- 調理レシピ: smelting(かまど 200tick), smoking(燻製器 100tick), campfire_cooking(焚き火 600tick), 経験値 0.35 が標準
- アイテムのテクスチャは 16x16 のピクセルアート。深海Modの暗く冷たい色調、ただし食料は食べ物らしい色にする

## 生物リスト (id / 英名 / 分類)
- 深海魚: anglerfish Anglerfish, viperfish Viperfish, barreleye Barreleye, stoplight_loosejaw Stoplight loosejaw, black_dragonfish Black dragonfish, fangtooth Fangtooth, hatchetfish Hatchetfish, lanternfish Lanternfish, blobfish Blobfish, tripod_fish Tripod Fish, abyssal_grenadier Abyssal Grenadier, black_swallower Black Swallower, mariana_snailfish Mariana Snailfish, vent_eelpout Vent Eelpout
- ウナギ型: gulper_eel Gulper Eel, snipe_eel Slender Snipe Eel, oarfish Oarfish, hagfish Hagfish
- サメ・ギンザメ: goblin_shark Goblin Shark, frilled_shark Frilled Shark, pacific_sleeper_shark Pacific Sleeper Shark, bluntnose_sixgill_shark Bluntnose Sixgill Shark, cookiecutter_shark Cookiecutter Shark, chimaera Chimaera
- クラゲ: silky_medusa Silky Medusa, atolla_jelly Atolla Jellyfish, helmet_jelly Helmet Jellyfish, giant_phantom_jelly Giant Phantom Jelly, deepstaria Deepstaria
- 対象外 (今回はドロップなし): イカ・タコ(giant_squid, vampire_squid, firefly_squid, bigfin_squid, dumbo_octopus)、甲殻類(deep_sea_shrimp, ohara_shrimp, yunohana_crab, goemon_squat_lobster, japanese_spider_crab, blind_lobster, supergiant_amphipod, giant_isopod, giant_sea_spider)、固着/底生(tubeworm, satsuma_tubeworm, sea_pig, yumenamako, brittle_star, sea_lily, venus_flower_basket, scaly_foot_snail)
- 大きさの目安: 小型(ランタンフィッシュ等 約0.2m)〜大型(ニシオンデンザメ 約4m)。大きい個体ほど多く落とす

## 既存の素材 (クラフトで触手などを使うときの参考。新素材は増やしすぎない)
植物素材: deep_fiber(深海繊維), plant_resin(海樹脂), hard_stalk(硬質茎), organic_matter(有機物), bio_oil(生体油), lumen_gel(発光ゲル), deep_pigment(深海色素), thermal_fiber(耐熱繊維), crystal_sap(結晶樹液), hadal_husk(超深海殻)
加工素材: sea_cloth(海布), marine_adhesive(海洋接着剤), reinforced_fiber(強化繊維), reinforced_cable(強化ケーブル), marine_resin, abyssal_composite, luminous_crystal(発光結晶), pressure_valve, hadal_plating
金属: abyssal_alloy_ingot(深海合金) ほか。バニラ素材も使える

## 作ってほしい成果物
1. **仕様書**: 次の形式 (inbox テンプレ)。「# <id> <title>」「tier: light|standard|heavy」「files: 編集してよいファイルやディレクトリ」「goal: 1〜3行」「constraints: 既存設計との整合」「accept: 完了条件」。分担できるなら複数の仕様書に分け、files を重複させない。tier の基準: light=JSON/lang/定数追加や取り込みなどの機械的・局所的な変更、standard=通常の機能追加、heavy=アーキテクチャ変更・性能影響
2. **アイテム設計表**: 新アイテム(合計 16 個以内)の id(snake_case), 英名, 日本語名, 種類(生肉/焼いた肉/素材), 食料値(nutrition, saturation, 食べる速さ), 追加効果(あれば), レア度, 一言の説明
3. **ドロップ表**: 生物(または分類)ごとに、何を何個、確率、ルーティングの扱い。大型ほど多く。毒のある生物は生で食べるとデメリットがあってもよい
4. **レシピ表**: 調理(smelting/smoking/campfire_cooking の対応と時間)、触手の使い道(クラフトで何になるか: 既存素材+バニラと組み合わせ。ゲームを壊さない範囲で、2〜4レシピ程度)
5. **テクスチャ画像**: 設計表のアイテム全部のアイコンを、1枚のシート画像で生成する。下の「画像の条件」に従う
6. 最後に、仕様の要点を短く(5行以内)

## 画像の条件
- Minecraft 1.20.1 の item アイコンのシート。手描き 16x16 ピクセルアート、バニラのアイテム(生タラ、焼きサケ、スライムボール)の雰囲気
- 背景は無地のマゼンタ #FF00FF。アイコンにマゼンタは使わない。1つのアイコンが1マス、マスの間は広いマゼンタの余白。枠線・ラベル・文字・透かしなし
- 左→右、上→下の順で、設計表の順番どおりに並べる (5列×4行など、個数に合わせる)
- 太い読みやすいシルエット、1px の暗い輪郭、アイコンごとに 4〜6 色、はっきりしたピクセル、ぼかし・グラデーション・影なし
- 生肉は赤〜桃色の切り身(魚種で色味を変える)、焼いた肉は茶〜こんがり色。触手は種ごとに色を変える(発光する種はうっすら光る感じ)
