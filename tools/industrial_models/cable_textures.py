"""Assembles the energy cable textures from the ChatGPT pixel parts in cable_parts.json.

    python tools/industrial_models/cable_textures.py

The cable models take their UVs from the element positions, so a 16x16 cable texture is a cross: the horizontal band
(rows band_from..+n) is what the side faces show, the vertical band (same columns) what the top/bottom faces show (the
strip transposed), the centre square the hub on the core faces.  The reinforced cable's ring swatch sits at 0..3 / 0..3
(the band element maps its faces there).  Everything else is transparent.  Writes the block textures and their locks.
"""
import json
import os
import shutil

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia", "textures", "block")
LOCK = os.path.join(ROOT, "tools", "texture_locks", "assets", "textures", "block")


def rgba(hexcode):
    return tuple(int(hexcode[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def build(spec, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    start, strip = spec["band_from"], spec["strip"]
    for i, row in enumerate(strip):
        for j, ch in enumerate(row):
            if ch != ".":
                px[j, start + i] = rgba(palette[ch])   # horizontal band
                px[start + i, j] = rgba(palette[ch])   # vertical band (transposed)
    for i, row in enumerate(spec["hub"]):
        for j, ch in enumerate(row):
            if ch != ".":
                px[start + j, start + i] = rgba(palette[ch])
    for i, row in enumerate(spec.get("ring", [])):
        for j, ch in enumerate(row):
            if ch != ".":
                px[j, i] = rgba(palette[ch])
    return img


def main():
    with open(os.path.join(HERE, "cable_parts.json"), encoding="utf-8") as f:
        parts = json.load(f)
    for name in ("energy_cable", "reinforced_energy_cable"):
        out = os.path.join(TEX, name + ".png")
        build(parts[name], parts["palette"]).save(out)
        shutil.copyfile(out, os.path.join(LOCK, name + ".png"))
        print("wrote", name)


if __name__ == "__main__":
    main()
