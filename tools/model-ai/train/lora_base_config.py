"""Writes a weights-free base-model directory for llama.cpp's convert_lora_to_gguf.py.

The training base is the bitsandbytes 4-bit checkpoint, whose config.json names a quantisation the
converter cannot read. The LoRA converter only needs the architecture, so we copy config.json without
`quantization_config` (plus tokenizer files) into ~/model-ai/lora-base.
    python lora_base_config.py [hf snapshot dir] [out dir]
"""

import glob
import json
import os
import shutil
import sys

src = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.expanduser("~/model-ai/hf/hub/models--unsloth--Qwen2.5-Coder-3B-Instruct-bnb-4bit/snapshots/*/")))[0]
out = sys.argv[2] if len(sys.argv) > 2 else os.path.expanduser("~/model-ai/lora-base")
os.makedirs(out, exist_ok=True)
with open(os.path.join(src, "config.json"), encoding="utf8") as f:
    cfg = json.load(f)
cfg.pop("quantization_config", None)
cfg["torch_dtype"] = "bfloat16"
with open(os.path.join(out, "config.json"), "w", encoding="utf8") as f:
    json.dump(cfg, f, indent=2)
for name in ["tokenizer.json", "tokenizer_config.json", "vocab.json", "merges.txt", "special_tokens_map.json", "added_tokens.json", "generation_config.json"]:
    p = os.path.join(src, name)
    if os.path.exists(p):
        shutil.copyfile(p, os.path.join(out, name))
print(json.dumps({"out": out, "files": sorted(os.listdir(out))}))
