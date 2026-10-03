"""Give the BT01 glass-like textures their alpha after a ChatGPT sheet import (sheets come in opaque).
Base alpha per texture, brighter pixels (highlights, ripples) more opaque. Writes the texture and its texture_locks copy.
  python tools/bt01/translucent.py [--dry-run]
"""
import shutil, sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/abyssia/textures/block"
LOCK = ROOT / "tools/texture_locks/assets/textures/block"
# name: (alpha of the darkest pixel, alpha of the brightest)
TARGETS = {"aquarium_glass": (70, 200), "habitat_membrane": (60, 170), "bio_tank": (150, 225)}


def main(argv):
    dry = "--dry-run" in argv
    for name, (lo, hi) in TARGETS.items():
        path = TEX / f"{name}.png"
        im = Image.open(path).convert("RGBA")
        lum = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b, _ in im.getdata()]
        mn, mx = min(lum), max(lum)
        out = [(r, g, b, round(lo + (hi - lo) * ((l - mn) / (mx - mn) if mx > mn else 0)))
               for (r, g, b, _), l in zip(im.getdata(), lum)]
        im.putdata(out)
        print(("would write " if dry else "wrote ") + str(path.relative_to(ROOT)))
        if not dry:
            im.save(path)
            LOCK.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, LOCK / path.name)


if __name__ == "__main__":
    main(sys.argv[1:])
