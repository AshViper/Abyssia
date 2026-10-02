H04 大型深海ロッカー

tier: H04 / 拠点設備
goal: 深海拠点内で大量のアイテムを保管できる大型収納を追加する。

constraints

habitat_* と同じダークガンメタル＋ティール系。

マルチブロックだが、内部インベントリは1つ。

ホッパー等のバニラ搬出入に対応。

破壊時は中身をドロップする。

破壊途中でアイテムが消失しない。

拠点壁・床に自然に設置できる。

仕様
項目	内容
ID	large_locker
サイズ	2×1×2ブロック（幅×奥行×高さ）
容量	54スロット
設置	床置き。プレイヤーの水平向きに合わせる
マルチブロック	4ブロックを1つの収納として扱う
GUI	54スロット＋プレイヤーインベントリ
開閉	上段の扉が開く簡易アニメーション
音	チェスト系の開閉音＋金属音
ホッパー	上：搬入、下：搬出、側面：搬入/搬出
破壊	4ブロック全体を破壊し、中身をドロップ
シルクタッチ	不要。収納本体をそのままドロップ
水密性	設置場所が拠点内であることを想定
見た目

各ブロックを parts ベースで生成。

左右の大型扉

扉中央の縦シール

上下の補強梁

ティール帯

小型インジケータ

扉ごとの取っ手

四隅のボルト

上部に淡いシアンの状態ランプ

4ブロックが一体の大型ロッカーに見えることを優先する。

レシピ

作業台：

iron_plate      iron_plate      iron_plate
corrosion_alloy_ingot machine_frame corrosion_alloy_ingot
iron_plate      iron_plate      iron_plate

→ large_locker

H05 壁掛け深海作業台

tier: H05 / 拠点設備
goal: 拠点の壁面に設置できる省スペースな3×3クラフト設備を追加する。

constraints

バニラ作業台と同じ3×3クラフトを基本機能とする。

通常の作業台レシピをそのまま使用。

床を占有しない。

habitat_* の壁面に馴染むデザイン。

壁が破壊された場合は作業台も破壊する。

作業途中のクラフト材料は通常のクラフトテーブルと同様に扱う。

仕様
項目	内容
ID	wall_workbench
サイズ	1×1×1
実体	壁から約6～8px突出
GUI	バニラ作業台と同じ3×3
設置	壁面に向かって右クリック
向き	設置した壁面を背面とする
支持	背面ブロックが必須
壁破壊時	作業台も破壊
ドロップ	本体＋作業途中のアイテム
ホッパー	H05では不要
追加機能：工具充電ドック

H05に1スロットの工具充電スロットを追加。

electric_drill

electric_cutter

のみ投入可能。

拠点のFEケーブル網に接続されている場合のみ充電。

充電速度：2,000 FE/t

GUI右側にFEゲージを表示。

これによりH05が、

クラフト＋電動工具の整備ステーション

になる。

見た目

parts で以下を構成。

壁固定用背面プレート

左右の支持アーム

中央クラフト面

小型作業面

ティール帯

シアンの電源ランプ

右側の工具充電スロット

小型ケーブル接続端子

突出量は大きくしすぎず、壁掛け端末/整備台に見える形。

レシピ
iron_plate          iron_plate          iron_plate
industrial_panel    machine_frame       industrial_panel
conductive_alloy_ingot copper            conductive_alloy_ingot

→ wall_workbench

I03 電動深海ツール

tier: I03 / 電動工業ツール
goal: 既存 abyssal_drill / abyssal_cutter とは別に、FEで動作する電動版を追加する。

方針

既存ツールは変更せず、新規アイテムとして実装する。

既存：

abyssal_drill

abyssal_cutter

↓

新規：

electric_abyssal_drill

electric_abyssal_cutter

これにより既存セーブ・既存バランスへの影響を避ける。

I03-A 電動ドリル
基本仕様
項目	値
ID	electric_abyssal_drill
FE容量	100,000 FE
1ブロック消費	500 FE
耐久	2,200相当
通常速度	12
修理	通常の耐久修理は不可
充電	H05工具充電ドック

FEを使用している間は耐久を消費しない。

FEが0の場合：

採掘不可

耐久を代替消費しない

「Energy depleted」を表示

特殊能力：3×3採掘

Shift を押しながら使用すると3×3モード。

プレイヤーが向いている面を基準

3×3×1を対象

採掘可能ブロックだけ破壊

各ブロック500 FE

最大4,500 FE/回

採掘できないブロックは無視

ブラックリストのブロックや保護ブロックは通常の採掘ルールに従う。

I03-B 電動カッター
基本仕様
項目	値
ID	electric_abyssal_cutter
FE容量	75,000 FE
1回使用	250 FE
耐久	1,500相当
充電	H05工具充電ドック

FE使用中は耐久を消費しない。

特殊能力

通常：

剣として使用可能

攻撃時にFEを消費

Shift 使用時：

精密切断モード

草・葉・蔓・海藻・珊瑚系などの植物ブロックを高速破壊

羊毛・クモの巣などの「切断可能」ブロックにも高速対応

それ以外は通常採掘速度

戦闘性能を過度に強化せず、**「切るための工具」**として差別化する。

エネルギー表示

両方とも：

Energy
████████░░ 82%
82,000 / 100,000 FE

をツールチップに表示。

さらにMinecraft標準の耐久バー部分をFE残量バーとして利用する。

満充電 → 最大

空 → 0

耐久値ではなくFE残量を表す

レシピ：電動ドリル
tungsten_tip       conductive_component tungsten_tip
abyssal_alloy_ingot machine_frame       abyssal_alloy_ingot
iron_plate         thermal_component     iron_plate
レシピ：電動カッター
abyssal_alloy_ingot conductive_component abyssal_alloy_ingot
thermal_component   machine_frame        thermal_component
iron_plate          iron_plate           iron_plate
H06 深海拠点建設装置・3Dモデル

tier: H06 / H02拡張
goal: habitat_constructor の平面アイコンを、ゲーム内で立体的な深海建設ツールとして表示する。

constraints

機能・IDは変更しない。

habitat_constructor

16×16アイテムテクスチャを基本にする。

parts の from/to 0～16座標でモデルを定義。

GUI/一人称/三人称で視認性を確保。

H01～H05と同じ工業・深海デザイン。

モデル構造

座標系：

X = 0～16
Y = 0～16
Z = 0～16
parts
part	from	to	用途
body	(3,3,2)	(13,13,11)	本体
grip	(5,8,10)	(11,16,14)	グリップ
front_housing	(2,5,3)	(14,11,8)	前部装置
projector	(4,6,0)	(12,10,3)	投影レンズ
top_panel	(5,2,3)	(11,4,9)	上部パネル
side_panel	(12,5,4)	(14,11,10)	表示部
teal_band	(3,6,8)	(13,10,9)	ティール帯

※実装時はこの寸法を基準に、16×16アイコン上でシルエットが明確になるよう微調整可。

テクスチャ
body

habitat_constructor_body

ダークガンメタル

#14181e / #343e4a / #6c7c8a

grip

habitat_constructor_grip

暗色グリップ

黒～濃灰

projector

habitat_constructor_projector

シアン発光

#3ee6f0

#b8f8ff

teal_band

habitat_constructor_teal

#1d6670

#2a9aa0

display

habitat_constructor_display

暗青ガラス

#13506a

小さなシアン表示

H06 表示設定
一人称

最重要。

斜め45°程度

画面中央～右下

グリップを自然に握っているように見せる

投影レンズを前方へ向ける

「銃」ではなく「建設用ハンディ端末」に見えることを優先。

三人称

プレイヤーの手に自然に収まるサイズ

本体がプレイヤーに隠れすぎない

シアン投影部が見える

GUI

Minecraftアイテム表示に適した3/4視点

既存の16×16アイコンと同程度の占有率

立体モデルによって平面アイコンとの差を明確にする

Ground / Dropped

床に落とした場合も、

工具本体＋グリップ＋発光レンズ

が認識できる角度にする。

必要テクスチャ一覧
H04

large_locker_front

large_locker_side

large_locker_top

large_locker_bottom

large_locker_handle

large_locker_indicator

H05

wall_workbench_back

wall_workbench_frame

wall_workbench_surface

wall_workbench_teal

wall_workbench_display

wall_workbench_connector

I03

electric_abyssal_drill_body

electric_abyssal_drill_grip

electric_abyssal_drill_tip

electric_abyssal_drill_indicator

electric_abyssal_cutter_body

electric_abyssal_cutter_grip

electric_abyssal_cutter_blade

electric_abyssal_cutter_indicator

H06

habitat_constructor_body

habitat_constructor_grip

habitat_constructor_projector

habitat_constructor_teal

habitat_constructor_display

デザイン共通方針

全25枚は、既存の

ダークガンメタル → 青灰鋼板 → ティール帯 → 暗青ガラス → シアン発光

の順で統一する。

特にH04/H05は**「工業機械」ではなく「深海基地の設備」、I03/H06は「同じ基地技術から作られた携帯工具」**として見えるようにする。