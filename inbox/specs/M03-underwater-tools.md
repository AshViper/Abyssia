# M03 水中活動向けツール・装備
tier: standard
files: src/main/java/com/abyssia/item/**, src/main/java/com/abyssia/registry/ModItems.java(追加のみ)/新規 ModTools.java, src/main/java/com/abyssia/Abyssia.java(イベント登録のみ), tools/gen_deep_assets.py(モデル/lang/レシピ), tools/mineral_textures.py, 生成物   ※M01 完了後に着手
goal: 新ティア「Abyssal Alloy(深海合金)」= ingot(vanadium+cobalt+nickel)。
- ツール5種(pickaxe/axe/shovel/hoe/sword): 耐久・速度はダイヤ〜ネザライト間。**水中採掘ペナルティ無効**: Forge の PlayerEvent.BreakSpeed で、プレイヤーが水没(isEyeInFluidType water)/空中でないとき、abyssia:underwater_tools タグのアイテムを持っていれば newSpeed を補正(水中×5戻し、地面外×5戻し)。
- Deep Diver's Helmet(潜水ヘルム): 装着中は水中の呼吸ゲージ消費なし(WATER_BREATHING 相当を Item#onArmorTick で付与)+ 水中暗視を弱く付与する設定は ClientConfig ではなく common Config に ON/OFF。
- Flippers(フィン、ブーツ): 水中の泳ぎ速度上昇(ForgeMod.SWIM_SPEED の属性修飾 or Dolphin's grace 相当)。
- レシピ: ingot/plate 素材は M01 のレアメタル+ plant の hadal_plating/sea_cloth 等を使用(plant_defs の素材を再利用、新素材は作らない)。
constraints: テクスチャは ChatGPT 不使用。バニラのダイヤ/ネザライトの装備・ツールアイコンと防具レイヤーPNGを Recolour(色替えのみ)で。防具レイヤーは assets/abyssia/textures/models/armor/*_layer_1/2.png。tag 追加は generator 経由。ビルドはメインが実行。
accept: アイテム登録・lang・モデル・レシピ・tag 生成、Java がコンパイル可能な状態(メインがビルド確認)。
