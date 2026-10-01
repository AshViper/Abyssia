#!/usr/bin/env python3
"""
KiroCrew Workflow Orchestrator
ChatGPT → KiroCrew 並列実行ワークフロー

Usage:
    python tools/kiro_workflow.py process-specs
    python tools/kiro_workflow.py process-request <request_id>
"""

import json
import sys
from pathlib import Path
from datetime import datetime

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

PROJECT_ROOT = Path(__file__).parent.parent
INBOX = PROJECT_ROOT / "inbox"
SPECS_DIR = INBOX / "specs"
REQUESTS_DIR = INBOX / "requests"
FLOW_STATE = INBOX / "flow" / "state.json"


def load_flow_state():
    """Load current flow state"""
    if not FLOW_STATE.exists():
        return {"tasks": {}, "main": {"status": "idle"}}
    with open(FLOW_STATE, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_flow_state(state):
    """Save flow state atomically"""
    FLOW_STATE.parent.mkdir(parents=True, exist_ok=True)
    tmp = FLOW_STATE.with_suffix('.tmp')
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(state, f, indent=2, ensure_ascii=False)
    tmp.replace(FLOW_STATE)


def parse_spec(spec_path):
    """Parse specification file and extract metadata"""
    with open(spec_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    metadata = {
        'id': spec_path.stem,
        'title': '',
        'tier': 'standard',
        'files': [],
        'goal': '',
        'constraints': '',
        'accept': ''
    }
    
    for line in lines:
        line = line.strip()
        if line.startswith('# '):
            metadata['title'] = line[2:].strip()
        elif line.startswith('tier:'):
            tier = line.split(':', 1)[1].strip().lower()
            if tier in ['light', 'standard', 'heavy']:
                metadata['tier'] = tier
        elif line.startswith('files:'):
            files = line.split(':', 1)[1].strip()
            metadata['files'] = [f.strip() for f in files.split(',')]
        elif line.startswith('goal:'):
            metadata['goal'] = line.split(':', 1)[1].strip()
        elif line.startswith('constraints:'):
            metadata['constraints'] = line.split(':', 1)[1].strip()
        elif line.startswith('accept:'):
            metadata['accept'] = line.split(':', 1)[1].strip()
    
    return metadata


def check_file_conflicts(specs):
    """Check for file conflicts between specs"""
    conflicts = []
    for i, spec1 in enumerate(specs):
        for spec2 in specs[i+1:]:
            files1 = set(spec1['files'])
            files2 = set(spec2['files'])
            if files1 & files2:  # Intersection
                conflicts.append({
                    'spec1': spec1['id'],
                    'spec2': spec2['id'],
                    'conflicting_files': list(files1 & files2)
                })
    return conflicts


def generate_spawn_tasks(specs):
    """Generate spawn_run tasks from specs"""
    tasks = []
    
    for spec in specs:
        task = {
            'id': spec['id'],
            'tier': spec['tier'],
            'task': f"""実装タスク: {spec['title']}

## 目的
{spec['goal']}

## 編集対象ファイル
{', '.join(spec['files'])}

## 制約
{spec['constraints']}

## 完了条件
{spec['accept']}

## 指示
1. 上記ファイルのみを編集する
2. 制約を遵守する
3. 完了条件を満たす実装を行う
4. ビルドエラーがないことを確認
5. 実装内容を簡潔に報告

他のファイルは編集しないこと。
並列実行中のため、gradle build/runClient は実行しないこと。
""",
            'files': spec['files']
        }
        tasks.append(task)
    
    return tasks


def create_kiro_spawn_command(tasks, conflicts):
    """Create KiroCrew spawn command structure"""
    if conflicts:
        # 直列実行が必要 - 依存関係を解析して実行順序を提案
        print("\n⚠️  ファイル競合検出 - 直列実行が必要:")
        for conflict in conflicts:
            print(f"  {conflict['spec1']} ⟷ {conflict['spec2']}")
            print(f"    競合: {', '.join(conflict['conflicting_files'])}")
        
        # 実行順序の提案
        print("\n💡 推奨実行順序:")
        execution_order = suggest_execution_order(tasks, conflicts)
        for i, batch in enumerate(execution_order, 1):
            print(f"\n【バッチ {i}】")
            for task_id in batch:
                task = next(t for t in tasks if t['id'] == task_id)
                print(f"  - {task_id} (tier: {task['tier']})")
            if i < len(execution_order):
                print("  ↓ 完了待ち")
        
        return {'execution_order': execution_order, 'tasks': tasks}
    
    # 並列実行可能
    spawn_structure = {
        'parallel_tasks': [],
        'tiers': {'light': [], 'standard': [], 'heavy': []}
    }
    
    for task in tasks:
        spawn_structure['parallel_tasks'].append({
            'id': task['id'],
            'tier': task['tier'],
            'task_text': task['task']
        })
        spawn_structure['tiers'][task['tier']].append(task['id'])
    
    return spawn_structure


def suggest_execution_order(tasks, conflicts):
    """Suggest execution order based on conflicts"""
    # 依存グラフを構築
    dependencies = {task['id']: set() for task in tasks}
    
    for conflict in conflicts:
        # 後のタスクが前のタスクに依存
        spec1_idx = next(i for i, t in enumerate(tasks) if t['id'] == conflict['spec1'])
        spec2_idx = next(i for i, t in enumerate(tasks) if t['id'] == conflict['spec2'])
        
        if spec1_idx < spec2_idx:
            dependencies[conflict['spec2']].add(conflict['spec1'])
        else:
            dependencies[conflict['spec1']].add(conflict['spec2'])
    
    # トポロジカルソート
    batches = []
    remaining = set(task['id'] for task in tasks)
    
    while remaining:
        # 依存関係のないタスクを抽出
        ready = [tid for tid in remaining if not dependencies[tid] & remaining]
        
        if not ready:
            # 循環依存の場合、残りを1つずつ実行
            ready = [remaining.pop()]
        
        batches.append(ready)
        
        for tid in ready:
            remaining.discard(tid)
    
    return batches


def process_specs():
    """Process all unprocessed specs in inbox/specs/"""
    print("📋 inbox/specs/ の未処理仕様書を確認中...")
    
    if not SPECS_DIR.exists():
        print("❌ inbox/specs/ が存在しません")
        return
    
    spec_files = list(SPECS_DIR.glob("*.md"))
    if not spec_files:
        print("✅ 未処理の仕様書はありません")
        return
    
    print(f"📄 {len(spec_files)} 件の仕様書を検出")
    
    # Parse all specs
    specs = []
    for spec_file in spec_files:
        try:
            spec = parse_spec(spec_file)
            specs.append(spec)
            print(f"  ✓ {spec['id']} (tier: {spec['tier']}) - {spec['title']}")
        except Exception as e:
            print(f"  ✗ {spec_file.name}: {e}")
    
    if not specs:
        return
    
    # Check conflicts
    conflicts = check_file_conflicts(specs)
    
    # Generate tasks
    tasks = generate_spawn_tasks(specs)
    
    # Create spawn command
    spawn_cmd = create_kiro_spawn_command(tasks, conflicts)
    
    if 'execution_order' in spawn_cmd:
        # 直列実行プラン
        print("\n" + "="*60)
        print("KiroCrew 実行プラン（直列）")
        print("="*60)
        
        for i, batch in enumerate(spawn_cmd['execution_order'], 1):
            print(f"\n【バッチ {i}】")
            
            batch_tasks = [t for t in tasks if t['id'] in batch]
            
            if len(batch_tasks) == 1:
                # 単一タスク
                task = batch_tasks[0]
                print(f"\n直接実行またはspawn_run:")
                print(f"  タスクID: {task['id']}")
                print(f"  tier: {task['tier']}")
                print(f"\n実装内容:")
                print(task['task'][:300] + "...")
            else:
                # 並列可能なバッチ
                print(f"\n並列実行可能（{len(batch_tasks)}タスク）:")
                print("spawn_run(")
                print("  tasks=[")
                for task in batch_tasks:
                    print(f'    """{task["task"][:100]}...""",')
                print("  ],")
                print("  solo_reason='parent_parallel',")
                print("  solo_details='メインは統合・ビルド担当'")
                print(")")
            
            if i < len(spawn_cmd['execution_order']):
                print("\n⏸️  完了を待ってから次のバッチへ")
        
        print("\n" + "="*60)
        print("最終ステップ:")
        print("  1. すべてのバッチ完了後")
        print("  2. gradle build で統合ビルド")
        print("  3. 検証")
        
    else:
        # 並列実行プラン（元のコード）
        print("\n" + "="*60)
        print("KiroCrew 実行プラン")
        print("="*60)
        
        for tier in ['light', 'standard', 'heavy']:
            if spawn_cmd['tiers'][tier]:
                print(f"\n【{tier.upper()} tier】 {len(spawn_cmd['tiers'][tier])} タスク")
                for task_id in spawn_cmd['tiers'][tier]:
                    print(f"  - {task_id}")
        
        print("\n" + "="*60)
        print("次のステップ:")
        print("="*60)
        print("1. KiroCrew で以下を実行:")
        print("   spawn_run(")
        print("     tasks=[")
        for task in spawn_cmd['parallel_tasks']:
            print(f'       """タスクID: {task["id"]}')
            print(f'       {task["task_text"][:100]}...""",')
        print("     ],")
        print("     solo_reason='parent_parallel',")
        print("     solo_details='メインエージェントは統合・ビルド・検証を担当'")
        print("   )")
        print("\n2. すべての完了を待つ")
        print("3. gradle build で統合ビルド")
        print("4. 検証")
    
    # Save tasks to JSON for reference
    tasks_file = INBOX / "flow" / "pending_tasks.json"
    tasks_file.parent.mkdir(parents=True, exist_ok=True)
    with open(tasks_file, 'w', encoding='utf-8') as f:
        json.dump(spawn_cmd, f, indent=2, ensure_ascii=False)
    
    print(f"\n💾 タスク詳細を保存: {tasks_file}")


def process_request(request_id):
    """Process a specific request"""
    request_file = REQUESTS_DIR / f"{request_id}.json"
    
    if not request_file.exists():
        print(f"❌ リクエストが見つかりません: {request_id}")
        return
    
    with open(request_file, 'r', encoding='utf-8') as f:
        request = json.load(f)
    
    description = request.get("description") or request.get("title") or "No description"
    print(f"📝 リクエスト: {description}")
    print(f"   経由: {request.get('via', 'claude')}")
    print(f"   tier: {request.get('tier', 'auto')}")
    
    via = request.get('via', 'claude')
    
    if via == 'chatgpt':
        print("\n→ ChatGPT へ転送が必要です")
        print("   1. ChatGPT でこのリクエストを処理")
        print("   2. 生成された仕様書を inbox/specs/ に配置")
        print("   3. このスクリプトを再実行")
    else:
        print("\n→ 直接仕様書化を推奨")
        print("   tier を判定して inbox/specs/ に仕様書を作成してください")


def main():
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python tools/kiro_workflow.py process-specs")
        print("  python tools/kiro_workflow.py process-request <request_id>")
        sys.exit(1)
    
    command = sys.argv[1]
    
    if command == "process-specs":
        process_specs()
    elif command == "process-request":
        if len(sys.argv) < 3:
            print("❌ リクエストIDを指定してください")
            sys.exit(1)
        process_request(sys.argv[2])
    else:
        print(f"❌ 不明なコマンド: {command}")
        sys.exit(1)


if __name__ == "__main__":
    main()
