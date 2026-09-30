"""Shared machinery for all category generators.

A generator is a class with one method per layer (``layer_base``,
``layer_material``, ... see :data:`core.settings.LAYER_ORDER`).  The
helpers below implement the pixel-art "grammar" the categories share:
quantized height fields, bevel lighting, pixel clusters, cracks and
embedded mineral grains.
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.analyzer import TextureAnalysis
from core.layers import ROLE_IDS, GenContext
from core.settings import TextureSettings


class BaseGenerator:
    category = "base"
    #: palette anchor overrides per role, e.g. {"base": "kelp_green"}
    role_overrides: dict[str, str] = {}

    # ----------------------------------------------------------- setup
    def is_sprite(self, s: TextureSettings) -> bool:
        return s.is_sprite

    def body_levels(self, s: TextureSettings, a: TextureAnalysis) -> int:
        """Number of body tone levels (the ramp gets 2 extra: shadow and highlight)."""
        k = s.levels if s.levels > 0 else int(np.clip(a.levels, 3, 7))
        if s.style == "programmer":
            k = max(3, k - 1)
        elif s.style == "detailed":
            k = min(8, k + 1)
        if s.color_limit:
            k = min(k, max(3, s.color_limit - 6))
        return int(k)

    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        """Role -> ramp anchor name overrides for this category/variant."""
        return dict(self.role_overrides)

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": max(3, k), "accent": 5, "accent2": 4, "glow": 4}

    def prepare(self, ctx: GenContext) -> None:
        """Hook run before the layers."""

    def finish(self, ctx: GenContext) -> None:
        """Hook run after the Minecraft style filter, before colours are resolved."""

    # ------------------------------------------------------- utilities
    @staticmethod
    def K(ctx: GenContext) -> int:
        return ctx.data["K"]

    def feature_freq(self, ctx: GenContext, mul: float = 1.0) -> float:
        return max(1.0, ctx.analysis.feature_scale(ctx.size) * mul)

    def aspect(self, ctx: GenContext, extra_layering: float = 0.0) -> tuple[float, float]:
        ax, ay = ctx.analysis.aspect()
        infl = ctx.settings.source_influence
        ax = 1 + (ax - 1) * infl
        ay = 1 + (ay - 1) * infl
        ay *= 1 + 2.2 * extra_layering
        return ax, ay

    def height_field(self, ctx: GenContext, key: str, freq_mul: float = 1.0, octaves: int | None = None,
                     kind: str = "perlin", aspect: tuple[float, float] | None = None,
                     warp: float = 0.04, persistence: float | None = None) -> np.ndarray:
        s = ctx.settings
        if octaves is None:
            octaves = 1 + int(round(s.roughness * 2.2))
        if persistence is None:
            persistence = 0.3 + 0.35 * s.roughness
        return noise.fbm(ctx.w, ctx.h, ctx.rng(key), self.feature_freq(ctx, freq_mul), octaves,
                         persistence, kind, aspect or self.aspect(ctx), warp)

    def quantize_body(self, ctx: GenContext, field: np.ndarray, mask: np.ndarray | None = None,
                      weights: np.ndarray | None = None) -> np.ndarray:
        """Continuous field -> body levels 1..K (0 and K+1 stay free for shading)."""
        w = ctx.data["weights"] if weights is None else weights
        return pa.quantize_field(field, w, mask) + 1

    def cleanup(self, ctx: GenContext, grid: np.ndarray, min_size: int | None = None,
                mask: np.ndarray | None = None) -> np.ndarray:
        if min_size is None:
            min_size = self.min_cluster(ctx)
        return pa.remove_small_clusters(grid, min_size, ctx.wrap, mask)

    def min_cluster(self, ctx: GenContext) -> int:
        s = ctx.settings
        base = 3 - 1.4 * s.noise
        if s.style == "programmer":
            base += 1
        elif s.style == "detailed":
            base -= 0.5
        return max(1, int(round(base * max(1.0, ctx.scale) ** 1.5)))

    def smooth_mask(self, ctx: GenContext, key: str, coverage: float, freq_mul: float = 1.0,
                    octaves: int = 2, mask: np.ndarray | None = None, kind: str = "value",
                    aspect: tuple[float, float] | None = None, warp: float = 0.0) -> np.ndarray:
        """Blobby region covering ``coverage`` of the texture (or of ``mask``)."""
        if coverage <= 0:
            return np.zeros((ctx.h, ctx.w), dtype=bool)
        f = self.height_field(ctx, key, freq_mul, octaves, kind, aspect, warp)
        vals = f[mask] if mask is not None else f.ravel()
        if vals.size == 0:
            return np.zeros((ctx.h, ctx.w), dtype=bool)
        cut = np.quantile(vals, 1 - min(1.0, coverage))
        out = f >= cut
        if mask is not None:
            out &= mask
        return out

    # ------------------------------------------------------- lighting
    def bevel(self, ctx: GenContext, grid: np.ndarray, direction: int, amount: float,
              mask: np.ndarray | None = None, key: str = "bevel", tag: str | None = None,
              floor: int = 0, ceil: int | None = None) -> np.ndarray:
        """Light (``direction``=+1) or shade (-1) the rims of a height-like grid.

        Light comes from the top-left.  ``+1`` brightens the top rim of
        raised regions; ``-1`` darkens the pixels just below a raised
        region (a one-pixel drop shadow).  Rims are thinned with
        low-frequency noise so they stay in short runs instead of long
        wire-like lines or dotted patterns.
        """
        if amount <= 0:
            return np.zeros(grid.shape, dtype=bool)
        g = grid.astype(np.float64)
        up = pa.neighbour(g, 0, -1, ctx.wrap, fill=g.min() if direction > 0 else g.max())
        left = pa.neighbour(g, -1, 0, ctx.wrap, fill=g.min() if direction > 0 else g.max())
        if direction > 0:
            rim = (g > up) | ((g > left) & (g - left >= 2))
        else:
            rim = (g < up) | ((g < left) & (left - g >= 2))
        if mask is not None:
            rim &= mask
        keep = self.height_field(ctx, key + str(direction), 1.3, 1) < 0.12 + amount * 0.75
        rim &= keep
        ctx.canvas.shift(rim, direction, floor, ceil)
        if tag:
            ctx.canvas.tag(tag)[rim] = True
        return rim

    # -------------------------------------------------------- clusters
    def place_clusters(self, ctx: GenContext, key: str, count: int, size_range: tuple[int, int],
                       mask: np.ndarray | None = None, min_dist: float | None = None,
                       compact: float = 0.75, bias: tuple[float, float] = (0.0, 0.0),
                       margin: int = 0) -> list[list[tuple[int, int]]]:
        """Grow ``count`` chunky blobs at well-spaced positions."""
        rng = ctx.rng(key)
        if count <= 0:
            return []
        if min_dist is None:
            min_dist = math.sqrt(ctx.w * ctx.h / max(1, count)) * 0.72
        pts = noise.poisson_points(ctx.w, ctx.h, rng, count * 3 if mask is not None else count,
                                   min_dist, ctx.wrap, margin)
        if mask is not None:
            pts = [p for p in pts if mask[p[1], p[0]]][:count]
        # ``gap`` = occupied pixels plus their 4-neighbours, kept up to date incrementally:
        # clusters stay one pixel apart so they read as separate grains
        gap = np.zeros((ctx.h, ctx.w), dtype=bool) if mask is None else ~mask
        near = np.zeros((ctx.h, ctx.w), dtype=bool)
        out = []
        for (x, y) in pts:
            size = int(rng.integers(size_range[0], size_range[1] + 1))
            if near[y, x]:
                continue
            cells = pa.grow_cluster(rng, (x, y), size, ctx.w, ctx.h, ctx.wrap, gap | near, compact, bias)
            for cx, cy in cells:
                for dx, dy in ((0, 0),) + pa.N4:
                    nx, ny = cx + dx, cy + dy
                    if ctx.wrap:
                        near[ny % ctx.h, nx % ctx.w] = True
                    elif 0 <= nx < ctx.w and 0 <= ny < ctx.h:
                        near[ny, nx] = True
            out.append(cells)
        return out

    def shade_cluster(self, ctx: GenContext, cells: list[tuple[int, int]], role: str,
                      base_tone: float = 0.55, sparkle: bool = True, outline_shadow: bool = True,
                      rng: np.random.Generator | None = None) -> np.ndarray:
        """Paint a blob with rim lighting in ``role``'s ramp."""
        m = np.zeros((ctx.h, ctx.w), dtype=bool)
        pa.stamp(m, cells, ctx.wrap)
        c = ctx.canvas
        n = c.length_of(role)
        base_lv = int(round(base_tone * (n - 1)))
        c.set(m, role, base_lv)
        lit = (pa.edge_of(m, -1, 0, ctx.wrap) | pa.edge_of(m, 0, -1, ctx.wrap))
        dark = (pa.edge_of(m, 1, 0, ctx.wrap) | pa.edge_of(m, 0, 1, ctx.wrap)) & ~lit
        if len(cells) >= 3:
            c.set(lit, role, min(n - 1, base_lv + 1))
            c.set(dark, role, max(0, base_lv - 1))
        if sparkle and len(cells) >= 3:
            rng = rng or ctx.rng("sparkle", cells[0])
            cand = [p for p in cells if lit[p[1] % ctx.h, p[0] % ctx.w]] or cells
            sx, sy = cand[int(rng.integers(len(cand)))]
            c.set(self._pt(ctx, sx, sy), role, n - 1)
            c.tag("protect")[sy % ctx.h, sx % ctx.w] = True
        if outline_shadow:
            below = pa.neighbour(m, 0, -1, ctx.wrap, fill=False) & ~m
            right = pa.neighbour(m, -1, 0, ctx.wrap, fill=False) & ~m
            sh = (below | right) & (c.ramp == 0) & ~c.tag("ore")
            c.shift(sh, -1)
        c.tag("ore")[m] = True
        return m

    def _pt(self, ctx: GenContext, x: int, y: int) -> np.ndarray:
        m = np.zeros((ctx.h, ctx.w), dtype=bool)
        m[y % ctx.h, x % ctx.w] = True
        return m

    # ---------------------------------------------------------- cracks
    def crack_paths(self, ctx: GenContext, amount: float, key: str = "cracks",
                    mask: np.ndarray | None = None, angle: float | None = None,
                    length: tuple[float, float] = (0.35, 0.8)) -> np.ndarray:
        """Random-walk crack network. Returns a boolean mask (not painted)."""
        rng = ctx.rng(key)
        out = np.zeros((ctx.h, ctx.w), dtype=bool)
        if amount <= 0.02:
            return out
        n = int(round((0.6 + amount * 2.6) * max(1.0, ctx.scale) ** 0.8))
        base_angle = angle if angle is not None else ctx.analysis.direction
        for i in range(n):
            x, y = rng.uniform(0, ctx.w), rng.uniform(0, ctx.h)
            ang = math.radians(base_angle + rng.normal(0, 35) + (90 if rng.random() < 0.3 else 0))
            L = int(ctx.w * rng.uniform(*length) * (0.6 + 0.6 * amount))
            self._walk_crack(ctx, rng, out, x, y, ang, L, amount, depth=0)
        if mask is not None:
            out &= mask
        return out

    def _walk_crack(self, ctx, rng, out, x, y, ang, length, amount, depth):
        prev = None
        for step in range(max(2, length)):
            ix, iy = int(math.floor(x)), int(math.floor(y))
            if ctx.wrap:
                ix %= ctx.w
                iy %= ctx.h
            elif not (0 <= ix < ctx.w and 0 <= iy < ctx.h):
                break
            out[iy, ix] = True
            if prev is not None and prev[0] != ix and prev[1] != iy:
                # diagonal step: fill a corner so the crack stays 4-connected (reads thicker)
                if rng.random() < 0.55:
                    out[prev[1], ix] = True
            if rng.random() < 0.18 + 0.3 * amount:
                # random width: widen perpendicular to the direction of travel
                if abs(math.cos(ang)) > abs(math.sin(ang)):
                    out[(iy + 1) % ctx.h if ctx.wrap else min(ctx.h - 1, iy + 1), ix] = True
                else:
                    out[iy, (ix + 1) % ctx.w if ctx.wrap else min(ctx.w - 1, ix + 1)] = True
            prev = (ix, iy)
            ang += rng.normal(0, 0.32)
            x += math.cos(ang)
            y += math.sin(ang)
            if depth < 1 and rng.random() < 0.05 + 0.08 * amount:
                self._walk_crack(ctx, rng, out, x, y, ang + rng.choice([-1, 1]) * rng.uniform(0.7, 1.3),
                                 int(length * rng.uniform(0.25, 0.5)), amount, depth + 1)

    def paint_cracks(self, ctx: GenContext, cracks: np.ndarray, role: str = "base",
                     lit_lip: bool = True) -> None:
        if not cracks.any():
            return
        c = ctx.canvas
        c.set(cracks & c.opaque, role, 0)
        c.tag("crack")[cracks] = True
        if lit_lip:
            lip = pa.neighbour(cracks, 0, -1, ctx.wrap, fill=False) & ~cracks
            c.shift(lip & c.opaque, 1)
            c.tag("protect")[lip] = False
        shade = pa.neighbour(cracks, 0, 1, ctx.wrap, fill=False) & ~cracks
        c.shift(shade & c.opaque & (c.level > 1), -1)

    # --------------------------------------------------------- minerals
    def mineral_grains(self, ctx: GenContext, amount: float, role: str = "accent", key: str = "grains",
                       prefer: np.ndarray | None = None, size_range: tuple[int, int] = (2, 3),
                       mask: np.ndarray | None = None) -> None:
        """Small exposed mineral grains (1-3 px) - protected from cleanup."""
        if amount <= 0.01:
            return
        rng = ctx.rng(key)
        count = int(round(amount * 9 * ctx.scale ** 2))
        cand_mask = mask if mask is not None else ctx.canvas.opaque
        if prefer is not None and prefer.any():
            cand_mask = cand_mask & prefer
        ys, xs = np.nonzero(cand_mask)
        if len(xs) == 0:
            return
        c = ctx.canvas
        n = c.length_of(role)
        for _ in range(count):
            i = int(rng.integers(len(xs)))
            size = int(rng.integers(size_range[0], size_range[1] + 1))
            cells = pa.grow_cluster(rng, (int(xs[i]), int(ys[i])), size, ctx.w, ctx.h, ctx.wrap,
                                    ~(mask if mask is not None else c.opaque), 0.8)
            m = np.zeros((ctx.h, ctx.w), dtype=bool)
            pa.stamp(m, cells, ctx.wrap)
            lo = max(1, n // 2 - 1)
            lv = int(rng.integers(lo, max(lo + 1, n - 2)))
            c.set(m, role, lv)
            top = cells[0]
            c.set(self._pt(ctx, *top), role, min(n - 2, lv + 1))
            c.tag("protect")[m] = True
            c.tag("mineral")[m] = True

    def glow_points(self, ctx: GenContext, amount: float, mask: np.ndarray, key: str = "glow",
                    role: str = "glow") -> np.ndarray:
        """Turn a few pixels inside ``mask`` into emissive glow pixels."""
        out = np.zeros((ctx.h, ctx.w), dtype=bool)
        if amount <= 0.01 or not mask.any():
            return out
        rng = ctx.rng(key)
        ys, xs = np.nonzero(mask)
        count = max(1, int(round(len(xs) * (0.04 + 0.35 * amount))))
        idx = rng.choice(len(xs), size=min(count, len(xs)), replace=False)
        out[ys[idx], xs[idx]] = True
        c = ctx.canvas
        n = c.length_of(role)
        tone = rng.integers(max(1, n - 2), n, size=len(idx))
        c.ramp[out] = ROLE_IDS[role]
        c.level[out] = tone
        c.emissive |= out
        c.tag("protect")[out] = True
        return out
