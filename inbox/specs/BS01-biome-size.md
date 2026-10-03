# BS01 深海マクロバイオームのサイズ縮小

依頼 20261003-155757。設計は ChatGPT (原文: `GL01-ST01-FS01-BS01-VD01-chatgpt-raw.md`)。Claude 補足は末尾。
tier: standard
files: tools/gen_worldgen.py (スケール定数・ノイズ設定・biome_sources の閾値) と、その生成物 (data/abyssia/worldgen/**、手書き禁止)
goal: 深海層 (Y -368..-64) の1バイオームを半径約64チャンク (直径約2048 blocks) 程度にし、少し移動すれば別のバイオームに行けるようにする。
constraints:
- 主分布: 直径 1500〜3000 blocks。数千〜数万 blocks 同じバイオームが続かない。
- 同一バイオームの長距離連続を抑える (閾値帯/重みの調整、隣接で同じものになりにくくする)。
- 境界は既存ノイズ方式のまま (格子・直線にしない)、遷移幅 約64〜192 blocks。
- 深海層だけ。海面〜Y -64 の上層バイオーム (CLIMATE_SCALE、上層の source) は変えない。
- 既存ワールド互換は不要 (新規ワールドのみ)。地形の形 (山・海溝) が大きく変わるのは避ける。
accept: 新規ワールドで `/abyssia map` (深海層) を見て、大部分のバイオームが直径 1500〜3000 blocks、同一バイオームの長い連続が減り、格子状でない。上層に変化なし。gradle build 通過。

## Claude 補足
- 定数 (gen_worldgen.py): `MACRO_SCALE = 2.5` (:139、深さ帯バイオームと地形 base/山/海溝の共通ノイズ = 約5000 blocks 規模)、`CLIMATE_SCALE = 2.0` (:140、上層のみ)、`REGION_SCALE = 9.0` (:141、region_volcanic / habitat / temperature / erosion / trench_region の xz = 1/REGION_SCALE、firstOctave -8 → 約 1/2304 per block)。深海層の climate router は DEEP_CLIMATE (:1455-1461)、閾値表は biome_sources() (:1396+)。H/W/T/E 帯はノイズの裾にあり、各地方は海底の 4〜10%。
- 方針: 地形と共有の MACRO_SCALE は触らず (地形が変わる)、バイオーム選択専用のノイズのスケールを縮める (REGION_SCALE 9→約4〜5、深さ帯バイオームが continentalness に依存しているなら、バイオーム選択には別の細かいノイズ (biome_fuzz の比率を上げる等) を使う) + 裾の閾値を広げて地方の出現率を上げる。どの数値にしたかと理由を報告する。
- 検証: 生成後に main が新規ワールドで /abyssia map を撮る。可能なら gen_worldgen.py 側で閾値帯ごとの面積比を概算して報告。
