"""Abyss mother texture (128x128). Box unwraps must match AbyssMotherRenderer's UV regions {u, v, tw, th, tl}.
Unwrap (texels tw,th,tl at u,v): TOP (u+tl,v,tw,tl)  BOTTOM (u+tl+tw,v,tw,tl)
RIGHT (u,v+tl,tl,th)  FRONT (u+tl,v+tl,tw,th)  LEFT (u+tl+tw,v+tl,tl,th)  BACK (u+2tl+tw,v+tl,tw,th)
Box axes: w = X (tw), h = Y (th), l = Z (tl). Side faces: rows top->bottom = +Y -> -Y.
Bell sectors: local +X = radially out, so RIGHT = outer skin, LEFT = subumbrella (inside of the bell).
Glow regions (CANAL/GONAD/RING/DOT/TGLOW) are drawn in the full-bright translucent pass: transparent = nothing.
The brood jellies (AbyssMotherRenderer.Spawn) reuse the bell, petal, core and tentacle regions.
Usage: python tools/boss_mother_texture.py [out.png]"""
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/abyssia/textures/entity/abyss_mother.png"
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()

def c(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (a,)

CLEAR = (0, 0, 0, 0)
DEEP, BELL, BELL_MID, BELL_HI, BELL_LINE = c("#1a1240"), c("#2a1d5c"), c("#35276e"), c("#4a3a96"), c("#4d62b0")
SUB, SUB_HI = c("#20164a"), c("#2e2266")
RIM_BAND, LIP = c("#1c1446"), c("#7a68d0")
CYAN, CYAN_HI, CYAN_DK = c("#7fe8ff"), c("#d2faff"), c("#4fb8dc")
CORE_DK, CORE, CORE_MID, CORE_HI, CORE_C = c("#6a0c4c"), c("#a8167a"), c("#d42a98"), c("#ff64cc"), c("#ffc8f0")
PETAL, PETAL_HI, PETAL_VEIN, PETAL_IN, PETAL_IN_HI = c("#3e2a80"), c("#5a44a8"), c("#7a64c8"), c("#5a1c62"), c("#7a2a7c")
ARM, ARM_SH, ARM_HI = c("#b49ad8"), c("#8a70b8"), c("#d8c6f0")
FRILL, FRILL_EDGE = c("#c4a6e6"), c("#ece0fa")
TENT, TENT_SH, NEMA = c("#b6a0dc"), c("#9682c2"), c("#f6f2ff")
TGLOW, TGLOW_HI = c("#c8b8ff", 200), c("#b8f0ff", 230)

def rect(x, y, w, h, col):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = col

def faces(u, v, tw, th, tl):
    return {"top": (u + tl, v, tw, tl), "bottom": (u + tl + tw, v, tw, tl),
            "right": (u, v + tl, tl, th), "front": (u + tl, v + tl, tw, th),
            "left": (u + tl + tw, v + tl, tl, th), "back": (u + 2 * tl + tw, v + tl, tw, th)}

def dither(x, y, w, h, a, b, period=3):
    """two-colour checker-ish dither (the 'see-through' jelly look in a cutout texture)."""
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = b if (i + 2 * j) % period == 0 else a

# BELL (0,0) tw4 th6 tl12: outer skin, faint canal line through the middle of each sector
f = faces(0, 0, 4, 6, 12)
for k in ("right", "front", "back"):
    x, y, w, h = f[k]
    dither(x, y, w, h, BELL, BELL_MID)
    rect(x, y, w, 1, BELL_HI)
x, y, w, h = f["right"]
rect(x + 5, y, 2, h, BELL_LINE)
x, y, w, h = f["top"]                                  # upper surface: u = radial, v = tangential
dither(x, y, w, h, BELL, BELL_MID)
rect(x, y + 5, w, 2, BELL_LINE)
rect(x + w - 1, y, 1, h, BELL_HI)
for k in ("left", "bottom"):                           # subumbrella: muscle rings
    x, y, w, h = f[k]
    rect(x, y, w, h, SUB)
    for j in range(0, h, 2):
        rect(x, y + j, w, 1, SUB_HI)

# BELL_RIM (32,0): skin, dark band, bright lip on the edge
f = faces(32, 0, 4, 6, 12)
for k in ("right", "front", "back"):
    x, y, w, h = f[k]
    dither(x, y, w, 4, BELL, BELL_MID)
    rect(x, y + 4, w, 1, RIM_BAND)
    rect(x, y + 5, w, 1, LIP)
x, y, w, h = f["right"]
rect(x + 5, y, 2, 4, BELL_LINE)
x, y, w, h = f["top"]
dither(x, y, w, h, BELL, BELL_MID)
for k in ("left",):
    x, y, w, h = f[k]
    rect(x, y, w, h, SUB)
    rect(x, y + 1, w, 1, SUB_HI)
    rect(x, y + 3, w, 1, SUB_HI)
    rect(x, y + 5, w, 1, LIP)                          # velum
rect(*f["bottom"], LIP)

def canal(x, y, w, h):
    """radial canal: a vertical glowing line in the middle of the face."""
    m = w // 2
    rect(x + m - 1, y, 2, h, CYAN)
    rect(x + m - 1, y, 1, h, CYAN_HI)

# CANAL (64,0) tw1 th6 tl12: only the LEFT face (the inside of the bell) is painted
f = faces(64, 0, 1, 6, 12)
canal(*f["left"])

# GONAD (90,0): canal + a horseshoe gonad around it
f = faces(90, 0, 1, 6, 12)
x, y, w, h = f["left"]
canal(x, y, w, h)
for i in range(w):
    for j in range(h):
        dx, dy = (i - 5.5) / 4.6, (j - 1.0) / 4.2
        d = dx * dx + dy * dy
        if 0.55 <= d <= 1.0:
            px[x + i, y + j] = CYAN_DK if d > 0.8 else CYAN

# RING (76,40): ring canal along the bell margin + the start of the radial canal
f = faces(76, 40, 1, 6, 12)
x, y, w, h = f["left"]
canal(x, y, w, 4)
rect(x, y + 4, w, 1, CYAN)
rect(x, y + 5, w, 1, CYAN_DK)

# DOT (116,0) tw2 th2 tl2: rim light
f = faces(116, 0, 2, 2, 2)
for k, r in f.items():
    rect(*r, CYAN)
    px[r[0], r[1]] = CYAN_HI

# SCALLOP (0,20) tw2 th6 tl12: rounded lobe hanging from the rim (cut out), lip along the edge
f = faces(0, 20, 2, 6, 12)
for k in ("right", "left"):
    x, y, w, h = f[k]
    for i in range(w):
        for j in range(h):
            dx, dy = (i - 5.5) / 6.0, (j + 0.5) / 6.4
            d = dx * dx + dy * dy
            if d > 1.0:
                col = CLEAR
            elif d > 0.72:
                col = LIP
            elif k == "right":
                col = BELL_MID if (i + 2 * j) % 3 == 0 else BELL
            else:
                col = SUB_HI if j % 2 == 0 else SUB
            px[x + i, y + j] = col
    rect(x + 5, y, 2, 3, BELL_LINE if k == "right" else CYAN_DK)
for k in ("front", "back"):
    rect(*f[k], LIP)

# CAP (28,20) tw8 th2 tl8: crown, 8 canals converging on a pale centre
f = faces(28, 20, 8, 2, 8)
for k, r in f.items():
    dither(*r, BELL, BELL_MID)
x, y, w, h = f["top"]
for i in range(w):
    for j in range(h):
        dx, dy = i - 3.5, j - 3.5
        if abs(dx) < 1 or abs(dy) < 1 or abs(abs(dx) - abs(dy)) < 0.6:
            px[x + i, y + j] = BELL_LINE
rect(x + 3, y + 3, 2, 2, BELL_HI)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, BELL_HI)

# CORE (60,20) tw8 th8 tl8: the abyss heart, magenta, brighter toward each face centre
f = faces(60, 20, 8, 8, 8)
for k, (x, y, w, h) in f.items():
    for i in range(w):
        for j in range(h):
            d = max(abs(i - 3.5), abs(j - 3.5))
            px[x + i, y + j] = CORE_C if d < 1 else CORE_HI if d < 2 else CORE_MID if d < 3 else CORE if d < 3.6 else CORE_DK

def petal(u, tip):
    f = faces(u, 20, 1, 8, 6)
    for k in ("right", "left"):
        x, y, w, h = f[k]
        for i in range(w):
            for j in range(h):
                half = 3.0 * (1.0 - j / 8.5) + 0.4 if tip else 3.0
                if abs(i - 2.5) > half:
                    px[x + i, y + j] = CLEAR
                    continue
                if k == "right":
                    col = PETAL_VEIN if i in (2, 3) else PETAL_HI if abs(abs(i - 2.5) - half) < 0.6 else PETAL
                else:
                    col = PETAL_IN_HI if i in (2, 3) else PETAL_IN
                px[x + i, y + j] = col
    for k in ("front", "back", "top", "bottom"):
        rect(*f[k], PETAL_HI)
    if tip:
        rect(*f["bottom"], CLEAR)

# PETAL (92,20) / PETAL_TIP (106,20) tw1 th8 tl6: inner-bell valve, outer violet, inner face red-purple
petal(92, False)
petal(106, True)

# ARM (0,40) tw4 th8 tl4: oral arm, pale purple with folds
f = faces(0, 40, 4, 8, 4)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, h, ARM)
    for j in range(1, h, 3):
        rect(x, y + j, w, 1, ARM_HI)
        rect(x, y + j + 1, w, 1, ARM_SH)
for k in ("top", "bottom"):
    rect(*f[k], ARM_SH)

# FRILL (16,40) tw1 th8 tl3: ruffled plate, both long edges scalloped (cut out)
f = faces(16, 40, 1, 8, 3)
for k in ("right", "left"):
    x, y, w, h = f[k]
    rect(x, y, w, h, FRILL)
    for j in range(h):
        px[x, y + j] = CLEAR if j % 4 == 0 else FRILL_EDGE
        px[x + w - 1, y + j] = CLEAR if j % 4 == 2 else FRILL_EDGE
for k in ("front", "back", "top", "bottom"):
    rect(*f[k], FRILL_EDGE)

# TENT (24,40) tw4 th4 tl16: thin lilac tentacle, white nematocyst dots
f = faces(24, 40, 4, 4, 16)
for k, (x, y, w, h) in f.items():
    dither(x, y, w, h, TENT, TENT_SH, 4)
    for i in range(w):
        for j in range(h):
            if (i + 3 * j) % 5 == 0:
                px[x + i, y + j] = NEMA

# TGLOW (64,40) tw4 th4 tl2: faint glowing band (every second tentacle segment)
f = faces(64, 40, 4, 4, 2)
for k, (x, y, w, h) in f.items():
    rect(x, y, w, h, TGLOW)
for k in ("right", "left"):
    x, y, w, h = f[k]
    rect(x, y + 1, w, 2, TGLOW_HI)

img.save(OUT)
print("saved", OUT)
