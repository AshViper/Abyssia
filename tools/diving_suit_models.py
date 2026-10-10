"""3D diving-suit armor models (helmet / chest tank / flippers) for the three gear tiers.

Per tier one parts table (cubes on the vanilla humanoid parts; left arm/leg mirror the right automatically; zero-thickness
cubes are flat planes whose silhouette comes from alpha).  Box-UV, shelf-packed into 64x64 (grows to 128x64 / 128x128).

    python tools/diving_suit_models.py              # write Java mesh + textures + bbmodels
    python tools/diving_suit_models.py --dry-run    # report only
    python tools/diving_suit_models.py --json       # machine-readable summary
    python tools/diving_suit_models.py --tier deep

Outputs: src/main/java/com/abyssia/client/armor/DivingSuitMesh.java (GENERATED), the tier texture as
textures/models/armor/<material>_layer_1.png (entry_diving, abyssal_alloy + diving_alloy, pressure_alloy), also copied to
tools/texture_locks, and tools/diving_suit_models/<tier>.bbmodel for inspection in Blockbench.
Palette: tools/diving_suit_palette.json (hex or {"base","highlight","shadow"} per material).  Leggings (layer_2) stay with
tools/armor_layers.py.

Cube tuple: (part, from, to, tag, opts).  Model units, part-local; model front = -Z, wearer's right = -X.  opts:
  inflate            CubeDeformation on every axis
  mirror_x           adds an x-mirrored copy sharing the UV (left side of a symmetric part)
  rot, pivot         degrees + part-local pivot: the cube becomes a rotated child part (PartPose.offsetAndRotation)
  bands              [(row0, row1, tag)] horizontal strips across the four side faces (texels from the face top)
  lines              [row, ...] dark 1px panel lines across the side faces
  bolts              True = dots along the mid row of the sides, "up" = ring of dots on the top face,
                     or [(face, x, y)] explicit 1px dots
  details            [(face, (x, y, w, h), tag[, style])] with style flat | glass | port | visor | lens | hazard
  plane="fin", rails=tag|True, tip=(rows, tag)   silhouette + decoration of a zero-thickness fin plane
Shading is deliberate and flat: top faces light, undersides dark, side faces get a lighter top row and darker bottom row.
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import math
import os
import shutil
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(ROOT, ".."))
JAVA = os.path.join(REPO, "src", "main", "java", "com", "abyssia", "client", "armor", "DivingSuitMesh.java")
TEX = os.path.join(REPO, "src", "main", "resources", "assets", "abyssia", "textures", "models", "armor")
LOCK = os.path.join(ROOT, "texture_locks", "assets", "textures", "models", "armor")
PALETTE = os.path.join(ROOT, "diving_suit_palette.json")
BB_DIR = os.path.join(ROOT, "diving_suit_models")

PARTS = ("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")
POSES = {"head": (0, 0, 0), "hat": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
         "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}
MIRROR_OF = {"right_arm": "left_arm", "right_leg": "left_leg"}
SIDES = ("north", "south", "east", "west")

# tier -> materials whose layer_1 png receives the tier texture
OUT_MATERIALS = {"entry": ["entry_diving"], "deep": ["abyssal_alloy", "diving_alloy"], "pressure": ["pressure_alloy"]}


def c(part, a, b, tag, inflate=0.0, **opts):
    return (part, tuple(a), tuple(b), tag, dict(opts, inflate=inflate))


# head box is x-4..4 y-8..0 z-4..4; the skin hat layer inflates to 4.5 -> helmet faces >= 5 from the head centre (or
# hidden inside another helmet cube).  Body x-4..4 y0..12 z-2..2.  Arm local x-3..1 y-2..10.  Leg local y=12 = ground.
TIERS = {
    "entry": [
        # --- half-face rubber mask, glass lens, side skirts, nose pocket
        c("head", (-3.5, -6.5, -5.7), (3.5, -2, -4.7), "rubber", lines=[4],
          details=[("north", (1, 1, 5, 3), "glass", "glass")]),
        c("head", (4.6, -6.5, -5.2), (5.3, -2.5, -3.2), "rubber", mirror_x=True),
        c("head", (-1.5, -3, -6.5), (1.5, -1.5, -5.7), "rubber"),
        # --- black head strap (sides + back) with adjuster buckle at the back
        c("head", (4.6, -6.5, -4.3), (5.0, -5, 4.8), "strap", mirror_x=True),
        c("head", (-5.0, -6.5, 4.6), (5.0, -5, 5.0), "strap"),
        c("head", (-1, -7, 5.0), (1, -4.5, 5.4), "bolt"),
        # --- J-snorkel on the wearer's right (-X): tube, tilted top with splash guard, bend to the mouthpiece
        c("head", (-5.9, -10.5, -1.2), (-5.0, -3, -0.3), "shell"),
        c("head", (-5.9, -12.5, -1.2), (-5.0, -10.5, -0.3), "shell", rot=(0, 0, -6), pivot=(-5.45, -10.5, -0.75)),
        c("head", (-6.2, -13.3, -1.5), (-4.7, -12.5, 0), "accent", rot=(0, 0, -6), pivot=(-5.45, -10.5, -0.75)),
        c("head", (-5.9, -3, -5.7), (-5.0, -2, -0.3), "shell"),
        c("head", (-5.0, -2.5, -5.7), (-2.5, -1, -4.7), "strap"),
        # --- harness vest: shoulder straps, chest strap with buckle, waist belt
        c("body", (-4.6, 0, -2.6), (4.6, 12, 2.6), "vest", bands=[(10, 12, "strap")],
          details=[("north", (1, 0, 2, 7), "strap"), ("north", (6, 0, 2, 7), "strap"), ("north", (1, 5, 7, 2), "strap"),
                   ("north", (4, 5, 1, 2), "brass"), ("north", (3, 10, 3, 2), "brass"),
                   ("south", (1, 0, 2, 10), "strap"), ("south", (6, 0, 2, 10), "strap"),
                   ("up_b", (1, 0, 2, 5), "strap"), ("up_b", (6, 0, 2, 5), "strap")]),
        # --- single grey tank (two crossed boxes = rounded), shoulder, brass valve + knob, tank bands
        c("body", (-2, 0.5, 3.1), (2, 10.5, 6.1), "tank", bands=[(2, 3, "strap"), (7, 8, "strap")]),
        c("body", (-1.5, 0.5, 2.6), (1.5, 10.5, 6.6), "tank", bands=[(2, 3, "strap"), (7, 8, "strap")]),
        c("body", (-1.5, -0.5, 3.1), (1.5, 0.5, 6.1), "tank"),
        c("body", (-0.5, -2, 4.1), (0.5, -0.5, 5.1), "brass"),
        c("body", (0.5, -1.5, 4.1), (2, -0.5, 5.1), "brass"),
        # --- regulator hose: valve -> over the left shoulder -> down the chest -> second stage; gauge on the right
        c("body", (1.5, -1.3, 4.1), (4.4, -0.3, 5.1), "hose"),
        c("body", (3.6, -1.3, -3.6), (4.6, -0.3, 5.1), "hose"),
        c("body", (3.6, -0.3, -3.6), (4.6, 4, -2.6), "hose"),
        c("body", (1.6, 3.5, -3.8), (4.6, 5.5, -2.6), "bolt", details=[("north", (1, 0, 1, 2), "tank")]),
        c("body", (-4.4, 3, -3.6), (-2.4, 5.5, -2.6), "bolt", details=[("north", (0, 0, 2, 2), "lamp", "lens")]),
        # --- short black flippers: foot pocket with yellow heel strap, blade with yellow rails
        c("right_leg", (-2.6, 9.5, -2.6), (2.6, 12, 2.6), "fin", bands=[(0, 1, "shell")]),
        c("right_leg", (-3.5, 11.8, -10), (3.5, 11.8, -2.6), "fin", plane="fin", rails="shell",
          rot=(-6, 0, 0), pivot=(0, 11.8, -2.6)),
    ],
    "deep": [
        # --- brass Mark-V helmet: stepped sphere (core, side/front bulges, equator ridges, top cap)
        c("head", (-5, -10.5, -5), (5, 1, 5), "brass"),
        c("head", (-6, -9, -4), (6, 0, 4), "brass"),
        c("head", (-4, -9, -6), (4, 0, 6), "brass"),
        c("head", (-6.5, -7.5, -3), (6.5, -1.5, 3), "brass"),
        c("head", (-3, -7.5, -6.5), (3, -1.5, 6.5), "brass"),
        c("head", (-3.5, -11.5, -3.5), (3.5, -10.5, 3.5), "brass"),
        # --- front porthole with grille, side portholes
        c("head", (-3.5, -8, -7), (3.5, -1, -5.5), "brass", details=[("north", (1, 1, 5, 5), "glass", "port")],
          bolts=[("north", 0, 0), ("north", 6, 0), ("north", 0, 6), ("north", 6, 6)]),
        c("head", (6, -7.5, -2.5), (7, -2.5, 2.5), "brass", mirror_x=True,
          details=[("east", (1, 1, 3, 3), "glass", "port"), ("west", (1, 1, 3, 3), "glass", "port")]),
        # --- collar ring + bolted breast flange, air inlet at the back, exhaust valve on the right
        c("head", (-5.5, 0.5, -5.5), (5.5, 1.8, 5.5), "brass"),
        c("head", (-6.5, 1.8, -6), (6.5, 2.8, 6), "brass", bolts="up"),
        c("head", (-6, 2.8, -5.5), (6, 3.6, 5.5), "brass"),
        c("head", (-1, -5, 6), (1, -2.5, 7.2), "brass"),
        c("head", (-7, -4, 3.5), (-5.5, -2, 5), "brass"),
        # --- canvas suit with brass breastplate and weight belt
        c("body", (-4.6, 0, -2.6), (4.6, 12, 2.6), "shell", lines=[6], bands=[(9, 11, "strap")],
          details=[("north", (3, 9, 3, 2), "brass")]),
        c("body", (-4.8, -0.5, -2.9), (4.8, 4.5, 2.9), "brass", bolts=True),
        # --- twin dark-steel tanks (crossed boxes), brass bands, manifold + valve
        c("body", (0.5, 0.5, 2.9), (3.5, 11, 5.9), "tank", mirror_x=True, bands=[(2, 3, "brass"), (7, 8, "brass")]),
        c("body", (1, 0.5, 2.6), (3, 11, 6.2), "tank", mirror_x=True, bands=[(2, 3, "brass"), (7, 8, "brass")]),
        c("body", (-3.5, -0.5, 3.9), (3.5, 0.5, 4.9), "brass"),
        c("body", (-0.5, -1.8, 3.9), (0.5, -0.5, 4.9), "brass"),
        # --- suit sleeves with brass wrist cuff and shoulder seam
        c("right_arm", (-3.5, -2.5, -2.5), (1.5, 10, 2.5), "shell", bands=[(11, 13, "brass")], lines=[5]),
        c("right_arm", (-3.9, -2.9, -2.9), (1.9, 0.5, 2.9), "shell"),
        # --- weighted boots with brass toe, long dark-teal fins
        c("right_leg", (-2.7, 8.5, -2.7), (2.7, 12, 2.7), "rubber", bands=[(0, 1, "strap")],
          details=[("north", (0, 2, 5, 2), "brass")]),
        c("right_leg", (-4, 11.8, -13), (4, 11.8, -2.7), "fin", plane="fin", rails=True,
          rot=(-5, 0, 0), pivot=(0, 11.8, -2.7)),
    ],
    "pressure": [
        # --- gunmetal hard-suit dome: stepped sphere
        c("head", (-5, -10.5, -5), (5, 1.5, 5), "shell"),
        c("head", (-6, -9, -4), (6, 0.5, 4), "shell"),
        c("head", (-4.5, -9.5, -6.2), (4.5, 0, 6), "shell"),
        c("head", (-6.5, -7.5, -3), (6.5, -1.5, 3), "shell"),
        c("head", (-3.5, -11.5, -3.5), (3.5, -10.5, 3.5), "shell"),
        # --- wide amber visor with bolted frame, head lamp, side lamps, ear pods, bolted neck ring
        c("head", (-4.5, -8.5, -7), (4.5, -2.5, -6), "shell", details=[("north", (1, 1, 7, 4), "glass", "visor")],
          bolts=[("north", 0, 0), ("north", 8, 0), ("north", 0, 5), ("north", 8, 5)]),
        c("head", (-1.5, -12.5, -4), (1.5, -10.5, -1), "shell", details=[("north", (0, 0, 3, 2), "lamp", "lens")]),
        c("head", (4.8, -7.5, -5.8), (6.8, -4.5, -4.3), "joint", mirror_x=True,
          details=[("north", (0, 1, 2, 2), "lamp", "lens")]),
        c("head", (6, -6.5, -2.5), (7.5, -2, 2.5), "joint", mirror_x=True,
          bolts=[("east", 2, 2), ("west", 2, 2)]),
        c("head", (-6, 0.5, -6), (6, 2.5, 6), "joint", bolts=True),
        # --- segmented armoured torso, bolted chest plate with hatch, belt ring
        c("body", (-5, -0.5, -3.5), (5, 12.5, 3.5), "shell", bands=[(4, 5, "joint"), (8, 9, "joint")]),
        c("body", (-4, 0, -4.5), (4, 5, -3.5), "shell", details=[("north", (3, 1, 2, 3), "joint")],
          bolts=[("north", 0, 0), ("north", 7, 0), ("north", 0, 4), ("north", 7, 4)]),
        c("body", (-5.5, 9, -4), (5.5, 11, 4), "joint", bolts=True),
        # --- armoured backpack with hazard tape, twin side bottles, top vent
        c("body", (-4.5, 0, 3.5), (4.5, 11, 7.5), "tank", lines=[5],
          details=[("south", (1, 1, 2, 9), "accent", "hazard"), ("south", (6, 1, 2, 9), "accent", "hazard")]),
        c("body", (2, 1, 7.5), (4, 10, 9.5), "tank", mirror_x=True, bands=[(1, 2, "accent"), (7, 8, "accent")]),
        c("body", (-2, -1, 4.5), (2, 0, 6.5), "joint"),
        # --- big rounded shoulder joint (3 crossed boxes), segmented upper arm, elbow ring, forearm
        c("right_arm", (-4, -3, -3), (2, 3, 3), "shell"),
        c("right_arm", (-3, -3.8, -2), (1, 3.8, 2), "shell"),
        c("right_arm", (-4.6, -2, -2), (2.6, 2, 2), "shell"),
        c("right_arm", (-3.5, 3, -2.5), (1.5, 6, 2.5), "joint"),
        c("right_arm", (-3.8, 5.5, -2.8), (1.8, 7, 2.8), "shell"),
        c("right_arm", (-3.6, 7, -2.6), (1.6, 10.5, 2.6), "shell", bands=[(3, 4, "joint")]),
        # --- heavy boots with dark toe cap, short orange-tipped fins
        c("right_leg", (-3, 7, -3), (3, 12, 3), "shell", bands=[(2, 3, "joint")]),
        c("right_leg", (-2.8, 10, -4.5), (2.8, 12, -3), "joint"),
        c("right_leg", (-3.5, 11.8, -9.5), (3.5, 11.8, -4.5), "fin", plane="fin", tip=(2, "accent"),
          rot=(-6, 0, 0), pivot=(0, 11.8, -4.5)),
    ],
}


# --------------------------------------------------------------------------- geometry
def r_half_up(v):
    return int(math.floor(v + 0.5 + 1e-9))


def build_cubes(spec):
    """Expand the parts table: integer UV dims + fractional grow, left parts / mirror_x copies sharing the UV box."""
    cubes = []
    for (part, a, b, tag, opts) in spec:
        d = [round(b[i] - a[i], 5) for i in range(3)]
        D = [0 if x <= 0 else max(1, r_half_up(x)) for x in d]
        grow = [(d[i] - D[i]) / 2 + opts["inflate"] for i in range(3)]
        cen = [(a[i] + b[i]) / 2 for i in range(3)]
        rot = tuple(opts.get("rot", (0, 0, 0)))
        pivot = tuple(opts.get("pivot", cen))
        base = {"tag": tag, "D": D, "grow": grow, "opts": opts, "id": len(cubes), "plane": 0 in D}
        cubes.append(dict(base, part=part, origin=[cen[i] - D[i] / 2 for i in range(3)], mirror=False, owner=True,
                          rot=rot, pivot=pivot))
        copies = []
        if opts.get("mirror_x"):
            copies.append((part, True))
        if part in MIRROR_OF:
            copies.append((MIRROR_OF[part], True))
        for (p2, m) in copies:
            o = [cen[0] * -1 - D[0] / 2, cen[1] - D[1] / 2, cen[2] - D[2] / 2]
            cubes.append(dict(base, part=p2, origin=o, mirror=m, owner=False,
                              rot=(rot[0], -rot[1], -rot[2]), pivot=(-pivot[0], pivot[1], pivot[2])))
    return cubes


def footprint(D):
    return 2 * (D[2] + D[0]), D[2] + D[1]


def pack(cubes):
    owners = [q for q in cubes if q["owner"]]
    order = sorted(owners, key=lambda q: (-footprint(q["D"])[1], -footprint(q["D"])[0], q["id"]))
    for (W, H) in ((64, 64), (128, 64), (128, 128), (256, 128), (256, 256)):
        x = y = rowh = 0
        pos, ok = {}, True
        for q in order:
            w, h = footprint(q["D"])
            if w > W:
                ok = False
                break
            if x + w > W:
                x, y, rowh = 0, y + rowh, 0
            if y + h > H:
                ok = False
                break
            pos[q["id"]] = (x, y)
            x += w
            rowh = max(rowh, h)
        if ok:
            for q in cubes:
                q["uv"] = pos[q["id"]]
            return W, H
    raise SystemExit("cannot pack diving suit UVs")


# --------------------------------------------------------------------------- painting
def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def scale(col, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in col)


def lighten(col, k):
    return tuple(max(0, min(255, int(round(v + (255 - v) * k)))) for v in col)


class Ramp:
    """Flat shade ramp of one material: base, highlight (top faces / top rows), shadow (bottom rows / undersides),
    mid (side columns) and line (panel lines, rivets)."""

    def __init__(self, spec):
        if isinstance(spec, str):
            spec = {"base": spec}
        self.base = hexc(spec["base"])
        self.hi = hexc(spec["highlight"]) if "highlight" in spec else lighten(self.base, 0.3)
        self.lo = hexc(spec["shadow"]) if "shadow" in spec else scale(self.base, 0.68)
        self.mid = tuple((a + b) // 2 for a, b in zip(self.base, self.lo))
        self.line = scale(self.lo, 0.66)


def faces_of(q):
    """(name, x, y, w, h) of every face with area on the sheet; box-UV layout of ModelPart.Cube."""
    u, v = q["uv"]
    dx, dy, dz = q["D"]
    out = [("down_t", u + dz, v, dx, dz), ("up_b", u + dz + dx, v, dx, dz), ("west", u, v + dz, dz, dy),
           ("north", u + dz, v + dz, dx, dy), ("east", u + dz + dx, v + dz, dz, dy),
           ("south", u + 2 * dz + dx, v + dz, dx, dy)]
    return [f for f in out if f[3] > 0 and f[4] > 0]


def put(px, x, y, col):
    px[x, y] = (*col, 255)


def fill(px, x0, y0, w, h, col):
    for y in range(h):
        for x in range(w):
            put(px, x0 + x, y0 + y, col)


def paint_face(px, x0, y0, w, h, R, face):
    """Top-lit flat shading of one box face."""
    if face == "up_b":
        fill(px, x0, y0, w, h, R.hi)
        if w >= 3 and h >= 3:
            for x in range(w):
                put(px, x0 + x, y0, R.base)
                put(px, x0 + x, y0 + h - 1, R.base)
            for y in range(h):
                put(px, x0, y0 + y, R.base)
                put(px, x0 + w - 1, y0 + y, R.base)
        return
    if face == "down_t":
        fill(px, x0, y0, w, h, R.lo)
        return
    fill(px, x0, y0, w, h, R.base)
    if h >= 3:
        for x in range(w):
            put(px, x0 + x, y0, R.hi)
    if h >= 2:
        for x in range(w):
            put(px, x0 + x, y0 + h - 1, R.lo)
    if w >= 3 and h >= 3:
        for y in range(1, h - 1):
            put(px, x0, y0 + y, R.mid)
            put(px, x0 + w - 1, y0 + y, R.mid)


def paint_band(px, x0, y0, w, h, r0, r1, R):
    r1 = min(r1, h)
    for y in range(r0, r1):
        for x in range(w):
            put(px, x0 + x, y0 + y, R.lo if (y == r1 - 1 and r1 - r0 >= 2) else R.base)


def paint_detail(px, x0, y0, w, h, rect, R, frame, style):
    rx, ry, rw, rh = rect
    rw, rh = min(rw, w - rx), min(rh, h - ry)
    if rw <= 0 or rh <= 0:
        return
    bx, by = x0 + rx, y0 + ry
    if style == "hazard":
        for y in range(rh):
            for x in range(rw):
                put(px, bx + x, by + y, R.base if ((x + y) // 2) % 2 == 0 else frame.line)
        return
    fill(px, bx, by, rw, rh, R.base)
    if style == "flat":
        return
    # glass / port / visor / lens: rounded corners in the frame colour, dark bottom row, specular highlight
    if style in ("glass", "port", "visor") and rw >= 4 and rh >= 3:
        for (cx, cy) in ((0, 0), (rw - 1, 0), (0, rh - 1), (rw - 1, rh - 1)):
            put(px, bx + cx, by + cy, frame.base)
    if rh >= 2:
        for x in range(1 if rw >= 4 else 0, rw - (1 if rw >= 4 else 0)):
            put(px, bx + x, by + rh - 1, R.lo)
    hi = ((1, 1), (2, 1), (1, 2)) if (rw >= 5 and rh >= 4) else (((1, 0), (2, 0)) if rw >= 4 else ((0, 0),))
    for (x, y) in hi:
        if x < rw and y < rh:
            put(px, bx + x, by + y, R.hi)
    if style == "port":
        for x in range(rw):
            put(px, bx + x, by + rh // 2, frame.line)
        for y in range(rh):
            put(px, bx + rw // 2, by + y, frame.line)


def paint_plane(px, x0, y0, w, h, R, opts, ramps):
    """Fin silhouette on a horizontal plane region: root = top row, widest at a third, rounded tapered tip."""
    cx = (w - 1) / 2
    cover = {}
    for y in range(h):
        t = y / max(1, h - 1)
        if t < 0.35:
            s = 0.75 + 0.25 * (t / 0.35)
        elif t < 0.85:
            s = 1.0 - 0.3 * ((t - 0.35) / 0.5)
        else:
            s = 0.7 * math.sqrt(max(0.0, 1.0 - ((t - 0.85) / 0.15) ** 2 * 0.6))
        half = (w / 2) * s
        for x in range(w):
            if abs(x - cx) < max(half, 0.6):
                cover[(x, y)] = True
    rails = opts.get("rails")
    rail_col = R.hi if rails is True else (ramps[rails].base if rails else None)
    rail_x = {int(round(cx - w / 4)), int(round(cx + w / 4))}
    tip_rows, tip_R = (opts["tip"][0], ramps[opts["tip"][1]]) if opts.get("tip") else (0, None)
    for (x, y) in cover:
        edge = (x - 1, y) not in cover or (x + 1, y) not in cover or (x, y + 1) not in cover
        src = tip_R if y >= h - tip_rows else R
        col = src.lo if edge else src.base
        if not edge and src is R:
            if rail_col and x in rail_x and 1 <= y < h * 0.8:
                col = rail_col
            elif x == int(cx) and y < h * 0.85:
                col = R.mid
        put(px, x0 + x, y0 + y, col)


def paint(cubes, W, H, pal):
    ramps = {k: Ramp(v) for k, v in pal.items()}
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for q in cubes:
        if not q["owner"]:
            continue
        R, o = ramps[q["tag"]], q["opts"]
        faces = {n: (x, y, w, h) for (n, x, y, w, h) in faces_of(q)}
        for (name, (x, y, w, h)) in faces.items():
            if q["plane"] and name in ("down_t", "up_b") and q["D"][1] == 0:
                paint_plane(px, x, y, w, h, R, o, ramps)
                continue
            paint_face(px, x, y, w, h, R, name)
            if name in SIDES:
                for (r0, r1, tag) in o.get("bands", []):
                    paint_band(px, x, y, w, h, r0, r1, ramps[tag])
                for row in o.get("lines", []):
                    if row < h:
                        for xx in range(w):
                            put(px, x + xx, y + row, R.line)
        for det in o.get("details", []):
            face, rect, tag = det[:3]
            style = det[3] if len(det) > 3 else "flat"
            if face in faces:
                x, y, w, h = faces[face]
                paint_detail(px, x, y, w, h, rect, ramps[tag], R, style)
        bolts = o.get("bolts")
        if bolts is True:
            for name in SIDES:
                if name in faces:
                    x, y, w, h = faces[name]
                    if h >= 2 and w >= 3:
                        for xx in range(1, w - 1, 3):
                            put(px, x + xx, y + h // 2, R.line)
        elif bolts == "up" and "up_b" in faces:
            x, y, w, h = faces["up_b"]
            if w >= 4 and h >= 4:
                for xx in range(1, w - 1, 2):
                    put(px, x + xx, y + 1, R.line)
                    put(px, x + xx, y + h - 2, R.line)
                for yy in range(1, h - 1, 2):
                    put(px, x + 1, y + yy, R.line)
                    put(px, x + w - 2, y + yy, R.line)
        elif isinstance(bolts, list):
            for (face, bx, by) in bolts:
                if face in faces:
                    x, y, w, h = faces[face]
                    if bx < w and by < h:
                        put(px, x + bx, y + by, R.line)
    return img


# --------------------------------------------------------------------------- java
def f(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def java_boxes(cubes, origin_of):
    lines, mir = [], False
    for q in cubes:
        if q["mirror"] != mir:
            mir = q["mirror"]
            lines.append("                .mirror(%s)" % ("true" if mir else "false"))
        o, D, g = origin_of(q), q["D"], q["grow"]
        lines.append("                .texOffs(%d, %d).addBox(\"\", %s, %s, %s, %s, %s, %s, new CubeDeformation(%s, %s, %s))"
                     % (q["uv"][0], q["uv"][1], f(o[0]), f(o[1]), f(o[2]), f(D[0]), f(D[1]), f(D[2]),
                        f(g[0]), f(g[1]), f(g[2])))
    return lines


def java_part(name, cubes):
    mine = [q for q in cubes if q["part"] == name]
    static = [q for q in mine if q["rot"] == (0, 0, 0)]
    groups = {}
    for q in mine:
        if q["rot"] != (0, 0, 0):
            groups.setdefault((q["rot"], q["pivot"]), []).append(q)
    p = POSES[name]
    lines = ["        PartDefinition %s = root.addOrReplaceChild(\"%s\", CubeListBuilder.create()" % (name, name)]
    lines += java_boxes(static, lambda q: q["origin"])
    lines.append("                , PartPose.offset(%s, %s, %s));" % (f(p[0]), f(p[1]), f(p[2])))
    for i, ((rot, pivot), qs) in enumerate(groups.items()):
        lines.append("        %s.addOrReplaceChild(\"%s_r%d\", CubeListBuilder.create()" % (name, name, i))
        lines += java_boxes(qs, lambda q: [q["origin"][k] - q["pivot"][k] for k in range(3)])
        lines.append("                , PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
                     % (f(pivot[0]), f(pivot[1]), f(pivot[2]),
                        f(math.radians(rot[0])), f(math.radians(rot[1])), f(math.radians(rot[2]))))
    return "\n".join(lines)


def java_source(results):
    out = ["package com.abyssia.client.armor;", "",
           "import net.minecraft.client.model.geom.builders.CubeDeformation;",
           "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
           "import net.minecraft.client.model.geom.builders.LayerDefinition;",
           "import net.minecraft.client.model.geom.builders.MeshDefinition;",
           "import net.minecraft.client.model.geom.builders.PartDefinition;",
           "import net.minecraft.client.model.geom.PartPose;", "",
           "/**",
           " * generated by tools/diving_suit_models.py - do not edit, change the parts tables and rerun the tool.",
           " * Humanoid armor meshes (head, hat, body, arms, legs at vanilla pivots) for the three diving-gear tiers;",
           " * rotated cubes (snorkel, fins) are child parts of those. Not built with HumanoidModel.createMesh, so no",
           " * vanilla boxes. Textures: textures/models/armor/<material>_layer_1.png.",
           " */",
           "@SuppressWarnings(\"unused\")",
           "public final class DivingSuitMesh",
           "{",
           "    private DivingSuitMesh() {}", ""]
    for tier, (cubes, W, H) in results.items():
        out += ["    /** %s tier, texture %dx%d. */" % (tier, W, H),
                "    public static LayerDefinition %s()" % tier, "    {",
                "        MeshDefinition mesh = new MeshDefinition();",
                "        PartDefinition root = mesh.getRoot();"]
        for name in PARTS:
            out.append(java_part(name, cubes))
        out += ["        return LayerDefinition.create(mesh, %d, %d);" % (W, H), "    }", ""]
    out.append("}")
    return "\n".join(out) + "\n"


# --------------------------------------------------------------------------- bbmodel (inspection only)
def bb(v):
    """Model space (y down, x flipped at render) -> Blockbench modded-entity space."""
    return [-v[0], 24 - v[1], v[2]]


def bbmodel(tier, cubes, img, W, H):
    tex_id = str(uuid.uuid4())
    buf = io.BytesIO()
    img.save(buf, "PNG")
    elements, groups = [], []
    for name in PARTS:
        mine = [q for q in cubes if q["part"] == name]
        if not mine:
            continue
        pose = POSES[name]
        ids = []
        for q in mine:
            a = [pose[k] + q["origin"][k] for k in range(3)]
            b = [a[k] + q["D"][k] for k in range(3)]
            piv = [pose[k] + q["pivot"][k] for k in range(3)]
            fa, tb = bb(b), bb(a)
            el = {"name": "%s_%s_%d" % (name, q["tag"], q["id"]), "box_uv": True, "rescale": False, "locked": False,
                  "from": [fa[0], fa[1], a[2]], "to": [tb[0], tb[1], b[2]], "autouv": 0, "color": 0,
                  "origin": bb(piv), "rotation": [-q["rot"][0], -q["rot"][1], q["rot"][2]],
                  "inflate": round(sum(q["grow"]) / 3, 3), "uv_offset": list(q["uv"]), "mirror_uv": q["mirror"],
                  "type": "cube", "uuid": str(uuid.uuid4())}
            elements.append(el)
            ids.append(el["uuid"])
        groups.append({"name": name, "origin": bb(pose), "rotation": [0, 0, 0], "bedrock_binding": "", "color": 0,
                       "uuid": str(uuid.uuid4()), "export": True, "mirror_uv": False, "isOpen": True, "locked": False,
                       "visibility": True, "autouv": 0, "children": ids})
    return {"meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
            "name": "diving_suit_" + tier, "model_identifier": "diving_suit_" + tier,
            "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
            "resolution": {"width": W, "height": H}, "elements": elements, "outliner": groups,
            "textures": [{"path": "", "name": tier + ".png", "folder": "", "namespace": "", "id": "0",
                          "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                          "saved": False, "uuid": tex_id,
                          "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")}]}


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--tier", choices=sorted(TIERS))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--json", action="store_true", help="print a JSON summary instead of text")
    a = ap.parse_args()
    with open(PALETTE, encoding="utf-8") as fh:
        pal = json.load(fh)
    results, images, summary = {}, {}, {}
    for tier in TIERS:
        cubes = build_cubes(TIERS[tier])
        W, H = pack(cubes)
        results[tier] = (cubes, W, H)
        images[tier] = paint(cubes, W, H, pal[tier])
        summary[tier] = {"texture": [W, H], "cubes": len(cubes), "rotated": sum(1 for q in cubes if q["rot"] != (0, 0, 0)),
                         "files": ["models/armor/%s_layer_1.png" % m for m in OUT_MATERIALS[tier]]}
    if not a.dry_run:
        os.makedirs(os.path.dirname(JAVA), exist_ok=True)
        with open(JAVA, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(java_source(results))
        os.makedirs(TEX, exist_ok=True)
        os.makedirs(LOCK, exist_ok=True)
        os.makedirs(BB_DIR, exist_ok=True)
        for tier, mats in OUT_MATERIALS.items():
            if a.tier and a.tier != tier:
                continue
            for m in mats:
                p = os.path.join(TEX, m + "_layer_1.png")
                images[tier].save(p)
                shutil.copyfile(p, os.path.join(LOCK, m + "_layer_1.png"))
            cubes, W, H = results[tier]
            with open(os.path.join(BB_DIR, tier + ".bbmodel"), "w", encoding="utf-8", newline="\n") as fh:
                json.dump(bbmodel(tier, cubes, images[tier], W, H), fh)
    if a.json:
        print(json.dumps({"dry_run": a.dry_run, "java": os.path.relpath(JAVA, REPO), "tiers": summary}, indent=1))
    else:
        print(("would write " if a.dry_run else "wrote ") + os.path.relpath(JAVA, REPO))
        for tier, s in summary.items():
            print("%s: %dx%d, %d cubes (%d rotated) -> %s"
                  % (tier, s["texture"][0], s["texture"][1], s["cubes"], s["rotated"], ", ".join(s["files"])))


if __name__ == "__main__":
    main()
