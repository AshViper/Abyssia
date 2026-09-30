"""Ore: low-contrast host rock with rim-lit mineral clumps, veins or specks.

The host rock is the terrain generator's rock (same layers, slightly
flattened so the ore pops).  The ore itself is planned once in
:meth:`OreGenerator.prepare` (so toggling a layer never moves it) and
painted in the *accent* layer:

* ``cluster``   - chunky crystal clumps (about 5-12 px at 16x16), well spaced,
                  plus a few small chips; larger sizes split clumps into grains
* ``vein``      - stair-stepped bands that wrap around the tile
* ``scattered`` - small 2-3 px grains, grouped in loose nests

Every ore body is painted with its own rim lighting (lit top-left,
darker bottom-right), a bright glint pixel and a one-pixel shadow in the
host rock under / right of it, so it reads even with the global
highlight and shadow layers switched off.  Those layers add extra
glints and a deeper drop shadow when they are on.
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.analyzer import TextureAnalysis
from core.layers import GenContext
from core.palette import ACCENT_PRESETS, THEMES, luminance, rgb_to_oklab

from .terrain import AUTO_MODE, TerrainGenerator

# Accent anchor -> anchor used for emissive ore glints (glow role).
GLOW_FOR: dict[str, str] = {
    "cyan_mineral": "glow_cyan", "teal": "glow_cyan", "blue_ice": "glow_cyan", "patina": "glow_cyan",
    "glow_cyan": "glow_cyan", "manganese": "glow_violet", "amethyst": "glow_violet",
    "glow_violet": "glow_violet", "crystal": "glow_violet", "emerald": "glow_green",
    "moss": "glow_green", "glow_green": "glow_green", "copper": "heat", "rust": "heat",
    "heat": "heat", "pyrite": "gold", "gold": "gold", "sulfur": "sulfur", "ruby": "coral_pink",
    "coral_pink": "coral_pink", "silver": "blue_ice", "bone": "bone",
}


def effective_accent(s) -> str:
    """Anchor name of the accent ramp the palette will actually use."""
    if s.accent:
        return ACCENT_PRESETS.get(s.accent, (s.accent, None))[0]
    th = THEMES.get(s.palette) or THEMES["abyss"]
    return th.accent


def glow_anchor_for(s) -> str:
    acc = effective_accent(s)
    return GLOW_FOR.get(acc, acc)   # unknown / custom "#hex" accents glow in their own hue


# ------------------------------------------------------------------ shapes


def blob_offsets(rng: np.random.Generator, n: int, elong: float = 1.0, angle: float = 0.0,
                 rough: float = 0.4) -> list[tuple[int, int]]:
    """A compact, 4-connected blob of ``n`` pixels around (0, 0).

    Pixels are taken nearest-first by a jittered elliptical distance, which
    gives rounded, chunky "crystal clump" silhouettes (never spidery).
    """
    n = max(1, int(n))
    if n == 1:
        return [(0, 0)]
    R = int(math.ceil(math.sqrt(n / math.pi) * 1.9 * max(1.0, elong) ** 0.5)) + 2
    ys, xs = np.mgrid[-R:R + 1, -R:R + 1]
    cx, cy = rng.uniform(-0.5, 0.5), rng.uniform(-0.5, 0.5)
    if abs(cx) + abs(cy) < 0.3:   # a centred seed makes symmetric "+" shapes
        cx = math.copysign(0.3 + 0.2 * rng.random(), cx if cx else 1.0)
    dx, dy = xs - cx, ys - cy
    ca, sa = math.cos(angle), math.sin(angle)
    u = dx * ca + dy * sa
    v = -dx * sa + dy * ca
    ea = math.sqrt(max(1.0, elong))
    d = np.sqrt((u / ea) ** 2 + (v * ea) ** 2)
    d = d + rough * 0.9 * rng.random(d.shape)
    order = np.argsort(d.ravel(), kind="stable")
    chosen = np.zeros(d.shape, dtype=bool)
    chosen.ravel()[order[:n]] = True
    # keep the largest 4-connected part and regrow to n along the frontier
    lab, sizes = pa.label_components(chosen.astype(np.int32), wrap=False, mask=chosen)
    if len(sizes) > 1:
        keep = int(np.argmax(sizes))
        chosen = lab == keep
    while chosen.sum() < n:
        front = pa.outline(chosen, wrap=False)
        if not front.any():
            break
        dd = np.where(front, d, np.inf)
        iy, ix = np.unravel_index(int(np.argmin(dd)), dd.shape)
        chosen[iy, ix] = True
    py, px = np.nonzero(chosen)
    return sorted(zip((px - R).tolist(), (py - R).tolist()))


def _cells_at(ctx: GenContext, offs, x: int, y: int) -> list[tuple[int, int]] | None:
    out = []
    for dx, dy in offs:
        cx, cy = x + dx, y + dy
        if ctx.wrap:
            cx %= ctx.w
            cy %= ctx.h
        elif not (0 <= cx < ctx.w and 0 <= cy < ctx.h):
            return None
        out.append((cx, cy))
    return out


def _mask_of(ctx: GenContext, cells) -> np.ndarray:
    m = np.zeros((ctx.h, ctx.w), dtype=bool)
    for x, y in cells:
        m[y, x] = True
    return m


def _periodic_1d(rng: np.random.Generator, t: np.ndarray, harmonics: int = 2,
                 amp: float = 1.0) -> np.ndarray:
    """Smooth random function of t, periodic on [0, 1)."""
    out = np.zeros_like(t, dtype=np.float64)
    for k in range(1, harmonics + 1):
        a = rng.normal(0, 1) / k
        ph = rng.random()
        out += a * np.sin(2 * np.pi * (k * t + ph))
    m = np.max(np.abs(out)) if out.size else 0
    return out / m * amp if m > 1e-9 else out


def _ore_profile(a: TextureAnalysis, influence: float) -> tuple[float, float]:
    """(ore coverage, mean ore cluster area at 16x16) from the style profile.

    The profile is already blended with the built-in one by
    ``source_influence``; a reference without ore-like colours (plain stone)
    contributes nothing, so fall back to the built-in ore profile for it.
    """
    d = TextureAnalysis.default_for("ore")
    frac, acl = a.accent_fraction, a.accent_cluster
    if not a.is_default:
        t = float(np.clip(influence, 0.0, 1.0))
        raw = (frac - (1 - t) * d.accent_fraction) / t if t > 0.02 else d.accent_fraction
        if t >= 0.999:
            raw = frac
        if raw < 0.03:
            frac, acl = d.accent_fraction, d.accent_cluster
    if acl < 1.5:
        acl = d.accent_cluster
    return float(np.clip(frac, 0.08, 0.3)), float(np.clip(acl, 2.5, 10.0))


# --------------------------------------------------------------- generator


class OreGenerator(TerrainGenerator):
    category = "ore"

    # -------------------------------------------------------------- setup
    def mode(self, ctx: GenContext) -> str:
        return AUTO_MODE.get(ctx.settings.material, "rough")

    def palette_roles(self, s) -> dict[str, str]:
        return {"glow": glow_anchor_for(s)}

    def prepare(self, ctx: GenContext) -> None:
        """Plan the ore bodies once, independent of the layer switches."""
        s = ctx.settings
        # the host's level grid (replaced by the material layer; kept when it is off)
        ctx.data.setdefault("levels", np.full((ctx.h, ctx.w), self.K(ctx) // 2 + 1, dtype=np.int32))
        variant = s.variant if s.variant in ("cluster", "vein", "scattered") else "cluster"
        a = ctx.analysis
        # ore coverage: from the reference's accent fraction (already blended with
        # the built-in profile by source_influence) - a source without ore colours
        # falls back to the built-in profile
        frac, acl = _ore_profile(a, s.source_influence)
        cover = frac * (0.2 + 1.6 * s.density)
        csz = 0.45 + 1.1 * s.cluster_size          # 0.45 .. 1.55, 1.0 at 0.5
        if variant == "vein":
            bodies = self._plan_vein(ctx, cover, csz)
        elif variant == "scattered":
            bodies = self._plan_scattered(ctx, cover, csz)
        else:
            bodies = self._plan_clusters(ctx, cover, acl * 2.0 * csz)
        union = np.zeros((ctx.h, ctx.w), dtype=bool)
        for b in bodies:
            for x, y in b:
                union[y, x] = True
        ctx.data["ore_bodies"] = bodies
        ctx.data["ore_mask"] = union
        ctx.data["ore_variant"] = variant
        if self.recolor(ctx):
            # recolour mode: the ore is wherever the reference has it
            ctx.data["ore_bodies"] = []
            ctx.data["ore_mask"] = ~self.recolor_host(ctx)

    # ---------------------------------------------------------- planning
    def _clump_size(self, ctx: GenContext, rng, area16: float, lo16: float = 2.0) -> int:
        area = area16 * ctx.scale ** 1.4
        n = area * math.exp(rng.normal(0, 0.33))
        lo = max(2, lo16 * ctx.scale ** 1.2)
        return int(round(np.clip(n, lo, area * 2.2)))

    def _place(self, ctx: GenContext, rng, offs_list, count: int, occupied: np.ndarray,
               min_dist: float, gap: int = 1, near: np.ndarray | None = None,
               tries_per: int = 6) -> list[list[tuple[int, int]]]:
        """Place blob shapes at Poisson points without touching ``occupied``."""
        pts = noise.poisson_points(ctx.w, ctx.h, rng, max(1, count) * tries_per, min_dist, ctx.wrap)
        rng.shuffle(pts)
        if near is not None:
            pts = [p for p in pts if near[p[1], p[0]]] + [p for p in pts if not near[p[1], p[0]]]
        out = []
        k = 0
        for x, y in pts:
            if len(out) >= count or k >= len(offs_list):
                break
            cells = _cells_at(ctx, offs_list[k], x, y)
            if cells is None:
                continue
            block = occupied
            for _ in range(gap):
                block = pa.dilate(block, ctx.wrap, diagonal=True)
            if any(block[cy, cx] for cx, cy in cells):
                continue
            for cx, cy in cells:
                occupied[cy, cx] = True
            out.append(cells)
            k += 1
        return out

    def _plan_clusters(self, ctx: GenContext, cover: float, area16: float) -> list:
        s = ctx.settings
        rng = ctx.rng("ore_clusters")
        area16 = float(np.clip(area16, 3.0, 16.0))
        mean_area = area16 * ctx.scale ** 1.4
        count = max(1, int(round(cover * ctx.w * ctx.h / mean_area)))
        rough = 0.35 + 0.6 * s.roughness
        base_ang = math.radians(ctx.analysis.direction) if ctx.analysis.anisotropy > 0.2 else 0.0
        offs = []
        for _ in range(count * 2):
            n = self._clump_size(ctx, rng, area16)
            el = 1.15 + abs(rng.normal(0, 0.5))
            ang = base_ang + rng.choice([-0.785, 0.0, 0.785]) + rng.normal(0, 0.3)
            offs.append(blob_offsets(rng, n, el, ang, rough))
        occupied = np.zeros((ctx.h, ctx.w), dtype=bool)
        min_dist = math.sqrt(ctx.w * ctx.h / count) * 0.7
        bodies = self._place(ctx, rng, offs, count, occupied, min_dist, gap=1)
        if len(bodies) < count:  # crowded: fill up with smaller clumps
            small = [blob_offsets(rng, max(2, len(o) // 2), 1.2, rng.random() * 3, rough) for o in offs]
            bodies += self._place(ctx, rng, small, count - len(bodies), occupied, 1.5, gap=1)
        # a few small chips next to the big clumps (hand-placed look)
        n_chip = int(round(len(bodies) * (0.1 + 0.4 * s.density)))
        if n_chip:
            near = occupied.copy()
            for _ in range(max(2, ctx.px(3))):
                near = pa.dilate(near, ctx.wrap, True)
            near &= ~pa.dilate(pa.dilate(occupied, ctx.wrap, True), ctx.wrap, True)
            chips = [blob_offsets(rng, self._clump_size(ctx, rng, 2.4, 2.0), 1.3, rng.random() * 3, rough)
                     for _ in range(n_chip * 3)]
            bodies += self._place(ctx, rng, chips, n_chip, occupied, 2.0, gap=1, near=near)
        return bodies

    def _plan_scattered(self, ctx: GenContext, cover: float, csz: float) -> list:
        s = ctx.settings
        rng = ctx.rng("ore_scatter")
        g_area = float(np.clip(2.4 * (0.75 + 0.35 * csz), 2.0, 3.6))
        grain_area = g_area * max(1.0, ctx.scale) ** 1.0
        n_grains = max(2, int(round(cover * 0.9 * ctx.w * ctx.h / grain_area)))
        per_nest = 4 if ctx.scale <= 1 else 5
        nests = max(1, int(round(n_grains / per_nest)))
        centers = noise.poisson_points(ctx.w, ctx.h, rng, nests,
                                       math.sqrt(ctx.w * ctx.h / nests) * 0.85, ctx.wrap)
        occupied = np.zeros((ctx.h, ctx.w), dtype=bool)
        bodies = []
        radius = max(1.8, 2.3 * max(0.6, ctx.scale) ** 0.8)
        for (cx, cy) in centers:
            k = int(rng.integers(max(1, per_nest - 1), per_nest + 2))
            for _ in range(k):
                for _attempt in range(8):
                    ang = rng.random() * 2 * math.pi
                    r = radius * math.sqrt(rng.random())
                    x, y = int(round(cx + r * math.cos(ang))), int(round(cy + r * math.sin(ang)))
                    lo = 2 if ctx.scale <= 1 else max(2, int(round(1.5 * ctx.scale)))
                    n = int(np.clip(round(grain_area * math.exp(rng.normal(0, 0.25))), lo, grain_area * 1.8))
                    offs = blob_offsets(rng, n, 1.0 + abs(rng.normal(0, 0.3)), rng.random() * 3,
                                        0.2 + 0.4 * s.roughness)
                    cells = _cells_at(ctx, offs, x, y)
                    if cells is None:
                        continue
                    block = pa.dilate(occupied, ctx.wrap, diagonal=True)
                    if any(block[yy, xx] for xx, yy in cells):
                        continue
                    for xx, yy in cells:
                        occupied[yy, xx] = True
                    bodies.append(cells)
                    break
        return bodies

    def _plan_vein(self, ctx: GenContext, cover: float, csz: float) -> list:
        s = ctx.settings
        rng = ctx.rng("ore_vein")
        W, H = ctx.w, ctx.h
        mask = np.zeros((H, W), dtype=bool)
        sc = max(0.5, ctx.scale)
        options = [(2, 1), (2, -1)]
        if ctx.scale >= 2:
            options += [(3, 1), (3, -1)]
        n_veins = 1 if ctx.scale < 4 else 2
        # amount of vein: density slider scaled by the reference's ore coverage
        dens = float(np.clip(cover / 0.36, 0.0, 1.0))
        for vi in range(n_veins):
            a, b = options[int(rng.integers(len(options)))]
            steep = rng.random() < 0.2
            L = a * (H if steep else W)
            span = b * (W if steep else H)
            t = np.arange(L) / L
            thick0 = (1.5 + 0.9 * (csz - 0.45) / 1.1 + 0.5 * dens) * sc ** 0.75
            if vi > 0:
                thick0 *= 0.6
            thick = thick0 + _periodic_1d(rng, t, 3, 0.35 * thick0)
            wob = _periodic_1d(rng, t, 2, (0.7 + 0.8 * s.roughness) * sc ** 0.8)
            pres = _periodic_1d(rng, t, 4 if ctx.scale <= 1 else 6, 1.0)
            cut = np.quantile(pres, np.clip(0.55 - 0.6 * dens, 0.0, 0.7))
            y0 = rng.random() * (W if steep else H)
            for i in range(L):
                if pres[i] < cut:
                    continue
                th = max(1, int(round(thick[i])))
                yc = y0 + span * t[i] + wob[i]
                top = int(math.floor(yc - th / 2 + 0.5))
                for r in range(top, top + th):
                    if steep:
                        px, py = r, i
                    else:
                        px, py = i, r
                    if ctx.wrap:
                        mask[py % H, px % W] = True
                    elif 0 <= px < W and 0 <= py < H:
                        mask[py, px] = True
        # tidy: fill 1px notches, drop crumbs
        notch = (~mask) & (sum(pa.neighbour(mask, dx, dy, ctx.wrap, False).astype(int)
                                for dx, dy in pa.N4) >= 3)
        mask |= notch
        lab, sizes = pa.label_components(mask.astype(np.int32), ctx.wrap, mask)
        bodies = []
        for i, sz in enumerate(sizes):
            if sz < max(3, ctx.px(3)):
                continue
            ys, xs = np.nonzero(lab == i)
            bodies.append(sorted(zip(xs.tolist(), ys.tolist())))
        # satellites: small clumps hugging the vein
        n_sat = int(round((0.5 + 2.5 * dens) * ctx.scale ** 1.3))
        if n_sat > 0:
            near = pa.dilate(pa.dilate(pa.dilate(mask, ctx.wrap, True), ctx.wrap, True), ctx.wrap, True) & ~mask
            offs = [blob_offsets(rng, self._clump_size(ctx, rng, 3.0 * csz), 1.2, rng.random() * 3, 0.4)
                    for _ in range(n_sat * 2)]
            occ = mask.copy()
            bodies += self._place(ctx, rng, offs, n_sat, occ, 2.0, gap=1, near=near)
        return bodies

    # -------------------------------------------------------- host layers
    def layer_material(self, ctx: GenContext) -> None:
        super().layer_material(ctx)
        if self.recolor(ctx):
            return          # a recoloured host keeps its own tones
        # flatten the host a little so the ore pops: body levels 1..K -> 2..K.
        # Level 1 is then reserved for the shadow rims of the ore bodies and
        # the top highlight (K+1) is never used by the host.
        K = self.K(ctx)
        lv = ctx.data["levels"]
        if K >= 4:
            new = 2 + np.rint((lv - 1) * (K - 2) / max(1, K - 1)).astype(np.int32)
            ctx.canvas.level[:] = new
            ctx.data["levels"] = new

    def layer_cracks(self, ctx: GenContext) -> None:
        if self.recolor(ctx):
            return
        avoid = pa.dilate(ctx.data["ore_mask"], ctx.wrap, diagonal=True)
        cracks = self.crack_paths(ctx, ctx.settings.cracks * 0.8, mask=~avoid)
        cracks = pa.remove_small_clusters(cracks.astype(np.int32), 3, ctx.wrap).astype(bool) & ~avoid
        self.paint_cracks(ctx, cracks)

    def layer_highlights(self, ctx: GenContext) -> None:
        s = ctx.settings
        if self.recolor(ctx):
            super().layer_highlights(ctx)
            ctx.data["ore_extra_glints"] = True
            return
        self.bevel(ctx, ctx.data["levels"], +1, 0.25 + 0.35 * s.roughness, ~ctx.canvas.tag("crack"),
                   tag="lit", ceil=self.K(ctx))
        ctx.data["ore_extra_glints"] = True

    def layer_shadows(self, ctx: GenContext) -> None:
        s = ctx.settings
        if self.recolor(ctx):
            ctx.data["ore_deep_shadow"] = True
            return
        self.bevel(ctx, ctx.data["levels"], -1, 0.25 + 0.4 * s.roughness, ~ctx.canvas.tag("crack"),
                   floor=1)
        ctx.data["ore_deep_shadow"] = True

    # -------------------------------------------------------------- ore
    def ramp_lengths(self, k: int) -> dict[str, int]:
        d = super().ramp_lengths(k)
        d["accent"] = 6       # 0 deep, 1-4 body window, 5 glint
        return d

    def _ore_tones(self, ctx: GenContext, union: np.ndarray) -> dict[str, int]:
        """Choose which accent levels the ore uses so it contrasts with the host.

        * normally a 3-level window of the ramp (dark / body / lit, plus the
          top level for glints) placed for maximum contrast against the rock
          around the ore - bright ores on dark rock use the upper part of the
          ramp, dark ores on pale rock the lower part;
        * dark, low-chroma "metal" ramps (manganese) on rock as light as their
          silver tones switch to a specular scheme: near-black body with a
          hard silver rim light, the way coal-like minerals read on stone.
        """
        c = ctx.canvas
        n = c.length_of("accent")
        top = n - 1
        pal = ctx.palette
        lab = rgb_to_oklab(np.array(pal.ramp("accent").colors, dtype=np.float64))
        acc = lab[:, 0]
        chroma = np.hypot(lab[:, 1], lab[:, 2])
        base = luminance(np.array(pal.ramp("base").colors, dtype=np.float64))
        ring = pa.outline(union, ctx.wrap, diagonal=True) & (c.ramp == 0)
        if not ring.any():
            ring = c.ramp == 0
        host_l = float(np.mean(base[np.clip(c.level[ring], 0, len(base) - 1)])) if ring.any() \
            else float(np.mean(base))
        ctx.data["ore_host_l"] = host_l
        body = slice(1, min(4, n))
        dark_metal = float(np.mean(acc[body])) < 0.42 and float(np.mean(chroma[body])) < 0.05
        if dark_metal and n >= 5:
            if float(acc[top - 2]) < host_l + 0.1:
                # pale rock: black body with a silver rim light (coal-like)
                return {"mode": 2, "deep": 0, "dark": 0, "body": 1, "lit": top - 2, "lit2": top - 1,
                        "glint": top}
        best, best_score = 2, -1.0
        for mid in range(1, max(2, n - 2)):
            lv = np.array([mid - 1, mid, mid, mid + 1])
            contrast = float(np.mean(np.abs(acc[np.clip(lv, 0, top)] - host_l)))
            score = contrast - 0.02 * abs(mid - 2)
            if score > best_score + 1e-9:
                best, best_score = mid, score
        mid = int(best)
        return {"mode": 0, "deep": max(0, mid - 2), "dark": max(0, mid - 1), "body": mid,
                "lit": min(top, mid + 1), "lit2": min(top, mid + 1), "glint": top}

    def _recolor_ore(self, ctx: GenContext) -> None:
        """Recolour mode: the reference's own ore pixels, ranked by tone onto the accent ramp."""
        c = ctx.canvas
        ore = ~self.recolor_host(ctx)
        n = c.length_of("accent")
        lv = self.rank_levels(ctx.data["source_rgba"][..., :3], ore, 0, n - 1, False)
        c.set(ore, "accent", lv)
        c.tag("ore")[ore] = True
        c.tag("protect")[ore] = True
        if ctx.settings.glow > 0.02:
            self.glow_points(ctx, ctx.settings.glow, ore & (lv >= n - 2))

    def layer_accent(self, ctx: GenContext) -> None:
        if self.recolor(ctx):
            self._recolor_ore(ctx)
            return
        bodies = ctx.data.get("ore_bodies") or []
        if bodies:
            self.paint_ore(ctx, bodies, ctx.data["ore_mask"])

    def paint_ore(self, ctx: GenContext, bodies: list, union: np.ndarray,
                  grains: np.ndarray | None = None, glow: bool = True,
                  minerals: bool = True) -> dict:
        """Paint ore bodies (lists of pixels) onto the host rock.

        ``grains`` optionally gives a label map (-1 = none) of the crystal
        grains the bodies are made of; by default large bodies are split
        automatically.  Returns ``{"score", "glints", "tones", "grains"}``
        so callers (e.g. thermal crystals) can add their own touches.
        """
        c = ctx.canvas
        wrap = ctx.wrap
        extra = ctx.data.get("ore_extra_glints", False)
        deep = ctx.data.get("ore_deep_shadow", False)

        # ---- drop shadow in the host rock (bottom / right of the ore): one step
        # darker; with the shadow layer on also the diagonal and a deeper floor
        host = c.opaque & (c.ramp == 0) & ~c.tag("seam")
        below = pa.neighbour(union, 0, -1, wrap, False) & ~union & host
        right = pa.neighbour(union, -1, 0, wrap, False) & ~union & host
        rim = below | right
        c.shift(rim, -1, floor=1)
        if deep:
            diag = pa.neighbour(union, -1, -1, wrap, False) & ~union & ~rim & host
            c.shift(diag, -1, floor=2)
            c.shift(below, -1, floor=1)
            rim |= diag
        c.tag("protect")[rim] = True
        c.tag("ore_rim")[rim] = True

        # ---- ore body: rim lit from the top-left
        up_o = ~pa.neighbour(union, 0, -1, wrap, False)
        lf_o = ~pa.neighbour(union, -1, 0, wrap, False)
        dn_o = ~pa.neighbour(union, 0, 1, wrap, False)
        rt_o = ~pa.neighbour(union, 1, 0, wrap, False)
        score = up_o.astype(np.int32) + lf_o - dn_o - rt_o      # outer silhouette
        n_nb = sum((~o).astype(np.int32) for o in (up_o, lf_o, dn_o, rt_o))
        tones = self._ore_tones(ctx, union)
        ctx.data["ore_tones"] = tones
        rng = ctx.rng("ore_paint")
        # big bodies (32px and up) are aggregates of several crystal grains,
        # each lit on its own: that is where larger textures gain detail
        if grains is None:
            grains = self._split_grains(ctx, ctx.rng("ore_grains"), bodies)
        g_up = pa.neighbour(grains, 0, -1, wrap, -1) != grains
        g_lf = pa.neighbour(grains, -1, 0, wrap, -1) != grains
        g_dn = pa.neighbour(grains, 0, 1, wrap, -1) != grains
        g_rt = pa.neighbour(grains, 1, 0, wrap, -1) != grains
        gscore = g_up.astype(np.int32) + g_lf - g_dn - g_rt
        g_nb = 4 - (g_up.astype(np.int32) + g_lf + g_dn + g_rt)
        table = np.array([tones["dark"], tones["dark"], tones["body"], tones["lit"], tones["lit2"]])
        lv = table[np.clip(gscore, -2, 2) + 2]
        glints = np.zeros_like(union)
        big = 12 * max(1.0, ctx.scale) ** 1.4
        for cells in bodies:
            m = _mask_of(ctx, cells)
            n = len(cells)
            if n <= 3:
                # tiny grains: a lit pixel and body colour, no dark side
                lv[m] = np.maximum(lv[m], tones["body"])
            elif n >= 6:
                # the far bottom-right corner drops into the deepest tone
                corner = m & dn_o & rt_o & ~up_o & ~lf_o
                lv[corner] = tones["deep"]
            # glint(s)
            want = 1 if n >= 4 else (1 if rng.random() < 0.4 else 0)
            if extra and n >= big:
                want += 1 + int(n >= 2.5 * big)
            if n >= 30:
                want += int(n // (28 * max(1.0, ctx.scale) ** 0.6))
            self._glints(ctx, rng, cells, score, n_nb, glints, want)
        # facet: the interior of each sizeable grain splits into a lit and a
        # shaded face; some grains sit one step darker for variety
        ids = np.unique(grains[grains >= 0])
        for gid in ids.tolist():
            gm = grains == gid
            inner = gm & (g_nb == 4)
            if inner.sum() >= 3:
                iy, ix = np.nonzero(gm)
                cx, cy = self._centroid(ctx, ix.astype(np.float64), iy.astype(np.float64))
                jy, jx = np.nonzero(inner)
                ddx = self._wrapdelta(ctx, jx - cx, ctx.w)
                ddy = self._wrapdelta(ctx, jy - cy, ctx.h)
                face = (ddx + ddy) > 0.0
                lv[jy[face], jx[face]] = tones["dark"]
            if len(ids) > len(bodies) and rng.random() < 0.25:
                body_px = gm & (gscore == 0)
                lv[body_px] = tones["dark"]
        c.set(union, "accent", lv)
        c.set(glints, "accent", tones["glint"])
        c.tag("protect")[union] = True
        c.tag("ore")[union] = True
        ctx.data["ore_glints"] = glints

        # ---- secondary mineral (accent2) tint inside clumps + stray grains
        if minerals:
            self._minerals(ctx, bodies, union, score, glints)
        # ---- emissive glints
        if glow:
            self._ore_glow(ctx, bodies, union, score, glints)
        return {"score": score, "glints": glints, "tones": tones, "grains": grains}

    # ----------------------------------------------------------- helpers
    def _split_grains(self, ctx, rng, bodies) -> np.ndarray:
        """Label map (-1 = no ore) dividing large bodies into crystal grains."""
        lab = np.full((ctx.h, ctx.w), -1, dtype=np.int32)
        gpx = 6.0 * max(1.0, ctx.scale) ** 0.8
        gid = 0
        for cells in bodies:
            n = len(cells)
            k = int(round(n / gpx))
            pts = np.array(cells, dtype=np.float64)
            if k <= 1 or ctx.scale < 2:
                for x, y in cells:
                    lab[y, x] = gid
                gid += 1
                continue

            def dist(i):
                dx = np.abs(self._wrapdelta(ctx, pts[:, 0] - pts[i, 0], ctx.w))
                dy = np.abs(self._wrapdelta(ctx, pts[:, 1] - pts[i, 1], ctx.h))
                return np.hypot(dx * 1.15, dy)

            seeds = [int(rng.integers(n))]
            d = dist(seeds[0])
            for _ in range(k - 1):
                far = int(np.argmax(d + rng.random(n) * 0.8))
                seeds.append(far)
                d = np.minimum(d, dist(far))
            dd = np.stack([dist(i) for i in seeds], axis=0) + rng.random((len(seeds), n)) * 0.35
            assign = np.argmin(dd, axis=0)
            for (x, y), g in zip(cells, assign.tolist()):
                lab[y, x] = gid + g
            gid += len(seeds)
        # tidy: a grain pixel with no same-grain 4-neighbour joins its neighbour
        for _ in range(2):
            same = np.zeros(lab.shape, dtype=np.int32)
            for dx, dy in pa.N4:
                same += (pa.neighbour(lab, dx, dy, ctx.wrap, -1) == lab)
            lone = (lab >= 0) & (same == 0)
            if not lone.any():
                break
            for y, x in zip(*np.nonzero(lone)):
                for dx, dy in pa.N4:
                    nx, ny = (x + dx) % ctx.w, (y + dy) % ctx.h
                    if lab[ny, nx] >= 0:
                        lab[y, x] = lab[ny, nx]
                        break
        return lab

    def _wrapdelta(self, ctx, d, n):
        if ctx.wrap:
            return (d + n / 2) % n - n / 2
        return d

    def _centroid(self, ctx, xs, ys):
        if not ctx.wrap:
            return xs.mean(), ys.mean()
        # circular mean so bodies across the seam get a sensible centre
        ax = np.angle(np.exp(2j * np.pi * xs / ctx.w).mean()) / (2 * np.pi) * ctx.w % ctx.w
        ay = np.angle(np.exp(2j * np.pi * ys / ctx.h).mean()) / (2 * np.pi) * ctx.h % ctx.h
        return ax, ay

    def _glints(self, ctx, rng, cells, score, n_nb, glints, want):
        """Pick ``want`` glint pixels on a body: the first near its upper-left,
        a little inside the rim; further ones spread out along lit edges."""
        if want <= 0 or not cells:
            return
        pts = np.array(cells, dtype=np.int64)
        xs, ys = pts[:, 0], pts[:, 1]
        sc, nb = score[ys, xs], n_nb[ys, xs]
        ok = (sc >= 0) & (nb >= 2)
        if not ok.any():
            ok[:] = True
        cx, cy = self._centroid(ctx, xs.astype(np.float64), ys.astype(np.float64))
        dx = self._wrapdelta(ctx, xs - cx, ctx.w)
        dy = self._wrapdelta(ctx, ys - cy, ctx.h)
        local = -0.6 * sc - 0.35 * nb + rng.random(len(xs)) * 1.2
        k = np.where(ok, (dx + dy) * 0.8 + local, np.inf)
        k_rest = np.where(ok, local, np.inf)
        spacing = max(3, ctx.px(3))
        for _ in range(want):
            i = int(np.argmin(k))
            if not np.isfinite(k[i]):
                break
            glints[ys[i], xs[i]] = True
            near = np.maximum(np.abs(self._wrapdelta(ctx, xs - xs[i], ctx.w)),
                              np.abs(self._wrapdelta(ctx, ys - ys[i], ctx.h))) < spacing
            k_rest = np.where(near, np.inf, k_rest)
            k = k_rest

    def _distinct_accent2(self, ctx) -> bool:
        lab1 = rgb_to_oklab(np.array(ctx.palette.ramp("accent").colors, dtype=np.float64))
        lab2 = rgb_to_oklab(np.array(ctx.palette.ramp("accent2").colors, dtype=np.float64))
        d = np.hypot(lab1[:, 1].mean() - lab2[:, 1].mean(), lab1[:, 2].mean() - lab2[:, 2].mean())
        return bool(d > 0.03)

    def _minerals(self, ctx, bodies, union, score, glints):
        s, c = ctx.settings, ctx.canvas
        amt = s.mineral
        if amt <= 0.02:
            return
        rng = ctx.rng("ore_mineral")
        n2 = c.length_of("accent2")
        tint = np.zeros_like(union)
        for cells in bodies:
            n = len(cells)
            if n < 4 or rng.random() > min(1.0, 0.1 + amt * 1.4):
                continue
            # grow a small patch from the shaded side of the body
            dark = [(x, y) for x, y in cells if score[y, x] <= -1 and not glints[y, x]]
            if not dark:
                continue
            start = dark[int(rng.integers(len(dark)))]
            size = max(1, int(round(n * (0.12 + 0.4 * amt))))
            blocked = ~union | glints | (score >= 1)
            patch = pa.grow_cluster(rng, start, size, ctx.w, ctx.h, ctx.wrap, blocked, 0.7)
            for x, y in patch:
                tint[y, x] = True
        if tint.any() and self._distinct_accent2(ctx):
            # hue change, not value change: each ore level maps to the accent2
            # level of closest lightness
            acc_l = luminance(np.array(ctx.palette.ramp("accent").colors, dtype=np.float64))
            a2_l = luminance(np.array(ctx.palette.ramp("accent2").colors, dtype=np.float64))
            match = np.argmin(np.abs(acc_l[:, None] - a2_l[None, :]), axis=1)
            cur = np.clip(c.level, 0, len(match) - 1)
            # one step deeper than the value match keeps the tint faint
            c.set(tint, "accent2", np.maximum(0, match[cur] - 1))
            c.tag("mineral")[tint] = True
        # stray accent2 grains in the host around the ore (only when accent2 is
        # a colour of its own - darker ore specks would just read as noise)
        if not self._distinct_accent2(ctx):
            return
        count = int(round(amt * 4 * ctx.scale ** 1.6 * (0.6 + 0.8 * s.density)))
        if count <= 0:
            return
        free = ~pa.dilate(pa.dilate(union, ctx.wrap, True), ctx.wrap, True) & c.opaque & ~c.tag("ore_rim")
        near = union
        for _ in range(4):
            near = pa.dilate(near, ctx.wrap, True)
        near &= free
        pool = near if near.any() else free
        ys, xs = np.nonzero(pool)
        if len(xs) == 0:
            return
        taken = np.zeros_like(union)
        a2_l = luminance(np.array(ctx.palette.ramp("accent2").colors, dtype=np.float64))
        host_l = ctx.data.get("ore_host_l", 0.4)
        base2 = int(np.clip(np.argmin(np.abs(a2_l - (host_l + 0.08))), 0, max(0, n2 - 2)))
        for _ in range(count):
            i = int(rng.integers(len(xs)))
            x, y = int(xs[i]), int(ys[i])
            size = 2 if ctx.scale <= 1 else int(rng.integers(2, 4))
            cells = pa.grow_cluster(rng, (x, y), size, ctx.w, ctx.h, ctx.wrap,
                                    ~free | pa.dilate(taken, ctx.wrap, True), 0.8)
            if len(cells) < 2:
                continue
            m = _mask_of(ctx, cells)
            taken |= m
            c.set(m, "accent2", base2)
            if amt > 0.5:
                c.set(self._pt(ctx, *cells[0]), "accent2", min(n2 - 1, base2 + 1))
            c.tag("protect")[m] = True
            c.tag("mineral")[m] = True

    def _ore_glow(self, ctx, bodies, union, score, glints):
        s, c = ctx.settings, ctx.canvas
        g = s.glow
        if g <= 0.02:
            return
        rng = ctx.rng("ore_glow")
        ng = c.length_of("glow")
        em = glints.copy()
        c.set(glints, "glow", ng - 1)
        if g >= 0.25:
            lit = union & (score >= 1) & ~glints & (c.ramp == 2)
            pick = lit & (rng.random(lit.shape) < (g - 0.15) * 1.3)
            # keep picks in runs: grow each pick one step along the rim
            pick |= pa.neighbour(pick, 1, 0, ctx.wrap, False) & lit & (rng.random(lit.shape) < 0.5)
            c.set(pick, "glow", ng - 2)
            em |= pick
        if g >= 0.6:
            core = union & ~em & (c.ramp == 2)
            lvg = np.where(score >= 1, ng - 2, np.where(score <= -1, 0, 1))
            c.set(core, "glow", lvg)
            em |= core
        c.emissive |= em
        c.tag("protect")[em] = True
