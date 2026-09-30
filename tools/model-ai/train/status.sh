#!/usr/bin/env bash
# Status of a run as one JSON line:  bash status.sh [run dir]   (default ~/model-ai/runs/v1)
run="${1:-$HOME/model-ai/runs/v1}"
log="$run/train.log"
running=$(pgrep -f "train_sft.py.*$run" >/dev/null && echo true || echo false)
exit_code=$(cat "$log.exit" 2>/dev/null || echo null)
last=$(tail -n 1 "$run/train_log.jsonl" 2>/dev/null || echo null)
progress=$(tail -c 2000 "$log" 2>/dev/null | tr '\r' '\n' | grep -oE '[0-9]+/[0-9]+ \[[^]]*\]' | tail -n 1)
error=$(grep -E 'Error|Traceback|Killed' "$log" 2>/dev/null | tail -n 1 | tr -d '"\\' | cut -c1-200)
gpu=$(nvidia-smi --query-gpu=utilization.gpu,memory.used --format=csv,noheader 2>/dev/null)
printf '{"run":"%s","running":%s,"exit":%s,"progress":"%s","gpu":"%s","error":"%s","last_log":%s}\n' "$run" "$running" "$exit_code" "$progress" "$gpu" "$error" "$last"
