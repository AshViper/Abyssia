#!/usr/bin/env python3
"""Generate 16x16 placeholder item textures for tools/material_spec.json items.

For every spec item with new==true and no textures/item/<id>.png, take the existing
texture named by texture.placeholder_from (fallback: similar existing item by kind),
remap its luminance onto the item's palette ramp (dark->light), keep the alpha
silhouette and add small accents (tier-colour corner pixel + item-specific marks) so
siblings differ. Never overwrites existing files. Idempotent.

NOT added to tools/texture_locks: the image-generation tab of Agent Flow imports real
art (ChatGPT sheets, see inbox/prompts/material-system-textures.md) over these
placeholders and locks it.

Usage: placeholder_items.py [--dry-run] [--only id[,id...]] [--contact-sheet PNG]
Output: JSON {created, skipped, missing_source}.
"""
import argparse, hashlib, json, sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "tools" / "material_spec.json"
TEX = ROOT / "src/main/resources/assets/abyssia/textures/item"
TIER = {1: "#c8c8c8", 2: "#5fd07a", 3: "#4a9af0", 4: "#f0d040", 5: "#f08030", 6: "#e040e0"}
KIND_FALLBACK = {"tool": "abyssal_alloy_pickaxe", "armor": "deep_diver_helmet",
                 "component": "abyssal_alloy_ingot", "material": "abyssal_alloy_ingot"}


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp(item):
    t = item["texture"]
    cols = [hexrgb(c) for c in t["palette"].split()] + [hexrgb(t["tint"])]
    cols.sort(key=lum)
    dark = cols[0] * 0.6
    return [dark] + cols  # extra-dark outline stop


def source(item):
    name = item["texture"].get("placeholder_from", "")
    for cand in (name, name.split(":")[-1], KIND_FALLBACK.get(item["kind"], "abyssal_alloy_ingot")):
        p = TEX / f"{cand}.png"
        if p.exists():
            return p
    return None


def make(item, src):
    im = np.array(Image.open(src).convert("RGBA").resize((16, 16), Image.NEAREST)).astype(float)
    a = im[..., 3]
    op = a > 0
    L = 0.299 * im[..., 0] + 0.587 * im[..., 1] + 0.114 * im[..., 2]
    lo, hi = L[op].min(), L[op].max()
    n = np.where(op, (L - lo) / max(hi - lo, 1), 0)
    r = ramp(item)
    pos = n * (len(r) - 1)
    out = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            if not op[y, x]:
                continue
            i = min(int(pos[y, x]), len(r) - 2)
            f = pos[y, x] - i
            out[y, x, :3] = r[i] * (1 - f) + r[i + 1] * f
            out[y, x, 3] = a[y, x]
    # item-specific accent marks on opaque pixels (lightest ramp colour)
    h = hashlib.md5(item["id"].encode()).digest()
    ys, xs = np.nonzero(op)
    for k in range(2):
        j = (h[k * 2] * 256 + h[k * 2 + 1]) % len(ys)
        out[ys[j], xs[j], :3] = r[-1]
    # tier corner pixel
    out[0, 15, :3] = hexrgb(TIER.get(item["phase"], "#c8c8c8"))
    out[0, 15, 3] = 255
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--only", default="")
    ap.add_argument("--contact-sheet", default="")
    a = ap.parse_args()
    only = {s for s in a.only.split(",") if s}
    res = {"created": [], "skipped": [], "missing_source": [], "dry_run": a.dry_run}
    made = []
    for it in json.load(open(SPEC, encoding="utf-8"))["items"]:
        if not it.get("new") or (only and it["id"] not in only):
            continue
        dst = TEX / f"{it['id']}.png"
        if dst.exists():
            res["skipped"].append(it["id"])
            continue
        s = source(it)
        if s is None:
            res["missing_source"].append(it["id"])
            continue
        im = make(it, s)
        made.append((it["id"], s.stem, im))
        if not a.dry_run:
            im.save(dst)
        res["created"].append({"id": it["id"], "from": s.stem})
    if a.contact_sheet and made:
        cols = 8
        rows = (len(made) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 72, rows * 72), (60, 60, 60, 255))
        for i, (_, _, im) in enumerate(made):
            sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), ((i % cols) * 72 + 4, (i // cols) * 72 + 4))
        sheet.save(a.contact_sheet)
    print(json.dumps(res, ensure_ascii=False))


if __name__ == "__main__":
    sys.exit(main())
