# SUB02 潜水艦 (ユーザーモデル) + ムーンプールのドック

依頼 (2026-10-04, ユーザー): `F:\BlockBench\bbmodel\Submarine.bbmodel` の潜水艦を実装。
W 前進 / S 後退 / A D 左右 / Space 上昇 / Ctrl 下降 / Shift 降車 / G ライト切替 / マウスで旋回。
ムーンプールにドックブロックも実装する。

過去の撤回版 (Obsidian decisions/submersible-vehicle.md) の教訓を使う。モデルは今回ユーザー作なので作り直さない。

## 0. 生成済み (触らない)
- `tools/vehicle_model.py` が .bbmodel を焼いて出力 (手編集禁止、再実行する):
  - `src/main/java/com/abyssia/vehicle/client/SubmarineMesh.java` — `float[] HULL / GLASS / LAMPS`、1 quad = 23 float:
    4 x (x, y, z, u, v) + (nx, ny, nz)。単位ブロック、y 上、**モデル前方 = -Z**、底 y=0、x/z 中心 0。
    `WIDTH 3.234 / HEIGHT 2.273 / LENGTH 4.438`。
  - `textures/entity/submarine.png` (256x256)、`textures/item/submarine.png` (16x16 アイコン)。
- モデル寸法 (px, 底=0 基準): 胴は八角形の筒 (中心 y≈17, 内側 y≈2..32)、z -20..23 (前隔壁 z -4..-2、後隔壁 z 21..23)、
  前方 z -20..-42 にガラスの鼻先。コックピット = z -20..-4。

## 1. Entity `com.abyssia.vehicle.Submarine extends Entity` (Boat ではない)
- 登録 `submarine`、MobCategory.MISC、hitbox 3.0 x 2.2 (正方形 XZ)、clientTrackingRange 10、updateInterval 1。
- SynchedData: ENERGY (int), DAMAGE (float), LIGHTS (bool), DOCK (Optional<BlockPos>)。
- 1.20.1 の乗り物はクライアント操縦 (`isControlledByLocalInstance`、ServerboundMoveVehiclePacket で同期) → 移動用の独自パケット不要。
  `getControllingPassenger()` = 先頭の Player。乗員 1 人。
- 操縦 (操縦側クライアントの tick):
  - yaw = 乗員の yRot (マウス旋回)。ピッチは船体に反映しない (水平のまま)。
  - W/S = 前後、A/D = 左右平行移動 (`options.keyUp/keyDown/keyLeft/keyRight`)。
  - Space = 上昇 (`keyJump`)、Ctrl = 下降: `keySprint` のキーを `InputConstants.isKeyDown(window, key)` で物理状態として読む
    (トグルダッシュでも押している間だけ)。Shift は vanilla の降車のまま。
  - 速度: 加速 0.04 b/t²、前進最大 0.42 b/t、後退/横 0.22、上下 0.18、水中抵抗 0.9/tick。値は Config。
  - 上昇推力は船体の 60% 以上が水中のときだけ (水面でのガタつき防止)。水外では推力なし + 重力、地上では止まる。
  - 水中で無操作なら中性浮力 (沈まない・浮かない)。
- エネルギー: 容量 60,000 FE (Config)。推進中 8 FE/t × 入力量、ライト 1 FE/t。0 なら推進しない (ライトも消える)。
  サーバー側で減算 (クライアント操縦なので、サーバーは位置差分 or 乗員の入力有無で判定: 位置差分 >0.01 で推進中とみなす)。
- ライト (G): クライアント KeyMapping `key.abyssia.submarine_light` (既定 G、カテゴリは既存 habitat と同じ abyssia カテゴリ)、
  乗っているときだけ `SubmarineLightPacket` (C→S) で LIGHTS を反転。
  照明: サーバーが前方レイ (目の高さ、yaw 方向、最大 12 ブロック、最初の固体手前) の終点と中間点に waterlogged / 空気の
  `Blocks.LIGHT` (level 15) を置き、動いたら古いのを消す。置くのは空気か水源だけ、消すときは置いたものだけ (記録して照合)。
  エンティティ remove / ライト OFF / 降車でも全部消す。chunk unload で残らないよう NBT に置いた座標を保存し、load 時に掃除。
  描画: LAMPS 層をライト ON で full-bright (LightTexture.FULL_BRIGHT)、OFF は通常ライト。
- 乗員: 席 = モデル z -11px 前方 (yaw 回転)、乗員の腰がモデル y≈12px に来るよう `positionRider` を自前で
  (Player の y = sub.y + 12/16 - 0.70)。頭がガラス/天井から出ないこと。
  `canBeRiddenUnderFluidType` true、`shouldRiderSit` true、サーバーで毎 tick 乗員の air を max に。
  乗員は窒息/溺れなし。降車位置: ドック中はドックの横の乾いた床 (下記)、それ以外は vanilla の dismount 探索。
- HUD: 乗員に 10 tick ごと action bar「⚡ 85%  ❤ 100%」(+ ドック中は「ドック中 — Ctrl で切り離し」)。lang キー。
- 破壊: boat 同様、攻撃で DAMAGE 加算、40 超で壊れて `submarine` アイテムをドロップ (ENERGY を NBT に保持)。
  creative の攻撃は即撤去 (ドロップなし)。乗っている本人の攻撃は無視。
- isPickable true (右クリックで搭乗)、canBeCollidedWith false、isPushable false。
- 保存: ENERGY, DAMAGE, LIGHTS, DOCK, 置いた LIGHT 座標。

## 2. Item `submarine`
- 右クリックで視線先 (水中/水面/地面) に設置 (boat の配置を参考、yaw = プレイヤーの向き)。ENERGY を NBT から引継ぎ、
  NBT なし (クラフト直後) は満充電。ツールチップに FE 表示。stack 1。
- レシピ: 既存素材から (I04 推進スクリュー x2 + 生体/工業素材 + ガラス)。tools/check_recipes.py が通ること。
  既存のどのレシピとも衝突しないこと。JEI/ガイド (`.source` lang) の説明を付ける (既存アイテムの流儀どおり)。

## 3. Renderer (client)
- `SubmarineRenderer extends EntityRenderer<Submarine>`: translate(0,0,0) → `Axis.YP.rotationDegrees(180 - yRot)`
  (補間 yaw) → 3 層を描画: HULL `RenderType.entityCutoutNoCull(tex)`、LAMPS 同 (ライト ON で FULL_BRIGHT)、
  GLASS `RenderType.entityTranslucent(tex)` (最後)。頂点は pose / normal 行列で変換、overlay NO_OVERLAY、
  被ダメ時は赤くしない (boat 同様に揺れだけで可)。面はすべて NoCull 前提 (向きは生成時の法線を使う)。
- 一人称: カメラがコックピット内でガラス越しに前が見えること (実機確認)。

## 4. ドック `submarine_dock` (ブロック + BlockEntity)
- 天井から吊るすクランプ。形状は既存の habitat/industrial テクスチャを使ったブロックモデル (下面寄り 12x6x12px 程度)、
  lightLevel 12 (プール照明を兼ねる)。新規テクスチャは作らない (既存流用)。
- **建設ツールで追加** (ユーザー変更 2026-10-04「ドックはツールで追加する形にしてください」、自動設置は廃止):
  BuildEntry `submarine_dock` (EQUIPMENT、`com.abyssia.vehicle.SubmarineDockEntry`、充電ステーションと同じ作り)。
  登録済み MOON_POOL の中ならどこを狙ってもプール中央・室内最上段 (x0, z mid, y = height-2 = 3、天井から吊る) にスナップ。
  ムーンプール以外 → `dock_not_pool`、そのプールに既にドック → `dock_exists` で拒否。回転なし (対称形)。
  コストは材料払い (深淵合金 6・導電合金 4・鉄 12・シーランタン 1)、ゴースト表示、ツールの解体で 80% 返却。
  アイテム・レシピ・ルートなし (ドロップしない、充電ステーションと同じ)。GENERATED プロパティも無し。
  ムーンプール解体前にドックを先に解体する必要がある (他の設備と同じ OCCUPIED 規則)。
- BlockEntity: FE バッファ 20,000 (受信のみ、ENERGY 全面) → HabitatPower の無線配電とケーブルが充電する
  (ChargingStationBlockEntity と同じ作り、`CableNetworkManager.touchAround` も)。
- ドッキング (サーバー、5 tick ごと):
  - 捕捉範囲: ドックの下、水平 ±3.5、垂直 下 6 ブロックまで。範囲内の潜水艦で、DOCK 空・クールダウン 0・
    (無人 or 上向きに動いている) のものを 1 隻ドック (sub.DOCK = pos)。1 ドック 1 隻。
  - ドック中の潜水艦 (両サイドの tick、操縦側が位置を決めるので両方で同じ処理): 目標 = ドック直下、船体上面がドック下面の
    0.3 ブロック下、yaw はそのまま。0.12 b/t で目標へ寄せ、到着後は固定 (速度 0)。操縦入力の移動は無視、
    Ctrl (下降) を押すと切り離し → DOCK 空、クールダウン 60 tick、少し下へ押し出す。
  - ドック中の充電: 500 FE/t をドックのバッファから。修理: DAMAGE を 0.05/tick 減らす。
  - ドック中に Shift 降車 → プールの縁の乾いた床 (ドック位置から水平に探す、乗員の向いている側優先、空気 2 マス + 下が固体)
    に降ろす。
  - ドックが壊れた/ブロックが変わったら切り離し。
- ドックを右クリック: action bar にバッファ FE とドック中の潜水艦のエネルギー。

## 5. Config ([submarine] in Config.java)
energy_capacity, thrust_fe_per_tick, light_fe_per_tick, max_speed, dock_charge_rate, dock_capture_range。

## 6. lang (en_us / ja_jp)
entity.abyssia.submarine (潜水艦)、item.abyssia.submarine、block.abyssia.submarine_dock (潜水艦ドック)、habitat.abyssia.mode.submarine_dock / .detail、message.abyssia.habitat.dock_not_pool / dock_exists、
key.abyssia.submarine_light (潜水艦ライト)、HUD / ツールチップ / `.source` 説明。ガイドブックは tools/guide_text.py 経由のみ
(必要なら 1 ページ追加、手編集禁止)。

## 7. テスト (必須、実測)
- `gradlew compileJava` / 既存チェック (check_recipes 等)。
- 隔離テストサーバー/クライアントのハーネス (solutions/isolated-test-server, autoshot) で:
  搭乗 → 各キーで移動 (前/後/横/上/下)、Shift 降車、G でライト ON (LIGHT ブロックが前方に出る / OFF で消える / 動くと追従)、
  エネルギー減少、破壊でドロップ (エネルギー保持)、建設ツールでムーンプールにドックを追加 (中央上にスナップ、非ムーンプール/2個目は拒否)、潜水艦が捕捉される、充電される、
  Ctrl で切り離し、ドック中の降車で乾いた床に立つ、ツールでドック解体→80% 返却。
- スクリーンショット: 外観 (三人称)、一人称コックピット、ライト ON、ドック中。

## 8. 左右の収納ポッド (ユーザー追加依頼 2026-10-04)
「潜水艦の左右にチェストの代わりの model を作ってあるので、そこをクリックすれば専用のインベントリを開けるように。サイズは普通のチェスト」
- モデルの group `Rcheast` (右, +x) / `Lcheast` (左, -x) がポッド。bbmodel 座標: R = from [13,7.4,2] to [19,19.4,16]、
  group 回転 z +12.5° / 原点 [16,13.4,9]。L は x 反転・回転 -12.5°。メッシュ座標 = bbmodel + (0, +7.6005, +2.5) px (ポッドの判定は焼いたメッシュ実測で y +9.473 px — SubmarinePods.SHIFT_Y)。
  判定用に各辺 1px ほど膨らませた OBB (回転込み) を使う。
- 右クリック (乗っていない人): サーバーでプレイヤーの目 + 視線のレイを潜水艦ローカル座標 (yaw の逆回転) に変換し、
  2 つのポッド OBB と交差判定。当たればそのポッドの収納を開く。外れれば従来どおり搭乗。
  (エンティティの AABB は軸平行で船体と回らないので、hit 位置ではなくレイで判定する。)
- 収納: 左右それぞれ 27 スロット (普通のチェスト、ChestMenu.threeRows)。タイトル「潜水艦の右収納 / 左収納」(lang)。
  stillValid = 潜水艦が生きていて距離 8 以内。エンティティ NBT に保存。破壊時は中身をその場にドロップ (チェスト付きボート同様)。
  creative の即撤去でもドロップする。開閉音はチェストの音。
- 乗っている本人はポッドを開けない (搭乗中の右クリックは無し) — 必要ならあとで。
- ドック中も開ける (ムーンプールの縁から)。
