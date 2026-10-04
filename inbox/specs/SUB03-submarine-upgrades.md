# SUB03 潜水艦アップグレード 4 種

依頼 (2026-10-04, ユーザー): 「潜水艦のアップグレードを４種類ほど作成してほしい。デザインなどはすべてchatGPTで作成するように。」
設計・アイコン = ChatGPT (Claude in Chrome、会話「潜水艦アップグレード設計」)。前提は SUB02 (`inbox/specs/SUB02-submarine-dock.md`)。

## 1. アップグレード一覧

| id | 日本語名 | English | 主効果 | 代償 |
|---|---|---|---|---|
| `submarine_upgrade_pressure_hull` | 耐圧船体 | Pressure Hull | 船体耐久 40 → 70、深層で被ダメージ -50% | 全方向速度 ×0.92 |
| `submarine_upgrade_high_capacity_battery` | 大容量バッテリー | High-Capacity Battery | 容量 60,000 → 150,000 FE | 上下速度 ×0.95 |
| `submarine_upgrade_maneuver_thruster` | 高出力推進器 | Maneuver Thruster | 前進 0.42→0.56、後退/横 0.22→0.30、上下 0.18→0.23 b/t | 推進 8 → 12 FE/t |
| `submarine_upgrade_sonar_scanner` | 深海ソナー | Deep-Sea Sonar | 半径 16 の生物・海流・ドックを action bar に表示 | 乗員がいる間 4 FE/t |

アイテムはすべて stack 1、ツールチップに効果と代償を表示 (lang)。JEI/ガイドの `.source` 説明も既存流儀で付ける。

### 1.1 耐圧船体
- 破壊しきい値 (DAMAGE) 40 → 70。HUD の ❤% は装着中の最大値 70 を分母にする。
- 潜水艦エンティティの `getY() <= -64` (深層) の間、潜水艦が受けるダメージ (攻撃・衝突) を DAMAGE に加算する直前に `ceil(damage × 0.5)` に軽減 (1 ダメージは 0 にしない)。
  ※ SUB02 には水圧ダメージは存在しない。新しい水圧ダメージは追加しない (軽減は既存ダメージに対して)。
- 速度: 前進/後退/横/上下すべて ×0.92 (0.42→0.386 など)。
- 取り外し時に DAMAGE が 40 を超えていたら取り外し不可 (メッセージ `upgrade_hull_damaged`)。ドックで修理してから外す。

### 1.2 大容量バッテリー
- ENERGY 上限 60,000 → 150,000 FE。装着時は現在値を維持 (58,000 → 58,000 / 150,000)。ドック充電速度は 500 FE/t のまま (満充電は約 300 秒)。
- 上下速度 ×0.95。
- 取り外し時、ENERGY が 60,000 を超えていた分は失われる (60,000 にクランプ)。画面に警告ツールチップ。
- 潜水艦アイテム化 (破壊) 時は ENERGY と装着アップグレードを NBT に保持 (§3)。

### 1.3 高出力推進器
- 速度の計算順は固定: 基礎速度 (推進器なし 0.42 / 0.22 / 0.18) → 推進器ありなら 0.56 / 0.30 / 0.23 に置換 → 船体倍率 0.92 → (上下のみ) バッテリー倍率 0.95。
- 加速 0.04 → 0.05 b/t²。各方向の速度成分を毎 tick 加速し、上の最大速度で hard clamp (加速で上限を超えない)。
- 推進消費: 推進入力がある tick は方向・同時押しに関係なく一律 12 FE/t (推進器なしは 8 FE/t)、入力なしは 0。入力量は速度計算にだけ使う。
  ※ SUB02 の「8 FE/t × 入力量」もこの一律方式に揃える (サーバーの推進判定は SUB02 どおり位置差分)。

### 1.4 深海ソナー
- 装着中かつ乗員がいる間だけ動作、4 FE/t。ENERGY 0 なら停止。トグルなし (外せば止まる)。
- サーバーで 10 tick ごとに潜水艦中心 半径 16 をスキャン:
  - 生物: 水生 LivingEntity (WaterAnimal + Abyssia fauna、プレイヤー除く) の数と最寄り 1 体の距離。表示は 10 以上を「10+」に丸める。
  - 海流: `OceanCurrentManager` / 自然海流 `getCurrentAt` の既存の値をそのまま使う (ソナー独自の海流計算はしない)。向き = 8 方位矢印、強さ = 速度 0.00-0.09 b/t「弱 →」、0.10-0.19「中 →→」、0.20 以上「強 →→→」。
  - ドック: 半径 32 以内の `submarine_dock` の距離と方角 (ドック中は出さない)。既存の管理 (ドック/ムーンプール登録) があればそれを引く。無ければ半径 32 の範囲内 BlockEntity だけを探す (ワールド全体は探さない)。
- 表示: SUB02 の action bar の後ろに追記 「⚡ 85% ❤ 100% | ◎ 生物 3 (8m) 海流 → ドック ↖ 24m」(lang キー)。
- 新規検出は `Set<UUID>` で前回検出済みを保持し、未検出→検出になった個体だけ演出 (範囲外に出たら Set から消し、再侵入で再演出)。検出の瞬間、潜水艦の周りに `minecraft:bubble_pop` (or note) パーティクル数個 + 小さな音 (クライアント通知不要、サーバーからのパーティクル送信で可)。
- ブロック (鉱石) スキャンはしない (負荷回避)。

## 2. 装着 UI
- 潜水艦の外から **スニーク + 右クリック** (潜水艦が無人のとき、ドック中を含む) で「潜水艦システム」画面を開く。
  搭乗中の Shift は vanilla の降車なので、搭乗中は開けない (キー割り当ても追加しない)。
  ポッド判定より先に判定 (スニーク中はポッドも搭乗もしない)。
- 画面: 4 スロットを横一列 + プレイヤーインベントリ (ChestMenu 風の独自 `AbstractContainerMenu`、背景はバニラの dispenser 画面テクスチャ流用か 1 行のチェスト背景)。
  上部に 「⚡ x / y FE  ❤ z%」テキスト。
- スロットは種類固定: 0 = 船体 (pressure_hull)、1 = 電源 (battery)、2 = 推進 (thruster)、3 = 補助 (sonar)。
  `mayPlace` で対応アイテムのみ、max stack 1。よって同種の重複は構造上不可能。
- stillValid = 潜水艦が生きていて距離 8 以内、かつ無人。スロット操作はサーバー側でも同条件 (存在・距離 8・無人・破壊されていない) を再検証。
  画面を開いている間に誰かが搭乗したらメニューを閉じる。
- interact の判定順: `player.isShiftKeyDown() && !isVehicle()` → アップグレード画面 (最優先) → ポッドのレイ判定 → 搭乗。
- 保存: エンティティ NBT `Upgrades` = CompoundTag `{Hull, Battery, Thruster, Utility}` に各スロットのアイテム ID 文字列 (空スロットは書かない)。ItemStack 丸ごとは保存しない。
  NeoForge 1.21.1 のアイテム側は Data Component (同じ 4 キー) で持つ。効果判定用に SynchedData `UPGRADES` (byte ビットマスク) をサーバーで更新しクライアントの速度計算に使う (操縦はクライアント側)。

## 3. 破壊・アイテム化
- 潜水艦が壊れて `submarine` アイテムになるとき、アップグレードはアイテムの NBT (`Upgrades`、同形式) に ENERGY と一緒に保持し、設置時に復元する (ドロップしない)。
  DAMAGE は保持しない (アイテム化は破壊時のみなので、保持すると常に破壊寸前で復元される。SUB02 どおり設置時は損傷 0。2026-10-04 実装時に変更)。
  ENERGY はアップグレード込みの最大値まで保持 (バッテリー付きなら最大 150,000)。ツールチップに装着中アップグレード名を表示。
- creative の即撤去はアップグレードをその場にドロップ (収納ポッドと同じ扱い)。

## 4. レシピ (3x3 有形)
汎用名 → 実アイテム候補 (実装時に確認、check_recipes.py を通す):
深淵合金 = `abyssia:abyssal_alloy_ingot`、導電合金 = `abyssia:conductive_alloy_ingot`、推進スクリュー = `abyssia:propulsion_screw`、
耐圧ガラス = `abyssia:pressure_shell` (無ければ耐圧殻で代替)、バッテリー = `abyssia:abyssal_energy_cell`、モーター = `abyssia:propulsion_screw` 以外の既存電動部品が無ければ `abyssia:iron_plate` + レッドストーンブロックで代替、
生体油 = `abyssia:bio_oil`、シーランタン = `minecraft:sea_lantern`。

- 耐圧船体:
  ```
  深淵合金 耐圧ガラス 深淵合金
  耐圧ガラス 鉄ブロック 耐圧ガラス
  深淵合金 深淵合金 深淵合金
  ```
- 大容量バッテリー:
  ```
  導電合金 銅ブロック 導電合金
  レッドストーン バッテリー レッドストーン
  導電合金 金ブロック 導電合金
  ```
- 高出力推進器:
  ```
  深淵合金 推進スクリュー 深淵合金
  鉄ブロック モーター 鉄ブロック
  深淵合金 レッドストーン 深淵合金
  ```
- 深海ソナー:
  ```
  シーランタン 耐圧ガラス シーランタン
  銅インゴット 生体油 銅インゴット
  レッドストーン 深淵合金 レッドストーン
  ```

## 5. ルール / Config
- 4 種は相互排他なし、フル装備可。速度倍率は乗算: 基本 (推進器で置換) × 船体 0.92 × (上下のみ) バッテリー 0.95。
- Config `[submarine.upgrades]`: hull_max_damage 70, hull_deep_reduction 0.5, hull_speed_mult 0.92, base_thrust_fe_per_tick 8, battery_capacity 150000,
  battery_vertical_mult 0.95, thruster_forward 0.56, thruster_side 0.30, thruster_vertical 0.23, thruster_fe_per_tick 12, thruster_accel 0.05,
  sonar_fe_per_tick 4, sonar_range 16, sonar_interval 10, sonar_dock_range 32。深層の境界は既存定数 (-64) を使う。

## 6. テクスチャ (ChatGPT 生成)
`inbox/textures/submarine_upgrade_<id>.png` (16x16 RGBA、背景 alpha 0)。ChatGPT の 4x1 画像から in-page で 16 セル中央値 + k-means 12 色 (最遠点初期化)、
孤立ピクセル除去。実装時に assets/abyssia/textures/item と tools/texture_locks の両方へコピー。

## 7. ChatGPT レビュー (1 回、2026-10-04)
大枠は合格・矛盾なし。指摘を反映済み:
- [要修正] 速度計算順の固定、加速後の最大速度 clamp、推進 FE を入力ありの tick で一律 (斜め/同時押しで増えない)、通常推進 8 FE/t も Config 化、ソナーの海流強度を既存値から段階表示 → §1.3 / §1.4 / §5。
- [要修正] Upgrades の NBT 形式をスロット別 ID 文字列に固定 → §2。
- [推奨] 深層判定は getY()、軽減は ceil、バッテリー装着時は現在値維持、ソナー生物数 10+ 丸め・UUID で新規検出、ドック探索を範囲限定、サーバー側再検証・搭乗でメニューを閉じる・スニーク優先順、アイテム化で ENERGY も保持 → 反映 (DAMAGE 保持は実装時に取り消し、§3)。
- バランス所見: フル装備の前進 0.56×0.92 = 0.515 b/t (+23%)、推進燃費 +50% で妥当なトレードオフ。4 枠コンプリートが「遠征用の完成型」になる想定。
- 実装メモ (Claude): 素材名は §4 の候補対応で実アイテムに置換 (耐圧ガラス・モーターは該当アイテムが無いので要決定)。新レシピは既存と衝突しないこと (check_recipes.py)。
