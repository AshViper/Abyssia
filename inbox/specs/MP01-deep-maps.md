# MP01 深海の地図・深海への海図
tier: standard
files: (Forge) src/main/java/com/abyssia/map/** (新規), src/main/java/com/abyssia/registry/ModItems.java (登録 2 行), tools/map_assets.py (新規、gen_deep_assets から呼ぶ), tools/gen_deep_assets.py (呼び出し 1 行), tools/guide_text.py (EXTRA ページ), data/abyssia/tags/worldgen/biome/deep_entrances.json, textures/item/deep_sea_map.png・abyss_chart.png (ChatGPT シート MP01)
goal: 深海層の海底を普通の地図として描く「深海の地図」と、地上で最寄りの深海への入口を赤い ✕ で示す宝の地図風「深海への海図」を追加する。
constraints: Mixin なし。Forge 1.20.1 と NeoForge 1.21.1 の両方。地図表示はバニラの filled_map を使う (自前 MapItem は手持ち・額縁で描かれないため)。地図描画・入口探索でチャンクを生成・強制ロードしない。
accept: 下の「受け入れ条件」。

出典: ChatGPT 仕様書 (2026-10-04, chat 6ac1bf02)。技術方針は Decision Agent (MODIFY) の結果を ChatGPT に渡して固定した。
ChatGPT の文で実装上あいまいな所は「Claude 補足」として書いた。

---

## A. 深海の地図

| 項目 | 値 |
|---|---|
| アイテム | `abyssia:deep_sea_map` (深海の白地図 / Empty Deep Sea Map) |
| 使用後 | バニラ `filled_map`、名前「深海の地図 / Deep Sea Map」、NBT `display.MapColor` = 暗いシアン |
| 縮尺 | scale 2 (1 ピクセル = 4×4 ブロック、512×512 ブロック) |
| 中心 | 使用したプレイヤーの位置。バニラと同じ格子にスナップ |
| 地図データ | 作成時から **ロック済み** (バニラの更新で上書きされない)。`DeepMapIndex` (SavedData) に地図 ID を登録 = 正 |
| ツールチップ | 「ロック済み / Locked」行を隠す。白地図: 「深海層の海底を記録するための白地図」「深海層で使用すると海底の地形を描きます」/ "An empty map for charting the seafloor of the deep sea" "Use it in the deep sea layer to map the seafloor" |

**描き込み** (サーバーのプレイヤーティック END、地図 ID ごとに 1 tick 1 回):
- 手 / オフハンドに持っている間 (Claude 補足: インベントリ内は対象外。バニラも手持ち時のみ更新)。
- 予算 約 256 列 / tick / 地図 (= 16 ピクセル)。プレイヤー周辺 (バニラと同じ半径) をストライプ順に巡回。
- ロード済みチャンクのみ (`getChunkNow`)。未ロードは後の tick で。チャンク生成・強制ロード禁止。
- 海底 = `DeepLayer.floorY` (天井の下から下へ、水の下の最初の動きを止めるブロック)。ピクセルの高さ = 4×4 の 16 列の平均、キャッシュ。
- 色 (検査後の確定仕様、MP01-review.md): 16 列のブロックの MapColor を 8 カテゴリ (岩 / 暗い岩 / 黒い岩 / 白 / 土砂 / 植物 / 青・シアン / 赤系アクセント) にまとめて最頻を採用 → 周囲 8 ピクセル中 5 以上が別の同じカテゴリなら置き換え (3×3 多数決) → 明暗:
  - 水深 (Claude 補足: 基準面 = 天井の下面 `DeepLayer.CEILING_BOTTOM_Y`、深さ = 基準面 − 海底) を **5 段階**。深いほど暗い。黒くつぶさない (元の色が分かる範囲)。
  - 北隣との高低差 **3 段階** (高い = 明 / 差が小さい = 中立 / 低い = 暗)。
  - 合成後の明暗は MapColor の 4 段 (LOW / NORMAL / HIGH / LOWEST) に収める。
- 深海層の外で使ったとき: 地図は作る (中心はプレイヤー)。地上は描かない (空白のまま)。メッセージ「深海の地図は深海層で使用してください。」/ "The Deep Sea Map must be used in the deep sea layer."。その後、深海層で持てば描き始める。
- 額縁・製図台コピー・再起動後の保持はバニラどおり (ロック済みは製図台で拡大されない)。

## B. 深海への海図

| 項目 | 値 |
|---|---|
| アイテム | `abyssia:abyss_chart` (深海への海図 / Abyss Chart)、使い捨て |
| ツールチップ | 「近くの深海への入口を探す海図」「使用すると最寄りの入口を示します」/ "A chart that searches for a nearby entrance to the deep sea" "Use it to locate the nearest entrance" |
| 対象 | バイオームタグ `#abyssia:deep_entrances` = `abyssia:deep_fissure`, `abyssia:abyssal_rift` (タグに足すだけで対象が増える) |
| 半径 | 水平 3000 ブロック |
| クールダウン | プレイヤーごと 30 秒。検索の完了時に確定 (失敗でも 30 秒) |

**探索:**
- メインスレッドでバイオームソースと sampler を取り、`Util.backgroundExecutor` で `findClosestBiome3d` (水平ステップ 64、Y 固定)。終わったら `server.execute` でメインへ戻す。
- 「地形生成なし」= ノイズを直接サンプルし、チャンクを生成しない (Claude 補足: ChatGPT 文の「生成済みチャンクのみ参照」は、チャンクを生成しないという意味で解釈する)。
- 候補を最大 32 点の `getBaseHeight(OCEAN_FLOOR_WG)` で絞り、スリットが開いている (海底が `DeepLayer.TOP_Y` より下) 列だけ採用。最も近いもの、同距離なら座標の小さい方。
- 同じプレイヤーの検索は重複起動しない。

**完成した地図:**
- バニラ `filled_map` (scale 2、プレイヤー位置を追跡、範囲外でも端に表示)、中心 = 入口付近。
- `renderBiomePreviewMap` (宝の地図のプレビュー塗り) + 入口に赤い ✕ (`addTargetDecoration` RED_X)。
- 名前「深海への海図」/ "Abyss Chart"、MapColor は宝の地図に近い色。
- 見つかったとき: 海図 1 個を消費して地図 1 個を渡す (手にまだ海図があるときだけ)。

**メッセージ (日 / 英):**
- 検索中: 「深海への入口を探しています……」/ "Searching for an entrance to the deep sea..."
- 失敗 (消費しない): 「近くに深海への入口が見つかりません。」/ "No entrance to the deep sea could be found nearby."
- クールダウン中: 「深海への海図はまだ使用できません。」/ "The Abyss Chart cannot be used yet."

## レシピ (shaped)

```
deep_sea_map          abyss_chart
D P G                 S P L
P C P                 P C P
G P D                 L P S
D = abyssia:deep_fiber, G = abyssia:deep_pigment, S = abyssia:sea_cloth, L = abyssia:lumen_gel, P = paper, C = compass
```

## テクスチャ
アイテムアイコン 2 つだけ (ChatGPT シート MP01、`inbox/prompts/MP01-textures.md`、texture_locks に固定)。

## ガイドブック 1 ページ
**深海の地図** — 深海層を探索するときは「深海の白地図」を使いましょう。深海層で使用すると、地上では見ることのできない海底の地形が地図に記録されます。水深や海底の高低差も陰影で確認できます。深海への入口を探すときは「深海への海図」が便利です。使用すると、近くの深海への入口を探して地図に示します。入口が見つからない場合、海図は消費されません。

**Deep Sea Maps** — Use a Deep Sea Map when exploring the deep sea. When used in the deep sea layer, it records the seafloor that cannot be seen on ordinary maps. Water depth and changes in seafloor elevation are shown through map shading. When looking for a way into the deep sea, use an Abyss Chart. It searches for a nearby entrance and marks its location on the map. If no entrance can be found, the chart is not consumed.

## 受け入れ条件 (テストサーバー)
- A: 深海層で使うと海底 (地上ではない) が描かれ、移動で未描画部分が埋まる。水深 5 段 / 高低差 3 段の明暗。ツールチップに Locked がない。手持ち・額縁・製図台コピー・再起動後も保持。深海層外では空白 + メッセージ、深海層に入ると描き始める。
- A 性能: 地図を持っていても未生成チャンクが増えない、tick 時間の増加 < 1 ms。
- B: 既定ワールド (deep_fissure) と Abyssia Ocean (abyssal_rift) の両方で、✕ が実際に開いたスリットの上にある。メインスレッドが止まらない。失敗時は消費しない + メッセージ。30 秒クールダウン、重複検索なし。複数人同時でも壊れない。
- Forge 1.20.1 と NeoForge 1.21.1 の両方で同じ結果。
