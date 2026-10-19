"""AI-generated source clips for the machine running loops (via the local audio-studio MCP server).

    F:\\AI\\audio-studio-mcp\\.venv\\Scripts\\python.exe tools/ai_machine_sounds.py [ids...] [--seed N] [--dry-run]

For each machine it asks generate_audio (sfx) for 4 variations of 8 s, skips the first 1 s, and picks the most
stationary 4.5 s window (LOOP + XFADE; lowest std of 100 ms RMS in dB, mean RMS >= -40 dBFS). The window is saved as
tools/sound_sources/machine/<id>.flac (+ <id>.json sidecar). machine_sounds.py then folds it into the 4 s loop and is
the only writer of the .ogg files. Env overrides: AUDIO_STUDIO_EXE, AUDIO_STUDIO_FFMPEG, AUDIO_STUDIO_CACHE
(HF_HUB_CACHE), AUDIO_STUDIO_MODEL (default sm-sfx), AUDIO_STUDIO_OUT (temp dir for raw candidates).
"""
from __future__ import annotations

import argparse
import asyncio
import glob
import json
import os
import sys
import tempfile

import numpy as np
import soundfile as sf
from mcp import Client, StdioServerParameters

SR = 44100
WIN = 4.5
STEP = 0.25
SKIP = 1.0
DUR = 8
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "sound_sources", "machine")
HABITAT = "inside a sealed underwater metal habitat, muffled, close"
UNDERWATER = "underwater, muffled, close"

PROMPTS = {
    "crusher": ("industrial rock crusher machine",
                "continuous steady grinding and crunching of stone between steel jaws, constant rhythm, no start or stop",
                "heavy steel, gravel, rumbling motor", HABITAT),
    "furnace": ("industrial smelting furnace", "continuous steady roaring flame with soft crackle, constant intensity",
                "fire, firebrick, metal casing", HABITAT),
    "separator": ("centrifugal separator pump machine", "continuous rhythmic pumping and whirring, steady pulse",
                  "metal pump, liquid sloshing in pipes", HABITAT),
    "turbine": ("underwater current turbine generator", "continuous smooth spinning whir with low hum, constant speed",
                "metal blades, electric generator hum, water flow", UNDERWATER),
    "geothermal": ("geothermal power generator", "continuous deep rumble with hissing steam and bubbling, steady",
                   "pressurized steam, metal pipes, deep bass rumble", UNDERWATER),
    "biofuel": ("small biofuel engine generator", "continuous steady chugging engine idle, constant rhythm",
                "small combustion engine, metal, exhaust", HABITAT),
}


def find_ffmpeg():
    hits = glob.glob(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Microsoft", "WinGet", "Packages",
                                  "Gyan.FFmpeg*", "*", "bin", "ffmpeg.exe"))
    return hits[0] if hits else "ffmpeg"


def load_mono(path):
    x, sr = sf.read(path, dtype="float64", always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        n = int(round(len(x) * SR / sr))
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)
    return x


def rms_db(x):
    return 20 * np.log10(np.sqrt(np.mean(x * x)) + 1e-9)


def best_window(x):
    """(score, start_seconds) of the most stationary window, or None."""
    x = x[int(SKIP * SR):]
    w, blk = int(WIN * SR), int(0.1 * SR)
    best = None
    for s in range(0, len(x) - w + 1, int(STEP * SR)):
        seg = x[s:s + w]
        if rms_db(seg) < -40:
            continue
        env = np.array([rms_db(seg[i:i + blk]) for i in range(0, w - blk + 1, blk)])
        score = float(env.std())
        if best is None or score < best[0]:
            best = (score, SKIP + s / SR)
    return best


async def generate(ids, seed, out_dir):
    env = {**os.environ,
           "HF_HUB_CACHE": os.environ.get("AUDIO_STUDIO_CACHE", r"F:\AI\hf-cache\hub"),
           "AUDIO_STUDIO_SA3_MODEL": os.environ.get("AUDIO_STUDIO_MODEL", "sm-sfx"),
           "AUDIO_STUDIO_FFMPEG": os.environ.get("AUDIO_STUDIO_FFMPEG") or find_ffmpeg(),
           "AUDIO_STUDIO_OUTPUT_DIR": out_dir}
    exe = os.environ.get("AUDIO_STUDIO_EXE", r"F:\AI\audio-studio-mcp\.venv\Scripts\audio-studio-mcp.exe")
    os.makedirs(OUT, exist_ok=True)
    async with Client(StdioServerParameters(command=exe, args=[], env=env), read_timeout_seconds=900) as c:
        for k in ids:
            src, act, mat, space = PROMPTS[k]
            r = await c.call_tool("generate_audio", {
                "audio_type": "sfx", "sound_source": src, "sound_action": act, "sound_material": mat,
                "sound_space": space, "duration_seconds": DUR, "variations": 4, "seed": seed, "title": f"machine_{k}"})
            text = "".join(getattr(p, "text", "") for p in r.content)
            if r.is_error:
                sys.exit(f"generate_audio failed for {k}: {text}")
            cands = json.loads(text)["candidates"]
            best = None
            for i, cand in enumerate(cands):
                b = best_window(load_mono(cand["path"]))
                if b and (best is None or b[0] < best[0]):
                    best = (b[0], b[1], i, cand["path"])
            if best is None:
                sys.exit(f"{k}: no usable window")
            score, start, idx, path = best
            x = load_mono(path)
            a = int(start * SR)
            seg = x[a:a + int(WIN * SR)]
            sf.write(os.path.join(OUT, f"{k}.flac"), seg, SR, subtype="PCM_16")
            with open(os.path.join(OUT, f"{k}.json"), "w", encoding="utf-8", newline="\n") as f:
                json.dump({"sound_source": src, "sound_action": act, "sound_material": mat, "sound_space": space,
                           "duration_seconds": DUR, "variations": 4, "seed": seed, "variation": idx,
                           "window_start": start, "score": round(score, 3)}, f, indent=2, ensure_ascii=False)
                f.write("\n")
            print(f"{k}: variation {idx} start {start:.2f}s score {score:.2f} dB")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("ids", nargs="*", help="machine ids (default: all)")
    ap.add_argument("--seed", type=int, default=1234)
    ap.add_argument("--dry-run", action="store_true", help="print prompts only")
    a = ap.parse_args()
    ids = a.ids or list(PROMPTS)
    for k in ids:
        if k not in PROMPTS:
            sys.exit(f"unknown machine id {k}")
    if a.dry_run:
        for k in ids:
            print(k, " / ".join(PROMPTS[k]))
        return
    out_dir = os.environ.get("AUDIO_STUDIO_OUT") or tempfile.mkdtemp(prefix="abyssia-machine-")
    asyncio.run(generate(ids, a.seed, out_dir))


if __name__ == "__main__":
    main()
