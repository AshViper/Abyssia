"""Organic: living deep-sea blocks.

Variants
--------
coral   porous coral block: domed lobes split by crevices, polyp pits with
        raised rims; glowing polyps with the glow slider
sponge  pale sponge body with rounded holes (shaded interiors, lit lower
        rims) and small pores; bioluminescent hole floors
vein    dark biomass mat of lumpy clumps crossed by branching veins; veins
        and their nodes light up (glow role, emissive) with the glow slider
moss    dense clumpy moss/algae cover: domed tufts, sprigs and spores

Sliders: density = pore / polyp / vein count, cluster_size = pore and blob
size, glow = bioluminescence, organic = how irregular the shapes are,
moisture = wet gloss and dark damp patches, roughness / noise = surface.
All layouts are built with wrap-aware noise and walks, so blocks tile.
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.layers import GenContext
from core.palette import RAMP_ANCHORS, THEMES, hex_to_rgb, luminance
from core.settings import TextureSettings

from . import _block_patterns as bp
from .base import BaseGenerator

VARIANT_NAMES = ("coral", "sponge", "vein", "moss")


def _dark_theme_base(theme: str) -> str:
    """The theme's base ramp if it is dark enough for a biomass mat, else abyss."""
    th = THEMES.get(theme) or THEMES["abyss"]
    anchors = RAMP_ANCHORS.get(th.base, RAMP_ANCHORS["abyss"])
    L = luminance(np.array([hex_to_rgb(a) for a in anchors], dtype=np.float64))
    return th.base if float(L.mean()) < 0.3 else "abyss"


def _round_corners(m: np.ndarray, wrap: bool) -> np.ndarray:
    """Knock the corners off rectangular holes so they read as round."""
    ys, xs = np.nonzero(m)
    if len(xs) < 9:
        return m
    h, w = m.shape
    # bounding box in wrapped space: unroll around the first pixel
    x0, y0 = xs[0], ys[0]
    dx = (xs - x0 + w // 2) % w - w // 2
    dy = (ys - y0 + h // 2) % h - h // 2
    area = (dx.max() - dx.min() + 1) * (dy.max() - dy.min() + 1)
    if area != len(xs):
        return m
    out = m.copy()
    for cx in (dx.min(), dx.max()):
        for cy in (dy.min(), dy.max()):
            out[(y0 + cy) % h, (x0 + cx) % w] = False
    return out


class OrganicGenerator(BaseGenerator):
    category = "organic"

    # ------------------------------------------------------------ setup
    def body_levels(self, s: TextureSettings, a) -> int:
        k = super().body_levels(s, a)
        if s.levels == 0 and (not s.color_limit or s.color_limit >= 12):
            k = max(4, k)
        return k

    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        v = s.variant
        if v == "coral":
            return {"base": "coral_pink", "secondary": "ruby"}
        if v == "sponge":
            return {"base": "bone", "secondary": "mud"}
        if v == "vein":
            return {"base": _dark_theme_base(s.palette)}
        if v == "moss":
            return {"base": "moss", "secondary": "deep_kelp"}
        return {}

    def prepare(self, ctx: GenContext) -> None:
        v = ctx.settings.variant if ctx.settings.variant in VARIANT_NAMES else "coral"
        ctx.data["variant"] = v
        ctx.data["L"] = bp.levels(ctx)
        getattr(self, f"_prep_{v}")(ctx)

    def _run(self, ctx: GenContext, layer: str) -> None:
        fn = getattr(self, f"_{ctx.data['variant']}_{layer}", None)
        if fn is not None:
            fn(ctx)

    def layer_base(self, ctx): self._run(ctx, "base")
    def layer_material(self, ctx): self._run(ctx, "material")
    def layer_large_detail(self, ctx): self._run(ctx, "large_detail")
    def layer_medium_detail(self, ctx): self._run(ctx, "medium_detail")
    def layer_small_detail(self, ctx): self._run(ctx, "small_detail")
    def layer_cracks(self, ctx): self._run(ctx, "cracks")
    def layer_highlights(self, ctx): self._run(ctx, "highlights")
    def layer_shadows(self, ctx): self._run(ctx, "shadows")
    def layer_accent(self, ctx): self._run(ctx, "accent")

    # --------------------------------------------------------- helpers
    def _lobes(self, ctx: GenContext, key: str, cell_px: float, jitter: float = 0.85,
               warp: float = 0.0) -> np.ndarray:
        """Periodic Voronoi cells (~``cell_px`` across) as a label map."""
        g = max(1, int(round(ctx.w / max(1.5, cell_px))))
        gy = max(1, int(round(ctx.h / max(1.5, cell_px))))
        off = noise.warp_offsets(ctx.w, ctx.h, ctx.rng(key + "_warp"), 3, warp) if warp > 0 else None
        cell = noise.cellular(ctx.w, ctx.h, ctx.rng(key), g, gy, jitter, offset=off)
        return cell.cell.astype(np.int32)

    def _dome_offsets(self, ctx: GenContext, labels: np.ndarray, amp: float = 1.2,
                      bias: float = 0.0) -> np.ndarray:
        """-1/0/+1 (or wider) lighting offsets for domed regions lit from the top-left."""
        light, dome = bp.dome_light(labels, ctx.wrap)
        t = light * amp + (dome - 0.5) * 0.6 + bias
        off = np.rint(np.clip(t, -1.49, 1.49)).astype(np.int32)
        return off

    def _holes(self, ctx: GenContext, key: str, count: int, r_range: tuple[float, float],
               min_gap: float = 1.5, avoid: np.ndarray | None = None) -> list[np.ndarray]:
        """Rounded, non-overlapping hole masks (wrap-aware)."""
        rng = ctx.rng(key)
        out: list[np.ndarray] = []
        if count <= 0:
            return out
        rmax = r_range[1]
        pts = bp.scatter(ctx, rng, count * 2, 2 * rmax + min_gap)
        ys, xs = np.mgrid[0:ctx.h, 0:ctx.w]
        taken = bp.zeros(ctx) if avoid is None else avoid.copy()
        for (px, py) in pts:
            if len(out) >= count:
                break
            r = rng.uniform(*r_range)
            ex = rng.uniform(0.85, 1.2)
            # centre on a pixel (odd-sized hole) or on a pixel corner (even-sized)
            cx, cy = (px + 0.5, py + 0.5) if rng.random() < 0.5 else (px + 1.0, py + 1.0)
            dx = np.abs(xs + 0.5 - cx)
            dy = np.abs(ys + 0.5 - cy)
            if ctx.wrap:
                dx = np.minimum(dx, ctx.w - dx)
                dy = np.minimum(dy, ctx.h - dy)
            m = (dx / ex) ** 2 + (dy * ex) ** 2 <= r * r
            if not m.any():
                m = bp.point_mask(ctx, [(px, py)])
            if (pa.dilate(m, ctx.wrap, True) & taken).any():
                continue
            m = _round_corners(m, ctx.wrap)
            taken |= pa.dilate(m, ctx.wrap, True)
            out.append(m)
        return out

    def _speckle(self, ctx: GenContext, key: str, mask: np.ndarray, count: int, delta_up: float,
                 size: tuple[int, int] = (2, 3), floor: int | None = None, ceil: int | None = None) -> None:
        L, c = ctx.data["L"], ctx.canvas
        rng = ctx.rng(key + "_sign")
        for cells in bp.clusters(ctx, key, count, size, mask=mask, min_dist=2.5, compact=0.6):
            m = bp.point_mask(ctx, cells) & mask
            c.shift(m, 1 if rng.random() < delta_up else -1,
                    L.lo if floor is None else floor, L.up if ceil is None else ceil)

    def _crevices(self, ctx: GenContext, labels: np.ndarray) -> np.ndarray:
        e = bp.edges(labels, ctx.wrap)
        cre = e["bottom"] | e["right"]
        return pa.remove_small_clusters(cre.astype(np.int32), 2, ctx.wrap).astype(bool) & cre

    def _gloss(self, ctx: GenContext, where: np.ndarray, frac: float, key: str = "gloss") -> None:
        L, c = ctx.data["L"], ctx.canvas
        ys, xs = np.nonzero(where)
        if not len(xs) or frac <= 0:
            return
        rng = ctx.rng(key)
        k = min(len(xs), max(1, int(round(len(xs) * frac))))
        idx = rng.choice(len(xs), size=k, replace=False)
        m = bp.zeros(ctx)
        m[ys[idx], xs[idx]] = True
        c.set(m, "base", L.hi)
        bp.protect(ctx, m)

    def _cell_size(self, ctx: GenContext, lo: float, hi: float) -> float:
        """Blob size in pixels from the cluster_size slider and the style profile."""
        s = ctx.settings
        prof = np.clip(math.sqrt(max(1.0, ctx.analysis.cluster_size)) / 2.0, 0.7, 1.4)
        return (lo + (hi - lo) * s.cluster_size) * ctx.scale * (0.7 + 0.3 * prof)

    # =================================================================== coral
    def _prep_coral(self, ctx: GenContext) -> None:
        s = ctx.settings
        cell = (3.4 + 2.6 * (1 - s.density)) * ctx.scale * (0.85 + 0.35 * s.cluster_size)
        prof = np.clip(math.sqrt(max(1.0, ctx.analysis.cluster_size)) / 2.0, 0.8, 1.25)
        labels = self._lobes(ctx, "polyps", cell * prof, 0.7, 0.015 + 0.05 * s.organic)
        rx, ry, rad = bp.region_coords(labels, ctx.wrap)
        r = np.sqrt(rx * rx + ry * ry)
        pit_r = (0.45 + 0.8 * s.cluster_size) * ctx.scale
        pit = r < pit_r
        n = int(labels.max()) + 1
        rmin = np.full(n, np.inf)
        np.minimum.at(rmin, labels.ravel(), r.ravel())
        pit |= r <= rmin[labels] + 1e-9
        depth = bp.region_depth(labels, ctx.wrap, 3)
        pit &= depth >= 1
        rim = (r < pit_r + 1.15 * ctx.scale) & ~pit & (depth >= 1)
        e = bp.edges(labels, ctx.wrap)
        crev = (e["bottom"] | e["right"]) & ~pit & ~rim
        ctx.data.update(lobes=labels, pit=pit, rim=rim, crevice=crev, cell_r=r)

    def _coral_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        c.set(ctx.data["crevice"], "base", L.lo)

    def _coral_material(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        light, _ = bp.dome_light(ctx.data["lobes"], ctx.wrap)
        off = np.where(light > 0.4, 1, np.where(light < -0.85, -1, 0)).astype(np.int32)
        off = pa.remove_small_clusters(off, 2, ctx.wrap)
        m = ~ctx.data["crevice"] & ~ctx.data["rim"] & (off != 0)
        c.shift(m, off, L.lo, L.up)

    def _coral_large_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["lobes"]
        n = int(labels.max()) + 1
        rng = ctx.rng("cell_tone")
        q = 0.3 + 0.3 * s.roughness
        vals = rng.choice([-1, 0, 1], size=n, p=[q * 0.4, 1 - q, q * 0.6])
        body = ~ctx.data["crevice"]
        c.shift(body & (vals[labels] != 0), vals[labels], L.lo, L.up)
        if s.moisture > 0.25:
            dark = self.smooth_mask(ctx, "damp", 0.3 * (s.moisture - 0.25), 0.6) & body
            c.shift(dark, -1, L.lo, L.up)

    def _coral_medium_detail(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        pit, rim = ctx.data["pit"], ctx.data["rim"]
        c.set(rim, "base", L.up)
        c.set(pit, "base", L.dark)
        c.tag("pit")[pit] = True
        c.tag("rim")[rim] = True
        bp.protect(ctx, pit)

    def _coral_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        free = ~(c.tag("pit") | c.tag("rim") | ctx.data["crevice"])
        # tiny pores in the tissue between the polyps
        rng = ctx.rng("pores")
        n = int(round((0.3 + 2.5 * s.noise) * (0.4 + s.density) * ctx.scale ** 2))
        cand = free & ~pa.dilate(~free, ctx.wrap)
        ys, xs = np.nonzero(cand)
        if len(xs) and n > 0:
            idx = rng.choice(len(xs), size=min(n, len(xs)), replace=False)
            m = bp.zeros(ctx)
            m[ys[idx], xs[idx]] = True
            c.set(m, "base", L.lo)
            bp.protect(ctx, m)
        self._speckle(ctx, "coral_specks", free, int(round(s.noise * 2 * ctx.scale ** 2)), 0.5)

    def _coral_cracks(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        amt = max(0.0, s.cracks - 0.35) / 0.65
        if amt <= 0.01:
            return
        cr = self.crack_paths(ctx, amt, "coral_cracks", length=(0.2, 0.45))
        cr = bp.four_connect(cr, None, ctx.wrap) & ~c.tag("pit")
        c.set(cr, "base", L.dark)
        c.tag("crack")[cr] = True

    def _rim_dir(self, ctx: GenContext) -> tuple[np.ndarray, np.ndarray]:
        rx, ry, _ = bp.region_coords(ctx.data["lobes"], ctx.wrap)
        r = np.maximum(ctx.data["cell_r"], 1e-6)
        return rx / r, ry / r

    def _coral_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        rim, pit = ctx.data["rim"], ctx.data["pit"]
        excl = c.tag("crack")
        rx, ry = self._rim_dir(ctx)
        lit_rim = rim & (rx + ry < -0.3) & ~excl
        c.set(lit_rim, "base", L.lit)
        # inside the pit the lower-right wall catches light: lip on the rim
        _, _, lip = bp.sunken_light(pit, ctx.wrap)
        c.set(lip & rim & ~excl, "base", L.lit)
        bp.protect(ctx, (lit_rim | lip) & rim)
        spec = rim & (rx + ry < -0.3) & bp.mask_edges(rim | pit, ctx.wrap)["top"] & ~excl
        keep = bp.noise_keep(self, ctx, "coral_spec", 0.25 + 0.5 * s.moisture, 1.6)
        c.set(spec & keep, "base", L.hi)
        bp.protect(ctx, spec & keep)

    def _coral_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        pit, rim = ctx.data["pit"], ctx.data["rim"]
        excl = c.tag("crack")
        shade, _, _ = bp.sunken_light(pit, ctx.wrap)
        c.set(shade & ~excl, "base", L.deep)
        rx, ry = self._rim_dir(ctx)
        c.set(rim & (rx + ry > 0.45) & ~excl & ~c.tag("pit"), "base", L.mid)
        cre = ctx.data["crevice"] & ~excl
        c.set(cre, "base", L.lo)
        # where three cells meet the crevice deepens
        nb = sum(pa.neighbour(cre, dx, dy, ctx.wrap, fill=False).astype(np.int32) for dx, dy in pa.N4)
        c.set(cre & (nb >= 3), "base", L.dark)

    def _coral_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        labels = ctx.data["lobes"]
        pit = c.tag("pit")
        n = c.length_of("glow")
        if s.glow > 0.02 and pit.any():
            rng = ctx.rng("coral_glow")
            cells = np.unique(labels[pit])
            k = max(1, int(round(len(cells) * min(1.0, 0.12 + 0.8 * s.glow))))
            chosen = rng.permutation(cells)[:k]
            m = pit & np.isin(labels, chosen)
            shade, _, _ = bp.sunken_light(m, ctx.wrap)
            lv = np.where(shade, 1, 2) + int(s.glow > 0.7)
            c.set(m, "glow", np.clip(lv, 0, n - 1))
            c.emissive[m] = True
            if s.glow > 0.5:
                # the polyp's rim picks up a little of its light
                rim_near = pa.dilate(m, ctx.wrap) & c.tag("rim") & (c.ramp == 0)
                c.shift(rim_near, 1, 0, ctx.data["L"].lit)
        if s.mineral > 0.2:
            free = ~(c.tag("pit") | c.tag("rim") | ctx.data["crevice"])
            self.mineral_grains(ctx, (s.mineral - 0.2) * 0.6, "accent", "coral_min", mask=free,
                                size_range=(1, 2))

    # ================================================================== sponge
    def _prep_sponge(self, ctx: GenContext) -> None:
        s = ctx.settings
        r_lo = (0.8 + 1.2 * s.cluster_size) * ctx.scale
        count = int(round((1.5 + 5 * s.density) * (1.4 - 0.6 * s.cluster_size) * ctx.scale ** 2
                          / max(1.0, ctx.scale ** 0.35)))
        holes = self._holes(ctx, "holes", max(1, count), (r_lo, r_lo + 0.7 * ctx.scale), 2.2)
        hole = bp.zeros(ctx)
        for m in holes:
            hole |= m
        n_pores = int(round((2 + 7 * s.density) * ctx.scale ** 2 * 0.8))
        pores = self._holes(ctx, "pores", n_pores, (0.45 * ctx.scale, 0.8 * ctx.scale), 1.4,
                            avoid=pa.dilate(hole, ctx.wrap, True))
        pore = bp.zeros(ctx)
        for m in pores:
            pore |= m
        ctx.data.update(holes=holes, hole=hole, pore=pore)

    def _sponge_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        n2 = c.length_of("secondary")
        c.set(ctx.data["hole"], "secondary", min(n2 - 1, 1))
        c.tag("hole")[ctx.data["hole"]] = True

    def _sponge_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ~ctx.data["hole"]
        off = bp.surface_offsets(self, ctx, "sponge_surf", body, 1, 1.1, flat=0.62 - 0.35 * s.roughness,
                                 kind="value")
        c.shift(body & (off != 0), off, L.lo, L.up)

    def _sponge_large_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ~ctx.data["hole"]
        dark = self.smooth_mask(ctx, "damp", 0.1 + 0.3 * s.moisture, 0.55) & body
        c.shift(dark, -1, L.lo, L.up)
        # hole interiors: deep shadow under the upper-left wall
        n2 = c.length_of("secondary")
        for m in ctx.data["holes"]:
            rx, ry, rad = bp.region_coords(np.where(m, 0, -1), ctx.wrap)
            t = (rx + ry) / (1.4 * rad)
            lv = np.where(t < -0.1, 0, np.where(t < 0.55, 1, 2))
            c.set(m, "secondary", np.clip(lv, 0, n2 - 1))

    def _sponge_medium_detail(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        pore = ctx.data["pore"]
        c.set(pore, "secondary", 0)
        c.tag("hole")[pore] = True
        bp.protect(ctx, pore)

    def _sponge_small_detail(self, ctx: GenContext) -> None:
        s = ctx.settings
        body = ~ctx.canvas.tag("hole") & ~pa.dilate(ctx.canvas.tag("hole"), ctx.wrap)
        self._speckle(ctx, "sponge_specks", body, int(round(s.noise * 4 * ctx.scale ** 2)), 0.45)

    def _sponge_cracks(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        amt = max(0.0, s.cracks - 0.35) / 0.65
        if amt <= 0.01:
            return
        cr = self.crack_paths(ctx, amt, "sponge_cracks", length=(0.2, 0.45))
        cr = bp.four_connect(cr, None, ctx.wrap) & ~c.tag("hole")
        c.set(cr, "base", L.dark)
        c.tag("crack")[cr] = True

    def _sponge_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        hole = c.tag("hole")
        _, _, lip = bp.sunken_light(hole, ctx.wrap)
        lip &= ~c.tag("crack")
        c.set(lip, "base", L.lit)
        bp.protect(ctx, lip)
        # soft bulges between the holes
        dist = bp.mask_depth(~pa.dilate(hole, ctx.wrap), ctx.wrap, 4)
        crest = (dist >= 2) & ~hole & ~c.tag("crack")
        keep = bp.noise_keep(self, ctx, "crest", 0.35 + 0.3 * s.roughness, 1.4)
        c.shift(crest & keep, 1, L.lo, L.up)
        if s.moisture > 0.35:
            self._gloss(ctx, lip, (s.moisture - 0.35) * 0.45)

    def _sponge_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        hole = c.tag("hole")
        # rim above/left of each hole slopes away from the light
        rim = (pa.neighbour(hole, 0, 1, ctx.wrap, fill=False) | pa.neighbour(hole, 1, 0, ctx.wrap, fill=False)) \
            & ~hole & ~c.tag("crack")
        c.shift(rim, -1, L.dark)
        bp.protect(ctx, rim)

    def _sponge_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        n = c.length_of("glow")
        if s.glow > 0.02:
            rng = ctx.rng("sponge_glow")
            holes = ctx.data["holes"]
            k = max(1, int(round(len(holes) * min(1.0, 0.2 + 0.8 * s.glow))))
            for i in rng.permutation(len(holes))[:k]:
                m = holes[int(i)]
                rx, ry, rad = bp.region_coords(np.where(m, 0, -1), ctx.wrap)
                t = (rx + ry) / (1.4 * rad)
                floor = m & (t > -0.1)
                lv = np.where(t > 0.5, 2 + int(s.glow > 0.6), 1 + int(s.glow > 0.35))
                c.set(floor, "glow", np.clip(lv, 0, n - 1))
                c.emissive[floor] = True
                bp.protect(ctx, floor)
        if s.mineral > 0.2:
            body = ~c.tag("hole")
            self.mineral_grains(ctx, (s.mineral - 0.2) * 0.5, "accent", "sponge_min", mask=body,
                                size_range=(1, 2))

    # ==================================================================== vein
    def _prep_vein(self, ctx: GenContext) -> None:
        s = ctx.settings
        cell = self._cell_size(ctx, 2.6, 5.0)
        ctx.data["lobes"] = self._lobes(ctx, "clumps", cell, 0.9, 0.03 + 0.05 * s.organic)
        rng = ctx.rng("veins")
        W, H = ctx.w, ctx.h
        vein = bp.zeros(ctx)
        nodes: list[tuple[int, int]] = []
        # the design keeps its look at every size: counts grow with the side
        # length, branching is per 16x16-pixel of travel, strokes stay thin
        sc = max(1.0, ctx.scale)
        roots = max(1, int(round((0.6 + 2.2 * s.density) * sc ** 0.6)))
        pts = bp.scatter(ctx, rng, roots, W / (1.2 + roots ** 0.5))
        for (x, y) in pts:
            ang0 = rng.uniform(0, 2 * math.pi)
            arms = 2 + int(rng.random() < 0.5 + 0.3 * s.density)
            for a in range(arms):
                ang = ang0 + a * 2 * math.pi / arms + rng.normal(0, 0.35)
                length = int(W * rng.uniform(0.35, 0.6) * (0.8 + 0.5 * s.density) / sc ** 0.2)
                self._branch(ctx, rng, x + 0.5, y + 0.5, ang, length, 0, vein, nodes,
                             (0.28 + 0.2 * s.organic) / sc ** 0.5, (0.08 + 0.1 * s.density) / sc ** 0.6)
            nodes.append((x, y))
        width = max(1, int(round(sc ** 0.5 / 1.4)))
        if width > 1:
            vein = bp.thicken(vein, width, ctx.wrap)
        node = bp.zeros(ctx)
        r = max(1, int(round((0.6 + 0.9 * s.cluster_size) * sc ** 0.6)))
        for (x, y) in nodes:
            for dy in range(-r + 1, r):
                for dx in range(-r + 1, r):
                    if abs(dx) + abs(dy) < r + (r > 1):
                        node[(y + dy) % H, (x + dx) % W] = True
        ctx.data.update(vein=vein, node=node, nodes=nodes)

    def _branch(self, ctx, rng, x, y, ang, length, depth, out, nodes, turn, p_branch) -> None:
        W, H = ctx.w, ctx.h
        px = py = None
        for i in range(max(1, length)):
            ix, iy = int(math.floor(x)) % W, int(math.floor(y)) % H
            if px is not None and ix != px and iy != py and rng.random() < 0.3:
                if rng.random() < 0.5:
                    out[py, ix] = True
                else:
                    out[iy, px] = True
            out[iy, ix] = True
            px, py = ix, iy
            if depth < 2 and i > 2 and rng.random() < p_branch:
                child = ang + rng.choice([-1, 1]) * rng.uniform(0.55, 1.1)
                self._branch(ctx, rng, x, y, child, int((length - i) * rng.uniform(0.35, 0.7)),
                             depth + 1, out, nodes, turn, p_branch * 0.7)
            ang += rng.normal(0, turn)
            x += math.cos(ang)
            y += math.sin(ang)
        if depth == 0 and rng.random() < 0.5:
            nodes.append((px, py))

    def _vein_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.lo)
        cre = self._crevices(ctx, ctx.data["lobes"])
        ctx.data["crevice"] = cre
        c.set(cre, "base", L.dark)
        vein = ctx.data["vein"]
        c.set(vein, "accent", 0)
        c.tag("vein")[vein] = True
        bp.protect(ctx, vein)

    def _vein_material(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        off = self._dome_offsets(ctx, ctx.data["lobes"], 1.0, -0.1)
        off = pa.remove_small_clusters(off, 2, ctx.wrap)
        m = ~ctx.data["crevice"] & ~c.tag("vein") & (off != 0)
        c.shift(m, off, L.dark, L.mid)

    def _vein_large_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ~c.tag("vein")
        up = self.smooth_mask(ctx, "mat_light", 0.15, 0.6) & body
        dn = self.smooth_mask(ctx, "mat_dark", 0.15 + 0.25 * s.moisture, 0.6) & body & ~up
        c.shift(up, 1, L.dark, L.mid)
        c.shift(dn, -1, L.dark, L.mid)
        # the mat swells a little along each vein
        swell = pa.dilate(c.tag("vein"), ctx.wrap) & body & ~ctx.data["crevice"]
        c.shift(swell, 1, L.dark, L.mid)

    def _vein_medium_detail(self, ctx: GenContext) -> None:
        """Nodes: bulbous pustules where the veins start and end."""
        c = ctx.canvas
        node = ctx.data["node"]
        na = c.length_of("accent")
        lit, dark, _ = bp.raised_light(node, ctx.wrap)
        c.set(node, "accent", min(na - 1, 2))
        c.set(node & lit, "accent", min(na - 1, 3))
        c.set(node & dark & ~lit, "accent", 1)
        c.tag("node")[node] = True
        c.tag("vein")[node] = True
        bp.protect(ctx, node)

    def _vein_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ~pa.dilate(c.tag("vein"), ctx.wrap)
        self._speckle(ctx, "mat_specks", body, int(round((1 + 3 * s.noise) * ctx.scale ** 2)), 0.5,
                      floor=L.dark, ceil=L.mid)
        # stray spores: single accent dots on the mat
        rng = ctx.rng("spores")
        n = int(round((0.5 + 2.5 * s.density) * s.noise * ctx.scale ** 2))
        ys, xs = np.nonzero(body & ~ctx.data["crevice"])
        if len(xs) and n > 0:
            idx = rng.choice(len(xs), size=min(n, len(xs)), replace=False)
            m = bp.zeros(ctx)
            m[ys[idx], xs[idx]] = True
            c.set(m, "accent", min(c.length_of("accent") - 1, 2))
            c.tag("spore")[m] = True
            bp.protect(ctx, m)

    def _vein_cracks(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        amt = max(0.0, s.cracks - 0.2) / 0.8
        if amt <= 0.01:
            return
        cr = self.crack_paths(ctx, amt, "mat_cracks", length=(0.2, 0.5))
        cr = bp.four_connect(cr, None, ctx.wrap) & ~c.tag("vein")
        c.set(cr, "base", L.deep)
        c.tag("crack")[cr] = True

    def _vein_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        lobes = ctx.data["lobes"]
        e = bp.edges(lobes, ctx.wrap)
        lit, _, _, _ = bp.bevel_masks(e)
        excl = c.tag("vein") | c.tag("crack") | ctx.data["crevice"] | c.tag("spore")
        c.shift(lit & ~excl, 1, L.dark, L.mid)
        # vein cores: brighter on their upper edge
        vein = c.tag("vein") & ~c.tag("node")
        top = vein & ~pa.neighbour(vein, 0, -1, ctx.wrap, fill=False)
        na = c.length_of("accent")
        c.set(top & (c.ramp == 2), "accent", min(na - 1, 1))
        if s.moisture > 0.4:
            self._gloss(ctx, lit & ~excl, (s.moisture - 0.4) * 0.3)

    def _vein_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        lobes = ctx.data["lobes"]
        e = bp.edges(lobes, ctx.wrap)
        _, dark, _, _ = bp.bevel_masks(e)
        excl = c.tag("vein") | c.tag("crack") | c.tag("spore")
        c.shift(dark & ~excl, -1, L.dark)
        # veins sit on top of the mat: a drop shadow below/right
        vein = c.tag("vein")
        drop = (pa.neighbour(vein, 0, -1, ctx.wrap, fill=False) | pa.neighbour(vein, -1, 0, ctx.wrap, fill=False)) \
            & ~vein & ~c.tag("crack")
        c.set(drop, "base", np.minimum(c.level, L.dark))

    def _vein_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if s.glow <= 0.02:
            return
        n = c.length_of("glow")
        vein = c.tag("vein")
        node = c.tag("node")
        # glowing stretches along the veins, always including the nodes
        keep = bp.noise_keep(self, ctx, "vein_glow", min(1.0, 0.25 + 0.9 * s.glow), 1.6)
        g = (vein & keep) | node
        top = g & ~pa.neighbour(g, 0, -1, ctx.wrap, fill=False)
        lv = np.where(top & (s.glow > 0.5), 2, 1)
        lv = np.where(node, n - 1 - int(s.glow < 0.6), lv)
        c.set(g, "glow", np.clip(lv, 0, n - 1))
        e = bp.mask_edges(node, ctx.wrap)
        c.set(node & (e["bottom"] | e["right"]) & ~(e["top"] | e["left"]), "glow", n - 2)
        c.emissive[g] = True
        spores = c.tag("spore")
        if spores.any() and s.glow > 0.3:
            c.set(spores, "glow", 1 + int(s.glow > 0.7))
            c.emissive[spores] = True

    # ==================================================================== moss
    def _prep_moss(self, ctx: GenContext) -> None:
        s = ctx.settings
        cell = self._cell_size(ctx, 3.6, 6.4)
        ctx.data["lobes"] = self._lobes(ctx, "tufts", cell, 1.0, 0.04 + 0.08 * s.organic)
        big = self._cell_size(ctx, 6.0, 10.0)
        ctx.data["patches"] = self._lobes(ctx, "patches", big, 1.0, 0.05)

    def _moss_base(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        cre = self._crevices(ctx, ctx.data["lobes"])
        # clumps grow into each other: only part of each border stays open
        cre &= bp.noise_keep(self, ctx, "moss_gaps", 0.45 + 0.35 * s.roughness, 1.8)
        cre = pa.remove_small_clusters(cre.astype(np.int32), 2, ctx.wrap).astype(bool) & cre
        ctx.data["crevice"] = cre
        c.set(cre, "base", L.lo)

    def _moss_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        light, _ = bp.dome_light(ctx.data["lobes"], ctx.wrap)
        grain = self.height_field(ctx, "moss_grain", 2.0, 1) - 0.5
        t = light * 0.8 + grain * (0.7 + 0.8 * s.roughness)
        off = np.where(t > 0.35, 1, np.where(t < -0.5, -1, 0)).astype(np.int32)
        off = pa.remove_small_clusters(off, 2, ctx.wrap)
        m = ~ctx.data["crevice"] & (off != 0)
        c.shift(m, off, L.lo, L.up)

    def _moss_large_detail(self, ctx: GenContext) -> None:
        """Patchy cover: groups of clumps a tone lighter or darker."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        patches = ctx.data["patches"]
        n = int(patches.max()) + 1
        rng = ctx.rng("patch_tone")
        pd = 0.2 + 0.2 * s.moisture
        vals = rng.choice([-1, 0, 1], size=n, p=[pd, 1 - pd - 0.25, 0.25])
        delta = vals[patches]
        c.shift((delta != 0) & ~ctx.data["crevice"], delta, L.lo, L.up)

    def _moss_medium_detail(self, ctx: GenContext) -> None:
        """Sprigs: short bright strands standing out of the clumps."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        light, _ = bp.dome_light(ctx.data["lobes"], ctx.wrap)
        mask = ~ctx.data["crevice"] & (light > -0.2)
        rng = ctx.rng("sprig_shape")
        count = int(round((1.5 + 5 * s.density) * ctx.scale ** 2))
        pts = bp.scatter(ctx, ctx.rng("sprigs"), count * 2, 2.6 * ctx.scale ** 0.5)
        sprig = bp.zeros(ctx)
        k = 0
        for (x, y) in pts:
            if k >= count or not mask[y, x]:
                continue
            ln = 1 + int(rng.random() < 0.6) + int(ctx.scale >= 2) * int(rng.integers(0, 3))
            dx = int(rng.choice([-1, 0, 0, 1]))
            seg = [(x + (dx if i == ln - 1 else 0), y + i) for i in range(ln)]
            sprig |= bp.point_mask(ctx, seg)
            k += 1
        sprig &= mask
        c.set(sprig, "base", np.maximum(c.level, L.up))
        below = pa.neighbour(sprig, 0, -1, ctx.wrap, fill=False) & ~sprig
        c.shift(below & ~ctx.data["crevice"], -1, L.lo)
        c.tag("sprig")[sprig] = True
        bp.protect(ctx, sprig)

    def _moss_small_detail(self, ctx: GenContext) -> None:
        s = ctx.settings
        mask = ~ctx.data["crevice"] & ~pa.dilate(ctx.canvas.tag("sprig"), ctx.wrap)
        self._speckle(ctx, "moss_specks", mask, int(round((1 + s.noise * 5) * ctx.scale ** 2)), 0.5,
                      size=(2, 3))

    def _moss_cracks(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        amt = max(0.0, s.cracks - 0.3) / 0.7
        if amt <= 0.01:
            return
        # bare gaps in the cover along clump borders
        gaps = ctx.data["crevice"] & self.smooth_mask(ctx, "gaps", amt * 0.7, 0.8)
        c.set(gaps, "base", L.deep)
        c.tag("crack")[gaps] = True

    def _moss_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        light, _ = bp.dome_light(ctx.data["lobes"], ctx.wrap)
        excl = c.tag("crack") | ctx.data["crevice"]
        tips = c.tag("sprig") & ~pa.neighbour(c.tag("sprig"), 0, -1, ctx.wrap, fill=False)
        c.set(tips & ~excl, "base", L.lit)
        crown = (light > 0.75) & ~excl & ~c.tag("sprig")
        keep = bp.noise_keep(self, ctx, "moss_lit", 0.45 + 0.3 * (1 - s.roughness), 1.5)
        c.shift(crown & keep, 1, L.lo, L.lit)
        if s.moisture > 0.35:
            self._gloss(ctx, (crown | tips) & ~excl, (s.moisture - 0.35) * 0.35)

    def _moss_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.set(ctx.data["crevice"] & ~c.tag("crack"), "base", L.dark)
        # the clump above a crevice overhangs it: one more dark pixel under the clump
        light, _ = bp.dome_light(ctx.data["lobes"], ctx.wrap)
        under = (light < -0.8) & ~c.tag("sprig") & ~c.tag("crack") & ~ctx.data["crevice"]
        c.shift(under, -1, L.dark)

    def _moss_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        mask = ~ctx.data["crevice"] & ~c.tag("crack")
        if s.glow > 0.02:
            rng = ctx.rng("moss_spores")
            count = int(round((0.8 + 4 * s.glow) * (0.6 + s.density) * ctx.scale ** 2))
            for cells in bp.clusters(ctx, "spore_pos", count, (1, 1 + int(s.glow > 0.5)),
                                             mask=mask, min_dist=3):
                m = bp.point_mask(ctx, cells) & mask
                c.set(m, "glow", 1 + int(rng.random() < s.glow * 0.8))
                c.emissive[m] = True
                bp.protect(ctx, m)
        if s.mineral > 0.2:
            self.mineral_grains(ctx, (s.mineral - 0.2) * 0.5, "accent", "moss_flowers", mask=mask,
                                size_range=(1, 1))
