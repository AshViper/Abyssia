"""Metal: salvaged and ancient metal blocks.

Variants
--------
plate   riveted plates (rows / grid / staggered) with dark seams, a
        diagonal sheen on each plate, rivets, dents and rust streaks
block   solid metal block: bevelled border, horizontally brushed body and a
        soft diagonal glare; bolts in the corners
grate   frame + grid of bars with fully transparent holes (big grid, slots
        or fine mesh)
raw     raw metal chunk: lumpy nuggets with crevices, glints and ore rust

Sliders: density = rivets / bolts / dents amount (grate: finer bars, raw:
more, smaller nuggets), mineral = rust or patina patches (accent role),
glow = indicator lights and glowing runes (glow role, emissive),
roughness = wear and dents, cracks = scratches and torn seams,
metallic = strength of the specular glints.  Specular glints use a bright
silver ramp (accent2) so the metal reads shiny on any theme.
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.layers import GenContext
from core.settings import TextureSettings

from . import _block_patterns as bp
from .base import BaseGenerator

VARIANT_NAMES = ("plate", "block", "grate", "raw")
PATINA_THEMES = {"ancient", "deep_ocean", "bioluminescent", "crystal", "cold"}

# tiny 3x3 rune glyphs for glowing inscriptions ('#' lit)
RUNES = (
    ("#.#", ".#.", "#.#"),
    (".#.", "###", ".#."),
    ("###", "#.#", "#.#"),
    ("#.#", "#.#", "###"),
    (".#.", "#.#", ".#."),
    ("##.", ".#.", ".##"),
    ("#..", "###", "..#"),
)


class MetalGenerator(BaseGenerator):
    category = "metal"

    # ------------------------------------------------------------ setup
    def body_levels(self, s: TextureSettings, a) -> int:
        k = super().body_levels(s, a)
        if s.levels == 0 and (not s.color_limit or s.color_limit >= 12):
            k = max(4, k)
        return k

    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        corrosion = "patina" if s.palette in PATINA_THEMES else "rust"
        return {"base": "dark_metal", "accent": corrosion, "accent2": "silver"}

    def prepare(self, ctx: GenContext) -> None:
        v = ctx.settings.variant if ctx.settings.variant in VARIANT_NAMES else "plate"
        ctx.data["variant"] = v
        ctx.data["L"] = bp.levels(ctx)
        ctx.data["scratch"] = float(np.clip((ctx.settings.cracks - 0.2) / 0.8, 0, 1))
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

    # ========================================================= shared pieces
    def _brushed(self, ctx: GenContext, mask: np.ndarray, key: str, amount: float) -> np.ndarray:
        """Horizontal brush streaks: runs of +-1 along rows (clustered, never single pixels)."""
        out = np.zeros((ctx.h, ctx.w), dtype=np.int32)
        if amount <= 0:
            return out
        rng = ctx.rng(key)
        ax, ay = self.aspect(ctx)
        horiz = ay >= ax * 0.8          # the style profile can turn the grain vertical
        H, W = (ctx.h, ctx.w) if horiz else (ctx.w, ctx.h)
        grid = np.zeros((H, W), dtype=np.int32)
        min_run = max(3, ctx.px(3))
        max_run = max(min_run + 1, ctx.px(9))
        for y in range(H):
            x = int(rng.integers(W))
            filled = 0
            while filled < W:
                ln = int(rng.integers(min_run, max_run + 1))
                u = rng.random()
                v = 1 if u < amount * 0.5 else (-1 if u < amount else 0)
                for i in range(ln):
                    grid[y, (x + i) % W] = v
                x += ln
                filled += ln
        if not horiz:
            grid = grid.T
        out[mask] = grid[mask]
        return out

    def _rivet(self, ctx: GenContext, x: int, y: int, big: bool) -> None:
        """A rivet head: bright top-left, dark bottom-right, drop shadow."""
        c, L = ctx.canvas, ctx.data["L"]
        na2 = c.length_of("accent2")
        m = bp.zeros(ctx)
        if big:
            r = max(1, int(round(ctx.scale / 2)))
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    if dx * dx + dy * dy <= r * r + 0.5:
                        m[(y + dy) % ctx.h, (x + dx) % ctx.w] = True
            lit, dark, drop = bp.raised_light(m, ctx.wrap)
            c.set(m, "base", L.up)
            c.set(m & lit, "base", L.lit)
            c.set(m & dark, "base", L.lo)
            e = bp.mask_edges(m, ctx.wrap)
            tl = m & e["top"] & e["left"]
            if not tl.any():
                tl = bp.point_mask(ctx, [(x - r // 2, y - r // 2)])
            c.set(tl | bp.point_mask(ctx, [(x - (r + 1) // 3, y - (r + 1) // 3)]), "accent2", na2 - 1)
            c.shift(drop & ~c.tag("rivet") & (c.ramp == 0), -1, L.dark)
            bp.protect(ctx, m | drop)
        else:
            m = bp.point_mask(ctx, [(x, y)])
            c.set(m, "accent2", max(0, na2 - 2))
            sh = bp.point_mask(ctx, [(x, y + 1)]) & ~c.tag("rivet")
            c.set(sh & (c.ramp == 0), "base", L.dark)
            bp.protect(ctx, m | sh)
            m = m | sh
        c.tag("rivet")[m] = True

    def _dents(self, ctx: GenContext, mask: np.ndarray, amount: float, key: str = "dents") -> None:
        if amount <= 0.02:
            return
        L, c = ctx.data["L"], ctx.canvas
        count = int(round(amount * 4 * ctx.scale ** 2))
        size = (2, max(3, ctx.px(4)))
        for cells in bp.clusters(ctx, key, count, size, mask=mask, min_dist=3 * ctx.scale ** 0.5):
            m = bp.point_mask(ctx, cells) & mask & ~c.tag("rivet")
            if m.sum() < 2:
                continue
            shade, lit_wall, lip = bp.sunken_light(m, ctx.wrap)
            c.shift(m, -1, L.dark)
            c.shift(lit_wall & ~shade, 1, L.dark, L.up)
            c.shift(lip & mask & ~c.tag("rivet"), 1, L.dark, L.lit)
            c.tag("dent")[m] = True

    def _scratches(self, ctx: GenContext, mask: np.ndarray, amount: float, key: str = "scratches") -> None:
        """Short straight scratches: a bright line with a dark pixel under its start."""
        if amount <= 0.01:
            return
        L, c = ctx.data["L"], ctx.canvas
        rng = ctx.rng(key)
        n = int(round((1 + 5 * amount) * ctx.scale))
        m = bp.zeros(ctx)
        ys, xs = np.nonzero(mask)
        if not len(xs):
            return
        for _ in range(n):
            i = int(rng.integers(len(xs)))
            ang = rng.choice([-0.5, -0.35, 0.35, 0.5, 0.0]) + rng.normal(0, 0.1)
            ln = int(round(ctx.w * rng.uniform(0.15, 0.35)))
            pts = [(xs[i] + k * math.cos(ang), ys[i] + k * math.sin(ang)) for k in range(ln)]
            m |= bp.line_mask(ctx, pts)
        m &= mask & ~c.tag("rivet")
        c.shift(m, -1 if amount > 0.6 else 1, L.dark, L.lit)
        c.tag("scratch")[m] = True
        bp.protect(ctx, m)

    def _corrode(self, ctx: GenContext, where: np.ndarray, amount: float, key: str,
                 prefer: np.ndarray | None = None) -> np.ndarray:
        """Rust / patina patches in the accent ramp that keep the metal's shading."""
        c, L = ctx.canvas, ctx.data["L"]
        if amount <= 0.02 or not where.any():
            return bp.zeros(ctx)
        f = self.height_field(ctx, key, 1.3, 2, kind="value")
        if prefer is not None and prefer.any():
            near = prefer.copy()
            for _ in range(max(1, ctx.px(2))):
                near = pa.dilate(near, ctx.wrap)
            f = f * 0.6 + near * 0.4
        vals = f[where]
        cut = np.quantile(vals, 1 - min(0.95, amount * 0.55))
        m = (f >= cut) & where
        m = pa.remove_small_clusters(m.astype(np.int32), 3, ctx.wrap).astype(bool) & where
        na = c.length_of("accent")
        lv = bp.base_to_role(c.level, L.K, na, 0, na - 2)
        # pitted corrosion: the patch core one step darker
        core = pa.erode(m, ctx.wrap) & (self.height_field(ctx, key + "_pit", 2.2, 1) > 0.55)
        lv = np.where(core, np.maximum(0, lv - 1), lv)
        c.set(m, "accent", lv)
        c.tag("rust")[m] = True
        return m

    def _runes(self, ctx: GenContext, spots: list[tuple[int, int]], key: str) -> None:
        """Glowing 3x3 runes (scaled) centred on ``spots``."""
        c = ctx.canvas
        rng = ctx.rng(key)
        n = c.length_of("glow")
        k = max(1, int(round(ctx.scale / 1.5)))
        for (x, y) in spots:
            g = RUNES[int(rng.integers(len(RUNES)))]
            m = bp.zeros(ctx)
            for gy, row in enumerate(g):
                for gx, ch in enumerate(row):
                    if ch == "#":
                        for dy in range(k):
                            for dx in range(k):
                                m[(y + (gy - 1) * k + dy) % ctx.h, (x + (gx - 1) * k + dx) % ctx.w] = True
            s = ctx.settings
            c.set(m, "glow", 1 + int(s.glow > 0.55))
            e = bp.mask_edges(m, ctx.wrap)
            c.set(m & e["top"] & e["left"], "glow", min(n - 1, 2 + int(s.glow > 0.8)))
            c.emissive[m] = True
            bp.protect(ctx, m)
            c.tag("rune")[m] = True
            # engraved: a dark rim below/right of the rune
            drop = (pa.neighbour(m, 0, -1, ctx.wrap, fill=False) | pa.neighbour(m, -1, 0, ctx.wrap, fill=False)) & ~m
            c.shift(drop & (c.ramp == 0), -1, ctx.data["L"].dark)

    def _lights(self, ctx: GenContext, spots: list[tuple[int, int]]) -> None:
        """Small indicator lights: a glowing pixel pair in a dark socket."""
        c, L = ctx.canvas, ctx.data["L"]
        n = c.length_of("glow")
        s = ctx.settings
        k = max(1, int(round(ctx.scale / 2)))
        for (x, y) in spots:
            m = bp.zeros(ctx)
            for dy in range(k):
                for dx in range(2 * k):
                    m[(y + dy) % ctx.h, (x + dx) % ctx.w] = True
            sock = pa.dilate(m, ctx.wrap) & ~m & (c.ramp == 0)
            c.set(sock, "base", L.dark)
            c.set(m, "glow", min(n - 1, 2 + int(s.glow > 0.6)))
            c.emissive[m] = True
            bp.protect(ctx, m | sock)
            c.tag("rune")[m | sock] = True

    def _glint(self, ctx: GenContext, where: np.ndarray, frac: float, key: str) -> None:
        """Specular glints: brightest silver on a few lit-edge pixels."""
        c = ctx.canvas
        ys, xs = np.nonzero(where)
        if not len(xs) or frac <= 0:
            return
        rng = ctx.rng(key)
        k = min(len(xs), max(1, int(round(len(xs) * frac))))
        idx = rng.choice(len(xs), size=k, replace=False)
        m = bp.zeros(ctx)
        m[ys[idx], xs[idx]] = True
        c.set(m, "accent2", c.length_of("accent2") - 1)
        bp.protect(ctx, m)

    # ================================================================= plate
    def _prep_plate(self, ctx: GenContext) -> None:
        s = ctx.settings
        rng = ctx.rng("layout")
        W, H = ctx.w, ctx.h
        style = str(rng.choice(["rows", "grid", "stagger"], p=[0.35, 0.35, 0.3]))
        if W <= 8:
            style = "rows"
        ph = H // 2 if H >= 16 else H
        pw = W // 2 if style in ("grid", "stagger") else W
        seam = max(1, int(round(ctx.scale / 2)))
        ys, xs = np.mgrid[0:H, 0:W]
        row = ys // ph
        shift = np.where((row % 2 == 1) & (style == "stagger"), pw // 2, 0)
        col = ((xs + shift) % W) // pw
        lx = (xs + shift) % pw
        ly = ys % ph
        gap = (ly >= ph - seam) | ((lx >= pw - seam) & (pw < W))
        n_cols = max(1, W // pw)
        labels = np.where(gap, -1, row * n_cols + col).astype(np.int32)
        plates = []
        for r in range(H // ph):
            for cc in range(n_cols):
                x0 = (cc * pw - (pw // 2 if (style == "stagger" and r % 2 == 1) else 0)) % W
                plates.append({"label": r * n_cols + cc, "x0": x0, "y0": r * ph,
                               "w": pw - (seam if pw < W else 0), "h": ph - seam})
        ctx.data.update(labels=labels, gap=gap, plates=plates, seam=seam, style=style)

    def _plate_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        c.set(ctx.data["gap"], "base", L.deep)
        rng = ctx.rng("plate_tone")
        labels = ctx.data["labels"]
        n = len(ctx.data["plates"])
        vals = rng.choice([-1, 0, 0, 1], size=n + 1).astype(np.int32)
        delta = np.where(labels >= 0, vals[np.clip(labels, 0, n)], 0)
        c.shift((labels >= 0) & (delta != 0), delta, L.lo, L.up)

    def _plate_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ctx.data["labels"] >= 0
        off = self._brushed(ctx, body, "brush", 0.25 + 0.35 * s.roughness)
        c.shift(body & (off != 0), off, L.lo, L.up)

    def _plate_large_detail(self, ctx: GenContext) -> None:
        """A diagonal sheen band across each plate + damp tarnish."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        body = labels >= 0
        rx, ry, rad = bp.region_coords(labels, ctx.wrap)
        # plates are rectangles: normalise by their half extents
        t = (rx + ry) / np.maximum(1.0, rad * 1.1)
        band = body & (t > -0.75) & (t < -0.2)
        c.shift(band, 1, L.lo, L.up)
        dull = body & (t > 0.6)
        c.shift(dull, -1, L.lo, L.up)
        if s.moisture > 0.3:
            damp = self.smooth_mask(ctx, "tarnish", 0.35 * (s.moisture - 0.3), 0.7) & body
            c.shift(damp, -1, L.lo, L.up)

    def _plate_medium_detail(self, ctx: GenContext) -> None:
        """Rivets along the plate edges (count from density)."""
        s = ctx.settings
        big = ctx.w >= 32
        inset = max(1, int(round(ctx.scale))) + (1 if big else 0)
        labels = ctx.data["labels"]
        for p in ctx.data["plates"]:
            if p["w"] < 4 or p["h"] < 4:
                continue
            xs = [p["x0"] + inset, p["x0"] + p["w"] - 1 - inset]
            ys = [p["y0"] + inset, p["y0"] + p["h"] - 1 - inset - (0 if big else 1)]
            spacing = max(3, int(round((9 - 5 * s.density) * ctx.scale)))
            pts = set()
            small = p["w"] <= 10 * ctx.scale or p["h"] <= 8 * ctx.scale
            if s.density >= 0.12:
                for y in (ys if (not small or s.density > 0.58) else ys[:1]):
                    for x in xs:
                        pts.add((x, y))
            if s.density > 0.6:
                for y in ys:
                    for x in range(xs[0] + spacing, xs[1] - spacing // 2 + 1, spacing):
                        pts.add((x, y))
                if s.density > 0.85:
                    for x in xs:
                        for y in range(ys[0] + spacing, ys[1] - spacing // 2 + 1, spacing):
                            pts.add((x, y))
            for (x, y) in sorted(pts):
                if labels[y % ctx.h, x % ctx.w] == p["label"]:
                    self._rivet(ctx, x, y, big)

    def _plate_small_detail(self, ctx: GenContext) -> None:
        s = ctx.settings
        body = (ctx.data["labels"] >= 0) & ~ctx.canvas.tag("rivet")
        self._dents(ctx, body & (bp.region_depth(ctx.data["labels"], ctx.wrap, 2) >= 1),
                    s.roughness * (0.3 + 0.7 * s.density) * 0.7)

    def _plate_cracks(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        amt = ctx.data["scratch"]
        body = ctx.data["labels"] >= 0
        self._scratches(ctx, body, amt)
        if amt > 0.55:
            # a torn seam: the plate edge buckles into the gap
            gap = ctx.data["gap"]
            tear = pa.dilate(gap, ctx.wrap) & body & self.smooth_mask(ctx, "tear", (amt - 0.55) * 0.5, 1.2)
            c.set(tear, "base", L.deep)
            c.tag("crack")[tear] = True

    def _plate_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        e = bp.edges(labels, ctx.wrap)
        lit, _, corner, _ = bp.bevel_masks(e, max(1, int(round(ctx.scale / 3))), labels, ctx.wrap)
        excl = c.tag("rivet") | c.tag("crack")
        c.set(lit & ~excl, "base", np.maximum(c.level, L.lit - 1))
        c.shift(lit & ~excl, 1, L.lo, L.lit)
        self._glint(ctx, (corner | (lit & e["top"])) & ~excl, 0.08 + 0.25 * s.metallic + 0.1 * (corner.sum() > 0),
                    "plate_glint")
        c.set(corner & ~excl, "accent2", c.length_of("accent2") - 1)
        bp.protect(ctx, corner & ~excl)

    def _plate_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        e = bp.edges(labels, ctx.wrap)
        _, dark, _, dcorner = bp.bevel_masks(e, max(1, int(round(ctx.scale / 3))), labels, ctx.wrap)
        excl = c.tag("rivet") | c.tag("crack")
        c.set(dark & ~excl, "base", np.minimum(c.level, L.lo))
        c.shift(dark & ~excl, -1, L.dark)

    def _plate_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        body = (ctx.data["labels"] >= 0) & ~c.tag("rivet")
        if s.mineral > 0.02:
            self._corrode(ctx, body | ctx.data["gap"], s.mineral, "rust",
                          prefer=ctx.data["gap"] | c.tag("rivet"))
            self._rust_streaks(ctx)
        self._plate_glow(ctx)

    def _rust_streaks(self, ctx: GenContext) -> None:
        """Rust running down from some rivets."""
        s, c = ctx.settings, ctx.canvas
        riv = c.tag("rivet")
        if not riv.any() or s.mineral < 0.1:
            return
        rng = ctx.rng("streaks")
        na = c.length_of("accent")
        ys, xs = np.nonzero(riv & ~pa.neighbour(riv, 0, 1, ctx.wrap, fill=False))
        m = bp.zeros(ctx)
        for x, y in zip(xs, ys):
            if rng.random() > s.mineral * 0.9:
                continue
            ln = int(rng.integers(2, 3 + int(4 * s.mineral))) * max(1, int(ctx.scale ** 0.5))
            for k in range(1, ln + 1):
                m[(y + k) % ctx.h, x] = True
        m &= (c.ramp == 0) & ~riv & ~ctx.data.get("gap", bp.zeros(ctx))
        c.set(m, "accent", np.clip(bp.base_to_role(c.level, ctx.data["L"].K, na, 0, na - 1), 0, na - 2))
        bp.protect(ctx, m)

    def _plate_glow(self, ctx: GenContext) -> None:
        s = ctx.settings
        if s.glow <= 0.02:
            return
        rng = ctx.rng("plate_glow")
        plates = ctx.data["plates"]
        order = rng.permutation(len(plates))
        n = max(1, int(round(len(plates) * min(1.0, s.glow * 1.2))))
        runes, lights = [], []
        for i in order[:n]:
            p = plates[int(i)]
            cx, cy = p["x0"] + p["w"] // 2, p["y0"] + p["h"] // 2
            if s.glow >= 0.35 and p["w"] >= 6 and p["h"] >= 6:
                runes.append((cx, cy))
            else:
                lights.append((cx - max(1, int(round(ctx.scale / 2))), cy))
        self._runes(ctx, runes, "plate_runes")
        self._lights(ctx, lights)

    # ================================================================= block
    def _prep_block(self, ctx: GenContext) -> None:
        rng = ctx.rng("layout")
        H, W = ctx.h, ctx.w
        ys, xs = np.mgrid[0:H, 0:W]
        top, left, bottom, right = ys, xs, H - 1 - ys, W - 1 - xs
        d = np.minimum(np.minimum(top, left), np.minimum(bottom, right))
        tl, br = np.minimum(top, left), np.minimum(bottom, right)
        bw = max(1, int(round(ctx.scale * 0.75)))
        style = str(rng.choice(["plain", "inset"], p=[0.5, 0.5])) if W >= 16 else "plain"
        ctx.data.update(fd=d, f_tl=tl < br, f_br=br < tl, bw=bw, style=style,
                        inset=d == bw + max(1, int(round(ctx.scale * 1.5))))

    def _block_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        if ctx.data["style"] == "inset":
            c.set(ctx.data["inset"], "base", L.lo)

    def _block_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ctx.data["fd"] >= ctx.data["bw"]
        body &= ~ctx.data["inset"] if ctx.data["style"] == "inset" else body
        off = self._brushed(ctx, body, "brush", 0.35 + 0.35 * s.roughness)
        c.shift(body & (off != 0), off, L.lo, L.up)

    def _block_large_detail(self, ctx: GenContext) -> None:
        """Soft diagonal glare from the top-left, darker toward the bottom-right."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        ys, xs = np.mgrid[0:ctx.h, 0:ctx.w]
        t = (xs + ys) / float(ctx.w + ctx.h - 2)
        wob = (self.height_field(ctx, "glare", 1.0, 1) - 0.5) * 0.12
        t = t + wob
        body = (ctx.data["fd"] >= ctx.data["bw"]) & ~(ctx.data["inset"] & (ctx.data["style"] == "inset"))
        c.shift(body & (t > 0.18) & (t < 0.36), 1, L.lo, L.up)
        c.shift(body & (t > 0.72), -1, L.lo, L.up)
        if s.moisture > 0.3:
            damp = self.smooth_mask(ctx, "tarnish", 0.35 * (s.moisture - 0.3), 0.7) & body
            c.shift(damp, -1, L.lo, L.up)

    def _block_medium_detail(self, ctx: GenContext) -> None:
        """Bolts in the corners (density) and along the edges at high density."""
        s = ctx.settings
        if s.density < 0.25:
            return
        big = ctx.w >= 32
        o = ctx.data["bw"] + max(1, int(round(ctx.scale))) - (0 if big else 1)
        W, H = ctx.w, ctx.h
        pts = [(o, o), (W - 1 - o, o), (o, H - 1 - o - (0 if big else 1)), (W - 1 - o, H - 1 - o - (0 if big else 1))]
        if s.density > 0.75:
            pts += [(W // 2 - (0 if big else 1), o), (W // 2 - (0 if big else 1), H - 1 - o - (0 if big else 1))]
        for (x, y) in pts:
            self._rivet(ctx, x, y, big)

    def _block_small_detail(self, ctx: GenContext) -> None:
        s = ctx.settings
        body = (ctx.data["fd"] > ctx.data["bw"]) & ~ctx.canvas.tag("rivet")
        self._dents(ctx, body, s.roughness * (0.2 + 0.6 * s.density) * 0.6)

    def _block_cracks(self, ctx: GenContext) -> None:
        body = ctx.data["fd"] >= ctx.data["bw"]
        self._scratches(ctx, body, ctx.data["scratch"])

    def _block_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        d, bw = ctx.data["fd"], ctx.data["bw"]
        excl = c.tag("rivet")
        outer = (d < bw) & ctx.data["f_tl"] & ~excl
        c.set(outer, "base", L.lit)
        if ctx.data["style"] == "inset":
            g = ctx.data["inset"]
            lip = (pa.neighbour(g, 0, -1, ctx.wrap, fill=False) | pa.neighbour(g, -1, 0, ctx.wrap, fill=False)) & ~g
            c.set(lip & ~excl & (d >= bw), "base", L.lit)
            bp.protect(ctx, lip & ~excl & (d >= bw))
        corner = bp.zeros(ctx)
        corner[:bw, :bw] = True
        c.set(corner, "accent2", c.length_of("accent2") - 1)
        bp.protect(ctx, corner)
        self._glint(ctx, outer & (d == 0), 0.1 + 0.3 * s.metallic, "block_glint")

    def _block_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        d, bw = ctx.data["fd"], ctx.data["bw"]
        excl = c.tag("rivet")
        c.set((d < bw) & ctx.data["f_br"] & ~excl, "base", L.dark)
        if ctx.data["style"] == "inset":
            g = ctx.data["inset"]
            c.set(g & ~excl, "base", L.dark)

    def _block_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        d = ctx.data["fd"]
        if s.mineral > 0.02:
            edge = d < max(2, ctx.px(3))
            self._corrode(ctx, ~c.tag("rivet"), s.mineral, "rust", prefer=edge | c.tag("rivet"))
            self._rust_streaks(ctx)
        if s.glow > 0.02:
            cx, cy = ctx.w // 2, ctx.h // 2
            if s.glow >= 0.35:
                self._runes(ctx, [(cx - (1 if ctx.w < 32 else 0), cy - (1 if ctx.w < 32 else 0))], "block_rune")
            else:
                self._lights(ctx, [(cx - max(1, int(round(ctx.scale))), cy)])

    # ================================================================= grate
    def _prep_grate(self, ctx: GenContext) -> None:
        s = ctx.settings
        rng = ctx.rng("layout")
        W, H = ctx.w, ctx.h
        frame = max(1, int(round(ctx.scale)))
        inner = W - 2 * frame
        pick = s.density * 2 + rng.uniform(-0.45, 0.45)
        style = "grid" if pick < 0.75 else ("slots" if pick < 1.4 else "mesh")
        bar = max(1, int(round(ctx.scale * (1.0 if style != "slots" else 1.5))))
        if style == "grid":
            hole = max(2, int(round(4 * ctx.scale)))
            bar = max(1, int(round(2 * ctx.scale))) if W >= 16 else 1
        elif style == "slots":
            hole = max(2, int(round(2 * ctx.scale)))
        else:
            hole = max(1, int(round(2 * ctx.scale)))
        n = max(1, int(round((inner + bar) / (hole + bar))))
        xsegs = bp.split_even(inner, n, bar)
        solid = np.ones((H, W), dtype=bool)
        if style == "slots":
            rows = 1 if rng.random() < 0.5 or H < 16 else 2
            ysegs = bp.split_even(inner, rows, bar)
        else:
            ysegs = xsegs
        for (sy, ly) in ysegs:
            for (sx, lx) in xsegs:
                solid[frame + sy:frame + sy + ly, frame + sx:frame + sx + lx] = False
        ys, xs = np.mgrid[0:H, 0:W]
        d = np.minimum(np.minimum(ys, xs), np.minimum(H - 1 - ys, W - 1 - xs))
        tl = np.minimum(ys, xs) < np.minimum(H - 1 - ys, W - 1 - xs)
        br = np.minimum(H - 1 - ys, W - 1 - xs) < np.minimum(ys, xs)
        # bars: which pixels belong to horizontal / vertical bars (crossings = both)
        hole_cols = np.zeros(W, dtype=bool)
        for (sx, lx) in xsegs:
            hole_cols[frame + sx:frame + sx + lx] = True
        hole_rows = np.zeros(H, dtype=bool)
        for (sy, ly) in ysegs:
            hole_rows[frame + sy:frame + sy + ly] = True
        hbar = solid & (d >= frame) & ~hole_rows[:, None]
        vbar = solid & (d >= frame) & ~hole_cols[None, :]
        ctx.data.update(solid=solid, frame_d=d, f_tl=tl, f_br=br, frame=frame, style=style,
                        hbar=hbar & ~vbar, vbar=vbar & ~hbar, cross=hbar & vbar, bar=bar)

    def _grate_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        c.set(ctx.data["hbar"], "base", L.up)
        c.clear(~ctx.data["solid"])

    def _grate_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        solid = ctx.data["solid"]
        frame = solid & (ctx.data["frame_d"] < ctx.data["frame"])
        off = self._brushed(ctx, frame, "brush", 0.2 + 0.3 * s.roughness)
        c.shift(frame & (off != 0), off, L.lo, L.up)

    def _grate_large_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        solid = ctx.data["solid"]
        # grime settles on the lower half of the bars
        ys = np.arange(ctx.h)[:, None] * np.ones((1, ctx.w))
        grime = solid & (ys > ctx.h * (0.55 - 0.3 * s.moisture)) & \
            self.smooth_mask(ctx, "grime", 0.3 + 0.4 * s.moisture, 0.8)
        c.shift(grime, -1, L.lo, L.up)

    def _grate_medium_detail(self, ctx: GenContext) -> None:
        """Bolts on the frame corners and on bar crossings (density)."""
        s = ctx.settings
        f = ctx.data["frame"]
        big = ctx.w >= 32
        if s.density > 0.3 and f >= 2:
            o = f // 2
            for (x, y) in ((o, o), (ctx.w - 1 - o, o), (o, ctx.h - 1 - o), (ctx.w - 1 - o, ctx.h - 1 - o)):
                self._rivet(ctx, x, y, big)
        if s.density > 0.6 and ctx.data["bar"] >= 2:
            cross = ctx.data["cross"]
            lab, _ = pa.label_components(cross.astype(np.int32), ctx.wrap, cross)
            for k in range(int(lab.max()) + 1):
                ys, xs = np.nonzero(lab == k)
                if len(xs):
                    self._rivet(ctx, int(xs.min()), int(ys.min()), False)

    def _grate_small_detail(self, ctx: GenContext) -> None:
        s = ctx.settings
        solid = ctx.data["solid"] & (ctx.data["frame_d"] < ctx.data["frame"]) & ~ctx.canvas.tag("rivet")
        self._dents(ctx, solid, s.roughness * 0.35)

    def _grate_cracks(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        amt = ctx.data["scratch"]
        if amt <= 0.3:
            return
        # a few bars have snapped: remove a short piece of bar
        rng = ctx.rng("snap")
        bars = ctx.data["hbar"] | ctx.data["vbar"]
        ys, xs = np.nonzero(bars)
        if not len(xs):
            return
        n = int(round((amt - 0.3) * 3 * ctx.scale ** 0.5)) + 1
        for _ in range(n):
            i = int(rng.integers(len(xs)))
            m = pa.dilate(bp.point_mask(ctx, [(xs[i], ys[i])]), ctx.wrap) & bars
            c.clear(m)
            c.tag("crack")[m] = True

    def _grate_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        solid = c.opaque
        excl = c.tag("rivet") | c.tag("crack")
        d = ctx.data["frame_d"]
        outer = (d == 0) & ctx.data["f_tl"]
        c.set(outer & ~excl, "base", L.lit)
        # bars: faces toward the top-left catch light
        lit, _, _ = bp.raised_light(solid, ctx.wrap)
        inner_lit = lit & (d > 0) & ~excl
        c.shift(inner_lit, 1, L.lo, L.lit)
        cross = ctx.data["cross"] & ~excl
        c.set(cross, "base", L.lit if ctx.data["bar"] == 1 else np.maximum(c.level, L.up))
        glint_where = (outer | (cross & inner_lit)) & ~excl
        self._glint(ctx, glint_where, 0.08 + 0.3 * s.metallic, "grate_glint")
        corner = bp.zeros(ctx)
        corner[0, 0] = True
        c.set(corner & solid, "accent2", c.length_of("accent2") - 1)
        bp.protect(ctx, corner)

    def _grate_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        solid = c.opaque
        excl = c.tag("rivet") | c.tag("crack")
        d = ctx.data["frame_d"]
        c.set((d == 0) & ctx.data["f_br"] & solid & ~excl, "base", L.dark)
        _, dark, _ = bp.raised_light(solid, ctx.wrap)
        c.shift(dark & (d > 0) & ~excl & ~ctx.data["cross"], -1, L.dark)

    def _grate_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        solid = c.opaque & ~c.tag("rivet")
        if s.mineral > 0.02:
            self._corrode(ctx, solid, s.mineral * 1.1, "rust", prefer=ctx.data["cross"])
        if s.glow > 0.02:
            n = c.length_of("glow")
            cross = ctx.data["cross"] & c.opaque & ~c.tag("rivet")
            keep = bp.noise_keep(self, ctx, "grate_glow", min(1.0, 0.2 + s.glow), 2.0)
            m = cross & keep
            if s.glow > 0.6:
                # energised: the bars carry light between the nodes
                bars = (ctx.data["hbar"] | ctx.data["vbar"]) & c.opaque & ~c.tag("rivet")
                e = bp.mask_edges(bars | cross, ctx.wrap)
                core = bars & ~(e["top"] | e["left"]) if ctx.data["bar"] >= 2 else bars
                m |= core & bp.noise_keep(self, ctx, "grate_glow2", (s.glow - 0.6) * 2.2, 1.2)
                c.set(m & ~cross, "glow", 1)
            c.set(m & cross, "glow", min(n - 1, 2 + int(s.glow > 0.7)))
            c.emissive[m] = True
            bp.protect(ctx, m)

    # =================================================================== raw
    def _prep_raw(self, ctx: GenContext) -> None:
        s = ctx.settings
        cell = (4.0 + 3.0 * s.cluster_size + 1.6 * (1 - s.density)) * ctx.scale * 0.85
        prof = np.clip(math.sqrt(max(1.0, ctx.analysis.cluster_size)) / 2.4, 0.8, 1.25)
        cell *= prof
        g = max(1, int(round(ctx.w / cell)))
        off = noise.warp_offsets(ctx.w, ctx.h, ctx.rng("nug_warp"), 3, 0.03 + 0.04 * s.roughness)
        cellr = noise.cellular(ctx.w, ctx.h, ctx.rng("nuggets"), g, g, 0.75, offset=off)
        labels = cellr.cell.astype(np.int32)
        e = bp.edges(labels, ctx.wrap)
        crev = e["bottom"] | e["right"]
        # widen the gaps where nuggets meet at a corner so they read round
        spacing_px = ctx.w / g
        crev |= cellr.edge * spacing_px < 0.22 + 0.2 * s.roughness
        crev = pa.remove_small_clusters(crev.astype(np.int32), 2, ctx.wrap).astype(bool) & crev
        labels = np.where(crev, -1, labels)
        light, dome = bp.dome_light(labels, ctx.wrap)
        ctx.data.update(labels=labels, crevice=crev, light=light, dome=dome)

    def _raw_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.up)
        c.set(ctx.data["crevice"], "base", L.dark)

    def _raw_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        light = ctx.data["light"]
        grain = (self.height_field(ctx, "raw_grain", 1.8, 1) - 0.5) * 0.6 * s.roughness
        t = light + grain
        off = np.where(t > 0.3, 1, np.where(t < -0.35, -1, 0)).astype(np.int32)
        off = np.where(t < -0.8, -2, off)
        off = pa.remove_small_clusters(off, 2, ctx.wrap)
        m = ~ctx.data["crevice"] & (off != 0)
        c.shift(m, off, L.lo, L.lit)

    def _raw_large_detail(self, ctx: GenContext) -> None:
        """Nuggets differ a little in tone."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        n = int(labels.max()) + 1
        vals = ctx.rng("nug_tone").choice([-1, 0, 0, 1], size=n)
        body = ~ctx.data["crevice"]
        c.shift(body & (vals[labels] != 0), vals[labels], L.lo, L.lit)
        if s.moisture > 0.3:
            damp = self.smooth_mask(ctx, "tarnish", 0.35 * (s.moisture - 0.3), 0.7) & body
            c.shift(damp, -1, L.lo, L.up)

    def _raw_medium_detail(self, ctx: GenContext) -> None:
        """Pits and pockmarks on the nuggets (density, roughness)."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ~ctx.data["crevice"] & (ctx.data["light"] < 0.4)
        count = int(round((0.5 + 3 * s.density) * (0.4 + s.roughness) * ctx.scale ** 2))
        for cells in bp.clusters(ctx, "pits", count, (1, max(1, ctx.px(2))), mask=body,
                                         min_dist=3 * ctx.scale ** 0.5):
            m = bp.point_mask(ctx, cells) & body
            c.set(m, "base", L.dark)
            lip = pa.neighbour(m, 0, -1, ctx.wrap, fill=False) & ~m & ~ctx.data["crevice"]
            c.shift(lip, 1, L.lo, L.lit)
            bp.protect(ctx, m)
            c.tag("pit")[m] = True

    def _raw_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        mask = ~ctx.data["crevice"] & ~c.tag("pit")
        count = int(round(s.noise * 3 * ctx.scale ** 2))
        rng = ctx.rng("raw_speck_sign")
        for cells in bp.clusters(ctx, "raw_specks", count, (2, 3), mask=mask, min_dist=3):
            m = bp.point_mask(ctx, cells) & mask
            c.shift(m, 1 if rng.random() < 0.5 else -1, L.lo, L.lit - 1)

    def _raw_cracks(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        amt = ctx.data["scratch"]
        if amt <= 0.01:
            return
        cr = self.crack_paths(ctx, amt * 0.8, "raw_cracks", length=(0.15, 0.4))
        cr = bp.four_connect(cr, None, ctx.wrap) & ~c.tag("pit")
        c.set(cr, "base", L.dark)
        c.tag("crack")[cr] = True

    def _raw_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        e = bp.edges(labels, ctx.wrap)
        lit, _, corner, _ = bp.bevel_masks(e)
        excl = ctx.data["crevice"] | c.tag("pit") | c.tag("crack")
        c.shift(lit & ~excl & (ctx.data["light"] > -0.2), 1, L.lo, L.lit)
        # a glint on the lit shoulder of most nuggets
        light = ctx.data["light"]
        n = int(labels.max()) + 1
        best = np.full(n, -np.inf)
        cand = ~excl & (light > 0.3)
        score = np.where(cand, light + ctx.data["dome"] * 0.5, -np.inf)
        np.maximum.at(best, labels.ravel(), score.ravel())
        top = cand & (score >= best[labels] - 1e-9)
        keep = ctx.rng("raw_glint").random(n) < 0.45 + 0.5 * s.metallic + 0.2
        top &= keep[labels]
        c.set(top, "accent2", c.length_of("accent2") - 1)
        near = pa.dilate(top, ctx.wrap) & ~top & cand & (c.ramp == 0)
        c.set(near & (light > 0.5), "base", L.lit)
        bp.protect(ctx, top)

    def _raw_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        e = bp.edges(labels, ctx.wrap)
        _, dark, _, _ = bp.bevel_masks(e)
        excl = ctx.data["crevice"] | c.tag("pit") | c.tag("crack")
        c.shift(dark & ~excl & (ctx.data["light"] < 0.2), -1, L.lo)
        cre = ctx.data["crevice"] & ~c.tag("crack")
        nb = sum(pa.neighbour(cre, dx, dy, ctx.wrap, fill=False).astype(np.int32) for dx, dy in pa.N4)
        c.set(cre, "base", L.dark)
        c.set(cre & (nb >= 3), "base", L.deep)

    def _raw_accent(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        if s.mineral > 0.02:
            n = int(labels.max()) + 1
            rng = ctx.rng("rusty")
            frac = min(1.0, s.mineral * 0.8)
            rusty = rng.random(n) < frac
            m = rusty[labels] & ~ctx.data["crevice"] & (c.ramp == 0)
            # corrosion keeps to the shadowed side of a nugget unless it is heavy
            if s.mineral < 0.7:
                m &= ctx.data["light"] < 0.35 + (s.mineral - 0.2)
            na = c.length_of("accent")
            lv = bp.base_to_role(c.level, L.K, na, 0, na - 1)
            m = pa.remove_small_clusters(m.astype(np.int32), 2, ctx.wrap).astype(bool) & m
            c.set(m, "accent", lv)
        if s.glow > 0.02:
            n = c.length_of("glow")
            cre = ctx.data["crevice"]
            keep = bp.noise_keep(self, ctx, "raw_glow", min(1.0, 0.06 + 0.42 * s.glow), 1.8)
            m = cre & keep
            c.set(m, "glow", 1 + int(s.glow > 0.5))
            nb = sum(pa.neighbour(m, dx, dy, ctx.wrap, fill=False).astype(np.int32) for dx, dy in pa.N4)
            c.set(m & (nb >= 2), "glow", min(n - 1, 2 + int(s.glow > 0.75)))
            c.emissive[m] = True
            bp.protect(ctx, m)
