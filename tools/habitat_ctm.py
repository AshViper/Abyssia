"""Connected-texture variants of the habitat shell blocks (CT01).

Derives textures/block/<id>_c<mask>.png (mask 1..15) from the base <id>.png of habitat_floor / trim / wall / ceiling /
light, mechanically: on every connected side the outer frame band (WIDTH table: (x, y) px) is replaced by the mirrored
panel interior next to it (so rivets / seams / the centre carry on); the frame of an unconnected side keeps running
through the corner (sampled from the middle row / column, like the window).  Mask bits as seen from outside the face:
1 = up, 2 = down, 4 = left, 8 = right.  The base is mask 0 and is never rewritten; outputs are copied into the
texture locks.  habitat_window is delegated to habitat_window_ctm.py (glass, own rule).

    python tools/habitat_ctm.py            # write the variants + locks
    python tools/habitat_ctm.py --dry-run  # only list them
"""
import os
import shutil
import sys

from PIL import Image

import habitat_window_ctm

ROOT = os.path.dirname(os.path.abspath(__file__))
TEX = habitat_window_ctm.TEX
LOCK = habitat_window_ctm.LOCK
UP, DOWN, LEFT, RIGHT = 1, 2, 4, 8
# frame band width in px per axis (x = left/right sides, y = top/bottom sides), read off the base PNGs:
# floor/wall/ceiling/light have a 2 px bevel + seam ring; trim is a horizontal hull band, so only 1 px edges.
WIDTH = {
    "habitat_floor": (2, 2),
    "habitat_wall": (2, 2),
    "habitat_ceiling": (2, 2),
    "habitat_light": (2, 2),
    "habitat_light_off": (2, 2),   # ECO03: the light with no power, dimmed from habitat_light
    "habitat_trim": (1, 1),
}


def axis(v, w, lo, hi):
    """(connected?, source coordinate if connected, in band?) for one axis."""
    if v < w:
        return lo, 2 * w - 1 - v, True
    if v >= 16 - w:
        return hi, 2 * (16 - w) - 1 - v, True
    return False, v, False


def variant(base, mask, wx, wy):
    out = Image.new("RGBA", (16, 16))
    src = base.load()
    px = out.load()
    for y in range(16):
        for x in range(16):
            cx, mx, bx = axis(x, wx, bool(mask & LEFT), bool(mask & RIGHT))
            cy, my, by = axis(y, wy, bool(mask & UP), bool(mask & DOWN))
            if cx and cy:
                p = src[mx, my]
            elif cx:
                p = src[mx, y] if not by else src[8, y]
            elif cy:
                p = src[x, my] if not bx else src[x, 8]
            else:
                p = src[x, y]
            px[x, y] = p
    return out


def dim_light():
    """habitat_light_off.png: habitat_light pulled down to the ceiling's dark steel tones (the lamp is out)."""
    src = Image.open(os.path.join(TEX, "habitat_light.png")).convert("RGBA")
    out = Image.new("RGBA", src.size)
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            out.putpixel((x, y), (int(r * 0.33), int(g * 0.35), int(b * 0.38), a))
    return out


def main(dry):
    written = []
    if not dry:
        off = dim_light()
        off.save(os.path.join(TEX, "habitat_light_off.png"))
        os.makedirs(LOCK, exist_ok=True)
        off.save(os.path.join(LOCK, "habitat_light_off.png"))
    for base_id, (wx, wy) in WIDTH.items():
        base = Image.open(os.path.join(TEX, base_id + ".png")).convert("RGBA")
        for mask in range(1, 16):
            name = f"{base_id}_c{mask}.png"
            written.append(name)
            if dry:
                continue
            variant(base, mask, wx, wy).save(os.path.join(TEX, name))
            os.makedirs(LOCK, exist_ok=True)
            shutil.copyfile(os.path.join(TEX, name), os.path.join(LOCK, name))
    habitat_window_ctm.main(dry)
    print({"written": written, "dry_run": dry})


if __name__ == "__main__":
    main("--dry-run" in sys.argv)
