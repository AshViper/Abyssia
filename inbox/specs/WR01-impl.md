# WR01 実装メモ (Main → coder、ChatGPT 仕様 `WR01-wireless-power-relay.md` の実装上の確定事項)

ChatGPT 仕様を正とし、曖昧な所だけここで決める。Forge (`F:\Java\Abyssia`, main) と NeoForge (`F:\Java\Abyssia-NeoForge`, NeoForge1.21.1) に**同じクラス名・同じ数値**で入れる。

## 触ってはいけないもの
- `habitat/power/HabitatBases.java` / `HabitatPower.java` は別セッションの作業中 (未コミット)。**編集禁止**。既存の public メソッドだけ使う:
  `HabitatBases.get(level)`, `.moduleAt(pos)`, `.baseAt(pos)`, `.root(id)`, `.energy(base)`, `.setEnergy(base, v)` (呼んだら `setDirty()` されるか確認、されないなら relay 側で何もしない — HabitatBases は SavedData なので `data.setDirty()` は public)、`HabitatPower.CAPACITY`。
- 他の未コミット変更 (inbox/ 等) も触らない。

## ファイル
- Java: `com.abyssia.habitat.relay` (新規) + `habitat/build/BuildContent.java` に 1 行 (`com.abyssia.habitat.relay.RelayContent.register(bus); // WR01`) と applyOrder の `"charging_station"` の直後に `"wireless_power_relay"` + `client/build/ClientBuildContent.java` に 1 行 + `network/AbyssiaNetwork.java` に packet 1 件 (次の空き id)。
- アセット: `tools/bt01/relay_assets.py` (blockstate / block model / build-menu アイコンの参照だけ。**PNG は作らない**: テクスチャは ChatGPT が描いて Main が取り込む)。`tools/gen_deep_assets.py` の bt01 パート一覧に `relay` を足す (他パートと同じ呼び方)。
- lang: 既存の habitat 設備 (charging_station) と同じ生成元に ja/en を追加 (`habitat.abyssia.mode.wireless_power_relay`, `.detail`, ブロック名, `message.abyssia.relay.*`)。lang JSON を手で直接書かず、charging_station の行がどこで生成されているか探してそこに足す。

## ブロック
- `wireless_power_relay` (下段, BlockEntity あり, FACING 水平) + `wireless_power_relay_top` (上段アンテナ, BE なし)。両方 noLootTable, item なし (charging_station と同じ)。どちらかが壊れたらもう片方も消す。当たり判定は下段=筐体 (ほぼ全ブロック)、上段=細いアンテナ柱。noOcclusion。下段の lightLevel 3 (未リンク) / 7 (リンク中) は blockstate `LINKED` で。
- テクスチャ名 (Main が用意): `abyssia:block/wireless_relay_body`, `wireless_relay_copper`, `wireless_relay_antenna`, `wireless_relay_core` (発光コア、emissive)。アイコン `item/habitat_icon_wireless_power_relay`。
- モデル形 (デザイン画 `inbox/designs/WR01.png` に従う、px): 下段 = 台座 1..15 x 0..2 + 筐体 2..14 x 2..13 x 2..14 + 角の銅補強柱 4 本 (各 3x3, y 2..12) + 上部の銅帯 (y 10..12 周回) + 天板 4..12 x 13..16 (中央に鉄色の小キューブ) + **正面 (FACING 側) に縦長の発光パネル 6..10 x 4..10 (シアン, emissive)**; 上段 = 銅の根元 6..10 x 0..2 + 柱 7..9 x 2..10 + 最上部の十字アンテナ: 横腕 1..15 x 10..12 (x方向) と 1..15 (z方向) の十字、4 本の腕先に 3x3 の鉄色キャップ、中心のコア 6..10 x 9..14 (シアン, emissive) + その上の細い先端 7..9 x 14..16。emissive の書き方は既存モデル (`forge_data`/`neoforge_data` の block_light/sky_light 15 など) に合わせる。
- アンテナ先端 (発光線の端点) = 上段ブロック原点 + (0.5, 12/16, 0.5)。

## 建設メニュー
- `RelayEntry implements BuildEntry`, id `wireless_power_relay`, カテゴリ EQUIPMENT。コスト: 鉄インゴット12、銅インゴット8、レッドストーン8、ガラス4、鉄ブロック2。
- 置き場所: 照準セル (ChargingStationEntry.plan と同じ落下処理)。**拠点外でも可**、拠点内も可。条件 = 2 セル (pos, pos.above()) が空気か水 (置き換え可能)、下が上向きに固い面。殻への接触は不要。チェック失敗キー `relay_floor` / `relay_space` (lang も)。回転 R = FACING。
- 解体: ChargingStationEntry と同じ (2 ブロック消して 80% 返却)。水中に置いたら消した跡は水に戻す (BuildStep の置換前状態)。

## リンク (サーバー)
- `RelayNetwork extends SavedData` (レベル単位): relay 位置の集合 + リンク (無向ペア) を保存。BE の onLoad/setRemoved ではなく、**設置 (BuildEntry の layout 完了 / block onPlace) で add、onRemove で remove**。
- 自動リンク: 20 tick ごと、空き枠 (<2) のある relay について、距離 ≤128 (3D ユークリッド, アンテナ先端間) で空き枠のある relay を近い順に試し、**両方**に空きがあればリンク。既存リンクは張り替えない (仕様の「毎 tick 再選択しない」)。距離 >128 になったり片方が消えたらリンク削除 → 次の探索で再リンク。視線・水・地形は無視。
- 片方のチャンクが未ロードならリンクは保持したまま **一時停止** (送電なし、クライアントに paused として送る → 描画しない)。chunk ticket は使わない。

## 送電 (サーバー, 毎 tick, RelayNetwork が level tick で処理)
- relay の端点 = 「拠点端点」か「バッファ端点」:
  - 拠点端点: relay 下段の位置が登録済みモジュール箱の中 (`moduleAt(pos) >= 0`)。その拠点の共有 FE (`energy(root)`) を直接読み書きする。容量 `HabitatPower.CAPACITY`。
  - バッファ端点: それ以外。BE の内部バッファ 1,024 FE。ENERGY capability (全面、receive/extract 両方可 → ケーブル網では buffer 扱いになり、発電機→relay、relay→機械が既存ケーブルで流れる)。さらに隣接 6 面の FE 受け取り機器 (ケーブル以外、他の relay 以外) へ毎 tick 押し出す (上限 512 FE/t)。
  - 拠点端点の relay の capability は receive→拠点 FE に加算、extract→拠点 FE から取る (内部バッファは使わない)。
- 各リンクを 1 tick に 1 回だけ処理 (再帰・経路探索なし = ループで増殖しない):
  - 充填率 r = stored / capacity。r の高い方 → 低い方へ。差が 0.5% 未満なら送らない。
  - 送り量 `send = min(512, src.stored, ceil((rS - rD) * capS*capD/(capS+capD)))`、受け側到着量 `got = floor(send * eff)` を受け側の空き容量でさらに制限し、制限したら send を逆算 (`send = ceil(got / eff)`)。src から send を引き、dst に got を足す。差分 (損失) は消える。
  - eff: 距離 ≤32 → 1.00, ≤64 → 0.90, ≤96 → 0.80, ≤128 → 0.70。
- 1 リンクの最近の送電量 (直近 20 tick 平均) と向きを保持 → 状態表示とクライアント同期に使う。

## クライアント同期
- `RelaySyncPacket` (PLAY_TO_CLIENT): そのレベルの全リンク `{BlockPos a, BlockPos b, byte state}`。state: 0=paused(描画しない) / 1=idle(リンクのみ) / 2=a→b 送電 / 3=b→a 送電、+ 送電量の段階 (0..3, 128/256/384 FE/t 区切り) を別 byte で。
- 送るのは: リンク追加/削除、state か段階が変わった時 (変化があった tick の終わりにまとめて 1 回、最短 10 tick 間隔)、プレイヤーのログイン・次元移動 (W01 WaypointSyncPacket と同じやり方を参考に)。
- クライアントはリストを保持し、レベルが変わったら消す。

## 描画 (クライアント)
- `RelayBeamRenderer`: `RenderLevelStageEvent` AFTER_TRANSLUCENT_BLOCKS (CurrentStreamRibbons を参考)。カメラから両端の中点まで ≤96 ブロック、かつリンク長 ≤96 のリンクだけ描く (仕様: 97..128 は描かない)。
- 線: アンテナ先端 → 相手のアンテナ先端。カメラ向きのビルボード帯 (太さ 1.5/16 ブロック) を 16 分割して、各点に小さな揺らぎ (sin, 振幅 0.04、時間で変化)。色 シアン (0x3FE6D8 前後)。加算合成でフルブライト、深度テストあり・深度書き込みなし (地形の向こうは隠れる。仕様の「貫通表示」は "地形で遮断されない" の意味なのでリンク成立の話。描画は普通に深度テスト)。
- idle: アルファ 0.35 で周期 3 秒のゆるい明滅。送電中: アルファ 0.8 + 光の粒 (線上を送電方向に流れる明るい短い区間 2〜3 個、速度は段階で 1.0〜1.6 倍)。
- アンテナのコア: リンク中は明るい、未リンクは暗いテクスチャ切替 (blockstate LINKED で上段モデルを切り替え、`wireless_relay_core` と `wireless_relay_core_off`)。

## 状態表示
- 下段/上段をスニーク+素手で右クリック → アクションバー (charging/HabitatPower と同じ書式): リンク数 n/2、各リンクの距離・効率%・最近の FE/t・向き(送信/受信)、端点種別 (拠点 / 単独 xxx/1024 FE)。

## テスト (Main が実施、coder はビルドまで)
- Forge: `./gradlew build` が通ること (gradle はメインだけ…ではなく今回は各 coder が自分のツリーで `compileJava` まで実行してよい。runClient/runServer は禁止)。
