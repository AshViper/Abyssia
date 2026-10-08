"""Derived textures: rebuilt from the current base textures instead of being drawn from scratch.

* stone families (building_assets.STONE_FAMILIES), from ``<rock>.png``:
  ``polished_<rock>`` (denoised base on the base's own colours + light bevel), ``<rock>_bricks`` (running bond,
  4px rows, 8px bricks, dark 1px joints), ``cracked_<rock>_bricks`` (the bricks + a few dark cracks and chipped
  corners) and ``chiseled_<rock>`` (raised border frame + recessed inset panel with a diamond boss)
* block glow overlays: every ``textures/block/<x>_glow.png`` that exists or that a model references (except
  texture_locks.DELETE): the pixels of ``<x>.png`` at or above
  the 65th luminance percentile (Rec. 709) of its visible pixels, everything else transparent

Locked textures (tools/texture_locks) are never overwritten, and a file is only rewritten when its pixels change, so
a rerun gives no diff.  gen_deep_assets.py and forge_textures.run() call run() at the end; the generators themselves
never write these files (texture_locks.wants).

    python tools/derive_textures.py                  # rebuild everything, JSON summary on stdout
    python tools/derive_textures.py --dry-run        # report only
    python tools/derive_textures.py --only polished_deep_sea_rock,deep_sea_rock_bricks
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import zlib

import numpy as np
from PIL import Image

import texture_locks

ASSETS = texture_locks.ASSETS
LUMA = np.array([0.2126, 0.7152, 0.0722])
GLOW_PERCENTILE = 65
SPECIAL_GLOW = {"textures/block/oil_kelp_ripe_glow.png": "oil_sac_glow"}     # OL02: own recipe instead of the luminance cut
GLOW_DIR = "textures/block/"        # block overlays only (entity emissive layers are painted by hand, fauna tools)


# ================================================================ targets

def _block(name: str) -> str:
    return f"textures/block/{name}.png"


def stone_targets() -> dict[str, tuple[str, str]]:
    """rel path -> (recipe, base rel path) for the stone families."""
    import building_assets as ba
    t = {}
    for fam in ba.STONE_FAMILIES:
        base = _block(fam.rock)
        t[_block(fam.polished)] = ("polished", base)
        t[_block(fam.bricks)] = ("bricks", base)
        t[_block(fam.cracked_bricks)] = ("cracked", base)
        t[_block(fam.chiseled)] = ("chiseled", base)
    return t


_STONE: dict | None = None


def is_derived(rel: str) -> bool:
    """``rel`` relative to the assets folder (``textures/block/x.png``)."""
    global _STONE
    if _STONE is None:
        _STONE = stone_targets()
    return rel in _STONE or (rel.startswith(GLOW_DIR) and rel.endswith("_glow.png"))


def _model_glow_refs() -> set[str]:
    out = set()
    for path in glob.glob(os.path.join(ASSETS, "models", "**", "*.json"), recursive=True):
        try:
            with open(path, encoding="utf-8") as f:
                textures = json.load(f).get("textures", {})
        except (OSError, ValueError, AttributeError):
            continue
        for ref in textures.values():
            if isinstance(ref, str) and not ref.startswith("#") and ref.endswith("_glow"):
                ns, _, p = ref.rpartition(":")
                if ns in ("", "abyssia") and f"textures/{p}.png".startswith(GLOW_DIR):
                    out.add(f"textures/{p}.png")
    return out


def glow_targets() -> dict[str, tuple[str, str]]:
    rels = {os.path.relpath(p, ASSETS).replace(os.sep, "/")
            for p in glob.glob(os.path.join(ASSETS, *GLOW_DIR.split("/"), "*_glow.png"))}
    rels |= _model_glow_refs()
    rels |= set(SPECIAL_GLOW)
    rels -= set(texture_locks.DELETE)
    return {rel: (SPECIAL_GLOW.get(rel, "glow"), rel[:-len("_glow.png")] + ".png") for rel in sorted(rels)}


def targets() -> dict[str, tuple[str, str]]:
    t = stone_targets()
    t.update(glow_targets())      # after the stones: a glow reads its base as it is now
    return t


# ================================================================ pixel helpers (float RGB, alpha kept apart)

def lum(rgb: np.ndarray) -> np.ndarray:
    return rgb @ LUMA


def mix(rgb: np.ndarray, target, t) -> np.ndarray:
    t = np.asarray(t, dtype=float)
    if t.ndim == 2:
        t = t[..., None]
    return rgb + (np.asarray(target, dtype=float) - rgb) * t


def lighten(rgb, t):
    return mix(rgb, (255, 255, 255), t)


def darken(rgb, t):
    return mix(rgb, (0, 0, 0), t)


def _neighbours(rgb: np.ndarray) -> np.ndarray:
    return np.stack([np.roll(rgb, (dy, dx), (0, 1)) for dy in (-1, 0, 1) for dx in (-1, 0, 1)])


def median3(rgb: np.ndarray) -> np.ndarray:
    """Tileable 3x3 median by luminance: every pixel takes the neighbour of median brightness (a real base colour)."""
    n = _neighbours(rgb)
    order = np.argsort(lum(n), axis=0, kind="stable")[4]
    return np.take_along_axis(n, order[None, ..., None].repeat(3, -1), 0)[0]


def base_palette(rgb: np.ndarray, levels: int = 6) -> np.ndarray:
    """``levels`` colours of the base picked at even luminance quantiles (its own colours, darkest first)."""
    flat = rgb.reshape(-1, 3)
    order = np.argsort(lum(flat), kind="stable")
    idx = [order[int(round(q * (len(order) - 1)))] for q in np.linspace(0.06, 0.94, levels)]
    return flat[idx]


def quantise(rgb: np.ndarray, pal: np.ndarray) -> np.ndarray:
    d = ((rgb[..., None, :] - pal[None, None]) ** 2).sum(-1)
    return pal[d.argmin(-1)]


def smooth(rgb: np.ndarray) -> np.ndarray:
    """Denoised base: two median passes, softened, back on the base's own colours."""
    s = median3(median3(rgb))
    s = mix(s, _neighbours(s).mean(0), 0.35)
    return quantise(s, base_palette(rgb))


def _grid():
    y, x = np.mgrid[0:16, 0:16]
    return y, x


def brick_layout():
    """Running bond on 16x16: rows of 4px (joint on the 4th), bricks 8px wide, odd rows offset by 4px."""
    y, x = _grid()
    row = y // 4
    off = np.where(row % 2 == 1, 4, 0)
    joint_h = y % 4 == 3
    joint_v = ((x - off) % 8 == 7) & ~joint_h
    brick = row * 2 + ((x - off) % 16) // 8
    return joint_h | joint_v, brick, y % 4, (x - off) % 8


def _rng(name: str) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(name.encode("utf-8")))


# ================================================================ recipes

def polished(rgb: np.ndarray, name: str) -> np.ndarray:
    out = smooth(rgb)
    y, x = _grid()
    out = np.where(((y == 0) | (x == 0))[..., None], lighten(out, 0.22), out)
    return np.where(((y == 15) | (x == 15))[..., None], darken(out, 0.35), out)


def bricks(rgb: np.ndarray, name: str) -> np.ndarray:
    face = median3(rgb)
    joint, brick, ry, rx = brick_layout()
    rng = _rng(name)
    tone = rng.uniform(-0.09, 0.09, 8)[brick]                 # every brick a little lighter or darker
    face = np.where((tone > 0)[..., None], lighten(face, np.abs(tone)), darken(face, np.abs(tone)))
    face = np.where(((ry == 0) & ~joint)[..., None], lighten(face, 0.12), face)      # lit top edge
    face = np.where(((ry == 2) & ~joint)[..., None], darken(face, 0.12), face)       # shaded bottom edge
    face = np.where(((rx == 0) & ~joint)[..., None], lighten(face, 0.06), face)
    return np.where(joint[..., None], darken(median3(rgb), 0.62), face)


def cracked(rgb: np.ndarray, name: str) -> np.ndarray:
    out = darken(bricks(rgb, name.replace("cracked_", "")), 0.05)
    joint, brick, ry, rx = brick_layout()
    dark = darken(out, 0.72)
    rng = _rng(name)
    hit = np.zeros((16, 16), bool)
    for _ in range(3):                                        # cracks: short dark walks away from a joint
        cand = np.argwhere(joint & ~hit)
        y, x = cand[rng.integers(len(cand))]
        dy, dx = (1 if rng.random() < 0.5 else -1), (1 if rng.random() < 0.5 else -1)
        for _step in range(int(rng.integers(3, 6))):
            if rng.random() < 0.5:
                y = (y + dy) % 16
            else:
                x = (x + dx) % 16
            hit[y, x] = True
    faces = np.argwhere(~joint & ((ry == 0) | (ry == 2)) & ((rx == 0) | (rx == 6)))
    for i in rng.choice(len(faces), 3, replace=False):       # chipped brick corners
        hit[tuple(faces[i])] = True
    return np.where(hit[..., None], dark, out)


def chiseled(rgb: np.ndarray, name: str) -> np.ndarray:
    out = smooth(rgb)
    y, x = _grid()
    d = np.minimum(np.minimum(x, y), np.minimum(15 - x, 15 - y))
    upper = (x + y < 15)[..., None]                           # upper-left half of each ring
    ring = lambda k: (d == k)[..., None]
    out = np.where(ring(0), darken(out, 0.55), out)                                          # outer groove
    out = np.where(ring(1) & upper, lighten(out, 0.25), np.where(ring(1), darken(out, 0.22), out))  # raised frame
    out = np.where(ring(3) & upper, darken(out, 0.45), np.where(ring(3), lighten(out, 0.18), out))  # recessed panel
    out = np.where((d >= 4)[..., None], darken(out, 0.12), out)
    m = np.abs(x - 7.5) + np.abs(y - 7.5)
    out = np.where(((m >= 2.5) & (m < 3.5))[..., None], darken(out, 0.4), out)               # diamond boss
    out = np.where((m < 2.5)[..., None] & upper, lighten(out, 0.3), np.where((m < 2.5)[..., None], lighten(out, 0.12), out))
    return out


def crust_bricks(rgb: np.ndarray, name: str) -> np.ndarray:
    joint = brick_layout()[0]
    return np.where(joint[..., None], np.round(darken(rgb, 0.45)), rgb)


RECIPES = {"polished": polished, "bricks": bricks, "cracked": cracked, "chiseled": chiseled,
           "crust_bricks": crust_bricks, "copy": lambda rgb, name: rgb}


def glow(a: np.ndarray) -> np.ndarray:
    out = np.zeros_like(a)
    vis = a[..., 3] > 0
    if vis.any():
        lu = lum(a[..., :3].astype(float))
        pick = vis & (lu >= np.percentile(lu[vis], GLOW_PERCENTILE))
        out[pick] = a[pick]
    return out


def oil_sac_glow(a: np.ndarray) -> np.ndarray:
    """OL02: only the orange/amber oil-sac pixels (hue 20-45 deg) of oil_kelp_ripe, recoloured amber (edge) to
    yellow-green (core) by their own brightness; everything else transparent."""
    import colorsys
    out = np.zeros_like(a)
    h = np.zeros(a.shape[:2])
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            r, g, b = (a[y, x, :3] / 255.0)
            h[y, x] = colorsys.rgb_to_hsv(r, g, b)[0] * 360
    sac = (a[..., 3] > 0) & (h >= 20) & (h <= 45)
    if sac.any():
        lu = lum(a[..., :3].astype(float))
        lo, hi = lu[sac].min(), lu[sac].max()
        t = ((lu - lo) / (hi - lo) if hi > lo else np.ones_like(lu))[..., None]
        col = mix(np.broadcast_to(np.array([0xe0, 0xa2, 0x3a], float), a.shape[:2] + (3,)), (0xf2, 0xe6, 0x6a), t)
        out[sac, :3] = np.clip(np.round(col[sac]), 0, 255).astype(np.uint8)
        out[sac, 3] = 255
    return out


def build(recipe: str, base: np.ndarray, name: str) -> np.ndarray:
    if recipe == "glow":
        return glow(base)
    if recipe == "oil_sac_glow":
        return oil_sac_glow(base)
    a = base[:base.shape[1]]                  # first frame of a strip
    rgb = RECIPES[recipe](a[..., :3].astype(float), name)
    out = np.empty(a.shape, np.uint8)
    out[..., :3] = np.clip(np.round(rgb), 0, 255).astype(np.uint8)
    out[..., 3] = a[..., 3] if recipe in ("copy", "crust_bricks") else 255
    return out


# ================================================================ run

def _load(path: str) -> np.ndarray | None:
    try:
        with Image.open(path) as im:
            return np.asarray(im.convert("RGBA"))
    except OSError:
        return None


def run(dry_run: bool = False, only: set[str] | None = None, quiet: bool = False) -> dict:
    report = {"written": [], "unchanged": 0, "locked": [], "missing_base": [], "dry_run": dry_run}
    for rel, (recipe, base_rel) in targets().items():
        name = os.path.basename(rel)[:-4]
        if only and name not in only:
            continue
        if texture_locks.is_locked(rel):
            report["locked"].append(name)
            continue
        base = _load(os.path.join(ASSETS, base_rel))
        if base is None:
            report["missing_base"].append(name)
            continue
        img = build(recipe, base, name)
        path = os.path.join(ASSETS, rel)
        old = _load(path)
        if old is not None and old.shape == img.shape and np.array_equal(old, img):
            report["unchanged"] += 1
            continue
        report["written"].append(name)
        if not dry_run:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            Image.fromarray(img, "RGBA").save(path)
    if not quiet:
        print(f"Derived textures: {len(report['written'])} {'to write (dry run)' if dry_run else 'written'}, "
              f"{report['unchanged']} unchanged, {len(report['locked'])} locked, "
              f"{len(report['missing_base'])} without a base")
    return report


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--dry-run", action="store_true", help="report what would change, write nothing")
    ap.add_argument("--only", help="comma separated texture names (e.g. polished_deep_sea_rock)")
    args = ap.parse_args()
    report = run(args.dry_run, set(args.only.split(",")) if args.only else None, quiet=True)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
