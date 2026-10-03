"""Rebuild the 4-frame (16x64) swaying strips of the animated plants from a single 16x16 still.

After a sheet import the animated plants (cave_grass, cave_kelp, giant_cave_kelp, hanging_kelp and their
_top/_tip parts) are plain 16x16 files.  This takes frame 0 (the top 16x16 of the file), shears rows sideways with
sin(pi*y/15), which is 0 at the top and bottom edge so stacked blocks still join, over phases 0, 90, 180, 270 deg, writes
the strip to the asset and to tools/texture_locks (the .png.mcmeta files are left untouched).

    python tools/plant_anim.py [--dry-run] [--amp 1.5] [ids...]
"""
from __future__ import annotations

import argparse
import math
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/abyssia/textures/block"
LOCK = ROOT / "tools/texture_locks/assets/textures/block"
IDS = ["cave_grass", "cave_grass_top", "cave_kelp", "cave_kelp_top", "giant_cave_kelp", "giant_cave_kelp_top",
       "hanging_kelp", "hanging_kelp_tip"]


def frames(a: np.ndarray, amp: float) -> np.ndarray:
    out = []
    for s in (0.0, 1.0, 0.0, -1.0):
        f = np.zeros_like(a)
        for y in range(16):
            dx = int(round(amp * math.sin(math.pi * y / 15) * s))
            row = a[y]
            if dx > 0:
                f[y, dx:] = row[:16 - dx]
            elif dx < 0:
                f[y, :16 + dx] = row[-dx:]
            else:
                f[y] = row
        out.append(f)
    return np.concatenate(out, axis=0)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--amp", type=float, default=1.5)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    for i in a.ids or IDS:
        p = TEX / f"{i}.png"
        if not (TEX / f"{i}.png.mcmeta").is_file():
            print(f"{i}: no .mcmeta, skipped")
            continue
        with Image.open(p) as im:
            arr = np.asarray(im.convert("RGBA"))[:16]
        strip = frames(arr, a.amp)
        print(f"{i}: 16x16 -> 16x64")
        if a.dry_run:
            continue
        Image.fromarray(strip, "RGBA").save(p)
        LOCK.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, LOCK / f"{i}.png")


if __name__ == "__main__":
    main()
