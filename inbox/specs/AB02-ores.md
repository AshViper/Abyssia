# AB02 地殻の鉱石（担当 O）

生成元: `tools/crust_ores.py`（全数値は定数、`python tools/crust_ores.py` で集計表を出力）。Java: `OreVeinFeature` の `"crust": true`、`ConfigPlacement` の `mk0_ore|mk1_ore|mk2_ore`、`Config` の節 `crust_ore`（`density` 全体倍率、`mk0_ore`/`mk1_ore`/`mk2_ore` ティア倍率、0〜10、0 で無効）。

## 仕組み
- 鉱脈の中心 = 配置位置（`height_range` 絶対 Y 一様）。海底検索・露出・ノジュールなし。置換は `#abyssia:vein_replaceable` のみ（水=洞窟は置換しない）。リムにホスト岩の縁は作らない。±13 ブロック以内は従来通り。
- 有限鉱床は従来通り `OreDepositManager.enqueue`。地殻モードの AABB は置いた鉱石ブロックの実際の範囲（従来の 26x26 の柱にしない）。
- 配置窓（配置点の Y）: B' -650..-393 / C -1100..-650 / D -1550..-1100 / E -1855..-1550（鉱脈が -380 より下、-1868 より上に収まる）。
- 密度 = 「その生物群系の体積あたり、チャンク当たり 100 ブロック高あたりの鉱脈数」。1 チャンクの試行数 = 密度 × 窓の厚み/100。別の生物群系に落ちた試行は `minecraft:biome` で捨てる。
- 主群（高率）と副群（低率）は別 placed feature（副は `_s`）。名前 `crust_<mk0|mk1|mk2>_<金属>_<b|c|d|e>_<small|medium|large|huge>[_s]`、384 個（主 240 + 副 144）、1 生物群系あたり 154〜200 個。

## 分布（金属 x 帯 x 生物群系）。主率/副率は 1 生物群系あたりの鉱脈数（全サイズ合計）

MK0（全帯・全 12 生物群系。副 = 主の 40%。帯係数 B' 1.0 / C 0.85 / D 0.70 / E 0.55）
| 金属 | 主生物群系 | B' | C | D | E |
|---|---|---|---|---|---|
| 鉄 iron | plain garden crystal ruins | 2.06/0.82 | 3.06/1.22 | 2.52/1.01 | 1.34/0.54 |
| 銅 copper | plain garden toxic_vents geothermal | 1.54/0.62 | 2.29/0.92 | 1.89/0.76 | 1.01/0.40 |
| 金 gold | plain volcanic magma geothermal ruins | 0.57/0.23 | 0.84/0.34 | 0.69/0.28 | 0.37/0.15 |
| 赤石 redstone | plain volcanic magma anomaly | 0.67/0.27 | 0.99/0.40 | 0.82/0.33 | 0.44/0.17 |
| ラピス lapis | plain crystal frozen cryo ruins | 0.57/0.23 | 0.84/0.34 | 0.69/0.28 | 0.37/0.15 |
| ダイヤ diamond | plain crystal cryo magma | 0.26/0.10 | 0.38/0.15 | 0.32/0.13 | 0.17/0.07 |
| エメラルド emerald | plain garden crystal ruins | 0.21/0.08 | 0.31/0.12 | 0.25/0.10 | 0.13/0.05 |

MK1（サイズ small 0.12 / medium 0.18 / large 0.04 / huge 0.008 の密度 x 帯係数 B' 0.7 / C 1.0 / D 0.35 / E 0.10。副 = 20%、small+medium のみ。金属ごとの偏りあり）
| 金属 | 主生物群系 | B' | C | D | E |
|---|---|---|---|---|---|
| コバルト | plain garden crystal | 0.63/0.11 | 1.57/0.27 | 0.55/0.09 | 0.11/0.02 |
| ニッケル | plain volcanic magma geothermal | 0.63/0.11 | 1.57/0.27 | 0.55/0.09 | 0.11/0.02 |
| マンガン | plain garden toxic ruins | 0.81/0.14 | 1.57/0.27 | 0.55/0.09 | 0.11/0.02 |
| チタン | plain volcanic frozen | 0.63/0.11 | 1.57/0.27 | 0.66/0.11 | 0.11/0.02 |
| 鉛 | plain toxic toxic_vents ruins | 0.88/0.15 | 1.57/0.27 | 0.44/0.08 | 0.11/0.02 |
| モリブデン | plain volcanic geothermal | 0.63/0.11 | 1.57/0.27 | 0.82/0.14 | 0.16/0.03 |
| バナジウム | plain crystal toxic_vents | 0.63/0.11 | 1.57/0.27 | 0.88/0.15 | 0.16/0.03 |
| 亜鉛 | toxic toxic_vents volcanic geothermal | 0.81/0.14 | 1.57/0.27 | 0.55/0.09 | 0.11/0.02 |

MK2（B' なし。サイズ small 0.10 / medium 0.07 / large 0.02 / huge 0.005 x 帯係数 C 0.4 / D 1.0 / E 1.8。C は small+medium、D は large まで、huge は E のみ。副 = 8% は D/E の small+medium のみ。iridium x0.6、tellurium x0.9、E のウラン/トリウム/ネオジム/イットリウムは x0.55）
| 金属 | C | D | E |
|---|---|---|---|
| タングステン | - | magma volcanic 0.86 | magma anomaly ruins 1.07 |
| 白金 | - | ruins crystal 0.86 | anomaly ruins 1.07 |
| テルル | - | toxic toxic_vents 0.77 | anomaly magma 0.96 |
| イリジウム | - | anomaly 0.51 | anomaly ruins magma 0.64 |
| ウラン | volcanic magma geothermal 0.31 | geothermal toxic_vents 0.86 | geothermal anomaly 0.59 |
| ネオジム | frozen cryo 0.31 | frozen cryo crystal 0.86 | frozen cryo 0.59 |
| イットリウム | frozen cryo 0.31 | cryo frozen 0.86 | cryo anomaly 0.59 |
| トリウム | volcanic magma geothermal 0.31 | volcanic geothermal magma 0.86 | magma anomaly 0.59 |

## 既存の海底表（A 帯）への所見（変更なし）
最小深度（manganese 2500m 〜 tungsten 6100m）は A 帯内で完結しており、新方針（MK0=全帯、MK1=B'/C 主、MK2=C 以深）と矛盾しない。tungsten/platinum/iridium 等が海底（A 帯、Y -242 以下）にも少量残るので、MK2 を「C 以深のみ」にしたければ RARE_MIN_DEPTH を上げるか RARE_VEINS から外す（要判断、未変更）。

## 未実施・要確認
- 実機での置換確認・鉱床の登録確認・チャンク境界の確認はメインがビルド後に行う。
- 生物群系の帯別の出現（どの帯にどの生物群系が出るか）は T の気候表次第。出ない組み合わせの feature は無害（biome フィルタで捨てる）。
- 新しい地殻の岩ブロックが増えたら `#abyssia:vein_replaceable`（`gen_deep_assets.py`）に足す。
