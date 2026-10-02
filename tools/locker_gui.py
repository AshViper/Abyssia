"""GUI texture of the large locker (H04 follow-up: 81 slots, own screen), drawn procedurally with Pillow.

Writes src/main/resources/assets/abyssia/textures/gui/large_locker.png (350x186, blitted with explicit texture size):
the deep-sea console panel of the industrial GUIs (palette / frame helpers from industrial_gui.py, spec
inbox/specs/I01-gui-spec.md), side by side: 9x9 storage grid left, player inventory + hotbar right (fits 426x240).

The numbers must match com.abyssia.furniture.LargeLockerMenu / client.LargeLockerScreen.
Run:  python tools/locker_gui.py
"""
import os

from PIL import Image

import industrial_gui as g

W, H = 350, 186
COLS, ROWS = 9, 9
GRID_X, GRID_Y = 8, 18            # first storage slot (item position), left half
INV_X, INV_LABEL_Y = 181, 18      # player inventory, right half, under its own label
INV_Y, HOTBAR_Y = 30, 88
TITLE_BAR = (5, 3, W - 6, 15)
AREA_BOX = (5, 16, 171, 180)      # storage grid
DIVIDER = (177, 16, W - 6, 27)    # "Inventory" header
DECOR_BOX = (177, 108, W - 6, 180)  # empty space under the hotbar: faint machinery


def panel(img):
    g.rect(img, 0, 0, W - 1, H - 1, g.PANEL)
    for y in range(H):  # sparse 1px noise, deterministic (same pattern as the machine GUIs)
        for x in range(W):
            if (x * 7 + y * 13 + (x * y) % 5) % 23 == 0:
                img.putpixel((x, y), g.PANEL_NOISE)
    g.bevel(img, 0, 0, W - 1, H - 1, g.BEVEL_DARK, g.BEVEL_DARK)
    g.bevel(img, 1, 1, W - 2, H - 2, g.BEVEL_LIGHT, g.BEVEL_DARK)
    g.bevel(img, 2, 2, W - 3, H - 3, g.FRAME, g.FRAME)
    for x in (1, W - 3):
        for y0 in (30, 120):
            g.glow_strip(img, x, y0, y0 + 16)
        for y in (18, 160):
            g.indicator(img, x, y)
    # title bar: cyan lamp left, amber status lights right
    x0, y0, x1, y1 = TITLE_BAR
    g.recess(img, TITLE_BAR, g.BEVEL_DARK)
    g.rect(img, x0 + 1, y1, x1, y1, g.DEEP)
    g.rect(img, 8, 6, 13, 11, g.GLOW_DARK)
    g.rect(img, 9, 7, 12, 10, g.CYAN)
    g.rect(img, 10, 8, 11, 9, g.CYAN_LIGHT)
    for i in range(3):
        g.rect(img, W - 18 + i * 3, 7, W - 17 + i * 3, 11, g.AMBER_DARK)
        g.rect(img, W - 18 + i * 3, 8, W - 17 + i * 3, 10, g.AMBER)
    # storage area (the grid fills it) and the seam between the halves
    g.recess(img, AREA_BOX, g.AREA)
    g.rect(img, 173, 18, 174, H - 6, g.BEVEL_DARK)
    g.rect(img, 175, 18, 175, H - 6, g.FRAME)
    # inventory header
    x0, y0, x1, y1 = DIVIDER
    g.rect(img, x0, y0, x1, y1, g.BEVEL_DARK)
    g.rect(img, x0, y0, x1, y0, g.DECOR_LIGHT)
    g.rect(img, x0, y1, x1, y1, g.DECOR_LIGHT)
    mid = (y0 + y1) // 2
    for x in range(240, W - 22, 2):
        img.putpixel((x, mid), g.DECOR)
    g.rect(img, W - 19, mid - 1, W - 14, mid, g.CYAN)
    img.putpixel((W - 17, mid - 1), g.CYAN_LIGHT)
    # faint machinery under the hotbar (decoration only, darker than items / text)
    g.recess(img, DECOR_BOX, g.AREA)
    bx0, by0, bx1, by1 = DECOR_BOX
    g.rect(img, bx0 + 4, by0 + 34, bx1 - 4, by0 + 36, g.DECOR_PIPE)
    for x in range(bx0 + 10, bx1 - 4, 22):
        g.rect(img, x, by0 + 33, x + 1, by0 + 37, g.DECOR)
    g.gear(img, bx0 + 40, by0 + 35, 12)
    g.gear(img, bx1 - 40, by0 + 35, 9)
    for x, y in ((0, 0), (W - 8, 0), (0, H - 8), (W - 8, H - 8)):
        g.rivet_plate(img, x, y)


def build():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    panel(img)
    for row in range(ROWS):
        for col in range(COLS):
            g.slot(img, GRID_X + col * 18, GRID_Y + row * 18)
    for row in range(3):
        for col in range(9):
            g.slot(img, INV_X + col * 18, INV_Y + row * 18)
    for col in range(9):
        g.slot(img, INV_X + col * 18, HOTBAR_Y)
    return img


def main():
    os.makedirs(g.OUT, exist_ok=True)
    path = os.path.join(g.OUT, "large_locker.png")
    build().save(path)
    print("wrote", os.path.relpath(path, os.path.join(g.HERE, "..")))


if __name__ == "__main__":
    main()
