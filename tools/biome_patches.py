"""Patch sizes of the deep-layer biome map (CSV written by `/abyssia caves map <radius> <step>`, columns x,z,biome,seabed).

    python tools/biome_patches.py <map.csv> [--limit-chunks 8]

A patch = a 4-connected group of samples with the same biome.  Per patch: area, equal-area radius and half of the
larger bounding-box side (the stricter one), in chunks.  The report gives the area-weighted median and the 90 / 99 /
max values, and how much of the map lies in patches that exceed the limit.  Read-only.
"""
import argparse
import csv
import math
import sys
from collections import deque


def load(path):
    rows = []
    with open(path, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            rows.append((int(r["x"]), int(r["z"]), r["biome"]))
    xs = sorted({r[0] for r in rows})
    zs = sorted({r[1] for r in rows})
    step = min(b - a for a, b in zip(xs, xs[1:])) if len(xs) > 1 else 16
    grid = {(x, z): b for x, z, b in rows}
    return grid, xs, zs, step


def patches(grid, xs, zs, step):
    seen = set()
    out = []
    for x in xs:
        for z in zs:
            if (x, z) in seen or (x, z) not in grid:
                continue
            biome = grid[(x, z)]
            q = deque([(x, z)])
            seen.add((x, z))
            cells = []
            while q:
                cx, cz = q.popleft()
                cells.append((cx, cz))
                for dx, dz in ((step, 0), (-step, 0), (0, step), (0, -step)):
                    n = (cx + dx, cz + dz)
                    if n not in seen and grid.get(n) == biome:
                        seen.add(n)
                        q.append(n)
            xa = [c[0] for c in cells]
            za = [c[1] for c in cells]
            area = len(cells) * step * step
            box = max(max(xa) - min(xa), max(za) - min(za)) + step
            out.append({"biome": biome, "area": area, "r_area": math.sqrt(area / math.pi) / 16.0, "r_box": box / 2.0 / 16.0,
                        "touches_edge": min(xa) == xs[0] or max(xa) == xs[-1] or min(za) == zs[0] or max(za) == zs[-1]})
    return out


def pct(sorted_vals, weights, q):
    total = sum(weights)
    acc = 0.0
    for v, w in zip(sorted_vals, weights):
        acc += w
        if acc >= q * total:
            return v
    return sorted_vals[-1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--limit-chunks", type=float, default=8.0)
    a = ap.parse_args()
    grid, xs, zs, step = load(a.csv)
    ps = [p for p in patches(grid, xs, zs, step) if p["biome"] != "none"]
    inner = [p for p in ps if not p["touches_edge"]] or ps      # patches cut by the map edge are only lower bounds
    total_area = sum(p["area"] for p in ps)
    print(f"samples {len(grid)} step {step} patches {len(ps)} (inner {len(inner)}) biomes {len({p['biome'] for p in ps})}")
    for key in ("r_area", "r_box"):
        s = sorted(inner, key=lambda p: p[key])
        vals = [p[key] for p in s]
        w = [p["area"] for p in s]
        print(f"{key:7s} chunks: area-weighted median {pct(vals, w, .5):5.1f}  p90 {pct(vals, w, .9):5.1f}  p99 {pct(vals, w, .99):5.1f}  max {vals[-1]:5.1f}")
    over = [p for p in ps if p["r_box"] > a.limit_chunks]
    print(f"over {a.limit_chunks:g} chunks (box radius): {len(over)} patches, {100.0 * sum(p['area'] for p in over) / total_area:.1f}% of the area")
    for p in sorted(ps, key=lambda p: -p["r_box"])[:5]:
        print(f"  biggest: {p['biome']:28s} r_box {p['r_box']:5.1f}  r_area {p['r_area']:5.1f} chunks{'  (cut by edge)' if p['touches_edge'] else ''}")


if __name__ == "__main__":
    sys.exit(main())
