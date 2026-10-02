# H04 大型ロッカー / H05 壁掛け作業台 / I03 電動ツール / H06 建設装置の3Dモデル
原本: inbox/specs/H04-chatgpt-raw.md (ChatGPT, 4 件まとめて)。下記は Claude の修正込み確定版。デザイン画 inbox/designs/H04.png、
テクスチャ = ChatGPT シート HAB4 (ロッカー・作業台のブロック面) / HAB5 (電動ツールのアイコン) / HAB6 (建設装置モデルの面)。

## 共通
- 見た目は habitat_* / 工業ブロックの延長 (ダークガンメタル、ティール帯、暗い青ガラス、シアン発光)。形のあるブロックは parts 定義から JSON モデルを生成 (tools/industrial_models/build_models.py と同じ方式、UV は位置から自動)
- 各 Java は別パッケージ・別ジェネレータ (JSON/レシピ/タグ/LANG)。lang ファイルはメインが最後にマージする
- 全レシピの素材は既存アイテムのみ

## H04 大型ロッカー large_locker (tier: heavy)
- 2x1x2 (幅2・奥行1・高さ2) の 4 ブロックで 1 つの収納、54 スロット (バニラ GENERIC_9x6 / ChestMenu を流用)
- 設置: 床置き、正面 = プレイヤー側 (水平4方向)。置いた位置を左下として右・上・右上の 3 マスが空いていなければ置けない (修正: ChatGPT は条件未定義)
- 実装: 左下が本体 (BlockEntity、中身を持つ)。他の 3 マスは同じブロックの別 PART (blockstate `part=bl|br|tl|tr`) で、本体へ委譲 (右クリック・ホッパー capability)
- どれかを壊すと 4 マスとも消え、ロッカー 1 個 + 中身を落とす。ピック (中クリック) はロッカー
- 開閉: blockstate `open` で正面テクスチャを開いた絵に切替 (修正: ドアのアニメーションではなくモデル差替え)。音は BARREL_OPEN/CLOSE + IRON_DOOR (小さめ)。開いている人数で open を管理
- ホッパー: 上面=搬入、下面=搬出、側面=両方 (全マスで本体の 54 スロットを公開)
- レシピ: `PPP` / `AMA` / `PPP` P=abyssia:iron_plate A=abyssia:corrosion_alloy_ingot M=abyssia:machine_frame
- テクスチャ (修正: 2x2 で一枚の正面に見えるよう正面を 4 分割、開いた状態も 4 枚): large_locker_front_{tl,tr,bl,br}, large_locker_open_{tl,tr,bl,br}, large_locker_side_top, large_locker_side_bottom, large_locker_top, large_locker_bottom

## H05 壁掛け作業台 wall_workbench (tier: heavy)
- 1x1、壁から 7px 突出 (背面が付いた壁)。壁の側面を右クリックで設置 (床・天井には付かない)。支えの壁が無くなると壊れて自分を落とす (壁付けたいまつと同じ)
- GUI: バニラ作業台と同じ 3x3 クラフト (通常の作業台レシピ) + 右側に充電スロット 1 つ + FE ゲージ。閉じたらクラフト欄の中身はプレイヤーに返す (バニラと同じ)
- 充電: 修正 — スロットは「FE capability を持つアイテム」を受け付ける (電動ツール専用に縛らず、I03 との依存をなくす)。充電スロットの中身は BlockEntity に保存し、壊したら落とす。2,000 FE/t
- 電源: 工業ケーブル網からの受電 (内部バッファ 20,000 FE、FE capability を全面で受け付け、CableNetworkManager の受電側として扱う)。ケーブルが無ければ充電しない
- 見た目 parts: 背面プレート、左右の支持アーム、中央の作業面 (上面がクラフト面)、ティール帯、シアンの電源ランプ (給電中だけ光る = blockstate `powered`)、右側の充電クレードル、ケーブル端子
- レシピ: `PPP` / `IMI` / `CKC` P=abyssia:iron_plate I=abyssia:industrial_panel M=abyssia:machine_frame C=abyssia:conductive_alloy_ingot K=minecraft:crafting_table (修正: 中央下は銅ではなく作業台そのもの)
- テクスチャ: wall_workbench_back, wall_workbench_frame, wall_workbench_surface (3x3 グリッドの作業面), wall_workbench_teal, wall_workbench_display, wall_workbench_display_on, wall_workbench_connector

## I03 電動ツール (tier: standard)
- 既存 abyssal_drill / abyssal_cutter は変えず、新規 electric_abyssal_drill / electric_abyssal_cutter
- 修正: 耐久は持たない (FE のみ、修理不可)。標準の耐久バーを FE 残量バーとして表示 (シアン)。ツールチップに `Energy 82% (82,000 / 100,000 FE)`
- FE は stack の NBT、Forge ENERGY capability を提供 (受電のみ、作業台 H05 や他 Mod の充電器で充電可)。クリエイティブタブには満充電品も並べる
- FE 0: 採掘速度 0 (掘れない)・攻撃はダメージ 1、アクションバーに「エネルギー切れ」
- ドリル: 容量 100,000 FE、1 ブロック 500 FE、Tier 速度 12 / 採掘レベル = abyssal_drill と同じ (つるはし+シャベル扱い)。**スニークしながら掘ると 3x3** (向いている面に垂直な 3x3x1、掘れる・正しいツールのブロックだけ、1 ブロックごとに 500 FE、保護は BlockEvent.BreakEvent を発火して尊重)
- カッター: 容量 75,000 FE、攻撃 1 回 250 FE、剣 (abyssal_cutter と同じ攻撃力)。植物・葉・蔓・海藻・珊瑚・羊毛・クモの巣を高速破壊 (ハサミ扱い、ハサミのドロップ)、1 ブロック 250 FE。スニーク時の「精密切断モード」は通常時に統合 (修正: 操作を増やさない)
- レシピ: ChatGPT 案どおり。ドリル `TCT` / `AMA` / `PHP` (T=tungsten_tip C=conductive_component A=abyssal_alloy_ingot M=machine_frame P=iron_plate H=thermal_component)、カッター `ACA` / `HMH` / `PPP`
- アイコン: 修正 — 立体モデルにせず 16x16 アイコン 2 枚 (electric_abyssal_drill, electric_abyssal_cutter)

## H06 建設装置の3Dモデル (tier: light)
- habitat_constructor の item model を parts から作る立体モデルに差し替え。parts は ChatGPT 案 (body / grip / front_housing / projector / top_panel / side_panel / teal_band) を基準にゲーム内の見た目で調整
- 面テクスチャ: habitat_constructor_body, _grip, _projector, _teal, _display (16x16 の面タイル)
- display: firstperson は斜め 45° の手持ち端末、thirdperson は手に収まる大きさ、gui は 3/4 視点で 16x16 アイコン相当の占有率、ground は小さく
- 確認: 一人称・三人称・GUI・地面のスクリーンショットを ChatGPT に検査させ、パーツの修正案を反映 (最大 3 回)
