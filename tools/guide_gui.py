"""Placeholder art for the guide book (GB01) until the ChatGPT art arrives.  Final PNGs drop in at the same paths/sizes.

Writes (under src/main/resources/assets/abyssia/textures/):
  gui/guide/book_bg.png      256x180  whole spread.  Left page content area x 16..120, right x 136..240, y 16..150
                                      (pages themselves ~ x 12..124 / 132..244, y 10..154); footer buttons sit at y 160.
  gui/guide/buttons.png      60x56    button sheet, every button 20x14.  Columns = state: x 0 normal, 20 hover,
                                      40 disabled.  Rows = button: y 0 contents(TOC), 14 previous, 28 next, 42 close.
  gui/guide/icons/<id>.png   16x16    one per chapter: intro abyss survival resources creatures diving base industry
                                      waypoints encyclopedia
  gui/guide/pages/abyss.png  96x64    sample illustration (any size works; it is scaled to fit)
  item/abyss_guide_book.png  16x16    item icon
Run:  python tools/guide_gui.py
"""
import os
import random

from PIL import Image, ImageDraw

TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "abyssia", "textures")
GUI = os.path.join(TEX, "gui", "guide")

BG = (7, 17, 29, 255)
FRAME = (30, 62, 92, 255)
FRAME_HI = (58, 104, 140, 255)
PAGE = (31, 42, 46, 255)
CYAN = (95, 216, 255, 255)
DEEP = (20, 60, 110, 255)


def save(img, *parts):
    path = os.path.join(*parts)
    if os.path.exists(path) and ("icons" in parts or "pages" in parts or"abyss_guide_book.png" in parts or "book_bg.png" in parts):
        return  # final art from tools/guide_icons.py (GB01 sheet); never overwrite
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def book_bg():
    img = Image.new("RGBA", (256, 180), BG)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 255, 179], outline=FRAME_HI)
    d.rectangle([1, 1, 254, 178], outline=FRAME)
    rnd = random.Random(7)
    for x0, x1 in ((12, 123), (133, 244)):
        d.rectangle([x0, 10, x1, 153], fill=PAGE, outline=FRAME)
        for _ in range(120):
            x, y = rnd.randint(x0 + 1, x1 - 1), rnd.randint(11, 152)
            v = rnd.randint(-5, 5)
            d.point((x, y), fill=(PAGE[0] + v, PAGE[1] + v, PAGE[2] + v, 255))
    d.rectangle([124, 10, 132, 153], fill=(12, 26, 40, 255))
    d.line([128, 10, 128, 153], fill=FRAME)
    d.rectangle([4, 156, 251, 175], fill=(11, 25, 40, 255), outline=FRAME)
    save(img, GUI, "book_bg.png")


def glyph(d, row, ox, oy, col):
    if row == 0:
        for i in range(3):
            d.line([ox + 6, oy + 4 + i * 3, ox + 13, oy + 4 + i * 3], fill=col)
    elif row == 1:
        d.line([ox + 11, oy + 3, ox + 7, oy + 7], fill=col)
        d.line([ox + 7, oy + 7, ox + 11, oy + 10], fill=col)
    elif row == 2:
        d.line([ox + 8, oy + 3, ox + 12, oy + 7], fill=col)
        d.line([ox + 12, oy + 7, ox + 8, oy + 10], fill=col)
    else:
        d.line([ox + 7, oy + 4, ox + 12, oy + 9], fill=col)
        d.line([ox + 12, oy + 4, ox + 7, oy + 9], fill=col)


def buttons():
    img = Image.new("RGBA", (60, 56), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fills = [((16, 40, 64, 255), CYAN), ((26, 64, 98, 255), (200, 245, 255, 255)), ((14, 24, 34, 255), (70, 90, 100, 255))]
    for row in range(4):
        for st, (fill, col) in enumerate(fills):
            ox, oy = st * 20, row * 14
            d.rectangle([ox, oy, ox + 19, oy + 13], fill=fill, outline=FRAME_HI if st != 2 else FRAME)
            glyph(d, row, ox, oy, col)
    save(img, GUI, "buttons.png")


ICON_COL = {"intro": CYAN, "abyss": (60, 120, 200, 255), "survival": (120, 220, 200, 255), "resources": (150, 120, 220, 255),
            "creatures": (255, 160, 120, 255), "diving": (120, 200, 255, 255), "base": (170, 190, 210, 255),
            "industry": (220, 200, 90, 255), "waypoints": (255, 120, 120, 255), "encyclopedia": (200, 160, 100, 255)}


def icons():
    for name, col in ICON_COL.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.rectangle([2, 2, 13, 13], outline=col)
        d.rectangle([5, 5, 10, 10], fill=col)
        d.point((len(name) % 3 + 7, 3), fill=(255, 255, 255, 255))
        save(img, GUI, "icons", name + ".png")


def sample_page():
    img = Image.new("RGBA", (96, 64), DEEP)
    d = ImageDraw.Draw(img)
    for y in range(64):
        d.line([0, y, 95, y], fill=(10 + y // 4, 30 + y // 2, 60 + y, 255))
    d.polygon([(10, 63), (30, 25), (45, 63)], fill=(8, 14, 24, 255))
    d.polygon([(55, 63), (72, 35), (90, 63)], fill=(8, 14, 24, 255))
    d.ellipse([40, 12, 48, 20], fill=CYAN)
    save(img, GUI, "pages", "abyss.png")


def item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 1, 13, 14], fill=(14, 30, 56, 255), outline=(5, 10, 20, 255))
    d.rectangle([2, 12, 13, 14], fill=(200, 190, 160, 255))
    d.line([5, 3, 10, 3], fill=CYAN)
    d.rectangle([6, 5, 9, 9], fill=CYAN)
    d.point((7, 7), fill=(255, 255, 255, 255))
    save(img, TEX, "item", "abyss_guide_book.png")


if __name__ == "__main__":
    book_bg(); buttons(); icons(); sample_page(); item()
