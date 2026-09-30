#!/usr/bin/env python3
"""Write a 16x16 PNG from the compact form emitted by the ChatGPT page extractor.

  pixels_to_png.py <out.png> "<r g b, r g b, ...> | <16 rows of palette letters A.. joined by />"

Palette entries are 'r g b' separated by commas; each row char indexes the palette (A = first).
"""
import json, sys
from pathlib import Path
from PIL import Image


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: pixels_to_png.py <out.png> \"<palette> | <rows>\"")
    out, data = Path(sys.argv[1]), sys.argv[2]
    pal_s, rows_s = (s.strip() for s in data.split("|"))
    pal = [tuple(int(v) for v in c.split()) for c in pal_s.split(",")]
    rows = rows_s.split("/")
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        sys.exit("need 16 rows of 16 chars")
    img = Image.new("RGB", (16, 16))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            img.putpixel((x, y), pal[ord(ch) - 65])
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(json.dumps({"ok": True, "file": str(out), "colors": len(pal)}))


if __name__ == "__main__":
    main()
