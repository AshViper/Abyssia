"""Thermal: hydrothermal-vent rocks (spec 18).

Variants
--------
* ``thermal_rock``    dark basalt-like rock split by heat seams (Voronoi plate
                      borders) that glow with the *glow* slider; sulfur bits
                      crust the seams.
* ``black_smoker``    near-black porous sulfide chimney rock: vertical streaks,
                      conduit pores and metallic sulfide flecks.
* ``white_smoker``    pale chalky barite / anhydrite rock with raised light
                      crusts and small pores.
* ``sulfur_rock``     dark rock with rim-lit yellow sulfur crust patches.
* ``mineral_crust``   warped, layered crust bands in rock / rust / ochre / cyan.
* ``thermal_crystal`` dark rock with embedded prism crystals that glow.

The host rock is the terrain generator's rock; every variant adds its own
features on top in the layer they belong to (seams -> cracks, crusts ->
large detail, pores -> medium detail, accent-coloured minerals -> accent).
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.layers import GenContext

from .ore import GLOW_FOR, OreGenerator, blob_offsets, effective_accent
from .terrain import TerrainGenerator

VARIANTS = ("thermal_rock", "black_smoker", "white_smoker", "sulfur_rock", "mineral_crust",
            "thermal_crystal")

# Ramp anchors each variant defines (base is skipped by the pipeline when the
# user set a base colour, accent when the user chose an accent).
VARIANT_ROLES: dict[str, dict[str, str]] = {
    "thermal_rock": {"glow": "heat"},
    "black_smoker": {"base": "black_smoker", "accent": "pyrite", "glow": "heat"},
    "white_smoker": {"base": "white_smoker", "secondary": "bone"},
    "sulfur_rock": {"accent": "sulfur", "glow": "heat"},
    "mineral_crust": {"secondary": "rust", "accent": "sulfur", "accent2": "cyan_mineral",
                      "glow": "heat"},
    "thermal_crystal": {},
}

# accents that glow with plain heat (orange) rather than a tinted light
WARM_ACCENTS = {"sulfur", "heat", "gold", "pyrite", "copper", "rust", "bone"}


def _mask(ctx: GenContext, cells) -> np.ndarray:
    m = np.zeros((ctx.h, ctx.w), dtype=bool)
    for x, y in cells:
        m[y % ctx.h, x % ctx.w] = True
    return m


def _components(ctx: GenContext, mask: np.ndarray, min_size: int = 1) -> list[list[tuple[int, int]]]:
    lab, sizes = pa.label_components(mask.astype(np.int32), ctx.wrap, mask)
    out = []
    for i, sz in enumerate(sizes):
        if sz < min_size:
            continue
        ys, xs = np.nonzero(lab == i)
        out.append(sorted(zip(xs.tolist(), ys.tolist())))
    return out


class ThermalGenerator(TerrainGenerator):
    category = "thermal"

    def __init__(self) -> None:
        self._ore = OreGenerator()

    # -------------------------------------------------------------- setup
    @staticmethod
    def variant(ctx_or_settings) -> str:
        s = getattr(ctx_or_settings, "settings", ctx_or_settings)
        return s.variant if s.variant in VARIANTS else "thermal_rock"

    def mode(self, ctx: GenContext) -> str:
        return "smooth" if self.variant(ctx) == "white_smoker" else "rough"

    def palette_roles(self, s) -> dict[str, str]:
        v = self.variant(s)
        roles = dict(VARIANT_ROLES.get(v, {}))
        if v == "thermal_crystal":
            # warm crystals glow with plain heat, others in their own light
            acc = effective_accent(s)
            roles["glow"] = "heat" if acc in WARM_ACCENTS else GLOW_FOR.get(acc, acc)
        return roles

    def body_levels(self, s, a) -> int:
        k = super().body_levels(s, a)
        if self.variant(s) == "black_smoker" and s.levels == 0:
            k = min(k, 4)   # the black ramp is narrow: fewer, clearer steps
        return k

    def ramp_lengths(self, k: int) -> dict[str, int]:
        d = super().ramp_lengths(k)
        d["accent"] = 6      # same accent layout as ore (crystals / crusts are painted like ore)
        return d

    def prepare(self, ctx: GenContext) -> None:
        v = self.variant(ctx)
        # the host's level grid (replaced by the material layer; kept when it is off)
        ctx.data.setdefault("levels", np.full((ctx.h, ctx.w), self.K(ctx) // 2 + 1, dtype=np.int32))
        if v == "thermal_crystal":
            self._plan_crystals(ctx)
        elif v == "thermal_rock":
            # high contrast basalt: flatter tone distribution than plain terrain
            w = np.asarray(ctx.data["weights"], dtype=np.float64)
            w = 0.5 * w / w.sum() + 0.5 / len(w)
            ctx.data["weights"] = w / w.sum()
        elif v == "black_smoker":
            # keep the dark ramp readable: bias the wall toward its upper tones,
            # the deepest tones are left for streaks and pores
            w = np.asarray(ctx.data["weights"], dtype=np.float64)
            w = w * np.linspace(0.3, 2.0, len(w))
            ctx.data["weights"] = w / w.sum()

    # --------------------------------------------------------- base / material
    def layer_base(self, ctx: GenContext) -> None:
        super().layer_base(ctx)
        v = self.variant(ctx)
        H = ctx.data["H"]
        if v == "thermal_rock":
            # basalt: faint vertical columns under the rough surface
            cols = self.height_field(ctx, "basalt_cols", 0.9, 2, aspect=(2.6, 0.45))
            H = noise.normalize(0.68 * H + 0.32 * cols)
        elif v == "black_smoker":
            # chimney walls: strong vertical flow streaks
            streak = self.height_field(ctx, "streaks", 1.1, 2, aspect=(3.2, 0.3), warp=0.03)
            H = noise.normalize(0.4 * H + 0.6 * streak)
        elif v == "white_smoker":
            soft = self.height_field(ctx, "chalk", 0.6, 1)
            H = noise.normalize(0.65 * H + 0.35 * soft)
        elif v == "mineral_crust":
            self._plan_crust_bands(ctx)
            wav = ctx.data["crust_shade"]
            H = noise.normalize(0.5 * H + 0.5 * wav)
            # the layering is the block's identity: lay the bands down flat here
            # (always on); the material layer shades them
            self._paint_crust_bands(ctx, flat=True)
        ctx.data["H"] = H

    def layer_material(self, ctx: GenContext) -> None:
        super().layer_material(ctx)
        v = self.variant(ctx)
        if v == "mineral_crust":
            # rock layers stay dark so the coloured crust layers read against them
            K = self.K(ctx)
            lv = ctx.data["levels"]
            hi = max(2, K // 2 + 1)
            new = 1 + np.rint((lv - 1) * (hi - 1) / max(1, K - 1)).astype(np.int32)
            ctx.canvas.level[:] = new
            ctx.data["levels"] = new
            self._paint_crust_bands(ctx)
        elif v == "white_smoker":
            # chalk: pale and low contrast - body levels 1..K squeezed into the
            # upper part of the ramp; the dark end is left to pores and cracks
            K = self.K(ctx)
            lv = ctx.data["levels"]
            lo = max(2, (K + 1) // 2)
            new = lo + np.rint((lv - 1) * (K - lo) / max(1, K - 1)).astype(np.int32)
            ctx.canvas.level[:] = new
            ctx.data["levels"] = new

    # ---------------------------------------------------------- detail
    def layer_large_detail(self, ctx: GenContext) -> None:
        v = self.variant(ctx)
        if v == "mineral_crust":
            # bands already carry the large structure: only a few stains
            s, c = ctx.settings, ctx.canvas
            dark = self.smooth_mask(ctx, "stain_dark", 0.08 + 0.12 * s.moisture, 0.55)
            c.shift(dark, -1, 1, self.K(ctx))
            return
        super().layer_large_detail(ctx)
        if v == "white_smoker":
            self._white_crusts(ctx)
        elif v == "black_smoker":
            # darker sooty flow channels
            s, c = ctx.settings, ctx.canvas
            ch = self.smooth_mask(ctx, "soot", 0.14 + 0.12 * s.roughness, 0.9, aspect=(3.0, 0.4))
            c.shift(ch, -2, 1, self.K(ctx))

    def layer_medium_detail(self, ctx: GenContext) -> None:
        v = self.variant(ctx)
        if v in ("black_smoker", "white_smoker"):
            super().layer_medium_detail(ctx)
            self._pores(ctx)
        elif v == "mineral_crust":
            self._crust_bumps(ctx)
        else:
            super().layer_medium_detail(ctx)

    def layer_cracks(self, ctx: GenContext) -> None:
        v = self.variant(ctx)
        s = ctx.settings
        if v == "thermal_rock":
            self._heat_seams(ctx)
            return
        if v == "black_smoker":
            cracks = self.crack_paths(ctx, s.cracks, angle=90.0)
        elif v == "mineral_crust":
            # desiccation cracks run across the bands
            cracks = self.crack_paths(ctx, s.cracks * 0.8, angle=90.0, length=(0.2, 0.45))
        else:
            cracks = self.crack_paths(ctx, s.cracks)
        cm = ctx.data.get("crystal_mask")
        if cm is not None:
            cracks &= ~pa.dilate(cm, ctx.wrap, True)
        self.paint_cracks(ctx, cracks)
        fl = self._floor_level(ctx)
        if fl > 0 and cracks.any():
            ctx.canvas.level[cracks & (ctx.canvas.ramp == 0)] = fl
        if v == "sulfur_rock" and s.glow > 0.02 and cracks.any():
            # vent heat seeping through the cracks
            self._glow_in(ctx, cracks, s.glow, "crack_glow")

    def _rock_only(self, ctx: GenContext) -> np.ndarray:
        """Pixels the terrain host shading may touch (not crust bands, not cracks)."""
        c = ctx.canvas
        m = ~c.tag("crack")
        mat = ctx.data.get("crust_mat")
        if mat is not None:
            m &= mat == 0
        return m

    def layer_small_detail(self, ctx: GenContext) -> None:
        if self.variant(ctx) == "mineral_crust":
            return   # bands and bumps carry the detail; grain would only add noise
        super().layer_small_detail(ctx)

    def layer_highlights(self, ctx: GenContext) -> None:
        s = ctx.settings
        self.bevel(ctx, ctx.data["levels"], +1, 0.35 + 0.4 * s.roughness, self._rock_only(ctx),
                   tag="lit", ceil=self.K(ctx) + 1)
        ctx.data["ore_extra_glints"] = True
        if self.variant(ctx) == "mineral_crust":
            c = ctx.canvas
            band = ctx.data["crust_band"]
            top = (pa.neighbour(band, 0, -1, ctx.wrap, -1) != band) & ~c.tag("crack")
            keep = self.height_field(ctx, "band_lit", 1.4, 1) < 0.35 + 0.5 * ctx.settings.roughness
            c.shift(top & keep, 1)

    def layer_shadows(self, ctx: GenContext) -> None:
        s = ctx.settings
        self.bevel(ctx, ctx.data["levels"], -1, 0.3 + 0.45 * s.roughness, self._rock_only(ctx), floor=1)
        ctx.data["ore_deep_shadow"] = True
        if self.variant(ctx) == "mineral_crust":
            c = ctx.canvas
            band = ctx.data["crust_band"]
            bottom = (pa.neighbour(band, 0, 1, ctx.wrap, -1) != band) & ~c.tag("crack")
            c.shift(bottom, -1, floor=1)

    def layer_accent(self, ctx: GenContext) -> None:
        v = self.variant(ctx)
        {
            "thermal_rock": self._accent_thermal_rock,
            "black_smoker": self._accent_black_smoker,
            "white_smoker": self._accent_white_smoker,
            "sulfur_rock": self._accent_sulfur_rock,
            "mineral_crust": self._accent_mineral_crust,
            "thermal_crystal": self._accent_crystals,
        }[v](ctx)

    # ================================================================ helpers
    def _glow_in(self, ctx: GenContext, where: np.ndarray, amount: float, key: str,
                 heat: np.ndarray | None = None) -> np.ndarray:
        """Turn ``where`` into glowing pixels whose brightness follows a smooth
        heat field (runs of equal level, never salt-and-pepper)."""
        c = ctx.canvas
        out = np.zeros_like(where)
        if amount <= 0.02 or not where.any():
            return out
        ng = c.length_of("glow")
        if heat is None:
            heat = self.height_field(ctx, key, 1.1, 1)
        vals = heat[where]
        # hottest share grows with the slider
        hot_cut = np.quantile(vals, 1.0 - min(1.0, 0.25 + 0.75 * amount))
        lit = where & (heat >= hot_cut)
        top = max(1, min(ng - 1, int(round(0.6 + amount * (ng - 1)))))
        span = max(1e-6, float(heat[lit].max() - hot_cut)) if lit.any() else 1.0
        lv = np.clip(np.floor((heat - hot_cut) / span * (top + 0.999)), 0, top).astype(np.int32)
        c.set(lit, "glow", lv)
        c.emissive |= lit & (lv >= 1)
        c.tag("protect")[lit] = True
        return lit

    def _floor_level(self, ctx: GenContext) -> int:
        """Darkest base level used for holes and cracks (white smoker stays pale)."""
        if self.variant(ctx) == "white_smoker":
            return max(1, (self.K(ctx) + 1) // 2 - 1)
        return 0

    # ------------------------------------------------------ thermal rock
    def _heat_seams(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        amount = 0.3 + 0.7 * s.cracks
        cells = max(2, int(round((2.4 + 1.6 * (1 - s.cluster_size)) * max(0.5, ctx.scale) ** 0.6)))
        off = noise.warp_offsets(ctx.w, ctx.h, ctx.rng("seam_warp"), 3, 0.03 + 0.03 * s.roughness)
        cell = noise.cellular(ctx.w, ctx.h, ctx.rng("seam_cells"), cells, cells, 0.85, offset=off)
        cid = cell.cell
        rn = pa.neighbour(cid, 1, 0, ctx.wrap, -1)
        dn = pa.neighbour(cid, 0, 1, ctx.wrap, -1)
        if not ctx.wrap:
            rn[:, -1] = cid[:, -1]
            dn[-1, :] = cid[-1, :]
        # whole plate borders open up (continuous seams, never dotted)
        n_pts = len(cell.points)
        pair_rng = ctx.rng("seam_pairs")
        open_pair = pair_rng.random((n_pts, n_pts))
        open_pair = np.minimum(open_pair, open_pair.T) < amount
        seams = (((rn != cid) & open_pair[cid, np.where(rn >= 0, rn, cid)])
                 | ((dn != cid) & open_pair[cid, np.where(dn >= 0, dn, cid)]))
        # seams widen slowly with size (detail, not magnification)
        for _ in range(max(1, int(round(0.6 * ctx.scale ** 0.7))) - 1):
            seams |= pa.neighbour(seams, -1, 0, ctx.wrap, False) | pa.neighbour(seams, 0, -1, ctx.wrap, False)
        # a few dark hairline cracks branching off (not glowing)
        hair = self.crack_paths(ctx, s.cracks * 0.5, key="hairline", length=(0.15, 0.35)) & ~seams
        self.paint_cracks(ctx, hair)
        if not seams.any():
            return
        c.set(seams, "base", 0)
        c.tag("crack")[seams] = True
        c.tag("seam")[seams] = True
        ctx.data["seams"] = seams
        # rim lighting of the seam walls: upper lip catches light, lower wall is shaded
        lip = pa.neighbour(seams, 0, 1, ctx.wrap, False) & ~seams
        low = pa.neighbour(seams, 0, -1, ctx.wrap, False) & ~seams
        c.shift(lip, 1)
        c.shift(low & (c.level > 1), -1)
        ng = c.length_of("glow")
        g = s.glow
        if g > 0.02:
            heat = self.height_field(ctx, "seam_heat", 1.0, 1)
            # junctions of three plates run hottest
            nb_seam = sum(pa.neighbour(seams, dx, dy, ctx.wrap, False).astype(np.int32) for dx, dy in pa.N8)
            heat = heat + 0.35 * (nb_seam >= 3)
            lit = self._glow_in(ctx, seams, g, "seam_heat", heat)
            if g > 0.55:
                # the hottest spots warm the rock right next to them (dull red, not emissive)
                hot = lit & (c.ramp == 4) & (c.level >= ng - 1)
                halo = pa.outline(hot, ctx.wrap) & (c.ramp == 0)
                c.set(halo, "glow", 0)
                c.tag("protect")[halo] = True
        else:
            # cooled seams: black cracks with runs of dull dark red (not emissive)
            heat = self.height_field(ctx, "seam_heat", 1.0, 1)
            warm = seams & (heat >= np.quantile(heat[seams], 0.5))
            warm = pa.remove_small_clusters(warm.astype(np.int32), 3, ctx.wrap).astype(bool) & warm
            c.set(warm, "glow", 0)
        c.tag("protect")[seams] = True

    def _accent_thermal_rock(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        seams = ctx.data.get("seams")
        rng = ctx.rng("sulfur_bits")
        count = int(round((1.5 + 3.5 * s.mineral + 1.5 * s.density) * ctx.scale ** 1.4))
        if count <= 0:
            return
        near = None
        if seams is not None and seams.any():
            # sulfur precipitates right beside the vents
            near = pa.dilate(seams, ctx.wrap) & ~seams
        bodies = self._blobs(ctx, rng, count, (2.0, 4.5 + 3 * s.cluster_size), near,
                             avoid=seams if seams is not None else None)
        if bodies:
            self._ore.paint_ore(ctx, bodies, self._union(ctx, bodies), glow=False, minerals=False)
        if s.mineral > 0.3:
            free = (c.ramp == 0) & ~c.tag("crack")
            self.mineral_grains(ctx, (s.mineral - 0.3) * 0.5, "accent2", "rock_grit", size_range=(2, 2),
                                mask=free)

    # ------------------------------------------------------- black smoker
    def _pores(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        v = self.variant(ctx)
        rng = ctx.rng("pores")
        count = int(round((2 + 7 * s.density) * ctx.scale ** 1.5 * (1.2 if v == "black_smoker" else 0.8)))
        occupied = np.zeros((ctx.h, ctx.w), dtype=bool)
        pores = np.zeros_like(occupied)
        pts = noise.poisson_points(ctx.w, ctx.h, rng, count, max(2.0, math.sqrt(ctx.w * ctx.h / max(1, count)) * 0.6),
                                   ctx.wrap)
        sc = max(1.0, ctx.scale)
        for x, y in pts:
            if v == "black_smoker":
                w = 1 if sc < 2 else int(rng.integers(1, max(2, int(sc * 0.6)) + 1))
                h = int(rng.integers(1, 3)) * max(1, int(round(sc * 0.8)))
            else:
                w = int(rng.integers(1, 3)) * max(1, int(round(sc * 0.5)))
                h = int(rng.integers(1, 3)) * max(1, int(round(sc * 0.5)))
            cells = [(x + i, y + j) for i in range(w) for j in range(h)]
            if ctx.scale >= 2 and len(cells) > 3:
                # round the corners off bigger pores
                cells = [p for p in cells if not ((p[0] - x in (0, w - 1)) and (p[1] - y in (0, h - 1)))] or cells
            m = _mask(ctx, cells) if ctx.wrap else self._mask_clip(ctx, cells)
            if (pa.dilate(occupied, ctx.wrap, True) & m).any():
                continue
            occupied |= m
            pores |= m
        if not pores.any():
            return
        c.set(pores, "base", self._floor_level(ctx))
        # pit lighting: the far (bottom-right) wall catches the light
        far = (pa.neighbour(pores, 0, -1, ctx.wrap, False) | pa.neighbour(pores, -1, 0, ctx.wrap, False)) & ~pores
        near = (pa.neighbour(pores, 0, 1, ctx.wrap, False) | pa.neighbour(pores, 1, 0, ctx.wrap, False)) & ~pores & ~far
        c.shift(far & c.opaque & (c.ramp == 0), 1)
        c.shift(near & c.opaque & (c.ramp == 0), -1, floor=1)
        c.tag("protect")[pores | far] = True
        c.tag("pore")[pores] = True

    def _mask_clip(self, ctx, cells):
        m = np.zeros((ctx.h, ctx.w), dtype=bool)
        for x, y in cells:
            if 0 <= x < ctx.w and 0 <= y < ctx.h:
                m[y, x] = True
        return m

    def _accent_black_smoker(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        rng = ctx.rng("sulfide")
        n = c.length_of("accent")
        count = int(round((2 + 7 * s.mineral + 1.5 * s.density) * ctx.scale ** 1.5))
        free = (c.ramp == 0) & ~c.tag("pore") & ~c.tag("crack")
        # flecks sit on the raised (lighter) parts of the wall
        lv = np.where(free, c.level, -1)
        prefer = free & (lv >= np.quantile(lv[free], 0.45)) if free.any() else free
        ys, xs = np.nonzero(prefer if prefer.any() else free)
        taken = np.zeros_like(free)
        for _ in range(count):
            if len(xs) == 0:
                break
            i = int(rng.integers(len(xs)))
            x, y = int(xs[i]), int(ys[i])
            size = 2 if ctx.scale <= 1 else int(rng.integers(2, max(3, ctx.px(1.5)) + 1))
            if size == 2:
                # flecks follow the vertical flow of the chimney wall
                dx, dy = ((0, 1), (0, 1), (1, 0), (1, 1))[int(rng.integers(4))]
                if ctx.wrap:
                    cells = [(x, y), ((x + dx) % ctx.w, (y + dy) % ctx.h)]
                else:
                    cells = [(x, y), (min(ctx.w - 1, x + dx), min(ctx.h - 1, y + dy))]
            else:
                cells = pa.grow_cluster(rng, (x, y), size, ctx.w, ctx.h, ctx.wrap,
                                        ~free | pa.dilate(taken, ctx.wrap, True), 0.6)
            m = _mask(ctx, cells)
            if (m & (pa.dilate(taken, ctx.wrap, True) | ~free)).any():
                continue
            taken |= m
            c.set(m, "accent", n - 4)
            c.set(self._pt(ctx, *cells[0]), "accent", n - 2 if rng.random() < 0.7 else n - 1)
            c.tag("protect")[m] = True
            c.tag("mineral")[m] = True
        if s.mineral > 0.35:
            # copper-sulfide tint: a few accent2 flecks
            self.mineral_grains(ctx, (s.mineral - 0.35) * 0.5, "accent2", "sulfide2", prefer=free,
                                size_range=(1, 2), mask=free & ~taken)
        if s.glow > 0.02:
            pores = c.tag("pore")
            if pores.any():
                # hot conduits: some pores glow from inside
                comps = _components(ctx, pores)
                rng2 = ctx.rng("hot_pores")
                hot = np.zeros_like(pores)
                for cells in comps:
                    if rng2.random() < 0.2 + 0.7 * s.glow:
                        hot |= _mask(ctx, cells)
                self._glow_in(ctx, hot, s.glow, "pore_heat")

    # ------------------------------------------------------- white smoker
    def _white_crusts(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        cov = 0.18 + 0.32 * s.density
        freq = 0.7 + 0.8 * (1 - s.cluster_size)
        crust = self.smooth_mask(ctx, "crust", cov, freq, 2, warp=0.05)
        crust = pa.remove_small_clusters(crust.astype(np.int32), max(4, ctx.px(4) * ctx.px(1)),
                                         ctx.wrap).astype(bool)
        if not crust.any():
            return
        n2 = c.length_of("secondary")
        c.set(crust, "secondary", n2 - 2)
        top = crust & ~pa.neighbour(crust, 0, -1, ctx.wrap, False)
        left = crust & ~pa.neighbour(crust, -1, 0, ctx.wrap, False)
        bot = crust & ~pa.neighbour(crust, 0, 1, ctx.wrap, False)
        c.set(top | (left & ~bot), "secondary", n2 - 1)
        c.set(bot & ~top, "secondary", max(1, n2 - 3))
        # the crust is raised: it throws a one-pixel shadow onto the rock below
        shadow = (pa.neighbour(crust, 0, -1, ctx.wrap, False) | pa.neighbour(crust, -1, -1, ctx.wrap, False)) & ~crust
        c.shift(shadow & (c.ramp == 0), -1, floor=1)
        c.tag("crust")[crust] = True
        c.tag("protect")[top | bot] = True

    def _accent_white_smoker(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        # faint sulfur bloom: small pale-yellow patches on the crusts and rims
        n = c.length_of("accent")
        prefer = (c.tag("crust") | c.tag("lit")) & ~c.tag("pore")
        self.mineral_grains(ctx, 0.05 + 0.45 * s.mineral, "accent", "sulfur_bloom", prefer=prefer,
                            size_range=(2, 3), mask=(c.ramp <= 1) & ~c.tag("pore"))
        bloom = c.tag("mineral") & (c.ramp == 2)
        c.level[bloom] = np.clip(c.level[bloom] + 2, n - 3, n - 1)
        if s.glow > 0.02:
            pores = c.tag("pore")
            if pores.any():
                comps = _components(ctx, pores)
                rng = ctx.rng("warm_pores")
                hot = np.zeros_like(pores)
                for cells in comps:
                    if rng.random() < 0.15 + 0.6 * s.glow:
                        hot |= _mask(ctx, cells)
                self._glow_in(ctx, hot, s.glow * 0.8, "white_heat")

    # -------------------------------------------------------- sulfur rock
    def _accent_sulfur_rock(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        H = ctx.data["H"]
        cov = 0.14 + 0.32 * s.density
        freq = 0.45 + 0.5 * (1 - s.cluster_size)
        # sulfur collects in the hollows of the rock
        f = self.height_field(ctx, "sulfur_patch", freq, 1, kind="value", warp=0.05) - 0.12 * H
        crack = c.tag("crack")
        cut = np.quantile(f, 1 - cov)
        patch = f >= cut
        # majority smoothing rounds the patches off and cuts thin necks
        for _ in range(2):
            nb = sum(pa.neighbour(patch, dx, dy, ctx.wrap, False).astype(np.int32) for dx, dy in pa.N8)
            patch = np.where(patch, nb >= 3, nb >= 6)
        small = max(4, int(round(4 * max(1.0, ctx.scale) ** 1.5)))
        patch = pa.remove_small_clusters(patch.astype(np.int32), small, ctx.wrap).astype(bool)
        # crusty edge: nibble a few single edge pixels away
        edge = patch & pa.outline(~patch, ctx.wrap)
        nib = edge & (self.height_field(ctx, "sulfur_nib", 2.5, 1) > 0.82 - 0.15 * s.roughness)
        patch &= ~nib
        bodies = _components(ctx, patch, 3)
        if bodies:
            self._ore.paint_ore(ctx, bodies, self._union(ctx, bodies), glow=False)
        if s.glow > 0.02 and not crack.any():
            # no cracks to glow through: warm a few crust glints instead
            g = ctx.data.get("ore_glints")
            if g is not None:
                self._glow_in(ctx, g, s.glow, "sulfur_glint")

    # ------------------------------------------------------ mineral crust
    def _plan_crust_bands(self, ctx: GenContext) -> None:
        s = ctx.settings
        rng = ctx.rng("crust_bands")
        sc = max(1.0, ctx.scale)
        nb = int(round((4 + 4 * s.layering + 2 * (1 - s.cluster_size)) * sc ** 0.55))
        nb = max(3, min(nb, ctx.h // 2))
        widths = rng.uniform(0.55, 1.6, nb)
        bounds = np.concatenate([[0.0], np.cumsum(widths) / widths.sum()])[:-1]
        # material sequence: 0 rock, 1 rust, 2 ochre, 3 cyan; no two neighbours equal
        colour_share = 0.35 + 0.5 * s.density
        cyan_share = 0.15 + 0.5 * s.mineral
        mats = []
        for i in range(nb):
            for _ in range(10):
                r = rng.random()
                if r > colour_share:
                    m = 0
                else:
                    m = 3 if rng.random() < cyan_share else (1 if rng.random() < 0.5 else 2)
                prev = mats[-1] if mats else -1
                first = mats[0] if (mats and i == nb - 1) else -1
                if m != prev and m != first:
                    break
            mats.append(m)
        # low-frequency, mostly vertical displacement: bands bend but stay bands
        fw = max(1.0, 1.5 * max(1.0, ctx.scale) ** 0.3)
        warp = noise.fbm(ctx.w, ctx.h, ctx.rng("crust_warp"), fw, 2, 0.45, "perlin", (1.0, 1.0))
        warp2 = noise.fbm(ctx.w, ctx.h, ctx.rng("crust_warp2"), fw * 2.5, 1, 0.5, "value", (1.0, 0.6))
        _, y = noise.grid(ctx.w, ctx.h)
        amp = 0.05 + 0.1 * s.roughness + 0.04 * s.organic
        coord = np.mod(y + amp * (warp - 0.5) * 2 + (0.012 + 0.02 * s.roughness) * (warp2 - 0.5) * 2, 1.0)
        band = (np.searchsorted(bounds, coord, side="right") - 1).astype(np.int32)
        band = pa.remove_small_clusters(band, max(3, ctx.px(3)), ctx.wrap)
        ctx.data["crust_band"] = band
        ctx.data["crust_mats"] = np.array(mats, dtype=np.int32)
        # shade within each band: lighter toward the top of the band (rounded layers)
        lo = bounds[band]
        hi = np.where(band + 1 < nb, bounds[np.minimum(band + 1, nb - 1)], 1.0)
        pos = np.clip((coord - lo) / np.maximum(1e-6, hi - lo), 0, 1)
        ctx.data["crust_shade"] = 1.0 - pos

    def _paint_crust_bands(self, ctx: GenContext, flat: bool = False) -> None:
        """Colour the crust layers (flat in the base layer, shaded by material)."""
        c = ctx.canvas
        band = ctx.data["crust_band"]
        mats = ctx.data["crust_mats"]
        mat = mats[np.clip(band, 0, len(mats) - 1)]
        shade = ctx.data["crust_shade"]
        roles = {1: "secondary", 2: "accent", 3: "accent2"}
        for mid, role in roles.items():
            m = mat == mid
            if not m.any():
                continue
            n = c.length_of(role)
            # two body tones per band (upper part a step lighter); the band's
            # top / bottom rows are lit and shaded by the highlight / shadow layers
            body = max(1, (n - 1) // 2)
            lvl = body + (0 if flat else (shade > 0.6).astype(np.int32))
            c.set(m, role, np.clip(lvl, 1, max(1, n - 2)))
        ctx.data["crust_mat"] = mat

    def _crust_bumps(self, ctx: GenContext) -> None:
        """Botryoidal bumps: small rounded knobs sitting on band tops."""
        s, c = ctx.settings, ctx.canvas
        band = ctx.data["crust_band"]
        rng = ctx.rng("bumps")
        count = int(round((2 + 5 * s.roughness) * ctx.scale ** 1.5))
        top = pa.neighbour(band, 0, -1, ctx.wrap, -1) != band
        ys, xs = np.nonzero(top)
        if len(xs) == 0:
            return
        for _ in range(count):
            i = int(rng.integers(len(xs)))
            x, y = int(xs[i]), int(ys[i])
            w = max(2, ctx.px(2))
            cells = [(x + k, y) for k in range(w)]
            m = _mask(ctx, cells) if ctx.wrap else self._mask_clip(ctx, cells)
            m &= band == band[y, x]
            c.shift(m, 1)
            c.tag("protect")[m] = True
            below = pa.neighbour(m, 0, -1, ctx.wrap, False) & ~m & (band == band[y, x])
            c.shift(below, -1, floor=1)

    def _accent_mineral_crust(self, ctx: GenContext) -> None:
        s = ctx.settings
        mat = ctx.data.get("crust_mat")
        band = ctx.data["crust_band"]
        # cyan crystals grow along band boundaries in rock / rust bands
        edge = (pa.neighbour(band, 0, 1, ctx.wrap, -1) != band)
        prefer = edge & (mat != 3) if mat is not None else edge
        amount = 0.15 + 0.85 * s.mineral
        self.mineral_grains(ctx, amount * 0.7, "accent2", "crust_cyan", prefer=prefer, size_range=(2, 3))
        if s.glow > 0.02:
            # thin hot seams between some bands
            seam = edge & (self.height_field(ctx, "crust_hot", 1.0, 1) > 0.75 - 0.35 * s.glow)
            seam = pa.remove_small_clusters(seam.astype(np.int32), 3, ctx.wrap).astype(bool) & seam
            self._glow_in(ctx, seam, s.glow, "crust_heat")

    # --------------------------------------------------------- crystals
    def _plan_crystals(self, ctx: GenContext) -> None:
        """Clusters of 1-3 prisms radiating from a base, angles snapped to 45 deg."""
        s = ctx.settings
        rng = ctx.rng("crystal_plan")
        sc = max(0.5, ctx.scale)
        n_clusters = max(1, int(round((1.6 + 2.6 * s.density) * sc ** 1.1)))
        length = (3.6 + 4.0 * s.cluster_size) * sc ** 0.9
        width = int(max(1, round((1.5 + 1.5 * s.cluster_size) * sc ** 0.8)))
        pts = noise.poisson_points(ctx.w, ctx.h, rng, n_clusters * 5,
                                   math.sqrt(ctx.w * ctx.h / n_clusters) * 0.75, ctx.wrap)
        grains = np.full((ctx.h, ctx.w), -1, dtype=np.int32)
        occupied = np.zeros((ctx.h, ctx.w), dtype=bool)
        bodies = []
        axes = []
        gid = 0
        dirs = [-90, -90, -90, -90, -45, -135, -45, -135, -60, -120, 0, 180]
        for bx, by in pts:
            if len(bodies) >= n_clusters:
                break
            deg = dirs[int(rng.integers(len(dirs)))]
            if deg in (-60, -120):
                deg = -90 + (deg + 90) * 0.75   # steep but not vertical: 2:1 pixel slope
            main = math.radians(deg)
            # single prisms at 16x16 (a cluster would not read); fans of 2-3 above
            k = 1 if sc < 2 else int(rng.integers(1, 4))
            # side prisms grow from beside the main prism's foot and lean outwards
            ux, uy = math.cos(main), math.sin(main)
            nx, ny = -uy, ux
            side = 1 if rng.random() < 0.5 else -1
            off = width + 1.0
            spec = [(main, 1.0, width, 0.0)]
            if k >= 2:
                spec.append((main + side * math.pi / 4, 0.6, max(1, width - 1), side * off))
            if k >= 3:
                spec.append((main - side * math.pi / 4, 0.5, max(1, width - 1), -side * off))
            cl = []
            cl_mask = np.zeros_like(occupied)
            for ang, lf, w, o in spec:
                L = length * lf * rng.uniform(0.85, 1.15)
                back = 0.0 if o == 0 else width * 0.6
                sx = int(round(bx + nx * o - ux * back))
                sy = int(round(by + ny * o - uy * back))
                pm, core = self._prism(ctx, sx, sy, ang, L, w)
                if pm.sum() < 2:
                    continue
                cl.append((pm, core))
                cl_mask |= pm
            if not cl_mask.any() or (pa.dilate(occupied, ctx.wrap, True) & cl_mask).any():
                continue
            occupied |= cl_mask
            # paint side crystals first so the main prism sits in front
            for pm, core in reversed(cl):
                grains[pm] = gid
                axes.append((gid, core))
                gid += 1
            ys, xs = np.nonzero(cl_mask)
            bodies.append(sorted(zip(xs.tolist(), ys.tolist())))
        ctx.data["crystal_bodies"] = bodies
        ctx.data["crystal_mask"] = occupied
        ctx.data["crystal_grains"] = grains
        ctx.data["crystal_axes"] = axes

    def _prism(self, ctx: GenContext, bx: int, by: int, ang: float, L: float, w: int):
        """Rasterise one pointed prism growing from pixel (bx, by).

        Even widths put the axis on a pixel corner, odd widths through pixel
        centres, so straight and 45-degree prisms come out with clean,
        even-width pixel runs.  Returns ``(mask, core_axis_mask)``.
        """
        R = int(math.ceil(L + w)) + 2
        ys, xs = np.mgrid[-R:R + 1, -R:R + 1]
        off = 0.5 if w % 2 else 0.0
        px = xs + 0.5 - off
        py = ys + 0.5 - off
        dx, dy = math.cos(ang), math.sin(ang)
        dx, dy = round(dx, 6), round(dy, 6)
        t = px * dx + py * dy
        sp = -px * dy + py * dx            # signed distance from the axis
        perp = np.abs(sp)
        diag = abs(abs(dx) - abs(dy)) < 0.1
        hw = w / 2.0 + (0.02 if not diag else (0.05 if w % 2 == 0 else 0.25))
        tip = max(1.0, w * 0.8)
        hw_t = np.where(t < L - tip, hw, hw * np.clip((L - t) / tip, 0.0, 1.0) + 0.3)
        inside = (t >= -0.6) & (t <= L) & (perp <= hw_t)
        core = inside & (sp >= -0.2) & (sp <= 0.75) & (t >= L * 0.1) & (t <= L * 0.8)
        m = np.zeros((ctx.h, ctx.w), dtype=bool)
        cm = np.zeros_like(m)
        for arr, sel in ((m, inside), (cm, core)):
            yy, xx = np.nonzero(sel)
            gx, gy = xx - R + int(bx), yy - R + int(by)
            if ctx.wrap:
                arr[gy % ctx.h, gx % ctx.w] = True
            else:
                ok = (gx >= 0) & (gx < ctx.w) & (gy >= 0) & (gy < ctx.h)
                arr[gy[ok], gx[ok]] = True
        return m, cm

    def _accent_crystals(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        bodies = ctx.data.get("crystal_bodies") or []
        if not bodies:
            return
        union = ctx.data["crystal_mask"]
        info = self._ore.paint_ore(ctx, bodies, union, grains=ctx.data["crystal_grains"], glow=False,
                                   minerals=False)
        if s.mineral > 0.02:
            free = (c.ramp == 0) & ~pa.dilate(union, ctx.wrap, True)
            near = pa.dilate(pa.dilate(pa.dilate(union, ctx.wrap, True), ctx.wrap, True), ctx.wrap, True)
            self.mineral_grains(ctx, s.mineral * 0.35, "accent2", "crystal_grit", prefer=near & free,
                                size_range=(2, 2), mask=free)
        g = s.glow
        if g <= 0.02:
            return
        ng = c.length_of("glow")
        core = np.zeros_like(union)
        for _, cm in ctx.data["crystal_axes"]:
            core |= cm
        core &= union
        em = np.zeros_like(union)
        # glowing heart along each crystal's axis, brightest toward the middle
        lvl = min(ng - 1, 1 + int(round(g * (ng - 2))))
        c.set(core, "glow", lvl)
        em |= core
        tips = info["glints"] & union
        c.set(tips, "glow", ng - 1)
        em |= tips
        if g > 0.6:
            inner = union & ~core & (info["score"] >= 0) & (c.ramp == 2)
            c.set(inner, "glow", max(0, lvl - 1))
            em |= inner
        c.emissive |= em
        c.tag("protect")[em] = True
        if g > 0.45:
            # the crystal lights the rock around it a little
            ring = pa.outline(union, ctx.wrap) & (c.ramp == 0) & ~c.tag("ore_rim")
            c.shift(ring, 1)

    # ------------------------------------------------------------ misc
    def _union(self, ctx: GenContext, bodies) -> np.ndarray:
        m = np.zeros((ctx.h, ctx.w), dtype=bool)
        for b in bodies:
            for x, y in b:
                m[y, x] = True
        return m

    def _blobs(self, ctx: GenContext, rng, count: int, size16: tuple[float, float],
               near: np.ndarray | None = None, avoid: np.ndarray | None = None) -> list:
        """Small compact blobs, optionally preferring positions in ``near``."""
        block = np.zeros((ctx.h, ctx.w), dtype=bool) if avoid is None else avoid.copy()
        pts = noise.poisson_points(ctx.w, ctx.h, rng, count * 3, max(2.0, math.sqrt(ctx.w * ctx.h / max(1, count)) * 0.5),
                                   ctx.wrap)
        if near is not None and near.any():
            pts = [p for p in pts if near[p[1], p[0]]] + [p for p in pts if not near[p[1], p[0]]]
        bodies = []
        area = max(1.0, ctx.scale) ** 1.4
        for x, y in pts:
            if len(bodies) >= count:
                break
            n = int(round(rng.uniform(*size16) * area))
            offs = blob_offsets(rng, max(2, n), 1.0 + abs(rng.normal(0, 0.4)), rng.random() * 3, 0.5)
            cells = []
            ok = True
            for dx, dy in offs:
                cx, cy = x + dx, y + dy
                if ctx.wrap:
                    cx %= ctx.w
                    cy %= ctx.h
                elif not (0 <= cx < ctx.w and 0 <= cy < ctx.h):
                    ok = False
                    break
                cells.append((cx, cy))
            if not ok:
                continue
            if any(block[cy, cx] for cx, cy in cells):
                continue
            block |= pa.dilate(_mask(ctx, cells), ctx.wrap, True)
            bodies.append(cells)
        return bodies
