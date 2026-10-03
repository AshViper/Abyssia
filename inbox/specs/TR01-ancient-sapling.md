# TR01 水中で育つ古代樹の苗 (ancient_sapling)

依頼 20261003-152705。設計は ChatGPT (原文: `FD01-TR01-CB01-chatgpt-raw.md` の TR01)。Claude 補足は末尾。
tier: standard
files: src/main/java/com/abyssia/block/AncientSaplingBlock.java (新規), src/main/java/com/abyssia/worldgen/** の小型古代樹 feature (新規クラス + 登録), ModBlocks.java / ModItems.java の苗登録行, tools/gen_deep_assets.py (苗の名前・モデル・ルート、ancient_frond のルート), tools/gen_worldgen.py (小型古代樹の自然生成), テクスチャは TREE1 シート (main が取込)
goal: 水中に植えると育つ ancient_sapling で、既存 ancient_stem / ancient_frond の小型木を作り、深海で木材を継続して集められる。
constraints: 新しい木材セットは作らない。ancient_stem / ancient_frond の性質・テクスチャは変えない (frond のドロップ追加だけ)。生成物を手で書かない。

## 苗
- ID ancient_sapling / 古代樹の苗 / Ancient Sapling。苗はブロック、アイテムは BlockItem (sprite はブロックテクスチャ、cross モデル)。
- 入手: ancient_frond を壊すと 5% で 1 個 (シルクタッチに関係なく。frond 自身のドロップ規則は変えない)。
- 植えられる: 水中 (水源) のみ。下が土・砂・砂利・粘土・深海岩などの固体の上。空気中は不可 (waterlogged 固定の水中植物として実装)。
- 成長: random tick。光は不要 (光量0でも育つ)。深海層 (Y -368..-64) でのみ育つ。平均 20〜40 分 (stage 0→1→木、苗木と同じ 2 段階、1 tick あたりの確率で調整)。
- 骨粉: 使える (バニラ苗と同じく 45% で段階が進む)。
- 成長後の木: 幹 ancient_stem (axis=y) 高さ 5〜9、太さ 1。葉 ancient_frond を上部 2〜3 層、横幅最大 5。木の置き場所に水・置き換え可能な植物以外のブロックがあれば成長しない (何も壊さない)。水中に置くブロックは waterlogged にできるものは waterlogged にする。

## 自然生成
- 深海系バイオーム (カスタムバイオームのすべての海底)、深さ Y -300..-80 中心、1 チャンク平均 0〜2 本 (低密度、既存の巨大 ancient 構造とは別)。
- 苗と同じ形の小型木を feature として出す (苗の成長と同じコードを使う)。

## Claude 補足
- ChatGPT の files 欄のパスは実際の構成 (registry/ModBlocks.java, gen_worldgen.py が placed_feature とバイオームの植生リストを書く) に合わせた。
- ChatGPT は「Y=-300..-80」と書いたが、深海層はワールドでは Y -368..-64 (DeepLayer SHIFT -240)。gen_worldgen の深さフィルタ (abyssia:depth、メートル) に換算して使う。
