# CT01 拠点殻ブロックの連結テクスチャ
(ChatGPT 2026-10-03 作成。request 20261003-160209。「Claude 注」は Claude が補った部分)

tier: standard
files: tools/habitat_assets.py / tools/habitat_ctm.py / tools/texture_locks/habitat_ctm/ / src/main/resources/assets/abyssia/blockstates/habitat_*.json / src/main/resources/assets/abyssia/models/block/habitat_*.json / src/main/resources/assets/abyssia/textures/block/habitat_*_c*.png
Claude 注: 6方向 boolean を持つブロッククラスが必要なので src/main/java/com/abyssia/habitat/HabitatConnectedBlock.java (新規) と src/main/java/com/abyssia/registry/ModHabitat.java も対象に含める。

goal: 拠点殻ブロックを、同じ系統の隣接ブロック同士で枠や継ぎ目が自然につながる見た目にする。既存の habitat_window の連結方式を基本にして、建設装置で生成したルーム全体がつながって見えるようにする。

constraints: 連結対象は habitat_floor / habitat_trim / habitat_wall / habitat_ceiling / habitat_light。habitat_door_frame / habitat_hatch / habitat_door / habitat_support は対象外。連結するのは同じブロック同士だけで、種類の違う殻ブロックとは連結しない。方式は habitat_window と同じく、面ごとの c1..c15 テクスチャと 6方向 boolean による 64 variants にする。既存の生成・テクスチャ固定の運用をそのまま使え、モデル構造も単純に保てるため。連結した方向は元テクスチャの外周の枠帯だけを消し、中央のパネル・リベット・内部のパネル継ぎ目は残す。枠帯を消す範囲は habitat_window_ctm.py と同じ考え方で決め、元画像から機械的に作れるルールにする。variant ごとに手描きで調整しない。blockstate/model は今までどおり habitat_assets.py が生成し、生成物を手で編集しない。殻ブロックはアイテムが無く回収もできないので item model は追加しない。64 variants をブロックごとに持つことになるため、対象をむやみに増やさず、既存の6方向判定の方式を守る。

accept: 建設装置で床・壁・天井・トリム・照明を含むルームを作り、内側と外側のスクリーンショットで次を確認する。同じブロックが続く面は外周の枠が途切れて一枚のパネルに見え、連結しない方向には元の枠が残っている。床・壁・天井の境界、照明が続く部分、同じブロックが 2×2 以上並ぶ所で、二重の枠・リベットの欠け・テクスチャの穴が出ていない。habitat_door_frame / habitat_hatch / habitat_door / habitat_support は今までどおり独立した見た目のまま。生成スクリプトで再生成しても、同じ blockstate/model/テクスチャ構成になる。
