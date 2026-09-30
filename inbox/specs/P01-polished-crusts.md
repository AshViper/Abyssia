# P01 研磨クラスト建材
tier: standard
files: tools/gen_deep_assets.py, src/main/resources/assets/abyssia/**(生成物), src/main/resources/data/abyssia/**(生成物)
goal: manganese/cobalt/nickel/iron/copper_crust の研磨版、階段、ハーフブロックを生成し、stonecutter とクラフトのレシピを追加する。
constraints: crust の元テクスチャ形状は維持する。生成物は手編集せず generator 経由で作る。既存の rock テクスチャを変更しない。
accept: 5種の polished_*_crust、stairs、slab とモデル・lang・loot・recipe が生成され、生成スクリプトを再実行できる。
