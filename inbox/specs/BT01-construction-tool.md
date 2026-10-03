# BT01 建設用ツールの拡張 (親仕様)
依頼: inbox/requests/20261003-184729.json。ChatGPT 原本: `BT01-chatgpt-raw.md` (子仕様 BT01a〜h の本文はそちら)。デザイン: `inbox/designs/BT01.png` (海流発電機・地熱発電機・バイオ燃料発電機・水槽・充電ステーション・梯子・捕獲容器・扉のない出入口)。
Decision (2026-10-03): MODIFY, risk high。以下の修正を原本より優先する。

## 原本からの修正 (Decision + 現コード確認)
- **登録の継ぎ目 (BT01a)**: `habitat/build/` に BuildEntry (id, category, cost, cells(origin,rot), check, start, dismantle) / BuildCategory / BuildRegistry (文字列 id = 旧 HabitatMode id で既存の装置 NBT を読める) / ModuleEntry (HabitatMode を包む) / BuiltUnits (SavedData: unitId, entryId, origin, rot, box, 支払った素材, moduleId) / BuildContent.register(bus) と client/build/ClientBuildContent。b〜g は自分の行を 1 行足すだけ。並び順の確定は h。
- 配置: ホイール = 距離 (3..12、初期 6、装置 NBT に保存)、R = 回転 (Shift+R で逆回転)。BUILD パケットに entryId+rot+distance、サーバーで距離をクランプして再計画。ハッチ吸着 2 ブロックは維持。ホログラム: 赤=不可/黄=素材不足/シアン=可、解体プレビューは橙。
- 室内設備 (梯子・ロッカー・作業台・充電ステーション・水槽) の設置判定 = 「登録済みモジュールの箱の中の空気」。
- **解体 (BT01b)**: HabitatBases の Module に removed フラグ + Edges を保存。旧セーブは隣接箱から辺を推定、モードは箱寸法 (+プール判定)、返却は現コスト x0.8。撤去で union-find 再構築、FE を生き残りの成分へ体積比で分配 (余りは最大成分)。撤去した単位のブロックは水に戻し、隣のモジュールが開けていた共有面は shellStates で殻に戻す (浸水させない)。設備の残るモジュールは解体不可 (先に設備を解体)。返却 floor(paid x0.8)、溢れはドロップ、中断は返却なし。
- **梯子 (BT01c)**: 室内は 3 段 (床 y0・天井 y4) なので梯子は 3 ブロック (縦ハッチ越しは 5)。上下積み: 登録済み ROOM の屋根を狙うと origin+5 の同じ回転に ROOM を吸着。縦ハッチ (カスタマイズ) は上下ぴったりの ROOM 同士のみ、下の天井中央と上の床中央を開口にして梯子列を付け、HabitatBases.union + 辺。MOON_POOL には不可。梯子は Forge isLadder=true、細い当たり判定、BlockItem 無し。
- **発電機 (BT01d)**: 本体 BE は ENERGY capability を持たない (二重計上回避)。毎 tick HabitatPower.externalReceiver(隣の殻, 面) に押し込むので、拠点の殻に接していなければ 0 FE。海流 = 120 x NaturalCurrents.getCurrentAt(タービン中心)、0.1 未満 0、上限 240。地熱 = 80 x VentHeat.multiplier(直下の噴出孔の活動度) = 0/40/80/120/160 (活動度は 5 段階、VentHeat だけ使う)。バイオ = bio_oil 1 個 100,000 FE (refined_oil 200,000)、100 FE/t、1 スタックのスロット + 右クリック投入、GUI なし。部品ブロックはドロップ/アイテムなし、タービンは BER で回転。マルチブロック立体モデルはユーザー依頼なので BT01 の発電機だけ例外 (I01 の機械は立方体のまま)。
- **カスタマイズ (BT01e)**: ガラス壁 = 13 幅の面の内側開口 11x3 = 33 マス、普通の壁セルだけ (ハッチ・扉・開口済みは除く)、ガラス 1 個/マス (最大 33)、戻すのは無料・返却なし。空気膜 = LiquidBlockContainer (canPlaceLiquid=false, placeLiquid=false)、canBeReplaced(state, Fluid)=false、当たり判定なし、半透明、流体なし、ドロップ/アイテムなし。開放型出入口 = ENTRANCE の外扉 (FAR) を 3x3 の膜パネルに置き換えたもの。充電ステーション: 「建設装置の充電」は削除 (装置は FE を持たない)。箱の中で受電のみ、20,000 FE バッファ、3 ブロック以内のプレイヤーへ合計 200 FE/t、対象は ChargeSlotHandler の判定、GUI の対象選択は削除。
- **レーダー (BT01f)**: tier standard に上げる。RADIUS/HALF_HEIGHT は定数で ScanData・ScanConsoleBlockEntity・ScanMapRenderer が使う → 段階をコンソール BE の NBT に持ち、グリッドと描画を半径で拡縮。Lv3 は約 17 倍のブロック数なので 1 tick の予算は据え置き、チャンクは読み込まない。
- **水槽 (BT01g)**: 成長状態は水槽 BE のデータだけ {種, 生まれた gameTime, 成体}。gameTime 差で遅延計算。捕獲した野生 = 成体、繁殖 = 幼体 (0.5 倍表示)。BER がクライアント専用のエンティティインスタンスを既存レンダラーで簡単な遊泳経路に描く (実エンティティなし)。水は BER で描き流体ブロックは使わない。捕獲可能 = entity tag `abyssia:aquarium_capturable`。解体で中身は捕獲済み容器としてドロップ。
- **統合 (BT01h)**: BuildContent/ClientBuildContent の並び順。ロッカー/作業台の建設コスト = 現レシピの材料。large_locker.json / wall_workbench.json のレシピは生成元 (furniture_assets.py) から外して削除、アイテム登録は残す。補助/熱水発電機のレシピはそのまま。.source の lang ヒントを更新。lang は tools/lang_parts/bt01*.json を lang 生成にマージ。捕獲容器は通常レシピ (建設メニューには入れない)。

## ファイル担当 (並列で重ならないこと)
- a: habitat/HabitatMode, HabitatBuilder, HabitatConstructorItem, HabitatControlPacket, client/HabitatClient, client/HabitatMenuScreen, client/HabitatHologram, HabitatPlan (距離のみ、その後 c へ), 新規 habitat/build/**, habitat/client/build/ClientBuildContent, Abyssia.java (1 行)
- b: habitat/power/HabitatBases, HabitatPower (登録/削除のみ), 新規 habitat/dismantle/**
- c: habitat/HabitatPlan (縦吸着), 新規 habitat/ladder/**, tools/bt01/ladder_assets.py
- d: 新規 habitat/generator/**, tools/bt01/generator_assets.py
- e: 新規 habitat/custom/**, habitat/charging/**, tools/bt01/custom_assets.py
- f: habitat/scan/ScanData, ScanConsoleBlockEntity, client/ScanMapRenderer, client/ScanConsoleScreen, 新規 ScanUpgradeEntry
- g: 新規 habitat/aquarium/**, tags/entity_types/aquarium_capturable.json, 捕獲容器レシピ, tools/bt01/aquarium_assets.py
- h: BuildContent/ClientBuildContent の並び, tools/furniture_assets.py, レシピ削除, lang 生成 + tools/lang_parts, 新規 habitat/build/furniture/{LockerEntry,WorkbenchEntry}
- b〜g の BuildContent / ClientBuildContent への追記は各自の 1 行だけ (アンカー行)。

## 順序
a → (b ∥ c ∥ d ∥ e ∥ f ∥ g) → h → NeoForge 移植。テクスチャ/モデルはデザイン画 BT01.png から ChatGPT に描かせる (各子仕様の実装後にまとめて依頼)。

## 検証 (Decision の verify)
旧ワールドで装置 NBT と HabitatBases が読める・旧ルームを解体して返却 / 3 連ルームの中央を解体して隣が乾いたまま・FE 分配・再起動後も正しい / 膜: バケツ・流水・ランダム tick でも水が入らずプレイヤーは通れる / 上下積み + 縦ハッチで 1 拠点・梯子を登れる / 殻に接しない発電機は 0 FE、値は仕様どおり / スキャン Lv3 で TPS 安定 / 水槽は成体 2 匹で 20 分後に繁殖、再起動後も保持。
