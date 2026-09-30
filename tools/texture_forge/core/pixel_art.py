"""Pixel-art primitives.

Everything here works on small integer grids and never introduces
blending: no anti-aliasing, no blur, no resampling filters.  These are
the building blocks that turn smooth noise into clustered, hand-drawn
looking pixels.
"""
from __future__ import annotations

import math
from collections import deque

import numpy as np

from .palette import rgb_to_oklab

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
N8 = N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))


# ------------------------------------------------------------ quantization


def quantize_field(field: np.ndarray, weights: list[float] | np.ndarray,
                   mask: np.ndarray | None = None) -> np.ndarray:
    """Quantize a continuous field into ``len(weights)`` levels.

    Thresholds are chosen so level *i* covers roughly ``weights[i]`` of the
    (masked) pixels - this is how the tone distribution of a reference
    texture is transferred without copying any of its pixels.
    """
    w = np.asarray(weights, dtype=np.float64)
    w = w / w.sum() if w.sum() > 0 else np.full(len(w), 1.0 / len(w))
    vals = field[mask] if mask is not None else field.ravel()
    if vals.size == 0:
        return np.zeros(field.shape, dtype=np.int32)
    cuts = np.quantile(vals, np.clip(np.cumsum(w)[:-1], 0, 1))
    return np.searchsorted(cuts, field, side="right").astype(np.int32)


def bell_weights(n: int, center: float = 0.5, spread: float = 0.28) -> np.ndarray:
    """A soft tone distribution peaking at ``center`` (0 dark .. 1 light)."""
    x = np.linspace(0, 1, n)
    w = np.exp(-((x - center) ** 2) / (2 * spread * spread)) + 0.08
    return w / w.sum()


# ------------------------------------------------------- connected components


def label_components(grid: np.ndarray, wrap: bool = True, mask: np.ndarray | None = None,
                     connectivity: int = 4) -> tuple[np.ndarray, list[int]]:
    """Label equal-valued connected regions. Returns ``(labels, sizes)``.

    Pixels outside ``mask`` get label -1.
    """
    h, w = grid.shape
    g = grid.tolist()
    m = mask.tolist() if mask is not None else None
    labels = [[-1] * w for _ in range(h)]
    sizes: list[int] = []
    nbrs = N4 if connectivity == 4 else N8
    for sy in range(h):
        for sx in range(w):
            if labels[sy][sx] != -1 or (m is not None and not m[sy][sx]):
                continue
            lab = len(sizes)
            val = g[sy][sx]
            labels[sy][sx] = lab
            q = deque([(sx, sy)])
            count = 0
            while q:
                x, y = q.popleft()
                count += 1
                for dx, dy in nbrs:
                    nx, ny = x + dx, y + dy
                    if wrap:
                        nx %= w
                        ny %= h
                    elif not (0 <= nx < w and 0 <= ny < h):
                        continue
                    if labels[ny][nx] != -1 or g[ny][nx] != val:
                        continue
                    if m is not None and not m[ny][nx]:
                        continue
                    labels[ny][nx] = lab
                    q.append((nx, ny))
            sizes.append(count)
    return np.array(labels, dtype=np.int32), sizes


def remove_small_clusters(grid: np.ndarray, min_size: int, wrap: bool = True,
                          mask: np.ndarray | None = None, protect: np.ndarray | None = None,
                          passes: int = 2) -> np.ndarray:
    """Merge regions smaller than ``min_size`` into their dominant neighbour.

    This is the main "no salt-and-pepper" rule: isolated specks become part
    of the surrounding cluster unless ``protect`` marks them intentional.
    """
    if min_size <= 1:
        return grid
    out = grid.copy()
    h, w = out.shape
    for _ in range(passes):
        labels, sizes = label_components(out, wrap, mask)
        small = [i for i, s in enumerate(sizes) if s < min_size]
        if not small:
            break
        changed = False
        small_set = set(small)
        ys, xs = np.nonzero(np.isin(labels, list(small_set)))
        members: dict[int, list[tuple[int, int]]] = {}
        for y, x in zip(ys.tolist(), xs.tolist()):
            members.setdefault(int(labels[y, x]), []).append((x, y))
        for lab, pix in members.items():
            if protect is not None and any(protect[y, x] for x, y in pix):
                continue
            votes: dict[int, int] = {}
            for x, y in pix:
                for dx, dy in N4:
                    nx, ny = x + dx, y + dy
                    if wrap:
                        nx %= w
                        ny %= h
                    elif not (0 <= nx < w and 0 <= ny < h):
                        continue
                    if labels[ny, nx] == lab:
                        continue
                    if mask is not None and not mask[ny, nx]:
                        continue
                    v = int(out[ny, nx])
                    votes[v] = votes.get(v, 0) + 1
            if votes:
                best = max(votes.items(), key=lambda kv: (kv[1], -abs(kv[0] - int(out[pix[0][1], pix[0][0]]))))[0]
                for x, y in pix:
                    out[y, x] = best
                changed = True
        if not changed:
            break
    return out


def isolated_pixels(grid: np.ndarray, wrap: bool = True, mask: np.ndarray | None = None) -> np.ndarray:
    """Pixels that differ from all four orthogonal neighbours."""
    diff = np.ones(grid.shape, dtype=bool)
    for dx, dy in N4:
        nb = neighbour(grid, dx, dy, wrap)
        diff &= nb != grid
    if mask is not None:
        diff &= mask
    return diff


def mode_filter(grid: np.ndarray, wrap: bool = True, mask: np.ndarray | None = None,
                protect: np.ndarray | None = None, min_votes: int = 3) -> np.ndarray:
    """Replace isolated pixels by their neighbours' majority value.

    A conservative noise reducer: a pixel changes only when at least
    ``min_votes`` of its 4 neighbours agree on another value.
    """
    iso = isolated_pixels(grid, wrap, mask)
    if protect is not None:
        iso &= ~protect
    if not iso.any():
        return grid
    out = grid.copy()
    nbs = np.stack([neighbour(grid, dx, dy, wrap) for dx, dy in N4], axis=0)
    for y, x in zip(*np.nonzero(iso)):
        vals, counts = np.unique(nbs[:, y, x], return_counts=True)
        i = int(np.argmax(counts))
        if counts[i] >= min_votes:
            out[y, x] = vals[i]
    return out


# ------------------------------------------------------------------ shifts


def neighbour(a: np.ndarray, dx: int, dy: int, wrap: bool = True, fill=0) -> np.ndarray:
    """Value of the neighbour at offset (dx, dy) for every pixel."""
    if wrap:
        return np.roll(np.roll(a, -dy, axis=0), -dx, axis=1)
    h, w = a.shape[:2]
    out = np.full_like(a, fill)
    ys = slice(max(0, -dy), min(h, h - dy))
    xs = slice(max(0, -dx), min(w, w - dx))
    ys2 = slice(max(0, dy), min(h, h + dy))
    xs2 = slice(max(0, dx), min(w, w + dx))
    out[ys, xs] = a[ys2, xs2]
    return out


def emboss(height: np.ndarray, light: tuple[int, int] = (-1, -1), wrap: bool = True) -> np.ndarray:
    """Slope toward the light: positive where a surface faces it (top-left by default)."""
    toward = neighbour(height, light[0], light[1], wrap, fill=0)
    away = neighbour(height, -light[0], -light[1], wrap, fill=0)
    return (height - toward) * 0.6 + (away - height) * 0.4


def edge_of(mask: np.ndarray, dx: int, dy: int, wrap: bool = False) -> np.ndarray:
    """Pixels of ``mask`` whose neighbour at (dx, dy) is outside the mask."""
    return mask & ~neighbour(mask, dx, dy, wrap, fill=False)


def outline(mask: np.ndarray, wrap: bool = False, diagonal: bool = False) -> np.ndarray:
    """Pixels just outside ``mask``."""
    out = np.zeros_like(mask)
    for dx, dy in (N8 if diagonal else N4):
        out |= neighbour(mask, dx, dy, wrap, fill=False)
    return out & ~mask


def dilate(mask: np.ndarray, wrap: bool = False, diagonal: bool = False) -> np.ndarray:
    return mask | outline(mask, wrap, diagonal)


def erode(mask: np.ndarray, wrap: bool = False) -> np.ndarray:
    out = mask.copy()
    for dx, dy in N4:
        out &= neighbour(mask, dx, dy, wrap, fill=False)
    return out


def sprite_light(mask: np.ndarray, light: tuple[int, int] = (-1, -1)) -> np.ndarray:
    """-1 / 0 / +1 shading for a flat sprite shape lit from ``light``.

    Pixels on the lit rim get +1, pixels on the far rim get -1.
    """
    lit = edge_of(mask, light[0], 0) | edge_of(mask, 0, light[1])
    far = edge_of(mask, -light[0], 0) | edge_of(mask, 0, -light[1])
    out = np.zeros(mask.shape, dtype=np.int32)
    out[far] = -1
    out[lit & ~far] = 1
    return out


# ------------------------------------------------------------------ shapes


def grow_cluster(rng: np.random.Generator, start: tuple[int, int], size: int, w: int, h: int,
                 wrap: bool = True, blocked: np.ndarray | None = None, compact: float = 0.7,
                 bias: tuple[float, float] = (0.0, 0.0)) -> list[tuple[int, int]]:
    """Grow a blob of ``size`` pixels (Eden growth).

    ``compact`` near 1 favours candidates with many filled neighbours,
    producing chunky mineral-like clumps rather than spidery shapes.
    ``bias`` stretches growth in a direction.
    """
    sx, sy = start
    cells = {(sx % w, sy % h) if wrap else (sx, sy)}
    frontier: dict[tuple[int, int], int] = {}

    def add_frontier(x: int, y: int) -> None:
        for dx, dy in N4:
            nx, ny = x + dx, y + dy
            if wrap:
                nx %= w
                ny %= h
            elif not (0 <= nx < w and 0 <= ny < h):
                continue
            if (nx, ny) in cells:
                continue
            if blocked is not None and blocked[ny, nx]:
                continue
            frontier[(nx, ny)] = frontier.get((nx, ny), 0) + 1

    add_frontier(*next(iter(cells)))
    exponent = 1 + 3 * compact
    while len(cells) < size and frontier:
        keys = list(frontier.keys())
        weights = [float(frontier[k]) ** exponent for k in keys]
        if bias != (0.0, 0.0):
            cx = sum(c[0] for c in cells) / len(cells)
            cy = sum(c[1] for c in cells) / len(cells)
            weights = [wt * math.exp(min(3.0, max(-3.0, (k[0] - cx) * bias[0] + (k[1] - cy) * bias[1])))
                       for wt, k in zip(weights, keys)]
        # same draw as rng.choice(len(keys), p=weights), without numpy's per-call overhead
        u = rng.random() * sum(weights)
        acc = 0.0
        pick = keys[-1]
        for k, wt in zip(keys, weights):
            acc += wt
            if acc > u:
                pick = k
                break
        del frontier[pick]
        cells.add(pick)
        add_frontier(*pick)
    return sorted(cells)


def line_points(x0: int, y0: int, x1: int, y1: int) -> list[tuple[int, int]]:
    """Bresenham line (inclusive)."""
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return pts


def fill_polygon(points: list[tuple[float, float]], w: int, h: int) -> np.ndarray:
    """Rasterise a polygon by testing pixel centres (no anti-aliasing)."""
    ys, xs = np.mgrid[0:h, 0:w]
    px = xs + 0.5
    py = ys + 0.5
    inside = np.zeros((h, w), dtype=bool)
    n = len(points)
    for i in range(n):
        x0, y0 = points[i]
        x1, y1 = points[(i + 1) % n]
        if y0 == y1:
            continue
        cond = ((y0 > py) != (y1 > py))
        xint = (x1 - x0) * (py - y0) / (y1 - y0) + x0
        inside ^= cond & (px < xint)
    return inside


def stamp(mask: np.ndarray, pts, wrap: bool = False) -> None:
    h, w = mask.shape
    for x, y in pts:
        if wrap:
            mask[int(y) % h, int(x) % w] = True
        elif 0 <= x < w and 0 <= y < h:
            mask[int(y), int(x)] = True


# ----------------------------------------------------------- alpha cleanup


def alpha_cleanup(mask: np.ndarray, min_island: int = 2, fill_holes: bool = True) -> np.ndarray:
    """Remove stray opaque pixels and single-pixel holes from a sprite mask."""
    out = mask.copy()
    if min_island > 1:
        labels, sizes = label_components(out.astype(np.int32), wrap=False, mask=out, connectivity=8)
        for lab, s in enumerate(sizes):
            if s < min_island:
                out[labels == lab] = False
    if fill_holes:
        nb = sum(neighbour(out, dx, dy, False, fill=False).astype(np.int32) for dx, dy in N4)
        out |= (~out) & (nb == 4)
    return out


def threshold_alpha(alpha: np.ndarray, threshold: int, translucent: int | None = None) -> np.ndarray:
    """Snap alpha to fully opaque / transparent (or a single translucent step)."""
    out = np.where(alpha >= threshold, 255, 0).astype(np.uint8)
    if translucent is not None:
        mid = (alpha >= threshold) & (alpha < 250)
        out[mid] = translucent
    return out


# ----------------------------------------------------------- colour limits


def unique_colors(rgba: np.ndarray) -> np.ndarray:
    px = rgba.reshape(-1, rgba.shape[-1])
    if px.shape[1] == 4:
        px = px[px[:, 3] > 0]
    if len(px) == 0:
        return np.zeros((0, 3), dtype=np.uint8)
    return np.unique(px[:, :3], axis=0)


def limit_colors(rgba: np.ndarray, limit: int) -> np.ndarray:
    """Merge the closest colours (in OKLab, weighted by use) until ``limit`` remain."""
    if limit <= 0:
        return rgba
    out = rgba.copy()
    flat = out.reshape(-1, out.shape[-1])
    vis = flat[:, 3] > 0 if flat.shape[1] == 4 else np.ones(len(flat), bool)
    cols, inv, counts = np.unique(flat[vis, :3], axis=0, return_inverse=True, return_counts=True)
    inv = inv.ravel()
    if len(cols) <= limit:
        return out
    lab = rgb_to_oklab(cols.astype(np.float64))
    groups = [[i] for i in range(len(cols))]
    weight = counts.astype(np.float64)
    centre = lab.copy()
    alive = list(range(len(cols)))
    while len(alive) > limit:
        c = centre[alive]
        d = ((c[:, None, :] - c[None]) ** 2).sum(-1)
        # small groups are cheaper to merge: penalise by the lighter weight
        wmin = np.minimum(weight[alive][:, None], weight[alive][None])
        cost = d * (wmin ** 0.35)
        np.fill_diagonal(cost, np.inf)
        a, b = np.unravel_index(int(np.argmin(cost)), cost.shape)
        ia, ib = alive[a], alive[b]
        keep, drop = (ia, ib) if weight[ia] >= weight[ib] else (ib, ia)
        groups[keep].extend(groups[drop])
        weight[keep] += weight[drop]
        alive.remove(drop)
    mapping = np.arange(len(cols))
    for k in alive:
        for i in groups[k]:
            mapping[i] = k
    new_cols = cols[mapping]
    vis_px = flat[vis]
    vis_px[:, :3] = new_cols[inv]
    flat[vis] = vis_px
    return flat.reshape(out.shape)
