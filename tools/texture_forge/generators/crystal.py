"""Crystal: prism clusters, single prisms, shards, buds (sprites) and crystal blocks.

Sprites are built from *prisms*: pixel-art columns that are sheared (not
rotated) so every slope is a clean 1:1, 1:2, 1:3 or 1:4 stair, with a
pointed pyramid tip or a flat broken top.  Every visible pixel remembers
which prism it belongs to and where it sits inside it (column across the
prism, row along it), so the layers can shade faces, ridges, tips, rims
and occlusion edges exactly the way a pixel artist would, in the colour
ramp of the prism's role:

    accent   main crystal colour          accent2  secondary crystal colour
    glow     emissive cores               base     dark rock the crystals grow from

Shading is expressed in *steps below the top of the ramp* so the same
rules work for ramps of any length (see ``SPEC`` .. ``OUT``).

The ``block`` variant is a tileable crystal block made of flat-shaded
facets (a jittered Voronoi mosaic) with lit / shaded facet edges.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from core import pixel_art as pa
from core.layers import ROLE_IDS, GenContext
from core.palette import ACCENT_PRESETS
from core.settings import TextureSettings

from .base import BaseGenerator

# ----------------------------------------------------------------- palette

#: theme -> (crystal body, secondary crystal, glow core)
THEME_CRYSTAL: dict[str, tuple[str, str, str]] = {
    "crystal": ("amethyst", "blue_ice", "glow_cyan"),
    "thermal": ("cyan_mineral", "amethyst", "heat"),
    "volcanic": ("heat", "sulfur", "heat"),
    "abyss": ("blue_ice", "glow_cyan", "glow_violet"),
    "trench": ("blue_ice", "glow_cyan", "glow_violet"),
    "deep_ocean": ("cyan_mineral", "blue_ice", "glow_cyan"),
    "bioluminescent": ("glow_green", "glow_cyan", "glow_green"),
    "ancient": ("patina", "emerald", "glow_cyan"),
    "cold": ("blue_ice", "silver", "glow_cyan"),
    "organic": ("emerald", "moss", "glow_green"),
}

# shading steps below the top of a ramp
SPEC, TIP, LIT, MID, DARK, DEEP, OUT = range(7)

_CRYSTAL_ROLES = ("accent", "accent2")


def _rnd(v: float) -> int:
    return int(math.floor(v + 0.5))


def _shear_off(r: int, s: float) -> int:
    """Column offset of row ``r`` for a prism leaning ``s`` columns per row."""
    if s == 0:
        return 0
    return int(math.copysign(math.floor(r * abs(s) + 1e-9), s))


#: glow colour that suits a user-chosen crystal colour (heat themes keep their own glow)
ACCENT_GLOW: dict[str, str] = {
    "amethyst": "glow_violet", "manganese": "glow_violet", "glow_violet": "glow_violet",
    "emerald": "glow_green", "moss": "glow_green", "glow_green": "glow_green", "patina": "glow_green",
    "cyan_mineral": "glow_cyan", "teal": "glow_cyan", "glow_cyan": "glow_cyan", "blue_ice": "glow_cyan",
    "silver": "glow_cyan", "bone": "glow_cyan",
    "ruby": "coral_pink", "coral_pink": "coral_pink", "heat": "heat", "copper": "heat", "rust": "heat",
    "sulfur": "sulfur", "sulfur_cyan": "sulfur", "gold": "sulfur", "pyrite": "sulfur",
}
_HEAT_THEMES = ("thermal", "volcanic")

#: rock the crystals grow from, when the theme's own base is too close to the crystal colour
THEME_ROCK: dict[str, str] = {"crystal": "manganese"}


def crystal_roles(s: TextureSettings) -> dict[str, str]:
    """Accent / accent2 / glow anchors for crystal-like materials."""
    body, second, glow = THEME_CRYSTAL.get(s.palette, THEME_CRYSTAL["crystal"])
    roles = {"accent": body, "accent2": second, "glow": glow}
    if s.palette in THEME_ROCK:
        roles["base"] = THEME_ROCK[s.palette]
    if s.accent:
        main, sec = ACCENT_PRESETS.get(s.accent, (s.accent, None))
        roles.pop("accent")
        if sec:
            roles.pop("accent2")      # keep the preset's own secondary colour
        elif second == main:
            roles["accent2"] = body if body != main else "silver"
        if s.palette not in _HEAT_THEMES:
            roles["glow"] = ACCENT_GLOW.get(s.accent, glow)
    return roles


# ------------------------------------------------------------------ voronoi


@dataclass
class Voronoi:
    cell: np.ndarray     # id of the nearest point
    f1: np.ndarray       # distance to it (in grid-cell units)
    f2: np.ndarray       # distance to the second nearest
    points: np.ndarray   # (n, 2) point positions in pixels
    gx: int
    gy: int


def voronoi(w: int, h: int, rng: np.random.Generator, gx: int, gy: int, jitter: float = 0.85,
            aspect: tuple[float, float] = (1.0, 1.0)) -> Voronoi:
    """Tileable jittered-grid Voronoi that only checks the 3x3 neighbouring
    grid cells, so it stays cheap at 256x256 with hundreds of cells."""
    gx, gy = max(1, int(gx)), max(1, int(gy))
    jx = (rng.random((gy, gx)) - 0.5) * jitter
    jy = (rng.random((gy, gx)) - 0.5) * jitter
    cw, ch = w / gx, h / gy
    iy, ix = np.mgrid[0:gy, 0:gx]
    pts_x = (ix + 0.5 + jx) * cw
    pts_y = (iy + 0.5 + jy) * ch
    ys, xs = np.mgrid[0:h, 0:w]
    px = xs + 0.5
    py = ys + 0.5
    gcx = np.minimum((px / cw).astype(np.int64), gx - 1)
    gcy = np.minimum((py / ch).astype(np.int64), gy - 1)
    ds, ids = [], []
    span_x = range(-1, 2) if gx > 2 else range(-(gx // 2), gx - gx // 2)
    span_y = range(-1, 2) if gy > 2 else range(-(gy // 2), gy - gy // 2)
    for oy in span_y:
        for ox in span_x:
            cx = gcx + ox
            cy = gcy + oy
            wx = cx % gx
            wy = cy % gy
            # unwrap the point next to the pixel (torus)
            qx = pts_x[wy, wx] + (cx - wx) * cw
            qy = pts_y[wy, wx] + (cy - wy) * ch
            dx = (px - qx) / cw * aspect[0]
            dy = (py - qy) / ch * aspect[1]
            ds.append(np.sqrt(dx * dx + dy * dy))
            ids.append(wy * gx + wx)
    d = np.stack(ds, axis=-1)
    idarr = np.stack(ids, axis=-1)
    order = np.argsort(d, axis=-1)
    f1 = np.take_along_axis(d, order[..., :1], -1)[..., 0]
    cell = np.take_along_axis(idarr, order[..., :1], -1)[..., 0]
    if d.shape[-1] > 1:
        f2 = np.take_along_axis(d, order[..., 1:2], -1)[..., 0]
        # identical ids can appear twice for tiny grids: find the first different one
        second_id = np.take_along_axis(idarr, order[..., 1:2], -1)[..., 0]
        same = second_id == cell
        if same.any() and d.shape[-1] > 2:
            f2 = np.where(same, np.take_along_axis(d, order[..., 2:3], -1)[..., 0], f2)
    else:
        f2 = f1 + 1.0
    pts = np.stack([pts_x.ravel() % w, pts_y.ravel() % h], axis=1)
    return Voronoi(cell.astype(np.int32), f1, f2, pts, gx, gy)


def cell_argmax(cell: np.ndarray, score: np.ndarray, mask: np.ndarray, ncell: int
                ) -> tuple[np.ndarray, np.ndarray]:
    """Per cell: flat index of the highest ``score`` among ``mask`` pixels
    (-1 when the cell has none) and the number of ``mask`` pixels."""
    fc = cell.ravel()
    idx = np.nonzero(mask.ravel())[0]
    counts = np.bincount(fc[idx], minlength=ncell)
    best = np.full(ncell, -1, dtype=np.int64)
    if len(idx):
        order = np.lexsort((score.ravel()[idx], fc[idx]))
        sc = fc[idx][order]
        last = np.r_[sc[1:] != sc[:-1], True]
        best[sc[last]] = idx[order][last]
    return best, counts


def cell_borders(cell: np.ndarray, wrap: bool) -> tuple[np.ndarray, np.ndarray]:
    """(lit, shaded) border pixels of a cell map.

    ``lit``: pixels whose upper or left neighbour lies in another cell (the
    top-left rim of a facet); ``shaded``: the bottom-right rim.
    """
    up = pa.neighbour(cell, 0, -1, wrap, fill=-1)
    left = pa.neighbour(cell, -1, 0, wrap, fill=-1)
    down = pa.neighbour(cell, 0, 1, wrap, fill=-1)
    right = pa.neighbour(cell, 1, 0, wrap, fill=-1)
    lit = ((up != cell) & (up >= 0)) | ((left != cell) & (left >= 0))
    shaded = (((down != cell) & (down >= 0)) | ((right != cell) & (right >= 0))) & ~lit
    return lit, shaded


# ------------------------------------------------------------------- prisms

# face classes inside a prism
F_LEFT, F_RIDGE, F_FRONT, F_RIGHT = range(4)


@dataclass
class Prism:
    x: int              # column of the base's left edge
    y: int              # bottom row (can lie below the canvas)
    w: int              # width in columns (measured horizontally)
    length: int         # rows, including the tip
    tip: int            # rows of the pointed top (0 = flat)
    shear: float        # columns per row, + leans right
    ridge: int          # column of the lit ridge (0..w-1)
    apex: int           # column of the tip's point
    role: str = "accent"
    faces: int = 2      # 2: lit | ridge | shaded   3: lit | ridge | front | shaded
    edge2: int = 0      # 3-face: first column of the shaded face
    broken: bool = False
    cut: float = 0.0    # broken top slope (rows per column, + = left side higher)
    notch: int = -1     # broken top: column knocked one row lower
    cap: int = 1        # broken top: thickness of the lit fracture face
    main: bool = False

    @property
    def body(self) -> int:
        return self.length - (0 if self.broken else self.tip)


def _face_of(p: Prism, c: int, f: float) -> int:
    """Face class of column ``c`` in a row ``f`` of the way up the tip (0 = body)."""
    b1 = p.ridge + (p.apex - p.ridge) * f
    rc = _rnd(b1)
    if c == rc:
        return F_RIDGE
    if c < b1:
        return F_LEFT
    if p.faces == 3:
        b2 = p.edge2 + (p.apex - p.edge2) * f
        if c < _rnd(b2):
            return F_FRONT
    return F_RIGHT


def raster_prism(p: Prism, w: int, h: int):
    """Pixels of a prism: (ys, xs, cols, rows, tip, face) as arrays."""
    ys, xs, cs, rs, ts, fs = [], [], [], [], [], []
    tops = None
    if p.broken:
        tops = []
        for c in range(p.w):
            k = c if p.cut > 0 else p.w - 1 - c
            tops.append(p.length - 1 - int(math.floor(abs(p.cut) * k + 1e-9)))
        if 0 <= p.notch < p.w:
            tops[p.notch] -= 1
    body = p.body
    for r in range(p.length):
        y = p.y - r
        if y < 0:
            break
        if y >= h:
            continue
        off = _shear_off(r, p.shear)
        f = 0.0
        if p.broken:
            cols = [c for c in range(p.w) if r <= tops[c]]
            tip_cols = {c for c in cols if r > tops[c] - p.cap}
        elif r >= body and p.tip > 0:
            f = (r - body + 1) / p.tip
            cmin = _rnd(f * p.apex)
            cmax = p.w - 1 - _rnd(f * (p.w - 1 - p.apex))
            cols = list(range(cmin, max(cmin, cmax) + 1))
            tip_cols = set(cols)
        else:
            cols = list(range(p.w))
            tip_cols = set()
        for c in cols:
            x = p.x + off + c
            if 0 <= x < w:
                ys.append(y)
                xs.append(x)
                cs.append(c)
                rs.append(r)
                ts.append(c in tip_cols)
                fs.append(_face_of(p, c, f))
    return (np.array(ys, dtype=np.int64), np.array(xs, dtype=np.int64), np.array(cs, dtype=np.int64),
            np.array(rs, dtype=np.int64), np.array(ts, dtype=bool), np.array(fs, dtype=np.int64))


def make_prism(rng: np.random.Generator, x_center: float, y: int, w: int, length: int,
               shear: float, steep: float, role: str = "accent", faces: int | None = None,
               broken: bool = False, sc: float = 1.0) -> Prism:
    """Build a prism with a sensible ridge / apex layout for its width."""
    w = max(1, int(w))
    length = max(1, int(length))
    x = int(math.floor(x_center - w / 2 + 0.5))
    if faces is None:
        faces = 3 if (w >= 6 and rng.random() < 0.6) or w >= 9 else 2
    if w <= 2:
        ridge = 0
        faces = 2
    elif faces == 2:
        ridge = (w - 1) // 2
        if w >= 5 and length >= 2.5 * w:
            ridge += int(rng.choice([-1, 0, 0]))
        ridge = int(np.clip(ridge, 1, w - 2))
    else:
        ridge = max(1, _rnd(w * rng.uniform(0.2, 0.3)))
    edge2 = 0
    apex = ridge
    if faces == 3:
        front = max(2, _rnd((w - ridge) * rng.uniform(0.45, 0.6)))
        edge2 = min(w - 1, ridge + front)
        apex = min(w - 1, ridge + max(1, (edge2 - ridge) // 2))
    reach = max(apex, w - 1 - apex)
    tip = 0 if broken else int(max(1, round(reach * steep)))
    tip = min(tip, max(0, length - 2))
    p = Prism(x, y, w, length, tip, shear, ridge, apex, role, faces, edge2)
    if broken:
        p.broken = True
        p.cut = float(rng.choice([-1, 1])) * float(rng.choice([0.5, 1.0, 1.0]))
        p.cap = 1 if sc < 2 else 2
        if w >= 4 and rng.random() < 0.5:
            p.notch = int(rng.integers(1, w - 1))
    return p


def body_steps(p: Prism, face: np.ndarray, f: float) -> np.ndarray:
    lit = LIT if f >= 0.25 else MID
    st = np.where((face == F_LEFT) | (face == F_RIDGE), lit, DARK)
    if p.faces == 3 and f >= 0.35:
        st = np.where(face == F_FRONT, MID, st)
    return st


def tip_steps(p: Prism, face: np.ndarray, f: float) -> np.ndarray:
    if p.broken:
        st = np.where((face == F_LEFT) | (face == F_RIDGE), TIP, LIT)
    else:
        st = np.select([face == F_LEFT, face == F_RIDGE, face == F_FRONT], [TIP, TIP, LIT], MID)
    return st + (1 if f < 0.3 else 0)


# --------------------------------------------------------------- generator


class CrystalGenerator(BaseGenerator):
    category = "crystal"

    # ------------------------------------------------------------ setup
    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        return crystal_roles(s)

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": max(3, k), "accent": 7, "accent2": 6, "glow": 4}

    @staticmethod
    def _block(ctx: GenContext) -> bool:
        return ctx.settings.variant == "block"

    def _facet(self, ctx: GenContext) -> float:
        s = ctx.settings
        f = s.facet
        if s.style == "programmer":
            f *= 0.5
        return f

    # =============================================================== layers
    def layer_base(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_base(ctx)
            return
        c = ctx.canvas
        c.clear_all()
        prisms = self._layout(ctx)
        h, w = ctx.h, ctx.w
        pid = np.full((h, w), -1, dtype=np.int32)
        col = np.zeros((h, w), dtype=np.int32)
        row = np.zeros((h, w), dtype=np.int32)
        tip = np.zeros((h, w), dtype=bool)
        face = np.zeros((h, w), dtype=np.int32)
        for i, p in enumerate(prisms):
            ys, xs, cs, rs, ts, fs = raster_prism(p, w, h)
            if len(ys) == 0:
                continue
            pid[ys, xs] = i
            col[ys, xs] = cs
            row[ys, xs] = rs
            tip[ys, xs] = ts
            face[ys, xs] = fs
        rock = self._rock_mask(ctx, prisms)
        pid[rock] = -1
        # tiny slivers of a back prism peeking out between others read as noise
        pid = self._drop_slivers(ctx, pid, col, row, tip, face, prisms)
        tip &= pid >= 0
        d = ctx.data
        d.update(prisms=prisms, pid=pid, col=col, row=row, tip=tip, face=face, rock=rock)
        d["keep"] = np.zeros((h, w), dtype=bool)   # pixels that stay opaque when translucent
        crystal = pid >= 0
        # flat two-tone: lit half / shaded half
        d["step"] = np.where((face == F_LEFT) | (face == F_RIDGE), MID, DARK).astype(np.int32)
        self._paint(ctx, crystal)
        c.tag("protect")[crystal] = True
        if rock.any():
            self._paint_rock_base(ctx, rock)

    def layer_material(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_material(ctx)
            return
        d = ctx.data
        f = self._facet(ctx)
        pid, face = d["pid"], d["face"]
        step = d["step"]
        for i, p in enumerate(d["prisms"]):
            m = pid == i
            if m.any():
                step[m] = body_steps(p, face[m], f)
        self._paint(ctx, pid >= 0)
        rock = d["rock"]
        if rock.any():
            self._rock_texture(ctx, rock)

    def layer_large_detail(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_large(ctx)
            return
        # pyramid tip facets / fracture faces, and the girdle line under the tip
        d = ctx.data
        f = self._facet(ctx)
        pid, face, row, tip = d["pid"], d["face"], d["row"], d["tip"]
        step = d["step"]
        for i, p in enumerate(d["prisms"]):
            m = (pid == i) & tip
            if m.any():
                step[m] = tip_steps(p, face[m], f)
            if f >= 0.45 and not p.broken and p.tip > 0 and p.w >= 4 and p.body >= 4:
                g = (pid == i) & (row == p.body - 1) & (face != F_RIDGE)
                step[g] = np.minimum(step[g] + 1, DEEP)
        self._paint(ctx, pid >= 0)

    def layer_medium_detail(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_medium(ctx)
            return
        # bright ridge lines where the lit face meets the next face
        d = ctx.data
        f = self._facet(ctx)
        if f < 0.2:
            return
        pid, face, row, tip = d["pid"], d["face"], d["row"], d["tip"]
        step = d["step"]
        frac = float(np.clip(0.3 + 0.85 * f, 0, 1))
        for i, p in enumerate(d["prisms"]):
            if p.w < 3:
                continue
            start = int(p.body * (1 - frac))
            m = (pid == i) & (face == F_RIDGE) & (row >= start) & ~tip
            if not m.any():
                continue
            upper = row >= start + max(1, (p.body - start) // 3)
            step[m] = np.where(upper[m], TIP, LIT)
            d["keep"][m] = True
        self._paint(ctx, pid >= 0)

    def layer_small_detail(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_small(ctx)
            return
        s = ctx.settings
        d = ctx.data
        pid, face, row, tip = d["pid"], d["face"], d["row"], d["tip"]
        step = d["step"]
        rng = ctx.rng("growth")
        f = self._facet(ctx)
        # growth steps: short darker notches across the faces of big prisms
        for i, p in enumerate(d["prisms"]):
            if p.w < 4 or p.body < 7:
                continue
            n = int(round((p.body / 8.0) * (0.25 + 0.75 * s.roughness) * min(1.0, ctx.scale / 2)))
            for _ in range(n):
                r = int(rng.integers(max(2, p.body // 4), max(3, p.body - 2)))
                fc = F_LEFT if rng.random() < 0.5 else (F_FRONT if p.faces == 3 else F_RIGHT)
                m = (pid == i) & (row == r) & (face == fc) & ~tip & ~d["keep"]
                if m.sum() >= 2:
                    step[m] = np.minimum(step[m] + 1, DEEP)
                    if f > 0.5:
                        above = pa.neighbour(m, 0, 1, False, fill=False) & (pid == i) & ~tip & ~d["keep"]
                        step[above] = np.maximum(step[above] - 1, TIP)
        # inclusions: tiny paired pixels inside large faces (bigger textures)
        if ctx.scale >= 2 or s.noise > 0.7:
            count = int(round(len(d["prisms"]) * (0.3 + s.noise) * min(2.0, ctx.scale / 2)))
            cand = (pid >= 0) & ~tip & ~d["keep"] & (face != F_RIDGE)
            cand &= pa.erode(cand, False)
            ys, xs = np.nonzero(cand)
            if len(xs):
                for _ in range(count):
                    k = int(rng.integers(len(xs)))
                    y, x = int(ys[k]), int(xs[k])
                    m = np.zeros_like(tip)
                    m[y, x] = True
                    if x + 1 < ctx.w and cand[y, x + 1]:
                        m[y, x + 1] = True
                    elif y + 1 < ctx.h and cand[y + 1, x]:
                        m[y + 1, x] = True
                    step[m] = np.clip(step[m] - 1, TIP, DEEP)
        self._paint(ctx, pid >= 0)
        rock = d["rock"]
        if rock.any():
            self._rock_pebbles(ctx, rock)

    def layer_cracks(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_cracks(ctx)
            return
        s = ctx.settings
        if s.cracks <= 0.05:
            return
        d = ctx.data
        pid, col, row, tip = d["pid"], d["col"], d["row"], d["tip"]
        step = d["step"]
        rng = ctx.rng("fractures")
        prisms = d["prisms"]
        order = sorted(range(len(prisms)), key=lambda i: -prisms[i].w * prisms[i].body)
        n = int(round(s.cracks * len(prisms) * 0.6 * max(1.0, ctx.scale) ** 0.5))
        for i in order[:n]:
            p = prisms[i]
            if p.w < 4 or p.body < 6:
                continue
            # an internal fracture plane: a short slanted line of lighter pixels
            # with darker pixels under it (reads as a reflective crack)
            r0 = int(rng.integers(max(2, p.body // 4), max(3, p.body * 3 // 4)))
            c0 = int(rng.integers(0, max(1, p.w - 3)))
            ln = int(max(2, min(p.w - 1, round(rng.uniform(2, 3.5) * max(1.0, ctx.scale) ** 0.7))))
            dirn = 1 if rng.random() < 0.5 else -1
            m = np.zeros_like(tip)
            under = np.zeros_like(tip)
            for k in range(ln):
                cc = c0 + k
                rr = r0 + dirn * (k // 2)
                if cc >= p.w - 1 or rr >= p.body - 1 or rr < 1:
                    break
                m |= (pid == i) & (col == cc) & (row == rr)
                under |= (pid == i) & (col == cc) & (row == rr - 1)
            m &= ~d["keep"] & ~tip
            under &= ~m & ~d["keep"] & ~tip
            if m.sum() >= 2:
                step[m] = np.maximum(step[m] - 1, TIP)
                step[under] = np.minimum(step[under] + 1, DEEP)
        self._paint(ctx, pid >= 0)
        rock = d["rock"]
        if rock.any() and ctx.scale >= 2:
            cr = self.crack_paths(ctx, s.cracks * 0.6, "rock_cracks", mask=rock, length=(0.1, 0.3))
            cr &= rock & ~pa.edge_of(rock, 0, -1)
            if cr.any():
                ctx.canvas.set(cr, "base", 0)

    def layer_highlights(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_highlights(ctx)
            return
        d = ctx.data
        f = self._facet(ctx)
        pid, face, row, tip = d["pid"], d["face"], d["row"], d["tip"]
        step = d["step"]
        rng = ctx.rng("sparkle")
        crystal = pid >= 0
        # lit left rim where the rim borders empty space (upper part of the body)
        empty_left = pa.neighbour(pid, -1, 0, False, fill=-1) < 0
        left_rock = pa.neighbour(d["rock"], -1, 0, False, fill=False)
        rim = crystal & empty_left & ~left_rock & ~tip
        for i, p in enumerate(d["prisms"]):
            rm = rim & (pid == i) & (row >= int(p.body * 0.35))
            step[rm] = np.minimum(step[rm], LIT)
        # apex points and a sparkle near the tip of the biggest prisms
        prisms = d["prisms"]
        big = sorted(range(len(prisms)), key=lambda i: -prisms[i].w * prisms[i].length)
        n_sp = 1 + int(ctx.scale >= 2) + int(f > 0.7 and len(prisms) > 2)
        for rank, i in enumerate(big):
            p = prisms[i]
            m = pid == i
            if not m.any():
                continue
            if not p.broken and p.tip > 0 and f >= 0.15:
                top = m & tip & (row == row[m].max())
                step[top] = SPEC
                d["keep"][top] = True
            if p.broken and f >= 0.4:
                cap = m & tip & (face == F_RIDGE)
                step[cap] = SPEC
                d["keep"][cap] = True
            if rank < n_sp and p.w >= 3 and p.body >= 5 and f >= 0.3:
                r = p.body - 2 - int(rng.integers(0, max(1, p.body // 5)))
                sp = m & (face == F_RIDGE) & (row == r) & ~tip
                if ctx.scale >= 3:
                    sp |= pa.neighbour(sp, 0, 1, False, fill=False) & m & ~tip
                step[sp] = SPEC
                d["keep"][sp] = True
        self._paint(ctx, crystal)
        rock = d["rock"]
        if rock.any():
            c = ctx.canvas
            top = pa.edge_of(rock, 0, -1) & ~pa.neighbour(crystal, 0, -1, False, fill=False)
            keep = self.height_field(ctx, "rock_lit", 1.5, 1) > 0.3
            c.set(top & keep, "base", self._rock_levels(ctx)[3])

    def layer_shadows(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_shadows(ctx)
            return
        d = ctx.data
        pid, row, tip = d["pid"], d["row"], d["tip"]
        step = d["step"]
        crystal = pid >= 0
        # far (right) rim
        empty_right = pa.neighbour(pid, 1, 0, False, fill=-1) < 0
        rim = crystal & empty_right & ~tip & ~d["keep"]
        step[rim] = np.maximum(step[rim], DEEP)
        # occlusion: a back prism darkens where a front prism overlaps it
        occ = np.zeros_like(crystal)
        for dx, dy in pa.N4:
            nb = pa.neighbour(pid, dx, dy, False, fill=-1)
            occ |= crystal & (nb > pid)
        occ &= ~d["keep"]
        step[occ] = np.maximum(step[occ], DEEP)
        # ambient occlusion at the bases
        for i, p in enumerate(d["prisms"]):
            ao = max(1, int(round(p.body * 0.2)))
            m = (pid == i) & (row < ao) & ~d["keep"]
            step[m] = np.minimum(step[m] + 1, OUT)
        rock = d["rock"]
        if rock.any():
            above_rock = crystal & pa.neighbour(rock, 0, 1, False, fill=False) & ~d["keep"]
            step[above_rock] = np.maximum(step[above_rock], DEEP)
        self._paint(ctx, crystal)
        if rock.any():
            c = ctx.canvas
            lv = self._rock_levels(ctx)
            right = (pa.edge_of(rock, 1, 0) | pa.edge_of(rock, 0, 1)) & ~pa.edge_of(rock, 0, -1)
            c.set(right, "base", lv[0])

    def layer_accent(self, ctx: GenContext) -> None:
        if self._block(ctx):
            self._block_accent(ctx)
            return
        s = ctx.settings
        if s.glow <= 0.02:
            return
        d = ctx.data
        c = ctx.canvas
        pid, row, tip, col = d["pid"], d["row"], d["tip"], d["col"]
        step = d["step"]
        g = s.glow
        n_glow = c.length_of("glow")
        prisms = d["prisms"]
        order = sorted(range(len(prisms)), key=lambda i: -prisms[i].w * prisms[i].length)
        n_lit = 1 + int(round((len(prisms) - 1) * max(0.0, g - 0.4) * 1.2))
        core_all = np.zeros_like(tip)
        for rank, i in enumerate(order[:n_lit]):
            p = prisms[i]
            m = pid == i
            if not m.any() or p.w < 3 or p.body < 3:
                continue
            # a lens-shaped core just right of the ridge, inside the body
            cc = min(p.w - 2, p.ridge + 1)
            core_w = 1 + int(p.w >= 6 and g > 0.3) + int(p.w >= 11 and g > 0.5)
            span = (0.25 + 0.5 * g) if rank == 0 else (0.15 + 0.4 * g)
            mid_r = p.body * 0.5
            r0 = max(1, int(round(mid_r - p.body * span / 2)))
            r1 = min(p.body - 1, max(r0 + 2, int(round(mid_r + p.body * span / 2))))
            rows = np.arange(r0, r1)
            prof = np.sin(np.pi * (rows - r0 + 0.5) / max(1, r1 - r0))
            core = np.zeros_like(tip)
            bright = np.zeros_like(tip)
            for r, pr in zip(rows, prof):
                wdt = max(1, int(round(core_w * pr + 0.25)))
                c0 = cc - (wdt - 1) // 2
                cols = [cc2 for cc2 in range(c0, c0 + wdt) if 0 < cc2 < p.w - 1]
                sel = m & (row == r) & np.isin(col, cols) & ~tip
                core |= sel
                if pr > 0.55:
                    bright |= sel & (col == cc)
            if core.sum() < 2:
                continue
            core_all |= core
            c.set(core, "glow", np.where(bright, n_glow - 1, n_glow - 2))
            if g > 0.5:
                # inner light: the crystal around the core brightens one step
                halo = pa.dilate(core, False) & m & ~core & ~tip & ~d["keep"]
                step[halo] = np.maximum(step[halo] - 1, TIP)
            if g > 0.8 and not p.broken and p.tip > 0:
                top = m & tip & (row == row[m].max())
                c.set(top, "glow", n_glow - 1)
                core_all |= top
        self._paint(ctx, (pid >= 0) & ~core_all)
        c.emissive[core_all] = True
        d["keep"][core_all] = True
        # glowing seams where the crystals meet the rock
        rock = d["rock"]
        if rock.any() and g > 0.45:
            seam = rock & pa.neighbour(pid >= 0, 0, -1, False, fill=False)
            seam &= self.height_field(ctx, "seam", 1.6, 1) > 1.1 - g
            seam = pa.remove_small_clusters(seam.astype(np.int32), 2, False).astype(bool) & seam
            if seam.any():
                c.set(seam, "glow", max(0, n_glow - 3))
                c.emissive[seam] = True

    # ============================================================ finishing
    def finish(self, ctx: GenContext) -> None:
        s = ctx.settings
        if s.transparency <= 0.02:
            return
        c = ctx.canvas
        crystal_ramp = np.isin(c.ramp, [ROLE_IDS[r] for r in _CRYSTAL_ROLES]) & c.opaque
        d = ctx.data
        if self._block(ctx):
            cell = d.get("cell")
            if cell is None:
                return
            inner = crystal_ramp.copy()
            for dx, dy in pa.N4:
                inner &= pa.neighbour(cell, dx, dy, ctx.wrap) == cell
            inner &= ~c.emissive & ~d.get("keep", np.zeros_like(inner))
            # the brightest and darkest steps stay opaque so facets stay crisp
            top = c.max_level()
            inner &= (c.level < top - 1) & (c.level > 1)
            c.alpha[inner] = 200
            return
        pid = d.get("pid")
        if pid is None:
            return
        inner = (pid >= 0) & crystal_ramp
        for dx, dy in pa.N4:
            inner &= pa.neighbour(pid, dx, dy, False, fill=-1) == pid
        inner &= ~d["keep"] & ~c.emissive & ~d["tip"]
        c.alpha[inner] = 200

    # ============================================================== helpers
    def _paint(self, ctx: GenContext, mask: np.ndarray) -> None:
        """Resolve shading steps to levels of each prism's ramp."""
        d = ctx.data
        c = ctx.canvas
        pid, step = d["pid"], d["step"]
        for i, p in enumerate(d["prisms"]):
            m = mask & (pid == i) & (c.ramp != ROLE_IDS["glow"])
            if not m.any():
                continue
            n = c.length_of(p.role)
            c.set(m, p.role, np.maximum(0, n - 1 - step))

    def _drop_slivers(self, ctx: GenContext, pid, col, row, tip, face, prisms) -> np.ndarray:
        """Hand tiny visible fragments of a prism to the neighbouring prism (or drop them)."""
        out = pid.copy()
        labels, sizes = pa.label_components(pid, wrap=False, mask=pid >= 0)
        min_px = 3 if ctx.scale < 2 else int(3 * ctx.scale)
        for lab, size in enumerate(sizes):
            if size >= min_px:
                continue
            m = labels == lab
            own = int(pid[m][0])
            if prisms[own].main:
                continue
            ring = pa.outline(m) & (pid >= 0)
            if ring.any():
                vals, counts = np.unique(pid[ring], return_counts=True)
                new = int(vals[np.argmax(counts)])
                out[m] = new
                # borrow the neighbour's local coordinates so shading stays coherent
                ys, xs = np.nonzero(m)
                for y, x in zip(ys, xs):
                    for dx, dy in pa.N4:
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < ctx.w and 0 <= ny < ctx.h and pid[ny, nx] == new:
                            col[y, x] = col[ny, nx] - dx
                            row[y, x] = row[ny, nx] + dy
                            face[y, x] = face[ny, nx]
                            tip[y, x] = tip[ny, nx]
                            break
            else:
                out[m] = -1
        return out

    # ---------------------------------------------------------------- layout
    def _silhouette_hint(self, ctx: GenContext) -> tuple[float, float, float]:
        """(height, count, spread) multipliers from the reference silhouette."""
        a = ctx.analysis
        if a.is_default or not a.has_alpha:
            return 1.0, 1.0, 1.0
        infl = 0.6 * ctx.settings.source_influence
        sil = a.silhouette
        d_top, d_stems, d_width = 0.85, 3, 0.4
        hmul = 1 + (np.clip(sil.top / d_top, 0.4, 1.3) - 1) * infl
        cmul = 1 + (np.clip(max(1, sil.stems) / d_stems, 0.4, 2.0) - 1) * infl
        wmul = 1 + (np.clip(sil.width / d_width, 0.5, 1.6) - 1) * infl
        return float(hmul), float(cmul), float(wmul)

    def _layout(self, ctx: GenContext) -> list[Prism]:
        v = ctx.settings.variant
        rng = ctx.rng("layout")
        if v == "single":
            return self._layout_single(ctx, rng)
        if v == "shard":
            return self._layout_shards(ctx, rng)
        if v == "bud":
            return self._layout_bud(ctx, rng)
        return self._layout_cluster(ctx, rng)

    def _fit(self, ctx: GenContext, p: Prism, min_len: int = 3) -> Prism:
        """Shorten / nudge a leaning prism so it stays inside the canvas."""
        for _ in range(96):
            off = _shear_off(p.length - 1, p.shear)
            lo = p.x + min(0, off)
            hi = p.x + p.w - 1 + max(0, off)
            if lo >= 0 and hi <= ctx.w - 1:
                break
            leaning_out = (lo < 0 and p.shear < 0) or (hi > ctx.w - 1 and p.shear > 0)
            if leaning_out and p.length > min_len and p.x >= 0 and p.x + p.w <= ctx.w:
                p.length -= 1
                if not p.broken:
                    p.tip = min(p.tip, max(1, p.length - 2))
                continue
            p.x += 1 if lo < 0 else -1
        return p

    def _base_row(self, ctx: GenContext) -> int:
        return ctx.h - 1

    @staticmethod
    def _lean_width(w_perp: float, shear: float, sc: float) -> int:
        return max(2, _rnd(w_perp * sc * math.sqrt(1 + shear * shear)))

    @staticmethod
    def _slope(v: float) -> float:
        """Snap a lean to a clean pixel-art slope (columns per row)."""
        a = abs(v)
        if a < 0.14:
            q = 0.0
        elif a < 0.29:
            q = 0.25
        elif a < 0.42:
            q = 1 / 3
        elif a < 0.75:
            q = 0.5
        else:
            q = 1.0
        return math.copysign(q, v) if q else 0.0

    def _fan(self, ctx: GenContext, rng: np.random.Generator, n: int, cx: float, main_len: int,
             main_w: float, spread: float, max_lean: float, broken_p: float = 0.0,
             role2_p: float = 0.3, main_broken: bool = False, steep: float | None = None) -> list[Prism]:
        """Prisms fanning out from a common foot: upright in the middle, leaning
        further out (and shorter / slimmer) towards the sides.  Returned back to front."""
        sc = ctx.scale
        y0 = self._base_row(ctx)
        if n <= 1:
            ts = np.array([0.0])
        else:
            ts = np.linspace(-1, 1, n)
            ts = ts + rng.uniform(-0.12, 0.12, n) * (2 / n)
            ts = np.clip(ts, -1, 1)
        main_i = int(np.argmin(np.abs(ts)))
        ts[main_i] = 0.0 if n % 2 else ts[main_i] * 0.3
        prisms: list[tuple[float, Prism]] = []
        for k, t in enumerate(ts):
            a = abs(float(t))
            is_main = k == main_i
            lean = self._slope(t * max_lean * rng.uniform(0.85, 1.15)) if not is_main else self._slope(t * 0.5)
            if is_main:
                L = main_len
                wp = main_w
            else:
                L = main_len * (1 - 0.42 * a ** 1.1) * rng.uniform(0.85, 1.02)
                wp = (main_w * (1 - 0.42 * a)) * rng.uniform(0.9, 1.08) / sc
                wp = wp * sc
            bx = cx + float(t) * spread
            pw = max(2, _rnd(wp * math.sqrt(1 + lean * lean))) if not is_main else max(2, _rnd(wp))
            if lean != 0:
                # keep the tip inside the canvas: the foot is near the middle
                room = (ctx.w - 1 - (bx + pw / 2)) if lean > 0 else (bx - pw / 2)
                L = min(L, max(3, room / abs(lean) + 1))
            broken = main_broken if is_main else bool(rng.random() < broken_p)
            role = "accent" if is_main or rng.random() >= role2_p else "accent2"
            st = steep if steep is not None else (float(rng.choice([1.0, 1.0, 1.5])) if pw >= 5 else 1.0)
            pr = make_prism(rng, bx, y0, pw, int(round(L)), lean, st, role, broken=broken, sc=sc)
            pr.main = is_main
            pr = self._fit(ctx, pr)
            prisms.append((a if not is_main else -1.0, pr))
        # outermost first (furthest back), the main prism last
        prisms.sort(key=lambda kv: -kv[0])
        return [p for _, p in prisms]

    def _leaner(self, ctx: GenContext, rng: np.random.Generator, side: int, foot: float, pw16: float,
                length: float, min_frac: float, role: str, slopes=(0.5, 0.5, 0.5, 1 / 3, 1.0)) -> Prism:
        """A crystal leaning out to ``side`` from ``foot``; flattens its lean when
        the canvas edge would cut it too short."""
        sc = ctx.scale
        first = float(rng.choice(slopes))
        order = [first] + [v for v in (1.0, 0.5, 1 / 3, 0.25) if v < first]
        best = None
        for lean in order:
            pw = self._lean_width(pw16, lean, sc)
            room = (ctx.w - 1 - (foot + pw / 2)) if side > 0 else (foot - pw / 2)
            L = min(length, max(2, room / lean + 1.5))
            best = (lean, pw, L)
            if L >= length * (min_frac + (0.2 if lean >= 1 else 0.0)):
                break
        lean, pw, L = best
        pr = make_prism(rng, foot, self._base_row(ctx), pw, int(round(L)), lean * side, 1.0, role, sc=sc)
        return self._fit(ctx, pr)

    def _layout_cluster(self, ctx: GenContext, rng: np.random.Generator) -> list[Prism]:
        s = ctx.settings
        sc = ctx.scale
        hmul, cmul, wmul = self._silhouette_hint(ctx)
        n = int(round((2.6 + s.density * 3.2) * cmul))
        n += int(round(math.log2(max(1.0, sc)) * (0.3 + s.density)))
        n = max(1, n)
        y0 = self._base_row(ctx)
        main_len = max(4, int(round(ctx.h * (0.5 + 0.45 * s.height) * hmul)))
        main_w = max(3, _rnd(float(rng.uniform(5.6, 7.2)) * sc))
        cx = ctx.w / 2 + float(rng.choice([-0.5, 0.0, 0.5])) * max(1.0, sc)
        main = make_prism(rng, cx, y0, main_w, main_len, 0.0, float(rng.choice([1.0, 1.0, 1.5])),
                          "accent", sc=sc)
        main.main = True
        first = -1 if rng.random() < 0.5 else 1
        leaners, backs, fronts = [], [], []
        extra = n - 1
        for k in range(min(2, extra)):
            side = first if k == 0 else -first
            foot = cx + side * (main_w / 2 - rng.uniform(0.3, 1.0) * sc) * wmul
            role = "accent2" if rng.random() < 0.3 else "accent"
            leaners.append(self._leaner(ctx, rng, side, foot, rng.uniform(3.1, 3.7),
                                        main_len * rng.uniform(0.55, 0.72), 0.45, role))
        extra -= 2
        k = 0
        while extra > 0:
            side = first if k % 2 == 0 else -first
            role = "accent2" if rng.random() < 0.35 else "accent"
            if k % 3 == 2:
                # small crystal in front of the foot
                foot = cx + side * rng.uniform(0.8, main_w / 2) 
                fronts.append(self._leaner(ctx, rng, side, foot, rng.uniform(2.0, 2.5),
                                           main_len * rng.uniform(0.26, 0.36), 0.5, role, (0.5, 1.0)))
            else:
                # slimmer crystal standing behind, between the main one and a leaner
                foot = cx + side * (main_w / 2 + rng.uniform(-0.5, 0.8) * sc)
                backs.append(self._leaner(ctx, rng, side, foot, rng.uniform(2.8, 3.6),
                                          main_len * rng.uniform(0.6, 0.78), 0.6, role, (1 / 3, 0.5)))
            extra -= 1
            k += 1
        return backs + leaners + [main] + fronts

    def _layout_single(self, ctx: GenContext, rng: np.random.Generator) -> list[Prism]:
        s = ctx.settings
        sc = ctx.scale
        hmul, _, _ = self._silhouette_hint(ctx)
        y0 = self._base_row(ctx)
        L = max(5, int(round(ctx.h * (0.6 + 0.38 * s.height) * hmul)))
        lean = float(rng.choice([0.0, 0.0, 0.0, 0.25, -0.25]))
        W = max(3, _rnd(rng.uniform(6.6, 7.6) * sc))
        cx = ctx.w / 2 - _shear_off(L - 1, lean) / 2
        main = make_prism(rng, cx, y0, W, L, lean, float(rng.choice([1.0, 1.34])), "accent",
                          faces=3 if W >= 5 else 2, sc=sc)
        main.main = True
        main = self._fit(ctx, main, min_len=L)
        out = []
        buds = int(s.density >= 0.35) + int(s.density >= 0.75) + int(sc >= 2 and s.density > 0.5)
        sides = [-1, 1] if rng.random() < 0.5 else [1, -1]
        for k in range(buds):
            side = sides[k % 2]
            shear = float(rng.choice([0.5, 1.0])) * side
            bw = self._lean_width(rng.uniform(2.2, 2.8), shear, sc)
            bl = max(3, int(round(L * rng.uniform(0.28, 0.42))))
            bx = cx + side * (W / 2 + (k // 2) * bw)
            pr = make_prism(rng, bx, y0, bw, bl, shear, 1.0, "accent2" if k == 1 else "accent", sc=sc)
            out.append(self._fit(ctx, pr))
        # the first bud sits behind the prism, the others in front
        return out[:1] + [main] + out[1:]

    def _layout_shards(self, ctx: GenContext, rng: np.random.Generator) -> list[Prism]:
        s = ctx.settings
        sc = ctx.scale
        hmul, cmul, wmul = self._silhouette_hint(ctx)
        n = max(2, int(round((2 + 2.4 * s.density) * cmul)) + int(sc >= 4))
        big_len = max(4, int(round(ctx.h * (0.46 + 0.42 * s.height) * hmul)))
        main_w = max(3.0, float(rng.uniform(4.4, 5.4)) * sc * wmul ** 0.5)
        cx = ctx.w / 2 + float(rng.choice([-1.0, -0.5, 0.5, 1.0])) * max(1.0, sc)
        spread = main_w / 2 + (0.2 + 0.6 * s.density) * sc
        prisms = self._fan(ctx, rng, n, cx, big_len, main_w, spread, 0.62, broken_p=0.7,
                           role2_p=0.35, main_broken=bool(rng.random() < 0.85))
        # break up the symmetry: shards lean a little more than cluster crystals
        chips = int(s.density > 0.3) + int(s.density > 0.7 and sc >= 2)
        for _ in range(chips):
            fx = float(rng.choice([rng.uniform(0.12, 0.3), rng.uniform(0.7, 0.88)]))
            shear = float(rng.choice([-1.0, 1.0]))
            pr = make_prism(rng, fx * ctx.w, self._base_row(ctx), max(2, _rnd(2.6 * sc)), max(2, _rnd(2.2 * sc)),
                            shear, 1.0, "accent", broken=True, sc=sc)
            prisms.append(self._fit(ctx, pr, min_len=2))
        return prisms

    def _layout_bud(self, ctx: GenContext, rng: np.random.Generator) -> list[Prism]:
        s = ctx.settings
        sc = ctx.scale
        L = max(3, int(round(ctx.h * (0.22 + 0.24 * s.height))))
        W = max(3.0, float(rng.uniform(4.6, 5.8)) * sc)
        cx = ctx.w / 2 + float(rng.choice([-0.5, 0.0, 0.5])) * sc
        n = 1 + int(s.density >= 0.25) * 2 + int(s.density >= 0.8) * 2
        return self._fan(ctx, rng, n, cx, L, W, (2.2 + 0.8 * s.density) * sc, 1.1, role2_p=0.25, steep=1.0)

    # ------------------------------------------------------------------ rock
    def _rock_levels(self, ctx: GenContext) -> tuple[int, int, int, int]:
        """(dark, body, rim, top) levels of the base ramp used by the rock foot."""
        n = ctx.canvas.length_of("base")
        top = max(1, _rnd(0.66 * (n - 1)))
        rim = max(1, min(top - 1, _rnd(0.5 * (n - 1))))
        body = max(0, min(rim - 1, _rnd(0.33 * (n - 1))))
        dark = max(0, body - 1)
        return dark, body, rim, top

    def _rock_mask(self, ctx: GenContext, prisms: list[Prism]) -> np.ndarray:
        """Rock chunks around the crystal feet (in front of them).  ``rock`` sets
        how many / how big; 0 leaves the crystals growing straight from the edge."""
        s = ctx.settings
        amount = {"cluster": s.rock, "single": s.rock * 0.9, "shard": s.rock * 0.9,
                  "bud": s.rock * 0.55}.get(s.variant, s.rock)
        m = np.zeros((ctx.h, ctx.w), dtype=bool)
        ids = np.full((ctx.h, ctx.w), -1, dtype=np.int32)
        ctx.data["rock_id"] = ids
        if amount < 0.12 or not prisms:
            return m
        sc = ctx.scale
        rng = ctx.rng("rock_chunks")
        mains = [p for p in prisms if p.main] or prisms
        lo = min(max(0, p.x) for p in mains)
        hi = max(min(ctx.w - 1, p.x + p.w - 1) for p in mains)
        amount = (amount - 0.25) / 0.75
        if amount <= 0:
            return m
        n = 1 + int(amount > 0.45) + int(round(math.log2(max(1.0, sc)) * amount))
        # boulders beside the main foot, alternating sides, with gaps between them
        side = -1 if rng.random() < 0.5 else 1
        taken = np.zeros(ctx.w, dtype=bool)
        for k in range(n):
            edge = lo if side < 0 else hi
            cxl = edge + side * rng.uniform(-0.3, 1.2 + k // 2 * 2.5) * sc
            side = -side
            hw = max(1.2, rng.uniform(1.3, 2.0) * sc * (0.8 + 0.4 * amount))
            hl = max(1.5, rng.uniform(1.8, 2.8) * sc * (0.75 + 0.35 * amount))
            x0 = max(0, int(math.floor(cxl - hw + 0.5)))
            x1 = min(ctx.w - 1, int(math.floor(cxl + hw - 0.5)))
            if x1 < x0 or taken[max(0, x0 - 1):x1 + 2].any():
                continue
            taken[x0:x1 + 1] = True
            for x in range(x0, x1 + 1):
                u = (x + 0.5 - cxl) / hw
                ht = int(round(hl * max(0.0, 1 - u * u) ** 0.6 + 0.3))
                if ht >= 1:
                    ids[ctx.h - ht:, x] = k
        m = ids >= 0
        # at 16px: no lonely one-pixel spikes on the outline
        spike = m & ~pa.neighbour(m, -1, 0, False, fill=False) & ~pa.neighbour(m, 1, 0, False, fill=False)
        spike &= ~pa.neighbour(m, 0, -1, False, fill=False) & pa.neighbour(m, 0, 1, False, fill=True)
        m &= ~spike
        ids[~m] = -1
        return m

    def _paint_rock_base(self, ctx: GenContext, rock: np.ndarray) -> None:
        c = ctx.canvas
        dark, body, rim, _ = self._rock_levels(ctx)
        ids = ctx.data.get("rock_id", np.where(rock, 0, -1))
        c.set(rock, "base", body)
        up = pa.neighbour(ids, 0, -1, False, fill=-1)
        left = pa.neighbour(ids, -1, 0, False, fill=-1)
        right = pa.neighbour(ids, 1, 0, False, fill=-1)
        top_edge = rock & (up != ids)
        left_edge = rock & (left != ids) & ~top_edge
        right_edge = rock & (right != ids) & ~top_edge
        c.set(left_edge, "base", rim)
        c.set(right_edge, "base", dark)
        c.set(top_edge, "base", rim)
        bottom_rows = np.zeros_like(rock)
        bottom_rows[-max(1, ctx.px(0.6)):, :] = True
        c.set(rock & bottom_rows & ~top_edge & ~left_edge, "base", dark)
        c.tag("rock")[rock] = True
        c.tag("protect")[rock] = True

    def _rock_texture(self, ctx: GenContext, rock: np.ndarray) -> None:
        c = ctx.canvas
        inner = rock & ~pa.edge_of(rock, 0, -1) & ~pa.edge_of(rock, -1, 0)
        if inner.sum() < 6:
            return
        dark, _, rim, _ = self._rock_levels(ctx)
        f = self.height_field(ctx, "rock_tex", 1.6, 2)
        vals = f[inner]
        hi = inner & (f > np.quantile(vals, 0.72))
        lo = inner & (f < np.quantile(vals, 0.22))
        hi = pa.remove_small_clusters(hi.astype(np.int32), 2, False).astype(bool) & hi
        lo = pa.remove_small_clusters(lo.astype(np.int32), 2, False).astype(bool) & lo
        c.set(hi, "base", rim)
        c.set(lo, "base", dark)

    def _rock_pebbles(self, ctx: GenContext, rock: np.ndarray) -> None:
        s = ctx.settings
        if ctx.scale < 2 and s.noise < 0.5:
            return
        count = int(round((0.5 + s.noise) * ctx.scale))
        inner = rock & ~pa.edge_of(rock, 0, -1)
        rim = self._rock_levels(ctx)[2]
        for cells in self.place_clusters(ctx, "rock_pebbles", count, (2, max(2, ctx.px(0.25) + 2)),
                                         mask=inner, compact=0.85):
            m = np.zeros_like(rock)
            pa.stamp(m, cells, False)
            ctx.canvas.set(m & inner, "base", rim)

    # ================================================================ block
    # A crystal block is a mosaic of large flat crystal faces (a jittered,
    # tileable Voronoi): every face has one tone from its random normal, a
    # two-step gradient toward the light, a lit top-left rim and a shaded
    # bottom-right rim.  Glints sit where faces meet.

    def _block_cells(self, ctx: GenContext) -> Voronoi:
        s = ctx.settings
        a = ctx.analysis
        infl = s.source_influence if not a.is_default else 0.0
        cs = 4.5 * (1 - infl) + a.cluster_size * infl
        size_mul = float(np.clip(np.sqrt(4.5 / max(1.0, cs)), 0.8, 1.25))
        n16 = (2.0 + 1.6 * s.density) * size_mul
        g = n16 * ctx.scale ** 0.75
        g = float(np.clip(g, 2, max(2, ctx.w / 4)))
        ax, ay = self.aspect(ctx)
        rng = ctx.rng("block_cells")
        gx = max(2, int(round(g / max(0.5, ax) ** 0.5)))
        gy = max(2, int(round(g * rng.uniform(0.9, 1.25) / max(0.5, ay) ** 0.5)))
        return voronoi(ctx.w, ctx.h, rng, gx, gy, 1.0)

    def _block_offsets(self, ctx: GenContext, vor: Voronoi) -> tuple[np.ndarray, np.ndarray]:
        """Pixel offset from its cell's point (wrap-aware)."""
        ys, xs = np.mgrid[0:ctx.h, 0:ctx.w]
        px, py = vor.points[vor.cell, 0], vor.points[vor.cell, 1]
        dx = xs + 0.5 - px
        dy = ys + 0.5 - py
        dx = (dx + ctx.w / 2) % ctx.w - ctx.w / 2
        dy = (dy + ctx.h / 2) % ctx.h - ctx.h / 2
        return dx, dy

    def _block_base(self, ctx: GenContext) -> None:
        vor = self._block_cells(ctx)
        cell = vor.cell
        ncell = len(vor.points)
        rng = ctx.rng("block_tone")
        # face tones spread evenly over LIT / MID / DARK; the reference's tone
        # weights decide how many faces land on each step
        w = np.asarray(ctx.data["weights"], dtype=np.float64)
        w3 = np.interp(np.linspace(0, 1, 3), np.linspace(0, 1, len(w)), w)
        w3 = w3 / w3.sum()
        rank = np.argsort(np.argsort(rng.random(ncell))) / max(1, ncell - 1)
        cell_step = np.where(rank < w3[0], DARK, np.where(rank < w3[0] + w3[1], MID, LIT))
        dx, dy = self._block_offsets(ctx, vor)
        ctx.data.update(cell=cell, vor=vor, cell_step=cell_step, dx=dx, dy=dy)
        ctx.data["step"] = cell_step[cell].astype(np.int32)
        ctx.data["keep"] = np.zeros((ctx.h, ctx.w), dtype=bool)
        ctx.data["roles"] = np.full((ctx.h, ctx.w), ROLE_IDS["accent"], dtype=np.int32)
        self._block_paint(ctx)

    def _block_paint(self, ctx: GenContext, mask: np.ndarray | None = None) -> None:
        c = ctx.canvas
        step = ctx.data["step"]
        roles = ctx.data["roles"]
        for role in _CRYSTAL_ROLES:
            m = roles == ROLE_IDS[role]
            if mask is not None:
                m &= mask
            m &= c.ramp != ROLE_IDS["glow"]
            if not m.any():
                continue
            n = c.length_of(role)
            c.set(m, role, np.maximum(0, n - 1 - step))

    def _block_material(self, ctx: GenContext) -> None:
        # each face brightens toward its lit (top-left) side: a two-step gradient
        f = self._facet(ctx)
        d = ctx.data
        cell, dx, dy = d["cell"], d["dx"], d["dy"]
        vor: Voronoi = d["vor"]
        rng = ctx.rng("block_grad")
        ncell = len(vor.points)
        tilt = rng.uniform(-0.6, 0.6, ncell)[cell]
        proj = -(dx + dy * (1 + tilt)) / max(1.0, ctx.w / max(vor.gx, vor.gy) / 2)
        cut = 0.35 - 0.3 * f
        lighter = proj > cut
        darker = proj < -1.1 + 0.2 * f
        step = d["step"]
        step[lighter] = np.maximum(step[lighter] - 1, TIP)
        if f > 0.5:
            step[darker] = np.minimum(step[darker] + 1, DEEP)
        self._block_paint(ctx)

    def _block_large(self, ctx: GenContext) -> None:
        # soft mottling inside the faces (stronger when facets are weak)
        s = ctx.settings
        d = ctx.data
        amt = 0.1 + 0.25 * (1 - self._facet(ctx)) + 0.1 * s.roughness
        dark = self.smooth_mask(ctx, "block_mottle_d", amt, 1.1, 2)
        light = self.smooth_mask(ctx, "block_mottle_l", amt * 0.8, 1.1, 2) & ~dark
        step = d["step"]
        step[dark] = np.minimum(step[dark] + 1, DEEP)
        step[light] = np.maximum(step[light] - 1, TIP)
        self._block_paint(ctx)

    def _block_medium(self, ctx: GenContext) -> None:
        # face rims: the top-left rim catches light, the bottom-right rim drops into shade
        f = self._facet(ctx)
        if f < 0.1:
            return
        d = ctx.data
        cell = d["cell"]
        lit, shaded = cell_borders(cell, ctx.wrap)
        keep = self.height_field(ctx, "block_edge_keep", 1.2, 1) < 0.4 + 0.65 * f
        step = d["step"]
        step[shaded] = np.minimum(step[shaded] + 1, DEEP)
        lit &= keep
        step[lit] = np.maximum(np.minimum(step[lit], LIT) - (1 if f > 0.6 else 0), TIP)
        d["rim_lit"] = lit
        self._block_paint(ctx)

    def _block_small(self, ctx: GenContext) -> None:
        # inclusions: short diagonal streaks (internal reflections) and a few
        # grains of the secondary crystal colour
        s = ctx.settings
        d = ctx.data
        rng = ctx.rng("block_streaks")
        step = d["step"]
        n = int(round((0.6 + 1.4 * s.noise) * ctx.scale ** 1.5))
        rim = d.get("rim_lit", np.zeros_like(d["keep"]))
        for _ in range(n):
            x, y = int(rng.integers(ctx.w)), int(rng.integers(ctx.h))
            ln = int(rng.integers(2, 3 + int(ctx.scale)))
            dirx = 1 if rng.random() < 0.5 else -1
            pts = [((x + k * dirx) % ctx.w, (y + k) % ctx.h) for k in range(ln)]
            if not ctx.wrap and any(not (0 <= x + k * dirx < ctx.w and y + k < ctx.h) for k in range(ln)):
                continue
            ids = {int(d["cell"][py, px]) for px, py in pts}
            if len(ids) > 1:
                continue
            for px, py in pts:
                if not rim[py, px] and not d["keep"][py, px]:
                    step[py, px] = max(TIP, int(step[py, px]) - 1)
        grains = int(round((0.4 + 1.6 * s.mineral) * ctx.scale ** 1.5))
        roles = d["roles"]
        for cells in self.place_clusters(ctx, "block_grains", grains, (2, max(3, ctx.px(0.25) + 2)),
                                         compact=0.8):
            m = np.zeros((ctx.h, ctx.w), dtype=bool)
            pa.stamp(m, cells, ctx.wrap)
            roles[m] = ROLE_IDS["accent2"]
            step[m] = np.where(pa.edge_of(m, 0, -1, ctx.wrap)[m], LIT, MID)
            ctx.canvas.tag("protect")[m] = True
        self._block_paint(ctx)

    def _block_cracks(self, ctx: GenContext) -> None:
        s = ctx.settings
        cr = self.crack_paths(ctx, s.cracks * 0.6, "block_cracks", length=(0.2, 0.45))
        if not cr.any():
            return
        d = ctx.data
        step = d["step"]
        step[cr] = DEEP
        lip = pa.neighbour(cr, 0, -1, ctx.wrap, fill=False) & ~cr
        step[lip] = np.maximum(step[lip] - 1, TIP)
        ctx.canvas.tag("crack")[cr] = True
        self._block_paint(ctx)

    def _junctions(self, ctx: GenContext) -> np.ndarray:
        cell = ctx.data["cell"]
        right = pa.neighbour(cell, 1, 0, ctx.wrap, fill=-1)
        down = pa.neighbour(cell, 0, 1, ctx.wrap, fill=-1)
        diag = pa.neighbour(cell, 1, 1, ctx.wrap, fill=-1)
        vals = np.stack([cell, right, down, diag])
        distinct = np.ones(cell.shape, dtype=np.int32)
        for i in range(1, 4):
            new = np.ones(cell.shape, dtype=bool)
            for j in range(i):
                new &= vals[i] != vals[j]
            distinct += new
        return distinct >= 3

    def _block_highlights(self, ctx: GenContext) -> None:
        d = ctx.data
        c = ctx.canvas
        step = d["step"]
        rng = ctx.rng("block_spark")
        f = self._facet(ctx)
        junction = self._junctions(ctx)
        ys, xs = np.nonzero(junction)
        spark = np.zeros((ctx.h, ctx.w), dtype=bool)
        if len(xs):
            ncell = len(ctx.data["vor"].points)
            k = max(1, int(round(ncell * (0.2 + 0.4 * f))))
            idx = rng.choice(len(xs), size=min(k, len(xs)), replace=False)
            for i in idx:
                y, x = int(ys[i]), int(xs[i])
                spark[y, x] = True
                if ctx.scale >= 4:
                    x2 = (x + 1) % ctx.w if ctx.wrap else min(ctx.w - 1, x + 1)
                    y2 = (y + 1) % ctx.h if ctx.wrap else min(ctx.h - 1, y + 1)
                    spark[y, x2] = spark[y2, x] = spark[y2, x2] = True
        step[spark] = SPEC
        d["keep"] |= spark
        d["spark"] = spark
        c.tag("protect")[spark] = True
        self._block_paint(ctx)

    def _block_shadows(self, ctx: GenContext) -> None:
        d = ctx.data
        junction = self._junctions(ctx) & ~d["keep"]
        step = d["step"]
        step[junction] = np.maximum(step[junction], DEEP)
        # the pixel right below a glint is the deepest shade of the valley
        spark = d.get("spark")
        if spark is not None:
            below = pa.neighbour(spark, 0, -1, ctx.wrap, fill=False) & ~d["keep"]
            step[below] = np.maximum(step[below], DEEP)
        self._block_paint(ctx)

    def _block_accent(self, ctx: GenContext) -> None:
        s = ctx.settings
        c = ctx.canvas
        d = ctx.data
        if s.glow <= 0.02:
            return
        vor: Voronoi = d["vor"]
        cell = d["cell"]
        rng = ctx.rng("block_glow")
        ncell = len(vor.points)
        k = max(1, int(round(ncell * (0.1 + 0.35 * s.glow))))
        chosen = np.zeros(ncell, dtype=bool)
        chosen[rng.choice(ncell, size=min(k, ncell), replace=False)] = True
        n = c.length_of("glow")
        core_r = 0.14 + 0.28 * s.glow
        glow = chosen[cell] & (vor.f1 < core_r)
        # cells whose core would be too small still get their two nearest pixels
        best, _ = cell_argmax(cell, -vor.f1, np.ones_like(glow), ncell)
        small = chosen & (np.bincount(cell[glow], minlength=ncell) < 2) & (best >= 0)
        glow.ravel()[best[small]] = True
        if not glow.any():
            return
        inner = glow & (vor.f1 < core_r * 0.5)
        centre = np.zeros(glow.size, dtype=bool)
        centre[best[chosen & (best >= 0)]] = True
        inner |= centre.reshape(glow.shape) & glow
        c.set(glow, "glow", max(0, n - 2) if s.glow > 0.5 else max(0, n - 3))
        c.set(inner, "glow", n - 1)
        c.emissive[glow] = True
        c.tag("protect")[glow] = True
        d["keep"] |= glow
