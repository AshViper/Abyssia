# PL02 プランターの作り直し
依頼: inbox/requests/20261003-211055.json。ChatGPT 原本: `PL02-OL02-chatgpt-raw.md` (PL02 節)。テクスチャ: `inbox/prompts/PL02-textures.md` (シート PLT1 本体 / PLT2 作物 3 段階)。
tier: standard
files: furniture/HydroPlanter*.java (Block/BlockEntity、Menu は削除)、furniture/PlanterCrop.java、client 側の planter screen/renderer、registry/ModFurniture (menu 登録の削除)、tools/planter_assets.py、tools/planter_gui.py (不要化)、lang
goal: hydro_planter を高さ 8px の下側ハーフブロックにし、2x2 の 4 区画に別々の作物を植えられるようにする。専用 GUI を廃止して右クリックで操作。枠の連結 (multipart) は廃止。
## 確定 (ChatGPT 原本 + Claude の調整)
- 形: 下側ハーフブロック (0..8px)、当たり判定も同じ。隣と連結しない単体。登録名 hydro_planter は維持、レシピも維持。
- 区画: NW/NE/SW/SE。右クリックの BlockHitResult のローカル X/Z (0.5 境界) で決める。上面以外の面を叩いたときも水平位置で判定。
- 植える: 空き区画を対応する苗 (abyssal_mushroom / pressure_gourd / deep_kelp) を持って右クリック → 1 個消費して植える。
- 収穫 (Claude 調整): 熟した区画を素手 (または苗以外) で右クリック → 収穫物 (min..max) をプレイヤーへ (満杯なら足元にドロップ)、その区画は成長 0 からやり直し (苗は残る = 現行 PL01 の挙動)。原本の「搬出インベントリに入れる」は GUI 廃止後に手で取り出せないため採らない。
- 苗を外す: スニーク + 素手右クリック → その区画の苗を返却して空に。
- ホッパー (維持): 下/横からの IItemHandler 抽出で、熟した区画の収穫物を直接取り出せる (取り出した区画は成長 0 から)。挿入は無し。
- 成長: 作物ごとの時間 (キノコ 8 分 / 瓢箪 12 分 / 昆布 6 分) を区画ごとに gameTime ベースで管理 (遅延計算、アンロード中も進む)、3 段階 (0 / 1 / 2=成熟) を表示。
- 表示: 4 区画それぞれに小さな作物モデル (PLT2 の planter_<crop>_<stage> を十字モデルで 1/4 サイズ)。区画からはみ出さない。BER か blockstate 不要の BakedModel、軽い方で。
- 移行: 旧 BE (Seed スロット + 成長値) を読んだら NW 区画へ移す。読めないデータでも破壊時に中身を落とす (消失させない)。
- GUI/Menu/Screen/planter_gui.py は削除。
## accept
ハーフブロック表示、4 区画に別作物、右クリックした区画だけ操作、GUI が開かない、成長時間どおり、3 段階が見える、収穫で収穫物と苗の再成長、スニークで苗回収、ホッパーで熟した収穫物を搬出、再起動後も維持、旧データ移行、両ローダー。
