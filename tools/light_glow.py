"""Generates textures/effect/light_glow.png: the round glow of spotlight lamps (client/light/SpotlightBeams).

A white middle (the beam cone samples UV 0.5,0.5 for a plain colour) fading smoothly to black at the rim. Drawn with
RenderType.eyes (additive), so black is see-through.

    python tools/light_glow.py [--size 32] [--dry-run]
"""
import argparse
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "abyssia", "textures", "effect", "light_glow.png")


def glow(size):
    img = Image.new("RGBA", (size, size))
    half = size / 2.0
    for y in range(size):
        for x in range(size):
            r = math.hypot(x + 0.5 - half, y + 0.5 - half) / half
            core = 1.0 if r < 0.12 else 0.0
            v = max(core, max(0.0, 1.0 - r) ** 2.2)
            c = round(255 * min(v, 1.0))
            img.putpixel((x, y), (c, c, c, 255))
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--size", type=int, default=32)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    img = glow(a.size)
    if a.dry_run:
        print(f"would write {os.path.normpath(OUT)} ({a.size}x{a.size})")
        return
    img.save(OUT)
    print(f"wrote {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
