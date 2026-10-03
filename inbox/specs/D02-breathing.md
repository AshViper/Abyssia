# Abyssia 仕様書
## D02「水中における呼吸の仕様変更」

---

## 目的

Minecraft の水中呼吸時間を Abyssia 独自の「潜水装備段階」に応じて延長する。

### 基本方針

- 装備なしでは **2分間** 水中活動可能とする。
- 潜水装備の段階が1上がるごとに **3分** 水中呼吸時間を延長する。
- 最大空気量そのものはバニラと同じ **300 air** を維持する。
- Mixin は使用しない。
- プレイヤー tick 処理で空気の減少速度を遅くする。
- 空気値の減少間隔は整数丸めによる誤差を避けるため、**端数を内部蓄積する**。
- HUD の泡ゲージは従来どおり10個のままとし、ゆっくり減少する。
- 空気が0以下になった場合の溺れダメージ処理はバニラ仕様を維持する。
- 水中呼吸ポーション、コンジット、水中呼吸効果を持つ食料、Respiration エンチャント等はバニラ仕様どおり併用可能とする。

Forge 1.20.1 と NeoForge 1.21.1 の両方で同一仕様を実装する。

---

# 仕様

## 1. 潜水装備段階

プレイヤーの頭部・胴部装備を確認し、装備している中で最も高い段階を採用する。

| 段階 | 装備 | 判定 |
|---:|---|---|
| 0 | 装備なし | 潜水装備なし |
| 1 | `entry_diver_helmet` / `entry_dive_tank` | どちらか一方でも装備 |
| 2 | `deep_diver_helmet` / `pressure_diver_helmet` / `dive_tank` | どれか一つでも装備 |

### 判定ルール

```text
stage = max(headStage, chestStage)

例:

装備なし + 装備なし
→ Stage 0

入門ヘルメット + 装備なし
→ Stage 1

装備なし + 入門タンク
→ Stage 1

入門ヘルメット + 入門タンク
→ Stage 1

深海ヘルメット + 入門タンク
→ Stage 2

入門ヘルメット + 深海タンク
→ Stage 2

深海ヘルメット + 深海タンク
→ Stage 2
フルセットボーナス

なし。

理由:

「レベルが上がるごとに3分延長」という原依頼を単純に維持する。

頭と胴の組み合わせによる追加ルールを設けると仕様が複雑になる。

片方だけ装備した場合でも、その装備段階の性能を明確に得られる。

フルセットを要求すると、装備交換時の挙動が不自然になる。

したがって、高い方の装備段階だけを採用する方式とする。

2. 水中呼吸時間

バニラの最大空気量 300 を変更せず、空気値の減少速度のみ変更する。

バニラでは300 air = 15秒であるため、

1秒 = 20 tick
300 air = 15秒

Abyssia では以下の時間になるように空気減少速度を調整する。

Stage	装備	目標水中呼吸時間	バニラ比
0	なし	2分 = 120秒	8倍
1	入門	5分 = 300秒	20倍
2	深海	8分 = 480秒	32倍
空気減少速度

300 air を目標時間で消費する。

Stage 0:
300 / 120秒 = 2.5 air/秒
1 airあたり 0.4秒

Stage 1:
300 / 300秒 = 1.0 air/秒
1 airあたり 1秒

Stage 2:
300 / 480秒 = 0.625 air/秒
1 airあたり 1.6秒

tick単位では、

Stage 0:
1 air / 8 tick

Stage 1:
1 air / 20 tick

Stage 2:
1 air / 32 tick

とする。

3. 端数の蓄積

Stage 2 は1 air / 32 tickとなるため、整数tick処理だけで単純に間隔を設定する。

ただし実装は「一定tickごとに1減らす」という固定タイマーだけにせず、空気消費量を浮動小数点相当の内部蓄積値として保持する方式を採用する。

概念:

airAccumulator += baseAirConsumptionPerTick * stageMultiplier

if airAccumulator >= 1.0:
    consume = floor(airAccumulator)
    air -= consume
    airAccumulator -= consume

これにより、

tick間隔の変更

Configによる時間変更

一時的な効果による減少速度変更

などが発生しても、端数切り捨てによる長期的なズレを防止する。

内部蓄積値

この値はプレイヤーの通常の air 値とは別管理とする。

AbyssiaAirAccumulator

プレイヤーのNBTへ永続保存する必要はない。

プレイヤーが水中から出た場合はリセットする。

4. 最大空気量

最大空気量は変更しない。

MAX_AIR = 300

禁止事項:

MAX_AIR = 2400
MAX_AIR = 9600

のように最大値そのものを増やす実装は行わない。

これにより、Minecraft標準の泡ゲージ表示をそのまま利用できる。

5. 泡ゲージ

バニラの泡ゲージをそのまま使用する。

泡アイコン数: 10個

最大 air: 300

Abyssia側では空気の減少速度だけを遅くする。

独自HUDは追加しない。

結果として、

Stage 0 → 2分かけて10個の泡が減少
Stage 1 → 5分かけて10個の泡が減少
Stage 2 → 8分かけて10個の泡が減少

となる。

6. 水中呼吸ポーション等との併用

水中呼吸効果が有効な場合は、バニラの水中呼吸処理を優先する。

Water Breathing

WATER_BREATHING が有効な間:

airを消費しない
装備耐久も消費しない

とする。

Abyssiaの時間制呼吸は「バニラの水中呼吸効果がない場合」にのみ適用する。

コンジット

コンジットによる水中呼吸についても、バニラ側で呼吸可能状態となる場合は同様に扱う。

水中呼吸を付与する食料

食料によって WATER_BREATHING 等のバニラ効果が付与される場合は、その効果を優先する。

Respiration

Respiration はバニラの空気消費軽減処理と併用する。

Abyssia側でRespirationを無効化・上書きしない。

概念:

Abyssiaによる基本消費速度
        ↓
バニラ側の水中呼吸判定
        ↓
Respiration等のバニラ補正
        ↓
最終的なair減少

具体的な実装では、バニラ処理を二重適用して実質的な呼吸時間が想定以上に延びないよう注意する。

7. 深海装備の常時水中呼吸効果

現在の深海装備に付与されている

WATER_BREATHING

の常時付与は廃止する。

深海装備は「常時水中呼吸」ではなく、Stage 2 の

8分間

の空気消費速度によって性能を表現する。

例外

深海ヘルメット等に存在する暗視効果は維持する。

つまり、

深海ヘルメット
├─ 水中呼吸常時効果 → 廃止
└─ 暗視 → 維持

とする。

数値表
水中呼吸
項目	Stage 0	Stage 1	Stage 2
装備	なし	入門装備	深海装備
最大air	300	300	300
呼吸時間	120秒	300秒	480秒
呼吸時間	2分	5分	8分
バニラ比	8倍	20倍	32倍
air消費	2.5/s	1.0/s	0.625/s
air 1あたり	0.4秒	1秒	1.6秒
air減少間隔	8tick	20tick	32tick
装備耐久

潜水装備は「呼吸時間を増やすための装置」として耐久を消費する。

採用仕様

水中にいる間のみ消費。

水中呼吸効果などにより air を消費していない場合は消費しない。

水から出た場合は消費しない。

タンク系アイテムを優先して耐久消費する。

ヘルメットは段階判定には使用するが、通常の呼吸運用では耐久を消費しない。

タンクが装備されている場合はタンクを消費対象とする。

タンクが存在しない場合に限り、段階を成立させているヘルメットを消費対象とする。

同時に2個の装備耐久を減らすことはしない。

耐久消費間隔
Stage	装備	耐久消費
0	なし	なし
1	入門装備	5秒ごとに1耐久
2	深海装備	10秒ごとに1耐久

耐久消費タイマーもtickベースで管理する。

Stage 1 = 100 tick
Stage 2 = 200 tick
耐久消費条件

以下をすべて満たした場合のみ消費する。

プレイヤーが水中
AND
バニラの水中呼吸効果等で完全呼吸状態ではない
AND
Abyssiaの呼吸時間システムがairを消費している
AND
対象装備の耐久が残っている
装備破損時

対象装備の耐久が0になった場合、その装備は通常どおり破損する。

破損後は次のtickから装備段階を再計算する。

例:

Stage 2
↓
深海タンク破損
↓
深海ヘルメットあり
↓
Stage 2を維持
Stage 2
↓
深海タンク破損
深海ヘルメットなし
↓
入門ヘルメットあり
↓
Stage 1
Config

Configはローダーごとの差を吸収できるよう、同一の論理名・既定値を使用する。

TOML
[breathing]
enabled = true

# Stage 0: 装備なし
stage0BreathingSeconds = 120

# Stage 1: 入門装備
stage1BreathingSeconds = 300

# Stage 2: 深海装備
stage2BreathingSeconds = 480

# Stage 1装備の耐久消費間隔
stage1DurabilitySeconds = 5

# Stage 2装備の耐久消費間隔
stage2DurabilitySeconds = 10

# フルセットによる追加ボーナス
fullSetBonusEnabled = false

# バニラの最大空気量。
# D02では300から変更しない。
maxAir = 300
Config項目
Config	既定値	単位	内容
breathing.enabled	true	boolean	D02システム有効/無効
stage0BreathingSeconds	120	秒	Stage 0の呼吸時間
stage1BreathingSeconds	300	秒	Stage 1の呼吸時間
stage2BreathingSeconds	480	秒	Stage 2の呼吸時間
stage1DurabilitySeconds	5	秒	Stage 1耐久消費間隔
stage2DurabilitySeconds	10	秒	Stage 2耐久消費間隔
fullSetBonusEnabled	false	boolean	フルセットボーナス
maxAir	300	air	最大空気量
maxAirについて

maxAir はConfig項目として公開するが、D02の既定値は必ず300とする。

実装上は、D02では最大airそのものを変更しないことを原則とする。

変更対象
1. 呼吸処理

プレイヤーのtick処理にAbyssiaの呼吸制御を追加する。

実装イメージ:

Player Tick
    ↓
プレイヤーか？
    ↓
水中か？
    ├─ No → airはバニラどおり回復
    │
    └─ Yes
         ↓
      Creative / Spectator？
         ├─ Yes → 呼吸制御対象外
         │
         └─ No
              ↓
      WATER_BREATHING等？
         ├─ Yes → air消費なし
         │
         └─ No
              ↓
        装備段階判定
              ↓
        Stage 0 / 1 / 2
              ↓
        消費量を蓄積
              ↓
        airを必要量だけ減少
              ↓
        装備耐久タイマー更新
2. Mixin

使用しない。

MixinによってMinecraft本体の呼吸処理を直接書き換えるのではなく、Forge / NeoForgeのイベント・tick処理を利用して実装する。

3. airの扱い

getAirSupply() / setAirSupply() 相当のAPIを利用してプレイヤーのair値を操作する。

Minecraft 1.20.1 ForgeではEntityにair取得・設定APIが存在し、NeoForge 1.21.1でもsetAirSupplyが提供されているため、ローダーごとのAPI差を吸収して実装する。

Forge 1.20.1
→ Entity#getAirSupply()
→ Entity#setAirSupply()

NeoForge 1.21.1
→ Entity#getAirSupply()
→ Entity#setAirSupply()
4. 装備段階判定

専用のユーティリティを作成する。

概念:

Java
int getDivingStage(Player player)

判定:

headStage = getHelmetStage(headItem)
chestStage = getChestStage(chestItem)

return max(headStage, chestStage)

装備判定はアイテムIDを直接大量に分散させず、Abyssia側の装備定義へ集約する。

5. 耐久管理

概念:

Java
ItemStack getBreathingEquipment(Player player, int stage)

優先順位:

1. dive tank
2. entry dive tank
3. 深海/入門ヘルメット

ただし、実際のStage判定と耐久消費対象は分離する。

エッジケース
1. 水から出た場合

水中から出た瞬間にAbyssiaの呼吸タイマーを停止する。

air回復はバニラ仕様を維持する。

水中
↓
水上
↓
Abyssia air消費停止
↓
バニラのair回復

Abyssia独自の高速回復は追加しない。

内部のair蓄積値:

AbyssiaAirAccumulator = 0

にリセットする。

耐久消費タイマーもリセットする。

2. 再び水中へ入った場合

水中へ戻った時点で現在の装備段階を再判定する。

例:

Stage 0
↓
入門タンク装備
↓
Stage 1

次のtickからStage 1の呼吸速度を適用する。

3. 装備交換中

水中で装備を交換した場合も、次のプレイヤーtickで段階を再計算する。

例:

Stage 0
↓
深海ヘルメットを装備
↓
次tickからStage 2

逆に、

Stage 2
↓
深海ヘルメットを外す
↓
Stage 0/1を再判定

とする。

4. クリエイティブ

Creativeプレイヤーはバニラ同様、通常の呼吸制限を受けない。

air消費なし
耐久消費なし

Spectatorについても呼吸制御対象外とする。

5. 水中呼吸ポーション

水中呼吸効果が有効な場合:

air消費なし
装備耐久消費なし

効果が切れた次tickからAbyssiaの通常処理へ戻る。

6. コンジット

コンジットによって水中呼吸状態になっている場合も、水中呼吸効果と同様に扱う。

Abyssia側でコンジットの効果を上書きしない。

7. Respiration

Respirationはバニラ仕様を維持する。

AbyssiaがRespirationを無効化したり、独自の倍率で置き換えたりしない。

特に、

Abyssiaの減少処理
+
バニラのRespiration処理

を二重に適用して意図せず呼吸時間を大幅に延長しないよう注意する。

8. airが0以下になった場合

溺れダメージはバニラ処理を利用する。

Abyssia側では、

air <= 0

になったことを理由に独自ダメージを発生させない。

バニラの溺れ判定・ダメージ・airリセットを維持する。

9. airが最大値を超える場合

Abyssia側では最大airを300として扱う。

air > 300

になるような処理は行わない。

水上での回復もバニラ側の最大値に従う。

10. 耐久値が不足している場合

耐久値が残り1の場合、通常の耐久消費によってアイテムが破損する。

破損後は即座に装備段階を再計算する。

耐久0の装備を無理にStage判定へ使用しない。

11. 装備なしでの溺れ

装備なしではStage 0。

300 air
↓
120秒で消費
↓
air 0
↓
バニラの溺れ処理

とする。

12. ログイン・リスポーン・ディメンション移動

Abyssia独自のair蓄積値は一時的なtick状態として扱う。

以下の場合はリセットしてよい。

ログイン
リスポーン
ディメンション移動
水から出る
死亡

プレイヤーの実際のair値そのものは、各イベントで不要に書き換えない。

テスト
T01: 装備なしの基本時間
手順

サバイバルモードにする。

水中へ潜る。

水中呼吸系の効果をすべて解除する。

泡ゲージが満タンの状態から計測する。

期待結果
約120秒でairが300 → 0

溺れダメージ開始後はバニラ仕様。

T02: 入門装備
手順

entry_diver_helmet を装備。

entry_dive_tank を装備。

水中へ潜る。

5分間計測する。

期待結果
約300秒でairが300 → 0
T03: 入門ヘルメットのみ
手順
entry_diver_helmet
+
胴装備なし
期待結果
Stage 1
約5分の呼吸時間
T04: 入門タンクのみ
手順
ヘルメットなし
+
entry_dive_tank
期待結果
Stage 1
約5分の呼吸時間
T05: 深海ヘルメット
手順
deep_diver_helmet
+
胴装備なし
期待結果
Stage 2
約8分の呼吸時間
T06: 耐圧ヘルメット
手順
pressure_diver_helmet
+
胴装備なし
期待結果
Stage 2
約8分の呼吸時間
T07: 深海タンク
手順
ヘルメットなし
+
dive_tank
期待結果
Stage 2
約8分の呼吸時間
T08: 異なる段階の組み合わせ
パターン
入門ヘルメット + 深海タンク
期待結果
Stage 2
約8分
T09: 深海ヘルメット + 入門タンク
期待結果
Stage 2
約8分
T10: フルセット
手順

Stage 1:

entry_diver_helmet
+
entry_dive_tank

Stage 2:

deep_diver_helmet
+
dive_tank
期待結果

フルセットによる追加ボーナスは発生しない。

Stage 1 → 5分
Stage 2 → 8分
T11: 暗視効果
手順

深海ヘルメットを装備して水中へ入る。

期待結果
暗視 → 維持
常時WATER_BREATHING → なし
呼吸時間 → Stage 2の8分
T12: 水中呼吸ポーション
手順

Stage 0状態にする。

水中呼吸ポーションを使用。

水中へ入る。

期待結果
WATER_BREATHING中
→ air消費なし
→ 装備耐久消費なし

効果終了後はStage 0の通常処理へ戻る。

T13: コンジット
期待結果

コンジットによる水中呼吸状態ではairを消費しない。

T14: Respiration
手順

Respiration付きヘルメットを使用する。

期待結果

AbyssiaによってRespirationが無効化されない。

バニラ仕様どおりの補正が適用される。

T15: 水上への退出
手順

水中でairを減らす。

水上へ出る。

air回復を確認する。

再び水中へ入る。

期待結果
水上
→ バニラ速度でair回復

水中へ再侵入
→ 現在の装備段階で呼吸速度を再判定

Abyssia独自の高速回復は発生しない。

T16: 耐久消費 Stage 1
手順

入門タンクを装備して水中に留まる。

期待結果
5秒ごとに1耐久

水上へ出た場合は耐久消費停止。

T17: 耐久消費 Stage 2
手順

深海タンクを装備して水中に留まる。

期待結果
10秒ごとに1耐久
T18: 水中呼吸中の耐久
手順

水中呼吸ポーションを使用し、潜水装備を装備する。

期待結果
air消費なし
耐久消費なし
T19: 装備破損
手順

耐久値1の潜水装備を装備して水中に入る。

期待結果

耐久消費タイミングで装備が破損し、その直後から新しい装備構成に応じたStageへ移行する。

T20: クリエイティブ
期待結果
air消費なし
耐久消費なし
溺れダメージなし
T21: 最大air確認
期待結果

どのStageでも、

最大air = 300

であること。

HUDの泡ゲージ数も、

10個

のままであること。

T22: 長時間精度試験

各Stageで満タンからairが0になるまで測定する。

許容値:

Stage 0: 120秒 ± 1秒
Stage 1: 300秒 ± 1秒
Stage 2: 480秒 ± 1秒

tick単位の丸め誤差が長時間累積して、設定値から大きく外れないことを確認する。

T23: Forge / NeoForge互換試験

以下の両環境で同一テストを実施する。

Forge 1.20.1
NeoForge 1.21.1

確認項目:

Stage判定

air減少速度

泡ゲージ

水中呼吸効果との併用

Respiration

耐久消費

装備破損

水上回復

クリエイティブ

溺れダメージ

両ローダーで仕様上の挙動が一致すること。

受け入れ条件

D02は以下をすべて満たした場合に完了とする。

[ ] 装備なしで約2分呼吸できる
[ ] 入門装備で約5分呼吸できる
[ ] 深海装備で約8分呼吸できる
[ ] 最大airが300のまま
[ ] 泡ゲージが10個のまま
[ ] Mixinを使用していない
[ ] 深海装備の常時WATER_BREATHINGが廃止されている
[ ] 深海ヘルメットの暗視が維持されている
[ ] 頭/胴の高い方のStageが採用される
[ ] フルセットによる追加ボーナスがない
[ ] Stage 1の耐久が5秒ごとに1減る
[ ] Stage 2の耐久が10秒ごとに1減る
[ ] 水中呼吸効果中はairが減らない
[ ] 水中呼吸効果中は装備耐久も減らない
[ ] Respirationを阻害しない
[ ] コンジットを阻害しない
[ ] 水中から出るとバニラ速度でairが回復する
[ ] クリエイティブで呼吸制限を受けない
[ ] airが0以下になった後の溺れダメージがバニラどおり
[ ] air消費の端数が長時間累積して誤差にならない
[ ] Forge 1.20.1で動作する
[ ] NeoForge 1.21.1で動作する
最終仕様まとめ
                 水中呼吸時間
                      │
        ┌─────────────┼─────────────┐
        │             │             │
     Stage 0       Stage 1       Stage 2
      装備なし       入門装備       深海装備
        │             │             │
      2分            5分            8分
        │             │             │
      300 air        300 air        300 air
        │             │             │
     8 tick/air     20 tick/air    32 tick/air
        │             │             │
       ─────────── バニラair値 ───────────
                      │
                0以下で溺れ
                      │
                 バニラ処理

D02の核心は、「最大airを増やす」のではなく、300 airを消費する速度をStage 0/1/2でそれぞれ 8倍/20倍/32倍遅くすることである。

これにより、Minecraft標準の泡ゲージ・air値・溺れ処理との互換性を維持しながら、

装備なし → 2分
入門装備 → 5分
深海装備 → 8分

というAbyssia独自の潜水システムを実現する。
---

> 実装メモ (2026-10-03): `fullSetBonusEnabled` と `maxAir` は効果がないため Config に入れていない。Config キーは `[breathing] enabled, stage0_seconds, stage1_seconds, stage2_seconds, stage1_durability_seconds, stage2_durability_seconds`。検査結果は D02-review.md。
