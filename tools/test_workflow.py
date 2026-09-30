"""
簡単なテスト実行: TEST01のみを実行
"""

test_task = """実装タスク: TEST01 簡単なテストタスク
タスクID: TEST01-simple
tier: light

## 目的
テスト用のファイルを作成して、ワークフローが正常に動作することを確認する。

## 編集対象ファイル
docs/test_output.txt

## 制約
既存ファイルを変更しない。

## 完了条件
docs/test_output.txt が作成され、タイムスタンプとメッセージが記録されている。

## 重要な指示
1. F:\\Java\\Abyssia\\docs\\test_output.txt を作成
2. 現在時刻とメッセージを記録
3. 完了を報告

例:
```
Test execution timestamp: 2026-09-30 16:45:00
Status: Workflow test successful
Task: TEST01-simple
Message: KiroCrew ChatGPT workflow is operational
```
"""

print("="*70)
print("TEST実行用コマンド")
print("="*70)
print("\nKiroCrewで以下を実行してください:\n")
print('spawn_run(')
print('    tasks=["""')
print(test_task)
print('"""],')
print("    solo_reason='bulk_data',")
print("    solo_details='テストタスク - ファイル作成のみ',")
print("    include_memory=False,")
print("    include_lessons=True,")
print("    include_project=True")
print(')')
print("\n" + "="*70)
print("\nまたは直接実行:")
print("="*70)
print(test_task)
