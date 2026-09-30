# KiroCrew ChatGPT Workflow - クイックスタート

## 🚀 すぐに使い始める

### 1. 仕様書を配置

ChatGPTで生成した仕様書を `inbox/specs/` に配置:

```markdown
# M04 タスク名
tier: light | standard | heavy
files: 編集対象ファイル (カンマ区切り)
goal: 目的 (1-3行)
constraints: 制約
accept: 完了条件
```

### 2. ワークフロー解析

```bash
cd F:\Java\Abyssia
python tools/kiro_workflow.py process-specs
```

**出力内容:**
- ✅ 検出された仕様書一覧
- ⚠️ ファイル競合の検出
- 💡 推奨実行順序（バッチ分割）
- 📄 `inbox/flow/pending_tasks.json` に詳細保存

### 3. 実行コマンド生成

```bash
# 特定のバッチの実行コードを生成
python tools/kiro_execute.py 1
```

### 4. KiroCrewで実行

生成されたコードをそのままコピー＆実行:

```python
spawn_run(
    tasks=[
        """実装タスク: ...
        ...
        """,
        # ... 他のタスク
    ],
    solo_reason='parent_parallel',
    solo_details='メインは統合・ビルド・検証担当',
    include_memory=False,
    include_lessons=True,
    include_project=True
)
```

### 5. 完了待ち → 次のバッチ

すべてのサブエージェント完了イベントを確認後、次のバッチへ。

### 6. 統合ビルド

```bash
cd F:\Java\Abyssia
./gradlew build
```

## 📊 実行例

```
📋 inbox/specs/ の未処理仕様書を確認中...
📄 3 件の仕様書を検出
  ✓ M01-feature-a (tier: light)
  ✓ M02-feature-b (tier: standard)
  ✓ M03-feature-c (tier: heavy)

💡 推奨実行順序:

【バッチ 1】 (並列3)
  - M01-feature-a
  - M02-feature-b
  - M03-feature-c
```

## 🔍 ファイル競合の例

```
⚠️  ファイル競合検出 - 直列実行が必要:
  M01 ⟷ M02
    競合: src/main/java/com/abyssia/registry/ModBlocks.java

💡 推奨実行順序:
【バッチ 1】
  - M01
  ↓ 完了待ち
【バッチ 2】
  - M02
```

→ M01完了後にM02を実行

## ⚙️ パラメータ説明

### spawn_run パラメータ

- **tasks**: タスク配列（並列実行）
- **solo_reason**: 
  - `'parent_parallel'` - 親が別作業を持つ
  - `'bulk_data'` - 大量データ処理でコンテキスト分離
  - `'specialist'` - 専門能力が必要
- **solo_details**: 親の作業内容を具体的に記述
- **include_memory**: false（仕様書に全記載済み）
- **include_lessons**: true（コード編集に必要）
- **include_project**: true（プロジェクト内作業）

## 🎯 ベストプラクティス

### ✅ 良い仕様書

```markdown
# M01 レアメタル追加
tier: heavy
files: src/main/java/com/abyssia/registry/ModBlocks.java, tools/gen_deep_assets.py
goal: 深海バイオームに希少金属6種を追加
constraints: 既存の鉱石パターンに従う
accept: ビルドが通り、ゲーム内で配置可能
```

### ❌ 避けるべきパターン

```markdown
# BAD: ファイル指定が曖昧
files: src/**/*.java

# BAD: 複数タスクで同じファイル
Task1: files: ModBlocks.java
Task2: files: ModBlocks.java  # 競合！
```

## 🛠️ トラブルシューティング

### ビルドエラー

```bash
# 個別に確認
./gradlew compileJava

# クリーンビルド
./gradlew clean build
```

### サブエージェント失敗

1. エラーログを確認
2. 必要に応じて上位tierで再実行
3. または手動修正

### メモリ不足

```python
# 実行前にリソース確認
resource_status()
```

## 📚 関連ファイル

- `tools/kiro_workflow.py` - ワークフロー解析
- `tools/kiro_execute.py` - 実行コード生成
- `inbox/specs/` - 仕様書置き場
- `inbox/flow/pending_tasks.json` - 実行プラン
- `.kiro/crew/skills/kirocrew-chatgpt-workflow/SKILL.md` - 詳細ドキュメント

## 🎓 次のステップ

1. ✅ **テスト実行**: TEST01-simple で動作確認
2. 📝 **実際の仕様書で実行**: 小規模タスクから開始
3. 🔄 **複数バッチ**: 依存関係のあるタスクを順次実行
4. 🚀 **本格運用**: ChatGPT連携で大規模開発

---

**動作確認済み**: 2026-09-30 ✓
