"""GUI textures of the industrial blocks (features I01, I02), drawn procedurally with Pillow.

Writes src/main/resources/assets/abyssia/textures/gui/industrial_<layout>.png (256x256): a deep-sea base console
176x166 panel (ui_kit.py frame, from the UI01 design tiles) with slot frames, the player inventory, an empty progress arrow / flame and the energy bar frame, plus
the "full" sprites at x=176: flame 14x14 at y=0, progress arrow 24x17 at y=14, energy bar fill 12x52 at y=31.

The numbers must match com.abyssia.industry.GuiLayout.  Run:  python tools/industrial_gui.py
"""
import math
import os

from PIL import Image

import ui_kit as k

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "abyssia", "textures", "gui")

W, H = 176, 166
ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H = 9, 17, 12, 52
SPRITE_X, FLAME_V, ARROW_V, ENERGY_V = 176, 0, 14, 31
ARROW_W, ARROW_H, FLAME = 24, 17, 14

# layout -> inputs, output, arrow, flame, optional reagent slot and rare output slots (same as GuiLayout)
LAYOUTS = {
    "machine": dict(inputs=[(56, 35)], output=(116, 35), arrow=(80, 34), flame=None),
    "alloy": dict(inputs=[(30, 35), (48, 35), (66, 35)], output=(124, 35), arrow=(90, 34), flame=None),
    "generator": dict(inputs=[(40, 53)], output=None, arrow=None, flame=(41, 35)),
    "energy": dict(inputs=[], output=None, arrow=None, flame=None),
    # I02 selective leaching separator: input over the reagent slot, big main output, a column of three rare outputs
    "leaching": dict(inputs=[(36, 21)], output=(94, 30), arrow=(60, 30), flame=None,
                     reagent=(36, 41), rares=[(126, 17), (126, 35), (126, 53)]),
    # Excavator: no inputs, output slot, arrow, energy bar only
    "excavator": dict(inputs=[], output=(116, 53), arrow=(80, 34), flame=None),
}

# palette: ui_kit (UI01).  Functional colours (amber, flame) stay.
def _c(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


AREA = k.ABYSS
DECOR = k.NAVY
EMPTY = k.NAVY_MID
CYAN = k.CYAN
CYAN_LIGHT = k.CYAN_PALE
GLOW_DARK = k.CYAN_DEEP
AMBER_DARK = _c("#6b4315")
AMBER = _c("#e8a020")
AMBER_LIGHT = _c("#ffb83d")
FLAME_EDGE = _c("#8a4a12")
FLAME_ORANGE = _c("#ff7a1a")
FLAME_LIGHT = _c("#e8a020")
FLAME_CORE = _c("#fff0b0")

TITLE_BAR = (7, 6, W - 8, 15)
AREA_BOX = (7, 16, W - 8, 69)
DIVIDER = (7, 70, W - 8, 81)

rect, bevel = k.rect, k.bevel
slot, big_slot, rare_slot = k.slot, k.big_slot, k.rare_slot


def title_bar(img, box, lights=True, light_x=None):
    """Title strip: sunken, cyan gem left, optional amber status lights right."""
    x0, y0, x1, y1 = box
    k.inset(img, box, k.ABYSS)
    k.gem(img, x0 + 2, y0 + 2, k.CYAN)
    if lights:
        lx = light_x if light_x is not None else x1 - 10
        for i in range(3):
            rect(img, lx + i * 3, y0 + 1, lx + 1 + i * 3, y1 - 1, AMBER_DARK)
            rect(img, lx + i * 3, y0 + 2, lx + 1 + i * 3, y1 - 2, AMBER)


def panel(img, title_lights=True):
    k.frame(img, 0, 0, W - 1, H - 1)
    title_bar(img, TITLE_BAR, title_lights, 158)
    # machine area with a faint vertical pipe on the right (kept clear of the status text)
    k.inset(img, AREA_BOX, AREA)
    ax0, ay0, ax1, ay1 = AREA_BOX
    rect(img, ax1 - 9, ay0 + 3, ax1 - 7, ay1 - 3, DECOR)
    for y in range(ay0 + 6, ay1 - 3, 14):
        rect(img, ax1 - 10, y, ax1 - 6, y + 1, k.NAVY_MID)
    # divider / inventory header
    x0, y0, x1, y1 = DIVIDER
    k.inset(img, DIVIDER, k.ABYSS)
    for x in range(64, 156, 2):
        img.putpixel((x, 76), k.NAVY_MID)
    rect(img, 159, 75, 164, 76, CYAN)
    img.putpixel((161, 75), CYAN_LIGHT)


def gear(img, cx, cy, r):
    """Faint gear behind the progress arrow (decoration only)."""
    for y in range(cy - r - 2, cy + r + 3):
        for x in range(cx - r - 2, cx + r + 3):
            d = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx)
            tooth = math.cos(a * 8) > 0.35 and r <= d <= r + 2
            if r - 3 <= d <= r or tooth or d <= 2:
                img.putpixel((x, y), DECOR)  # darker than the empty arrow drawn over it


def droplet(img, x, y):
    """Label-free reagent mark (7x10, top-left at x, y) left of the reagent slot."""
    cx, cy = x + 3, y + 6
    for py in range(y, y + 10):
        for px in range(x, x + 7):
            inside = math.hypot(px - cx, py - cy) <= 3.2 or (py <= cy and abs(px - cx) <= (py - y) * 0.5)
            if inside:
                img.putpixel((px, py), EMPTY)
    img.putpixel((cx - 1, cy), k.BLUE)
    img.putpixel((cx - 1, cy + 1), k.BLUE)


def branch(img, output, rares):
    """Faint pipe from the main output frame to the rare slots."""
    ox, oy = output
    vx = ox + 25
    ys = [ry + 7 for _, ry in rares]
    rect(img, ox + 21, oy + 7, vx + 1, oy + 8, DECOR)
    rect(img, vx, min(ys), vx + 1, max(ys) + 1, DECOR)
    for (rx, _), ry in zip(rares, ys):
        rect(img, vx + 2, ry, rx - 2, ry + 1, DECOR)


def arrow_pixels():
    out = []
    for y in range(ARROW_H):
        for x in range(ARROW_W):
            shaft = x <= 15 and 6 <= y <= 10
            head = 15 <= x <= 23 and abs(y - 8) <= 23 - x
            if shaft or head:
                out.append((x, y))
    return out


def flame_pixels():
    out = []
    for y in range(FLAME):
        t = y / (FLAME - 1)
        half = 1.0 + 5.5 * (t ** 0.8) if t < 0.8 else 6.5 - (t - 0.8) * 10
        for x in range(FLAME):
            if abs(x - 6.5) <= half:
                out.append((x, y))
    return out


def energy_frame(img, x=ENERGY_X, y=ENERGY_Y, w=ENERGY_W, h=ENERGY_H):
    k.gauge_frame(img, x, y, w, h)


def sprites(img):
    pixels = set(flame_pixels())
    for x, y in pixels:
        edge = any((x + dx, y + dy) not in pixels for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        t = y / (FLAME - 1)
        core = abs(x - 6.5) <= 1.5 and t > 0.55
        color = FLAME_EDGE if edge else FLAME_CORE if core else FLAME_LIGHT if t > 0.5 else FLAME_ORANGE
        img.putpixel((SPRITE_X + x, FLAME_V + y), color)
    for x, y in arrow_pixels():
        edge = y in (0, ARROW_H - 1) or (x > 15 and abs(y - 8) == 23 - x) or y in (6, 10) and x < 15
        img.putpixel((SPRITE_X + x, ARROW_V + y), GLOW_DARK if edge else CYAN_LIGHT if y == 8 else CYAN)
    for y in range(ENERGY_H):
        for x in range(ENERGY_W):
            if x in (0, ENERGY_W - 1) or y % 6 == 5:
                color = GLOW_DARK
            elif x in (5, 6):
                color = CYAN_LIGHT
            else:
                color = CYAN
            img.putpixel((SPRITE_X + x, ENERGY_V + y), color)


def build(layout):
    spec = LAYOUTS[layout]
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    panel(img, title_lights=layout != "leaching")
    energy_frame(img)
    for x, y in spec["inputs"]:
        slot(img, x, y)
    if spec.get("reagent"):
        rx, ry = spec["reagent"]
        slot(img, rx, ry)
        droplet(img, rx - 11, ry + 3)
    if spec.get("rares"):
        branch(img, spec["output"], spec["rares"])
        for x, y in spec["rares"]:
            rare_slot(img, x, y)
    if spec["output"]:
        big_slot(img, *spec["output"])
    if spec["arrow"]:
        ax, ay = spec["arrow"]
        gear(img, ax + 12, ay + 8, 9)
        for x, y in arrow_pixels():
            img.putpixel((ax + x, ay + y), EMPTY)
    if spec["flame"]:
        fx, fy = spec["flame"]
        for x, y in flame_pixels():
            img.putpixel((fx + x, fy + y), EMPTY)
    for row in range(3):
        for col in range(9):
            slot(img, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(img, 8 + col * 18, 142)
    sprites(img)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for layout in LAYOUTS:
        path = os.path.join(OUT, f"industrial_{layout}.png")
        build(layout).save(path)
        print("wrote", os.path.relpath(path, os.path.join(HERE, "..")))


if __name__ == "__main__":
    main()
