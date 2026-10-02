"""Connected-glass variants of the habitat window (H01 follow-up).

Derives textures/block/habitat_window_c<mask>.png (mask 0..15) from the approved habitat_window.png (ChatGPT sheet
HAB1, glass made translucent): on every connected side the 3px frame band (2px metal + seal line) becomes glass, and
the frame of an unconnected side keeps running through the corner.  The highlight stays only on panes whose top-left
block it is (no up / left neighbour).  Mask bits are as seen from outside the face: 1 = up, 2 = down, 4 = left,
8 = right.  The base texture itself is mask 0 and is not rewritten; the 15 others are copied into the texture locks.

    python tools/habitat_window_ctm.py            # write the variants + locks
    python tools/habitat_window_ctm.py --dry-run  # only list them
"""
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(ROOT, "..", "src", "main", "resources", "assets", "abyssia", "textures", "block")
LOCK = os.path.join(ROOT, "texture_locks", "assets", "textures", "block")
BASE = "habitat_window"
GLASS = (19, 80, 106, 96)
FRAME = 3  # band width
UP, DOWN, LEFT, RIGHT = 1, 2, 4, 8


def band(v):
    return "lo" if v < FRAME else "hi" if v >= 16 - FRAME else "mid"


def variant(base, mask):
    out = Image.new("RGBA", (16, 16))
    src = base.load()
    px = out.load()
    for y in range(16):
        for x in range(16):
            bx, by = band(x), band(y)
            cx = {"lo": bool(mask & LEFT), "hi": bool(mask & RIGHT), "mid": None}[bx]
            cy = {"lo": bool(mask & UP), "hi": bool(mask & DOWN), "mid": None}[by]
            if bx == "mid" and by == "mid":
                p = src[x, y]
                # glass interior: keep the highlight only on the pane's top-left block
                if p[3] == 255 and (mask & (UP | LEFT)):
                    p = GLASS
            elif by == "mid":
                p = GLASS if cx else src[x, y]
            elif bx == "mid":
                p = GLASS if cy else src[x, y]
            elif cx and cy:
                p = GLASS
            elif cy:      # side frame continues up / down through the corner
                p = src[x, 8]
            elif cx:      # top / bottom frame continues sideways through the corner
                p = src[8, y]
            else:
                p = src[x, y]
            px[x, y] = p
    return out


def main(dry):
    base = Image.open(os.path.join(TEX, BASE + ".png")).convert("RGBA")
    written = []
    for mask in range(1, 16):
        name = f"{BASE}_c{mask}.png"
        written.append(name)
        if dry:
            continue
        img = variant(base, mask)
        img.save(os.path.join(TEX, name))
        os.makedirs(LOCK, exist_ok=True)
        shutil.copyfile(os.path.join(TEX, name), os.path.join(LOCK, name))
    print({"written": written, "dry_run": dry})


if __name__ == "__main__":
    main("--dry-run" in sys.argv)
