# OL01 栽培できる生体油昆布 (oil_kelp)

依頼 20261003-154431。設計は ChatGPT (原文 `OL01-PL01-chatgpt-raw.md` の OL01)。Claude 補足は末尾。
tier: standard
files: block/OilKelpBlock.java (新規), ModBlocks.java / ModItems.java の oil_kelp・oil_sac・oil_kelp_seed 登録行, tools/gen_deep_assets.py (名前・モデル・ルート・焼成レシピ), tools/gen_worldgen.py (自然生成), テクスチャは OIL1 シート (main が取込)
goal: 浅めの海底で bio_oil を栽培できる。oil_bladder_weed は変えない。

- ブロック oil_kelp / 生体油昆布 / Oil Kelp。水中専用、上向きに 1 ブロックずつ伸びる (最大 8)。下向きには伸びない。
- 各節に `ripe` (boolean)。未熟の節は random tick ごと 5% で熟す。伸びる判定も random tick (バニラ昆布と同程度)。
- 熟した節を右クリック → oil_sac 1 個 (確定)、その節は未熟に戻る。破壊: 熟した節は oil_sac 1、どの節も oil_kelp_seed 5%。
- oil_sac / 油嚢 / Oil Sac: 食べられない素材。かまどで焼くと bio_oil x2 (200 tick)。
- oil_kelp_seed / 油昆布の種 / Oil Kelp Seed: 水源の中、固体の海底の上に植えると oil_kelp (長さ 1) になる。
- 骨粉: 1 回で 1 回分の成長抽選 (伸びるか、未熟の節を 1 つ熟させる)。
- 自然生成: Y -250..-64 (深さフィルタはメートル換算)、深海系の海底、1 チャンク平均 0〜2 本、長さ 4〜6、一部の節は最初から熟している。
- bio_oil の既存の用途・燃料値、oil_bladder_weed の生成・ドロップは変えない。

## テクスチャ (OIL1 シート)
oil_kelp_base (根元の節), oil_kelp_middle (茎の節, 未熟), oil_kelp_ripe (油嚢が付いた節), oil_sac (アイテム), oil_kelp_seed (アイテム)。ブロックは cross モデル (cutout)。最上段も middle/ripe を使う。

## Claude 補足
- ChatGPT の files 欄 (ModRecipes.java, 手書き JSON) は実構成に合わせ、生成物は gen_deep_assets / gen_worldgen が書く。
- 「ブロック破壊で未熟節は種を低確率」と「種は破壊時 5%」は同じ 5% にまとめた。
