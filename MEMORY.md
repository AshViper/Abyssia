# Abyssia プロジェクト記憶索引

このファイルはプロジェクトの記憶への **索引** です。
実際の記憶は `G:\Obsidian\Abyssia Vault\project/` に保存されています。

## 記憶の入口

**必ず最初に読むこと**: `G:\Obsidian\Abyssia Vault\project\overview.md`

## Level 区分

- **Level 0**: 現在のタスク（この会話）
- **Level 1**: プロジェクト概要 (overview.md)
- **Level 2**: 設計・技術情報（通常の作業で読む）
- **Level 3**: 過去の判断・問題解決（必要なときだけ）
- **Level 4**: アーカイブ（原則読まない）

## 新しい記憶の追加方法

1. **種類を決める**
   - 設計判断 → `decisions/`
   - 問題 → `problems/`
   - 解決手段 → `solutions/`
   - システム → `systems/`
   - モジュール/ツール → `modules/`
   - 履歴 → `history/`

2. **Vault に書き込む**
   - `G:\Obsidian\Abyssia Vault\project/<種類>/<名前>.md`
   - フロントマターを含める:
     ```yaml
     ---
     name: <識別名>
     description: 1行説明
     metadata:
       type: <種類>
       level: 1-4
     ---
     ```

3. **このファイルに索引を追加**
   - 下記の「索引」セクションに1行追加
   - Level 2-3 は `overview.md` からもリンク

## 重要な原則

- ❌ すべての会話を保存しない
- ✅ 将来の作業で再利用する価値がある情報のみ
- ✅ 事実・判断・制約を記録する
- ❌ 「○○を修正した」のような作業ログ
- ✅ 「○○は××を使う。△△は使用禁止」のような設計制約

---

## 索引 (2026-09-30 更新)

### Level 1: 概要
- `overview.md` — プロジェクト概要と記憶の入口

### Level 2: 設計・技術 (通常読む)
- `architecture.md` — コード・ジェネレーター構成
- `code-structure.md` — Javaコードベース構造
- `requirements.md` — 要求・設計ルール
- `development-tools.md` — 開発ツール一覧

#### ワークフロー
- `systems/chatgpt-claude-workflow.md` — ChatGPT ↔ Claude Code 連携
- `modules/agentflow.md` — 並列実行可視化UI

#### システム
- `systems/worldgen.md` — ワールド生成
- `systems/fauna-system.md` — 生物システム
- `systems/external-fauna.md` — 外部生物プロバイダ
- `systems/resource-plants.md` — 資源植物
- `systems/seabed-structures.md` — 海底構造物

#### モジュール/ツール
- `modules/asset-generators.md` — アセット生成
- `modules/texture-pipeline.md` — テクスチャパイプライン
- `modules/texture-studio.md` — テクスチャエディタ
- `modules/bbmodel-generator.md` — モデル生成
- `modules/model-ai.md` — AI学習システム

### Level 3: 判断・問題 (必要時のみ)
- `decisions/*.md` — 設計判断記録 (ADR)
- `problems/*.md` — 既知の問題
- `solutions/*.md` — 解決手段

### Level 4: アーカイブ (原則読まない)
- `history/status-2026-09-30.md` — 現状スナップショット
- `history/2026-09-28.md` — 過去の記録
- `_archive/` — 削除された記憶

---

## 使い方の詳細

`G:\Obsidian\Abyssia Vault\00 使い方.md` を参照してください。
