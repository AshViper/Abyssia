# EN01 深海エンチャント・バフ/デバフ
依頼: inbox/requests/20261003-165439.json。ChatGPT 原本: `EN01-chatgpt-raw.md` (reply 2/3 が 3 章以降の確定版。reply 1 の `deep_sight_coral` / `propeller` 素材は撤回済み)。
tier: standard
files: src/main/java/com/abyssia/effect/ (新規)、src/main/java/com/abyssia/enchantment/ (新規)、registry/ModEnchantments.java・ModMobEffects.java・ModPotions.java (新規)、Abyssia.java (登録のみ)、霧/暗さのクライアント処理 (既存ファイルへ係数1か所)、料理の FoodProperties (効果追加のみ)、fauna 攻撃処理 (デバフ付与のみ)、lang 生成元、textures/mob_effect/
goal: Abyssia 専用のエンチャント 3 種・MobEffect 5 種・ポーション 2 系統を追加する。バニラの水中系 (水中呼吸・水中採掘・水中歩行・イルカの好意・コンジットパワー・暗視) と重複させず「深海探索・視界・採取」に特化。

## 1. エンチャント
| ID | 名前 ja / en | 対象 | 最大 | 効果 | レア | 排他 | 入手 |
|---|---|---|---|---|---|---|---|
| deep_swimmer | 深海遊泳 / Deep Swimmer | ブーツ | III | 水中の水平移動 +10%/Lv。陸上 0 | RARE | Depth Strider, Frost Walker | エンチャント台・取引 |
| thermal_catch | 熱処理 / Thermal Catch | 剣・斧 | I | Abyssia の魚類を倒すと生肉→対応する焼き肉。焼き肉が無い魚は変換しない。Looting は維持。Fire Aspect とは無関係 | RARE | なし | 宝物のみ |
| abyssal_focus | 深海集中 / Abyssal Focus | ヘルメット | II | 深海層 (Y -368..-64) にいる間 Deep Sight I/II を常時付与。外せば消える | VERY_RARE | Aqua Affinity | 宝物のみ |
- 魚類判定は Abyssia の魚類タグ (無ければ作る)。バニラ・他 Mod の魚には作用しない。

## 2. MobEffect
| ID | 名前 | 色 | 効果 | 付与源 |
|---|---|---|---|---|
| deep_sight (バフ) | 深海視界 / Deep Sight | #39B8D8 | 深海霧と暗さを軽減。I: 霧距離 +64 相当、暗さ軽減。II: +144 相当、さらに軽減。暗視ではない (地上・洞窟は変えない、Night Vision を付与しない) | abyssal_focus、料理、ポーション |
| abyssal_current (バフ) | 深海潮流 / Abyssal Current | #2868C7 | 水中の水平移動 +15% / II +30%。陸上なし | 料理、ポーション |
| pressure_fatigue (デバフ) | 深海疲労 / Pressure Fatigue | #514A72 | 水中移動 -10% / II -20%。ダメージなし | 大型魚・サメ等の近接攻撃で 5 秒 Lv1 |
| cold_shock (デバフ) | 冷却ショック / Cold Shock | #8AB8D8 | 移動・採掘 -15% / II -30%、陸上でも持続。ダメージなし | クラゲ系・冷たい Mob の攻撃で 4 秒 Lv1 (再被弾は最大 4 秒に更新) |
| murk (デバフ) | 深海濁り / Murk | #26333D | 視界を暗くし霧を強める。Deep Sight I で 50% 軽減、II で無効 | 視界妨害クラゲ等の攻撃で 6 秒 Lv1 |
- 霧の軽減は既存の深海霧の係数に組み込む (新しい霧システムを作らない、シェーダー経路も同じ値)。
- 同一攻撃で多重付与しない。amplifier 0 = Lv1。

## 3. ポーション (Forge: BrewingRecipeRegistry / NeoForge: RegisterBrewingRecipesEvent)
| ポーション | 基本 | 延長 (Redstone) | 強化 (Glowstone) | 素材 (Awkward +) |
|---|---|---|---|---|
| deep_sight | I 180s | I 480s | II 90s | jelly_tentacle |
| abyssal_current | I 180s | I 480s | II 90s | 生体油昆布 (成熟した通常アイテム 1 種) |
- Splash / Lingering はバニラ標準 (Gunpowder / Dragon's Breath)。新しい醸造素材は作らない。

## 4. 料理バフ (既存料理に対応付け。新料理は作らない、満腹度・食事時間は変えない)
| 料理 (実在 ID に対応付け) | 効果 | Lv | 秒 |
|---|---|---|---|
| 深海魚の焼き身 | abyssal_current | I | 120 |
| 深海魚の上質な焼き料理 | abyssal_current | II | 60 |
| 発光クラゲ料理 | deep_sight | I | 120 |
| 生体油昆布料理 | deep_sight | I | 90 |
| 深海魚と昆布の料理 | deep_sight | I | 150 |
| 深海魚の濃厚スープ系 | abyssal_current | I | 180 |
- 同効果の重ね掛け: 高いレベル優先、同レベルなら長いほう。

## 5. アイコン (18x18、透明背景、文字なし、ChatGPT に依頼)
deep_sight (発光する眼+波紋)、abyssal_current (前へ流れる渦)、pressure_fatigue (押し潰す圧力+沈む波)、cold_shock (氷結した水流)、murk (濁り+ぼやけた視界)。エンチャントのアイコンは作らない。

## constraints
- 全 ID `abyssia:<id>`。Forge と NeoForge で ID・数値・入手方法を一致させる。
- サーバーで効果・エンチャント・ドロップ変換、クライアントは表示と霧だけ。
- 環境ダメージ (水圧・寒さ) は追加しない。デバフは直接ダメージを与えない。
- バニラの水中系エフェクト/エンチャントの挙動を変えない。

## accept
- `/enchant` で deep_swimmer I/II/III → 水中の水平速度 +10/20/30%、陸上は変化なし。
- thermal_catch 付き剣/斧で Abyssia の魚を倒す → 焼き肉。無しなら生肉。バニラの鱈には作用しない。Looting 維持。
- abyssal_focus I/II ヘルメット → 深海層で Deep Sight I/II、外すと消える。
- Deep Sight 有無で深海の霧・暗さの差が明確 (シェーダー時も)。地上は変化なし。Murk は Deep Sight I で半減、II で無効。
- abyssal_current I/II で水中 +15/30%、陸上なし。
- 対象 Mob の攻撃で各デバフ、HP ダメージは増えない。
- 2 系統のポーションが醸造・延長・強化・Splash/Lingering 化できる。
- 指定料理だけが表どおりの効果。
- NeoForge 1.21.1 でも同じ。

## Claude の対応付け (実 ID、調査結果 2026-10-03)
- 魚類タグ `abyssia:fish` (新規 entity tag): abyssal_grenadier, barreleye, black_swallower, hatchetfish, lanternfish, mariana_snailfish, stoplight_loosejaw, tripod_fish, black_dragonfish, fangtooth, viperfish, blobfish, anglerfish, vent_eelpout。ウナギ・サメ・クラゲは対象外。
- thermal_catch 変換: abyssal_fish_fillet→cooked_abyssal_fish, viper_flesh→cooked_viper_flesh, blobfish_flesh→cooked_blobfish, angler_flesh→cooked_angler_flesh, eelpout_flesh→cooked_eelpout_flesh (shark_flesh→cooked_shark_flesh も表に入れるが、サメはタグ外なので実質発生しない)。LivingDropsEvent で該当 ItemEntity の stack を同数の焼き版に差し替える。
- 料理: cooked_abyssal_fish=abyssal_current I 120s / mushroom_fish_grill=abyssal_current II 60s / jellyfish_skewer・jellyfish_stew=deep_sight I 120s / abyssal_survival_ration (bio_oil 入り、油昆布料理が無いため代替)=deep_sight I 90s / kelp_fish_stew=deep_sight I 150s / fish_soup=abyssal_current I 180s。ModItems の FoodEffect に追加 (chance 1.0)。
- ポーション素材: deep_sight=jelly_tentacle、abyssal_current=oil_sac (生体油昆布の産物)。
- デバフ付与源: pressure_fatigue=サメ (DeepSeaShark の bite)・ダイオウイカ・アンコウの威嚇 5s / cold_shock=helmet_jelly の刺し 4s / murk=atolla_jelly の刺し 6s。LivingHurtEvent で source.getEntity() の型を見て付与。
- 霧: 実際の深海霧の far は ABYSS 10〜SURFACE 96 (DeepOceanClientEffects.targetFogEnd、既に Night Vision/Conduit で x2 のフックあり)。仕様の「+64/+144 相当」は 144 前提の値なので倍率に読み替える: deep_sight I = end x2.5、II = x4 (上限 96)、暗さ (onFogColor の brightness) を I で 1 との差を 30%、II で 55% 詰める。murk = end x0.5 (deep_sight I で x0.75、II で x1)、brightness x0.7。深海層/水中以外では何もしない。
- 水泳速度: ForgeMod.SWIM_SPEED の MULTIPLY_TOTAL modifier (deep_swimmer +0.10/Lv、abyssal_current +0.15/Lv、pressure_fatigue -0.10/Lv)。推進スクリューもこの属性を読むので一緒に速くなる (許容)。
