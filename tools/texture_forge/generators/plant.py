"""Plants: deep-sea flora drawn as Minecraft cross-model sprites.

Variants: ``fern``, ``grass`` (tuft), ``bush``, ``vine`` (hangs from the top
edge) and ``bulb`` (stalk with a bulbous head).

Unlike the block generators, plants are *drawn*, not quantized from noise:
the base layer lays out the plant as a set of strokes (stems, blades,
leaflets, heads) on a small "sprite model", and every later layer only
changes the tone of that model (gradient along each stroke, depth, rim
lighting, veins, glow...).  After each layer the model is re-committed to
the indexed canvas, so any layer can be switched off and the plant still
reads cleanly.  The stroke/tone toolkit here (``_Sprite`` & friends) is
shared with the kelp generator.
"""
from __future__ import annotations

import math

import numpy as np

from core import noise
from core import pixel_art as pa
from core.analyzer import Silhouette, TextureAnalysis
from core.layers import ROLE_IDS, GenContext
from core.palette import hex_to_rgb, lch_to_oklab, oklab_to_lch, oklab_to_rgb, rgb_to_hex, rgb_to_oklab
from core.settings import TextureSettings

from .base import BaseGenerator

STEM, LEAF, HEAD, MASS = 0, 1, 2, 3
_ROLES = ("base", "secondary", "accent", "accent2", "glow")

# theme -> body ramp for plants (when the user did not pick a base colour)
_PLANT_BASE = {
    "deep_ocean": "deep_kelp", "abyss": "abyssal_grass", "trench": "abyssal_kelp",
    "thermal": "thermal_kelp", "volcanic": "thermal_kelp", "crystal": "abyssal_kelp",
    "bioluminescent": "abyssal_grass", "ancient": "giant_kelp", "cold": "deep_kelp",
    "organic": "kelp_green",
}
# body ramp -> darker stem colour (secondary ramp)
_STEM_HEX = {
    "deep_kelp": "#30523A", "abyssal_grass": "#28483F", "abyssal_kelp": "#352E5A",
    "thermal_kelp": "#5A2A18", "giant_kelp": "#5A4420", "kelp_green": "#556428",
}
# accents for bulbs / buds where the theme accent would vanish into the body
_PLANT_ACCENT = {
    "deep_ocean": "coral_pink", "abyss": "amethyst", "trench": "amethyst",
    "organic": "coral_pink", "cold": "blue_ice",
}


# =============================================================== toolkit


def _derived_hex(color: str, dl: float = -0.09, dc: float = 0.85, dh: float = -12.0) -> str:
    """A darker / shifted sibling of ``color`` (used for stems when base_color is set)."""
    try:
        lab = rgb_to_oklab(np.array(hex_to_rgb(color), dtype=np.float64))
    except (ValueError, IndexError):
        return "#303830"
    lch = oklab_to_lch(lab)
    lch = np.array([max(0.12, lch[0] + dl), lch[1] * dc, (lch[2] + dh) % 360])
    return rgb_to_hex(oklab_to_rgb(lch_to_oklab(lch)))


def _silhouette(ctx: GenContext) -> tuple[Silhouette, float]:
    """Measured silhouette and how much it should steer structure (0 = not at all)."""
    a = ctx.analysis
    if a.is_default or not a.has_alpha:
        return TextureAnalysis.default_for(ctx.settings.category).silhouette, 0.0
    return a.silhouette, float(np.clip(ctx.settings.source_influence, 0, 1))


def _nb(a: np.ndarray, dx: int, dy: int, wrap_y: bool, fill) -> np.ndarray:
    """Value of the neighbour at (dx, dy); x never wraps, y optionally does."""
    if wrap_y:
        return pa.neighbour(np.roll(a, -dy, axis=0), dx, 0, False, fill)
    return pa.neighbour(a, dx, dy, False, fill)


def _turtle(x: float, y: float, ang: float, length: float, turn: float = 0.0, turn_pow: float = 1.0,
            wobble: float = 0.0, wob_freq: float = 1.0, phase: float = 0.0,
            step: float = 0.2) -> tuple[np.ndarray, np.ndarray]:
    """Points along a curve that starts at (x, y) heading ``ang`` (radians, y down).

    ``turn`` is the total heading change reached at the tip (distributed with
    ``u ** turn_pow``), ``wobble`` adds a sinusoidal meander of the heading.
    Returns ``(points (n, 2), u (n,))`` with ``u`` running 0..1 base to tip.
    """
    length = max(0.5, float(length))
    n = max(3, int(math.ceil(length / step)) + 1)
    u = np.linspace(0.0, 1.0, n)
    heading = ang + turn * u ** turn_pow + wobble * np.sin(2 * np.pi * wob_freq * u + phase)
    ds = length / (n - 1)
    xs = x + np.concatenate([[0.0], np.cumsum(np.cos(heading[:-1]) * ds)])
    ys = y + np.concatenate([[0.0], np.cumsum(np.sin(heading[:-1]) * ds)])
    return np.stack([xs, ys], axis=1), u


def _pp_cells(pts: np.ndarray, u: np.ndarray) -> tuple[list[tuple[int, int]], list[float]]:
    """Pixel-perfect 8-connected cells along a curve (no L-shaped doubled corners)."""
    cells: list[tuple[int, int]] = []
    ts: list[float] = []

    def push(x: int, y: int, t: float) -> None:
        if len(cells) >= 2:
            ax, ay = cells[-2]
            if abs(ax - x) == 1 and abs(ay - y) == 1:
                cells.pop()
                ts.pop()
        if cells and cells[-1] == (x, y):
            return
        cells.append((x, y))
        ts.append(t)

    fx = np.floor(pts[:, 0]).astype(int).tolist()
    fy = np.floor(pts[:, 1]).astype(int).tolist()
    for x, y, t in zip(fx, fy, u.tolist()):
        if cells and cells[-1] == (x, y):
            continue
        if cells:
            px, py = cells[-1]
            if abs(x - px) > 1 or abs(y - py) > 1:
                for bx, by in pa.line_points(px, py, x, y)[1:-1]:
                    push(bx, by, t)
        push(x, y, t)
    return cells, ts


def _offsets(W: int) -> list[int]:
    """Perpendicular pixel offsets that thicken a 1-px line to ``W`` pixels."""
    if W <= 1:
        return []
    lo = -((W - 1) // 2)
    return [k for k in range(lo, lo + W) if k != 0] if W >= 3 else [1]


class _Sprite:
    """A small drawing model: which stroke ("element") owns each pixel.

    Elements carry a part kind, colour role, depth ``z`` and base tone; the
    tone state (``body`` 0..1 plus integer ``rim`` steps and overrides) is
    what layers edit and :meth:`commit` resolves to canvas levels.
    """

    def __init__(self, w: int, h: int, wrap_y: bool = False):
        self.w, self.h, self.wrap_y = w, h, wrap_y
        self.elem = np.full((h, w), -1, dtype=np.int32)
        self.t = np.zeros((h, w))
        self.core = np.zeros((h, w), dtype=bool)
        self.width = np.ones((h, w))          # stroke width at that pixel (in pixels)
        self.face = np.zeros((h, w))          # -1 far side .. +1 side facing the light
        self.hy = np.zeros((h, w))            # 0 at the root .. 1 at the far end of the plant
        self.rho = np.ones((h, w))            # normalised radius inside blobs (0 centre .. 1 rim)
        self.els: list[dict] = []
        self.body = np.full((h, w), 0.5)
        self.rim = np.zeros((h, w), dtype=np.int32)
        self.over_role = np.full((h, w), -1, dtype=np.int32)
        self.over_level = np.zeros((h, w), dtype=np.int32)
        self.protect = np.zeros((h, w), dtype=bool)
        self.emissive = np.zeros((h, w), dtype=bool)
        self.tips: list[tuple[int, int, int]] = []   # (x, y, element) of stroke ends
        self.anchor_bottom = False    # strokes continue below the bottom edge (rooted)
        self.anchor_top = False       # strokes continue above the top edge (hanging)

    # ------------------------------------------------------------ queries
    @property
    def vis(self) -> np.ndarray:
        return self.elem >= 0

    def prop(self, key: str, default=0.0) -> np.ndarray:
        """Per-pixel map of an element property (``default`` where empty)."""
        vals = np.array([e.get(key, default) for e in self.els] + [default], dtype=np.float64)
        idx = np.where(self.elem >= 0, self.elem, len(self.els))
        return vals[idx]

    def part_mask(self, *parts: int) -> np.ndarray:
        p = self.prop("part", -1)
        return np.isin(p, parts) & self.vis

    def role_map(self) -> np.ndarray:
        rid = np.array([ROLE_IDS[e["role"]] for e in self.els] + [-1], dtype=np.int32)
        return rid[np.where(self.elem >= 0, self.elem, len(self.els))]

    def nb(self, a: np.ndarray, dx: int, dy: int, fill) -> np.ndarray:
        return _nb(a, dx, dy, self.wrap_y, fill)

    def thin(self, mask: np.ndarray | None = None) -> np.ndarray:
        """Pixels with at most one opaque 4-neighbour (tips, diagonal steps)."""
        m = self.vis if mask is None else mask
        cnt = sum(self.nb(m, dx, dy, False).astype(np.int32) for dx, dy in pa.N4)
        return m & (cnt <= 1)

    # ------------------------------------------------------------ drawing
    def add(self, mask: np.ndarray, t: np.ndarray | float, part: int, role: str, z: float = 0.0,
            tone: float = 0.5, core: np.ndarray | None = None, width: np.ndarray | float = 1.0,
            behind: bool = False, face: np.ndarray | float = 0.0, **meta) -> int:
        """Add an element; it covers pixels of elements with a lower ``z``."""
        m = mask.copy()
        if self.els:
            ez = self.prop("z", -1e9)
            m &= (self.elem < 0) | ((ez < z) if behind else (ez <= z))
        i = len(self.els)
        self.els.append(dict(part=part, role=role, z=z, tone=tone, **meta))
        self.elem[m] = i
        self.t[m] = t[m] if isinstance(t, np.ndarray) else t
        self.core[m] = core[m] if core is not None else False
        self.width[m] = width[m] if isinstance(width, np.ndarray) else width
        self.face[m] = face[m] if isinstance(face, np.ndarray) else face
        self.body[m] = 0.5
        self.rim[m] = 0
        self.over_role[m] = -1
        self.emissive[m] = False
        return i

    def crowding(self, mask: np.ndarray, zmin: float = -1e9, upper: float = 0.0,
                 tmap: np.ndarray | None = None) -> float:
        """Fraction of ``mask`` touching visible pixels of elements with ``z >= zmin``.

        Only pixels with ``t >= upper`` are counted (so shared roots are allowed).
        """
        if not self.els:
            return 0.0
        occ = self.vis & (self.prop("z", -1e9) >= zmin)
        near = occ.copy()
        for dx, dy in pa.N4:
            near |= self.nb(occ, dx, dy, False)
        m = mask if tmap is None else mask & (tmap >= upper)
        return float((m & near).sum()) / max(1, int(m.sum()))

    def stroke(self, pts: np.ndarray, u: np.ndarray, widths, part: int, role: str,
               grow: tuple[float, float] = (0.0, 1.0), z: float = 0.0, tone: float = 0.5,
               behind: bool = False, tip: bool = True, pre=None, **meta) -> int:
        """Rasterise a (tapered) stroke and add it as an element."""
        mask, tmap, core, wmap, fmap = pre if pre is not None else self.raster(pts, u, widths, grow)
        if not mask.any():
            return -1
        i = self.add(mask, tmap, part, role, z, tone, core, wmap, behind, fmap, **meta)
        if tip:
            cells, _ = _pp_cells(pts, u)
            for x, y in reversed(cells):
                yy = y % self.h if self.wrap_y else y
                if 0 <= x < self.w and 0 <= yy < self.h:
                    self.tips.append((x, yy, i))
                    break
        return i

    def raster(self, pts: np.ndarray, u: np.ndarray, widths, grow=(0.0, 1.0)):
        """Rasterise a stroke -> ``(mask, t, core, width, face)`` maps.

        ``face`` is the position across the stroke seen from the light
        (+1 on the side facing the top-left, -1 on the far side, 0 on the
        centre line), which is what shading uses instead of outline tests.
        """
        h, w = self.h, self.w
        wv = np.broadcast_to(np.asarray(widths, dtype=np.float64), u.shape)
        mask = np.zeros((h, w), dtype=bool)
        tmap = np.zeros((h, w))
        core = np.zeros((h, w), dtype=bool)
        wmap = np.ones((h, w))
        face = np.zeros((h, w))
        cells, ts = _pp_cells(pts, u)
        if not cells:
            return mask, tmap, core, wmap, face
        arr = np.array(cells)
        tarr = np.array(ts)
        W = np.interp(tarr, u, wv)

        def put(xs, ys, tv, wv_, fv, is_core, override=True):
            xs = np.asarray(xs, dtype=np.int64)
            ys = np.asarray(ys, dtype=np.int64)
            if self.wrap_y:
                ys = ys % h
            ok = (xs >= 0) & (xs < w) & (ys >= 0) & (ys < h)
            xs, ys = xs[ok], ys[ok]
            tv = np.broadcast_to(np.asarray(tv, dtype=np.float64), ok.shape)[ok]
            wv_ = np.broadcast_to(np.asarray(wv_, dtype=np.float64), ok.shape)[ok]
            fv = np.broadcast_to(np.asarray(fv, dtype=np.float64), ok.shape)[ok]
            new = ~mask[ys, xs]
            # keep the lowest t where a stroke overlaps itself
            upd = (new | (tmap[ys, xs] > tv)) if override else new
            tmap[ys[upd], xs[upd]] = tv[upd]
            wmap[ys[upd], xs[upd]] = wv_[upd]
            face[ys[upd], xs[upd]] = fv[upd]
            mask[ys, xs] = True
            if is_core:
                core[ys, xs] = True

        L = (-0.70710678, -0.70710678)          # toward the light (top-left)
        if W.max() <= 3.5:
            n = len(arr)
            k = 2
            cf = np.zeros(n)
            ex, ey, et, ew, ef = [], [], [], [], []
            for i in range(n):
                Wi = int(round(W[i]))
                if Wi <= 1:
                    continue
                a, b = arr[max(0, i - k)], arr[min(n - 1, i + k)]
                tx, ty = b[0] - a[0], b[1] - a[1]
                if abs(tx) >= abs(ty):
                    ax, ay, sg = 0, 1, (1 if grow[1] >= 0 else -1)
                else:
                    ax, ay, sg = 1, 0, (1 if grow[0] >= 0 else -1)
                offs = _offsets(Wi)
                ks = np.array([0] + offs, dtype=np.float64)
                pos = (ks - ks.mean()) / (Wi / 2.0)
                dv = (ax * L[0] + ay * L[1]) * sg
                cf[i] = pos[0] * dv
                for o, pz in zip(offs, pos[1:]):
                    ex.append(arr[i, 0] + ax * o * sg)
                    ey.append(arr[i, 1] + ay * o * sg)
                    et.append(tarr[i])
                    ew.append(W[i])
                    ef.append(pz * dv)
            put(arr[:, 0], arr[:, 1], tarr, W, cf, True)
            if ex:
                put(ex, ey, et, ew, ef, False)
        else:
            # distance field: union of discs along the curve (larger textures).
            # The curve is processed in short chunks, each against its own small box.
            r = wv * 0.5
            step = max(1, int(round(0.6 / max(1e-6, float(np.median(np.hypot(*np.diff(pts, axis=0).T))) if len(pts) > 1 else 1))))
            sel = np.arange(0, len(pts), step)
            if sel[-1] != len(pts) - 1:
                sel = np.r_[sel, len(pts) - 1]
            P, R, U = pts[sel], r[sel], u[sel]
            best = np.full((h, w), np.inf)
            bi = np.full((h, w), -1, dtype=np.int64)
            K = 12
            for j in range(0, len(P), K):
                cp, cr = P[j:j + K + 1], R[j:j + K + 1]
                pad = float(cr.max()) + 1.0
                x0 = max(0, int(math.floor(cp[:, 0].min() - pad)))
                x1 = min(w, int(math.ceil(cp[:, 0].max() + pad)) + 1)
                y0 = int(math.floor(cp[:, 1].min() - pad))
                y1 = int(math.ceil(cp[:, 1].max() + pad)) + 1
                if not self.wrap_y:
                    y0, y1 = max(0, y0), min(h, y1)
                if x1 <= x0 or y1 <= y0:
                    continue
                gy, gx = np.mgrid[y0:y1, x0:x1]
                d = np.hypot(gx[..., None] + 0.5 - cp[:, 0], gy[..., None] + 0.5 - cp[:, 1]) - cr
                kk = np.argmin(d, axis=-1)
                dm = np.take_along_axis(d, kk[..., None], axis=-1)[..., 0]
                yy = gy % h if self.wrap_y else gy
                cur = best[yy, gx]
                better = dm < cur
                best[yy[better], gx[better]] = dm[better]
                bi[yy[better], gx[better]] = kk[better] + j
            inside = best < -0.02
            if inside.any():
                iy, ix = np.nonzero(inside)
                bsel = bi[iy, ix]
                ox = ix + 0.5 - P[bsel, 0]
                oy = iy + 0.5 - P[bsel, 1]
                if self.wrap_y:
                    oy = (oy + h / 2) % h - h / 2
                fv = np.clip((ox * L[0] + oy * L[1]) / np.maximum(0.5, R[bsel]), -1, 1)
                put(ix, iy, U[bsel], np.interp(U[bsel], u, wv), fv, False)
            put(arr[:, 0], arr[:, 1], tarr, W, 0.0, True, override=False)
            mask &= _tidy(mask, self.wrap_y)
        return mask, tmap, core, wmap, face

    def raster_sides(self, pts: np.ndarray, u: np.ndarray, wl, wr, shade_w: float = 2.0):
        """Stroke with separate pixel widths on each side of the centre line.

        ``wl`` / ``wr`` (arrays over ``u``) are the pixel counts added on the
        negative / positive side of the dominant normal axis - used for
        notched, feather-like fronds whose pinnae alternate sides.
        """
        h, w = self.h, self.w
        mask = np.zeros((h, w), dtype=bool)
        tmap = np.zeros((h, w))
        core = np.zeros((h, w), dtype=bool)
        wmap = np.full((h, w), float(shade_w))
        face = np.zeros((h, w))
        cells, ts = _pp_cells(pts, u)
        if not cells:
            return mask, tmap, core, wmap, face
        arr = np.array(cells)
        tarr = np.array(ts)
        WL = np.rint(np.interp(tarr, u, np.broadcast_to(np.asarray(wl, dtype=np.float64), u.shape))).astype(int)
        WR = np.rint(np.interp(tarr, u, np.broadcast_to(np.asarray(wr, dtype=np.float64), u.shape))).astype(int)
        n = len(arr)
        xs, ys, tv, fv, cv = [], [], [], [], []
        for i in range(n):
            a, b = arr[max(0, i - 2)], arr[min(n - 1, i + 2)]
            tx, ty = b[0] - a[0], b[1] - a[1]
            ax, ay = (0, 1) if abs(tx) >= abs(ty) else (1, 0)
            xs.append(arr[i, 0]); ys.append(arr[i, 1]); tv.append(tarr[i]); fv.append(0.0); cv.append(True)
            for k in range(1, max(0, WL[i]) + 1):
                xs.append(arr[i, 0] - ax * k); ys.append(arr[i, 1] - ay * k)
                tv.append(tarr[i]); fv.append(0.5); cv.append(False)
            for k in range(1, max(0, WR[i]) + 1):
                xs.append(arr[i, 0] + ax * k); ys.append(arr[i, 1] + ay * k)
                tv.append(tarr[i]); fv.append(-0.5); cv.append(False)
        xs = np.array(xs); ys = np.array(ys)
        if self.wrap_y:
            ys = ys % h
        ok = (xs >= 0) & (xs < w) & (ys >= 0) & (ys < h)
        tv = np.array(tv)[ok]; fv = np.array(fv)[ok]; cv = np.array(cv)[ok]
        xs, ys = xs[ok], ys[ok]
        # centre line wins over side pixels of neighbouring cells
        order = np.argsort(cv, kind="stable")
        xs, ys, tv, fv, cv = xs[order], ys[order], tv[order], fv[order], cv[order]
        mask[ys, xs] = True
        tmap[ys, xs] = tv
        face[ys, xs] = fv
        core[ys[cv], xs[cv]] = True
        return mask, tmap, core, wmap, face

    def blob(self, cx: float, cy: float, rx: float, ry: float, part: int, role: str, z: float = 0.0,
             tone: float = 0.5, angle: float = 0.0, behind: bool = False, **meta) -> int:
        """An ellipse (bulbs, bladders, foliage masses). ``t`` = 0 bottom .. 1 top."""
        h, w = self.h, self.w
        ys, xs = np.mgrid[0:h, 0:w]
        dx = xs + 0.5 - cx
        dy = ys + 0.5 - cy
        if self.wrap_y:
            dy = (dy + h / 2) % h - h / 2
        ca, sa = math.cos(angle), math.sin(angle)
        ex = (dx * ca + dy * sa) / max(0.5, rx)
        ey = (-dx * sa + dy * ca) / max(0.5, ry)
        mask = ex * ex + ey * ey <= 1.0
        if not mask.any():
            iy, ix = int(math.floor(cy)) % h if self.wrap_y else int(math.floor(cy)), int(math.floor(cx))
            if 0 <= ix < w and 0 <= iy < h:
                mask[iy, ix] = True
        mask = _tidy(mask, self.wrap_y)
        t = np.clip(0.5 - ey * 0.5, 0, 1)
        fx, fy = dx / max(0.5, rx), dy / max(0.5, ry)
        fv = np.clip(-(fx + fy) * 0.70710678, -1, 1)
        i = self.add(mask, t, part, role, z, tone, None, 2 * min(rx, ry), behind, fv, **meta)
        self.rho[self.elem == i] = np.sqrt(ex * ex + ey * ey)[self.elem == i]
        return i

    def remove(self, mask: np.ndarray) -> None:
        self.elem[mask] = -1
        self.core[mask] = False
        self.over_role[mask] = -1
        self.protect[mask] = False
        self.emissive[mask] = False

    def drop_islands(self, min_size: int = 2) -> None:
        """Drop opaque specks (8-connected groups smaller than ``min_size``)."""
        vis = self.vis
        if not vis.any():
            return
        labels, sizes = pa.label_components(vis.astype(np.int32), wrap=False, mask=vis, connectivity=8)
        small = [i for i, s in enumerate(sizes) if s < min_size]
        if small:
            self.remove(np.isin(labels, small) & vis)

    # --------------------------------------------------------- lighting
    def edges(self):
        """Per-direction masks: neighbour empty / behind (lower z) / in front."""
        vis = self.vis
        z = self.prop("z", -1e9)
        out = {}
        for name, (dx, dy) in {"up": (0, -1), "down": (0, 1), "left": (-1, 0), "right": (1, 0)}.items():
            ne = self.nb(self.elem, dx, dy, -1)
            nz = self.nb(z, dx, dy, -1e9)
            if not self.wrap_y:
                if name == "down" and self.anchor_bottom:
                    ne[-1] = self.elem[-1]
                    nz[-1] = z[-1]
                if name == "up" and self.anchor_top:
                    ne[0] = self.elem[0]
                    nz[0] = z[0]
            other = vis & (ne != self.elem)
            empty = vis & (ne < 0)
            out[name] = (empty, other & (ne >= 0) & (nz < z), other & (ne >= 0) & (nz > z))
        return out

    # ------------------------------------------------------------ commit
    def commit(self, ctx: GenContext, extra_protect: np.ndarray | None = None) -> None:
        c = ctx.canvas
        c.clear_all()
        vis = self.vis
        if not vis.any():
            return
        roles = self.role_map()
        for name in _ROLES:
            rid = ROLE_IDS[name]
            m = vis & (roles == rid)
            if not m.any():
                continue
            n = c.length_of(name)
            lo, hi = (1, n - 2) if n >= 4 else (0, n - 1)
            lv = lo + np.rint(np.clip(self.body, 0, 1) * (hi - lo)).astype(np.int32) + self.rim
            c.set(m, name, np.clip(lv, 0, n - 1))
        ov = vis & (self.over_role >= 0)
        for name in _ROLES:
            m = ov & (self.over_role == ROLE_IDS[name])
            if m.any():
                c.set(m, name, np.clip(self.over_level, 0, c.length_of(name) - 1))
        c.emissive[:] = self.emissive & vis
        prot = self.protect | self.thin()
        if extra_protect is not None:
            prot = prot | extra_protect
        c.tag("protect")[:] = prot & vis


def _tidy(mask: np.ndarray, wrap_y: bool = False) -> np.ndarray:
    """Pixel-art outline cleanup: remove one-pixel nubs, fill one-pixel notches."""
    m = mask.copy()
    for _ in range(2):
        cnt = sum(_nb(m, dx, dy, wrap_y, False).astype(np.int32) for dx, dy in pa.N4)
        big = m & (cnt >= 3)
        nub = m & (cnt == 1)
        # a nub is a lone pixel sticking out of a thick body
        nub_on_body = np.zeros_like(m)
        for dx, dy in pa.N4:
            nub_on_body |= nub & _nb(big, dx, dy, wrap_y, False)
        notch = ~m & (cnt >= 3)
        new = (m & ~nub_on_body) | notch
        if (new == m).all():
            break
        m = new
    return m


def _smooth_noise(ctx: GenContext, key: str, freq: float, wrap: bool = False) -> np.ndarray:
    return noise.fbm(ctx.w, ctx.h, ctx.rng(key), max(1.0, freq), 2, 0.5, "value")


def _rng_sign(rng: np.random.Generator) -> int:
    return 1 if rng.random() < 0.5 else -1


# ------------------------------------------------------- shared layer logic


def _face_threshold(sp: "_Sprite") -> np.ndarray:
    """How far toward a stroke's edge the lit / shaded band starts (about one pixel)."""
    return np.maximum(0.2, 1.0 - 2.0 / np.maximum(1.0, sp.width))


def sprite_rim_light(sp: "_Sprite") -> np.ndarray:
    """Pixels on the side of each stroke / blob that faces the top-left light."""
    return sp.vis & (sp.face > _face_threshold(sp))


def sprite_rim_shadow(sp: "_Sprite") -> tuple[np.ndarray, np.ndarray]:
    """(far side of each stroke, shadow cast by an element in front above / left)."""
    far = sp.vis & (sp.face < -_face_threshold(sp))
    e = sp.edges()
    cast = (e["up"][2] | e["left"][2]) & ~sp.thin() & ~far
    return far, cast


def sprite_gloss(sp: "_Sprite", amount: float, rng: np.random.Generator,
                 parts: tuple[int, ...] = (1, 2)) -> np.ndarray:
    """Wet gloss (moisture): short bright streaks on the lit side of leaves / blades.

    On one-pixel strokes the streak runs along the stroke itself.  Each streak
    is two pixels long so it survives the clean-up filter as a deliberate mark.
    """
    out = np.zeros((sp.h, sp.w), dtype=bool)
    if amount <= 0.2 or not sp.els:
        return out
    lit = sprite_rim_light(sp)
    cand = (lit | sp.thin() | (sp.width < 1.5)) & sp.part_mask(*parts) & (sp.t > 0.3) & (sp.t < 0.93)
    ys, xs = np.nonzero(cand)
    if not len(xs):
        return out
    k = max(1, int(round(len(sp.els) * (amount - 0.2) * 0.9)))
    k = min(k, max(1, len(xs) // 6))
    for j in rng.choice(len(xs), size=min(k, len(xs)), replace=False):
        y, x = int(ys[j]), int(xs[j])
        e = sp.elem[y, x]
        out[y, x] = True
        # extend one pixel toward the tip of the same element
        best = None
        for dx, dy in pa.N8:
            nx, ny = x + dx, y + dy
            if sp.wrap_y:
                ny %= sp.h
            if 0 <= nx < sp.w and 0 <= ny < sp.h and sp.elem[ny, nx] == e and sp.t[ny, nx] > sp.t[y, x]:
                if best is None or sp.t[ny, nx] < sp.t[best[1], best[0]]:
                    best = (nx, ny)
        if best is not None:
            out[best[1], best[0]] = True
    sp.rim = np.where(out, sp.rim + 2, sp.rim)
    sp.protect |= out
    return out


def sprite_glow(sp: _Sprite, ctx: GenContext, cells: np.ndarray, levels: np.ndarray | int | None = None) -> None:
    """Paint ``cells`` in the glow ramp (emissive, protected)."""
    if not cells.any():
        return
    n = ctx.canvas.length_of("glow")
    if levels is None:
        levels = n - 2
    sp.over_role[cells] = ROLE_IDS["glow"]
    sp.over_level[cells] = np.broadcast_to(np.asarray(levels), cells.shape)[cells]
    sp.emissive |= cells
    sp.protect |= cells


def tip_cells(sp: _Sprite, count: int, rng: np.random.Generator, length: int = 1,
              parts: tuple[int, ...] = (LEAF,), prefer_high: bool = True) -> np.ndarray:
    """Mask of the last ``length`` pixels of ``count`` stroke tips."""
    out = np.zeros((sp.h, sp.w), dtype=bool)
    sizes = np.bincount(sp.elem[sp.elem >= 0], minlength=len(sp.els))
    cand = [(x, y, i) for x, y, i in sp.tips
            if 0 <= i < len(sp.els) and sp.els[i]["part"] in parts and sp.elem[y, x] == i
            and sizes[i] > 2 * length]
    if not cand or count <= 0:
        return out
    if prefer_high:
        cand.sort(key=lambda c: (c[1], c[0]))
        pool = cand[:max(count, int(len(cand) * 0.8) + 1)]
    else:
        pool = cand
    idx = rng.choice(len(pool), size=min(count, len(pool)), replace=False)
    for j in idx:
        x, y, i = pool[int(j)]
        el = sp.elem == i
        tt = np.where(el, sp.t, -1.0)
        # the top-most `length` pixels of this element by t
        vals = np.sort(tt[el])[::-1]
        if len(vals) == 0:
            continue
        cut = vals[min(len(vals) - 1, max(0, length - 1))]
        out |= el & (tt >= cut)
    return out


# ================================================================ generator


class PlantGenerator(BaseGenerator):
    category = "plant"

    # --------------------------------------------------------------- palette
    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        base = _PLANT_BASE.get(s.palette, "abyssal_grass")
        stem = _derived_hex(s.base_color) if s.base_color else _STEM_HEX.get(base, "#2A3A30")
        roles = {"base": base, "secondary": stem}
        acc = _PLANT_ACCENT.get(s.palette)
        if acc and not s.accent:
            roles["accent"] = acc
        return roles

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": max(4, k), "accent": 5, "accent2": 4, "glow": 4}

    # ---------------------------------------------------------- parameters
    def params(self, ctx: GenContext) -> dict:
        s = ctx.settings
        sil, ws = _silhouette(ctx)
        sc = ctx.scale
        ds = sc ** 0.65                      # feature scale: grows slower than the texture
        height = 0.36 + 0.64 * s.height
        density, leaf = s.density, s.leaf
        if ws > 0:
            height = height * (1 - 0.4 * ws) + float(np.clip(sil.top, 0.2, 1.0)) * 0.4 * ws
            density = float(np.clip(density + (sil.coverage - 0.3) * 0.6 * ws, 0, 1))
            leaf = float(np.clip(leaf + (sil.perimeter_ratio - 0.7) * 0.5 * ws, 0, 1))
        return dict(sc=sc, ds=ds, height=float(np.clip(height, 0.15, 1.0)), density=density, leaf=leaf,
                    stems_hint=int(sil.stems) if ws > 0 else 0, ws=ws, sil=sil,
                    organic=s.organic, wob=0.05 + 0.35 * s.organic + 0.25 * s.noise,
                    symmetry=sil.symmetry if ws > 0 else 0.4,
                    hgrad={"grass": 0.5, "fern": 0.35, "bush": 0.45, "vine": 0.35, "bulb": 0.3}.get(s.variant, 0.3))

    # ---------------------------------------------------------------- layers
    def layer_base(self, ctx: GenContext) -> None:
        ctx.canvas.clear_all()
        sp = _Sprite(ctx.w, ctx.h)
        sp.anchor_bottom = ctx.settings.variant != "vine"
        sp.anchor_top = ctx.settings.variant == "vine"
        P = self.params(ctx)
        build = {"fern": _build_fern, "grass": _build_grass, "bush": _build_bush,
                 "vine": _build_vine, "bulb": _build_bulb}.get(ctx.settings.variant, _build_grass)
        build(ctx, sp, P)
        sp.drop_islands(2)
        if not sp.vis.any():   # never return an empty sprite
            pts, u = _turtle(ctx.w / 2, ctx.h, -math.pi / 2, ctx.h * 0.5)
            sp.stroke(pts, u, 1, STEM, "secondary")
        rows = np.nonzero(sp.vis.any(axis=1))[0]
        y = np.arange(ctx.h, dtype=np.float64)[:, None] + 0.5
        if sp.anchor_top:
            span = max(1.0, rows.max() + 1.0)
            sp.hy = np.broadcast_to(np.clip(y / span, 0, 1), sp.hy.shape).copy()
        else:
            span = max(1.0, ctx.h - rows.min())
            sp.hy = np.broadcast_to(np.clip((ctx.h - y) / span, 0, 1), sp.hy.shape).copy()
        ctx.data["spr"] = sp
        ctx.data["P"] = P
        sp.commit(ctx)

    def layer_material(self, ctx: GenContext) -> None:
        """Tone gradient along every stroke (dark base -> light tip) + per-element tone."""
        s, sp = ctx.settings, ctx.data["spr"]
        rng = ctx.rng("plant_material")
        spread = 0.05 + 0.22 * s.roughness
        tones = np.array([e["tone"] + rng.uniform(-spread, spread) + rng.normal(0, 0.1 * s.noise)
                          for e in sp.els] + [0.5])
        idx = np.where(sp.elem >= 0, sp.elem, len(sp.els))
        grad = np.array([e.get("grad", 0.55) for e in sp.els] + [0.0])[idx]
        jitter = (_smooth_noise(ctx, "plant_mat_noise", 2 * ctx.scale ** 0.5) - 0.5) * 0.18 * s.noise
        jitter = np.where(sp.width >= 2.5, jitter, 0.0)
        hgrad = ctx.data["P"].get("hgrad", 0.3)
        body = tones[idx] + 0.1 + grad * (sp.t - 0.5) + hgrad * (sp.hy - 0.5) + jitter - 0.2 * s.moisture
        sp.body = np.where(sp.vis, np.clip(body, 0.0, 1.0), sp.body)
        sp.commit(ctx)

    def layer_large_detail(self, ctx: GenContext) -> None:
        """Depth: elements further back are darker; bushes get an inner foliage mass."""
        s, sp = ctx.settings, ctx.data["spr"]
        if ctx.settings.variant == "bush":
            _bush_mass(ctx, sp, ctx.data["P"])
        elif ctx.settings.variant == "grass":
            _grass_mass(ctx, sp, ctx.data["P"])
        back = sp.prop("back", 0.0) > 0.5
        sp.body = np.where(back, sp.body - 0.24, sp.body)
        mass = sp.part_mask(MASS)
        sp.body = np.where(mass, 0.16 + 0.3 * sp.hy - 0.1 * s.moisture, sp.body)
        sp.commit(ctx)

    def layer_medium_detail(self, ctx: GenContext) -> None:
        """Leaf mottling on broad leaves and node bands on stems."""
        s, sp = ctx.settings, ctx.data["spr"]
        wide = sp.vis & (sp.width >= 2.5) & sp.part_mask(LEAF, MASS)
        if wide.any():
            f = _smooth_noise(ctx, "plant_mottle", 3 * ctx.scale ** 0.6)
            cut_hi = np.quantile(f[wide], 0.78 - 0.1 * s.roughness)
            cut_lo = np.quantile(f[wide], 0.16 + 0.08 * s.roughness)
            up = wide & (f >= cut_hi) & ~sp.core
            dn = wide & (f <= cut_lo) & ~sp.core
            up = pa.remove_small_clusters(up.astype(np.int32), 3, False).astype(bool) & wide
            dn = pa.remove_small_clusters(dn.astype(np.int32), 3, False).astype(bool) & wide
            sp.body = np.where(up, sp.body + 0.2, sp.body)
            sp.body = np.where(dn, sp.body - 0.2, sp.body)
        stems = sp.part_mask(STEM)
        if stems.any():
            period = max(3.0, 4.0 * ctx.scale ** 0.6)
            # node bands every `period` pixels along each stem
            L = sp.prop("length", 10.0)
            pos = sp.t * L
            node = stems & ((pos % period) < max(1.0, period * 0.22)) & (sp.t > 0.12) & (sp.t < 0.92)
            sp.body = np.where(node, sp.body - 0.22, sp.body)
        heads = sp.part_mask(HEAD)
        if heads.any():
            # a darker calyx band at the base of heads
            sp.body = np.where(heads & (sp.t < 0.22), sp.body - 0.25, sp.body)
        sp.commit(ctx)

    def layer_small_detail(self, ctx: GenContext) -> None:
        """Ragged leaf edges (roughness) and tiny two-pixel speckles (noise)."""
        s, sp = ctx.settings, ctx.data["spr"]
        rng = ctx.rng("plant_small")
        if s.roughness > 0.5:
            # nibble single pixels out of the outer edge of broad leaves only
            leafy = sp.part_mask(LEAF, MASS) & (sp.width >= 3.0)
            cnt = sum(sp.nb(sp.vis, dx, dy, False).astype(np.int32) for dx, dy in pa.N4)
            cand = leafy & (cnt == 3) & ~sp.core & (sp.t > 0.25) & (sp.t < 0.85)
            nib = cand & (rng.random(cand.shape) < (s.roughness - 0.5) * 0.45)
            sp.remove(nib)
        if s.noise > 0.05:
            body = sp.vis & ~sp.thin() & sp.part_mask(LEAF, MASS, HEAD) & (sp.width >= 2)
            ys, xs = np.nonzero(body)
            if len(xs):
                count = int(round(len(xs) * 0.05 * s.noise))
                for _ in range(count):
                    i = int(rng.integers(len(xs)))
                    x, y = int(xs[i]), int(ys[i])
                    dx, dy = ((1, 0), (0, 1))[int(rng.integers(2))]
                    if x + dx < sp.w and y + dy < sp.h and body[y + dy, x + dx] \
                            and sp.elem[y + dy, x + dx] == sp.elem[y, x]:
                        d = 0.2 if rng.random() < 0.5 else -0.2
                        sp.body[y, x] += d
                        sp.body[y + dy, x + dx] += d
        sp.commit(ctx)

    def layer_cracks(self, ctx: GenContext) -> None:
        """Veins: a darker midrib on broad leaves, ribs on bulb heads."""
        sp = ctx.data["spr"]
        rib = sp.core & sp.part_mask(LEAF, MASS) & (sp.width >= 2.6) & (sp.t > 0.08) & (sp.t < 0.9)
        sp.rim = np.where(rib, sp.rim - 1, sp.rim)
        heads = sp.part_mask(HEAD)
        if heads.any():
            for i, e in enumerate(sp.els):
                if e["part"] != HEAD:
                    continue
                m = sp.elem == i
                ys, xs = np.nonzero(m)
                if len(xs) < 9:
                    continue
                cx = e.get("hcx", xs.mean())
                wdt = xs.max() - xs.min() + 1
                gap = max(2, int(round(wdt / 3.0)))
                cols = [int(math.floor(cx + k * gap)) for k in (-1, 1)]
                for col in cols:
                    line = m & (np.arange(sp.w)[None, :] == col) & (sp.t > 0.2) & (sp.t < 0.85)
                    sp.rim = np.where(line, sp.rim - 1, sp.rim)
        sp.commit(ctx)

    def layer_highlights(self, ctx: GenContext) -> None:
        s, sp = ctx.settings, ctx.data["spr"]
        lit = sprite_rim_light(sp)
        sp.rim = np.where(lit, sp.rim + 1, sp.rim)
        ctx.data["lit"] = lit
        # brightest pixels: the lit tips of strokes, and wet gloss
        rng = ctx.rng("plant_gloss")
        tipm = tip_cells(sp, max(1, int(len(sp.tips) * (0.25 + 0.4 * s.moisture))), rng, 1,
                         (LEAF,)) & ~sp.thin()
        sp.rim = np.where(tipm, sp.rim + 1, sp.rim)
        sprite_gloss(sp, s.moisture, rng, (LEAF, HEAD))
        # heads get a specular dot
        for i, e in enumerate(sp.els):
            if e["part"] == HEAD:
                m = sp.elem == i
                ys, xs = np.nonzero(m & lit)
                if len(xs) >= 4:
                    j = int(np.argmin(xs + ys * 1.3))
                    sx, sy = int(xs[j]) + 1, int(ys[j]) + 1
                    if 0 <= sx < sp.w and 0 <= sy < sp.h and m[sy, sx]:
                        sp.rim[sy, sx] += 2
                        sp.protect[sy, sx] = True
        sp.commit(ctx)

    def layer_shadows(self, ctx: GenContext) -> None:
        sp = ctx.data["spr"]
        far, cast = sprite_rim_shadow(sp)
        cast &= ~sp.part_mask(STEM)
        sp.rim = np.where(far, sp.rim - 1, sp.rim)
        sp.rim = np.where(cast & ~far, sp.rim - 1, sp.rim)
        # ground occlusion: the lowest pixels of bottom-anchored plants
        if ctx.settings.variant in ("bush", "bulb"):
            depth = max(1, int(round(1.2 * ctx.scale ** 0.6)))
            rows = np.arange(sp.h)[:, None] >= sp.h - depth
            occ = sp.vis & rows & (sp.t < 0.3) & ~far
            sp.rim = np.where(occ, sp.rim - 1, sp.rim)
        sp.commit(ctx)

    def layer_accent(self, ctx: GenContext) -> None:
        s, sp, P = ctx.settings, ctx.data["spr"], ctx.data["P"]
        v = s.variant
        rng = ctx.rng("plant_accent")
        n_acc = ctx.canvas.length_of("accent")
        # small buds / berries / spore dots in the accent ramp
        buds = np.zeros((sp.h, sp.w), dtype=bool)
        if v in ("grass", "fern", "vine", "bush"):
            want = {"grass": 0.0, "fern": 0.0, "vine": 0.6, "bush": 0.16}[v] * (0.4 + P["density"])
            if v == "fern":
                buds |= _fern_sori(ctx, sp, rng)
            elif v == "grass":
                buds |= _grass_heads(ctx, sp, rng)
            else:
                k = int(round(len(sp.tips) * want))
                buds |= tip_cells(sp, k, rng, 1 if ctx.w <= 16 else 2,
                                  (STEM,) if v == "vine" else (LEAF,))
        if buds.any():
            sp.over_role[buds] = ROLE_IDS["accent"]
            lv = np.full(buds.shape, n_acc - 2)
            lit = ctx.data.get("lit")
            if lit is not None:
                lv = np.where(lit, n_acc - 1, lv)
            sp.over_level[buds] = lv[buds]
            sp.protect |= buds
        if s.glow > 0.02:
            if v == "bulb":
                # a luminous core inside every head, brightest in the middle
                heads = sp.part_mask(HEAD)
                reach = 0.3 + 0.45 * s.glow
                g = heads & (sp.rho < reach)
                g = pa.remove_small_clusters(g.astype(np.int32), 2, False).astype(bool) & heads
                n = ctx.canvas.length_of("glow")
                sprite_glow(sp, ctx, g, np.where(sp.rho < reach * 0.5, n - 1, n - 2))
            k = max(1, int(round(len(sp.tips) * (0.12 + 0.6 * s.glow))))
            if v == "bulb":
                k = int(round(len(sp.tips) * 0.3 * max(0.0, s.glow - 0.5)))
            parts = (LEAF, STEM) if v in ("vine", "grass") else (LEAF,)
            g = tip_cells(sp, k, ctx.rng("plant_glow_tips"), 1 + int(s.glow > 0.6 and ctx.w >= 32), parts)
            g &= ~buds
            n = ctx.canvas.length_of("glow")
            sprite_glow(sp, ctx, g, n - 1 if s.glow > 0.8 else n - 2)
        sp.commit(ctx)


# ================================================================ builders


def _bottom_x(ctx: GenContext, rng: np.random.Generator, n: int, spread: float) -> list[float]:
    """``n`` well spread base positions across the bottom edge."""
    w = ctx.w
    lo, hi = w * (0.5 - spread / 2), w * (0.5 + spread / 2)
    if n <= 1:
        return [w / 2 + rng.uniform(-0.6, 0.6)]
    xs = np.linspace(lo, hi, n)
    xs = xs + rng.uniform(-0.45, 0.45, n) * (hi - lo) / n
    return [float(np.clip(x, 0.5, w - 0.5)) for x in xs]


def _build_grass(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """A tuft: blades radiate from a band at the bottom, taller in the middle."""
    s = ctx.settings
    rng = ctx.rng("grass_geom")
    sc, ds = P["sc"], P["ds"]
    w, h = ctx.w, ctx.h
    n = int(round((3 + 8 * P["density"] + 2 * P["leaf"]) * (sc / ds) ** 1.5))
    if P["stems_hint"]:
        n = int(round(n * (1 - 0.3 * P["ws"]) + P["stems_hint"] * 1.4 * (sc / ds) ** 1.5 * 0.3 * P["ws"]))
    n = max(2, n)
    Hmax = h * P["height"]
    band = 0.42 + 0.4 * P["density"]            # width of the root band (fraction of the texture)
    xs = _bottom_x(ctx, rng, n, band)
    # blade foot width: 1 px at 16x16 unless stem/leaf are high, tapered strokes when larger
    Wb = max(1.0, (0.9 + 1.0 * s.stem) * (0.8 + 0.4 * P["leaf"]) * ds)
    wob = P["wob"] * 0.35 * min(1.0, max(0.0, ds - 1.2))
    fan = math.radians(14 + 16 * P["organic"] + 6 * (1 - P["height"]))
    lim = math.radians(40 + 22 * P["organic"])
    blades = []
    for j in range(n):
        x = xs[j]
        rel = (x - w / 2) / max(1.0, w * band / 2)
        rel = float(np.clip(rel, -1.2, 1.2))
        dome = 1 - 0.45 * min(1.0, abs(rel)) ** 1.6
        L = Hmax * dome * rng.uniform(0.66 - 0.2 * s.noise, 1.0)
        if rng.random() < 0.15 + 0.2 * s.noise:
            L *= 0.6
        lean = rel * fan + rng.normal(0, math.radians(3 + 6 * P["organic"] + 8 * s.noise))
        sgn = math.copysign(1, lean) if abs(lean) > 0.04 else _rng_sign(rng)
        turn = sgn * math.radians(rng.uniform(4, 10 + 36 * P["organic"]))
        # keep tips steep enough that blades never collapse into horizontal runs
        lean = float(np.clip(lean, -lim * 0.75, lim * 0.75))
        turn = float(np.clip(lean + turn, -lim, lim) - lean)
        blades.append([False, j, x, rel, L, lean, turn])
    # the shorter blades stand behind (darker), the tall ones in front, with a few flips
    Ls = np.array([b[4] for b in blades])
    order = np.argsort(Ls + rng.normal(0, 0.12 * Hmax, n))
    for r_, bi in enumerate(order):
        blades[bi][0] = r_ < int(round(n * 0.42))
    # neighbours packed tighter than ~1.5 px must differ in depth (and so in tone)
    for j in range(1, n):
        a, b = blades[j - 1], blades[j]
        if not a[0] and not b[0] and abs(a[2] - b[2]) < 1.5 * ds:
            (a if a[4] < b[4] else b)[0] = True
    blades.sort(key=lambda b: (not b[0], b[1]))          # back row first
    for bk, j, x, rel, L, lean, turn in blades:
        wmul = 0.6 if bk else 1.0
        z = -1.0 if bk else float(j % 3) * 0.01
        side = math.copysign(1, lean) if abs(lean) > 0.02 else 1
        phase, wf = rng.uniform(0, 6.3), rng.uniform(0.6, 1.2)
        best = None
        # front blades: pick the lean that keeps the blade clear of its neighbours
        tries = (0.0,) if bk else (0.0, -0.1, 0.1, -0.2, 0.2)
        for dl in tries:
            pts, u = _turtle(x, h - 0.01, -math.pi / 2 + lean + dl, L, turn, 2.0, wob, wf, phase)
            wid = np.maximum(1.0, 1.0 + (Wb * wmul - 1.0) * (1 - u) ** 1.2)
            pre = sp.raster(pts, u, wid, (side, 0))
            crowd = sp.crowding(pre[0], -0.5, 0.3, pre[1]) + abs(dl) * 0.4
            if best is None or crowd < best[0]:
                best = (crowd, pts, u, wid, pre, dl)
        _, pts, u, wid, pre, dl = best
        lean = float(np.clip(lean + dl, -lim, lim))
        sp.stroke(pts, u, wid, LEAF, "base", (side, 0), z=z,
                  tone=0.55 + rng.uniform(-0.03, 0.03) - 0.06 * float(np.clip(lean / 0.45, -1, 1)),
                  back=float(bk), length=L, grad=0.3, pre=pre)
        # branch: a shorter blade forking off part-way up, diverging clearly
        if s.branch > 0.05 and rng.random() < s.branch * 0.5 and L > 5 * ds:
            k = int(len(pts) * rng.uniform(0.3, 0.5))
            bx, by = pts[k]
            fs = -side if rng.random() < 0.5 else side
            fa = float(np.clip(lean + fs * math.radians(rng.uniform(26, 38)), -lim, lim))
            if abs(fa - lean) < math.radians(18):
                fa = lean - fs * math.radians(26)          # no room on that side: fork the other way
            ft = fs * math.radians(rng.uniform(4, 12))
            ft = float(np.clip(fa + ft, -lim, lim) - fa)
            p2, u2 = _turtle(bx, by, -math.pi / 2 + fa, L * rng.uniform(0.4, 0.55), ft, 1.5)
            sp.stroke(p2, u2 * 0.55 + 0.45, np.maximum(1.0, Wb * 0.6), LEAF, "base", (fs, 0), z=z - 0.005,
                      tone=0.55, back=float(bk), length=L, grad=0.3, behind=True)


def _build_fern(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Fronds rise from one crown like a vase and arch outward.

    Small textures draw each frond as a feather with notched, alternating
    pinnae; from 64px up every pinna is its own tapered leaflet.
    """
    s = ctx.settings
    rng = ctx.rng("fern_geom")
    sc, ds = P["sc"], P["ds"]
    w, h = ctx.w, ctx.h
    nf = int(np.clip(round(1.6 + 2.6 * P["density"] * (sc / ds) ** 0.4), 1, 7))
    if P["stems_hint"]:
        nf = int(np.clip(round(nf * (1 - 0.3 * P["ws"]) + (1 + P["stems_hint"]) * 0.3 * P["ws"]), 1, 7))
    Hmax = h * P["height"] * 0.98
    fronds = []
    for i in range(nf):
        spread = 0.0 if nf == 1 else (i / (nf - 1) - 0.5) * 2          # -1 .. 1
        if nf % 2 == 0:
            spread *= 0.85
        ang = spread * math.radians(17 + 10 * P["organic"] + 3 * nf) + rng.normal(0, 0.05 + 0.12 * s.noise)
        L = Hmax * (1.0 - 0.22 * abs(spread)) * rng.uniform(0.88, 1.02)
        turn = spread * math.radians(rng.uniform(8, 14 + 26 * P["organic"]))
        if abs(spread) < 0.2:
            turn = rng.normal(0, math.radians(6 + 12 * P["organic"]))
        x0 = w / 2 + spread * max(0.6, 0.9 * ds) + rng.uniform(-0.4, 0.4)
        fronds.append((abs(spread), x0, ang, L, turn, spread))
    fronds.sort(key=lambda f: -f[0])          # outer fronds behind, the centre one in front
    stem_w = 1.0 + s.stem * max(0.55, ds - 0.4) * 1.1
    for k, (sa, x0, ang, L, turn, spread) in enumerate(fronds):
        z = float(k)
        back = 1.0 if (sa > 0.8 and nf > 3) else 0.0
        lim = math.radians(70)
        turn = float(np.clip(ang + turn, -lim, lim) - ang)
        pts, u = _turtle(x0, h - 0.01, -math.pi / 2 + ang, L, turn, 1.7, P["wob"] * 0.25, 1.0,
                         rng.uniform(0, 6.3))
        stipe = 0.05 + 0.08 * (1 - P["leaf"]) + 0.14 * s.stem
        if ds < 3.0:
            _frond_notched(ctx, sp, P, pts, u, L, z, back, stipe, rng)
        else:
            _frond_pinnae(ctx, sp, P, pts, u, L, z, back, stipe, rng)
        # rachis on top of the pinnae (the stipe below them)
        keep = u <= 0.86
        rw = np.maximum(1.0, stem_w * (1 - 0.8 * u))
        sp.stroke(pts[keep], u[keep], rw[keep], STEM, "secondary", (1, 0), z=z + 0.5, tone=0.5, back=back,
                  length=L, grad=0.5, tip=False)
    # branch: young fronds still curled up as fiddleheads
    nfid = max(0, int(round(s.branch * 2.2 * (sc / ds) ** 0.5 - 0.3)))
    for i in range(nfid):
        side = -1 if i % 2 == 0 else 1
        x0 = w / 2 + side * rng.uniform(1.5, 3.0) * ds
        L = Hmax * rng.uniform(0.28, 0.42)
        ang = side * math.radians(rng.uniform(10, 24))
        pts, u = _fiddlehead(x0, h - 0.01, -math.pi / 2 + ang, L, max(1.25, 1.3 * ds), -side,
                             rng.normal(0, 0.2))
        sp.stroke(pts, u, np.maximum(1.0, (0.8 + 0.6 * s.stem) * ds * (1 - 0.5 * u)), STEM, "secondary",
                  (1, 0), z=-2.0 - i * 0.1, tone=0.62, back=0.0, length=L, grad=0.5, tip=True,
                  fiddle=1.0, behind=True)


def _fiddlehead(x: float, y: float, ang: float, length: float, R0: float, curl: int,
                bend: float = 0.0) -> tuple[np.ndarray, np.ndarray]:
    """A young fern frond: a stalk ending in a tightening spiral (koru)."""
    stalk, _ = _turtle(x, y, ang, length, bend, 1.5)
    hx, hy = stalk[-1] - stalk[-2]
    hd = math.atan2(hy, hx)
    # spiral centre sits beside the stalk end, on the curl side
    nx, ny = math.cos(hd + curl * math.pi / 2), math.sin(hd + curl * math.pi / 2)
    cx, cy = stalk[-1, 0] + nx * R0, stalk[-1, 1] + ny * R0
    th0 = math.atan2(stalk[-1, 1] - cy, stalk[-1, 0] - cx)
    turns = 1.2
    n = max(12, int(2 * math.pi * turns * R0 / 0.15))
    q = np.linspace(0, 1, n)
    th = th0 + curl * 2 * math.pi * turns * q
    r = R0 * (1 - 0.7 * q)
    spiral = np.stack([cx + r * np.cos(th), cy + r * np.sin(th)], axis=1)
    pts = np.concatenate([stalk, spiral[1:]], axis=0)
    seg = np.r_[0, np.cumsum(np.hypot(*np.diff(pts, axis=0).T))]
    return pts, seg / max(seg[-1], 1e-6)


def _frond_notched(ctx, sp, P, pts, u, L, z, back, stipe, rng) -> None:
    ds = P["ds"]
    # envelope: bare stipe, widest a third of the way up, tapering to the tip
    wmax = (1.1 + 1.6 * P["leaf"]) * ds * (0.85 if back else 1.0)
    uu = np.clip((u - stipe) / (1 - stipe), 0, 1)
    env = np.where(u < stipe, 0.0, wmax * np.clip(np.minimum(uu / 0.3, (1.02 - uu) / 0.7), 0, 1) ** 0.8)
    arc = u * L
    gap = max(2.0, 2.0 * ds * (1.15 - 0.3 * P["leaf"]))
    ph = (arc / gap + rng.uniform(0, 1)) % 1.0
    left = np.where(ph < 0.5, env, env * 0.3)
    right = np.where(ph >= 0.5, env, env * 0.3)
    # organic ragged pinnae
    if P["organic"] > 0.3:
        j = rng.uniform(0.75, 1.15, 64)
        idx = np.minimum(63, (arc / gap).astype(int) % 64)
        left, right = left * j[idx], right * j[(idx + 7) % 64]
    pre = sp.raster_sides(pts, u, left, right)
    sp.stroke(pts, u, 1.0, LEAF, "base", z=z, tone=0.55, back=back, length=L, grad=0.35, pre=pre)


def _frond_pinnae(ctx, sp, P, pts, u, L, z, back, stipe, rng) -> None:
    s = ctx.settings
    ds = P["ds"]
    seg = np.cumsum(np.r_[0, np.hypot(*np.diff(pts, axis=0).T)])
    gap = max(2.0, (1.0 - 0.25 * P["leaf"]) * ds * 1.3)
    pos = stipe * seg[-1]
    side = _rng_sign(rng)
    while pos < seg[-1] - 1.0:
        j = min(int(np.searchsorted(seg, pos)), len(pts) - 2)
        uu = u[j]
        hx, hy = pts[j + 1] - pts[j]
        heading = math.atan2(hy, hx)
        t2 = (uu - stipe) / (1 - stipe)
        env = min(1.0, t2 / 0.3, (1.05 - t2) / 0.75)
        Lp = max(1.5, (1.6 + 2.6 * P["leaf"]) * ds * max(0.15, env))
        for sd in (-1, 1):
            if sd == side or P["symmetry"] > 0.5 or rng.random() < 0.5:
                la = heading + sd * math.radians(rng.uniform(58, 72))
                p2, u2 = _turtle(pts[j, 0], pts[j, 1], la, Lp, -sd * math.radians(18 + 20 * P["organic"]), 1.3)
                lw = np.maximum(1.0, 0.42 * Lp * np.sin(np.pi * np.clip(u2 * 0.85 + 0.12, 0, 1)) ** 0.7)
                sp.stroke(p2, u2, lw, LEAF, "base", (0, 1), z=z, tone=0.55, back=back, length=Lp, grad=0.3,
                          behind=True, frond=1)
                # bipinnate lobes on long leaflets
                if s.branch > 0.25 and Lp > 5:
                    for q in np.arange(0.3, 0.85, max(0.18, 2.2 / Lp)):
                        qi = int(q * (len(p2) - 1))
                        for sub in (-1, 1):
                            p3, u3 = _turtle(p2[qi, 0], p2[qi, 1], la + sub * math.radians(60),
                                             max(1.0, Lp * 0.22 * s.branch * (1 - q)), 0.0)
                            sp.stroke(p3, u3, 1.0, LEAF, "base", (0, 1), z=z - 0.05, tone=0.55, back=back,
                                      length=Lp, grad=0.2, behind=True, tip=False)
        side = -side
        pos += gap * rng.uniform(0.9, 1.1) * (1 - 0.3 * uu)


def _fern_sori(ctx: GenContext, sp: _Sprite, rng: np.random.Generator) -> np.ndarray:
    """Spore dots on the underside of a few leaflets (accent ramp)."""
    out = np.zeros((sp.h, sp.w), dtype=bool)
    leaf = sp.part_mask(LEAF)
    under = leaf & ~sp.nb(sp.vis, 0, 1, False) & (sp.t > 0.35)
    ys, xs = np.nonzero(under)
    if not len(xs):
        return out
    if ctx.w < 32:
        return out           # single spore pixels only read as noise on small textures
    k = int(round(len(xs) * (0.03 + 0.06 * ctx.settings.density) * min(1.0, (ctx.w / 32) ** 0.5)))
    if k:
        idx = rng.choice(len(xs), size=min(k, len(xs)), replace=False)
        out[ys[idx], xs[idx]] = True
    return out


def _gap_fill(vis: np.ndarray, max_gap: int) -> np.ndarray:
    """Empty pixels with opaque pixels on both sides within ``max_gap`` (horizontal)."""
    h, w = vis.shape
    INF = 10 ** 6
    dl = np.full((h, w), INF)
    dr = np.full((h, w), INF)
    for k in range(max_gap, 0, -1):
        dl[:, k:][vis[:, :-k]] = k
        dr[:, :-k][vis[:, k:]] = k
    return ~vis & (dl + dr - 1 <= max_gap)


def _grass_mass(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Close the gaps between blade roots so the tuft reads as one clump."""
    h = sp.h
    Hmax = h * P["height"]
    depth = min(Hmax * (0.1 + 0.16 * P["density"]), 1.0 + 1.6 * P["ds"])
    rows = np.arange(h)[:, None] >= h - depth
    fill = _gap_fill(sp.vis, max(1, int(round(1.5 * P["ds"])))) & rows
    # ragged top edge: only fill where the pixel below is solid (or it is the bottom row)
    for _ in range(2):
        below = np.zeros_like(fill)
        below[:-1] = (sp.vis | fill)[1:]
        below[-1] = True
        fill &= below
    if fill.any():
        t = np.clip((np.arange(h)[:, None] - (h - depth)) / max(1.0, depth), 0, 1)
        sp.add(fill, np.broadcast_to(1 - t, fill.shape).copy(), MASS, "base", z=-10.0, tone=0.2,
               behind=True, back=1.0, grad=0.0)


def _grass_heads(ctx: GenContext, sp: _Sprite, rng: np.random.Generator) -> np.ndarray:
    """Seed heads: the top pixels of the tallest front blades in the accent ramp."""
    out = np.zeros((sp.h, sp.w), dtype=bool)
    tips = [(y, x, i) for x, y, i in sp.tips
            if sp.els[i]["part"] == LEAF and sp.els[i].get("back", 0) < 0.5 and sp.elem[y, x] == i]
    if not tips:
        return out
    tips.sort()
    k = max(1, int(round((0.6 + 1.2 * ctx.settings.leaf) * (ctx.w / 16) ** 0.4)))
    ln = max(1, int(round(1.6 * (ctx.w / 16) ** 0.5)))
    for y, x, i in tips[:k]:
        el = sp.elem == i
        tt = np.where(el, sp.t, -1.0)
        vals = np.sort(tt[el])[::-1]
        out |= el & (tt >= vals[min(len(vals) - 1, ln - 1)])
    return out


def _build_bush(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """A rounded shrub built from overlapping foliage puffs on a short trunk."""
    s = ctx.settings
    rng = ctx.rng("bush_geom")
    sc, ds = P["sc"], P["ds"]
    w, h = ctx.w, ctx.h
    Hc = h * P["height"]
    cx = w / 2 + rng.uniform(-0.4, 0.4)
    rx = w * (0.30 + 0.14 * P["density"])
    ry = Hc * 0.44
    cy = h - Hc + ry + 0.5
    ry_fill = min(ry * 1.25, h - cy - 1.0)     # foliage reaches down close to the ground
    # trunk and branches (secondary ramp)
    stem_w = 1.0 + s.stem * max(1.1, ds)
    # a stronger stem lifts the crown on a longer visible trunk
    lift = (s.stem - 0.4) * 0.12 * h
    cy -= max(0.0, lift)
    ry = max(2.0, ry - max(0.0, lift) * 0.5)
    ry_fill = min(ry * 1.25, h - cy - 1.0 - max(0.0, lift))
    trunk_top = cy + ry * 0.35
    L = max(2.0, h - trunk_top)
    pts, u = _turtle(cx, h - 0.01, -math.pi / 2 + rng.normal(0, 0.06), L, rng.normal(0, 0.2), 1.5)
    sp.stroke(pts, u, np.maximum(1.0, stem_w * (1 - 0.3 * u)), STEM, "secondary", (1, 0), z=0.0, tone=0.45,
              length=L, grad=0.3, tip=False)
    # foliage puffs: well spaced over the crown, larger in the middle
    n = int(round((3 + 4 * P["density"]) * (sc / ds) ** 1.3))
    rc0 = (1.7 + 1.3 * P["leaf"]) * ds
    pts_c = noise.poisson_points(w, h, rng, n * 4, max(1.5, rc0 * 1.15), False)
    cand = []
    for x, y in pts_c:
        ex = (x + 0.5 - cx) / max(1.0, rx - rc0 * 0.6)
        ey = (y + 0.5 - cy) / max(1.0, (ry if y + 0.5 < cy else ry_fill) - rc0 * 0.5)
        if ex * ex + ey * ey <= 1.0:
            cand.append((x + 0.5, y + 0.5, ex * ex + ey * ey))
    cand = cand[:n] or [(cx, cy, 0.0)]
    # branches reach from the trunk to some puffs (visible in the gaps)
    nb = int(round(len(cand) * (0.25 + 0.6 * s.branch)))
    for (x, y, _) in cand[:nb]:
        bx, by = pts[int(len(pts) * rng.uniform(0.55, 0.95))]
        dx, dy = x - bx, y - by
        Lb = math.hypot(dx, dy)
        if Lb < 1.5:
            continue
        p2, u2 = _turtle(bx, by, math.atan2(dy, dx), Lb, rng.normal(0, 0.25), 1.2)
        sp.stroke(p2, u2, np.maximum(1.0, stem_w * 0.7 * (1 - u2)), STEM, "secondary", (1, 0), z=0.1,
                  tone=0.4, length=Lb, grad=0.3, tip=False)
    puffs = []
    for (x, y, d2) in cand:
        irr = 0.15 + 0.3 * P["organic"] + 0.15 * s.noise
        r = rc0 * rng.uniform(1 - irr, 1 + irr) * (1.1 - 0.25 * d2)
        x += rng.normal(0, 0.4 * (P["organic"] + s.noise) * ds)
        y += rng.normal(0, 0.3 * (P["organic"] + s.noise) * ds)
        back = rng.random() < 0.35 and d2 > 0.25
        # higher, central puffs sit in front
        z = (1.0 - d2) + (cy - y) / max(1.0, ry) * 0.5 + rng.uniform(0, 0.3) - (2.0 if back else 0.0)
        puffs.append((z, x, y, r, back))
    puffs.sort()
    for z, x, y, r, back in puffs:
        sp.blob(x, y, r * 1.08, r * 0.92, LEAF, "base", z=z + 1.0, tone=0.55 + rng.uniform(-0.04, 0.04),
                back=float(back), grad=0.3, puff=1.0)
        # pointed leaf tips on the upper / outer rim of the puff
        nt = int(round((0.8 + 1.8 * P["leaf"]) * (0.6 + 0.4 * ds)))
        for _ in range(nt):
            a = math.atan2(y - cy - ry * 0.6, x - cx) + rng.normal(0, 0.9)
            if math.sin(a) > 0.35:           # not pointing down into the trunk
                a = -a
            lx, ly = x + math.cos(a) * r * 0.75, y + math.sin(a) * r * 0.75
            Lt = max(1.0, (0.5 + 0.9 * P["leaf"]) * ds * rng.uniform(0.8, 1.2))
            p3, u3 = _turtle(lx, ly, a + rng.normal(0, 0.25), Lt + r * 0.25, rng.normal(0, 0.3), 1.0)
            lw = np.maximum(1.0, (0.9 + 0.9 * P["leaf"]) * ds * (1 - u3) ** 1.3 + 0.2)
            sp.stroke(p3, u3, lw, LEAF, "base", (0, 1), z=z + 0.99, tone=0.6, back=float(back), length=Lt,
                      grad=0.3, behind=True)
    ctx.data["bush"] = (cx, cy, rx, ry)


def _bush_mass(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Dark inner foliage behind the leaves (fills the crown, adds depth)."""
    if "bush" not in ctx.data:
        return
    cx, cy, rx, ry = ctx.data["bush"]
    rng = ctx.rng("bush_mass")
    n = 3 + int(round(3 * P["density"]))
    for i in range(n):
        ox = rng.uniform(-0.55, 0.55) * rx
        oy = rng.uniform(-0.35, 0.45) * ry
        r = rng.uniform(0.35, 0.55)
        sp.blob(cx + ox, cy + oy, rx * r, ry * r * 0.9, MASS, "base", z=-5.0 - i * 0.01, tone=0.2,
                behind=True, back=1.0, grad=0.2)
    # keep the mass hidden well inside the crown so it reads as shade, not a second outline
    mass = sp.part_mask(MASS)
    leafy = sp.vis & ~mass
    near = leafy.copy()
    for _ in range(max(1, int(round(1.2 * P["ds"])))):
        near = pa.dilate(near, False, True)
    sp.remove(mass & ~near)


def _sway(x: float, y: float, length: float, amp: float, waves: float, phase: float,
          lean: float = 0.0, step: float = 0.2) -> tuple[np.ndarray, np.ndarray]:
    """A hanging strand: straight down with a gentle sideways sway."""
    n = max(3, int(math.ceil(length / step)) + 1)
    u = np.linspace(0.0, 1.0, n)
    ys = y + u * length
    xs = x + amp * np.sin(2 * np.pi * waves * u + phase) - amp * math.sin(phase) + lean * u * length
    return np.stack([xs, ys], axis=1), u


def _build_vine(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Leafy strands hanging from the top edge."""
    s = ctx.settings
    rng = ctx.rng("vine_geom")
    sc, ds = P["sc"], P["ds"]
    h = ctx.h
    n = max(1, int(round((1.6 + 3.2 * P["density"]) * (sc / ds) ** 1.1)))
    Lmax = h * P["height"]
    xs = _bottom_x(ctx, rng, n, 0.72)
    stem_w = 1.0 + s.stem * max(0.0, ds - 0.8)
    Ls = [Lmax * rng.uniform(0.55 - 0.25 * s.noise, 1.0) for _ in range(n)]
    # the longest strands in front
    order = np.argsort(Ls)
    for rank, i in enumerate(order):
        x, L = xs[i], Ls[i]
        back = rank < n // 2 and n > 2
        z = rank * 0.1 - (2.0 if back else 0.0)
        amp = (0.4 + 1.3 * P["organic"] + 0.6 * s.noise) * ds * rng.uniform(0.6, 1.0)
        pts, u = _sway(x, 0.0, L, amp, rng.uniform(0.5, 1.2), rng.uniform(0, 6.3), rng.normal(0, 0.06))
        sp.stroke(pts, u, np.maximum(1.0, stem_w * (1 - 0.5 * u)), STEM, "secondary", (1, 0), z=z,
                  tone=0.5, back=float(back), length=L, grad=0.2)
        _vine_leaves(ctx, sp, P, pts, u, L, z + 0.05, back, rng)
        if s.branch > 0.1 and rng.random() < s.branch * 1.2 and L > 6 * ds:
            k = int(len(pts) * rng.uniform(0.2, 0.45))
            side = _rng_sign(rng)
            Lb = L * rng.uniform(0.35, 0.6)
            p2, u2 = _turtle(pts[k, 0], pts[k, 1], math.pi / 2 + side * rng.uniform(0.4, 0.7), Lb,
                             -side * rng.uniform(0.3, 0.6), 1.2)
            sp.stroke(p2, u2, 1.0, STEM, "secondary", (1, 0), z=z - 0.05, tone=0.5, back=float(back),
                      length=Lb, grad=0.2, behind=True)
            _vine_leaves(ctx, sp, P, p2, u2, Lb, z - 0.04, back, rng)
    # where the strands grip the ceiling: a short ragged rim of leaves along the top edge
    if s.stem > 0.35:
        for x in xs:
            sd = _rng_sign(rng)
            p3, u3 = _turtle(x, 0.5, math.pi if sd < 0 else 0.0, (0.6 + 1.6 * (s.stem - 0.35)) * ds * 1.5,
                             sd * 0.4, 1.0)
            sp.stroke(p3, u3, np.maximum(1.0, 1.2 * ds * (1 - u3)), STEM, "secondary", (0, 1), z=-3.0,
                      tone=0.5, back=0.0, length=2.0, grad=0.1, behind=True, tip=False)


def _vine_leaves(ctx, sp, P, pts, u, L, z, back, rng) -> None:
    ds = P["ds"]
    gap = max(1.6, (2.6 - 1.0 * P["leaf"]) * ds * 0.85)
    seg = np.cumsum(np.r_[0, np.hypot(*np.diff(pts, axis=0).T)])
    pos = gap * rng.uniform(0.4, 0.9)
    side = _rng_sign(rng)
    while pos < seg[-1] - 0.5:
        j = min(int(np.searchsorted(seg, pos)), len(pts) - 2)
        hx, hy = pts[j + 1] - pts[j]
        heading = math.atan2(hy, hx)
        uu = u[j]
        Lp = max(1.5, (1.3 + 2.0 * P["leaf"]) * ds ** 1.2 * rng.uniform(0.85, 1.15) * (1 - 0.3 * uu))
        # leaves point outward and droop downward
        la = heading - side * math.radians(rng.uniform(50, 70))
        p2, u2 = _turtle(pts[j, 0], pts[j, 1], la, Lp, side * math.radians(35), 1.2)
        lw = np.maximum(1.0, (0.7 + 1.1 * P["leaf"]) * ds ** 1.2 * np.sin(np.pi * np.clip(u2 * 0.8 + 0.15, 0, 1)))
        sp.stroke(p2, u2, lw, LEAF, "base", (0, 1), z=z, tone=0.55, back=float(back), length=Lp, grad=0.35)
        side = -side
        pos += gap * rng.uniform(0.85, 1.15)


def _build_bulb(ctx: GenContext, sp: _Sprite, P: dict) -> None:
    """Stalks carrying round bulbous heads, with a rosette of leaves at the base."""
    s = ctx.settings
    rng = ctx.rng("bulb_geom")
    sc, ds = P["sc"], P["ds"]
    w, h = ctx.w, ctx.h
    Hmax = h * P["height"]
    n = int(np.clip(1 + round(2.2 * P["density"] * (sc / ds) ** 0.4 - 0.4), 1, 5))
    stem_w = 1.0 + s.stem * max(0.4, ds - 0.1)
    xs = _bottom_x(ctx, rng, n, 0.36)
    main = int(np.argmin([abs(x - w / 2) for x in xs]))
    # basal leaves: rise and curl outward (behind the stalks)
    nl = int(round((1 + 3.5 * P["leaf"]) * (sc / ds) ** 0.5))
    for i in range(nl):
        side = -1 if i % 2 == 0 else 1
        x0 = w / 2 + side * rng.uniform(0.3, 1.5) * ds
        ang = side * rng.uniform(0.22, 0.5)
        L = max(2.5, Hmax * rng.uniform(0.3, 0.48) * (0.7 + 0.5 * P["leaf"]))
        pts, u = _turtle(x0, h - 0.01, -math.pi / 2 + ang, L, side * rng.uniform(0.2, 0.55), 1.6,
                         P["wob"] * 0.2)
        lw = np.maximum(1.0, (0.9 + 1.3 * P["leaf"]) * ds * np.sin(np.pi * np.clip(u * 0.75 + 0.2, 0, 1)))
        sp.stroke(pts, u, lw, LEAF, "base", (side, 0), z=-1.0 + i * 0.01, tone=0.55, back=float(i >= 2),
                  length=L, grad=0.4)
    heads = []
    for i, x in enumerate(xs):
        is_main = i == main
        r = (2.2 + 0.5 * rng.random()) * ds * (1.0 if is_main else 0.68)
        L = (Hmax - 2.1 * r) * (1.0 if is_main else rng.uniform(0.5, 0.75))
        L = max(2.0, L)
        rel = (x - w / 2) / (w / 2)
        ang0 = rel * 0.4 + rng.normal(0, 0.05 + 0.15 * s.noise)
        L *= rng.uniform(1 - 0.2 * s.noise, 1.0)
        turn0 = rng.normal(0, 0.2 + 0.35 * P["organic"])
        ph = rng.uniform(0, 6.3)
        # the head must stay inside the texture: straighten the stalk until it fits
        for k in (1.0, 0.6, 0.3, 0.0):
            ang = ang0 * k
            pts, u = _turtle(x, h - 0.01, -math.pi / 2 + ang, L, turn0 * k, 1.6, P["wob"] * 0.25 * k, 1.0, ph)
            hd_ = math.atan2(*(pts[-1] - pts[-2])[::-1])
            if r * 1.1 <= pts[-1, 0] + math.cos(hd_) * r * 0.9 <= w - r * 1.1:
                break
        z = 1.0 if is_main else 0.0
        sw = stem_w if is_main else max(1.0, stem_w - 1)
        sp.stroke(pts, u, np.maximum(1.0, sw * (1 - 0.25 * u)), STEM, "secondary", (1, 0), z=z, tone=0.55,
                  back=0.0 if is_main else 1.0, length=L, grad=0.4, tip=False)
        hd = math.atan2(*(pts[-1] - pts[-2])[::-1])
        heads.append((pts[-1, 0] + math.cos(hd) * r * 0.9, pts[-1, 1] + math.sin(hd) * r * 0.9, r, z, hd))
        # side branches with small bulbs
        if s.branch > 0.15 and rng.random() < s.branch * (1.2 if is_main else 0.5) and L > 5 * ds:
            k = int(len(pts) * rng.uniform(0.4, 0.62))
            side = _rng_sign(rng)
            Lb = max(2.0, L * rng.uniform(0.3, 0.42))
            p2, u2 = _turtle(pts[k, 0], pts[k, 1], -math.pi / 2 + ang + side * rng.uniform(0.6, 0.9), Lb,
                             -side * 0.6, 1.2)
            sp.stroke(p2, u2, 1.0, STEM, "secondary", (1, 0), z=z - 0.5, tone=0.5, back=1.0, length=Lb,
                      grad=0.3, behind=True, tip=False)
            hd2 = math.atan2(*(p2[-1] - p2[-2])[::-1])
            rb = max(1.0, r * 0.55)
            heads.append((p2[-1, 0] + math.cos(hd2) * rb * 0.9, p2[-1, 1] + math.sin(hd2) * rb * 0.9, rb,
                          z - 0.4, hd2))
    for (hx, hy, r, z, hd) in heads:
        # snap to the pixel grid so small heads come out symmetric
        wpx = max(2, int(round(2 * r * 0.95)))
        hx = math.floor(hx) + (0.5 if wpx % 2 else 0.0)
        hy = math.floor(hy) + 0.5
        i = sp.blob(hx, hy, wpx / 2 + 0.15, r * 1.1, HEAD, "accent", z=z + 0.2, tone=0.5,
                    angle=0.0, hcx=hx, grad=0.5)
        # a small pointed tip on top of larger heads
        if r >= 1.6 and i >= 0:
            m = sp.elem == i
            ys, xs_ = np.nonzero(m)
            if len(ys):
                top = ys.min()
                cols = xs_[ys == top]
                tx = int(cols[len(cols) // 2])
                if top > 0 and sp.elem[top - 1, tx] < 0:
                    tipm = np.zeros_like(m)
                    tipm[top - 1, tx] = True
                    sp.add(tipm, 1.0, HEAD, "accent", z=z + 0.2, tone=0.5, hcx=hx, grad=0.5,
                           face=np.full(m.shape, 0.3))


