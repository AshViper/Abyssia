# クラスト・結晶ブロック テクスチャ 再生成プロンプト (ChatGPT ImageGen 用)

1枚のシート(14個 / 5列)として生成し `inbox/textures/sheets/CRUST_CRYSTALBLOCK.png` に保存。Claude/Codex は画像を生成しない。
取込後、研磨/レンガ版はクラストの絵から自動再生成し、結晶ブロックの glow は新しい絵から導出する。

## シート CRUSTBLOCK (14個 / 5列)

```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft (amethyst block, calcite, tuff, deepslate). Each texture is ONE opaque tile in its own square cell with wide magenta (#FF00FF) gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic front view of a single material filling the whole square, seamlessly tileable on all four edges.
Limited palette of 4-6 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no soft glow, no drop shadow, no perspective, no 3D render. Sparse, slightly asymmetric detail with large calm areas; avoid noisy AI grain and repeating patterns. Each tile must be clearly different from the others. Draw at exactly 16x16 logical pixels (square pixels, integer scale).
このシートの内容（左→右、上→下の順）:
1. マンガンクラスト: 黒〜赤紫がかった暗色の薄い鉱物被膜。ひび割れた瘤状の皮殻、表面に鈍い光沢の粒（パレット #0d0a10 #241a2b #4a3556 #7a6288）
2. コバルトクラスト: 深い青の鉱物被膜。層状に割れた皮殻に、明るい青の結晶粒が少し（パレット #0a1430 #14306a #2a5cb0 #6fa0e0）
3. ニッケルクラスト: 灰緑〜明るい銀緑の被膜。粒状に固まった皮殻、白緑のハイライト（パレット #1c2a24 #3c5a4b #7fae95 #cfe8da）
4. 鉄クラスト: 赤錆色〜暗い赤褐色の被膜。錆びた鱗状の割れ、橙の錆斑（パレット #2a120e #5a2618 #94402a #d0704a）
5. 銅クラスト: 橙〜緑青のまだらな被膜。銅の橙と青緑の錆が混ざる（パレット #3a1c0e #a8531e #d98a3a #4aa58a）
6. 洞窟の鉱物クラスト: 黄土〜橙褐色の洞窟壁面の鉱物皮殻。乾いてひび割れ、硫黄の黄が少し（パレット #2a1d10 #5e3f1e #a8743a #d6b05a）
7. 塩の外殻: 白〜淡い灰紫の塩の皮殻。結晶質の粒と浅い亀裂、柔らかい面（パレット #8f8892 #b7b0c0 #dcd7e4 #ffffff）
8. 水色の結晶ブロック: 透明感のあるシアンの結晶が詰まった不透明ブロック面。角張ったファセット（パレット #0a3a4a #1a7a90 #3ec8d8 #b8f4f8）
9. 青い結晶ブロック: 深い青のファセット結晶が詰まったブロック面（パレット #0a1a50 #1a3ca0 #3a72e0 #a0c4ff）
10. 紫の結晶ブロック: 青紫〜紫のファセット結晶が詰まったブロック面（パレット #241048 #4a2a90 #8a5ad8 #d4b8ff）
11. 緑の結晶ブロック: エメラルド〜黄緑のファセット結晶が詰まったブロック面（パレット #0a2e1a #1a6a3a #3ab86a #b8f4c8）
12. 白い結晶ブロック: 白〜淡い青灰のファセット結晶、透明感のある面（パレット #5a6070 #98a0b4 #d4dae8 #ffffff）
13. 琥珀色の結晶ブロック: 橙〜金のファセット結晶が詰まったブロック面（パレット #4a2408 #a8561a #e8962a #ffdc80）
14. 深海結晶ブロック: 暗い藍〜シアンの深い結晶、内部に淡い光の芯（パレット #06142a #0e3a5a #1a7a9a #5ad0e0）
```

対応ID順: manganese_crust, cobalt_crust, nickel_crust, iron_crust, copper_crust, cave_mineral_crust, salt_crust, cyan_crystal_block, blue_crystal_block, violet_crystal_block, green_crystal_block, white_crystal_block, amber_crystal_block, deep_crystal_block
