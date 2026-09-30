"""Approved assets frozen against regeneration.

Files under tools/texture_locks/assets/ mirror src/main/resources/assets/abyssia/ and are copied over whatever the
generators wrote; paths in DELETE are generator outputs the locked versions do not use (e.g. a glow overlay the
approved model has no layer for). forge_textures.run() and gen_deep_assets.py apply the locks at the end, so no rerun
can bring an unapproved texture back.

To change a locked texture on purpose: regenerate it, check it, then copy the new file into the lock folder (or
delete the lock file to hand the texture back to its generator).

    python tools/texture_locks.py           # re-apply the locks
    python tools/texture_locks.py --check   # list files that differ from their lock
"""
from __future__ import annotations

import filecmp
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
LOCKS = os.path.join(ROOT, "texture_locks", "assets")
ASSETS = os.path.join(ROOT, "..", "src", "main", "resources", "assets", "abyssia")

# Rock textures approved on 2026-09-29 (the set in game before the 01:59 regeneration replaced them).
DELETE = ["textures/block/thermal_cave_rock_glow.png"]


def locked() -> list[str]:
    out = []
    for folder, _, files in os.walk(LOCKS):
        for f in files:
            out.append(os.path.relpath(os.path.join(folder, f), LOCKS).replace(os.sep, "/"))
    return sorted(out)


def differing() -> list[str]:
    diff = [rel for rel in locked() if not os.path.exists(os.path.join(ASSETS, rel))
            or not filecmp.cmp(os.path.join(LOCKS, rel), os.path.join(ASSETS, rel), shallow=False)]
    return diff + [rel for rel in DELETE if os.path.exists(os.path.join(ASSETS, rel))]


def apply(quiet: bool = False) -> int:
    changed = 0
    for rel in locked():
        src, dst = os.path.join(LOCKS, rel), os.path.join(ASSETS, rel)
        if os.path.exists(dst) and filecmp.cmp(src, dst, shallow=False):
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        changed += 1
    for rel in DELETE:
        path = os.path.join(ASSETS, rel)
        if os.path.exists(path):
            os.remove(path)
            changed += 1
    if not quiet:
        print(f"Texture locks: {len(locked())} locked files, {changed} restored")
    return changed


if __name__ == "__main__":
    if "--check" in sys.argv:
        diff = differing()
        print("\n".join(diff) if diff else "All locked files match.")
        sys.exit(1 if diff else 0)
    apply()
