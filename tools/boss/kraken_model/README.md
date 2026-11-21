# 大クラーケン（abyss_kraken）の GeckoLib モデル生成

大海蛇（`tools/boss/serpent_model`）と同じ「Python で GeckoLib モデルを生成する」流れ。
geo / animation / テクスチャ / 約束ファイルを丸ごと書き出す。**出力は手で直さない**（ここを直して流し直す）。

## 流す順番

```bash
python tools/boss/kraken_model/build.py               # 1. geo / animation / texture / contract.json
python tools/boss/check_animations.py abyss_kraken    # 2. ボーン名・キーの逆順・尺超えが無いか
python tools/boss/kraken_model/preview.py             # 3. 六面図を out/ へ（目で確かめる）
```

- 3 は一部だけも描ける: `preview.py rest`（書き出したままの静止姿勢）/ `whole`（腕を広げて垂らした姿）/
  `body`（本体と腕の付け根の拡大）/ `bent`（腕を強く巻いた姿と拡大）/ `severed`（腕 1 を切断した姿と蓋の拡大）
- 姿勢を付けた図は、Java の描画と同じ置き方（体節を関節の中点へ運び、局所 +Z を先端へ、
  局所 +Y（背）を平行移動で受け渡す）で作る。袖の重なりと吸盤の向きはここで見る
- 戦闘側（攻撃の起点・当たり判定・hurt/death のアニメーション）は今回の対象外

## 中身

| ファイル | 役割 |
| --- | --- |
| `build.py` | 骨格・外皮・アニメーション・約束ファイル。冒頭の寸法と表を変えれば形が変わる |
| `atlas.py` | 256x256 のテクスチャアトラス（皮膚と発光）。色は `tools/boss_textures.py` の kraken() を引き継ぐ |
| `preview.py` | 実テクスチャを貼った六面図（`out/`） |
| `contract.json` | 描画の Java 側との約束（build.py が書く） |
| `../boss_kit.py` `../boss_views.py` `../check_animations.py` | 共通の道具（ここからは変えない） |

書き出し先:

- `src/main/resources/assets/abyssia/geo/entity/abyss_kraken.geo.json`
- `src/main/resources/assets/abyssia/animations/entity/abyss_kraken.animation.json`
- `src/main/resources/assets/abyssia/textures/entity/boss/abyss_kraken_geo.png` / `abyss_kraken_geo_glow.png`
  （旧描画が使う `textures/entity/abyss_kraken.png` は触らない）

## 寸法と MODEL_SCALE

**MODEL_SCALE = 4**（大海蛇と同じ）。geo の 1 単位はゲーム内で 0.25 ブロック、テクスチャ 1 画素も 0.25 ブロック。
ブロック → geo 単位は × 4。

| 量 | 実寸（ブロック） | モデル（単位） | 出典 |
| --- | --- | --- | --- |
| 腕 | 8 本 × 関節 16（体節 15） | | AbyssKraken.TENTACLES / JOINTS |
| 関節間隔＝体節の長さ | 5.5 | 22 | AbyssKraken.SPACING |
| 腕の半径 | 付け根 1.8 → 棍棒 1.16 → 先 0.09 | 7.2 → 4.63 → 0.35 | AbyssKrakenRenderer.armRadius（関節ごとの値は contract.json） |
| 腕の断面 | 幅 1.08r・背 1.0r・吸盤面 0.92r | 同じ | 同 tube |
| 付け根の輪 | 高さ -6・半径 3 | y -24・半径 12 | AbyssKraken.anchor |
| 外套膜 | 高さ -2.6〜15.9、最大径 13.6 | y -10.4〜64、半径 27.2 | 同 MANTLE 表 |
| 頭 | 高さ -8.3〜-0.8、半径 5 | y -33.2〜-3.2 | 同 HEAD 表 |
| ひれ | 軸から 10.8 張り出す | 43.2 | 同 FIN 表 |
| 全体 | 腕の先から先まで約 171（輪 3 + 腕 82.5 の 2 倍） | | |

## 骨の約束（Java の描画）

- `mantle` と `arm_A_SS`（A = 1..8、SS = 01..15）は **root 直下**。腕 A は `tentacle(A - 1)`。
  静止姿勢は腕 8 本を X に並べ、どれも +Z（付け根→先端）へ一直線。関節 j は `arm_rest_origin[A] + (0, 0, 22j)`
- `arm_A_SS` の pivot は体節の中心。局所 -Z が付け根側、局所 -Y が吸盤面。Java は毎フレーム中点へ運び、
  -Z を関節 SS-1 へ、-Y を本体の内側へ向ける（式は contract.json の `arm_rotation_with_roll`）
- `mantle` の子: `mantle_shell`（外套膜、呼吸の scale）→ `fin_l` / `fin_r`、`head` → `eye_l` / `eye_r` /
  `beak_upper` / `beak_lower`、`siphon`。どれも animation（idle）に任せる
- 切断: `arm_A_stump` は `arm_A_03` の子で、geo では `neverRender: true`。切れた腕は `arm_A_04..15` を隠して
  stump を見せる。GeoBone はモデルで共有なので、hidden は毎フレーム全部の腕に入れ直す
- GeckoLib の符号（setPos は geo 単位、setRot は描画空間で (-rx, -ry, +rz)、X が反転）は contract.json の
  `geckolib_conventions`（大海蛇と同じ）

## 形の持ち方

| 部位 | 作り方 |
| --- | --- |
| 腕の体節 | 楕円の断面を 8 角で `ring_loft` 1 区間。前端は 0.9 倍の差し込み（関節の 0.5r 前まで）、後端は 1.04 倍の袖（関節の 0.4r 後ろまで） |
| 継ぎ目 | 袖が次の体節の差し込みへ被さる。28 度曲げても隙間の奥は次の皮しか見えない（`bent_close`） |
| 腕の先 | 体節 15 は 0.32 まで板で細り、その先を八芒形の棒 2 段と棘 1 本で尖らせる |
| 吸盤 | 吸盤面（-Y）の板に、2 列の吸盤の絵を丸ごと貼る。板の縦横比に合わせて 1 列 6 / 9 / 12 / 16 個の絵を選ぶ |
| 鉤 | 棍棒（体節 12〜14）の吸盤面に 2 列、計 10 本。先が付け根の側へ反る |
| 切断面 | 体節 3 の袖の端を、肉の扇 8 枚（`Kit.fan` の膜）で塞ぎ、中央に生肉が盛り上がる |
| 外套膜 | Y 軸の環 10 段を 16 枚ずつの板で（`y_loft`）。先は八芒形の棒 2 段 |
| 頭 | 環 8 段を 14 枚ずつ。下に唇の環と口の闇、嘴（2 次ベジェの棒 3 本ずつ） |
| 眼 | 頭から突き出た 10 角の縁の筒と、その奥の眼の円盤 |
| ひれ | 菱形の外縁の辺ごとに、辺へ直交する帯を外套膜の中まで張る。厚みを交互に変えて重なりのちらつきを防ぐ |
| 漏斗 | 襟の下から腹側（+Z）へ下りる 8 角の細る管 |

## テクスチャの約束

- 腕の板は左右で u が鏡写しになる（板を回転だけで置くため）。横向きに意味のある絵は描かない
- 絵の上端は面に沿って上る向き（腕は背の側、外套膜と頭は先の側）。吸盤・眼・嘴・鉤・切断面・漏斗は
  領域まるごと貼る（`STRETCHED`）
- 発光は `_glow.png`（外套膜・頭・ひれ・腕の脇の淡い点、吸盤の口の一つおき、眼の虹彩）。光らない画素は (0,0,0,0)
- 生成は座標のハッシュだけで、何度流しても同じ PNG

## cube の数

上限 1500。いまは 1467（本体 307 / 腕 1080 / 切断面 80）。`build.py` の最後で数え、超えると止まる。
腕は体節あたり 8 枚（先端と棍棒は鉤と棘のぶん多い）。
