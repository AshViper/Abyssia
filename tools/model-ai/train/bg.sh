#!/usr/bin/env bash
# Runs a run.sh job with its output in a log file, writing <log>.pid and <log>.exit when done.
#   bash bg.sh <log file> train_sft.py --data ... --out ...
# Keep the calling wsl.exe open for the whole job: when every wsl.exe connection closes, WSL stops
# the distro and kills it (nohup/setsid/systemd included) unless .wslconfig has instanceIdleTimeout=-1.
set -uo pipefail
log="$1"; shift
mkdir -p "$(dirname "$log")"
cd "$(dirname "$0")"
echo $$ > "$log.pid"
rm -f "$log.exit"
bash ./run.sh "$@" > "$log" 2>&1 < /dev/null
code=$?
echo "$code" > "$log.exit"
exit "$code"
