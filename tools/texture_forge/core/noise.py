"""Tileable, dependency-free noise.

All noise here is periodic over the texture, so generated block textures
tile seamlessly in-game.  Frequencies are expressed as *lattice cells
across the whole texture* and are therefore integers.

Noise is deliberately low resolution: a 16x16 texture typically uses a
3-6 cell lattice.  The smooth field is later quantized into a handful of
levels by :mod:`core.pixel_art`, which is what gives Minecraft-style
pixel clusters instead of per-pixel static.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

# --------------------------------------------------------------------- helpers


def grid(w: int, h: int) -> tuple[np.ndarray, np.ndarray]:
    """Pixel-centre coordinates normalised to [0, 1)."""
    xs = (np.arange(w, dtype=np.float64) + 0.5) / w
    ys = (np.arange(h, dtype=np.float64) + 0.5) / h
    return np.meshgrid(xs, ys)


def normalize(a: np.ndarray) -> np.ndarray:
    lo, hi = float(a.min()), float(a.max())
    if hi - lo < 1e-9:
        return np.zeros_like(a, dtype=np.float64)
    return (a - lo) / (hi - lo)


def _smooth(t: np.ndarray) -> np.ndarray:
    return t * t * (3.0 - 2.0 * t)


def _quintic(t: np.ndarray) -> np.ndarray:
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)


def _cells(f: float) -> int:
    return max(1, int(round(f)))


# --------------------------------------------------------------- lattice noise


def _sample_value(lat: np.ndarray, u: np.ndarray, v: np.ndarray) -> np.ndarray:
    cy, cx = lat.shape
    u = np.mod(u, cx)
    v = np.mod(v, cy)
    x0 = np.floor(u).astype(np.int64)
    y0 = np.floor(v).astype(np.int64)
    sx = _smooth(u - x0)
    sy = _smooth(v - y0)
    x0 %= cx
    y0 %= cy
    x1 = (x0 + 1) % cx
    y1 = (y0 + 1) % cy
    top = lat[y0, x0] * (1 - sx) + lat[y0, x1] * sx
    bot = lat[y1, x0] * (1 - sx) + lat[y1, x1] * sx
    return top * (1 - sy) + bot * sy


def _sample_perlin(grad: np.ndarray, u: np.ndarray, v: np.ndarray) -> np.ndarray:
    cy, cx = grad.shape[:2]
    u = np.mod(u, cx)
    v = np.mod(v, cy)
    x0 = np.floor(u).astype(np.int64)
    y0 = np.floor(v).astype(np.int64)
    tx = u - x0
    ty = v - y0
    x0 %= cx
    y0 %= cy
    x1 = (x0 + 1) % cx
    y1 = (y0 + 1) % cy

    def dot(ix, iy, dx, dy):
        g = grad[iy, ix]
        return g[..., 0] * dx + g[..., 1] * dy

    n00 = dot(x0, y0, tx, ty)
    n10 = dot(x1, y0, tx - 1, ty)
    n01 = dot(x0, y1, tx, ty - 1)
    n11 = dot(x1, y1, tx - 1, ty - 1)
    sx = _quintic(tx)
    sy = _quintic(ty)
    top = n00 * (1 - sx) + n10 * sx
    bot = n01 * (1 - sx) + n11 * sx
    return (top * (1 - sy) + bot * sy) * 0.70710678 + 0.5


def value_noise(w: int, h: int, rng: np.random.Generator, fx: float, fy: float | None = None,
                offset: tuple[np.ndarray, np.ndarray] | None = None) -> np.ndarray:
    """Smoothly interpolated random lattice (``fx`` x ``fy`` cells)."""
    cx, cy = _cells(fx), _cells(fy if fy is not None else fx)
    lat = rng.random((cy, cx))
    x, y = grid(w, h)
    if offset is not None:
        x = x + offset[0]
        y = y + offset[1]
    return _sample_value(lat, x * cx, y * cy)


def perlin_noise(w: int, h: int, rng: np.random.Generator, fx: float, fy: float | None = None,
                 offset: tuple[np.ndarray, np.ndarray] | None = None) -> np.ndarray:
    """Gradient (Perlin) noise, tileable, roughly in [0, 1]."""
    cx, cy = _cells(fx), _cells(fy if fy is not None else fx)
    ang = rng.random((cy, cx)) * 2 * np.pi
    grad = np.stack([np.cos(ang), np.sin(ang)], axis=-1)
    x, y = grid(w, h)
    if offset is not None:
        x = x + offset[0]
        y = y + offset[1]
    return np.clip(_sample_perlin(grad, x * cx, y * cy), 0.0, 1.0)


NOISE_KINDS = ("value", "perlin")


def warp_offsets(w: int, h: int, rng: np.random.Generator, freq: float, amount: float
                 ) -> tuple[np.ndarray, np.ndarray]:
    """Domain-warp offsets (in normalised texture units)."""
    if amount <= 0:
        z = np.zeros((h, w))
        return z, z
    ox = (value_noise(w, h, rng, freq) - 0.5) * 2 * amount
    oy = (value_noise(w, h, rng, freq) - 0.5) * 2 * amount
    return ox, oy


def fbm(w: int, h: int, rng: np.random.Generator, freq: float, octaves: int = 3,
        persistence: float = 0.5, kind: str = "value", aspect: tuple[float, float] = (1.0, 1.0),
        warp: float = 0.0, warp_freq: float = 2.0) -> np.ndarray:
    """Fractal sum of tileable noise, normalised to [0, 1].

    ``aspect`` multiplies the x / y frequency, e.g. ``(1, 3)`` squashes the
    features vertically for horizontal strata.
    """
    sampler = perlin_noise if kind == "perlin" else value_noise
    offset = warp_offsets(w, h, rng, warp_freq, warp) if warp > 0 else None
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for octave in range(max(1, octaves)):
        f = freq * (2 ** octave)
        fx = min(f * aspect[0], w)
        fy = min(f * aspect[1], h)
        total += amp * sampler(w, h, rng, fx, fy, offset)
        norm += amp
        amp *= persistence
        if fx >= w and fy >= h:
            break
    return normalize(total / norm)


# --------------------------------------------------------------- cellular noise


@dataclass
class CellularResult:
    f1: np.ndarray          # distance to nearest feature point (normalised)
    f2: np.ndarray          # distance to second nearest
    cell: np.ndarray        # index of nearest feature point
    points: np.ndarray      # feature points in normalised coords, shape (n, 2)

    @property
    def edge(self) -> np.ndarray:
        """Small near Voronoi borders, large at cell centres."""
        return self.f2 - self.f1


def jittered_points(rng: np.random.Generator, gx: int, gy: int, jitter: float = 0.9) -> np.ndarray:
    gx, gy = max(1, gx), max(1, gy)
    iy, ix = np.mgrid[0:gy, 0:gx]
    px = (ix + 0.5 + (rng.random((gy, gx)) - 0.5) * jitter) / gx
    py = (iy + 0.5 + (rng.random((gy, gx)) - 0.5) * jitter) / gy
    return np.stack([px.ravel() % 1.0, py.ravel() % 1.0], axis=1)


def cellular(w: int, h: int, rng: np.random.Generator, gx: int, gy: int | None = None,
             jitter: float = 0.9, aspect: tuple[float, float] = (1.0, 1.0),
             points: np.ndarray | None = None,
             offset: tuple[np.ndarray, np.ndarray] | None = None) -> CellularResult:
    """Tileable Worley noise over a jittered ``gx`` x ``gy`` grid of points."""
    gy = gx if gy is None else gy
    if points is None:
        points = jittered_points(rng, gx, gy, jitter)
    x, y = grid(w, h)
    if offset is not None:
        x = np.mod(x + offset[0], 1.0)
        y = np.mod(y + offset[1], 1.0)
    dx = np.abs(x[..., None] - points[:, 0])
    dy = np.abs(y[..., None] - points[:, 1])
    dx = np.minimum(dx, 1.0 - dx) * aspect[0]
    dy = np.minimum(dy, 1.0 - dy) * aspect[1]
    d = np.sqrt(dx * dx + dy * dy)
    if d.shape[-1] == 1:
        order = np.zeros(d.shape, dtype=np.int64)
        f1 = d[..., 0]
        f2 = f1 + 1.0
    else:
        order = np.argpartition(d, 1, axis=-1)[..., :2]
        f1 = np.take_along_axis(d, order[..., :1], axis=-1)[..., 0]
        f2 = np.take_along_axis(d, order[..., 1:2], axis=-1)[..., 0]
    spacing = 1.0 / np.sqrt(max(1, len(points)))
    return CellularResult(f1 / spacing, f2 / spacing, order[..., 0], points)


# ------------------------------------------------------------------- sampling


def torus_distance(ax: float, ay: float, bx: np.ndarray, by: np.ndarray, w: int, h: int) -> np.ndarray:
    dx = np.abs(bx - ax)
    dy = np.abs(by - ay)
    dx = np.minimum(dx, w - dx)
    dy = np.minimum(dy, h - dy)
    return np.sqrt(dx * dx + dy * dy)


def poisson_points(w: int, h: int, rng: np.random.Generator, count: int, min_dist: float,
                   wrap: bool = True, margin: int = 0, attempts: int = 60) -> list[tuple[int, int]]:
    """Dart-throwing Poisson disk sample of up to ``count`` integer points.

    Accepted points are kept in a spatial hash, so each candidate is only
    compared with its neighbourhood (fast even for thousands of points).
    """
    pts: list[tuple[int, int]] = []
    if count <= 0:
        return pts
    # cells at least min_dist wide that divide the texture exactly (so wrapping neighbours line up)
    gw = max(1, int(w // max(1.0, float(min_dist))))
    gh = max(1, int(h // max(1.0, float(min_dist))))
    cw, ch = w / gw, h / gh
    buckets: dict[tuple[int, int], list[tuple[int, int]]] = {}
    md2 = float(min_dist) * float(min_dist)
    tries = 0
    lo_x, hi_x = margin, max(margin + 1, w - margin)
    lo_y, hi_y = margin, max(margin + 1, h - margin)
    while len(pts) < count and tries < count * attempts:
        tries += 1
        px = int(rng.integers(lo_x, hi_x))
        py = int(rng.integers(lo_y, hi_y))
        cx, cy = min(gw - 1, int(px // cw)), min(gh - 1, int(py // ch))
        keys = set()
        for gy in range(cy - 1, cy + 2):
            for gx in range(cx - 1, cx + 2):
                if wrap:
                    keys.add((gx % gw, gy % gh))
                elif 0 <= gx < gw and 0 <= gy < gh:
                    keys.add((gx, gy))
        ok = True
        for key in keys:
            for qx, qy in buckets.get(key, ()):
                dx = abs(qx - px)
                dy = abs(qy - py)
                if wrap:
                    dx = min(dx, w - dx)
                    dy = min(dy, h - dy)
                if dx * dx + dy * dy < md2:
                    ok = False
                    break
            if not ok:
                break
        if not ok:
            continue
        pts.append((px, py))
        buckets.setdefault((cx, cy), []).append((px, py))
    return pts
