"""Shared deep-sea UI kit (feature UI01): palette + helpers built from the traced design tiles in tools/ui_kit/src/.

Design origin = guide book frame (gui/guide/book_bg.png): navy riveted metal, steel brackets, 1px light-blue bevel,
cyan gems, coral/bubble ornaments.  The tiles in ui_kit/src are ChatGPT art, traced; never overwrite them.
Only integer crops / mirrors of the tiles are used (no resampling).

Public API (all draw into a Pillow RGBA image, coordinates inclusive unless noted):
  palette: ABYSS NAVY_DEEP NAVY NAVY_MID BLUE BLUE_LIGHT STEEL_BLUE PALE_BLUE STEEL_DARK STEEL STEEL_LIGHT HIGHLIGHT
           CYAN_DEEP CYAN CYAN_GLOW CYAN_PALE  (RGBA tuples)
  rect(img, x0, y0, x1, y1, color)            filled rectangle
  bevel(img, x0, y0, x1, y1, top_left, bottom_right)   1px outline lit from the top-left
  frame(img, x0, y0, x1, y1, gem=True)        panel: bracket corners, edge bands, flat navy interior (box = whole panel)
  inset(img, box, fill=ABYSS)                 sunken area (dark top/left, steel bottom/right)
  slot(img, x, y)                             18x18 recessed slot around an item at (x, y)  (box x-1..x+16)
  big_slot(img, x, y)                         26x26 output slot around an item at (x, y)     (box x-5..x+20)
  rare_slot(img, x, y)                        18x18 slot with a steel rim and cyan corner pixels
  gem(img, x, y, color=CYAN)                  5x5 cyan gem (top-left at x, y)
  gauge_frame(img, x, y, w, h)                tube frame 1px outside a w x h gauge at (x, y)
  ornament(img, x, y, corner="tl")            coral/bubble ornament (transparent over the background), 12x14 at x, y
  button(w, h, state="normal")                new w x h RGBA image, nine-sliced from the button tiles (normal|hover|disabled)
  tile(name)                                  raw design tile (RGBA) by file stem, e.g. "ui01a_slot"
  to_disabled(img)                            darker, cyan-free copy of an image (palette-mapped)
  FRAME_L, FRAME_T                            frame band thickness: left/right 7, top/bottom 6 (content starts inside)
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "ui_kit", "src")


def _c(h):
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


ABYSS = _c("021123")
NAVY_DEEP = _c("021c38")
NAVY = _c("0b2a49")
NAVY_MID = _c("133c62")
BLUE = _c("175482")
BLUE_LIGHT = _c("257dae")
STEEL_BLUE = _c("528fbb")
PALE_BLUE = _c("6eafd3")
STEEL_DARK = _c("476c8c")
STEEL = _c("7c9ab2")
STEEL_LIGHT = _c("a9c0d0")
HIGHLIGHT = _c("c1f3f8")
CYAN_DEEP = _c("12a6da")
CYAN = _c("43bcdc")
CYAN_GLOW = _c("58eef9")
CYAN_PALE = _c("87ddf8")

FRAME_L, FRAME_T = 7, 6

_cache = {}


def tile(name):
    if name not in _cache:
        _cache[name] = Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")
    return _cache[name]


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), color)


def bevel(img, x0, y0, x1, y1, top_left, bottom_right):
    rect(img, x0, y0, x1, y0, top_left)
    rect(img, x0, y0, x0, y1, top_left)
    rect(img, x0 + 1, y1, x1, y1, bottom_right)
    rect(img, x1, y0 + 1, x1, y1, bottom_right)


def _put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def frame(img, x0, y0, x1, y1, gem=True):
    """Panel frame over the box (x0, y0)..(x1, y1): interior, edge bands, mirrored corner brackets, top centre gem."""
    corner, edge = tile("ui01a_corner"), tile("ui01a_edge")
    rect(img, x0, y0, x1, y1, NAVY_DEEP)
    w, h = x1 - x0 + 1, y1 - y0 + 1
    top = [corner.getpixel((22, r)) for r in range(8)]      # flat top band (steel/blue bevel, dark line)
    left = [corner.getpixel((c, 29)) for c in range(FRAME_L)]  # flat left band
    for x in range(w):
        for r in range(FRAME_T):
            img.putpixel((x0 + x, y0 + r), top[r])
            img.putpixel((x0 + x, y1 - r), top[r])
    for y in range(h):
        for c in range(FRAME_L):
            img.putpixel((x0 + c, y0 + y), left[c])
            img.putpixel((x1 - c, y0 + y), left[c])
    if gem and w >= 48:
        gx = x0 + (w - 16) // 2
        for r in range(FRAME_T):
            for c in range(16):
                px = edge.getpixel((8 + c, r))
                img.putpixel((gx + c, y0 + r), px)
                img.putpixel((gx + c, y1 - r), px)
    # corner brackets: L-shaped crop of the corner tile, mirrored to all four corners
    for sx, sy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        for y in range(13):
            for x in range(15):
                if not ((y < 7 and x < 15) or (x < 8 and y < 13)):
                    continue
                px = corner.getpixel((x, y))
                if px[3] == 0:
                    continue
                tx = x1 - x if sx else x0 + x
                ty = y1 - y if sy else y0 + y
                img.putpixel((tx, ty), px)


def inset(img, box, fill=ABYSS):
    """Sunken area: dark top/left rim, steel bottom/right rim."""
    x0, y0, x1, y1 = box
    rect(img, x0, y0, x1, y1, fill)
    bevel(img, x0, y0, x1, y1, ABYSS, STEEL_DARK)


def slot(img, x, y):
    """18x18 recessed slot: 1px dark top/left, light bottom/right, navy inside (item area x..x+15, y..y+15)."""
    bx, by = x - 1, y - 1
    rect(img, bx, by, bx + 17, by + 17, NAVY)
    bevel(img, bx, by, bx + 17, by + 17, ABYSS, STEEL_BLUE)
    _put(img, bx + 17, by, STEEL_DARK)
    _put(img, bx, by + 17, STEEL_DARK)


def rare_slot(img, x, y):
    slot(img, x, y)
    bevel(img, x - 1, y - 1, x + 16, y + 16, STEEL_LIGHT, STEEL_LIGHT)
    for cx, cy in ((x - 1, y - 1), (x + 16, y - 1), (x - 1, y + 16), (x + 16, y + 16)):
        _put(img, cx, cy, CYAN_DEEP)


def big_slot(img, x, y):
    """26x26 output frame around an item at (x, y)."""
    bx, by = x - 5, y - 5
    rect(img, bx, by, bx + 25, by + 25, NAVY)
    bevel(img, bx, by, bx + 25, by + 25, STEEL, STEEL)
    bevel(img, bx + 1, by + 1, bx + 24, by + 24, ABYSS, STEEL_BLUE)
    for cx, cy in ((bx, by), (bx + 25, by), (bx, by + 25), (bx + 25, by + 25)):
        _put(img, cx, cy, CYAN_DEEP)


def gem(img, x, y, color=CYAN):
    """5x5 gem, top-left at (x, y)."""
    rows = ["..d..", ".dcd.", "dcgcd", ".dcd.", "..d.."]
    colors = {"d": CYAN_DEEP, "c": color, "g": CYAN_GLOW}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                _put(img, x + i, y + j, colors[ch])


def gauge_frame(img, x, y, w, h):
    """Tube frame 1px outside the w x h gauge at (x, y); the inside is left to the caller's fill."""
    x0, y0, x1, y1 = x - 1, y - 1, x + w, y + h
    rect(img, x0, y0, x1, y1, STEEL_DARK)
    rect(img, x, y, x1 - 1, y1 - 1, ABYSS)
    rect(img, x0 + 1, y0, x1 - 1, y0, STEEL_LIGHT)
    for yy in range(y + 5, y + h, 6):
        rect(img, x, yy, x + 1, yy, NAVY_MID)
        rect(img, x1 - 2, yy, x1 - 1, yy, NAVY_MID)


def ornament(img, x, y, corner="tl"):
    """Coral/bubble ornament from the fill tile (only non-background pixels), 12x14 (tl) or 15x14 (br)."""
    fill = tile("ui01a_fill")
    box = (0, 0, 12, 14) if corner in ("tl", "tr") else (17, 17, 32, 31)
    crop = fill.crop(box)
    if corner in ("tr", "bl"):
        crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
    for j in range(crop.height):
        for i in range(crop.width):
            px = crop.getpixel((i, j))
            if px != NAVY_DEEP and px[3]:
                _put(img, x + i, y + j, px)


_DISABLED = {}


def _disabled_map():
    if _DISABLED:
        return _DISABLED
    pairs = {
        "021123": "021123", "021c38": "021123", "0b2a49": "021c38", "133c62": "0b2a49", "175482": "133c62",
        "257dae": "133c62", "528fbb": "476c8c", "6eafd3": "476c8c", "476c8c": "34506a", "7c9ab2": "476c8c",
        "a9c0d0": "5a768e", "c1f3f8": "7c9ab2", "12a6da": "175482", "43bcdc": "257dae", "58eef9": "476c8c",
        "87ddf8": "5a768e",
    }
    for k, v in pairs.items():
        _DISABLED[_c(k)] = _c(v)
    return _DISABLED


def to_disabled(img):
    m = _disabled_map()
    out = img.copy()
    for y in range(out.height):
        for x in range(out.width):
            px = out.getpixel((x, y))
            if px[3]:
                out.putpixel((x, y), m.get(px, px))
    return out


def button(w, h, state="normal", border=6):
    """w x h button nine-sliced from the button tiles (integer crops only; corners 1:1, edges/centre repeat)."""
    src = tile("ui01a_button_hover" if state == "hover" else "ui01a_button")
    b = min(border, w // 2, h // 2)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sw, sh = src.size
    for y in range(h):
        sy = y if y < b else sh - (h - y) if y >= h - b else b + (y - b) % (sh - 2 * b)
        for x in range(w):
            sx = x if x < b else sw - (w - x) if x >= w - b else b + (x - b) % (sw - 2 * b)
            if x >= b and x < w - b and y >= b and y < h - b:
                sx, sy2 = sw // 2, sh // 2   # flat centre
            else:
                sy2 = sy
                if b <= x < w - b:
                    sx = sw // 2
                if b <= y < h - b:
                    sy2 = sh // 2
            out.putpixel((x, y), src.getpixel((sx, sy2)))
    return to_disabled(out) if state == "disabled" else out
