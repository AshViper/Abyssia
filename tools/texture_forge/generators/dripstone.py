"""Dripstone: speleothem sprites in the five thicknesses of pointed dripstone.

Variants (thickness, root to tip): ``base``, ``middle``, ``frustum``, ``tip``
and ``tip_merge`` (a thin tip that reaches all the way to the tile edge to
meet an opposite tip).  ``part`` picks ``down`` (stalactite, rooted at the
top edge) or ``up`` (stalagmite, rooted at the bottom edge, the vertical
mirror).

Each column is a stack of rows with a half width taken from the thickness
profile (plus a little per-seed wobble); the rows are shaded as a lit
cylinder (light from the left), cut by vertical flow grooves and banded by
growth rings, and the very tip carries a drop highlight.

Roles: ``base`` = the mineral, ``accent`` = mineral streaks along the flow
grooves (mineral slider), ``glow`` = emissive core pixels (glow slider).
"""
from __future__ import annotations

import numpy as np

from core import pixel_art as pa
from core.layers import GenContext
from core.settings import TextureSettings

from . import _block_patterns as bp
from .base import BaseGenerator

VARIANT_NAMES = ("tip", "tip_merge", "frustum", "middle", "base")


def _profile(variant: str, n: int, sc: float) -> np.ndarray:
    """Half width per row (root at row 0), in pixels."""
    y = (np.arange(n) + 0.5) / n
    if variant == "base":
        hw = np.full(n, 5.2)
    elif variant == "middle":
        hw = np.full(n, 3.7)
    elif variant == "frustum":
        hw = 3.5 - 1.6 * y
    elif variant == "tip":
        hw = np.where(y < 0.72, 2.2 * np.clip(1 - y / 0.72, 0, 1) ** 0.8, 0.0)
    else:  # tip_merge
        hw = 2.0 - 1.35 * y
    return hw * sc


class DripstoneGenerator(BaseGenerator):
    category = "dripstone"

    def body_levels(self, s: TextureSettings, a) -> int:
        k = super().body_levels(s, a)
        return max(4, k) if s.levels == 0 else k

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": max(3, k), "accent": 5, "accent2": 4, "glow": 4}

    def prepare(self, ctx: GenContext) -> None:
        s = ctx.settings
        v = s.variant if s.variant in VARIANT_NAMES else "tip"
        N = ctx.w
        # the profile does not depend on the part, so a stalactite and its stalagmite match
        rng = ctx.rng("profile")
        hw = _profile(v, ctx.h, ctx.scale)
        wob = rng.normal(0, 0.22 + 0.25 * s.roughness, ctx.h) * ctx.scale
        wob = np.convolve(wob, [0.25, 0.5, 0.25], mode="same")
        hw = np.where(hw > 0, np.maximum(0.55, hw + wob * (hw > 1.0)), 0.0)
        drift = np.zeros(ctx.h)
        if v in ("tip", "tip_merge"):
            drift = np.cumsum(rng.normal(0, 0.12 * (0.5 + s.organic), ctx.h)) * ctx.scale
            drift -= drift[0]
        cx = N / 2.0 + drift
        yy, xx = np.mgrid[0:ctx.h, 0:N].astype(np.float64)
        d = xx + 0.5 - cx[:, None]
        mask = np.abs(d) <= hw[:, None]
        # keep every row at least one pixel wide while the profile is open
        for y in range(ctx.h):
            if hw[y] > 0 and not mask[y].any():
                mask[y, int(np.clip(np.floor(cx[y]), 0, N - 1))] = True
        mask = pa.alpha_cleanup(mask, 2, True)
        t = np.clip((d + hw[:, None]) / np.maximum(1e-6, 2 * hw[:, None]), 0, 1)
        ctx.data.update(variant=v, mask=mask, t=t, hw=hw, cx=cx, L=bp.levels(ctx))

    # ------------------------------------------------------------ layers
    def layer_base(self, ctx: GenContext) -> None:
        c = ctx.canvas
        c.clear_all()
        c.set(ctx.data["mask"], "base", ctx.data["L"].mid)

    def layer_material(self, ctx: GenContext) -> None:
        """Cylinder shading: lit left third, shaded right third."""
        L, c = ctx.data["L"], ctx.canvas
        mask, t, hw = ctx.data["mask"], ctx.data["t"], ctx.data["hw"]
        wide = (hw >= 1.4 * ctx.scale)[:, None]
        lv = np.full(mask.shape, L.mid, dtype=np.int32)
        lv = np.where(wide & (t < 0.3), L.up, lv)
        lv = np.where(wide & (t > 0.7), L.lo, lv)
        lv = np.where(~wide & (t < 0.5), L.up, lv)
        c.set(mask, "base", lv)

    def layer_large_detail(self, ctx: GenContext) -> None:
        """Growth rings: slightly darker bands every few rows."""
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        mask = ctx.data["mask"]
        rng = ctx.rng("rings")
        period = max(3, int(round((3.2 + 1.5 * (1 - s.density)) * ctx.scale)))
        phase = int(rng.integers(period))
        rows = ((np.arange(ctx.h) + phase) % period) == 0
        band = mask & rows[:, None] & bp.noise_keep(self, ctx, "ring_keep", 0.75)
        c.shift(band, -1, floor=L.dark)
        ctx.data["rings"] = band

    def layer_medium_detail(self, ctx: GenContext) -> None:
        """Vertical flow grooves on wide columns."""
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        mask, hw = ctx.data["mask"], ctx.data["hw"]
        inner = mask & pa.neighbour(mask, -1, 0, False, fill=False) & pa.neighbour(mask, 1, 0, False, fill=False)
        inner &= (hw >= 2.0 * ctx.scale)[:, None]
        if not inner.any():
            ctx.data["grooves"] = np.zeros_like(mask)
            return
        g = self.height_field(ctx, "grooves", 1.2, 1, "value", aspect=(2.8, 0.25))
        cut = np.quantile(g[inner], 0.25 + 0.1 * s.roughness)
        groove = inner & (g <= cut)
        groove = pa.remove_small_clusters(groove.astype(np.int32), 3, False).astype(bool) & inner
        c.shift(groove, -1, floor=L.dark)
        lip = pa.neighbour(groove, -1, 0, False, fill=False) & inner & ~groove
        c.shift(lip, 1, ceil=L.lit - 1)
        ctx.data["grooves"] = groove

    def layer_small_detail(self, ctx: GenContext) -> None:
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        if s.noise <= 0.1:
            return
        mask = pa.erode(ctx.data["mask"], False)
        rng = ctx.rng("specks")
        ys, xs = np.nonzero(mask)
        k = int(len(xs) * 0.05 * s.noise)
        for i in (rng.choice(len(xs), size=min(k, len(xs)), replace=False) if k else []):
            y, x = int(ys[i]), int(xs[i])
            m = bp.point_mask(ctx, [(x, y), (x, y + 1)]) & mask
            c.shift(m, 1 if rng.random() < 0.5 else -1, floor=L.dark + 1, ceil=L.lit - 1)

    def layer_highlights(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        mask = ctx.data["mask"]
        left = pa.edge_of(mask, -1, 0)
        c.set(left & ~ctx.data.get("rings", np.zeros_like(mask)), "base", L.lit)
        # the drop hanging from the tip
        v = ctx.data["variant"]
        if v == "tip":
            rows = np.nonzero(mask.any(axis=1))[0]
            if len(rows):
                y = int(rows.max())
                xs = np.nonzero(mask[y])[0]
                c.set(bp.point_mask(ctx, [(int(xs[0]), y)]), "base", L.hi)
                bp.protect(ctx, bp.point_mask(ctx, [(int(xs[0]), y)]))
        bp.protect(ctx, left)

    def layer_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        mask = ctx.data["mask"]
        right = pa.edge_of(mask, 1, 0) & ~pa.edge_of(mask, -1, 0)
        c.set(right, "base", L.dark)
        c.set(right & (ctx.data["hw"] >= 2.5 * ctx.scale)[:, None], "base", L.deep)
        bp.protect(ctx, right)

    def layer_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        mask = ctx.data["mask"]
        if s.mineral > 0.03:
            prefer = ctx.data.get("grooves")
            if prefer is None or not prefer.any():
                prefer = pa.erode(mask, False)
            self.mineral_grains(ctx, s.mineral * 0.6, "accent", "streaks", prefer=prefer, size_range=(1, 2),
                                mask=pa.erode(mask, False) if pa.erode(mask, False).any() else mask)
        if s.glow > 0.02:
            core = mask & (np.abs(ctx.data["t"] - 0.5) <= 0.5 / np.maximum(1.0, ctx.data["hw"][:, None]))
            core &= ~pa.edge_of(mask, -1, 0) & ~pa.edge_of(mask, 1, 0) | (mask & (ctx.data["hw"] < 1.2)[:, None])
            self.glow_points(ctx, s.glow, core & mask)

    def finish(self, ctx: GenContext) -> None:
        if ctx.settings.part != "up":
            return
        c = ctx.canvas
        for a in ("ramp", "level", "alpha", "height_map", "emissive"):
            setattr(c, a, getattr(c, a)[::-1].copy())
        c.tags = {k: v[::-1].copy() for k, v in c.tags.items()}
