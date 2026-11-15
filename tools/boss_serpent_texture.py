"""Abyss serpent texture (128x128). Box unwraps must match AbyssSerpentRenderer's UV regions.
Unwrap (texels tw,th,tl at u,v): TOP (u+tl,v,tw,tl)  BOTTOM (u+tl+tw,v,tw,tl)
RIGHT (u,v+tl,tl,th)  FRONT (u+tl,v+tl,tw,th)  LEFT (u+tl+tw,v+tl,tl,th)  BACK (u+2tl+tw,v+tl,tw,th)
Side faces: rows top->bottom = +Y -> -Y. TOP face rows = back -> front."""
import sys
from PIL import Image

OUT = sys.argv[1]
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()

def c(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)

EDGE, DARK, MID, HI = c("#0a1220"), c("#0f1d30"), c("#17293f"), c("#24405e")
TRANS = c("#3a5068")
BELLY, BELLY_SH, BELLY_HI = c("#8597a9"), c("#6a7c8f"), c("#a2b2c2")
MOUTH, MOUTH_SH, TONGUE = c("#3b1526"), c("#28101b"), c("#5a2236")
TOOTH, TOOTH_SH = c("#dfe6e8"), c("#a9b6bd")
SPINE, SPINE_TIP = c("#1b2f49"), c("#4a6f92")
FIN, FIN_RAY, FIN_EDGE = c("#132842"), c("#2a4c6e"), c("#3d6b8f")
GLOW, GLOW_CORE, GLOW_RIM = c("#4feaff"), c("#d2ffff"), c("#1fb4d2")
EYE, EYE_CORE = c("#59f2e4"), c("#e8fffb")

def rect(x, y, w, h, col):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = col

def faces(u, v, tw, th, tl):
    return {"top": (u + tl, v, tw, tl), "bottom": (u + tl + tw, v, tw, tl),
            "right": (u, v + tl, tl, th), "front": (u + tl, v + tl, tw, th),
            "left": (u + tl + tw, v + tl, tl, th), "back": (u + 2 * tl + tw, v + tl, tw, th)}

def scales(x, y, w, h, stripe=True):
    """dorsal scale pattern: offset rows of 4x3 scales, dark rim + highlight, darker dorsal stripe in the middle."""
    for i in range(w):
        for j in range(h):
            row = j // 3
            sx = (i + (2 if row % 2 else 0)) % 4
            sy = j % 3
            col = MID
            if sy == 2: col = DARK
            elif sy == 0 and sx in (1, 2): col = HI
            if sx == 0 and sy == 1: col = DARK
            if stripe and abs(i - (w - 1) / 2) < 1: col = EDGE
            px[x + i, y + j] = col

def side(x, y, w, h, belly_rows):
    """side face: scales on top, a transition row, then pale belly rows."""
    top = h - belly_rows - 1
    scales(x, y, w, top, stripe=False)
    rect(x, y + top, w, 1, TRANS)
    for j in range(belly_rows):
        for i in range(w):
            col = BELLY if j % 3 != 2 else BELLY_SH
            if j == 0 and i % 2 == 0: col = BELLY_HI
            px[x + i, y + top + 1 + j] = col

def belly(x, y, w, h):
    for i in range(w):
        for j in range(h):
            col = BELLY_SH if j % 4 == 3 else BELLY
            if i in (0, w - 1): col = BELLY_SH
            px[x + i, y + j] = col

# BODY (0,0) tw12 th12 tl16
f = faces(0, 0, 12, 12, 16)
scales(*f["top"])
belly(*f["bottom"])
for k in ("right", "left"):
    side(*f[k], belly_rows=4)
for k in ("front", "back"):
    x, y, w, h = f[k]
    side(x, y, w, h, belly_rows=4)

# CRANIUM (0,28) tw18 th9 tl15
f = faces(0, 28, 18, 9, 15)
scales(*f["top"])
x, y, w, h = f["top"]
rect(x + 2, y + h - 2, w - 4, 1, HI)          # brow ridge at the front edge
rect(*f["bottom"], MOUTH)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    scales(x, y, w, h - 2, stripe=False)
    rect(x, y + h - 2, w, 1, TRANS)
    rect(x, y + h - 1, w, 1, BELLY_SH)
x, y, w, h = f["front"]
rect(x + 3, y + h - 2, w - 6, 2, MOUTH_SH)     # gullet seen when the jaw opens

# SNOUT (0,52) tw12 th6 tl12
f = faces(0, 52, 12, 6, 12)
scales(*f["top"])
x, y, w, h = f["top"]
px[x + 3, y + h - 2] = EDGE; px[x + w - 4, y + h - 2] = EDGE  # nostrils
rect(*f["bottom"], MOUTH)
x, y, w, h = f["bottom"]
rect(x + 2, y, w - 4, h, MOUTH_SH)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    scales(x, y, w, h - 1, stripe=False)
    rect(x, y + h - 1, w, 1, BELLY_HI)          # lip line

# LOWER JAW (0,70) tw11 th5 tl24
f = faces(0, 70, 11, 5, 24)
x, y, w, h = f["top"]
rect(x, y, w, h, MOUTH)
rect(x + 3, y + 2, w - 6, h - 4, TONGUE)
belly(*f["bottom"])
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, BELLY_HI)                  # lip line
    scales(x, y + 1, w, 2, stripe=False)
    rect(x, y + 3, w, 1, TRANS)
    rect(x, y + 4, w, h - 4, BELLY)

# FIN (0,100) tw1 th10 tl15: vertical blade, rays run along z
f = faces(0, 100, 1, 10, 15)
for k in ("right", "left"):
    x, y, w, h = f[k]
    for i in range(w):
        for j in range(h):
            col = FIN_RAY if (i + j) % 4 == 0 else FIN
            if j == 0 or j == h - 1: col = FIN_EDGE
            px[x + i, y + j] = col
for k in ("top", "bottom", "front", "back"):
    rect(*f[k], FIN_EDGE)

# SPINE (60,0) tw2 th6 tl4 and FRILL (76,0) tw2 th9 tl4: dark base, pale tip
for (u, v, th) in ((60, 0, 6), (76, 0, 9)):
    f = faces(u, v, 2, th, 4)
    for k in ("right", "left", "front", "back"):
        x, y, w, h = f[k]
        rect(x, y, w, h, SPINE)
        rect(x, y, w, 2, SPINE_TIP)
        rect(x, y + h - 1, w, 1, EDGE)
    rect(*f["top"], SPINE_TIP)
    rect(*f["bottom"], EDGE)

# TOOTH (92,0) tw1 th3 tl1
f = faces(92, 0, 1, 3, 1)
for k, r in f.items():
    rect(*r, TOOTH)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, TOOTH_SH)

# GLOW (100,0) tw3 th1 tl3: bright core, rim
f = faces(100, 0, 3, 1, 3)
for k, r in f.items():
    rect(*r, GLOW_RIM)
x, y, w, h = f["top"]
rect(x, y, w, h, GLOW)
px[x + 1, y + 1] = GLOW_CORE

# EYE (100,8) tw1 th2 tl3
f = faces(100, 8, 1, 2, 3)
for k, r in f.items():
    rect(*r, EYE)
for k in ("right", "left"):
    x, y, w, h = f[k]
    px[x + 1, y] = EYE_CORE

img.save(OUT)
print("saved", OUT)
