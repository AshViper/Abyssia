# GB01 深海ガイドブック (Abyssia Guide)
(ChatGPT 2026-10-03 作成、request 20261003-161712。デザインシート: inbox/designs/GB01-sheet.png。全文は ChatGPT の返答を要約したもの。「Claude 注」は Claude が直した部分)

tier: standard
files: src/main/java/com/abyssia/guide/** (GuideBookItem / GuideBookScreen / GuideBookData / GuideBookDataLoader / GuideBookRegistry / GuideBookPage + Text/Item/Image/Recipe page / GuideBookFirstLogin)、src/main/resources/assets/abyssia/guide/ (chapters.json、pages/<chapter>.json、lang/ja_jp.json・en_us.json)、src/main/resources/assets/abyssia/textures/gui/guide/ (book_bg.png 256x180、buttons.png、decor.png、icons/<chapter>.png ×10、pages/<name>.png 挿絵)、textures/item/abyss_guide_book.png、レシピ JSON、本アイテムの登録と名前の lang 1 行
Claude 注: ChatGPT の files 欄の `assets/abyssia/guide/textures/...` は誤りで、正しくは `assets/abyssia/textures/gui/guide/...`。

goal: 初めて遊ぶプレイヤーが、ゲーム内だけで何をすればよいかと進め方を理解できる独自のチュートリアル本を追加する。ゲームの進行順に説明し、主要アイテムの詳細も読めるようにする。章・ページ・本文は JSON と専用の lang から読み込む。

constraints:
- Patchouli 等の本 Mod に依存しない。JEI は任意 (無くてもすべて読める。JEI 連携ボタンは JEI があるときだけ表示)
- 自前の Screen で作る。論理サイズは 256x180 で、画面の中央に置きスケールする。見開き 2 ページ。左上に目次、左右に前後ページ、下部にページ番号、右下に閉じる。ESC でも閉じる。スクロールは使わず、収まらない分はページを分ける
- テーマ「深海の古い記録書」: 背景 #07111D 前後、本文は暗い羊皮紙、強調はシアン〜青、枠線は深い青灰色、装飾は海流・泡・発光植物・深海生物のシルエット。ピクセルアート
- 本文を Java に書かない。JSON には lang キーだけを置き、文章は guide/lang/<code>.json に書く (Claude 注: 自前のローダーが現在の言語のファイルを読み、無ければ en_us を使う)
- 初回配布: プレイヤーの永続データ (PERSISTED_NBT_TAG) に `abyssia_guide_received` を保存し、1 回だけ配る
- レシピ: shapeless `minecraft:book + abyssia:kelp_leaf → abyssia:abyss_guide_book`
- item ページ: 登録済みのアイテムモデルでアイコンを描く。名前は通常の表示名、説明はガイド専用の lang キー。recipe ページは JEI を使わず自前で簡易表示する (shaped / shapeless)
- 不正な JSON、未登録のアイテム、存在しない画像やキーがあってもクラッシュさせず、そのページを飛ばすか代わりの表示にする
- 既存のバランス・アイテム・GUI は変えない。NeoForge 1.21.1 に移植しやすく作る (データ形式はローダーに依存させない)

ページ型 (この 4 種のみ):
- text: type, id, chapter, title(key), text(key), image?(パス), image_position? (left|right|top|bottom), image_size?
- item: type, id, chapter, item(完全修飾 ID), title?(key。無ければアイテム名), description(key), obtaining(key), recipe?(レシピ ID), image?
- image: type, id, chapter, title(key), image(textures/gui/guide/pages/...), caption?(key)
- recipe: type, id, chapter, title(key), recipe(レシピ ID), description?(key)
chapters.json: 各章に id, title(key), icon, order, pages(ページ ID の並び)。pages/ は章ごとに JSON を分ける。lang キーの形は guide.abyssia.<chapter>.<...>。
Claude 注: ChatGPT の例に出てくるアイテム ID (abyss_helmet / diving_helmet) は仮の名前。実際に登録されている ID を使う。

章 (ゲームの進行順に 10 章。章アイコンは icons/ に同じ名前で置く):
I intro (はじめに / Introduction): Abyssia とは、最初にすること、深海へ行く前の基本
II abyss (深海の世界 / The Abyss): 深海層、深度と環境、バイオーム、探索の注意
III survival (生存の基礎 / Survival Basics): 酸素と呼吸時間、食料と回復、水中探索
IV resources (資源と植物 / Resources & Flora): 岩・鉱石・鉱物、資源植物、栽培・再生、資源アイテムの詳細
V creatures (生き物と食料 / Creatures & Food): 深海生物、食材、生肉と焼き肉、料理、食料アイテムの詳細
VI diving (潜水装備 / Diving Equipment): 入門装備・深海装備、呼吸時間 (装備なし 2 分 / 入門 5 分 / 深海 8 分)、装備アイテムの詳細
VII base (拠点建設 / Base Building): ルーム、廊下、ムーンプール、スキャン室などの設備
VIII industry (工業と電力 / Industry & Power): FE、発電、ケーブル、機械、水耕プランター
IX waypoints (ウェイポイントと探検 / Waypoints & Exploration): ビーコン、マーカー、登録、探索の進め方
X encyclopedia (アイテム図鑑 / Item Encyclopedia): 潜水装備・資源・植物・食材・食料・建築/拠点・工業・ウェイポイント・その他

画像 (ChatGPT に生成を頼む): item/abyss_guide_book.png (16x16、暗い青〜黒の表紙にシアンに光る紋章)、gui/guide/book_bg.png (256x180 の見開き)、buttons.png (目次・前・次・閉じる、ホバー時、無効時)、decor.png、icons/ ×10 (16x16)、pages/ の挿絵 (intro・abyss・biomes・survival・resources・flora・creatures・diving・base・industry・waypoints・exploration。どれも必須ではない)

accept: abyss_guide_book があり、右クリックで本の GUI が開く。初回ログインで 1 冊だけ配られ、再ログインしても再び配られない。レシピで作れる。見開き・目次・前後ページ・閉じるが動き、10 章すべてに目次から飛べる。ja/en で表示が切り替わる。4 種のページ型がすべて表示できる。JEI が無くても欠ける機能が無い。データが壊れていてもクラッシュしない。Forge 1.20.1 で動く (NeoForge 1.21.1 にも移植する)。
