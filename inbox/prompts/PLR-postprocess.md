# PLR 取込後の手順

シート PLR1..PLR12 (PLR-textures.md、プリセットは sheets.json)。取込は lock=true なのでロック済みの旧ファイルも
バックアップ (`inbox/backup/gui-import-<時刻>/`) の上で上書きされ、ロックも更新される (フラグ不要)。skip なし。

## 1. 取込 (各シート)

```
python tools/agentflow/sheets.py detect auto --preset PLR1      # 検出数 = ids 数を確認 (merge 調整)
python tools/agentflow/sheets.py import auto --preset PLR1 --dry-run
python tools/agentflow/sheets.py import auto --preset PLR1
```
失敗時は `python tools/agentflow/sheets.py undo`。

## 2. glow オーバーレイ

取込が `derive: true` で自動再導出し、ロック済み glow も再ロックする (sheets.py import の `derived.written` を確認)。
対象 (65 パーセンタイル輝度カット): abyssal_bloom, abyssal_mushroom, cave_bloom, floating_bloom, glow_anemone, hadal_bloom,
glowtip_grass, glow_coral, soul_coral, luminous_moss, crystal_kelp_top, crystal_plant, cave_crystal_plant, glasslace_ripe,
lumen_quill_ripe, abyssal_vine_tip, cave_vine_tip, thermal_plant, thermal_tube_top。
`python tools/derive_textures.py` 単体はロック済みを書かない (`--only` でも不可) ので、取り込み後の再導出は必ず import 経由。
漏れの確認: `python tools/derive_textures.py --dry-run` の `locked` に残りがあっても、sheets.py 側で再ロック済みなら差分なし。
光らせたい部分は明るい色 (シアン/白) を使っていること。

## 3. アニメ植物 (PLR10 の 8 枚)

旧テクスチャは 16x64 (4 フレーム、`.png.mcmeta` frametime 12 / interpolate)。ChatGPT は 1 枚だけ描くので取込後は 16x16 になる
(mcmeta 付き 16x16 は 1 フレーム扱いで壊れない)。フレームを生成して戻す:
```
python tools/plant_anim.py --dry-run
python tools/plant_anim.py            # 16x16 -> 16x64 (上下端は揺れ 0 で縦の継ぎ目は一致)、アセットと texture_locks の両方、mcmeta は維持
```
アニメを諦める場合は `.png.mcmeta` 8 個を削除して 16x16 のままにする (報告すること)。

## 4. 検査

```
python tools/check_textures.py          # missing / invalid_json が空であること
git status --short src/main/resources tools/texture_locks   # 差分は PLR ids + glow のみ。planter_* / oil_kelp_* に変化がないこと
```
ペアの継ぎ目 (本体と _top/_tip、通常と _ripe) は 4 倍拡大で縦に並べて目視 (取込が bbox 中央寄せなので x 位置ずれに注意。ずれたら
その1枚だけ再生成)。gen_deep_assets は不要 (テクスチャのみ)。
