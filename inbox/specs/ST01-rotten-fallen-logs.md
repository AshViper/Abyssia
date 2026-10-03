# ST01 朽ち木・倒木の海底構造物

依頼 20261003-155438。設計は ChatGPT (原文: `GL01-ST01-FS01-BS01-VD01-chatgpt-raw.md`)。Claude 補足は末尾。
tier: standard
files: tools/seabed_structures.py (2構造 + PROFILES 追記), src/main/java/com/abyssia/worldgen/structure/formation/RottenTreeFormation.java と FallenLogFormation.java (新規), Formation.java の switch に case 2つ
goal: 海底に立ち枯れの「朽ち木」と横倒しの「倒木」を ancient 木材セットで描く。壊すと ancient 木材が手に入り、木材入手の補助になる。
constraints:
- 朽ち木 rotten_tree: 高さ 3〜8、幹の太さ 1〜2 (2 は 2x2)、不規則な枝 0〜3本 (横向き ancient_stem, axis 付き)、根元に 1〜3 ブロックの根 (ancient_root / ancient_wood)。上端は折れた形 (幹の最上段を欠く、stripped を混ぜる)。
- 倒木 fallen_log: 長さ 5〜14、太さ 1〜2、枝 0〜4本、axis を倒れた向き (x / z) に合わせる。一部を海底に 1 段埋め、途中で 1 ブロック横ずれさせて完全な直線にしない。
- ブロック: ancient_stem / stripped_ancient_stem / ancient_wood / stripped_ancient_wood が主、ancient_frond を少量 (朽ち木の枝先のみ)、ancient_planks は折れ口にまばら。新ブロックなし。
- 深さ Y=-340〜-80 (深海層)、海底に接地できる所だけ。密度は有効海底チャンクあたり平均 0.05〜0.15 (small tier, spacing は既存 small と同程度)。
- 出現: 古代樹・古代遺跡系と相性のよい深海バイオーム (PROFILES で ancient/ruin 系 + 一般の深海平原に低確率)。
- 既存構造物の配置・座標系・衝突判定は変えない。生成物 JSON は gen_worldgen.py が書く (手書き禁止)。
accept: 新規ワールドで `/abyssia structures locate rotten_tree|fallen_log` で見つかり、自然な形、空中/地中に大きく埋もれない、壊すと ancient 木材、gradle build 通過。

## Claude 補足
- データ形式: `structure(name, category, tier, radius, max_height, formation, anchor=, dressing=, mobs=, **conditions)` (seabed_structures.py:68)、`f(kind, **params)` (:92)、PROFILES (:426) の `e(name, chance, spacing)`。
- Java: `worldgen/structure/Formation.java:30-42` の type switch に case を足し、`formation/XxxFormation.java` (TYPE, CODEC, paint(Site, Painter)) を作る。近い前例は `fallen_crystal_spans` (seabed_structures.py:316)。Painter.place=固体、fill=水だけ埋める、carve=掘る。
- 木ブロックの axis は `RotatedPillarBlock.AXIS`。ancient_stem は StrippableLogBlock (ModBlocks.java:240)。
- ancient ブロックのルートテーブルは既存 (自分を落とす) なので変えなくてよい。
