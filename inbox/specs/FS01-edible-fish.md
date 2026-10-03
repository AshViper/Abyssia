# FS01 食用魚8種

依頼 20261003-155503。設計は ChatGPT (原文: `GL01-ST01-FS01-BS01-VD01-chatgpt-raw.md`、初案は既存種と重複 → 改訂版)。Claude 補足は末尾。
tier: heavy
files: tools/fauna/<id>.py (新規8), tools/fauna/__init__.py の NAMES, tools/bbmodel-generator/definitions/<id>.json (新規8), ModItems.java の食べ物 16 行, tools/gen_deep_assets.py の recipes() 調理ループに 8 組追加と ITEM_NAMES (アイテム名), 生成物 (gen_fauna.py / bbmodel cli が書く)
goal: 既存種と重ならない実在の食用深海魚8種を追加。それぞれ専用の生肉を落とし、焼くと焼き魚になる。

| id | 日本語 | English | 学名 | 体長(block) | 体色・特徴 | depth_m | placement | group | 行動 |
|---|---|---|---|---|---|---|---|---|---|
| orange_roughy | オレンジラフィー | Orange Roughy | Hoplostethus atlanticus | 1.0 | 鮮やかな橙赤、丸く体高のある体、高い背びれ、大きな目 | (180, 400, 1400, 1800) | midwater | 2-4 | ゆっくり群泳 |
| sablefish | ギンダラ | Sablefish | Anoplopoma fimbria | 1.1 | 黒〜暗灰、細長い、背びれ2枚、側面やや明るい | (200, 500, 2400, 3000) | near_floor | 1-3 | 海底付近を単独〜小群 |
| patagonian_toothfish | マジェランアイナメ | Patagonian Toothfish | Dissostichus eleginoides | 1.4 | 暗灰〜茶、太い流線型、大きな頭と口、小さな歯、背びれ2枚 | (200, 500, 1200, 1600) | near_floor | 1-2 | ゆっくり巡回、単独性 |
| black_scabbardfish | クロタチカマス | Black Scabbardfish | Aphanopus carbo | 1.2 | 金属光沢の黒銀、非常に細長い刀状、長い背びれ、細い尾 (oarfish より体高低い) | (350, 500, 1400, 1700) | midwater | 2-4 | 中層をゆっくり群泳 |
| greenland_halibut | カラスガレイ | Greenland Halibut | Reinhardtius hippoglossoides | 1.3 | 暗褐〜灰、扁平な楕円、両眼が上側、長い背・臀びれ、水平姿勢 | (200, 500, 1500, 2200) | near_floor | 1-2 | 海底を滑るように低速 |
| alfonsino | キンメダイ | Alfonsino | Beryx decadactylus | 0.7 | 鮮赤、体高が高く大きな目、二股尾 (orange_roughy より小さく丸い) | (200, 300, 700, 1000) | midwater | 2-5 | 小群、近づくと散開 |
| blue_ling | ブルーリング | Blue Ling | Molva dypterygia | 1.1 | 青灰〜銀灰、細長いウナギ状 (snipe_eel より太く普通の魚の頭)、長い背・臀びれ | (200, 400, 1000, 1500) | near_floor | 1-3 | 海底沿いを単独〜小群 |
| deepwater_redfish | アラスカメヌケ | Deepwater Redfish | Sebastes mentella | 0.8 | 赤〜橙赤、体高のある体、大きな目、棘のある背びれ | (300, 500, 800, 1000) | midwater | 2-5 | 群泳 (赤い魚影) |

| 生肉 id | 満腹/隠し | 焼き id | 満腹/隠し |
|---|---|---|---|
| raw_orange_roughy | 3 / 0.4 | cooked_orange_roughy | 7 / 0.9 |
| raw_sablefish | 3 / 0.5 | cooked_sablefish | 8 / 1.0 |
| raw_patagonian_toothfish | 4 / 0.5 | cooked_patagonian_toothfish | 9 / 1.1 |
| raw_black_scabbardfish | 3 / 0.4 | cooked_black_scabbardfish | 8 / 0.9 |
| raw_greenland_halibut | 4 / 0.5 | cooked_greenland_halibut | 9 / 1.1 |
| raw_alfonsino | 3 / 0.4 | cooked_alfonsino | 7 / 0.9 |
| raw_blue_ling | 3 / 0.4 | cooked_blue_ling | 8 / 1.0 |
| raw_deepwater_redfish | 3 / 0.4 | cooked_deepwater_redfish | 7 / 0.9 |

constraints: 効果なし。既存種の ID・モデル・ドロップは変えない。全種 harmless (role は prey 系)。生成物は手書きしない。
- 隠し満腹度は saturationModifier (バニラ焼き鱈 0.6 と同じ単位)。ModItems.food() は絶対値を取るので `2 × 満腹度 × 隠し満腹度` を渡す (例 cooked_orange_roughy 7/0.9 → food(..., 7, 12.6f))。
- ドロップ: INFO["loot"] = 生肉 1〜2 (小型 alfonsino / deepwater_redfish は 1)、looting +1。
- アイコン 16 枚 (生/焼き) は ChatGPT 画像 → texture_locks (main が取り込む)。取込前はプレースホルダ可。
accept: 8種がスポーンし (`/abyssia fauna` で確認)、モデルで見分けられる、倒すと専用生肉、焼くと焼き魚 (かまど/燻製器/焚き火)、食料値が表どおり、既存種に回帰なし、gradle build 通過。

## Claude 補足
- ChatGPT 初案は lanternfish / barreleye / fangtooth / chimaera / oarfish / grenadier が既存種と重複していたので差し替えを依頼した (改訂版)。deepwater_redfish の深度・群れ・学名は ChatGPT の表が途中で切れたため Claude が補った。
- アイテム id は ChatGPT 案 `<id>_raw / <id>_cooked` を、既存・バニラの命名 (cooked_cod, cooked_shark_flesh) に合わせて `raw_<id> / cooked_<id>` にした。
- 種の定義: `tools/fauna/<id>.py` の INFO (例 tools/fauna/abyssal_grenadier.py)、INFO["java"] = dict(kind="swimmer", size_m, health, attack, traits, render) → fauna_java.py が GeneratedFauna.java を出す。食べ物は ModItems.java:120-138 の food(name, nutrition, saturation, rarity, ...)、調理レシピは gen_deep_assets.py:1174 のループ。
- モデル: `tools/bbmodel-generator/definitions/<id>.json` → `node tools/bbmodel-generator/cli.js all` → `python tools/gen_fauna.py`。
