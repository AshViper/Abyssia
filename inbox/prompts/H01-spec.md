あなたは Minecraft Forge 1.20.1 の深海Mod「Abyssia」の設計担当です。次の依頼の仕様書を作ってください。実装は別の担当(Claude)が行います。

## 依頼 (ユーザー原文)
水中に拠点用の構造物を生成できるアイテムを作成。土台 / 多目的ルーム / 廊下 / 出入り口 / ムーンプール の感じで。構造物の中身は空気になるように。shift+右クリックで切り替えできるように。コストは鉱石関連を使用する。生成するブロックは破壊しても回収できないようにしてほしい。

## 既存の前提
- 深海は overworld の Y -368..-64 (深海層)。海は水源ブロック。
- 既存インゴット: iron(バニラ), copper(バニラ), manganese_ingot, cobalt_ingot, nickel_ingot, vanadium_ingot, molybdenum_ingot, tungsten_ingot, platinum_ingot, tellurium_ingot, yttrium_ingot, 合金: high_strength_alloy_ingot, corrosion_alloy_ingot, heat_resistant_alloy_ingot, conductive_alloy_ingot, thermal_alloy_ingot, abyssal_alloy_ingot, tungsten_alloy_ingot。iron_plate あり。原石 raw_* あり。
- 既存建材: industrial_panel(金属パネル), metal_grating, industrial_beam, work_light。これらは通常ドロップする。
- 生成物はドロップなしにするため、専用ブロック(見た目は工業パネル系、ドロップなし)を新設してよい。

## 出してほしいもの (Markdown、短く)
1. 仕様書テンプレ: `# H01 <title>` / tier / files / goal / constraints / accept
2. アイテム: ID・日本語名・英語名、操作 (右クリックで設置、shift+右クリックでモード切替、プレビュー表示の有無)、コストの払い方 (インベントリから消費か、アイテムに充填か)
3. 5モジュールそれぞれ: 寸法 (幅x奥行x高さ、外寸)、設置基準 (プレイヤーの向き・クリック面)、ブロック構成 (床/壁/天井/窓/照明/開口部)、内部は空気、コスト (鉱石関連の具体的な個数)
   - 出入り口: 外の水と中の空気の境界をどうするか (エアロック等)
   - ムーンプール: 床に水面の穴を開ける。水が室内に溢れない形にする
4. 新規ブロック一覧 (ID・名前・ドロップなし・硬さ・光源)
5. 設置ルール: 置けない条件 (既存ブロックとの重なり、空中/陸上でも可か、保護)、失敗時にコストを消費しない
6. 配色・見た目の方針 (既存工業パネルとの関係)
7. レシピ (作業台、鉱石系素材)
