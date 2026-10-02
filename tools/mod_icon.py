#!/usr/bin/env python3
"""Abyssia mod icon (M01): renders the pixel-art data in tools/mod_icon_data/M01.json.

The design comes from ChatGPT (inbox/designs/M01-design.png). It was traced into a 48x48
palette grid, and that grid is the source of truth: edit the JSON rows to change a pixel,
then rerun. Never edit the PNG outputs. Scaling is nearest-neighbour only, so pixels stay sharp.

  python tools/mod_icon.py   -> src/main/resources/abyssia_logo.png (x8), pack.png (x4)
"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
DATA = ROOT / "tools/mod_icon_data/M01.json"


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def draw():
    d = json.loads(DATA.read_text(encoding="utf-8"))
    rows, pal = d["rows"], {k: hexrgb(v) for k, v in d["palette"].items()}
    n = len(rows)
    bad = [y for y, r in enumerate(rows) if len(r) != n or set(r) - pal.keys()]
    if bad:
        raise SystemExit(f"bad rows (wrong length or unknown letter): {bad}")
    img = Image.new("RGB", (n, n))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            img.putpixel((x, y), pal[c])
    return img


def main():
    img = draw()
    n = img.width
    img.resize((n * 8, n * 8), Image.NEAREST).save(RES / "abyssia_logo.png")
    img.resize((n * 4, n * 4), Image.NEAREST).save(RES / "pack.png")
    print(f"wrote abyssia_logo.png ({n * 8}px), pack.png ({n * 4}px) from {n}x{n} data")


if __name__ == "__main__":
    main()
