# PL01 水耕栽培プランター (hydro_planter)

依頼 20261003-154551。設計は ChatGPT (原文 `OL01-PL01-chatgpt-raw.md` の PL01)。Claude 補足は末尾。
tier: standard
files: block/HydroPlanterBlock.java, block entity / menu / screen クラス (新規、既存の locker / industrial の配置に合わせる), ModBlocks / ModItems / ModBlockEntities / ModMenus の登録行, tools/gen_deep_assets.py (名前・ブロックステート・モデル・ルート・レシピ), tools/planter_gui.py (新規、GUI 画像生成、tools/locker_gui.py に倣う), テクスチャは PLANT1 シート (main が取込)
goal: 水の無い拠点の中でも食材 (mushroom_cap / gourd_flesh / kelp_leaf) を育てられる。並べると枠が消えて 1 つの大きなプランターに見える。

- hydro_planter / 水耕栽培プランター / Hydro Planter。水・FE は不要 (受け付けない)。普通に壊して回収できる。
- 右クリックで 1 スロットの GUI。入れられる「苗」と収穫:
  | 入力 (アイテム) | 収穫物 | 量 | 成長時間 |
  |---|---|---:|---:|
  | abyssia:abyssal_mushroom | abyssia:mushroom_cap | 1〜2 | 8 分 |
  | abyssia:pressure_gourd | abyssia:gourd_flesh | 1〜2 | 12 分 |
  | abyssia:deep_kelp | abyssia:kelp_leaf | 2〜3 | 6 分 |
- 苗が入っていると成長が進む (GUI を閉じても、チャンクが読み込まれていれば進む)。収穫できる状態で右クリック → 収穫物が手に入り (インベントリへ、入らなければ足元にドロップ)、成長 0% に戻る。苗は消費しない。成長中の右クリックは GUI を開くだけ。
- 骨粉を持って右クリック: 1 回で成長時間の 10% 分進む (骨粉 1 消費)。
- 苗は 1 個だけ入る (スロット上限 1)。GUI に成長の進行バーを表示。壊すと苗をドロップ。
- 繋がる見た目: blockstate に north/south/east/west/up/down の接続 (隣が hydro_planter なら true)。各側面の枠は 4 辺のパーツに分け、その辺の向こうに同じプランターがあれば描かない (側面の左右 = 水平の隣、上下 = up/down)。上面・底面の枠も同じく水平の隣で消す。上面は栽培面が連続して見える。接続は見た目だけで、インベントリ・成長は各ブロック独立。multipart + 枠パーツモデルで実装し、テクスチャを組み合わせ数だけ作らない。
- 見た目の状態: blockstate `crop` (none/mushroom/gourd/kelp) と `ripe` で上面に作物が見えるとよい (任意。作るなら上面に小さな cross パーツ。テクスチャは既存の植物テクスチャを流用)。
- レシピ: `IPI / G G / IPI` (I 鉄インゴット, P ガラス板, G organic_matter) → 1。

## テクスチャ (PLANT1 シート)
hydro_planter_side (枠なしの側面: 暗いガラス越しに栽培槽), hydro_planter_top (枠なしの上面: 水耕トレイ・栽培面), hydro_planter_bottom (底), hydro_planter_frame (枠だけ、中は透明。四辺の金属フレーム)。GUI は tools/planter_gui.py で生成 (既存の locker / industrial GUI と同じ作り方)。

## Claude 補足
- ChatGPT の inner は top に、gui は既存の GUI 生成ツール方式に置き換え、枠を消すために frame を別テクスチャにした (ChatGPT の「テクスチャを大量に作らず model の切替で」に合わせるため)。
- ChatGPT の「上下左右」は、側面ごとに見て左右 = 水平の隣、上下 = 上下の隣と解釈した。前後方向 (面の奥) はつながらない。
