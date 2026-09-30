#!/usr/bin/env bash
# Windows-side orchestrator (Git Bash): train (WSL, GPU) -> predict (WSL) -> evaluate (Windows node).
# Runs in the foreground and keeps a wsl.exe alive, so WSL does not stop the distro mid-job.
#   bash train/pipeline.sh <run> [--wait] [--no-train] [--limit 400] [-- extra train_sft.py args]
#     <run>      run name = data version (data/<run>/sft, ~/model-ai/runs/<run>)
#     --wait     first wait for a training of <run> that is already running (its train.log.exit)
#     --no-train skip training (adapter must exist)
# Outputs: data/<run>/pred_<run>_{test,real}.jsonl, data/<run>/report_<run>_{test,real}.json
# Progress lines go to stdout as JSON; the dashboard shows the same state.
set -uo pipefail
cd "$(dirname "$0")/.."
run="$1"; shift
wait=0; train=1; limit=400; extra=()
while [ $# -gt 0 ]; do
	case "$1" in
		--wait) wait=1 ;;
		--no-train) train=0 ;;
		--limit) limit="$2"; shift ;;
		--) shift; extra=("$@"); break ;;
	esac
	shift
done
export MSYS_NO_PATHCONV=1
W="wsl.exe -d ${MODEL_AI_DISTRO:-Ubuntu-24.04} --"
T=/mnt/f/Java/Abyssia/tools/model-ai/train
R='$HOME/model-ai/runs'
log() { printf '{"time":"%s","run":"%s","step":"%s"}\n' "$(date +%H:%M:%S)" "$run" "$1"; }
fail() { log "failed: $1"; exit 1; }

if [ "$wait" = 1 ]; then
	log "waiting for the running training"
	until $W bash -c "test -f $R/$run/train.log.exit"; do sleep 60; done
	code=$($W bash -c "cat $R/$run/train.log.exit" | tr -d '\r')
	[ "$code" = 0 ] || fail "training exited with $code"
	train=0
fi
if [ "$train" = 1 ]; then
	log "training"
	$W bash $T/bg.sh "$R/$run/train.log" train_sft.py --data "../data/$run/sft" --out "$R/$run" --batch 2 --accum 8 "${extra[@]}" || fail "training"
fi
for split in test real; do
	lim=$([ "$split" = test ] && echo "$limit" || echo 0)
	log "predict $split"
	$W bash $T/bg.sh "$R/$run/predict_$split.log" predict.py --adapter "$R/$run/adapter" --data "../data/$run/sft/$split.jsonl" --out "../data/$run/pred_${run}_$split.jsonl" --limit "$lim" --batch 16 || fail "predict $split"
	log "evaluate $split"
	node ai.js eval --pred "data/$run/pred_${run}_$split.jsonl" --ref "data/$run/$split.jsonl" --subset --out "data/$run/report_${run}_$split.json" > "data/$run/summary_${run}_$split.json" || fail "eval $split"
done
log "done"
