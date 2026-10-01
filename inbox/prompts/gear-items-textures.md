# gear-items — 水中装備アイテムのテクスチャ作り直しプロンプト

ChatGPT ImageGen 用（ユーザー依頼により ChatGPT 生成に切替。M03 当初は ChatGPT 不使用だったが本依頼が優先）。
各項目を個別に生成し、PNG を指定先へ保存する。Claude は画像を生成しない。

共通条件: Minecraft 1.20.1 の 16×16 アイテム用ピクセルアート。透明背景、中央に単一アイコンのみ。
影・シーン・文字・枠・余白・複数アイテムなし。16px でも判読できる太めのシルエット、ピクセル境界を明瞭に。
合金部の限定色パレット: #26343a → #45656a → #70a4a1 → #b5d4c8 → #e6f2df（深海の青錆ティール）。
柄は暗い木の茶 (#49392a → #806044 → #b18a5b)。既存 Minecraft のダイヤ・ネザライト装備の複製にしない独自意匠。

## abyssal_alloy_pickaxe

主題: 深海合金のつるはし。ティールグレーのヘッド、茶の柄。斜め構図。
inbox/textures/abyssal_alloy_pickaxe.png に保存。

生成指示: 共通条件を守り、上記の道具が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## abyssal_alloy_axe

主題: 深海合金の斧。幅広のティールグレーの刃、茶の柄。斜め構図。
inbox/textures/abyssal_alloy_axe.png に保存。

生成指示: 共通条件を守り、上記の道具が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## abyssal_alloy_shovel

主題: 深海合金のシャベル。丸みあるティールグレーの scoop、茶の柄。斜め構図。
inbox/textures/abyssal_alloy_shovel.png に保存。

生成指示: 共通条件を守り、上記の道具が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## abyssal_alloy_hoe

主題: 深海合金のクワ。ティールグレーの刃、茶の柄。斜め構図。
inbox/textures/abyssal_alloy_hoe.png に保存。

生成指示: 共通条件を守り、上記の道具が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## abyssal_alloy_sword

主題: 深海合金の剣。波形のティールグレーの刀身、茶の柄と小さな鍔。斜め構図。
inbox/textures/abyssal_alloy_sword.png に保存。

生成指示: 共通条件を守り、上記の武器が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## deep_diver_helmet

主題: 深海探検家のダイバーヘルメット。暗いティールグレーの兜、正面に発光シアン (#4d98a2) の丸い覗き窓。
inbox/textures/deep_diver_helmet.png に保存。

生成指示: 共通条件を守り、上記の防具が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## abyssal_flippers

主題: 深海合金のフィン（ブーツ1足分を1アイコンに）。ティールグレーの足ひれ、足首バンド付き。横向き。
inbox/textures/abyssal_flippers.png に保存。

生成指示: 共通条件を守り、上記の防具が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

## abyssal_alloy_ingot

主題: 深海合金のインゴット。上面に明るいハイライトのある台形の金属塊、ティールグレー。
inbox/textures/abyssal_alloy_ingot.png に保存。

生成指示: 共通条件を守り、上記の素材が 16×16 アイテムアイコンとして一目で分かるピクセルアートを作る。

---

## 取込手順 (Claude 用メモ)

- `import_chatgpt_textures.py` はブロック用（不透明化・タイリング）なのでアイテムには使わない。
- 取込は中央正方形 crop → 16×16 NEAREST 縮小 → アルファ保持のまま
  `src/main/resources/assets/abyssia/textures/item/<id>.png` に上書き。モデル JSON は既存のまま流用。
- rock 系 texture_locks には触れない（今回対象外）。
