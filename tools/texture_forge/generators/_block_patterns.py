"""Private helpers shared by the structured block generators
(decoration, organic, metal).

Region label maps, bevel lighting, periodic layouts, clustered surface
offsets and a few wrap-aware drawing tools.  Everything here works in
level space (never RGB) and respects ``ctx.wrap`` so layouts built from
these pieces tile seamlessly.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from core import pixel_art as pa
from core.layers import GenContext

N4 = pa.N4


# ------------------------------------------------------------------ levels


@dataclass(frozen=True)
class Levels:
    """Named positions on the base ramp (0 .. K+1)."""
    K: int

    @property
    def deep(self) -> int:       # mortar, holes, groove bottoms
        return 0

    @property
    def dark(self) -> int:       # shaded bevel edge
        return 1

    @property
    def mid(self) -> int:
        return (self.K + 2) // 2

    @property
    def lo(self) -> int:
        return max(1, self.mid - 1)

    @property
    def up(self) -> int:
        return min(self.K, self.mid + 1)

    @property
    def lit(self) -> int:        # lit bevel edge
        return self.K

    @property
    def hi(self) -> int:         # specular highlight
        return self.K + 1


def levels(ctx: GenContext) -> Levels:
    return Levels(int(ctx.data["K"]))


def resample_weights(weights, n: int) -> np.ndarray:
    """Resample a tone distribution to ``n`` bins (keeps its overall shape)."""
    w = np.asarray(weights, dtype=np.float64)
    if n <= 1:
        return np.ones(max(1, n))
    if w.size == 0 or w.sum() <= 0:
        return np.full(n, 1.0 / n)
    w = w / w.sum()
    if len(w) == n:
        return w
    cdf = np.concatenate([[0.0], np.cumsum(w)])
    src = np.linspace(0, 1, len(cdf))
    new = np.interp(np.linspace(0, 1, n + 1), src, cdf)
    out = np.maximum(np.diff(new), 1e-3)
    return out / out.sum()


# ------------------------------------------------------------ masks & edges


def zeros(ctx: GenContext) -> np.ndarray:
    return np.zeros((ctx.h, ctx.w), dtype=bool)


def point_mask(ctx: GenContext, pts) -> np.ndarray:
    m = zeros(ctx)
    for x, y in pts:
        if ctx.wrap:
            m[int(y) % ctx.h, int(x) % ctx.w] = True
        elif 0 <= x < ctx.w and 0 <= y < ctx.h:
            m[int(y), int(x)] = True
    return m


def edges(labels: np.ndarray, wrap: bool) -> dict[str, np.ndarray]:
    """Region pixels whose neighbour in a direction belongs to another region.

    ``labels`` < 0 marks gaps (mortar, holes).  Returns masks keyed
    ``top``, ``left``, ``bottom``, ``right``.
    """
    valid = labels >= 0

    def diff(dx: int, dy: int) -> np.ndarray:
        nb = pa.neighbour(labels, dx, dy, wrap, fill=-7)
        return valid & (nb != labels)

    return {"top": diff(0, -1), "left": diff(-1, 0), "bottom": diff(0, 1), "right": diff(1, 0)}


def mask_edges(mask: np.ndarray, wrap: bool) -> dict[str, np.ndarray]:
    return {"top": pa.edge_of(mask, 0, -1, wrap), "left": pa.edge_of(mask, -1, 0, wrap),
            "bottom": pa.edge_of(mask, 0, 1, wrap), "right": pa.edge_of(mask, 1, 0, wrap)}


def bevel_masks(e: dict[str, np.ndarray], width: int = 1, labels: np.ndarray | None = None,
                wrap: bool = True) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """Split region edges into (lit, dark, lit_corner, dark_corner) for top-left light.

    Top/left edges are lit, bottom/right edges dark.  The ambiguous
    top-right and bottom-left corner pixels stay neutral so bevels meet
    in a clean mitre.  ``width`` > 1 (large textures) widens the bands.
    """
    top, left, bottom, right = e["top"], e["left"], e["bottom"], e["right"]
    if width > 1 and labels is not None:
        valid = labels >= 0
        for k in range(2, width + 1):
            top = top | (valid & (pa.neighbour(labels, 0, -k, wrap, fill=-7) != labels))
            left = left | (valid & (pa.neighbour(labels, -k, 0, wrap, fill=-7) != labels))
            bottom = bottom | (valid & (pa.neighbour(labels, 0, k, wrap, fill=-7) != labels))
            right = right | (valid & (pa.neighbour(labels, k, 0, wrap, fill=-7) != labels))
    lit = top | left
    dark = (bottom | right) & ~lit
    tr = top & right & ~left & ~bottom
    bl = bottom & left & ~top & ~right
    lit &= ~(tr | bl)
    lit_corner = e["top"] & e["left"]
    dark_corner = e["bottom"] & e["right"] & ~e["top"] & ~e["left"]
    return lit, dark, lit_corner, dark_corner


def raised_light(mask: np.ndarray, wrap: bool) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """(lit rim, dark rim, drop shadow outside) for a raised shape."""
    e = mask_edges(mask, wrap)
    lit = e["top"] | e["left"]
    dark = (e["bottom"] | e["right"]) & ~lit
    below = pa.neighbour(mask, 0, -1, wrap, fill=False) & ~mask
    rightof = pa.neighbour(mask, -1, 0, wrap, fill=False) & ~mask
    return lit, dark, below | rightof


def sunken_light(mask: np.ndarray, wrap: bool) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """(shadowed inner wall, lit inner wall, lit lip outside) for a groove/hole."""
    e = mask_edges(mask, wrap)
    shade = e["top"] | e["left"]
    lit = (e["bottom"] | e["right"]) & ~shade
    below = pa.neighbour(mask, 0, -1, wrap, fill=False) & ~mask
    rightof = pa.neighbour(mask, -1, 0, wrap, fill=False) & ~mask
    return shade, lit, below | rightof


def region_depth(labels: np.ndarray, wrap: bool, max_d: int = 12) -> np.ndarray:
    """4-connected distance of each region pixel to its region's border (edge = 0)."""
    inner = labels >= 0
    depth = np.zeros(labels.shape, dtype=np.int32)
    for _ in range(max_d):
        nxt = inner.copy()
        for dx, dy in N4:
            nxt &= pa.neighbour(inner, dx, dy, wrap, fill=False)
            nxt &= pa.neighbour(labels, dx, dy, wrap, fill=-7) == labels
        if not nxt.any():
            break
        depth += nxt
        inner = nxt
    return depth


def mask_depth(mask: np.ndarray, wrap: bool, max_d: int = 12) -> np.ndarray:
    return region_depth(np.where(mask, 0, -1), wrap, max_d)


def region_coords(labels: np.ndarray, wrap: bool) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Offsets of each pixel from its region's centroid (wrap-aware) and region radius."""
    h, w = labels.shape
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    valid = labels >= 0
    n = int(labels.max()) + 1 if valid.any() else 1
    lab = np.where(valid, labels, n).ravel()
    cnt = np.bincount(lab, minlength=n + 1).astype(np.float64)
    if wrap:
        def circ(coord, period):
            ang = coord.ravel() * 2 * np.pi / period
            s = np.bincount(lab, np.sin(ang), minlength=n + 1)
            c = np.bincount(lab, np.cos(ang), minlength=n + 1)
            centre = (np.arctan2(s, c) % (2 * np.pi)) * period / (2 * np.pi)
            d = coord - centre[lab].reshape(h, w)
            return (d + period / 2) % period - period / 2
        rx = circ(xs, w)
        ry = circ(ys, h)
    else:
        cx = np.bincount(lab, xs.ravel(), minlength=n + 1) / np.maximum(cnt, 1)
        cy = np.bincount(lab, ys.ravel(), minlength=n + 1) / np.maximum(cnt, 1)
        rx = xs - cx[lab].reshape(h, w)
        ry = ys - cy[lab].reshape(h, w)
    rad = np.sqrt(cnt / np.pi)[lab].reshape(h, w)
    rx[~valid] = 0
    ry[~valid] = 0
    return rx, ry, np.maximum(rad, 0.5)


def dome_light(labels: np.ndarray, wrap: bool) -> tuple[np.ndarray, np.ndarray]:
    """(light, dome) for rounded regions: light in -1..1 (top-left positive), dome 0..1."""
    rx, ry, rad = region_coords(labels, wrap)
    light = np.clip(-(rx + ry) / (1.25 * rad), -1, 1)
    r = np.sqrt(rx * rx + ry * ry) / rad
    dome = np.clip(1 - r * r, 0, 1)
    return light, dome


# --------------------------------------------------------------- layouts


def partition(total: int, rng: np.random.Generator, options, weights=None) -> list[int]:
    """Random list of segment lengths drawn from ``options`` that sums to ``total``."""
    opts = sorted({int(o) for o in options if int(o) > 0})
    if not opts:
        return [total]
    wmap = {o: 1.0 for o in opts}
    if weights is not None:
        for o, wt in zip([int(o) for o in options], weights):
            if o in wmap:
                wmap[o] = float(wt)
    reach = [False] * (total + 1)
    reach[0] = True
    for t in range(1, total + 1):
        reach[t] = any(t - o >= 0 and reach[t - o] for o in opts)
    if not reach[total]:
        out, rest = [], total
        while rest > 0:
            o = min(opts, key=lambda v: abs(v - rest)) if rest < max(opts) * 2 else int(rng.choice(opts))
            o = min(o, rest)
            out.append(o)
            rest -= o
        return out
    out, rest = [], total
    while rest > 0:
        cand = [o for o in opts if o <= rest and reach[rest - o]]
        p = np.array([wmap[o] for o in cand], dtype=np.float64)
        o = cand[int(rng.choice(len(cand), p=p / p.sum()))]
        out.append(o)
        rest -= o
    rng.shuffle(out)
    return out


def split_even(total: int, n: int, gap: int) -> list[tuple[int, int]]:
    """Split ``total`` into ``n`` symmetric segments separated by ``gap``.

    Returns (start, length) pairs; leftover pixels go to the middle
    segments first so the arrangement stays mirror-symmetric.
    """
    n = max(1, n)
    free = total - gap * (n - 1)
    base = max(1, free // n)
    lens = [base] * n
    extra = free - base * n
    pairs = sorted({(min(i, n - 1 - i), max(i, n - 1 - i)) for i in range(n)},
                   key=lambda p: abs(p[0] - (n - 1) / 2))
    guard = 0
    while extra > 0 and guard < 4 * total + 8:
        for a, b in pairs:
            if extra <= 0:
                break
            if a == b:
                lens[a] += 1
                extra -= 1
            elif extra >= 2:
                lens[a] += 1
                lens[b] += 1
                extra -= 2
            elif all(p[0] != p[1] for p in pairs):
                lens[a] += 1      # odd leftover and no centre segment
                extra -= 1
        guard += 1
    out, pos = [], 0
    for ln in lens:
        out.append((pos, ln))
        pos += ln + gap
    return out


def nearest_divisor(n: int, target: float, lo: int = 1) -> int:
    divs = [d for d in range(max(1, lo), n + 1) if n % d == 0]
    if not divs:
        return n
    return min(divs, key=lambda d: (abs(math.log(d / max(target, 1e-6))), d))


# ---------------------------------------------------------------- surface


def surface_offsets(gen, ctx: GenContext, key: str, mask: np.ndarray, spread: int = 1,
                    freq_mul: float = 1.0, flat: float = 0.0, octaves: int | None = None,
                    aspect: tuple[float, float] | None = None, min_size: int | None = None,
                    kind: str = "perlin") -> np.ndarray:
    """Clustered integer offsets in ``-spread..spread`` inside ``mask``.

    The distribution follows the style profile's tone weights; ``flat``
    (0..1) moves weight to the neutral level for smoother surfaces.
    """
    out = np.zeros((ctx.h, ctx.w), dtype=np.int32)
    if spread <= 0 or not mask.any():
        return out
    n = 2 * spread + 1
    f = gen.height_field(ctx, key, freq_mul, octaves, kind=kind, aspect=aspect)
    w = resample_weights(ctx.data.get("weights", [1.0]), n)
    if flat > 0:
        w = w * (1 - flat)
        w[spread] += flat
        w = w / w.sum()
    q = pa.quantize_field(f, w, mask) - spread
    q[~mask] = 0
    ms = gen.min_cluster(ctx) if min_size is None else min_size
    if ms > 1:
        q = pa.remove_small_clusters(q, ms, ctx.wrap, mask)
    q[~mask] = 0
    return q.astype(np.int32)


def noise_keep(gen, ctx: GenContext, key: str, amount: float, freq_mul: float = 1.3) -> np.ndarray:
    """Low-frequency on/off mask (keeps runs rather than single pixels)."""
    if amount >= 1:
        return np.ones((ctx.h, ctx.w), dtype=bool)
    if amount <= 0:
        return zeros(ctx)
    f = gen.height_field(ctx, key, freq_mul, 1)
    cut = np.quantile(f, 1 - amount)
    return f >= cut


# ---------------------------------------------------------------- drawing


def line_mask(ctx: GenContext, pts: list[tuple[float, float]], closed: bool = False) -> np.ndarray:
    """Polyline through ``pts`` (Bresenham, wrap-aware)."""
    m = zeros(ctx)
    ipts = [(int(round(x)), int(round(y))) for x, y in pts]
    segs = list(zip(ipts, ipts[1:] + ipts[:1])) if closed else list(zip(ipts, ipts[1:]))
    if len(ipts) == 1:
        segs = [(ipts[0], ipts[0])]
    for (x0, y0), (x1, y1) in segs:
        for x, y in pa.line_points(x0, y0, x1, y1):
            if ctx.wrap:
                m[y % ctx.h, x % ctx.w] = True
            elif 0 <= x < ctx.w and 0 <= y < ctx.h:
                m[y, x] = True
    return m


def thicken(mask: np.ndarray, n: int, wrap: bool = False) -> np.ndarray:
    """Grow a mask by ``n - 1`` pixels toward the bottom-right (stroke width ``n``)."""
    out = mask.copy()
    for _ in range(max(0, n - 1)):
        out = out | pa.neighbour(out, -1, 0, wrap, fill=False) | pa.neighbour(out, 0, -1, wrap, fill=False) \
            | pa.neighbour(out, -1, -1, wrap, fill=False)
    return out


def four_connect(mask: np.ndarray, rng: np.random.Generator | None = None, wrap: bool = False) -> np.ndarray:
    """Fill diagonal-only steps so a line becomes 4-connected (reads as a solid groove)."""
    out = mask.copy()
    dr = mask & pa.neighbour(mask, 1, 1, wrap, fill=False) & ~pa.neighbour(mask, 1, 0, wrap, fill=False) \
        & ~pa.neighbour(mask, 0, 1, wrap, fill=False)
    dl = mask & pa.neighbour(mask, -1, 1, wrap, fill=False) & ~pa.neighbour(mask, -1, 0, wrap, fill=False) \
        & ~pa.neighbour(mask, 0, 1, wrap, fill=False)
    # add the pixel below the upper end of each diagonal step
    out |= pa.neighbour(dr, 0, -1, wrap, fill=False)
    out |= pa.neighbour(dl, 0, -1, wrap, fill=False)
    return out


def protect(ctx: GenContext, mask: np.ndarray) -> None:
    ctx.canvas.tag("protect")[mask] = True


def base_to_role(level: np.ndarray, K: int, n_role: int, lo: int = 0, hi: int | None = None) -> np.ndarray:
    """Map base-ramp levels (0..K+1) onto another ramp of ``n_role`` levels."""
    hi = n_role - 1 if hi is None else hi
    t = np.clip(np.asarray(level, dtype=np.float64) / max(1, K + 1), 0, 1)
    return np.clip(np.rint(lo + t * (hi - lo)), 0, n_role - 1).astype(np.int32)


def random_walk(rng: np.random.Generator, x: float, y: float, ang: float, length: int,
                turn: float = 0.35, allowed: np.ndarray | None = None, wrap: bool = True,
                w: int = 16, h: int = 16) -> list[tuple[int, int]]:
    """A jagged 4-connected walk; stops when it leaves ``allowed``."""
    pts: list[tuple[int, int]] = []
    px = py = None
    for _ in range(max(1, length)):
        ix, iy = int(math.floor(x)), int(math.floor(y))
        if wrap:
            ix %= w
            iy %= h
        elif not (0 <= ix < w and 0 <= iy < h):
            break
        if allowed is not None and not allowed[iy, ix]:
            break
        if px is not None and ix != px and iy != py:
            # keep it 4-connected: step through an orthogonal neighbour
            cx, cy = (ix, py) if rng.random() < 0.5 else (px, iy)
            if allowed is None or allowed[cy, cx]:
                pts.append((cx, cy))
        if not pts or pts[-1] != (ix, iy):
            pts.append((ix, iy))
        px, py = ix, iy
        ang += rng.normal(0, turn)
        x += math.cos(ang)
        y += math.sin(ang)
    return pts


# ------------------------------------------------------------- scattering


def scatter(ctx: GenContext, rng: np.random.Generator, count: int, min_dist: float,
            mask: np.ndarray | None = None) -> list[tuple[int, int]]:
    """Up to ``count`` random points inside ``mask``, at least ``min_dist`` apart.

    Occupancy-grid sampling: linear in the number of pixels, so it stays
    fast at 256x256 where dart throwing with distance checks does not.
    """
    if count <= 0:
        return []
    H, W = ctx.h, ctx.w
    cand = np.ones((H, W), dtype=bool) if mask is None else mask
    ys, xs = np.nonzero(cand)
    if not len(xs):
        return []
    r = max(0.0, float(min_dist))
    ri = int(math.ceil(r))
    oy, ox = np.mgrid[-ri:ri + 1, -ri:ri + 1]
    keep = (ox * ox + oy * oy) < r * r
    ox, oy = ox[keep], oy[keep]
    blocked = np.zeros((H, W), dtype=bool)
    out: list[tuple[int, int]] = []
    for i in rng.permutation(len(xs)):
        x, y = int(xs[i]), int(ys[i])
        if blocked[y, x]:
            continue
        out.append((x, y))
        if len(out) >= count:
            break
        if len(ox):
            if ctx.wrap:
                blocked[(y + oy) % H, (x + ox) % W] = True
            else:
                bx, by = x + ox, y + oy
                ok = (bx >= 0) & (bx < W) & (by >= 0) & (by < H)
                blocked[by[ok], bx[ok]] = True
    return out


def clusters(ctx: GenContext, key: str, count: int, size_range: tuple[int, int],
             mask: np.ndarray | None = None, min_dist: float | None = None, compact: float = 0.75,
             bias: tuple[float, float] = (0.0, 0.0)) -> list[list[tuple[int, int]]]:
    """Grow ``count`` small blobs at well-spaced positions (fast ``place_clusters``)."""
    if count <= 0:
        return []
    rng = ctx.rng(key)
    if min_dist is None:
        min_dist = math.sqrt(ctx.w * ctx.h / max(1, count)) * 0.72
    pts = scatter(ctx, rng, count, min_dist, mask)
    blocked = None if mask is None else ~mask
    out = []
    for (x, y) in pts:
        size = int(rng.integers(size_range[0], size_range[1] + 1))
        if size <= 1:
            out.append([(x, y)])
            continue
        out.append(pa.grow_cluster(rng, (x, y), size, ctx.w, ctx.h, ctx.wrap, blocked, compact, bias))
    return out
