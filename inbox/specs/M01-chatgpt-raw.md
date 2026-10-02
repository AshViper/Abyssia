了解です。今回は**「デザイン画(2)の方向性」＝チョウチンアンコウ＋光る提灯を主役にした、素朴なMinecraft風ドット絵**として固定します。
特に、生成AIっぽく見えやすい「細密描写・光のグラデーション・装飾的な背景・ロゴ」を排除し、32×32の手打ちピクセル絵として扱える仕様にしています。

(1) 仕様書
Markdown
# Abyssia MODアイコン 仕様書

## コンセプト

Abyssiaの世界観を一目で伝える、32×32ピクセルのMinecraft風MODアイコン。

深海を代表する生物である「チョウチンアンコウ」と、その頭部から伸びる「光る提灯」を
唯一の主役モチーフとして使用する。

細密なイラストではなく、人が1ピクセルずつ手で描いたような素朴なドット絵を目指す。

---

## モチーフ

- チョウチンアンコウ
- 頭部から伸びる光る提灯
- 背景は暗い深海
- 泡・岩などは最低限の補助要素として使用可能
- サメ、クラゲ、ウナギ、基地、熱水噴出孔などの別モチーフは入れない
- 「Abyssia」の文字やロゴは入れない

---

## 構図

- 32×32ピクセルの正方形
- チョウチンアンコウを画面中央〜左側に大きく配置
- 魚は右向き
- 大きく口を開けたシルエット
- 頭部から伸びる提灯を右上へ配置
- 提灯はアイコン内で最も明るい部分にする
- 魚本体はアイコンの約55〜70%を占める
- 背景は暗い深海色
- 泡や岩は魚のシルエットを邪魔しない範囲で少量だけ配置

---

## 配色

使用色は12色以下。

基本色：

- 非常に暗い紺：深海背景・輪郭
- 暗い青：背景
- 青緑：水中アクセント
- 濃紺〜紫：魚の本体
- 中間の紫青：魚の明部
- 淡い青灰：魚のハイライト
- クリーム色：歯・目
- 茶紫：提灯の柄
- オレンジ：提灯の外側
- 黄色：提灯の中心

---

## ドット絵ルール

- サイズ：32×32
- 正方形
- 色数：12色以下
- グラデーション禁止
- アンチエイリアス禁止
- 半透明禁止
- ベタ塗りを基本とする
- 色の切り替えは明確にする
- 1pxの暗色アウトラインを基本とする
- 細い線は1px単位で描く
- ディザリングは禁止
- 例外的にディザを使用する場合も、手打ちの2×2市松1段まで
- 光はぼかさず、段階的なピクセル配置で表現する
- 発光部分の周囲に半透明の光彩を作らない
- 小さなピクセルの乱雑な追加をしない

---

## 手描き感のルール

- 左右完全対称にしない
- 魚体の輪郭を機械的な曲線にしない
- 1〜3px単位の自然な凹凸を残す
- 明暗の境界をはっきりさせる
- 細密な鱗・皮膚・筋肉表現は入れない
- 写実的な陰影を入れない
- ピクセル単位で「人間が意図して置いた」ような形を優先する
- 同じ色をランダムに散らさない
- Minecraftの既存アイテムテクスチャと並べても違和感がない程度の情報量にする

---

## 提灯の表現

提灯はAbyssiaアイコンの視認性を高める重要なアクセント。

- 暗色の柄を1px単位で描く
- 外側はオレンジ
- 中央は黄色〜淡黄色
- 最も明るい色は数ピクセルだけ使用
- グラデーションではなく、色面の段差で発光を表現
- 光源から周囲へぼかした光を描かない

---

## 禁止事項

- MOD名・ロゴ文字
- 「Abyssia」の文字
- 英数字
- ポスター風構図
- 写実的なイラスト
- 高密度な背景
- 複数の生物
- 基地や建築物
- 熱水噴出孔
- 大量の泡
- レンズフレア
- ブラー
- 光のグラデーション
- 3Dレンダリング風表現
- AI生成イラスト特有の細密なディテール

---

## 最終判断基準

Minecraftのアイテムテクスチャ群の中に置いたとき、

「プロのイラスト」ではなく
「MinecraftのMOD作者が32×32ドットを自分で打ったアイコン」

に見えることを最優先とする。
(3) 実装用32×32ドット絵データ

以下を正データとして扱えます。
パレットは12色以内、rows は32行 × 各32文字です。

JSON
{
  "palette": {
    "A": "#0a0f1e",
    "B": "#12344a",
    "C": "#26304a",
    "D": "#3b3f5a",
    "E": "#59607a",
    "F": "#8e9ab0",
    "G": "#e8d9a5",
    "H": "#6b4b5b",
    "I": "#f29c38",
    "J": "#fff3b0",
    "K": "#2d6f7f",
    "L": "#151923"
  },
  "rows": [
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    "AAAAAAAAAAAAAAAAAAAAAAAAAAABBBBA",
    "ABBBBAAAAAAAAAAAAAAAAAAAAAABBBBA",
    "ABBBBAAAAAAAAAAAAAAAAAALLLABBBBA",
    "ABBBBAAAAAAAAAAAAAAAAALHHHLBBBBA",
    "ABBBBAAAAAAAAAAAAAAAAHHHHIJIAAAA",
    "AAAAAAAKKAAAAAAAAAAAAHHHIJJJIAAA",
    "AAAAAAAKKDAAAAAAAAAAHHHLIJJJIAAA",
    "AAAAAAAAADDDLAAAAALHHLAAIJJJIKKA",
    "AAAAAAAALLDDDLLLLAHLAAAAAIIIAKKA",
    "AAAAAAALLLDDDDDLLLAAAAAAAKKAAAAA",
    "AAACALLLLCDCDCCCCCLLAAAAAKKAAAAA",
    "ACCCCLLCCDDDDDDDCCCLLAAAAAAAAAAA",
    "AACCCCCCDDDDEEEEDJJCLLAAAAAAAAAA",
    "AACCCCCDDDEEEEEEEJJDCLLAAAAAAAAA",
    "AAACCCCDDDEEEEEEEAAAAALLLAAAAAAA",
    "AAALCCCDDEEFEEEEEAAAGAACLLAAAAAA",
    "AALLCCDDDDEFFEEEEEAAGAGAALKKKAAA",
    "AALLCCCDDDDFFEEEEEAAAAGAALLKKAAA",
    "AAALLCCDDDDFFEEEEEEAAAAGAALAAAAA",
    "AAALLLCDDDDDDDDDEDDAAAAGAALAAAAA",
    "AAAALLLDDDDDDDDDDDAAAAAAALLAAAAA",
    "AAAAALLCCCDEEEEEDDGAAAGAALAAAAAA",
    "AABBBLLLCCCEEEEEDAGAGAGALLAAAAAA",
    "AABBBBLCCCCEEEEECCCAGALLLAABBBBA",
    "AABBBCCCCCLCCCCCCCCCALLAAAABBBBA",
    "AABBBCCCCLLLLLLCLLLLLLAAAAABBBBA",
    "AAALACCLAALLLLLLLLLLAAAAALABBBBL",
    "AAKLLCKLLAAKLLLKLLLLKAAAKLLBBKLL",
    "LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL",
    "LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL"
  ]
}

この形式なら、JSONを読み込んでそのまま32×32 PNGへ変換する処理にも使えます。
また、今回は画像そのものではなく**このドット配列を実装上の正（source of truth）**にする、という扱いで問題ありません。