#!/usr/bin/env bash
# Runs a train/ script inside the WSL venv (~/model-ai/.venv, created with uv).
#   From Windows: wsl -d Ubuntu-24.04 -- bash /mnt/f/Java/Abyssia/tools/model-ai/train/run.sh train_sft.py --help
set -euo pipefail
cd "$(dirname "$0")"
source "$HOME/model-ai/.venv/bin/activate"
export HF_HOME="${HF_HOME:-$HOME/model-ai/hf}"
export PYTHONUNBUFFERED=1
# Triton compiles small C helpers at runtime; fall back to the zig cc wrapper when gcc is missing
if ! command -v gcc >/dev/null; then export CC="$HOME/model-ai/bin/cc"; fi
exec python "$@"
