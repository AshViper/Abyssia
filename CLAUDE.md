# Claude Code 運用・設計指示

目的: **少ないトークンとコンテキストで、速く正確に**。週の使用量上限を守るため、読む量・呼ぶモデル・エージェント数・やり取りを最小にする。

## 1. 読む量を減らす（最優先）

- **全文を読まない。** Grep で場所を絞り、Read は `offset`/`limit` で必要な範囲だけ。`.claude/settings.json` のフック（`tools/token_guard.py`）が 40KB 超・生成物（`*Mesh.java`、`.bbmodel`、`build/`、`run/logs` など）の丸読みを止める。
- **Vault は抜粋で引く**: `python tools/memory.py search <語>` → `heads <note>` → `show <note> <見出し>`（skill `mem`）。ノートを丸ごと読まない。`history/` と `_archive/` は読まない。
- 取得順: 要求 → CLAUDE.md → 関連 Memory の抜粋 → 対象コードの該当部分 → 必要な依存だけ。巨大ログ・生成物・`build`・`.git`・キャッシュは入れない。
- 大量に読んだら、必要な事実だけを残して捨てる。同じ内容を二度読まない。出力・報告も短く（表・全文再掲・事前の説明をしない）。

## 2. モデルは軽く、必要かどうかも軽量モデルで判断

| 作業 | モデル |
|---|---|
| 要求整理・必要性の判断（設計/Memory/サブエージェントが要るか）・分類・要約・Memory 更新・仕様書 | **Haiku**（agent `memo`、`decision`）。**コードは書かせない** |
| **コーディング全般**（機械的な変更も含む）・設計書・修正・検証 | **Sonnet**（`coder-light`、`coder-standard`、`verify`） |
| **難しい実装・同じバグが Sonnet で 3 回直らない部分** | **Opus**（`coder-heavy`、1 問題 1 回） |

- 軽量モデルは Haiku 5.5（agent 定義ではエイリアス `haiku`）、Sonnet は `claude-sonnet-5-5`、Opus は `claude-opus-5-5`。Opus を通常の実装・設計・判断・要約に使わない。メイン自身が高いモデルなら、読む・書く量の多い作業はサブエージェントに出す。
- **必要性の判断**: 単純な作業（明確なバグ修正・typo・設定・既存パターンの実装）は判断も設計も省いて直接実装。迷うときだけ `memo` に 1 回聞く（JSON: `{needs_design, memory_terms, model, subagents}`）。設計・Decision・Verification・Memory 更新は、必要な条件を満たすときだけ。
- 流れ: 要求 → (必要なら Haiku が判断) → Sonnet が実装 → ビルド・テスト（**実際の結果で検証**、AI の推測で成功と判断しない）→ 失敗は Sonnet が修正（3 回まで）→ 直らなければ Opus 1 回 → 成功したら必要なときだけ Haiku が Obsidian に要約。
- Opus で直したら、原因と再発防止を Vault に残す（再び Opus を呼ばないため）。

## 3. サブエージェント: 最大 6、通信なし

- **最大 6 つ**（2026-10-09 にユーザーが 3→6 に変更）、標準は 0〜1。独立して分けられる作業だけ。同じコードを複数に調べさせない。サブエージェントのエフォートは**高**（Agent の `effort: "high"` を渡す）。
- **エージェント同士は通信しない。** メインだけが結果を受け取り統合する。依頼文は自己完結（対象ファイル・行範囲・仕様・受け入れ条件）にし、会話の経緯や Vault の全文を渡さない。`SendMessage` での往復や、あるエージェントの出力を次へ中継する連鎖を作らない。
- 返答は `STATUS / FILES / NOTES`（10 行以内）か JSON。検証・判断は読み取り専用で `PASS/FAIL`、`APPROVE/REJECT/MODIFY` だけ。
- **Decision Agent**（Haiku）はアーキテクチャ変更・新ライブラリ・API/DB 変更・複数案の比較・性能/セキュリティ・既存設計との矛盾の可能性があるときだけ。**Verification Agent**（Sonnet）は大きな変更のときだけ。ビルドとテストが先。

## 4. 外部記憶（Obsidian）

- Vault: `G:\Obsidian\Abyssia Vault\project\`（書式・レベルは Vault の `00 使い方.md`）。auto-memory の `MEMORY.md` は索引だけで、新しい記憶は Vault に書いて索引に 1 行足す。
- **将来使う確定情報だけ**（仕様・設計判断・制約・問題の原因と解決）を `What / Why / Decision / Constraint` で短く。会話・思考過程・ログ・Git で分かる変更は残さない。更新するのは、新仕様の確定・重要な判断・再利用できる解決・今後に影響する制約・古くなった記述のとき。
- 優先順位: 現在のコード > 設定 > 公式ドキュメント > 仕様 > Memory > 過去の会話。矛盾したら現状に合わせて Memory を直す。技術判断は公式情報を優先し、バージョン依存は現行を確認。推測を事実にしない。

## 5. 実装ルール

- **最小変更。** 無関係なリファクタ・命名変更をしない。原因候補 → 最小の検証 → 修正の順で、闇雲に変えない。同じ修正を繰り返さない。
- 確認が要るのは、破壊的変更・大量削除・公開環境・課金・セキュリティ影響・方針が複数で選択が必要・意図が不明なとき。それ以外は自律的に進める。
- 繰り返す手順は skill / CLI に寄せる（`mem`、`port-forge`、`tools/memory.py`、`tools/mc_format.py`）。ツールは Claude 専用にせず、ユーザーも使える CLI（JSON 可、副作用を明示、可能なら dry-run）にする。
- **ワールド生成・バランスの変更は、仕様書の完了条件を 1 項目ずつ実機で確認する**（2026-10-09 ユーザー指示）。実機 = scratch サーバー（`/abyssia map`・`caves census|locate|stats`・`execute if block|biome`・チャンク生成時間）と、見た目・環境効果はクライアント（AutoShot 等）。複数シードと両ワールド型（normal / ocean_world）、Forge と NeoForge の両方で行い、結果は数値で報告する。確認していない項目・確認できない項目（プレイヤー不在でのギミック等）は「未検証」と書き、実装済み・成功と報告しない。重いデバッグコマンドはサーバーを 60 秒止めるので小さい範囲で回す。

## 6. このプロジェクト（Abyssia）

- **ベースは NeoForge 1.21.1**（branch `NeoForge1.21.1`、worktree `F:\Java\Abyssia-NeoForge`）。ここで実装・テストし、**Forge 1.20.1（`main`、`F:\Java\Abyssia`）へ移植**して、両方に入って完了（skill `port-forge`）。パッケージ・クラス名・modid は変えない。データは `python tools/mc_format.py --to-forge F:/Java/Abyssia`（1.21→1.20）。
- `tools/` の生成ツールの出力は**手編集しない**（生成元を直して再実行）。`tools/texture_locks` のテクスチャは再生成で消さない。
- gradle・実機テストはメインが実行（サブエージェントはしない）。dev client の実行中は gradle を回さない。実機確認は `F:\Java\Abyssia-scratch-*` のコピーで行い、本体の `run/` に触れない。
- ユーザーへの返答は**日本語**。**プラン（設計・仕様の立案）は ChatGPT（Claude in Chrome 経由）が考える**（2026-10-09 ユーザー指示、従来は Haiku/メイン）。メインは ChatGPT のプランを受け取り、現行コードとの矛盾・実現性を確認して実装に回す（矛盾があれば根拠を添えてユーザーに確認）。Haiku（`memo`）は仕様書の清書・Vault 記録だけ。ChatGPT はほかにテクスチャ画像の生成も行う。agentflow の運用は Vault `modules/agentflow.md`（`memory.py show agentflow <見出し>`）。完了時は Obsidian にメモし、commit/push は依頼どおり。
