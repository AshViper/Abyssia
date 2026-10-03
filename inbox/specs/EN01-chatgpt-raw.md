<!-- reply 1 -->
```markdown
# EN01 深海エンチャント・バフ/デバフ

- tier: 中核コンテンツ
- files:
  - `src/main/java/.../registry/ModEnchantments.java`
  - `src/main/java/.../registry/ModMobEffects.java`
  - `src/main/java/.../enchantment/DeepSwimmerEnchantment.java`
  - `src/main/java/.../enchantment/ThermalCatchEnchantment.java`
  - `src/main/java/.../effect/DeepSightEffect.java`
  - `src/main/java/.../effect/PressureFatigueEffect.java`
  - `src/main/java/.../effect/ColdShockEffect.java`
  - `src/main/java/.../effect/MurkEffect.java`
  - `src/main/java/.../event/EN01EventHandler.java`
  - `src/main/java/.../potion/ModPotions.java`
  - `src/main/resources/assets/abyssia/lang/ja_jp.json`
  - `src/main/resources/assets/abyssia/lang/en_us.json`
  - `src/main/resources/assets/abyssia/textures/mob_effect/`
  - `src/main/resources/data/abyssia/recipes/`
- goal:
  - Abyssia 専用のエンチャント・MobEffect・ポーションを追加する。
  - バニラの水中呼吸・水中採掘・水中歩行・イルカの好意・コンジットパワー・暗視とは役割を重複させず、「深海探索・視界・採取」に特化する。
  - Forge 1.20.1 を基準実装とし、NeoForge 1.21.1 へ同等仕様で移植可能な構造にする。
  - 独自 MobEffect は登録方式を使用する。Forge では MobEffect が属性変更等を持てるため、必要な効果は独自クラスで実装する。

## 1. エンチャント

| ID | 名前 | 対象 | 最大Lv | 効果 | レア度 | 排他 | 入手 |
|---|---|---|---:|---|---|---|---|
| `deep_swimmer` | 深海遊泳 / Deep Swimmer | ブーツ | III | 水中での水平移動速度を Lv×10% 上昇。Lv1=10%、Lv2=20%、Lv3=30%。水上・陸上には影響しない | RARE | Depth Strider / Frost Walker と排他 | エンチャント台・取引 |
| `thermal_catch` | 熱処理 / Thermal Catch | 剣・斧 | I | Abyssia の魚類を倒した際、生肉ではなく対応する焼き肉をドロップする。焼き肉が存在しない魚は通常ドロップ | RARE | Looting と排他にしない | 宝物のみ |
| `abyssal_focus` | 深海集中 / Abyssal Focus | ヘルメット | II | 深海層で `Deep Sight` 効果を自動付与。Lv1=視界補正弱、Lv2=視界補正強。効果時間は常時更新 | VERY_RARE | Aqua Affinity と排他 | 宝物のみ |

### エンチャント詳細

#### `deep_swimmer`
- 対象は `FEET` の防具。
- プレイヤーが水中にいる場合のみ移動速度属性を加算する。
- 速度補正は通常移動速度に対する乗算ではなく、既存の水中移動処理を破壊しない方式で適用する。
- `Dolphin's Grace`、Depth Strider 等のバニラ効果を変更・無効化しない。
- 陸上では追加速度 0%。

#### `thermal_catch`
- Abyssia の魚類のみ対象。
- 対象魚:
  - cod 系 / salmon 系相当の Abyssia 魚
  - deepwater_redfish 等、Abyssia が魚として分類する全魚類
- 「魚類」の判定は個別クラス名のハードコードではなく、Abyssia 内部の魚類タグ/分類を使用する。
- 生魚→対応する焼き魚への変換テーブルを用意する。
- 焼き魚アイテムが登録されていない魚は変換せず通常ドロップ。
- Looting のドロップ数計算は維持する。
- `Fire Aspect` や炎上状態とは無関係に判定する。

#### `abyssal_focus`
- 深海層 Y=-368..-64 にいるプレイヤーのみ発動。
- 独自 `Deep Sight` を付与する。
- バニラ `Night Vision` は付与しない。
- `abyssal_focus II` は `Deep Sight II` として扱う。
- 地上・浅海では効果を付与しない。
- 防具を外した場合は通常の効果更新タイミングで消える。

## 2. MobEffect

### バフ

| ID | 名前 | 色 | 効果 | 付与源 |
|---|---|---|---|---|
| `deep_sight` | 深海視界 / Deep Sight | `#39B8D8` | 深海の暗さと霧を軽減。Lv1: 深海霧の実効距離を約+64ブロック相当、暗さを軽減。Lv2: 霧を約+144ブロック相当まで軽減し、暗さをさらに軽減 | `abyssal_focus`、料理、ポーション |
| `abyssal_current` | 深海潮流 / Abyssal Current | `#2868C7` | 水中水平移動速度 +15%。Lv2=+30%。陸上では効果なし | 料理、ポーション |

### デバフ

| ID | 名前 | 色 | 効果 | 付与源 |
|---|---|---|---|---|
| `pressure_fatigue` | 深海疲労 / Pressure Fatigue | `#514A72` | 水中移動速度 -10%。Lv2=-20%。攻撃速度は変更しない | 深海生物の攻撃 |
| `cold_shock` | 冷却ショック / Cold Shock | `#8AB8D8` | 移動速度 -15%、採掘速度 -15%。Lv2=-30%。陸上でも持続する | 特定の深海生物の攻撃 |
| `murk` | 深海濁り / Murk | `#26333D` | 視界を暗くし、深海霧を強化。`Deep Sight` がある場合は相殺される。Lv1は通常、Lv2は強い視界悪化 | クラゲ等の攻撃 |

### `Deep Sight` の視界仕様
- 「暗視」として完全に明るくするのではなく、Abyssia の深海霧・暗さを軽減する専用効果とする。
- 深海霧の自前描画システムが存在するため、MobEffect のクライアント側状態を参照して霧係数を変更する。
- シェーダー使用時も同じ効果状態を参照する。
- 通常ワールドの夜・洞窟を明るくする目的では使用しない。
- `Night Vision` と同時に存在しても `Night Vision` の仕様を変更しない。

## 3. ポーション

### `Potion of Deep Sight`
- 通常ポーション: `Deep Sight I` / 3:00
- 延長版: `Deep Sight I` / 8:00
- 強化版: `Deep Sight II` / 1:30

醸造:
- Awkward Potion + Abyssia の専用素材 `deep_sight_coral` → Potion of Deep Sight
- Potion of Deep Sight + Redstone → 延長版
- Potion of Deep Sight + Glowstone Dust → 強化版
- 既存の暗視ポーションとは別系統。
- 醸造レシピは Forge の BrewingRecipeRegistry を使用する。Forge の醸造レシピは通常のデータパックレシピとは別に登録する必要がある。

### `Potion of Abyssal Current`
- 通常: `Abyssal Current I` / 3:00
- 延長: `Abyssal Current I` / 8:00
- 強化: `Abyssal Current II` / 1:30

醸造:
- Awkward Potion + `propeller` → Potion of Abyssal Current
- Redstone → 延長
- Glowstone Dust → 強化

## 4. 料理への付与

既存の料理30種のうち、以下の4種に限定して追加効果を設定する。

| 料理 | 効果 | Lv | 時間 |
|---|---|---:|---:|
| 深海魚系の焼き料理 | `Abyssal Current` | I | 2:00 |
| `gourd_flesh` 系料理 | `Deep Sight` | I | 2:00 |
| `kelp_leaf` 系料理 | `Deep Sight` | I | 1:30 |
| 高級な魚料理系 | `Abyssal Current` | II | 1:00 |

- 既存の満腹度・隠し満腹度を変更しない。
- 同じ効果を持つ料理を連続して食べた場合、レベルが高い方を優先し、同レベルなら残り時間を延長する。
- 全30料理へ無差別にバフを付けない。
- 食料効果は `FoodProperties` 等、既存の料理実装方式に合わせる。

## 5. デバフ付与条件

### `Pressure Fatigue`
- Abyssia 深海生物の一部（大型魚・サメ等）の近接攻撃時に 5 秒。
- Lv1を基本とする。
- 同一攻撃で多重付与しない。

### `Cold Shock`
- クラゲ系・冷たい環境を表現する特定Mobの攻撃時に 4 秒。
- Lv1。
- 連続被弾時は残り時間を最大 4 秒まで更新する。

### `Murk`
- 視界妨害能力を持つクラゲ等が攻撃した場合に 6 秒。
- Lv1。
- `Deep Sight` が有効な場合、視界悪化量を50%軽減する。
- `Deep Sight II` では `Murk` の視界悪化を完全に無効化する。

## 6. 実装上の共通制約

- 登録 ID はすべて `abyssia:<id>`。
- Forge 1.20.1 / NeoForge 1.21.1 で機能仕様を一致させる。
- バニラの既存エンチャント・MobEffect の挙動を変更しない。
- サーバー側で効果判定を行い、クライアント側は表示・視界描画のみ担当する。
- `Deep Sight` の霧軽減は、既存の Abyssia 深海霧レンダリング実装にフックする。別の霧システムを新設しない。
- MobEffect の強度は Minecraft 標準の amplifier (`0=Lv1`) に合わせる。
- 効果時間・移動速度等は tick 単位で内部処理し、表示上の秒数は Minecraft の20 tick=1秒を基準とする。
- エンチャント判定は対象アイテム・装備部位を明示的に制限する。
- `thermal_catch` は Abyssia の魚類だけに適用し、バニラの魚・他Modの魚を変更しない。
- 他Modの魚への影響を避けるため、対象判定は Abyssia 独自タグを使用する。
- 既存の水中呼吸・水中採掘・水中歩行・イルカの好意・コンジットパワー・暗視を代替する実装にしない。
- 水圧・寒さによる自然ダメージシステムは今回追加しない。デバフ名として `Pressure Fatigue` / `Cold Shock` を使用するが、ダメージそのものは発生させない。
- Effectアイコンは 18x18 の専用テクスチャを使用する。
- アイコン未完成の場合でも登録名・効果ロジックを先に実装可能とする。

## 7. 必要なアイコン画像

### MobEffectアイコン 6種
1. `deep_sight.png`
   - シアン系の発光した深海眼/視界
2. `abyssal_current.png`
   - 水流＋推進を表す渦
3. `pressure_fatigue.png`
   - 深海圧力を受けている波/矢印
4. `cold_shock.png`
   - 氷結した波紋
5. `murk.png`
   - 濁った水中と視界低下を表すアイコン

※ バフ/デバフ合計は5種。`deep_sight` / `abyssal_current` / `pressure_fatigue` / `cold_shock` / `murk`。

### エンチャント関連
- エンチャント自体に専用GUIアイコンは必須としない。
- 必要に応じて将来、ガイドブック等で使用する説明アイコンを別途作成する。

## 8. 翻訳キー

最低限、以下を追加する。

- `enchantment.abyssia.deep_swimmer`
- `enchantment.abyssia.thermal_catch`
- `enchantment.abyssia.abyssal_focus`
- `effect.abyssia.deep_sight`
- `effect.abyssia.abyssal_current`
- `effect.abyssia.pressure_fatigue`
- `effect.abyssia.cold_shock`
- `effect.abyssia.murk`
- 各ポーション名
- 各エンチャントの説明文

## 9. accept

### エンチャント
- [ ] `/enchant` 等で `Deep Swimmer` を付与し、水中でLv1/2/3の速度差を確認できる。
- [ ] 陸上では `Deep Swimmer` による追加速度が発生しない。
- [ ] `Thermal Catch` 装備中に Abyssia の魚を倒すと焼き魚が落ちる。
- [ ] `Thermal Catch` 未装備では従来どおり生魚が落ちる。
- [ ] `Thermal Catch` がバニラ魚・他Mod魚に作用しない。
- [ ] `Abyssal Focus` 装備時、深海層で `Deep Sight` が付与される。

### バフ
- [ ] `Deep Sight` 無し/有りで深海霧の見通しが明確に変化する。
- [ ] `Deep Sight II` はLv1より明確に強い。
- [ ] `Deep Sight` が通常の `Night Vision` の代用品として働かない。
- [ ] `Abyssal Current` により水中移動速度が上昇する。
- [ ] バニラの水中系効果と併用してもクラッシュ・不正な速度値が発生しない。

### デバフ
- [ ] 対象Mobの攻撃で `Pressure Fatigue` が付与される。
- [ ] 対象Mobの攻撃で `Cold Shock` が付与される。
- [ ] 対象Mobの攻撃で `Murk` が付与される。
- [ ] `Deep Sight I` で `Murk` が50%軽減される。
- [ ] `Deep Sight II` で `Murk` の視界悪化が無効になる。
- [ ] デバフはダメージを直接発生させない。

### ポーション・料理
- [ ] Deep Sight / Abyssal Current の通常・延長・強化ポーションが醸造できる。
- [ ] Redstone/Glowstone による時間/レベル変更が正しく動作する。
- [ ] 指定された4種の料理だけがEN01の追加効果を持つ。
- [ ] 食事による既存の満腹度・食料値が変化していない。

### 移植性
- [ ] Forge 1.20.1 で全項目が動作する。
- [ ] NeoForge 1.21.1 でも同一 ID・同一数値・同一ゲームプレイ仕様になる。
- [ ] クライアントのみ接続/サーバーのみ接続など不正な環境で登録エラーを起こさない。
- [ ] 深海霧の自前描画・シェーダー使用時にも `Deep Sight` の軽減が反映される。
```

<!-- reply 2 -->
```markdown
## 3. ポーション

- ポーションは新規素材を追加せず、既存のAbyssiaアイテムを醸造素材として使用する。
- Forge 1.20.1では醸造レシピを `BrewingRecipeRegistry` 等から登録する方式を前提とする。NeoForge 1.21.1では対応する `RegisterBrewingRecipesEvent` 等のAPIへ読み替える。

### 3-1. Potion of Deep Sight

| 項目 | 仕様 |
|---|---|
| ID | `deep_sight` |
| 効果 | `Deep Sight I` |
| 通常時間 | 180秒 |
| 延長時間 | 480秒 |
| 強化 | `Deep Sight II` / 90秒 |
| 用途 | 深海の暗さ・霧を軽減し、周囲を見やすくする |

#### 醸造

| 入力 | 素材 | 出力 |
|---|---|---|
| Awkward Potion | `jelly_tentacle`（深海の発光クラゲの触手） | Potion of Deep Sight |
| Potion of Deep Sight | Redstone Dust | 8:00版 |
| Potion of Deep Sight | Glowstone Dust | Deep Sight II / 1:30 |

- `jelly_tentacle` は既存アイテムをそのまま使用する。
- 新規の `deep_sight_coral` 等は追加しない。
- `jelly_tentacle` が複数種類存在する場合は、EN01で指定された深海の発光クラゲの触手だけを対象とする。

### 3-2. Potion of Abyssal Current

| 項目 | 仕様 |
|---|---|
| ID | `abyssal_current` |
| 効果 | `Abyssal Current I` |
| 通常時間 | 180秒 |
| 延長時間 | 480秒 |
| 強化 | `Abyssal Current II` / 90秒 |
| 効果 | 水中水平移動速度 +15%、Lv2は +30% |

#### 醸造

| 入力 | 素材 | 出力 |
|---|---|---|
| Awkward Potion | 生体油昆布 (`oil_kelp` 系の既存アイテム) | Potion of Abyssal Current |
| Potion of Abyssal Current | Redstone Dust | 8:00版 |
| Potion of Abyssal Current | Glowstone Dust | Abyssal Current II / 1:30 |

- 新規醸造素材は作らない。
- 生体油昆布の既存アイテムIDは実装側で実際の登録IDに合わせる。
- 生体油昆布が複数段階（seed/base/middle/ripe等）存在する場合は、最終段階の通常素材1種だけを醸造素材にする。

### 3-3. Splash / Lingering Potion

- 通常Potionからバニラ標準方式で Splash Potion / Lingering Potion を作成可能にする。
- Gunpowder、Dragon's Breath は既存バニラ素材を使用する。
- EN01では投擲ポーション専用の追加効果を設けない。
- 効果・レベル・持続時間は通常ポーションと同じ系統で扱う。

---

## 4. 料理へのバフ付与

料理30種すべてには付与せず、深海料理として意味のあるものだけに限定する。

| 料理 | 効果 | Lv | 時間 |
|---|---|---:|---:|
| 深海魚の焼き身 | `Abyssal Current` | I | 120秒 |
| 深海魚の上質な焼き料理 | `Abyssal Current` | II | 60秒 |
| 発光クラゲ料理 | `Deep Sight` | I | 120秒 |
| 生体油昆布料理 | `Deep Sight` | I | 90秒 |
| 深海魚と昆布の料理 | `Deep Sight` | I | 150秒 |
| 深海魚の濃厚スープ系 | `Abyssal Current` | I | 180秒 |

### 料理実装ルール

- 上表の料理名はゲーム内で実際に登録されている料理名へ対応させる。
- 同一効果を持つ料理を食べた場合は、既存の同効果の残り時間を無条件にリセットせず、以下で処理する。
  - 高いレベルを優先。
  - 同レベルの場合は残り時間と新規時間の長い方を採用。
- 食料の満腹度・隠し満腹度・食べる速度は変更しない。
- 料理に付与する効果はEN01の表に記載されたものだけとする。
- バニラの `Night Vision`、`Water Breathing`、`Dolphin's Grace` 等は料理から付与しない。

---

## 5. 必要なアイコン画像

### 5-1. MobEffectアイコン

すべて Minecraft のMobEffect用 **18×18 px**。

| ファイル | 名前 | デザイン |
|---|---|---|
| `deep_sight.png` | 深海視界 | 発光する深海の眼＋視界を示す波紋 |
| `abyssal_current.png` | 深海潮流 | 水流が前方へ渦巻くイメージ |
| `pressure_fatigue.png` | 深海疲労 | 上から押し潰す圧力＋沈降する波 |
| `cold_shock.png` | 冷却ショック | 氷結した水流・鋭い冷気 |
| `murk.png` | 深海濁り | 濁った水＋ぼやけた視界 |

- Minecraftの既存MobEffectアイコンと同じ18×18 px基準。
- 背景は透明。
- 文字・数字は入れない。
- バフとデバフを一目で区別できるシルエットにする。
- 色は以下を基準とする。
  - `Deep Sight`: `#39B8D8`
  - `Abyssal Current`: `#2868C7`
  - `Pressure Fatigue`: `#514A72`
  - `Cold Shock`: `#8AB8D8`
  - `Murk`: `#26333D`

### 5-2. エンチャント

- EN01ではエンチャント専用テクスチャを追加しない。
- Minecraft標準のエンチャント表示を使用する。
- ガイドブック等で説明する場合のイラストは別依頼とする。

---

## 6. constraints

- tier: `standard`
  - 目安: **中規模の独自ゲームプレイ機能**。
  - 新規MobEffect、エンチャント、醸造、料理効果、Mob攻撃時のデバフ、深海霧との連携が必要なため、単純なアイテム追加より重い。
  - ただし新規Mob・新規ワールド生成・新規素材の追加は行わない。
- Forge 1.20.1を基準に実装し、NeoForge 1.21.1へ同等仕様で移植する。
- 全IDは `abyssia:<id>`。
- 独自MobEffectは登録システムを使用する。Minecraft 1.20.1の `MobEffect` は属性変更等を持てるため、必要な移動速度効果は独自Effectまたは属性Modifierで実装可能。
- サーバー側で効果状態・エンチャント判定・ドロップ変換を管理する。
- クライアント側はMobEffect表示と深海霧・視界描画を担当する。
- `Deep Sight` はバニラの `Night Vision` を付与しない。
- `Deep Sight` はAbyssiaの深海霧レンダリングを軽減する専用効果とする。
- 深海霧の既存実装を置き換えない。既存の霧係数へ `Deep Sight` の軽減値を組み込む。
- シェーダー使用時にも同じ効果状態を参照する。
- `Abyssal Current` は水中水平移動のみを強化し、陸上移動速度は変更しない。
- バニラの水中歩行、イルカの好意、コンジットパワー等を変更・無効化しない。
- `Thermal Catch` はAbyssiaが「魚類」として登録したMobだけを対象とする。
- `Thermal Catch` はバニラ魚・他Mod魚へ作用させない。
- `Thermal Catch` は対応する焼き肉が存在する場合だけ生肉→焼き肉へ変換する。
- Looting等の既存ドロップ数計算は維持する。
- 環境による水圧ダメージ・寒さダメージは追加しない。
- `Pressure Fatigue` / `Cold Shock` は名称に反して直接ダメージを与えない。
- 新規醸造素材を作らない。
- 醸造素材は以下の既存アイテムだけを使用する。
  - `jelly_tentacle`
  - 生体油昆布の既存アイテム
  - その他、Redstone / Glowstone Dust / Gunpowder / Dragon's Breath等の既存バニラ素材
- 生肉を醸造素材にする場合は、既存の深海魚の生肉を使用してよい。ただし、同じ目的をより明確に表現できる既存素材（`jelly_tentacle` / 生体油昆布）がある場合はそちらを優先する。
- ポーション醸造はMinecraft/ローダー側の醸造登録APIに合わせる。Forge 1.20.1では醸造レシピがデータパックレシピではなくコード登録対象である。
- 効果時間は20 tick = 1秒として実装する。
- MobEffect amplifierはMinecraft標準方式で `0=Lv1`, `1=Lv2` とする。
- Effectアイコンが未納品でも、登録・効果処理自体は実装可能な構造にする。
- 既存の料理30種の名前・IDを勝手に変更しない。
- 表に記載した料理に該当する実際の登録アイテムが存在しない場合、実装側で勝手に新料理を追加せず、最も近い既存料理へ対応付ける。

---

## 7. accept

### エンチャント

- [ ] `Deep Swimmer I/II/III` を付与したブーツを装備すると、水中水平移動速度がそれぞれ +10% / +20% / +30% になる。
- [ ] `Deep Swimmer` の効果が陸上移動へ影響しない。
- [ ] `Thermal Catch` I を付けた剣/斧でAbyssiaの魚を倒すと、対応する焼き魚がドロップする。
- [ ] `Thermal Catch` がない場合は従来どおり生肉がドロップする。
- [ ] `Thermal Catch` がバニラ魚・他Mod魚へ作用しない。
- [ ] Looting付き武器でもドロップ数の通常処理が維持される。
- [ ] `Abyssal Focus I/II` 装備時、深海層で `Deep Sight I/II` が自動付与される。
- [ ] 防具を外すと `Abyssal Focus` 由来の効果が消える。

### バフ

- [ ] `Deep Sight I` で深海の暗さ・霧が軽減される。
- [ ] `Deep Sight II` はIより強く霧・暗さが軽減される。
- [ ] `Deep Sight` が通常ワールドの暗闇を完全な暗視状態へ変更しない。
- [ ] `Abyssal Current I/II` で水中水平移動速度が +15% / +30% になる。
- [ ] `Abyssal Current` が陸上移動速度へ影響しない。
- [ ] バニラの水中系効果との併用でクラッシュ・異常な速度値が発生しない。

### デバフ

- [ ] 対象Mobの攻撃で `Pressure Fatigue` が付与される。
- [ ] `Pressure Fatigue I` で水中移動速度が -10% になる。
- [ ] 対象Mobの攻撃で `Cold Shock` が付与される。
- [ ] `Cold Shock I` で移動速度・採掘速度がそれぞれ -15% になる。
- [ ] 対象Mobの攻撃で `Murk` が付与される。
- [ ] `Murk I` で視界が悪化する。
- [ ] `Deep Sight I` 中は `Murk` の視界悪化が50%軽減される。
- [ ] `Deep Sight II` 中は `Murk` の視界悪化が完全に無効化される。
- [ ] 3種のデバフはいずれも直接HPダメージを発生させない。

### ポーション

- [ ] `Potion of Deep Sight` をAwkward Potion + `jelly_tentacle` で醸造できる。
- [ ] `Potion of Abyssal Current` をAwkward Potion + 生体油昆布で醸造できる。
- [ ] Redstoneで各ポーションを延長できる。
- [ ] Glowstone Dustで各ポーションを強化できる。
- [ ] Splash / Lingering Potionをバニラ標準素材から作成できる。
- [ ] 新規醸造素材アイテムが追加されていない。

### 料理

- [ ] 指定された料理を食べると表の効果・レベル・秒数が付与される。
- [ ] 指定外の料理にはEN01の効果が付与されない。
- [ ] 料理の満腹度・隠し満腹度・食事時間が変更されていない。
- [ ] 同じ効果の料理を連続して食べても、不自然な効果レベル低下や即時消失が発生しない。

### 視界・霧

- [ ] 深海層 Y=-368..-64 で `Deep Sight` の霧軽減が確認できる。
- [ ] `Deep Sight` が既存の144ブロック深海霧システムと正常に連動する。
- [ ] シェーダー有効時にも効果が反映される。
- [ ] `Deep Sight` のON/OFFで視界距離に明確な差がある。
- [ ] 地上・浅海で通常のMinecraft描画を不必要に変更しない。

### 移植性

- [ ] Forge 1.20.1で全EN01機能が動作する。
- [ ] NeoForge 1.21.1で同じID・効果・数値・ゲームプレイ仕様になる。
- [ ] ローダー固有API部分を除き、ゲームプレイ上の仕様差がない。
- [ ] クライアント・サーバー間でMobEffect状態が正常に同期される。
- [ ] 専用Effectアイコンが存在する場合、インベントリ/画面上で正しく表示される。
```

<!-- reply 3 -->
了解です。今回は**検索結果・引用マーカーは仕様書本文に一切入れず**、3以降だけをそのまま使える形で出します。

### 3. ポーション

- **Potion of Deep Sight**
  - 効果: `Deep Sight I`
  - 通常: 180秒
  - 延長: 480秒
  - 強化: `Deep Sight II` / 90秒
  - 醸造素材: `jelly_tentacle`（深海の発光クラゲの触手）
  - 醸造:
    - Awkward Potion + `jelly_tentacle` → Potion of Deep Sight
    - Potion of Deep Sight + Redstone Dust → 480秒版
    - Potion of Deep Sight + Glowstone Dust → Deep Sight II / 90秒版

- **Potion of Abyssal Current**
  - 効果: `Abyssal Current I`
  - 通常: 180秒
  - 延長: 480秒
  - 強化: `Abyssal Current II` / 90秒
  - 醸造素材: **生体油昆布の既存アイテム**
  - 醸造:
    - Awkward Potion + 生体油昆布 → Potion of Abyssal Current
    - Potion of Abyssal Current + Redstone Dust → 480秒版
    - Potion of Abyssal Current + Glowstone Dust → Abyssal Current II / 90秒版

- 新規の醸造素材アイテムは追加しない。
- `jelly_tentacle` と生体油昆布は既存登録アイテムをそのまま使用する。
- 生体油昆布が複数段階ある場合は、**成熟した通常の生体油昆布1種だけ**を醸造素材にする。
- 深海魚の生肉は、今後別のポーションを追加する場合の候補素材とするが、EN01では上記2種を優先し、醸造レシピを増やしすぎない。
- Splash Potion / Lingering Potion はバニラ標準の Gunpowder / Dragon's Breath を使用して作成可能とする。
- 投擲化によって効果内容・レベル・基本持続時間を変更しない。

### 3.1 料理に付けるバフ

既存料理30種のうち、以下の料理だけに追加効果を付与する。

| 料理 | 効果 | レベル | 時間 |
|---|---|---:|---:|
| 深海魚の焼き身 | `Abyssal Current` | I | 120秒 |
| 深海魚の上質な焼き料理 | `Abyssal Current` | II | 60秒 |
| 発光クラゲ料理 | `Deep Sight` | I | 120秒 |
| 生体油昆布料理 | `Deep Sight` | I | 90秒 |
| 深海魚と昆布の料理 | `Deep Sight` | I | 150秒 |
| 深海魚の濃厚スープ系料理 | `Abyssal Current` | I | 180秒 |

- 表中の料理名は実際に登録済みの料理名へ対応させる。
- 該当する料理が存在しない場合、新しい料理を勝手に追加せず、実装済みの最も近い料理へ割り当てる。
- 指定外の料理にはEN01のバフを付けない。
- 満腹度、隠し満腹度、食事時間は変更しない。
- 同じ効果の料理を連続して食べた場合:
  - 高いレベルを優先する。
  - 同レベルなら残り時間と新規付与時間の長い方を採用する。
- `Night Vision`、`Water Breathing`、`Dolphin's Grace` 等のバニラ効果は料理から付与しない。

---

### 4. アイコン一覧

すべて **18×18 px、透明背景、文字なし**。

| ファイル | 名前 | 内容 |
|---|---|---|
| `deep_sight.png` | 深海視界 / Deep Sight | 発光する深海の眼＋視界を示す波紋 |
| `abyssal_current.png` | 深海潮流 / Abyssal Current | 前方へ流れる水流・渦 |
| `pressure_fatigue.png` | 深海疲労 / Pressure Fatigue | 上から押し潰す圧力＋沈降する波 |
| `cold_shock.png` | 冷却ショック / Cold Shock | 氷結した水流・冷気 |
| `murk.png` | 深海濁り / Murk | 濁った水＋ぼやけた視界 |

色の目安:

- `Deep Sight`: `#39B8D8`
- `Abyssal Current`: `#2868C7`
- `Pressure Fatigue`: `#514A72`
- `Cold Shock`: `#8AB8D8`
- `Murk`: `#26333D`

エンチャント専用アイコンは作らない。Minecraft標準のエンチャント表示を使用する。

---

### 5. accept

#### エンチャント

- [ ] `Deep Swimmer I/II/III` で水中水平移動速度がそれぞれ +10% / +20% / +30% になる。
- [ ] `Deep Swimmer` が陸上移動速度に影響しない。
- [ ] `Thermal Catch I` 付き武器でAbyssiaの魚を倒すと、対応する焼き魚がドロップする。
- [ ] `Thermal Catch` がない場合は従来どおり生肉がドロップする。
- [ ] `Thermal Catch` がバニラ魚・他Modの魚に作用しない。
- [ ] Looting等の既存ドロップ数処理が維持される。
- [ ] `Abyssal Focus I/II` で深海層にいる間 `Deep Sight I/II` が付与される。
- [ ] `Abyssal Focus` の付いた防具を外すと、そのエンチャント由来の効果が消える。

#### バフ

- [ ] `Deep Sight I` で深海の暗さ・霧が軽減される。
- [ ] `Deep Sight II` は `Deep Sight I` より強く霧・暗さを軽減する。
- [ ] `Deep Sight` が通常の `Night Vision` を付与しない。
- [ ] `Abyssal Current I` で水中水平移動速度が +15% になる。
- [ ] `Abyssal Current II` で水中水平移動速度が +30% になる。
- [ ] `Abyssal Current` が陸上移動速度に影響しない。
- [ ] バニラの水中系エフェクトとの併用で異常な速度値やクラッシュが発生しない。

#### デバフ

- [ ] 対象Mobの攻撃で `Pressure Fatigue` が付与される。
- [ ] `Pressure Fatigue I` で水中移動速度が -10% になる。
- [ ] 対象Mobの攻撃で `Cold Shock` が付与される。
- [ ] `Cold Shock I` で移動速度・採掘速度がそれぞれ -15% になる。
- [ ] 対象Mobの攻撃で `Murk` が付与される。
- [ ] `Murk I` で視界が悪化する。
- [ ] `Deep Sight I` 中は `Murk` の視界悪化が50%軽減される。
- [ ] `Deep Sight II` 中は `Murk` の視界悪化が完全に無効化される。
- [ ] `Pressure Fatigue`、`Cold Shock`、`Murk` は直接HPダメージを発生させない。

#### ポーション

- [ ] Awkward Potion + `jelly_tentacle` で `Potion of Deep Sight` を作成できる。
- [ ] Awkward Potion + 生体油昆布で `Potion of Abyssal Current` を作成できる。
- [ ] Redstone Dustで効果時間を延長できる。
- [ ] Glowstone Dustで効果レベルを強化できる。
- [ ] Splash / Lingering Potionへ変換できる。
- [ ] EN01のために新しい醸造素材アイテムが追加されていない。

#### 料理

- [ ] 指定された料理を食べると表どおりの効果・レベル・時間になる。
- [ ] 指定外の料理にはEN01の追加バフが付かない。
- [ ] 料理の満腹度・隠し満腹度・食事時間が変わっていない。
- [ ] 同じ効果の料理を連続して食べても効果が不自然に弱くならない。

#### 深海視界・霧

- [ ] 深海層 Y=-368..-64 で `Deep Sight` の有無による視界差を確認できる。
- [ ] 既存の144ブロック深海霧システムと正常に連動する。
- [ ] シェーダー使用時にも `Deep Sight` の霧軽減が反映される。
- [ ] `Deep Sight` が地上・浅海の通常描画を不必要に変更しない。

---

### constraints

- **tier: `standard`**
  - 目安: **中規模の独自ゲームプレイ機能**。
  - 独自エンチャント3種、MobEffect 5種、ポーション2系統、料理効果、Mob攻撃時デバフ、既存深海霧との連携が必要。
  - 一方で、新規Mob・新規ワールド生成・新規ブロック・新規醸造素材は追加しないため `heavy` にはしない。
- Forge 1.20.1を基準実装し、NeoForge 1.21.1へ同等仕様で移植する。
- 全IDは `abyssia:<id>` とする。
- 独自MobEffectは登録システムを使用する。
- サーバー側で効果状態、エンチャント判定、ドロップ変換、料理効果を管理する。
- クライアント側はMobEffect表示と深海霧・視界描画を担当する。
- `Deep Sight` は `Night Vision` を付与しない。
- `Deep Sight` はAbyssia独自の深海霧・暗さを軽減する専用効果とする。
- 既存の深海霧レンダリングシステムを置き換えず、既存の霧係数へ `Deep Sight` の補正を組み込む。
- シェーダー使用時も同じ `Deep Sight` 状態を参照する。
- `Abyssal Current` は水中水平移動だけを強化し、陸上移動速度は変更しない。
- バニラの水中歩行、イルカの好意、コンジットパワー、水中呼吸、水中採掘等を変更・無効化しない。
- `Thermal Catch` はAbyssiaが魚類として扱うMobだけを対象とする。
- `Thermal Catch` はバニラ魚および他Modの魚へ作用させない。
- `Thermal Catch` は対応する焼き肉が存在する場合だけ生肉を焼き肉へ変換する。
- Looting等の既存ドロップ処理を維持する。
- 水圧ダメージ・寒さダメージなどの新しい環境ダメージシステムは追加しない。
- `Pressure Fatigue` / `Cold Shock` は直接ダメージを与えない。
- EN01では新規醸造素材を一切追加しない。
- 醸造素材は既存アイテムから選択する:
  - `jelly_tentacle`
  - 生体油昆布
  - 必要に応じて深海魚の生肉
  - Redstone Dust
  - Glowstone Dust
  - Gunpowder
  - Dragon's Breath
- 料理30種の既存ID・名前を変更しない。
- 表に存在しない新料理をEN01のために追加しない。
- Effectアイコンは18×18 pxを使用する。
- アイコン未納品でも、登録・効果ロジックを先に実装できる構造にする。
- 効果時間は20 tick = 1秒として扱う。
- MobEffect amplifierは `0=Lv1`、`1=Lv2` とする。
- Forge版とNeoForge版でID、数値、効果、入手方法、持続時間を一致させる。
