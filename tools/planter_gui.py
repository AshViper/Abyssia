"""GUI texture of the hydro planter (PL01, spec inbox/specs/PL01-hydro-planter.md), drawn procedurally with Pillow.

Writes src/main/resources/assets/abyssia/textures/gui/hydro_planter.png (256x256 sheet): the 176x166 deep-sea console
panel of the industrial GUIs (palette / helpers from industrial_gui.py) with one seedling slot, an empty growth arrow
and the player inventory, plus the "full" growth arrow sprite 24x17 at x=176, y=14 (same place as the industrial sheets).

The numbers must match com.abyssia.furniture.HydroPlanterMenu / client.HydroPlanterScreen.
Run:  python tools/planter_gui.py
"""
import os

from PIL import Image

import industrial_gui as g

SEED = (44, 35)
ARROW = (70, 34)


def build():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    g.panel(img)
    g.slot(img, *SEED)
    ax, ay = ARROW
    g.gear(img, ax + 12, ay + 8, 9)
    for x, y in g.arrow_pixels():
        img.putpixel((ax + x, ay + y), g.EMPTY)
    for row in range(3):
        for col in range(9):
            g.slot(img, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        g.slot(img, 8 + col * 18, 142)
    for x, y in g.arrow_pixels():  # full arrow sprite
        edge = y in (0, g.ARROW_H - 1) or (x > 15 and abs(y - 8) == 23 - x) or y in (6, 10) and x < 15
        img.putpixel((g.SPRITE_X + x, g.ARROW_V + y), g.GLOW_DARK if edge else g.CYAN_LIGHT if y == 8 else g.CYAN)
    return img


def main():
    os.makedirs(g.OUT, exist_ok=True)
    path = os.path.join(g.OUT, "hydro_planter.png")
    build().save(path)
    print("wrote", os.path.relpath(path, os.path.join(g.HERE, "..")))


if __name__ == "__main__":
    main()
