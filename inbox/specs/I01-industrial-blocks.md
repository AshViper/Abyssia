# I01 工業ブロック(建材+発電機2+機械3)
tier: heavy
files: (実装時に分割) A: 建材 (block/industrial/*, ModBlocks, blockstates/models/lang/loot/recipes) / B: エネルギー基盤+発電機2 (BlockEntity, IEnergyStorage) / C: 機械3+Menu/Screen / D: thermal_vent ドロップ変更
goal: 設計: ChatGPT (2026-10-02 チャット「工業ブロック実装提案」)。深海基地向けの工業建材と、熱水噴出孔を主電源とする初期工業ライン(発電機2・機械3)を追加する。「熱水噴出孔を発見→発電→基地建設→資源加工」のループを作る。
constraints: エネルギーは Forge の ForgeCapabilities.ENERGY / IEnergyStorage (独自APIなし)。送電は隣接直接 + ケーブルネットワーク (下の追補が優先)。配管は見た目だけ。全ブロック waterlogged 可・水中でも性能低下なし。3機械は GUI (Input/Output/Progress/Energy)。Tier6 素材 (abyssal_power_core 等) をレシピに使わない。合金炉の配合は既存の作業台レシピの素材をそのまま使い出力だけ ×2→×3。既存ID・既存レシピは変更しない (thermal_vent のドロップのみ例外)。
accept: コンパイル通過 / check_recipes・check_textures OK / 熱水噴出孔上の熱水発電機から隣接機械が動く / 補助発電機が燃料で動く / 3機械の処理が下表どおり / 水中設置で動作 / thermal_vent が本体をドロップしない

## 建材
| id | 英名 | 日本語名 | 仕様 |
|---|---|---|---|
| industrial_panel (+_slab/_stairs/_wall) | Industrial Panel | 工業用金属パネル | 基本壁材 |
| metal_grating (+_slab) | Metal Grating | 金属グレーチング | 透過床 (下が見える) |
| industrial_beam | Industrial Beam | 工業用梁 | X/Y/Z 軸 |
| industrial_pipe | Industrial Pipe | 工業用配管 | 6方向見た目接続 (配管/バルブ/機械/発電機) |
| industrial_valve | Industrial Valve | 工業用バルブ | 配管装飾 |
| work_light | Work Light | 作業灯 | 光 15、白 |
| warning_light | Warning Light | 警告灯 | 光 10、橙/黄 |

## 発電機
| id | 日本語名 | 発電 | 容量 | 条件 |
|---|---|---|---|---|
| hydrothermal_generator | 熱水発電機 | 80 FE/t | 32,000 | thermal_vent の真上 or 隣接。燃料不要 |
| auxiliary_generator | 補助発電機 | 40 FE/t | 16,000 | 燃料: 石炭/木炭 80,000 FE、生体油 120,000、精製油 200,000 |

thermal_vent: 本体をドロップしない (シルクタッチでも不可)。持ち運び不可 = 自然生成地点だけが熱水発電の場所。

## 機械
| id | 日本語名 | 処理 | 時間 | 消費 |
|---|---|---|---|---|
| crusher | 粉砕機 | 原石×1→粉×3 / クラスト×1→精鉱×4 (ハンマー比 +1) | 80t | 3,200 FE |
| refinery_furnace | 精錬炉 | 粉×1→インゴット×1 | 60t | 2,400 FE |
|  |  | 精鉱×1→インゴット×1 (粉工程を省略) | 100t | 4,000 FE |
| alloy_furnace | 合金炉 | 既存合金レシピの素材→合金×3 (作業台は×2) | 120t | 6,000 FE |

合金炉の対象 (既存レシピ、Claude 照合済み): 深海合金 = バナジウム+コバルト+ニッケルインゴット / 耐食 = ニッケルインゴット+コバルト粉+鉄粉 / 高強度 = マンガンインゴット+バナジウム粉+鉄粉 / 耐熱 = モリブデンインゴット+ニッケル粉+鉄粉 / 導電 = 銅インゴット+テルル粉 (2素材) / 熱 = 熱試薬+モリブデンインゴット+タングステン粉。
粉砕機の対象は既存の破砕ハンマーレシピ (recipes/*_from_raw, *_from_crust) から出力+1 で導出。

## レシピ (作業台)
- industrial_panel ×8: 鉄板×9
- metal_grating ×8: 鉄棒×8 + 銅線 (中央)
- industrial_beam ×6: 鉄棒×6 (2列×3段)
- industrial_pipe ×8: 鉄板 角4 + 銅線 辺4 + 鉄棒 中央
- industrial_valve ×2: 鉄棒×4 (十字) + 圧力バルブ (中央)
- work_light ×2: 鉄板 発光素子 鉄板 / 鉄棒 銅線 鉄棒 / _ 鉄棒 _
- warning_light ×2: 銅線 発光素子 銅線 / 鉄板 鉄棒 鉄板 / _ 鉄棒 _
- crusher: 鉄板×3 / 鉄歯車 機械枠 鉄歯車 / 鉄棒 銅線 鉄棒
- refinery_furnace: 鉄板×3 / 圧力バルブ 機械枠 圧力バルブ / 鉄板 熱部品 鉄板
- alloy_furnace: 耐食合金 熱部品 耐食合金 / 導電部品 機械枠 導電部品 / 鉄板 圧力バルブ 鉄板
- auxiliary_generator: 鉄板×3 / 鉄歯車 機械枠 鉄歯車 / 銅線 かまど 銅線
- hydrothermal_generator: 耐食合金 熱部品 耐食合金 / 圧力バルブ 機械枠 圧力バルブ / 鉄板 銅線 鉄板
- 建材の slab/stairs/wall は通常レシピ + 石切台

## 見た目 (16x16)
共通: ダークガンメタル・青灰・暗いティール、ボルト/溶接跡/円形ハッチ/観測窓、発光はシアン、警告色は黄橙を少量。錆びた鉄ではなく耐圧・耐食金属。Create 風の明るいスチームパンクにしない。
- crusher: 正面=円形ローター+シアン指示灯、側面=厚装甲+歯車、上面=投入口。稼働中ローター発光
- refinery_furnace: 正面=縦長炉+観測窓、側面=配管+バルブ、上面=排熱口。稼働中 窓が橙〜白に発光
- alloy_furnace: 正面=円形炉+発光コア、側面=太い配管、上面=投入口。稼働中 コアがシアン→白
- hydrothermal_generator: 正面=大型円形タービン+シアン発光リング、側面=熱水配管、下面=ノズル。稼働中 発光+小さな熱気パーティクル
- auxiliary_generator: 小型回転部+燃料インジケータ+排気管、無骨で少し汚れた感じ
イメージ画: inbox/designs/I01.png

## 追補 (2026-10-02 ユーザー追加依頼: 高温炉・エネルギー装置・ケーブル、ChatGPT 設計)
### 熱水発電機の活動度倍率 (種類による倍率なし)
DORMANT ×0 / WEAK ×0.5 / ACTIVE ×1.0 / STRONG ×1.5 / SUPERHEATED ×2.0 (= 0/40/80/120/160 FE/t)。隣接する噴出孔のうち最大の活動度を使う。

### high_temp_furnace 高温炉 (Tier 3〜4)
条件: thermal_vent が 6 ブロック以内にあり、活動度が DORMANT 以外 (種類は問わない)。満たさないと停止。
| 入力 | 出力 | 時間 | FE |
|---|---|---|---|
| タングステン粉×1 | タングステンインゴット×1 | 60t | 4,000 |
| 熱試薬+モリブデンインゴット+タングステン粉 | 熱合金×3 (作業台×2) | 100t | 8,000 |
| タングステンインゴット+ニッケル粉 | タングステン合金×3 (作業台×2) | 100t | 8,000 |
レシピ: 耐熱合金 熱部品 耐熱合金 / 熱部品 機械枠 熱部品 / 鉄板 圧力バルブ 鉄板
見た目: 正面=厚い炉扉+小さな耐熱観測窓+温度計、側面=耐熱配管+耐熱合金装甲、上面=高温排気口+放熱フィン。稼働中 炉内が白〜淡い橙、温度インジケータがシアン、小さな熱気。

### energy_device エネルギー装置 = 大容量蓄電池
容量 1,000,000 FE、受電/送電 256 FE/t、GUI あり、水中可。発電機→蓄電池→機械。
エネルギーセル類は FE アイテムにしない (素材のまま)。ドリル/カッターの電動化は I02 以降。
レシピ: 導電合金 深海エネルギーセル 導電合金 / 導電部品 機械枠 導電部品 / 深海合金 発光素子 深海合金 (Tier 4〜5、power_core 不使用)
見た目: 正面=大きな円形シアンエネルギーコア+下に蓄電ゲージ、側面=厚装甲+冷却フィン+小型接続口、上面=発光ライン+点検ハッチ。蓄電量で空=暗い/低=弱シアン/中=シアン/高=明るいシアン (blockstate charge 0..3)。

### ケーブル (配管とは別ブロック)
| id | 英名 | 日本語名 | 転送 | レシピ |
|---|---|---|---|---|
| energy_cable | Energy Cable | エネルギーケーブル | 128 FE/t | 銅線×8 + 深海繊維(中央) → ×8 (Tier 2) |
| reinforced_energy_cable | Reinforced Energy Cable | 強化エネルギーケーブル | 512 FE/t | 導電合金×6 (上下段) / 銅線 深海合金 銅線 → ×6 (Tier 3) |
つながったケーブル全体を1ネットワークとし、発電→蓄電→機械へ分配。複雑な優先度設定はなし。6方向自動接続 (ケーブル同士・機械・発電機・蓄電池)、水中可。
見た目: 配管より細い。ダークガンメタルの外装に暗いティール/シアンの芯線、通電中は弱く発光 (強化版は太め・金属帯つき)。

## 実装契約 (Claude 決定、Java と生成ツールの共通取り決め)
- blockstate: panel=cube / grating=cube(cutout,waterlogged) / beam=axis+waterlogged / pipe・cable=north..down(bool)+waterlogged / valve=facing(6)+waterlogged / work_light・warning_light=facing(6, 取付面)+waterlogged / 機械・発電機=facing(水平)+lit+waterlogged / energy_device=facing(水平)+charge(0..3)+waterlogged
- テクスチャ (block/): industrial_panel, metal_grating(穴は透過), industrial_beam_side, industrial_beam_end, industrial_pipe, industrial_valve, work_light, warning_light, energy_cable, reinforced_energy_cable, および各機械 <id>_front / <id>_front_on / <id>_side / <id>_top (energy_device は front_on を charge で明るさ違いに派生)。ChatGPT シート IND1/IND2 から取込・ロック。
- JSON (blockstate/model/loot/recipe/lang/tag) は tools/industrial_assets.py が生成 (gen_deep_assets から呼ぶ)。手書きしない。thermal_vent のドロップ無しも生成側で。
- 機械レシピ (処理) は Java 側でバニラの RecipeManager から実行時に導出 (破砕ハンマーのレシピ +1、溶鉱炉レシピ、合金の作業台レシピ ×1.5)。新しいレシピ JSON 型は作らない。

### モデル (2026-10-02 ユーザー指定: 機械・発電機・蓄電池は普通の立方体ブロック (かまど型 6 面テクスチャ)。配管・バルブ・ケーブル・ライトだけ形のあるモデル)
- 形は ChatGPT が設計 (`tools/industrial_models/parts.json`、ChatGPT 検査 2 回で valve 修正・合格)、`tools/industrial_models/build_models.py` が Java Block Model JSON を生成し、industrial_assets.py が models/block/ にコピー。Blockbench では assets 側の JSON を開いて確認する。機械は industrial_assets.py の立方体モデル。
- valve: 配管の途中に付く形 (z 方向に 4..12 の配管が 0..16 を貫通、3..13 のハウジング、上に軸と円形ハンドル y≤14)。
- モデル名: industrial_pipe_core / industrial_pipe_arm、energy_cable_core / _arm、reinforced_energy_cable_core / _arm (arm は north 向き、blockstate で回転)、industrial_valve (north 向き)、work_light / warning_light (床置き=facing up が基準、blockstate で x/y 回転)、機械 7 台 <id> (正面 north、テクスチャ変数 front/side/top を使う)。稼働版 <id>_on と energy_device_c0..c3 は生成側が parent + front 差し替えで作る (c1,c2 は front と front_on の中間を生成側で派生)。アイテムモデル: <id>_inventory があればそれ、無ければ core / 本体。
- 当たり判定 (Java VoxelShape, 16 単位): pipe core 4..12 / arm 同幅、energy_cable 6..10、reinforced 5..11、valve 3..13 の箱 (facing 方向に 0..16)、light 床置き 4..12 × 0..6 × 4..12 (facing で回転)、機械・発電機・蓄電池 は 0..16 フル (見た目の出っ張りは 1px 程度まで、ブロック外にはみ出さない)。

## 第2段階 (今回やらない)
crystal_processor / mining_machine、エネルギーセルの FE 化、ドリル/カッター電動化、熱水噴出孔の欠片素材。
