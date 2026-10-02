# I04 推進スクリュー / H07 スキャン室
原本: inbox/specs/I04-H07-chatgpt-raw.md (ChatGPT)。下記は Claude の修正込み確定版。デザイン画 inbox/designs/I04.png、
テクスチャ = ChatGPT シート HAB7 (推進スクリューのアイコン + 3D モデル面) / HAB8 (スキャン台 + メニューアイコン)。

## I04 推進スクリュー propulsion_screw (tier: standard)
- I03 電動ツールと同じ FE 方式 (ElectricTools: stack NBT、耐久なし、耐久バー = FE、ツールチップ、ENERGY capability で充電)。容量 100,000 FE、推進中 100 FE/tick (= 約 50 秒)
- 操作: 水中で右クリック長押し (use duration 72000) の間、視線方向 (上下含む) に推進。持っているだけでは効果なし。水中以外 (目が水に入っていない) は推進しない。FE 0 で止まる (アクションバー通知は I03 と同じ)
- 速度: 推進中の移動速度の目標 = 通常泳ぎの 1.8 倍 (上限 2.0 倍)。プレイヤーの SWIM_SPEED 属性を掛ける (フィンと併用で速くなる)。海流 (OceanCurrentPush) は通常どおり加算 = 逆流では遅くなる
- 修正 (同期): プレイヤーの移動はクライアント側で計算されるので、推進の加速は onUseTick で両側に同じ式で入れ、FE の減算と停止判定はサーバー (クライアントは stack の FE で止まる)。ChatGPT の「サーバー権威」はこの形で満たす
- 演出: 推進中は BUBBLE / BUBBLE_COLUMN_UP 粒子を後方へ、音は BEACON_AMBIENT か CONDUIT_AMBIENT_SHORT 系を低ピッチで間欠 (新規音なし)
- 見た目: 修正 — 建設装置 H06 と同じく parts からの 3D 手持ちモデル (本体の筒、後方のスクリュー + ガード、上のハンドル/グリップ、シアンのライト)。インベントリ表示も 3D。面タイル: propulsion_screw_body / _grip / _propeller / _light
- レシピ: `KTK` / `AMA` / `CTC` K=conductive_alloy_ingot T=tungsten_tip A=abyssal_alloy_ingot M=machine_frame C=conductive_component

## H07 スキャン室 scan_room (tier: heavy)
- 建設装置の新モード (メニュー 6 番目、アイコン habitat_icon_scan_room)。外寸 5x5x5、内部 3x3x3、4 面中央に 3x3 接続ハッチ (H01 と同じ規格、廊下・ルームに吸着して開通)、床・壁・天井・帯・照明は habitat_* ブロック (回収不可)。窓は角の列 (左右面の x=±2 は壁) にしない — 修正: 4 面とも接続口が内部幅いっぱいなので窓は付けない
- 建設コスト: iron_ingot 14, copper_ingot 10, conductive_alloy_ingot 4, abyssia:iron_plate 8, abyssal_crystal_shard 4 (修正: ChatGPT は未指定)
- 室内中央の床に scan_console (台座ブロック、BE あり、回収不可 = ドロップなし・BlockItem なし、生成時に建設装置が置く)。修正: ChatGPT の scan_terminal は 3x3 の室内に置き場がないので台座に統合 (右クリックで端末 GUI)
- 電源: 台座は FE を全面で受電 (内部 20,000 FE、ケーブル網の受電側)。スキャン 1 回 5,000 FE。足りなければスキャンしない (GUI に表示)
- スキャン: 範囲は台座中心の水平半径 32・上下 ±24。サーバーで 1 tick あたり最大 4,096 ブロックずつ分割走査 (約 2.5 秒)。ロード済みチャンクだけ (未ロードはスキップ、強制ロードしない)。結果: 対象鉱石の位置 (最大 2,048 件、近い順) + 地形の粗いボクセル (4x4x4 ごとの固体率 → 半分以上固体なら 1、17x13x17 グリッド)
- 対象: `forge:ores` タグ + 新タグ `abyssia:scannable` (abyssia の鉱石・クラスト・ノジュール・鉱物クラスターを生成器で列挙)。GUI で「全部」または種類 1 つを選ぶ (選択肢 = スキャン範囲に見つかった種類だけ + 全部)。色はブロックの MapColor
- 更新: GUI の SCAN ボタンで開始。その後、16 ブロック以内にプレイヤーがいる間は 5 秒ごとに自動再スキャン (FE があれば)。走査中は前回の結果を表示し、完了時に入れ替え
- 同期: 結果は BE に保存し、更新パケット (getUpdateTag / ClientboundBlockEntityDataPacket) で近くのクライアントへ
- ホログラム (BlockEntityRenderer): 台座の上 (y+1..+2.5 の範囲) に縮尺 1/40 程度の 3D マップ。地形 = 暗いシアンの半透明ボクセル、鉱石 = MapColor の発光キューブ (対象は明るく)、台座位置 = シアンのマーカー、近くのプレイヤー = 白。ゆっくり自動回転 (北の目印つき)。F1 で消えないブロック描画 (普通の BER)
- 端末 GUI: 左に対象リスト (全部 + 見つかった種類、件数つき)、右に同じマップの 3D 表示 (ドラッグで回転、ホイールでズーム、R で北向き) と FE ゲージ、SCAN ボタン、走査中の進捗
- テクスチャ: scan_console_side, scan_console_top (発光コアの面)、メニューアイコン habitat_icon_scan_room。壁などは既存 habitat_* を流用 (修正: ChatGPT の scan_room_wall 等の専用テクスチャは作らない)
