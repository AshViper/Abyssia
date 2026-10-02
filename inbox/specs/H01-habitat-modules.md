# H01 深海拠点モジュール建設装置
tier: heavy
files: src/main/java/com/abyssia/habitat/** (新規), src/main/java/com/abyssia/registry/ModHabitat.java (新規),
       Abyssia.java / ModItems.java (登録呼び出し数行のみ), tools/habitat_assets.py (新規、gen_deep_assets から呼ぶ),
       生成される blockstates/models/recipes/tags/lang (habitat_*), textures/block/habitat_*.png, textures/item/habitat_constructor.png,
       inbox/prompts/sheets.json + habitat-textures.md (テクスチャ = ChatGPT シート HAB1/HAB2)
goal: 1つの建設装置で、水中に 土台 / 多目的ルーム / 廊下 / 出入り口 / ムーンプール を一括生成する。内部は空気、コストはインゴット類をインベントリから消費、生成ブロックは壊してもドロップしない。
constraints:
- 原本: inbox/specs/H01-chatgpt-raw.md (ChatGPT)。デザイン画: inbox/designs/H01.png。下記は Claude の修正込み確定版
- 修正1 (浸水): ChatGPT 案の「部屋の4面に 1x3 の開口」「廊下の前後開口」は水中で室内が浸水する。開口は**接続ハッチ** habitat_hatch (1x3、ドロップなし) で塞いで生成し、隣のモジュールのハッチと向かい合ったら両方を空気にして自動接続する
- 修正2 (扉): 二重扉に専用扉 habitat_door (手で開閉、ドロップなし) を追加。バニラの扉は開いていても水を通さない
- 修正3 (配置): 「水中のみ」は Y 制限ではなく「範囲の全マスが水 (水源・流水・水没した置換可能植物)」で判定する。深海層以外の海でも置ける
- 既存ブロックは上書きしない。範囲内に生き物がいたら不可。失敗時は消費しない。クリエイティブは無料
- 生成ブロックはルートテーブルなし (= 何も落とさない)、BlockItem もなし (入手不可)
accept: gradle build 通過。5モードを shift+右クリックで切替 (アクションバー表示)、右クリックで生成、素材不足/重なり/水外では生成されず消費なし。生成後に室内へ水が入らない (ムーンプールの水面は残る)。部屋→廊下→出入り口をハッチ狙いで繋ぐと自動で通路が開通。生成ブロックを壊しても何も落ちない。緑/赤の範囲プレビュー

## アイテム
habitat_constructor / 深海拠点建設装置 / Deep-Sea Habitat Constructor (stack 1)
- 右クリック: 選択中モジュールを生成。shift+右クリック: モード切替 (土台→多目的ルーム→廊下→出入り口→ムーンプール→…)、stack NBT `Mode`
- ツールチップ: 現モード、寸法、コスト、操作
- 手に持つ間、生成範囲の枠を表示 (緑=可, 赤=不可)

## 配置 (ローカル座標: x=幅 中央揃え, z=奥行 0=手前, y=高さ 0=床。前方=プレイヤーの水平向き)
1. ハッチを狙っている (到達 6): そのハッチパネル中央の外側 1 マスにモジュールの手前接続口 (z=0 の中央) を合わせ、前方=ハッチの外向き。床 y = ハッチ列の最下段 -1
2. ブロックの上面を狙っている: 上のマスを中心にモジュールを置く (土台の上にルーム等)
3. それ以外: プレイヤーの 2 マス先に手前面、床 y = 足元 y (海底に立って使うと海底の上に建つ。実機テストで -1 は沈下/海底で不可になると判明し変更)

## モジュール (外寸 幅x奥行x高さ) — 2026-10-02 ユーザー変更: 寸法拡大・接続口 3x3・土台は穴なし
| mode | 外寸 | 構成 | 接続口 | コスト |
|---|---|---|---|---|
| 土台 foundation | 13x13x1 | 全面床 (穴なし)。縁の四隅+各辺中央に照明 | なし | iron_ingot 12, copper_ingot 8, abyssia:iron_plate 8 |
| 多目的ルーム room | 13x13x5 | 内部 11x11x3。前・左右に窓 (y2-3、接続口から 1 マス離す: 前 \|x\|=3..5、左右 \|z-6\|=3..5)、天井照明 3x3 (x,z-6 = -4/0/+4) | 4面 | iron 16, copper 12, high_strength_alloy_ingot 4, iron_plate 12 |
| 廊下 corridor | 5x7x5 | 内部 3x5x3 (両端は接続パネル)、左右に窓 (y2-3, z1-5)、天井照明 z=1,3,5 | 前後 | iron 10, copper 6, high_strength_alloy 2, iron_plate 6 |
| 出入り口 entrance | 5x5x5 | 内部 3x3x3 の前室 (ベスティビュール)。前=外扉 (扉 2 段 + 上に扉枠、1 列)、左右に窓、天井照明。2026-10-02 ユーザー変更: 拠点側の内扉は廃止し 3x3 接続パネルに | 手前 (3x3 ハッチパネル、接続で開く) | iron 18, copper 12, high_strength_alloy 4, corrosion_alloy_ingot 2, iron_plate 10 |
| ムーンプール moon_pool | 13x13x5 | 床の中央 7x7 は水源 (壁内側の床枠 3 幅)、上 3 段は空気、天井照明 3x3 | 4面 | iron 16, copper 10, corrosion_alloy 4, high_strength_alloy 4, iron_plate 8 |

接続口 (2026-10-02 変更): 面中央の 3 幅 x 3 高 (面に沿って -1..1, y1-3) の habitat_hatch パネル。生成後、接続口のすぐ外 9 マスが全部ハッチなら両方のパネルを空気に 。ハッチを狙った配置は、狙ったハッチから下端→パネル中央 (幅 3) を求め、その外側に新モジュールの手前接続口中央を合わせる。

全面結合 (2026-10-02 ユーザー変更): 13 幅同士 (ルーム/ムーンプール) が壁と壁を接して接続したら、共有する壁を丸ごと開ける。両方の壁の内側部分 (面に沿って -5..5, y1-3) を空気に。角の列・床 y0・天井 y4 は残すので水は入らない。相手は既存ブロックで判定 (1 マス外の壁が -6..6 x y0-4 全部拠点ブロック、中央 3x3 がハッチ、その奥 -5..5 x y1-3 が空気)。廊下・出入り口との接続は 3x3 のまま。

## 新規ブロック (ドロップなし、つるはし、金属音)
| id | 名前 | 硬さ | 光 | 備考 |
|---|---|---|---|---|
| habitat_floor | 深海拠点床 | 5.0 | 0 | |
| habitat_trim | 深海拠点外壁(帯) | 6.0 | 0 | 壁の最下段 (y1) に一周するティールの帯 (デザイン画) |
| habitat_wall | 深海拠点外壁 | 6.0 | 0 | |
| habitat_ceiling | 深海拠点天井 | 5.0 | 0 | |
| habitat_window | 深海拠点窓 | 3.0 | 0 | 半透明 (translucent) |
| habitat_light | 深海拠点照明 | 3.0 | 15 | |
| habitat_door_frame | 深海拠点扉枠 | 6.0 | 0 | |
| habitat_hatch | 深海拠点接続ハッチ | 6.0 | 0 | 修正1 |
| habitat_door | 深海拠点気密扉 | 6.0 | 0 | 修正2、DoorBlock 手で開閉 |

## レシピ
habitat_constructor: `PHP` / `CAC` / `IRI` P=abyssia:iron_plate H=high_strength_alloy_ingot C=copper_ingot A=abyssal_alloy_ingot I=iron_ingot R=redstone

## 見た目
デザイン画どおり: 壁の y1 段は habitat_trim (ティール帯)、窓は横長 (ルーム前/左右は y2-3 の幅5、廊下は左右 y2-3 の幅3〜5)、天井照明は天井面に埋め込み、土台の縁にも照明を数個。
industrial_panel 系の延長 (ダークガンメタル、青みグレー、ティールの帯、暗い青〜シアンの窓、白〜淡いシアンの照明)。工業設備ではなく深海基地に見えるよう、フレーム・気密・窓を強調。テクスチャは ChatGPT シート HAB1 (ブロック) / HAB2 (アイテム)。
