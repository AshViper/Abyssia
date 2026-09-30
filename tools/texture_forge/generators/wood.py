"""Wood: planks, logs, stripped logs, doors and trapdoors.

Variants
--------
planks            horizontal boards in staggered courses: a dark seam under each
                  course, butt joints with a lit board end, long grain streaks
                  and a few knots
log               bark side: vertical ridges split by dark furrows and broken
                  into plates; tiles in both directions
log_top           log end: bark rim around concentric growth rings and a pith
stripped_log      bare wood side: fine vertical grain, no bark
stripped_log_top  log end without the bark rim
door_top          plank door; both halves are planned together (independent of
door_bottom       the variant) so they meet: frame, vertical boards, rails, a
                  round porthole window in the top half, hinges and a handle
trapdoor          framed hatch of boards with a porthole and corner rivets
door_item         the same door drawn as a small item icon

Roles: ``base`` = wood, ``secondary`` = bark (derived from the wood colour
unless ``secondary_color`` is set), ``accent`` = metal fittings, ``glow`` =
optional bioluminescent specks in furrows and seams (glow slider).

Sliders: density = knots / rivets, roughness = bark depth and grain contrast,
cracks = splits along the grain, mineral = nails on planks and lichen in bark
furrows (accent), noise = grain dashes.
"""
from __future__ import annotations

import math

import numpy as np

from core import pixel_art as pa
from core.layers import GenContext
from core.palette import RAMP_ANCHORS
from core.seed import derive_rng
from core.settings import TextureSettings

from . import _block_patterns as bp
from .base import BaseGenerator
from .plant import _derived_hex

VARIANT_NAMES = ("planks", "log", "log_top", "stripped_log", "stripped_log_top", "door_top", "door_bottom",
                 "trapdoor", "door_item")

# theme -> wood ramp when no base colour is set
_WOOD_BASE = {"ancient": "ancient_wood", "organic": "driftwood", "bioluminescent": "ancient_wood",
              "deep_ocean": "ancient_wood", "cold": "driftwood"}


def _board_edges(n: int, frame: int, boards: int = 3) -> list[int]:
    """Columns of the seams between ``boards`` equal vertical boards inside a frame (plus both ends)."""
    inner = n - 2 * frame - (boards - 1)
    bw = inner / boards
    return [frame] + [frame + int(round(bw * (i + 1))) + i for i in range(boards - 1)] + [n - frame]


class WoodGenerator(BaseGenerator):
    category = "wood"

    # ------------------------------------------------------------ setup
    def body_levels(self, s: TextureSettings, a) -> int:
        k = super().body_levels(s, a)
        if s.levels == 0 and (not s.color_limit or s.color_limit >= 12):
            k = max(4, k)
        return k

    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        wood = s.base_color or _WOOD_BASE.get(s.palette, "driftwood")
        mid = wood if wood.startswith("#") else RAMP_ANCHORS.get(wood, RAMP_ANCHORS["driftwood"])[2]
        roles = {"base": wood, "secondary": _derived_hex(mid, -0.12, 0.9, 20.0)}
        if not s.accent:
            roles["accent"] = "copper"
        return roles

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": k + 2, "accent": 5, "accent2": 4, "glow": 4}

    def prepare(self, ctx: GenContext) -> None:
        v = ctx.settings.variant if ctx.settings.variant in VARIANT_NAMES else "planks"
        ctx.data["variant"] = v
        ctx.data["L"] = bp.levels(ctx)
        plan = {"planks": self._plan_planks, "log": self._plan_log, "stripped_log": self._plan_stripped,
                "log_top": self._plan_rings, "stripped_log_top": self._plan_rings,
                "door_top": self._plan_door, "door_bottom": self._plan_door, "trapdoor": self._plan_trapdoor,
                "door_item": self._plan_door_item}[v]
        plan(ctx)

    def _run(self, ctx: GenContext, layer: str) -> None:
        v = ctx.data["variant"]
        family = {"log_top": "rings", "stripped_log_top": "rings", "door_top": "panel", "door_bottom": "panel",
                  "trapdoor": "panel", "door_item": "item"}.get(v, v)
        fn = getattr(self, f"_{family}_{layer}", None)
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

    # ------------------------------------------------------------ shared
    def _grain(self, ctx: GenContext, key: str, vertical: bool, freq: float = 1.0) -> np.ndarray:
        """Long streaks along the grain (periodic, so tileable)."""
        aspect = (2.6, 0.28) if vertical else (0.28, 2.6)
        return self.height_field(ctx, key, freq, 2, "value", aspect=aspect, warp=0.03)

    @staticmethod
    def _bands(field: np.ndarray, mask: np.ndarray, lo_q: float, hi_q: float) -> np.ndarray:
        """-1 / 0 / +1 streak offsets from a grain field (quantiles inside ``mask``)."""
        vals = field[mask] if mask.any() else field.ravel()
        lo, hi = np.quantile(vals, lo_q), np.quantile(vals, 1 - hi_q)
        return (field >= hi).astype(np.int32) - (field <= lo).astype(np.int32)

    def _dashes(self, ctx: GenContext, key: str, mask: np.ndarray, count: int, vertical: bool,
                length: tuple[int, int] = (2, 3)) -> tuple[np.ndarray, np.ndarray]:
        """Short grain dashes inside ``mask``: (lighter, darker) masks."""
        rng = ctx.rng(key)
        up = np.zeros((ctx.h, ctx.w), dtype=bool)
        dn = np.zeros((ctx.h, ctx.w), dtype=bool)
        ys, xs = np.nonzero(mask)
        if len(xs) == 0:
            return up, dn
        for _ in range(count):
            i = int(rng.integers(len(xs)))
            n = int(rng.integers(length[0], length[1] + 1))
            tgt = up if rng.random() < 0.45 else dn
            for k in range(n):
                x = (xs[i] + (0 if vertical else k)) % ctx.w
                y = (ys[i] + (k if vertical else 0)) % ctx.h
                if mask[y, x]:
                    tgt[y, x] = True
        return up, dn

    def _glow_in(self, ctx: GenContext, where: np.ndarray, amount: float) -> None:
        if ctx.settings.glow > 0.02 and where.any():
            self.glow_points(ctx, ctx.settings.glow * amount, where)

    # ================================================================ planks

    def _plan_planks(self, ctx: GenContext) -> None:
        W, H = ctx.w, ctx.h
        rng = ctx.rng("layout")
        bh = bp.nearest_divisor(H, 4 * ctx.scale, lo=min(3, H))
        rows = max(1, H // bh)
        board = np.zeros((H, W), dtype=np.int32)
        seam = np.zeros((H, W), dtype=bool)
        joint = np.zeros((H, W), dtype=bool)
        top = np.zeros((H, W), dtype=bool)
        tones = []
        prev: list[int] = []
        bid = 0
        gap = max(3, ctx.px(3))
        for r in range(rows):
            y0 = r * bh
            y1 = y0 + bh
            seam[y1 - 1, :] = True
            top[y0, :] = True
            n = max(1, int(round((1 if rng.random() < 0.55 else 2) * max(1.0, ctx.scale) ** 0.5)))
            xs: list[int] = []
            for _ in range(40 * n):
                if len(xs) >= n:
                    break
                x = int(rng.integers(0, W))
                far = all(min(abs(x - p), W - abs(x - p)) >= gap for p in prev + xs)
                spaced = all(min(abs(x - p), W - abs(x - p)) >= max(gap, W // (n + 1)) for p in xs)
                if far and spaced:
                    xs.append(x)
            if not xs:
                xs = [int(rng.integers(0, W))]
            xs.sort()
            for x in xs:
                joint[y0:y1 - 1, x] = True
            cols = np.arange(W)
            seg = np.searchsorted(np.array(xs), cols, side="right") % len(xs)
            board[y0:y1, :] = bid + seg[None, :]
            for _ in xs:
                tones.append(int(rng.choice([-1, 0, 0, 0, 1])))
            bid += len(xs)
            prev = xs
        ctx.data.update(board=board, seam=seam, joint=joint, top=top, tones=np.array(tones, dtype=np.int32),
                        bh=bh, joints_x=None)
        ctx.data["grain"] = self._grain(ctx, "grain", vertical=False)

    def _planks_base(self, ctx: GenContext) -> None:
        ctx.canvas.fill("base", ctx.data["L"].mid)

    def _planks_material(self, ctx: GenContext) -> None:
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        body = ~ctx.data["seam"]
        off = self._bands(ctx.data["grain"], body, 0.18 + 0.1 * s.roughness, 0.14 + 0.08 * s.roughness)
        lv = L.mid + off + ctx.data["tones"][ctx.data["board"]]
        lv = np.clip(lv, L.dark + 1, L.lit - 1)
        lv = self.cleanup(ctx, lv, 2)
        c.set(np.ones_like(body), "base", lv)
        ctx.data["levels"] = lv

    def _planks_large_detail(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        seam, joint = ctx.data["seam"], ctx.data["joint"]
        c.set(seam, "base", L.deep)
        soft = seam & bp.noise_keep(self, ctx, "seam_soft", 0.25)
        c.set(soft, "base", L.dark)
        c.set(joint & ~seam, "base", L.deep)
        bp.protect(ctx, seam | joint)

    def _planks_medium_detail(self, ctx: GenContext) -> None:
        """Knots: a dark eye with a lit rim above it."""
        s, c, L = ctx.settings, ctx.canvas, ctx.data["L"]
        n = int(round(s.density * 1.6 * ctx.scale ** 2))
        if n <= 0:
            return
        free = ~(pa.dilate(ctx.data["seam"] | ctx.data["joint"], ctx.wrap, diagonal=True) | ctx.data["top"])
        rng = ctx.rng("knots")
        ys, xs = np.nonzero(free)
        for _ in range(n):
            if len(xs) == 0:
                break
            i = int(rng.integers(len(xs)))
            x, y = int(xs[i]), int(ys[i])
            eye = bp.point_mask(ctx, [(x, y), (x + 1, y)] if ctx.scale < 2 else
                                [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)])
            c.set(eye, "base", L.dark)
            rim = pa.neighbour(eye, 0, 1, ctx.wrap, fill=False) & ~eye & free
            c.shift(rim, 1, ceil=L.lit)
            bp.protect(ctx, eye | rim)

    def _planks_small_detail(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        body = ~(ctx.data["seam"] | ctx.data["joint"] | c.tag("protect"))
        up, dn = self._dashes(ctx, "dashes", body, int(round(s.noise * 7 * ctx.scale ** 2)), False)
        c.shift(up, 1, ceil=ctx.data["L"].lit - 1)
        c.shift(dn, -1, floor=ctx.data["L"].dark + 1)

    def _planks_cracks(self, ctx: GenContext) -> None:
        s, c, L = ctx.settings, ctx.canvas, ctx.data["L"]
        if s.cracks <= 0.2:
            return
        body = ~(pa.dilate(ctx.data["seam"], ctx.wrap) | ctx.data["joint"] | ctx.data["top"])
        up, dn = self._dashes(ctx, "splits", body, int(round((s.cracks - 0.2) * 5 * ctx.scale ** 1.5)), False,
                              (3, max(4, ctx.px(5))))
        split = up | dn
        c.set(split, "base", L.dark)
        c.tag("crack")[split] = True
        bp.protect(ctx, split)

    def _planks_highlights(self, ctx: GenContext) -> None:
        c, L = ctx.canvas, ctx.data["L"]
        joint = ctx.data["joint"]
        lit_top = ctx.data["top"] & ~joint & bp.noise_keep(self, ctx, "top_lit", 0.65)
        c.shift(lit_top & ~c.tag("crack"), 1, ceil=L.lit)
        board_end = pa.neighbour(joint, -1, 0, ctx.wrap, fill=False) & ~joint & ~ctx.data["seam"]
        c.shift(board_end, 1, ceil=L.lit)
        bp.protect(ctx, board_end)

    def _planks_shadows(self, ctx: GenContext) -> None:
        c, L = ctx.canvas, ctx.data["L"]
        seam, joint = ctx.data["seam"], ctx.data["joint"]
        above = pa.neighbour(seam, 0, 1, ctx.wrap, fill=False) & ~seam & ~joint
        c.shift(above & bp.noise_keep(self, ctx, "under_lit", 0.5), -1, floor=L.dark)
        before = pa.neighbour(joint, 1, 0, ctx.wrap, fill=False) & ~joint & ~seam
        c.shift(before, -1, floor=L.dark)

    def _planks_accent(self, ctx: GenContext) -> None:
        s, c = ctx.settings, ctx.canvas
        if s.mineral > 0.05:
            # nail heads beside the butt joints
            rng = ctx.rng("nails")
            n = c.length_of("accent")
            joint, top = ctx.data["joint"], ctx.data["top"]
            heads = np.zeros_like(joint)
            for y, x in zip(*np.nonzero(joint & pa.neighbour(top, 0, -1, ctx.wrap, fill=False))):
                if rng.random() < 0.35 + 0.6 * s.mineral:
                    for dx in (-1, 1):
                        heads[y, (x + dx) % ctx.w] = True
            c.set(heads, "accent", n - 2)
            bp.protect(ctx, heads)
        self._glow_in(ctx, ctx.data["seam"], 0.4)

    # ================================================================ bark

    def _plan_log(self, ctx: GenContext) -> None:
        """Bark: meandering vertical furrows (periodic in y) with ridges between them."""
        s = ctx.settings
        W, H = ctx.w, ctx.h
        rng = ctx.rng("furrows")
        spacing = (3.1 + 1.3 * (1 - s.density)) * ctx.scale
        n = max(2, int(round(W / spacing)))
        base = (np.arange(n) + rng.uniform(0, 1)) * W / n + rng.uniform(-0.45, 0.45, n) * ctx.scale
        y = np.arange(H) + 0.5
        paths = []
        for i in range(n):
            waves = int(rng.integers(1, 3))
            amp = rng.uniform(0.3, 1.0) * ctx.scale * (0.5 + s.organic + 0.5 * s.roughness)
            paths.append(base[i] + amp * np.sin(2 * np.pi * waves * y / H + rng.uniform(0, 2 * np.pi)))
        cols = np.floor(np.array(paths)).astype(int) % W          # (n, H)
        rows = np.arange(H)
        furrow = np.zeros((H, W), dtype=bool)
        wide = self.height_field(ctx, "furrow_wide", 1.3, 1) > 0.78 - 0.25 * s.roughness
        for i in range(n):
            furrow[rows, cols[i]] = True
            extra = wide[rows, cols[i]]
            furrow[rows[extra], (cols[i][extra] + 1) % W] = True
        # position across each ridge: 0 just right of a furrow .. 1 just left of the next
        dl = np.zeros((H, W))
        dr = np.zeros((H, W))
        for yy in range(H):
            f = np.nonzero(furrow[yy])[0]
            for x in range(W):
                a = (x - f) % W
                b = (f - x) % W
                dl[yy, x] = a[a > 0].min() if (a > 0).any() else W
                dr[yy, x] = b[b > 0].min() if (b > 0).any() else W
        t = np.clip((dl - 1) / np.maximum(1, dl + dr - 2), 0, 1)
        # a few horizontal breaks split the ridges into plates
        breaks = np.zeros((H, W), dtype=bool)
        ys, xs = np.nonzero(~pa.dilate(furrow, ctx.wrap) & (dl + dr >= 4))
        for _ in range(int(round((1 + 2.5 * s.roughness) * ctx.scale ** 1.5))):
            if len(xs) == 0:
                break
            i = int(rng.integers(len(xs)))
            yy, x = int(ys[i]), int(xs[i])
            while not furrow[yy, (x - 1) % W]:
                x = (x - 1) % W
            while not furrow[yy, x]:
                breaks[yy, x] = True
                x = (x + 1) % W
        ctx.data.update(furrow=furrow, breaks=breaks, t=t, grain=self._grain(ctx, "bark_grain", True, 1.3))

    def _log_base(self, ctx: GenContext) -> None:
        ctx.canvas.fill("secondary", ctx.data["L"].mid)

    def _log_material(self, ctx: GenContext) -> None:
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        t = ctx.data["t"]
        ridge = ~ctx.data["furrow"]
        lv = np.full(t.shape, L.mid, dtype=np.int32)
        lv = np.where(t < 0.3, L.up, np.where(t > 0.7, L.lo, lv))
        lv += self._bands(ctx.data["grain"], ridge, 0.12 + 0.08 * s.roughness, 0.1)
        lv = np.clip(lv, L.dark + 1, L.lit - 1)
        lv = self.cleanup(ctx, lv, 2)
        c.set(np.ones_like(ridge), "secondary", lv)

    def _log_large_detail(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        furrow, breaks = ctx.data["furrow"], ctx.data["breaks"]
        c.set(furrow, "secondary", L.deep)
        c.set(furrow & bp.noise_keep(self, ctx, "furrow_soft", 0.25), "secondary", L.dark)
        c.set(breaks, "secondary", L.dark)
        bp.protect(ctx, furrow | breaks)

    def _log_small_detail(self, ctx: GenContext) -> None:
        s, c, L = ctx.settings, ctx.canvas, ctx.data["L"]
        ridge = ~(ctx.data["furrow"] | ctx.data["breaks"])
        up, dn = self._dashes(ctx, "bark_dashes", ridge, int(round(s.noise * 4 * ctx.scale ** 2)), True)
        c.shift(up, 1, ceil=L.lit - 1)
        c.shift(dn, -1, floor=L.dark + 1)

    def _log_highlights(self, ctx: GenContext) -> None:
        c, L = ctx.canvas, ctx.data["L"]
        furrow, breaks = ctx.data["furrow"], ctx.data["breaks"]
        lit = pa.neighbour(furrow, -1, 0, ctx.wrap, fill=False) & ~furrow & ~breaks
        lit |= pa.neighbour(breaks, 0, -1, ctx.wrap, fill=False) & ~furrow & ~breaks
        c.shift(lit & bp.noise_keep(self, ctx, "ridge_lit", 0.55 + 0.35 * ctx.settings.roughness), 1, ceil=L.lit)

    def _log_shadows(self, ctx: GenContext) -> None:
        c, L = ctx.canvas, ctx.data["L"]
        dark = ctx.data["furrow"] | ctx.data["breaks"]
        sh = pa.neighbour(ctx.data["furrow"], 1, 0, ctx.wrap, fill=False) & ~dark
        sh |= pa.neighbour(ctx.data["breaks"], 0, 1, ctx.wrap, fill=False) & ~dark
        c.shift(sh & bp.noise_keep(self, ctx, "ridge_dark", 0.6), -1, floor=L.dark)

    def _log_accent(self, ctx: GenContext) -> None:
        s = ctx.settings
        furrow = ctx.data["furrow"]
        if s.mineral > 0.05:
            self.mineral_grains(ctx, s.mineral * 0.8, "accent", "lichen", prefer=pa.dilate(furrow, ctx.wrap),
                                size_range=(1, 2))
        self._glow_in(ctx, furrow, 0.35)

    # ========================================================= stripped side

    def _plan_stripped(self, ctx: GenContext) -> None:
        s = ctx.settings
        ctx.data["grain"] = self._grain(ctx, "grain", vertical=True, freq=1.2)
        rng = ctx.rng("streaks")
        streak = np.zeros((ctx.h, ctx.w), dtype=bool)
        for _ in range(int(round((1 + 2 * s.roughness) * ctx.scale))):
            x = int(rng.integers(ctx.w))
            y = int(rng.integers(ctx.h))
            for k in range(int(rng.integers(ctx.px(4), ctx.px(9) + 1))):
                streak[(y + k) % ctx.h, x] = True
        ctx.data["streak"] = streak

    def _stripped_log_base(self, ctx: GenContext) -> None:
        ctx.canvas.fill("base", ctx.data["L"].mid)

    def _stripped_log_material(self, ctx: GenContext) -> None:
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        mask = np.ones((ctx.h, ctx.w), dtype=bool)
        off = self._bands(ctx.data["grain"], mask, 0.2 + 0.1 * s.roughness, 0.22)
        lv = np.clip(L.mid + off, L.dark + 1, L.lit - 1)
        lv = self.cleanup(ctx, lv, 2)
        c.set(mask, "base", lv)

    def _stripped_log_large_detail(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.set(ctx.data["streak"], "base", L.lo - 1 if L.lo - 1 > L.deep else L.dark)
        bp.protect(ctx, ctx.data["streak"])

    def _stripped_log_small_detail(self, ctx: GenContext) -> None:
        s, c, L = ctx.settings, ctx.canvas, ctx.data["L"]
        up, dn = self._dashes(ctx, "dashes", ~ctx.data["streak"], int(round(s.noise * 6 * ctx.scale ** 2)), True)
        c.shift(up, 1, ceil=L.lit - 1)
        c.shift(dn, -1, floor=L.dark + 1)

    def _stripped_log_highlights(self, ctx: GenContext) -> None:
        c, L = ctx.canvas, ctx.data["L"]
        streak = ctx.data["streak"]
        lit = pa.neighbour(streak, -1, 0, ctx.wrap, fill=False) & ~streak
        c.shift(lit & bp.noise_keep(self, ctx, "streak_lit", 0.6), 1, ceil=L.lit)

    def _stripped_log_accent(self, ctx: GenContext) -> None:
        self._glow_in(ctx, ctx.data["streak"], 0.3)

    # ================================================================ ends

    def _plan_rings(self, ctx: GenContext) -> None:
        s = ctx.settings
        N = ctx.w
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w].astype(np.float64)
        c0 = (N - 1) / 2.0
        dx, dy = xx - c0, yy - c0
        cheb = np.maximum(np.abs(dx), np.abs(dy))
        eu = np.hypot(dx, dy)
        wob = (self.height_field(ctx, "ring_wobble", 0.9, 1) - 0.5) * (0.9 + 0.8 * s.organic) * ctx.scale
        d = 0.55 * cheb + 0.45 * eu + wob
        rim_w = ctx.px(1)
        rim = cheb >= N / 2.0 - rim_w
        bulge = (cheb >= N / 2.0 - 2 * rim_w) & (self.height_field(ctx, "rim_bulge", 1.4, 1) > 0.72 - 0.2 * s.roughness)
        rim |= bulge
        spacing = 1.55 * ctx.scale * (1.15 - 0.3 * s.density)
        ring_line = (np.mod(d / spacing, 1.0) < 0.38) & ~rim
        pith = eu <= max(0.8, 0.9 * ctx.scale)
        sap = (cheb >= N / 2.0 - 2 * rim_w - ctx.px(1)) & ~rim
        crack = np.zeros_like(rim)
        if s.cracks > 0.25:
            rng = ctx.rng("check")
            ang = rng.uniform(0, 2 * math.pi)
            r1 = (N / 2.0 - 2 * rim_w) * (0.5 + 0.5 * s.cracks)
            pts = pa.line_points(int(round(c0)), int(round(c0)), int(round(c0 + math.cos(ang) * r1)),
                                 int(round(c0 + math.sin(ang) * r1)))
            for x, y in pts[1:]:
                if 0 <= x < N and 0 <= y < N:
                    crack[y, x] = True
            crack &= ~rim
        ctx.data.update(dx=dx, dy=dy, rim=rim, ring_line=ring_line, pith=pith, sap=sap, crack=crack,
                        bark=ctx.data["variant"] == "log_top")

    def _rings_base(self, ctx: GenContext) -> None:
        ctx.canvas.fill("base", ctx.data["L"].up)

    def _rings_material(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        c.set(ctx.data["ring_line"], "base", L.lo)
        c.set(ctx.data["sap"] & ~ctx.data["ring_line"], "base", min(L.lit - 1, L.up + 1))
        c.set(ctx.data["pith"], "base", L.dark)
        bp.protect(ctx, ctx.data["ring_line"] | ctx.data["pith"])

    def _rings_large_detail(self, ctx: GenContext) -> None:
        """The rim: bark on a log end, a darker weathered edge on a stripped one."""
        L, c = ctx.data["L"], ctx.canvas
        rim = ctx.data["rim"]
        role = "secondary" if ctx.data["bark"] else "base"
        lv = np.full(rim.shape, L.mid if ctx.data["bark"] else L.lo, dtype=np.int32)
        f = self.height_field(ctx, "rim_tone", 1.6, 1)
        lv = lv + (f > 0.62).astype(np.int32) - (f < 0.3).astype(np.int32)
        c.set(rim, role, np.clip(lv, L.dark, L.lit - 1))
        bp.protect(ctx, rim)

    def _rings_small_detail(self, ctx: GenContext) -> None:
        """Growth rings are lighter on the top-left of the end, darker bottom-right."""
        L, c = ctx.data["L"], ctx.canvas
        dx, dy = ctx.data["dx"], ctx.data["dy"]
        inner = ~ctx.data["rim"] & ~ctx.data["pith"]
        side = (dx + dy) / max(1.0, ctx.w)
        c.shift(inner & (side < -0.42), 1, ceil=L.lit - 1)
        c.shift(inner & (side > 0.45), -1, floor=L.dark + 1)

    def _rings_cracks(self, ctx: GenContext) -> None:
        crack = ctx.data["crack"]
        if crack.any():
            ctx.canvas.set(crack, "base", ctx.data["L"].deep)
            ctx.canvas.tag("crack")[crack] = True
            bp.protect(ctx, crack)

    def _rings_highlights(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        rim = ctx.data["rim"]
        N = ctx.w
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        lit = rim & ((yy == 0) | (xx == 0)) & ~((yy == N - 1) | (xx == N - 1))
        c.shift(lit & bp.noise_keep(self, ctx, "rim_lit", 0.7), 1, ceil=L.lit)

    def _rings_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        rim = ctx.data["rim"]
        N = ctx.w
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        c.shift(rim & ((yy == N - 1) | (xx == N - 1)), -1, floor=L.deep)
        # the wood just inside the rim sits in its shadow
        inner = pa.dilate(rim, False) & ~rim
        c.shift(inner & ((yy >= N - 3) | (xx >= N - 3)) & bp.noise_keep(self, ctx, "rim_sh", 0.5), -1,
                floor=L.dark)

    def _rings_accent(self, ctx: GenContext) -> None:
        if ctx.data["bark"] and ctx.settings.mineral > 0.05:
            self.mineral_grains(ctx, ctx.settings.mineral * 0.5, "accent", "lichen", prefer=ctx.data["rim"],
                                size_range=(1, 2))
        self._glow_in(ctx, ctx.data["ring_line"], 0.15)

    # ================================================================ doors

    def _door_rng(self, ctx: GenContext, key: str) -> np.random.Generator:
        # both door halves share one plan: keyed on the seed, not on the variant
        return derive_rng(ctx.settings.seed, "wood", "door", key)

    def _plan_door(self, ctx: GenContext) -> None:
        s = ctx.settings
        N = ctx.w
        H2 = 2 * N
        f = ctx.px(1)
        yy, xx = np.mgrid[0:H2, 0:N].astype(np.float64)
        region = np.zeros((H2, N), dtype=np.int32)          # 0 board, 1 frame, 2 rail
        frame = (xx < f) | (xx >= N - f) | (yy < f) | (yy >= H2 - f)
        rails = ((yy >= N - f) & (yy < N + f)) | ((yy >= H2 - ctx.px(5)) & (yy < H2 - ctx.px(3)))
        region[rails] = 2
        region[frame] = 1
        # vertical boards: seams split the inside into three
        seams = np.zeros((H2, N), dtype=bool)
        edges = _board_edges(N, f)
        for x in edges[1:-1]:
            seams[:, x] = True
        seams &= region == 0
        # porthole in the top half
        cx, cy = N / 2.0, N * 0.47
        r = 3.5 * ctx.scale
        dist = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        ring = (dist <= r) & (dist > r - max(1.05, 1.2 * ctx.scale))
        glass = dist <= r - max(1.05, 1.2 * ctx.scale)
        ang = np.arctan2(yy + 0.5 - cy, xx + 0.5 - cx)
        fittings = np.zeros((H2, N), dtype=bool)
        hinge_rows = [ctx.px(3), H2 - ctx.px(6)]
        for hy in hinge_rows:
            fittings[hy:hy + ctx.px(2), 0:ctx.px(3)] = True
        hx = N - ctx.px(4)
        handle = np.zeros((H2, N), dtype=bool)
        handle[N + ctx.px(3):N + ctx.px(6), hx:hx + ctx.px(1)] = True
        rng = self._door_rng(ctx, "tones")
        tone = np.zeros((H2, N), dtype=np.int32)
        for a, b in zip([0] + edges[1:-1], edges[1:-1] + [N]):
            tone[:, a:b] = int(rng.choice([-1, 0, 0, 1]))
        g_rng = self._door_rng(ctx, "grain")
        from core import noise
        gv = noise.fbm(N, H2, g_rng, max(2.0, 2.0 * ctx.scale), 2, 0.5, "value", (2.6, 0.28), 0.03)
        gh = noise.fbm(N, H2, g_rng, max(2.0, 2.0 * ctx.scale), 2, 0.5, "value", (0.28, 2.6), 0.03)
        rows = slice(0, N) if s.variant == "door_top" else slice(N, H2)
        ctx.data["panel"] = dict(
            region=region[rows], seams=seams[rows], ring=ring[rows], glass=glass[rows], ang=ang[rows],
            fittings=(fittings | handle)[rows], rails=(region == 2)[rows], frame=frame[rows],
            tone=tone[rows], gv=gv[rows], gh=gh[rows], rivets=np.zeros((N, N), dtype=bool),
            y_off=0 if s.variant == "door_top" else N, height=H2)

    def _plan_trapdoor(self, ctx: GenContext) -> None:
        N = ctx.w
        f = ctx.px(1)
        yy, xx = np.mgrid[0:N, 0:N].astype(np.float64)
        region = np.zeros((N, N), dtype=np.int32)
        frame = (xx < 2 * f) | (xx >= N - 2 * f) | (yy < 2 * f) | (yy >= N - 2 * f)
        region[frame] = 1
        seams = np.zeros((N, N), dtype=bool)
        edges = _board_edges(N, 2 * f)
        for x in edges[1:-1]:
            seams[:, x] = True
        seams &= region == 0
        cx = cy = N / 2.0
        r = 3.1 * ctx.scale
        dist = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        ring = (dist <= r) & (dist > r - max(1.05, 1.2 * ctx.scale))
        glass = dist <= r - max(1.05, 1.2 * ctx.scale)
        ang = np.arctan2(yy + 0.5 - cy, xx + 0.5 - cx)
        rivets = np.zeros((N, N), dtype=bool)
        if ctx.settings.density > 0.2:
            for x in (f, N - f - 1):
                for y in (f, N - f - 1):
                    rivets[y, x] = True
        rng = ctx.rng("tones")
        tone = np.zeros((N, N), dtype=np.int32)
        for a, b in zip([0] + edges[1:-1], edges[1:-1] + [N]):
            tone[:, a:b] = int(rng.choice([-1, 0, 0, 1]))
        ctx.data["panel"] = dict(
            region=region, seams=seams, ring=ring, glass=glass, ang=ang, fittings=np.zeros((N, N), dtype=bool),
            rails=np.zeros((N, N), dtype=bool), frame=frame, tone=tone,
            gv=self._grain(ctx, "grain_v", True), gh=self._grain(ctx, "grain_h", False), rivets=rivets,
            y_off=0, height=N)

    def _panel_base(self, ctx: GenContext) -> None:
        ctx.canvas.fill("base", ctx.data["L"].mid)

    def _panel_material(self, ctx: GenContext) -> None:
        L, c, s = ctx.data["L"], ctx.canvas, ctx.settings
        P = ctx.data["panel"]
        boards = P["region"] == 0
        rails = P["region"] == 2
        lo_q, hi_q = 0.18 + 0.1 * s.roughness, 0.16
        off = np.where(rails, self._bands(P["gh"], rails, lo_q, hi_q), self._bands(P["gv"], boards, lo_q, hi_q))
        lv = L.mid + off + np.where(boards, P["tone"], 0)
        lv = np.clip(lv, L.dark + 1, L.lit - 1)
        c.set(np.ones_like(boards), "base", lv)

    def _panel_large_detail(self, ctx: GenContext) -> None:
        """The frame: a dark border, one step lighter along the outer top / left edge."""
        L, c = ctx.data["L"], ctx.canvas
        P = ctx.data["panel"]
        frame = P["frame"]
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        outer_lit = frame & (((yy + P["y_off"]) == 0) | (xx == 0))
        c.set(frame, "base", L.dark)
        c.set(outer_lit, "base", L.lo)
        c.set(P["seams"], "base", L.dark)
        # rails and frame stand proud of the boards: shadow under / right of them
        proud = P["region"] != 0
        drop = (pa.neighbour(proud, 0, -1, False, fill=False) | pa.neighbour(proud, -1, 0, False, fill=False)) & ~proud
        c.shift(drop & ~P["seams"], -1, floor=L.dark)
        bp.protect(ctx, P["seams"] | drop | frame)

    def _panel_small_detail(self, ctx: GenContext) -> None:
        s, c, L = ctx.settings, ctx.canvas, ctx.data["L"]
        P = ctx.data["panel"]
        boards = (P["region"] == 0) & ~P["seams"] & ~c.tag("protect")
        up, dn = self._dashes(ctx, "dashes", boards, int(round(s.noise * 4 * ctx.scale ** 2)), True)
        c.shift(up, 1, ceil=L.lit - 1)
        c.shift(dn, -1, floor=L.dark + 1)

    def _panel_highlights(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        P = ctx.data["panel"]
        rails = P["region"] == 2
        top_edge = rails & ~pa.neighbour(rails, 0, -1, False, fill=True)
        c.shift(top_edge, 1, ceil=L.lit)
        # the board face just right of each seam, and just inside the bottom / right frame, catches the light
        board = P["region"] == 0
        board_lit = pa.neighbour(P["seams"], -1, 0, False, fill=False) & ~P["seams"] & board
        c.shift(board_lit & bp.noise_keep(self, ctx, "board_lit", 0.6), 1, ceil=L.lit - 1)

    def _panel_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        P = ctx.data["panel"]
        rails = P["region"] == 2
        bottom_edge = rails & ~pa.neighbour(rails, 0, 1, False, fill=True)
        c.shift(bottom_edge, -1, floor=L.dark)
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        outer = P["frame"] & (((yy + P["y_off"]) == P["height"] - 1) | (xx == ctx.w - 1))
        c.set(outer, "base", L.deep)

    def _panel_accent(self, ctx: GenContext) -> None:
        c = ctx.canvas
        P = ctx.data["panel"]
        n = c.length_of("accent")
        # porthole: a metal ring lit on its top-left, window glass left open
        ring = P["ring"]
        ang = P["ang"]
        lit = ring & (np.cos(ang + math.pi * 0.75) > 0.35)
        dark = ring & (np.cos(ang + math.pi * 0.75) < -0.35)
        c.set(ring, "accent", max(1, n // 2))
        c.set(lit, "accent", n - 1)
        c.set(dark, "accent", 1)
        c.clear(P["glass"])
        fit = P["fittings"]
        if fit.any():
            c.set(fit, "accent", max(1, n // 2))
            c.set(fit & ~pa.neighbour(fit, 0, -1, False, fill=True), "accent", n - 2)
            c.set(fit & ~pa.neighbour(fit, 0, 1, False, fill=True), "accent", 1)
        if P["rivets"].any():
            c.set(P["rivets"], "accent", n - 2)
        bp.protect(ctx, ring | fit | P["rivets"])
        if ctx.settings.glow > 0.02:
            # glowing lamp light behind the porthole glass
            self.glow_points(ctx, ctx.settings.glow, P["glass"] & ~pa.erode(P["glass"], False))

    # ============================================================ item icon

    def _plan_door_item(self, ctx: GenContext) -> None:
        N = ctx.w
        x0, x1 = int(round(N * 0.25)), int(round(N * 0.75))
        body = np.zeros((N, N), dtype=bool)
        body[:, x0:x1] = True
        yy, xx = np.mgrid[0:N, 0:N].astype(np.float64)
        cx, cy = (x0 + x1) / 2.0, N * 0.27
        r = 2.1 * ctx.scale
        dist = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        glass = dist <= r - 1.0
        ring = (dist <= r) & ~glass
        seam = np.zeros_like(body)
        seam[:, (x0 + x1) // 2] = True
        rail = np.zeros_like(body)
        rail[int(round(N * 0.53)):int(round(N * 0.53)) + ctx.px(1), x0:x1] = True
        seam &= ~rail & ~ring & ~glass
        handle = np.zeros_like(body)
        handle[int(round(N * 0.62)):int(round(N * 0.62)) + ctx.px(2), x1 - ctx.px(2)] = True
        ctx.data["icon"] = dict(body=body, glass=glass & body, ring=ring & body, seam=seam & body, rail=rail,
                                handle=handle, ang=np.arctan2(yy + 0.5 - cy, xx + 0.5 - cx), x0=x0, x1=x1)

    def _item_base(self, ctx: GenContext) -> None:
        c = ctx.canvas
        c.clear_all()
        c.set(ctx.data["icon"]["body"], "base", ctx.data["L"].mid)

    def _item_material(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        I = ctx.data["icon"]
        g = self._grain(ctx, "grain", True)
        off = self._bands(g, I["body"], 0.25, 0.2)
        c.set(I["body"], "base", np.clip(L.mid + off, L.dark + 1, L.lit - 1))
        c.set(I["seam"], "base", L.dark)
        c.set(I["rail"], "base", L.up)

    def _item_highlights(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        I = ctx.data["icon"]
        body = I["body"]
        c.shift(pa.edge_of(body, -1, 0) & ~pa.edge_of(body, 0, 1), 1, ceil=L.lit)

    def _item_shadows(self, ctx: GenContext) -> None:
        L, c = ctx.data["L"], ctx.canvas
        body = ctx.data["icon"]["body"]
        outline = pa.edge_of(body, 1, 0) | pa.edge_of(body, 0, 1) | pa.edge_of(body, 0, -1)
        c.set(outline, "base", L.deep)
        c.set(pa.edge_of(body, -1, 0) & ~outline, "base", L.dark)
        bp.protect(ctx, outline)

    def _item_accent(self, ctx: GenContext) -> None:
        c = ctx.canvas
        I = ctx.data["icon"]
        n = c.length_of("accent")
        ring = I["ring"]
        c.set(ring, "accent", max(1, n // 2))
        c.set(ring & (np.cos(I["ang"] + math.pi * 0.75) > 0.35), "accent", n - 1)
        c.set(ring & (np.cos(I["ang"] + math.pi * 0.75) < -0.35), "accent", 1)
        c.set(I["glass"], "glow", 1)
        c.set(I["glass"] & (np.cos(I["ang"] + math.pi * 0.75) > 0.3), "glow", 2)
        c.set(I["handle"], "accent", n - 2)
        bp.protect(ctx, ring | I["glass"] | I["handle"])
