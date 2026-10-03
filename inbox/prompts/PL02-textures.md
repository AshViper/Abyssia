# PL02 プランター作り直し テクスチャ — ChatGPT ImageGen 用

仕様: inbox/specs/PL02-planter-remake.md。取込: `python tools/agentflow/sheets.py import auto --preset PLT1` / `PLT2`。

## 共通プロンプト
```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft. Each texture is ONE opaque tile in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic view of one block face filling the whole square, no perspective.
Limited palette of 4-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no glow halo, no drop shadow. Exactly 16x16 logical pixels. Never use magenta inside a tile.
Theme: deep-sea habitat hydroponics (dark gunmetal and blue-grey steel, teal accents, dark nutrient substrate).
```

## シート PLT1 (3個 / 3列×1行)
```
（共通プロンプト）
このシートの内容（左→右）:
1. hydro_planter_top: 下側ハーフブロックの水耕プランターの上面。外周 2px の暗いガンメタルの縁、内側は暗い青緑の培養土/培地が 2x2 の 4 区画に十字の細い金属の仕切りで分かれている
2. hydro_planter_side: 側面。下半分 (y8-15) だけ使う 8px 厚の鋼の側板、上端に細いティールの帯、小さなボルト。上半分 (y0-7) も同じ柄で埋めてよい
3. hydro_planter_bottom: 底面。暗い鋼板、四隅にボルト
```

## シート PLT2 (9個 / 3列×3行)
```
Minecraft 1.20.1 plant sprite, hand-drawn 16x16 pixel art in vanilla style (like wheat/carrot crop stages), each sprite in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters, plant grows from the bottom edge, no labels, no text. Limited palette, hard pixel edges, no anti-aliasing, no outline box. Never use magenta inside a sprite. Small plants (they are drawn in a quarter of a block).
行ごとに 1 作物、左→右が 成長段階 0 (植えた直後) / 1 (中間) / 2 (成熟):
1-3. planter_mushroom_0..2: 深海の発光キノコ (暗い紫の柄、シアンに光る傘)。0=小さな芽、1=小さな傘、2=大きな傘が 2 本
4-6. planter_gourd_0..2: 圧力瓢箪 (暗い緑の蔓、くすんだ青緑の瓢箪の実)。0=双葉、1=蔓と小さな実、2=丸い実が熟す
7-9. planter_kelp_0..2: 深海昆布 (暗い緑〜茶の葉)。0=短い葉、1=中くらい、2=背の高い葉が揺れる
```
