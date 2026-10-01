# M01 深海ディメンションを overworld の岩盤下へ統合
tier: heavy
files: (サブタスク別。下記 M01a / M01b)
goal: abyssia:deep_ocean ディメンションを廃止し、overworld の Y -368..-64（岩盤帯 -64..-60 の下、304 ブロック）に深海の地形・バイオーム・洞窟・構造物・生物・演出をそのまま移す。テレポートではなく、裂け目（abyssal_rift）と深海の割れ目（deep_fissure）の竪穴から泳いで降りる。
constraints:
- 新規ワールド専用（既存ワールドの移行はしない）。overworld の dimension_type は min_y -368 / height 688 / logical_height 688。
- 座標変換は 1 つの定数だけで行う: 旧深海 Y + `DeepLayer.SHIFT`(-240) = overworld Y。旧 transition（overworld -40 = 深海 200）とも一致する。
  旧深海の Y -128..176 が overworld の -368..-64 に入る。旧深海で Y 176 より上にあった地形（棚・バンク・火山頂）は天井の下に収まるよう抑える。
- 層の境界: Y < -64 が深海層 (`DeepLayer.isDeep(y)`)。-64..-60 は岩盤帯（ocean 側のバイオーム）、その下に厚さ約 8〜24 の岩の天井があり、その下が深海の水。
- 竪穴: rift の竪穴と deep_fissure の中心の細い裂け目は、岩盤帯と天井を貫いて深海層まで水でつながる。
- ワールド生成はジェネレーターで作り、出力 JSON は手で編集しない（tools/gen_worldgen.py, seabed_structures.py ほか）。
- ノイズルーターは 1 つにまとめ、`minecraft:range_choice`（入力は Y の勾配）で層ごとに切り替える: final_density / initial_density / continents / temperature / erosion / ridges / vegetation / depth。
  バイオームは router depth で分ける（ocean 側エントリは depth 0 付近、深海側は depth 2 付近）。
- 地表ルール: Y >= -64 には従来の ocean ルール（岩盤帯は絶対座標 -64..-60）、Y < -64 には deep ルール（最後に stone → deep_sea_rock）。底 -368 の岩盤は above_bottom のまま。
- 深海の床に置く地物は heightmap では海面側の海底を拾ってしまう。新しい配置 `abyssia:deep_floor`（天井の下から下向きに走査して、水に接した最初の固体の上へ置く）に置き換える。
- テレポート（DeepOceanTransition の移動・初回リスポーン）を削除する。設定は config_version を上げて不要キーを除く。
- 次元判定（dimension == DEEP_OCEAN）は `DeepLayer.isDeep(level, y)` に置き換える（霧、粒子、マリンスノー、海流、熱水、スポーン、コマンド、シェーダー霧）。
accept:
- gradlew build が通る。隔離サーバーで新規ワールドの起動時にエラーが出ない。
- `/abyssia map` が両層を出せる。深海層のバイオーム分布が統合前とほぼ同じ。
- 実チャンク: rift と割れ目の下が深海層まで水でつながる。深海層の床に植物・鉱脈・構造物がある。天井と岩盤帯がある。
- 深海の生物が深海層にスポーンする。
- `/abyssia caves`、`/abyssia structures` が overworld の深海層で動く。

## Decision（2026-10-01、MODIFY → 次の内容で確定）
- SHIFT は -240 のまま。旧深海の海底の上限は固定値で切らず、なだらかに抑える: 旧深海 Y 約 115 より上は傾き約 0.5 で圧縮し、旧深海 Y 140（`DeepLayer.SEABED_MAX_Y` = -100）でクランプする。天井の下面は `CEILING_BOTTOM_Y`（-88）より上に収める。
  棚が潰れて見えたら SHIFT -272 / MIN_Y -400 / height 720 に切り替える。
- バイオームの層分け: ocean 側エントリは depth -2、深海側エントリは depth +2。router の depth は Y < -64 で +10、それ以外で -10。境界の -64 は quart -16 に揃う。
- final_density は `interp(range_choice(y, deep, ocean))` の形にし、range_choice の外側で 1 回だけ interpolated する。
  - range_choice を flat_cache / cache_2d で包まない（Y を無視してしまう）。
  - 深海側の枝は max(深海地形, 天井帯) を取り、rift と割れ目スリットで削る。
  - initial_density の深海側は DEEP_GRAD+deep_seabed_offset だけにし、天井を入れない（CaveNetwork の二分探索を単調に保つため）。
- 海底の取り方:
  - 計画する処理（CaveNetwork、SeabedStructures/Painter、ThermalVentGenerator、TrenchFormation、CaveDebugRender、SpawnSite の候補選び）は密度から求めた海底（SHIFT 込み、範囲は [MIN_Y, TOP_Y)）を使う。
  - 実ブロックに置く処理（deep_floor 配置、AI）は `DeepLayer.floorY` を使う。
  - 深海層では WG の heightmap を一切使わない。
- 洞窟の入口と構造物の上端は CEILING_BOTTOM_Y から余白を取った高さより下にする。天井の中では deep_caves を出さない。
- DepthZone はメートル換算の原点を旧深海 Y 200 のまま固定する（`DEPTH_ORIGIN_DEEP_Y`）。Config には依存しないようにし、表と depth フィルターは変えない。
- 深海の lightmap と霧は ocean_world の演出に統合し、toDeepY で切り替える。旧ディメンションの ambient_light 0.1 は、Y に応じた lightmap で補う。
- FaunaSpawner の上限と数え方は層ごとに分ける。
- 最初に確認すること:
  - 新規ワールドで feature order cycle やレジストリのエラーが出ないこと（同じ地物を ocean 側と深海側のバイオームで違う順に並べないこと）
  - /abyssia map で両層のバイオームのヒストグラムを統合前と比べること

## サブタスク（ファイルは互いに重ならない。並列で進め、ビルドと検証はメインが行う）
- M01a（heavy, Opus 5.5）worldgen と生成時の Java:
  - tools/gen_worldgen.py、tools/seabed_structures.py、tools/cave_assets.py、tools/plant_defs.py（必要なら）
  - src/main/resources/data/**
  - src/main/java/com/abyssia/worldgen/**（cave/、structure/、terrain/、placement/、DepthFilter、OreVeinFeature、RockSpireFeature、RootArchFeature、ModWorldgen、OceanChunkGenerator）。DeepLayer.java は除く
  - ThermalVentGenerator（worldgen 以外のパッケージにあればそれも）
- M01b（heavy, Opus 5.5）実行時の Java:
  - DeepOceanTransition.java（削除、または最小のハンドラにする）、Config.java、ClientConfig.java
  - client/**、environment/**、fauna/**（DepthZone、SpawnSite、CarrionScent、external/ を含む）、entity/**（ai/SeabedRandomPos ほか）、network/**
  - thermal/ のうち生成以外のもの（ThermalVentManager）、registry/ModEntities のスポーン配置
  - lang（DeepOceanTransition の表示文言が変わる場合）
- 共有: `com.abyssia.worldgen.DeepLayer`。メインが作成済みで、変更はメインに依頼する。
