"""Split a ChatGPT item sheet (icons on flat magenta) into 16x16 transparent item textures.

    python tools/import_item_sheet.py SHEET.png --ids id1,id2,... [--out DIR] [--colors 14] [--dry-run]

Icons are found as connected non-magenta/non-white blobs, ordered row by row (left to right), and matched to --ids in
that order. Each icon is fitted (aspect kept) into 16x16, alpha thresholded, colours quantized. Needs Pillow, numpy, scipy.
A sheet image may contain several panels; blobs are simply read in row order per panel (--panels N splits by white gutters).
"""
from __future__ import annotations
import argparse, json, os
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(PROJECT, "src", "main", "resources", "assets", "abyssia", "textures", "item")


def blobs(rgb: np.ndarray, x0: int, x1: int, min_area: int = 800):
    a = rgb[:, x0:x1].astype(int)
    mag = (a[..., 0] > 180) & (a[..., 1] < 110) & (a[..., 2] > 180)
    fg = ~mag & ~(a.min(axis=2) > 225)
    lab, n = ndi.label(ndi.binary_dilation(fg, iterations=1))
    out = []
    for i, sl in enumerate(ndi.find_objects(lab), 1):
        h, w = sl[0].stop - sl[0].start, sl[1].stop - sl[1].start
        if h > 0.8 * a.shape[0] or w > 0.8 * a.shape[1] or (fg & (lab == i)).sum() < min_area:
            continue
        m = (lab == i) & fg
        out.append((sl, m))
    return out


def order(items):
    items = sorted(items, key=lambda t: (t[0][0].start + t[0][0].stop) / 2)
    rows, cur, last = [], [], None
    for it in items:
        cy = (it[0][0].start + it[0][0].stop) / 2
        if last is not None and cy - last > 70:
            rows.append(cur); cur = []
        cur.append(it); last = cy
    rows.append(cur)
    return [it for r in rows for it in sorted(r, key=lambda t: t[0][1].start)]


def to_tile(rgb, sl, m, colors):
    m = ndi.binary_erosion(m, iterations=1)[sl]
    crop = rgb[sl].astype(float)
    ys, xs = np.nonzero(m)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    crop, m = crop[y0:y1, x0:x1], m[y0:y1, x0:x1].astype(float)
    s = max(crop.shape[:2]); scale = 16 / s
    h, w = max(1, round(crop.shape[0] * scale)), max(1, round(crop.shape[1] * scale))
    pm = Image.fromarray(np.clip(crop * m[..., None], 0, 255).astype(np.uint8)).resize((w, h), Image.BOX)
    al = Image.fromarray((m * 255).astype(np.uint8)).resize((w, h), Image.BOX)
    p, al = np.asarray(pm).astype(float), np.asarray(al).astype(float) / 255
    col = np.where(al[..., None] > 0, p / np.maximum(al[..., None], 1e-6), 0)
    solid = al >= 0.5
    img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8))
    if colors:
        img = img.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    arr = np.dstack([np.asarray(img), (solid * 255).astype(np.uint8)])
    tile = np.zeros((16, 16, 4), np.uint8)
    oy, ox = (16 - h) // 2, (16 - w) // 2
    tile[oy:oy + h, ox:ox + w] = arr
    return Image.fromarray(tile, "RGBA")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("sheet"); ap.add_argument("--ids", required=True)
    ap.add_argument("--x0", type=int, default=0); ap.add_argument("--x1", type=int, default=0)
    ap.add_argument("--out", default=OUT); ap.add_argument("--colors", type=int, default=14)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    rgb = np.asarray(Image.open(a.sheet).convert("RGB"))
    x1 = a.x1 or rgb.shape[1]
    ids = a.ids.split(",")
    found = order(blobs(rgb, a.x0, x1))
    if len(found) != len(ids):
        print(json.dumps({"error": f"found {len(found)} icons, expected {len(ids)}"})); return 1
    for bid, (sl, m) in zip(ids, found):
        sl = (slice(sl[0].start, sl[0].stop), slice(sl[1].start + a.x0, sl[1].stop + a.x0))
        full = np.zeros(rgb.shape[:2], bool); full[:, a.x0:x1] = m[:, :] if m.shape[1] == x1 - a.x0 else m
        tile = to_tile(rgb, sl, np.pad(m, ((0, 0), (a.x0, rgb.shape[1] - x1)))[...], a.colors)
        if not a.dry_run:
            os.makedirs(a.out, exist_ok=True); tile.save(os.path.join(a.out, bid + ".png"))
    print(json.dumps({"written": ids, "dry_run": a.dry_run}, ensure_ascii=False)); return 0


if __name__ == "__main__":
    raise SystemExit(main())
