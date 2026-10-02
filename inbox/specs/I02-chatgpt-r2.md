A: 検査
合格
GUI：指定したガンメタル＋シアン発光の配色、凹みタイトルバー、区切り、スロット、エネルギーバーが意図どおり。文字も十分なコントラスト。
通常ケーブル：暗い被覆＋細いシアン芯線になっており、「太い発光棒」ではなく配線として見える。
強化ケーブル：通常より太い金属被覆＋金属バンドが確認でき、幹線らしい見た目になっている。
機械・GUI・ケーブルを並べた際の深海工業テーマの統一感も問題なし。
B: I02 修正版
設計方針
1処理単位 = クラスト1個相当に統一。
レア金属は基本 8%、イットリウムは 2%。採掘時の2% / 0.5%に対して約4倍。
入力は クラスト1個 または 対応精鉱3個。後者はクラスト1個相当として同じ結果。
水は独立入力にせず、浸出試薬のクラフト材料として水入りバケツを使用。
機械自体は水中または水隣接で+10%速度。
使用済み浸出液・海洋樹脂補助剤は削除。GUIをシンプルにする。
出力は 主産物1枠＋レア/副産物2枠程度。
処理表
入力	主産物	レア金属	試薬	時間 / FE
コバルトクラスト ×1	コバルト精鉱 ×3	白金原石 8%、テルル原石 8%、イットリウム原石 2%	浸出試薬 ×1	60 tick / 6,000 FE
コバルト精鉱 ×3	コバルト精鉱 ×3	同上	浸出試薬 ×1	40 tick / 4,000 FE
マンガンクラスト ×1	マンガン精鉱 ×3	モリブデン原石 8%、バナジウム原石 8%、イットリウム原石 2%	×1	60 / 6,000
マンガン精鉱 ×3	マンガン精鉱 ×3	同上	×1	40 / 4,000
ニッケルクラスト ×1	ニッケル精鉱 ×3	タングステン原石 8%、イットリウム原石 2%	×1	60 / 6,000
ニッケル精鉱 ×3	ニッケル精鉱 ×3	同上	×1	40 / 4,000
鉄クラスト ×1	鉄粉 ×3	イットリウム原石 2%	×1	60 / 6,000
銅クラスト ×1	銅インゴット ×2	イットリウム原石 2%	×1	60 / 6,000
水隣接時：処理時間 ×0.9
※レア判定は各処理単位につき独立。幸運はI02の処理結果には適用しない。
浸出試薬
硫黄 ×2
熱試薬 ×1
水入りバケツ ×1
↓
酸性浸出試薬 ×4
水入りバケツを試薬製造時に消費する扱いにすると、I02側に水専用スロットを追加せず済みます。
海洋樹脂について
削除でよいです。
I02の目的は「レア金属を工業的に回収すること」なので、
入力 → 試薬 → 主産物 + レア金属
に絞ったほうがGUIもレシピもかなり分かりやすいです。
I02 仕様書
# I02 — 選択浸出分離機
tier:
  I02
files:
  block:
    selective_leaching_separator
  textures:
    selective_leaching_separator_front
    selective_leaching_separator_front_on
    selective_leaching_separator_side
    selective_leaching_separator_top
    machine_bottom
  gui:
    selective_leaching_separator
  items:
    acidic_leaching_reagent
goal:
  クラストまたはクラスト由来の精鉱を湿式処理し、
  採掘時より高い確率でレア金属原石を回収する。
concept:
  深海クラストの浸出・選択分離をモデルにした
  レアメタル回収設備。
inputs:
  crust:
    cobalt_crust
    manganese_crust
    nickel_crust
    iron_crust
    copper_crust
  concentrate:
    cobalt_concentrate x3
    manganese_concentrate x3
    nickel_concentrate x3
  reagent:
    acidic_leaching_reagent x1
outputs:
  cobalt_crust:
    main: cobalt_concentrate x3
    platinum_raw: 8%
    tellurium_raw: 8%
    yttrium_raw: 2%
  manganese_crust:
    main: manganese_concentrate x3
    molybdenum_raw: 8%
    vanadium_raw: 8%
    yttrium_raw: 2%
  nickel_crust:
    main: nickel_concentrate x3
    tungsten_raw: 8%
    yttrium_raw: 2%
  iron_crust:
    main: iron_powder x3
    yttrium_raw: 2%
  copper_crust:
    main: copper_ingot x2
    yttrium_raw: 2%
processing:
  crust:
    time: 60 ticks
    energy: 6000 FE
  concentrate:
    time: 40 ticks
    energy: 4000 FE
conditions:
  - 水中設置可能
  - 陸上設置可能
  - 水中または水隣接時、処理速度 +10%
  - 熱水噴出孔は不要
  - 独立した水入力スロットは持たない
reagent_recipe:
  sulfur x2
  thermal_reagent x1
  water_bucket x1
  ->
  acidic_leaching_reagent x4
gui:
  layout:
    - input: 1
    - reagent: 1
    - main_output: 1
    - rare_output: 2
    - energy_bar
    - progress
  follow_I01_layout: true
visual:
  - I01と同じ共通筐体
  - 立方体ブロック
  - 6面テクスチャ
  - 正面中央に円形の浸出・分離機構
  - 側面に短い配管
  - 上面に薬液投入口
  - アクセントカラーは青紫
  - 稼働時は中央機構をシアン～青紫で発光
  - machine_bottomを共用
constraints:
  - レア金属の処理単位はクラスト1個相当
  - 採掘時の基本ドロップ率より明確に高い
  - 採掘時のクラスト本体ドロップを置き換えない
  - 幸運をI02のレア抽選に適用しない
  - 使用済み浸出液を生成しない
  - 海洋樹脂の専用補助剤スロットを設けない
  - 鉄精鉱・銅精鉱を新設しない
  - 他Modへの直接依存なし
accept:
  - クラスト1個を処理できる
  - 対応精鉱3個をクラスト1個相当として処理できる
  - 主産物が既存のI01素材フローと一致する
  - レア金属が採掘より約4倍の基本確率で得られる
  - 試薬に水を含められる
  - 水中・水隣接で正常動作する
  - FE消費が正しく行われる
  - I01と同じGUIレイアウトで動作する
  - チャンク再ロード後も処理状態が維持される
  - I01機械群と外観が統一される
この修正なら、I01＝素材を精鉱化する一次処理、I02＝そこからレア成分を拾い上げる二次処理という役割分担がかなり明確になります。