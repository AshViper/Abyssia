# 海溝の大ワーム（trench_wyrm）の GeckoLib モデル生成

大海蛇（`tools/boss/serpent_model`）と同じ「Python で GeckoLib モデルを生成する」流れで、
geo / animation / テクスチャ / 約束ファイルを丸ごと書き出す。**出力は手で直さない**（ここを直して流し直す）。

## 流す順番

```bash
python tools/boss/wyrm_model/build.py                # 1. geo / animation / texture / contract.json
python tools/boss/check_animations.py trench_wyrm    # 2. ボーン名・キーの逆順・尺超えが無いか
python tools/boss/wyrm_model/preview.py              # 3. 六面図を out/ へ（目で確かめる）
```

- 3 は一部だけも描ける: `preview.py head`（頭と首の拡大）/ `whole`（全身）/ `tail`（尾）/ `open`（大顎を全開）/
  `bent`（S 字に曲げた全身・首・折れ角最大の関節 14 のまわり）
- 曲げた図は、Java の描画と同じ置き方（各 root Bone を関節の中点へ運び、局所 -Z を前の関節へ向ける）で作る。
  関節ごとの折れは最大 23 度。袖の重なりで継ぎ目の奥が抜けていないかは `wyrm_bent_mid.png` で見る
- 何度流しても同じ出力（乱数は key の crc32 と座標のハッシュだけ）
- 戦闘側（攻撃起点・部位の切り分け・hurt/death などの animation）は今回の対象外

## 中身

| ファイル | 役割 |
| --- | --- |
| `build.py` | 骨格・外皮・アニメーション・約束ファイル。冒頭の寸法と表を変えれば形が変わる |
| `atlas.py` | 256x256 のテクスチャアトラス（皮膚と発光）。色は `tools/boss_textures.py` の wyrm() を引き継ぐ |
| `preview.py` | 実テクスチャを貼った六面図（`out/`） |
| `contract.json` | 描画の Java 側との約束（build.py が書く） |
| `../boss_kit.py` `../boss_views.py` `../check_animations.py` | 共通の道具（ここからは読むだけ。変えない） |

書き出し先:

- `src/main/resources/assets/abyssia/geo/entity/trench_wyrm.geo.json`
- `src/main/resources/assets/abyssia/animations/entity/trench_wyrm.animation.json`
- `src/main/resources/assets/abyssia/textures/entity/boss/trench_wyrm_geo.png` / `trench_wyrm_geo_glow.png`
  （旧描画が使う `textures/entity/trench_wyrm.png` は触らない）

## 寸法と MODEL_SCALE

**MODEL_SCALE = 3**（= TrenchWyrm.SIZE）。描画側は `withScale(3)` で一様に 3 倍する。geo の 1 単位はゲーム内で
3/16 ブロックで、**体節の長さ = 関節間隔 12 × 16 / 3 = 64 単位**。旧描画の頭（半径 8 で作り r/8 倍）は
1 単位 = 8.25/8 × 16/3 = **5.5 単位**（build.py の `HU`）で写した。

| 量 | 実寸（ブロック） | モデル（単位） | 出典 |
| --- | --- | --- | --- |
| 関節間隔＝体節の長さ | 12 | 64 | TrenchWyrm（頭＋26 関節） |
| 首の半径 | 8.25 | 44 | TrenchWyrm.bodyRadius() = 2.75 * SIZE |
| 断面 | 幅 1.05r・背 0.95r・腹 0.85r（指数 0.95） | 同じ | TrenchWyrmRenderer の tube |
| 尾への細り | bodyScale(k) | 同じ式 | TrenchWyrmRenderer.bodyScale |
| 甲殻の環 | 1 関節に 2 つ | 見える長さ 32 ずつ | 旧描画の「two armour rings per joint」 |
| 襟（頭） | 口縁 r≈7.3、最も太い所 r≈9.5 | 口縁 z=-50 r40、最大 z=-17 r52、首への袖 z=+16 | 旧 COLLAR を HU 倍・前後反転 |
| 大顎 | 長さ約 18、蝶番は口縁の後ろ・芯から 8.3 | 蝶番 (±32.3, ±32.3, -33)、閉じた先 z=-123 | 旧 MANDIBLE を HU 倍 |
| 尾の針 | 関節 26 から 1.9r | 84 | 旧描画の tail needle |
| 全長 | 約 312 + 頭 23 + 針 16 | 1664 + 123 + 84 | |

## 骨の約束（Java の描画）

- `head` と `seg_01`〜`seg_26` は **root 直下**。静止姿勢は一直線で、関節 j は (0, 0, 64j)、体の芯は y=0。
- `head` の pivot は関節 0（原点）。口は -Z。子: `teeth`（喉の 3 重の歯の輪）、`mandible_1`〜`mandible_4`
- `mandible_1` 左上 / `_2` 右上 / `_3` 右下 / `_4` 左下（+X が左）。pivot は蝶番。真下に置いた**正準形**で作り、
  bone の静止姿勢の回転 z（-135 / 135 / 45 / -45）で 4 隅へ回してある。だから**開くのは 4 本とも rotation x の正**
  （geo の度数）。x = 0 が閉じた姿（4 本の先が口の前で揃う）、旧描画の全開は x = 72 度
- `seg_k` は関節 k-1（前）〜k（後ろ）、pivot は中心 (0, 0, 64(k-0.5))。長さ方向は -Z が前
- `seg_XX_bristle_l` / `_r`（seg_01〜24）は剛毛の房 2 つずつ。pivot は房の付け根を結ぶ線上
- Java は毎フレーム root の Bone の位置と回転を上書きする。子は animation（idle）に任せる。
  GeckoLib の符号（setPos は geo 単位、setRot は描画空間で (-rx, -ry, +rz)、X が反転）は contract.json の
  `geckolib_conventions` に書いた（大海蛇と同じ。変えない）
- Java から大顎を開くなら `setRotX(-radians(角))` だけを入れ、`setRotZ` は触らない（静止姿勢の z を保つ）

## 形の持ち方

| 部位 | 作り方 |
| --- | --- |
| 体の環 | 超楕円（指数 2.1）の断面を 14 角（細い尾は 12 角）で張った筒。前端 0.76r の差し込みから後ろ端 1.0r の袖へ太る |
| 継ぎ目 | 袖が継ぎ目を 13 単位越えて次の環へ被さり、次の環は 13 単位前から潜る。環の境ごとに段が付く（節の意匠）。23 度曲げても外側の奥は次の環の皮 |
| 剛毛 | 環ごと左右に 1 房: 肉の房（疣足）＋外下へ開く細い剛毛 2 本。旧描画と同じ付け根（脇の少し下）・長さ 0.75r |
| 背の棘 | 環ごとに 1 本、後ろへ傾く 2 片の棒（菱形に向ける） |
| 襟 | 16 角の 4 区間。後ろ端は首の差し込みへ被さる袖 |
| 喉 | 12 角の漏斗（板は面の外側へ置き、内から見る）。奥ほど赤く光る |
| 歯の輪 | 3 重（16 / 12 / 8 本）。1 列目は 2 片で先細り |
| 大顎 | 2 次ベジェの鉤を 8 角の菱形断面で 4 区間。内側に 2 列 4 本ずつの歯 |
| 襟の眼 | 大顎のあいだ（上・左・下・右）に光る眼（旧描画の ember eyes） |

## テクスチャの約束

- 体の環・襟・喉・大顎の板は領域まるごとを貼る。絵の横は板の長さ方向（左が前・根元）、縦は周方向で
  **左右の板で上下が裏返る**ので、どの絵も上下対称に描く
- 環の板は前の袖の下へ `VIS_U0`（= 26/58）まで潜る。見える所に「継ぎ目 → 明るい前縁の稜 → 溝 → 後ろへ
  暗くなる甲殻 → 段の照り」と並べた。脇腹（RING_L）の継ぎ目は燃えさし色に光る
- 何片かに分けた棒（尾の針・背の棘・1 列目の歯）は、片ごとに縦長の絵の該当する帯を貼り、色が途切れない
- 発光は `_glow.png`。光らない画素は (0,0,0,0)

## cube の数

上限 1500。いまは 1470（頭 340 / 体 1130、体節あたり約 43）、bone 82。`build.py` の最後で数え、超えると止まる。
