# 深淵の母（abyss_mother）の GeckoLib モデル生成

大海蛇（`tools/boss/serpent_model`）と同じ「Python で GeckoLib モデルを生成する」流れで作った巨大クラゲ。
geo / animation / テクスチャ / 約束ファイルを丸ごと書き出す。**出力は手で直さない**（ここを直して流し直す）。

## 流す順番

```bash
python tools/boss/mother_model/build.py              # 1. geo / animation / texture / contract.json
python tools/boss/check_animations.py abyss_mother   # 2. ボーン名・キーの逆順・尺超えが無いか
python tools/boss/mother_model/preview.py            # 3. 六面図を out/ へ（目で確かめる）
```

- 3 は一部だけも描ける: `whole`（全身）/ `bell`（傘の拡大）/ `organs`（傘を外した中身）/ `bottom`（下から）/
  `beat`（拍動で縮んだ裾）/ `bloom`（開花の最後の姿）/ `bent`（触手と口腕を曲げた姿）
- 図は半透明を描かない（alpha 32 以上は不透明に塗る）。傘の中は `organs` で見る
- 曲げた図は contract.json の `java_orientation` と同じ式で骨を置いている（Java の写しの検算）
- 戦闘側（部位切り分け・hurt/death の追記）は対象外。旧描画の心臓の光輪と発光パルスの予告の粒は Java 側に残す

## 中身

| ファイル | 役割 |
| --- | --- |
| `build.py` | 骨格・外皮・アニメーション・約束ファイル。冒頭の寸法を変えれば形が変わる |
| `shape.py` | 傘と下傘の断面の表（build と atlas が共に読む。台形の切り抜きの寸法もここから） |
| `atlas.py` | 256x256 のテクスチャアトラス（皮膚と発光）。色は `tools/boss_textures.py` の mother() を引き継ぐ |
| `preview.py` | 実テクスチャを貼った六面図（`out/`） |
| `contract.json` | 描画の Java 側との約束（build.py が書く） |

書き出し先:

- `src/main/resources/assets/abyssia/geo/entity/abyss_mother.geo.json`
- `src/main/resources/assets/abyssia/animations/entity/abyss_mother.animation.json`（`idle` と `bloom`）
- `src/main/resources/assets/abyssia/textures/entity/boss/abyss_mother_geo.png` / `abyss_mother_geo_glow.png`
  （旧描画が使う `textures/entity/abyss_mother.png` は触らない）

## 寸法と MODEL_SCALE

**MODEL_SCALE = 4**（大海蛇と同じ）。geo の 1 単位はゲーム内で 0.25 ブロック。

| 量 | 実寸（ブロック） | モデル（単位） | 出典 |
| --- | --- | --- | --- |
| 傘 | 頂点 y=8、最大半径 18.2（y=-3）、縁 y=-6.4・半径 16.9 | 頂点 32、最大半径 72.8 | AbyssMotherRenderer.BELL |
| 下傘 | 中心 y=3.6 → 縁 y=-6.4・半径 16.85 | | 同 UNDER（最後の2区間を1つに） |
| 触手 | 12 本 × 関節 28（体節 27）、間隔 6、付け根は半径 15・y=-3 | 体節 24、付け根 (±60, -12) | AbyssMother |
| 触手の太さ | 半径 0.6 → 0.14 | 2.4 → 0.56 | 旧描画 tentacles() |
| 口腕 | 5 本 × 9 区間、区間 3.375（全長約 30）、付け根は半径 2.2・y=-6 | 区間 13.5 | 旧描画 organs() |
| 心臓 | 中心 y=-4、半径 3.4（開花 4.6） | 中心 -16、半径 13.6 | 旧描画 CORE_Y・cr |

## 骨の約束（Java の描画）

- root 直下: `bell`（pivot は原点 = AbyssMother.headPos()）、`tent_T_SS`（T=1..12、SS=01..27）、`oral_O_SS`（O=1..5、SS=01..09）
- 体節は静止姿勢で**まっすぐ下**に垂れ、pivot は体節の中心、局所 +Y が付け根側。tent_T の関節 0 は
  描画空間の `anchor(T-1) - headPos()` と同じ点（geo では X が反転）
- Java は root の骨の位置と回転を毎フレーム上書きする。向きは「付け根の外向き o から平行移動で運んだ p と、
  先端→付け根の d」で作る回転行列を X→Y→Z の角に分解する（式は contract.json の `java_orientation`）
- `bell` の子は animation に任せる: `heart` → `heart_core`、`bell_petal_k`（pivot は頂点）→ `bell_skirt_k`（肩）→ `bell_frill_k`（縁）
- `idle`（2.5 秒 = beat の周期、ループ）は裾の拍動・フリルの波・心臓の鼓動。`bloom`（3 秒、hold_on_last_frame）は
  花弁が 38 度外へ倒れ、心臓が 1.35 倍になって止まる。触る骨が分かれているので別のコントローラで同時に流せる
- GeckoLib の符号（X 反転、setPos は geo 単位、setRot は (-rx, -ry, +rz)）は大海蛇と同じ（contract.json の `geckolib_conventions`）

## 形の持ち方

| 部位 | 作り方 |
| --- | --- |
| 傘 | 24 列 × 7 段の板。各板は台形を囲む長方形で、テクスチャで台形の外を透明に抜く（隣と重ならない） |
| 下傘 | 24 列 × 4 段。外の面は傘の内側（下）を向き、12 個の馬蹄形の生殖腺を描く |
| 半透明 | 傘・下傘は厚み 0.4 単位の1枚だけ（内側に隠れた箱を作らない）。厚みの面は透明、外の面と内の面にだけ絵 |
| 花弁 | 2 列ずつ 12 枚。花弁の縁の放射管を太く（開花で割れる線）、中心（触手の真上）を細く |
| フリル | 列ごとに1枚の厚み 0 の膜。丸い襞の縁。拍動で裾と一緒に縮み、縁を 2 周する波で揺れる |
| 中身（不透明） | 口柄（頂点の下から心臓を貫く柱）、放射管 12 本、環状管（半径 15）、触手の付け根の球。開花すると傘の骨のように残る |
| 心臓 | 10 × 5 帯の球。フルブライト・全面発光 |
| 触手 | 一辺 1.6r の棒 2 本を 45 度ずらした星形。付け根から 12 体節は関節に数珠（袖）。それより先は太い前の体節が関節を越えて被さる |
| 口腕 | 芯の棒と、外向きから ±40 度に開く2枚のフリル。体節ごとに ±14 度ずらして襞にする |

## テクスチャの約束

- 傘・下傘の板の UV は build.py が直接書く（外の面 `*_OUT`、内の面は上下を返した `*_IN`、厚みは `CLEAR`）。
  列ごとに R（花弁の中心が絵の右）と L の2枚。どちらを貼るかは板の局所 X の向きで決める
- 半透明は傘 158〜220、下傘 140〜230、フリル 190〜230。ほかは 255 か切り抜きの 0
- 発光は `_glow.png`。光らない画素は (0,0,0,0)。台形の外も 0
- 生成は座標のハッシュだけで、何度流しても同じ PNG

## cube の数

上限 1500。いまは 1359（傘の板とフリル 288 / 中身 144 / 触手 792 / 口腕 135）。`build.py` で数え、超えると止まる。
