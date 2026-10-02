# 工業ブロック テクスチャ (I01) — ChatGPT ImageGen 用

2枚のシート。保存先 `inbox/textures/sheets/IND1.png` / `IND2.png`。Claude は画像を生成しない。
取込: `python tools/agentflow/sheets.py import auto --preset IND1` (IND2 も同様)。ロックされる。

## 共通プロンプト
```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft (iron block, blast furnace, smoker, observer, copper). Each texture is ONE opaque tile in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic view of one block face filling the whole square, no perspective, no 3D render.
Limited palette of 4-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no soft glow halo, no drop shadow. Draw at exactly 16x16 logical pixels (square pixels, integer scale). Never use magenta inside a tile.
Theme: deep-sea research base / pressure-proof heavy industry. Dark gunmetal, blue-grey steel, dark teal; bolts light grey; glowing parts cyan (#3ee6f0, #b8f8ff); small amounts of warning yellow/orange (#e8a020, #ff7a1a). Not rusty, not bright steampunk.
Palette guide: #14181e #232a33 #343e4a #4a5866 #6c7c8a #9aa8b4 (metal), #0f3a40 #1d6670 (teal), #3ee6f0 #b8f8ff (glow), #e8a020 #ff7a1a (warning).
```

## シート IND1 (10個 / 5列)

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. industrial_panel: 工業用金属パネル。厚い耐圧鋼板、四隅にボルト、中央に浅い継ぎ目、タイル状に繋がる。落ち着いた広い面
2. metal_grating: 金属グレーチング床。格子状の鋼材、格子の穴は純黒 #000000 で塗る (後で透明にする)。外周は細い枠
3. industrial_beam_side: 工業用梁の側面。縦に通るI形鋼、両端に補強板とボルト、縦方向に繋がる
4. industrial_beam_end: 梁の断面 (木口)。I字の断面形、周囲は暗い鋼
5. industrial_pipe: 太い配管の表面。横に通る円筒をなめた面、継ぎ目のフランジ帯が1本、暗いティールの塗装
6. industrial_valve: 配管バルブの正面。中央に丸いハンドル (暗い赤 #8a2a20 か橙)、周囲はフランジとボルト
7. work_light: 作業灯の発光面。保護ケージの格子の奥に白〜淡いシアンの光る板
8. warning_light: 警告灯の発光面。橙〜黄の光るレンズ、黒と黄の斜線の縁
9. energy_cable: 細い電力ケーブルの表面。ダークガンメタルの外装に中央を通る細いシアンの芯線、横方向に繋がる
10. reinforced_energy_cable: 強化ケーブルの表面。太めの外装、等間隔の金属バンド、中央に明るいシアンの芯線
```

## v2 方針 (2026-10-02、機械は立方体ブロックに決定後に描き直し)
7台共通の筐体 (同じ太さの外枠・角のボルト・同じ明暗方向) で、並べると一列の設備に見える。個性は正面の大きな中央機構 (8px 以上) とアクセント色1色。側面は落ち着いたパネルに識別ディテール1つ、上面はハッチ/排気口。正面稼働版は停止版と同一で発光ピクセルだけ明るく。共通底面 machine_bottom。1マス=正方形。
旧 IND2 (立体モデル前提の v1) は inbox/textures/sheets/IND2.png。

## シート IND2A (16個 / 4列×4行)

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）: 4列×4行、各行は 正面(停止)/正面(稼働)/側面/上面。7台共通の筐体デザイン (同じ外枠・角のボルト・同じ明暗方向)、正面に 8px 以上の中央機構、アクセント色1色。
行1 粉砕機: 円形の粉砕ローター+シアンの指示灯 / 稼働でローター中心がシアン / 側面に小さな歯車 / 上面=四角い原料投入口
行2 精錬炉: 縦長の炉と小窓 / 稼働で窓が橙〜白 / 側面に配管と圧力バルブ / 上面=排熱スリット
行3 合金炉: 円形の炉と中央の発光コア / 稼働でコアがシアン〜白 / 側面に太い配管 / 上面=材料投入口
行4 高温炉: 厚い炉扉と耐熱観測窓、温度計 / 稼働で窓が白〜淡い橙 / 側面に耐熱配管 / 上面=排気口と放熱フィン
```

## シート IND2B (13個 / 4列×4行、行4は1個)

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）: IND2A と同じ筐体デザイン。各行は 正面(停止)/正面(稼働)/側面/上面。
行1 熱水発電機: 大型の円形タービンとシアンの発光リング / 稼働でリングとタービン中心が明るい / 側面に太い熱水配管とバルブ / 上面=熱気の排出グリル
行2 補助発電機: 小型の回転機構と燃料インジケータ、少し汚れた無骨な外装 / 稼働でインジケータが橙 / 側面に排気管 / 上面=燃料投入口のふた
行3 エネルギー装置(蓄電池): 円形のエネルギーコア (停止中は暗いティール) と下の横長ゲージ / 稼働でコアとゲージが明るいシアン / 側面に厚い装甲と冷却フィン / 上面=発光ラインと点検ハッチ
行4 1マス目のみ: 全機械共通の底面 machine_bottom (同じ外枠、中央は脚・補強リブのある暗い底板)。残り3マスは空
```

## シート I02 (4個 / 2列×2行)

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）: I01 v2 機械と同じ共通筐体。正面(停止)/正面(稼働)/側面/上面。選択浸出分離機: 正面中央に円形の浸出・分離槽 (液体コア)、アクセントは青紫、稼働で中央がシアン〜青紫に発光、側面に短い配管、上面に薬液投入口
```

## シート I02ITEM (1個)

```
（共通プロンプト）
アイテムアイコン 1 個: 酸性浸出試薬。小さな耐圧ガラス瓶/アンプルに黄緑〜琥珀色の酸性薬液 (硫黄由来)、金属キャップ。1px の暗い輪郭、4〜6色
```
