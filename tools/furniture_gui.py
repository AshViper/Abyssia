"""GUI backgrounds of the wall workbench and the submarine upgrade screen (UI01), drawn with ui_kit.py.

Writes src/main/resources/assets/abyssia/textures/gui/:
  wall_workbench.png     176x166  3x3 grid at (30,17) step 18, result big slot at (124,35), arrow (90,34), energy gauge
                                  frame around (155,17) 12x32, charge slot (152,53), inventory (8,84), hotbar (8,142)
  submarine_upgrade.png  176x143  title bar, upgrade slot row at (44,30) step 18 (5 slots), inventory y=61, hotbar y=119
The numbers must match furniture.WallWorkbenchMenu / vehicle.SubmarineUpgradeMenu.  Run:  python tools/furniture_gui.py
"""
import os

from PIL import Image

import industrial_gui as g
import ui_kit as k

UPGRADE_SLOTS = 5  # SubmarineUpgrades.SLOTS


def player_inventory(img, y_inv, y_hot):
    for row in range(3):
        for col in range(9):
            k.slot(img, 8 + col * 18, y_inv + row * 18)
    for col in range(9):
        k.slot(img, 8 + col * 18, y_hot)


def wall_workbench():
    img = Image.new("RGBA", (176, 166), (0, 0, 0, 0))
    k.frame(img, 0, 0, 175, 165)
    k.inset(img, (7, 16, 168, 70), g.AREA)
    for row in range(3):
        for col in range(3):
            k.slot(img, 30 + col * 18, 17 + row * 18)
    k.big_slot(img, 124, 35)
    ax, ay = 90, 34
    for x, y in g.arrow_pixels():
        img.putpixel((ax + x, ay + y), g.EMPTY)
    k.gauge_frame(img, 155, 17, 12, 32)
    k.slot(img, 152, 53)
    player_inventory(img, 84, 142)
    return img


def submarine_upgrade():
    img = Image.new("RGBA", (176, 143), (0, 0, 0, 0))
    k.frame(img, 0, 0, 175, 142)
    g.title_bar(img, (7, 6, 168, 15), True, 158)
    k.inset(img, (7, 28, 168, 48), g.AREA)
    for i in range(UPGRADE_SLOTS):
        k.slot(img, 44 + i * 18, 30)
    player_inventory(img, 61, 119)
    return img


def main():
    for name, build in (("wall_workbench", wall_workbench), ("submarine_upgrade", submarine_upgrade)):
        path = os.path.join(g.OUT, name + ".png")
        build().save(path)
        print("wrote", os.path.relpath(path, os.path.join(g.HERE, "..")))


if __name__ == "__main__":
    main()
