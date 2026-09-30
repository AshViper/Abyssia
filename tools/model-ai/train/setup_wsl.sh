#!/usr/bin/env bash
# One-time training environment inside WSL2 Ubuntu (no sudo needed):
#   wsl -d Ubuntu-24.04 -- bash /mnt/f/Java/Abyssia/tools/model-ai/train/setup_wsl.sh
# Creates ~/model-ai/.venv (uv-managed Python 3.12, which includes Python.h) with Unsloth (+ torch/transformers/trl/peft/bitsandbytes).
# Triton needs a C compiler at runtime: gcc (sudo apt install build-essential) is used when present,
# otherwise a zig-based `cc` wrapper in ~/model-ai/bin (from the ziglang PyPI package).
set -euo pipefail
ROOT="$HOME/model-ai"
mkdir -p "$ROOT/bin" "$ROOT/runs" "$ROOT/hf"
if ! command -v uv >/dev/null && [ ! -x "$HOME/.local/bin/uv" ]; then
	curl -LsSf https://astral.sh/uv/install.sh | sh
fi
UV="$(command -v uv || echo "$HOME/.local/bin/uv")"
# uv-managed Python ships its headers (Triton compiles against Python.h); the system one needs python3-dev
if [ ! -x "$ROOT/.venv/bin/python" ] || ! "$ROOT/.venv/bin/python" -c 'import sysconfig,os,sys; sys.exit(not os.path.exists(os.path.join(sysconfig.get_paths()["include"],"Python.h")))'; then
	rm -rf "$ROOT/.venv"
	"$UV" venv --managed-python --python 3.12 "$ROOT/.venv"
fi
source "$ROOT/.venv/bin/activate"
"$UV" pip install unsloth ziglang
cat > "$ROOT/bin/cc" <<'EOF'
#!/usr/bin/env bash
# zig cc for Triton: lld does not understand -l:<file>, so resolve it against the -L dirs
dirs=(); args=()
for a in "$@"; do case "$a" in -L*) dirs+=("${a#-L}");; esac; done
for a in "$@"; do
	if [[ "$a" == -l:* ]]; then
		f="${a#-l:}"
		for d in "${dirs[@]}"; do if [ -e "$d/$f" ]; then a="$d/$f"; break; fi; done
	fi
	args+=("$a")
done
exec python -m ziglang cc "${args[@]}"
EOF
chmod +x "$ROOT/bin/cc"
echo 'int main(void){return 0;}' > /tmp/model_ai_cc_test.c
if command -v gcc >/dev/null; then echo "compiler: gcc"; else "$ROOT/bin/cc" /tmp/model_ai_cc_test.c -o /tmp/model_ai_cc_test && echo "compiler: zig cc (install build-essential to use gcc)"; fi
python -c 'import torch, unsloth; print("torch", torch.__version__, "cuda", torch.cuda.is_available())'
