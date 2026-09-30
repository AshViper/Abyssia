# BBModel Generator

実在の深海生物を Minecraft 向けにデフォルメし、**Blockbench でそのまま開ける `.bbmodel`**（Bone / Cube / Box UV / Texture / Glow / Animation 付き）と、
Forge 1.20.1 用の Java（`LayerDefinition` / `AnimationDefinition`）を生成するローカルツール。

```
Creature Definition → Semantic Parts → Template → Geometry(Bone/Cube) → UV → Texture(+Glow) → Animation → Validation → BBModel / Java
```

基本は生物ごとの **Definition(JSON)** を汎用 **Template** に渡して生成します。写真から作る場合は **Import Image**（下記）で写真 → `photo` Template の Definition を作ります。各段の中間データはすべて JSON なので、
GUI の Parts / Outliner / JSON タブでそのまま確認できます。

## 起動

```bash
cd tools/bbmodel-generator
npm install            # three.js（プレビュー用）のみ
node server.js         # → http://127.0.0.1:5178
```

CLI（GUI なしで一括生成）:

```bash
node cli.js anglerfish                  # output/Anglerfish/ に書き出し
node cli.js all --preset low_detail     # 全生物
node cli.js anglerfish --seed 7 --variation 1 --zip
node cli.js --help
```

Validation でエラーがあるモデルは書き出しません（`--force` で強制）。テスト: `npm test`（全生物 × 全プリセット × 各設定を検証）。

## 画面

| 場所 | 内容 |
|---|---|
| 左 | Creature / Creature Type(Template) / Preset、Random Variation（Seed・A/B/C・量）、**Definition Editor**（Body/Head/Mouth/Eye/Tail・Fin/Leg/Tentacle/Glow/Palette/Animation/Parts）、Save Definition・Edit JSON・New Creature |
| 中央 | 3D Preview（回転・ズーム・パン、Texture/Glow/Wireframe/Bones/Pivots/Grid/Dark water、Blockbench / Minecraft ライティング切替、視点）、アニメ再生タイムライン、下部タブ（Texture+UV / Outliner / Parts / Animations / Validation / JSON） |
| 右 | Model Settings: Width/Height/Length、Pixel Density、Block Count（最大Cube数）、Detail Level(LOD)、Texture Size、UV Mode、形式、Animation Ready / Generate Texture / Generate UV / Generate Animation / Glow Layer、Generate Model / Generate Texture / Generate BBModel / Export ZIP / Save to output/ |

パラメータを変えるとプレビューは即時更新されます（F5 再生成、Space 再生、W ワイヤ、B ボーン）。
プレビューのシェーディングは Blockbench の式（上1.0/南北0.8/東西0.6/下0.5）と Minecraft のエンティティライトを再現しています。

## 出力

```
output/<Name>/
├─ <id>.bbmodel          Blockbench プロジェクト（既定 format 5.0 / Modded Entity）
├─ <id>.png              ベーステクスチャ
├─ <id>_glow.png         発光マスク（同じ UV、非発光部は透明）
├─ <id>.json             生成設定・Bone 一覧・アニメ一覧・サイズ・定義
├─ <Id>Geometry.java     Forge 1.20.1: Bone 名定数 + create() → LayerDefinition
└─ <Id>Animations.java   Forge 1.20.1: AnimationDefinition 定数（HierarchicalModel#animate 用）
```

- 形式: Modded Entity（既定・依存なし）/ GeckoLib（プラグイン）/ Bedrock / Generic。Blockbench 4.x 用の format 4.10 も出力可。
- Glow テクスチャは Blockbench では `render_mode: layered` で重ねて表示されます（面には割り当てない仕様）。
- Per Face UV は Modded Entity では使えません（Blockbench が Box UV に戻し、Java の ModelPart も Box UV のみ）→ Validation でエラー。
- Java 変換は Blockbench 自身の Modded Entity エクスポータと同じ式（X/Y 反転、回転 Cube は `_rN` サブパート化）。
- アニメ値は Blockbench 5 の規約（静的回転と同じ three.js ZYX）。4.10 出力時のみ X/Y 回転・X 位置を反転。

## 生物（definitions/*.json）

| id | 和名 | Template | 生息域 |
|---|---|---|---|
| anglerfish | チョウチンアンコウ | fish | bathypelagic |
| giant_isopod | ダイオウグソクムシ | arthropod | abyssal_floor |
| gulper_eel | フクロウナギ | eel | bathypelagic |
| viperfish | ホウライエソ | fish | mesopelagic |
| goblin_shark | ミツクリザメ | fish | continental_slope |
| barreleye | デメニギス | fish | mesopelagic |
| frilled_shark | ラブカ | eel | continental_slope |
| giant_squid | ダイオウイカ | squid | bathypelagic |
| yumenamako | ユメナマコ | softbody | abyssal_floor |
| tubeworm | チューブワーム | worm | hydrothermal_vent |
| yunohana_crab | ユノハナガニ | crustacean(crab) | hydrothermal_vent |
| deep_sea_shrimp | シンカイエビ | crustacean(shrimp) | bathypelagic |
| scaly_foot_snail | スケーリーフット | gastropod | hydrothermal_vent |
| goemon_squat_lobster | ゴエモンコシオリエビ | crustacean(crab) | hydrothermal_vent |
| ohara_shrimp | オハラエビ | crustacean(shrimp) | hydrothermal_vent |
| satsuma_tubeworm | サツマハオリムシ | worm | hydrothermal_vent |
| vent_eelpout | ゲンゲ（熱水域） | eel | hydrothermal_vent |
| silky_medusa | ニジクラゲ | medusa | mesopelagic |
| atolla_jelly | ムラサキカムリクラゲ | medusa | bathypelagic |
| helmet_jelly | クロカムリクラゲ | medusa | bathypelagic |
| giant_phantom_jelly | ダイオウクラゲ | medusa | bathypelagic |
| stalked_sea_squirt | 有柄ホヤ（写真から: samples/stalked_ascidian.png） | photo | abyssal_floor |
| stalked_sea_squirt_ai | 有柄ホヤ（同じ写真を AI が部位で組立） | parts | abyssal_floor |

## Template（backend/templates）

| Template | 構成 | 固有アニメ |
|---|---|---|
| fish | 頭・蝶番の顎・歯/牙・眼・誘引突起(illicium+esca)・吻・透明ドーム・胴・尾チェーン・膜ヒレ・発光器列 | mouth_open/close, bite, glow |
| eel | 巨大な顎と袋・細長い体節チェーン（進行波）・フリル状のエラ・リボン状ヒレ・発光尾端 | mouth_open/close, glow |
| squid | 外套膜チェーン・ヒレ・巨大な眼・腕の輪（吸盤面）・触腕と掌部 | tentacle_move, grab |
| arthropod | 丸まれる体節チェーン・複眼・触角・関節脚・尾扇 | walk, curl, uncurl |
| crustacean | crab（甲羅・鋏・脚）/ shrimp（額角・長い触角・曲がる腹部・遊泳脚・尾扇） | walk, swim, flick, claw_snap |
| worm | 岩の土台に群生する管（Seed で配置）・襟・羽毛状の鰓冠（交差面） | sway, retract, extend |
| softbody | ゼラチン質の体節・前方のベール・後方のブリム・触手・管足 | swim |
| parts | 部位リスト（形・大きさ・取付位置・向き・チェーン・リング・左右対）から組み立て。AI が写真を見て書く用 | idle, sway / swim, tentacle_move, pulse |
| photo | 写真のシルエット格子から作る放射対称の積層（行ごとの中心を保持）・首/頭の自動ボーン分割・写真から投影したテクスチャ | sway, contract |
| gastropod | 巻貝（殻の渦チェーン）・鱗で覆われた足・吻と触角 | crawl, retract |
| medusa | 段状の半透明の傘（dome/disc/helmet）・透ける胃・冠溝の区画（groove_1..n、順に光らせる用）・縁弁・触手の輪・長大触手・リボン状の口腕（一枚板） | pulse, swim, spread |

全 Template 共通で idle / hurt / death を生成。アニメは Bone の役割（role）から構造的に作るため、パラメータを変えても破綻しません。

## Preset（presets/*.json）

Minecraft Deep Sea（既定）/ Minecraft Vanilla / Minecraft Creature / Stylized Deep Sea / Low Detail / Medium Detail / High Detail（32px HD テクスチャ）。
色の彩度・明度、ランプ段数と色相シフト、ノイズ・模様の強さ、発光の強さ、部位の誇張率、アニメ振幅、既定の LOD / Pixel Density を持ちます。

**Rich Deep Sea**（`rich_deep_sea`）は、ユーザーの参考画像の雰囲気に合わせたプリセットです。追加のキーはすべて省略時オフで、他のプリセットの見た目は変わりません。
- `texture.noiseScale`: まだら模様の細かさ（既定 0.3、大きいほど細かい）
- `texture.bellyLine` / `bellyLift`: 下側を明るい腹にする境目の高さと明るさ。境目はギザギザ。palette の `belly` か、部位の `belly` があればその色を使う
- `texture.finRays`: ヒレの筋の濃さの倍率
- `geometry.roundSteps` / `roundCorners: false`: parts の丸い形を少ない段で、角落としなしで作る（大きな箱のゴツい形）

## Random Variation

Definition の `variation`（例 `"head": 0.08` = ±8%）の範囲で体・頭・ヒレ・眼・発光器・色相を変えます（最大 ±25% に制限）。
Seed が同じなら常に同じ個体。Amount 0 は基準個体、A/B/C は固定 Seed の個体。

## Validation

Bone/Cube 名の重複・不正文字、UV の範囲外・重なり、Texture 未参照/未使用、空 Bone、不正/遠すぎる Pivot、極端な座標、
アニメの対象 Bone 不存在・NaN・ループ継ぎ目、JSON 妥当性（NaN/UUID/Outliner 参照）、Box UV の端数サイズ、形式と UV モードの組み合わせ。

## parts Template（AI が部位を組む）

写真を見た AI（Claude Code）や人が **部位のリスト** を書くと、形を段差の Cube に分解し、Bone チェーン・UV・テクスチャ・アニメまで生成します。
`photo`（輪郭のトレース）と違い、部位の意味が分かるので魚・クラゲ・触手・ヒレなど体型を選びません。
Claude Code では画像を貼って `/photo-to-model` と頼むと、この仕様に従って Definition を書き、生成とプレビュー比較を繰り返します。

```jsonc
{
  "id": "stalked_sea_squirt_ai", "name": "…", "template": "parts",
  "params": {
    "orientation": "upright",          // upright（sway）| horizontal（swim）
    "scale": 1,                        // 全体倍率（個体差の対象）
    "parts": [
      { "name": "base",  "shape": "cylinder", "size": [6, 6, 6], "taper": 0.85, "color": "#8c7c68", "role": "body" },
      { "name": "stalk", "shape": "cylinder", "parent": "base", "at": "tip", "size": [5, 10, 5], "segments": 2, "color": "#a3b4bf" },
      { "name": "head",  "shape": "profile",  "parent": "stalk", "at": "tip", "size": [13, 12, 13], "profile": [0.74, 1, 1, 0.97, 0.9, 0.76, 0.5] },
      { "name": "tentacle", "parent": "head", "at": "bottom", "shape": "cylinder", "size": [1, 10, 1],
        "count": 8, "arrange": "ring", "radius": 5, "tilt": 10, "segments": 3, "curl": 8 },
      { "name": "eye", "parent": "head", "at": [1, 0.6, 0.2], "shape": "eye", "size": 2, "mirror": true }
    ]
  }
}
```

| キー | 意味 |
|---|---|
| `name` | lower_snake_case。Bone 名になる（複数個は `_1`…、左右は `_right`/`_left` が付く）。名前から role を推定（head/bell/cap→head, tentacle/arm→tentacle, fin/frill→fin, tail, eye, leg, stalk/stem→segment, body/mantle→body） |
| `shape` | `ellipsoid` 楕円体 / `dome` 半球（底が平ら）/ `cylinder`（`taper`: 先端の太さ比）/ `cone`（`tip`）/ `profile`（`profile`: 根元→先端の太さ 0..1 の列）/ `box` / `plane` 厚み0の板（`size` に 0 を1つ、`outline`: fan, fork, round, leaf, frill, triangle, sail, taper, hair…）/ `eye`（`size`: 数値） |
| `size` | `[x, y, z]` モデル単位（16 = 1 ブロック）。x = 幅、y = 高さ、z = 前後の長さ |
| `axis` | 伸びる向き: `y`（上、既定）/ `-y`（下、tentacle の既定）/ `-z`（前）/ `z`（後）/ `x` / `-x`。`center: true` で取付点を中心に置く |
| `parent`, `at` | 親の部位と取付位置: `top` `bottom` `front` `back` `left` `right` `center` `tip`（親の伸びた先端）`base`、または `[fx, fy, fz]`（親の箱の 0..1。x: 左0→右1、y: 下0→上1、z: 前0→後1） |
| `offset`, `rotate` | 取付点からのずれ（単位）/ 静的回転 `[x, y, z]` 度 |
| `segments`, `curl` | 軸方向に Bone チェーンを作る（1..8）。`curl`: 節ごとに曲げる角度 |
| `count` + `arrange` | `ring`（`radius`, `tilt` 外向き角度, `start_angle`）/ `row`（`spacing: [x,y,z]`）で複数配置 |
| `mirror` | 左右対を作る（書いた側の反対側を鏡像で追加） |
| `color`, `material` | `#rrggbb` か palette キー / 既存マテリアル（skin, fin, eye, glow, dome, tube, plume, shell, leg, rock, setae, sucker, membrane…） |
| `glow`, `glow_color` | 発光強度 0..2 と色 |
| `glow_pattern` | 光る場所: `spots` 斑点 / `stripes` 縞 / `edges` 背の縁が途切れ途切れに / `rim` 根元の帯（傘の縁など）。`glow` が必要。省略時は部位全体が光る |
| `color_to` | 根元 → 先端へのグラデーションの終わりの色（`#rrggbb`）。連なった節全体で 1 本のグラデーションになる |
| `belly` | 明るい腹の色（`#rrggbb`）。下側がギザギザの境目でこの色になる（プリセットに関係なく有効） |
| `role`, `detail`, `steps`, `round` | role の明示 / 優先度（1 必須〜3 細部、Block Count で 3 から削る）/ 丸い形の段数 / 角落とし off |

エラーは「`parts[2] "fin": unknown parent "bodyy"`」のように部位名付きで返るので、そのまま直せます。

## 写真から作る（Import Image / photo.js）

横から撮った写真 1 枚から `photo` Template の Definition を作ります（有柄ホヤ・海綿・イソギンチャク・ウミエラ・クラゲなど、
**縦軸まわりにほぼ放射対称**な生物向け。魚のような左右対称の横長体型は未対応）。

```
写真 → 切り抜き（範囲）→ 被写体の抽出 → シルエット格子（1 マス = 1 モデル単位）+ パレット(≤16色) → definitions/<id>.json → 通常の生成
```

- **GUI**: 左パネル **Import Image…** → 画像を開く / ドロップ / **Ctrl+V で貼り付け** → 写真上をドラッグして被写体を囲む（クリックで被写体上の点を指定）
  → Threshold・Height・Colours を調整 → Grid を手で修正（左クリック=選択色で塗る、右クリック/Shift=消す）→ **Create Model** → Save Definition。
- **CLI**（PNG のみ。JPEG 等は GUI）:
  ```bash
  node photo.js samples/stalked_ascidian.png --id stalked_sea_squirt --crop 235,90,195,370 --ground --height 28 --generate
  node photo.js photo.png --id sponge --dry-run      # 格子を表示するだけ
  ```
  `output/_photo/<id>_mask.png` に抽出結果（赤い輪郭）を出すので、ずれていたら `--crop` / `--threshold`（小さいほど多く拾う）を調整。
- 抽出: 範囲の左右端の帯を「背景」とみなし、行ごとに背景色とノイズ量を推定して色差を背景ノイズ単位で判定（暗い水中と砂地の両方を背景として扱える）。
  **Stands on the crop bottom / `--ground`** は、砂と同化した半透明の柄を範囲の下端まで追跡します。
- 形: 行ごとの幅と中心 → 最適分割で Detail の Cube 数に収まるスライスに統合 → 幅×高さ×(幅×Depth/width) の箱（Medium 以上は角を落とした 2 箱）。
  最も大きく太くなる所（柄→頭）で `head` ボーンを分け、その下の柄を 1〜3 ボーンに分割（sway で曲がる）。
- テクスチャ: `photo` マテリアルが各テクセルを格子に投影（前後面=写真そのまま、側面=90°回転、上下面=中心からの距離）。パレットは `p0`〜`pN` として Definition の palette に入るので、Preset の色補正・個体差もそのまま効きます。
- Definition は格子（`params.photo.grid`、文字列の行）を持つだけで元画像は不要。`height` / `width_scale` / `depth_ratio` / `segments` / `round` は Definition Editor から変更できます。

## 新しい生物・Template の追加

1. 既存 Template で表せる生物 → `definitions/<id>.json` を追加（GUI の New Creature → Save Definition でも可）。
2. 新しい体制 → `backend/templates/` に `CreatureTemplate` を継承したクラス（`defaults / editorSchema / semanticParts / build / animationCatalog`）を作り `templates/index.js` に登録。
   部品は `parts.js`（眼・歯列・関節脚・チェーン）と `shared.js`（段差で丸める roundedBox など）を再利用。

## 構成

```
bbmodel-generator/
├─ server.js / cli.js / photo.js
├─ frontend/  components/ editor/ preview/ styles/   GUI（three.js）
├─ backend/   generator/ templates/ creatures/ texture/ uv/ animation/ bbmodel/ image/   生成コア（ブラウザと Node で共通）
├─ definitions/  presets/  output/  tests/
```
