"""GB01 guide page art: ChatGPT sheet (4x3 landscape panels, white gutters) -> 12 70x50 RGBA illustrations.

Names (reading order): intro abyss biomes survival resources flora creatures diving base industry waypoints exploration.
Writes gui/guide/pages/<name>.png and copies into tools/texture_locks.
Run:  python tools/guide_pages.py [--sheet PATH] [--trim N] [--colors N] [--dry-run]
"""
import argparse
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/abyssia/textures"
LOCKS = ROOT / "tools/texture_locks/assets/textures"
NAMES = "intro abyss biomes survival resources flora creatures diving base industry waypoints exploration".split()
W, H = 70, 50


def spans(mask, n):
    """Return n (start, stop) runs of True in a 1D mask (largest n runs, in order)."""
    runs, s = [], None
    for i, v in enumerate(list(mask) + [False]):
        if v and s is None:
            s = i
        elif not v and s is not None:
            runs.append((s, i)); s = None
    runs = sorted(sorted(runs, key=lambda r: r[0] - r[1])[:n])
    if len(runs) != n:
        raise SystemExit(f"found {len(runs)} runs, expected {n}")
    return runs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", default=str(ROOT / "inbox/textures/sheets/GB01-pages.png"))
    ap.add_argument("--trim", type=int, default=2, help="px trimmed from each panel edge before crop")
    ap.add_argument("--colors", type=int, default=0, help="palette quantize (0 = off)")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    im = Image.open(a.sheet).convert("RGB")
    rgb = np.asarray(im).astype(int)
    content = (rgb < 240).any(axis=2)  # non-white
    cols = spans(content.mean(axis=0) > 0.05, 4)
    rows = spans(content.mean(axis=1) > 0.05, 3)
    i = 0
    for (y0, y1) in rows:
        for (x0, x1) in cols:
            # refine the panel's own bbox within its cell
            sub = content[y0:y1, x0:x1]
            ys, xs = np.where(sub)
            bx0, bx1, by0, by1 = x0 + xs.min(), x0 + xs.max() + 1, y0 + ys.min(), y0 + ys.max() + 1
            bx0 += a.trim; by0 += a.trim; bx1 -= a.trim; by1 -= a.trim
            w, h = bx1 - bx0, by1 - by0
            if w * H > h * W:  # too wide -> crop width
                nw = h * W // H
                bx0 += (w - nw) // 2; bx1 = bx0 + nw
            else:
                nh = w * H // W
                by0 += (h - nh) // 2; by1 = by0 + nh
            name = NAMES[i]; i += 1
            print(name, "bbox", (bx0, by0, bx1, by1))
            if a.dry_run:
                continue
            t = im.crop((bx0, by0, bx1, by1)).resize((W, H), Image.BOX)
            if a.colors:
                t = t.quantize(a.colors, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")
            t = t.convert("RGBA")
            for base in (TEX, LOCKS):
                p = base / "gui/guide/pages" / (name + ".png")
                p.parent.mkdir(parents=True, exist_ok=True)
                t.save(p)


if __name__ == "__main__":
    main()
