以下の仕様なら、既存の深海装備と役割が被らず、「地上から深海へ行くための安価な入門装備」→「深海ヘルム系へ更新」という進行を作れます。Forge 1.20.1 では onInventoryTick が装備由来の毎tick処理に使え、ArmorItem#getDefaultAttributeModifiers で属性を扱えるため、指定の実装方針とも整合します。
MC Street Guy
+1

D01 入門潜水装備仕様書
D01 地上用入門潜水装備

tier: light

files:

src/main/java/.../item/armor/EntryDiverHelmetItem.java

src/main/java/.../item/armor/EntryDiverLeggingsItem.java

src/main/java/.../item/EntryDiveTankItem.java

src/main/java/.../item/EntryDivingFlippersItem.java

src/main/resources/assets/abyssia/models/item/entry_diver_helmet.json

src/main/resources/assets/abyssia/models/item/entry_dive_tank.json

src/main/resources/assets/abyssia/models/item/entry_diving_suit_leggings.json

src/main/resources/assets/abyssia/models/item/entry_diving_flippers.json

src/main/resources/assets/abyssia/textures/item/entry_diver_helmet.png

src/main/resources/assets/abyssia/textures/item/entry_dive_tank.png

src/main/resources/assets/abyssia/textures/item/entry_diving_suit_leggings.png

src/main/resources/assets/abyssia/textures/item/entry_diving_flippers.png

src/main/resources/assets/abyssia/textures/models/armor/entry_diving_suit_layer_1.png

src/main/resources/assets/abyssia/textures/models/armor/entry_diving_suit_layer_2.png

src/main/resources/assets/abyssia/lang/en_us.json

src/main/resources/assets/abyssia/lang/ja_jp.json

src/main/resources/data/abyssia/recipes/entry_diver_helmet.json

src/main/resources/data/abyssia/recipes/entry_dive_tank.json

src/main/resources/data/abyssia/recipes/entry_diving_suit_leggings.json

src/main/resources/data/abyssia/recipes/entry_diving_flippers.json

goal:

地上・浅海から深海層へ向かうプレイヤー向けの「入門用潜水装備」を追加する。

深海装備を作るまでの中間装備として機能させる。

銅・鉄・革・ガラス・ケルプなど、通常ワールドで入手可能なバニラ素材だけで製作可能にする。

既存の deep_diver_helmet、abyssal_flippers、dive_tank、diving_suit_leggings、pressure_diver_helmet より明確に低性能にする。

「深海へ行けるが、長時間の探索には向かない」という明確な立ち位置にする。

深海装備を作る前の探索・素材集めを成立させる。

constraints:

新規装備は4点セットとする。

セット構成は以下。

entry_diver_helmet

entry_dive_tank

entry_diving_suit_leggings

entry_diving_flippers

胴体用ArmorItemは追加しない。

entry_dive_tank は既存 dive_tank と同様に専用装備枠ではなく、実装側で既存システムに合わせた携行装備として扱う。

新規素材・Abyssia素材はレシピに使用しない。

レシピは作業台またはかまどのみ。

高性能な水中呼吸無限化は行わない。

暗視効果は常時付与しない。

深海ヘルムの「水中呼吸＋弱い暗視」と同等以上の性能を絶対に与えない。

abyssal_flippers の泳速+35%を超えない。

既存の深海装備と同時装備した場合、新装備側の効果が二重加算されて大幅に強化されないようにする。

実装は既存プロジェクトの ArmorItem サブクラス方式を踏襲する。

装備中効果は onInventoryTick を基本とする。

移動速度・泳速度などの属性値は getDefaultAttributeModifiers を基本とする。

レシピはMinecraft 1.20.1のデータ駆動レシピとして実装する。Forge/MinecraftではレシピJSONが標準的な方式。

onArmorTick を新規実装の中心にはしない。Forge 1.20.1では非推奨で、onInventoryTick が代替として案内されている。

accept:

4種類すべてが登録され、ゲーム内で入手できる。

4種類すべてがバニラ素材だけで作成できる。

入門装備だけで海中探索が可能。

水中呼吸能力は「無限」ではなく、明確な制限がある。

暗視は深海ヘルムより弱く、常時暗視にならない。

泳速は既存の abyssal_flippers より低い。

フルセット時に入門装備専用のセットボーナスが発生する。

フルセットでも既存の深海装備フルセットより弱い。

既存の深海装備を装備した場合、新規入門装備のセットボーナスが誤って発動しない。

JEI等でレシピが確認できる。

16×16アイテムテクスチャと防具レイヤーが用意される。

バニラ装備の延長線上に見えるデザインで、Abyssia固有の深海合金装備とは明確に区別できる。

1. 装備構成
ID	日本語名	English	部位	防御	タフネス	主能力
entry_diver_helmet	簡易潜水ヘルム	Entry Diver Helmet	HEAD	2	0	水中活動時間延長
entry_dive_tank	簡易潜水タンク	Entry Dive Tank	特殊/携行	0	0	空気供給
entry_diving_suit_leggings	簡易潜水レギンス	Entry Diving Suit Leggings	LEGS	2	0	泳速+3%
entry_diving_flippers	簡易潜水フィン	Entry Diving Flippers	FEET相当/既存方式	1	0	泳速+15%
基本方針

入門装備は「深海に耐える装備」ではなく、

地上 → 海面 → 浅海 → 深海入口

までを安全に移動するための装備とする。

deep_diver_helmet は、

防御3

タフネス2

水中呼吸

弱い暗視

を持つため、入門ヘルムはこれより明確に低性能にする。

2. entry_diver_helmet
基本性能

ID

abyssia:entry_diver_helmet

日本語

簡易潜水ヘルム

英語

Entry Diver Helmet

Armor

防御力: 2

タフネス: 0

ノックバック耐性: 0

耐久値: 120

特殊効果
水中呼吸補助

常時 Water Breathing を付与しない。

代わりに、装備中かつ水中にいる場合、

通常の空気ゲージを使用

空気が一定値まで減少した際に、少量だけ補助空気を供給

補助は一定間隔でしか発生しない

装備を外せば補助状態は終了

とする。

実装上は onInventoryTick で処理する。

推奨値:

補助発動条件: Air Supply <= 80

補助後: 140

再補助クールダウン: 40 tick

1回の補助で耐久値1を消費

これにより、

「酸素が無限になる」のではなく、「通常より長く潜れる」

という性能になる。

暗所補助

暗視効果は付与しない。

代わりに、水中かつ非常に暗い場所でのみ、

Night Vision I

20 tick

40 tick間隔で更新

を検討可能。

ただし、暗視が強すぎる場合はこの機能自体を削除する。

第一候補は「暗視なし」。

深海ヘルムとの差別化を優先する。

3. entry_dive_tank
基本性能

ID

abyssia:entry_dive_tank

日本語

簡易潜水タンク

英語

Entry Dive Tank

耐久値

160

効果

ヘルムとは別系統の空気補助を担当する。

装備中、水中で空気が減少した際に少量の補助空気を供給する。

推奨値:

発動条件: Air Supply <= 100

補給量: +40

クールダウン: 60 tick

1回につき耐久1消費

ヘルムとタンクを両方装備した場合は、

別々に無限補給するのではなく、セットとして補助量を統合する。

推奨仕様:

ヘルムのみ

+60程度の延長

タンクのみ

+40程度の延長

ヘルム＋タンク

+100程度の延長

ただし、毎tickで回復させず、クールダウンを設ける。

これにより深海ヘルム系の「水中呼吸常時維持」と明確に差別化する。

4. entry_diving_suit_leggings
基本性能

ID

abyssia:entry_diving_suit_leggings

日本語

簡易潜水レギンス

英語

Entry Diving Suit Leggings

Armor

防御力: 2

タフネス: 0

耐久値: 150

泳速

属性補正:

SWIM_SPEED +3%

既存:

diving_suit_leggings = +5%

より低くする。

5. entry_diving_flippers
基本性能

ID

abyssia:entry_diving_flippers

日本語

簡易潜水フィン

英語

Entry Diving Flippers

Armor

防御力: 1

タフネス: 0

耐久値: 100

泳速

属性補正:

SWIM_SPEED +15%

既存:

abyssal_flippers = +35%

より大幅に低くする。

6. 4点セットボーナス

4点すべてを装備した場合のみ発動。

判定対象:

entry_diver_helmet

entry_dive_tank

entry_diving_suit_leggings

entry_diving_flippers

セット効果
潜水効率

水中にいる間、

泳速 +5%

水中採掘速度 +5%

を付与。

また、空気補助のクールダウンを少し短縮する。

推奨:

40 tick → 32 tick

合計泳速

単品:

レギンス +3%

フィン +15%

セット:

ボーナス +5%

合計:

+23%

既存の abyssal_flippers +35% より低い。

また、既存の深海装備フルセットの +10% セットボーナスより単純に上位互換にならないよう、

入門セットの+5%は「入門装備自身の性能を補うためのボーナス」

として扱う。

7. レシピ

すべてバニラ素材のみ。

Abyssia素材は禁止。

7.1 簡易潜水ヘルム

entry_diver_helmet

作業台 3×3
I G I
I L I
I   I
記号	素材
I	鉄インゴット
G	ガラス板
L	革

出力

1 × entry_diver_helmet

意図

鉄製の簡易フレーム＋ガラス製の視界＋革製の密閉材。

7.2 簡易潜水タンク

entry_dive_tank

作業台 3×3
 I I
I K I
 I I
記号	素材
I	鉄インゴット
K	ケルプ

中央のケルプは「空気供給装置内部の生体素材」というAbyssia独自設定ではなく、

簡易的な酸素生成・ろ過材

という表現にする。

ただし、ゲームバランス上ケルプを消費する必要はない。

出力

1 × entry_dive_tank

7.3 簡易潜水レギンス

entry_diving_suit_leggings

作業台 3×3
L   L
L I L
L L L
記号	素材
L	革
I	鉄インゴット

出力

1 × entry_diving_suit_leggings

7.4 簡易潜水フィン

entry_diving_flippers

作業台 3×3
L   L
L   L
S   S
記号	素材
L	革
S	糸

出力

1 × entry_diving_flippers

革を主体にしているため、深海フィンよりも明確に安価な装備とする。

8. レシピ素材の方針

使用可能素材:

鉄インゴット

ガラス板

革

糸

ケルプ

使用禁止:

深海合金

Abyssia鉱物

Abyssia植物

深海クラスト

深海結晶

その他Abyssia専用素材

これにより、

「Abyssiaを始めた直後でも作れる」

ことを保証する。

9. テクスチャデザイン

全アイテム:

16×16 PNG / 透過背景 / Minecraft Vanilla風ピクセルアート

共通デザイン

深海装備:

ティール

深海青

暗い金属

深海合金

を中心とする。

入門装備はそれよりも、

鉄グレー

銅色

革ブラウン

ガラス水色

ケルプ緑

を中心にする。

つまり、

「普通のダイビング装備をMinecraft化したもの」

に見えるようにする。

10. entry_diver_helmet.png

16×16アイコン。

配色:

メイン: #6B7375 鉄グレー

補助: #8A4F32 革ブラウン

ガラス: #78B9C5 水色

ハイライト: #B7D6D8

影: #303638

デザイン:

丸型の簡易潜水ヘルメット

正面に小さなガラス窓

側面に簡単な空気ホース

ボルトを数個配置

深海ヘルムより明らかに簡素

11. entry_dive_tank.png

配色:

タンク: 鉄グレー

ベルト: 革ブラウン

バルブ: 銅色

ホース: 黒

小さな圧力計: 水色

16×16では、

「背負う小型ボンベ」

と一目で分かるシルエットを優先する。

12. entry_diving_suit_leggings.png

配色:

スーツ: #303B3D

革部分: #70462F

金属部分: #747D7F

ステッチ: #9A7658

通常の革装備より少し工業的。

ただし深海装備のような発光・結晶・ティール主体にはしない。

13. entry_diving_flippers.png

配色:

フィン本体: #40595B

革ストラップ: #70462F

金属バックル: #777D7D

ハイライト: #6E9294

横向きのフィン形状。

16×16で、

「靴ではなくフィン」

と認識できるシルエットを最優先する。

14. 防具レイヤー

ファイル:

entry_diving_suit_layer_1.png

entry_diving_suit_layer_2.png

layer 1

ヘルム・レギンスなどの主要防具部分。

layer 2

必要な追加パーツ。

デザイン:

暗い灰色の潜水服

革製ベルト

小型の金属バックル

銅製の配管

水色のガラス部品

Abyssia専用の発光表現は禁止。

深海装備へ進化した際に、

「バニラ潜水装備 → Abyssia深海装備」

という視覚的なアップグレードが分かるようにする。

15. lang
ja_jp.json
{
  "item.abyssia.entry_diver_helmet": "簡易潜水ヘルム",
  "item.abyssia.entry_dive_tank": "簡易潜水タンク",
  "item.abyssia.entry_diving_suit_leggings": "簡易潜水レギンス",
  "item.abyssia.entry_diving_flippers": "簡易潜水フィン"
}
en_us.json
{
  "item.abyssia.entry_diver_helmet": "Entry Diver Helmet",
  "item.abyssia.entry_dive_tank": "Entry Dive Tank",
  "item.abyssia.entry_diving_suit_leggings": "Entry Diving Suit Leggings",
  "item.abyssia.entry_diving_flippers": "Entry Diving Flippers"
}
16. 既存装備との性能比較
装備	入手難度	水中呼吸	暗視	泳速
簡易潜水ヘルム	バニラ素材	限定的な補助	なし	-
簡易潜水タンク	バニラ素材	限定的な補助	-	-
簡易潜水レギンス	バニラ素材	-	-	+3%
簡易潜水フィン	バニラ素材	-	-	+15%
diving_suit_leggings	Abyssia側装備	-	-	+5%
abyssal_flippers	深海素材	-	-	+35%
deep_diver_helmet	深海素材	常時水中呼吸	弱い暗視	-
pressure_diver_helmet	上位深海装備	上位性能	上位性能	-

性能階層は、

バニラ装備
  ↓
簡易潜水装備 ← D01
  ↓
既存Abyssia潜水装備
  ↓
深海潜水装備
  ↓
上位深海装備

とする。

17. 深海層での位置付け

Abyssiaの深海層:

通常ワールド
Y -64
│
│ 海洋
│
│ 浅海
│
├── 深海入口
│
│   ← D01で到達可能
│
│ 深海
│
│   ← 長時間探索には不向き
│
Y -368

水圧ダメージは現在未実装のため、D01では水圧耐性を実装しない。

将来的に水圧ダメージを追加する場合も、

簡易潜水装備は「水圧無効」にはしない。

必要であれば、

深度による追加ダメージ

深海装備による軽減

圧力耐性装備

へ発展させる。

18. 実装上の注意

各ArmorItemサブクラスでは、

onInventoryTick()

を利用して装備状態を判定する。

特に水中呼吸補助は、

装備確認
↓
水中確認
↓
クールダウン確認
↓
Air Supply確認
↓
補助空気供給
↓
耐久値消費

という順番にする。

onArmorTick は新規実装の中心にしない。Forge 1.20.1では非推奨扱いで、onInventoryTick が利用可能。

属性補正については ArmorItem#getDefaultAttributeModifiers を既存実装と同じ方針で利用する。

19. 実装完了判定

以下をすべて満たした時点でD01完了とする。

4アイテム登録

4アイテムの英語名・日本語名登録

4アイテムの16×16テクスチャ

防具レイヤー実装

ヘルムの空気補助

タンクの空気補助

レギンス泳速+3%

フィン泳速+15%

4点セット判定

4点セット泳速+5%

4点セット水中採掘速度+5%

作業台レシピ4種

JEIで全レシピ表示

深海装備との性能逆転がない

新規装備だけで深海入口まで到達可能

水中呼吸が無限になっていない

暗視が深海ヘルム相当以上になっていない

Abyssia素材をレシピに使用していない

専用素材なしでゲーム序盤から製作可能

20. デザインコンセプト

D01の装備は、

「深海に行くために最初に作る、あり合わせの潜水装備」

とする。

完成された深海装備ではなく、

鉄・革・ガラス・ケルプから作った簡易的な潜水ギア

という見た目・性能・レシピの三方向から一貫させる。

これによりプレイヤーには、

「これなら深海に行けそう」
        ↓
「でも酸素がきつい」
        ↓
「もっと本格的な装備が必要だ」
        ↓
「深海合金装備を作ろう」

という自然な装備更新の動機を与える。

特に重要なのは、「入門装備で水中呼吸無限」をやらないことです。そこをやると deep_diver_helmet の価値が一気に落ちます。今回の「一定間隔で少量の空気を補助する」方式なら、実装も既存の onInventoryTick 方針に乗せやすく、性能差もかなり明確になります。