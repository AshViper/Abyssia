"""Lists model texture references that have no PNG (the purple-black missing texture in game).

Reads every JSON under assets/abyssia/models/ and checks each ``abyssia:`` (or namespace-less) texture reference
against assets/abyssia/textures/<path>.png.  Vanilla (``minecraft:``) references and ``#variables`` are skipped.
Read-only; exit code 1 when something is missing.

    python tools/check_textures.py            # JSON: {"models": n, "refs": n, "missing": [...]}
"""
from __future__ import annotations

import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia"))


def check() -> dict:
    models = sorted(glob.glob(os.path.join(ASSETS, "models", "**", "*.json"), recursive=True))
    missing, refs, invalid = [], 0, []
    for path in models:
        rel = os.path.relpath(path, ASSETS).replace(os.sep, "/")
        try:
            with open(path, encoding="utf-8") as f:
                model = json.load(f)
        except (OSError, ValueError) as e:
            invalid.append({"model": rel, "error": str(e)})
            continue
        for key, ref in (model.get("textures") or {}).items():
            if not isinstance(ref, str) or ref.startswith("#"):
                continue
            ns, _, tex = ref.rpartition(":")
            if ns not in ("", "abyssia"):
                continue
            refs += 1
            if not os.path.isfile(os.path.join(ASSETS, "textures", *tex.split("/")) + ".png"):
                missing.append({"model": rel, "key": key, "ref": ref})
    return {"models": len(models), "refs": refs, "missing": missing, "invalid_json": invalid}


if __name__ == "__main__":
    report = check()
    print(json.dumps(report, indent=2))
    sys.exit(1 if report["missing"] or report["invalid_json"] else 0)
