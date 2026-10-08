# ORE01 鉱床での採掘 (クラスト廃止 / 独自鉱物は採掘機専用)

Base = NeoForge 1.21.1 worktree `F:\Java\Abyssia-NeoForge` (編集は絶対パス)。Forge 1.20.1 への移植は別タスク `ORE01-forge`。
既存: 鉱脈ごとに鉱床データ `OreDeposit` (`worldgen/deposit/`、totalOre / minedAmount、有限)、採掘機 Mk1 (`industry/…/AbyssalExcavator*`、`ExcavatorTier`、半径 32 の鉱床から FE で採る)。新しい仕組みは作らず、これを土台に変える。

## ユーザー決定 (2026-10-08)
- 鉱床はブロックで見える (今の鉱脈ブロックのまま)。採掘機はティア付き Mk1/Mk2。鉱床は有限 (枯渇する)。
- 地形生成の**クラストを削除**。
- **バニラの品を落とす鉱脈は手掘りのまま**: iron, copper, diamond, gold, redstone, lapis, emerald, quartz。
- **それ以外 (manganese, cobalt, nickel, sulfur, thermal_crystal, abyssal_crystal, rare metals 6種) は採掘機だけ**で取れる。
- コバルトなどが取りにくくなるので、レシピはあとでユーザーと調整する (今回は必須の修正だけ)。

## タスク A: クラスト削除と手掘りの禁止 (担当 A)
1. 全ての `*_crust` ブロック (manganese, cobalt, nickel, iron, copper, diamond, gold, redstone, lapis, emerald, quartz …探して全部) を削除: ModBlocks / ModBuildingBlocks の登録、blockstates / models / loot / recipes / tags / lang、テクスチャとロック (`tools/texture_locks` の該当、`mineral_textures` 等)、`CRUST_RARE_DROPS`、DeepMapFiller / cave 系 / ModTags の参照。`sulfur_deposit`・`mineral_sediment`・`crystal_sediment` は名前にクラストが無いので残す (判断は報告に書く)。
2. `OreVeinFeature` の `crust` 設定と生成を削除し、`tools/gen_worldgen.py` の `MINERALS` / `VANILLA_VEINS` と生成 JSON を整合させる (`crust` キーなし)。`bounds.maxY` のコメントも直す。
3. 独自鉱物の鉱石ブロック (`manganese_ore, cobalt_ore, deep_nickel_ore, sulfur_ore, thermal_crystal_ore, abyssal_crystal_ore`、rare metals 6 の `*_ore`) と、その成長物 (`manganese_nodules, cobalt_cluster, nickel_cluster, sulfur_cluster, abyssal_crystal_cluster, thermal_crystal_cluster`) を**サバイバルで壊せない・何も落とさない**にする (destroySpeed -1 相当、クリエイティブは可、爆発耐性高め)。lang の名称は「○○鉱床 / ○○ Deposit」に (成長物は名称そのまま)。バニラの品を落とす鉱石 (`abyssal_iron_ore, deep_copper_ore, abyssal_<vanilla>_ore`) は今のまま手掘り。
4. 精鉱の分離機 (`MachineRecipes` / `I02`) と `industrial_assets.py` 以外のレシピで、クラストを入力にしているものを探し、入力を対応する `raw_*` に置き換えて最小限直す (比率はそのまま、バランスはユーザー調整)。クラストの製錬レシピは削除。
5. 生成物は手編集せず、生成ツール (`gen_worldgen.py`, `gen_deep_assets.py` ほか) を直して再実行する。ロック済みテクスチャを消さない。

## タスク B: 採掘機 Mk2 とティア制限 (担当 B)
1. `ExcavatorTier` に MK2 を追加 (定数だけで調整できる形のまま)。目安: Mk1 = 今の値、Mk2 = 半径 48、cycle 30t、orePerCycle 2、20→40 FE/t。ブロック登録 `abyssal_excavator_mk2` (Mk1 と同じモデル、テクスチャは Mk1 を色違いで `tools/` のスクリプトから生成)、BE は tier を block から読む既存の作りに合わせる。
2. 鉱物ごとの必要ティアの表を 1 か所に置く (例 `industry/ExcavatorMinerals`): Mk1 = manganese, nickel, sulfur, thermal_crystal, iron, copper, バニラ 6 種。**Mk2 のみ** = cobalt, abyssal_crystal, rare metals 6 種。足りないティアのときの状態コード (`STATUS_TIER_TOO_LOW`) と GUI 表示、lang。
3. Mk2 の副産物: 対象鉱床が cobalt / manganese / nickel のとき、1 サイクルごとに旧 `CRUST_RARE_DROPS` (platinum, tellurium, molybdenum, vanadium, tungsten, yttrium) の確率 (fortune 0 の値) で `raw_<metal>` を出す。出力スロットに入らないときは副産物を捨てる (本体の産出を優先)。
4. 鉱床が枯渇したら (`minedAmount >= totalOre`)、その `bounds` 内の独自鉱物の鉱石ブロック (その鉱床の mineralId) を `mineral_host_rock` に置き換える (サーバー側、チャンク読み込み済みの範囲だけ、1 回で大量に置換しないよう 1 tick あたり上限)。
5. Mk1 / Mk2 のレシピは、**採掘機でしか取れない物 (cobalt, abyssal_crystal, rare metals) を要求しない** (鶏卵になるため)。既存の Mk1 レシピの構成を見て、Mk2 = Mk1 + 追加素材 (cobalt を含まない既存の合金/部品) とする。
6. モデル: `F:\Java\Abyssia-NeoForge\tools\excavator_models\AbyssalExcavator.bbmodel` が最新 (3x3x3 ブロック大、24 キューブ、回転グループ 4)。既存の `ExcavatorMesh` / `ExcavatorRenderer` がこれと一致するか `tools/bbmodel_to_mesh.py` で再生成して確認し、違えば更新する (.bbmodel は全文を読まない、スクリプトで要約する)。
7. lang は `tools/lang_parts/ore01.json` ({"key": [en, ja]}) に書く。

## 共通
- 触ってよいファイルは自分の担当の範囲だけ。ほかの担当と重なりそうなら編集せず報告に書く (相手とは通信しない)。
- gradle は実行しない (メインが実行)。データの確認は `python tools/mc_format.py --to-forge F:/Java/Abyssia --dry-run` が errors 0 になること。
- 返答は `STATUS / FILES / NOTES` 10 行以内。
- 受け入れ: ビルドが通る / `*_crust` の参照がソースとデータに残らない / 独自鉱物の鉱石が手掘りで壊れない / Mk2 が cobalt 鉱床を採れて Mk1 は採れない / 枯渇で鉱石が host rock になる。
