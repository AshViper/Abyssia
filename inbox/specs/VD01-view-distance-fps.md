# VD01 深海の視界拡大と振り向き時の FPS 低下

依頼 20261003-155852。設計は ChatGPT (原文: `GL01-ST01-FS01-BS01-VD01-chatgpt-raw.md`)。Claude 補足は末尾。
tier: heavy
files: client/DeepOceanClientEffects.java, client/ShaderFogPass.java (必要なら), ClientConfig.java, entity/Tubeworm.java, tools/fauna_java.py (GeneratedFauna の sessile テンプレート), fauna/FaunaSpawner.java, environment/ParticleBudget.java, client 側のパーティクル (AbyssParticle / CurrentParticle)
goal: 深海でもフォグ終端を約144 blocks (9チャンク) にして遠くが見えるようにし、一度行った場所へ振り向いたときの FPS 低下をなくす。
constraints:
- クライアント設定 `deepSeaFogDistance` (ClientConfig、既定 144、範囲 64〜192)。深海のどの深さでもフォグ終端 ≒ この値 (マリンスノー/噴出孔ヘイズ/洞窟係数で縮めても 0.6 倍まで)。
- 深さの雰囲気は距離ではなくフォグの濃さで出す: 深いほどフォグ開始 (near) を手前に、フォグ色を暗く。終端付近は完全に消さず暗い深海色の遠景にする。
- ShaderFogPass (Iris/Oculus) でも同じ距離。非シェーダーでも同等。
- 視界を広げてもパーティクル数を比例して増やさない (カメラ距離で上限)。
- FPS: 訪問済みの場所に溜まる負荷を断つ。疑わしい原因 (Explore 調査): sessile 生物 (tubeworm, satsuma_tubeworm, sea_lily, venus_flower_basket) が `removeWhenFarAway = false` で消えず、FaunaSpawner は半径96内だけ数えるので訪れた先々で増え続ける。→ プレイヤー由来 (名付け・バケツ等の requiresCustomPersistence) 以外は遠くで消えるようにするか、チャンク/領域あたりの上限を設ける。どちらにしたか報告。
- ParticleBudget の ALIVE カウンタが remove() を通らずに消えたパーティクルで増えたままになる問題も直す (上限で新規が出なくなる)。
- Mod 独自のキャッシュがあれば上限付きにするかワールド離脱時に破棄する (CarrionScent SCENTS/SINKING などを確認)。
- バニラのチャンク描画は置き換えない。
accept: 深海で約144 blocks 先の地形が見え、遠景は暗い深海色。広く移動したあと元の場所へ振り向いても FPS が大きく落ちない (main が実機で 正面→180度旋回 を5回測る)。エンティティ数が移動距離に比例して増えない (`/abyssia fauna` や F3 の E: で確認)。シェーダー有無でフォグが破綻しない。gradle build 通過。

## Claude 補足
- フォグ距離の現状: DeepOceanClientEffects.java:45-47 SURFACE_FOG_END 96 / BOUNDARY_FOG_END 28 / ABYSS_FOG_END 10、targetFogEnd (:133) でマリンスノー・ヘイズ・洞窟・暗視で倍率、onRenderFog (:156-175) が far を設定し ShaderFogPass.submit(near, end) (ShaderFogPass.java:42-77)。ON/OFF は Config.DEEP_OCEAN_ENABLE_FOG、ClientConfig.java:19-44 に距離設定は無い。
- sessile: entity/Tubeworm.java:149、ModEntities.java:76-79、GeneratedFauna.java:211,267 (fauna_java.py が生成、直接編集しない)。FaunaSpawner PLAYER_RADIUS = 96 (:54, :105, :227-231)。
- ParticleBudget の減算は AbyssParticle.java:139 と CurrentParticle.java:87 の remove() だけ。
- クライアント設定は ClientConfig に置く (decisions/client-options-in-clientconfig)。
