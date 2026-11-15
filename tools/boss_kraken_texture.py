"""Abyss kraken texture (128x128). Box unwraps must match AbyssKrakenRenderer's UV regions.
Unwrap (texels tw,th,tl at u,v): TOP (u+tl,v,tw,tl)  BOTTOM (u+tl+tw,v,tw,tl)
RIGHT (u,v+tl,tl,th)  FRONT (u+tl,v+tl,tw,th)  LEFT (u+tl+tw,v+tl,tl,th)  BACK (u+2tl+tw,v+tl,tw,th)
Side faces: rows top->bottom = +Y -> -Y. Mantle: FRONT = +Z (dorsal), BACK = -Z (ventral, pale).
Tentacle boxes: local +Z = toward the tip, BOTTOM (-Y) = underside with the suckers.
Usage: python tools/boss_kraken_texture.py [out.png]"""
import random
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/abyssia/textures/entity/abyss_kraken.png"
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()
rng = random.Random(7)

def c(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (a,)

EDGE, DARK, MID, CRIM, HI = c("#1a0610"), c("#3a0c22"), c("#5e1230"), c("#7d1a32"), c("#9a2a40")
CHROMA, CHROMA_RIM = c("#200612"), c("#4a0f26")
TRANS = c("#8a4a5a")
BELLY, BELLY_SH, BELLY_HI = c("#c9a6ad"), c("#a98890"), c("#e0c6ca")
SUCK_RIM, SUCK_IN, SUCK_C = c("#ecd8d4"), c("#8a5e68"), c("#5a3440")
IRIS, IRIS_DK, IRIS_HI, PUPIL, EYE_RIM = c("#d9a326"), c("#a67414"), c("#f2cf5a"), c("#050304"), c("#2a0a14")
LIP, LIP_DK, HOLE = c("#4a1626"), c("#2a0a14"), c("#0a0306")
BEAK, BEAK_HI, BEAK_TIP = c("#1a1210"), c("#4a3a30"), c("#0b0807")
HOOK, HOOK_SH, HOOK_TIP = c("#e6dcc8"), c("#a8987c"), c("#6a5a44")
FIN, FIN_RAY, FIN_EDGE = c("#4a0f28"), c("#6a1a36"), c("#8c2a44")
FLESH, FLESH_DK, FLESH_HI, FLESH_C = c("#b8283a"), c("#7a1424"), c("#e06070"), c("#f09098")
GLOW, GLOW_CORE, GLOW_RIM = c("#c8ecff"), c("#ffffff"), c("#7ac0f0")
RIM = c("#a8d8ff", 110)
PALM_SPOT = c("#7ac0f0")

def rect(x, y, w, h, col):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = col

def faces(u, v, tw, th, tl):
    return {"top": (u + tl, v, tw, tl), "bottom": (u + tl + tw, v, tw, tl),
            "right": (u, v + tl, tl, th), "front": (u + tl, v + tl, tw, th),
            "left": (u + tl + tw, v + tl, tl, th), "back": (u + 2 * tl + tw, v + tl, tw, th)}

def chroma(x, y, w, h, n, dark=CHROMA, rim=CHROMA_RIM):
    """chromatophores: n scattered dark dots, some with a lighter rim pixel."""
    for _ in range(n):
        i, j = rng.randrange(w), rng.randrange(h)
        px[x + i, y + j] = dark
        if rng.random() < 0.4 and i + 1 < w:
            px[x + i + 1, y + j] = rim

def skin(x, y, w, h, bands, n):
    """dark-to-crimson banded skin (bands = [(rows, colour)] from the top) with chromatophores."""
    j = 0
    for rows, col in bands:
        rect(x, y + j, w, min(rows, h - j), col)
        j += rows
        if j >= h:
            break
    if j < h:
        rect(x, y + j, w, h - j, bands[-1][1])
    chroma(x, y, w, h, n)

def belly(x, y, w, h, n=0):
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = BELLY_SH if (i + j * 3) % 7 == 0 else BELLY
    chroma(x, y, w, h, n, BELLY_SH, BELLY_HI)

# MANTLE (0,0) tw16 th16 tl16
f = faces(0, 0, 16, 16, 16)
skin(*f["top"], [(16, DARK)], 22)
x, y, w, h = f["bottom"]
rect(x, y, w, h, BELLY_SH)
rect(x + 2, y + 2, w - 4, h - 4, TRANS)
mantle_bands = [(3, DARK), (6, MID), (6, CRIM), (1, HI)]
for k in ("right", "left", "front"):
    x, y, w, h = f[k]
    skin(x, y, w, h - 1, mantle_bands, 26)
    rect(x, y + h - 1, w, 1, HI)                     # mantle opening edge
for k, col0 in (("right", 14), ("left", 0)):          # sides pale toward the ventral (-Z) edge
    x, y, w, h = f[k]
    for i in range(col0, col0 + 2):
        for j in range(4, h - 1):
            px[x + i, y + j] = TRANS if (i == col0) == (k == "right") else BELLY_SH
x, y, w, h = f["back"]                                # ventral: pale pinkish grey
rect(x, y, w, 2, MID)
rect(x, y + 2, w, 1, TRANS)
belly(x, y + 3, w, h - 4, 6)
rect(x, y + h - 1, w, 1, BELLY_HI)

# HEAD (64,0) tw12 th8 tl12
f = faces(64, 0, 12, 8, 12)
skin(*f["top"], [(12, MID)], 10)
x, y, w, h = f["bottom"]
rect(x, y, w, h, LIP)
rect(x + 3, y + 3, w - 6, h - 6, LIP_DK)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    skin(x, y, w, 4, [(2, CRIM), (2, HI)], 5)
    rect(x, y + 4, w, 1, TRANS)
    belly(x, y + 5, w, 2, 2)
    rect(x, y + 7, w, 1, LIP)                         # arm web at the base

# GLOW (112,0) tw3 th1 tl3: bright core, rim
f = faces(112, 0, 3, 1, 3)
for k, r in f.items():
    rect(*r, GLOW_RIM)
x, y, w, h = f["top"]
rect(x, y, w, h, GLOW)
px[x + 1, y + 1] = GLOW_CORE
x, y, w, h = f["bottom"]
rect(x, y, w, h, GLOW)
px[x + 1, y + 1] = GLOW_CORE

# RIM (112,8) tw4 th1 tl1: faint translucent line
rect(112, 8, 10, 2, RIM)

# CUT (96,24) tw6 th6 tl2: raw flesh, skin ring at the border
f = faces(96, 24, 6, 6, 2)
for k, r in f.items():
    rect(*r, FLESH)
for k in ("front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, h, FLESH_DK)
    rect(x + 1, y + 1, w - 2, h - 2, FLESH)
    rect(x + 2, y + 2, w - 4, h - 4, FLESH_HI)
    px[x + 2, y + 2] = FLESH_C
    px[x + 3, y + 3] = FLESH_C
    px[x, y] = DARK; px[x + w - 1, y] = DARK; px[x, y + h - 1] = DARK; px[x + w - 1, y + h - 1] = DARK

def suckers(x, y, w, h, period):
    """underside: pale, two staggered columns of sucker rings (3x3: pale rim, dark centre)."""
    belly(x, y, w, h)
    for col, off in ((1, 0), (4, period // 2)):
        for j0 in range(-period, h, period):
            for di in range(3):
                for dj in range(3):
                    jj = j0 + off + dj
                    if 0 <= jj < h:
                        px[x + col + di, y + jj] = SUCK_C if (di, dj) == (1, 1) else SUCK_RIM
    for j in range(h):
        px[x, y + j] = BELLY_SH
        px[x + w - 1, y + j] = BELLY_SH

def arm_side(x, y, w, h, top_col, tip=HOOK):
    """side: skin on top, transition, pale underside, sucker / hook edges on the last row."""
    skin(x, y, w, 4, [(2, top_col), (2, top_col)], max(1, w // 4))
    rect(x, y + 4, w, 1, TRANS)
    rect(x, y + 5, w, h - 6, BELLY)
    for i in range(w):
        px[x + i, y + h - 1] = tip if i % 3 == 1 else BELLY_SH

# ARM (0,32) tw8 th8 tl16
f = faces(0, 32, 8, 8, 16)
x, y, w, h = f["top"]
skin(x, y, w, h, [(16, MID)], 10)
rect(x + 3, y, 2, h, DARK)                            # dorsal stripe
suckers(*f["bottom"], 4)
for k in ("right", "left", "front", "back"):
    arm_side(*f[k], MID, SUCK_RIM)

# CLUB (48,32) tw8 th8 tl12: deeper crimson, hooks and glow spots on the palm
f = faces(48, 32, 8, 8, 12)
x, y, w, h = f["top"]
skin(x, y, w, h, [(12, CRIM)], 10)
rect(x + 3, y, 2, h, MID)
x, y, w, h = f["bottom"]
suckers(x, y, w, h, 6)
for j in range(1, h, 6):
    px[x + 3, y + j + 1] = PALM_SPOT; px[x + 4, y + j + 1] = PALM_SPOT
for k in ("right", "left", "front", "back"):
    arm_side(*f[k], CRIM, HOOK)

# FIN (0,56) tw12 th12 tl2: flap, rays fanning out from the mantle side
f = faces(0, 56, 12, 12, 2)
for k in ("front", "back"):
    x, y, w, h = f[k]
    for i in range(w):
        for j in range(h):
            col = FIN_RAY if (j - h // 2) != 0 and (i + abs(j - h // 2) * 2) % 5 == 0 else FIN
            if j in (0, h - 1) or (k == "front" and i == w - 1) or (k == "back" and i == 0):
                col = FIN_EDGE
            px[x + i, y + j] = col
    chroma(x, y, w, h, 6)
for k in ("top", "bottom", "right", "left"):
    rect(*f[k], FIN_EDGE)

# EYE (32,56) tw2 th8 tl10: golden iris, horizontal black pupil (not glowing)
f = faces(32, 56, 2, 8, 10)
for k, r in f.items():
    rect(*r, EYE_RIM)
for k in ("right", "left"):
    x, y, w, h = f[k]
    for i in range(1, w - 1):
        for j in range(1, h - 1):
            dx, dy = (i - (w - 1) / 2) / ((w - 2) / 2), (j - (h - 1) / 2) / ((h - 2) / 2)
            if dx * dx + dy * dy <= 1.15:
                px[x + i, y + j] = IRIS_DK if dy > 0.45 else IRIS
    rect(x + 2, y + 3, w - 4, 2, PUPIL)
    px[x + 3, y + 2] = IRIS_HI; px[x + 4, y + 2] = IRIS_HI

# MOUTH (56,56) tw8 th3 tl8: lips around a black hole
f = faces(56, 56, 8, 3, 8)
for k, r in f.items():
    rect(*r, LIP)
x, y, w, h = f["bottom"]
rect(x + 1, y + 1, w - 2, h - 2, LIP_DK)
rect(x + 2, y + 2, w - 4, h - 4, HOLE)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y + h - 1, w, 1, LIP_DK)
    rect(x, y, w, 1, TRANS)

# BEAK (88,56) tw3 th4 tl2: glossy black chitin
f = faces(88, 56, 3, 4, 2)
for k, r in f.items():
    rect(*r, BEAK)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, 1, h - 1, BEAK_HI)
    rect(x, y + h - 1, w, 1, BEAK_TIP)
rect(*f["top"], BEAK_HI)

# HOOK (104,56) tw1 th2 tl1: pale claw, dark point
f = faces(104, 56, 1, 2, 1)
for k, r in f.items():
    rect(*r, HOOK)
for k in ("right", "left", "front", "back"):
    x, y, w, h = f[k]
    rect(x, y, w, 1, HOOK_SH)
    rect(x, y + h - 1, w, 1, HOOK_TIP)
rect(*f["bottom"], HOOK_TIP)

img.save(OUT)
print("saved", OUT)
