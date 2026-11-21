# 大海蛇（abyss_serpent）の GeckoLib モデル生成

ParaInfaction の多体節ボス（`tools/serpent_model`）と同じ「Python で GeckoLib モデルを生成する」流れを、
Abyssia 用に移したもの。geo / animation / テクスチャ / 約束ファイルを丸ごと書き出す。
**出力は手で直さない**（ここを直して流し直す）。

## 流す順番

```bash
python tools/boss/serpent_model/build.py              # 1. geo / animation / texture / contract.json
python tools/boss/check_animations.py abyss_serpent   # 2. ボーン名・キーの逆順・尺超えが無いか
python tools/boss/serpent_model/preview.py            # 3. 六面図を out/ へ（目で確かめる）
```

- 3 は一部だけも描ける: `preview.py head`（頭の拡大）/ `whole`（全身）/ `tail`（尾）/ `open`（顎と鰓を開いた頭）/ `bent`（S 字に曲げた全身と首）
- 曲げた図は、Java の描画と同じ置き方（各 root Bone を関節の中点へ運び、局所 -Z を前の関節へ向ける）で作る。
  袖の重なりで継ぎ目が隠れているかはここで見る
- 戦闘側（攻撃起点 anchors・部位切り分け split・hitboxes・hurt/death/spawn の追記）は今回の対象外

## 中身

| ファイル | 役割 |
| --- | --- |
| `build.py` | 骨格・外皮・アニメーション・約束ファイル。冒頭の寸法と表を変えれば形が変わる |
| `atlas.py` | 256x256 のテクスチャアトラス（皮膚と発光）。色は `tools/boss_textures.py` の serpent() を引き継ぐ |
| `preview.py` | 実テクスチャを貼った六面図（`out/`） |
| `contract.json` | 描画の Java 側との約束（build.py が書く） |
| `../boss_kit.py` `../boss_views.py` `../check_animations.py` | ParaInfaction から複写した共通の道具 |

書き出し先:

- `src/main/resources/assets/abyssia/geo/entity/abyss_serpent.geo.json`
- `src/main/resources/assets/abyssia/animations/entity/abyss_serpent.animation.json`
- `src/main/resources/assets/abyssia/textures/entity/boss/abyss_serpent_geo.png` / `abyss_serpent_geo_glow.png`
  （旧描画が使う `textures/entity/abyss_serpent.png` は触らない）

## 寸法と MODEL_SCALE

**MODEL_SCALE = 4**。モデルは実寸の 1/4 で作り、描画側で一様に 4 倍する。geo の 1 単位はゲーム内で
4/16 = 0.25 ブロック、テクスチャ 1 画素も 0.25 ブロック。体の板1枚が 60x20 単位ほどで、
256 のアトラスに引き伸ばし無しで収まる倍率にした（2 だと面が領域より大きくなり引き伸ばされ、
8 だと 1 画素が 0.5 ブロックで粗い）。

| 量 | 実寸（ブロック） | モデル（単位） | 出典 |
| --- | --- | --- | --- |
| 関節間隔＝体節の長さ | 14 | 56 | AbyssSerpent（頭＋28 関節） |
| 首の半径 | 9 | 36 | AbyssSerpent.bodyRadius() |
| 断面 | 幅 0.88r・背 1.08r・腹 0.95r | 同じ | AbyssSerpentRenderer の tube |
| 尾への細り | bodyScale(k) | 同じ式 | AbyssSerpentRenderer.bodyScale |
| 頭 | 幅約 23・長さ約 32（襟を除く） | 幅 94・鼻先 z=-129 | 同 SKULL / JAW_LOFT を 4 倍・前後反転 |
| 全長 | 約 392＋頭 | 1568＋頭 | |

## 骨の約束（Java の描画）

- `head` と `seg_01`〜`seg_28` は **root 直下**。静止姿勢は一直線で、関節 j は (0, 0, 56j)、体の芯は y=0。
- `head` の pivot は関節 0（原点）。鼻先は -Z。子: `jaw`（pivot は蝶番 (0,-6,12)）→ `teeth_lower`、
  `teeth_upper`、`horns`、`gill_l` / `gill_r`、`fin_l1` / `fin_r1` / `fin_l2` / `fin_r2`
- `seg_k` は関節 k-1（前）〜k（後ろ）、pivot は中心 (0, 0, 56(k-0.5))。長さ方向は -Z が前
- `seg_02_fin`〜`seg_24_fin` は背びれ（pivot は付け根）、`seg_25_fin`〜`seg_28_fin` は尾びれ（pivot は体節前端の芯）
- Java は毎フレーム root の Bone の位置と回転を上書きする。子は animation（idle）に任せる。
  GeckoLib の符号（setPos は geo 単位、setRot は描画空間で (-rx, -ry, +rz)、X が反転）は contract.json の
  `geckolib_conventions` に書いた

## 形の持ち方

| 部位 | 作り方 |
| --- | --- |
| 体節 | 超楕円（指数 2.3）の断面を 12 角（尾は 8 角）で `ring_loft`。前端は 0.88 倍に細い差し込み、後端は 1.02→1.055 倍の袖で関節を越える |
| 継ぎ目 | 袖が次の体節の差し込みへ前後に重なる（長さは半径の 0.3 ずつ）。23 度曲げても隙間の奥は次の皮しか見えない（`bent_neck`） |
| 頭骨 | 指数 2.6 の角張った断面を 10 枚の板で 9 区間。後ろ端は首へ被さる襟。眉の稜・頭頂の稜は骨板の棒 |
| 下顎 | 指数 2.4 の断面を 8 枚で 9 区間。口縁は上顎より内側（上の牙が外へ垂れる） |
| 口の中 | 口蓋と顎の底の板、舌、喉の奥の闇。顎を開くと見える（`open`） |
| 角・牙・髭棘 | 2 次ベジェを 5 本（牙は 2 本）の菱形の棒に分け、先ほど細く |
| 鰭 | `Kit.fan`（鰭条の扇と膜）。尾びれは扇のあいだを膜の板で埋めて1枚の葉形にする |
| 鰓 | 光る鰓裂 3 本と鰓弓。idle で外へ開く（呼吸） |

## テクスチャの約束

- 体の板は左右で u が鏡写しになる（板を回転だけで置くため）。横向きに意味のある絵は描かない
- 絵の上端は面に沿って上る向き。眼・牙・角・鰭条・腹板・袖は領域まるごと貼る（`STRETCHED`）
- 発光は `_glow.png`。光らない画素は (0,0,0,0)。RenderType.eyes でも GeckoLib の自動発光層でも使える
- 生成は座標のハッシュだけで、何度流しても同じ PNG

## cube の数

上限 1500。いまは 1328（頭 373 / 体 955、体節あたり約 31）。`build.py` の最後で数え、超えると止まる。
