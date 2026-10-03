# CU01 海流帯 (Current Stream)
依頼: inbox/requests/20261003-221228.json。ChatGPT 原本: `CU01-chatgpt-raw.md` (数値・見た目・accept はそちらが正)。
tier: heavy。Forge (main) と NeoForge (NeoForge1.21.1) を同時に実装する (ユーザー指定)。両方で同じクラス名・同じ数値・同じ生成結果 (同じシードで同じ帯) にすること。

## 実装の確定 (Claude、両ローダー共通)
- パッケージ `com.abyssia.environment`: `CurrentStream` (不変 record: id, 制御点 List<Vec3> 4-6 個の Catmull-Rom 中心線, 長さ, 半径 (直径 8-20 → 半径 4-10), strength (0.65-1.2, NORMAL 最頻), 流向 = 中心線の接線), `CurrentStreams` (生成 + 照会: セル 192 のグリッド、層ごと (上層 Y4..52 / 深海層 -330..-110、深海層を主に) の抽選 0.20、NaturalCurrents とは別の塩 (salt を "cu01" で派生させた別系列)、近傍 3x3 セル探索、セル内で中心同士 64 未満は後者を捨てる、照会 `sample(level, pos)` → 最寄り点の (方向, r=正規化半径, flowMultiplier = 1 - smoothstep(0.65,1,r), strength))。決定的: シード + セル座標だけから生成 (チャンク読み込み順に依存しない)。
- 押し流し: `OceanCurrentPush.push` に CU01 を追加。目標速度 = 方向 x clamp(base_flow_speed 0.34 x strength, 0, max_flow_speed 0.40) x flowMultiplier。現在速度の流れ方向成分を毎 tick 係数 0.25 で目標へ補間 (入る/出るとき 1-3 tick でなじむ)、それ以外の成分は触らない。岩/固体の中や水の外では 0。対象 = Player/Mob/Item/Boat (コンフィグで個別)、CurrentResistant と無効タグは既存と同じ。NaturalCurrents と重なったら速度を合成し、絶対速度の上限 0.60。
- `NaturalCurrents.getCurrentAt` を CU01 も返すよう拡張 (強い方 or 合成)、発電機の式 120 x strength は変更しない。
- クライアント: 既存の塩パケット (`NaturalCurrentSaltPacket`) に CU01 の設定値 (enabled, chance, cell, 長さ/幅/強さの範囲, base/max speed) を追加してクライアントでも同じ帯を再構築 (PROTOCOL を 1 上げる)。
- 見た目: 既存の `current_mote` 粒子 (vanilla PARTICLE_SHEET_TRANSLUCENT = シェーダーでも見える) を流用し、CU01 用に「流れに乗る連続した筋」を出す `client/CurrentStreamClient`: 帯ごとに LOD (0-32 / 32-64 / 64-96、96 超は描かない)、粒子数 80-160 / 30-80 / 10-30、全体予算 cu01_particle_budget 600 (近い帯・画面中央優先)。粒子: 幅 0.025-0.08 (近距離アクセント 0.12)、長さ 0.8-2.5 (最大 3.5)、寿命 20-60、流れ速度で移動、色 #EAF8FF、アルファ 中心 0.20-0.45 / 外周 0.05-0.20、外周 25-35% は密度を大きく下げる。粒子の生成点は帯の断面上で「レーン」を固定 (数十本の筋が束に見えるよう、帯ごとに seed で決めたレーン座標から出す)。固体ブロック内には出さない。
- コンフィグ: Config `[current_streams]` (サーバー共通: enabled, generation_chance 0.20, cell_size 192, min/max_length 64/256, min/max_width 8/20, min/max_strength, base_flow_speed 0.34, max_flow_speed 0.40, affects_players/mobs/items/boats) と ClientConfig `[current_streams]` (max_render_distance 96, particle_budget 600)。
- デバッグ: `/abyssia currents streams near [r]` (位置・長さ・幅・強さを列挙、最寄りへの座標) と `show` で中心線をダスト表示。
- テクスチャ: 既存 `current_mote.png` を使う (新規なし)。見た目が弱ければ後で ChatGPT に描かせる。
- テスト用の静的フック: `CurrentStreams.nearest(level, pos, radius)` を公開。
