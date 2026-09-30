"""Decoration: built blocks for sunken ruins and deep-sea structures.

Variants
--------
bricks    staggered courses of bevel-lit bricks in dark mortar; chipped
          corners, cracked bricks, mineral inlays, glowing joints
tiles     square tile grid (plain / checker / inset / diamond tiles) with
          corner inlays where four tiles meet
polished  smooth slab: bevelled frame around a recessed, glossy field
chiseled  framed slab carved with a mirror-symmetric motif picked by seed
          (trident, shell, eye, waves, star, jellyfish, anchor, rune)
pillar    pillar side with vertical fluting; tiles vertically
lamp      stone frame holding glowing panes / core (glow role, emissive)

Every structural pattern is periodic over the texture, so the blocks tile.
"""
from __future__ import annotations

import math

import numpy as np

from core import pixel_art as pa
from core.layers import GenContext
from core.settings import TextureSettings

from . import _block_patterns as bp
from .base import BaseGenerator

VARIANT_NAMES = ("bricks", "tiles", "polished", "chiseled", "pillar", "lamp")
MOTIFS = ("trident", "shell", "eye", "waves", "star", "jelly", "anchor", "rune")


class DecorationGenerator(BaseGenerator):
    category = "decoration"

    # ------------------------------------------------------------ setup
    def body_levels(self, s: TextureSettings, a) -> int:
        k = super().body_levels(s, a)
        if s.levels == 0 and (not s.color_limit or s.color_limit >= 12):
            k = max(4, k)   # bevels + surface need a few body tones
        return k

    def prepare(self, ctx: GenContext) -> None:
        s = ctx.settings
        v = s.variant if s.variant in VARIANT_NAMES else "bricks"
        ctx.data["variant"] = v
        # built blocks are intact at the default slider values: cracks and
        # damp stains only start above them
        ctx.data["cracks"] = float(np.clip((s.cracks - 0.33) / 0.67, 0, 1))
        ctx.data["damp"] = float(np.clip((s.moisture - 0.15) / 0.85, 0, 1))
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

    # ================================================================ panels
    # bricks and tiles: a label map of panels separated by a mortar/grout gap

    def _prep_bricks(self, ctx: GenContext) -> None:
        rng = ctx.rng("layout")
        W, H = ctx.w, ctx.h
        target = 4 * max(0.5, ctx.scale) ** 0.75
        ch = bp.nearest_divisor(H, target, lo=min(4, H))
        if H >= 32 and rng.random() < 0.3:
            smaller = [d for d in range(4, ch) if H % d == 0]
            if smaller:
                ch = smaller[-1]
        m = max(1, int(round(ch / 10)))
        style = str(rng.choice(["running", "irregular", "long"], p=[0.35, 0.5, 0.15]))
        opts = sorted({max(2, int(round(ch * f))) for f in (1.0, 1.5, 2.0, 2.5, 3.0)})
        opt_w = [0.5, 1.0, 1.5, 1.0, 0.6][: len(opts)]
        long_opts = sorted({max(2, int(round(ch * f))) for f in (2.0, 3.0, 4.0)})
        sep = max(2, ch // 2)
        courses = max(1, H // ch)
        labels = np.full((H, W), -1, dtype=np.int32)
        gap = np.zeros((H, W), dtype=bool)
        panels, joints = [], []
        base_off = int(rng.integers(W))
        prev = first = None
        lab = 0

        def far_enough(js, other):
            if other is None:
                return True
            for a in js:
                for b in other:
                    d = abs(a - b) % W
                    if min(d, W - d) < sep:
                        return False
            return True

        for r in range(courses):
            y0 = r * ch
            widths, js, off = [W], [0], 0
            for attempt in range(60):
                if style == "running":
                    bw = min(W, 2 * ch)
                    widths = [bw] * max(1, W // bw)
                    if sum(widths) != W:
                        widths[-1] += W - sum(widths)
                    off = (base_off + (r % 2) * ch) % W
                elif style == "long":
                    widths = bp.partition(W, rng, long_opts, [1.0, 1.6, 1.2])
                    off = int(rng.integers(W))
                else:
                    widths = bp.partition(W, rng, opts, opt_w)
                    off = int(rng.integers(W))
                starts = np.cumsum([0] + widths[:-1])
                js = [int((off + s0) % W) for s0 in starts]
                ok = far_enough(js, prev) and (r < courses - 1 or far_enough(js, first) or courses < 3)
                if ok or style == "running":
                    break
            rows = np.arange(y0, y0 + ch)
            body_rows = rows[: ch - m] if ch > m else rows[:1]
            gap[rows[len(body_rows):] % H, :] = True
            for i, (st, wd) in enumerate(zip(js, widths)):
                xs = (st + np.arange(wd)) % W
                mcols = xs[: min(m, max(0, wd - 1))]
                bcols = xs[len(mcols):]
                labels[np.ix_(body_rows % H, bcols)] = lab
                gap[np.ix_(body_rows % H, mcols)] = True
                panels.append({"label": lab, "x0": int(st + len(mcols)), "y0": int(y0),
                               "w": int(len(bcols)), "h": int(len(body_rows))})
                joints.append((int(st), int(y0), int(len(mcols)), int(len(body_rows))))
                lab += 1
            if first is None:
                first = js
            prev = js
        labels[gap] = -1
        ctx.data.update(labels=labels, gap=gap, panels=panels, joints=joints, unit=ch,
                        mortar=m, bevel_w=max(1, int(round(ch / 9))), style=style)

    def _prep_tiles(self, ctx: GenContext) -> None:
        s = ctx.settings
        rng = ctx.rng("layout")
        W, H = ctx.w, ctx.h
        cands = [t for t in (W // 2, W // 4, W // 8)
                 if t >= (4 if W <= 8 else 6 if s.density < 0.8 else 4) and W % t == 0 and H % t == 0] or [W // 2 or W]
        pick = (s.density - 0.2) * 1.4 * (len(cands) - 1) + rng.uniform(-0.3, 0.3)
        ts = cands[int(np.clip(round(pick), 0, len(cands) - 1))]
        g = max(1, int(round(ts / 12)))
        body = ts - g
        styles = ["plain", "checker"] + (["inset", "diamond", "diamond"] if body >= 7 else [])
        style = str(rng.choice(styles))
        ys, xs = np.mgrid[0:H, 0:W]
        tx, ty = xs // ts, ys // ts
        lx, ly = xs % ts, ys % ts
        gap = (lx >= body) | (ly >= body)
        n = W // ts
        labels = np.where(gap, -1, ty * n + tx).astype(np.int32)
        tone = np.zeros((H, W), dtype=np.int32)
        raised = np.zeros((H, W), dtype=bool)
        if style == "checker":
            tone = np.where((tx + ty) % 2 == 0, 1, -1).astype(np.int32)
        elif style == "inset":
            r = max(1, body // 4)
            raised = (lx >= r) & (ly >= r) & (lx < body - r) & (ly < body - r)
        elif style == "diamond":
            cxy = (body - 1) / 2
            rd = max(1.0, cxy - max(1, body // 7))
            raised = (np.abs(lx - cxy) + np.abs(ly - cxy)) <= rd + 0.01
        raised &= ~gap
        tone[gap] = 0
        panels = [{"label": int(j * n + i), "x0": i * ts, "y0": j * ts, "w": body, "h": body}
                  for j in range(H // ts) for i in range(n)]
        cross = [(i * ts + body + (g - 1) // 2, j * ts + body + (g - 1) // 2)
                 for j in range(H // ts) for i in range(n)]
        ctx.data.update(labels=labels, gap=gap, panels=panels, struct_tone=tone, raised=raised,
                        unit=ts, mortar=g, bevel_w=max(1, int(round(body / 10))), style=style,
                        crossings=cross)

    # ---------------------------------------------------------- panel layers
    def _panel_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.fill("base", L.mid)
        c.set(ctx.data["gap"], "base", L.deep)
        tone = ctx.data.get("struct_tone")
        body = ctx.data["labels"] >= 0
        if tone is not None and tone.any():
            c.shift(body & (tone != 0), tone, L.dark, L.lit)
        raised = ctx.data.get("raised")
        if raised is not None and raised.any():
            c.shift(raised, 1, L.dark, L.lit)

    def _panel_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ctx.data["labels"] >= 0
        smooth = 0.1 if ctx.data["variant"] == "tiles" else 0.0
        off = bp.surface_offsets(self, ctx, "surface", body, 1, 1.5,
                                 flat=0.8 + smooth - 0.4 * s.roughness)
        c.shift(body & (off != 0), off, L.lo, L.up)

    def _panel_large_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        body = labels >= 0
        rng = ctx.rng("panel_tone")
        n = len(ctx.data["panels"])
        q = 0.35 + 0.3 * s.roughness
        vals = rng.choice([-1, 0, 1], size=n + 1, p=[q * 0.35, 1 - q, q * 0.65]).astype(np.int32)
        delta = np.where(body, vals[np.clip(labels, 0, n)], 0)
        c.shift(body & (delta != 0), delta, L.lo, L.up)
        if ctx.data["damp"] > 0:
            damp = self.smooth_mask(ctx, "damp", 0.04 + 0.4 * ctx.data["damp"], 0.6) & body
            c.shift(damp, -1, L.lo, L.up)

    def _panel_medium_detail(self, ctx: GenContext) -> None:
        """Chipped corners: corner pixels fall into the mortar."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        panels = ctx.data["panels"]
        rng = ctx.rng("chips")
        count = int(round(len(panels) * (0.04 + 0.3 * s.density) * (0.5 + 0.8 * s.roughness)))
        if count <= 0:
            return
        labels, gap = ctx.data["labels"], ctx.data["gap"]
        chip = bp.zeros(ctx)
        for _ in range(count):
            p = panels[int(rng.integers(len(panels)))]
            if p["w"] < 3 or p["h"] < 2:
                continue
            corner = int(rng.choice(4, p=[0.3, 0.2, 0.2, 0.3]))
            r = max(0, int(round(min(p["w"], p["h"]) / 6)))
            pts = [(dx, dy) for dy in range(r + 1) for dx in range(r + 1 - dy)]
            if r == 0 and p["w"] >= 6 and rng.random() < 0.4:
                pts = [(0, 0), (1, 0)]
            for dx, dy in pts:
                x = p["x0"] + (dx if corner in (0, 2) else p["w"] - 1 - dx)
                y = p["y0"] + (dy if corner in (0, 1) else p["h"] - 1 - dy)
                chip[y % ctx.h, x % ctx.w] = True
        chip &= labels >= 0
        labels[chip] = -1
        gap |= chip
        c.set(chip, "base", L.deep)
        c.tag("chip")[chip] = True

    def _panel_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        if s.noise <= 0.02:
            return
        body = ctx.data["labels"] >= 0
        count = int(round(s.noise * (2 + 3 * s.density) * ctx.scale ** 2 * 0.6))
        rng = ctx.rng("speck_sign")
        inner = body & (bp.region_depth(ctx.data["labels"], ctx.wrap, 1) >= 1)
        for cells in bp.clusters(ctx, "specks", count, (2, 2 + int(ctx.scale >= 2)),
                                         mask=inner if inner.any() else body, min_dist=3, compact=0.6):
            m = bp.point_mask(ctx, cells) & body
            c.shift(m, 1 if rng.random() < 0.4 else -1, L.lo, L.up)

    def _panel_cracks(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        cracks = ctx.data["cracks"]
        if cracks <= 0.01:
            return
        panels = ctx.data["panels"]
        labels = ctx.data["labels"]
        rng = ctx.rng("panel_cracks")
        n = int(round(len(panels) * cracks * (0.35 + 0.4 * s.density) + 0.4))
        if n <= 0:
            return
        crack = bp.zeros(ctx)
        order = rng.permutation(len(panels))[:n]
        for i in order:
            p = panels[int(i)]
            if p["w"] < 2 or p["h"] < 2:
                continue
            allowed = labels == p["label"]
            x = p["x0"] + int(rng.integers(1, max(2, p["w"] - 1)))
            y = p["y0"]
            ang = math.pi / 2 + rng.choice([-1, 1]) * rng.uniform(0.5, 0.9)
            length = int(max(p["h"], p["w"] * 0.6) * rng.uniform(1.0, 1.6)) + 2
            allow = None if cracks > 0.5 and rng.random() < (cracks - 0.4) * 1.5 else allowed
            pts = bp.random_walk(rng, x + 0.5, y + 0.5, ang, length * (2 if allow is None else 1),
                                 0.3, allow, ctx.wrap, ctx.w, ctx.h)
            if len(pts) >= 3:
                crack |= bp.point_mask(ctx, pts)
        crack &= labels >= 0
        self._paint_crack(ctx, crack, ctx.data["L"].dark)

    def _paint_crack(self, ctx: GenContext, crack: np.ndarray, level: int | None = None) -> None:
        if not crack.any():
            return
        L, c = ctx.data["L"], ctx.canvas
        c.set(crack, "base", L.deep if level is None else level)
        c.tag("crack")[crack] = True
        lip = (pa.neighbour(crack, -1, 0, ctx.wrap, fill=False) | pa.neighbour(crack, 0, -1, ctx.wrap, fill=False))
        lip &= ~crack & (c.ramp == 0) & (c.level > L.deep) & ~c.tag("chip")
        c.shift(lip, 1, L.dark, L.lit)

    def _panel_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        e = bp.edges(labels, ctx.wrap)
        lit, _, corner, _ = bp.bevel_masks(e, ctx.data["bevel_w"], labels, ctx.wrap)
        excl = c.tag("crack")
        keep = ~bp.noise_keep(self, ctx, "wear_lit", 0.25 * s.roughness * (0.5 + s.density))
        c.shift(lit & keep & ~excl & ~c.tag("chip"), 1, L.lo, L.lit)
        rng = ctx.rng("corner_spec")
        spec = corner & ~excl
        if spec.any():
            ys, xs = np.nonzero(spec)
            pick = rng.random(len(xs)) < 0.3 + 0.4 * (1 - s.roughness)
            m = bp.zeros(ctx)
            m[ys[pick], xs[pick]] = True
            c.set(m, "base", L.hi)
            bp.protect(ctx, m)
        raised = ctx.data.get("raised")
        if raised is not None and raised.any():
            rl, _, _ = bp.raised_light(raised, ctx.wrap)
            c.shift(rl & ~excl, 1, L.dark, L.lit)
        if s.moisture > 0.45:
            self._gloss(ctx, lit & ~excl, (s.moisture - 0.45) * 0.5)

    def _gloss(self, ctx: GenContext, where: np.ndarray, frac: float) -> None:
        L, c = ctx.data["L"], ctx.canvas
        ys, xs = np.nonzero(where)
        if not len(xs) or frac <= 0:
            return
        rng = ctx.rng("gloss")
        k = min(len(xs), max(1, int(len(xs) * frac)))
        idx = rng.choice(len(xs), size=k, replace=False)
        m = bp.zeros(ctx)
        m[ys[idx], xs[idx]] = True
        c.set(m, "base", L.hi)
        bp.protect(ctx, m)

    def _panel_shadows(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        labels = ctx.data["labels"]
        e = bp.edges(labels, ctx.wrap)
        _, dark, _, dcorner = bp.bevel_masks(e, ctx.data["bevel_w"], labels, ctx.wrap)
        excl = c.tag("crack") | c.tag("chip")
        c.shift(dark & ~excl, -1, L.dark)
        raised = ctx.data.get("raised")
        if raised is not None and raised.any():
            _, rd, drop = bp.raised_light(raised, ctx.wrap)
            body = labels >= 0
            c.shift(rd & ~excl, -1, L.dark)
            c.shift(drop & body & ~excl, -1, L.dark)

    def _inlay(self, ctx: GenContext, m: np.ndarray, role: str, glow: bool = False) -> None:
        """Paint an inlaid gem/mineral piece with its own tiny bevel."""
        if not m.any():
            return
        c = ctx.canvas
        n = c.length_of(role)
        lit, dark, _ = bp.raised_light(m, ctx.wrap)
        base_lv = max(0, n - 3) if not glow else max(1, n - 3)
        c.set(m, role, base_lv)
        c.set(m & lit, role, min(n - 1, base_lv + 1))
        c.set(m & dark, role, max(0, base_lv - 1))
        e = bp.mask_edges(m, ctx.wrap)
        tl = m & e["top"] & e["left"]
        c.set(tl, role, n - 1)
        bp.protect(ctx, m)
        c.tag("inlay")[m] = True
        if glow:
            c.emissive[m] = True

    def _accent_panels(self, ctx: GenContext, amount: float) -> None:
        """High mineral: whole panels of accent stone (keeps their shading)."""
        if amount <= 0:
            return
        L, c = ctx.data["L"], ctx.canvas
        panels = ctx.data["panels"]
        labels = ctx.data["labels"]
        rng = ctx.rng("accent_panels")
        k = int(round(len(panels) * amount))
        if k <= 0:
            return
        for i in rng.permutation(len(panels))[:k]:
            m = (labels == panels[int(i)]["label"]) & ~c.tag("inlay") & (c.ramp == 0)
            if not m.any():
                continue
            lv = bp.base_to_role(c.level, L.K, c.length_of("accent"), 0, c.length_of("accent") - 1)
            c.set(m, "accent", lv)

    def _grains(self, ctx: GenContext, amount: float, mask: np.ndarray, key: str) -> None:
        """Small inlaid accent grains inside panel interiors."""
        if amount <= 0.01 or not mask.any():
            return
        c = ctx.canvas
        count = int(round(amount * (2 + 0.6 * len(ctx.data.get("panels", [1] * 6))) * max(1, ctx.scale) ** 0.5))
        size = (2, 2 + int(ctx.scale >= 2) * 2)
        n = c.length_of("accent")
        rng = ctx.rng(key + "_tone")
        for cells in bp.clusters(ctx, key, count, size, mask=mask, min_dist=3, compact=0.85):
            m = bp.point_mask(ctx, cells) & mask
            if not m.any():
                continue
            lv = int(rng.integers(max(1, n - 3), n - 1))
            c.set(m, "accent", lv)
            e = bp.mask_edges(m, ctx.wrap)
            c.set(m & (e["top"] | e["left"]), "accent", min(n - 1, lv + 1))
            bp.protect(ctx, m)
            c.tag("inlay")[m] = True

    # ---------------------------------------------------------------- bricks
    _bricks_base = _panel_base
    _bricks_material = _panel_material
    _bricks_large_detail = _panel_large_detail
    _bricks_medium_detail = _panel_medium_detail
    _bricks_small_detail = _panel_small_detail
    _bricks_cracks = _panel_cracks
    _bricks_highlights = _panel_highlights
    _bricks_shadows = _panel_shadows

    def _bricks_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        labels = ctx.data["labels"]
        body = labels >= 0
        interior = body & (bp.region_depth(labels, ctx.wrap, 2) >= 1) & ~c.tag("crack")
        self._accent_panels(ctx, max(0.0, s.mineral - 0.5) * 0.55)
        self._grains(ctx, s.mineral, interior if interior.any() else body, "brick_grains")
        if s.glow > 0.02:
            self._glow_joints(ctx)

    def _glow_joints(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        rng = ctx.rng("glow_joints")
        gap = ctx.data["gap"] & ~c.tag("chip")
        joints = ctx.data["joints"]
        n = c.length_of("glow")
        strong = s.glow > 0.55
        chosen = [j for j in joints if rng.random() < 0.12 + 0.55 * s.glow]
        if not chosen and joints:
            chosen = [joints[int(rng.integers(len(joints)))]]
        m_all = bp.zeros(ctx)
        for (x0, y0, mw, bh) in chosen:
            m = bp.zeros(ctx)
            xs = (x0 + np.arange(max(1, mw))) % ctx.w
            ys = (y0 + np.arange(max(1, bh))) % ctx.h
            m[np.ix_(ys, xs)] = True
            if strong:
                # run into the horizontal mortar below: a T-shaped glowing seam
                yb = (y0 + bh) % ctx.h
                ext = int(round(ctx.data["unit"] * (0.1 + 0.5 * (s.glow - 0.55))))
                for dx in range(-ext, ext + mw):
                    m[yb, (x0 + dx) % ctx.w] = True
            m &= gap
            if not m.any():
                continue
            d = bp.mask_depth(m, ctx.wrap, 3)
            lv = np.full((ctx.h, ctx.w), 1, dtype=np.int32)
            # brightest in the middle of each joint
            mid_y = (y0 + bh // 2) % ctx.h
            core = m & (np.arange(ctx.h)[:, None] == mid_y)
            lv[core | (d >= 1)] = min(n - 1, 2 + int(s.glow > 0.85))
            c.set(m, "glow", lv)
            m_all |= m
        c.emissive[m_all] = True
        bp.protect(ctx, m_all)

    # ----------------------------------------------------------------- tiles
    _tiles_base = _panel_base
    _tiles_material = _panel_material
    _tiles_large_detail = _panel_large_detail
    _tiles_medium_detail = _panel_medium_detail
    _tiles_small_detail = _panel_small_detail
    _tiles_cracks = _panel_cracks
    _tiles_highlights = _panel_highlights
    _tiles_shadows = _panel_shadows

    def _tiles_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        self._accent_panels(ctx, max(0.0, s.mineral - 0.6) * 0.5)
        if s.mineral + s.glow <= 0.02:
            return
        rng = ctx.rng("tile_inlays")
        body = ctx.data["unit"] - ctx.data["mortar"]
        r = 1 if body < 7 else max(1, body // 4)
        ys, xs = np.mgrid[0:ctx.h, 0:ctx.w]
        g = ctx.data["mortar"]
        for (cx, cy) in ctx.data["crossings"]:
            u = rng.random()
            if u >= min(1.0, 2.2 * (s.mineral + s.glow)):
                continue
            glow = rng.random() < s.glow / max(1e-6, s.mineral + s.glow)
            dx = np.abs(((xs - cx + ctx.w / 2) % ctx.w) - ctx.w / 2 - (g - 1) / 2)
            dy = np.abs(((ys - cy + ctx.h / 2) % ctx.h) - ctx.h / 2 - (g - 1) / 2)
            m = (dx + dy) <= r + (g - 1) / 2 + 0.01
            self._inlay(ctx, m, "glow" if glow else "accent", glow)

    # ============================================================== framed slabs
    def _frame_geometry(self, ctx: GenContext, fw: int) -> None:
        H, W = ctx.h, ctx.w
        ys, xs = np.mgrid[0:H, 0:W]
        top, left, bottom, right = ys, xs, H - 1 - ys, W - 1 - xs
        d = np.minimum(np.minimum(top, left), np.minimum(bottom, right))
        tl = np.minimum(top, left)
        br = np.minimum(bottom, right)
        ctx.data.update(fd=d, f_tl=tl < br, f_br=br < tl, fw=fw)

    def _prep_polished(self, ctx: GenContext) -> None:
        rng = ctx.rng("layout")
        fw = max(2, int(round(2 * ctx.scale ** 0.6)))
        if ctx.w <= 8:
            fw = 1
        self._frame_geometry(ctx, fw)
        ctx.data["style"] = str(rng.choice(["frame", "frame", "double"]))
        ctx.data["bevel_w"] = max(1, int(round(ctx.scale / 3)))

    def _slab_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        d, fw = ctx.data["fd"], ctx.data["fw"]
        c.fill("base", L.mid)
        c.set(d < fw, "base", L.up)
        if ctx.data.get("style") == "double" and fw >= 2 and ctx.w >= 16:
            # a second, thin groove line inside the frame
            g = d == fw + max(1, fw // 2)
            c.set(g, "base", L.lo)
            ctx.data["groove"] = g

    def _slab_material(self, ctx: GenContext, flat: float = 0.55) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        d, fw = ctx.data["fd"], ctx.data["fw"]
        field = d >= fw
        off = bp.surface_offsets(self, ctx, "field", field, 1, 0.55, flat=flat - 0.35 * s.roughness)
        c.shift(field & (off != 0) & ~ctx.data.get("groove", bp.zeros(ctx)), off, L.dark, L.lit)
        frame = d < fw
        off2 = bp.surface_offsets(self, ctx, "frame_surf", frame, 1, 1.2, flat=0.8 - 0.4 * s.roughness)
        c.shift(frame & (off2 != 0) & (d > 0), off2, L.lo, L.lit)

    def _slab_light(self, ctx: GenContext, sign: int) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        d, fw, tl, br = ctx.data["fd"], ctx.data["fw"], ctx.data["f_tl"], ctx.data["f_br"]
        bw = ctx.data["bevel_w"]
        outer = d < bw
        excl = c.tag("crack") | c.tag("inlay")
        if sign > 0:
            c.set(outer & tl & ~excl, "base", L.lit)
            corner = bp.zeros(ctx)
            corner[:bw, :bw] = True
            c.set(corner & ~excl, "base", L.hi)
            bp.protect(ctx, corner)
            inner_lit = (d == fw - 1) & br & (d >= bw)
            c.shift(inner_lit & ~excl, 1, L.dark, L.lit)
        else:
            c.set(outer & br & ~excl, "base", L.dark)
            inner_sh = (d == fw) & tl
            c.shift(inner_sh & ~excl, -1, L.dark)
            g = ctx.data.get("groove")
            if g is not None:
                below = pa.neighbour(g, 0, -1, ctx.wrap, fill=False) & ~g
                c.shift(below & (d > fw) & ~excl, 1, L.dark, L.lit)

    def _polished_base(self, ctx): self._slab_base(ctx)
    def _polished_material(self, ctx): self._slab_material(ctx, 0.92)

    def _polished_large_detail(self, ctx: GenContext) -> None:
        """Soft marble veins (one tone darker) wandering across the field."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        field = ctx.data["fd"] > ctx.data["fw"]
        veins = self._veins(ctx, "veins", field, int(round(0.4 + 2 * s.density)))
        c.shift(veins, 1, L.lo, L.up)
        ctx.data["veins"] = veins
        if ctx.data["damp"] > 0:
            damp = self.smooth_mask(ctx, "damp", 0.04 + 0.35 * ctx.data["damp"], 0.6) & field
            c.shift(damp, -1, L.dark, L.lit)

    def _veins(self, ctx: GenContext, key: str, mask: np.ndarray, n: int) -> np.ndarray:
        rng = ctx.rng(key)
        out = bp.zeros(ctx)
        ys, xs = np.nonzero(mask)
        if not len(xs):
            return out
        for _ in range(max(0, n)):
            i = int(rng.integers(len(xs)))
            ang = rng.uniform(0, 2 * math.pi)
            length = int(ctx.w * rng.uniform(0.3, 0.6))
            pts = bp.random_walk(rng, xs[i] + 0.5, ys[i] + 0.5, ang, length, 0.25, mask, ctx.wrap,
                                 ctx.w, ctx.h)
            if len(pts) >= 3:
                out |= bp.point_mask(ctx, pts)
        return out & mask

    def _polished_medium_detail(self, ctx: GenContext) -> None:
        """A few worn corners on the frame."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        rng = ctx.rng("frame_chips")
        n = int(round(3 * (0.3 + s.density) * max(0.0, s.roughness - 0.55) * 2.2))
        H, W = ctx.h, ctx.w
        corners = [(0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)]
        chip = bp.zeros(ctx)
        for k in rng.permutation(4)[:n]:
            x, y, sx, sy = corners[int(k)]
            r = max(0, int(round(ctx.scale / 2)))
            for dy in range(r + 1):
                for dx in range(r + 1 - dy):
                    chip[y + sy * dy, x + sx * dx] = True
        c.set(chip, "base", L.lo)
        c.tag("chip")[chip] = True

    def _polished_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        field = ctx.data["fd"] > ctx.data["fw"]
        count = int(round(s.noise * (1 + 3 * s.density) * ctx.scale ** 2 * 0.7))
        rng = ctx.rng("speck_sign")
        for cells in bp.clusters(ctx, "specks", count, (2, 3), mask=field, min_dist=3):
            m = bp.point_mask(ctx, cells) & field
            c.shift(m, 1 if rng.random() < 0.5 else -1, L.dark, L.lit)

    def _slab_cracks(self, ctx: GenContext) -> None:
        cracks = ctx.data["cracks"]
        if cracks <= 0.01:
            return
        cr = self.crack_paths(ctx, cracks * 0.8, "slab_cracks", length=(0.25, 0.6))
        cr = bp.four_connect(cr, None, ctx.wrap) & (ctx.data["fd"] > 0)
        self._paint_crack(ctx, cr, ctx.data["L"].dark)

    _polished_cracks = _slab_cracks

    def _polished_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        self._slab_light(ctx, +1)
        # glossy glints: short diagonal streaks in the upper-left of the field
        d, fw = ctx.data["fd"], ctx.data["fw"]
        field = (d > fw) & ~c.tag("crack")
        rng = ctx.rng("glints")
        # rough stone keeps no glints (it would read as metal); smooth or wet stone gets a few
        n = int(round(2.5 * (1 - s.roughness) + s.moisture))
        glint = bp.zeros(ctx)
        for i in range(n):
            ln = max(2, int(round(ctx.scale * rng.uniform(1.5, 3.0))))
            x0 = fw + 1 + int(rng.integers(0, max(1, ctx.w // 3)))
            y0 = fw + 1 + ln + int(rng.integers(0, max(1, ctx.h // 3)))
            pts = [(x0 + k, y0 - k) for k in range(ln)]
            glint |= bp.line_mask(ctx, pts)
        glint = bp.four_connect(glint, None, ctx.wrap) & field
        c.shift(glint, 1, L.dark, L.lit)
        if s.moisture > 0.45:
            self._gloss(ctx, glint, (s.moisture - 0.45) * 0.6)

    def _polished_shadows(self, ctx): self._slab_light(ctx, -1)

    def _polished_accent(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        d, fw = ctx.data["fd"], ctx.data["fw"]
        field = (d > fw) & ~c.tag("crack")
        if s.mineral > 0.02:
            if s.mineral >= 0.4:
                veins = ctx.data.get("veins")
                if veins is None or not veins.any() or not s.layer_enabled("large_detail"):
                    veins = self._veins(ctx, "veins", field, 1 + int(round(2 * s.density)))
                keep = bp.noise_keep(self, ctx, "vein_keep", min(1.0, (s.mineral - 0.3) * 1.6), 1.6)
                m = veins & keep & field
                n_acc = c.length_of("accent")
                lv = np.where(bp.mask_edges(m, ctx.wrap)["top"], n_acc - 2, n_acc - 3)
                c.set(m, "accent", np.clip(lv, 0, n_acc - 1))
                bp.protect(ctx, m)
                c.tag("inlay")[m] = True
            self._grains(ctx, min(0.4, s.mineral) * 0.6, field, "flecks")
        if s.glow > 0.02:
            ring = d == fw + (2 if ctx.data.get("style") == "double" else 1) * max(1, fw // 2)
            ring &= d < min(ctx.w, ctx.h) // 2
            if s.glow < 0.45:
                # only the corners of the inlaid ring
                ys, xs = np.mgrid[0:ctx.h, 0:ctx.w]
                arm = max(2, int(round(ctx.w * (0.12 + 0.35 * s.glow))))
                near = (np.minimum(xs, ctx.w - 1 - xs) < fw + arm) & (np.minimum(ys, ctx.h - 1 - ys) < fw + arm)
                ring &= near
            n = c.length_of("glow")
            c.set(ring, "glow", min(n - 1, 1 + int(s.glow > 0.55)))
            if s.glow > 0.85:
                ys, xs = np.nonzero(ring)
                if len(xs):
                    cor = ring & ((np.abs(np.arange(ctx.w)[None, :] - xs.min()) < 1) | (np.abs(np.arange(ctx.w)[None, :] - xs.max()) < 1)) \
                        & ((np.abs(np.arange(ctx.h)[:, None] - ys.min()) < 1) | (np.abs(np.arange(ctx.h)[:, None] - ys.max()) < 1))
                    c.set(cor, "glow", n - 1)
            c.emissive[ring] = True
            bp.protect(ctx, ring)

    # --------------------------------------------------------------- chiseled
    def _prep_chiseled(self, ctx: GenContext) -> None:
        fw = max(1, int(round(2 * ctx.scale ** 0.6)))
        if ctx.w <= 8:
            fw = 1
        self._frame_geometry(ctx, fw)
        ctx.data["bevel_w"] = max(1, int(round(ctx.scale / 3)))
        ctx.data["style"] = "frame"
        mrng = ctx.rng("motif")
        motif = MOTIFS[int(mrng.integers(len(MOTIFS)))]
        carve = str(mrng.choice(["engraved", "relief"], p=[0.55, 0.45]))
        s = ctx.settings
        detail = int(np.clip(round(s.density * 2.2 + mrng.uniform(-0.3, 0.3)), 0, 2))
        avail = ctx.w - 2 * fw - 2
        if avail >= 10:
            k = max(1, avail // 10)
            stroke, fill, gem = _motif(motif, k, detail)
        else:
            stroke, fill, gem = _mini_motif(motif)
        off = max(0, (ctx.w - stroke.shape[1]) // 2)
        big = lambda m: _place(m, ctx.h, ctx.w, off)
        ctx.data.update(motif=motif, carve=carve, m_stroke=big(stroke), m_fill=big(fill),
                        m_gem=big(gem), detail=detail)

    def _carving(self, ctx: GenContext) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """(raised or recessed area, grooves, gem) of the motif for the chosen carving style."""
        stroke, fill, gem = ctx.data["m_stroke"], ctx.data["m_fill"], ctx.data["m_gem"]
        if ctx.data["carve"] == "engraved":
            return fill | gem, stroke, gem
        inside = stroke & pa.dilate(fill, ctx.wrap)
        return fill | gem | (stroke & ~inside), stroke & inside, gem

    def _chiseled_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        self._slab_base(ctx)
        area, grooves, gem = self._carving(ctx)
        if ctx.data["carve"] == "engraved":
            c.set(area, "base", L.lo)
            c.set(grooves, "base", L.dark)
        else:
            c.set(area, "base", L.up)
            c.set(grooves, "base", L.lo)

    def _chiseled_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        area, grooves, gem = self._carving(ctx)
        shape = area | grooves
        d, fw = ctx.data["fd"], ctx.data["fw"]
        field = (d > fw) & ~pa.dilate(shape, ctx.wrap, True)
        off = bp.surface_offsets(self, ctx, "field", field, 1, 0.8, flat=0.9 - 0.3 * s.roughness)
        c.shift(field & (off != 0), off, L.lo, L.up)
        frame = (d < fw) & (d > 0)
        off2 = bp.surface_offsets(self, ctx, "frame_surf", frame, 1, 1.2, flat=0.85 - 0.35 * s.roughness)
        c.shift(frame & (off2 != 0), off2, L.lo, L.lit)

    def _chiseled_large_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        if ctx.data["damp"] > 0:
            damp = self.smooth_mask(ctx, "damp", 0.04 + 0.35 * ctx.data["damp"], 0.6)
            c.shift(damp & ~ctx.data["m_gem"], -1, L.dark, L.lit)

    def _chiseled_medium_detail(self, ctx: GenContext) -> None:
        """Frame studs in the corners (density) and worn corners."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        fw = ctx.data["fw"]
        if s.density > 0.55 and fw >= 2:
            r = fw
            stud = bp.zeros(ctx)
            for (x, y) in ((0, 0), (ctx.w - r, 0), (0, ctx.h - r), (ctx.w - r, ctx.h - r)):
                stud[y:y + r, x:x + r] = True
            c.set(stud, "base", L.up)
            lit, dark, _ = bp.raised_light(stud, False)
            c.set(stud & lit, "base", L.lit)
            c.set(stud & dark & ~lit, "base", L.lo)
            ctx.data["studs"] = stud
        self._polished_medium_detail(ctx)

    def _chiseled_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        area, grooves, _ = self._carving(ctx)
        field = (ctx.data["fd"] > ctx.data["fw"]) & ~pa.dilate(area | grooves, ctx.wrap, True)
        count = int(round(s.noise * (0.5 + 1.5 * s.density) * ctx.scale ** 2 * 0.5))
        rng = ctx.rng("speck_sign")
        for cells in bp.clusters(ctx, "specks", count, (2, 3), mask=field, min_dist=3):
            m = bp.point_mask(ctx, cells) & field
            c.shift(m, 1 if rng.random() < 0.5 else -1, L.lo, L.up)

    def _chiseled_cracks(self, ctx: GenContext) -> None:
        cracks = ctx.data["cracks"]
        if cracks <= 0.01:
            return
        cr = self.crack_paths(ctx, cracks * 0.7, "slab_cracks", length=(0.25, 0.55))
        cr = bp.four_connect(cr, None, ctx.wrap) & (ctx.data["fd"] > 0) & ~ctx.data["m_gem"]
        self._paint_crack(ctx, cr, ctx.data["L"].dark)

    def _chiseled_highlights(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        self._slab_light(ctx, +1)
        area, grooves, gem = self._carving(ctx)
        excl = c.tag("crack") | gem
        field = ctx.data["fd"] >= ctx.data["fw"]
        if ctx.data["carve"] == "engraved":
            rec = area | grooves
            _, lit_wall, lip = bp.sunken_light(rec, ctx.wrap)
            c.shift(lip & field & ~excl & ~c.tag("chip"), 1, L.dark, L.lit)
            c.shift(lit_wall & area & ~excl, 1, L.dark, L.mid)
            bp.protect(ctx, (rec | lip) & field & ~c.tag("crack"))
        else:
            lit, _, _ = bp.raised_light(area, ctx.wrap)
            c.set(lit & ~excl, "base", L.lit)
            e = bp.mask_edges(area, ctx.wrap)
            tips = area & e["top"] & e["left"] & ~excl
            c.set(tips, "base", L.hi)
            bp.protect(ctx, tips)
            # grooves cut into the raised area: lit lower lip
            _, _, lip = bp.sunken_light(grooves, ctx.wrap)
            c.shift(lip & area & ~excl, 1, L.dark, L.lit)
            bp.protect(ctx, (area | grooves) & ~c.tag("crack"))

    def _chiseled_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        self._slab_light(ctx, -1)
        area, grooves, gem = self._carving(ctx)
        excl = c.tag("crack") | gem
        field = ctx.data["fd"] >= ctx.data["fw"]
        if ctx.data["carve"] == "engraved":
            c.set(grooves & ~excl, "base", L.deep)
            shade, _, _ = bp.sunken_light(area, ctx.wrap)
            c.set(shade & ~excl & ~grooves, "base", L.dark)
        else:
            _, dark, drop = bp.raised_light(area, ctx.wrap)
            c.shift(dark & ~excl & ~grooves, -1, L.lo)
            c.shift(drop & ~excl & field & ~area, -1, L.dark)
            c.set(grooves & ~excl, "base", L.dark)
            bp.protect(ctx, (area | grooves | drop) & field & ~c.tag("crack"))

    def _chiseled_accent(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        stroke, fill, gem = ctx.data["m_stroke"], ctx.data["m_fill"], ctx.data["m_gem"]
        excl = c.tag("crack")
        if s.mineral > 0.6:
            # inlaid mineral lines: the carving itself is filled with accent
            inl = (stroke | fill) & ~excl & ~gem
            lv = bp.base_to_role(c.level, L.K, c.length_of("accent"), 1, c.length_of("accent") - 1)
            c.set(inl, "accent", lv)
            bp.protect(ctx, inl)
        if s.glow > 0.6:
            g = stroke & ~excl & ~gem
            n = c.length_of("glow")
            lv = np.where(bp.mask_edges(g, ctx.wrap)["top"], min(n - 1, 2), 1 + int(s.glow > 0.85))
            c.set(g, "glow", lv)
            c.emissive[g] = True
            bp.protect(ctx, g)
        if gem.any():
            if s.glow > 0.02:
                self._inlay(ctx, gem, "glow", True)
            elif s.mineral > 0.02:
                self._inlay(ctx, gem, "accent")
        studs = ctx.data.get("studs")
        if studs is not None and s.mineral > 0.3:
            e = bp.mask_edges(studs, False)
            core = studs & ~(e["bottom"] | e["right"])
            self._inlay(ctx, core, "accent")

    # ----------------------------------------------------------------- pillar
    def _prep_pillar(self, ctx: GenContext) -> None:
        s = ctx.settings
        rng = ctx.rng("layout")
        W, H = ctx.w, ctx.h
        edge = max(1, int(round(ctx.scale)))
        fil = max(1, int(round(ctx.scale * 0.75)))
        inner = W - 2 * edge - 2 * fil
        # flute count: density picks many narrow or few wide flutes
        best = None
        want = 2 + s.density * 3 + rng.uniform(-0.4, 0.4)
        for n in range(2, 9):
            fwid = (inner - (n - 1) * fil) / n
            if fwid < 2 * max(1, ctx.scale ** 0.5) or fwid > 7 * ctx.scale:
                continue
            free = inner - (n - 1) * fil
            sym = (free % n == 0) or (n % 2 == 1) or ((free % n) % 2 == 0)
            score = abs(n - want) + (0 if sym else 1.5)
            if best is None or score < best[0]:
                best = (score, n)
        n = best[1] if best else 2
        kinds = np.zeros(W, dtype=np.int32)       # 0 fillet, 1 flute, 2 left edge, 3 right edge
        pos = np.zeros(W, dtype=np.float64)       # position across a flute 0..1
        flute_id = np.full(W, -1, dtype=np.int32)
        kinds[:edge] = 2
        kinds[W - edge:] = 3
        segs = bp.split_even(inner, n, fil)
        for i, (st, ln) in enumerate(segs):
            x0 = edge + fil + st
            kinds[x0:x0 + ln] = 1
            pos[x0:x0 + ln] = (np.arange(ln) + 0.5) / ln
            flute_id[x0:x0 + ln] = i
        seam = None
        if rng.random() < 0.5 and H >= 16:
            seam = int(rng.integers(H // 4, 3 * H // 4))
        K = np.broadcast_to(kinds, (H, W))
        ctx.data.update(kinds=K.copy(), fpos=np.broadcast_to(pos, (H, W)).copy(),
                        fid=np.broadcast_to(flute_id, (H, W)).copy(), n_flutes=n, seam=seam,
                        edge=edge, fil=fil)

    def _pillar_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        k = ctx.data["kinds"]
        c.fill("base", L.mid)
        c.set(k == 0, "base", L.up)
        c.set(k == 1, "base", L.lo)
        seam = ctx.data["seam"]
        if seam is not None:
            m = bp.zeros(ctx)
            m[seam, :] = True
            c.set(m, "base", L.deep)
            c.tag("seam")[m] = True

    def _pillar_material(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        body = ~c.tag("seam")
        off = bp.surface_offsets(self, ctx, "surface", body, 1, 0.9, flat=0.82 - 0.35 * s.roughness,
                                 aspect=(2.5, 0.7))
        c.shift(body & (off != 0), off, L.dark, L.lit)

    def _pillar_large_detail(self, ctx: GenContext) -> None:
        """Water stains running down the shaft."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        amt = 0.04 + 0.4 * ctx.data["damp"]
        streak = self.smooth_mask(ctx, "streaks", amt, 0.8, 1, aspect=(3.0, 0.35))
        c.shift(streak & ~c.tag("seam"), -1, L.dark, L.lit)

    def _pillar_medium_detail(self, ctx: GenContext) -> None:
        """Nicks along the fillet ridges."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        k = ctx.data["kinds"]
        rng = ctx.rng("nicks")
        n = int(round((1 + 5 * s.density) * s.roughness * ctx.scale))
        cols = np.nonzero(k[0] == 0)[0]
        if not len(cols):
            return
        m = bp.zeros(ctx)
        for _ in range(n):
            x = int(rng.choice(cols))
            y = int(rng.integers(ctx.h))
            ln = int(rng.integers(1, 3)) * max(1, int(ctx.scale ** 0.5))
            for dy in range(ln):
                m[(y + dy) % ctx.h, x] = True
        m &= ~c.tag("seam")
        c.set(m, "base", L.lo)
        c.tag("chip")[m] = True

    def _pillar_small_detail(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        if s.noise <= 0.02:
            return
        mask = (ctx.data["kinds"] <= 1) & ~c.tag("seam")
        count = int(round(s.noise * (2 + 3 * s.density) * ctx.scale ** 2 * 0.6))
        rng = ctx.rng("speck_sign")
        for cells in bp.clusters(ctx, "specks", count, (2, 3), mask=mask, min_dist=3,
                                         bias=(0.0, 1.5)):
            m = bp.point_mask(ctx, cells) & mask
            c.shift(m, 1 if rng.random() < 0.4 else -1, L.dark, L.lit)

    def _pillar_cracks(self, ctx: GenContext) -> None:
        cracks = ctx.data["cracks"]
        if cracks <= 0.01:
            return
        cr = self.crack_paths(ctx, cracks, "pillar_cracks", angle=90.0, length=(0.3, 0.7))
        cr = bp.four_connect(cr, None, ctx.wrap) & (ctx.data["kinds"] <= 1)
        self._paint_crack(ctx, cr, ctx.data["L"].dark)

    def _pillar_highlights(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        k, pos = ctx.data["kinds"], ctx.data["fpos"]
        excl = c.tag("crack") | c.tag("seam") | c.tag("chip")
        c.set((k == 2) & ~excl, "base", L.lit)
        # right wall of each concave flute faces the light
        c.shift((k == 1) & (pos > 0.66) & ~excl, 2 if L.K >= 5 else 1, L.dark, L.lit)
        c.shift((k == 1) & (pos > 0.4) & (pos <= 0.66) & ~excl, 1, L.dark, L.lit)
        seam = ctx.data["seam"]
        if seam is not None:
            m = bp.zeros(ctx)
            m[(seam + 1) % ctx.h, :] = True
            c.shift(m & ~excl, 1, L.dark, L.lit)
        # a few specular glints on the fillets
        fil = (k == 0) & ~excl
        keep = bp.noise_keep(self, ctx, "fillet_spec", 0.18 + 0.2 * (1 - s.roughness), 1.5)
        spec = fil & keep & (pa.neighbour(k, -1, 0, ctx.wrap, fill=0) == 1)
        c.set(spec, "base", L.lit)

    def _pillar_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        k, pos = ctx.data["kinds"], ctx.data["fpos"]
        excl = c.tag("crack") | c.tag("seam")
        c.set((k == 3) & ~excl, "base", L.dark)
        c.shift((k == 1) & (pos < 0.34) & ~excl, -1, L.dark)
        seam = ctx.data["seam"]
        if seam is not None:
            m = bp.zeros(ctx)
            m[(seam - 1) % ctx.h, :] = True
            c.shift(m & ~excl, -1, L.dark)

    def _pillar_accent(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        k, pos, fid = ctx.data["kinds"], ctx.data["fpos"], ctx.data["fid"]
        n = ctx.data["n_flutes"]
        rng = ctx.rng("pillar_inlay")
        excl = c.tag("crack") | c.tag("seam")
        # the deepest column of a flute: the one just left of centre
        bottom = (k == 1) & (pos >= 0.3) & (pos < 0.55)
        if not bottom.any():
            bottom = (k == 1) & (pos < 0.6)
        order = rng.permutation(n)
        if s.mineral > 0.02:
            n_acc = max(1, int(round(n * min(1.0, s.mineral * 1.2))))
            chosen = np.isin(fid, order[:n_acc])
            keep = bp.noise_keep(self, ctx, "pillar_acc_keep", min(1.0, 0.25 + s.mineral), 1.0) \
                if s.mineral < 0.75 else np.ones_like(bottom)
            m = bottom & chosen & keep & ~excl
            na = c.length_of("accent")
            ys = np.arange(ctx.h)[:, None] * np.ones((1, ctx.w), dtype=int)
            lv = np.where((ys % max(2, ctx.px(4))) == 0, na - 2, na - 3)
            c.set(m, "accent", np.clip(lv, 0, na - 1))
            bp.protect(ctx, m)
        if s.glow > 0.02:
            n_gl = max(1, int(round(n * min(1.0, s.glow * 1.3))))
            chosen = np.isin(fid, order[::-1][:n_gl])
            keep = bp.noise_keep(self, ctx, "pillar_glow_keep", min(1.0, 0.3 + s.glow), 0.8, )
            m = bottom & chosen & keep & ~excl
            ng = c.length_of("glow")
            top_lv = min(ng - 1, 1 + int(s.glow > 0.4) + int(s.glow > 0.9))
            pulse = self.height_field(ctx, "pillar_pulse", 1.2, 1, aspect=(1.0, 1.0)) > 0.5
            c.set(m, "glow", np.where(pulse, top_lv, max(1, top_lv - 1)))
            c.emissive[m] = True
            bp.protect(ctx, m)

    # ------------------------------------------------------------------- lamp
    def _prep_lamp(self, ctx: GenContext) -> None:
        s = ctx.settings
        rng = ctx.rng("layout")
        W, H = ctx.w, ctx.h
        fw = max(1, int(round(2 * ctx.scale ** 0.8))) if W > 8 else 1
        inner = W - 2 * fw
        pick = s.density * 3 + rng.uniform(-0.95, 0.95)
        style = "single" if pick < 0.95 else ("quad" if pick < 2.05 else "nine")
        if style == "single" and rng.random() < 0.4:
            style = "ring"
        mull = max(1, int(round(ctx.scale * (1.5 if style == "quad" else 1.0))))
        if inner < 8:
            style = "single"
        n = {"single": 1, "ring": 1, "quad": 2, "nine": 3}[style]
        segs = bp.split_even(inner, n, mull) if n > 1 else [(0, inner)]
        pane_id = np.full((H, W), -1, dtype=np.int32)
        k = 0
        for (sy, ly) in segs:
            for (sx, lx) in segs:
                pane_id[fw + sy:fw + sy + ly, fw + sx:fw + sx + lx] = k
                k += 1
        core = np.zeros((H, W), dtype=bool)
        if style == "ring":
            # a stone boss in the middle of a glowing ring
            r = max(1, int(round(inner * 0.22)))
            c0 = W // 2 - r
            core[c0:c0 + 2 * r, c0:c0 + 2 * r] = True
            pane_id[core] = -1
        self._frame_geometry(ctx, fw)
        ctx.data.update(pane=pane_id, lamp_style=style, boss=core, bevel_w=max(1, int(round(ctx.scale / 3))),
                        core_shape=str(rng.choice(["round", "diamond", "cross"])))

    def _lamp_base(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        pane = ctx.data["pane"] >= 0
        c.fill("base", L.mid)
        c.set(pane, "glow", 1)
        c.emissive[pane] = True
        boss = ctx.data["boss"]
        c.set(boss, "base", L.up)

    def _lamp_material(self, ctx: GenContext) -> None:
        """Frame surface + the glow gradient of each pane."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        pane_id = ctx.data["pane"]
        pane = pane_id >= 0
        frame = ~pane
        off = bp.surface_offsets(self, ctx, "frame_surf", frame & (ctx.data["fd"] > 0), 1, 1.1,
                                 flat=0.82 - 0.35 * s.roughness)
        c.shift(frame & (off != 0), off, L.lo, L.up)
        c.set(pane, "glow", self._pane_levels(ctx))
        c.emissive[pane] = True

    def _pane_levels(self, ctx: GenContext) -> np.ndarray:
        """Glow levels of the panes: a pale body, a brighter centre, dimmer rims."""
        s = ctx.settings
        pane_id = ctx.data["pane"]
        pane = pane_id >= 0
        style = ctx.data["lamp_style"]
        n = ctx.canvas.length_of("glow")
        body = min(n - 2, 2)
        if style == "ring":
            depth = bp.region_depth(np.where(pane, 0, -1), False, 8)
            dmax = max(1, int(depth.max()))
            t = 1 - depth / dmax
        else:
            lab = np.where(pane, 0 if style == "single" else pane_id, -1)
            rx, ry, rad = bp.region_coords(lab, False)
            half = np.maximum(1.0, rad * 0.886)
            cheb = np.maximum(np.abs(rx), np.abs(ry)) / half
            eu = np.sqrt(rx * rx + ry * ry) / half
            t = 0.5 * cheb + 0.5 * eu / 1.25
        if s.roughness > 0.3:
            warp = self.height_field(ctx, "pane_warp", 1.3, 2) - 0.5
            t = t + warp * 0.3 * (s.roughness - 0.3)
        lv = np.full((ctx.h, ctx.w), body, dtype=np.int32)
        lv[t < 0.12 + 0.3 * s.glow] = min(n - 1, body + 1)
        big = style in ("single", "ring") or ctx.w >= 32
        if big:
            lv[t > 0.86 - 0.12 * s.glow] = max(0, body - 1)
        lv = np.clip(lv, 0, n - 1)
        lv = pa.remove_small_clusters(np.where(pane, lv, -1), 2, False, pane)
        return np.where(pane, lv, 0)

    def _lamp_large_detail(self, ctx: GenContext) -> None:
        """A shaped core in the middle of each pane (single / quad lamps)."""
        s, c = ctx.settings, ctx.canvas
        style = ctx.data["lamp_style"]
        if style in ("ring", "nine"):
            return
        pane_id = ctx.data["pane"]
        pane = pane_id >= 0
        rx, ry, rad = bp.region_coords(np.where(pane, 0 if style == "single" else pane_id, -1), False)
        half = np.maximum(1.0, rad * 0.886)
        shape = ctx.data["core_shape"]
        size = 0.3 + 0.25 * s.glow
        ax, ay = np.abs(rx) / half, np.abs(ry) / half
        if shape == "diamond":
            core = ax + ay < size * 1.4
        elif shape == "cross":
            core = ((ax < size * 0.35) & (ay < size * 1.2)) | ((ay < size * 0.35) & (ax < size * 1.2))
        else:
            core = ax * ax + ay * ay < size * size
        core &= pane
        if core.sum() < 2:
            return
        n = c.length_of("glow")
        ring = pa.outline(core, False) & pane
        c.set(ring & (c.level >= n - 1), "glow", n - 2)
        c.set(core, "glow", n - 1)
        bp.protect(ctx, core)

    def _lamp_medium_detail(self, ctx: GenContext) -> None:
        """Corner studs on the frame."""
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        fw = ctx.data["fw"]
        if fw < 2 or s.density < 0.25:
            return
        r = max(1, fw - 1)
        stud = bp.zeros(ctx)
        o = max(0, (fw - r) // 2)
        for (x, y) in ((o, o), (ctx.w - o - r, o), (o, ctx.h - o - r), (ctx.w - o - r, ctx.h - o - r)):
            stud[y:y + r, x:x + r] = True
        c.set(stud, "base", L.up)
        c.tag("stud")[stud] = True
        bp.protect(ctx, stud)

    def _lamp_small_detail(self, ctx: GenContext) -> None:
        """Bright motes drifting in the panes."""
        s, c = ctx.settings, ctx.canvas
        pane = ctx.data["pane"] >= 0
        n = c.length_of("glow")
        cand = pane & (c.level <= n - 3) & (c.ramp == 4)
        count = int(round((0.5 + 2 * s.noise) * ctx.scale ** 2 * 0.6))
        for cells in bp.clusters(ctx, "motes", count, (1, 2), mask=cand, min_dist=3):
            m = bp.point_mask(ctx, cells) & cand
            c.set(m, "glow", n - 2)
            bp.protect(ctx, m)

    def _lamp_cracks(self, ctx: GenContext) -> None:
        s, L, c = ctx.settings, ctx.data["L"], ctx.canvas
        cracks = ctx.data["cracks"]
        if cracks <= 0.01:
            return
        cr = self.crack_paths(ctx, cracks * 0.7, "lamp_cracks", length=(0.2, 0.5))
        cr = bp.four_connect(cr, None, ctx.wrap)
        pane = ctx.data["pane"] >= 0
        self._paint_crack(ctx, cr & ~pane & (ctx.data["fd"] > 0), L.dark)
        # cracked glass: a dimmer line through the panes
        pc = cr & pane
        c.set(pc, "glow", np.maximum(0, c.level - 1))

    def _lamp_highlights(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        self._slab_light(ctx, +1)
        pane = ctx.data["pane"] >= 0
        excl = c.tag("crack") | c.tag("stud")
        # light spill: frame pixels next to a pane catch its glow
        spill = pa.outline(pane, False) & ~pane & ~excl & (ctx.data["fd"] > 0)
        c.shift(spill, 1, L.dark, L.lit)
        stud = c.tag("stud")
        if stud.any():
            lit, _, _ = bp.raised_light(stud, False)
            c.set(stud & lit, "base", L.hi)
        boss = ctx.data["boss"]
        if boss.any():
            lit, _, _ = bp.raised_light(boss, False)
            c.set(boss & lit, "base", L.lit)
        # glass reflection in the top-left of each pane
        pid = ctx.data["pane"]
        n = c.length_of("glow")
        for k in range(int(pid.max()) + 1):
            ys, xs = np.nonzero(pid == k)
            if len(xs) < 9:
                continue
            x0, y0 = xs.min(), ys.min()
            ln = max(2, int(round((xs.max() - x0 + 1) / 4)))
            pts = [(x0 + 1 + i, y0 + ln - i) for i in range(ln)]
            m = bp.line_mask(ctx, pts) & (pid == k)
            c.set(m, "glow", n - 1)
            bp.protect(ctx, m)

    def _lamp_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        self._slab_light(ctx, -1)
        pane = ctx.data["pane"] >= 0
        stud = c.tag("stud")
        if stud.any():
            _, dark, drop = bp.raised_light(stud, False)
            c.set(stud & dark, "base", L.lo)
            c.shift(drop & ~pane & ~stud, -1, L.dark)
        boss = ctx.data["boss"]
        if boss.any():
            _, dark, _ = bp.raised_light(boss, False)
            c.set(boss & dark, "base", L.lo)
        # panes sit behind the frame: their top/left rim is shadowed
        e = bp.mask_edges(pane, False)
        rim = pane & (e["top"] | e["left"])
        c.set(rim, "glow", np.maximum(0, c.level - 1))

    def _lamp_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if s.mineral <= 0.02:
            return
        stud = c.tag("stud")
        if stud.any():
            self._inlay(ctx, stud, "accent")
        if s.mineral > 0.45:
            # accent trim ring around the panes
            pane = ctx.data["pane"] >= 0
            trim = pa.outline(pane, False, diagonal=True) & ~pane & (ctx.data["fd"] > 0) & ~stud
            L = ctx.data["L"]
            lv = bp.base_to_role(c.level, L.K, c.length_of("accent"), 1, c.length_of("accent") - 1)
            c.set(trim, "accent", lv)
            bp.protect(ctx, trim)


# ====================================================================== motifs
# Hand-made 10x10 glyphs, mirror-symmetric about the vertical axis.
#   '#' carved stroke   '+' carved/raised area   'o' gem socket
#   '1' / '2' stroke drawn only at detail level >= 1 / >= 2
# Larger textures scale them by an integer factor (chunky, never smoothed).

_GLYPHS: dict[str, list[str]] = {
    "trident": [
        "....##....",
        "#..####..#",
        "#...##...#",
        "#...##...#",
        "##..##..##",
        ".########.",
        "....oo....",
        "....##....",
        "...1..1...",
        "....##....",
    ],
    "shell": [
        ".++.++.++.",
        "++++++++++",
        "+#+#++#+#+",
        "+#+#++#+#+",
        "+#++++++#+",
        ".+#++++#+.",
        "..+#++#+..",
        "...+##+...",
        "..++oo++..",
        "..+2..2+..",
    ],
    "eye": [
        ".2..22..2.",
        "..........",
        "..######..",
        ".#..##..#.",
        "#..#oo#..#",
        "#..#oo#..#",
        ".#..##..#.",
        "..######..",
        "..........",
        "....11....",
    ],
    "waves": [
        "..........",
        ".##....##.",
        "#..#..#..#",
        "....##....",
        "..........",
        ".##....##.",
        "#..#..#..#",
        "....##....",
        "..........",
        "...1oo1...",
    ],
    "star": [
        "....++....",
        "....++....",
        "1...++...1",
        ".1.++++.1.",
        "++++oo++++",
        "++++oo++++",
        ".1.++++.1.",
        "1...++...1",
        "....++....",
        "....++....",
    ],
    "jelly": [
        "...####...",
        ".##++++##.",
        "#++++++++#",
        "#+o++++o+#",
        "##########",
        ".#..##..#.",
        ".#..##..#.",
        "#..#..#..#",
        "#..#..#..#",
        ".1......1.",
    ],
    "anchor": [
        "....##....",
        "...#..#...",
        "....##....",
        ".##.oo.##.",
        "....##....",
        "#...##...#",
        "##..##..##",
        ".#..##..#.",
        "..#.##.#..",
        "...####...",
    ],
    "rune": [
        "1...##...1",
        "...#..#...",
        "..#.##.#..",
        ".#.#..#.#.",
        "#.#.oo.#.#",
        "#.#.oo.#.#",
        ".#.#..#.#.",
        "..#.##.#..",
        "...#..#...",
        "1...##...1",
    ],
}


# 4x4 fallback glyphs for textures too small for a 10x10 glyph (8x8 blocks)
def _mini_motif(name: str) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    rows = {"trident": ["#..#", "####", ".oo.", ".##."],
            "shell": ["+##+", "#++#", ".++.", ".oo."],
            "star": [".++.", "+oo+", "+oo+", ".++."]}.get(name, [".##.", "#oo#", "#oo#", ".##."])
    g = np.array([list(r) for r in rows])
    return g == "#", g == "+", g == "o"


def _place(m: np.ndarray, H: int, W: int, off: int) -> np.ndarray:
    out = np.zeros((H, W), dtype=bool)
    h, w = m.shape
    h2, w2 = min(h, H - off), min(w, W - off)
    if h2 > 0 and w2 > 0:
        out[off:off + h2, off:off + w2] = m[:h2, :w2]
    return out


def _motif(name: str, k: int, detail: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """(stroke, fill, gem) masks of a glyph scaled by ``k`` (size 10k x 10k)."""
    rows = _GLYPHS.get(name, _GLYPHS["rune"])
    g = np.array([list(r) for r in rows])
    stroke = (g == "#") | ((g == "1") & (detail >= 1)) | ((g == "2") & (detail >= 2))
    fill = g == "+"
    gem = g == "o"
    up = lambda m: np.kron(m, np.ones((k, k), dtype=bool)).astype(bool)
    return up(stroke), up(fill), up(gem)
