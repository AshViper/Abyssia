# Texture Forge

Minecraft風 Pixel Art の「文法」を使って、**完全に新規のオリジナルテクスチャ**を大量生成するローカル GUI ツール。
深海 Mod（Abyssia）向けの岩石・海底地形・鉱石・鉱物・植物・海藻・結晶・熱水噴出孔・装飾を想定しています。

```
Vanilla Texture ─► Texture Analysis ─► Material / Palette Analysis ─► Generation Parameters
      ─► Pixel Art Generator ─► Variation Generation ─► Human Selection ─► Resource Pack Export
```

## 起動

```bash
pip install -r requirements.txt     # PySide6 / numpy / Pillow
python main.py                      # または run.bat（Windows）
python main.py path/to/stone.png    # 参考テクスチャを開いた状態で起動
```

Python 3.11 以上。外部 API・ネットワークは不要です（ローカル LLM 連携は任意）。

## 参考画像の扱い（著作権への配慮）

参考テクスチャ（Vanilla の stone.png など）は、既定では **統計だけ** が使われます。
（例外は明示的にオンにする **Recolour the reference**（`recolor_source`）だけで、その場合の出力は参考画像の派生物になります。下記参照。）

| 解析するもの | 生成での使い道 |
|---|---|
| 明度レベル数・間隔・分布 | 新しいランプの階調構造と、ノイズ量子化の閾値 |
| Pixel Cluster の大きさ | ノイズ周波数・クリーンアップの最小塊サイズ |
| Edge / Noise density | 細部の量 |
| Texture Direction（構造テンソル） | ノイズの伸長方向・亀裂の向き |
| アクセント色の割合と塊の大きさ | 鉱石の量と粒の大きさ |
| シルエット（透明画像） | 植物の高さ・茎の数・葉の多さ |

- ピクセルのコピー、色相変更、反転、単純なフィルターは一切行いません。
- 生成パレットは参考パレットを使わず、テーマの色から毎回新しく作ります（参考画像の「明度の並び方」だけを借ります）。
- Compare タブの **Difference** 表示と「identical pixels」値で、元画像と一致するピクセルがほぼ 0% であることを確認できます。

## 画面構成

| エリア | 内容 |
|---|---|
| **Source** | 参考画像（PNG/JPG/WEBP、ドラッグ＆ドロップ可）、Nearest Neighbor 拡大、解析結果 |
| **Preview** | Generated / Compare（左右比較・Difference・Palette・Brightness）/ Variations / Layers |
| **Generation** | Style・Category・Variant・Material・Size・Seed、パレット、各スライダー、Layer ON/OFF、Style Filter |
| **Palette** | 生成パレット（ランプ別）と、参考画像から抽出した主要色（参照用） |
| **Output** | Texture Name・Mod ID・Export PNG・Export Resource Pack |
| **History** | 生成履歴（クリックで復元。`output/history/` に PNG + 設定 JSON を保存） |

プレビューは Grid（Pixel Grid）、Tile 3×3（シームレス確認）、背景（Checkerboard / Dark / Light）を切り替えられます。
ホイールでズーム、ドラッグでパン、ダブルクリックで Fit。

### ショートカット

| キー | 動作 |
|---|---|
| F5 | Generate |
| Ctrl+R | Randomize（Seed 変更 + 生成） |
| Ctrl+Shift+G | Variation 生成 |
| Ctrl+Z / Ctrl+Y | Undo / Redo |
| Ctrl+S / Ctrl+Shift+S | Export PNG / 名前を付けて保存 |
| Ctrl+E | Export Resource Pack |
| Ctrl+I | AI Prompt |
| Ctrl+B | Batch Generation |
| G | Pixel Grid 切替 |

## カテゴリ

| Category | Variant | 主なスライダー |
|---|---|---|
| Terrain | auto / rough / layered / cobbled / smooth / strata | Roughness, Layering, Cracks, Noise, Contrast, Moisture, Mineral, Alteration |
| Ore | cluster / vein / scattered | Density, Cluster Size, Mineral, Glow |
| Mineral | crystalline / banded / granular / crust | Density, Cluster Size, Mineral, Facet, Glow |
| Plant | fern / grass / bush / vine / bulb | Density, Stem, Leaf, Branch, Height, Glow, Moisture |
| Kelp | deep / giant / abyssal / thermal / seagrass（Part: stalk / top） | Density, Stem, Leaf, Height, Glow |
| Crystal | cluster / single / shard / bud / block | Density, Height, Facet, Transparency, Glow |
| Thermal | thermal_rock / black_smoker / white_smoker / sulfur_rock / mineral_crust / thermal_crystal | Density, Mineral, Cluster Size, Glow |
| Decoration | bricks / tiles / polished / chiseled / pillar / lamp | Density, Mineral, Glow |
| Organic | coral / sponge / vein / moss | Density, Cluster Size, Glow |
| Metal | plate / block / grate / raw | Density, Mineral, Glow |
| Wood | planks / log / log_top / stripped_log / stripped_log_top / door_top / door_bottom / trapdoor / door_item | Density, Mineral, Glow |
| Item | ingot / raw / dust / nugget | Density, Mineral, Height |
| Dripstone | tip / tip_merge / frustum / middle / base（Part: down / up） | Density, Mineral, Glow |

ブロック系は常にシームレスにタイルします。Plant / Kelp / Item / Dripstone / Crystal（block 以外）/ Wood のドア・トラップドアは透過スプライトです。

- **Terrain strata**（Material = Slate で auto 選択）：深層岩のような岩。層に沿って横長の階調クラスタ、切れ切れの層理線、横向きの細かい粒、低角度の亀裂。ハイライトはランプ最上段を使わず控えめ。
- **Recolour the reference**（`recolor_source`、Terrain / Ore、既定 OFF）：参考画像のピクセル配置をそのまま下地にし、階調の明暗順にパレットへ置き換えて再着色、その上に装飾レイヤー（湿った染み・追加の層理・Alteration・亀裂・鉱物・濡れた光沢・発光）だけを重ねます。出力は参考画像の派生物です。Ore では `source_host`（母岩の色のカンマ区切り）に含まれる色を base ランプ、それ以外を鉱石として accent ランプへ置き換えます（未指定なら彩度で分離）。
- **Crack color**（`crack_color`、Terrain）：亀裂を「泣く黒曜石」のような色付きの筋（暗く鮮やかな縁＋明るい芯、短く枝分かれ）で描きます。Glow を上げると芯が発光します。未指定なら従来の暗い亀裂。
- **Alteration**（Terrain / Ore）：岩の陰影を保ったまま、一部を secondary ランプに置き換える「変質」パッチ（熱による赤褐色化、火山ガラス、鉱物膜、生物膜など）。色は Secondary color で指定。
- **Kelp seagrass**：中心の茎を持たない細い葉の束。stalk は縦にタイルし、top は同じ列から伸びて先端で終わるので、積み重なる草や（上下反転して）垂れ下がる蔓・根にも使えます。
- **Wood**：base = 木材色、secondary = 樹皮（Secondary color 未指定なら木材色から自動生成）、accent = 金具。door_top / door_bottom は Seed から同じ設計図を作るので、上下が必ず噛み合います。
- **Item**：インベントリ用スプライト。ingot は小さな 3D 台形柱を投影して描き、raw / nugget は丸いこぶの集まり、dust は崩れた粉の山。暗い輪郭・左上からの光は Minecraft のアイテム表現に合わせています。
- **Dripstone**：鍾乳石 5 段階（pointed dripstone と同じ太さの並び）。up は down の上下反転なので、鍾乳石と石筍が揃います。
Kelp の `stalk` は縦方向にシームレスに繋がります（`kelp_plant` 相当）。

## 生成の仕組み

生成は **Layer 方式**で、各 Layer を ON/OFF できます（Layers タブで各段階の結果を確認可能）。

```
Base → Material → Large Detail → Medium Detail → Small Detail → Cracks → Highlights → Shadows → Accent
```

- キャンバスは RGB ではなく **(ランプ, 階調)** のインデックス画像。色は最後にパレットから解決するので、色数が増えず、陰影は必ずパレット上の一段上／一段下に乗ります。
- ノイズは低解像度・タイル可能な Value / Perlin / Cellular（Worley）を numpy だけで実装し、階調へ量子化して Pixel Cluster を作ります。
- Pixel Cluster：鉱石・粒は Eden 成長で「塊」として生成し、最終段で孤立ピクセルを除去します。
- 亀裂：ランダムウォーク＋分岐、幅をランダムに変え、下側に光の当たる縁を付けます。
- 光源は常に左上。パレットはシャドウを青へ、ハイライトを黄へ寄せる Hue Shift 付き。
- **Minecraft Style Filter**：Pixel Cluster Cleanup / Noise Reduction / Edge Cleanup / Palette Limiting / Color Quantization / Contrast Normalization（各 ON/OFF 可）。
- **Pixel Perfect**：アンチエイリアス・ぼかし・スムージングなし。アルファは 0 / 255（Transparency 使用時のみ半透明 1 段）に量子化（Alpha Threshold 設定可）。
- Seed はレイヤーごとに独立したストリームへ分岐するため、同じ Seed + 同じ設定なら常に同じ結果になり、ある Layer を切っても他の Layer は変化しません。
- 発光ピクセルがある場合は、Resource Pack 出力時に `<name>_e.png`（発光マスク）も書き出せます。

## パレット

テーマ：Deep Ocean / Abyss / Trench / Thermal / Volcanic / Crystal / Bioluminescent / Ancient / Cold / Organic
アクセント：sulfur, sulfur_cyan, cyan_mineral, manganese, copper, pyrite, heat, amethyst, emerald … など。
Base color で任意の色からランプを作ることもできます（Secondary color で樹皮・茎などの secondary ランプも指定可能）。
どちらも `#26282E,#33363D,#40444B,...` のように暗→明のカンマ区切りで複数色を渡すと、その色列がそのままランプの骨格になります（岩石ファミリーのように共通パレットを厳密に揃えたいとき用）。色調整（Hue / Saturation / Brightness / Contrast / Temperature / Tint）はパレットに適用されるため、色数は変わりません。
Color limit は 8 / 12 / 16 / 24 / 32 / Unlimited（既定 16）。

## プリセット

`presets/*.json`（Presets メニューから適用、「Save Current as Preset…」で保存）。最初から次を同梱：

Abyssal Rock, Trench Rock, Thermal Rock, Mineral Sediment, Deep Kelp, Abyssal Grass, Thermal Crystal, Abyssal Crystal
（ほか Deep Sediment, Crystal Rock, Manganese Ore, Copper Sulfide Vein, Black/White Smoker Rock, Sulfur Rock, Giant Kelp, Abyssal Kelp, Abyssal Bricks, Glow Coral Block など）

```json
{
  "category": "terrain",
  "size": 16,
  "palette": "abyss",
  "roughness": 0.82,
  "contrast": 0.65,
  "noise": 0.35,
  "cracks": 0.42,
  "crystal": 0.08,
  "organic": 0.02,
  "seed": 123456
}
```

キーは `core/settings.py` の `TextureSettings` と同じ名前で、書いていない項目は既定値になります。

## Batch Generation（Ctrl+B）

- **Input Folder**：フォルダ内の参考画像を 1 枚ずつ解析 → 生成 → 出力。ファイル名からカテゴリを推定（`*_ore` → Ore、`kelp` → Kelp …）。
- **Batch Preset**：`presets/batch/*.json`。例：*Deep Sea Terrain Pack* → abyssal_rock, deep_sediment, trench_rock, thermal_rock, mineral_sediment, crystal_rock。
- 1 テクスチャあたりの Variation 数を指定すると `name.png, name_01.png, name_02.png …` で保存。Resource Pack 同時出力も可能。

## AI 連携（任意・ローカル LLM）

LLM は画像を作りません。**自然言語 → 生成パラメータ（JSON）** の変換だけを担当し、画像は常に Generator が作ります。

1. Settings → Preferences → *AI (Local LLM)*
2. Provider：`Ollama`（既定 `http://127.0.0.1:11434`）/ `llama.cpp`（`llama-server`, `http://127.0.0.1:8080/v1`）/ OpenAI 互換ローカル API
3. Model を入力（`qwen3.5:4b` など）→ **Connect** でモデル一覧取得、**Test** で疎通確認
4. Generate → **AI Prompt…**（Ctrl+I）に文章を入力 → Interpret → Apply & Generate

例：「深海の熱水噴出孔周辺にある黒い岩。硫黄が少し付着していて、ところどころ青緑色の鉱物が露出している。Minecraft風16×16。」
→ `{"category": "thermal", "variant": "sulfur_rock", "palette": "thermal", "accent": "sulfur_cyan", "mineral": 0.3, "brightness": 0.25, "size": 16, …}`

LLM が未設定・未起動・不正な出力を返した場合は、内蔵のキーワードパーサ（日本語／英語）に自動で切り替わるので、オフラインでも使えます。
パラメータは `ai/schema.py` で検証・範囲制限されるため、モデルの出力が壊れていても生成は失敗しません。

## Resource Pack Export

```
<pack_name>/
├── pack.mcmeta          (pack_format は Minecraft バージョンから自動: 1.20.1 → 15)
├── pack.png
└── assets/<modid>/textures/<block|item|particle>/<texture_name>.png
```

Mod ID・既定テクスチャ名・出力フォルダ・Minecraft バージョンは Preferences で設定します。
出力 PNG には生成設定が埋め込まれ、*File → Load Settings from PNG…* で再現できます。

## プロジェクト構成

```
texture_forge/
├── main.py
├── app/            GUI（PySide6）: window, theme, worker（非同期実行）, widgets/, dialogs/
├── core/           analyzer, palette, generator, layers, pixel_art, noise, seed,
│                   settings, history, presets, batch, compare, config
├── generators/     terrain, ore, mineral, plant, kelp, crystal, thermal, decoration, organic, metal,
│                   wood, item, dripstone
├── ai/             ollama（Ollama / OpenAI互換クライアント）, prompt_parser, schema
├── export/         png, resource_pack
├── presets/        *.json と batch/*.json
├── assets/         UI アイコン
├── output/         書き出し先・履歴（git 管理外）
└── tests/
```

GUI と生成処理は完全に分離しています。GUI を使わずに直接呼び出すこともできます：

```python
from PIL import Image
from core.generator import TextureGenerator
from core.settings import TextureSettings

gen = TextureGenerator()
settings = TextureSettings(category="terrain", palette="abyss", roughness=0.85, cracks=0.4, seed=123456)
result = gen.generate(Image.open("stone.png"), settings)
result.image.save("abyssal_rock.png")

variants = gen.generate_variations(None, settings, 8)   # Seed から派生した 8 候補
```

### 新しいカテゴリの追加

`generators/base.py` の `BaseGenerator` を継承し、`layer_base` … `layer_accent` を実装して
`generators/__init__.py` の `_REGISTRY`（または `register()`）に登録し、`core/settings.py` の `CATEGORIES` / `VARIANTS` に追加します。
将来の Animation / Connected Texture / Normal・Height・Emission Map なども、同じキャンバス（高さマップ・発光マスクを保持）から派生させる想定です。

## Tips

- **ライブプレビュー**：16×16 は 1 枚 40ms 以下なので、スライダーを動かすとすぐ反映されます（Settings → Live Preview で切替）。256×256 は 1〜2.5 秒でバックグラウンド生成されます。
- **Variation → 選別**：Ctrl+Shift+G で候補を並べ、クリックで確認、ダブルクリックで採用（History に追加）。
- **Kelp**：`stalk` と `top` は同じ Seed・同じ Stem 値で作ると継ぎ目が一致します。Batch Preset「Deep Sea Flora Pack」参照。
- **Crystal**：足元の岩は Material の *Rock* スライダーで調整（0.25 以下で岩なし）。結晶の色は Accent、2 色目は Accent の組（例 `cyan_amethyst`）で決まります。
- **暗すぎる／明るすぎる**：Theme を変えるより Brightness / Contrast を使うと色数を保ったまま調整できます。Abyss・Black Smoker は意図的に暗いテーマです。
- **発光**：Glow を上げると発光ピクセルが増え、Resource Pack 出力時に `<name>_e.png`（発光マスク）を書き出せます。
- **再現**：Export した PNG には設定が埋め込まれているので、File → Load Settings from PNG… で同じテクスチャを再生成・微調整できます。

## テスト

```bash
python -m unittest discover -s tests -v
```

16×16 / 32×32 生成、透過テクスチャ、Ore / Plant / Crystal / Thermal 生成、Seed 再現性、PNG Export、Resource Pack Export、
Batch Generation に加え、全カテゴリ×全 Variant、Layer 分離、色数制限、アルファの量子化、タイル継ぎ目、コピーでないこと、AI パラメータ変換を検証します。
テストは合成画像だけを使い、Mojang のアセットには依存しません。

## Abyssia での利用

`tools/forge_textures.py` が Abyssia の全ブロック・アイテムテクスチャ（建材を含む）の設定表です。
岩石はバニラ深層岩、堆積物はバニラ砂／粘土、鉱石はバニラ深層岩鉱石、石の建材は深層岩の建材を `recolor_source` で再着色し、
岩ごとの違いは色味と装飾（泣く黒曜石風の亀裂・湿り気・層理・変質パッチ・鉱物沈殿・発光）で表現しています（`ROCKS` 表）。
これらは Mojang のテクスチャの派生物です（一覧は `forge_textures.RECOLOURED`）。Mod を配布する前に扱いを確認してください。
各テクスチャに「バニラの参考テクスチャ（Gradle キャッシュの client jar から直接読み込み）」と Texture Forge の設定を割り当て、
`src/main/resources/assets/abyssia/textures/` へ書き出します。`tools/gen_deep_assets.py` の最後に自動で実行されます。

```bash
python tools/forge_textures.py --only deep_sea_rock,ancient_planks --preview sheet.png
```
