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
tier: light | standard | heavy      # 実装モデルの目安（memo が判定）
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
- Claude は「requestsを処理して」で `flow.py req list` を読み、仕様書化 (軽量モデル memo) → `inbox/specs/` → tier 別に実装。via によらず仕様書は ChatGPT に頼まない。
- 進行に合わせて `flow.py req set <id> specced|running|done`。tier=auto は Claude が判定。

### 役割分担 (2026-10-08、ユーザー指定。2026-10-02/03 の「ChatGPT が仕様書・デザイン・検査」を置き換え)
- **ChatGPT はテクスチャ画像の生成だけ**。仕様書・デザイン画・モデル設計案・検査は頼まない。
- **仕様書は軽量モデル (agent `memo`、Haiku) が書く**。上のテンプレで短く (目的・変更ファイル・受け入れ条件・tier)。単純な依頼 (明確なバグ修正・typo・設定・既存パターンの実装) は仕様書を省略して直接実装する。
- **実装・修正は Sonnet** (`coder-standard`)、機械的な変更は Haiku (`coder-light`)。**検査**は実際のビルド・テストの結果と、必要なときだけ `verify`。見た目はゲーム内スクリーンショットを Claude 自身が見て確認する (ChatGPT に見せない)。
- モデル・ブロックの見た目は依頼文と既存の見た目に合わせて Claude がモデル定義 (`tools/bbmodel-generator/definitions/<id>.json`) に落とす。写真があるときは `/photo-to-model`。

### 依頼処理の手順 (2026-10-08)
1. 依頼を読み、単純なら仕様書を省いて実装へ。そうでなければ `memo` に仕様書を 1 回書かせて `inbox/specs/<id>.md` に保存する。
2. 手順が決まったら `flow.py add` でタスクを登録して実装する (サブエージェントは最大 3、通信なし。結果はメインだけが受け取る)。
3. **テクスチャが新規に要るときだけ ChatGPT**: Claude が Claude in Chrome で chatgpt.com を操作し (ログイン済み前提。パスワード入力・CAPTCHA は人がやる)、`inbox/prompts/<task>-textures.md` のプロンプトで画像を生成させ、`inbox/textures/` に保存する。ChatGPT 画像は blob URL でブラウザのダウンロードが落ちないことがある。その場合は画像ビューアを開いて `computer zoom` + `save_to_disk` で取得する (ダウンロードに確認は不要、ユーザー許可済み)。取り込みは texture pipeline (texture_locks 厳守)。
4. ビルド・テストで検証し、直らないものだけ上のモデル運用 (Sonnet 3 回 → Opus 1 回) に従う。

### 役割一覧 (2026-10-03)
| 担当 | 誰 | やること | 記録・表示 |
|---|---|---|---|
| 依頼 | ユーザー | フォームか `inbox/requests/` に依頼を出すだけ | stage `request` |
| 仕様書・判断 | memo (Haiku) | 必要なときだけ仕様書を短く書く。必要性・tier の判断、要約、Obsidian 更新 | stage `router` |
| テクスチャ生成 | ChatGPT (Claude in Chrome で操作) | テクスチャ画像だけを生成する (仕様書・デザイン・検査は頼まない) | stage `chatgpt` / `texture`、ツリー右の CHATGPT 枠 |
| 振り分け・統合 | Main (Sonnet 5.5) | 仕様書を受けて必要なときだけタスクに割り、tier ごとにサブエージェント (最大3、通信なし) へ渡し、結果をまとめる。自分では大きな実装をしない | stage `router`、ツリー橙 |
| 調査 | Explore | 該当コードの場所を探して要点だけ返す | ツリー青 |
| 設計判断 | decision (Haiku) | CLAUDE.md §8 のときだけ。approve / reject / modify を JSON で返す。コードは書かない | ツリー左の紫枠 |
| 実装 | coder-light (Haiku)、coder-standard (Sonnet 5.5)、coder-heavy (Opus 5.5、Sonnet で3回直らないときだけ) | 1体1タスク。`files:` の範囲だけ編集。手に負えないときは ESCALATE | `flow.py add/set`、ツリー緑 |
| テクスチャ取込 | Texture Pipeline | ChatGPT 画像を `inbox/textures/` から取り込む (texture_locks 厳守) | stage `texture` |
| 検証 | verify エージェント (Sonnet 5.5、読むだけ) | 設計違反、API の誤用、互換性。PASS / FAIL を JSON で返す。コードは変えない | stage `verify` |
| ビルド・テスト | Main のみ | `gradle build` と実機テスト。サブエージェントには走らせない | stage `build` |
| 記憶 | Main | Obsidian Vault に重要な事実と判断だけ書く | stage `memory` |
| 公開 | Main | 依頼で変えたファイルだけを commit して push | stage `git` |

### Agent Flow 運用ルール (2026-10-03、ユーザー指定)
1. **ChatGPT はテクスチャ生成だけ (2026-10-08、ユーザー指定)**: 仕様書は軽量モデル (memo) が書く。Claude は画像を描かず、新規テクスチャだけ ChatGPT ImageGen に作らせる。ChatGPT が使えないときは `flow.py log main --kind decision` に残して報告する。
2. **サブエージェントは最大 3・通信なし (2026-10-08)**: 必要なときだけ割り、1エージェント1タスク、依頼文は自己完結。`files:` が重ならないタスクは並列、依存するものは queued。エージェント同士で結果を渡し合わず、メインだけが受け取って統合する (SendMessage や中継の連鎖を作らない)。
3. **作業が終わったら Obsidian メモと git push**: ① Vault `G:\Obsidian\Abyssia Vault\project\` の該当ノートを更新するか、`history/` に日付ログを書く (CLAUDE.md §17 の形式。些細な変更は書かない)。② その依頼で変えたファイルだけを commit し、ほかで作業中の変更は混ぜない。③ push する。Forge は `main` と `Forge1.20.1`、NeoForge 移植は `NeoForge1.21.1`。各段階は `flow.py stage memory|git running` → `done` で記録する。push に失敗したら `flow.py log main --kind error` に残して報告する。
4. **1.21.1 をベースに開発し、1.20.1 へ移植する (2026-10-08、ユーザー指定。2026-10-03 の「Forge 先」を置き換え)**: 依頼は NeoForge 1.21.1 (`NeoForge1.21.1`、worktree `F:\Java\Abyssia-NeoForge`) で実装・ビルド・検査を済ませ、Forge 1.20.1 (`main`、`F:\Java\Abyssia`) へ移植して両方で通って初めて完了とする。① NeoForge で実装・ビルド・実機テスト・検査を済ませる。② 同じ変更を Forge に移植するタスク `<id>-forge` を `flow.py add` し、NeoForge タスクの完了まで queued にする。移植は別のサブエージェントに任せ、パッケージ・クラス名・modid は変えない。data/lang/tags の形式差は Vault `decisions/two-loader-branches.md` を見る (`tools/mc_format.py` は現状 1.20→1.21 の向きなので、逆向きの扱いは同ノートの 2026-10-08)。worktree を編集するときは必ず絶対パスを使う。③ Forge 側でも `gradle build` と実機テスト、検査を通す。④ 両方を commit・push する (NeoForge は `NeoForge1.21.1`、Forge は `main` と `Forge1.20.1`)。片方だけ通った状態では依頼を done にしない。1.20 で作れない部分は差分を `flow.py log main --kind decision` に残して報告する。
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

## 画面の使い方 (2026-10-03 更新)
- フロータブはエージェントツリー1枚にまとめた (旧3段フローチャートは廃止)。左=decision、中央=main とサブエージェント、右=ChatGPT・PIPELINE (依頼〜git push の全ステージ)・TASKS (flow.py の実行中・待機・失敗タスクだけ)。左列の DECISION の下に DETAIL と NEW REQUEST (依頼フォーム)。下=やり取りとセッションログ (hook の記録と flow.py の log を時系列で混ぜ、flow.py 分は黄色)。承認待ちはツリー上部の橙の帯、自動実行ログはステータスバーの折りたたみ。依頼フォームの既定は「ChatGPT に仕様書/テクスチャを依頼」。
- main の箱にはセッションの 📁 リポジトリ・ブランチ (hook の cwd から) と「担当」を出す。担当は最新のユーザー依頼で、依頼が記録にないときは子エージェントの作業名。箱の色の点はセッションごとの色で、同じ色のサブエージェントがそのセッションの担当。
- サブエージェントの下の「back to main session」段に Verification → Build/Test → Memory → Git の後処理を並べる。右の PIPELINE は前段 (依頼/振り分け/ChatGPT/texture) だけ。Verification は verify エージェントが動いている間は実行中で、終了時の返答の PASS / FAIL で完了か失敗か。
- 箱・PIPELINE の行・TASKS の行を押すと、右の「詳細」に中身が出る (エージェントならいまの作業と触ったファイル)。
- 待機に戻る: 何も動かず45秒たつと全ノードが待機表示。ヘッダーの「リセット」で即時。作業の最後に `flow.py finish`。
- 「テクスチャ」タブ: 変更前(inbox/backup/textures-20260930)と現在を並べて表示。作り直し/ChatGPT/ロック/変更なしで絞り込み。
- 自動実行: 既定OFF。ヘッダーのチェック(または tools/agentflow/autorun.json の enabled)でONにすると、依頼の送信で headless `claude -p` が起動する。許可ツールは autorun.json の allowed_tools。
