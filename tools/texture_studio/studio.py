"""Abyssia Texture Studio: layered, vanilla-based texture editor + CLI.

    python tools/texture_studio/studio.py                 # GUI
    python tools/texture_studio/studio.py list [--json] [--status]
    python tools/texture_studio/studio.py check           # managed textures whose PNG differs from the spec
    python tools/texture_studio/studio.py export [NAME... | --all] [--dry-run]
    python tools/texture_studio/studio.py import NAME...  # wrap existing PNGs as editable specs (paint layer)
    python tools/texture_studio/studio.py render NAME --out out.png [--scale 16]
    python tools/texture_studio/studio.py hue NAME... --hue 30 [--sat 0 --val 0]   # recolour specs in place

NAME is ``block/abyssal_rock`` or ``item/cobalt_ingot``.  Specs live in tools/texture_studio/specs/.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import texture_engine as E  # noqa: E402


def _keys(names, all_):
    if all_ or not names:
        return E.managed_keys()
    return [n if "/" in n else f"block/{n}" for n in names]


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd")
    p = sub.add_parser("list"); p.add_argument("--json", action="store_true"); p.add_argument("--status", action="store_true")
    sub.add_parser("check")
    p = sub.add_parser("export"); p.add_argument("names", nargs="*"); p.add_argument("--all", action="store_true"); p.add_argument("--dry-run", action="store_true")
    p = sub.add_parser("import"); p.add_argument("names", nargs="+")
    p = sub.add_parser("render"); p.add_argument("name"); p.add_argument("--out", required=True); p.add_argument("--scale", type=int, default=16)
    p = sub.add_parser("hue"); p.add_argument("names", nargs="+"); p.add_argument("--hue", type=float, default=0); p.add_argument("--sat", type=float, default=0); p.add_argument("--val", type=float, default=0)
    a = ap.parse_args(argv)
    if not a.cmd:
        from gui import main as gui_main
        return gui_main(sys.argv[:1])

    src = E.Sources()
    if a.cmd == "list":
        managed = set(E.managed_keys())
        rows = []
        for k in E.KINDS:
            for n in src.mod_names(k):
                rows.append({"key": n, "managed": n in managed, "locked": E.is_locked(n)})
        if a.json:
            print(json.dumps(rows, indent=1))
        else:
            for r in rows:
                print(f"{r['key']:44s} {'managed' if r['managed'] else '-':8s} {'locked' if r['locked'] else ''}")
            print(f"{len(rows)} textures, {len(managed)} managed")
    elif a.cmd == "check":
        bad = [k for k in E.managed_keys() if (s := E.load_spec(k)) and E.is_outdated(k, s, src)]
        print("\n".join(bad) if bad else "All managed textures are up to date.")
        return 1 if bad else 0
    elif a.cmd == "export":
        n = 0
        for k in _keys(a.names, a.all):
            s = E.load_spec(k)
            if s is None:
                print(f"no spec: {k}", file=sys.stderr); continue
            if E.export(k, s, src, dry_run=a.dry_run):
                n += 1; print(("would write " if a.dry_run else "wrote ") + k)
        print(f"{n} changed")
    elif a.cmd == "import":
        for k in _keys(a.names, False):
            s = E.spec_from_png(k)
            if s is None:
                print(f"no png: {k}", file=sys.stderr); continue
            if E.load_spec(k) is None:
                E.save_spec(k, s); print("imported", k)
    elif a.cmd == "render":
        from PIL import Image
        k = a.name if "/" in a.name else f"block/{a.name}"
        s = E.load_spec(k)
        if s is None:
            print("no spec", file=sys.stderr); return 1
        arr = E.render(s, src, k)
        Image.fromarray(arr, "RGBA").resize((arr.shape[1] * a.scale,) * 2, Image.NEAREST).save(a.out)
    elif a.cmd == "hue":
        for k in _keys(a.names, False):
            s = E.load_spec(k) or E.spec_from_png(k)
            if s is None:
                continue
            for L in s["layers"]:
                E.shift_layer_hsv(L, a.hue, a.sat, a.val)
            E.save_spec(k, s); E.export(k, s, src); print("recoloured", k)
    return 0


if __name__ == "__main__":
    sys.exit(main())
