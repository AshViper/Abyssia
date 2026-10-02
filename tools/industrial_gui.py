"""GUI textures of the industrial blocks (features I01, I02), drawn procedurally with Pillow.

Writes src/main/resources/assets/abyssia/textures/gui/industrial_<layout>.png (256x256): a deep-sea base console
176x166 panel (ChatGPT design: inbox/specs/I01-gui-spec.md) with slot frames, the player inventory, an empty progress arrow / flame and the energy bar frame, plus
the "full" sprites at x=176: flame 14x14 at y=0, progress arrow 24x17 at y=14, energy bar fill 12x52 at y=31.

The numbers must match com.abyssia.industry.GuiLayout.  Run:  python tools/industrial_gui.py
"""
import math
import os

from PIL import Image

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
}

# palette: ChatGPT GUI design (inbox/specs/I01-gui-spec.md, mockup inbox/designs/I01-gui-mockup.png)
def _c(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


PANEL = _c("#1b2229")
PANEL_NOISE = _c("#232a33")
FRAME = _c("#343e4a")
BEVEL_LIGHT = _c("#6c7c8a")
BEVEL_DARK = _c("#14181e")
DEEP = _c("#0d1115")
PLATE = _c("#232a33")
RIVET = _c("#6c7c8a")
GLOW_DARK = _c("#1d6670")
CYAN = _c("#3ee6f0")
CYAN_LIGHT = _c("#b8f8ff")
AMBER_DARK = _c("#6b4315")
AMBER = _c("#e8a020")
AMBER_LIGHT = _c("#ffb83d")
AREA = _c("#232a33")
DECOR = _c("#343e4a")
DECOR_PIPE = _c("#2b353f")
DECOR_LIGHT = _c("#4a5866")
SLOT_BG = _c("#14181e")
SLOT_LIGHT = _c("#6c7c8a")
SLOT_DARK = _c("#232a33")
SLOT_SHADOW = _c("#0f1419")
OUTPUT_EDGE = _c("#9aa8b4")
EMPTY = _c("#343e4a")
TICK = _c("#4a5866")
FLAME_EDGE = _c("#8a4a12")
FLAME_ORANGE = _c("#ff7a1a")
FLAME_LIGHT = _c("#e8a020")
FLAME_CORE = _c("#fff0b0")

TITLE_BAR = (5, 3, W - 6, 15)
AREA_BOX = (5, 17, W - 6, 69)
DIVIDER = (5, 70, W - 6, 81)


def rect(img, x0, y0, x1, y1, color):
    """Filled rectangle, inclusive corners."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), color)


def bevel(img, x0, y0, x1, y1, top_left, bottom_right):
    rect(img, x0, y0, x1, y0, top_left)
    rect(img, x0, y0, x0, y1, top_left)
    rect(img, x0 + 1, y1, x1, y1, bottom_right)
    rect(img, x1, y0 + 1, x1, y1, bottom_right)


def recess(img, box, fill):
    """Sunken area: dark top/left, frame-coloured bottom/right."""
    x0, y0, x1, y1 = box
    rect(img, x0, y0, x1, y1, fill)
    bevel(img, x0, y0, x1, y1, BEVEL_DARK, FRAME)


def rivet_plate(img, x, y):
    rect(img, x, y, x + 7, y + 7, PLATE)
    bevel(img, x, y, x + 7, y + 7, FRAME, BEVEL_DARK)
    rect(img, x + 3, y + 3, x + 4, y + 4, RIVET)
    img.putpixel((x + 5, y + 5), BEVEL_DARK)


def glow_strip(img, x, y0, y1):
    """2px light strip set into the side frame."""
    rect(img, x, y0, x + 1, y1, GLOW_DARK)
    rect(img, x, y0 + 1, x, y1 - 1, CYAN)
    img.putpixel((x, y0 + 2), CYAN_LIGHT)


def indicator(img, x, y):
    rect(img, x, y, x + 1, y + 2, AMBER_DARK)
    img.putpixel((x, y + 1), AMBER)
    img.putpixel((x + 1, y + 1), AMBER_LIGHT)


def panel(img, title_lights=True):
    rect(img, 0, 0, W - 1, H - 1, PANEL)
    for y in range(H):  # sparse 1px noise, deterministic
        for x in range(W):
            if (x * 7 + y * 13 + (x * y) % 5) % 23 == 0:
                img.putpixel((x, y), PANEL_NOISE)
    # outer frame: dark rim, light/dark bevel, frame body
    bevel(img, 0, 0, W - 1, H - 1, BEVEL_DARK, BEVEL_DARK)
    bevel(img, 1, 1, W - 2, H - 2, BEVEL_LIGHT, BEVEL_DARK)
    bevel(img, 2, 2, W - 3, H - 3, FRAME, FRAME)
    for x in (1, W - 3):
        glow_strip(img, x, 26, 42)
        glow_strip(img, x, 112, 128)
        indicator(img, x, 18)
        indicator(img, x, 146)
    # title bar with a cyan lamp and amber status lights
    x0, y0, x1, y1 = TITLE_BAR
    recess(img, TITLE_BAR, BEVEL_DARK)
    rect(img, x0 + 1, y1, x1, y1, DEEP)
    rect(img, 8, 6, 13, 11, GLOW_DARK)
    rect(img, 9, 7, 12, 10, CYAN)
    rect(img, 10, 8, 11, 9, CYAN_LIGHT)
    for i in range(3 if title_lights else 0):  # off where the English title is long enough to reach them
        rect(img, 158 + i * 3, 7, 159 + i * 3, 11, AMBER_DARK)
        rect(img, 158 + i * 3, 8, 159 + i * 3, 10, AMBER)
    # machine area with a faint vertical pipe on the right (kept clear of the status text)
    recess(img, AREA_BOX, AREA)
    ax0, ay0, ax1, ay1 = AREA_BOX
    rect(img, ax1 - 9, ay0 + 3, ax1 - 7, ay1 - 3, DECOR_PIPE)
    for y in range(ay0 + 6, ay1 - 3, 14):
        rect(img, ax1 - 10, y, ax1 - 6, y + 1, DECOR)
    # divider / inventory header
    x0, y0, x1, y1 = DIVIDER
    rect(img, x0, y0, x1, y1, BEVEL_DARK)
    rect(img, x0, y0, x1, y0, DECOR_LIGHT)
    rect(img, x0, y1, x1, y1, DECOR_LIGHT)
    for x in range(64, 156, 2):
        img.putpixel((x, 76), DECOR)
    rect(img, 159, 75, 164, 76, CYAN)
    img.putpixel((161, 75), CYAN_LIGHT)
    for x, y in ((0, 0), (W - 8, 0), (0, H - 8), (W - 8, H - 8), (0, 72), (W - 8, 72)):
        rivet_plate(img, x, y)


def gear(img, cx, cy, r):
    """Faint gear behind the progress arrow (decoration only)."""
    for y in range(cy - r - 2, cy + r + 3):
        for x in range(cx - r - 2, cx + r + 3):
            d = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx)
            tooth = math.cos(a * 8) > 0.35 and r <= d <= r + 2
            if r - 3 <= d <= r or tooth or d <= 2:
                img.putpixel((x, y), DECOR_PIPE)  # darker than the empty arrow drawn over it


def slot(img, x, y):
    """18x18 frame around an item at (x, y)."""
    rect(img, x - 1, y - 1, x + 16, y + 16, SLOT_BG)
    bevel(img, x - 1, y - 1, x + 16, y + 16, SLOT_DARK, SLOT_LIGHT)
    rect(img, x, y, x + 15, y, SLOT_SHADOW)
    rect(img, x, y, x, y + 15, SLOT_SHADOW)


def big_slot(img, x, y):
    """26x26 output frame around an item at (x, y)."""
    rect(img, x - 5, y - 5, x + 20, y + 20, SLOT_BG)
    bevel(img, x - 5, y - 5, x + 20, y + 20, OUTPUT_EDGE, OUTPUT_EDGE)
    bevel(img, x - 4, y - 4, x + 19, y + 19, SLOT_DARK, SLOT_LIGHT)
    rect(img, x - 3, y - 3, x + 18, y - 3, SLOT_SHADOW)
    rect(img, x - 3, y - 3, x - 3, y + 18, SLOT_SHADOW)
    for cx, cy in ((x - 5, y - 5), (x + 20, y - 5), (x - 5, y + 20), (x + 20, y + 20)):
        img.putpixel((cx, cy), GLOW_DARK)


def rare_slot(img, x, y):
    """18x18 rare output frame: a normal slot with the output edge and glowing corners."""
    slot(img, x, y)
    bevel(img, x - 1, y - 1, x + 16, y + 16, OUTPUT_EDGE, OUTPUT_EDGE)
    rect(img, x, y, x + 15, y, SLOT_SHADOW)
    rect(img, x, y, x, y + 15, SLOT_SHADOW)
    for cx, cy in ((x - 1, y - 1), (x + 16, y - 1), (x - 1, y + 16), (x + 16, y + 16)):
        img.putpixel((cx, cy), GLOW_DARK)


def droplet(img, x, y):
    """Label-free reagent mark (7x10, top-left at x, y) left of the reagent slot."""
    cx, cy = x + 3, y + 6
    for py in range(y, y + 10):
        for px in range(x, x + 7):
            inside = math.hypot(px - cx, py - cy) <= 3.2 or (py <= cy and abs(px - cx) <= (py - y) * 0.5)
            if inside:
                img.putpixel((px, py), EMPTY)
    img.putpixel((cx - 1, cy), DECOR_LIGHT)
    img.putpixel((cx - 1, cy + 1), DECOR_LIGHT)


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


def energy_frame(img):
    x0, y0 = ENERGY_X - 1, ENERGY_Y - 1
    x1, y1 = ENERGY_X + ENERGY_W, ENERGY_Y + ENERGY_H
    rect(img, x0, y0, x1, y1, BEVEL_LIGHT)          # tube frame
    rect(img, ENERGY_X, ENERGY_Y, x1 - 1, y1 - 1, SLOT_BG)
    rect(img, x0 + 1, y0, x1 - 1, y0, OUTPUT_EDGE)  # top cap highlight
    for y in range(ENERGY_Y + 5, ENERGY_Y + ENERGY_H, 6):
        rect(img, ENERGY_X, y, ENERGY_X + 1, y, TICK)
        rect(img, x1 - 2, y, x1 - 1, y, TICK)


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
