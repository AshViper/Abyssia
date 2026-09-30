# T01 ブロックテクスチャの作り直し
tier: standard
files: inbox/prompts/**, inbox/textures/**, tools/texture_locks/**, src/main/resources/assets/abyssia/textures/**
goal: 既存ブロックテクスチャを深海の世界観に合わせて作り直す。固定済みの rock 系も対象に含める。
constraints: この仕様書はユーザーの明示的な指示により rock の texture_locks 更新を許可する。画像は ChatGPT ImageGen で生成し、各ブロックIDのプロンプトと出力先を inbox/prompts に記録する。Codex は画像を生成しない。
accept: 対象IDごとの画像生成プロンプトが作成され、ChatGPT 生成画像を inbox/textures に取り込める状態になっている。変更した rock テクスチャのロック基準も更新される。
