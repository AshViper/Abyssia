# AB06 最深層の巨大空洞と深淵への道（2026-10-10 ユーザー依頼）

依頼: 「最深層のバイオームで生成されるはずの空洞や巨大洞窟の生成がない。1 つの空洞を 20x20 チャンクぐらいの規模にして、深層と最深層がつながる空洞の道も用意してほしい」

## 現状（実測 2026-10-10, NeoForge scratch, seed 777）
- 帯 B'〜E の洞窟網は計画どおり掘られている（例: B' の massive 広間 88x104 は水で満たされて存在）。
- ただし最深層の岩のうち洞窟は 0.6〜3%（census r64）。最大の mega でも幅 130〜235、大半は 40〜100。
- 深海層からの入口がない: B' 窓は `above == null` で縦リンクを持たず、hadal shaft は Y -560 の固い岩で終わる。→ 降りても空洞に出会えない。
- チャンク生成 0.276 秒/チャンク（forceload 10x10, x/z 5008）。

## 仕様
### 1. 巨大空洞 `ABYSS_CAVITY`（新 CaveType, Size.MEGA, 窓のみ）
- 各帯の窓に粗い格子（スーパーセル = 窓のセル 4x4、帯ごとに別ソルト）。1 スーパーセルに最大 1 個、確率 0.8。
  置くセルと位置はスーパーセルの種から決める（planWindow を呼ばずに決まること＝再帰しない）。生物群系にプロファイルが無ければ置かない。
- 半径 155〜175 で開始（実測の広間幅は ≒1.7〜2.0×r。`caves hall` の幅 280〜340 ≒ 18〜21 チャンクになるよう実測で調整）。形は既存の広間（`CaveShape.Hall` + HallFormations/HallDecorator、水で満たす）。satellite hall は付けない（branches のみ）。
- 高さ: 窓に収める。半径は縮めず高さを縮める（帯で入る最大: B' ≒95（上の余白 12、下 6）、C/D ≒100、E ≒80（余白 6））。
- 空洞の中心・半径・セルはスーパーセルの種だけで決める（profileAt の前）。profile 無しなら「置かない＝抑制もしない」で一貫。スーパーセルはセル単位（Config.BAND_SPACING 可変）。
- 空洞の中心から R+60 以内にハブがある通常の系は作らない（隣接 3x3 スーパーセルを見る）。空洞の系は通常どおり branches / connectors を持つ。
- ABYSS_CAVITY は profile の caveTypes 抽選に入れない（initTypeWeights/typeScale で 0）。MEGA_CAVERN を特別扱いしている所（hallType、CavernPlanner の Tier.MEGA、HallFormations の size、mayReach、CaveCommand の MEGA 分岐 等）は新タイプも同じ扱いに。
- 定数はコード内（Config は別作業が編集中なので触らない）。

### 2. 深淵への道（routes）
- **B' の空洞 → 深海層の海底**: 空洞の壁上部の port から、折り返し 3〜4 区間の斜坑（半径 6〜8、水）で上り、海底に開口する。
  開口は空洞中心から水平 R+80〜R+160。各折り返しに小部屋（r 8〜12）。最上段は海底 Y+4 まで掘る（海底 Y は root ネットワークの seabed 関数）。開口部は広めの漏斗（r ~10）。
  → 窓に `root` 参照を追加（`above` は流用しない: vlink が root の plan を拾うため）。B' の `carveTopY` を root の maxY に。開口点は root の seabed ≤ maxEntranceY の所を選ぶ。海底との高低差が大きければ区間を増やす（reach 内）。
  → CaveChunk: 窓のチャンクで yMax > 窓の maxY（経路が深海層に入るチャンク）は seabed[] を `deepFloor(sections)` で埋める（地層 stratum の深さが負にならないように）。finish() で yMax ≥ ABYSS_TOP_Y なら heightmap（OCEAN_FLOOR_WG 等）を更新。掘ったブロックが水・空気なしを確認。
- **C/D/E の空洞 → 上の帯**: 既存の vlink / verticalLink を拡張: 空洞の Plan は確率 1、目標は上の窓の最寄りの**空洞**（水平 ≤ 430）、無ければ従来どおり最寄りの系のハブ。半径 5〜7。mayReach の VLink 分岐はそのまま使える。
- 経路は系の reach（640）内に収める: 水平 ≤ 640 − 半径 − meander(0.33L+24)。mayReach に空洞の半径と B' 経路を含める。
- 経路の要約を系の summary / entrances に出す（`descent route > seabed entrance@x y z`）。

### 3. デバッグ
- `/abyssia caves locate abyss_cavity` が使えること。
- `/abyssia caves cavities <radius>`: 全窓の空洞を計画から列挙（中心、幅、高さ、環境、経路の開口・接続先）。チャンクは生成しない。

## 完了条件（実機, 数値で報告）
1. NeoForge / Forge、normal / ocean_world、seed 2 種で: 2000x2000 内の空洞数（窓ごと）、`caves hall` の幅が 280〜340。
2. 生成済みチャンクで空洞の中心柱が水（`execute if block`）、census で空洞域の洞窟率。
3. B' 空洞の海底開口から空洞まで、経路上の点が水（途中に岩・空気なし）。C/D/E 空洞から上の帯へ同様。
4. チャンク生成時間: 無作為の区域でベースライン 0.276 秒/チャンクから +15% 以内。空洞の直上は既存の mega 直上と比較して報告。
（ocean_world も最深層の 12 バイオームを含むので空洞が出るのが期待値。コメント「the ocean world ends up with an empty network」は古い可能性 → 実機で確認。）
5. 見た目（クライアント）は可能なら AutoShot、できなければ未検証と明記。
