"""Import ChatGPT-made block images as 16x16 pixel-art tiles.

    python tools/import_chatgpt_textures.py [ids...] [--dry-run] [--colors 16] [--no-tile] [--src DIR]

Reads inbox/textures/<block_id>.png (any size, RGB/RGBA), centre-crops to a square, makes it tile-friendly
(offset cross-blend so opposite edges match), downsamples to 16x16 and quantizes to a small palette, then writes
src/main/resources/assets/abyssia/textures/block/<id>.png. Textures frozen in tools/texture_locks are skipped.
With no ids, every png in the source folder is imported. Prints a JSON summary. Needs Pillow + numpy.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(ROOT)
SRC = os.path.join(PROJECT, "inbox", "textures")
OUT = os.path.join(PROJECT, "src", "main", "resources", "assets", "abyssia", "textures", "block")
LOCKS = os.path.join(ROOT, "texture_locks", "assets", "textures", "block")
SIZE = 16


def make_tileable(img: np.ndarray, band: float = 0.25) -> np.ndarray:
    """Cross-fade the image with a half-shifted copy toward the edges so left/right and top/bottom meet."""
    h, w = img.shape[:2]
    shifted = np.roll(np.roll(img, h // 2, axis=0), w // 2, axis=1)
    ys = np.abs(np.linspace(-1, 1, h))[:, None]
    xs = np.abs(np.linspace(-1, 1, w))[None, :]
    wgt = np.clip((np.maximum(ys, xs) - (1 - 2 * band)) / (2 * band), 0, 1)[..., None] ** 1.5
    return img * (1 - wgt) + shifted * wgt


def convert(path: str, colors: int, tile: bool) -> Image.Image:
    im = Image.open(path)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        bg = Image.new("RGBA", im.size, (0, 0, 0, 255))
        im = Image.alpha_composite(bg, im)
    im = im.convert("RGB")
    w, h = im.size
    s = min(w, h)
    im = im.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s))
    if tile:
        arr = make_tileable(np.asarray(im, dtype=np.float32))
        im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    im = im.resize((SIZE, SIZE), Image.BOX)   # area average keeps colour without aliasing
    if colors and colors > 0:
        im = im.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    return im


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("ids", nargs="*", help="block ids (default: all pngs in the source folder)")
    ap.add_argument("--src", default=SRC)
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--colors", type=int, default=16, help="palette size, 0 = no quantize (default 16)")
    ap.add_argument("--no-tile", action="store_true", help="skip the edge-blend for seamless tiling")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    ids = a.ids or sorted(f[:-4] for f in os.listdir(a.src) if f.lower().endswith(".png")) if os.path.isdir(a.src) else a.ids
    result = {"dry_run": a.dry_run, "written": [], "skipped_locked": [], "missing": [], "errors": {}}
    for bid in ids:
        src = os.path.join(a.src, bid + ".png")
        if os.path.exists(os.path.join(LOCKS, bid + ".png")):
            result["skipped_locked"].append(bid)
            continue
        if not os.path.exists(src):
            result["missing"].append(bid)
            continue
        try:
            tile = convert(src, a.colors, not a.no_tile)
            if not a.dry_run:
                os.makedirs(a.out, exist_ok=True)
                tile.save(os.path.join(a.out, bid + ".png"))
            result["written"].append(bid)
        except Exception as e:  # noqa: BLE001 - report per file
            result["errors"][bid] = str(e)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if result["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
