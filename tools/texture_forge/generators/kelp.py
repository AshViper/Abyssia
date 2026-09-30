"""Kelp: tall seaweed sprites in two parts, like vanilla kelp / kelp_plant.

``part = "stalk"`` tiles seamlessly in y (the stalk is a periodic curve and
every blade is drawn with vertical wrap-around), ``part = "top"`` is the
growing tip whose stalk leaves the bottom edge at the same column a stalk
tile enters at, so the two stack.

Variants:

* ``deep``    - dark green to blue-green, long narrow blades held steeply
  upward (a long vertical rhythm).
* ``giant``   - olive-gold, broad ruffled blades each with a round gas
  bladder (pneumatocyst) at its base.
* ``abyssal`` - indigo / teal with bioluminescent blade tips.
* ``thermal`` - rust-brown, short frilly blades with ember-hot tips.
* ``seagrass`` - no central stalk: several thin blades side by side (tall
  seagrass, stacking grass, or - flipped - hanging vines and roots).

The drawing model and layer grammar are shared with the plant generator.
"""
from __future__ import annotations

import math

import numpy as np

from core import pixel_art as pa
from core.layers import ROLE_IDS, GenContext
from core.seed import derive_rng
from core.settings import TextureSettings

from .base import BaseGenerator
from .plant import (HEAD, LEAF, STEM, _derived_hex, _rng_sign, _silhouette, _smooth_noise, _Sprite, _turtle,
                    sprite_gloss, sprite_glow, sprite_rim_light, sprite_rim_shadow, tip_cells)

_KELP_BASE = {"deep": "deep_kelp", "giant": "giant_kelp", "abyssal": "abyssal_kelp", "thermal": "thermal_kelp",
              "seagrass": "abyssal_grass"}
_KELP_STALK = {"deep": "#4C6634", "giant": "#6E5226", "abyssal": "#3E3674", "thermal": "#6E2E18",
               "seagrass": "#28483F"}
_KELP_ACCENT = {"deep": "teal", "giant": "gold", "abyssal": "glow_violet", "thermal": "sulfur", "seagrass": "teal"}

# per-variant blade shape: elevation (deg above horizontal), upward curl (deg),
# length and width (16px units), count multiplier
_SHAPE = {
    "deep":    dict(elev=(28, 42), curl=(15, 32), length=(6.5, 8.5), width=1.15, count=1.1, tone=0.62),
    "giant":   dict(elev=(8, 20), curl=(20, 40), length=(5.5, 7.0), width=1.7, count=0.85, tone=0.62),
    "abyssal": dict(elev=(15, 30), curl=(30, 55), length=(5.5, 7.0), width=1.3, count=1.0, tone=0.66),
    "thermal": dict(elev=(14, 28), curl=(15, 40), length=(4.5, 6.0), width=1.35, count=1.1, tone=0.62),
    # seagrass / strands: several thin blades running the full height (no central stalk)
    "seagrass": dict(elev=(80, 90), curl=(0, 25), length=(4.0, 12.0), width=1.0, count=1.0, tone=0.6),
}


class KelpGenerator(BaseGenerator):
    category = "kelp"

    # --------------------------------------------------------------- palette
    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        v = s.variant if s.variant in _KELP_BASE else "deep"
        roles = {"base": _KELP_BASE[v],
                 "secondary": _derived_hex(s.base_color, -0.1, 0.8, -15) if s.base_color else _KELP_STALK[v]}
        if v == "abyssal":
            roles["glow"] = "glow_violet" if s.palette in ("trench", "crystal") else "glow_cyan"
        elif v == "thermal":
            roles["glow"] = "heat"
        else:
            roles["glow"] = "glow_green"
        if not s.accent:
            roles["accent"] = _KELP_ACCENT[v]
        return roles

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": max(4, k), "accent": 5, "accent2": 4, "glow": 4}

    # ------------------------------------------------------------ geometry
    def params(self, ctx: GenContext) -> dict:
        s = ctx.settings
        v = s.variant if s.variant in _SHAPE else "deep"
        sil, ws = _silhouette(ctx)
        sc = ctx.scale
        ds = sc ** 0.65
        density, leaf = s.density, s.leaf
        if ws > 0:
            density = float(np.clip(density + (sil.coverage - 0.35) * 0.8 * ws, 0, 1))
            leaf = float(np.clip(leaf + (sil.width - 0.4) * 0.8 * ws, 0, 1))
        height = 0.42 + 0.5 * s.height
        if ws > 0 and not sil.touches_top:
            height = height * (1 - 0.35 * ws) + float(np.clip(sil.top, 0.3, 1.0)) * 0.35 * ws
        return dict(v=v, sc=sc, ds=ds, density=density, leaf=leaf, height=float(np.clip(height, 0.3, 0.97)),
                    organic=s.organic, shape=_SHAPE[v], stalk=s.part != "top")

    def layer_base(self, ctx: GenContext) -> None:
        ctx.canvas.clear_all()
        P = self.params(ctx)
        sp = _Sprite(ctx.w, ctx.h, wrap_y=P["stalk"])
        sp.anchor_bottom = True
        rng = ctx.rng("kelp_geom", ctx.settings.part)
        _build_kelp(ctx, sp, P, rng)
        sp.drop_islands(2)
        sp.hy = np.zeros((ctx.h, ctx.w))
        if not P["stalk"]:
            rows = np.nonzero(sp.vis.any(axis=1))[0]
            y = np.arange(ctx.h, dtype=np.float64)[:, None] + 0.5
            span = max(1.0, ctx.h - (rows.min() if len(rows) else 0))
            sp.hy = np.broadcast_to(np.clip((ctx.h - y) / span, 0, 1), (ctx.h, ctx.w)).copy()
        ctx.data["spr"] = sp
        ctx.data["P"] = P
        self._commit(ctx)

    def _commit(self, ctx: GenContext) -> None:
        sp = ctx.data["spr"]
        extra = None
        if sp.wrap_y:
            # the rows that meet the neighbouring tile are exact by construction:
            # keep the clean-up filter from changing them (it does not wrap)
            extra = np.zeros((ctx.h, ctx.w), dtype=bool)
            extra[0] = extra[-1] = True
        sp.commit(ctx, extra)

    # ---------------------------------------------------------------- layers
    def layer_material(self, ctx: GenContext) -> None:
        """Blades darken toward the stalk and brighten toward the tip."""
        s, sp, P = ctx.settings, ctx.data["spr"], ctx.data["P"]
        rng = ctx.rng("kelp_material")
        spread = 0.04 + 0.2 * s.roughness
        tones = np.array([e["tone"] + rng.uniform(-spread, spread) + rng.normal(0, 0.08 * s.noise)
                          for e in sp.els] + [0.5])
        idx = np.where(sp.elem >= 0, sp.elem, len(sp.els))
        grad = np.array([e.get("grad", 0.5) for e in sp.els] + [0.0])[idx]
        body = tones[idx] + grad * (sp.t - 0.5) + 0.25 * (sp.hy - 0.5) * (not P["stalk"]) - 0.2 * s.moisture
        if s.noise > 0.05:
            jitter = (_wrap_noise(ctx, "kelp_mat_noise", 3, P["stalk"]) - 0.5) * 0.2 * s.noise
            body = body + np.where(sp.width >= 2.5, jitter, 0.0)
        sp.body = np.where(sp.vis, np.clip(body, 0, 1), sp.body)
        self._commit(ctx)

    def layer_large_detail(self, ctx: GenContext) -> None:
        """Depth: blades behind the stalk are a step darker."""
        sp = ctx.data["spr"]
        back = sp.prop("back", 0.0) > 0.5
        sp.body = np.where(back, sp.body - 0.24, sp.body)
        self._commit(ctx)

    def layer_medium_detail(self, ctx: GenContext) -> None:
        """Ruffled bands across giant blades, mottling on others, knots on the stalk."""
        s, sp, P = ctx.settings, ctx.data["spr"], ctx.data["P"]
        blades = sp.part_mask(LEAF)
        if P["v"] == "giant" or s.roughness > 0.55:
            # corrugated / ruffled blades: pale bands across the blade
            L = sp.prop("length", 6.0)
            period = max(2.0, (2.2 if P["v"] == "giant" else 2.8) * P["ds"])
            band = blades & (np.sin(2 * np.pi * (sp.t * L / period)) > 0.35) & (sp.t > 0.15) & (sp.t < 0.9)
            if P["v"] == "giant":
                sp.body = np.where(band, sp.body + 0.16 + 0.12 * s.roughness, sp.body)
            else:
                sp.rim = np.where(band & (sp.width >= 1.5), sp.rim + 1, sp.rim)
        if P["v"] != "giant":
            wide = blades & (sp.width >= 2.5)
            if wide.any():
                f = _wrap_noise(ctx, "kelp_mottle", 4, P["stalk"])
                cut = np.quantile(f[wide], 0.2 + 0.1 * s.roughness)
                dn = wide & (f <= cut) & ~sp.core
                dn = pa.remove_small_clusters(dn.astype(np.int32), 3, False).astype(bool) & wide
                sp.body = np.where(dn, sp.body - 0.18, sp.body)
        # a small knot on the stalk where each blade joins
        knots = ctx.data.get("knots")
        if knots is not None:
            stalk = sp.part_mask(STEM)
            sp.body = np.where(knots & stalk, sp.body - 0.15, sp.body)
        self._commit(ctx)

    def layer_small_detail(self, ctx: GenContext) -> None:
        """Frayed blade edges (roughness, strongest on thermal kelp) and fine speckles."""
        s, sp, P = ctx.settings, ctx.data["spr"], ctx.data["P"]
        rng = ctx.rng("kelp_small")
        rough = s.roughness + (0.2 if P["v"] == "thermal" else 0.0)
        if rough > 0.5:
            # nibble single pixels out of the outer edge of broad blade parts only
            blades = sp.part_mask(LEAF) & (sp.width >= 3.0) & ~sp.core
            cnt = sum(sp.nb(sp.vis, dx, dy, False).astype(np.int32) for dx, dy in pa.N4)
            edge = blades & (cnt == 3) & (sp.t > 0.3) & (sp.t < 0.85)
            if sp.wrap_y:
                edge[0] = edge[-1] = False
            sp.remove(edge & (rng.random(edge.shape) < (rough - 0.5) * 0.45))
        if s.noise > 0.1:
            spots = sp.part_mask(LEAF) & (sp.width >= 2.5) & ~sp.core
            ys, xs = np.nonzero(spots)
            k = int(len(xs) * 0.04 * s.noise)
            for i in (rng.choice(len(xs), size=min(k, len(xs)), replace=False) if k else []):
                y, x = int(ys[i]), int(xs[i])
                if x + 1 < sp.w and spots[y, x + 1] and sp.elem[y, x + 1] == sp.elem[y, x]:
                    sp.body[y, x:x + 2] += 0.18
        self._commit(ctx)

    def layer_cracks(self, ctx: GenContext) -> None:
        """Midribs on broad blades and a groove down a thick stalk."""
        sp, P = ctx.data["spr"], ctx.data["P"]
        rib = sp.core & sp.part_mask(LEAF) & (sp.width >= 2.6) & (sp.t > 0.12) & (sp.t < 0.88)
        if P["v"] == "deep":
            sp.rim = np.where(rib, sp.rim + 1, sp.rim)     # a pale vertical vein
        else:
            sp.rim = np.where(rib, sp.rim - 1, sp.rim)
        groove = ctx.data.get("groove")
        if groove is not None:
            sp.rim = np.where(groove & sp.part_mask(STEM), sp.rim - 1, sp.rim)
        self._commit(ctx)

    def layer_highlights(self, ctx: GenContext) -> None:
        s, sp = ctx.settings, ctx.data["spr"]
        lit = sprite_rim_light(sp)
        sp.rim = np.where(lit, sp.rim + 1, sp.rim)
        ctx.data["lit"] = lit
        rng = ctx.rng("kelp_gloss")
        tipm = tip_cells(sp, max(1, int(len(sp.tips) * (0.3 + 0.4 * s.moisture))), rng, 1, (LEAF,))
        sp.rim = np.where(tipm & ~lit, sp.rim + 1, sp.rim)
        sprite_gloss(sp, s.moisture, rng, (LEAF, HEAD))
        self._commit(ctx)

    def layer_shadows(self, ctx: GenContext) -> None:
        sp = ctx.data["spr"]
        far, cast = sprite_rim_shadow(sp)
        sp.rim = np.where(far, sp.rim - 1, sp.rim)
        # blades throw a shadow on the stalk just below where they join
        under = ctx.data.get("under")
        if under is not None:
            sp.rim = np.where(under & sp.part_mask(STEM) & ~far, sp.rim - 1, sp.rim)
        sp.rim = np.where(cast & ~far & sp.part_mask(LEAF), sp.rim - 1, sp.rim)
        self._commit(ctx)

    def layer_accent(self, ctx: GenContext) -> None:
        s, sp, P = ctx.settings, ctx.data["spr"], ctx.data["P"]
        v = P["v"]
        if v == "giant":
            _bladders(ctx, sp, P)
        # glowing tips: abyssal and thermal kelp always carry some, others only with the slider
        amount = s.glow
        if v in ("abyssal", "thermal"):
            amount = 0.3 + 0.7 * s.glow
        if amount > 0.02:
            rng = ctx.rng("kelp_glow")
            n = ctx.canvas.length_of("glow")
            k = max(1, int(round(len(sp.tips) * min(1.0, 0.25 + 0.75 * amount))))
            ln = 1 + int(amount > 0.25) + int(amount > 0.8 and ctx.w >= 32)
            g = tip_cells(sp, k, rng, ln, (LEAF,), prefer_high=False)
            if g.any():
                # hottest / brightest at the very tip, cooling toward the blade
                top = n - 1 if amount > 0.8 else n - 2
                lv = np.where(sp.t >= _tip_cut(sp, g), top, top - 1)
                if v == "thermal":
                    lv = np.where(sp.t >= _tip_cut(sp, g), n - 1, np.maximum(1, n - 3 + (sp.t > 0.9)))
                sprite_glow(sp, ctx, g, lv)
            if v == "abyssal" and amount > 0.45:
                # photophores: a few glowing dots along blade middles
                cand = sp.core & sp.part_mask(LEAF) & (sp.t > 0.3) & (sp.t < 0.8) & ~g
                ys, xs = np.nonzero(cand)
                kk = int(len(xs) * (amount - 0.45) * 0.25)
                if kk:
                    idx = rng.choice(len(xs), size=min(kk, len(xs)), replace=False)
                    dots = np.zeros_like(cand)
                    dots[ys[idx], xs[idx]] = True
                    sprite_glow(sp, ctx, dots, n - 2)
        self._commit(ctx)


# ================================================================ geometry


def _wrap_noise(ctx: GenContext, key: str, freq: float, wrap: bool) -> np.ndarray:
    # core noise is always periodic, so it is seamless for the stalk part as well
    return _smooth_noise(ctx, key, freq * ctx.scale ** 0.5)


def _tip_cut(sp: _Sprite, g: np.ndarray) -> np.ndarray:
    """Per-pixel: the highest t of the element the pixel belongs to (inside ``g``)."""
    out = np.full(sp.t.shape, 2.0)
    for i in np.unique(sp.elem[g]):
        m = g & (sp.elem == i)
        out[m] = sp.t[m].max()
    return out


def _stalk_x(y: np.ndarray, cx: float, amp: float, waves: int, phase_sign: float, h: int) -> np.ndarray:
    """Stalk centre line; periodic in y with period h and exactly ``cx`` at y = 0 / h."""
    return cx + phase_sign * amp * np.sin(2 * np.pi * waves * y / h)


def _build_kelp(ctx: GenContext, sp: _Sprite, P: dict, rng: np.random.Generator) -> None:
    s = ctx.settings
    w, h = ctx.w, ctx.h
    sc, ds = P["sc"], P["ds"]
    sh = P["shape"]
    stalk_part = P["stalk"]
    if P["v"] == "seagrass":
        _build_seagrass(ctx, sp, P)
        return
    # ---- the stalk -----------------------------------------------------
    W = max(1, int(round((0.9 + 1.7 * s.stem) * ds)))
    cx = w / 2.0
    waves = 1 if h <= 32 else 2
    amp = min((0.75 + 1.1 * P["organic"]) * ds, h / (2 * math.pi * waves) * 0.95)
    sgn = float(_rng_sign(rng))
    y_top = 0.0 if stalk_part else h * (1 - P["height"])
    yc = np.arange(h) + 0.5
    xc = _stalk_x(yc, cx, amp, waves, sgn, h)
    stalk = np.zeros((h, w), dtype=bool)
    face = np.zeros((h, w))
    tmap = np.zeros((h, w))
    rows = range(h) if stalk_part else range(int(math.ceil(y_top)), h)
    for y in rows:
        wy = W
        if not stalk_part:
            # the growing tip narrows over its last few pixels
            left = (y + 0.5 - y_top) / max(1.0, 3.0 * ds)
            wy = max(1, min(W, int(round(W * min(1.0, 0.5 + left)))))
        x0 = int(math.floor(xc[y] - wy / 2.0 + 0.5))
        for k in range(wy):
            x = x0 + k
            if 0 <= x < w:
                stalk[y, x] = True
                face[y, x] = -0.70710678 * ((k - (wy - 1) / 2) / (wy / 2)) if wy > 1 else 0.0
                tmap[y, x] = 0.5 if stalk_part else (h - y - 0.5) / max(1.0, h - y_top)
    sp.add(stalk, tmap, STEM, "secondary", z=0.0, tone=0.8, width=float(W), face=face,
           grad=0.0 if stalk_part else 0.3)
    if W >= 3:
        groove = np.zeros((h, w), dtype=bool)
        for y in rows:
            x = int(math.floor(xc[y] + 0.5 * ((W % 2) == 0)))
            if 0 <= x < w and stalk[y, x]:
                groove[y, x] = True
        ctx.data["groove"] = groove
    # ---- blades ---------------------------------------------------------
    span = h - y_top if not stalk_part else h
    per = (2.4 + 2.6 * P["density"]) * sh["count"] * (sc / ds) ** 1.0
    nb = max(2 if stalk_part else 1, int(round(per * span / h)))
    if stalk_part and nb % 2:
        nb += 1                                  # sides alternate cleanly across the seam
    s0 = _rng_sign(rng)
    phase = rng.uniform(0, 1)
    knots = np.zeros((h, w), dtype=bool)
    under = np.zeros((h, w), dtype=bool)
    lenmul = 0.7 + 0.6 * P["leaf"]
    for i in range(nb):
        side = s0 * (1 if i % 2 == 0 else -1)
        if stalk_part:
            yb = ((i + phase + rng.uniform(-0.18, 0.18) * (1 + s.noise)) * h / nb) % h
        else:
            # blades from the bottom up to just under the tip, young ones smaller
            f = (i + 0.5 + rng.uniform(-0.2, 0.2)) / nb
            yb = h - f * (span - 2.5 * ds) + 0.5
        young = 1.0 if stalk_part else float(np.clip(0.55 + 0.6 * (yb - y_top) / max(1.0, span), 0.5, 1.0))
        xs = float(_stalk_x(np.array([yb]), cx, amp, waves, sgn, h)[0])
        xb = xs + side * (W / 2.0 - 0.45)
        elev = math.radians(rng.uniform(*sh["elev"]) + rng.normal(0, 9 * s.noise))
        curl = math.radians(rng.uniform(*sh["curl"])) * (0.6 + 0.8 * P["organic"])
        a0 = -elev if side > 0 else math.pi + elev
        turn = -side * curl
        L = rng.uniform(*sh["length"]) * ds * lenmul * young * rng.uniform(1 - 0.3 * s.noise, 1 + 0.1 * s.noise)
        wid0 = max(1.0, sh["width"] * (1.35 + 0.9 * P["leaf"]) * ds * (0.8 + 0.2 * young))
        pts, u = _turtle(xb, yb, a0, L, turn, 1.4, 0.06 * P["organic"], 1.0, rng.uniform(0, 6.3))
        # full width for most of the blade, tapering over the last stretch
        prof = np.maximum(1.0, wid0 * (1 - 0.5 * u ** 2.5))
        if P["v"] == "giant":
            prof = np.maximum(1.0, wid0 * np.sin(np.pi * np.clip(u * 0.75 + 0.22, 0, 1)) ** 0.5)
        back = rng.random() < 0.35
        z = -1.0 if back else 1.0 + i * 0.01
        sp.stroke(pts, u, prof, LEAF, "base", (side, 1), z=z, tone=sh["tone"] + rng.uniform(-0.04, 0.04),
                  back=float(back), length=L, grad=0.45, attach=(xb, yb), side=side, blade=i)
        # knot where the blade joins, and the shadow it casts on the stalk below
        yi = int(math.floor(yb)) % h if stalk_part else int(math.floor(yb))
        if 0 <= yi < h:
            row = stalk[yi]
            cols = np.nonzero(row)[0]
            if len(cols):
                edge_col = cols.max() if side > 0 else cols.min()
                knots[yi, edge_col] = True
                for dy in (1,):
                    yy = (yi + dy) % h if stalk_part else yi + dy
                    if 0 <= yy < h:
                        c2 = np.nonzero(stalk[yy])[0]
                        if len(c2):
                            under[yy, c2.max() if side > 0 else c2.min()] = True
    # ---- the growing tip -------------------------------------------------
    if not stalk_part:
        tip_y = y_top + 0.5
        tx = float(_stalk_x(np.array([tip_y]), cx, amp, waves, sgn, h)[0])
        side = _rng_sign(rng)
        L = max(2.0, (3.0 + 2.5 * P["leaf"]) * ds)
        pts, u = _turtle(tx, tip_y + 0.5, -math.pi / 2 + side * 0.15, L, side * math.radians(70 + 30 * P["organic"]),
                         1.5)
        prof = np.maximum(1.0, max(1.0, W * 0.9) * np.clip(1.1 - u, 0, 1) ** 0.8 + 0.2)
        sp.stroke(pts, u, prof, LEAF, "base", (side, 1), z=2.0, tone=sh["tone"] + 0.08, back=0.0, length=L,
                  grad=0.55, side=side, blade=-1)
        # a young blade budding on the other side just below the tip
        L2 = max(1.5, L * 0.55)
        p2, u2 = _turtle(tx - side * W / 2.0, tip_y + 1.5 * ds, -math.pi / 2 - side * 0.9, L2,
                         side * math.radians(40), 1.3)
        sp.stroke(p2, u2, np.maximum(1.0, W * 0.7 * (1 - u2) + 0.2), LEAF, "base", (-side, 1), z=1.9,
                  tone=sh["tone"], back=0.0, length=L2, grad=0.5, side=-side, blade=-2)
    ctx.data["knots"] = knots
    ctx.data["under"] = under


def _build_seagrass(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Thin blades side by side.  The stalk part tiles vertically; the top part
    starts every blade at the same column the stalk leaves the tile and ends it
    in a leaning, tapered tip.  Blade layout is keyed on the seed only (not the
    part), so stalk and top always line up."""
    s = ctx.settings
    w, h = ctx.w, ctx.h
    ds = P["ds"]
    lay = derive_rng(s.seed, "kelp", "seagrass_layout")
    tips = derive_rng(s.seed, "kelp", "seagrass_tips")
    stalk_part = P["stalk"]
    n = int(np.clip(round((2.2 + 3.6 * P["density"]) * (w / 16.0) ** 0.9), 2, max(2, w // 2)))
    margin = 1.5 * ds
    slots = np.linspace(margin, w - margin, n + 1)
    for i in range(n):
        x0 = float(np.clip(lay.uniform(slots[i], slots[i + 1]), 1, w - 2)) + 0.5
        x0 = math.floor(x0) + 0.5
        amp = lay.uniform(0.3, 1.1) * ds * (0.5 + P["organic"])
        sgn = 1.0 if lay.random() < 0.5 else -1.0
        wide = lay.random() < 0.15 + 0.6 * P["leaf"] and ds >= 0.99
        W = 2.0 if wide else 1.0
        z = lay.uniform(-1.0, 1.0)
        back = z < -0.35
        tone = P["shape"]["tone"] + lay.uniform(-0.08, 0.08)
        lean = lay.uniform(-1, 1)
        top_h = tips.uniform(0.35, 0.95) * P["height"] * h
        if stalk_part:
            ys = np.linspace(h, 0.0, max(8, 3 * h))
        else:
            ys = np.linspace(h, h - top_h, max(6, int(3 * top_h)))
        xs = x0 + sgn * amp * np.sin(2 * np.pi * ys / h)
        u = (h - ys) / max(1.0, h if stalk_part else top_h)
        if not stalk_part:
            # the free end leans and curls to one side
            k = np.clip((u - 0.55) / 0.45, 0, 1)
            xs = xs + lean * (1.2 + 1.5 * P["organic"]) * ds * k ** 1.6
        pts = np.stack([xs, ys], axis=1)
        prof = np.full(len(u), W) if stalk_part else np.maximum(1.0, W * np.clip(1.15 - u, 0, 1) ** 0.6)
        sp.stroke(pts, u, prof, LEAF, "base", (float(np.sign(lean) or 1.0), 1), z=z, tone=tone,
                  back=float(back), length=float(h if stalk_part else top_h), grad=0.35, tip=not stalk_part,
                  side=int(sgn), blade=i)
    ctx.data["knots"] = None
    ctx.data["under"] = None


def _bladders(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Round gas bladders at the base of giant-kelp blades (accent ramp)."""
    ds = P["ds"]
    r = max(0.9, (0.95 + 0.35 * P["leaf"]) * ds)
    for i, e in enumerate(list(sp.els)):
        if e["part"] != LEAF or "attach" not in e or e.get("blade", -1) < 0:
            continue
        xb, yb = e["attach"]
        side = e["side"]
        bx = xb + side * r * 0.9
        by = yb + 0.3
        if sp.wrap_y:
            by %= sp.h
        # snap so a 2x2 / 3x3 bladder comes out round and symmetric
        d = max(2, int(round(2 * r)))
        bx = math.floor(bx) + (0.5 if d % 2 else 0.0)
        by = math.floor(by) + (0.5 if d % 2 else 0.0)
        sp.blob(bx, by, d / 2 + 0.2, d / 2 + 0.2, HEAD, "accent", z=e["z"] + 0.5, tone=0.55,
                back=e.get("back", 0.0), grad=0.2)
    # sphere shading of their own (they are drawn after the lighting layers)
    heads = sp.part_mask(HEAD)
    thr = np.maximum(0.2, 1.0 - 2.0 / np.maximum(1.0, sp.width))
    sp.rim = np.where(heads & (sp.face > thr), 1, np.where(heads & (sp.face < -thr), -1, sp.rim))
    sp.body = np.where(heads, 0.2, sp.body)
    # give each bladder a highlight pixel
    for i, e in enumerate(sp.els):
        if e["part"] == HEAD:
            m = sp.elem == i
            ys, xs = np.nonzero(m)
            if len(xs) >= 3:
                j = int(np.argmin(xs + ys))
                sp.over_role[ys[j], xs[j]] = ROLE_IDS["accent"]
                sp.over_level[ys[j], xs[j]] = ctx.canvas.length_of("accent") - (1 if len(xs) >= 6 else 2)
                sp.protect[ys[j], xs[j]] = True
