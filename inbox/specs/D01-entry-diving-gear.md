# D01 入門潜水装備 (地上→深海用、バニラ素材)
tier: standard
files: src/main/java/com/abyssia/item/EntryDivingGear.java (新規), src/main/java/com/abyssia/registry/ModItems.java (register 呼び出し1行のみ),
       src/main/resources/data/abyssia/recipes/entry_*.json, assets/abyssia/models/item/entry_*.json,
       assets/abyssia/textures/item/entry_*.png, assets/abyssia/textures/models/armor/entry_diving_layer_{1,2}.png,
       lang en_us/ja_jp, tools/texture_locks (新アイコン登録)
goal: バニラ素材だけで作れる4点の入門潜水装備。深海層まで潜れるが、深海装備 (deep_diver_helmet 系) より明確に弱い。
constraints:
- 原本: inbox/specs/D01-chatgpt-raw.md (ChatGPT)。下記は Claude の修正込みの確定版
- 修正1: ChatGPT は「胴体 ArmorItem を追加しない」としたが、既存 dive_tank は CHESTPLATE。「既存 dive_tank と同様」に従い entry_dive_tank は CHESTPLATE
- 修正2: タンクのレシピ (4列で不正) を 3x3 に修正
- 水中呼吸・暗視エフェクトは付与しない (暗視は ChatGPT 第一候補どおり無し)。空気補充は耐久を消費 → 耐久が尽きれば終わり
- Abyssia 素材禁止。onInventoryTick (装備中のみ) / getDefaultAttributeModifiers 方式。既存 ModTools/MaterialTools のパターンに合わせる
accept: gradle build 通過、4アイテムがクリエイティブタブに出てレシピで作れる、水中で空気ゲージが補充され耐久が減る、フルセットで泳速ボーナス

## アイテム
| ID | 日本語 | English | 部位 | 防御 | 耐久 | 効果 |
|---|---|---|---|---|---|---|
| entry_diver_helmet | 簡易潜水ヘルム | Entry Diver Helmet | HEAD | 2 | 120 | 水中で air<=80 なら air=min(max,140)、CD 40t、耐久-1 |
| entry_dive_tank | 簡易潜水タンク | Entry Dive Tank | CHEST | 0 | 160 | 水中で air<=100 なら air+=40、CD 60t、耐久-1 |
| entry_diving_suit_leggings | 簡易潜水レギンス | Entry Diving Suit Leggings | LEGS | 2 | 150 | SWIM_SPEED +3% |
| entry_diving_flippers | 簡易潜水フィン | Entry Diving Flippers | FEET | 1 | 100 | SWIM_SPEED +15% |

タフネス0、ノックバック耐性0、修理素材 鉄インゴット、エンチャント適性 9 (鉄並み)、装備音 iron。
クールダウンは stack NBT ではなくゲーム時刻 (player.level().getGameTime()) と player の persistent data で管理。

## 4点セット (4つすべて entry_* のときだけ)
- 水中で SWIM_SPEED +5% (合計 +23% < abyssal_flippers 35%)
- 空気補充クールダウン 0.8 倍 (40→32, 60→48)
- 深海装備が混ざると不発 (entry_* 4点の判定のみ)。水中採掘 +5% は見送り (ChatGPT 案、効果が微小で既存 breakSpeed と干渉するため)

## レシピ (作業台)
- helmet: `IGI` / `ILI` / `I I`  I=鉄インゴット G=ガラス板 L=革
- tank:   ` I ` / `IKI` / `III`  K=ケルプ (minecraft:kelp)
- leggings: `LIL` / `L L` / `L L` (I=鉄インゴット, L=革)
- flippers: `L L` / `L L` / `S S`  L=革 S=糸

## 配色 (16x16 アイコン + 防具レイヤー、バニラ鉄/革装備の延長)
- helmet: 鉄グレー #6B7375、革 #8A4F32、ガラス #78B9C5、ハイライト #B7D6D8、影 #303638。丸い簡易ヘルム + 正面の小窓
- tank: 鉄グレーのボンベ、革ベルト、銅バルブ、水色の圧力計
- leggings: スーツ #303B3D、革 #70462F、金属 #747D7F、ステッチ #9A7658
- flippers: 本体 #40595B、ストラップ #70462F、バックル #777D7D、ハイライト #6E9294。横向きフィン
- 発光・ティール主体は禁止 (深海装備との差別化)
