# T02 深海タイトル画面背景
tier: standard
files: src/main/java/com/abyssia/client/title/* (新規), Config (client: title_panorama), assets/abyssia/textures/gui/title/background/panorama_0..5.png,
       lang (config 名), devtest は scratch のみ
goal: タイトル画面の回転パノラマを、ゲーム内で組んだ深海シーンの6方向撮影に置き換え、控えめな水中演出 (暗青オーバーレイ・泡・微粒子) を足す
constraints:
- 原本: ChatGPT 「深海タイトル画面背景 仕様書」(2026-10-02)。以下は Claude の実装判断込みの確定版
- 置換方法: assets/minecraft のパノラマは上書きしない。ScreenEvent.Init.Post で TitleScreen.panorama (SRG f_96729_) を AbyssPanorama (PanoramaRenderer サブクラス、abyssia の CubeMap) に差し替え
- リソースパック優先: minecraft:textures/gui/title/background/panorama_0.png の提供元が vanilla 以外 (他パック) なら差し替えない
- client config `title_panorama` (既定 true) で OFF にするとバニラ。演出は `title_panorama_effects` (既定 true)
- 演出は CubeMap 描画の直後・バニラ overlay の前に描く (AbyssPanorama.render 内)。控えめ、ロゴ/ボタンを邪魔しない
- ロゴ変更は対象外
accept: build 通過、タイトル画面で深海パノラマが回転、config OFF でバニラ、スクリーンショットで確認

## シーン (撮影は scratch の AutoShot で Minecraft#grabPanoramixScreenshot)
- 深海層 (Y 約 -200)、夜、天候なし。暗く青～青緑、遠景はフォグで消える
- カメラ: 海底から 10～15 ブロック上、水平
- 配置: 中央付近に大きな熱水噴出孔 (暖色光)、発光植物 (青～青緑)、漂うクラゲ、深海魚数種、岩・起伏、海底構造物、一部に工業ブロック/作業灯 (弱い人工光)、遠景に大型構造物のシルエット
- 360° どこを向いても深海らしく見えること
