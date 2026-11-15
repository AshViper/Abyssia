"""Trench wyrm texture (128x128). Box unwraps must match TrenchWyrmRenderer's UV regions.
Usage: python tools/boss_wyrm_texture.py [out.png]   (default: src/main/resources/assets/abyssia/textures/entity/trench_wyrm.png)
Unwrap (texels tw,th,tl at u,v): TOP (u+tl,v,tw,tl)  BOTTOM (u+tl+tw,v,tw,tl)
RIGHT (u,v+tl,tl,th)  FRONT (u+tl,v+tl,tw,th)  LEFT (u+tl+tw,v+tl,tl,th)  BACK (u+2tl+tw,v+tl,tw,th)
Side faces: rows top->bottom = +Y -> -Y. TOP face rows = back -> front. BOTTOM face rows = front -> back.
RIGHT (+X) columns = front -> back, LEFT (-X) columns = back -> front.
Flat few-colour pixel art: rusty grey-brown dorsal plates with sand-dirty margins, pale belly rings,
dark round maw with a tooth ring, red-orange glow bits."""
import os
import sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia", "textures", "entity", "trench_wyrm.png")
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()


def c(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


PLATE_DK, PLATE, PLATE_HI = c("#2b2420"), c("#463a31"), c("#5e4a3b")
RUST, RUST_DK = c("#7f4a2b"), c("#5c3521")
SAND, SAND_LT = c("#a8987a"), c("#cfc3a4")
FLESH_DK, FLESH = c("#2a1918"), c("#4a2d2a")
BELLY, BELLY_SH, BELLY_HI = c("#857565"), c("#62564a"), c("#a29280")
MOUTH, MOUTH_RED, MOUTH_LIP = c("#140909"), c("#451414"), c("#6a2a22")
TOOTH, TOOTH_SH = c("#ddd2ba"), c("#a39680")
GLOW, GLOW_CORE, GLOW_RIM = c("#ff7426"), c("#ffd58c"), c("#d2461c")


def rect(x, y, w, h, col):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = col


def faces(u, v, tw, th, tl):
    return {"top": (u + tl, v, tw, tl), "bottom": (u + tl + tw, v, tw, tl),
            "right": (u, v + tl, tl, th), "front": (u + tl, v + tl, tw, th),
            "left": (u + tl + tw, v + tl, tl, th), "back": (u + 2 * tl + tw, v + tl, tw, th)}


def plate(x, y, w, h, seed=0):
    """chitin plate: grey-brown with blocky rust patches and pits (deterministic pattern)."""
    for i in range(w):
        for j in range(h):
            n = ((i // 2) * 7 + (j // 2) * 13 + seed * 5) % 17
            col = PLATE
            if n < 3: col = RUST
            elif n == 3: col = RUST_DK
            elif n == 9 and (i + j) % 2 == 0: col = PLATE_DK
            elif n in (13, 14): col = PLATE_HI
            px[x + i, y + j] = col


def ring_side(x, y, w, h, plated_rows, front_left):
    """ring side face: plated rows at the top, pale belly below, dark joint grooves at both ends."""
    for i in range(w):
        for j in range(h):
            if j < plated_rows:
                col = RUST_DK if (i + j) % 5 == 0 else PLATE_DK
            elif j == plated_rows:
                col = BELLY_SH
            else:
                col = BELLY if (j - plated_rows) % 3 else BELLY_HI
            if i == 0 or i == w - 1: col = FLESH_DK
            elif i == 1 or i == w - 2: col = FLESH if j >= plated_rows else PLATE_DK
            px[x + i, y + j] = col


# RING (0,0) tw10 th10 tl12: the body ring of every segment (mostly covered by the plate on top)
f = faces(0, 0, 10, 10, 12)
x, y, w, h = f["top"]
for i in range(w):
    for j in range(h):
        px[x + i, y + j] = FLESH_DK if j in (0, h - 1) else (FLESH if (i + j) % 4 else FLESH_DK)
x, y, w, h = f["bottom"]                           # belly: rows front -> back, grooves at both ends
for i in range(w):
    for j in range(h):
        col = BELLY if (j // 2) % 2 == 0 else BELLY_HI
        if j in (0, h - 1): col = FLESH_DK
        elif j in (1, h - 2): col = BELLY_SH
        if i in (0, w - 1): col = BELLY_SH
        px[x + i, y + j] = col
for k in ("right", "left"):
    ring_side(*f[k], plated_rows=4, front_left=k == "left")
for k in ("front", "back"):                        # joint ends (seen only at the tail tip and on sharp bends)
    x, y, w, h = f[k]
    rect(x, y, w, h, FLESH)
    rect(x + 2, y + 2, w - 4, h - 4, FLESH_DK)
    rect(x, y + h - 3, w, 3, BELLY_SH)

# PLATE_TOP (0,24) tw12 th2 tl12: dorsal tergite; sand settles along the back edge and the rims
f = faces(0, 24, 12, 2, 12)
x, y, w, h = f["top"]
plate(x, y, w, h, 1)
rect(x, y, w, 1, SAND_LT)                          # back edge (row 0), the overlapping lip
rect(x, y + 1, w, 1, SAND)
for j in range(h):
    px[x, y + j] = SAND if j % 3 else SAND_LT
    px[x + w - 1, y + j] = SAND if j % 3 else SAND_LT
rect(x + 1, y + h - 2, w - 2, 2, PLATE_DK)         # front strip, tucked under the plate ahead
for j in range(3, h - 3, 3):                       # dorsal keel
    px[x + w // 2, y + j] = PLATE_HI
    px[x + w // 2 - 1, y + j] = PLATE_HI
rect(*f["bottom"], FLESH_DK)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, SAND)
    rect(x, y + 1, w, 1, PLATE_DK)
x, y, w, h = f["back"]
rect(x, y, w, 1, SAND_LT)

# PLATE_SIDE (50,24) tw1 th6 tl12: the flaps hanging down the flanks; lower margin sandy
f = faces(50, 24, 1, 6, 12)
for k in ("right", "left"):
    x, y, w, h = f[k]
    plate(x, y, w, h - 2, 2)
    rect(x, y + h - 2, w, 1, SAND)
    rect(x, y + h - 1, w, 1, SAND_LT)
    back_col = x + w - 1 if k == "right" else x       # back edge column
    for j in range(h):
        px[back_col, y + j] = SAND if j < h - 1 else SAND_LT
for k in ("top", "bottom", "front", "back"):
    rect(*f[k], PLATE_DK)
rect(*f["bottom"], SAND)

# HEAD (0,44) tw16 th16 tl13: armoured capsule, round maw on the front face
f = faces(0, 44, 16, 16, 13)
plate(*f["top"], 3)
x, y, w, h = f["top"]
rect(x, y + h - 1, w, 1, SAND)
rect(*f["bottom"], BELLY)
x, y, w, h = f["bottom"]
for j in range(1, h, 3):
    rect(x, y + j, w, 1, BELLY_SH)
for k in ("right", "left"):
    x, y, w, h = f[k]
    plate(x, y, w, h - 5, 4)
    rect(x, y + h - 5, w, 1, SAND)
    rect(x, y + h - 4, w, 4, BELLY)
    rect(x, y + h - 2, w, 1, BELLY_SH)
rect(*f["back"], FLESH)
x, y, w, h = f["front"]                            # the maw, seen down the throat
cx, cy = (w - 1) / 2, (h - 1) / 2
for i in range(w):
    for j in range(h):
        r = ((i - cx) ** 2 + (j - cy) ** 2) ** 0.5
        if r > 7.6: col = PLATE_DK
        elif r > 6.6: col = MOUTH_LIP
        elif r > 5.6: col = MOUTH_RED
        elif r > 4.6: col = TOOTH if (i + j) % 2 == 0 else MOUTH_RED   # painted tooth ring
        elif r > 3.0: col = MOUTH_RED if (i * 3 + j) % 5 else MOUTH_LIP
        else: col = MOUTH
        px[x + i, y + j] = col

# JAW (0,74) tw10 th2 tl10: the three maw plates; outer face plated, tip sandy, inner face fleshy
f = faces(0, 74, 10, 2, 10)
x, y, w, h = f["top"]                              # outer face: rows hinge (back) -> tip (front)
plate(x, y, w, h, 5)
rect(x, y + h - 2, w, 1, SAND)
rect(x, y + h - 1, w, 1, SAND_LT)
for j in range(1, h - 2, 3):
    px[x, y + j] = SAND
    px[x + w - 1, y + j] = SAND
x, y, w, h = f["bottom"]                           # inner face: rows tip (front) -> hinge (back)
for i in range(w):
    for j in range(h):
        px[x + i, y + j] = MOUTH_LIP if j % 2 == 0 else MOUTH_RED
rect(x, y, w, 1, TOOTH_SH)
for k in ("right", "left", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, PLATE)
    rect(x, y + 1, w, h - 1, PLATE_DK)
x, y, w, h = f["front"]
rect(x, y, w, h, SAND_LT)

# LEG (80,0) tw1 th3 tl1: short belly legs, pale tip
f = faces(80, 0, 1, 3, 1)
for k, r in f.items():
    rect(*r, BELLY_SH)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, PLATE_DK)
    rect(x, y + h - 1, w, 1, TOOTH_SH)
rect(*f["bottom"], TOOTH_SH)

# TOOTH (88,0) tw1 th1 tl2: inner ring and jaw teeth
f = faces(88, 0, 1, 1, 2)
for k, r in f.items():
    rect(*r, TOOTH)
for k in ("right", "left"):
    x, y, w, h = f[k]
    px[x + (w - 1 if k == "left" else 0), y] = TOOTH_SH   # base end darker
rect(*f["back"], TOOTH_SH)

# GLOW (96,0) tw2 th1 tl2: red-orange glow bits (drawn full-bright)
f = faces(96, 0, 2, 1, 2)
for k, r in f.items():
    rect(*r, GLOW)
for k in ("front", "top"):
    x, y, w, h = f[k]
    rect(x, y, w, h, GLOW_CORE if k == "front" else GLOW)
x, y, w, h = f["top"]
px[x, y] = GLOW_RIM
px[x + w - 1, y + h - 1] = GLOW_RIM

# NEEDLE (80,8) tw4 th4 tl6: tail spike segments, dark chitin with a pale streak
f = faces(80, 8, 4, 4, 6)
for k, r in f.items():
    rect(*r, PLATE_DK)
for k in ("right", "left"):
    x, y, w, h = f[k]
    rect(x, y + 1, w, 1, SAND)
    rect(x, y, w, 1, PLATE)
x, y, w, h = f["top"]
rect(x + 1, y, 2, h, PLATE_HI)
rect(*f["bottom"], BELLY_SH)
rect(*f["back"], TOOTH_SH)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("saved", OUT)
