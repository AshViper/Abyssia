Abyssia (Minecraft Forge 1.20.1 / NeoForge 1.21.1 の深海Mod、深海層は Y -368..-64) の設計担当として、3件の仕様書を作ってください。実装は Claude。各仕様書は「# <id> <title>」「tier」「files」「goal」「constraints」「accept」の形式で、Claude がそのまま実装できる粒度で短く。3件は files を重ねないこと。

## 依頼1 FD01 (ユーザー原文): 食料関連を30種類追加 — キノコや植物から料理をたくさん作れるようにしてほしい
既存: 食べ物は魚肉6種の生/焼き (例 abyssal_fish_fillet 2/0.3 → cooked_abyssal_fish 6/9.6、shark_flesh 3/0.3 → cooked 8/12.8、一部に効果: 暗闇/毒/水中呼吸/再生/暗視 確率付き) だけ。植物の食べ物は無い。植物は収穫で素材を落とす: deep_fiber (草/海藻)、plant_resin、resin、hard_stalk、lumen_gel (abyssal_mushroom 等の発光植物)、bio_oil、crystal_sap、organic_matter、deep_pigment、thermal_fiber (噴出孔植物)、hadal_husk (pressure_gourd、6000m以深)。クラゲの触手 (jelly/atolla/phantom/deepstaria_tentacle) は素材。調理は かまど/燻製器/焚き火 + 作業台。拠点に壁掛け作業台あり。
Claude 案: 植物から食用の「食材」を数種落とす (例: 深海キノコの傘、ゴード果肉、海藻の葉) か既存素材を食材に使い、それらと魚肉を組み合わせた料理 (焼き物/スープ(ボウル)/串/パイ/保存食など) で合計30種。食材の入手元 (どの植物の収穫/破壊で何%) も決めてほしい。
出してほしいもの: 30種の表 (id / 日本語名 / 英語名 / 満腹度 / 隠し満腹度 / 効果(任意,確率) / レシピ(形 or 不定形 or 調理種別) / 容器返却(ボウル等))、新規食材の表 (id/名前/入手元/確率)、バランス方針 (バニラ料理と比べて)、必要テクスチャ一覧 (16x16 アイテム)。

## 依頼2 TR01 (ユーザー原文): 水中で育てれる木を実装 — 木材系の収集ができないので入手難易度を落としたい
既存: 深海層で取れる木は「ancient」セットだけ。ancient_stem (原木, 斧で剥げる), ancient_frond (葉), ancient_root、板材 ancient_planks と階段/ハーフ/フェンス/ゲート/ドア/トラップドア/感圧板/ボタン (WoodType CRIMSON 扱い、燃えない、logs/planks タグ入りなので作業台・棒などは作れる)。しかし洞窟の巨大構造 (高さ20〜70) でしか生成されず、苗木も成長も無い。hard_stalk から棒は作れる。植物は ResourcePlantBlock (水中植物、ランダムtickで実る) がある。
Claude 案: 水中に植えて育つ苗木「ancient_sapling」を追加 (ancient_frond を壊すと低確率で落とす + 海底に小さめの自然木を新たに生成)。水中 (水源ブロック内、waterlogged) でのみ成長し、成長すると高さ5〜9程度の小型 ancient 木 (stem + frond) になる。骨粉対応。既存 ancient セットの再利用でブロック追加を最小に。
出してほしいもの: 苗木の仕様 (ID/名前/入手元と確率/植えられる場所/成長条件と時間/骨粉/光の要否)、成長後の木の形 (サイズ・葉の付き方)、自然生成 (どのバイオーム・深さ・密度)、ancient_frond のドロップ変更、別の新木セットにすべきならその理由、必要テクスチャ一覧。

## 依頼3 CB01 (ユーザー原文): 深海の石材で丸石を追加 — 丸石で取れるようにして、かまどとか石ツールの素材につかえるように
既存: 深海岩 deep_sea_rock(硬さ1.8) / abyssal_rock 2.5 / trench_rock 3.0 / thermal_rock / volcanic_rock / crystal_rock / mineral_host_rock の7種に石材ファミリー (polished/bricks/cracked/chiseled + 階段/ハーフ/塀、石切台) あり。ほかに噴出孔岩・洞窟岩・B02岩 (ancient_masonry, fossil, salt, lumen, frozen) 等。いま全岩は「自分自身」を落とし、stone_tool_materials / stone_crafting_materials タグに入っていないので、深海では石ツールもかまども作れない。岩テクスチャは固定 (texture_locks) で変更禁止。
Claude 案: 主要岩ごとに丸石 cobbled_<rock> を追加 (少なくとも上の7種)、岩はシルクタッチ以外で丸石を落とす、丸石を焼くと元の岩、丸石を minecraft:stone_tool_materials / stone_crafting_materials タグに入れてバニラのかまど・石ツール・石の道具類を作れるようにする、丸石の階段/ハーフ/塀も追加。
出してほしいもの: 対象岩の一覧と丸石 ID/名前、ドロップ規則 (シルクタッチ・噴出孔/洞窟岩の扱い)、レシピ (焼成・石切台・ファミリー)、タグ、必要テクスチャ一覧 (元の岩テクスチャの色調を保った丸石柄)。

各仕様書の最後に「質問」欄は不要。不明点は妥当な既定値で決めてください。