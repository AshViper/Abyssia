#!/usr/bin/env bash
# Throughput benchmark of training settings on the same rows (run inside WSL):
#   bash bench.sh [data dir] [rows]      -> one JSON line per setting (rows_per_second, peak VRAM)
set -uo pipefail
cd "$(dirname "$0")"
data="${1:-../data/v2/sft}"; rows="${2:-640}"
for cfg in "b2x8:--batch 2 --accum 8" "b4x4:--batch 4 --accum 4" "pack2x8:--batch 2 --accum 8 --packing"; do
	name="${cfg%%:*}"; args="${cfg#*:}"
	out="$HOME/model-ai/runs/bench_$name"
	rm -rf "$out"
	bash ./run.sh train_sft.py --data "$data" --out "$out" --limit "$rows" --epochs 1 --no-eval $args > "$out.log" 2>&1
	if [ -f "$out/run.json" ]; then
		python3 -c "import json,sys; r=json.load(open('$out/run.json')); print(json.dumps({'setting':'$name','rows_per_second':r.get('rows_per_second'),'train_seconds':r.get('train_seconds'),'peak_vram_gb':r.get('peak_vram_gb'),'train_loss':round(r.get('train_loss') or 0,4)}))"
	else
		echo "{\"setting\":\"$name\",\"error\":\"$(grep -E 'Error|error' "$out.log" | tail -1 | tr -d '"\\' | cut -c1-160)\"}"
	fi
done
