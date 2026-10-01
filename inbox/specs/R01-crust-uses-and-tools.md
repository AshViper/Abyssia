# R01 クラスト用途と水中ツールの統合
tier: standard
files: M02-crust-uses.md, M03-underwater-tools.md, tools/gen_deep_assets.py, tools/mineral_textures.py, src/main/java/com/abyssia/item/**, src/main/java/com/abyssia/registry/ModItems.java, 生成物
goal: 既存の M02（クラストの精錬・建材化）と M03（水中活動向けツール・装備）を、この依頼の実装範囲として処理する。
constraints: 既存仕様を重複実装しない。M01 のレアメタル生成物を前提にし、既存レシピ・テクスチャロック・plant_defs の整合性を維持する。新規機械や GUI は追加しない。
accept: M02 と M03 の受け入れ条件を満たし、生成スクリプトが再実行できる。最後にメインの Claude がビルドと検証を実行する。
