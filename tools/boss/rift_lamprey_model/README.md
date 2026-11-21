# 裂溝ヤツメ（GNT01 の 6 番） の GeckoLib モデル生成

大きな直方体だけで組むバニラ風のモデル。**出力は手で直さない**（`build.py` を直して流し直す）。
`cube_kit.py` はこのフォルダ用の小さな道具（箱 + 面ごとの UV + 粗いドット絵 + 図のための姿勢付け）。共通の `tools/boss/*.py` は変えていない。

```bash
python tools/boss/rift_lamprey_model/build.py            # geo / animation / texture(+_glow) / contract.json
python tools/boss/check_animations.py rift_lamprey
python tools/boss/rift_lamprey_model/preview.py          # out/ に六面図・斜め・頭の拡大・デザイン画との比較
```

- 骨とレンダラーの約束は `contract.json`（head と seg_NN は root 直下、箱は子の body_NN）
- animation: idle / attack / latch / latch_grind
- テクスチャは 1 テクセル 0.25 ブロック、平らな 3 段。発光は `_geo_glow.png`
