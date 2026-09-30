"""
KiroCrew Workflow Executor
実際に spawn_run を呼び出して並列実行を行う
"""
from pathlib import Path
import json
import sys

# プロジェクトルートをPythonパスに追加
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root / "tools"))

from kiro_workflow import load_flow_state, parse_spec, check_file_conflicts, generate_spawn_tasks


def execute_batch(batch_specs, batch_num):
    """
    Execute one batch of specifications using spawn_run
    Returns the spawn_run command as a string for KiroCrew
    """
    tasks_text = []
    
    for spec_id in batch_specs:
        spec_path = project_root / "inbox" / "specs" / f"{spec_id}.md"
        spec = parse_spec(spec_path)
        
        task_text = f"""実装タスク: {spec['title']}
タスクID: {spec['id']}
tier: {spec['tier']}

## 目的
{spec['goal']}

## 編集対象ファイル
{', '.join(spec['files'])}

## 制約
{spec['constraints']}

## 完了条件
{spec['accept']}

## 重要な指示
1. 上記の「編集対象ファイル」のみを編集すること
2. 制約を厳守すること
3. 完了条件を満たす実装を行うこと
4. 並列実行中のため、gradle build/runClient は実行しないこと
5. 実装完了後、変更内容を簡潔に報告すること

他のタスクと競合しないよう、指定されたファイルのみを編集してください。
"""
        tasks_text.append(task_text)
    
    return tasks_text


def print_execution_plan(pending_tasks_file):
    """
    Print the execution plan for KiroCrew
    """
    with open(pending_tasks_file, 'r', encoding='utf-8') as f:
        plan = json.load(f)
    
    if 'execution_order' not in plan:
        print("❌ 実行プランが見つかりません")
        return None
    
    print("\n" + "="*70)
    print("KiroCrew 実行プラン")
    print("="*70)
    
    execution_steps = []
    
    for i, batch in enumerate(plan['execution_order'], 1):
        print(f"\n【バッチ {i}】 {len(batch)} タスク")
        
        batch_tasks = []
        for task_id in batch:
            task = next(t for t in plan['tasks'] if t['id'] == task_id)
            batch_tasks.append(task)
            print(f"  - {task_id} (tier: {task['tier']})")
        
        tasks_text = execute_batch(batch, i)
        
        execution_steps.append({
            'batch_num': i,
            'task_ids': batch,
            'tasks_text': tasks_text
        })
        
        if i < len(plan['execution_order']):
            print("  ↓ 完了待ち")
    
    return execution_steps


def generate_kiro_command(execution_steps, batch_num):
    """
    Generate the actual spawn_run command for a specific batch
    """
    step = execution_steps[batch_num - 1]
    
    if len(step['tasks_text']) == 1:
        # 単一タスク - 直接実行も可能
        print(f"\n{'='*70}")
        print(f"バッチ {batch_num}: 単一タスク")
        print("="*70)
        print("\n以下のタスクを実行してください:\n")
        print(step['tasks_text'][0])
        print("\n" + "="*70)
        return None
    
    # 複数タスク - spawn_run で並列実行
    print(f"\n{'='*70}")
    print(f"バッチ {batch_num}: spawn_run で並列実行")
    print("="*70)
    print("\n以下をKiroCrewで実行してください:\n")
    
    command = "spawn_run(\n"
    command += "    tasks=[\n"
    
    for task_text in step['tasks_text']:
        # タスクテキストをPython文字列リテラルとしてエスケープ
        escaped = task_text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
        command += f'        """{task_text}""",\n'
    
    command += "    ],\n"
    command += "    solo_reason='parent_parallel',\n"
    command += "    solo_details='メインエージェントは統合・ビルド・検証を担当。各サブエージェントは独立したファイル群を編集',\n"
    command += "    include_memory=False,  # 仕様書に全て記載済み\n"
    command += "    include_lessons=True,  # コード編集に必要\n"
    command += "    include_project=True   # プロジェクト内作業\n"
    command += ")"
    
    print(command)
    print("\n" + "="*70)
    
    return command


if __name__ == "__main__":
    project_root = Path(__file__).parent.parent
    pending_tasks = project_root / "inbox" / "flow" / "pending_tasks.json"
    
    if not pending_tasks.exists():
        print("❌ pending_tasks.json が見つかりません")
        print("先に以下を実行してください:")
        print("  python tools/kiro_workflow.py process-specs")
        sys.exit(1)
    
    # 実行プランを表示
    steps = print_execution_plan(pending_tasks)
    
    if steps is None:
        sys.exit(1)
    
    # バッチ番号を指定
    if len(sys.argv) > 1:
        batch_num = int(sys.argv[1])
        if batch_num < 1 or batch_num > len(steps):
            print(f"❌ 無効なバッチ番号: {batch_num}")
            print(f"有効な範囲: 1-{len(steps)}")
            sys.exit(1)
        
        print(f"\n実行対象: バッチ {batch_num}")
        generate_kiro_command(steps, batch_num)
    else:
        print("\n" + "="*70)
        print("使い方")
        print("="*70)
        print(f"特定のバッチを実行するには:")
        print(f"  python tools/kiro_execute.py <バッチ番号>")
        print(f"\n例: バッチ1を実行")
        print(f"  python tools/kiro_execute.py 1")
