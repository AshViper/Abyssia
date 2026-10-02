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

### 役割分担 (2026-10-02、ユーザー指定の理想形)
- **ChatGPT が管理するもの**: 仕様書、デザイン画、モデルの設計 (パーツ定義の案・寸法・配色)、テクスチャ (画像生成)。「何をどう作るか」と「デザイン通りか」の判断は ChatGPT 側。
- **Claude がやること**: ChatGPT の設計をモデル定義 (`tools/bbmodel-generator/definitions/<id>.json`) やコードに落として生成・実装・ビルド・実機テストする。設計を勝手に変えない (ツール制約で作れない所だけ差分を報告して相談)。
- **検査**: できた成果物 (ゲーム内スクリーンショット) を ChatGPT に見せ、デザイン通りに作れているかを ChatGPT が判定。差異があれば ChatGPT が直し方 (パーツ定義の修正案) を返し、Claude が反映して再生成する。合格するまで (最大3回) 繰り返す。
- テクスチャ: 生成ツールの UV 展開図 (`Base PNG`) を ChatGPT に渡して塗らせる形が目標。現状は配色指定のみで、ChatGPT 画像の取込は inbox/textures/ → texture pipeline。

### 依頼処理の必須手順 (2026-10-02、ユーザー指定)
依頼を処理するときは、タスクの分担 (サブエージェント投入) を始める前に、必ず ChatGPT に **仕様書** と **必要なデザイン** を生成させる。
1. Claude が Claude in Chrome で chatgpt.com を直接操作する (ログイン済み前提。パスワード入力・CAPTCHA は人がやる)。依頼文と制約を送り、仕様書 (上のテンプレ) を出させて `inbox/specs/` に保存。
2. 見た目が要る依頼 (乗り物・Mob・ブロックなど) は、デザイン画 (4面図など) も ChatGPT に生成させ `inbox/designs/<id>.png` に保存。プロンプト雛形は `inbox/prompts/`。モデル化は `/photo-to-model`。
3. 画像の保存: ChatGPT 画像は blob URL でブラウザのダウンロードが落ちないことがある。その場合は画像ビューアを開いて `computer zoom` + `save_to_disk` で取得する。ChatGPT 画像のダウンロードに確認は不要 (ユーザー許可済み)。
4. 仕様書とデザインが揃ってから `flow.py add` でタスク分担を始める。
5. **検査**: 実装後 (ビルド通過後)、依頼どおりにできているかを ChatGPT に検査させる。依頼文・仕様書・デザイン画と、成果物のスクリーンショット (モデルのプレビュー/ゲーム内) を添付し、「依頼・仕様・デザインとの差異」を箇条書きで出させる。指摘のうち妥当なものは直して再検査 (最大2回)。結果は `flow.py log main` に1行で残し、`inbox/specs/<id>-review.md` に保存する。ChatGPT の見落としや誤りは Claude が現物で確認する (鵜呑みにしない)。

### 役割一覧 (2026-10-03)
| 担当 | 誰 | やること | 記録・表示 |
|---|---|---|---|
| 依頼 | ユーザー | フォームか `inbox/requests/` に依頼を出すだけ | stage `request` |
| 管理 (設計・検査) | ChatGPT (Claude in Chrome で操作) | 仕様書・デザイン画・モデル設計案・テクスチャ画像を作る。成果物が依頼どおりか検査し、修正案を返す (最大3回) | stage `chatgpt` / `texture`、ツリー右の CHATGPT 枠 |
| 振り分け・統合 | Main (Opus 5.5) | ChatGPT の成果を受けてタスクに割り、tier ごとにサブエージェントへ渡し、結果をまとめる。自分では大きな実装をしない | stage `router`、ツリー橙 |
| 調査 | Explore | 該当コードの場所を探して要点だけ返す | ツリー青 |
| 設計判断 | decision (Opus 5.5) | CLAUDE.md §8 のときだけ。approve / reject / modify を JSON で返す。コードは書かない | ツリー左の紫枠 |
| 実装 | coder-light / standard (Sonnet 5.5)、coder-heavy (Opus 5.5) | 1体1タスク。`files:` の範囲だけ編集。手に負えないときは ESCALATE | `flow.py add/set`、ツリー緑 |
| テクスチャ取込 | Texture Pipeline | ChatGPT 画像を `inbox/textures/` から取り込む (texture_locks 厳守) | stage `texture` |
| 検証 | verify エージェント (Sonnet 5.5、読むだけ) | 設計違反、API の誤用、互換性。PASS / FAIL を JSON で返す。コードは変えない | stage `verify` |
| ビルド・テスト | Main のみ | `gradle build` と実機テスト。サブエージェントには走らせない | stage `build` |
| 記憶 | Main | Obsidian Vault に重要な事実と判断だけ書く | stage `memory` |
| 公開 | Main | 依頼で変えたファイルだけを commit して push | stage `git` |

### Agent Flow 運用ルール (2026-10-03、ユーザー指定)
1. **仕様書とテクスチャは ChatGPT が兼任**: 仕様書・デザイン画・テクスチャ画像はすべて ChatGPT に作らせる (上の必須手順)。Claude は自分で仕様書を書いたり画像を描いたりしない。ChatGPT が使えないときだけ代行し、その理由を `flow.py log main --kind decision` に残す。
2. **複数エージェントで分担・協力**: 1つの依頼を小さなタスクに割り、1エージェント1タスクで負担を小さくする。調査は Explore、設計判断は decision、実装は coder-light/standard/heavy、検査は別のエージェントに任せる。`files:` が重ならないタスクは並列にし、依存するタスクは queued で後に回す。前のエージェントの結果 (仕様書・調査メモ・SendMessage) を次のエージェントに渡して協力させる。メインは振り分けと統合に徹する。
3. **作業が終わったら Obsidian メモと git push**: ① Vault `G:\Obsidian\Abyssia Vault\project\` の該当ノートを更新するか、`history/` に日付ログを書く (CLAUDE.md §17 の形式。些細な変更は書かない)。② その依頼で変えたファイルだけを commit し、ほかで作業中の変更は混ぜない。③ push する。Forge は `main` と `Forge1.20.1`、NeoForge 移植は `NeoForge1.21.1`。各段階は `flow.py stage memory|git running` → `done` で記録する。push に失敗したら `flow.py log main --kind error` に残して報告する。

## エージェントツリー (live、2026-10-03)
フロータブ最上段。`.claude/settings.json` の hook (`tools/agentflow/hook.py`) が全エージェントのツール呼び出し・サブエージェント起動/終了・やり取りを `inbox/flow/live.jsonl` に追記し、`server.py` が `/live.json` にまとめる。flow.py を呼ばなくても自動で出る。
- 箱 = エージェント (橙 main / 緑 coder / 青 その他 / 紫 decision は左の advisor 枠)。「where」行 = いま何のツールでどのファイルを触っているか (終了後は最後のファイル)。
- 線 = 親→子。実行中は光が流れ、依頼・結果が出るとその線をパケットが往復する。
- agent messages = main→サブの依頼文、サブ→main の結果、SendMessage。クリックで全文表示。箱クリックでそのエージェントに絞り込む。
- 完了したサブエージェントと終わったセッションの main は60秒後に隠れる。ハブ行の「完了 N 件を表示」で再表示する (ブラウザごとに記憶)。
- 下段のステージ (⚡ 付き) は hook から自動で変わる。flow.py の `stage` も使え、新しいほうが優先される。
  - Verification: `verify` エージェント (`.claude/agents/verify.md`、読むだけ) の起動で実行中。結果の `"result": "PASS"|"FAIL"` で完了か失敗か
  - Build/Test: `gradlew build|test|check|runClient...` で実行中。出力の BUILD SUCCESSFUL / FAILED で完了か失敗か
  - Memory: Obsidian Vault への Edit/Write、Vault に cd して書くコマンド
  - Git: `git commit` / `git push`。error・fatal・rejected なら失敗
  - ChatGPT: Claude in Chrome の操作。Texture Pipeline: textures/ への書き込みとテクスチャ生成ツール。この2つは90秒動きがなければ完了
  - ヒアドキュメントの本文は判定に使わない。ノードを押すと、そのステージの最近の記録が出る

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
