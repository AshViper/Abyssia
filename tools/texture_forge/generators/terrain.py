"""Terrain: rock, mud, sediment and other ground blocks.

Modes (variant, or picked from the material when ``auto``): rough, layered,
cobbled, smooth and ``strata`` - a deepslate-like slate: long horizontal
tone clusters, a few wavy strata lines, fine horizontal grain dashes,
restrained highlights (never the top ramp level) and low-angle cracks.

``alteration`` paints weathered patches in the *secondary* ramp (heat
staining, volcanic glass, mineral films, living films), keeping the rock's
own shading so the patches read as the same stone, changed.

``recolor_source`` (opt-in) skips the pattern generation: the reference's
own tones become the base levels (recoloured through the palette) and only
the decoration layers run - stains, extra bedding, alteration, cracks,
minerals, wet gloss, glow.  The output is then a derivative of the reference.
"""
from __future__ import annotations

import numpy as np

from core import noise
from core import pixel_art as pa
from core.layers import GenContext
from core.palette import hex_to_rgb, luminance, oklab_to_lch, rgb_to_oklab

from ._block_patterns import base_to_role as bp_base_to_role
from .base import BaseGenerator

AUTO_MODE = {"rock": "rough", "mud": "smooth", "sediment": "layered", "mineral": "rough",
             "thermal": "rough", "crystal": "cobbled", "metal": "smooth", "organic": "smooth",
             "plant": "smooth", "slate": "strata"}


class TerrainGenerator(BaseGenerator):
    category = "terrain"

    def mode(self, ctx: GenContext) -> str:
        v = ctx.settings.variant
        return AUTO_MODE.get(ctx.settings.material, "rough") if v in ("", "auto") else v

    @staticmethod
    def _levels(ctx: GenContext) -> np.ndarray:
        """Macro level map from the material layer (or the current canvas if it was skipped)."""
        if "levels" not in ctx.data:
            ctx.data["levels"] = ctx.canvas.level.copy()
        return ctx.data["levels"]

    # ---------------------------------------------------------------- layers
    # ------------------------------------------------------------ recolour
    @staticmethod
    def recolor(ctx: GenContext) -> bool:
        return ctx.settings.recolor_source and "source_rgba" in ctx.data

    def recolor_host(self, ctx: GenContext) -> np.ndarray:
        """Pixels of the reference that belong to the host material (the rest is ore / inclusions)."""
        if "recolor_host" in ctx.data:
            return ctx.data["recolor_host"]
        src = ctx.data["source_rgba"][..., :3]
        s = ctx.settings
        if s.source_host:
            host_cols = {tuple(hex_to_rgb(h.strip())) for h in s.source_host.split(",") if h.strip()}
            host = np.array([tuple(p) in host_cols for p in src.reshape(-1, 3)]).reshape(ctx.h, ctx.w)
        elif self.category == "ore":
            chroma = oklab_to_lch(rgb_to_oklab(src.reshape(-1, 3).astype(np.float64)))[:, 1]
            host = (chroma < 0.03).reshape(ctx.h, ctx.w)
        else:
            host = np.ones((ctx.h, ctx.w), dtype=bool)
        ctx.data["recolor_host"] = host
        return host

    @staticmethod
    def rank_levels(rgb: np.ndarray, mask: np.ndarray, lo: int, hi: int, spare_ends: bool) -> np.ndarray:
        """Levels lo..hi for the pixels in ``mask``, from the rank of their tone (dark -> light).

        With ``spare_ends`` the extreme levels are kept free when the source has few enough tones."""
        out = np.zeros(mask.shape, dtype=np.int32)
        if not mask.any():
            return out
        lum = np.round(luminance(rgb[mask].astype(np.float64)), 5)
        tones, rank = np.unique(lum, return_inverse=True)
        n = len(tones)
        rank = rank.ravel().astype(np.float64)
        span = hi - lo
        if n <= 1:
            out[mask] = (lo + hi) // 2
        elif spare_ends and n <= span - 1:
            out[mask] = lo + 1 + np.rint(rank * (span - 2) / (n - 1)).astype(np.int32)
        else:
            out[mask] = lo + np.rint(rank * span / (n - 1)).astype(np.int32)
        return out

    def _recolor_base(self, ctx: GenContext) -> None:
        """The reference's host tones, ranked dark -> light, become base levels (0 and K+1 stay free if possible)."""
        src = ctx.data["source_rgba"]
        K = self.K(ctx)
        host = self.recolor_host(ctx)
        lv = self.rank_levels(src[..., :3], host, 0, K + 1, True)
        lv[~host] = K // 2 + 1
        c = ctx.canvas
        c.set(np.ones((ctx.h, ctx.w), dtype=bool), "base", lv)
        c.tag("protect")[:] = True           # the reference's pixel layout is kept as it is
        ctx.data["levels"] = lv
        ctx.data["H"] = lv / max(1, K + 1)
        if ctx.settings.layering > 0.5:
            # extra bedding planes as a decoration (strongly layered rocks)
            self._strata_bedding(ctx, 2 * ctx.settings.layering - 1.0)

    def _strata_bedding(self, ctx: GenContext, amount: float) -> None:
        s = ctx.settings
        nb = max(2, int(round((2 + 2.5 * amount) * ctx.scale ** 0.5)))
        wy = noise.value_noise(ctx.w, ctx.h, ctx.rng("strata"), 2, 1) * (0.25 + 0.2 * s.organic)
        _, y = noise.grid(ctx.w, ctx.h)
        line_rows = np.mod(y * nb + wy, 1.0) < 0.5 / max(1, ctx.h / nb)
        keep = self.height_field(ctx, "bed_keep", 1.4, 1, aspect=(0.5, 1.5)) > 0.62 - 0.3 * amount
        ctx.data["bedding"] = line_rows & keep

    # ---------------------------------------------------------------- layers
    def layer_base(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        c.fill("base", self.K(ctx) // 2 + 1)
        if self.recolor(ctx):
            self._recolor_base(ctx)
            return
        mode = self.mode(ctx)
        if mode == "strata":
            self._strata_base(ctx)
            return
        layering = s.layering + (0.45 if mode == "layered" else 0.0)
        warp = 0.04 + 0.12 * s.organic
        H = self.height_field(ctx, "macro", 1.45, aspect=self.aspect(ctx, layering * 0.8), warp=warp)
        rock_w = s.rock * (0.55 if mode == "cobbled" else 0.2)
        if mode == "cobbled" or s.rock > 0.5:
            cells = max(2, int(round(self.feature_freq(ctx, 0.55 if mode == "cobbled" else 0.8))))
            cell = noise.cellular(ctx.w, ctx.h, ctx.rng("stones"), cells, cells, 0.85,
                                  offset=noise.warp_offsets(ctx.w, ctx.h, ctx.rng("stone_warp"), 3, 0.03))
            dome = 1.0 - np.clip(cell.f1, 0, 1.2) / 1.2
            H = noise.normalize(H * (1 - rock_w) + dome * rock_w)
            ctx.data["cells"] = cell
        if layering > 0.05:
            nb = max(1, int(round(2 + 3 * layering * ctx.scale ** 0.5)))
            wy = noise.value_noise(ctx.w, ctx.h, ctx.rng("strata"), 2, 1) * 0.35
            _, y = noise.grid(ctx.w, ctx.h)
            bands = 0.5 + 0.5 * np.sin(2 * np.pi * (y * nb + wy))
            H = noise.normalize(H * (1 - 0.55 * layering) + bands * 0.55 * layering)
        ctx.data["H"] = H

    def layer_material(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if self.recolor(ctx):
            return
        H = ctx.data["H"]
        mat = s.material
        if mat == "mud" or self.mode(ctx) == "smooth":
            H = noise.normalize(H + 0.35 * self.height_field(ctx, "mud", 0.6, 1))
        if mat == "sediment":
            grain = self.height_field(ctx, "grain", 2.0, 1, aspect=(1.0, 4.0))
            H = noise.normalize(H * 0.75 + grain * 0.25)
        if s.crystal > 0.05:
            cells = max(2, int(round(self.feature_freq(ctx, 0.8))))
            cell = noise.cellular(ctx.w, ctx.h, ctx.rng("facets"), cells)
            tone = ctx.rng("facet_tone").random(len(cell.points))[cell.cell]
            H = noise.normalize(H * (1 - 0.6 * s.crystal) + tone * 0.6 * s.crystal)
        if s.metallic > 0.05:
            brush = self.height_field(ctx, "brush", 1.5, 1, aspect=(0.5, 5.0))
            H = noise.normalize(H * (1 - 0.4 * s.metallic) + brush * 0.4 * s.metallic)
        weights = np.asarray(ctx.data["weights"], dtype=np.float64)
        if mat == "mud":
            weights = weights * np.exp(-((np.linspace(-1, 1, len(weights))) ** 2) * 1.2)
        levels = self.quantize_body(ctx, H, weights=weights)
        levels = self.cleanup(ctx, levels)
        c.ramp[:] = 0
        c.level[:] = levels
        ctx.data["levels"] = levels
        ctx.data["H"] = H

    # ------------------------------------------------------------ strata (slate)
    def _strata_base(self, ctx: GenContext) -> None:
        """Deepslate-like slate: tone clusters stretched along the bedding, plus wavy strata."""
        s = ctx.settings
        ax, ay = self.aspect(ctx, 0.15 + 0.3 * s.layering)
        H = self.height_field(ctx, "macro", 1.5, aspect=(ax * 0.85, ay * 1.1), warp=0.05 + 0.08 * s.organic)
        fine = self.height_field(ctx, "bedding", 2.6, 1, aspect=(0.55, 1.9))
        nb = max(2, int(round((2 + 2.5 * s.layering) * ctx.scale ** 0.5)))
        wy = noise.value_noise(ctx.w, ctx.h, ctx.rng("strata"), 2, 1) * (0.25 + 0.2 * s.organic)
        _, y = noise.grid(ctx.w, ctx.h)
        bands = 0.5 + 0.5 * np.sin(2 * np.pi * (y * nb + wy))
        wl = 0.1 + 0.3 * s.layering
        H = noise.normalize(H * (1 - wl - 0.28) + bands * wl + fine * 0.28)
        ctx.data["H"] = H
        # a few thin, broken strata lines (bedding planes) one step darker
        line_rows = (np.mod(y * nb + wy, 1.0) < 0.5 / max(1, ctx.h / nb)) if nb else np.zeros_like(H, bool)
        keep = self.height_field(ctx, "bed_keep", 1.4, 1, aspect=(0.5, 1.5)) > 0.62 - 0.3 * s.layering
        ctx.data["bedding"] = line_rows & keep

    def _strata_dashes(self, ctx: GenContext, key: str, count: int, length: tuple[int, int]) -> tuple[np.ndarray, np.ndarray]:
        """Short horizontal grain dashes: (lighter, darker)."""
        rng = ctx.rng(key)
        up = np.zeros((ctx.h, ctx.w), dtype=bool)
        dn = np.zeros((ctx.h, ctx.w), dtype=bool)
        for _ in range(count):
            x, y = int(rng.integers(ctx.w)), int(rng.integers(ctx.h))
            tgt = up if rng.random() < 0.45 else dn
            for k in range(int(rng.integers(length[0], length[1] + 1))):
                tgt[y, (x + k) % ctx.w] = True
        return up & ~dn, dn

    def _alteration(self, ctx: GenContext) -> None:
        """Weathered patches in the secondary ramp, keeping the rock's own shading."""
        s, c = ctx.settings, ctx.canvas
        if s.alteration <= 0.02:
            return
        aspect = (0.6, 1.6) if self.mode(ctx) == "strata" else None
        m = self.smooth_mask(ctx, "alteration", 0.06 + 0.5 * s.alteration, 0.75, 2, aspect=aspect, warp=0.05)
        m &= ~c.tag("joint")
        m = pa.remove_small_clusters(m.astype(np.int32), max(3, self.min_cluster(ctx)), ctx.wrap).astype(bool)
        m &= c.ramp == 0
        if not m.any():
            return
        n = c.length_of("secondary")
        K = self.K(ctx)
        c.set(m, "secondary", bp_base_to_role(c.level, K, n, 1, max(1, n - 2)))
        c.tag("altered")[m] = True

    def layer_large_detail(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if self.recolor(ctx):
            # decorations only: damp patches, extra bedding planes, alteration
            K = self.K(ctx)
            if s.moisture > 0.3:
                damp = self.smooth_mask(ctx, "stain_dark", 0.3 * (s.moisture - 0.3), 0.55, aspect=(0.6, 1.6))
                c.shift(damp, -1, 1, K)
            bed = ctx.data.get("bedding")
            if bed is not None and bed.any():
                c.shift(bed, -1, 1)
                c.tag("bedding")[bed] = True
            self._alteration(ctx)
            return
        if self.mode(ctx) == "strata":
            K = self.K(ctx)
            dark = self.smooth_mask(ctx, "stain_dark", 0.08 + 0.2 * s.moisture, 0.55, aspect=(0.6, 1.6))
            light = self.smooth_mask(ctx, "stain_light", 0.05 + 0.06 * (1 - s.moisture), 0.55, aspect=(0.6, 1.6)) & ~dark
            c.shift(dark, -1, 1, K)
            c.shift(light, 1, 1, K - 1)
            bed = ctx.data.get("bedding")
            if bed is not None and bed.any():
                c.shift(bed, -1, 1)
                c.tag("bedding")[bed] = True
            self._alteration(ctx)
            return
        # broad stains: wet or weathered patches one step darker / lighter
        dark = self.smooth_mask(ctx, "stain_dark", 0.10 + 0.18 * s.moisture + 0.08 * s.roughness, 0.55)
        light = self.smooth_mask(ctx, "stain_light", 0.06 + 0.1 * (1 - s.moisture), 0.55) & ~dark
        K = self.K(ctx)
        c.shift(dark, -1, 1, K)
        c.shift(light, 1, 1, K)
        if self.mode(ctx) == "cobbled" and "cells" in ctx.data:
            cell = ctx.data["cells"]
            joints = cell.edge < (0.12 + 0.1 * s.roughness)
            joints = pa.remove_small_clusters(joints.astype(np.int32), 3, ctx.wrap).astype(bool)
            c.set(joints, "base", 1)
            c.tag("joint")[joints] = True

    def layer_medium_detail(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if self.recolor(ctx):
            return
        if self.mode(ctx) == "strata":
            # grains flattened along the bedding
            n = int(round((5 + 6 * s.roughness) * ctx.scale ** 1.6))
            up, dn = self._strata_dashes(ctx, "slate_grain", n, (2, max(2, ctx.px(2 + s.roughness))))
            K = self.K(ctx)
            c.shift(up, 1, 1, K - 1)
            c.shift(dn, -1, 1, K)
            return
        count = int(round((2 + 5 * s.roughness) * ctx.scale ** 1.6))
        lo = max(2, ctx.px(2))
        hi = max(lo + 1, ctx.px(3 + 3 * s.roughness))
        rng = ctx.rng("pebble_sign")
        up = np.zeros((ctx.h, ctx.w), dtype=bool)
        down = np.zeros((ctx.h, ctx.w), dtype=bool)
        for cells in self.place_clusters(ctx, "pebbles", count, (lo, hi), compact=0.8):
            pa.stamp(up if rng.random() < 0.5 else down, cells, ctx.wrap)
        joint = c.tag("joint")
        c.shift(up & ~joint, 1, 1, self.K(ctx))
        c.shift(down & ~joint, -1, 1, self.K(ctx))

    def layer_small_detail(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if s.noise <= 0.02 or self.recolor(ctx):
            return
        count = int(round(s.noise * 10 * ctx.scale ** 2))
        size = (2, 2 + int(s.roughness * 2))
        rng = ctx.rng("grain_sign")
        up = np.zeros((ctx.h, ctx.w), dtype=bool)
        down = np.zeros((ctx.h, ctx.w), dtype=bool)
        for cells in self.place_clusters(ctx, "grain", count, size, min_dist=2.2, compact=0.5):
            pa.stamp(up if rng.random() < 0.45 else down, cells, ctx.wrap)
        c.shift(up, 1, 1, self.K(ctx))
        c.shift(down, -1, 1, self.K(ctx))

    def layer_cracks(self, ctx: GenContext) -> None:
        if ctx.settings.crack_color:
            self.paint_veins(ctx, self.vein_paths(ctx))
            return
        cracks = self.crack_paths(ctx, ctx.settings.cracks)
        self.paint_cracks(ctx, cracks)

    def vein_paths(self, ctx: GenContext) -> np.ndarray:
        """Short, scattered, sometimes branching and thickened streaks (crying-obsidian tears)."""
        s = ctx.settings
        out = np.zeros((ctx.h, ctx.w), dtype=bool)
        if s.cracks <= 0.02:
            return out
        rng = ctx.rng("veins")
        n = max(1, int(round((1 + 5 * s.cracks) * ctx.scale ** 0.8)))

        def mark(x: float, y: float) -> None:
            xi, yi = int(np.floor(x)), int(np.floor(y))
            if ctx.wrap:
                out[yi % ctx.h, xi % ctx.w] = True
            elif 0 <= xi < ctx.w and 0 <= yi < ctx.h:
                out[yi, xi] = True

        def walk(x: float, y: float, ang: float, length: int, depth: int) -> None:
            for _ in range(length):
                mark(x, y)
                if rng.random() < 0.3:        # chunky: thicken sideways now and then
                    mark(x - np.sin(ang), y + np.cos(ang))
                ang += rng.normal(0, 0.55)
                x += np.cos(ang)
                y += np.sin(ang)
                if depth == 0 and rng.random() < 0.12:
                    walk(x, y, ang + rng.choice([-1, 1]) * rng.uniform(0.8, 1.4), int(rng.integers(1, 4)), 1)

        for _ in range(n):
            ang = np.pi / 2 + rng.choice([-1, 1]) * rng.uniform(0.3, 1.1)   # steep diagonals
            walk(rng.uniform(0, ctx.w), rng.uniform(0, ctx.h), ang, int(rng.integers(ctx.px(3), ctx.px(7) + 1)), 0)
        return out

    def paint_veins(self, ctx: GenContext, veins: np.ndarray) -> None:
        """Crying-obsidian-like crack veins in the accent2 ramp: a dark saturated fringe on part of
        the rim, a bright core with a few brightest runs.  The core glows with the glow slider."""
        if not veins.any():
            return
        s, c = ctx.settings, ctx.canvas
        n = c.length_of("accent2")
        core = veins & c.opaque
        fringe = pa.dilate(core, ctx.wrap) & ~core & c.opaque
        fringe &= self.height_field(ctx, "vein_fringe", 1.7, 1) > 0.35
        bright = core & (self.height_field(ctx, "vein_bright", 1.9, 1) > 0.58)
        c.set(fringe, "accent2", max(0, n - 3))
        c.set(core, "accent2", n - 2)
        c.set(bright, "accent2", n - 1)
        c.tag("crack")[core] = True
        c.tag("vein")[core] = True
        c.tag("protect")[core | fringe] = True
        if s.glow > 0.02:
            c.emissive[core] = True

    def layer_highlights(self, ctx: GenContext) -> None:
        s = ctx.settings
        if self.recolor(ctx):
            # the reference is already lit: its lightest tones are the lit rims (wet gloss uses them)
            c = ctx.canvas
            c.tag("lit")[(c.ramp == 0) & (c.level >= self.K(ctx))] = True
            return
        # slate keeps its highlights small: they show the relief, they never reach the top of the ramp
        ceil = self.K(ctx) if self.mode(ctx) == "strata" else self.K(ctx) + 1
        amount = 0.25 + 0.3 * s.roughness if self.mode(ctx) == "strata" else 0.35 + 0.4 * s.roughness
        self.bevel(ctx, self._levels(ctx), +1, amount, ~ctx.canvas.tag("crack"), tag="lit", ceil=ceil)

    def layer_shadows(self, ctx: GenContext) -> None:
        s = ctx.settings
        if self.recolor(ctx):
            return
        self.bevel(ctx, self._levels(ctx), -1, 0.3 + 0.45 * s.roughness, ~ctx.canvas.tag("crack"),
                   floor=1)

    def layer_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if self.mode(ctx) != "strata" and s.alteration > 0.02 and not c.tag("altered").any():
            self._alteration(ctx)
        low = (c.level <= 2) | pa.dilate(c.tag("crack"), ctx.wrap)
        self.mineral_grains(ctx, s.mineral + 0.4 * s.crystal, "accent", "minerals", prefer=low)
        if s.metallic > 0.1:
            lit = c.tag("lit") & (c.ramp == 0)
            self.mineral_grains(ctx, s.metallic * 0.6, "accent2", "metal_specks", prefer=lit,
                                size_range=(1, 2))
        if s.moisture > 0.4:
            # wet gloss: a few top-level pixels on lit rims
            lit = c.tag("lit") & (c.ramp == 0)
            ys, xs = np.nonzero(lit)
            if len(xs):
                rng = ctx.rng("gloss")
                k = int(len(xs) * (s.moisture - 0.4) * 0.35)
                idx = rng.choice(len(xs), size=min(k, len(xs)), replace=False)
                g = np.zeros_like(lit)
                g[ys[idx], xs[idx]] = True
                c.set(g, "base", c.length_of("base") - 1)
                c.tag("protect")[g] = True
        if s.glow > 0.02:
            # vein cores already glow; elsewhere a few crack / mineral pixels light up
            self.glow_points(ctx, s.glow * 0.5, (c.tag("crack") & ~c.tag("vein")) | c.tag("mineral"))
