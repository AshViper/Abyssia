"""GUI texture of the large locker (H04 follow-up: 81 slots, own screen), drawn procedurally with Pillow.

Writes src/main/resources/assets/abyssia/textures/gui/large_locker.png (350x186, blitted with explicit texture size):
the deep-sea console panel of the industrial GUIs (ui_kit.py frame / slot helpers via industrial_gui.py, UI01), side by side: 9x9 storage grid left, player inventory + hotbar right (fits 426x240).

The numbers must match com.abyssia.furniture.LargeLockerMenu / client.LargeLockerScreen.
Run:  python tools/locker_gui.py
"""
import os

from PIL import Image

import industrial_gui as g
import ui_kit as k

W, H = 350, 186
COLS, ROWS = 9, 9
GRID_X, GRID_Y = 8, 18            # first storage slot (item position), left half
INV_X, INV_LABEL_Y = 181, 18      # player inventory, right half, under its own label
INV_Y, HOTBAR_Y = 30, 88
TITLE_BAR = (7, 6, W - 8, 15)
AREA_BOX = (7, 16, 170, H - 7)    # storage grid
DIVIDER = (177, 16, W - 8, 27)    # "Inventory" header
DECOR_BOX = (177, 108, W - 8, H - 7)  # empty space under the hotbar: faint machinery


def panel(img):
    k.frame(img, 0, 0, W - 1, H - 1)
    g.title_bar(img, TITLE_BAR, True, W - 18)
    # storage area (the grid fills it) and the seam between the halves
    k.inset(img, AREA_BOX, g.AREA)
    g.rect(img, 172, 16, 174, H - 7, k.ABYSS)
    g.rect(img, 175, 16, 175, H - 7, k.STEEL_DARK)
    # inventory header
    x0, y0, x1, y1 = DIVIDER
    k.inset(img, DIVIDER, k.ABYSS)
    mid = (y0 + y1) // 2
    for x in range(240, W - 22, 2):
        img.putpixel((x, mid), k.NAVY_MID)
    g.rect(img, W - 19, mid - 1, W - 14, mid, g.CYAN)
    img.putpixel((W - 17, mid - 1), g.CYAN_LIGHT)
    # faint machinery under the hotbar (decoration only, darker than items / text)
    k.inset(img, DECOR_BOX, g.AREA)
    bx0, by0, bx1, by1 = DECOR_BOX
    g.rect(img, bx0 + 4, by0 + 34, bx1 - 4, by0 + 36, g.DECOR)
    for x in range(bx0 + 10, bx1 - 4, 22):
        g.rect(img, x, by0 + 33, x + 1, by0 + 37, k.NAVY_MID)
    g.gear(img, bx0 + 40, by0 + 35, 12)
    g.gear(img, bx1 - 40, by0 + 35, 9)


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
