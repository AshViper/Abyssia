"""Mineral: tileable mineral blocks - crystalline grain mosaics, banded
agate / sediment, chunky raw-ore lumps and bumpy mineral crusts.

Roles: ``base`` is the host / body material, ``accent`` the main mineral
(grains, veins, coated lumps), ``accent2`` a secondary mineral and
``glow`` optional luminous grains.

Every variant is built from a tileable *cell* structure (a jittered
Voronoi, or a band field for ``banded``) so the layers can shade whole
grains / lumps / bands with flat pixel-art tones, rim them with light on
the top-left and shade them on the bottom-right, without ever producing
per-pixel noise.  All randomness comes from ``ctx.rng`` streams, and all
neighbour operations honour ``ctx.wrap`` so blocks tile seamlessly.
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.layers import ROLE_IDS, GenContext
from core.palette import ACCENT_PRESETS, THEMES
from core.settings import TextureSettings

from .base import BaseGenerator
from .crystal import Voronoi, cell_argmax, cell_borders, voronoi

#: theme -> secondary mineral when the theme has none of its own
MINERAL_ACCENT2: dict[str, str] = {
    "deep_ocean": "bone", "abyss": "silver", "trench": "silver", "volcanic": "sulfur",
    "bioluminescent": "glow_cyan", "ancient": "bone", "cold": "silver", "organic": "rust",
}

_BASE = ROLE_IDS["base"]
_ACC = ROLE_IDS["accent"]
_ACC2 = ROLE_IDS["accent2"]


def _rnd(v: float) -> int:
    return int(math.floor(v + 0.5))


class MineralGenerator(BaseGenerator):
    category = "mineral"

    # ------------------------------------------------------------ setup
    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        roles: dict[str, str] = {}
        th = THEMES.get(s.palette)
        has_second = bool(th and th.accent2)
        if s.accent:
            has_second = ACCENT_PRESETS.get(s.accent, (s.accent, None))[1] is not None
        if not has_second and s.palette in MINERAL_ACCENT2:
            roles["accent2"] = MINERAL_ACCENT2[s.palette]
        return roles

    def mode(self, ctx: GenContext) -> str:
        v = ctx.settings.variant
        return v if v in ("crystalline", "banded", "granular", "crust") else "crystalline"

    # ----------------------------------------------------------- helpers
    def _lv(self, ctx: GenContext, role_id: int, tone: np.ndarray | float) -> np.ndarray:
        """Body level of a role for a tone 0..1 (keeps the darkest and lightest
        entries of the ramp free for gaps and highlights)."""
        c = ctx.canvas
        n = int(c.lengths[role_id])
        lo, hi = 1, max(1, n - 2)
        return np.clip(np.rint(lo + np.asarray(tone, dtype=np.float64) * (hi - lo)), lo, hi).astype(np.int32)

    def _paint_cells(self, ctx: GenContext, mask: np.ndarray, tone: np.ndarray) -> None:
        """Paint ``tone`` (0..1 per pixel) in each pixel's material ramp."""
        c = ctx.canvas
        role = ctx.data["role"]
        for rid, name in ((_BASE, "base"), (_ACC, "accent"), (_ACC2, "accent2")):
            m = mask & (role == rid) & (c.ramp != ROLE_IDS["glow"])
            if m.any():
                c.set(m, name, self._lv(ctx, rid, tone))

    def _nudge(self, ctx: GenContext, mask: np.ndarray, delta: int, top_free: bool = True) -> None:
        """Shift levels within the body range of each pixel's own ramp."""
        c = ctx.canvas
        m = mask & c.opaque & (c.ramp != ROLE_IDS["glow"])
        if not m.any():
            return
        top = c.max_level()
        hi = top - 1 if top_free else top
        c.level[m] = np.clip(c.level[m] + delta, 1, np.maximum(1, hi[m]))

    def _grid_count(self, ctx: GenContext, n16: float, cap_div: float = 3.0) -> tuple[int, int]:
        s = ctx.settings
        a = ctx.analysis
        infl = s.source_influence if not a.is_default else 0.0
        cs = a.cluster_size if infl > 0 else 4.5
        size_mul = float(np.clip(np.sqrt(4.5 / max(1.0, cs)), 0.7, 1.35)) ** infl
        size_mul *= 1.3 - 0.6 * s.cluster_size
        g = n16 * size_mul * ctx.scale ** 0.8
        g = float(np.clip(g, 2, max(2, ctx.w / cap_div)))
        ax, ay = self.aspect(ctx)
        rng = ctx.rng("grid_aspect")
        gx = max(2, int(round(g / max(0.5, ax) ** 0.5)))
        gy = max(2, int(round(g * rng.uniform(0.9, 1.15) / max(0.5, ay) ** 0.5)))
        return gx, gy

    def _offsets(self, ctx: GenContext, vor: Voronoi) -> tuple[np.ndarray, np.ndarray]:
        ys, xs = np.mgrid[0:ctx.h, 0:ctx.w]
        dx = xs + 0.5 - vor.points[vor.cell, 0]
        dy = ys + 0.5 - vor.points[vor.cell, 1]
        dx = (dx + ctx.w / 2) % ctx.w - ctx.w / 2
        dy = (dy + ctx.h / 2) % ctx.h - ctx.h / 2
        return dx, dy

    def _assign_roles(self, ctx: GenContext, ncell: int, key: str, p_acc: float, p_acc2: float) -> np.ndarray:
        """Material per cell: host, mineral or secondary mineral."""
        rng = ctx.rng(key)
        r = rng.random(ncell)
        roles = np.full(ncell, _BASE, dtype=np.int32)
        roles[r < p_acc] = _ACC
        roles[(r >= p_acc) & (r < p_acc + p_acc2)] = _ACC2
        return roles

    def _mineral_share(self, ctx: GenContext, base: float, gain: float) -> float:
        s = ctx.settings
        a = ctx.analysis
        share = base + gain * s.mineral
        if not a.is_default and a.accent_fraction > 0:
            share = share * (1 - 0.4 * s.source_influence) + a.accent_fraction * 0.4 * s.source_influence * 1.5
        return float(np.clip(share, 0.0, 0.9))

    # =============================================================== layers
    def layer_base(self, ctx: GenContext) -> None:
        getattr(self, f"_{self.mode(ctx)}_base")(ctx)

    def layer_material(self, ctx: GenContext) -> None:
        getattr(self, f"_{self.mode(ctx)}_material")(ctx)

    def layer_large_detail(self, ctx: GenContext) -> None:
        # broad light / shade zones (whole cells move together)
        s = ctx.settings
        d = ctx.data
        cell = d["cell"]
        f = self.height_field(ctx, "zones", 0.45, 2, kind="value")
        ncell = int(cell.max()) + 1
        # mean of the field per cell -> whole cells shift
        sums = np.bincount(cell.ravel(), weights=f.ravel(), minlength=ncell)
        cnt = np.maximum(1, np.bincount(cell.ravel(), minlength=ncell))
        z = sums / cnt
        lo = np.quantile(z, 0.22 + 0.08 * s.moisture)
        hi = np.quantile(z, 0.82)
        dark = (z < lo)[cell] & d["body"]
        light = (z > hi)[cell] & d["body"]
        self._nudge(ctx, dark, -1)
        self._nudge(ctx, light, +1)
        if self.mode(ctx) == "banded":
            # pinch and swell: bands brighten / darken along their length
            m = self.smooth_mask(ctx, "band_swell", 0.18 + 0.2 * s.roughness, 0.9, 2) & d["body"]
            self._nudge(ctx, m & ~light, +1)

    def layer_medium_detail(self, ctx: GenContext) -> None:
        getattr(self, f"_{self.mode(ctx)}_medium")(ctx)

    def layer_small_detail(self, ctx: GenContext) -> None:
        getattr(self, f"_{self.mode(ctx)}_small")(ctx)

    def layer_cracks(self, ctx: GenContext) -> None:
        s = ctx.settings
        d = ctx.data
        angle = None
        if self.mode(ctx) == "banded":
            angle = d.get("band_angle", 0.0) + 90.0
        cracks = self.crack_paths(ctx, s.cracks * (0.8 if self.mode(ctx) != "granular" else 0.5), "cracks",
                                  angle=angle)
        cracks &= ~d.get("gap", np.zeros_like(cracks))
        self.paint_cracks(ctx, cracks)
        d["body"] &= ~cracks

    def layer_highlights(self, ctx: GenContext) -> None:
        getattr(self, f"_{self.mode(ctx)}_highlights")(ctx)

    def layer_shadows(self, ctx: GenContext) -> None:
        getattr(self, f"_{self.mode(ctx)}_shadows")(ctx)

    def layer_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        d = ctx.data
        mode = self.mode(ctx)
        # loose mineral specks in the host
        host = (d["role"] == _BASE) & d["body"] & ~c.tag("crack")
        if mode == "crust":
            prefer = d.get("pore", np.zeros_like(host)) | d.get("seam", np.zeros_like(host))
            prefer = pa.dilate(prefer, ctx.wrap) & host
        elif mode == "granular":
            prefer = host & d.get("low", host)
        else:
            prefer = host & (c.level <= max(2, self.K(ctx) // 2))
        self.mineral_grains(ctx, s.mineral * (0.9 if mode != "banded" else 0.6), "accent", "specks",
                            prefer=prefer, mask=host)
        if s.glow > 0.02:
            mineral = (d["role"] != _BASE) & d["body"]
            if mode == "granular":
                mineral = mineral | (d.get("gap", mineral) & ~c.tag("crack"))
            cand = pa.erode(mineral, ctx.wrap) if pa.erode(mineral, ctx.wrap).any() else mineral
            self._glow_grains(ctx, s.glow, cand)

    def _glow_grains(self, ctx: GenContext, amount: float, mask: np.ndarray) -> None:
        """Luminous grains: small clusters (2-3 px) of glow inside ``mask``."""
        if not mask.any():
            return
        c = ctx.canvas
        rng = ctx.rng("glow_grains")
        n = c.length_of("glow")
        count = max(1, int(round((0.5 + 3.5 * amount) * ctx.scale ** 1.6)))
        ys, xs = np.nonzero(mask)
        out = np.zeros_like(mask)
        for _ in range(count):
            i = int(rng.integers(len(xs)))
            size = int(rng.integers(2, 4 if ctx.scale < 2 else 5))
            cells = pa.grow_cluster(rng, (int(xs[i]), int(ys[i])), size, ctx.w, ctx.h, ctx.wrap, ~mask, 0.8)
            m = np.zeros_like(mask)
            pa.stamp(m, cells, ctx.wrap)
            out |= m
            top = cells[0]
            c.set(m, "glow", max(0, n - 2) if amount < 0.6 else n - 2)
            c.set(self._pt(ctx, *top), "glow", n - 1)
        c.emissive |= out
        c.tag("protect")[out] = True

    # ======================================================= crystalline
    def _crystalline_base(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        c.fill("base", max(1, self.K(ctx) // 2))
        gx, gy = self._grid_count(ctx, 3.2 + 2.4 * s.density)
        vor = voronoi(ctx.w, ctx.h, ctx.rng("grains"), gx, gy, 0.95)
        ncell = len(vor.points)
        share = self._mineral_share(ctx, 0.12, 0.55)
        roles = self._assign_roles(ctx, ncell, "grain_roles", share, 0.06 + 0.18 * share)
        d = ctx.data
        d.update(cell=vor.cell, vor=vor, role=roles[vor.cell], body=np.ones((ctx.h, ctx.w), dtype=bool))
        d["dx"], d["dy"] = self._offsets(ctx, vor)
        # flat facet tone per grain from a random facet normal; facet widens the spread
        rng = ctx.rng("grain_tone")
        theta = rng.uniform(0, 2 * np.pi, ncell)
        tilt = rng.uniform(0.2, 1.0, ncell)
        lam = -(np.cos(theta) + np.sin(theta)) * 0.7071 * tilt
        spread = 0.25 + 0.75 * s.facet
        tone = np.clip(0.5 + 0.5 * lam * spread / 0.8 + rng.normal(0, 0.06, ncell), 0, 1)
        # host grains follow the reference's tone weights
        d["grain_tone"] = tone
        d["lam"] = lam
        self._paint_cells(ctx, np.ones((ctx.h, ctx.w), dtype=bool), tone[vor.cell])

    def _crystalline_material(self, ctx: GenContext) -> None:
        # bigger grains split into two flat facets (the lit side one step lighter)
        s = ctx.settings
        d = ctx.data
        if s.facet < 0.15:
            return
        vor: Voronoi = d["vor"]
        rng = ctx.rng("grain_split")
        ncell = len(vor.points)
        ang = rng.uniform(0, np.pi, ncell)
        split = rng.random(ncell) < 0.3 + 0.6 * s.facet
        a = ang[d["cell"]]
        side = (d["dx"] * np.sin(a) - d["dy"] * np.cos(a)) > 0.35
        facing = (np.cos(ang) - np.sin(ang)) > 0
        lighter = np.where(facing[d["cell"]], side, ~side) & split[d["cell"]] & d["body"]
        self._nudge(ctx, lighter, +1)

    def _crystalline_medium(self, ctx: GenContext) -> None:
        # bright grain edges on the top-left rim, shaded bottom-right rim
        s = ctx.settings
        d = ctx.data
        lit, shaded = cell_borders(d["cell"], ctx.wrap)
        keep = self.height_field(ctx, "edge_keep", 1.3, 1) < 0.35 + 0.6 * s.facet + 0.1
        mineral = d["role"] != _BASE
        self._nudge(ctx, lit & keep & d["body"], +1 + int(s.facet > 0.55), top_free=False)
        self._nudge(ctx, shaded & d["body"] & (mineral | (self.height_field(ctx, "shade_keep", 1.3, 1) < 0.7)), -1)
        ctx.canvas.tag("lit")[lit & keep] = True

    def _crystalline_small(self, ctx: GenContext) -> None:
        self._specks(ctx, ctx.data["body"] & (ctx.data["role"] == _BASE))

    def _crystalline_highlights(self, ctx: GenContext) -> None:
        # a glint at the top-left corner of mineral grains
        s = ctx.settings
        d = ctx.data
        vor: Voronoi = d["vor"]
        rng = ctx.rng("glints")
        roles = d["role"]
        ncell = len(vor.points)
        # step one pixel inside the rim so the glint sits on the face
        inner = d["body"] & (pa.neighbour(d["cell"], -1, -1, ctx.wrap) == d["cell"])
        score = -(d["dx"] + d["dy"]) + rng.uniform(0, 0.6, d["cell"].shape)
        best, counts = cell_argmax(d["cell"], score, d["body"] & inner, ncell)
        cell_role = np.full(ncell, _BASE)
        cell_role[d["cell"].ravel()] = roles.ravel()
        gate = rng.random(ncell) < 0.35 + 0.55 * s.facet
        gate &= (cell_role != _BASE) | (rng.random(ncell) < 0.25)
        gate &= (counts >= 4) & (best >= 0)
        pts = np.zeros(d["cell"].size, dtype=bool)
        pts[best[gate]] = True
        self._glint(ctx, pts.reshape(d["cell"].shape), 0)

    def _crystalline_shadows(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        d = ctx.data
        cell = d["cell"]
        right = pa.neighbour(cell, 1, 0, ctx.wrap)
        down = pa.neighbour(cell, 0, 1, ctx.wrap)
        junction = (right != cell) & (down != cell) & d["body"]
        c.set(junction & (d["role"] == _BASE), "base", 0)
        self._nudge(ctx, junction & (d["role"] != _BASE), -2)
        if s.roughness > 0.55:
            # rough blocks: the lower rim of grains becomes a dark gap
            _, shaded = cell_borders(cell, ctx.wrap)
            gap = shaded & pa.neighbour(shaded, 0, 1, ctx.wrap) & d["body"]
            gap &= self.height_field(ctx, "gap_keep", 1.2, 1) < (s.roughness - 0.55) * 2
            c.set(gap, "base", 0)
            d["body"] &= ~gap

    # ============================================================ banded
    def _band_axes(self, ctx: GenContext) -> tuple[int, int, float]:
        """Integer band direction (a, b): the field a*x + b*y tiles exactly."""
        s = ctx.settings
        a = ctx.analysis
        infl = s.source_influence if not a.is_default else 0.0
        ang = a.direction if (infl > 0.3 and a.anisotropy > 0.25) else 0.0
        rng = ctx.rng("band_dir")
        if infl <= 0.3 or a.anisotropy <= 0.25:
            ang = float(rng.choice([0.0, 0.0, 0.0, 26.6, -26.6, 45.0, -45.0]))
        # bands run along ``ang`` -> normal = ang + 90
        choices = [(0, 1, 0.0), (1, 2, -26.6), (-1, 2, 26.6), (1, 1, -45.0), (-1, 1, 45.0),
                   (2, 1, -63.4), (-2, 1, 63.4), (1, 0, 90.0)]
        ang = ((ang + 90) % 180) - 90
        best = min(choices, key=lambda t: min(abs(t[2] - ang), 180 - abs(t[2] - ang)))
        return best

    def _banded_base(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        a_, b_, ang = self._band_axes(ctx)
        x, y = noise.grid(ctx.w, ctx.h)
        warp_amt = 0.05 + 0.12 * s.roughness + 0.2 * s.organic
        wf = noise.fbm(ctx.w, ctx.h, ctx.rng("band_warp"), 2, 2, 0.5, "perlin")
        t = (a_ * x + b_ * y + (wf - 0.5) * warp_amt * 2) % 1.0
        # the period is cut into bands of random thickness
        rng = ctx.rng("band_profile")
        # the period spans ctx.h / max(|a|, |b|) pixels across the bands; keep
        # bands at least ~2px (16x16) thick
        span = ctx.h / max(abs(a_), abs(b_))
        nb = int(round((3 + 4 * s.layering + 2 * s.density) * (1.3 - 0.6 * s.cluster_size)
                       * ctx.scale ** 0.55 / max(abs(a_), abs(b_))))
        nb = int(np.clip(nb, 2, max(2, span / (1.6 * max(1.0, ctx.scale ** 0.5)))))
        widths = rng.gamma(2.5, 1.0, nb)
        thin = rng.random(nb) < 0.3
        widths[thin] *= 0.45
        min_w = (1.6 * max(1.0, ctx.scale ** 0.6)) / span
        widths = widths / widths.sum()
        widths = np.maximum(widths, min_w)
        edges = np.concatenate([[0], np.cumsum(widths) / widths.sum()])
        band = np.clip(np.searchsorted(edges, t, side="right") - 1, 0, nb - 1)
        # materials: host bands with alternating tones; mineral bands by share
        share = self._mineral_share(ctx, 0.15, 0.5)
        roles = np.full(nb, _BASE, dtype=np.int32)
        r = rng.random(nb)
        roles[r < share] = _ACC
        roles[thin & (r >= share) & (r < share + 0.35)] = _ACC2
        tone = np.empty(nb)
        tone[0::2] = rng.uniform(0.45, 0.85, len(tone[0::2]))
        tone[1::2] = rng.uniform(0.1, 0.5, len(tone[1::2]))
        # band cells: ids must not wrap-merge different bands
        d = ctx.data
        d.update(cell=band.astype(np.int32), role=roles[band], body=np.ones((ctx.h, ctx.w), dtype=bool),
                 band_t=t, band_edges=edges, band_angle=ang, band_tone=tone)
        c.fill("base", 1)
        self._paint_cells(ctx, np.ones((ctx.h, ctx.w), dtype=bool), tone[band])

    def _banded_material(self, ctx: GenContext) -> None:
        # grain inside the bands: clustered one-step variation along the bands
        s = ctx.settings
        d = ctx.data
        ang = self._band_axes(ctx)[2]
        along = (4.0, 1.0) if abs(ang) < 30 else ((1.0, 4.0) if abs(ang) > 60 else (2.0, 2.0))
        f = self.height_field(ctx, "band_grain", 1.3, 2, aspect=(1 / along[1] * 2, 1 / along[0] * 2))
        cut_hi = np.quantile(f, 0.86 - 0.12 * s.noise)
        cut_lo = np.quantile(f, 0.12 + 0.1 * s.noise)
        mc = max(4, int(4 * ctx.scale))
        hi = f > cut_hi
        lo = f < cut_lo
        hi = pa.remove_small_clusters(hi.astype(np.int32), mc, ctx.wrap).astype(bool) & hi
        lo = pa.remove_small_clusters(lo.astype(np.int32), mc, ctx.wrap).astype(bool) & lo
        host = d["body"] & (d["role"] == _BASE)
        self._nudge(ctx, hi & host, +1)
        self._nudge(ctx, lo & host, -1)

    def _banded_medium(self, ctx: GenContext) -> None:
        # band boundaries: a thin light line on the upper edge of each band
        s = ctx.settings
        d = ctx.data
        band = d["cell"]
        lit, shaded = cell_borders(band, ctx.wrap)
        mineral = d["role"] != _BASE
        # only some band tops catch a line of light: all mineral bands, and host
        # bands that are lighter than the band above
        rng = ctx.rng("band_lines")
        nb = int(band.max()) + 1
        lined = rng.random(nb) < 0.35 + 0.4 * s.facet
        keep = self.height_field(ctx, "band_line_keep", 1.2, 1) < 0.55 + 0.4 * s.facet
        lit &= keep & d["body"] & (mineral | lined[band])
        self._nudge(ctx, lit, +1, top_free=False)
        self._nudge(ctx, shaded & d["body"] & ~lit & mineral, -1)
        ctx.canvas.tag("lit")[lit & keep] = True

    def _banded_small(self, ctx: GenContext) -> None:
        self._specks(ctx, ctx.data["body"] & (ctx.data["role"] == _BASE))

    def _banded_highlights(self, ctx: GenContext) -> None:
        # glossy pixels along the lit edge of mineral bands
        s, c = ctx.settings, ctx.canvas
        d = ctx.data
        lit = c.tag("lit") & (d["role"] != _BASE) & d["body"]
        if not lit.any():
            return
        keep = self.height_field(ctx, "gloss", 2.0, 1) > 0.78 - 0.2 * s.facet
        g = lit & keep
        g = pa.remove_small_clusters(g.astype(np.int32), 2, ctx.wrap).astype(bool) & g
        for rid, name in ((_ACC, "accent"), (_ACC2, "accent2")):
            m = g & (d["role"] == rid)
            c.set(m, name, c.length_of(name) - 1)
        c.tag("protect")[g] = True

    def _banded_shadows(self, ctx: GenContext) -> None:
        # host right under a mineral band sits in its shadow
        d = ctx.data
        mineral = d["role"] != _BASE
        under = pa.neighbour(mineral, 0, -1, ctx.wrap) & ~mineral & d["body"]
        self._nudge(ctx, under, -1)

    # ========================================================== granular
    def _dome(self, ctx: GenContext, vor: Voronoi, key: str, offset: float = 0.22,
              jitter: float = 0.08) -> np.ndarray:
        """Rounded-lump shading: bright spot up-left of each cell centre, dark valleys."""
        d = ctx.data
        cw, ch = ctx.w / vor.gx, ctx.h / vor.gy
        dist = np.sqrt(((d["dx"] + offset * cw) / cw) ** 2 + ((d["dy"] + offset * ch) / ch) ** 2)
        edge = vor.f2 - vor.f1
        v = (1 - np.clip(dist / 0.8, 0, 1)) * 0.65 + np.clip(edge / 0.45, 0, 1) * 0.35
        rng = ctx.rng(key)
        v = v + rng.uniform(-jitter, jitter, len(vor.points))[vor.cell]
        return np.clip(v, 0, 1)

    def _paint_domes(self, ctx: GenContext, v: np.ndarray, mask: np.ndarray) -> None:
        """Quantize a dome field per material: host follows the reference tone weights."""
        c = ctx.canvas
        role = ctx.data["role"]
        host = mask & (role == _BASE)
        if host.any():
            # flatter than the reference weights: lumps need their full contrast
            w = np.asarray(ctx.data["weights"], dtype=np.float64)
            w = 0.5 * w / w.sum() + 0.5 / len(w)
            lv = self.quantize_body(ctx, v, host, weights=w)
            c.set(host, "base", lv)
        for rid, name in ((_ACC, "accent"), (_ACC2, "accent2")):
            m = mask & (role == rid)
            if not m.any():
                continue
            n = c.length_of(name)
            steps = max(1, n - 2)
            q = pa.quantize_field(v, np.ones(steps), m) + 1
            c.set(m, name, q)

    def _granular_base(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        gx, gy = self._grid_count(ctx, 2.0 + 1.4 * s.density, cap_div=5.0)
        vor = voronoi(ctx.w, ctx.h, ctx.rng("lumps"), gx, gy, 0.85)
        ncell = len(vor.points)
        share = self._mineral_share(ctx, 0.08, 0.6)
        roles = self._assign_roles(ctx, ncell, "lump_roles", share, 0.05 + 0.15 * share)
        d = ctx.data
        d.update(cell=vor.cell, vor=vor, role=roles[vor.cell])
        d["dx"], d["dy"] = self._offsets(ctx, vor)
        edge = vor.f2 - vor.f1
        # dark gaps between lumps: roughly one pixel, broken up where lumps touch
        gap = edge < (0.07 + 0.1 * s.roughness) * max(1.0, 16 / ctx.w * max(gx, gy) / 3.5)
        touch = self.height_field(ctx, "lump_touch", 1.5, 1) > 0.62 + 0.3 * s.roughness
        gap &= ~touch
        gap = pa.remove_small_clusters(gap.astype(np.int32), 2, ctx.wrap).astype(bool) & gap
        d["gap"] = gap
        d["body"] = ~gap
        v = self._dome(ctx, vor, "lump_tone", 0.22, 0.1)
        d["dome"] = v
        c.fill("base", 1)
        self._paint_domes(ctx, v, ~gap)
        d["low"] = (c.level <= 2) & ~gap
        c.set(gap, "base", 1)

    def _granular_material(self, ctx: GenContext) -> None:
        # pitted lump surface: clustered one-step blotches
        s = ctx.settings
        d = ctx.data
        f = self.height_field(ctx, "lump_tex", 1.8, 2)
        body = d["body"]
        vals = f[body] if body.any() else f.ravel()
        lo = (f < np.quantile(vals, 0.15 + 0.1 * s.roughness)) & body
        lo = pa.remove_small_clusters(lo.astype(np.int32), 2, ctx.wrap).astype(bool) & lo
        self._nudge(ctx, lo, -1)

    def _granular_medium(self, ctx: GenContext) -> None:
        # lump rims: lit top-left rim, shaded bottom-right rim
        s = ctx.settings
        d = ctx.data
        body = d["body"]
        lit = body & (pa.neighbour(d["gap"], 0, -1, ctx.wrap) | pa.neighbour(d["gap"], -1, 0, ctx.wrap))
        shade = body & (pa.neighbour(d["gap"], 0, 1, ctx.wrap) | pa.neighbour(d["gap"], 1, 0, ctx.wrap)) & ~lit
        keep = self.height_field(ctx, "rim_keep", 1.4, 1) < 0.55 + 0.4 * s.facet
        self._nudge(ctx, lit & keep, +1)
        self._nudge(ctx, shade, -1)
        ctx.canvas.tag("lit")[lit & keep] = True

    def _granular_small(self, ctx: GenContext) -> None:
        self._specks(ctx, ctx.data["body"] & (ctx.data["role"] == _BASE), dark_only=True)

    def _granular_highlights(self, ctx: GenContext) -> None:
        # a shine on the top-left of each lump
        s, c = ctx.settings, ctx.canvas
        d = ctx.data
        vor: Voronoi = d["vor"]
        rng = ctx.rng("lump_shine")
        ncell = len(vor.points)
        score = -(d["dx"] + d["dy"]) - 0.8 * vor.f1 * ctx.w / max(vor.gx, vor.gy)
        score = score + rng.uniform(0, 0.5, score.shape)
        m = d["body"] & ~c.tag("lit")
        best, counts = cell_argmax(d["cell"], score, m, ncell)
        gate = (rng.random(ncell) < 0.5 + 0.45 * s.facet) & (counts >= 5) & (best >= 0)
        pts = np.zeros(d["cell"].size, dtype=bool)
        pts[best[gate]] = True
        pts = pts.reshape(d["cell"].shape)
        # a second pixel to the right when it is on the same lump
        right = pa.neighbour(pts, -1, 0, ctx.wrap) & m & (pa.neighbour(d["cell"], -1, 0, ctx.wrap) == d["cell"])
        self._glint(ctx, pts | right, 1)

    def _granular_shadows(self, ctx: GenContext) -> None:
        # gaps under / right of a lump fall into the deepest shade
        c = ctx.canvas
        d = ctx.data
        gap = d["gap"]
        body = d["body"]
        drop = gap & (pa.neighbour(body, 0, -1, ctx.wrap) | pa.neighbour(body, -1, 0, ctx.wrap))
        c.set(drop, "base", 0)

    # ============================================================== crust
    def _crust_base(self, ctx: GenContext) -> None:
        """Bumpy encrustation: rounded bumps lit from the top-left, partly coated
        with patches of a second mineral (``accent2``)."""
        s, c = ctx.settings, ctx.canvas
        gx, gy = self._grid_count(ctx, 2.6 + 1.6 * s.density, cap_div=4.0)
        vor = voronoi(ctx.w, ctx.h, ctx.rng("bumps"), gx, gy, 0.9)
        ncell = len(vor.points)
        d = ctx.data
        d.update(cell=vor.cell, vor=vor)
        d["dx"], d["dy"] = self._offsets(ctx, vor)
        dome = 1 - np.clip(vor.f1 / 0.85, 0, 1)
        H = 0.65 * dome + 0.35 * self.height_field(ctx, "crust_h", 1.0, 2)
        lit = pa.emboss(H, (-1, -1), ctx.wrap)
        shade = noise.normalize(0.45 * H + 2.2 * lit)
        # coating patches: whole bumps, grouped by a low-frequency field
        share = self._mineral_share(ctx, 0.08, 0.5)
        f = self.height_field(ctx, "coat", 0.55, 2, kind="value")
        pts = vor.points.astype(int)
        z = f[np.clip(pts[:, 1], 0, ctx.h - 1), np.clip(pts[:, 0], 0, ctx.w - 1)]
        roles = np.full(ncell, _BASE, dtype=np.int32)
        if share > 0.02:
            roles[z >= np.quantile(z, 1 - share)] = _ACC2
            rng = ctx.rng("coat_roles")
            roles[(roles == _BASE) & (rng.random(ncell) < share * 0.2)] = _ACC
        d["role"] = roles[vor.cell]
        d["body"] = np.ones((ctx.h, ctx.w), dtype=bool)
        d["pore"] = np.zeros((ctx.h, ctx.w), dtype=bool)
        d["seam"] = np.zeros((ctx.h, ctx.w), dtype=bool)
        d["dome"] = shade
        c.fill("base", 1)
        self._paint_domes(ctx, shade, d["body"])

    def _crust_material(self, ctx: GenContext) -> None:
        # pores: dark holes, a little wider than tall, with a lit lower lip
        s, c = ctx.settings, ctx.canvas
        d = ctx.data
        count = int(round((1.2 + 3.0 * s.roughness + 1.5 * s.density) * ctx.scale ** 1.45))
        lo = 2
        hi = max(3, ctx.px(1.6 + 2.0 * s.cluster_size))
        pores = np.zeros((ctx.h, ctx.w), dtype=bool)
        for cells in self.place_clusters(ctx, "pores", count, (lo, hi), compact=0.85, bias=(0.7, 0.0)):
            m = np.zeros_like(pores)
            pa.stamp(m, cells, ctx.wrap)
            pores |= m
        if not pores.any():
            return
        c.set(pores, "base", 0)
        lip = pa.neighbour(pores, 0, -1, ctx.wrap) & ~pores
        brow = pa.neighbour(pores, 0, 1, ctx.wrap) & ~pores
        self._nudge(ctx, lip, +1, top_free=False)
        self._nudge(ctx, brow & ~lip, -1)
        c.tag("protect")[pores | lip] = True
        d["pore"] = pores
        d["body"] &= ~pores

    def _crust_medium(self, ctx: GenContext) -> None:
        # the coating is a layer on top: lit upper rim, shadow cast below it
        c = ctx.canvas
        d = ctx.data
        coat = (d["role"] != _BASE) & d["body"]
        if not coat.any():
            return
        up_host = ~pa.neighbour(coat, 0, -1, ctx.wrap)
        left_host = ~pa.neighbour(coat, -1, 0, ctx.wrap)
        rim = coat & (up_host | left_host)
        self._nudge(ctx, rim, +1, top_free=False)
        shadow = (pa.neighbour(coat, 0, -1, ctx.wrap) | pa.neighbour(coat, -1, 0, ctx.wrap)) & ~coat & d["body"]
        self._nudge(ctx, shadow, -1)
        c.tag("lit")[rim] = True

    def _crust_small(self, ctx: GenContext) -> None:
        self._specks(ctx, ctx.data["body"] & (ctx.data["role"] == _BASE))

    def _crust_highlights(self, ctx: GenContext) -> None:
        # a glossy pixel on the crown of some bumps
        s = ctx.settings
        d = ctx.data
        vor: Voronoi = d["vor"]
        rng = ctx.rng("crust_gloss")
        ncell = len(vor.points)
        best, counts = cell_argmax(d["cell"], d["dome"] + rng.uniform(0, 0.01, d["dome"].shape), d["body"], ncell)
        gate = (rng.random(ncell) < 0.2 + 0.35 * s.facet + 0.25 * s.moisture) & (counts >= 5) & (best >= 0)
        pts = np.zeros(d["cell"].size, dtype=bool)
        pts[best[gate]] = True
        self._glint(ctx, pts.reshape(d["cell"].shape), 0)

    def _crust_shadows(self, ctx: GenContext) -> None:
        # valleys between bumps sink one more step on their shaded side
        d = ctx.data
        vor: Voronoi = d["vor"]
        valley = ((vor.f2 - vor.f1) < 0.1) & d["body"]
        _, shaded = cell_borders(d["cell"], ctx.wrap)
        self._nudge(ctx, valley & shaded, -1)

    # ============================================================ shared
    def _glint(self, ctx: GenContext, pts: np.ndarray, base_drop: int = 0) -> None:
        """Top-of-ramp highlight pixels in each pixel's own material ramp."""
        c = ctx.canvas
        role = ctx.data["role"]
        for rid, name in ((_BASE, "base"), (_ACC, "accent"), (_ACC2, "accent2")):
            m = pts & (role == rid) & (c.ramp != ROLE_IDS["glow"])
            if m.any():
                c.set(m, name, c.length_of(name) - 1 - (base_drop if rid == _BASE else 0))
        c.tag("protect")[pts] = True

    def _specks(self, ctx: GenContext, mask: np.ndarray, dark_only: bool = False) -> None:
        """Small 2-3 px tone clusters (lighter / darker) - the noise slider."""
        s = ctx.settings
        if s.noise <= 0.03 or not mask.any():
            return
        count = int(round(s.noise * 5 * ctx.scale ** 1.5))
        rng = ctx.rng("speck_sign")
        for cells in self.place_clusters(ctx, "specks_pos", count, (2, 3 if ctx.scale < 2 else 4),
                                         mask=mask, min_dist=2.5, compact=0.6):
            m = np.zeros_like(mask)
            pa.stamp(m, cells, ctx.wrap)
            self._nudge(ctx, m & mask, -1 if dark_only or rng.random() < 0.5 else 1)
