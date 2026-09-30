---
title: KiroCrew ChatGPT Workflow
description: ChatGPT → KiroCrew 並列実行ワークフロー
---

# KiroCrew ChatGPT Workflow

ChatGPT の成果物（仕様書・テクスチャ）を KiroCrew で並列実行するワークフロー。

## 概要

```
ChatGPT (仕様書・テクスチャ生成)
    ↓
inbox/specs/ + inbox/textures/
    ↓
kiro_workflow.py (解析・競合チェック)
    ↓
KiroCrew spawn_run (並列実行)
    ↓
メインエージェント (統合・ビルド・検証)
```

## ディレクトリ構造

```
inbox/
├── specs/         仕様書 (*.md)
├── textures/      ChatGPT生成画像 (*.png)
├── requests/      実装依頼 (*.json)
├── flow/          状態管理
│   ├── state.json
│   └── pending_tasks.json
├── backup/        変更前バックアップ
└── approvals/     承認待ち
```

## 仕様書フォーマット

`inbox/specs/<id>-<slug>.md`:

```markdown
# <id> <title>
tier: light | standard | heavy
files: path/to/file1.java, path/to/file2.java
goal: 1-3行の目的説明
constraints: 使用禁止API・設計制約
accept: 完了条件（ビルド通過、確認方法）
```

### Tier 定義

- **light**: 小規模変更、既存パターン踏襲、シンプルなロジック
- **standard**: 標準的な機能追加、中規模変更
- **heavy**: 大規模変更、アーキテクチャ判断、複雑なロジック

## 使い方

### 1. 仕様書の配置

ChatGPT で生成した仕様書を `inbox/specs/` に配置。

### 2. ワークフロー解析

```bash
cd F:\Java\Abyssia
python tools/kiro_workflow.py process-specs
```

これにより:
- 仕様書の解析
- ファイル競合チェック
- tier 別タスク分類
- `inbox/flow/pending_tasks.json` 生成

### 3. KiroCrew で並列実行

スクリプトが出力した実行プランを使用:

```python
# KiroCrew で実行
spawn_run(
    tasks=[
        """タスクID: M01-rare-metals
        実装タスク: レアメタル追加
        
        ## 目的
        深海バイオームに希少金属を追加
        
        ## 編集対象ファイル
        src/main/java/com/abyssia/registry/ModBlocks.java
        
        ## 制約
        既存の鉱石パターンに従う
        
        ## 完了条件
        ビルドが通り、ゲーム内で配置可能
        
        他のファイルは編集しないこと。
        並列実行中のため、gradle build/runClient は実行しないこと。
        """,
        # ... 他のタスク
    ],
    solo_reason='parent_parallel',
    solo_details='メインエージェントは統合・ビルド・検証を担当。各サブエージェントは独立したファイル群を編集'
)
```

### 4. 完了待ち

すべてのサブエージェント完了イベントを待つ。

### 5. 統合ビルド・検証

```bash
# ビルド
./gradlew build

# 検証
./gradlew runClient
# ゲーム内で確認
```

## 並列実行ルール

### ✅ 並列可能な条件

- `files:` が **完全に互いに素**（重複なし）
- 各タスクが独立して完結する
- 共有リソース（DB、サーバー等）へのアクセスがない

### ❌ 並列不可（直列実行が必要）

- ファイル競合がある
- タスク間に依存関係がある
- gradle build / runClient の実行が必要

`kiro_workflow.py` が競合を検出した場合、警告を出力します。

## エスカレーション

サブエージェントが失敗した場合:

1. エラー内容を確認
2. 必要に応じて上位 tier で再実行:
   - light → standard
   - standard → heavy
3. または手動で修正

## spawn_run パラメータ

```python
spawn_run(
    tasks=[...],                    # 並列タスク配列
    solo_reason='parent_parallel',  # 必須: 並列実行の理由
    solo_details='...',             # 必須: 親の作業内容
    include_memory=False,           # 仕様に全て記載済み
    include_lessons=True,           # コード編集には必要
    include_project=True            # プロジェクト内作業
)
```

## テクスチャ処理

ChatGPT 生成画像を `inbox/textures/` に配置後:

```bash
# バックアップ作成
mkdir -p inbox/backup/textures-$(date +%Y%m%d)
cp -r src/main/resources/assets/abyssia/textures/* inbox/backup/textures-$(date +%Y%m%d)/

# 取り込み
python tools/import_chatgpt_textures.py

# 処理
python tools/forge_textures.py
```

**注意**: rock 系は `texture_locks.py` で保護されています。

## リクエスト処理

実装依頼を `inbox/requests/<id>.json` に保存:

```json
{
  "description": "水中ツールの追加",
  "via": "claude",
  "tier": "standard",
  "timestamp": "2026-09-30T16:00:00"
}
```

処理:

```bash
python tools/kiro_workflow.py process-request <id>
```

## トラブルシューティング

### ファイル競合エラー

```
⚠️  ファイル競合検出 - 直列実行が必要:
  M01 ⟷ M02
    競合: src/main/java/com/abyssia/registry/ModBlocks.java
```

**対応**: 
1. 一方を先に実行
2. 完了後にもう一方を実行
3. または仕様書を統合

### ビルドエラー

並列実行後のビルドエラーは **統合時の問題**:

1. 各サブエージェントの変更を確認
2. インポート文の追加漏れをチェック
3. 競合する変更がないか確認

### メモリ不足

同時実行数が多すぎる場合:

```python
# resource_status でチェック
resource_status()

# 同時実行数を制限（自動キューイング）
# 最大4並列、それ以上は自動待機
```

## 制限事項

- **gradle 実行**: メインエージェントのみ
- **runClient**: 同時に1つのみ（開発クライアント競合）
- **共有ファイル**: 編集は1タスクのみ
- **最大並列数**: resource_status による動的制限

## 関連

- [[chatgpt-claude-workflow]] — 全体ワークフロー設計
- [[agentflow]] — 可視化UI（元の Claude Code 用）
- `inbox/README.md` — 元の仕様
