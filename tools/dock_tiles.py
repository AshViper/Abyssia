"""SUB04 submarine dock tiles: clean flat 16x16 pixel art in the submarine texture's own palette.

The ChatGPT-traced tiles read as noisy / "AI" (user 2026-10-04), so the dock now uses flat fills with 1 px bevels,
like the user's submarine texture. Writes inbox/textures/dock_<tile>.png (input of tools/anim_model.py).

    python tools/dock_tiles.py [--out DIR] [--preview PNG]
"""
import argparse
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# palette sampled from textures/entity/submarine.png
STEEL, STEEL_HI, STEEL_LO = (0x61, 0x6c, 0x74), (0x73, 0x7b, 0x80), (0x4b, 0x55, 0x5b)
DARK, DARK_HI, DARK_LO = (0x3c, 0x45, 0x4b), (0x4e, 0x55, 0x59), (0x2f, 0x33, 0x36)
ORANGE, ORANGE_LO = (0xc6, 0x6e, 0x1e), (0xb5, 0x64, 0x1b)
TEAL, TEAL_HI = (0x4f, 0xd8, 0xc4), (0xb9, 0xf7, 0xe3)
CLEAR = (0, 0, 0, 0)


def tile(fill):
    return Image.new("RGBA", (16, 16), fill + (255,) if len(fill) == 3 else fill)


def put(img, x, y, c):
    img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def bevel(img, hi, lo, x0=0, y0=0, x1=15, y1=15):
    for i in range(x0, x1 + 1):
        put(img, i, y0, hi)
        put(img, i, y1, lo)
    for j in range(y0, y1 + 1):
        put(img, x0, j, hi)
        put(img, x1, j, lo)


def metal():
    img = tile(STEEL)
    bevel(img, STEEL_HI, STEEL_LO)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):  # rivets
        put(img, x, y, STEEL_LO)
        put(img, x - 1, y - 1, STEEL_HI)
    return img


def dark():
    img = tile(DARK)
    bevel(img, DARK_HI, DARK_LO)
    for x in range(1, 15):  # centre seam
        put(img, x, 7, DARK_LO)
        put(img, x, 8, DARK_HI)
    return img


def light():
    img = tile(DARK)
    bevel(img, DARK_HI, DARK_LO)
    for x in range(2, 14):
        for y in range(6, 10):
            put(img, x, y, TEAL)
        put(img, x, 7, TEAL_HI)
    return img


def hazard():
    img = tile(DARK_LO)
    for x in range(16):
        for y in range(16):
            if (x + y) // 4 % 2 == 0:
                put(img, x, y, ORANGE)
    for i in range(16):
        put(img, i, 15, ORANGE_LO if (i + 15) // 4 % 2 == 0 else DARK_LO)
    return img


def grating():
    img = tile(CLEAR)
    for i in range(16):
        for b in (0, 15):
            put(img, i, b, STEEL_LO)
            put(img, b, i, STEEL_LO)
        for b in (5, 10):  # bars
            put(img, b, i, STEEL)
            put(img, i, b, STEEL)
    return img


def rail():
    img = tile(CLEAR)
    for x in range(16):
        put(img, x, 1, STEEL_HI)
        put(img, x, 2, STEEL_LO)
        put(img, x, 9, STEEL)
    for x in (0, 1, 14, 15):
        for y in range(1, 16):
            put(img, x, y, STEEL if x in (0, 14) else STEEL_LO)
    return img


TILES = {"metal": metal, "dark": dark, "light": light, "hazard": hazard, "gangway_grating": grating, "gangway_rail": rail}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "..", "inbox", "textures"))
    ap.add_argument("--preview")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    imgs = {k: f() for k, f in TILES.items()}
    for k, img in imgs.items():
        img.save(os.path.join(a.out, f"dock_{k}.png"))
    if a.preview:
        sheet = Image.new("RGBA", (len(imgs) * 130, 130), (40, 40, 40, 255))
        for i, img in enumerate(imgs.values()):
            big = img.resize((128, 128), Image.NEAREST)
            sheet.paste(big, (i * 130, 0), big)
        sheet.save(a.preview)
    print({"written": [f"dock_{k}.png" for k in imgs], "out": os.path.abspath(a.out)})


if __name__ == "__main__":
    main()
