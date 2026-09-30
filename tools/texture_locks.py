"""Approved assets frozen against regeneration, and the texture write policy every generator follows.

Files under tools/texture_locks/assets/ mirror src/main/resources/assets/abyssia/ and are copied over whatever the
generators wrote; paths in DELETE are generator outputs the locked versions do not use (e.g. a glow overlay the
approved model has no layer for). forge_textures.run() and gen_deep_assets.py apply the locks at the end, so no rerun
can bring an unapproved texture back.

To change a locked texture on purpose: regenerate it, check it, then copy the new file into the lock folder (or
delete the lock file to hand the texture back to its generator).

    python tools/texture_locks.py           # re-apply the locks
    python tools/texture_locks.py --check   # list files that differ from their lock

Write policy (``wants`` / ``save``): the committed textures are the source of truth.  Generators ask ``wants(path)``
before drawing a texture and write through ``save``; the textures folders are never wiped.  The mode is chosen with
``--textures`` on gen_deep_assets.py / forge_textures.py (``set_mode`` from code):

    missing-only  (default) draw a texture only when its PNG does not exist yet; locked textures are never drawn
    locked-only   redraw every texture except the locked ones
    all           old behaviour: redraw everything, locked ones too (the locks are re-applied afterwards)

Derived textures (stone family polished / bricks / cracked bricks / chiseled, crust bricks, polished crusts and
every *_glow overlay) are never written by the generators in any mode: tools/derive_textures.py rebuilds them from
the current base textures (it skips locked files).  Both generators run it at the end; on its own:

    python tools/derive_textures.py            # rebuild the derived textures, JSON summary on stdout
    python tools/derive_textures.py --dry-run  # only report what would change
    python tools/check_textures.py             # list model texture references without a PNG
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

MODES = ("missing-only", "locked-only", "all")
MODE = "missing-only"


def locked() -> list[str]:
    out = []
    for folder, _, files in os.walk(LOCKS):
        for f in files:
            out.append(os.path.relpath(os.path.join(folder, f), LOCKS).replace(os.sep, "/"))
    return sorted(out)


# ================================================================ write policy

def set_mode(mode: str) -> None:
    global MODE
    if mode not in MODES:
        raise SystemExit(f"--textures must be one of {', '.join(MODES)} (got {mode!r})")
    MODE = mode


def mode_from_argv(argv: list[str] | None = None) -> str:
    """Pick ``--textures MODE`` / ``--textures=MODE`` out of a hand-parsed command line (gen_deep_assets.py)."""
    argv = sys.argv[1:] if argv is None else argv
    for i, a in enumerate(argv):
        if a == "--textures" and i + 1 < len(argv):
            set_mode(argv[i + 1])
        elif a.startswith("--textures="):
            set_mode(a.split("=", 1)[1])
    return MODE


def asset_rel(path: str) -> str | None:
    """``.../assets/abyssia/textures/block/x.png`` -> ``textures/block/x.png`` (None outside the mod's assets)."""
    base = os.path.normpath(os.path.abspath(ASSETS))
    p = os.path.normpath(os.path.abspath(path))
    if not p.startswith(base + os.sep):
        return None
    return p[len(base) + 1:].replace(os.sep, "/")


def is_locked(rel: str) -> bool:
    """``rel``: a path relative to the assets folder, e.g. ``textures/block/x.png``."""
    return os.path.exists(os.path.join(LOCKS, rel))


def wants(path: str) -> bool:
    """Should a generator draw and write this texture now (see the module docstring)?"""
    rel = asset_rel(path)
    if rel is None or not rel.startswith("textures/"):
        return True                                   # previews, --out folders, non-texture files
    import derive_textures
    if derive_textures.is_derived(rel):
        return False                                  # derive_textures.py owns it
    if MODE == "all":
        return True
    if is_locked(rel):
        return False
    return MODE == "locked-only" or not os.path.exists(path)


def save(img, path: str) -> bool:
    """Write a PIL image, pixelart Canvas or RGBA array if ``wants(path)``; returns whether it was written."""
    if not wants(path):
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if hasattr(img, "shape"):
        from PIL import Image
        img = Image.fromarray(img, "RGBA")
    img.save(path)
    return True


# ================================================================ locks

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
