# B02 新バイオーム用ブロック登録とテクスチャ取込
tier: standard
files: tools/gen_deep_assets.py, tools/forge_textures.py, src/main/resources/assets/abyssia/**(生成物), inbox/textures/**
goal: B01 の新ブロック15種（各バイオーム surface/sub/rock）を登録し、ChatGPT 生成画像を 16x16 に落として取り込む。
constraints: 既存の rock テクスチャ(texture_locks)を上書きしない。生成物は手編集しない。B01 のブロックID確定後に着手。
accept: ブロックが登録され、blockstate/model/lang/loot が生成される。ビルド通過。
