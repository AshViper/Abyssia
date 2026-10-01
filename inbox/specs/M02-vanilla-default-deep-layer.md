# M02 既定ワールド（バニラ）＋深海の割れ目＋岩盤下の深海層
tier: heavy
goal: ワールドタイプを選ばなくても（既定の minecraft:normal、サーバーの level-type 既定でも）、陸のあるバニラの overworld が生成されるようにする。そのうえで次を加える。
- バニラの海に「深海の割れ目」（abyssia:deep_fissure）を加える
- 岩盤の下 Y -368..-64 に、M01 と同じ深海層（地形・14 バイオーム・洞窟・構造物・生物・演出）を作る
- 割れ目の中心スリットは岩盤帯と天井を抜けて深海層までつなげる
「Abyssia 海洋」（abyssia:ocean_world）は、選べるワールドタイプとして今のまま残す。

constraints:
- M01 の仕組み（DeepLayer、OceanChunkGenerator、deep_floor、深海の density/surface/feature、M01c の高速化）を再利用し、二重に実装しない。
- 地上側はバニラ 1.20.1 の minecraft:overworld の地形、バイオーム（プリセット）、地表ルール、帯水層、溶岩、洞窟、構造物、鉱石を可能な限りそのまま使い、見た目を変えない。
- 上書きするのは data/minecraft/worldgen/world_preset/normal.json（既定のワールドタイプ）だけにする。大きなバイオーム・アンプリファイド・フラットはバニラのままにする。
- 新規ワールド専用（既存ワールドの移行はしない）。
- 生成物は gen_worldgen.py などのジェネレーターで作る。バニラ JSON は jar から読む（vanilla()）。

open questions（Decision Agent が決める）:
1. バイオームソース: バニラの overworld は multi_noise の preset（コード生成で JSON がない）を使う。案は次のどちらか。
   a. 自作の BiomeSource `abyssia:layered`: 上はバニラの preset、下は深海の multi_noise リスト。割れ目は router の値で上書きする。
   b. バニラのパラメータを全部 JSON に書き出す。
2. 割れ目の判定をバイオームソースに渡す方法（router の depth に割れ目マスクを混ぜるなど）。割れ目は海（大陸性が低い所）だけに出す。
3. バニラの帯水層・溶岩（Y < -54 の溶岩）と深海層の水の関係:
   - OceanChunkGenerator の fluid picker は、深海層では水、それ以外はバニラの規則にする
   - 帯水層は深海層で fluid_level_floodedness = 1 などとして全面を水にする
   - 割れ目が掘った部分を、帯水層が「地下」と誤認しないように、initial_density にも割れ目を入れる
4. final_density の合成（バニラの final_density は内部に interpolated と beardifier を含む）と、計算コスト。
5. 地表ルール: バニラのルールは Y >= -64 に限定する（deepslate の勾配が深海層に入らないように）。WorldGenerationContext の minY が -64 なので、above_bottom の岩盤はバニラどおり -64 に出る。
6. dimension_type: バニラの overworld の値で min_y -368 / height 688、effects は新しく `abyssia:overworld`（地上はバニラの空、Y < -64 は深海の lightmap と霧）。DeepLayer.isDeep の effects 判定を 2 種類の effects に広げる。
7. 地上の Abyssia 生物（FaunaSpawner の地上層）をバニラの海でも出すか、深海層だけにするか。

## Decision（2026-10-01、MODIFY → 次の内容で確定。バニラ 1.20.1 のソースで確認済み）
- Q1 バイオームソース: Java の `abyssia:layered` を使う（JSON 書き出し案 b は採らない）。
  - 上（upper）と下（lower）はどちらも MultiNoiseBiomeSource にする。
  - sampler で 1 回だけ取得してから分岐する（t = sampler.sample(...)）:
    - quartY >= -16 で、t.depth() < quantize(fissure_max_depth) なら fissure_biome
    - quartY >= -16 でそれ以外なら upper.getNoiseBiome(t)
    - quartY < -16 なら lower.getNoiseBiome(t)
  - possibleBiomes は 3 つの和集合。
- Q2 割れ目:
  - fissure_v = M01 の fissure × 海ゲート（minecraft:overworld/continents が -1.0..-0.6 で 1、-0.5 以上と -1.05 以下で 0。キノコ島は除く）。
  - router の depth は Y で切り替える: 地上側はバニラの depth - 10·clamp(1000·(fissure_v - 0.15), 0, 1)、深海側は 0。
  - **深海側の router depth は 0 にし、深海エントリも depth 0 にする**（帯水層の isDeepDarkRegion 判定で深海層が空気になるのを防ぐため）。ocean_world は帯水層を使わないので、今の ±10 のままでよい。
- Q3 流体:
  - OceanChunkGenerator の codec に、省略可能なフィールド `vanilla_fluids`（既定 false。false なら ocean_world は今と同じ）と `fluid_zone`（DensityFunction の holder）を追加する。
  - picker: y <= -64 は水（seaLevel）、y < -54 かつ zone <= 0 は溶岩（-54）、それ以外は水（seaLevel）。
  - fissure_zone = 割れ目の帯を約 24 ブロック広げたもの（2D、flat_cache）。
    - RandomState ごとに visitor（visitNoise → getOrCreateNoise）で配線する。
    - LevelEvent.Load で ServerChunkCache.randomState() から結び付け、まだなら fillFromNoise で遅延して結び付ける。
    - スレッドごとの列キャッシュを持つ。
  - fluid_level_floodedness は y <= -64 で 1.0、それ以外は max(バニラ, zone)。切り替えは -64 ちょうど。
  - 割れ目の掘削は initial_density にも入れる。
  - fillFromNoise の後で、-64 より下のセクションの post-processing 印を消す（深海の水がすべて液体更新されるのを防ぐ）。
- Q4 final_density:
  - バニラの final_density をコピーし、その中の interpolated ノード（1 つだけであることを assert する）の引数を range_choice に置き換える。
    - Y < -66 なら deep_density_nr
    - それ以外なら min(blend_density(バニラの中身), crack_carve)
  - deep_density_nr = deep_density から rift の穴と rift_limit を除き、割れ目スリットの穴だけを持たせたもの。同じ Python ビルダーを引数で切り替えて作る。
  - crack_carve: 床の Y = 40 - 90·clamp(3·fissure_v, 0, 1)。掘るのは海面より下だけ。スリットは岩盤帯を貫通させる。
  - noodle 洞窟はそのまま（Y -60 で自分で止まる）。
- Q5 地表ルール:
  - Y >= -64: 割れ目の竪穴の deepslate（y < -56）、割れ目の床の砂利と deepslate の壁、そのあとにバニラのルール
  - Y < -64: deep_surface_rule()
- Q6 次元タイプ:
  - dimension_type `abyssia:overworld` は ocean_world.json と同じ値で、effects を `abyssia:overworld` にする。
  - DeepLayer.isDeep は ocean_world と overworld の両方の effects を受け付ける。
  - クライアントは、地上はバニラの OverworldEffects、-64 より下は深海の lightmap、空、霧にする。
- Q7 生物: Abyssia の生物は深海層だけに出す。FaunaSpawner は、effects が ocean_world でない限り、深海層でない位置では 0 を返す。

## 担当間の取り決め（コーデックとファイル名）
- バイオームソース:
  `{"type":"abyssia:layered","upper":{"type":"minecraft:multi_noise","preset":"minecraft:overworld"},"lower":{"type":"minecraft:multi_noise","biomes":[...深海14,depth 0...]},"fissure_biome":"abyssia:deep_fissure","fissure_max_depth":-5.0}`
- 生成器:
  `{"type":"abyssia:ocean_noise","settings":"abyssia:overworld","biome_source":{...},"vanilla_fluids":true,"fluid_zone":"abyssia:overworld/fissure_zone"}`
- 出力ファイル:
  - data/abyssia/worldgen/noise_settings/overworld.json
  - data/abyssia/worldgen/density_function/overworld/*.json（fissure_v、fissure_zone、crack_carve、deep_density_nr など）
  - data/minecraft/worldgen/world_preset/normal.json
  - data/abyssia/dimension_type/overworld.json

## サブタスク（ファイルは互いに重ならない）
- S1（heavy, Opus）:
  - tools/gen_worldgen.py と、それが生成する data/**（上の出力ファイル）
  - check_feature_layers を jar のバニラの全バイオームに広げる
- S2（heavy, Opus）:
  - worldgen/LayeredBiomeSource.java（新規）、ModWorldgen.java、OceanChunkGenerator.java、DeepLayer.java
- S3（standard, Sonnet）: client/AbyssiaDimensionEffects.java（必要なら client/DeepOceanClientEffects.java）、fauna/FaunaSpawner.java

accept:
- 既定設定の新規ワールド（サーバーで level-type 未指定）で、地上がバニラと同じ見た目になる（同じシードで地形とバイオームがバニラとほぼ一致する）。
- 海に deep_fissure があり、そこから深海層まで水でつながる。
- 深海層は M01 と同等（/abyssia map のバイオーム分布、洞窟、構造物、植物）。
- feature order cycle やレジストリのエラーが出ない。チャンクあたりの生成時間を、バニラの overworld 単体と比べて報告する。
- 「Abyssia 海洋」ワールドタイプは今までどおり動く。
