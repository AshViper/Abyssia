# チャンク生成ベンチマーク手順（scratch サーバー）

目的: 最適化の前後を同じ条件で比べる。実測値のみ報告する。

## 計測できるもの
`/abyssia caves stats`（RCON 可）の 2 行:
1. `stats()`: 全チャンク平均/最大、cave チャンク当たりの phase 時間（`field / carve / geology+formations+ores / decoration / vegetation`）。**浅いネットワーク**は cave チャンク当たり、`| per window chunk` は**全地殻ウィンドウ合計**を窓チャンク当たり。地殻ウィンドウ別は `crust windows:` 欄。
2. `caches:` `shallow:` / `windows:` ごとに plans / systems / minors / cavities の
   `hit`（ヒット数と率）、`built`（構築数）、`clears`（全消去回数）、`dropped`（消去で捨てた数）、`rebuilt<=`（初回の全消去より後の構築数 = 再構築の上限）。
`/abyssia caves stats reset` で全カウンターを 0 にする。`CAVE_DEBUG_TIMING` を true にすると 256 チャンクごとにログにも出る。

## 手順（各条件 ≥3 回、ウォームアップ除く）
1. 固定条件: シード（例 777 と別 1 つ）、normal / ocean_world の両方、同じ JVM 引数、他セッションの重い処理を止める（wall は ±20% ずれる）。
2. 変更前の jar でも同じ座標を測る（`git stash` ではなく別の scratch コピーを使う。本体 `run/` には触れない）。
3. scratch を再起動 → **初回（冷えたキャッシュ）**: 新規領域に対し
   `python scratch_rcon.py gentime <x> <z> 8`（8x8=64 チャンク、4x4 バッチ）→ 直後に `caves stats` を記録。
4. `caves stats reset` → **温まった状態**: 隣接する未生成の新規領域に `gentime`（キャッシュは残る）→ `caves stats` を記録。
   （同じ領域は再生成されない=ディスクから読むだけなので、必ず新規座標。）
5. 地殻ウィンドウ別は `crust windows:` 欄。浅い/窓はステップ 3, 4 の 2 行目で分離済み。
6. CPU/GC: `jcmd <pid> JFR.start name=gen settings=profile filename=gen.jfr` → gentime → `JFR.stop` → `jfr print --events jdk.ExecutionSample gen.jfr` のサンプル数比（変更前後）、`jdk.GarbageCollection` の合計。
7. 生成結果の一致確認: 変更前後の保存ワールドを `scratch_rcon.py mcadiff` で比較（最適化は 0 差分が合格条件）。

## 判断基準
- `rebuilt<=` が `built` の大半 / `clears` が頻発 → キャッシュ方式(LRU・上限)を検討。ほぼ 0 なら上限は触らない。
- 窓の `systems` の `clears` が多い（上限 768）なら窓だけ上限・追い出しを見直す。
- phase の `field` が最大なら `computeField` の早期除外を優先。

`scratch_rcon.py` は git 管理外（`.claude/worktrees/zen-gagarin-d3ecfb/tools/`）。

`tools/chunkgen_bench.sh PORT OUT`: 3 地点 × (冷 6x6 → 同条件の隣接 6x6) を回して `caves stats` を OUT に記録する。

## 注意（2026-10-11 の実測から）
- 並列生成のため、同一コードでも生成結果は完全一致しない: 基準 vs 基準の A/A で 12 チャンク 538 ブロック差（基準 vs 変更後 10 チャンク 507 ブロック）。`mcadiff` の「0 差分」は合格基準にできない。差のチャンク数・ブロック数が A/A と同程度かで判断する（単スレッド化できるなら完全比較）。
- 「温まった」でも wall は冷より約半分: JIT の影響が大きいので、変更前後は必ず同じ順序で比べる。
- 洞窟パイプライン(CaveGenerator.generate)は JFR で全サンプルの約 14%、`computeField` は約 4%。Beardifier(バニラ)が自己時間約 18%。
