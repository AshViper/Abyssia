Abyssia (Minecraft Forge 1.20.1 深海Mod、NeoForge 1.21.1 にも移植) の設計担当として、CT01 の仕様書を作ってください。実装は Claude。

## 依頼 (ユーザー原文)
拠点ブロックのテクスチャの連結: 拠点のガラスのようにほかのブロックも繋げてきれいな見た目になるようにして

## 既存の前提
- 拠点の殻ブロック (ModHabitat、建設装置が配置、アイテム無し・回収不可): habitat_floor / habitat_trim / habitat_wall / habitat_ceiling / habitat_light (光源15) / habitat_door_frame = 素の Block + cube_all。habitat_hatch (FACING、北南面だけ hatch テクスチャ)、habitat_door (DoorBlock)、habitat_support (8px の柱)。
- habitat_window (連結済みの見本): AbstractGlassBlock + 6方向 boolean。同じブロックが隣にあると true。blockstate は 64 variants、面ごとに habitat_window_c<mask> テクスチャ (mask 1=上 2=下 4=左 8=右、外から見て)。15 枚の連結テクスチャは tools/habitat_window_ctm.py が元画像から自動生成 (3px の枠帯を連結側だけ消す) し、tools/texture_locks に固定。
- 水耕プランター (PL01) は別方式: multipart で、ベースの立方体 + 面ごとの 2px 枠ストリップ 24 枚を「その面が露出していて、その辺の隣がプランターでない」ときだけ重ねる。
- モデル・blockstate はすべて tools/habitat_assets.py が生成 (手で編集しない)。テクスチャは ChatGPT 作の既存画像 (変えない)。

## 決めてほしいこと (短く、Claude がそのまま実装できる粒度)
`# CT01 <title>` / tier / files / goal / constraints / accept と:
1. 連結の対象ブロック一覧 (床・壁・天井・トリム・照明など、どれを連結し、どれをしないか)
2. 連結の単位: 同じブロック同士だけか、壁と天井のように別ブロックでもつながるグループがあるか
3. 方式: ガラスと同じ「面ごとの連結テクスチャ (c1..c15) + 64 variants」か、プランターと同じ「multipart の枠オーバーレイ」か。理由も一言
4. 連結したときの見た目: 何を消して何を残すか (枠・リベット・パネル継ぎ目など)。元テクスチャから自動で作れるルールにすること
5. 性能の注意 (モデル数・blockstate 数、殻はアイテムが無いので item model は不要か)
6. accept: 確認方法 (建設装置で作ったルームの内外のスクリーンショットで判定できる条件)
