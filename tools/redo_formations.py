"""Formation / crystal / ground-cover textures rebuilt from vanilla pixels (R2, 2026-09-30).

The procedural look (perfect gradients, evenly sprinkled glints) was rejected.  Here every texture starts from a
vanilla one (pointed dripstone, amethyst block, obsidian, gravel, tuff ...), is remapped onto a small per-family
ramp by lightness rank, and is then roughened by hand-made-looking irregularity: seeded low-frequency noise
shifts which tone a pixel lands on, a few lone pixels jump a tone, glints cluster instead of being spread evenly.
At most 8 colours per texture, no outlines, deterministic seeds.  File names, sizes and silhouettes of the
files they replace are kept; ``*_glow`` companions are the emissive subset (same pixels, same colours).

Runs at the end of forge_textures.run() (before texture_locks / texture_studio, so locked or studio-made
textures still win).  Clusters and buds are NOT here: mineral_textures.py owns them.

    python tools/redo_formations.py            # write the missing textures (tools/texture_locks.py policy)
    python tools/redo_formations.py --textures all   # redraw existing (unlocked: see texture_locks) ones too
    python tools/redo_formations.py --list
Locked textures are skipped; glow companions are written by tools/derive_textures.py, not here.
"""
from __future__ import annotations

import os
import sys
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import texture_locks  # noqa: E402

BLOCK = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures", "block")
LOCKS = os.path.join(HERE, "texture_locks", "assets", "textures", "block")


# ------------------------------------------------------------------ helpers

def _seed(name: str) -> int:
    return zlib.crc32(("r2:" + name).encode()) & 0x7FFFFFFF


def _lum(a: np.ndarray) -> np.ndarray:
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def tile_noise(rng: np.random.Generator, h: int, w: int, cell: int) -> np.ndarray:
    """Value noise in 0..1 that wraps on an h x w tile."""
    gh, gw = max(h // cell, 1), max(w // cell, 1)
    g = rng.random((gh, gw))
    ys = (np.arange(h) + 0.5) / cell
    xs = (np.arange(w) + 0.5) / cell
    y0 = np.floor(ys).astype(int); x0 = np.floor(xs).astype(int)
    fy = ys - y0; fx = xs - x0
    fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
    y0 %= gh; x0 %= gw; y1 = (y0 + 1) % gh; x1 = (x0 + 1) % gw
    a = g[np.ix_(y0, x0)]; b = g[np.ix_(y0, x1)]; c = g[np.ix_(y1, x0)]; d = g[np.ix_(y1, x1)]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]


def rank01(values: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Percentile rank (0..1) of ``values`` among the masked pixels (ties broken by scan order)."""
    out = np.zeros(values.shape)
    idx = np.flatnonzero(mask.ravel())
    order = np.argsort(values.ravel()[idx], kind="stable")
    r = np.empty(len(idx)); r[order] = np.arange(len(idx))
    out.ravel()[idx] = r / max(len(idx) - 1, 1)
    return out


def roughen(score: np.ndarray, mask: np.ndarray, rng, noise_amp: float, jitter: float, cell: int = 4) -> np.ndarray:
    """Vanilla structure (score) + uneven low-frequency noise + a few lone outliers, re-ranked to 0..1."""
    h, w = score.shape
    s = score + (tile_noise(rng, h, w, cell) - 0.5) * noise_amp
    lone = rng.random((h, w)) < jitter
    s = s + lone * (rng.random((h, w)) - 0.5) * 0.5
    return rank01(s, mask)


def to_tones(rank: np.ndarray, edges: list[float]) -> np.ndarray:
    return np.searchsorted(np.array(edges), rank, side="right")


def paint(tones: np.ndarray, alpha: np.ndarray, ramp: list[tuple]) -> np.ndarray:
    out = np.zeros(tones.shape + (4,), np.uint8)
    for i, c in enumerate(ramp):
        out[tones == i, :3] = c
    out[..., 3] = np.where(alpha, 255, 0)
    return out


def save(arr: np.ndarray, name: str) -> None:
    texture_locks.save(arr, os.path.join(BLOCK, name + ".png"))


def glow_of(arr: np.ndarray, sel: np.ndarray, name: str) -> None:
    g = np.zeros_like(arr)
    g[sel] = arr[sel]
    texture_locks.save(g, os.path.join(BLOCK, name + "_glow.png"))   # derived: skipped (derive_textures.py)


# ------------------------------------------------------------------ vanilla
_VAN = None


def vanilla(ref: str) -> np.ndarray:
    global _VAN
    if _VAN is None:
        import forge_textures as ft
        _VAN = ft.Vanilla(ft.find_client_jar())
    im = _VAN.get(ref)
    if im is None:
        raise RuntimeError(f"vanilla texture {ref} not available (Gradle client jar missing)")
    return np.array(im.convert("RGBA"))


# ------------------------------------------------------------------ stalactites
# dark -> light, 6 tones each; glow families add two emissive tones on top (hot, core).
FAMILY = {
    "abyssal": dict(ramp=[(28, 32, 44), (40, 46, 62), (54, 62, 82), (70, 80, 102), (90, 102, 124), (114, 126, 146)],
                    speck=None, glow=None),
    "mineral": dict(ramp=[(44, 38, 33), (62, 54, 46), (82, 72, 60), (104, 92, 76), (128, 114, 94), (152, 136, 112)],
                    speck=(118, 64, 38), glow=None),
    "crystal": dict(ramp=[(14, 62, 82), (20, 92, 116), (30, 126, 150), (50, 160, 182), (92, 198, 214), (142, 224, 234)],
                    speck=None, glow=((159, 246, 254), (239, 255, 255))),
    "thermal": dict(ramp=[(38, 16, 12), (60, 25, 16), (86, 37, 20), (114, 51, 26), (142, 68, 32), (172, 90, 40)],
                    speck=None, glow=((240, 118, 24), (255, 208, 96))),
}
PARTS = ("base", "frustum", "middle", "tip", "tip_merge")


def stalactite(family: str, direction: str, part: str) -> list[str]:
    name = f"{family}_stalactite_{direction}_{part}"
    fam = FAMILY[family]
    van = vanilla(f"block/pointed_dripstone_{direction}_{part}")
    alpha = van[..., 3] > 0
    rng = np.random.default_rng(_seed(name))
    score = np.clip((_lum(van[..., :3].astype(float)) - 50) / 130, 0, 1)
    # dripstone's own banding stays; noise makes the bands wander, outliers break the rhythm.
    r = roughen(score * 0.4 + rank01(score, alpha) * 0.6, alpha, rng, noise_amp=0.22, jitter=0.05, cell=4)
    tones = to_tones(r, [0.10, 0.28, 0.50, 0.72, 0.90])
    if fam["speck"]:                       # rust film in a few pits
        sp = alpha & (rng.random(r.shape) < 0.045) & (tones >= 1) & (tones <= 3)
    ramp = list(fam["ramp"])
    if fam["speck"]:
        ramp.append(fam["speck"])
        tones = np.where(sp, 6, tones)
    arr = paint(tones, alpha, ramp)
    written = [name]
    if fam["glow"]:
        hot, core = fam["glow"]
        n = int(alpha.sum())
        k = max(2, int(round(n * 0.035)))
        cand = np.flatnonzero((alpha & (tones >= 2)).ravel())
        pick = rng.permutation(cand)[:k]
        sel = np.zeros(alpha.shape, bool)
        h, w = alpha.shape
        for p in pick:
            y, x = divmod(int(p), w)
            arr[y, x, :3] = core
            sel[y, x] = True
            if family == "thermal" or rng.random() < 0.4:   # lava veins / crystal streaks run down a pixel or two
                for dy in range(1, 1 + int(rng.integers(1, 3))):
                    if y + dy < h and alpha[y + dy, x]:
                        arr[y + dy, x, :3] = hot
                        sel[y + dy, x] = True
        glow_of(arr, sel, name)
        written.append(name + "_glow")
    save(arr, name)
    return written


# ------------------------------------------------------------------ crystal blocks
BLOCKS = {
    "amber_crystal_block":  ([(86, 42, 10), (128, 66, 14), (170, 96, 20), (206, 128, 32), (232, 162, 58), (246, 196, 100)], "amethyst_block", 0),
    "blue_crystal_block":   ([(14, 34, 110), (20, 56, 160), (30, 84, 206), (52, 116, 232), (92, 152, 244), (146, 192, 250)], "amethyst_block", 1),
    "cyan_crystal_block":   ([(10, 86, 104), (16, 124, 144), (24, 158, 176), (48, 190, 204), (92, 214, 224), (148, 232, 238)], "amethyst_block", 2),
    "green_crystal_block":  ([(16, 80, 52), (24, 116, 70), (34, 152, 88), (60, 184, 110), (108, 212, 148), (158, 232, 186)], "amethyst_block", 3),
    "violet_crystal_block": ([(60, 22, 110), (88, 36, 150), (118, 56, 186), (146, 84, 214), (178, 122, 232), (210, 164, 244)], "amethyst_block", 4),
    "white_crystal_block":  ([(128, 150, 168), (160, 180, 194), (190, 206, 216), (214, 226, 232), (232, 240, 244), (246, 250, 252)], "calcite", 5),
    "deep_crystal_block":   ([(6, 52, 74), (10, 80, 104), (16, 108, 132), (26, 140, 160), (50, 170, 188), (96, 200, 212)], "budding_amethyst", 6),
}
EMISSIVE = {"amber_crystal_block", "blue_crystal_block", "cyan_crystal_block", "green_crystal_block",
            "violet_crystal_block", "white_crystal_block"}


def crystal_block(name: str) -> list[str]:
    ramp, ref, k = BLOCKS[name]
    van = vanilla("block/" + ref)
    rng = np.random.default_rng(_seed(name))
    a = van[..., :3].astype(float)
    score = _lum(a)
    # every colour gets its own layout: mirror / transpose / wrap-around shift of the vanilla facets
    if k % 2:
        score = score[:, ::-1]
    if k % 3 == 1:
        score = score.T
    score = np.roll(score, (int(rng.integers(16)), int(rng.integers(16))), (0, 1))
    full = np.ones(score.shape, bool)
    soft = (score + 0.5 * (np.roll(score, 1, 0) + np.roll(score, -1, 0) + np.roll(score, 1, 1) + np.roll(score, -1, 1))) / 3
    r = roughen(rank01(soft, full), full, rng, noise_amp=0.45, jitter=0.03, cell=4)
    edges = [0.14, 0.34, 0.56, 0.76, 0.92]
    tones = to_tones(r, edges)                       # 0..5 ; top 11% -> tone 5 (lit facets)
    top = ramp[5]
    hot, core = _mix(top, (255, 255, 255), 0.5), _mix(top, (255, 255, 255), 0.85)
    arr = paint(tones, full, ramp + [hot, core])
    # glints: the brightest facet pixels turn emissive; a couple get the white core
    lit = np.argwhere(tones == 5)
    sel = np.zeros(full.shape, bool)
    order = rng.permutation(len(lit))
    for j, i in enumerate(order):
        y, x = lit[i]
        arr[y, x, :3] = core if j < max(2, len(lit) // 4) else hot
        sel[y, x] = True
    if name in EMISSIVE:
        glow_of(arr, sel, name + "")            # written as <name>_glow.png
    save(arr, name)
    return [name] + ([name + "_glow"] if name in EMISSIVE else [])


# ------------------------------------------------------------------ ground cover
def volcanic_glass() -> list[str]:
    name = "volcanic_glass"
    ramp = [(15, 11, 25), (25, 19, 41), (37, 29, 58), (52, 43, 76), (72, 62, 98), (100, 90, 126)]
    rng = np.random.default_rng(_seed(name))
    obs = vanilla("block/obsidian")[..., :3].astype(float)
    bs = vanilla("block/blackstone")[..., :3].astype(float)
    full = np.ones((16, 16), bool)
    score = rank01(_lum(obs), full) * 0.5 + rank01(_lum(bs), full) * 0.5
    r = roughen(score, full, rng, noise_amp=0.7, jitter=0.05, cell=8)
    tones = to_tones(r, [0.16, 0.42, 0.70, 0.90, 0.975])
    arr = paint(tones, full, ramp)
    # conchoidal sheen: three short slanted glints of different length, never the same slope twice
    for _ in range(3):
        y, x = int(rng.integers(1, 14)), int(rng.integers(0, 14))
        dx = int(rng.choice([1, 1, 2]))
        for s in range(int(rng.integers(2, 4))):
            yy, xx = (y + s // dx) % 16, (x + s) % 16
            arr[yy, xx, :3] = ramp[4] if s else ramp[5]
    save(arr, name)
    return [name]


def _blobs(rng, count, rmin, rmax, angular, margin=1, tries=400):
    """Non-touching irregular blobs on a 16x16 tile -> list of (mask 16x16, lit-shade 16x16 in 0..1)."""
    taken = np.zeros((16, 16), bool)
    yy, xx = np.mgrid[0:16, 0:16]
    out = []
    for _ in range(tries):
        if len(out) >= count:
            break
        r = rng.uniform(rmin, rmax)
        cy, cx = rng.uniform(1.5, 14.5), rng.uniform(1.5, 14.5)
        ang = np.arctan2(yy - cy, xx - cx)
        wob = 1 + 0.28 * np.sin(ang * int(rng.integers(2, 4)) + rng.uniform(0, 6.28)) + 0.12 * (rng.random() - .5)
        dy, dx = np.abs(yy - cy), np.abs(xx - cx)
        dist = (np.maximum(dy, dx) * angular + np.hypot(dy, dx) * (1 - angular))
        m = dist <= r * wob
        if not m.any() or (m & taken).any():
            continue
        grown = m.copy()
        for sy in (-margin, margin):
            grown |= np.roll(m, sy, 0)
            grown |= np.roll(m, sy, 1)
        if (grown & taken).any():
            continue
        shade = np.clip(0.5 - ((yy - cy) * 0.55 + (xx - cx) * 0.35) / (2 * r + 1), 0, 1)   # light from upper left
        taken |= m
        out.append((m, shade))
    return out


def scatter(name: str, van_ref: str, ramp, count, rmin, rmax, angular, dark_bias=0.0) -> list[str]:
    rng = np.random.default_rng(_seed(name))
    van = vanilla(van_ref)[..., :3].astype(float)
    grain = rank01(_lum(van), np.ones((16, 16), bool))
    arr = np.zeros((16, 16, 4), np.uint8)
    n = len(ramp)
    for m, shade in _blobs(rng, count, rmin, rmax, angular):
        base = rng.uniform(0.25, 0.75)           # each stone has its own overall lightness
        v = np.clip(base * 0.5 + shade * 0.35 + grain * 0.25 + (rng.random((16, 16)) - .5) * 0.16 - dark_bias, 0, 0.999)
        t = (v * n).astype(int)
        idx = np.argwhere(m)
        for y, x in idx:
            arr[y, x, :3] = ramp[t[y, x]]
            arr[y, x, 3] = 255
        # a stone's lower-right rim is one tone darker (contact shadow), the top-left edge one lighter
        for y, x in idx:
            if not m[(y + 1) % 16, (x + 1) % 16] and t[y, x] > 0 and rng.random() < 0.7:
                arr[y, x, :3] = ramp[t[y, x] - 1]
    save(arr, name)
    return [name]


def manganese_nodules() -> list[str]:
    ramp = [(30, 24, 34), (50, 41, 54), (72, 60, 76), (98, 84, 102), (128, 112, 132), (162, 148, 166)]
    return scatter("manganese_nodules", "block/tuff", ramp, 4, 2.4, 3.4, 0.0)


def seafloor_pebbles() -> list[str]:
    ramp = [(44, 50, 57), (66, 73, 82), (92, 101, 111), (120, 130, 140), (150, 160, 168)]
    return scatter("seafloor_pebbles", "block/gravel", ramp, 6, 0.9, 1.8, 0.35)


def cave_rubble() -> list[str]:
    ramp = [(26, 28, 35), (40, 43, 52), (57, 61, 72), (76, 81, 93), (98, 104, 116)]
    return scatter("cave_rubble", "block/cobblestone", ramp, 5, 1.3, 2.3, 0.85, dark_bias=0.08)


def crystal_shards() -> list[str]:
    name = "crystal_shards"
    fam = FAMILY["crystal"]
    ramp = list(fam["ramp"])
    hot, core = fam["glow"]
    rng = np.random.default_rng(_seed(name))
    arr = np.zeros((16, 16, 4), np.uint8)
    sel = np.zeros((16, 16), bool)
    taken = np.zeros((16, 16), bool)
    dirs = [(0, 1), (1, 1), (1, 2), (-1, 2), (1, 0), (-1, 1)]
    n = 0
    for _ in range(300):
        if n >= 5:
            break
        y, x = int(rng.integers(1, 15)), int(rng.integers(1, 15))
        dy, dx = dirs[int(rng.integers(len(dirs)))]
        ln = int(rng.integers(3, 6))
        pts = [(y + (dy * s) // 2, x + (dx * s) // 2) for s in range(ln)]
        pts = list(dict.fromkeys(pts))
        if any(not (0 <= py < 16 and 0 <= px < 16) for py, px in pts):
            continue
        halo = set()
        for py, px in pts:
            for oy in (-1, 0, 1):
                for ox in (-1, 0, 1):
                    halo.add((py + oy, px + ox))
        if any(0 <= py < 16 and 0 <= px < 16 and taken[py, px] for py, px in halo):
            continue
        for i, (py, px) in enumerate(pts):
            t = 3 if i % 2 == 0 else 2                   # lit spine, shadow flank
            arr[py, px] = (*ramp[t + int(rng.integers(0, 2))], 255)
            taken[py, px] = True
        py, px = pts[int(rng.integers(len(pts)))]         # one glint per fragment
        arr[py, px] = (*(core if n % 2 else hot), 255)
        sel[py, px] = True
        n += 1
    glow_of(arr, sel, name)
    save(arr, name)
    return [name, name + "_glow"]


NEEDLE_MASK = (
    "................",
    "................",
    "................",
    ".#..............",
    "..####..........",
    "..####..........",
    "..####..........",
    "..####..........",
    "...####.........",
    "...####.........",
    "...####....#....",
    "...####...###...",
    "....####.###....",
    "....####.###....",
    "....#######.....",
    "....#######.....",
)


def crystal_needle() -> list[str]:
    """Silhouette = the original needle sprite (NEEDLE_MASK); shading is hand-style: each horizontal run of a needle is
    lit on its left, shadowed on its right, with uneven noise (no smooth gradient)."""
    name = "crystal_needle"
    ramp = [(14, 62, 82), (26, 108, 132), (48, 150, 172), (92, 196, 214), (150, 228, 238), (210, 246, 250)]
    alpha = np.array([[c == "#" for c in row] for row in NEEDLE_MASK])
    rng = np.random.default_rng(_seed(name))
    h, w = alpha.shape
    score = np.zeros((h, w))
    for y in range(h):
        xs = np.flatnonzero(alpha[y])
        if len(xs) == 0:
            continue
        runs = np.split(xs, np.flatnonzero(np.diff(xs) > 1) + 1)
        for run in runs:
            for k, x in enumerate(run):
                score[y, x] = 1.0 - (k + 0.5) / len(run) if len(run) > 1 else rng.uniform(0.4, 0.8)
    score += (rng.random((h, w)) - 0.5) * 0.45 + (1 - np.arange(h)[:, None] / h) * 0.15   # tips a little paler
    r = rank01(score, alpha)
    tones = to_tones(r, [0.10, 0.30, 0.55, 0.78, 0.94])
    save(paint(tones, alpha, ramp), name)
    return [name]


def brine_surface() -> list[str]:
    """16x128 animation strip (8 frames, interpolated): two noise layers drifting in different directions."""
    name = "brine_surface"
    ramp = [(70, 92, 92), (96, 120, 118), (126, 150, 146), (160, 182, 176), (200, 216, 208)]
    alphas = [118, 134, 150, 170, 194]
    rng = np.random.default_rng(_seed(name))
    a = tile_noise(rng, 16, 16, 8)
    b = tile_noise(rng, 16, 16, 4)
    c = tile_noise(rng, 16, 16, 2)
    speck = rng.random((16, 16))
    strip = np.zeros((128, 16, 4), np.uint8)
    for f in range(8):
        fa = np.roll(a, 2 * f, 0)
        fb = np.roll(b, -2 * f, 1)
        fc = np.roll(c, (f, f), (0, 1))
        v = 0.5 * fa + 0.34 * fb + 0.16 * fc + (np.roll(speck, f, 0) - 0.5) * 0.10
        t = np.clip(((v - 0.30) / 0.36) * 5, 0, 4.999).astype(int)
        for i in range(5):
            m = t == i
            strip[f * 16:(f + 1) * 16][m] = (*ramp[i], alphas[i])
    texture_locks.save(strip, os.path.join(BLOCK, name + ".png"))
    return [name]


# ------------------------------------------------------------------ driver
def targets() -> dict:
    t = {}
    for fam in FAMILY:
        for d in ("up", "down"):
            for p in PARTS:
                t[f"{fam}_stalactite_{d}_{p}"] = (lambda f=fam, d=d, p=p: stalactite(f, d, p))
    for n in BLOCKS:
        t[n] = (lambda n=n: crystal_block(n))
    t.update({"volcanic_glass": volcanic_glass, "manganese_nodules": manganese_nodules,
              "seafloor_pebbles": seafloor_pebbles, "cave_rubble": cave_rubble, "crystal_shards": crystal_shards,
              "crystal_needle": crystal_needle, "brine_surface": brine_surface})
    return t


def _fix_block_glow_name() -> None:
    """crystal_block() saves its glow through glow_of(name + '') which already appends _glow; nothing to do."""


def run(quiet: bool = False) -> list[str]:
    written: list[str] = []
    for name, fn in targets().items():
        if os.path.exists(os.path.join(LOCKS, name + ".png")) or not texture_locks.wants(os.path.join(BLOCK, name + ".png")):
            continue                                   # locked / kept as committed: leave it alone
        written += fn()
    if not quiet:
        print(f"redo_formations: {len(written)} textures written")
    return written


if __name__ == "__main__":
    texture_locks.mode_from_argv()
    if "--list" in sys.argv:
        print("\n".join(targets()))
    else:
        run()
