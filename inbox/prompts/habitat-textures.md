# 深海拠点 テクスチャ (H01) — ChatGPT ImageGen 用

保存先 `inbox/textures/sheets/HAB1.png` / `HAB2.png`。取込: `python tools/agentflow/sheets.py import auto --preset HAB1` (HAB2 も)。
habitat_window の純黒 #000000 は取込後に半透明の青緑ガラスへ置き換える。デザイン画: inbox/designs/H01.png。

## 共通プロンプト
```
Minecraft 1.20.1 block texture, hand-drawn 16x16 pixel art in the style of vanilla Minecraft (iron block, blast furnace, observer, copper, sea lantern). Each texture is ONE opaque tile in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters between tiles, no cell borders, no labels, no text, no watermark. Flat orthographic view of one block face filling the whole square, no perspective, no 3D render.
Limited palette of 4-7 colors per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no soft glow halo, no drop shadow. Draw at exactly 16x16 logical pixels (square pixels, integer scale). Never use magenta inside a tile.
Theme: pressure-proof deep-sea habitat (same family as an existing industrial metal panel: dark gunmetal, blue-grey steel, thick plates with corner bolts). Emphasise airtight frames, seals and windows so it reads as a deep-sea BASE, not a factory. Teal accent stripe, glass dark blue to cyan, lights white to pale cyan.
Palette guide: #14181e #232a33 #343e4a #4a5866 #6c7c8a #9aa8b4 (metal), #0f3a40 #1d6670 #2a9aa0 (teal), #3ee6f0 #b8f8ff #eafcff (glow/light), #0b2a3a #13506a (glass tint).
```

## シート HAB1 (10個 / 5列×2行)

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1. habitat_floor: 拠点の床。滑り止めの刻みが入った厚い鋼板、四隅にボルト、タイル状に繋がる。壁より少し明るい
2. habitat_trim: 外壁の最下段。壁と同じ鋼板の中央を横に通る太いティールの帯 (3-4px)、帯の上下に細い縁、左右に繋がる
3. habitat_wall: 外壁。厚い耐圧パネル、外周に気密シールの枠、四隅にボルト、中央は落ち着いた広い面
4. habitat_ceiling: 天井/屋根。外壁より暗い鋼板、縦横の補強リブ、タイル状に繋がる
5. habitat_window: 耐圧窓。外周 2px の厚い金属フレームと角のボルト。フレームの内側 (ガラス部分) は全部 純黒 #000000 で塗る (後で半透明ガラスに置き換える)。ガラス内に小さな白いハイライト 2-3px だけ置いてよい
6. habitat_light: 天井埋め込み照明。細い金属枠の中に白〜淡いシアンの光る板、細い格子
7. habitat_door_frame: 扉の上の枠。厚い鋼の梁、中央に小さな黄色の警告ランプか表示、左右に繋がる
8. habitat_hatch: 接続ハッチ (塞がれた通路口)。円形の気密ハッチ扉とハンドル、ティールの縁、黄色と黒の細い斜線を少し
9. habitat_door_top: 気密扉の上半分。厚い扉板、上部に丸い小窓 (暗い青ガラス)、縁にシール
10. habitat_door_bottom: 気密扉の下半分。厚い扉板、横向きの取っ手、下端に黄色と黒の斜線
```

## シート HAB2 (1個 / 1列)

```
Minecraft 1.20.1 item icon, hand-drawn 16x16 pixel art in vanilla style, ONE item centred in a square cell on flat solid magenta (#FF00FF) background, no labels, no text. Limited palette, hard pixel edges, dark 1px outline like vanilla items, no anti-aliasing, no gradients, no glow halo. Never use magenta inside the item.
habitat_constructor: 深海拠点建設装置。手持ちの建設ツール: ダークガンメタルの本体とグリップ、先端にシアンに光る投影レンズ、側面にティールの帯と小さな表示窓 (ミニチュアの青い箱型ブロック)。斜め45度のアイテム向き。
Palette: #14181e #343e4a #6c7c8a #9aa8b4 #1d6670 #2a9aa0 #3ee6f0 #b8f8ff
```

## シート HAB3 (5個 / 5列、H02 建設メニューのアイコン)

```
Minecraft 1.20.1 GUI icons, hand-drawn 16x16 pixel art in vanilla style, five icons in ONE row, each centred in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters, no labels, no text. Limited palette, hard pixel edges, dark 1px outline like vanilla items, no anti-aliasing, no gradients. Never use magenta inside an icon.
Each icon is a tiny isometric (3/4 view) model of one deep-sea base module, dark gunmetal panels with a teal stripe, dark blue windows, white-cyan lights (palette #14181e #343e4a #6c7c8a #9aa8b4 #1d6670 #2a9aa0 #3ee6f0 #b8f8ff #13506a):
1. habitat_icon_foundation: flat square platform slab with 4 corner lights
2. habitat_icon_room: big square box room with a row of windows and a teal stripe
3. habitat_icon_corridor: long narrow tube-like corridor box with side windows
4. habitat_icon_entrance: small cube with a door on the front
5. habitat_icon_moon_pool: square box seen from slightly above, open top showing blue water inside
```

## シート HAB4 (19個 / 5列×4行、H04 ロッカー・H05 壁掛け作業台のブロック面)

```
（共通プロンプト）
このシートの内容（左→右、上→下の順）:
1-4. large_locker_front_tl / front_tr / front_bl / front_br: 2x2 ブロックで 1 枚になる大型ロッカーの正面 (閉)。左右 2 枚の縦長の扉、中央に縦のシール線 (tl/bl の右端と tr/br の左端が合わさる)、上段 (tl/tr) の上端に補強梁とシアンの小さな状態ランプ、下段 (bl/br) の下端に補強梁、扉ごとに縦の取っ手 (tl の右下・tr の左下あたり)、ティールの横帯は下段の中ほど。4 枚を 2x2 に並べると継ぎ目なく一枚の正面に見えること
5-8. large_locker_open_tl / open_tr / open_bl / open_br: 同じロッカーの開いた状態。扉の内側が見え、中は暗い棚 (棚板 2 段とフック)、扉の縁だけ外側に残る
9. large_locker_side_top: ロッカー側面の上段。耐圧鋼板、上端に補強梁
10. large_locker_side_bottom: 側面の下段。ティール帯、下端に補強梁
11. large_locker_top: 天板。暗い鋼板、四隅にボルト
12. large_locker_bottom: 底板。脚と補強リブ
13. wall_workbench_back: 壁掛け作業台の背面プレート。四隅のボルトで壁に固定
14. wall_workbench_frame: 支持アームと枠の鋼材
15. wall_workbench_surface: 作業面 (上から見たクラフト面)。3x3 の格子が刻まれた明るめの鋼板
16. wall_workbench_teal: ティールの帯パネル
17. wall_workbench_display: 小さな表示パネル (消灯、暗い青ガラス)
18. wall_workbench_display_on: 同じ表示パネル (点灯、シアンのゲージと電源マーク)
19. wall_workbench_connector: ケーブル端子 (暗い金属に小さなシアンの芯)
```

## シート HAB5 (2個 / 2列、I03 電動ツールのアイコン)

```
Minecraft 1.20.1 item icons, hand-drawn 16x16 pixel art in vanilla style, two items side by side, each centred in its own square cell on flat solid magenta (#FF00FF) with wide magenta gutters, no labels, no text. Limited palette, hard pixel edges, dark 1px outline like vanilla tools, no anti-aliasing, no gradients, no glow halo. Diagonal tool orientation like vanilla pickaxes/swords (handle bottom-left, head top-right). Never use magenta inside an item.
1. electric_abyssal_drill: hand-held cordless electric drill (pistol grip): dark gunmetal motor body with a teal stripe and a small cyan charge light, black grip, spiral drill bit of pale tungsten steel pointing top-right
2. electric_abyssal_cutter: hand-held electric circular-saw cutter: dark gunmetal pistol-grip body with a cyan power light and teal stripe, a pale steel circular saw blade with teeth at the top-right
Palette: #14181e #343e4a #6c7c8a #9aa8b4 #d8dde0 #1d6670 #2a9aa0 #3ee6f0 #b8f8ff
```

## シート HAB6 (5個 / 5列、H06 建設装置の立体モデルの面タイル)

```
（共通プロンプト）
これは手持ち工具の立体モデルの表面に貼る 16x16 の面タイル (小さな部品に一部だけ切り取って使われる)。細かい模様は避け、大きめのパネル分割と少しのディテールだけ:
1. habitat_constructor_body: 本体外装。ダークガンメタルの耐圧パネル、細い継ぎ目、少しのボルト
2. habitat_constructor_grip: グリップ。黒〜濃灰のゴム、横の滑り止め溝
3. habitat_constructor_projector: 投影レンズ。中央が明るいシアン〜白、外周に暗い縁 (全体が光る面)
4. habitat_constructor_teal: ティールの帯パネル
5. habitat_constructor_display: 側面の小さな表示窓。暗い青ガラスにシアンの小さな箱 (モジュールのミニ表示)
```

## シート HAB7 (4個 / 4列、I04 推進スクリューの 3D モデル面タイル)

```
（共通プロンプト）
これは手持ち推進装置 (水中スクーター) の立体モデルの表面に貼る 16x16 の面タイル (小さな部品に一部だけ切り取って使われる)。大きめのパネル分割と少しのディテールだけ:
1. propulsion_screw_body: 本体の筒の外装。ダークガンメタルの耐圧パネル、ティールの細い帯、少しのボルト
2. propulsion_screw_grip: ハンドル/グリップ。黒〜濃灰のゴム、滑り止め溝
3. propulsion_screw_propeller: スクリューの羽根を後ろから見た面。明るい鋼色の羽根 3〜4 枚、中央のハブ、外周に暗いガード
4. propulsion_screw_light: 前面のライト。中央が明るいシアン〜白、外周に暗い縁 (全体が光る面)
```

## シート HAB8 (3個 / 3列、H07 スキャン室)

```
（共通プロンプト）
このシートの内容（左→右）:
1. scan_console_side: 床に置くスキャン台座 (ホログラム投影機) の側面。耐圧鋼板の台座、ティールの帯、下部に通気スリット、小さなシアンの表示ランプ
2. scan_console_top: 台座の上面。中央に円形の投影レンズ (シアン〜白に光る)、周囲に 4 つの小さな投影口、外周は暗い鋼の枠
3. habitat_icon_scan_room: (ここだけアイコン) 建設メニュー用の 16x16 アイコン。小さな立方体の部屋を斜め上から見た図、天井がなく中央の台座の上にシアンのホログラムの球と光の点
```
