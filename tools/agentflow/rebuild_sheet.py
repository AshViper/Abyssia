#!/usr/bin/env python3
"""Rebuild a sheet image from icons extracted in the ChatGPT tab by tools/agentflow/chatgpt_extract.js.

    python tools/agentflow/rebuild_sheet.py LINES.jsonl inbox/textures/sheets/<preset>.png --cols N

Each icon (32 px, palette + index string in two halves) is drawn x8 on flat magenta with 64 px gutters, in reading
order, so `sheets.py import auto --preset <preset>` detects and box-averages it like an original sheet.
Writes only the output PNG.
"""
import argparse
import json

from PIL import Image

AL = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
SCALE, GAP, SIZE = 8, 64, 32


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("lines"); ap.add_argument("out"); ap.add_argument("--cols", type=int, required=True)
    a = ap.parse_args()
    tiles = {}
    for line in open(a.lines, encoding="utf-8"):
        if not line.strip():
            continue
        p = json.loads(line)
        t = tiles.setdefault(p["t"], {"w": p["w"], "h": p["h"], "idx": ["", ""]})
        if p.get("pal"):
            t["pal"] = [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) for c in p["pal"].split(",")]
        t["idx"][p["half"]] = p["idx"]
    cell = SIZE * SCALE
    rows = -(-len(tiles) // a.cols)
    sheet = Image.new("RGB", (a.cols * (cell + GAP) + GAP, rows * (cell + GAP) + GAP), (255, 0, 255))
    for k in sorted(tiles):
        t = tiles[k]
        idx = t["idx"][0] + t["idx"][1]
        if len(idx) != t["w"] * t["h"]:
            raise SystemExit(f"icon {k}: {len(idx)} pixels, expected {t['w'] * t['h']} (missing half?)")
        im = Image.new("RGB", (t["w"], t["h"]), (255, 0, 255))
        for i, ch in enumerate(idx):
            if ch != ".":
                im.putpixel((i % t["w"], i // t["w"]), t["pal"][AL.index(ch)])
        im = im.resize((t["w"] * SCALE, t["h"] * SCALE), Image.NEAREST)
        x = GAP + (k % a.cols) * (cell + GAP) + (cell - im.width) // 2
        y = GAP + (k // a.cols) * (cell + GAP) + (cell - im.height) // 2
        sheet.paste(im, (x, y))
    sheet.save(a.out)
    print(json.dumps({"out": a.out, "size": sheet.size, "icons": len(tiles)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
