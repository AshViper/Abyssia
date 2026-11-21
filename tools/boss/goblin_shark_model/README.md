# 巨大ミツクリザメ（colossal_goblin_shark）の GeckoLib モデル生成

GNT01 の巨大野生生物。ボスと同じ「Python で GeckoLib モデルを生成する」流れ（`tools/boss`）で、
geo / animation / 仮テクスチャ / 約束ファイルを丸ごと書き出す。**出力は手で直さない**（ここを直して流し直す）。
共通の道具（`../boss_kit.py` `../boss_views.py` `../check_animations.py`）は変えずに使う。

## 流す順番

```bash
python tools/boss/goblin_shark_model/build.py                  # 1. geo / animation / texture / contract.json
python tools/boss/check_animations.py colossal_goblin_shark    # 2. ボーン名・キーの逆順・尺超えが無いか
python tools/boss/goblin_shark_model/preview.py                # 3. 図を out/ へ（目で確かめる）
```

- 3 は一部だけも描ける: `whole`（六面図）/ `head`（頭〜seg_04）/ `jaw`（顎を 6 ブロック突き出し下顎を開く）/
  `bent`（S 字の全身と、関節 1〜3 を 16〜18 度折った頭〜seg_06）/ `oblique`（斜め前。まっすぐと顎を突き出したもの）

書き出し先:

- `src/main/resources/assets/abyssia/geo/entity/colossal_goblin_shark.geo.json`
- `src/main/resources/assets/abyssia/animations/entity/colossal_goblin_shark.animation.json`（idle / attack / jaw_out）
- `src/main/resources/assets/abyssia/textures/entity/boss/colossal_goblin_shark_geo.png`（仮。本番は別工程。発光なし）

## 寸法と骨

- MODEL_SCALE 2（geo の 1 単位 = 1/8 ブロック）、アトラス 256。全長 32 ブロック、17 関節・間隔 2 ブロック（SEG 16 単位）
- 吻端は関節 0 の 1.75 ブロック前、尾の先は関節 0 から 30.25。表は吻端からの距離 s で書く（`zs(s)`）
- `head` と `seg_01`〜`seg_16` は root 直下（ボスと同じ規則。関節 j = (0, 0, 16j)、seg_k の pivot = (0, 0, 16(k-0.5))）。
  `head` は吻・眼・頭の前半（関節 1 まで）、seg_01 は箱なし。体節の前端は前の関節を中心とする球面で前の殻へ入る
- 顎: `jaw`（head の子、上顎・歯・口蓋）→ `jaw_lower`（下顎、rotation x 正で開く）・`jaw_sling`（喉の膜の管）。
  pivot はどれも顎関節。突き出し量 f で jaw は position f×(0, -19.2, -44)・rotation x 12f 度、
  jaw_sling は `sling_pose(f)` の rotation x と scale z（静止の長さ 1 ブロック → f=1 で 6.93 倍）
- 鰭: `fin_l/r`（胸、seg_04）、`fin_pelvic_l/r`（腹、seg_07）、`seg_06_fin` / `seg_09_fin`（背 2 基）、
  `seg_10_anal`（臀）、`seg_11_fin`〜`seg_16_fin`（尾びれ下葉の膜）。鰓孔 5 対は seg_04 の箱

## 形の持ち方

| 部位 | 作り方 |
| --- | --- |
| 吻・頭・体 | 楕円断面（BODY 表）の輪を板で結ぶ（`ring_loft`）。吻は薄い剣状、吻の下は顎が収まるよう腹を引っ込める |
| 顎 | U 字の弓を棒で（`band`）、細い釘形の歯（根元と先の 2 本、前ほど長く外へ突き出す）、口蓋・口の床・顎の皮は板 |
| 鰭 | 厚み 0.9 単位の肉質の板。外縁の各辺から重心へ張り、辺と重心の三角形を丸ごと覆う（`fin_panels`） |
| 尾びれ | 上葉は体の芯。下葉の膜は 0.25 ブロックごとの板で、外縁の辺にそろえる（`caudal_strips`）。欠刻と先の葉あり |

## cube の数

上限 1328（既存ボスでいちばん少ない abyss_serpent）。いまは 968（頭 236、うち顎 105 / 体 732）。
`build.py` の最後で数え、超えると止まる。
