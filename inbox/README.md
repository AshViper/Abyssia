# inbox — ChatGPT → KiroCrew/Claude Code 受け渡し

ChatGPT の成果物をここに置く。Claude Code/KiroCrew は inbox/ 以外を ChatGPT 出力として扱わない。

- specs/     仕様書 `<id>-<slug>.md`（下記テンプレ）
- textures/  ChatGPT 生成画像（取り込み後は tools/texture_studio か forge_textures で処理、rock は texture_locks 厳守）
- prompts/   ChatGPT 用プロンプト置き場（再利用するもの）

## テクスチャ画像の生成

新規テクスチャや作り直しが必要な場合、Claude は画像を直接生成しない。Claude はブロックID・色・質感・
出力先を含むプロンプトを `inbox/prompts/<task>-textures.md` に作り、ChatGPT ImageGen が
`inbox/textures/<block_id>.png` を生成する。画像の取込後に Claude が texture_studio または
forge_textures を実行する。rock 系は texture_locks を必ず維持する。

### シート画像の取込 (Agent Flow「画像生成」タブ / `tools/agentflow/sheets.py`)
プリセットは `inbox/prompts/sheets.json`(シートごとの ids 読み順・kind・dest・skip・lock・derive。プロンプト本文は md から都度抽出)。
1. タブでシートを選び「プロンプトをコピー」→ ChatGPT で生成 (`sheets.py prompt ORE`)
2. 画像をドロップ → `inbox/textures/sheets/<name>.*` に保存 (25MB まで)
3. パネル自動提案 or ドラッグで範囲指定、結合(merge)調整、番号の除外/入替を確認 (`sheets.py detect auto --preset ORE`)
4. ドライラン → 取り込む: textures 上書き・ロック更新・glow 等を再導出・check_textures。旧ファイルは `inbox/backup/gui-import-<時刻>/`
5. 失敗したら「最後の取込を元に戻す」(`sheets.py undo`)。skip の ids(植物の壁/カーペット系など)は常に上書きしない

### スタイル一致の手続き生成 (Agent Flow「テクスチャ生成」タブ / `tools/texture_gen.py`)
ChatGPT 画像が無いとき (新ブロック/アイテム、色替え、ティア違い、欠落) 用。承認済みテクスチャからパレット・明度分布・
ノイズ尺度・鉱石粒の統計を学習し (`profile --category rocks|ores|crusts|plants|tools|...`、キャッシュ `inbox/generated/profiles/`)、
recolor / variants (polished・bricks・cracked・chiseled・mossy・frosted・scorched、`+` で重ね) / ore / synth / sprite / tier / blend で候補を作る。
1. 候補は `inbox/generated/<batch>/<name>.png` + `contact.png` + `manifest.json` にだけ書く (`--dry-run` で書かない)。seed 固定で再現可能
2. まとめて作るなら JSON 仕様: `python tools/texture_gen.py batch inbox/generated/example-batch.json` (見本 = demo バッチ)
3. 確認して `apply <batch>/<name> [--as block/<id>] [--lock]`。ロック済みは `--force-locked` 無しでは拒否、派生物 (glow/bricks/polished) は `--lock` 必須。
   旧ファイルは `inbox/backup/texgen-<時刻>/`、派生物を再導出して check_textures を実行。失敗したら `undo`
4. テスト: `python tools/test_texture_gen.py` (apply/undo は一時コピーでのみ実行)

## 仕様書テンプレ
```
# <id> <title>
tier: light | standard | heavy      # 実装モデルの目安（ChatGPTが判定）
files: <編集してよいファイル/ディレクトリ。並列タスク間で重複させない>
goal: 1-3行
constraints: 使用禁止API・既存設計との整合など
accept: 完了条件（ビルド通過、/abyssia コマンドでの確認など）
```

## 並列実行ルール
- 並列にするのは `files:` が互いに素なタスクのみ。重複があれば直列。
- gradle build / runClient はメインだけが実行（実行中クライアントが壊れる: problems/gradle-breaks-running-dev-client）。
- サブエージェントは ESCALATE を返したら上位 tier で再投入。

## 使い方

### KiroCrew の場合 (推奨)

```bash
# 1. 仕様書を解析
python tools/kiro_workflow.py process-specs

# 2. KiroCrew で並列実行（スクリプトが生成した spawn_run コードを使用）
# 詳細: .kiro/crew/skills/kirocrew-chatgpt-workflow/SKILL.md
```

### Claude Code の場合

Claude Code に「inbox/specs の未処理を tier 別に並列実行して」と指示 → メインが tier で
coder-light/standard/heavy に振り分け、結果を統合してビルド・検証する。

### 実行モデル

AgentFlow の tier はすべて Claude のサブエージェントが実行する。管理(main)は Opus 5.5、light / standard (coder-light / coder-standard) は Sonnet 5.5、heavy (coder-heavy) は Opus 5.5。tier は作業量の目安。依頼 JSON の `via` は `claude` または `chatgpt`。

## 可視化 (tools/agentflow)
フローチャートUI。状態は `inbox/flow/state.json`、CLI で更新、UI は1.5秒ごとに自動反映。
- 起動: `python tools/agentflow/server.py` → http://127.0.0.1:8765/ (または preview `agentflow`)
- 更新: `python tools/agentflow/flow.py add|set|stage|main|escalate|show|reset`
- 運用ルール(メイン): 仕様取り込み時 `add`、サブエージェント投入時 `set <id> running`、完了/失敗で `set`、ESCALATE は `escalate <id>`、ビルド/検証は `stage`、自分のモデルは `main --model`。

## 実装したいことの伝え方 (requests)
UI右の「実装したいこと」フォーム、または Claude に直接言う。フォームは `inbox/requests/<id>.json` に保存される。
- Claude は「requestsを処理して」で `flow.py req list` を読み、via=claude なら仕様書化 / via=chatgpt なら ChatGPT へ依頼 → `inbox/specs/` → tier 別に並列実装。
- 進行に合わせて `flow.py req set <id> specced|running|done`。tier=auto は Claude が判定。

## 詳細ログ (activity)
`flow.py log <task|main|stage> "今やっていること" [--kind file|tool|decision|error] [--model M]`。add/set/stage/main/escalate は自動でログされる。
- メインは節目ごとに1行: 読んだファイル(file)、判断(decision)、エラー(error)。実行中ノードには最新の非status行が表示される。
- UI: 下のアクティビティ欄が新しい順。ノード選択+「選択中のみ」でそのタスクだけに絞れる。

## サイト側の承認 (approvals)
確認が必要な場面（破壊的操作・課金・セキュリティ等 CLAUDE.md §24）だけ、質問をチャットでなくサイトへ出す: `flow.py ask "内容" --who B01 --wait 600`（exit 0=承認, 3=拒否, 4=未回答）。サイト右上の「承認待ち」カードのボタンで回答。通常の設計判断は承認不要（自分で決めて `log --kind decision`）。

## 画面の使い方 (2026-09-30 更新)
- 3段: 上=テクスチャ・管理(依頼/Main/ChatGPT/Texture Pipeline)、中=作業エージェント(light/standard/heavy)、下=その他(Verification/Build/Memory)。光の粒=情報の流れ、実行中の線は速く明るい。
- 待機に戻る: 何も動かず45秒たつと全ノードが待機表示。ヘッダーの「リセット」で即時。作業の最後に `flow.py finish`。
- 「テクスチャ」タブ: 変更前(inbox/backup/textures-20260930)と現在を並べて表示。作り直し/ChatGPT/ロック/変更なしで絞り込み。
- 自動実行: 既定OFF。ヘッダーのチェック(または tools/agentflow/autorun.json の enabled)でONにすると、依頼の送信で headless `claude -p` が起動する。許可ツールは autorun.json の allowed_tools。
