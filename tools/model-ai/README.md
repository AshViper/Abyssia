# model-ai

A locally trained AI that makes Blockbench models from text, edit instructions and (later) photos.
The AI never writes `.bbmodel` directly. It writes the `parts` definition (spec) of
[bbmodel-generator](../bbmodel-generator), and the generator turns that into the model, textures, animation and Java.

```
Request text ──► LoRA model ──► spec (parts JSON) ──► bbmodel-generator ──► .bbmodel / .png / Java
Edit request ──► LoRA model ──► part-addressed JSON Patch ──► apply to spec ──► same as above
```

| Phase | Content | Status |
|---|---|---|
| 1 | Dataset generation, headless renderer, evaluation, baselines | **Done** |
| 2 | Text → spec (QLoRA of a 3B model, WSL2) | Not started |
| 3 | Edit → patch | Not started |
| 4 | Inference server, CLI, retry on error | Not started |
| 5 | Photo input (VLM) | Not started |
| 6 | `cubes` template (Java blocks/items, general-purpose) | Not started |
| 7 | GUI panel | Not started |

## Commands (all print JSON; logs go to stderr)

```bash
node ai.js sample all --n 2 --out sheet.png        # visual check of the body-plan samplers
node ai.js render anglerfish --out a.png           # 4-view render of a definition id / definition / spec / eval/real item
node ai.js check eval/real/anglerfish.json         # does it generate? (ok, problems, stats)
node ai.js dataset --n 8000 --images 2000 --out data/v1   # build the dataset (--dry-run shows stats only)
node ai.js baseline --mode keyword --ref data/v1/test.jsonl --out preds.jsonl
node ai.js eval --pred preds.jsonl --ref data/v1/test.jsonl --out report.json
node ai.js score pred_spec.json ref_spec.json      # score a single pair
npm test
```

## Progress dashboard

```bash
node dashboard.js            # http://localhost:5179 (Claude Code launch config "model-ai-dashboard")
node ai.js progress v2       # the same data as JSON
node ai.js watch v2          # live: one JSON line per training step (ends when the run finishes)
```

Live tracking: `train_sft.py` logs every step and rewrites `runs/<run>/live.json` (step, loss, grad norm, lr, sec/step, ETA, VRAM).
The dashboard gets it pushed through `/api/stream` (server-sent events, checked every second), so progress, the loss curve (raw per step + moving average), the grad-norm chart and the "N s ago" clock move with each step.

Shows progress, time left, estimated finish time, the loss curve, GPU usage and VRAM, evaluation reports (with the phase-2 pass/fail check) and the log tail, refreshed every 10 s.
The top also has a pipeline diagram with the current stage highlighted, and a gallery comparing prompts, the AI's output and the reference model as rendered images.
`/model` is a neuron-diagram view of the base model (36 layers, LoRA positions) animating the forward pass, the loss and backpropagation into LoRA, or token-by-token generation, plus a Netron-style block diagram of one decoder layer with real dimensions and parameter counts (from `runs/<run>/model_config.json`, written by train_sft.py).
It reads the WSL run directories from `\\wsl.localhost\Ubuntu-24.04\home\<user>\model-ai\runs` (override with `MODEL_AI_RUNS` / `MODEL_AI_DISTRO`). Read-only.

## Generating while training (CPU, llama.cpp)

```bash
node ai.js gen "a glowing blue jellyfish with long tentacles" [--run v2]   # or the dashboard page /generate
```

The GPU is fully used by training, so generation runs on the CPU: `runtime/llama/llama-server.exe` (llama.cpp b11254, win-cpu-x64, 8 threads, port 5180)
with the base `runtime/models/qwen2.5-coder-3b-instruct-q4_k_m.gguf` plus the newest LoRA of the run (the final `adapter/`, otherwise the highest `checkpoints/checkpoint-N`, otherwise no LoRA).
A new checkpoint is converted to GGUF in WSL (`convert_lora_to_gguf.py` against the weights-free `~/model-ai/lora-base` made by `train/lora_base_config.py`, because the converter cannot read the bitsandbytes base config) and the server restarts with it.
Output runs in JSON mode; invalid specs are retried twice with temperature 0.4. Results go to `runtime/generated/<id>/` (spec.json, preview.png, .bbmodel / .png / Java when valid).
`runtime/` (downloads, converted LoRAs, results) is not committed.

The progress page also has a flowchart of the training loop and the generation path, highlighting the node that is running now.

## Training (WSL2, phase 2+)

```bash
wsl -d Ubuntu-24.04 -- bash /mnt/f/Java/Abyssia/tools/model-ai/train/setup_wsl.sh      # once (no sudo needed)
wsl -d Ubuntu-24.04 -- bash /mnt/f/Java/Abyssia/tools/model-ai/train/bg.sh ~/model-ai/runs/v1/train.log train_sft.py --data ../data/v1/sft --out ~/model-ai/runs/v1 --batch 2 --accum 8
wsl -d Ubuntu-24.04 -- bash /mnt/f/Java/Abyssia/tools/model-ai/train/run.sh predict.py --adapter ~/model-ai/runs/v1/adapter --data ../data/v1/sft/test.jsonl --out ../data/v1/pred_v1_test.jsonl --limit 400
node ai.js eval --pred data/v1/pred_v1_test.jsonl --ref data/v1/test.jsonl --subset --out data/v1/report_v1_test.json
```

- **When every `wsl.exe` connection closes, the whole Ubuntu instance stops and training with it** (also for nohup jobs and systemd services). Keep the `wsl.exe` that runs `bg.sh` open while training. To keep the distro running anyway, set `[general]` `instanceIdleTimeout=-1` in `%USERPROFILE%\.wslconfig`.
- On 8 GB, Windows itself uses 1–3 GB of VRAM (browser etc.), so training uses batch 2 × accumulation 8 (batch 4 ran out of memory).
- The setup pitfalls (Python.h, no gcc, lld `-l:`) are handled by `setup_wsl.sh`.

## Data (`data/v1/`, not committed; regenerate with the command above)

- `train/val/test.jsonl`: records. `task` is `text2spec` or `edit`; each record has `prompt`, `target_spec`, and, for edits only, `input_spec` and `target_patch`.
- `real.jsonl`: held-out evaluation set built from the hand-written [eval/real/](eval/real) (`parts` versions of the 21 existing Abyssia creatures × 2 English + 2 Japanese captions). It is not in the synthetic distribution, so it measures generalisation.
- `sft/*.jsonl`: `{id, messages}` for training. The prompts are defined in [lib/prompts.js](lib/prompts.js); **if you change them, bump `PROMPT_VERSION`** (trained adapters become incompatible).
- `images/`: 4-view renders for phase 5.

Generation: body-plan samplers ([lib/archetypes](lib/archetypes); 21 kinds including fish, jellyfish, crabs, four-legged animals, insects, plants…) → the generator's validator → templated English/Japanese captions ([lib/captions.js](lib/captions.js)) → edit instructions from mutations ([lib/mutate.js](lib/mutate.js); resize, length, recolour, glow, count, remove, add, curl, pattern, scale, and two-step combinations).

## Output formats

- **spec**: `{"orientation", "scale"?, "texture"?, "parts": [...]}`. Key order and rounding are fixed ([lib/spec.js](lib/spec.js)), one part per line.
- **Edit patch**: RFC 6902 `add | remove | replace`, but `/parts/<name>` selects a part by name. `/parts/-` appends a part, and `remove` also removes its descendants ([lib/patch.js](lib/patch.js)).

## Evaluation ([lib/metrics.js](lib/metrics.js))

| Metric | Meaning |
|---|---|
| `valid` | Accepted by the generator and validator |
| `iou` | Silhouette IoU of the front, right and top views in a shared frame (size and position count) |
| `shape_iou` | Silhouette IoU with each model fitted to its own frame (shape only) |
| `size` / `color` / `parts` | Bounding-box ratio / colour agreement over overlapping pixels / F1 of part names |
| `score` | 0.35·iou + 0.25·shape_iou + 0.15·size + 0.15·color + 0.1·parts (0 when invalid) |
| `gain` (edits) | (score − score(input)) / (1 − score(input)); a no-op scores 0, an exact match 1 |

Baselines ([eval/baselines/](eval/baselines), v1):

| Split | Predictor | text2spec score | edit gain |
|---|---|---|---|
| test | empty (one box) | 0.335 | 0 |
| test | keyword (fixed sample by the kind word) | 0.511 | 0 |
| real | keyword | 0.447 | – |

Phase 2 target: text2spec **valid ≥ 0.9** and a score clearly above keyword on both test and real. Phase 3 target: **apply ≥ 0.85**, touched ≥ 0.85, gain > 0.6.

## Renderer ([lib/render.js](lib/render.js))

A software rasteriser that doesn't need WebGL. It reproduces the GUI preview (`frontend/preview/model_mesh.js`): bone/cube placement, the Box-UV vertex order, and Blockbench viewport shading plus the glow layer. Views: `front` (the -Z face), `right`, `top` (front at the top of the image), `back`, `left`, `iso`.

## Adding a body plan

Add a function to `lib/archetypes/*.js` that returns `{spec, facts}` (facts = `kind` / `traits` / `colors`, in English and Japanese), register it with a weight in `index.js`, then check it with `node ai.js sample <id> --n 4 --out x.png` and `npm test`.
