"""Item: inventory sprites for materials - ingots, raw ore lumps, dust piles, nuggets.

Variants
--------
ingot   a trapezoid metal bar in three-quarter view, projected from a small 3D
        prism (top face lightest, front face darkest, a lit bevel along the top)
raw     a lumpy raw-ore chunk: a union of rounded knobs with dome lighting,
        crevices and embedded grains of a second mineral (accent)
dust    a heaped pile of powder with loose grains around its foot
nugget  two or three small knobs, like a crumb of the raw lump

Roles: ``base`` = the material, ``accent`` = grains / inclusions (mineral
slider), ``accent2`` = specular glints.  The silhouette of a reference
sprite steers the overall size (coverage), ``height`` the bar thickness,
``density`` the number of knobs / grains.  Every sprite gets the
Minecraft item look: a dark outline, light from the top-left.
"""
from __future__ import annotations

import math

import numpy as np

from core import pixel_art as pa
from core.layers import GenContext
from core.settings import TextureSettings

from . import _block_patterns as bp
from .base import BaseGenerator
from .plant import _silhouette

VARIANT_NAMES = ("ingot", "raw", "dust", "nugget")


class ItemGenerator(BaseGenerator):
    category = "item"

    def body_levels(self, s: TextureSettings, a) -> int:
        k = super().body_levels(s, a)
        if s.levels == 0:
            k = max(4, min(6, k))
        return k

    def palette_roles(self, s: TextureSettings) -> dict[str, str]:
        roles = {"accent2": "silver"}
        return roles

    def ramp_lengths(self, k: int) -> dict[str, int]:
        return {"base": k + 2, "secondary": max(3, k), "accent": 5, "accent2": 4, "glow": 4}

    def prepare(self, ctx: GenContext) -> None:
        v = ctx.settings.variant if ctx.settings.variant in VARIANT_NAMES else "ingot"
        ctx.data["variant"] = v
        ctx.data["L"] = bp.levels(ctx)
        sil, ws = _silhouette(ctx)
        # a reference sprite that fills more of its canvas gives a bigger item
        ctx.data["fill"] = float(np.clip(1.0 + (sil.coverage - 0.38) * 0.8 * ws, 0.8, 1.15)) if ws else 1.0
        getattr(self, f"_plan_{v}")(ctx)

    # ------------------------------------------------------------ plans
    def _plan_ingot(self, ctx: GenContext) -> None:
        s = ctx.settings
        N = ctx.w
        Lx, Wy, Hz = 10.0, 4.4, 1.9 + 1.4 * s.height
        i = 1.0
        bot = [(0, 0, 0), (Lx, 0, 0), (Lx, Wy, 0), (0, Wy, 0)]
        top = [(i, i * 0.7, Hz), (Lx - i, i * 0.7, Hz), (Lx - i, Wy - i * 0.7, Hz), (i, Wy - i * 0.7, Hz)]
        faces = {
            "top": top,
            "front": [bot[3], bot[2], top[2], top[3]],
            "end": [bot[0], bot[3], top[3], top[0]],
            "back": [bot[0], bot[1], top[1], top[0]],
            "far_end": [bot[1], bot[2], top[2], top[1]],
        }
        px_axis = np.array([0.84, 0.56, 0.0])
        py_axis = np.array([-0.5, 0.6, -1.0])
        view = np.cross(px_axis, py_axis)

        def proj(p):
            p = np.asarray(p, dtype=np.float64)
            return float(p @ px_axis), float(p @ py_axis)

        allp = np.array([proj(p) for p in bot + top])
        span = allp.max(0) - allp.min(0)
        sc = min((N - 2.2) / span[0], (N - 2.2) / span[1]) * ctx.data["fill"]
        sc = min(sc, (N - 2.0) / max(span))
        off = (np.array([N, N]) - span * sc) / 2 - allp.min(0) * sc
        order = sorted(faces, key=lambda k: float(np.mean(faces[k], axis=0) @ view))
        face_id = np.full((N, N), -1, dtype=np.int32)
        names = []
        for k in order:
            pts = [tuple(np.array(proj(p)) * sc + off) for p in faces[k]]
            m = pa.fill_polygon(pts, N, N)
            face_id[m] = len(names)
            names.append(k)
        mask = face_id >= 0
        mask = pa.alpha_cleanup(mask, 2, True)
        ctx.data.update(mask=mask, face_id=face_id, face_names=names)

    def _blobs(self, ctx: GenContext, n: int, r_range: tuple[float, float], spread: float,
               centre: tuple[float, float]) -> np.ndarray:
        rng = ctx.rng("knobs")
        N = ctx.w
        yy, xx = np.mgrid[0:N, 0:N].astype(np.float64)
        mask = np.zeros((N, N), dtype=bool)
        pts = [centre]
        for k in range(n):
            if k == 0:
                cx, cy = centre
            else:
                bx, by = pts[int(rng.integers(len(pts)))]
                ang = rng.uniform(0, 2 * math.pi)
                d = rng.uniform(0.6, 1.0) * spread
                cx, cy = bx + math.cos(ang) * d, by + math.sin(ang) * d * 0.8
            r = rng.uniform(*r_range)
            cx = float(np.clip(cx, r + 1, N - r - 1))
            cy = float(np.clip(cy, r + 1, N - r - 1))
            pts.append((cx, cy))
            mask |= np.hypot((xx + 0.5 - cx) / 1.05, yy + 0.5 - cy) <= r
        return pa.alpha_cleanup(mask, 3, True)

    def _plan_raw(self, ctx: GenContext) -> None:
        s = ctx.settings
        sc = ctx.scale * ctx.data["fill"]
        n = 3 + int(round(2 * s.density))
        mask = self._blobs(ctx, n, (2.9 * sc, 4.0 * sc), 3.4 * sc, (ctx.w / 2.0, ctx.h / 2.0 + 0.5))
        ctx.data["mask"] = mask

    def _plan_nugget(self, ctx: GenContext) -> None:
        s = ctx.settings
        sc = ctx.scale * ctx.data["fill"]
        n = 2 + int(round(s.density))
        mask = self._blobs(ctx, n, (1.8 * sc, 2.5 * sc), 2.6 * sc, (ctx.w / 2.0, ctx.h * 0.56))
        ctx.data["mask"] = mask

    def _plan_dust(self, ctx: GenContext) -> None:
        """A heap of grains: a mound whose rim crumbles into loose grains."""
        s = ctx.settings
        N = ctx.w
        sc = ctx.scale * ctx.data["fill"]
        rng = ctx.rng("heap")
        yy, xx = np.mgrid[0:N, 0:N].astype(np.float64)
        cx, base_y = N / 2.0 + rng.uniform(-0.6, 0.6), N * 0.88
        rx, ry = 6.3 * sc, (4.6 + 3.0 * s.height) * sc
        # a pointed mound: the profile narrows faster toward the top than an ellipse
        u = np.clip((base_y - (yy + 0.5)) / ry, 0, None)
        half = rx * np.clip(1 - u, 0, 1) ** 0.8
        heap = (np.abs(xx + 0.5 - cx) <= half) & (yy + 0.5 <= base_y + 0.5) & (u <= 1)
        heap = pa.alpha_cleanup(heap, 3, True)
        # the rim crumbles: knock out some edge pixels, scatter grains beside the heap
        edge = heap & ~pa.erode(heap, False)
        f = self.height_field(ctx, "crumble", 2.2, 1)
        heap &= ~(edge & (f > 0.8 - 0.15 * s.roughness))
        loose = np.zeros_like(heap)
        near = pa.dilate(heap, False, True)
        for _ in range(4 + int(round(5 * s.density))):
            for _t in range(30):
                x = int(rng.integers(1, N - 1))
                y = int(rng.integers(int(base_y - ry * 0.7), min(N - 1, int(base_y + 1))))
                if not near[y, x] and not loose[y, x] and abs(x + 0.5 - cx) < rx + 3 * sc:
                    loose[y, x] = True
                    if rng.random() < 0.3 and x + 1 < N - 1:
                        loose[y, x + 1] = True
                    break
        ctx.data.update(mask=heap | loose, heap=heap, loose=loose)

    # ------------------------------------------------------------ layers
    def layer_base(self, ctx: GenContext) -> None:
        c = ctx.canvas
        c.clear_all()
        c.set(ctx.data["mask"], "base", ctx.data["L"].mid)

    def layer_material(self, ctx: GenContext) -> None:
        v, L, c = ctx.data["variant"], ctx.data["L"], ctx.canvas
        mask = ctx.data["mask"]
        if v == "ingot":
            names, fid = ctx.data["face_names"], ctx.data["face_id"]
            tone = {"top": L.up, "end": L.mid, "front": L.lo, "back": L.lo, "far_end": L.lo}
            lv = np.zeros_like(fid)
            for i, k in enumerate(names):
                lv[fid == i] = tone[k]
            c.set(mask, "base", lv)
            return
        # rounded bodies: a dome from the distance to the edge, broken up by noise
        depth = bp.mask_depth(mask, False, 6).astype(np.float64)
        f = self.height_field(ctx, "body", 1.4, 2)
        dome = np.clip(depth / max(1.0, depth.max()), 0, 1)
        field = 0.65 * dome + 0.35 * f
        lv = self.quantize_body(ctx, field, mask=mask)
        lv = np.clip(lv, L.dark + 1, L.lit - 1)
        lv = pa.remove_small_clusters(lv, 2, False, mask)
        c.set(mask, "base", lv)

    def layer_large_detail(self, ctx: GenContext) -> None:
        """Light from the top-left across the whole body."""
        v, L, c = ctx.data["variant"], ctx.data["L"], ctx.canvas
        if v == "ingot":
            return
        mask = ctx.data.get("heap", ctx.data["mask"])
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        ys, xs = np.nonzero(mask)
        if not len(xs):
            return
        cx, cy = xs.mean(), ys.mean()
        side = (xx - cx) + (yy - cy)
        spread = max(2.0, float(np.std(xs) + np.std(ys)))
        c.shift(mask & (side < -0.8 * spread), 1, ceil=L.lit - 1)
        c.shift(mask & (side > 0.9 * spread), -1, floor=L.dark + 1)

    def layer_medium_detail(self, ctx: GenContext) -> None:
        """Ingot: bevel along the top face's front edge. Lumps: crevices between knobs."""
        v, L, c, s = ctx.data["variant"], ctx.data["L"], ctx.canvas, ctx.settings
        if v == "ingot":
            names, fid = ctx.data["face_names"], ctx.data["face_id"]
            topm = fid == names.index("top")
            below = pa.neighbour(topm, 0, 1, False, fill=False) & ~topm
            left = pa.neighbour(topm, -1, 0, False, fill=False) & ~topm
            rim = topm & ~pa.neighbour(topm, 0, 1, False, fill=True)
            c.set(rim, "base", L.lit)
            bp.protect(ctx, rim)
            ctx.data["bevel"] = rim
            del below, left
            return
        if v in ("raw", "nugget"):
            mask = ctx.data["mask"]
            inner = pa.erode(mask, False)
            cr = self.crack_paths(ctx, 0.25 + 0.5 * s.roughness, "crevices", mask=inner, length=(0.2, 0.4))
            cr &= inner
            c.set(cr, "base", L.dark)
            c.tag("crack")[cr] = True
            bp.protect(ctx, cr)

    def layer_small_detail(self, ctx: GenContext) -> None:
        v, L, c, s = ctx.data["variant"], ctx.data["L"], ctx.canvas, ctx.settings
        mask = ctx.data["mask"]
        if v == "dust":
            # grain texture: scattered lighter / darker single grains (kept on purpose)
            rng = ctx.rng("grains")
            inner = pa.erode(ctx.data["heap"], False)
            ys, xs = np.nonzero(inner)
            k = int(len(xs) * (0.12 + 0.12 * s.noise))
            if k:
                idx = rng.choice(len(xs), size=min(k, len(xs)), replace=False)
                sel = np.zeros_like(inner)
                sel[ys[idx], xs[idx]] = True
                up = sel & (rng.random(sel.shape) < 0.5)
                c.shift(up, 1, ceil=L.lit)
                c.shift(sel & ~up, -1, floor=L.dark)
                bp.protect(ctx, sel)
            loose = ctx.data["loose"]
            c.set(loose, "base", L.up)
            bp.protect(ctx, loose)
        elif v == "ingot":
            # faint cast marks on the top face
            names, fid = ctx.data["face_names"], ctx.data["face_id"]
            topm = pa.erode(fid == names.index("top"), False) & ~ctx.data.get("bevel", np.zeros_like(mask))
            ys, xs = np.nonzero(topm)
            if len(xs) >= 6 and s.noise > 0.15:
                rng = ctx.rng("cast")
                i = int(rng.integers(len(xs)))
                m = bp.point_mask(ctx, [(xs[i], ys[i]), (xs[i] + 1, ys[i])]) & topm
                c.shift(m, -1)
                bp.protect(ctx, m)

    def layer_highlights(self, ctx: GenContext) -> None:
        v, L, c = ctx.data["variant"], ctx.data["L"], ctx.canvas
        mask = ctx.data["mask"]
        lit = pa.edge_of(mask, 0, -1) | pa.edge_of(mask, -1, 0)
        if v == "ingot":
            names, fid = ctx.data["face_names"], ctx.data["face_id"]
            lit &= fid == names.index("top")
        c.shift(lit & ~pa.edge_of(mask, 0, 1) & ~pa.edge_of(mask, 1, 0), 1, ceil=L.lit)
        # glints: brightest pixels near the top-left of the body
        ys, xs = np.nonzero(pa.erode(mask, False) if v != "dust" else ctx.data["heap"])
        if len(xs):
            order = np.argsort(xs + ys)
            n = c.length_of("accent2")
            k = 1 if v in ("nugget", "dust") else 2
            pts = [(int(xs[order[j]]), int(ys[order[j]])) for j in range(min(k, len(order)))]
            g = bp.point_mask(ctx, pts)
            if v == "ingot":
                c.set(g, "base", L.hi)
            else:
                c.set(g, "accent2", n - 1)
            bp.protect(ctx, g)

    def layer_shadows(self, ctx: GenContext) -> None:
        """The item outline: darkest on the bottom-right, a step lighter on the top-left."""
        L, c = ctx.data["L"], ctx.canvas
        mask = ctx.data["mask"]
        far = pa.edge_of(mask, 0, 1) | pa.edge_of(mask, 1, 0)
        near = (pa.edge_of(mask, 0, -1) | pa.edge_of(mask, -1, 0)) & ~far
        loose = ctx.data.get("loose")
        if loose is not None:
            far &= ~loose
            near &= ~loose
        if ctx.data["variant"] == "dust":
            # powder has no hard edge: only a soft darker rim
            c.shift(far, -1, floor=L.dark)
            bp.protect(ctx, far)
            return
        c.set(far, "base", L.deep)
        c.set(near & (c.level > L.dark + 1), "base", L.dark)
        bp.protect(ctx, far)

    def layer_accent(self, ctx: GenContext) -> None:
        v, c, s = ctx.data["variant"], ctx.canvas, ctx.settings
        if s.mineral <= 0.02 or v == "ingot":
            return
        mask = ctx.data.get("heap", ctx.data["mask"])
        inner = pa.erode(mask, False) & ~c.tag("crack")
        self.mineral_grains(ctx, s.mineral * (0.6 if v == "raw" else 0.35), "accent", "inclusions",
                            mask=inner, size_range=(1, 2))
