# M02 クラストの使い道
tier: standard
files: tools/gen_deep_assets.py, tools/mineral_textures.py, tools/plant_defs.py(レシピ参照のみ), 生成物   ※M01 完了後に着手
goal: 6種の *_crust(manganese/cobalt/nickel/iron/copper/cave_mineral)に使い道を付ける。
1) 精錬: crust → ingot を smelting/blasting(1→2、cave_mineral_crust→硫黄+鉄粒など既存素材)。iron_crust=鉄インゴット2、copper_crust=銅2。
2) 建材: <mineral>_crust から polished_<mineral>_crust(研磨)+ stairs/slab を stonecutter+クラフトで。テクスチャはクラストの色替え(明度を少し上げ枠線を入れる程度、新規デザイン不要)。cave_mineral_crust は対象外でよい。
3) 掘削ドロップは M01 の追加レアメタル確率を維持しつつ、シルクタッチ以外でも crust ブロック自体がドロップするか確認し、無ければ「掘ると crust そのまま」を維持(壊れて消えない)。
constraints: 新規機械/GUIは作らない。既存レシピ/バニラを上書きしない(ID は <result>_from_<crust>)。crust のテクスチャ形状は変更しない(mineral-texture-categories)。plant_defs.check() を壊さない。
accept: レシピ/ロード/lang 生成、ツール再実行成功、追加ブロックID列挙。
